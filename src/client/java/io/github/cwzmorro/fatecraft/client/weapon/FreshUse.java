package io.github.cwzmorro.fatecraft.client.weapon;

import io.github.cwzmorro.fatecraft.Fatecraft;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * An item of {@link #FRESH_USE} coming into the main hand while the use button is held (Ea, taken out of the vault with that
 * right-click) isn't used until the button is pressed again, since vanilla keeps using whatever is held while the button is
 * down. Checked before the tick's keys, after its packets.
 */
public final class FreshUse {
	private static final TagKey<Item> FRESH_USE = TagKey.create(Registries.ITEM, Fatecraft.id("fresh_use"));
	private static boolean heldFresh;

	private FreshUse() {
	}

	public static void register() {
		ClientTickEvents.START_CLIENT_TICK.register(FreshUse::tick);
	}

	private static void tick(Minecraft minecraft) {
		boolean fresh = minecraft.player != null && minecraft.player.getMainHandItem().is(FRESH_USE);
		if (fresh && !heldFresh && minecraft.options.keyUse.isDown()) {
			minecraft.options.keyUse.setDown(false);
		}
		heldFresh = fresh;
	}
}
