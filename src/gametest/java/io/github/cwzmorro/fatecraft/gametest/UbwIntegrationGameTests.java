package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;
import static io.github.cwzmorro.fatecraft.gametest.UbwFeatureGameTests.bounds;
import static io.github.cwzmorro.fatecraft.gametest.UbwFeatureGameTests.mana;
import static io.github.cwzmorro.fatecraft.gametest.UbwFeatureGameTests.shots;
import static io.github.cwzmorro.fatecraft.gametest.UbwTests.QUIET_WRITE;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.api.AlayaServants;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.progression.TrueName;
import io.github.cwzmorro.alayacore.servant.ServantChoice;
import io.github.cwzmorro.alayacore.servant.ServantStorage;
import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.gate.GateOfBabylon;
import io.github.cwzmorro.fatecraft.gate.GateReturns;
import io.github.cwzmorro.fatecraft.gate.TreasureEntity;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.trace.Projections;
import io.github.cwzmorro.fatecraft.trace.Traces;
import io.github.cwzmorro.fatecraft.ubw.UbwController;
import io.github.cwzmorro.fatecraft.ubw.UbwEnvironmentEntity;
import io.github.cwzmorro.fatecraft.ubw.UbwGroundSwordEntity;
import io.github.cwzmorro.fatecraft.ubw.UbwMembership;
import io.github.cwzmorro.fatecraft.ubw.UbwRuntime;
import io.github.cwzmorro.fatecraft.ubw.UbwSwords;
import io.github.cwzmorro.fatecraft.ubw.UbwTerrain;
import io.github.cwzmorro.fatecraft.ubw.UnlimitedBladeWorks;
import io.github.cwzmorro.fatecraft.vault.Vault;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

// Each test runs alone, since clock and journal faults are server-wide.
public class UbwIntegrationGameTests {
	private static final BlockPos START = new BlockPos(4, 10, 4);
	private static final int FAR = 20_000_000;
	private static final byte[] UNREADABLE = {1, 7, 3, 9};
	private static final int UNRELATED_LOADS = 4;

	private static ServerPlayer emiya(GameTestHelper helper) {
		return emiya(helper, START);
	}

	private static ServerPlayer emiya(GameTestHelper helper, BlockPos start) {
		ServerPlayer player = FateTests.servant(helper, start, 50_000, FateServants.EMIYA);
		TrueName.realize(player);
		UbwFeatureGameTests.field(helper, player);
		return player;
	}

	private static UbwController.Status open(GameTestHelper helper, ServerPlayer owner) throws IOException {
		FateTests.tap(owner, UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.MARBLE, false);
		check(helper, "actual registered NP opens", UbwRuntime.hasMarble(owner));
		// These tests are about the standing field; UbwFeatureGameTests.openingSwapsBehindItsFrontThenRestores covers the opening.
		UbwRuntime.finishOpening(owner);
		return UbwController.of(helper.getLevel().getServer()).status(owner.getUUID()).orElseThrow();
	}

	private static void closed(GameTestHelper helper, ServerPlayer owner, UbwController.Status status) {
		check(helper, "registered lifecycle restores and clears props/protection", !UbwRuntime.hasMarble(owner)
			&& !UbwRuntime.protects(helper.getLevel(), status.center())
			&& helper.getLevel().getEntitiesOfClass(UbwEnvironmentEntity.class, bounds(status)).isEmpty()
			&& helper.getLevel().getEntitiesOfClass(UbwGroundSwordEntity.class, bounds(status)).isEmpty());
	}

	@GameTest(environment = "fatecraft-gametest:isolated/integration_return", setupTicks = 2, padding = 64, maxTicks = 100)
	public void returnPlayerAndMobFromRestoredSolidSpots(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		ServerLevel level = helper.getLevel();
		BlockPos center = UbwController.placement(owner, FateConfig.ubw());
		BlockPos playerSpot = new BlockPos(center.getX() - 1, owner.blockPosition().getY(), center.getZ());
		BlockPos mobSpot = playerSpot.offset(2, 0, 0);
		for (BlockPos spot : List.of(playerSpot, mobSpot)) {
			for (int height = 0; height < 3; height++) {
				level.setBlock(spot.above(height), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		UbwController.Status status = open(helper, owner);
		var mob = FateTests.mob(helper, START);
		mob.setNoAi(true);
		Vec3 playerBefore = Vec3.atBottomCenterOf(playerSpot);
		Vec3 mobBefore = Vec3.atBottomCenterOf(mobSpot);
		owner.teleportTo(playerBefore.x, playerBefore.y, playerBefore.z);
		mob.setPos(mobBefore);
		check(helper, "both occupants are clear inside UBW before restoration", level.noCollision(owner) && level.noCollision(mob));
		check(helper, "close completes", UbwRuntime.cancel(owner) == UbwController.CloseResult.CLOSED);
		closed(helper, owner, status);
		for (BlockPos spot : List.of(playerSpot, mobSpot)) {
			for (int height = 0; height < 3; height++) {
				check(helper, "original solid column restored", level.getBlockState(spot.above(height)).is(Blocks.STONE));
			}
		}
		check(helper, "player returned out of restored solid terrain", !owner.position().equals(playerBefore) && level.noCollision(owner));
		check(helper, "mob returned out of restored solid terrain", !mob.position().equals(mobBefore) && level.noCollision(mob));
		Vec3 safePlayer = owner.position();
		UbwRuntime.cancel(owner);
		check(helper, "repeat close preserves safe position", owner.position().equals(safePlayer));
		helper.runAfterDelay(1, () -> {
			check(helper, "player and mob stay clear after native movement", level.noCollision(owner) && level.noCollision(mob));
			mob.discard();
			helper.succeed();
		});
	}

	@GameTest(environment = "fatecraft-gametest:isolated/integration_freeze", setupTicks = 2, padding = 64, maxTicks = 250)
	public void frozenCallbacksNativeStepsResumeAndCancel(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		UbwController.Status status = open(helper, owner);
		ServerLevel level = helper.getLevel();
		var server = level.getServer();
		var enemy = FateTests.mob(helper, START);
		enemy.setInvulnerable(true);
		enemy.setPos(Vec3.atLowerCornerOf(status.center()).add(4, 1, 0));
		Abilities.press(owner, UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.AOE_THROW, false);
		Abilities.release(owner, UnlimitedBladeWorks.KEY.identifier());
		int[] raw = {-1};
		long[] logical = {-1};
		double[] frozenMana = {0};
		List<ThrownWeaponEntity> initial = shots(helper, owner, status);
		int[] formation = {0};
		int[] phase = {0};
		long[] resumed = {-1};
		double[] steppedMana = {0};
		int[] resumeRaw = {0};
		boolean[] finished = {false};
		ServerTickEvents.END_SERVER_TICK.register(ignored -> {
			if (finished[0]) {
				return;
			}
			try {
				if (phase[0] == 0 && helper.getTick() >= 10) {
					server.tickRateManager().setFrozen(true);
					raw[0] = server.getTickCount();
					logical[0] = level.getGameTime();
					frozenMana[0] = mana(owner);
					formation[0] = initial.getFirst().formationRemaining();
					phase[0] = 1;
				} else if (phase[0] == 1 && server.getTickCount() >= raw[0] + 48) {
					check(helper, "48 frozen callbacks: time=" + level.getGameTime() + "/" + logical[0] + " mana=" + mana(owner) + "/" + frozenMana[0]
						+ " regen=" + rawRegen(owner, raw[0], server.getTickCount()) + " formation=" + initial.getFirst().formationRemaining() + "/"
						+ formation[0] + " shots=" + shots(helper, owner, status).stream().map(shot -> shot.heldProjection() + ":" + shot.formationRemaining()).toList(),
						level.getGameTime() == logical[0]
							&& Math.abs(mana(owner) - frozenMana[0] - rawRegen(owner, raw[0], server.getTickCount())) < 1e-7
							&& initial.getFirst().formationRemaining() == formation[0]
							&& shots(helper, owner, status).stream().allMatch(ThrownWeaponEntity::heldProjection));
					frozenMana[0] = mana(owner);
					UbwController.of(server).tick(owner.getUUID());
					check(helper, "direct controller also skips frozen upkeep", mana(owner) == frozenMana[0]);
					server.tickRateManager().stepGameIfPaused(3);
					phase[0] = 2;
					raw[0] = server.getTickCount();
				} else if (phase[0] == 2 && server.getTickCount() >= raw[0] + 8) {
					check(helper, "exact three native steps advance time and upkeep", level.getGameTime() == logical[0] + 3
						&& Math.abs(mana(owner) - frozenMana[0] + 3 * FateConfig.ubw().upkeepManaPerSecond() / SharedConstants.TICKS_PER_SECOND - rawRegen(owner, raw[0], server.getTickCount())) < 1e-7);
					check(helper, "three steps retain the unrequested waiting volley",
						shots(helper, owner, status).stream().allMatch(ThrownWeaponEntity::heldProjection));
					Abilities.release(owner, UnlimitedBladeWorks.KEY.identifier());
					server.tickRateManager().setFrozen(false);
					resumed[0] = level.getGameTime();
					steppedMana[0] = mana(owner);
					resumeRaw[0] = server.getTickCount();
					phase[0] = 3;
				} else if (phase[0] == 3 && level.getGameTime() == resumed[0] + 10) {
					check(helper, "resume charges only ten logical ticks and no unsolicited release",
						Math.abs(mana(owner) - steppedMana[0] + 10 * FateConfig.ubw().upkeepManaPerSecond() / SharedConstants.TICKS_PER_SECOND - rawRegen(owner, resumeRaw[0], server.getTickCount())) < 1e-7
							&& shots(helper, owner, status).stream().allMatch(ThrownWeaponEntity::heldProjection));
					phase[0] = 4;
				} else if (phase[0] == 4 && level.getGameTime() >= statusTime(status, owner) + 40) {
					check(helper, "unrequested volley stays held beyond forty ticks",
						shots(helper, owner, status).size() == initial.size()
							&& shots(helper, owner, status).stream().allMatch(ThrownWeaponEntity::heldProjection));
					server.tickRateManager().setFrozen(true);
					check(helper, "frozen field remains protected", UbwRuntime.protects(level, status.center()));
					FateTests.tap(owner, UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.MARBLE, false);
					closed(helper, owner, status);
					enemy.discard();
					server.tickRateManager().setFrozen(false);
					finished[0] = true;
					helper.runAfterDelay(1, helper::succeed);
				}
			} catch (IOException | RuntimeException failure) {
				finished[0] = true;
				server.tickRateManager().setFrozen(false);
				UbwRuntime.cancel(owner);
				enemy.discard();
				helper.runAfterDelay(1, () -> {
					throw new IllegalStateException(failure);
				});
			}
		});
	}

	private static double rawRegen(ServerPlayer player, int before, int after) {
		return (after / 10 - before / 10) * AlayaConfig.mana().regenPerSecond() * 0.5 * AlayaServants.manaRegenMultiplier(player);
	}

	private static void rawAfter(GameTestHelper helper, int delay, Runnable action, ServerPlayer owner) {
		var server = helper.getLevel().getServer();
		int until = server.getTickCount() + delay;
		boolean[] done = {false};
		ServerTickEvents.END_SERVER_TICK.register(ignored -> {
			if (done[0] || server.getTickCount() < until) {
				return;
			}
			done[0] = true;
			try {
				action.run();
			} catch (RuntimeException failure) {
				helper.runAfterDelay(1, () -> {
					throw failure;
				});
			} finally {
				server.tickRateManager().setFrozen(false);
				UbwRuntime.cancel(owner);
			}
		});
	}

	private static long statusTime(UbwController.Status status, ServerPlayer owner) {
		return owner.level().getEntitiesOfClass(UbwEnvironmentEntity.class, bounds(status)).getFirst().openedAt();
	}

	@GameTest(environment = "fatecraft-gametest:isolated/integration_death", setupTicks = 2, padding = 64, maxTicks = 100)
	public void deathEventClosesFrozenField(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		UbwController.Status status = open(helper, owner);
		var server = helper.getLevel().getServer();
		owner.setGameMode(GameType.SURVIVAL);
		server.tickRateManager().setFrozen(true);
		owner.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
		rawAfter(helper, 2, () -> {
			closed(helper, owner, status);
			helper.runAfterDelay(1, helper::succeed);
		}, owner);
	}

	@GameTest(environment = "fatecraft-gametest:isolated/integration_disconnect", setupTicks = 2, padding = 64, maxTicks = 100)
	public void disconnectEventClosesField(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		UbwController.Status status = open(helper, owner);
		owner.connection.disconnect(Component.literal("UBW integration logout fixture"));
		helper.runAfterDelay(3, () -> {
			closed(helper, owner, status);
			helper.succeed();
		});
	}

	@GameTest(environment = "fatecraft-gametest:isolated/integration_destroyed", setupTicks = 2, padding = 64, maxTicks = 100)
	public void destroyedUnloadAndServantChangeClose(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		UbwController.Status first = open(helper, owner);
		// Register both mock players' ability tick callbacks before GameTestInfo starts iterating its task map.
		ServerPlayer next = emiya(helper);
		UbwController.Status[] second = {null};
		owner.remove(Entity.RemovalReason.KILLED);
		helper.startSequence().thenIdle(2).thenExecute(() -> {
			closed(helper, owner, first);
			try {
				second[0] = open(helper, next);
				next.setAttached(ServantStorage.CHOICE, ServantChoice.servant(AlayaRegistries.SERVANT.getKey(FateServants.GILGAMESH)));
			} catch (IOException failure) {
				throw new IllegalStateException(failure);
			}
		}).thenIdle(2).thenExecute(() -> closed(helper, next, second[0])).thenSucceed();
	}

	@GameTest(environment = "fatecraft-gametest:isolated/integration_copy", setupTicks = 2, padding = 64, maxTicks = 150)
	public void alreadyInsideGobCopyClashAndTraceExclusion(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		UbwController.Status status = open(helper, owner);
		ServerLevel level = helper.getLevel();
		ServerPlayer gil = FateTests.gilgamesh(helper, START, 50_000);
		gil.setPos(Vec3.atLowerCornerOf(status.center()).add(5, 2, 0));
		Vault vault = Vault.of(gil);
		vault.setSettings(vault.settings().withVolleyChanged(0, 1 - vault.settings().volley()).withReturnSeconds(2));
		vault.section(0).insert(Projections.create(Items.DIAMOND_SWORD));
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(),
			"gamerule fatecraft:gate_of_babylon_copies true");
		Abilities.press(gil, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, false);
		TreasureEntity[] original = {null};
		long[] at = {-1};
		helper.onEachTick(() -> {
			if (original[0] != null) {
				return;
			}
			var incoming = level.getEntitiesOfClass(TreasureEntity.class, bounds(status), shot -> gil.getUUID().equals(shot.owner()));
			if (incoming.isEmpty()) {
				return;
			}
			original[0] = incoming.getFirst();
			Abilities.release(gil, GateOfBabylon.KEY.identifier());
			check(helper, "actual GoB copy dispatch provenance", original[0].getAttached(GateReturns.THROWN).copy()
				&& (UbwSwords.incoming(original[0]) || shots(helper, owner, status).size() == 1));
			// The real dispatch may already have run its native sweep before this helper observes it.
			if (UbwSwords.incoming(original[0])) {
				original[0].setPos(Vec3.atLowerCornerOf(status.center()).add(5, 2, 0));
				original[0].setDeltaMovement(-3, 0, 0);
				original[0].tick();
			}
			check(helper, "already-inside one exact opposing copy", shots(helper, owner, status).size() == 1
				&& ItemStack.isSameItemSameComponents(shots(helper, owner, status).getFirst().item(), original[0].item()));
			original[0].tick();
			check(helper, "no duplicate pending counter", shots(helper, owner, status).size() == 1);
			at[0] = helper.getTick();
		});
		helper.succeedWhen(() -> {
			check(helper, "counter copy disposal: countered=" + (original[0] != null && UbwSwords.countered(original[0])) + " removed="
				+ (original[0] != null && original[0].isRemoved()) + " vault=" + vault.section(0).entries(),
				at[0] >= 0 && UbwSwords.countered(original[0]) && original[0].isRemoved() && vault.section(0).entries().size() == 1
					&& vault.section(0).entries().getFirst().count() == 1
					&& ItemStack.isSameItemSameComponents(vault.section(0).entries().getFirst().item(), original[0].item()));
			ThrownWeaponEntity trace = UbwSwords.project(level, gil, Projections.create(Items.IRON_SWORD),
				Vec3.atLowerCornerOf(status.center()).add(5, 3, 0), new Vec3(-3, 0, 0), 0, false, 1.0F);
			trace.tick();
			check(helper, "ordinary Trace copy has no GoB provenance or counter", !UbwSwords.incoming(trace) && shots(helper, owner, status).isEmpty());
			trace.discard();
			level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(),
				"gamerule fatecraft:gate_of_babylon_copies false");
			UbwRuntime.cancel(owner);
		});
	}

	@GameTest(environment = "fatecraft-gametest:isolated/integration_divine", setupTicks = 2, padding = 64, maxTicks = 150)
	public void divineConstructEaEntersUncounteredAndIsNotLearned(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		UbwController.Status status = open(helper, owner);
		ServerLevel level = helper.getLevel();
		ServerPlayer gil = FateTests.gilgamesh(helper, START, 50_000);
		gil.setPos(Vec3.atLowerCornerOf(status.center()).add(5, 2, 0));
		Vault vault = Vault.of(gil);
		vault.setSettings(vault.settings().withVolleyChanged(0, 1 - vault.settings().volley()).withReturnSeconds(2));
		vault.section(0).insert(new ItemStack(FateWeapons.EA));
		Abilities.press(gil, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, false);
		TreasureEntity[] original = {null};
		long[] seen = {-1};
		helper.onEachTick(() -> {
			if (original[0] == null) {
				var incoming = level.getEntitiesOfClass(TreasureEntity.class, bounds(status), shot -> gil.getUUID().equals(shot.owner()));
				if (incoming.isEmpty()) {
					return;
				}
				original[0] = incoming.getFirst();
				Abilities.release(gil, GateOfBabylon.KEY.identifier());
				seen[0] = helper.getTick();
				check(helper, "the thrown item is Ea", original[0].item().is(FateWeapons.EA));
				original[0].setPos(Vec3.atLowerCornerOf(status.center()).add(5, 2, 0));
				original[0].setDeltaMovement(-3, 0, 0);
				original[0].tick();
			}
		});
		helper.succeedWhen(() -> {
			check(helper, "Ea seen", seen[0] >= 0 && helper.getTick() > seen[0] + 5);
			check(helper, "no opposing copy of Ea", shots(helper, owner, status).isEmpty() && !UbwSwords.countered(original[0]));
			check(helper, "Ea not learned", Traces.known(owner).stream().noneMatch(id -> id.equals(BuiltInRegistries.ITEM.getKey(FateWeapons.EA))));
			UbwRuntime.cancel(owner);
		});
	}

	@GameTest(environment = "fatecraft-gametest:isolated/integration_startup", setupTicks = 2, padding = 64, maxTicks = 100)
	public void corruptStartupRetainsEvidenceAndReloadRestores(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		UbwController.Status status = open(helper, owner);
		var server = helper.getLevel().getServer();
		UbwTerrain terrain = UbwTerrain.of(server);
		Path path = terrain.checkpointPath();
		byte[] valid = Files.readAllBytes(path);
		// A process restart can't be embedded in this runner: invoke the registered stop/start events with journal retained.
		ServerLifecycleEvents.SERVER_STOPPED.invoker().onServerStopped(server);
		Files.write(path, UNREADABLE);
		try {
			ServerLifecycleEvents.SERVER_STARTED.invoker().onServerStarted(server);
			check(helper, "corrupt startup refuses casting and preserves exact bytes",
				!UbwRuntime.ready(server) && Arrays.equals(Files.readAllBytes(path), UNREADABLE));
			check(helper, "NP refusal costs nothing", new UnlimitedBladeWorks().cannotStart(owner, UnlimitedBladeWorks.MARBLE) != null);
			Files.write(path, valid);
			ServerLifecycleEvents.END_DATA_PACK_RELOAD.invoker().endDataPackReload(server,
				(CloseableResourceManager) server.getResourceManager(), true);
			check(helper, "successful registered reload recovers journal before casts",
				UbwRuntime.ready(server) && UbwTerrain.of(server).pending().isEmpty());
			// No-save live entities are a real restart's responsibility; this event replay must clean its test props explicitly.
			helper.getLevel().getEntitiesOfClass(UbwEnvironmentEntity.class, bounds(status)).forEach(Entity::discard);
			helper.getLevel().getEntitiesOfClass(UbwGroundSwordEntity.class, bounds(status)).forEach(Entity::discard);
			closed(helper, owner, status);
		} finally {
			if (!UbwRuntime.ready(server)) {
				Files.write(path, valid);
				UbwRuntime.reloaded(server);
			}
		}
		helper.succeed();
	}

	private static void place(GameTestHelper helper, ServerPlayer owner, BlockPos primary, Item item, float yaw, boolean denied) {
		ServerLevel level = helper.getLevel();
		owner.setGameMode(GameType.SURVIVAL);
		owner.setYRot(yaw);
		var targets = List.of(primary.below(), primary, primary.above(), primary.east(), primary.east().below());
		Map<BlockPos, BlockState> before = new HashMap<>();
		for (BlockPos pos : targets) {
			before.put(pos, level.getBlockState(pos));
		}
		try {
			level.setBlock(primary, Blocks.AIR.defaultBlockState(), QUIET_WRITE);
			level.setBlock(primary.above(), Blocks.AIR.defaultBlockState(), QUIET_WRITE);
			level.setBlock(primary.east(), Blocks.AIR.defaultBlockState(), QUIET_WRITE);
			level.setBlock(primary.below(), Blocks.STONE.defaultBlockState(), QUIET_WRITE);
			level.setBlock(primary.east().below(), Blocks.STONE.defaultBlockState(), QUIET_WRITE);
			Map<BlockPos, BlockState> prepared = new HashMap<>();
			for (BlockPos pos : targets) {
				prepared.put(pos, level.getBlockState(pos));
			}
			ItemStack stack = new ItemStack(item, 3);
			stack.set(DataComponents.CUSTOM_NAME, Component.literal("UBW exact placement stack"));
			owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
			ItemStack original = stack.copy();
			var hit = new BlockHitResult(Vec3.atCenterOf(primary.below()).add(0, 0.5, 0), Direction.UP, primary.below(), false);
			var result = stack.useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND, hit));
			check(helper, "real survival placement keeps components", ItemStack.isSameItemSameComponents(original, owner.getMainHandItem()));
			if (denied) {
				check(helper, "protected actual placement refuses without consuming or writing " + item,
					!result.consumesAction() && owner.getMainHandItem().getCount() == 3
						&& prepared.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue())));
			} else {
				check(helper, "outside actual placement consumes exactly one",
					result.consumesAction() && owner.getMainHandItem().getCount() == 2 && level.getBlockState(primary).is(Blocks.STONE));
			}
		} finally {
			before.forEach((pos, state) -> level.setBlock(pos, state, QUIET_WRITE));
		}
	}

	@GameTest(environment = "fatecraft-gametest:isolated/integration_placement", setupTicks = 2, padding = 64, maxTicks = 100)
	public void realSurvivalPlacementActiveAndPendingPreservesItems(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		UbwController.Status status = open(helper, owner);
		var server = helper.getLevel().getServer();
		Path path = UbwTerrain.of(server).checkpointPath();
		Path pending = path.resolveSibling(path.getFileName() + ".pending");
		try {
			place(helper, owner, status.center().above(2), Items.STONE, 0, true);
			place(helper, owner, status.center().east(status.radius()).below(), Items.OAK_DOOR, 0, true);
			place(helper, owner, status.center().west(status.radius() + 1), Items.RED_BED, -90, true);
			place(helper, owner, status.center().east(status.radius() + 5), Items.STONE, 0, false);
			Files.createDirectory(pending);
			UbwRuntime.cancel(owner);
			Files.delete(pending);
			check(helper, "pending closing retains protection and forbids benefit", UbwRuntime.hasMarble(owner) && !UbwMembership.freeProjection(owner));
			place(helper, owner, status.center().above(2), Items.STONE, 0, true);
			place(helper, owner, status.center().east(status.radius()).below(), Items.OAK_DOOR, 0, true);
			place(helper, owner, status.center().west(status.radius() + 1), Items.RED_BED, -90, true);
			place(helper, owner, status.center().east(status.radius() + 5), Items.STONE, 0, false);
		} finally {
			Files.deleteIfExists(pending);
			UbwRuntime.reloaded(server);
			UbwRuntime.cancel(owner);
		}
		closed(helper, owner, status);
		helper.succeed();
	}

	private static boolean spawns(GameTestHelper helper, BlockPos pos, EntitySpawnReason reason) {
		Zombie zombie = EntityType.ZOMBIE.spawn(helper.getLevel(), pos, reason);
		boolean stays = zombie != null && zombie.isAlive() && helper.getLevel().getEntity(zombie.getUUID()) != null;
		if (zombie != null) {
			zombie.discard();
		}
		return stays;
	}

	@GameTest(environment = "fatecraft-gametest:isolated/integration_natural_spawn", setupTicks = 2, padding = 64, maxTicks = 100)
	public void onlyNaturalSpawnsAreRefusedInsideTheMarble(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		UbwController.Status status = open(helper, owner);
		BlockPos inside = status.center().above(5);
		BlockPos outside = status.center().east(status.radius() + 5);
		for (EntitySpawnReason natural : List.of(EntitySpawnReason.NATURAL, EntitySpawnReason.CHUNK_GENERATION,
			EntitySpawnReason.STRUCTURE, EntitySpawnReason.PATROL)) {
			check(helper, natural + " spawn inside the marble is refused", !spawns(helper, inside, natural));
			check(helper, natural + " spawn outside the marble is allowed", spawns(helper, outside, natural));
		}
		for (EntitySpawnReason intended : List.of(EntitySpawnReason.COMMAND, EntitySpawnReason.SPAWN_ITEM_USE,
			EntitySpawnReason.MOB_SUMMONED, EntitySpawnReason.SPAWNER, EntitySpawnReason.BREEDING, EntitySpawnReason.CONVERSION)) {
			check(helper, intended + " spawn inside the marble is allowed", spawns(helper, inside, intended));
		}
		UbwRuntime.cancel(owner);
		check(helper, "natural spawns return once the marble is closed", spawns(helper, inside, EntitySpawnReason.NATURAL));
		helper.succeed();
	}

	@GameTest(environment = "fatecraft-gametest:isolated/integration_retry", setupTicks = 2, padding = 64, maxTicks = 100)
	public void frozenTransientCloseRetainsProtectionThenRetries(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		UbwController.Status status = open(helper, owner);
		var server = helper.getLevel().getServer();
		Path path = UbwTerrain.of(server).checkpointPath();
		Path pending = path.resolveSibling(path.getFileName() + ".pending");
		Files.createDirectory(pending);
		server.tickRateManager().setFrozen(true);
		try {
			FateTests.tap(owner, UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.MARBLE, false);
			check(helper, "failed close remains owned/protected but no free projections", UbwRuntime.hasMarble(owner)
				&& UbwRuntime.protects(helper.getLevel(), status.center()) && !UbwMembership.freeProjection(owner) && !UbwRuntime.ready(server)
				&& Files.exists(path));
		} finally {
			Files.delete(pending);
		}
		rawAfter(helper, 25, () -> {
			check(helper, "maintenance retries recovery while frozen", UbwRuntime.ready(server));
			closed(helper, owner, status);
			helper.runAfterDelay(1, helper::succeed);
		}, owner);
	}

	private static void strand(GameTestHelper helper, UbwController.Status status, List<BlockPos> positions) throws IOException {
		UbwTerrain terrain = UbwTerrain.of(helper.getLevel().getServer());
		Path path = terrain.checkpointPath();
		CompoundTag journal = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
		int[] center = {status.center().getX(), status.center().getY(), status.center().getZ()};
		CompoundTag operation = null;
		for (var tag : journal.getListOrEmpty("operations")) {
			if (Arrays.equals(((CompoundTag) tag).getCompoundOrEmpty("volume").getIntArray("center").orElse(null), center)) {
				operation = (CompoundTag) tag;
			}
		}
		check(helper, "the marble's journalled operation", operation != null);
		Tag gold = BlockState.CODEC.encodeStart(NbtOps.INSTANCE, Blocks.GOLD_BLOCK.defaultBlockState()).getOrThrow();
		ListTag originals = operation.getListOrEmpty("originals");
		for (BlockPos pos : positions) {
			check(helper, "stranded chunk not loaded", !loaded(helper, pos));
			CompoundTag original = new CompoundTag();
			original.putIntArray("pos", new int[] {pos.getX(), pos.getY(), pos.getZ()});
			original.put("state", gold.copy());
			originals.add(original);
		}
		NbtIo.writeCompressed(journal, path);
		terrain.reload();
	}

	private static boolean loaded(GameTestHelper helper, BlockPos pos) {
		int x = SectionPos.blockToSectionCoord(pos.getX());
		int z = SectionPos.blockToSectionCoord(pos.getZ());
		return helper.getLevel().getChunkSource().getChunkNow(x, z) != null;
	}

	private static void force(GameTestHelper helper, BlockPos pos, boolean forced) {
		helper.getLevel().setChunkForced(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), forced);
	}

	private static byte[] swap(Path path, byte[] contents) {
		try {
			byte[] held = Files.readAllBytes(path);
			Files.write(path, contents);
			return held;
		} catch (IOException failure) {
			throw new UncheckedIOException(failure);
		}
	}

	@GameTest(environment = "fatecraft-gametest:isolated/integration_stranded", setupTicks = 2, padding = 64, maxTicks = 300)
	public void strandedTerrainWaitsAloneForItsChunk(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		ServerLevel level = helper.getLevel();
		var server = level.getServer();
		BlockPos marker = UbwController.placement(owner, FateConfig.ubw()).above(2);
		level.setBlock(marker, Blocks.EMERALD_BLOCK.defaultBlockState(), QUIET_WRITE);
		UbwController.Status status = open(helper, owner);
		check(helper, "the field replaced the marker", !level.getBlockState(marker).is(Blocks.EMERALD_BLOCK));
		UUID operation = status.operation().orElseThrow();
		BlockPos stranded = new BlockPos(FAR, status.center().getY(), FAR);
		strand(helper, status, List.of(stranded));
		UbwRuntime.cancel(owner);
		check(helper, "loaded terrain restored at once", level.getBlockState(marker).is(Blocks.EMERALD_BLOCK));
		UbwRuntime.reloaded(server);
		check(helper, "only the stranded part stays journalled and protected, and casting stays open", UbwRuntime.ready(server)
			&& UbwTerrain.of(server).pending().contains(operation) && UbwRuntime.protects(level, status.center()) && !loaded(helper, stranded));
		ServerPlayer other = emiya(helper, START.east(FateConfig.ubw().radius() * 3));
		UbwController.Status elsewhere = open(helper, other);
		UbwRuntime.cancel(other);
		closed(helper, other, elsewhere);
		force(helper, stranded, true);
		helper.succeedWhen(() -> {
			check(helper, "stranded chunk loaded", loaded(helper, stranded));
			check(helper, "stranded part restored once its chunk loads", level.getBlockState(stranded).is(Blocks.GOLD_BLOCK));
			closed(helper, owner, status);
			force(helper, stranded, false);
		});
	}

	@GameTest(environment = "fatecraft-gametest:isolated/integration_stranded_ticket", setupTicks = 2, padding = 64, maxTicks = 400)
	public void closingMarbleLoadsItsWaitedOnChunkAndLetsGo(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		ServerLevel level = helper.getLevel();
		UbwController.Status status = open(helper, owner);
		BlockPos stranded = new BlockPos(FAR, status.center().getY(), FAR);
		strand(helper, status, List.of(stranded));
		// No player is near the stranded chunk and nothing forces it: only the marble's own wait can load it.
		UbwRuntime.cancel(owner);
		helper.startSequence().thenWaitUntil(() -> {
			check(helper, "stranded part restored without anyone loading its chunk", level.getBlockState(stranded).is(Blocks.GOLD_BLOCK));
			closed(helper, owner, status);
		}).thenExecute(() -> {
			try {
				UbwController.Status again = open(helper, owner);
				UbwRuntime.cancel(owner);
				closed(helper, owner, again);
			} catch (IOException failure) {
				throw new UncheckedIOException(failure);
			}
		}).thenWaitUntil(() -> check(helper, "no ticket of the marble's keeps the chunk loaded once its terrain is restored",
			!loaded(helper, stranded))
		).thenSucceed();
	}

	@GameTest(environment = "fatecraft-gametest:isolated/integration_stranded_loads", setupTicks = 2, padding = 64, maxTicks = 300)
	public void chunkLoadsWakeOnlyTheRestorationsWaitingOnThem(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		ServerLevel level = helper.getLevel();
		var server = level.getServer();
		UbwController.Status status = open(helper, owner);
		BlockPos first = new BlockPos(FAR, status.center().getY(), FAR);
		BlockPos second = first.east(2 * SectionPos.SECTION_SIZE);
		strand(helper, status, List.of(first, second));
		UbwRuntime.cancel(owner);
		UbwRuntime.reloaded(server);
		Path path = UbwTerrain.of(server).checkpointPath();
		// Any journal read from here fails recovery and closes casting; any write replaces these bytes.
		byte[] valid = swap(path, UNREADABLE);
		for (int i = 1; i <= UNRELATED_LOADS; i++) {
			level.getChunk(SectionPos.blockToSectionCoord(-FAR) - i, SectionPos.blockToSectionCoord(-FAR));
		}
		// The waited-on chunks load by themselves (their tickets) within a few ticks, so this is checked at once.
		boolean untouched = Arrays.equals(swap(path, valid), UNREADABLE);
		check(helper, "unrelated chunk loads neither read nor write the journal", untouched && UbwRuntime.ready(server));
		helper.startSequence().thenWaitUntil(() -> {
			closed(helper, owner, status);
			// Read only once the marble is gone: its chunks are let go after their parts are restored, and persisted.
			check(helper, "each chunk's part restored once its chunk loads",
				level.getBlockState(first).is(Blocks.GOLD_BLOCK) && level.getBlockState(second).is(Blocks.GOLD_BLOCK));
		}).thenSucceed();
	}
}
