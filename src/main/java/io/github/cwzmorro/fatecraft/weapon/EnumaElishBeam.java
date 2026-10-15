package io.github.cwzmorro.fatecraft.weapon;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.beam.BeamVolume;
import io.github.cwzmorro.alayacore.beam.HeldBeam;
import io.github.cwzmorro.alayacore.clash.BeamClashes;
import io.github.cwzmorro.alayacore.clash.ClashBeam;
import io.github.cwzmorro.alayacore.combat.AttackData;
import io.github.cwzmorro.alayacore.combat.Attacks;
import io.github.cwzmorro.alayacore.combat.ContinuousSource;
import io.github.cwzmorro.alayacore.combat.ServantBonus;
import io.github.cwzmorro.alayacore.combat.WeaponBlocks;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.marble.RealityMarbles;
import io.github.cwzmorro.alayacore.util.HeldInFront;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Enuma Elish's beam: {@link #length} blocks long from in front of the caster, {@link #radiusAt widening} from Ea to
 * the far end ({@link EaConfig}). It follows the caster's look at the beam turn rate, deals EX damage per second
 * (ignoring armor and Protection) and carves out its blocks; a shield stops it. Can clash ({@link Attacks}); never saved.
 */
public final class EnumaElishBeam extends Entity implements ClashBeam {
	private static final double ORIGIN_AHEAD = 1.5;
	private static final double CARVE_AFTER_TURNING = Math.toRadians(1.0);
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Fatecraft.id("enuma_elish"));
	private static final EntityDataAccessor<Float> DATA_REACH = SynchedEntityData.defineId(EnumaElishBeam.class, EntityDataSerializers.FLOAT);

	private @Nullable UUID caster;
	private @Nullable Vec3 carvedAlong;
	private double carvedReach;

	public EnumaElishBeam(EntityType<? extends EnumaElishBeam> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	static void fire(ServerLevel level, LivingEntity caster) {
		EnumaElishBeam beam = new EnumaElishBeam(FateWeapons.ENUMA_ELISH_BEAM, level);
		beam.caster = caster.getUUID();
		beam.setAttached(Attacks.ATTACK, new AttackData(caster.getUUID(), EnumaElish.RANK, true, true));
		beam.follow(caster, caster.getLookAngle());
		level.addFreshEntity(beam);
	}

	public static Vec3 origin(Vec3 eye, Vec3 direction) {
		return HeldInFront.at(eye, direction, ORIGIN_AHEAD);
	}

	public static double length() {
		return FateConfig.ea().enumaElishLength();
	}

	public static double radiusAt(double distance) {
		EaConfig config = FateConfig.ea();
		return Mth.lerp(distance / config.enumaElishLength(), config.enumaElishStartRadius(), config.enumaElishEndRadius());
	}

	public double reach() {
		return this.entityData.get(DATA_REACH);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		Entity found = this.caster == null ? null : level.getEntity(this.caster);
		if (!(found instanceof LivingEntity owner) || !owner.isAlive() || !Abilities.isActive(owner, EnumaElish.KEY.identifier())) {
			this.discard();
			return;
		}
		this.follow(owner, HeldBeam.turned(this.getLookAngle(), owner.getLookAngle()));
	}

	@Override
	public void beamEffects(ServerLevel level) {
		LivingEntity owner = this.beamOwner();
		if (owner == null) {
			return;
		}
		Vec3 direction = this.getLookAngle();
		HeldBeam.Clip clip = HeldBeam.clip(level, this);
		double reach = clip.reach();
		this.entityData.set(DATA_REACH, (float) reach);
		RealityMarbles.shatter(level, this.position(), this.position().add(direction.scale(reach)));
		DamageSource source = ContinuousSource.of(level, DAMAGE, this, owner);
		float damage = BeamClashes.damage(this, ContinuousSource.perTick(AlayaConfig.ranks().beamDamagePerSecond(EnumaElish.RANK) + ServantBonus.classAttack(owner)));
		clip.hurtShield(level, source, damage);
		this.hitEntities(level, owner, source, damage, direction, reach);
		if (WeaponBlocks.allowed(level) && this.shouldCarve(direction, reach)) {
			this.carve(level, direction, reach);
		}
	}

	@Override
	public Entity beamEntity() {
		return this;
	}

	@Override
	public Vec3 beamOrigin() {
		return this.position();
	}

	@Override
	public Vec3 beamDirection() {
		return this.getLookAngle();
	}

	@Override
	public double beamLength() {
		return length();
	}

	@Override
	public double beamRadius() {
		return FateConfig.ea().enumaElishEndRadius();
	}

	@Override
	public @Nullable LivingEntity beamOwner() {
		Entity found = this.caster == null || !(this.level() instanceof ServerLevel level) ? null : level.getEntity(this.caster);
		return found instanceof LivingEntity living ? living : null;
	}

	@Override
	public double beamDrainPerSecond() {
		return AlayaConfig.ranks().beamDrainPerSecond(EnumaElish.RANK);
	}

	@Override
	public boolean beamLive() {
		LivingEntity owner = this.beamOwner();
		return owner != null && owner.isAlive() && Abilities.isActive(owner, EnumaElish.KEY.identifier());
	}

	@Override
	public void loseClash() {
		LivingEntity owner = this.beamOwner();
		if (owner != null) {
			Abilities.end(owner, EnumaElish.KEY.identifier());
		}
		this.discard();
	}

	private void follow(LivingEntity owner, Vec3 direction) {
		this.setPos(origin(owner.getEyePosition(), direction));
		this.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
		this.setXRot((float) Math.toDegrees(-Math.asin(Mth.clamp(direction.y, -1.0, 1.0))));
	}

	private void hitEntities(ServerLevel level, LivingEntity owner, DamageSource source, float damage, Vec3 direction, double reach) {
		BeamVolume.hit(level, this, owner, source, damage, this.position(), direction, reach, EnumaElishBeam::radiusAt);
	}

	private boolean shouldCarve(Vec3 direction, double reach) {
		if (reach <= this.carvedReach && this.carvedAlong != null
			&& Math.acos(Mth.clamp(this.carvedAlong.dot(direction), -1.0, 1.0)) < CARVE_AFTER_TURNING) {
			return false;
		}
		this.carvedAlong = direction;
		this.carvedReach = reach;
		return true;
	}

	private void carve(ServerLevel level, Vec3 direction, double reach) {
		BeamVolume.carve(level, this.position(), direction, reach, EnumaElishBeam::radiusAt);
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
		builder.define(DATA_REACH, (float) length());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}
}
