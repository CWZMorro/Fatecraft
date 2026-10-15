package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;
import static io.github.cwzmorro.fatecraft.gametest.UbwTests.PALETTE;
import static io.github.cwzmorro.fatecraft.gametest.UbwTests.prepare;
import static io.github.cwzmorro.fatecraft.gametest.UbwTests.rejected;

import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.ubw.UbwConfig;
import io.github.cwzmorro.fatecraft.ubw.UbwLayout;
import io.github.cwzmorro.fatecraft.ubw.UbwTerrain;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SculkCatalystBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class UbwTerrainGameTests {
	private static final UbwConfig SMALL = new UbwConfig(2, UbwConfig.DEFAULTS.openingMana(), UbwConfig.DEFAULTS.upkeepManaPerSecond());

	private static BlockPos center(GameTestHelper helper) {
		return helper.absolutePos(new BlockPos(4, 4, 4));
	}

	private static void set(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, UbwTests.QUIET_WRITE);
	}

	private static UUID construct(UbwTerrain terrain, ServerLevel level, BlockPos center) throws IOException {
		return terrain.construct(level, center, SMALL, center.getY() - 1, 1, PALETTE);
	}

	@GameTest(environment = "fatecraft-gametest:isolated/terrain_bounds")
	public void sphericalBoundsAnchoredFloorAndStates(GameTestHelper helper) throws IOException {
		ServerLevel level = helper.getLevel();
		BlockPos center = center(helper);
		prepare(level, center, 4);
		UbwTerrain terrain = UbwTerrain.of(level.getServer());
		check(helper, "default radius", UbwConfig.DEFAULTS.radius() == 25 && FateConfig.ubw().radius() == 25);
		UbwConfig config = new UbwConfig(4, UbwConfig.DEFAULTS.openingMana(), UbwConfig.DEFAULTS.upkeepManaPerSecond());
		Map<BlockPos, BlockState> layout = UbwLayout.sphere(level, center, config, center.getY() + 1, 1, PALETTE);
		BlockPos floor = center.above();
		BlockPos deeper = center.below();
		BlockPos outside = center.offset(4, 1, 0);
		BlockPos fluid = center.above(2);
		BlockState waterlogged = Blocks.OAK_STAIRS.defaultBlockState()
			.setValue(BlockStateProperties.WATERLOGGED, true)
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
		set(level, deeper, Blocks.DIAMOND_BLOCK.defaultBlockState());
		set(level, outside, Blocks.EMERALD_BLOCK.defaultBlockState());
		set(level, fluid, waterlogged);
		for (BlockPos pos : layout.keySet()) {
			long dx = pos.getX() - center.getX();
			long dy = pos.getY() - center.getY();
			long dz = pos.getZ() - center.getZ();
			check(helper, "all voxels bounded", dx * dx + dy * dy + dz * dz <= 16 && !level.isOutsideBuildHeight(pos));
		}
		UUID owner = terrain.construct(level, center, config, floor.getY(), 1, PALETTE);
		check(helper, "caller-anchored floor", level.getBlockState(floor).is(Blocks.DIRT));
		check(helper, "spherical shell", level.getBlockState(center.offset(4, 0, 0)).is(Blocks.GLASS));
		check(helper, "interior cleared", level.getBlockState(fluid).isAir());
		check(helper, "deeper original preserved", level.getBlockState(deeper).is(Blocks.DIAMOND_BLOCK));
		check(helper, "outside sphere unchanged", level.getBlockState(outside).is(Blocks.EMERALD_BLOCK));
		check(helper, "restored", terrain.restore(owner));
		check(helper, "waterlogged orientation restored", level.getBlockState(fluid).equals(waterlogged));
		check(helper, "idempotent restore", terrain.restore(owner));
		BlockPos ceiling = new BlockPos(center.getX(), level.getMaxY() - 1, center.getZ());
		UUID clipped = terrain.construct(level, ceiling, SMALL, ceiling.getY() - 1, 1, PALETTE);
		check(helper, "real ceiling shell constructed", level.getBlockState(ceiling.offset(2, 0, 0)).is(Blocks.GLASS));
		check(helper, "ceiling clipped and restored", terrain.restore(clipped));
		BlockPos bottom = new BlockPos(center.getX(), level.getMinY(), center.getZ());
		UUID clippedBottom = terrain.construct(level, bottom, SMALL, bottom.getY(), 1, PALETTE);
		check(helper, "real minimum floor", level.getBlockState(bottom).is(Blocks.DIRT));
		check(helper, "minimum clipped and restored", terrain.restore(clippedBottom));
		helper.succeed();
	}

	@GameTest(environment = "fatecraft-gametest:isolated/terrain_inventory")
	public void inventorySignNoDropsAndRepeatedRecovery(GameTestHelper helper) throws IOException {
		ServerLevel level = helper.getLevel();
		BlockPos center = center(helper);
		prepare(level, center, 2);
		BlockPos signPos = center.east();
		set(level, center, Blocks.CHEST.defaultBlockState());
		ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(center);
		ItemStack stack = new ItemStack(Items.DIAMOND, 17);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("journal-owned diamonds"));
		chest.setItem(0, stack);
		chest.setComponents(DataComponentMap.builder().set(DataComponents.CUSTOM_NAME, Component.literal("original chest")).build());
		set(level, signPos, Blocks.OAK_SIGN.defaultBlockState());
		SignBlockEntity sign = (SignBlockEntity) level.getBlockEntity(signPos);
		sign.setText(sign.getFrontText().setMessage(0, Component.literal("UBW front")).setHasGlowingText(true).setColor(DyeColor.BLUE), true);
		sign.setText(sign.getBackText().setMessage(2, Component.literal("UBW back")), false);
		sign.setWaxed(true);
		CompoundTag chestTag = chest.saveWithFullMetadata(level.registryAccess()).copy();
		CompoundTag signTag = sign.saveWithFullMetadata(level.registryAccess()).copy();
		AABB area = new AABB(center).inflate(4);
		int drops = level.getEntitiesOfClass(ItemEntity.class, area).size();
		UbwTerrain terrain = UbwTerrain.of(level.getServer());
		UUID owner = construct(terrain, level, center);
		check(helper, "container removed without drops",
			level.getBlockEntity(center) == null && level.getEntitiesOfClass(ItemEntity.class, area).size() == drops);
		terrain.reload();
		check(helper, "disk journal owns operation", terrain.pending().contains(owner));
		check(helper, "explicit reload recovery", terrain.recover().isEmpty());
		check(helper, "complete chest NBT", chestTag.equals(level.getBlockEntity(center).saveWithFullMetadata(level.registryAccess())));
		check(helper, "complete two-sided waxed sign NBT", signTag.equals(level.getBlockEntity(signPos).saveWithFullMetadata(level.registryAccess())));
		check(helper, "repeated recovery empty", terrain.recover().isEmpty() && terrain.restore(owner));
		check(helper, "no duplication in chest", ((ChestBlockEntity) level.getBlockEntity(center)).getItem(0).getCount() == 17);
		helper.runAfterDelay(2, () -> {
			check(helper, "no delayed replacement/restoration drops", level.getEntitiesOfClass(ItemEntity.class, area).size() == drops);
			helper.succeed();
		});
	}

	@GameTest(environment = "fatecraft-gametest:isolated/terrain_dimensions")
	public void dimensionOwnershipAndOverlapRejection(GameTestHelper helper) throws IOException {
		ServerLevel level = helper.getLevel();
		ServerLevel other = level.getServer().getLevel(Level.NETHER);
		BlockPos fixture = center(helper);
		BlockPos center = new BlockPos(fixture.getX(), Math.max(level.getMinY(), other.getMinY()) + 4, fixture.getZ());
		prepare(level, center, 2);
		prepare(other, center, 2);
		set(level, center, Blocks.GOLD_BLOCK.defaultBlockState());
		set(other, center, Blocks.IRON_BLOCK.defaultBlockState());
		UbwTerrain terrain = UbwTerrain.of(level.getServer());
		UUID first = construct(terrain, level, center);
		byte[] before = Files.readAllBytes(terrain.checkpointPath());
		rejected(helper, "overlap rejected", () -> construct(terrain, level, center.east()));
		check(helper, "overlap no disk mutation", Arrays.equals(before, Files.readAllBytes(terrain.checkpointPath())));
		check(helper, "overlap no block mutation", level.getBlockState(center).isAir());
		UUID second = construct(terrain, other, center);
		check(helper, "unique owners", !first.equals(second));
		check(helper, "foreign UUID harmless", terrain.restore(UUID.randomUUID()) && level.getBlockState(center).isAir());
		check(helper, "first restore", terrain.restore(first) && level.getBlockState(center).is(Blocks.GOLD_BLOCK));
		check(helper, "other dimension still owned", other.getBlockState(center).isAir() && terrain.pending().contains(second));
		check(helper, "second restore", terrain.restore(second) && other.getBlockState(center).is(Blocks.IRON_BLOCK));
		helper.succeed();
	}

	@GameTest(environment = "fatecraft-gametest:isolated/terrain_checkpoint")
	public void checkpointFailuresFailClosedAndRetirementReplays(GameTestHelper helper) throws IOException {
		ServerLevel level = helper.getLevel();
		BlockPos center = center(helper);
		prepare(level, center, 2);
		UbwTerrain terrain = UbwTerrain.of(level.getServer());
		UUID initialize = construct(terrain, level, center);
		terrain.restore(initialize);
		set(level, center, Blocks.GOLD_BLOCK.defaultBlockState());
		Path path = terrain.checkpointPath();
		Path pending = path.resolveSibling(path.getFileName() + ".pending");
		byte[] clean = Files.readAllBytes(path);
		Files.createDirectory(pending);
		try {
			rejected(helper, "pre-mutation save rejected", () -> construct(terrain, level, center));
			check(helper, "pre-mutation original intact", level.getBlockState(center).is(Blocks.GOLD_BLOCK));
			rejected(helper, "failed save closes authority", terrain::pending);
			rejected(helper, "interrupted-write evidence closes reload", terrain::reload);
		} finally {
			Files.delete(pending);
		}
		terrain.reload();
		Files.write(path, new byte[] {1, 2, 3});
		try {
			rejected(helper, "corrupt reload rejected", terrain::reload);
			rejected(helper, "corrupt authority cannot mutate", () -> construct(terrain, level, center));
			check(helper, "corrupt evidence retained", Files.size(path) == 3);
		} finally {
			Files.write(path, clean);
			terrain.reload();
		}
		UUID owner = construct(terrain, level, center);
		Files.createDirectory(pending);
		try {
			rejected(helper, "failed retirement reported", () -> terrain.restore(owner));
			check(helper, "terrain restored before retirement", level.getBlockState(center).is(Blocks.GOLD_BLOCK));
		} finally {
			Files.delete(pending);
			terrain.reload();
		}
		check(helper, "failed retirement journal retained", terrain.pending().contains(owner));
		check(helper, "failed retirement replay is idempotent", terrain.recover().isEmpty() && level.getBlockState(center).is(Blocks.GOLD_BLOCK));
		helper.succeed();
	}

	private record StateDamage(String name, Consumer<CompoundTag> damage) { }

	@GameTest(environment = "fatecraft-gametest:isolated/terrain_corruption")
	public void damagedPersistedStatesRejectWithoutMutation(GameTestHelper helper) throws IOException {
		ServerLevel level = helper.getLevel();
		BlockPos center = center(helper);
		prepare(level, center, 2);
		BlockState state = Blocks.OAK_STAIRS.defaultBlockState()
			.setValue(BlockStateProperties.WATERLOGGED, true)
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
		set(level, center, state);
		UbwTerrain terrain = UbwTerrain.of(level.getServer());
		UUID owner = construct(terrain, level, center);
		Path path = terrain.checkpointPath();
		byte[] pristine = Files.readAllBytes(path);
		var damages = List.of(
			new StateDamage("missing facing", tag -> tag.getCompoundOrEmpty("Properties").remove("facing")),
			new StateDamage("missing waterlogged", tag -> tag.getCompoundOrEmpty("Properties").remove("waterlogged")),
			new StateDamage("missing Properties", tag -> tag.remove("Properties")),
			new StateDamage("invalid facing", tag -> tag.getCompoundOrEmpty("Properties").putString("facing", "sideways")),
			new StateDamage("invalid waterlogged", tag -> tag.getCompoundOrEmpty("Properties").putString("waterlogged", "maybe")),
			new StateDamage("wrong property type", tag -> tag.getCompoundOrEmpty("Properties").putInt("waterlogged", 1)),
			new StateDamage("malformed Properties", tag -> tag.putString("Properties", "damaged")),
			new StateDamage("unknown property", tag -> tag.getCompoundOrEmpty("Properties").putString("unknown", "true")),
			new StateDamage("unknown block ID", tag -> tag.putString("Name", "fatecraft:missing_original_block")),
			new StateDamage("missing block ID", tag -> tag.remove("Name")),
			new StateDamage("malformed block ID", tag -> tag.putInt("Name", 1)),
			new StateDamage("malformed state", tag -> {
				tag.remove("Name");
				tag.remove("Properties");
			})
		);
		try {
			for (StateDamage damage : damages) {
				CompoundTag journal = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
				CompoundTag operation = (CompoundTag) journal.getListOrEmpty("operations").get(0);
				CompoundTag savedState = null;
				for (var original : operation.getListOrEmpty("originals")) {
					CompoundTag candidate = ((CompoundTag) original).getCompoundOrEmpty("state");
					if (candidate.getStringOr("Name", "").equals("minecraft:oak_stairs")) {
						savedState = candidate;
					}
				}
				check(helper, "real persisted stair fixture", savedState != null);
				damage.damage().accept(savedState);
				NbtIo.writeCompressed(journal, path);
				byte[] damaged = Files.readAllBytes(path);
				try {
					rejected(helper, damage.name() + " reload rejected", terrain::reload);
					rejected(helper, damage.name() + " restore blocked", () -> terrain.restore(owner));
					rejected(helper, damage.name() + " construction blocked", () -> construct(terrain, level, center));
					check(helper, damage.name() + " terrain unchanged", level.getBlockState(center).isAir());
					check(helper, damage.name() + " evidence unchanged", Arrays.equals(damaged, Files.readAllBytes(path)));
				} finally {
					Files.write(path, pristine);
					terrain.reload();
				}
			}
		} finally {
			Files.write(path, pristine);
			terrain.reload();
			terrain.restore(owner);
		}
		check(helper, "pristine state restored losslessly", level.getBlockState(center).equals(state));
		helper.succeed();
	}

	private static List<GameEventListener> catalystListeners(ServerLevel level, BlockPos pos) {
		List<GameEventListener> listeners = new ArrayList<>();
		level.getChunkAt(pos).getListenerRegistry(SectionPos.blockToSectionCoord(pos.getY())).visitInRangeListeners(
			GameEvent.ENTITY_DIE, Vec3.atCenterOf(pos), GameEvent.Context.of(Blocks.SCULK_CATALYST.defaultBlockState()),
			(listener, source) -> {
				if (listener instanceof SculkCatalystBlockEntity.CatalystListener && BlockPos.containing(source).equals(pos)) {
					listeners.add(listener);
				}
			});
		return listeners;
	}

	private static int catalystCharge(SculkCatalystBlockEntity catalyst) {
		return catalyst.getListener().getSculkSpreader().getCursors().stream().mapToInt(cursor -> cursor.getCharge()).sum();
	}

	private static Zombie deathSource(GameTestHelper helper, BlockPos pos) {
		ServerLevel level = helper.getLevel();
		var zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.TRIGGERED);
		zombie.setPos(Vec3.atCenterOf(pos));
		check(helper, "adult death source offers XP",
			zombie.shouldDropExperience() && zombie.getExperienceReward(level, null) > 0 && !zombie.wasExperienceConsumed());
		return zombie;
	}

	@GameTest(environment = "fatecraft-gametest:isolated/terrain_catalyst")
	public void catalystListenersRestorationRecoveryAndNormalRemoval(GameTestHelper helper) throws IOException {
		ServerLevel level = helper.getLevel();
		BlockPos center = center(helper);
		prepare(level, center, 2);
		UbwTerrain terrain = UbwTerrain.of(level.getServer());
		Path path = terrain.checkpointPath();
		Path pending = path.resolveSibling(path.getFileName() + ".pending");
		set(level, center, Blocks.SCULK_CATALYST.defaultBlockState());
		var original = (SculkCatalystBlockEntity) level.getBlockEntity(center);
		original.getListener().getSculkSpreader().addCursors(center.east(), 37);
		check(helper, "fixture has one real listener", catalystListeners(level, center).equals(List.of(original.getListener())));
		for (int round = 0; round < 3; round++) {
			CompoundTag metadata = level.getBlockEntity(center).saveWithFullMetadata(level.registryAccess()).copy();
			UUID owner = construct(terrain, level, center);
			try {
				check(helper, "construction unregisters original listener", catalystListeners(level, center).isEmpty());
				if (round == 0) {
					check(helper, "direct catalyst restoration", terrain.restore(owner));
				} else if (round == 1) {
					terrain.reload();
					check(helper, "catalyst persisted recovery", terrain.recover().isEmpty());
				} else {
					Files.createDirectory(pending);
					try {
						rejected(helper, "catalyst failed retirement", () -> terrain.restore(owner));
					} finally {
						Files.delete(pending);
					}
					terrain.reload();
					check(helper, "catalyst repeated restoration replay", terrain.pending().contains(owner) && terrain.recover().isEmpty());
				}
			} finally {
				terrain.restore(owner);
			}
			var restored = (SculkCatalystBlockEntity) level.getBlockEntity(center);
			check(helper, "complete catalyst snapshot restored", metadata.equals(restored.saveWithFullMetadata(level.registryAccess())));
			check(helper, "only authoritative catalyst listener registered", catalystListeners(level, center).equals(List.of(restored.getListener())));
			var positive = deathSource(helper, center.east());
			int charge = catalystCharge(restored);
			level.gameEvent(GameEvent.ENTITY_DIE, positive.position(), GameEvent.Context.of(positive));
			check(helper, "real authoritative listener consumes death XP", positive.wasExperienceConsumed() && catalystCharge(restored) > charge);
			check(helper, "real death event blooms catalyst", level.getBlockState(center).getValue(BlockStateProperties.BLOOM));
		}
		var removed = (SculkCatalystBlockEntity) level.getBlockEntity(center);
		int charge = catalystCharge(removed);
		check(helper, "ordinary catalyst removal", level.removeBlock(center, false));
		check(helper, "ordinary removal leaves no listener", catalystListeners(level, center).isEmpty());
		var negative = deathSource(helper, center.east());
		level.gameEvent(GameEvent.ENTITY_DIE, negative.position(), GameEvent.Context.of(negative));
		check(helper, "no ghost XP consumption", !negative.wasExperienceConsumed() && catalystCharge(removed) == charge);
		check(helper, "no catalyst resurrection", level.getBlockState(center).isAir() && level.getBlockEntity(center) == null);
		helper.runAfterDelay(10, () -> {
			check(helper, "no delayed ghost reaction", !negative.wasExperienceConsumed() && level.getBlockState(center).isAir()
				&& level.getBlockEntity(center) == null && catalystListeners(level, center).isEmpty());
			helper.succeed();
		});
	}

	@GameTest(environment = "fatecraft-gametest:isolated/terrain_unavailable")
	public void unavailablePositionsAndPendingChunkSaveOrdering(GameTestHelper helper) throws IOException {
		ServerLevel level = helper.getLevel();
		BlockPos center = center(helper);
		prepare(level, center, 2);
		UbwTerrain terrain = UbwTerrain.of(level.getServer());
		BlockPos unavailable = new BlockPos(20_000_000, center.getY(), 20_000_000);
		check(helper, "fixture chunk actually unavailable", level.getChunkSource().getChunkNow(unavailable.getX() >> 4, unavailable.getZ() >> 4) == null);
		rejected(helper, "unavailable construction rejected", () -> construct(terrain, level, unavailable));
		set(level, center, Blocks.GOLD_BLOCK.defaultBlockState());
		UUID owner = construct(terrain, level, center);
		Path path = terrain.checkpointPath();
		byte[] real = Files.readAllBytes(path);
		CompoundTag journal = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
		CompoundTag operation = (CompoundTag) journal.getListOrEmpty("operations").get(0);
		CompoundTag original = (CompoundTag) operation.getListOrEmpty("originals").get(0);
		// A journalled position in a chunk that doesn't exist yet: restoring must neither generate nor erase it.
		original.putIntArray("pos", new int[] {unavailable.getX(), unavailable.getY(), unavailable.getZ()});
		NbtIo.writeCompressed(journal, path);
		try {
			terrain.reload();
			check(helper, "unavailable restoration retained", !terrain.restore(owner) && terrain.pending().contains(owner));
			check(helper, "loaded positions restored without waiting for the unavailable one", level.getBlockState(center).is(Blocks.GOLD_BLOCK));
			check(helper, "recovery still pending", terrain.recover().contains(owner));
			check(helper, "pending part waits on its own chunk only",
				terrain.waiting(level.dimension(), ChunkPos.containing(unavailable)).equals(Set.of(owner))
					&& terrain.waiting(level.dimension(), ChunkPos.containing(center)).isEmpty());
			check(helper, "no forced chunk generation", level.getChunkSource().getChunkNow(unavailable.getX() >> 4, unavailable.getZ() >> 4) == null);
		} finally {
			Files.write(path, real);
			terrain.reload();
		}
		var chunk = level.getChunkAt(center);
		var storage = level.getChunkSource().chunkMap;
		CompoundTag older = SerializableChunkData.copyOf(level, chunk).write();
		var serialization = CompletableFuture.supplyAsync(() -> older);
		// Queued the way ChunkMap.save queues a write: a supplier that waits on async serialization.
		var queued = storage.write(chunk.getPos(), () -> serialization.join());
		check(helper, "restore after old queued save", terrain.restore(owner));
		queued.join();
		storage.synchronize(true).join();
		CompoundTag persisted = storage.read(chunk.getPos()).join().orElseThrow();
		var data = SerializableChunkData.parse(level, level.palettedContainerFactory(), persisted);
		var readChunk = data.read(level, level.getPoiManager(), storage.storageInfo(), chunk.getPos());
		check(helper, "disk contains restored state not old queued terrain", readChunk.getBlockState(center).is(Blocks.GOLD_BLOCK));
		terrain.reload();
		check(helper, "durable successful retirement", !terrain.pending().contains(owner));
		helper.succeed();
	}
}
