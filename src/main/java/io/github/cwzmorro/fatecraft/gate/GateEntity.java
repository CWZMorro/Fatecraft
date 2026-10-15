package io.github.cwzmorro.fatecraft.gate;

import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import java.util.UUID;
import net.minecraft.core.SectionPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One gate of the Gate of Babylon: a golden ripple with one item out of the vault half out of it, point first, until it
 * is thrown ({@link #launch}); then the gate closes. It belongs to one cast: if the cast ends first or the gate unloads,
 * the item goes back to the vault. Never saved, so nothing comes back twice.
 */
public final class GateEntity extends Entity {
	private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(GateEntity.class, EntityDataSerializers.ITEM_STACK);
	private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(GateEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> DATA_OPENS_AT = SynchedEntityData.defineId(GateEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_CLOSES_AT = SynchedEntityData.defineId(GateEntity.class, EntityDataSerializers.INT);
	public static final int OPEN_TICKS = 6;
	public static final int EMERGE_TICKS = 10;
	public static final int CLOSE_TICKS = 8;
	private static final int OPEN = -1;

	// Server only.
	private @Nullable UUID owner;
	private long session;
	private int section;
	private boolean copy;
	private boolean gone;
	private int slot = GateWall.NO_SLOT;
	// Client only, in blocks: keeps the gate's size once the item is thrown.
	private double drawnLength;
	private final InterpolationHandler interpolation = new InterpolationHandler(this);

	public GateEntity(EntityType<? extends GateEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	static GateEntity open(ServerLevel level, ServerPlayer owner, GateSession session, ItemStack item, Vec3 at, Vec3 facing, int delay, int slot) {
		GateEntity gate = new GateEntity(Gates.GATE, level);
		gate.owner = owner.getUUID();
		gate.session = session.id();
		gate.section = session.section();
		gate.copy = session.copy();
		gate.slot = slot;
		gate.entityData.set(DATA_ITEM, item);
		gate.entityData.set(DATA_SCALE, owner.getScale());
		gate.entityData.set(DATA_OPENS_AT, delay);
		gate.setPos(at);
		ThrownWeaponEntity.face(gate, facing);
		level.addFreshEntity(gate);
		return gate;
	}

	public ItemStack item() {
		return this.entityData.get(DATA_ITEM);
	}

	public float scale() {
		return this.entityData.get(DATA_SCALE);
	}

	public double drawnLength() {
		return this.drawnLength;
	}

	public void setDrawnLength(double length) {
		this.drawnLength = length;
	}

	public int opensAt() {
		return this.entityData.get(DATA_OPENS_AT);
	}

	public int closesAt() {
		return this.entityData.get(DATA_CLOSES_AT);
	}

	public Vec3 facing() {
		return Vec3.directionFromRotation(this.getXRot(), this.getYRot());
	}

	public @Nullable UUID owner() {
		return this.owner;
	}

	int slot() {
		return this.slot;
	}

	boolean ready() {
		return !this.gone && this.tickCount >= this.opensAt() + EMERGE_TICKS;
	}

	boolean gone() {
		return this.gone;
	}

	ItemStack launch(Vec3 direction) {
		ThrownWeaponEntity.face(this, direction);
		ItemStack item = this.item().copy();
		this.shut();
		return item;
	}

	Vec3 muzzle() {
		return this.position();
	}

	void close() {
		if (!this.gone && !this.copy && this.owner != null && this.level() instanceof ServerLevel level) {
			GateReturns.give(level.getServer(), this.owner, this.section, this.item());
		}
		this.shut();
	}

	private void shut() {
		this.gone = true;
		this.entityData.set(DATA_ITEM, ItemStack.EMPTY);
		this.entityData.set(DATA_CLOSES_AT, this.tickCount);
	}

	// Drawn out to the configured entity view distance, not the distance its tiny box would give.
	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		double range = this.renderRange();
		return distance < range * range;
	}

	public double renderRange() {
		return SectionPos.sectionToBlockCoord(this.getType().clientTrackingRange()) * Entity.getViewScale();
	}

	@Override
	public InterpolationHandler getInterpolation() {
		return this.interpolation;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			this.interpolation.interpolate();
		}
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.gone) {
			if (this.tickCount - this.closesAt() >= CLOSE_TICKS) {
				this.discard();
			}
		} else if (!this.castGoesOn(level)) {
			this.close();
		}
	}

	private boolean castGoesOn(ServerLevel level) {
		ServerPlayer owner = this.owner == null ? null : level.getServer().getPlayerList().getPlayer(this.owner);
		GateSession session = owner == null ? null : owner.getAttached(GateOfBabylon.SESSION);
		return session != null && session.id() == this.session;
	}

	@Override
	public void remove(RemovalReason reason) {
		// Unloaded with the item still in it: it goes back rather than being lost.
		if (!this.gone) {
			this.close();
		}
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
		builder.define(DATA_ITEM, ItemStack.EMPTY);
		builder.define(DATA_SCALE, 1.0F);
		builder.define(DATA_OPENS_AT, 0);
		builder.define(DATA_CLOSES_AT, OPEN);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}
}
