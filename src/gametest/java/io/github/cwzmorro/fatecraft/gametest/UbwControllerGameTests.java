package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;
import static io.github.cwzmorro.fatecraft.gametest.UbwTests.PALETTE;
import static io.github.cwzmorro.fatecraft.gametest.UbwTests.QUIET_WRITE;
import static io.github.cwzmorro.fatecraft.gametest.UbwTests.prepare;
import static io.github.cwzmorro.fatecraft.gametest.UbwTests.rejected;

import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.ubw.UbwConfig;
import io.github.cwzmorro.fatecraft.ubw.UbwController;
import io.github.cwzmorro.fatecraft.ubw.UbwTerrain;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class UbwControllerGameTests {
	private static final UbwConfig SMALL = new UbwConfig(3, UbwConfig.DEFAULTS.openingMana(), UbwConfig.DEFAULTS.upkeepManaPerSecond());

	private static Runnable checked(UbwTests.Checked action) {
		return () -> {
			try {
				action.run();
			} catch (IOException failure) {
				throw new RuntimeException(failure);
			}
		};
	}

	// Idle so a few-tick mana fixture falls between two of Alaya Core's regenerations, which run every ten ticks.
	private static int betweenRegens(GameTestHelper helper) {
		return (11 - helper.getLevel().getServer().getTickCount() % 10) % 10;
	}

	private static ServerPlayer caster(GameTestHelper helper, double mana) {
		ServerPlayer player = FateTests.human(helper, new BlockPos(4, 4, 4));
		player.setGameMode(GameType.SURVIVAL);
		AlayaMana.getOrCreate(player);
		AlayaMana.addMax(player, mana);
		AlayaMana.update(player, data -> data.withCurrent(mana));
		// Non-boundary sub-block coordinates make the expected floor discretization unambiguous.
		player.setPos(Vec3.atLowerCornerOf(player.blockPosition()).add(0.25, 0.25, 0.25));
		player.setYRot(0.0F);
		player.setXRot(-90.0F);
		prepare(helper.getLevel(), UbwController.placement(player, SMALL), SMALL.radius());
		return player;
	}

	private static UbwController.OpenResult open(UbwController controller, ServerPlayer player, UbwConfig config) throws IOException {
		return controller.open(player, config, player.blockPosition().getY(), 1, PALETTE);
	}

	private static double mana(ServerPlayer player) {
		return AlayaMana.getOrCreate(player).current();
	}

	@GameTest(environment = "fatecraft-gametest:isolated/controller_funding")
	public void openingFundingAndPreflightNeverSpend(GameTestHelper helper) throws IOException {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = caster(helper, SMALL.openingMana() - 1);
		UbwController controller = UbwController.of(level.getServer());
		UbwTerrain terrain = UbwTerrain.of(level.getServer());
		BlockPos center = UbwController.placement(player, SMALL);
		level.setBlock(center, Blocks.GOLD_BLOCK.defaultBlockState(), QUIET_WRITE);
		check(helper, "default and registered config values", UbwConfig.DEFAULTS.radius() == 25 && FateConfig.ubw().openingMana() == 3_000
			&& FateConfig.ubw().upkeepManaPerSecond() == 25);
		double before = mana(player);
		check(helper, "insufficient opening result", open(controller, player, SMALL) == UbwController.OpenResult.INSUFFICIENT_MANA);
		check(helper, "insufficient no spend/mutation/ownership",
			mana(player) == before && level.getBlockState(center).is(Blocks.GOLD_BLOCK) && controller.status(player.getUUID()).isEmpty());
		AlayaMana.addCurrent(player, 1);
		before = mana(player);
		UUID existing = terrain.construct(level, center, SMALL, center.getY(), 1, PALETTE);
		try {
			rejected(helper, "real ownership overlap rejected", () -> open(controller, player, SMALL));
			check(helper, "overlap no spend/no controller operation",
				mana(player) == before && controller.status(player.getUUID()).isEmpty() && terrain.pending().contains(existing));
		} finally {
			terrain.restore(existing);
		}
		Vec3 original = player.position();
		BlockPos unloaded = new BlockPos(20_000_000, center.getY(), 20_000_000);
		player.setPos(Vec3.atLowerCornerOf(unloaded).add(0.25, 0.25, 0.25));
		try {
			rejected(helper, "unloaded preflight rejected", () -> open(controller, player, SMALL));
			check(helper, "unloaded no spend/no generation/no ownership", mana(player) == before
				&& level.getChunkSource().getChunkNow(unloaded.getX() >> 4, unloaded.getZ() >> 4) == null && controller.status(player.getUUID()).isEmpty());
		} finally {
			player.setPos(original);
		}
		helper.succeed();
	}

	@GameTest(environment = "fatecraft-gametest:isolated/controller_opening")
	public void openingOnceFixedPlacementProtectionAndCancel(GameTestHelper helper) throws IOException {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = caster(helper, SMALL.openingMana() * 2);
		UbwController controller = UbwController.of(level.getServer());
		BlockPos feet = player.blockPosition();
		BlockPos center = feet.south(1); // .25 + radius/2 = 1.75, floored; pitch does not move Y.
		check(helper, "half-radius ahead block-grid placement", UbwController.placement(player, SMALL).equals(center));
		BlockPos retained = center.below(2);
		level.setBlock(retained, Blocks.DIAMOND_BLOCK.defaultBlockState(), QUIET_WRITE);
		double before = mana(player);
		check(helper, "opened", open(controller, player, SMALL) == UbwController.OpenResult.OPENED);
		check(helper, "opening exactly once", mana(player) == before - SMALL.openingMana());
		check(helper, "repeat open busy",
			open(controller, player, SMALL) == UbwController.OpenResult.BUSY && mana(player) == before - SMALL.openingMana());
		controller.tick(player.getUUID());
		controller.tick(player.getUUID());
		check(helper, "opening tick not charged upkeep", mana(player) == before - SMALL.openingMana());
		check(helper, "active real shell and retained terrain",
			level.getBlockState(center.east(3)).is(Blocks.GLASS) && level.getBlockState(retained).is(Blocks.DIAMOND_BLOCK));
		check(helper, "query covers shell interior and lower retained volume", controller.protects(level.dimension(), center.east(3))
			&& controller.protects(level.dimension(), center) && controller.protects(level.dimension(), retained));
		check(helper, "query excludes outside sphere/dimension/build height", !controller.protects(level.dimension(), center.offset(3, 1, 0))
			&& !controller.protects(Level.NETHER, center)
			&& !controller.protects(level.dimension(), new BlockPos(center.getX(), level.getMaxY(), center.getZ())));
		Vec3 original = player.position();
		player.setPos(-100.25, original.y, -100.25);
		check(helper, "negative placement uses Minecraft floor", UbwController.placement(player, SMALL).equals(new BlockPos(-101, feet.getY(), -99)));
		player.setYRot(90.0F);
		check(helper, "captured region never follows movement/facing",
			controller.status(player.getUUID()).orElseThrow().center().equals(center) && controller.protects(level.dimension(), center));
		player.setPos(original);
		check(helper, "manual cancel closed", controller.cancel(player.getUUID()) == UbwController.CloseResult.CLOSED);
		check(helper, "repeat cancel safe and query released", controller.cancel(player.getUUID()) == UbwController.CloseResult.CLOSED
			&& controller.status(player.getUUID()).isEmpty() && !controller.protects(level.dimension(), center));
		check(helper, "cancel does not refund or charge", mana(player) == before - SMALL.openingMana());
		helper.succeed();
	}

	// Start after the synchronous journal-fault/recovery tests, which intentionally replay all pending operations.
	@GameTest(environment = "fatecraft-gametest:isolated/controller_upkeep", setupTicks = 1)
	public void configuredUpkeepDuplicateTickAndLowManaRestoration(GameTestHelper helper) throws IOException {
		ServerLevel level = helper.getLevel();
		UbwConfig config = new UbwConfig(SMALL.radius(), 40, 70);
		double perTick = config.upkeepManaPerSecond() / SharedConstants.TICKS_PER_SECOND;
		ServerPlayer[] player = {null};
		BlockPos[] center = {null};
		UbwController controller = UbwController.of(level.getServer());
		helper.startSequence().thenIdle(betweenRegens(helper)).thenExecute(() -> {
			player[0] = caster(helper, config.openingMana() + perTick * 2);
			center[0] = UbwController.placement(player[0], config);
			level.setBlock(center[0], Blocks.GOLD_BLOCK.defaultBlockState(), QUIET_WRITE);
			try {
				check(helper, "configured opening cost",
					open(controller, player[0], config) == UbwController.OpenResult.OPENED && mana(player[0]) == perTick * 2);
			} catch (IOException failure) {
				throw new RuntimeException(failure);
			}
		}).thenIdle(1).thenExecute(checked(() -> {
			controller.tick(player[0].getUUID());
			controller.tick(player[0].getUUID());
			check(helper, "one configured upkeep charge in real next tick", mana(player[0]) == perTick
				&& controller.status(player[0].getUUID()).orElseThrow().phase() == UbwController.Phase.ACTIVE);
		})).thenIdle(1).thenExecute(checked(() -> {
			controller.tick(player[0].getUUID());
			controller.tick(player[0].getUUID());
			check(helper, "last funded tick stays active at zero", mana(player[0]) == 0 && controller.status(player[0].getUUID()).isPresent());
		})).thenIdle(1).thenExecute(checked(() -> {
			controller.tick(player[0].getUUID());
			check(helper, "unfunded upkeep restores without negative spend", mana(player[0]) == 0
				&& controller.status(player[0].getUUID()).isEmpty() && level.getBlockState(center[0]).is(Blocks.GOLD_BLOCK));
		})).thenSucceed();
	}

	@GameTest(environment = "fatecraft-gametest:isolated/controller_creative", setupTicks = 1)
	public void creativeOpeningAndUpkeepAreFree(GameTestHelper helper) throws IOException {
		UbwController controller = UbwController.of(helper.getLevel().getServer());
		ServerPlayer[] player = {null};
		helper.startSequence().thenIdle(betweenRegens(helper)).thenExecute(checked(() -> {
			player[0] = caster(helper, 0);
			player[0].setGameMode(GameType.CREATIVE);
			check(helper, "creative available is unlimited", Double.isInfinite(AlayaMana.available(player[0])));
			check(helper, "creative funded opening with zero stored mana",
				open(controller, player[0], SMALL) == UbwController.OpenResult.OPENED && mana(player[0]) == 0);
		})).thenIdle(1).thenExecute(checked(() -> {
			controller.tick(player[0].getUUID());
			controller.tick(player[0].getUUID());
			check(helper, "creative upkeep free and stays active",
				mana(player[0]) == 0 && controller.status(player[0].getUUID()).orElseThrow().phase() == UbwController.Phase.ACTIVE);
			check(helper, "creative manual cancel", controller.cancel(player[0].getUUID()) == UbwController.CloseResult.CLOSED && mana(player[0]) == 0);
		})).thenSucceed();
	}

	@GameTest(environment = "fatecraft-gametest:isolated/controller_retry")
	public void incompleteClosingRetainsOwnershipProtectionAndRetry(GameTestHelper helper) throws IOException {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = caster(helper, SMALL.openingMana() * 2);
		UbwController controller = UbwController.of(level.getServer());
		UbwTerrain terrain = UbwTerrain.of(level.getServer());
		BlockPos center = UbwController.placement(player, SMALL);
		check(helper, "opened for incomplete restore", open(controller, player, SMALL) == UbwController.OpenResult.OPENED);
		UUID operation = controller.status(player.getUUID()).orElseThrow().operation().orElseThrow();
		Path path = terrain.checkpointPath();
		byte[] real = Files.readAllBytes(path);
		CompoundTag journal = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
		CompoundTag saved = null;
		for (var tag : journal.getListOrEmpty("operations")) {
			CompoundTag candidate = (CompoundTag) tag;
			// Locate the actual operation by one original's coordinates, without assuming other tests are idle.
			for (var entry : candidate.getListOrEmpty("originals")) {
				int[] pos = ((CompoundTag) entry).getIntArray("pos").orElseThrow();
				if (pos[0] == center.getX() && pos[2] == center.getZ()) {
					saved = candidate;
				}
			}
		}
		check(helper, "actual opening journal found", saved != null);
		BlockPos unavailable = new BlockPos(20_000_000, center.getY(), 20_000_000);
		((CompoundTag) saved.getListOrEmpty("originals").get(0)).putIntArray("pos",
			new int[] {unavailable.getX(), unavailable.getY(), unavailable.getZ()});
		NbtIo.writeCompressed(journal, path);
		double before = mana(player);
		try {
			terrain.reload();
			check(helper, "unloaded closing pending", controller.cancel(player.getUUID()) == UbwController.CloseResult.PENDING);
			check(helper, "repeat cancel pending", controller.cancel(player.getUUID()) == UbwController.CloseResult.PENDING);
			controller.tick(player.getUUID());
			check(helper, "closing retains owner/full-volume protection/no spend", terrain.pending().contains(operation)
				&& controller.status(player.getUUID()).orElseThrow().phase() == UbwController.Phase.CLOSING
				&& controller.protects(level.dimension(), center.below(2)) && mana(player) == before);
			check(helper, "closing cannot reopen", open(controller, player, SMALL) == UbwController.OpenResult.BUSY && mana(player) == before);
			check(helper, "closing never generates chunks",
				level.getChunkSource().getChunkNow(unavailable.getX() >> 4, unavailable.getZ() >> 4) == null);
		} finally {
			Files.write(path, real);
			terrain.reload();
		}
		check(helper, "explicit retry completes and releases", controller.cancel(player.getUUID()) == UbwController.CloseResult.CLOSED
			&& !terrain.pending().contains(operation) && !controller.protects(level.dimension(), center));
		helper.succeed();
	}

	@GameTest(environment = "fatecraft-gametest:isolated/controller_dimensions")
	public void dimensionQualifiedRegionsAtSameCoordinates(GameTestHelper helper) throws IOException {
		ServerLevel level = helper.getLevel();
		ServerLevel other = level.getServer().getLevel(Level.NETHER);
		ServerPlayer player = caster(helper, SMALL.openingMana());
		BlockPos fixture = player.blockPosition();
		int y = Math.max(level.getMinY(), other.getMinY()) + SMALL.radius() + 1;
		player.setPos(fixture.getX() + 0.25, y + 0.25, fixture.getZ() + 0.25);
		BlockPos center = UbwController.placement(player, SMALL);
		prepare(level, center, SMALL.radius());
		prepare(other, center, SMALL.radius());
		var otherCaster = EntityType.HUSK.create(other, EntitySpawnReason.TRIGGERED);
		otherCaster.setPos(player.position());
		otherCaster.setYRot(player.getYRot());
		AlayaMana.getOrCreate(otherCaster);
		AlayaMana.addMax(otherCaster, SMALL.openingMana());
		AlayaMana.addCurrent(otherCaster, SMALL.openingMana());
		UbwController controller = UbwController.of(level.getServer());
		check(helper, "overworld opened", open(controller, player, SMALL) == UbwController.OpenResult.OPENED);
		check(helper, "query initially only in owning dimension",
			controller.protects(level.dimension(), center) && !controller.protects(other.dimension(), center));
		check(helper, "same coordinates other dimension independently opened",
			controller.open(otherCaster, SMALL, y, 1, PALETTE) == UbwController.OpenResult.OPENED);
		check(helper, "both dimension regions protected by query",
			controller.protects(level.dimension(), center) && controller.protects(other.dimension(), center));
		check(helper, "first cancel independent", controller.cancel(player.getUUID()) == UbwController.CloseResult.CLOSED
			&& !controller.protects(level.dimension(), center) && controller.protects(other.dimension(), center));
		check(helper, "second cancel releases region",
			controller.cancel(otherCaster.getUUID()) == UbwController.CloseResult.CLOSED && !controller.protects(other.dimension(), center));
		otherCaster.discard();
		helper.succeed();
	}

	@GameTest(environment = "fatecraft-gametest:isolated/controller_checkpoint")
	public void journalFailuresExposeStatusAndRequireExplicitRecovery(GameTestHelper helper) throws IOException {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = caster(helper, SMALL.openingMana() * 2);
		UbwController controller = UbwController.of(level.getServer());
		UbwTerrain terrain = UbwTerrain.of(level.getServer());
		Path path = terrain.checkpointPath();
		Path pending = path.resolveSibling(path.getFileName() + ".pending");
		double before = mana(player);
		Files.createDirectories(path.getParent());
		Files.createDirectory(pending);
		try {
			rejected(helper, "failed opening journal write exposed", () -> open(controller, player, SMALL));
			check(helper, "uncertain opening retained without spend",
				controller.status(player.getUUID()).orElseThrow().phase() == UbwController.Phase.RECOVERY_REQUIRED && mana(player) == before);
			rejected(helper, "cannot forget uncertain operation", () -> controller.releaseRecovered(player.getUUID()));
			check(helper, "same caster cannot retry uncertain opening", open(controller, player, SMALL) == UbwController.OpenResult.BUSY);
		} finally {
			Files.delete(pending);
			terrain.reload();
		}
		controller.releaseRecovered(player.getUUID());
		check(helper, "explicit recovery release allows opening",
			open(controller, player, SMALL) == UbwController.OpenResult.OPENED && mana(player) == before - SMALL.openingMana());
		UUID operation = controller.status(player.getUUID()).orElseThrow().operation().orElseThrow();
		Files.createDirectory(pending);
		try {
			rejected(helper, "retirement failure exposed", () -> controller.cancel(player.getUUID()));
			check(helper, "retirement failure retains closing protection and operation",
				controller.status(player.getUUID()).orElseThrow().phase() == UbwController.Phase.CLOSING
					&& controller.status(player.getUUID()).orElseThrow().operation().orElseThrow().equals(operation)
					&& controller.protects(level.dimension(), UbwController.placement(player, SMALL)));
		} finally {
			Files.delete(pending);
			terrain.reload();
		}
		check(helper, "retirement replay completes", terrain.pending().contains(operation)
			&& controller.cancel(player.getUUID()) == UbwController.CloseResult.CLOSED && !terrain.pending().contains(operation));
		check(helper, "journal failure/cancel never charge extra", mana(player) == before - SMALL.openingMana());
		helper.succeed();
	}
}
