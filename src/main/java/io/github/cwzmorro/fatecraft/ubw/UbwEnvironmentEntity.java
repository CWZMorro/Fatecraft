package io.github.cwzmorro.fatecraft.ubw;

import io.github.cwzmorro.fatecraft.Fatecraft;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityDataRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/** A marble's sky, gears and fire, drawn only inside its sphere; synced to clients, never saved. */
public final class UbwEnvironmentEntity extends Entity {
	private static final EntityDataSerializer<Optional<UUID>> OPTIONAL_UUID =
		EntityDataSerializer.forValueType(ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC));

	private static final EntityDataSerializer<UbwOpening.Timing> OPENING_TIMING =
		EntityDataSerializer.forValueType(UbwOpening.Timing.STREAM_CODEC);

	static {
		FabricEntityDataRegistry.register(Fatecraft.id("ubw_optional_uuid"), OPTIONAL_UUID);
		FabricEntityDataRegistry.register(Fatecraft.id("ubw_opening_timing"), OPENING_TIMING);
	}

	private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(UbwEnvironmentEntity.class, OPTIONAL_UUID);
	private static final EntityDataAccessor<BlockPos> CENTER =
		SynchedEntityData.defineId(UbwEnvironmentEntity.class, EntityDataSerializers.BLOCK_POS);
	private static final EntityDataAccessor<Integer> RADIUS = SynchedEntityData.defineId(UbwEnvironmentEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> FLOOR = SynchedEntityData.defineId(UbwEnvironmentEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> ACTIVE =
		SynchedEntityData.defineId(UbwEnvironmentEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Long> OPENED = SynchedEntityData.defineId(UbwEnvironmentEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<UbwOpening.Timing> TIMING = SynchedEntityData.defineId(UbwEnvironmentEntity.class, OPENING_TIMING);
	// Relative to the centre: small, so a float vector keeps it exact far from the world's origin.
	private static final EntityDataAccessor<Vector3fc> ORIGIN =
		SynchedEntityData.defineId(UbwEnvironmentEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Integer> SHATTER_TICKS =
		SynchedEntityData.defineId(UbwEnvironmentEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> SHATTERED = SynchedEntityData.defineId(UbwEnvironmentEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Vector3fc> IMPACT =
		SynchedEntityData.defineId(UbwEnvironmentEntity.class, EntityDataSerializers.VECTOR3);
	private static final int EMBERS_PER_TICK = 6;
	private static final double EMBER_HEIGHT = 2.0;
	private static final double EMBER_RISE = 0.03;
	private static final int SAND_PER_TICK = 10;

	// Loads the class on both sides at startup, so the serializer ids never depend on when the first one spawns.
	static void init() { }

	public UbwEnvironmentEntity(EntityType<? extends UbwEnvironmentEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	static UbwEnvironmentEntity create(ServerLevel level, UUID owner, BlockPos center, int radius, int floorY, Vec3 origin, long openedAt,
		UbwOpening.Timing timing) {
		UbwEnvironmentEntity entity = new UbwEnvironmentEntity(Ubw.ENVIRONMENT, level);
		entity.entityData.set(OWNER, Optional.of(owner));
		entity.entityData.set(CENTER, center);
		entity.entityData.set(RADIUS, radius);
		entity.entityData.set(FLOOR, floorY);
		entity.entityData.set(ACTIVE, true);
		entity.entityData.set(OPENED, openedAt);
		entity.entityData.set(TIMING, timing);
		entity.entityData.set(ORIGIN, new Vector3f((float) (origin.x - center.getX()), 0, (float) (origin.z - center.getZ())));
		entity.setPos(center.getX(), center.getY(), center.getZ());
		level.addFreshEntity(entity);
		return entity;
	}

	public @Nullable UUID owner() {
		return this.entityData.get(OWNER).orElse(null);
	}

	public BlockPos center() {
		return this.entityData.get(CENTER);
	}

	public int radius() {
		return this.entityData.get(RADIUS);
	}

	public int floorY() {
		return this.entityData.get(FLOOR);
	}

	public boolean active() {
		return this.entityData.get(ACTIVE);
	}

	/** Returns the dimension game time the opening started, not an age, so a late viewer doesn't replay it. */
	public long openedAt() {
		return this.entityData.get(OPENED);
	}

	public UbwOpening.Timing openingTiming() {
		return this.entityData.get(TIMING);
	}

	void openedAt(long time) {
		this.entityData.set(OPENED, time);
	}

	public Vector3fc origin() {
		return this.entityData.get(ORIGIN);
	}

	public double span() {
		return UbwOpening.span(this.origin().x(), this.origin().z(), this.radius());
	}

	void closing() {
		this.entityData.set(ACTIVE, false);
	}

	void shatter(Vec3 impact, long time, int ticks) {
		this.entityData.set(IMPACT, new Vector3f((float) (impact.x - this.center().getX()), (float) (impact.y - this.center().getY()),
			(float) (impact.z - this.center().getZ())));
		this.entityData.set(SHATTERED, time);
		this.entityData.set(SHATTER_TICKS, ticks);
	}

	public boolean shattering() {
		return this.shatterTicks() > 0;
	}

	public int shatterTicks() {
		return this.entityData.get(SHATTER_TICKS);
	}

	public long shatteredAt() {
		return this.entityData.get(SHATTERED);
	}

	public Vec3 impact() {
		return new Vec3(this.entityData.get(IMPACT));
	}

	public double shatterSpan() {
		return UbwShatter.span(this.impact(), this.radius());
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide() || !this.active()) {
			return;
		}
		if (this.shattering()) {
			this.pourSand();
			return;
		}
		double age = this.level().getGameTime() - this.openedAt();
		UbwOpening.Timing timing = this.openingTiming();
		if (age < timing.ignitionTicks() || age >= timing.spreadTicks()) {
			return;
		}
		double reach = UbwOpening.reach(age, this.span(), timing);
		double floor = this.floorY() + 1;
		double rim = UbwOpening.floorRadius(this.radius(), floor - this.center().getY());
		for (int i = 0; i < EMBERS_PER_TICK; i++) {
			double angle = this.random.nextDouble() * Mth.TWO_PI;
			double x = this.origin().x() + Math.cos(angle) * reach;
			double z = this.origin().z() + Math.sin(angle) * reach;
			if (x * x + z * z > rim * rim) {
				continue;
			}
			this.level().addParticle(ParticleTypes.SMALL_FLAME, this.center().getX() + x, floor + this.random.nextDouble() * EMBER_HEIGHT,
				this.center().getZ() + z, 0, EMBER_RISE, 0);
		}
	}

	private void pourSand() {
		double fallen = UbwShatter.fall(this.level().getGameTime() - this.shatteredAt(), this.shatterTicks(), this.shatterSpan());
		Vec3 impact = this.impact();
		double floor = this.floorY() + 1;
		double height = floor - this.center().getY() - impact.y;
		if (fallen <= Math.abs(height)) {
			return;
		}
		double reach = Math.sqrt(fallen * fallen - height * height);
		double rim = UbwOpening.floorRadius(this.radius(), floor - this.center().getY());
		BlockParticleOption sand = new BlockParticleOption(ParticleTypes.FALLING_DUST, Ubw.GROUND.defaultBlockState());
		for (int i = 0; i < SAND_PER_TICK; i++) {
			double angle = this.random.nextDouble() * Mth.TWO_PI;
			double x = impact.x + Math.cos(angle) * reach;
			double z = impact.z + Math.sin(angle) * reach;
			if (x * x + z * z > rim * rim) {
				continue;
			}
			this.level().addParticle(sand, this.center().getX() + x, floor, this.center().getZ() + z, 0, 0, 0);
		}
	}

	@Override
	protected AABB makeBoundingBox(Vec3 position) {
		int radius = this.entityData == null ? 1 : this.radius();
		return new AABB(position.x - radius, position.y - radius, position.z - radius, position.x + radius, position.y + radius,
			position.z + radius);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (RADIUS.equals(accessor)) {
			this.setBoundingBox(this.makeBoundingBox());
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(OWNER, Optional.empty());
		builder.define(CENTER, BlockPos.ZERO);
		builder.define(RADIUS, 1);
		builder.define(FLOOR, 0);
		builder.define(ACTIVE, false);
		builder.define(OPENED, 0L);
		builder.define(TIMING, UbwConfig.DEFAULTS.opening());
		builder.define(ORIGIN, new Vector3f());
		builder.define(SHATTER_TICKS, 0);
		builder.define(SHATTERED, 0L);
		builder.define(IMPACT, new Vector3f());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) { }

	@Override
	protected void readAdditionalSaveData(ValueInput input) { }
}
