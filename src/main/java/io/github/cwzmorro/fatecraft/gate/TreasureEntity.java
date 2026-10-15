package io.github.cwzmorro.fatecraft.gate;

import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A weapon the Gate of Babylon threw, the real item out of the vault or a copy. A real one drops or sticks where it hits
 * and returns by itself ({@link GateReturns}); a copy vanishes where it hits or when its time is up.
 */
public final class TreasureEntity extends ThrownWeaponEntity {
	public TreasureEntity(EntityType<? extends TreasureEntity> type, Level level) {
		super(type, level);
		this.damageType(Gates.DAMAGE);
	}

	static TreasureEntity launch(ServerLevel level, ServerPlayer owner, ItemStack item, Vec3 from, Vec3 velocity, int section, boolean copy) {
		TreasureEntity treasure = new TreasureEntity(Gates.TREASURE, level);
		treasure.throwFrom(owner, item, from, velocity);
		GateReturns.throwing(level, treasure, owner, section, copy, item.copyWithCount(1));
		level.addFreshEntity(treasure);
		return treasure;
	}

	@Nullable Thrown thrown() {
		return this.getAttached(GateReturns.THROWN);
	}

	@Override
	public @Nullable UUID owner() {
		Thrown thrown = this.thrown();
		return thrown == null ? null : thrown.owner();
	}

	@Override
	protected boolean timeUp() {
		return GateReturns.timeUp(this);
	}

	@Override
	protected void expire(ServerLevel level) {
		GateReturns.sendBack(level, this);
	}

	@Override
	protected boolean kept() {
		Thrown thrown = this.thrown();
		return thrown != null && !thrown.copy();
	}

	@Override
	protected void landed(ServerLevel level) {
		GateReturns.landed(level, this);
	}

	@Override
	protected void forget(ServerLevel level) {
		GateReturns.forget(level, this);
	}

	@Override
	protected void itemChanged(ServerLevel level, ItemStack item) {
		GateReturns.restack(level, this, item);
	}

	// Anyone may pick it up; its owner sends it straight back to the vault, and what another player takes is theirs.
	@Override
	public void playerTouch(Player player) {
		Thrown thrown = this.thrown();
		if (!(this.level() instanceof ServerLevel level) || !this.stuck() || thrown == null || thrown.copy()) {
			return;
		}
		if (player.getUUID().equals(thrown.owner())) {
			player.take(this, 1);
			GateReturns.sendBack(level, this);
		} else if (player.getInventory().add(this.item().copy())) {
			player.take(this, 1);
			GateReturns.forget(level, this);
			this.discard();
		}
	}
}
