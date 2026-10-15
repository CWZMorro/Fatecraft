package io.github.cwzmorro.fatecraft;

import io.github.cwzmorro.fatecraft.altera.Altera;
import io.github.cwzmorro.fatecraft.artoria.Artoria;
import io.github.cwzmorro.fatecraft.chain.Chains;
import io.github.cwzmorro.fatecraft.gate.Gates;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.shield.FateShields;
import io.github.cwzmorro.fatecraft.trace.Traces;
import io.github.cwzmorro.fatecraft.ubw.Ubw;
import io.github.cwzmorro.fatecraft.vault.Vaults;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entrypoint; each feature registers its own content and hooks. */
public final class Fatecraft implements ModInitializer {
	public static final String MOD_ID = "fatecraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static <T extends Entity> EntityType<T> entity(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	@Override
	public void onInitialize() {
		FateServants.init();
		FateWeapons.init();
		Traces.init();
		Vaults.init();
		Gates.init();
		Chains.init();
		FateShields.init();
		Altera.init();
		Artoria.init();
		FateGameRules.init();
		FateConfig.init();
		Ubw.init();
		LOGGER.info("Fatecraft loaded");
	}
}
