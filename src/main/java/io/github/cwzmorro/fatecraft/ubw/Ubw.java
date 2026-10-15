package io.github.cwzmorro.fatecraft.ubw;

import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.trace.Projections;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Registers Unlimited Blade Works: its blocks, entities, ability and server hooks. */
public final class Ubw {
	private static final float UNBREAKABLE = -1;
	private static final float BLAST_PROOF = 3_600_000;
	private static final long NO_TIMEOUT = 0;
	public static final Block GROUND = block("ubw_ground", BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_BROWN)
		.sound(SoundType.SAND)
		.strength(UNBREAKABLE, BLAST_PROOF)
		.noLootTable());
	// A full cube to collide with, but not occluding: the client hides its inner face inside the marble.
	public static final Block SHELL = block("ubw_shell", BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_BROWN)
		.strength(UNBREAKABLE, BLAST_PROOF)
		.noLootTable()
		.noOcclusion());
	private static final int TRACKING_RANGE = 32;
	private static final int UPDATE_INTERVAL = 1;
	public static final EntityType<UbwEnvironmentEntity> ENVIRONMENT = Fatecraft.entity("ubw_environment",
		EntityType.Builder.<UbwEnvironmentEntity>of(UbwEnvironmentEntity::new, MobCategory.MISC)
			.sized(1, 1)
			.noSave()
			.fireImmune()
			.clientTrackingRange(TRACKING_RANGE)
			.updateInterval(UPDATE_INTERVAL));
	public static final EntityType<UbwGroundSwordEntity> GROUND_SWORD = Fatecraft.entity("ubw_ground_sword",
		EntityType.Builder.<UbwGroundSwordEntity>of(UbwGroundSwordEntity::new, MobCategory.MISC)
			.sized(0.5F, 1.5F)
			.noSave()
			.fireImmune()
			.clientTrackingRange(TRACKING_RANGE)
			.updateInterval(UPDATE_INTERVAL));

	// Keeps a chunk loaded while a closed marble's terrain there waits to be restored; a restart rebuilds it from the journal.
	public static final TicketType TERRAIN_TICKET = Registry.register(BuiltInRegistries.TICKET_TYPE, Fatecraft.id("ubw_terrain"),
		new TicketType(NO_TIMEOUT, TicketType.FLAG_LOADING));

	private Ubw() { }

	public static void init() {
		UbwEnvironmentEntity.init();
		Registry.register(AlayaRegistries.ABILITY, UnlimitedBladeWorks.KEY, new UnlimitedBladeWorks());
		UbwRuntime.register();
		UbwSwords.register();
		Projections.waiveCostWhen(UbwMembership::freeProjection);
	}

	public static UbwLayout.Palette palette() {
		return new UbwLayout.Palette(SHELL.defaultBlockState(), GROUND.defaultBlockState(), Blocks.AIR.defaultBlockState());
	}

	private static Block block(String name, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Fatecraft.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, new Block(properties.setId(key)));
	}
}
