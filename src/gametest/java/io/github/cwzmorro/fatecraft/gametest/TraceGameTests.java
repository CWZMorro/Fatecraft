package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.combat.Aim;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.item.WeaponStats;
import io.github.cwzmorro.alayacore.progression.Grand;
import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.alayacore.util.Percent;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.shield.RhoAias;
import io.github.cwzmorro.fatecraft.trace.ExcaliburStrain;
import io.github.cwzmorro.fatecraft.trace.Project;
import io.github.cwzmorro.fatecraft.trace.Projections;
import io.github.cwzmorro.fatecraft.trace.SwordBody;
import io.github.cwzmorro.fatecraft.trace.Trace;
import io.github.cwzmorro.fatecraft.trace.TraceConfig;
import io.github.cwzmorro.fatecraft.trace.Traces;
import io.github.cwzmorro.fatecraft.ubw.UbwRuntime;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

public class TraceGameTests {
	private static final BlockPos START = new BlockPos(4, 3, 4);
	private static final double EPS = 1e-6;
	private static final int DEATH_TICKS = 460;
	private static final int FLOOR_TICKS = 480;

	private static ServerPlayer emiya(GameTestHelper helper) {
		return FateTests.servant(helper, START, FateTests.MANA, FateServants.EMIYA);
	}

	private static void trace(ServerPlayer player, Item item) {
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
		FateTests.tap(player, Trace.KEY.identifier(), 0, false);
	}

	private static void project(ServerPlayer player, Item item, int mode) {
		FateTests.tap(player, Project.key(BuiltInRegistries.ITEM.getKey(item)).identifier(), mode, false);
	}

	@GameTest
	public void swordsOnlyEmiyaOnlyNoDuplicatesAndNewAbility(GameTestHelper helper) {
		ServerPlayer player = emiya(helper);
		int initial = Traces.known(player).size();
		trace(player, Items.DIAMOND_AXE);
		check(helper, "axe refused", Traces.known(player).size() == initial);
		trace(player, Items.DIAMOND_SWORD);
		trace(player, Items.DIAMOND_SWORD);
		check(helper, "one trace per item", Traces.known(player).size() == initial + 1);
		var key = Project.key(BuiltInRegistries.ITEM.getKey(Items.DIAMOND_SWORD));
		check(helper, "new menu entry owned", Abilities.owned(player).contains(key));
		check(helper, "two modes", Ability.modes(key.identifier()) == 2);
		ServerPlayer other = FateTests.gilgamesh(helper, START.offset(1, 0, 0), FateTests.MANA);
		trace(other, Items.DIAMOND_SWORD);
		check(helper, "other servant cannot learn", other.getAttachedOrElse(Traces.STORAGE, List.of()).isEmpty());
		check(helper, "other servant cannot project", !Abilities.owns(other, key.identifier()));
		trace(player, FateWeapons.HRUNTING);
		check(helper, "Fatecraft sword recognized without vanilla tag", Abilities.owns(player,
			Project.key(BuiltInRegistries.ITEM.getKey(FateWeapons.HRUNTING)).identifier()));
		check(helper, "bow and scabbard excluded", !Traces.sword(new ItemStack(FateWeapons.EMIYA_BOW)) && !Traces.sword(new ItemStack(FateWeapons.AVALON)));
		helper.succeed();
	}

	@GameTest
	public void projectCleanBaseAttackConfiguredUsesAndBusyRefusal(GameTestHelper helper) {
		ServerPlayer player = emiya(helper);
		ItemStack original = new ItemStack(Items.DIAMOND_SWORD);
		original.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 3);
		player.setItemInHand(InteractionHand.MAIN_HAND, original);
		FateTests.tap(player, Trace.KEY.identifier(), 0, false);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.APPLE));
		double before = AlayaMana.available(player);
		project(player, Items.DIAMOND_SWORD, Project.PROJECT);
		check(helper, "busy hand unchanged", player.getMainHandItem() == original);
		check(helper, "busy refusal charges nothing", AlayaMana.available(player) == before);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		project(player, Items.DIAMOND_SWORD, Project.PROJECT);
		ItemStack projected = player.getMainHandItem();
		check(helper, "main-hand projection", projected.is(Items.DIAMOND_SWORD) && Projections.isProjection(projected));
		check(helper, "base attack kept", WeaponStats.attackDamage(projected) == WeaponStats.attackDamage(new ItemStack(Items.DIAMOND_SWORD)));
		check(helper, "no enchantments", !projected.isEnchanted());
		int uses = FateConfig.trace().uses();
		check(helper, "configured uses", projected.getMaxDamage() == uses);
		var target = FateTests.mob(helper, START.offset(2, 0, 2));
		for (int hit = 1; hit < uses; hit++) {
			projected.hurtEnemy(target, player);
			check(helper, "wear once per hit", projected.getDamageValue() == hit && !projected.isEmpty());
		}
		projected.hurtEnemy(target, player);
		check(helper, "last use breaks", projected.isEmpty());
		ItemStack caladbolg = Projections.create(FateWeapons.CALADBOLG);
		player.setItemInHand(InteractionHand.MAIN_HAND, caladbolg);
		for (int hit = 0; hit < uses; hit++) {
			caladbolg.hurtEnemy(target, player);
		}
		check(helper, "originally non-durable Fatecraft sword also breaks", caladbolg.isEmpty());
		helper.succeed();
	}

	@GameTest
	public void aProjectedSwordIsNotWornByInstantBreakBlocks(GameTestHelper helper) {
		ServerPlayer player = emiya(helper);
		ItemStack projected = Projections.create(Items.DIAMOND_SWORD);
		BlockPos pos = helper.absolutePos(START);
		projected.mineBlock(helper.getLevel(), Blocks.TORCH.defaultBlockState(), pos, player);
		check(helper, "instant-break block does not wear", projected.getDamageValue() == 0);
		projected.mineBlock(helper.getLevel(), Blocks.STONE.defaultBlockState(), pos, player);
		check(helper, "other block wears one use", projected.getDamageValue() == 1);
		helper.succeed();
	}

	@GameTest
	public void costsAndStartingTraces(GameTestHelper helper) {
		ServerPlayer player = emiya(helper);
		check(helper, "three starting sword traces", Traces.known(player).size() == 3);
		for (Item item : List.of(FateWeapons.KANSHOU, FateWeapons.BAKUYA, FateWeapons.CALADBOLG)) {
			check(helper, "starting project", Abilities.owned(player).contains(Project.key(BuiltInRegistries.ITEM.getKey(item))));
		}
		check(helper, "Rho Aias remains available", Abilities.owned(player).contains(RhoAias.KEY));
		TraceConfig config = FateConfig.trace();
		check(helper, "paired cost", Projections.cost(FateWeapons.KANSHOU) == config.pairedCost() && Projections.cost(FateWeapons.BAKUYA) == config.pairedCost());
		check(helper, "Caladbolg cost", Projections.cost(FateWeapons.CALADBOLG) == config.caladbolgCost());
		check(helper, "other sword cost", Projections.cost(Items.DIAMOND_SWORD)
			== config.attackMultiplier() * WeaponStats.attackDamage(new ItemStack(Items.DIAMOND_SWORD)));
		check(helper, "loaded projection durability", Projections.create(FateWeapons.CALADBOLG).getMaxDamage() == config.uses());
		helper.succeed();
	}

	@GameTest
	public void singleProjectionPrefersMainThenOffhandWithoutReplacingItems(GameTestHelper helper) {
		ServerPlayer player = emiya(helper);
		trace(player, Items.DIAMOND_SWORD);
		for (Item ability : List.of(Items.DIAMOND_SWORD, FateWeapons.CALADBOLG)) {
			for (int busy = 0; busy < 4; busy++) {
				ItemStack main = (busy & 1) != 0 ? new ItemStack(FateWeapons.EMIYA_BOW) : ItemStack.EMPTY;
				ItemStack off = (busy & 2) != 0 ? new ItemStack(Items.APPLE) : ItemStack.EMPTY;
				player.setItemInHand(InteractionHand.MAIN_HAND, main);
				player.setItemInHand(InteractionHand.OFF_HAND, off);
				double before = AlayaMana.available(player);
				boolean canProject = Projections.emptyHands(player) > 0;
				project(player, ability, Project.PROJECT);
				if (canProject) {
					ItemStack projected = main.isEmpty() ? player.getMainHandItem() : player.getOffhandItem();
					check(helper, "clean projection in first empty hand", projected.is(ability) && Projections.isProjection(projected)
						&& !projected.isEnchanted() && projected.getMaxDamage() == FateConfig.trace().uses());
				}
				check(helper, "occupied main hand preserved", main.isEmpty() || player.getMainHandItem() == main);
				check(helper, "occupied offhand preserved", off.isEmpty() || player.getOffhandItem() == off);
				check(helper, "empty offhand stays empty when main is free", !off.isEmpty() || !main.isEmpty() || player.getOffhandItem().isEmpty());
				check(helper, "pay once or refuse without charge", Math.abs(before - AlayaMana.available(player)
					- (canProject ? Projections.cost(ability) : 0)) < EPS);
			}
		}
		helper.succeed();
	}

	@GameTest
	public void pairFillsOnlyEmptyHands(GameTestHelper helper) {
		ServerPlayer player = emiya(helper);
		for (Item ability : List.of(FateWeapons.KANSHOU, FateWeapons.BAKUYA)) {
			for (int busy = 0; busy < 4; busy++) {
				ItemStack main = (busy & 1) != 0 ? new ItemStack(Items.APPLE) : ItemStack.EMPTY;
				ItemStack off = (busy & 2) != 0 ? new ItemStack(FateWeapons.BAKUYA) : ItemStack.EMPTY;
				player.setItemInHand(InteractionHand.MAIN_HAND, main);
				player.setItemInHand(InteractionHand.OFF_HAND, off);
				double before = AlayaMana.available(player);
				int count = Projections.emptyHands(player);
				project(player, ability, Project.PROJECT);
				check(helper, "main preserved or Kanshou", main.isEmpty() ? player.getMainHandItem().is(FateWeapons.KANSHOU) : player.getMainHandItem() == main);
				check(helper, "off preserved or Bakuya", off.isEmpty() ? player.getOffhandItem().is(FateWeapons.BAKUYA) : player.getOffhandItem() == off);
				check(helper, "pay only swords created", Math.abs(before - AlayaMana.available(player) - count * Projections.cost(ability)) < EPS);
			}
		}
		check(helper, "golden durability", Projections.create(FateWeapons.KANSHOU).getMaxDamage() == new ItemStack(Items.GOLDEN_SWORD).getMaxDamage());
		helper.succeed();
	}

	@GameTest
	public void traceOnThreeTargetedCopiesAndTripleCost(GameTestHelper helper) {
		ServerPlayer player = emiya(helper);
		player.setYRot(0);
		player.setXRot(-45);
		Vec3 aim = Aim.point(player, FateConfig.trace().aimRange());
		double before = AlayaMana.available(player);
		project(player, FateWeapons.CALADBOLG, Project.TRACE_ON);
		var swords = helper.getLevel().getEntitiesOfClass(ThrownWeaponEntity.class, player.getBoundingBox().inflate(16), sword -> player.getUUID().equals(sword.owner()));
		check(helper, "three shots", swords.size() == FateConfig.trace().volley());
		check(helper, "triple cost", Math.abs(before - AlayaMana.available(player) - FateConfig.trace().volley() * Projections.cost(FateWeapons.CALADBOLG)) < EPS);
		for (ThrownWeaponEntity sword : swords) {
			check(helper, "marked clean projected sword", Projections.isProjection(sword.item()) && !sword.item().isEnchanted());
			check(helper, "Gate speed", Math.abs(sword.getDeltaMovement().length() - AlayaConfig.thrownWeapons().blocksPerTick()) < EPS);
			check(helper, "crosshair target", sword.getDeltaMovement().normalize().distanceTo(aim.subtract(sword.position()).normalize()) < EPS);
			Vec3 from = sword.position();
			check(helper, "configured formation delay", sword.formationRemaining() == FateConfig.trace().formationTicks());
			for (int tick = 0; tick < FateConfig.trace().formationTicks(); tick++) {
				sword.tick();
				check(helper, "stationary while forming", sword.position().equals(from));
			}
			sword.tick();
			check(helper, "original aimed flight after formation", sword.position().distanceTo(from.add(sword.getDeltaMovement())) < EPS);
			sword.discard();
		}
		helper.succeed();
	}

	@GameTest
	public void aMobsTraceOnFiresAtWhatItLooksAt(GameTestHelper helper) {
		Husk mob = FateTests.mobServant(helper, START, FateTests.MANA, FateServants.EMIYA);
		mob.setYRot(0);
		mob.setXRot(-45);
		Vec3 aim = Aim.point(mob, FateConfig.trace().aimRange());
		double before = AlayaMana.available(mob);
		Identifier caladbolg = Project.key(BuiltInRegistries.ITEM.getKey(FateWeapons.CALADBOLG)).identifier();
		Abilities.press(mob, caladbolg, Project.TRACE_ON, false);
		Abilities.release(mob, caladbolg);
		var swords = helper.getLevel().getEntitiesOfClass(ThrownWeaponEntity.class, mob.getBoundingBox().inflate(16), sword -> mob.getUUID().equals(sword.owner()));
		check(helper, "three shots, the mob's", swords.size() == FateConfig.trace().volley());
		check(helper, "triple cost", Math.abs(before - AlayaMana.available(mob) - FateConfig.trace().volley() * Projections.cost(FateWeapons.CALADBOLG)) < EPS);
		for (ThrownWeaponEntity sword : swords) {
			check(helper, "at what it looks at", sword.getDeltaMovement().normalize().distanceTo(aim.subtract(sword.position()).normalize()) < EPS);
			sword.discard();
		}
		mob.discard();
		helper.succeed();
	}

	@GameTest
	public void playerSaveReloadAndDeathCopyKeepTraces(GameTestHelper helper) {
		ServerPlayer player = emiya(helper);
		trace(player, Items.IRON_SWORD);
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
		player.saveWithoutId(output);
		ServerPlayer loaded = FateTests.player(helper, START.offset(1, 0, 0));
		loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult()));
		check(helper, "player disk reload retains traces", Traces.known(loaded).equals(Traces.known(player)));
		ServerPlayer respawned = helper.getLevel().getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
		check(helper, "death copy retains traces", Traces.known(respawned).equals(Traces.known(player)));
		check(helper, "death copy retains menu entry", Abilities.owned(respawned).contains(Project.key(BuiltInRegistries.ITEM.getKey(Items.IRON_SWORD))));
		helper.succeed();
	}

	@GameTest
	public void divineConstructEaCannotBeTracedOrKept(GameTestHelper helper) {
		ServerPlayer player = emiya(helper);
		int initial = Traces.known(player).size();
		Identifier ea = BuiltInRegistries.ITEM.getKey(FateWeapons.EA);
		check(helper, "Ea is tagged a divine construct", Traces.divine(new ItemStack(FateWeapons.EA)) && !Traces.divine(new ItemStack(FateWeapons.EXCALIBUR)));
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FateWeapons.EA));
		check(helper, "Trace refuses Ea in hand", new Trace().cannotStart(player, 0) != null);
		FateTests.tap(player, Trace.KEY.identifier(), 0, false);
		check(helper, "touching Ea learns nothing", Traces.known(player).size() == initial);
		Traces.learn(player, new ItemStack(FateWeapons.EA));
		check(helper, "learn refuses Ea", !Traces.known(player).contains(ea));
		// A stale or edited save that holds Ea is ignored everywhere, including the UBW's firing and ground swords.
		player.setAttached(Traces.STORAGE, List.of(ea, BuiltInRegistries.ITEM.getKey(Items.IRON_SWORD)));
		check(helper, "stored Ea is not known", !Traces.known(player).contains(ea) && !Abilities.owned(player).contains(Project.key(ea)));
		check(helper, "UBW never uses Ea or the strained Excalibur", UbwRuntime.swords(player).stream().noneMatch(item -> item == FateWeapons.EA || Traces.strained(item)));
		helper.succeed();
	}

	@GameTest
	public void aSecondDivineConstructIsRefusedByEveryTrace(GameTestHelper helper) {
		ServerPlayer player = emiya(helper);
		int initial = Traces.known(player).size();
		Item blade = FateTestWeapons.DIVINE_BLADE;
		Identifier id = BuiltInRegistries.ITEM.getKey(blade);
		check(helper, "a sword, and tagged a divine construct", Traces.sword(new ItemStack(blade)) && Traces.divine(new ItemStack(blade)));
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(blade));
		check(helper, "Trace refuses it in hand", new Trace().cannotStart(player, 0) != null);
		FateTests.tap(player, Trace.KEY.identifier(), 0, false);
		Traces.learn(player, new ItemStack(blade));
		check(helper, "nothing learned", Traces.known(player).size() == initial);
		player.setAttached(Traces.STORAGE, List.of(id));
		check(helper, "a stored one is not known and owns no Project", !Traces.known(player).contains(id) && !Abilities.owned(player).contains(Project.key(id)));
		check(helper, "the arsenal of Unlimited Blade Works leaves it out", !UbwRuntime.swords(player).contains(blade));
		helper.succeed();
	}

	@GameTest
	public void aSecondHandOnlyWeaponIsProjectedByHandAndNeverVolleyed(GameTestHelper helper) {
		ServerPlayer player = emiya(helper);
		Item blade = FateTestWeapons.HAND_ONLY_BLADE;
		trace(player, blade);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		check(helper, "learned", Traces.known(player).contains(BuiltInRegistries.ITEM.getKey(blade)));
		check(helper, "not volleyable", !Traces.volleyable(blade));
		Project project = new Project(blade);
		check(helper, "projectable into the hand", project.cannotStart(player, Project.PROJECT) == null);
		Component refusal = project.cannotStart(player, Project.TRACE_ON);
		check(helper, "Trace On refuses it as hand-only", refusal != null && refusal.getContents() instanceof TranslatableContents contents
			&& contents.getKey().equals("ability.fatecraft.trace.not_volley"));
		check(helper, "in his arsenal, but not shot by Unlimited Blade Works",
			UbwRuntime.swords(player).contains(blade) && !UbwRuntime.volleySwords(player).contains(blade));
		helper.succeed();
	}

	@GameTest
	public void excaliburIsTracedAndProjectedAtMostOfHisMana(GameTestHelper helper) {
		ServerPlayer player = emiya(helper);
		trace(player, FateWeapons.EXCALIBUR);
		check(helper, "Excalibur traced like any sword", Traces.known(player).contains(BuiltInRegistries.ITEM.getKey(FateWeapons.EXCALIBUR)));
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		double max = AlayaMana.get(player).max();
		double cost = max * Percent.toFraction(FateConfig.trace().excaliburManaPercent());
		AlayaMana.addCurrent(player, cost * 0.99 - AlayaMana.get(player).current());
		project(player, FateWeapons.EXCALIBUR, Project.PROJECT);
		check(helper, "refused below the share: nothing in hand, no drain", player.getMainHandItem().isEmpty() && !ExcaliburStrain.active(player));
		AlayaMana.addCurrent(player, max - AlayaMana.get(player).current());
		project(player, FateWeapons.EXCALIBUR, Project.PROJECT);
		check(helper, "projected at full mana", player.getMainHandItem().is(FateWeapons.EXCALIBUR) && Projections.isProjection(player.getMainHandItem()));
		double left = AlayaMana.get(player).current();
		check(helper, "took the configured share of max: left " + left + " of " + max, Math.abs(max - left - cost) < max * 0.001);
		check(helper, "drain started", ExcaliburStrain.active(player));
		helper.succeed();
	}

	@GameTest(maxTicks = DEATH_TICKS)
	public void excaliburDrainsFivePercentPerSecondThroughSwordBodyUntilDeath(GameTestHelper helper) {
		ServerPlayer player = emiya(helper);
		FateTests.tap(player, SwordBody.KEY.identifier(), 0, false);
		check(helper, "Sword Body on", SwordBody.active(player));
		ExcaliburStrain.begin(player);
		float max = player.getMaxHealth();
		player.setHealth(max);
		long start = helper.getLevel().getGameTime();
		helper.onEachTick(() -> ExcaliburStrain.tick(player));
		helper.runAfterDelay(SharedConstants.TICKS_PER_SECOND, () -> {
			double expected = max * (1 - Percent.toFraction(FateConfig.trace().excaliburDrainPercent()));
			check(helper, "after one second health " + player.getHealth() + ", expected " + expected + " (ticks " + (helper.getLevel().getGameTime() - start) + ")",
				Math.abs(player.getHealth() - expected) < max * 0.01 && ExcaliburStrain.active(player));
		});
		helper.succeedWhen(() -> check(helper, "dies of the drain", !player.isAlive() && !ExcaliburStrain.active(player)));
	}

	@GameTest(maxTicks = FLOOR_TICKS)
	public void excaliburDrainOfAGrandStopsAtTheHealthFloor(GameTestHelper helper) {
		ServerPlayer player = emiya(helper);
		Grand.qualify(player);
		check(helper, "is a Grand", Grand.isGrand(player));
		ExcaliburStrain.begin(player);
		player.setHealth(player.getMaxHealth());
		helper.onEachTick(() -> ExcaliburStrain.tick(player));
		helper.runAfterDelay(Durations.ticks(Percent.WHOLE / FateConfig.trace().excaliburDrainPercent()) + SharedConstants.TICKS_PER_SECOND * 2, () -> {
			double floor = player.getMaxHealth() * Percent.toFraction(FateConfig.trace().excaliburGrandFloorPercent());
			boolean held = player.isAlive() && Math.abs(player.getHealth() - floor) < 1e-3 && ExcaliburStrain.active(player);
			Grand.loseTitle(player);
			check(helper, "held at the floor " + floor + ", health " + player.getHealth(), held);
			helper.succeed();
		});
	}

	@GameTest
	public void excaliburDrainSurvivesSaveLoadAndEndsWithTheServant(GameTestHelper helper) {
		ServerPlayer player = emiya(helper);
		ExcaliburStrain.begin(player);
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
		player.saveWithoutId(output);
		ServerPlayer loaded = FateTests.player(helper, START.offset(1, 0, 0));
		loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult()));
		check(helper, "drain reloaded", ExcaliburStrain.active(loaded));
		ExcaliburStrain.tick(loaded);
		check(helper, "a tick does not clear it", ExcaliburStrain.active(loaded));
		FateTests.become(helper, player, FateServants.GILGAMESH);
		check(helper, "servant change ends it", !ExcaliburStrain.active(player));
		helper.succeed();
	}
}
