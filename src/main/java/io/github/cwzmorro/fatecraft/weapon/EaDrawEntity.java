package io.github.cwzmorro.fatecraft.weapon;

import io.github.cwzmorro.fatecraft.network.EaDrawPayload;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

/**
 * Ea being drawn: it rises slowly out of a golden gate in front of its owner, handle up ({@link EaDraw}), and
 * waits there until they right-click it, which puts it in their main hand. If its owner leaves, goes elsewhere or walks away, it
 * goes back behind the vault.
 */
public final class EaDrawEntity extends Entity {
	private static final EntityDataAccessor<ItemStack> DATA_EA = SynchedEntityData.defineId(EaDrawEntity.class, EntityDataSerializers.ITEM_STACK);
	private static final double OWNER_REACH = 32.0;

	private @Nullable UUID owner;

	public EaDrawEntity(EntityType<? extends EaDrawEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	static void draw(ServerLevel level, ServerPlayer player, ItemStack ea) {
		Vec3 ahead = player.getLookAngle().multiply(1, 0, 1).normalize().scale(EaDraw.AHEAD);
		EaDrawEntity entity = new EaDrawEntity(FateWeapons.EA_DRAW, level);
		entity.owner = player.getUUID();
		entity.entityData.set(DATA_EA, ea);
		entity.setPos(player.position().add(ahead));
		level.addFreshEntity(entity);
		EaDrawPayload payload = new EaDrawPayload(entity.position(), player.getYRot(), player.getId());
		for (ServerPlayer viewer : level.players()) {
			ServerPlayNetworking.send(viewer, payload);
		}
	}

	public ItemStack ea() {
		return this.entityData.get(DATA_EA);
	}

	public double risen(float partialTick) {
		return EaDraw.risen(this.tickCount + partialTick);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		Player owner = this.owner == null ? null : level.getPlayerByUUID(this.owner);
		if (owner == null || !owner.isAlive() || owner.distanceTo(this) > OWNER_REACH) {
			EaVault.returnToOwner(level.getServer(), this.ea());
			this.discard();
		}
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (this.level().isClientSide() || !player.getUUID().equals(this.owner) || this.risen(0) < 1) {
			return InteractionResult.PASS;
		}
		ItemStack before = player.getMainHandItem().copyAndClear();
		player.setItemInHand(InteractionHand.MAIN_HAND, this.ea());
		player.getInventory().placeItemBackInInventory(before);
		this.discard();
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean isPickable() {
		return true;
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
		builder.define(DATA_EA, ItemStack.EMPTY);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.storeNullable("owner", UUIDUtil.CODEC, this.owner);
		output.store("ea", ItemStack.OPTIONAL_CODEC, this.ea());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
		this.entityData.set(DATA_EA, input.read("ea", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
	}
}
