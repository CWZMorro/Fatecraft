package io.github.cwzmorro.fatecraft.ubw;

import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.gate.GateThrows;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/** Which weapons Unlimited Blade Works can trace as craftable: any a live recipe outputs, plus the exception tag. */
public final class UbwCraftability {
	private static final TagKey<Item> EXCEPTIONS = TagKey.create(Registries.ITEM, Fatecraft.id("ubw_craftable_weapons"));

	private UbwCraftability() { }

	public static boolean craftable(ServerLevel level, ItemStack weapon) {
		if (!GateThrows.weapon(weapon)) {
			return false;
		}
		if (weapon.is(EXCEPTIONS)) {
			return true;
		}
		var context = SlotDisplayContext.fromLevel(level);
		for (var recipe : level.getServer().getRecipeManager().getRecipes()) {
			for (var display : recipe.value().display()) {
				for (ItemStack output : display.result().resolveForStacks(context)) {
					if (output.is(weapon.getItem())) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
