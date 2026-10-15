package io.github.cwzmorro.fatecraft.altera;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.beam.HeldBeam;
import io.github.cwzmorro.alayacore.clash.ClashBeam;
import io.github.cwzmorro.alayacore.combat.AttackData;
import io.github.cwzmorro.alayacore.combat.AttackOrigin;
import io.github.cwzmorro.alayacore.combat.Attacks;
import io.github.cwzmorro.alayacore.combat.ContinuousSource;
import io.github.cwzmorro.alayacore.combat.DashMovement;
import io.github.cwzmorro.alayacore.combat.ForcedAdvance;
import io.github.cwzmorro.alayacore.combat.HitCooldown;
import io.github.cwzmorro.alayacore.combat.ServantBonus;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.alayacore.shield.ShieldEntity;
import io.github.cwzmorro.alayacore.shield.ShieldShape;
import io.github.cwzmorro.alayacore.shield.Shields;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import io.github.cwzmorro.fatecraft.weapon.WeaponOpening;
import io.github.cwzmorro.fatecraft.weapon.WeaponSpin;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Photon Ray's cone shield and the server side of her run, which carves her way and hits what she passes. Hits come from
 * her chest where the tick's move began, not from the sword ahead, so a shield she presses against keeps what is behind it.
 */
public final class PhotonRayEntity extends ShieldEntity implements AttackOrigin {
	private static final double COLLISION_STEP = 0.25;
	// Her path this many ticks ahead is already broken, so a client slightly behind the server still finds it clear.
	private static final int CARVE_LEAD_TICKS = 3;
	// Vanilla's ground drag on a push (block friction 0.6 times 0.91): a push of speed v carries a body v / (1 - drag) blocks.
	private static final double GROUND_DRAG = 0.546;
	public static final double SWORD_AHEAD = 2.5;
	public static final double CHEST_HEIGHT = 1.0;
	private static final double CONE_TIP_RADIUS = 0.15;
	private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(PhotonRayEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DASHING = SynchedEntityData.defineId(PhotonRayEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(PhotonRayEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> CHARGE_NEEDED = SynchedEntityData.defineId(PhotonRayEntity.class, EntityDataSerializers.INT);
	private static final int NOT_LOCKED = -1;
	private final Set<UUID> pierced = new HashSet<>();
	private int lockedAge = NOT_LOCKED;
	private @Nullable LivingEntity caster;
	private ItemStack sword = ItemStack.EMPTY;
	private Vec3 direction = Vec3.ZERO;
	private @Nullable Vec3 struckFrom;
	private Vec3 lastAt = Vec3.ZERO;

	public PhotonRayEntity(EntityType<? extends PhotonRayEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public void charge(LivingEntity owner, int neededTicks) {
		this.caster = owner;
		this.sword = owner.getMainHandItem();
		this.entityData.set(OWNER, owner.getId());
		this.entityData.set(CHARGE_NEEDED, neededTicks);
		this.setAttached(Attacks.ATTACK, new AttackData(owner.getUUID(), PhotonRay.RANK, false, true));
		WeaponOpening.set(this.sword, this.level().getGameTime(), true);
		this.sword.set(FateWeapons.SPIN, new WeaponSpin(this.level().getGameTime(), (float) FateConfig.altera().spinDegreesPerTick()));
		this.follow(owner, owner.getLookAngle());
	}

	public void launch() {
		if (this.caster == null) {
			return;
		}
		this.direction = ForcedAdvance.heading(this.caster);
		this.lastAt = this.caster.position();
		this.entityData.set(DASHING, true);
		this.deploy(PhotonRay.RANK);
		ForcedAdvance.set(this.caster, speed(1.0));
	}

	private static double speed(double share) {
		return FateConfig.altera().dashBlocksPerSecond() / SharedConstants.TICKS_PER_SECOND * share;
	}

	public boolean dashing() {
		return this.entityData.get(DASHING);
	}

	public void charging(int heldTicks) {
		this.entityData.set(CHARGE, Math.min(heldTicks, this.entityData.get(CHARGE_NEEDED)));
	}

	public boolean charged() {
		return this.entityData.get(CHARGE) >= this.entityData.get(CHARGE_NEEDED);
	}

	/** Returns the charge share, 0 to 1, smoothed within the tick; it reaches 1 only once {@link #charged()}. */
	public float chargeProgress(float partialTick) {
		int needed = this.entityData.get(CHARGE_NEEDED);
		if (this.dashing() || this.charged() || needed <= 0) {
			return 1.0F;
		}
		return (Math.min(this.entityData.get(CHARGE), needed - 1) + partialTick) / needed;
	}

	public float ticksSinceLocked(float partialTick) {
		return this.lockedAge == NOT_LOCKED ? NOT_LOCKED : this.tickCount - this.lockedAge + partialTick;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (this.level().isClientSide() && CHARGE.equals(accessor) && this.lockedAge == NOT_LOCKED && this.charged()) {
			this.lockedAge = this.tickCount;
		}
	}

	public @Nullable LivingEntity owner() {
		Entity found = this.level().getEntity(this.entityData.get(OWNER));
		return found instanceof LivingEntity living ? living : this.caster;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.isRemoved() || !(this.level() instanceof ServerLevel level)) {
			return;
		}
		LivingEntity owner = this.caster;
		if (owner == null || !owner.isAlive() || owner.level() != level || owner.getMainHandItem() != this.sword
			|| !Abilities.isActive(owner, PhotonRay.KEY.identifier())) {
			this.discard();
			return;
		}
		if (!this.dashing()) {
			this.follow(owner, owner.getLookAngle());
			return;
		}
		double speed = speed(this.beamPressing(level) ? FateConfig.altera().beamDashSpeedFactor() : 1.0);
		ForcedAdvance.set(owner, speed);
		this.direction = ForcedAdvance.heading(owner);
		Vec3 before = this.lastAt;
		Vec3 after = owner.position();
		this.lastAt = after;
		this.struckFrom = before.add(0, CHEST_HEIGHT, 0);
		DashMovement.carve(level, owner, this.direction.scale(speed * CARVE_LEAD_TICKS), COLLISION_STEP);
		this.hitPass(level, owner, before, after);
		this.follow(owner, this.direction);
		this.hitFront(level, owner);
		// Her fall isn't hers to pay for: she runs over gaps and lands where she stops.
		owner.fallDistance = 0;
	}

	private void hitPass(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 to) {
		double radius = FateConfig.altera().hitRadius();
		Vec3 lift = new Vec3(0, CHEST_HEIGHT, 0);
		Vec3 start = from.add(lift);
		Vec3 end = to.add(lift);
		DamageSource source = level.damageSources().source(Altera.DAMAGE, this, owner);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(radius))) {
			if (target != owner && intersects(target.getBoundingBox().inflate(radius), start, end) && this.pierced.add(target.getUUID())) {
				// A pierce lands even on a target still in its hit cooldown.
				if (HitCooldown.ignoring(target, () -> target.hurtServer(level, source, (float) PhotonRay.damage(owner)))) {
					this.knockBack(target);
				}
			}
		}
	}

	private void knockBack(LivingEntity target) {
		target.knockback(FateConfig.altera().knockbackDistance() * (1 - GROUND_DRAG), -this.direction.x, -this.direction.z);
		target.hurtMarked = true;
	}

	private void hitFront(ServerLevel level, LivingEntity owner) {
		AlteraConfig config = FateConfig.altera();
		Vec3 end = this.position().add(this.direction.scale(config.frontReach()));
		DamageSource source = ContinuousSource.of(level, Altera.DAMAGE, this, owner);
		float damage = ContinuousSource.perTick((AlayaConfig.ranks().beamDamagePerSecond(PhotonRay.RANK) + ServantBonus.classAttack(owner))
			* config.frontDamageMultiplier());
		Vec3 from = this.attackOrigin(end);
		Shields.Hit shield = Shields.firstHit(level, from, end, this);
		if (shield != null) {
			shield.shield().hurtAt(level, source, damage, shield.entry());
		}
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(this.position(), end).inflate(config.hitRadius()))) {
			if (target != owner && intersects(target.getBoundingBox().inflate(config.hitRadius()), this.position(), end)) {
				target.hurtServer(level, source, damage);
			}
		}
	}

	private static boolean intersects(AABB box, Vec3 start, Vec3 end) {
		return box.contains(start) || box.contains(end) || box.clip(start, end).isPresent();
	}

	private boolean beamPressing(ServerLevel level) {
		for (Entity entity : level.getAllEntities()) {
			if (!(entity instanceof ClashBeam beam) || entity.isRemoved() || !beam.beamLive()) {
				continue;
			}
			Shields.Hit hit = HeldBeam.clip(level, beam).shield();
			if (hit != null && hit.shield() == this) {
				return true;
			}
		}
		return false;
	}

	@Override
	public ShieldShape shape() {
		return new ShieldShape.Cone(coneLength(), coneRadius(), CONE_TIP_RADIUS);
	}

	public static double coneLength() {
		return FateConfig.altera().coneLength();
	}

	public static double coneRadius() {
		return FateConfig.altera().coneRadius();
	}

	@Override
	public Vec3 attackOrigin(Vec3 target) {
		return this.struckFrom == null ? this.position() : this.struckFrom;
	}

	@Override
	public @Nullable Vec3 entry(Vec3 from, Vec3 to) {
		return this.dashing() && !this.isRemoved() ? super.entry(from, to) : null;
	}

	// The run, not a shield's tap timeout, decides how long the cone lasts.
	@Override
	public long ticksLeft() {
		return this.isRemoved() ? 0 : 1;
	}

	@Override
	protected double maxHpFor(Rank rank) {
		return AlayaConfig.ranks().npDamage(rank, 0.0) * FateConfig.altera().coneHpFactor();
	}

	@Override
	public void remove(RemovalReason reason) {
		if (this.caster != null) {
			ForcedAdvance.stop(this.caster);
		}
		if (!this.sword.isEmpty()) {
			this.sword.remove(FateWeapons.SPIN);
			WeaponOpening.set(this.sword, this.level().getGameTime(), false);
		}
		super.remove(reason);
	}

	private void follow(LivingEntity owner, Vec3 look) {
		this.setPos(owner.position().add(0, CHEST_HEIGHT, 0).add(look.scale(SWORD_AHEAD)));
		this.setYRot((float) Math.toDegrees(Math.atan2(-look.x, look.z)));
		this.setXRot((float) Math.toDegrees(-Math.asin(look.y)));
		this.fitBox();
	}

	@Override
	public boolean isPickable() {
		return this.dashing();
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(OWNER, 0);
		builder.define(DASHING, false);
		builder.define(CHARGE, 0);
		builder.define(CHARGE_NEEDED, 0);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}
}
