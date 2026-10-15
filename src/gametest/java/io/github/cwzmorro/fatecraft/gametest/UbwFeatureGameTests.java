package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;
import static io.github.cwzmorro.fatecraft.gametest.UbwTests.PALETTE;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.ability.AbilitySetting;
import io.github.cwzmorro.alayacore.ability.AbilitySettings;
import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.api.AlayaServants;
import io.github.cwzmorro.alayacore.combat.WeaponBlocks;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.marble.RealityMarble;
import io.github.cwzmorro.alayacore.marble.RealityMarbles;
import io.github.cwzmorro.alayacore.progression.TrueName;
import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.gate.GateEntity;
import io.github.cwzmorro.fatecraft.gate.GateOfBabylon;
import io.github.cwzmorro.fatecraft.gate.GateThrows;
import io.github.cwzmorro.fatecraft.gate.Gates;
import io.github.cwzmorro.fatecraft.gate.TreasureEntity;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.trace.Project;
import io.github.cwzmorro.fatecraft.trace.Projections;
import io.github.cwzmorro.fatecraft.trace.Traces;
import io.github.cwzmorro.fatecraft.ubw.Ubw;
import io.github.cwzmorro.fatecraft.ubw.UbwConfig;
import io.github.cwzmorro.fatecraft.ubw.UbwController;
import io.github.cwzmorro.fatecraft.ubw.UbwCraftability;
import io.github.cwzmorro.fatecraft.ubw.UbwEnvironmentEntity;
import io.github.cwzmorro.fatecraft.ubw.UbwGroundSwordEntity;
import io.github.cwzmorro.fatecraft.ubw.UbwLayout;
import io.github.cwzmorro.fatecraft.ubw.UbwMembership;
import io.github.cwzmorro.fatecraft.ubw.UbwRuntime;
import io.github.cwzmorro.fatecraft.ubw.UbwSettings;
import io.github.cwzmorro.fatecraft.ubw.UbwSwords;
import io.github.cwzmorro.fatecraft.ubw.UbwTerrain;
import io.github.cwzmorro.fatecraft.ubw.UnlimitedBladeWorks;
import io.github.cwzmorro.fatecraft.vault.Vault;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

// Default-radius tests are padded so their fields cannot reach another test's fixture.
public class UbwFeatureGameTests {
	static final BlockPos START = new BlockPos(4, 8, 4);
	private static final UbwConfig SMALL = new UbwConfig(3, 10, 0);
	private static final Item[] MORE_SWORDS = {
		Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};

	static double mana(ServerPlayer player) {
		return AlayaMana.get(player).current();
	}

	private static void current(ServerPlayer player, double value) {
		AlayaMana.update(player, data -> data.withCurrent(value));
	}

	// Entities added to a chunk loaded this tick (the ground swords) stay hidden from world queries until its status reaches the entity manager.
	private static void prepare(ServerLevel level, BlockPos center, int radius) {
		UbwTests.prepare(level, center, radius);
		while (level.getChunkSource().pollTask()) {
		}
	}

	static ServerPlayer emiya(GameTestHelper helper) {
		ServerPlayer player = FateTests.servant(helper, START, 50_000, FateServants.EMIYA);
		TrueName.realize(player);
		field(helper, player);
		return player;
	}

	static void field(GameTestHelper helper, LivingEntity caster) {
		caster.setYRot(0);
		caster.setXRot(0);
		BlockPos center = UbwController.placement(caster, FateConfig.ubw());
		prepare(helper.getLevel(), center, FateConfig.ubw().radius());
		helper.getLevel().getServer().getCommands().performPrefixedCommand(helper.getLevel().getServer().createCommandSourceStack(),
			"forceload add " + (center.getX() - 48) + " " + (center.getZ() - 48) + " " + (center.getX() + 48) + " " + (center.getZ() + 48));
	}

	static UbwController.Status open(GameTestHelper helper, ServerPlayer owner) throws IOException {
		check(helper, "live registration/startup recovery/NP eligibility",
			UnlimitedBladeWorks.eligible(owner) && UbwRuntime.ready(helper.getLevel().getServer()));
		check(helper, "live default open", UbwRuntime.open(owner) == UbwController.OpenResult.OPENED);
		// These tests are about the standing field; openingSwapsBehindItsFrontThenRestores covers the opening itself.
		UbwRuntime.finishOpening(owner);
		return UbwController.of(helper.getLevel().getServer()).status(owner.getUUID()).orElseThrow();
	}

	static AABB bounds(UbwController.Status status) {
		Vec3 center = Vec3.atLowerCornerOf(status.center());
		int reach = status.radius() + 2;
		return new AABB(center.subtract(reach, reach, reach), center.add(reach, reach, reach));
	}

	static List<UbwGroundSwordEntity> ground(GameTestHelper helper, UbwController.Status status) {
		return helper.getLevel().getEntitiesOfClass(UbwGroundSwordEntity.class, bounds(status));
	}

	static List<ThrownWeaponEntity> shots(GameTestHelper helper, LivingEntity owner, UbwController.Status status) {
		return helper.getLevel().getEntitiesOfClass(ThrownWeaponEntity.class, bounds(status), shot -> owner.getUUID().equals(shot.owner()));
	}

	private static ServerPlayer incumbent(GameTestHelper helper, UbwController controller, Vec3 position, double strength) throws IOException {
		ServerPlayer player = FateTests.player(helper, START);
		player.setGameMode(GameType.SURVIVAL);
		FateTests.addMana(player, 50_000);
		player.setPos(position);
		player.setYRot(0);
		prepare(helper.getLevel(), UbwController.placement(player, SMALL), SMALL.radius());
		check(helper, "incumbent opens",
			controller.open(player, SMALL, player.blockPosition().getY() - 1, 1, PALETTE) == UbwController.OpenResult.OPENED);
		current(player, strength);
		return player;
	}

	@GameTest
	public void membershipDefaultsAndCraftabilityExceptions(GameTestHelper helper) {
		UbwConfig config = UbwConfig.DEFAULTS;
		check(helper, "full defaults", config.radius() == 25 && config.openingMana() == 3000 && config.upkeepManaPerSecond() == 25
			&& config.groundSwordsPerTrace() == 40 && config.maxGroundSwords() == 400);
		BlockPos center = new BlockPos(-5, 20, 7);
		Vec3 corner = Vec3.atLowerCornerOf(center);
		check(helper, "actual sphere not cube",
			UbwMembership.contains(center, 25, corner.add(25, 0, 0)) && !UbwMembership.contains(center, 25, corner.add(25, 0, 1)));
		check(helper, "swept velocity3 boundary",
			Math.abs(UbwMembership.entry(center, 25, corner.add(27, 0, 0), corner.add(24, 0, 0)) - 2.0 / 3) < 1e-8);
		check(helper, "already inside counter entry", UbwMembership.entry(center, 25, corner, corner.add(3, 0, 0)) == 0);
		check(helper, "nonrecipe divine weapons excluded", !UbwCraftability.craftable(helper.getLevel(), new ItemStack(FateWeapons.EA))
			&& !UbwCraftability.craftable(helper.getLevel(), new ItemStack(FateWeapons.EXCALIBUR)));
		check(helper, "recipe-output nonsword weapon allowed", UbwCraftability.craftable(helper.getLevel(), new ItemStack(Items.DIAMOND_AXE)));
		check(helper, "declared craftable exceptions", UbwCraftability.craftable(helper.getLevel(), new ItemStack(FateWeapons.CALIBURN))
			&& UbwCraftability.craftable(helper.getLevel(), new ItemStack(FateWeapons.KANSHOU))
			&& UbwCraftability.craftable(helper.getLevel(), new ItemStack(FateWeapons.BAKUYA)));
		helper.succeed();
	}

	private record Range(String id, double min, double max, double defaultValue) { }

	@GameTest
	public void settingsRangesAndDefaultsAreAsDesigned(GameTestHelper helper) {
		List<Range> expected = List.of(new Range("rate", 1, 100, 25), new Range("volley_1", 1, 400, 8), new Range("volley_2", 1, 400, 25),
			new Range("volley_3", 1, 400, 54), new Range("volley_4", 1, 400, 200), new Range("sword_size", 0.25, 4, 1),
			new Range("spread", 0.5, 3, 1), new Range("ground_amount", 0, 4, 1), new Range("ground_spread", 0.2, 1, 1),
			new Range("ground_tilt", 0, 30, 0));
		List<AbilitySetting> actual = new UnlimitedBladeWorks().settings();
		check(helper, "as many settings as expected", actual.size() == expected.size());
		for (int i = 0; i < expected.size(); i++) {
			Range range = expected.get(i);
			AbilitySetting setting = actual.get(i);
			check(helper, "setting " + range.id() + ": " + setting, setting.id().equals(range.id()) && setting.min() == range.min()
				&& setting.max() == range.max() && setting.defaultValue() == range.defaultValue());
		}
		helper.succeed();
	}

	@GameTest(environment = "fatecraft-gametest:isolated/feature_clash", padding = 64)
	public void clashChecksEveryIncumbentAndActualCreativeMana(GameTestHelper helper) throws IOException {
		UbwController controller = UbwController.of(helper.getLevel().getServer());
		Vec3 origin = helper.absoluteVec(new Vec3(4, 12, 4));
		ServerPlayer first = incumbent(helper, controller, origin, 100);
		ServerPlayer second = incumbent(helper, controller, origin.add(10, 0, 0), 300);
		ServerPlayer challenger = FateTests.player(helper, START);
		challenger.setGameMode(GameType.SURVIVAL);
		FateTests.addMana(challenger, 50_000);
		challenger.setPos(origin.add(5, 0, -2.5));
		challenger.setYRot(0);
		UbwConfig large = new UbwConfig(8, 10, 0);
		prepare(helper.getLevel(), UbwController.placement(challenger, large), large.radius());
		try {
			current(challenger, 200);
			check(helper, "stronger second incumbent blocks without evicting weaker first",
				controller.open(challenger, large, challenger.blockPosition().getY() - 1, 1, PALETTE) == UbwController.OpenResult.CLASH_LOST
					&& controller.status(first.getUUID()).isPresent() && controller.status(second.getUUID()).isPresent() && mana(challenger) == 200);
			current(challenger, 300);
			challenger.setGameMode(GameType.CREATIVE);
			check(helper, "creative infinity spending is NOT clash strength, tie keeps incumbent",
				controller.open(challenger, large, challenger.blockPosition().getY() - 1, 1, PALETTE) == UbwController.OpenResult.CLASH_LOST);
			challenger.setGameMode(GameType.SURVIVAL);
			current(challenger, 301);
			check(helper, "compare pre-cost, restore both before replacement",
				controller.open(challenger, large, challenger.blockPosition().getY() - 1, 1, PALETTE) == UbwController.OpenResult.OPENED
					&& controller.status(first.getUUID()).isEmpty() && controller.status(second.getUUID()).isEmpty() && mana(challenger) == 291);
		} finally {
			controller.cancel(first.getUUID());
			controller.cancel(second.getUUID());
			controller.cancel(challenger.getUUID());
		}
		helper.succeed();
	}

	@GameTest(environment = "fatecraft-gametest:isolated/feature_foreign_clash", padding = 64)
	public void clashChecksMarblesOfAnotherKindToo(GameTestHelper helper) throws IOException {
		UbwController controller = UbwController.of(helper.getLevel().getServer());
		ServerPlayer challenger = FateTests.player(helper, START);
		challenger.setGameMode(GameType.SURVIVAL);
		FateTests.addMana(challenger, 50_000);
		challenger.setPos(helper.absoluteVec(new Vec3(4, 12, 4)));
		challenger.setYRot(0);
		BlockPos center = UbwController.placement(challenger, SMALL);
		prepare(helper.getLevel(), center, SMALL.radius());
		ServerPlayer incumbent = FateTests.player(helper, START);
		incumbent.setGameMode(GameType.SURVIVAL);
		FateTests.addMana(incumbent, 50_000);
		current(incumbent, 300);
		FateTestMarbles.Marble foreign = FateTestMarbles.open(incumbent, Vec3.atLowerCornerOf(center), SMALL.radius());
		int floor = challenger.blockPosition().getY() - 1;
		try {
			current(challenger, 200);
			check(helper, "a stronger marble of another kind blocks the opening and stands",
				controller.open(challenger, SMALL, floor, 1, PALETTE) == UbwController.OpenResult.CLASH_LOST
					&& foreign.phase() == RealityMarble.Phase.STANDING && controller.status(challenger.getUUID()).isEmpty() && mana(challenger) == 200);
			current(challenger, 301);
			check(helper, "a weaker marble of another kind is closed for the opening, which compares mana before its cost",
				controller.open(challenger, SMALL, floor, 1, PALETTE) == UbwController.OpenResult.OPENED
					&& foreign.phase() == RealityMarble.Phase.CLOSING && mana(challenger) == 301 - SMALL.openingMana());
		} finally {
			controller.cancel(challenger.getUUID());
			foreign.close();
		}
		helper.succeed();
	}

	@GameTest(environment = "fatecraft-gametest:isolated/feature_restore", padding = 64)
	public void unfundedAndFailedRestoreCannotEvictOrSpend(GameTestHelper helper) throws IOException {
		UbwController controller = UbwController.of(helper.getLevel().getServer());
		Vec3 origin = helper.absoluteVec(new Vec3(4, 12, 4));
		ServerPlayer first = incumbent(helper, controller, origin, 1);
		ServerPlayer challenger = FateTests.player(helper, START);
		challenger.setGameMode(GameType.SURVIVAL);
		FateTests.addMana(challenger, 50_000);
		challenger.setPos(origin);
		challenger.setYRot(0);
		current(challenger, 9);
		check(helper, "unfunded stronger challenger leaves incumbent",
			controller.open(challenger, SMALL, challenger.blockPosition().getY() - 1, 1, PALETTE) == UbwController.OpenResult.INSUFFICIENT_MANA
				&& controller.status(first.getUUID()).isPresent() && mana(challenger) == 9);
		current(challenger, 20);
		UbwTerrain terrain = UbwTerrain.of(helper.getLevel().getServer());
		Path journal = terrain.checkpointPath();
		Path pending = journal.resolveSibling(journal.getFileName() + ".pending");
		Files.createDirectory(pending);
		try {
			boolean failed = false;
			try {
				controller.open(challenger, SMALL, challenger.blockPosition().getY() - 1, 1, PALETTE);
			} catch (IOException expected) {
				failed = true;
			}
			check(helper, "retirement IO error visible, no winner operation/spend/layering",
				failed && mana(challenger) == 20 && controller.status(challenger.getUUID()).isEmpty()
					&& controller.status(first.getUUID()).orElseThrow().phase() == UbwController.Phase.CLOSING
					&& controller.protects(helper.getLevel().dimension(), UbwController.placement(first, SMALL)));
		} finally {
			Files.delete(pending);
			terrain.reload();
			controller.cancel(first.getUUID());
			controller.cancel(challenger.getUUID());
		}
		helper.succeed();
	}

	@GameTest(environment = "fatecraft-gametest:isolated/feature_opening", setupTicks = 2, padding = 64, maxTicks = 100)
	public void openingSwapsBehindItsFrontThenRestores(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		ServerLevel level = helper.getLevel();
		UbwController controller = UbwController.of(level.getServer());
		BlockPos center = UbwController.placement(owner, FateConfig.ubw());
		Map<BlockPos, BlockState> standing = UbwLayout.sphere(level, center, FateConfig.ubw(), (int) Math.floor(owner.getY()) - 1, 1,
			Ubw.palette());
		Map<BlockPos, BlockState> originals = UbwTests.originals(level, standing);
		Vec3 origin = owner.position();
		int count = UbwRuntime.groundSwords(owner, FateConfig.ubw());
		check(helper, "live staged open", UbwRuntime.open(owner) == UbwController.OpenResult.OPENED);
		UbwController.Status status = controller.status(owner.getUUID()).orElseThrow();
		check(helper, "nothing replaced or standing before the ring ignites",
			matches(level, originals, standing.keySet()) && ground(helper, status).isEmpty());
		helper.runAfterDelay(UbwConfig.DEFAULTS.opening().chargeTicks() - 1, () -> check(helper, "nothing replaced or standing while the mana gathers",
			matches(level, originals, standing.keySet()) && ground(helper, status).isEmpty()));
		helper.runAfterDelay(UbwConfig.DEFAULTS.opening().chargeTicks() + UbwConfig.DEFAULTS.opening().spreadTicks() / 2, () -> {
			double front = controller.front(owner.getUUID());
			Set<BlockPos> behind = new HashSet<>();
			Set<BlockPos> ahead = new HashSet<>();
			for (BlockPos pos : standing.keySet()) {
				boolean passed = Math.hypot(pos.getX() + 0.5 - origin.x, pos.getZ() + 0.5 - origin.z) <= front;
				(passed ? behind : ahead).add(pos);
			}
			check(helper, "replaced behind the front, real world ahead of it",
				matches(level, standing, behind) && matches(level, originals, ahead));
			check(helper, "mid-opening world is half swapped: front=" + front,
				behind.stream().anyMatch(pos -> !standing.get(pos).equals(originals.get(pos)))
					&& ahead.stream().anyMatch(pos -> !standing.get(pos).equals(originals.get(pos))));
			List<UbwGroundSwordEntity> swords = ground(helper, status);
			check(helper, "swords stand only where the front has passed: " + swords.size(), !swords.isEmpty() && swords.size() < count
				&& swords.stream().allMatch(sword -> Math.hypot(sword.getX() - origin.x, sword.getZ() - origin.z) <= front));
		});
		helper.runAfterDelay(UbwConfig.DEFAULTS.opening().chargeTicks() + UbwConfig.DEFAULTS.opening().spreadTicks() + 1, () -> {
			check(helper, "whole field after the opening",
				matches(level, standing, standing.keySet()) && ground(helper, status).size() == count);
			check(helper, "close restores", UbwRuntime.cancel(owner) == UbwController.CloseResult.CLOSED
				&& matches(level, originals, originals.keySet()) && ground(helper, status).isEmpty());
			helper.succeed();
		});
	}

	static boolean matches(ServerLevel level, Map<BlockPos, BlockState> states, Set<BlockPos> positions) {
		return positions.stream().allMatch(pos -> level.getBlockState(pos).equals(states.get(pos)));
	}

	@GameTest(environment = "fatecraft-gametest:isolated/feature_ground", setupTicks = 2, padding = 64, maxTicks = 100)
	public void defaultGroundAnyoneFullInventoryOnePullAndFreeProject(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		double before = mana(owner);
		UbwController.Status status = open(helper, owner);
		check(helper, "default opening once", mana(owner) == before - FateConfig.ubw().openingMana());
		List<UbwGroundSwordEntity> props = ground(helper, status);
		int count = UbwRuntime.groundSwords(owner, FateConfig.ubw());
		check(helper, count + " ground props, the arsenal's; actual=" + props.size(), props.size() == count);
		for (UbwGroundSwordEntity prop : props) {
			check(helper, "true-size no-save traced prop: scale=" + prop.scale() + " pitch=" + prop.getXRot() + " serializable="
				+ prop.getType().canSerialize() + " sword=" + Traces.sword(prop.item()) + " known=" + Traces.known(owner) + " stack=" + prop.item(),
				prop.scale() == 1 && prop.getXRot() == 90 && !prop.getType().canSerialize() && Traces.sword(prop.item())
					&& Traces.known(owner).contains(BuiltInRegistries.ITEM.getKey(prop.item().getItem())) && Projections.isProjection(prop.item())
					&& !prop.item().isEnchanted());
		}
		check(helper, "spread and different traced types", props.stream().map(prop -> prop.blockPosition()).distinct().count() == count
			&& props.stream().map(prop -> prop.item().getItem()).distinct().count() == 3);
		ServerPlayer anyone = FateTests.player(helper, START);
		anyone.setGameMode(GameType.CREATIVE);
		for (int slotIndex = 0; slotIndex < 36; slotIndex++) {
			anyone.getInventory().setItem(slotIndex, new ItemStack(Items.STONE, 64));
		}
		UbwGroundSwordEntity slot = props.getFirst();
		check(helper, "full inventory even creative retains slot",
			slot.interact(anyone, InteractionHand.MAIN_HAND, slot.position()) == InteractionResult.PASS && !slot.isRemoved());
		anyone.setGameMode(GameType.SURVIVAL);
		anyone.getInventory().setItem(0, ItemStack.EMPTY);
		check(helper, "anyone gets one clean projection", slot.interact(anyone, InteractionHand.MAIN_HAND, slot.position()) == InteractionResult.SUCCESS
			&& Projections.isProjection(anyone.getInventory().getItem(0)) && anyone.getInventory().getItem(0).getCount() == 1 && slot.isRemoved());
		check(helper, "same slot cannot be pulled twice", slot.interact(anyone, InteractionHand.MAIN_HAND, slot.position()) == InteractionResult.PASS);
		var kanshou = new Project(FateWeapons.KANSHOU);
		check(helper, "both server costs zero inside", kanshou.startCost(owner, Project.PROJECT) == 0 && kanshou.startCost(owner, Project.TRACE_ON) == 0);
		owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		double afterOpen = mana(owner);
		FateTests.tap(owner, Project.key(BuiltInRegistries.ITEM.getKey(FateWeapons.KANSHOU)).identifier(), Project.PROJECT, false);
		check(helper, "actual paired hand projection is free", Projections.isProjection(owner.getMainHandItem())
			&& Projections.isProjection(owner.getOffhandItem()) && mana(owner) == afterOpen);
		check(helper, "Kanshou, Bakuya and Excalibur are hand-only: Trace On refuses them, the others (Caladbolg) are volleyed",
			kanshou.cannotStart(owner, Project.TRACE_ON) != null && new Project(FateWeapons.BAKUYA).cannotStart(owner, Project.TRACE_ON) != null
				&& new Project(FateWeapons.EXCALIBUR).cannotStart(owner, Project.TRACE_ON) != null
				&& new Project(FateWeapons.CALADBOLG).cannotStart(owner, Project.TRACE_ON) == null
				&& UbwRuntime.volleySwords(owner).stream().noneMatch(item -> !Traces.volleyable(item)) && !UbwRuntime.volleySwords(owner).isEmpty());
		FateTests.tap(owner, Project.key(BuiltInRegistries.ITEM.getKey(FateWeapons.CALADBOLG)).identifier(), Project.TRACE_ON, false);
		check(helper, "actual Trace On three non-GoB copies free", shots(helper, owner, status).size() == 3
			&& shots(helper, owner, status).stream().noneMatch(UbwSwords::incoming) && mana(owner) == afterOpen);
		helper.runAfterDelay(2, () -> {
			check(helper, "no regrowth", ground(helper, status).size() == count - 1);
			check(helper, "cancel free/unpicked removed",
				UbwRuntime.cancel(owner) == UbwController.CloseResult.CLOSED && ground(helper, status).isEmpty());
			check(helper, "legitimate pickup/hand projections survive close, costs restored",
				Projections.isProjection(anyone.getInventory().getItem(0)) && Projections.isProjection(owner.getMainHandItem())
					&& kanshou.startCost(owner, Project.TRACE_ON) > 0 && !UbwMembership.freeProjection(owner));
			helper.succeed();
		});
	}

	@GameTest(environment = "fatecraft-gametest:isolated/feature_protection", setupTicks = 2, padding = 64)
	public void retainedTerrainPlacementExplosionAndTeleportProtection(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		ServerLevel level = helper.getLevel();
		BlockPos center = UbwController.placement(owner, FateConfig.ubw());
		BlockPos retained = center.below(4);
		BlockState old = level.getBlockState(retained);
		level.setBlock(retained, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
		UbwController.Status status = open(helper, owner);
		try {
			check(helper, "retained lower blocks protected from weapon excavation and ordinary break", !WeaponBlocks.breakable(level, retained)
				&& !owner.gameMode.destroyBlock(retained) && level.getBlockState(retained).is(Blocks.GOLD_BLOCK));
			level.explode(null, retained.getX() + 0.5, retained.getY() + 0.5, retained.getZ() + 0.5, 4, Level.ExplosionInteraction.TNT);
			check(helper, "explosion preserves protected originals", level.getBlockState(retained).is(Blocks.GOLD_BLOCK));
			BlockPos primary = center.east(status.radius()).below();
			BlockPos support = primary.below();
			BlockState primaryOld = level.getBlockState(primary);
			BlockState supportOld = level.getBlockState(support);
			try {
				level.setBlock(primary, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				level.setBlock(support, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
				BlockPlaceContext door = new BlockPlaceContext(owner, InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_DOOR),
					new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false));
				check(helper, "actual door upper secondary protected with primary outside", door.getClickedPos().equals(primary)
					&& !UbwRuntime.protects(level, primary) && RealityMarbles.refusesPlacement(door, Blocks.OAK_DOOR.defaultBlockState()));
			} finally {
				level.setBlock(primary, primaryOld, Block.UPDATE_ALL);
				level.setBlock(support, supportOld, Block.UPDATE_ALL);
			}
			BlockPos bedFoot = center.west(status.radius() + 1);
			BlockState footOld = level.getBlockState(bedFoot);
			BlockState underOld = level.getBlockState(bedFoot.below());
			try {
				level.setBlock(bedFoot, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				level.setBlock(bedFoot.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
				BlockPlaceContext bed = new BlockPlaceContext(owner, InteractionHand.MAIN_HAND, new ItemStack(Items.RED_BED),
					new BlockHitResult(Vec3.atCenterOf(bedFoot.below()), Direction.UP, bedFoot.below(), false));
				check(helper, "actual bed head secondary protected with foot outside", bed.getClickedPos().equals(bedFoot)
					&& RealityMarbles.refusesPlacement(bed, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST)));
			} finally {
				level.setBlock(bedFoot, footOld, Block.UPDATE_ALL);
				level.setBlock(bedFoot.below(), underOld, Block.UPDATE_ALL);
			}
			owner.setPos(Vec3.atLowerCornerOf(center).add(status.radius() + 5, 0, 0));
			check(helper, "ordinary outside position isn't forced cancel; no free projection outside",
				UbwRuntime.hasMarble(owner) && !UbwMembership.freeProjection(owner));
			owner.teleportTo(owner.getX() + 1, owner.getY(), owner.getZ());
			check(helper, "actual player teleport closes props/environment/protection", !UbwRuntime.hasMarble(owner)
				&& ground(helper, status).isEmpty() && !UbwRuntime.protects(level, retained)
				&& level.getEntitiesOfClass(UbwEnvironmentEntity.class, bounds(status)).isEmpty());
		} finally {
			UbwRuntime.cancel(owner);
			level.setBlock(retained, old, Block.UPDATE_ALL);
		}
		helper.succeed();
	}

	@GameTest(environment = "fatecraft-gametest:isolated/feature_manual", setupTicks = 2, padding = 64, maxTicks = 250)
	public void noAutonomousOffenseWithHostilesThenActualManualFire(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		UbwController.Status status = open(helper, owner);
		ServerLevel level = helper.getLevel();
		Husk enemy = EntityType.HUSK.create(level, EntitySpawnReason.TRIGGERED);
		enemy.setNoAi(true);
		enemy.setNoGravity(true);
		enemy.setInvulnerable(true);
		enemy.setPos(Vec3.atLowerCornerOf(status.center()).add(4, 1, 0));
		level.addFreshEntity(enemy);
		List<ThrownWeaponEntity> manual = new ArrayList<>();
		helper.onEachTick(() -> {
			if (helper.getTick() <= 121) {
				check(helper, "no unsolicited offense over three forty-tick intervals with a live hostile",
					enemy.isAlive() && shots(helper, owner, status).isEmpty());
			}
		});
		helper.runAfterDelay(122, () -> {
			FateTests.tap(owner, UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.AOE_THROW, false);
			manual.addAll(shots(helper, owner, status));
			check(helper, "player input forms real held blue copies without golden gates",
				!manual.isEmpty() && manual.stream().allMatch(ThrownWeaponEntity::heldProjection)
					&& level.getEntitiesOfClass(GateEntity.class, bounds(status)).isEmpty());
		});
		helper.runAfterDelay(165, () -> FateTests.tap(owner, UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.AOE_THROW, false));
		helper.runAfterDelay(195, () -> {
			check(helper, "second player tap really releases manual projectiles", manual.stream().anyMatch(shot -> !shot.heldProjection()));
			check(helper, "opening ground swords do not regrow or disappear during idle/manual fire",
				level.getEntitiesOfClass(UbwGroundSwordEntity.class, bounds(status)).size() == UbwRuntime.groundSwords(owner, FateConfig.ubw()));
			UbwRuntime.cancel(owner);
			enemy.discard();
			helper.succeed();
		});
	}

	private static void choose(ServerPlayer owner, String setting, double value) {
		AbilitySettings.set(owner, UnlimitedBladeWorks.KEY.identifier(), setting, value);
	}

	@GameTest(environment = "fatecraft-gametest:isolated/feature_settings", setupTicks = 2, padding = 64, maxTicks = 100)
	public void ubwOwnSettingsSetVolleyAndSwordSizeNotGobs(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		UbwController.Status status = open(helper, owner);
		Vault vault = Vault.of(owner);
		vault.setSettings(vault.settings().withVolleyChanged(0, 3 - vault.settings().volley()).withGatesPerSecond(2));
		check(helper, "defaults: 25 a second, 8 swords in the first tier, full size, usual spread",
			UbwSettings.rate(owner) == 25 && UbwSettings.volley(owner) == 8 && UbwSettings.swordSize(owner) == 1.0F
				&& UbwSettings.spread(owner) == 1.0);
		FateTests.tap(owner, UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.AOE_THROW, false);
		List<ThrownWeaponEntity> first = shots(helper, owner, status);
		check(helper, "default volley of 8 full-size swords, whatever the vault says; got " + first.size(),
			first.size() == 8 && first.stream().allMatch(shot -> shot.scale() == 1.0F));
		Abilities.end(owner, UnlimitedBladeWorks.KEY.identifier());
		helper.runAfterDelay(1, () -> {
			choose(owner, "volley_1", 5);
			choose(owner, "sword_size", 2.0);
			choose(owner, "spread", 2.0);
			check(helper, "chosen values read back, the rest stay default", UbwSettings.volley(owner) == 5 && UbwSettings.swordSize(owner) == 2.0F
				&& UbwSettings.spread(owner) == 2.0 && UbwSettings.rate(owner) == 25);
			FateTests.tap(owner, UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.AOE_THROW, false);
			List<ThrownWeaponEntity> second = shots(helper, owner, status);
			check(helper, "chosen volley of 5 swords twice the size, box too; got " + second.size(), second.size() == 5
				&& second.stream().allMatch(shot -> shot.scale() == 2.0F && shot.getBbWidth() > first.getFirst().getBbWidth()));
			Abilities.end(owner, UnlimitedBladeWorks.KEY.identifier());
			UbwRuntime.cancel(owner);
			helper.succeed();
		});
	}

	@GameTest(environment = "fatecraft-gametest:isolated/feature_rate", setupTicks = 2, padding = 64, maxTicks = 200)
	public void ubwRateSettingSetsFiringSpeed(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		UbwController.Status status = open(helper, owner);
		choose(owner, "rate", 1);
		FateTests.tap(owner, UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.AOE_THROW, false);
		int formed = FateConfig.trace().formationTicks() + GateOfBabylon.VOLLEY_OPENS_OVER + 2;
		helper.runAfterDelay(formed, () -> FateTests.tap(owner, UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.AOE_THROW, false));
		// The first sword leaves on the second's 20th ability tick. A tick's scheduled callbacks (this check, the tap, and the
		// servant's per-tick ability run) come out of a hash map in no fixed order, so check a tick later: 20 to 22 ticks, never 19.
		helper.runAfterDelay(formed + SharedConstants.TICKS_PER_SECOND + 1, () -> {
			long released = shots(helper, owner, status).stream().filter(shot -> !shot.heldProjection()).count();
			check(helper, "1 a second releases one or two swords in a second, not the whole volley; got " + released,
				released >= 1 && released <= 2);
			Abilities.end(owner, UnlimitedBladeWorks.KEY.identifier());
			UbwRuntime.cancel(owner);
			helper.succeed();
		});
	}

	private static Set<Item> standing(List<UbwGroundSwordEntity> swords) {
		Set<Item> items = new HashSet<>();
		for (UbwGroundSwordEntity sword : swords) {
			items.add(sword.item().getItem());
		}
		return items;
	}

	@GameTest(environment = "fatecraft-gametest:isolated/feature_arsenal", setupTicks = 2, padding = 64, maxTicks = 100)
	public void groundSwordsFollowTheArsenalNeverDivine(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		UbwConfig config = FateConfig.ubw();
		owner.setAttached(Traces.STORAGE, List.of(BuiltInRegistries.ITEM.getKey(FateWeapons.EA)));
		Traces.learn(owner, new ItemStack(FateWeapons.EA));
		int small = UbwRuntime.swords(owner).size();
		List<UbwGroundSwordEntity> few = ground(helper, open(helper, owner));
		check(helper, "a small arsenal stands its share of each sword; got " + few.size() + " for " + small,
			few.size() == Math.min(config.maxGroundSwords(), config.groundSwordsPerTrace() * small));
		check(helper, "no divine construct among the few", standing(few).stream().noneMatch(item -> Traces.divine(item.getDefaultInstance())));
		check(helper, "closes", UbwRuntime.cancel(owner) == UbwController.CloseResult.CLOSED);
		for (Item sword : MORE_SWORDS) {
			Traces.learn(owner, new ItemStack(sword));
		}
		int large = UbwRuntime.swords(owner).size();
		List<UbwGroundSwordEntity> many = ground(helper, open(helper, owner));
		check(helper, "more traces stand more swords; got " + many.size() + " for " + large, large == small + MORE_SWORDS.length
			&& many.size() == Math.min(config.maxGroundSwords(), config.groundSwordsPerTrace() * large) && many.size() > few.size());
		check(helper, "every traced sword stands, Kanshou and Bakuya too, and no divine construct",
			standing(many).equals(Set.copyOf(UbwRuntime.swords(owner))) && standing(many).contains(FateWeapons.KANSHOU)
				&& standing(many).contains(FateWeapons.BAKUYA) && !standing(many).contains(FateWeapons.EA));
		UbwRuntime.cancel(owner);
		helper.succeed();
	}

	@GameTest(environment = "fatecraft-gametest:isolated/feature_ground_settings", setupTicks = 2, padding = 64, maxTicks = 100)
	public void groundSettingsSetAmountSpreadAndTilt(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		UbwConfig config = FateConfig.ubw();
		check(helper, "defaults: the arsenal's count, the whole field, upright",
			UbwSettings.groundAmount(owner) == 1.0 && UbwSettings.groundSpread(owner) == 1.0 && UbwSettings.groundTilt(owner) == 0.0);
		List<UbwGroundSwordEntity> upright = ground(helper, open(helper, owner));
		check(helper, "default swords stand upright", !upright.isEmpty() && upright.stream().allMatch(sword -> sword.lean(1) == 0));
		check(helper, "closes", UbwRuntime.cancel(owner) == UbwController.CloseResult.CLOSED);
		double amount = 2.0;
		double spread = 0.5;
		double tilt = 30.0;
		choose(owner, "ground_amount", amount);
		choose(owner, "ground_spread", spread);
		choose(owner, "ground_tilt", tilt);
		UbwController.Status status = open(helper, owner);
		List<UbwGroundSwordEntity> chosen = ground(helper, status);
		Vec3 origin = UbwController.of(helper.getLevel().getServer()).origin(owner.getUUID());
		double span = helper.getLevel().getEntitiesOfClass(UbwEnvironmentEntity.class, bounds(status)).getFirst().span();
		check(helper, "the amount multiplies the arsenal's count; got " + chosen.size(), chosen.size() == Math.min(config.maxGroundSwords(),
			Math.round(config.groundSwordsPerTrace() * UbwRuntime.swords(owner).size() * amount)) && chosen.size() > upright.size());
		check(helper, "all stand within the chosen share of the field from where he stood",
			chosen.stream().allMatch(sword -> Math.hypot(sword.getX() - origin.x, sword.getZ() - origin.z) <= spread * span));
		check(helper, "they lean up to the chosen tilt, not all upright",
			chosen.stream().allMatch(sword -> sword.lean(1) >= 0 && sword.lean(1) <= tilt) && chosen.stream().anyMatch(sword -> sword.lean(1) > 0));
		UbwRuntime.cancel(owner);
		helper.succeed();
	}

	@GameTest
	public void theCounterRecognizesThrownWeaponsByTheirTag(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		check(helper, "the Gate's weapon is listed", UbwSwords.incoming(new TreasureEntity(Gates.TREASURE, level)));
		check(helper, "another mod's listed weapon is counterable", UbwSwords.incoming(new ThrownWeaponEntity(FateTestThrown.COUNTERABLE, level)));
		check(helper, "an unlisted thrown weapon is not", !UbwSwords.incoming(new ThrownWeaponEntity(FateWeapons.THROWN_WEAPON, level)));
		helper.succeed();
	}

	@GameTest(environment = "fatecraft-gametest:isolated/feature_counter", setupTicks = 2, padding = 64, maxTicks = 150)
	public void actualGobSweptCounterPreservesEnchantedOriginalAndLearnsAxe(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		UbwController.Status status = open(helper, owner);
		ServerLevel level = helper.getLevel();
		ServerPlayer gil = FateTests.gilgamesh(helper, START, 50_000);
		gil.setPos(Vec3.atLowerCornerOf(status.center()).add(status.radius() + 12, 2, 0));
		gil.setYRot(-90);
		gil.setXRot(0);
		Vault vault = Vault.of(gil);
		vault.setSettings(vault.settings().withVolleyChanged(0, 1 - vault.settings().volley()).withReturnSeconds(2));
		ItemStack originalStack = new ItemStack(Items.DIAMOND_AXE);
		originalStack.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 3);
		vault.section(0).insert(originalStack.copy());
		Abilities.press(gil, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, false);
		TreasureEntity[] original = {null};
		long[] counterAt = {-1};
		int initialTraces = Traces.known(owner).size();
		helper.onEachTick(() -> {
			if (original[0] == null) {
				List<TreasureEntity> gob = level.getEntitiesOfClass(TreasureEntity.class, gil.getBoundingBox().inflate(32),
					shot -> gil.getUUID().equals(shot.owner()));
				if (gob.isEmpty()) {
					return;
				}
				Abilities.release(gil, GateOfBabylon.KEY.identifier());
				original[0] = gob.getFirst();
				check(helper, "actual dispatched GoB WEAPON provenance", UbwSwords.incoming(original[0]) && GateThrows.weapon(original[0].item()));
				original[0].setPos(Vec3.atLowerCornerOf(status.center()).add(status.radius() + 2, 2, 0));
				original[0].setDeltaMovement(-3, 0, 0);
				original[0].tick();
				List<ThrownWeaponEntity> counters = shots(helper, owner, status);
				check(helper, "one exact enchanted opposing projectile at swept shell boundary", counters.size() == 1
					&& ItemStack.isSameItemSameComponents(counters.getFirst().item(), originalStack)
					&& counters.getFirst().getDeltaMovement().x > 0 && !original[0].isRemoved());
				original[0].tick();
				check(helper, "pending sweep does not duplicate counter", shots(helper, owner, status).size() == 1);
				counterAt[0] = helper.getTick();
			}
			if (counterAt[0] >= 0 && helper.getTick() == counterAt[0] + FateConfig.trace().formationTicks() + 3) {
				check(helper, "clash marks original, keeps enchantment/durability, learns one permanent nonsword trace",
					UbwSwords.countered(original[0]) && ItemStack.isSameItemSameComponents(original[0].item(), originalStack)
						&& Traces.known(owner).size() == initialTraces + 1
						&& Traces.known(owner).contains(BuiltInRegistries.ITEM.getKey(Items.DIAMOND_AXE)) && shots(helper, owner, status).isEmpty());
				UbwRuntime.cancel(owner);
				owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				FateTests.tap(owner, Project.key(BuiltInRegistries.ITEM.getKey(Items.DIAMOND_AXE)).identifier(), Project.PROJECT, false);
				check(helper, "after closure craftable axe hand projection is clean/finite, not ordinary Trace On",
					owner.getMainHandItem().is(Items.DIAMOND_AXE) && Projections.isProjection(owner.getMainHandItem())
						&& !owner.getMainHandItem().isEnchanted() && owner.getMainHandItem().getMaxDamage() == FateConfig.trace().uses()
						&& new Project(Items.DIAMOND_AXE).cannotStart(owner, Project.TRACE_ON) != null);
			}
		});
		helper.succeedWhen(() -> {
			check(helper, "counter really happened and original returned once", counterAt[0] >= 0 && UbwSwords.countered(original[0])
				&& original[0].isRemoved() && !UbwRuntime.hasMarble(owner) && vault.section(0).entries().size() == 1
				&& vault.section(0).entries().getFirst().count() == 1
				&& ItemStack.isSameItemSameComponents(vault.section(0).entries().getFirst().item(), originalStack));
		});
	}

	@GameTest(environment = "fatecraft-gametest:isolated/feature_eligibility", setupTicks = 2, padding = 64, maxTicks = 100)
	public void actualNpEligibilityHoldReleaseAndZeroManaToggle(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		double before = mana(owner);
		TrueName.unrealize(owner);
		FateTests.tap(owner, UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.MARBLE, false);
		check(helper, "unrealized NP refuses before charge/construction", !UbwRuntime.hasMarble(owner) && mana(owner) == before);
		TrueName.realize(owner);
		FateTests.tap(owner, UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.MARBLE, false);
		check(helper, "actual NP toggle opens once, tap/release doesn't close",
			UbwRuntime.hasMarble(owner) && mana(owner) == before - FateConfig.ubw().openingMana());
		UbwController.Status status = UbwController.of(helper.getLevel().getServer()).status(owner.getUUID()).orElseThrow();
		Abilities.press(owner, UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.AOE_THROW, false);
		helper.runAfterDelay(30, () -> {
			check(helper, "held volley really launches traced copies", shots(helper, owner, status).stream().anyMatch(shot -> !shot.heldProjection()));
			Abilities.release(owner, UnlimitedBladeWorks.KEY.identifier());
			check(helper, "hold release ends firing only, never the toggled field", UbwRuntime.hasMarble(owner)
				&& UnlimitedBladeWorks.firingMode(owner) == -1 && shots(helper, owner, status).stream().noneMatch(ThrownWeaponEntity::heldProjection));
			current(owner, 0);
			FateTests.tap(owner, UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.MARBLE, false);
			check(helper, "zero mana cancellation allowed", !UbwRuntime.hasMarble(owner) && ground(helper, status).isEmpty() && mana(owner) == 0);
			helper.succeed();
		});
	}

	@GameTest(environment = "fatecraft-gametest:isolated/feature_cancel", setupTicks = 2, padding = 64, maxTicks = 100)
	public void heldFiringSwitchesToMarbleCancellationWithoutShotCosts(GameTestHelper helper) throws IOException {
		ServerPlayer owner = emiya(helper);
		UbwController.Status status = open(helper, owner);
		Vault.of(owner).setSettings(Vault.of(owner).settings().withReturnSeconds(1));
		double before = mana(owner);
		Abilities.press(owner, UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.AOE_THROW, false);
		check(helper, "four-mode ownership and blue held swords, no golden vault consumption",
			UnlimitedBladeWorks.firingMode(owner) == UnlimitedBladeWorks.AOE_THROW && !shots(helper, owner, status).isEmpty()
				&& shots(helper, owner, status).stream().allMatch(ThrownWeaponEntity::heldProjection) && mana(owner) == before);
		Abilities.release(owner, UnlimitedBladeWorks.KEY.identifier());
		double[] beforeFiring = {0};
		int[] firingRaw = {0};
		helper.runAfterDelay(25, () -> {
			// Waiting beyond the one-second copy return time must not expire the held volley.
			check(helper, "tap remains waiting beyond timeout", shots(helper, owner, status).stream().anyMatch(ThrownWeaponEntity::heldProjection));
			beforeFiring[0] = mana(owner);
			firingRaw[0] = helper.getLevel().getServer().getTickCount();
			FateTests.tap(owner, UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.AOE_THROW, false);
		});
		helper.runAfterDelay(28, () -> {
			check(helper, "waiting beyond timeout launches copies, only upkeep; actual mana=" + mana(owner) + " before=" + beforeFiring[0]
				+ " shots=" + shots(helper, owner, status).size(), shots(helper, owner, status).stream().anyMatch(shot -> !shot.heldProjection())
				&& Math.abs(mana(owner) - beforeFiring[0] + 3 * FateConfig.ubw().upkeepManaPerSecond() / SharedConstants.TICKS_PER_SECOND
					- ((helper.getLevel().getServer().getTickCount() - 1) / 10 - (firingRaw[0] - 1) / 10)
						* AlayaConfig.mana().regenPerSecond() * 0.5 * AlayaServants.manaRegenMultiplier(owner)) < 1e-7);
			Abilities.press(owner, UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.TARGETED_THROW, false);
			check(helper, "active ability-ID switch to the other firing mode",
				UnlimitedBladeWorks.firingMode(owner) == UnlimitedBladeWorks.TARGETED_THROW);
			Abilities.release(owner, UnlimitedBladeWorks.KEY.identifier());
			current(owner, 0);
			Abilities.press(owner, UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.MARBLE, false);
			check(helper, "MARBLE request cancels while firing/waiting without funding",
				!UbwRuntime.hasMarble(owner) && UnlimitedBladeWorks.firingMode(owner) == -1 && ground(helper, status).isEmpty());
			helper.succeed();
		});
	}
}
