package io.github.cwzmorro.fatecraft.weapon;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.fatecraft.FateConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Ea. Right-click and hold: compressed air, for anyone wielding it ({@link CompressedAir}). Gilgamesh's
 * Enuma Elish is an ability ({@link EnumaElish}). Its parts spin while either is used ({@link FateWeapons#SPIN}).
 */
public final class EaItem extends Item {
	private static final int USE_TICKS = 72_000;

	private final ReloadableMelee melee = new ReloadableMelee();

	public EaItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!CompressedAir.canAfford(player)) {
			return InteractionResult.FAIL;
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return USE_TICKS;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.TRIDENT;
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksLeft) {
		if (level instanceof ServerLevel serverLevel) {
			CompressedAir.tick(serverLevel, entity, USE_TICKS - ticksLeft);
		}
	}

	// An Ea with no owner (from the creative menu) becomes the first holder's. Of its two spins, the faster wins.
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
		if (entity instanceof ServerPlayer player && !stack.has(EaVault.OWNER)) {
			stack.set(EaVault.OWNER, player.getUUID());
		}
		EaConfig config = FateConfig.ea();
		this.melee.apply(stack, config.meleeDamage(), config.attackSpeed());
		float speed = 0.0F;
		if (entity instanceof LivingEntity holder) {
			if (holder.isUsingItem() && holder.getUseItem() == stack) {
				speed = (float) config.compressedAirSpinDegreesPerTick();
			}
			if (slot == EquipmentSlot.MAINHAND && Abilities.isActive(holder, EnumaElish.KEY.identifier())) {
				speed = Math.max(speed, (float) config.enumaElishSpinDegreesPerTick());
			}
		}
		WeaponSpin spin = stack.get(FateWeapons.SPIN);
		if (speed > 0 && (spin == null || spin.degreesPerTick() != speed)) {
			stack.set(FateWeapons.SPIN, new WeaponSpin(level.getGameTime(), speed));
		} else if (speed == 0 && spin != null) {
			stack.remove(FateWeapons.SPIN);
		}
	}
}
