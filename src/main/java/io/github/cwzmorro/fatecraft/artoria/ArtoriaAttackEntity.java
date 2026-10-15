package io.github.cwzmorro.fatecraft.artoria;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.combat.Aim;
import io.github.cwzmorro.alayacore.combat.AttackData;
import io.github.cwzmorro.alayacore.combat.Attacks;
import io.github.cwzmorro.alayacore.combat.Deflection;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.alayacore.shield.Shields;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/** The light of Artoria's sword skills: its phases, movement and hits run on the server and are synced for drawing. */
public final class ArtoriaAttackEntity extends Entity {
	public enum Kind { WIND, GOLD, SHOT }

	/** Where it is in its life: the cone's burst; Mana Shot's light gathering; the crescent's sweep; the end blast or the crescent's fade. */
	public enum Phase { SLASH, CHARGE, FLIGHT, BLAST }

	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Fatecraft.id("artoria_skill"));
	private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(ArtoriaAttackEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(ArtoriaAttackEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(ArtoriaAttackEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(ArtoriaAttackEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Vector3fc> BEAM_START = SynchedEntityData.defineId(ArtoriaAttackEntity.class, EntityDataSerializers.VECTOR3);
	private @Nullable LivingEntity caster;
	private Identifier ability = StrikeAir.KEY.identifier();
	private Vec3 direction = Vec3.ZERO;
	private float damage;
	private final Set<UUID> hit = new HashSet<>();
	private final Set<UUID> deflected = new HashSet<>();
	// Kept so a weapon swap mid-charge still unlights the sword.
	private ItemStack chargingSword = ItemStack.EMPTY;

	public ArtoriaAttackEntity(EntityType<? extends ArtoriaAttackEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public void start(LivingEntity owner, Kind kind, Identifier ability, Rank rank, double damage) {
		this.caster = owner;
		this.ability = ability;
		this.damage = (float) damage;
		this.entityData.set(OWNER, owner.getId());
		this.entityData.set(KIND, kind.ordinal());
		this.setAttached(Attacks.ATTACK, new AttackData(owner.getUUID(), rank, false, false));
		this.direction = owner.getLookAngle();
		this.setYRot(owner.getYRot());
		this.setXRot(owner.getXRot());
		ArtoriaConfig c = FateConfig.artoria();
		if (kind == Kind.WIND) {
			this.phase(Phase.SLASH);
			this.setPos(owner.getEyePosition());
			owner.swing(InteractionHand.MAIN_HAND, true);
			if (this.level() instanceof ServerLevel level) {
				this.windHit(level, owner, c);
				this.deflect(level, this.position(), this.position(), c.deflectRadius());
			}
		} else {
			this.phase(kind == Kind.SHOT ? Phase.CHARGE : Phase.FLIGHT);
			if (kind == Kind.SHOT) {
				this.chargingSword = owner.getMainHandItem();
				this.chargingSword.set(Artoria.MANA_SHOT_CHARGE, true);
			}
			this.setPos(kind == Kind.GOLD ? owner.getEyePosition() : this.tip(owner));
			if (kind == Kind.GOLD) {
				this.direction = new Vec3(this.direction.x, 0, this.direction.z).normalize();
				this.setXRot(0);
				owner.swing(InteractionHand.MAIN_HAND, true);
			}
		}
	}

	public Kind kind() {
		return Kind.values()[this.entityData.get(KIND)];
	}

	public Phase phase() {
		return Phase.values()[this.entityData.get(PHASE)];
	}

	public int phaseAge() {
		return this.entityData.get(AGE);
	}

	public @Nullable LivingEntity owner() {
		Entity found = this.level().getEntity(this.entityData.get(OWNER));
		return found instanceof LivingEntity living ? living : this.caster;
	}

	private void phase(Phase phase) {
		this.entityData.set(PHASE, phase.ordinal());
		this.entityData.set(AGE, 0);
	}

	private Vec3 tip(LivingEntity owner) {
		ArtoriaConfig c = FateConfig.artoria();
		return owner.getEyePosition().add(owner.getLookAngle().scale(c.stopDistance()));
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		LivingEntity owner = this.caster;
		if (owner == null || !owner.isAlive() || owner.level() != level || !ArtoriaAbility.sword(owner)
			|| !Abilities.isActive(owner, this.ability)) {
			this.discard();
			return;
		}
		this.entityData.set(AGE, this.phaseAge() + 1);
		ArtoriaConfig c = FateConfig.artoria();
		switch (this.phase()) {
			case SLASH -> {
				this.deflect(level, this.position(), this.position(), c.deflectRadius());
				if (this.phaseAge() >= Durations.ticks(c.strikeEffectSeconds())) {
					this.discard();
				}
			}
			case CHARGE -> {
				this.setPos(this.tip(owner));
				if (this.phaseAge() >= Durations.ticks(c.chargeSeconds())) {
					this.direction = Aim.point(owner, owner.getLookAngle(), c.shotRange()).subtract(this.position()).normalize();
					this.setYRot(owner.getYRot());
					this.setXRot(owner.getXRot());
					owner.swing(InteractionHand.MAIN_HAND, true);
					this.entityData.set(BEAM_START, this.position().toVector3f());
					this.phase(Phase.FLIGHT);
					this.clearCharge();
					this.fly(level, owner, c);
				}
			}
			case FLIGHT -> this.fly(level, owner, c);
			case BLAST -> {
				if (this.phaseAge() >= Durations.ticks(this.kind() == Kind.GOLD ? c.slashFadeSeconds() : c.effectSeconds())) {
					this.discard();
				}
			}
		}
	}

	private void deflect(ServerLevel level, Vec3 from, Vec3 to, double radius) {
		Deflection.deflect(level, this.ability, this, from, to, radius, this.deflected);
	}

	private DamageSource source(ServerLevel level, LivingEntity owner) {
		return level.damageSources().source(DAMAGE, this, owner);
	}

	private void windHit(ServerLevel level, LivingEntity owner, ArtoriaConfig c) {
		Vec3 from = this.position();
		Vec3 end = from.add(this.direction.scale(c.strikeConeLength()));
		Shields.Hit shield = Shields.firstHit(level, from, end);
		if (shield != null) {
			shield.shield().hurtAt(level, this.source(level, owner), this.damage, shield.entry());
			end = shield.entry();
		}
		double coneSlope = Math.tan(Math.toRadians(c.strikeConeDegrees()) / 2);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(c.strikeConeLength() * coneSlope))) {
			Vec3 centre = target.getBoundingBox().getCenter();
			Vec3 offset = centre.subtract(from);
			double depth = offset.dot(this.direction);
			if (target != owner && depth > 0 && depth <= c.strikeConeLength()
				&& offset.subtract(this.direction.scale(depth)).length() <= depth * coneSlope
				&& level.clip(new ClipContext(from, centre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS
				&& Shields.firstHit(level, from, centre) == null) {
				target.hurtServer(level, this.source(level, owner), this.damage);
				Vec3 away = centre.subtract(from);
				Vec3 dir = new Vec3(away.x, 0, away.z);
				if (dir.equals(Vec3.ZERO)) {
					dir = this.direction;
				}
				target.knockback(c.strikeKnockback(), -dir.x, -dir.z);
				target.hurtMarked = true;
			}
		}
	}

	// A side-to-side melee sector: anchored to the caster, never a travelling wave.
	private void goldSweep(ServerLevel level, LivingEntity owner, ArtoriaConfig c) {
		double reach = c.slashRange();
		this.setPos(owner.getEyePosition());
		Vec3 origin = this.position();
		double halfArc = Math.toRadians(c.slashArcDegrees()) / 2;
		double progress = Math.min(1, this.phaseAge() / (double) Durations.ticks(c.slashSweepSeconds()));
		double sweptAngle = -halfArc + 2 * halfArc * progress;
		Vec3 across = this.direction.cross(Vec3.Y_AXIS);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(reach))) {
			Vec3 centre = target.getBoundingBox().getCenter();
			Vec3 offset = centre.subtract(origin);
			double distance = Math.hypot(offset.x, offset.z);
			double angle = Math.atan2(offset.dot(across), offset.dot(this.direction));
			if (target == owner || !target.isAlive() || this.hit.contains(target.getUUID())
				|| distance > reach || angle < -halfArc || angle > sweptAngle
				|| Math.abs(offset.y) > c.slashRadius()) {
				continue;
			}
			HitResult block = level.clip(new ClipContext(origin, centre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
			if (block.getType() != HitResult.Type.MISS) {
				continue;
			}
			Shields.strike(level, origin, target, this.source(level, owner), this.damage, this.hit);
		}
		if (progress >= 1) {
			this.phase(Phase.BLAST);
		}
	}

	private void fly(ServerLevel level, LivingEntity owner, ArtoriaConfig c) {
		if (this.kind() == Kind.GOLD) {
			this.goldSweep(level, owner, c);
			return;
		}
		// The beam is instant: one lance of light from the centre of her view to the first entity, else block, else full range.
		Vec3 from = this.position();
		Vec3 end = from.add(this.direction.scale(c.shotRange()));
		HitResult block = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		Shields.Hit shield = Shields.firstHit(level, from, end);
		if (shield != null) {
			shield.shield().hurtAt(level, this.source(level, owner), this.damage, shield.entry());
			this.setPos(shield.entry());
			// A shield stops the blast too; never splash through it.
			this.phase(Phase.BLAST);
			return;
		}
		double radius = this.getBbWidth() / 2;
		LivingEntity first = null;
		double nearest = from.distanceToSqr(end);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(radius))) {
			if (target == owner || !target.isAlive()) {
				continue;
			}
			AABB box = target.getBoundingBox().inflate(radius);
			Vec3 contact = box.contains(from) ? from : box.clip(from, end).orElse(null);
			if (contact == null) {
				continue;
			}
			if (from.distanceToSqr(contact) <= nearest) {
				nearest = from.distanceToSqr(contact);
				first = target;
			}
		}
		if (first != null) {
			end = first.getBoundingBox().getCenter();
		}
		this.setPos(end);
		this.blast(level, owner, c);
		this.phase(Phase.BLAST);
	}

	public Vec3 beamStart() {
		return new Vec3(this.entityData.get(BEAM_START));
	}

	private void blast(ServerLevel level, LivingEntity owner, ArtoriaConfig c) {
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(c.blastRadius()))) {
			Vec3 centre = target.getBoundingBox().getCenter();
			if (target != owner && centre.distanceTo(this.position()) <= c.blastRadius()
				&& Shields.firstHit(level, this.position(), centre) == null) {
				target.hurtServer(level, this.source(level, owner), this.damage);
			}
		}
	}

	private void clearCharge() {
		this.chargingSword.remove(Artoria.MANA_SHOT_CHARGE);
	}

	@Override
	public void onRemoval(Entity.RemovalReason reason) {
		this.clearCharge();
		super.onRemoval(reason);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(OWNER, 0);
		builder.define(KIND, Kind.WIND.ordinal());
		builder.define(PHASE, Phase.SLASH.ordinal());
		builder.define(AGE, 0);
		builder.define(BEAM_START, new Vector3f());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}
}
