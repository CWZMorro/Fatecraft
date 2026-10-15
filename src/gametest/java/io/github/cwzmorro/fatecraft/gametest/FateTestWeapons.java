package io.github.cwzmorro.fatecraft.gametest;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

// Swords the test mod's tags list, so the tag rules are shown to work from the tag, not from Fatecraft's own weapons' ids.
public final class FateTestWeapons implements ModInitializer {
	static final Item DIVINE_BLADE = register("divine_blade");
	static final Item HAND_ONLY_BLADE = register("hand_only_blade");

	private static Item register(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, FateTests.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1)));
	}

	@Override
	public void onInitialize() {
	}
}
