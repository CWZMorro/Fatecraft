package io.github.cwzmorro.fatecraft.client.vault;

import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * The vault screen's search, like JEI's: every space-separated word must match. A plain word is part of the item's name,
 * {@code @word} part of its mod's id, {@code #word} part of one of its tags.
 */
final class VaultSearch {
	private static final String MOD = "@";
	private static final String TAG = "#";

	private VaultSearch() {
	}

	static boolean matches(ItemStack stack, String query) {
		for (String word : query.toLowerCase(Locale.ROOT).split("\\s+")) {
			if (!word.isEmpty() && !matchesWord(stack, word)) {
				return false;
			}
		}
		return true;
	}

	private static boolean matchesWord(ItemStack stack, String word) {
		if (word.startsWith(MOD)) {
			return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().contains(word.substring(MOD.length()));
		}
		if (word.startsWith(TAG)) {
			String tag = word.substring(TAG.length());
			return stack.typeHolder().tags().anyMatch(key -> key.location().toString().contains(tag));
		}
		return stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(word);
	}
}
