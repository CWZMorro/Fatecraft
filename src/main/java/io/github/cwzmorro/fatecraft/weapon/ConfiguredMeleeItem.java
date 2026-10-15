package io.github.cwzmorro.fatecraft.weapon;

import java.util.function.DoubleSupplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** A sword whose melee attributes follow the config: items built before a reload pick up the new numbers. */
abstract class ConfiguredMeleeItem extends Item {
	private final DoubleSupplier damage;
	private final DoubleSupplier speed;
	private final ReloadableMelee melee = new ReloadableMelee();

	ConfiguredMeleeItem(Properties properties, DoubleSupplier damage, DoubleSupplier speed) {
		super(properties);
		this.damage = damage;
		this.speed = speed;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		this.melee.apply(stack, this.damage.getAsDouble(), this.speed.getAsDouble());
	}
}
