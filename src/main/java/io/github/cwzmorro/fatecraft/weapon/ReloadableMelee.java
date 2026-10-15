package io.github.cwzmorro.fatecraft.weapon;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** A weapon's melee attributes kept in step with the config: items built before a reload pick up the new numbers. */
final class ReloadableMelee {
	private double damage = Double.NaN;
	private double speed = Double.NaN;
	private ItemAttributeModifiers attributes = ItemAttributeModifiers.EMPTY;

	void apply(ItemStack stack, double currentDamage, double currentSpeed) {
		if (this.damage != currentDamage || this.speed != currentSpeed) {
			this.damage = currentDamage;
			this.speed = currentSpeed;
			this.attributes = FateWeapons.melee(currentDamage, currentSpeed);
		}
		if (!this.attributes.equals(stack.get(DataComponents.ATTRIBUTE_MODIFIERS))) {
			stack.set(DataComponents.ATTRIBUTE_MODIFIERS, this.attributes);
		}
	}
}
