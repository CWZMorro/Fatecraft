package io.github.cwzmorro.fatecraft.vault;

import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.alayacore.ability.AbilityCast;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.fatecraft.Fatecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/** Gilgamesh's Vault: opens the vault screen. */
public final class VaultAbility extends Ability {
	public static final ResourceKey<Ability> KEY = ResourceKey.create(AlayaRegistries.ABILITY_KEY, Fatecraft.id("vault"));

	public VaultAbility() {
		super(new Ability.Properties());
	}

	@Override
	public void onStart(AbilityCast cast) {
		if (cast.entity() instanceof ServerPlayer player) {
			VaultMenu.open(player);
		}
		cast.end();
	}
}
