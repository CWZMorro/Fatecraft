package io.github.cwzmorro.fatecraft.artoria;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.github.cwzmorro.alayacore.item.LostToVoid;
import io.github.cwzmorro.alayacore.loot.LootTableGroups;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.WorldData;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/** Lets one Avalon generate per world: the flag is claimed when the loot is generated, not on reload or when it is picked up. */
public final class AvalonLoot implements LootItemCondition {
	public static final AvalonLoot INSTANCE = new AvalonLoot();
	public static final MapCodec<AvalonLoot> CODEC = MapCodec.unit(INSTANCE);
	public static final SavedDataType<WorldData<Boolean>> GENERATED = WorldData.type("avalon_generated", Codec.BOOL, () -> false);
	public static final Identifier GROUP = Fatecraft.id("avalon");

	private AvalonLoot() {
	}

	public static boolean generated(MinecraftServer server) {
		return WorldData.of(server, GENERATED).get();
	}

	/** Claims the world's Avalon, returning false if it was already generated. Server thread only. */
	public static boolean claim(MinecraftServer server) {
		if (generated(server)) {
			return false;
		}
		WorldData.of(server, GENERATED).set(true);
		return true;
	}

	/** Lets Avalon generate again once a dropped one has fallen into the void ({@link LostToVoid}). */
	public static void lostToVoid(MinecraftServer server) {
		WorldData.of(server, GENERATED).set(false);
	}

	@Override
	public MapCodec<? extends LootItemCondition> codec() {
		return CODEC;
	}

	@Override
	public boolean test(LootContext context) {
		return context.getRandom().nextDouble() < FateConfig.artoria().avalonLootChance() && claim(context.getLevel().getServer());
	}

	public static void register() {
		Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE, Fatecraft.id("avalon_available"), CODEC);
		LostToVoid.register(FateWeapons.AVALON, (level, item) -> lostToVoid(level.getServer()));
		LootTableEvents.MODIFY.register((key, table, source, registries) -> {
			if (LootTableGroups.tables(registries, GROUP).contains(key)) {
				table.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
					.when(() -> INSTANCE).add(LootItem.lootTableItem(FateWeapons.AVALON)));
			}
		});
	}
}
