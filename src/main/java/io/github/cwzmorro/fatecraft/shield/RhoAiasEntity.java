package io.github.cwzmorro.fatecraft.shield;

import io.github.cwzmorro.alayacore.combat.AttackData;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.alayacore.shield.ShieldEntity;
import io.github.cwzmorro.alayacore.shield.ShieldShape;
import io.github.cwzmorro.alayacore.util.HeldInFront;
import io.github.cwzmorro.alayacore.util.Percent;
import io.github.cwzmorro.fatecraft.FateConfig;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Rho Aias's disc: one HP pool shown as four or seven petals, half damage from ordinary projectiles, and solid to
 * everything but its caster.
 */
public final class RhoAiasEntity extends ShieldEntity {
	// Visual only: the shield is already full throughout these 2 seconds.
	public static final int SUMMON_TICKS = 2 * SharedConstants.TICKS_PER_SECOND;
	private static final double ACROSS_PER_RADIUS = 2.0;
	private static final EntityDataAccessor<Integer> DATA_PETALS = SynchedEntityData.defineId(RhoAiasEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> DATA_CAST_AT = SynchedEntityData.defineId(RhoAiasEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(RhoAiasEntity.class, EntityDataSerializers.FLOAT);

	private static final EntityDataAccessor<Integer> DATA_FOLLOWING = SynchedEntityData.defineId(RhoAiasEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_DISTANCE = SynchedEntityData.defineId(RhoAiasEntity.class, EntityDataSerializers.FLOAT);
	private static final int NOT_FOLLOWING = -1;
	private static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> DATA_CASTER = SynchedEntityData.defineId(RhoAiasEntity.class,
		EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);

	private ShieldShape.Disc shape;

	public RhoAiasEntity(EntityType<? extends RhoAiasEntity> type, Level level) {
		super(type, level);
		this.shape = new ShieldShape.Disc(this.radius());
	}

	public static @Nullable RhoAiasEntity of(LivingEntity caster) {
		FateShields.Projection projection = caster.getAttached(FateShields.PROJECTION);
		return projection != null && caster.level() instanceof ServerLevel level
			&& level.getEntity(projection.shield()) instanceof RhoAiasEntity shield && shield.isAlive() ? shield : null;
	}

	static boolean isUp(LivingEntity caster) {
		FateShields.Projection projection = caster.getAttached(FateShields.PROJECTION);
		if (projection == null || projection.expiresAt() <= caster.level().getGameTime() || !(caster.level() instanceof ServerLevel here)) {
			return false;
		}
		ServerLevel level = here.getServer().getLevel(projection.dimension());
		return level != null && (!level.areEntitiesLoaded(projection.chunk().pack())
			|| level.getEntity(projection.shield()) instanceof RhoAiasEntity shield && shield.isAlive());
	}

	static void dismiss(LivingEntity caster) {
		FateShields.Projection projection = caster.removeAttached(FateShields.PROJECTION);
		if (projection == null || !(caster.level() instanceof ServerLevel here)) {
			return;
		}
		ServerLevel level = here.getServer().getLevel(projection.dimension());
		if (level != null) {
			if (level.getEntity(projection.shield()) instanceof RhoAiasEntity shield) {
				shield.discard();
			} else {
				FateShields.dismissUnloaded(level, projection);
			}
		}
	}

	public @Nullable LivingEntity following() {
		return this.level().getEntity(this.entityData.get(DATA_FOLLOWING)) instanceof LivingEntity owner ? owner : null;
	}

	public double followDistance() {
		return this.entityData.get(DATA_DISTANCE);
	}

	void stopFollowing() {
		this.entityData.set(DATA_FOLLOWING, NOT_FOLLOWING);
	}

	static RhoAiasEntity project(ServerLevel level, LivingEntity caster, Rank rank) {
		RhoAiasEntity shield = new RhoAiasEntity(FateShields.RHO_AIAS, level);
		shield.entityData.set(DATA_CASTER, Optional.of(EntityReference.of(caster)));
		shield.entityData.set(DATA_CAST_AT, level.getGameTime());
		shield.entityData.set(DATA_RADIUS, (float) (RhoAias.size(caster) / ACROSS_PER_RADIUS));
		shield.deploy(rank);
		shield.follow(caster);
		level.addFreshEntity(shield);
		return shield;
	}

	public int petals() {
		return this.entityData.get(DATA_PETALS);
	}

	public long castTime() {
		return this.entityData.get(DATA_CAST_AT);
	}

	public double radius() {
		return this.entityData.get(DATA_RADIUS);
	}

	private static float defaultRadius() {
		return (float) (FateConfig.rhoAias().defaultSize() / ACROSS_PER_RADIUS);
	}

	@Override
	public ShieldShape.Disc shape() {
		return this.shape;
	}

	@Override
	public void deploy(Rank rank) {
		super.deploy(rank);
		this.entityData.set(DATA_PETALS, petalsAt(rank));
	}

	void follow(LivingEntity caster) {
		this.entityData.set(DATA_FOLLOWING, caster.getId());
		double distance = RhoAias.distance(caster);
		this.entityData.set(DATA_DISTANCE, (float) distance);
		this.setYRot(caster.getYRot());
		this.setXRot(caster.getXRot());
		this.setPos(HeldInFront.at(caster.getEyePosition(), caster.getLookAngle(), distance));
		this.fitBox();
		this.remember(caster);
	}

	@Override
	protected boolean solid() {
		return true;
	}

	@Override
	public boolean hurtAt(ServerLevel level, DamageSource source, float amount, Vec3 point) {
		boolean hit = super.hurtAt(level, source, amount, point);
		// A vanilla arrow can fly on after hitting something that isn't alive, so a projectile that hit is used up here,
		// a piercing arrow or one whose rank did nothing too.
		if (hit && source.getDirectEntity() instanceof Projectile projectile) {
			projectile.discard();
		}
		return hit;
	}

	@Override
	public boolean hold(LivingEntity holder) {
		boolean held = super.hold(holder);
		this.remember(holder);
		return held;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_RADIUS.equals(accessor)) {
			this.shape = new ShieldShape.Disc(this.radius());
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		if (reason.shouldDestroy() && this.level() instanceof ServerLevel level) {
			LivingEntity caster = this.findCaster(level);
			FateShields.Projection projection = caster == null ? null : caster.getAttached(FateShields.PROJECTION);
			if (projection != null && projection.shield().equals(this.getUUID())) {
				caster.removeAttached(FateShields.PROJECTION);
			}
		}
		super.remove(reason);
	}

	@Override
	protected double damageTaken(DamageSource source, @Nullable AttackData attack, float amount) {
		return source.is(DamageTypeTags.IS_PROJECTILE) && (attack == null || !attack.noblePhantasm())
			? amount * Percent.toFraction(FateConfig.rhoAias().projectileDamagePercent()) : amount;
	}

	private static int petalsAt(Rank rank) {
		RhoAiasConfig config = FateConfig.rhoAias();
		return rank.letter().compareTo(config.trueNameRank().letter()) >= 0 ? config.trueNamePetals() : config.lowerPetals();
	}

	private void remember(LivingEntity caster) {
		caster.setAttached(FateShields.PROJECTION, new FateShields.Projection(this.getUUID(), this.level().getGameTime() + this.ticksLeft(),
			this.level().dimension(), this.chunkPosition()));
	}

	@Override
	protected @Nullable UUID casterUuid() {
		return this.entityData.get(DATA_CASTER).map(EntityReference::getUUID).orElse(null);
	}

	private @Nullable LivingEntity findCaster(ServerLevel level) {
		UUID caster = this.casterUuid();
		if (caster == null) {
			return null;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(caster);
		return player != null ? player : level.getEntity(caster) instanceof LivingEntity living ? living : null;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PETALS, FateConfig.rhoAias().lowerPetals());
		builder.define(DATA_CAST_AT, 0L);
		builder.define(DATA_RADIUS, defaultRadius());
		builder.define(DATA_FOLLOWING, NOT_FOLLOWING);
		builder.define(DATA_DISTANCE, (float) FateConfig.rhoAias().defaultDistance());
		builder.define(DATA_CASTER, Optional.empty());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		UUID caster = this.casterUuid();
		if (caster != null) {
			output.store("caster", UUIDUtil.CODEC, caster);
		}
		output.putLong("cast_at", this.castTime());
		output.putFloat("radius", (float) this.radius());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(DATA_CASTER, input.read("caster", UUIDUtil.CODEC).map(EntityReference::<LivingEntity>of));
		this.entityData.set(DATA_PETALS, petalsAt(this.rank()));
		this.entityData.set(DATA_CAST_AT, input.getLongOr("cast_at", 0L));
		this.entityData.set(DATA_RADIUS, input.getFloatOr("radius", defaultRadius()));
	}
}
