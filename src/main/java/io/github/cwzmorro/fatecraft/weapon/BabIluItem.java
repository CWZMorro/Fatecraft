package io.github.cwzmorro.fatecraft.weapon;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.progression.TrueName;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.level.Level;

/**
 * The key (Bab-ilu), one of Gilgamesh's starting items, usable only by a servant with Enuma Elish. Right-click draws Ea out
 * of the vault ({@link EaDrawEntity}) once his true name is realized, or puts Ea back if it is in the other hand.
 */
public final class BabIluItem extends Item {
	private static final int RAISED_TICKS = EaDraw.KEY_LIGHT_END;

	public BabIluItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
			// Consume the click without a predicted swing: only the server may authorize the draw
			// and start the raised-key animation (true name realized, or creative).
			return InteractionResult.CONSUME;
		}
		if (!Abilities.owns(player, EnumaElish.KEY.identifier())) {
			player.sendOverlayMessage(Component.translatable("item.fatecraft.bab_ilu.gilgamesh_only"));
			return InteractionResult.FAIL;
		}
		InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		ItemStack held = player.getItemInHand(other);
		if (held.is(FateWeapons.EA)) {
			held.set(EaVault.OWNER, player.getUUID());
			EaVault.put(serverPlayer, held.copyAndClear());
			coolDown(player.getItemInHand(hand), player);
			return InteractionResult.SUCCESS;
		}
		if (!TrueName.allows(serverPlayer)) {
			player.sendOverlayMessage(Component.translatable("item.fatecraft.bab_ilu.true_name_first"));
			return InteractionResult.FAIL;
		}
		ItemStack ea = EaVault.take(serverPlayer);
		if (ea == null) {
			player.sendOverlayMessage(Component.translatable("item.fatecraft.bab_ilu.ea_drawn"));
			return InteractionResult.FAIL;
		}
		EaDrawEntity.draw(serverLevel, serverPlayer, ea);
		coolDown(player.getItemInHand(hand), player);
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	private static void coolDown(ItemStack key, Player player) {
		UseCooldown pearl = Items.ENDER_PEARL.components().get(DataComponents.USE_COOLDOWN);
		if (pearl != null) {
			pearl.apply(key, player);
		}
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return RAISED_TICKS;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.TRIDENT;
	}
}
