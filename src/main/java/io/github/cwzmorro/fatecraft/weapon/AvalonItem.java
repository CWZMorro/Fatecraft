package io.github.cwzmorro.fatecraft.weapon;

import io.github.cwzmorro.fatecraft.artoria.Avalon;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/** Avalon, raised like a shield by a servant with the Avalon ability: free, and with no true name, sword, cooldown or time limit. */
public final class AvalonItem extends Item {
	public AvalonItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!Avalon.owns(player)) {
			return InteractionResult.FAIL;
		}
		player.startUsingItem(hand);
		if (!level.isClientSide()) {
			Avalon.begin(player);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return Integer.MAX_VALUE;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.BLOCK;
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksLeft) {
		if (!level.isClientSide()) {
			Avalon.tick(entity);
		}
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int ticksLeft) {
		if (!level.isClientSide()) {
			Avalon.end(entity);
		}
		return false;
	}
}
