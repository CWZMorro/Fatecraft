package io.github.cwzmorro.fatecraft.ubw;

import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** A sword standing in a marble's ground, which can be pulled out once; never saved or returned to a vault. */
public final class UbwGroundSwordEntity extends Entity {
	private static final EntityDataAccessor<ItemStack> ITEM =
		SynchedEntityData.defineId(UbwGroundSwordEntity.class, EntityDataSerializers.ITEM_STACK);
	private static final float POINT_DOWN = 90;
	private @Nullable UUID owner;
	private @Nullable UUID operation;
	private boolean pulled;

	public UbwGroundSwordEntity(EntityType<? extends UbwGroundSwordEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	static UbwGroundSwordEntity create(ServerLevel level, UUID owner, UUID operation, ItemStack sword, Vec3 point, float yaw, float lean) {
		UbwGroundSwordEntity entity = new UbwGroundSwordEntity(Ubw.GROUND_SWORD, level);
		entity.owner = owner;
		entity.operation = operation;
		entity.entityData.set(ITEM, sword.copyWithCount(1));
		entity.setPos(point);
		entity.setYRot(yaw);
		entity.setXRot(POINT_DOWN - lean);
		level.addFreshEntity(entity);
		return entity;
	}

	public ItemStack item() {
		return this.entityData.get(ITEM);
	}

	public float lean(float partialTick) {
		return POINT_DOWN - this.getXRot(partialTick);
	}

	public float scale() {
		return 1.0F;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (this.level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (this.pulled || this.isRemoved() || this.operation == null || !(this.level() instanceof ServerLevel level)
			|| !UbwRuntime.pullable(level.getServer(), this.owner, this.operation)) {
			return InteractionResult.PASS;
		}
		ItemStack projected = this.item().copyWithCount(1);
		// A projection never stacks, so add takes it whole or not at all. The free slot is checked first because in creative,
		// add reports success while deleting an item that doesn't fit.
		if (player.getInventory().getFreeSlot() < 0 || !player.getInventory().add(projected)) {
			return InteractionResult.PASS;
		}
		this.pulled = true;
		player.take(this, 1);
		this.discard();
		return InteractionResult.SUCCESS;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level && !UbwRuntime.ownsGround(level.getServer(), this.owner, this.operation)) {
			this.discard();
		}
	}

	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(ITEM, ItemStack.EMPTY);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) { }

	@Override
	protected void readAdditionalSaveData(ValueInput input) { }
}
