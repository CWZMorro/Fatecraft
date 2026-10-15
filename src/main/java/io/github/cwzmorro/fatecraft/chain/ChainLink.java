package io.github.cwzmorro.fatecraft.chain;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Where one of Enkidu's chains can be struck: a small box on the chain just off the bound target, within its
 * reach. Hits on it go to its bind's shared HP ({@link ChainBind#strikeChain}). Drawn by the bind; never saved.
 */
public final class ChainLink extends Entity {
	private @Nullable ChainBind bind;

	public ChainLink(EntityType<? extends ChainLink> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	static ChainLink of(ChainBind bind) {
		ChainLink link = new ChainLink(Chains.LINK, bind.level());
		link.bind = bind;
		return link;
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide() && (this.bind == null || this.bind.isRemoved())) {
			this.discard();
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return this.bind != null && this.bind.strikeChain(source.getEntity(), amount);
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}
}
