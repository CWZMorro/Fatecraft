package io.github.cwzmorro.fatecraft.chain;

import io.github.cwzmorro.alayacore.combat.ServantBonus;
import io.github.cwzmorro.alayacore.divinity.Divinity;
import io.github.cwzmorro.alayacore.progression.Progression;
import io.github.cwzmorro.fatecraft.FateConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * One cast of Enkidu's bind: once the chains reach the target it is held with its attack cut until the caster lets go,
 * either is gone, or the chain's one HP pool is struck away through its links ({@link ChainLink}).
 */
public final class ChainBind extends Entity {
	private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(ChainBind.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_CHAINS = SynchedEntityData.defineId(ChainBind.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> DATA_SEED = SynchedEntityData.defineId(ChainBind.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Vector3fc> DATA_ORIGIN = SynchedEntityData.defineId(ChainBind.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(ChainBind.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> DATA_RELEASED_AT = SynchedEntityData.defineId(ChainBind.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_TIGHTENING = SynchedEntityData.defineId(ChainBind.class, EntityDataSerializers.BOOLEAN);
	private static final Identifier ATTACK_CUT = Chains.BOUND_ID;
	private static final int EFFECT_TICKS = 40;
	private static final double DRIFT = 0.05;
	private static final double LINK_OUT = 0.6;
	private static final int NOT_YET = -1;

	// Server only.
	private @Nullable UUID caster;
	private @Nullable LivingEntity target;
	private @Nullable Vec3 anchor;
	private final List<ChainLink> links = new ArrayList<>();
	private List<Vec3> gates = List.of();
	private double hp;
	private double divinity;
	private int nextTighten = NOT_YET;

	public ChainBind(EntityType<? extends ChainBind> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public static @Nullable ChainBind of(LivingEntity caster) {
		Integer id = caster.getAttached(Chains.BINDING);
		return id != null && caster.level().getEntity(id) instanceof ChainBind bind && !bind.released() ? bind : null;
	}

	static ChainBind start(ServerLevel level, LivingEntity caster, LivingEntity target, int chains) {
		ChainBind bind = new ChainBind(Chains.BIND, level);
		bind.caster = caster.getUUID();
		bind.target = target;
		bind.entityData.set(DATA_TARGET, target.getId());
		bind.entityData.set(DATA_CHAINS, chains);
		bind.entityData.set(DATA_SEED, level.getRandom().nextLong());
		bind.entityData.set(DATA_ORIGIN, target.getBoundingBox().getCenter().toVector3f());
		bind.entityData.set(DATA_RADIUS, (float) ChainLayout.radius(target.getBoundingBox()));
		bind.setPos(target.position());
		level.addFreshEntity(bind);
		caster.setAttached(Chains.BINDING, bind.getId());
		return bind;
	}

	public int targetId() {
		return this.entityData.get(DATA_TARGET);
	}

	public int chains() {
		return this.entityData.get(DATA_CHAINS);
	}

	public long seed() {
		return this.entityData.get(DATA_SEED);
	}

	public Vec3 origin() {
		Vector3fc origin = this.entityData.get(DATA_ORIGIN);
		return new Vec3(origin.x(), origin.y(), origin.z());
	}

	public double radius() {
		return this.entityData.get(DATA_RADIUS);
	}

	public int boundAt() {
		return ChainLayout.arrivalTicks(this.radius());
	}

	public boolean bound() {
		return this.tickCount >= this.boundAt() && !this.released();
	}

	public int releasedAt() {
		return this.entityData.get(DATA_RELEASED_AT);
	}

	public boolean released() {
		return this.releasedAt() >= 0;
	}

	public boolean tightening() {
		return this.entityData.get(DATA_TIGHTENING);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.released()) {
			if (this.tickCount - this.releasedAt() >= ChainLayout.RELEASE_TICKS) {
				this.discard();
			}
			return;
		}
		LivingEntity caster = this.caster(level);
		LivingEntity target = this.target;
		if (caster == null || !caster.isAlive() || target == null || !target.isAlive() || target.isRemoved() || target.level() != level) {
			this.release();
			return;
		}
		this.setPos(target.position());
		if (this.tickCount == this.boundAt()) {
			this.hold(level, caster, target);
		}
		if (this.bound()) {
			this.keepHeld(target);
			if (this.tickCount % SharedConstants.TICKS_PER_SECOND == 0 && !(target instanceof Player)) {
				this.wearDown(level, target);
			}
		}
	}

	private @Nullable LivingEntity caster(ServerLevel level) {
		return this.caster != null && level.getEntity(this.caster) instanceof LivingEntity living ? living : null;
	}

	private void hold(ServerLevel level, LivingEntity caster, LivingEntity target) {
		EnkiduConfig config = FateConfig.enkidu();
		this.anchor = target.position();
		this.divinity = Divinity.of(target);
		this.hp = config.chainHp(Progression.sync(caster), this.divinity);
		AttributeInstance attack = target.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attack != null) {
			// Transient: a bind is never saved, so neither is its cut.
			attack.removeModifier(ATTACK_CUT);
			attack.addTransientModifier(new AttributeModifier(ATTACK_CUT, config.attackKept(this.divinity) - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		this.gates = ChainLayout.gates(this.origin(), this.radius(), this.seed(), this.chains());
		for (int chain = 0; chain < this.chains(); chain++) {
			ChainLink link = ChainLink.of(this);
			this.links.add(link);
			level.addFreshEntity(link);
		}
		this.placeLinks(target);
	}

	private void keepHeld(LivingEntity target) {
		if (this.anchor != null && target.position().distanceToSqr(this.anchor) > DRIFT * DRIFT) {
			target.teleportTo(this.anchor.x, this.anchor.y, this.anchor.z);
		}
		target.setDeltaMovement(Vec3.ZERO);
		target.resetFallDistance();
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		MobEffectInstance effect = target.getEffect(Chains.BOUND);
		if (effect == null || effect.getDuration() < EFFECT_TICKS / 2) {
			target.addEffect(new MobEffectInstance(Chains.BOUND, EFFECT_TICKS, 0, false, false, true));
		}
		this.placeLinks(target);
	}

	private void placeLinks(LivingEntity target) {
		Vec3 centre = target.getBoundingBox().getCenter();
		double out = Math.max(target.getBbWidth(), target.getBbHeight()) / 2 + LINK_OUT;
		for (int chain = 0; chain < this.links.size(); chain++) {
			Vec3 toGate = this.gates.get(chain).subtract(centre).normalize();
			ChainLink link = this.links.get(chain);
			link.setPos(centre.add(toGate.scale(out)).subtract(0, link.getBbHeight() / 2, 0));
		}
	}

	private void wearDown(ServerLevel level, LivingEntity target) {
		AttributeInstance attack = target.getAttribute(Attributes.ATTACK_DAMAGE);
		double strain = attack != null && attack.getValue() > 0
			? attack.getValue() / FateConfig.enkidu().attackKept(this.divinity)
			: target.getMaxHealth() * FateConfig.enkidu().noAttackStrain();
		this.weaken(level, strain);
	}

	// The bound target, or anyone not on the caster's team, takes it off the chain's HP; the caster and their team can't. Returns whether it counted.
	boolean strikeChain(@Nullable Entity attacker, float amount) {
		if (!(this.level() instanceof ServerLevel level) || !this.bound() || attacker == null) {
			return false;
		}
		LivingEntity caster = this.caster(level);
		if (attacker != this.target && (attacker == caster || caster != null && attacker.isAlliedTo(caster))) {
			return false;
		}
		this.weaken(level, amount);
		return true;
	}

	private void weaken(ServerLevel level, double amount) {
		this.hp -= amount;
		if (this.hp <= 0) {
			if (this.caster(level) instanceof ServerPlayer caster) {
				caster.sendOverlayMessage(Component.translatable("ability.fatecraft.enkidu.broken"));
			}
			this.release();
		}
	}

	void tighten(ServerLevel level, LivingEntity caster) {
		if (!this.bound() || this.target == null) {
			return;
		}
		boolean first = !this.tightening();
		if (first || this.tickCount >= this.nextTighten) {
			ChainDamage.tighten(level, caster, this.target, FateConfig.enkidu().tighten(first, this.divinity) + ServantBonus.classAttack(caster));
			this.nextTighten = this.tickCount + SharedConstants.TICKS_PER_SECOND;
		}
		this.entityData.set(DATA_TIGHTENING, true);
	}

	void stopTightening() {
		this.entityData.set(DATA_TIGHTENING, false);
	}

	void release() {
		if (this.released()) {
			return;
		}
		this.entityData.set(DATA_RELEASED_AT, this.tickCount);
		this.entityData.set(DATA_TIGHTENING, false);
		this.links.forEach(Entity::discard);
		this.links.clear();
		this.gates = List.of();
		if (this.target != null) {
			AttributeInstance attack = this.target.getAttribute(Attributes.ATTACK_DAMAGE);
			if (attack != null) {
				attack.removeModifier(ATTACK_CUT);
			}
			this.target.removeEffect(Chains.BOUND);
		}
		if (this.level() instanceof ServerLevel level && this.caster(level) instanceof LivingEntity caster) {
			caster.removeAttached(Chains.BINDING);
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		this.release();
		super.remove(reason);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_TARGET, NOT_YET);
		builder.define(DATA_CHAINS, 0);
		builder.define(DATA_SEED, 0L);
		builder.define(DATA_ORIGIN, new Vector3f());
		builder.define(DATA_RADIUS, 1.0F);
		builder.define(DATA_RELEASED_AT, NOT_YET);
		builder.define(DATA_TIGHTENING, false);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}
}
