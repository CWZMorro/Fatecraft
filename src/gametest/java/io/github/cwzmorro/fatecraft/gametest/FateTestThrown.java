package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;

// Listed as counterable by the test mod's tag, so the counter is shown to work from the tag, not from the Gate's entity type.
public final class FateTestThrown implements ModInitializer {
	static final EntityType<ThrownWeaponEntity> COUNTERABLE = register();

	private static EntityType<ThrownWeaponEntity> register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, FateTests.id("counterable_thrown"));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, ThrownWeaponEntity.<ThrownWeaponEntity>type(ThrownWeaponEntity::new).build(key));
	}

	@Override
	public void onInitialize() {
	}
}
