package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;
import static io.github.cwzmorro.fatecraft.gametest.FateTests.mob;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.combat.Aim;
import io.github.cwzmorro.alayacore.combat.AttackTags;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.FateGameRules;
import io.github.cwzmorro.fatecraft.gate.GateConfig;
import io.github.cwzmorro.fatecraft.gate.GateEntity;
import io.github.cwzmorro.fatecraft.gate.GateOfBabylon;
import io.github.cwzmorro.fatecraft.gate.GateReturns;
import io.github.cwzmorro.fatecraft.gate.GateThrows;
import io.github.cwzmorro.fatecraft.gate.Gates;
import io.github.cwzmorro.fatecraft.gate.TreasureEntity;
import io.github.cwzmorro.fatecraft.vault.Vault;
import io.github.cwzmorro.fatecraft.vault.VaultEntry;
import io.github.cwzmorro.fatecraft.vault.VaultSection;
import io.github.cwzmorro.fatecraft.vault.VaultSettings;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

// A gate outside the test's barrier walls throws into them, so tests needing one weapon to land use a volley of one gate.
public class GateOfBabylonGameTests {
	private static final BlockPos STANDING = new BlockPos(4, 1, 2);
	private static final BlockPos TARGET = new BlockPos(4, 1, 6);
	private static final float LOOKING_DOWN = 60.0F;
	private static final int VOLLEY = 8;
	private static final int SWORDS = 10;
	private static final int OPENED_TICKS = 40;
	private static final int THROWN_TICKS = 40;
	private static final float NETHERITE_SWORD = 8.0F;
	private static final float HUSK_ARMOR_TAKES_AT_MOST = 0.5F;
	private static final double SEARCH = 32.0;
	private static final double LIFT = 60.0;
	private static final double DRIFT = 0.05;
	private static final int PAST_OLD_FALL_TICKS = 140;
	private static final int DRIFT_RETURN_SECONDS = 10;
	private static final double GIANT = 2.0;
	private static final int GATE_OPEN_TICKS = 10;
	private static final int UNLOADED_RETURN_SECONDS = 2;
	private static final int TRIDENT_RETURN_SECONDS = 3;
	private static final int USED_UP_TICKS = 80;
	private static final int KEPT_TICKS = 100;
	private static final Vec3 SHIELD = new Vec3(4.5, 2.5, 5.5);
	private static final float FACING_HIM = 180.0F;
	private static final float FACING_AWAY = 0.0F;
	private static final Rank SHIELD_RANK = Rank.parse("B");
	private static final BlockPos IMMUNITY_TARGET = new BlockPos(1, 1, 6);
	private static final BlockPos IMMUNITY_FIRST_STANDING = new BlockPos(2, 1, 4);
	private static final BlockPos IMMUNITY_SECOND_STANDING = new BlockPos(5, 1, 4);
	private static final double SWORD_HEIGHT = 1.0;
	private static final double SWORD_RUN_UP = 1.5;
	private static final double SWORD_SPEED = 1.0;
	private static final int AFTER_LAST_HIT_TICKS = 3;
	private static final float HIT_EPSILON = 0.01F;
	// Immunity tests must not let vanilla hurt knockback lift the gravity-free target out of the next sword's ray.
	private static final double IMMUNITY_KNOCKBACK_RESISTANCE = 1.0;
	private static final double CLOSE_MANA = 0.000001;
	private static final double REGENERATED_MANA = 3.0;
	// After the release: long enough for a copy to be thrown, to land and to vanish where it did.
	private static final int COPY_TICKS = 30;

	private static ServerPlayer gilgamesh(GameTestHelper helper) {
		ServerPlayer player = FateTests.gilgamesh(helper, STANDING, FateTests.MANA);
		player.setXRot(LOOKING_DOWN);
		return player;
	}

	private static void fill(ServerPlayer player, Item item, int count) {
		for (int left = count; left > 0; left -= item.getDefaultMaxStackSize()) {
			Vault.of(player).section(0).insert(new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize())));
		}
	}

	private static int count(ServerPlayer player) {
		return Vault.of(player).section(0).entries().stream().mapToInt(VaultEntry::count).sum();
	}

	private static void oneGate(ServerPlayer player) {
		Vault vault = Vault.of(player);
		vault.setSettings(vault.settings().withVolleyChanged(0, 1 - vault.settings().volley()));
	}

	private static List<TreasureEntity> thrown(GameTestHelper helper, ServerPlayer player) {
		return helper.getLevel().getEntitiesOfClass(TreasureEntity.class, player.getBoundingBox().inflate(SEARCH),
			treasure -> player.getUUID().equals(treasure.owner()));
	}

	private static int openGates(GameTestHelper helper, ServerPlayer player) {
		return helper.getLevel().getEntitiesOfClass(GateEntity.class, player.getBoundingBox().inflate(SEARCH),
			gate -> player.getUUID().equals(gate.owner()) && !gate.item().isEmpty()).size();
	}

	private static FateTestShields.Disc throwAcrossShield(GameTestHelper helper, ServerPlayer player, float shieldYaw) {
		player.setXRot(0.0F);
		oneGate(player);
		fill(player, Items.GOLDEN_SWORD, 1);
		FateTestShields.Disc shield = helper.spawn(FateTestShields.DISC, SHIELD);
		shield.setYRot(shieldYaw);
		shield.deploy(SHIELD_RANK);
		Abilities.press(player, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, false);
		helper.runAfterDelay(OPENED_TICKS, () -> Abilities.release(player, GateOfBabylon.KEY.identifier()));
		return shield;
	}

	@GameTest
	public void onlyWeaponsArrowsAndThrowablesAreThrown(GameTestHelper helper) {
		for (Item item : FateTests.WEAPONS_AND_THROWABLES) {
			boolean expected = item != Items.BOW && item != Items.CROSSBOW;
			check(helper, item + (expected ? " is thrown" : " is not thrown"), GateThrows.throwable(new ItemStack(item)) == expected);
		}
		// Avalon, Emiya's bow and Bab-ilu are never thrown.
		for (Item item : List.of(Items.POTION, Items.DIRT, Items.APPLE, Items.SHIELD, FateWeapons.AVALON, FateWeapons.EMIYA_BOW, FateWeapons.BAB_ILU)) {
			check(helper, item + " is not thrown", !GateThrows.throwable(new ItemStack(item)));
		}
		helper.succeed();
	}

	@GameTest
	public void defaultsMatchTheDesignedValues(GameTestHelper helper) {
		GateConfig gate = GateConfig.DEFAULTS;
		check(helper, "5 mana a weapon", gate.manaPerWeapon() == 5.0);
		check(helper, "aim range 128", gate.aimRange() == 128.0);
		check(helper, "single target 12 to 20 blocks round the target", gate.singleTargetNearest() == 12.0 && gate.singleTargetFarthest() == 20.0);
		check(helper, "single target 20 to 90 degrees above the ground", gate.singleTargetMinDegrees() == 20.0 && gate.singleTargetMaxDegrees() == 90.0);
		VaultSettings settings = VaultSettings.DEFAULTS;
		check(helper, "four tiers of 8, 25, 54 and 200 gates", VaultSettings.TIERS == 4 && settings.volleys().equals(List.of(8, 25, 54, 200)));
		check(helper, "25 gates a second", settings.gatesPerSecond() == 25);
		check(helper, "back after a minute", settings.returnSeconds() == 60);
		check(helper, "12 chains", settings.chains() == 12);
		helper.succeed();
	}

	@GameTest(maxTicks = OPENED_TICKS + THROWN_TICKS + 1)
	public void everyItemThrownCostsTheFlatManaPerWeapon(GameTestHelper helper) {
		ServerPlayer player = gilgamesh(helper);
		Vault vault = Vault.of(player);
		List<Item> items = List.of(Items.IRON_SWORD, Items.ARROW, Items.SNOWBALL);
		vault.setSettings(vault.settings().withVolleyChanged(0, items.size() - vault.settings().volley()));
		for (Item item : items) {
			fill(player, item, 1);
		}
		double before = AlayaMana.get(player).current();
		FateTests.tap(player, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, false);
		helper.runAfterDelay(OPENED_TICKS, () -> {
			check(helper, "opening the gates is free", Math.abs(before - AlayaMana.get(player).current()) < CLOSE_MANA);
			FateTests.tap(player, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, false);
		});
		// The price is the most that was ever spent: regeneration gives some back once the volley is over, and may a little during it.
		double[] most = {0};
		helper.onEachTick(() -> most[0] = Math.max(most[0], before - AlayaMana.get(player).current()));
		helper.runAfterDelay(OPENED_TICKS + THROWN_TICKS, () -> {
			double price = items.size() * FateConfig.gate().manaPerWeapon();
			check(helper, "the same price for each of the " + items.size() + ", spent " + most[0],
				most[0] <= price + CLOSE_MANA && most[0] > price - REGENERATED_MANA);
			helper.succeed();
		});
	}

	// Sets a game rule, so it runs alone and puts the rule back at the end.
	@GameTest(environment = "fatecraft-gametest:isolated/gate_copies", maxTicks = OPENED_TICKS + COPY_TICKS + 1)
	public void withTheCopiesRuleTheVaultKeepsEverythingAndACopyVanishes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getGameRules().set(FateGameRules.GATE_OF_BABYLON_COPIES, true, level.getServer());
		ServerPlayer player = gilgamesh(helper);
		oneGate(player);
		fill(player, Items.GOLDEN_SWORD, 1);
		Abilities.press(player, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, false);
		boolean[] threwACopy = {false};
		// A copy can land and vanish on its first flight tick, so it is looked for as soon as it can have been thrown.
		Runnable lookForACopy = () -> thrown(helper, player).forEach(treasure -> threwACopy[0] |= treasure.getAttachedOrThrow(GateReturns.THROWN).copy());
		FateTests.afterAbilities(player, lookForACopy);
		helper.runAfterDelay(OPENED_TICKS, () -> {
			Abilities.release(player, GateOfBabylon.KEY.identifier());
			lookForACopy.run();
		});
		helper.runAfterDelay(OPENED_TICKS + COPY_TICKS, () -> {
			try {
				check(helper, "a copy was thrown", threwACopy[0]);
				check(helper, "the sword never left the vault", count(player) == 1);
				check(helper, "the copy vanished where it landed", thrown(helper, player).isEmpty());
			} finally {
				level.getGameRules().set(FateGameRules.GATE_OF_BABYLON_COPIES, false, level.getServer());
			}
			helper.succeed();
		});
	}

	@GameTest(maxTicks = OPENED_TICKS)
	public void withoutManaForOneWeaponNoGateOpens(GameTestHelper helper) {
		ServerPlayer player = FateTests.gilgamesh(helper, STANDING, 0.0);
		AlayaMana.spend(player, AlayaMana.available(player));
		fill(player, Items.IRON_SWORD, SWORDS);
		for (int mode : List.of(GateOfBabylon.AOE_THROW, GateOfBabylon.SINGLE_TARGET, GateOfBabylon.TARGETED_THROW)) {
			FateTests.tap(player, GateOfBabylon.KEY.identifier(), mode, false);
			check(helper, "no cast started in mode " + mode, !Abilities.isActive(player, GateOfBabylon.KEY.identifier()));
		}
		helper.runAfterDelay(GATE_OPEN_TICKS, () -> {
			check(helper, "no gate opened", openGates(helper, player) == 0);
			check(helper, "nothing left the vault", count(player) == SWORDS);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 120)
	public void anEnchantedTridentIsAGateWeaponThatGoesBack(GameTestHelper helper) {
		ServerPlayer player = gilgamesh(helper);
		Vault vault = Vault.of(player);
		vault.setSettings(vault.settings().withReturnSeconds(TRIDENT_RETURN_SECONDS));
		oneGate(player);
		ItemStack trident = new ItemStack(Items.TRIDENT);
		HolderLookup.RegistryLookup<Enchantment> enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		trident.enchant(enchantments.getOrThrow(Enchantments.LOYALTY), 3);
		vault.section(0).insert(trident.copy());
		check(helper, "a trident is a Gate weapon", GateThrows.weapon(trident));
		Abilities.press(player, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, false);
		helper.runAfterDelay(OPENED_TICKS, () -> {
			Abilities.release(player, GateOfBabylon.KEY.identifier());
			List<TreasureEntity> flying = thrown(helper, player);
			check(helper, "thrown as a Treasure: left " + count(player) + ", flying " + flying.size(), count(player) == 0 && flying.size() == 1);
			check(helper, "that UBW counters", flying.getFirst().is(AttackTags.COUNTERABLE_THROWN_WEAPONS));
		});
		helper.succeedWhen(() -> check(helper, "back in the vault with its enchantments", helper.getTick() > OPENED_TICKS && count(player) == 1
			&& ItemStack.isSameItemSameComponents(vault.section(0).entries().getFirst().stack(1), trident)));
	}

	private static void keptByAnotherPlayer(GameTestHelper helper, Item item, BiConsumer<ServerPlayer, ServerPlayer> pickup) {
		ServerPlayer player = gilgamesh(helper);
		ServerPlayer other = FateTests.player(helper, TARGET);
		Vault vault = Vault.of(player);
		vault.setSettings(vault.settings().withReturnSeconds(1));
		oneGate(player);
		fill(player, item, 1);
		Abilities.press(player, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, false);
		helper.runAfterDelay(OPENED_TICKS, () -> Abilities.release(player, GateOfBabylon.KEY.identifier()));
		boolean[] taken = {false};
		helper.onEachTick(() -> {
			if (!taken[0]) {
				// Lying in the floor, it is theirs to pick up; mock players don't touch things themselves.
				pickup.accept(player, other);
				taken[0] = other.getInventory().countItem(item) > 0;
			}
		});
		// Well past the return time (1 s), which the sweep would have acted on.
		helper.runAfterDelay(OPENED_TICKS + KEPT_TICKS, () -> {
			check(helper, "another player picked it up", taken[0]);
			check(helper, "it is still theirs", other.getInventory().countItem(item) == 1);
			check(helper, "and never came back to the vault", count(player) == 0);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = OPENED_TICKS + KEPT_TICKS + 1)
	public void aSwordAnotherPlayerPickedUpIsTheirsAndNeverGoesBack(GameTestHelper helper) {
		keptByAnotherPlayer(helper, Items.GOLDEN_SWORD, (owner, other) -> thrown(helper, owner).forEach(treasure -> treasure.playerTouch(other)));
	}

	@GameTest(maxTicks = OPENED_TICKS + KEPT_TICKS + 1)
	public void anArrowAnotherPlayerPickedUpIsTheirsAndNeverGoesBack(GameTestHelper helper) {
		keptByAnotherPlayer(helper, Items.ARROW, (owner, other) -> helper.getLevel().getEntitiesOfClass(AbstractArrow.class, owner.getBoundingBox().inflate(SEARCH),
			arrow -> arrow.hasAttached(GateReturns.THROWN) && arrow.getAttachedOrThrow(GateReturns.THROWN).owner().equals(owner.getUUID()))
			.forEach(arrow -> arrow.playerTouch(other)));
	}

	private static void usedUpAtTheTarget(GameTestHelper helper, Item item) {
		ServerPlayer player = gilgamesh(helper);
		Husk target = mob(helper, TARGET);
		player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
		Vault vault = Vault.of(player);
		vault.setSettings(vault.settings().withReturnSeconds(1));
		oneGate(player);
		fill(player, item, 1);
		FateTests.tap(player, GateOfBabylon.KEY.identifier(), GateOfBabylon.TARGETED_THROW, false);
		helper.runAfterDelay(OPENED_TICKS, () -> FateTests.tap(player, GateOfBabylon.KEY.identifier(), GateOfBabylon.TARGETED_THROW, false));
		helper.runAfterDelay(OPENED_TICKS + USED_UP_TICKS, () -> {
			check(helper, item + " was used up at the target, not returned", count(player) == 0);
			List<Entity> flying = helper.getLevel().getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(SEARCH),
				entity -> entity.hasAttached(GateReturns.THROWN) && entity.getAttachedOrThrow(GateReturns.THROWN).owner().equals(player.getUUID()));
			check(helper, item + " is not still flying", flying.isEmpty());
			if (item == Items.ARROW) {
				check(helper, "the arrow hit", target.getHealth() < target.getMaxHealth());
			}
			helper.succeed();
		});
	}

	@GameTest(maxTicks = OPENED_TICKS + USED_UP_TICKS + 1)
	public void anArrowUsedUpInItsTargetIsNotReturned(GameTestHelper helper) {
		usedUpAtTheTarget(helper, Items.ARROW);
	}

	@GameTest(maxTicks = OPENED_TICKS + USED_UP_TICKS + 1)
	public void aThrownPotionThatBurstsIsNotReturned(GameTestHelper helper) {
		usedUpAtTheTarget(helper, Items.SPLASH_POTION);
	}

	@GameTest(maxTicks = OPENED_TICKS + THROWN_TICKS + 1)
	public void aPressOpensTheVolleyAndASecondPressThrowsIt(GameTestHelper helper) {
		ServerPlayer player = gilgamesh(helper);
		fill(player, Items.IRON_SWORD, SWORDS);
		FateTests.tap(player, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, false);
		helper.runAfterDelay(OPENED_TICKS, () -> {
			check(helper, "a volley of 8 gates waits", openGates(helper, player) == VOLLEY);
			check(helper, "their swords left the vault", count(player) == SWORDS - VOLLEY);
			FateTests.tap(player, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, false);
		});
		helper.runAfterDelay(OPENED_TICKS + THROWN_TICKS, () -> {
			check(helper, "all thrown", openGates(helper, player) == 0);
			check(helper, "8 swords flew", thrown(helper, player).size() == VOLLEY);
			check(helper, "the cast is over", !Abilities.isActive(player, GateOfBabylon.KEY.identifier()));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void delayedTapsLeaveBothWallModesWaitingAndThenFire(GameTestHelper helper) {
		ServerPlayer aoe = gilgamesh(helper);
		ServerPlayer targeted = gilgamesh(helper);
		for (ServerPlayer player : List.of(aoe, targeted)) {
			fill(player, Items.IRON_SWORD, SWORDS);
		}
		// Start after the mock player's tick, as a network press does; tick zero also ticks mock abilities.
		helper.runAfterDelay(1, () -> {
			Abilities.press(aoe, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, false);
			Abilities.press(targeted, GateOfBabylon.KEY.identifier(), GateOfBabylon.TARGETED_THROW, false);
		});
		helper.runAfterDelay(Math.max(2, AlayaConfig.abilities().holdThresholdTicks() - 1), () -> {
			Abilities.release(aoe, GateOfBabylon.KEY.identifier());
			Abilities.release(targeted, GateOfBabylon.KEY.identifier());
		});
		helper.runAfterDelay(OPENED_TICKS * 3, () -> {
			for (ServerPlayer player : List.of(aoe, targeted)) {
				check(helper, "waiting without a timeout: gates=" + openGates(helper, player) + ", active=" + Abilities.isActive(player, GateOfBabylon.KEY.identifier()),
					openGates(helper, player) == VOLLEY && Abilities.isActive(player, GateOfBabylon.KEY.identifier()));
			}
			FateTests.tap(aoe, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, false);
			FateTests.tap(targeted, GateOfBabylon.KEY.identifier(), GateOfBabylon.TARGETED_THROW, false);
		});
		helper.runAfterDelay(OPENED_TICKS * 3 + THROWN_TICKS, () -> {
			for (ServerPlayer player : List.of(aoe, targeted)) {
				check(helper, "second tap fired the waiting volley: gates=" + openGates(helper, player) + ", thrown=" + thrown(helper, player).size()
					+ ", active=" + Abilities.isActive(player, GateOfBabylon.KEY.identifier()) + ", at " + player.position(),
					openGates(helper, player) == 0 && thrown(helper, player).size() == VOLLEY);
			}
			helper.succeed();
		});
	}

	@GameTest(maxTicks = OPENED_TICKS + 5)
	public void wallsFollowAndTargetedGatesTurnBeforeTheShot(GameTestHelper helper) {
		ServerPlayer aoe = gilgamesh(helper);
		ServerPlayer targeted = gilgamesh(helper);
		for (ServerPlayer player : List.of(aoe, targeted)) {
			oneGate(player);
			fill(player, Items.IRON_SWORD, 1);
		}
		FateTests.tap(aoe, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, false);
		FateTests.tap(targeted, GateOfBabylon.KEY.identifier(), GateOfBabylon.TARGETED_THROW, false);
		Vec3 step = new Vec3(1, 0, 0);
		List<GateEntity> gates = new ArrayList<>();
		List<Vec3> before = new ArrayList<>();
		helper.startSequence().thenIdle(OPENED_TICKS).thenExecute(() -> {
			gates.addAll(List.of(gate(helper, aoe), gate(helper, targeted)));
			before.addAll(gates.stream().map(Entity::position).toList());
			aoe.setPos(aoe.position().add(step));
			targeted.setPos(targeted.position().add(step));
			targeted.setXRot(0.0F);
		}).thenIdle(2).thenExecute(() -> {
			for (int index = 0; index < gates.size(); index++) {
				check(helper, "wall moved with its owner", gates.get(index).position().distanceTo(before.get(index).add(step)) < 0.001);
			}
			GateEntity gate = gates.getLast();
			Vec3 aim = Aim.point(targeted, FateConfig.gate().aimRange()).subtract(gate.position()).normalize();
			check(helper, "gate faces the current target before firing", gate.facing().distanceTo(aim) < 0.001);
		}).thenSucceed();
	}

	private static GateEntity gate(GameTestHelper helper, ServerPlayer player) {
		return helper.getLevel().getEntitiesOfClass(GateEntity.class, player.getBoundingBox().inflate(SEARCH),
			gate -> player.getUUID().equals(gate.owner())).getFirst();
	}

	@GameTest(maxTicks = OPENED_TICKS)
	public void endingTheCastPutsTheWaitingItemsBack(GameTestHelper helper) {
		ServerPlayer player = gilgamesh(helper);
		fill(player, Items.ARROW, SWORDS);
		FateTests.tap(player, GateOfBabylon.KEY.identifier(), GateOfBabylon.TARGETED_THROW, false);
		helper.runAfterDelay(OPENED_TICKS - 1, () -> {
			check(helper, "out of the vault while waiting", count(player) == SWORDS - VOLLEY);
			Abilities.endAll(player);
			check(helper, "back in", count(player) == SWORDS);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = OPENED_TICKS)
	public void discardingAWaitingGateReturnsItsItemOnlyOnce(GameTestHelper helper) {
		ServerPlayer player = gilgamesh(helper);
		oneGate(player);
		fill(player, Items.IRON_SWORD, 1);
		FateTests.tap(player, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, false);
		helper.runAfterDelay(OPENED_TICKS - 1, () -> {
			check(helper, "the sword is waiting in its gate", count(player) == 0);
			gate(helper, player).discard();
			check(helper, "discard returns the waiting sword", count(player) == 1);
			Abilities.endAll(player);
			check(helper, "ending the cast cannot return it again", count(player) == 1);
			helper.succeed();
		});
	}

	@GameTest
	public void shiftAndTheAbilityChangesTheTier(GameTestHelper helper) {
		ServerPlayer player = gilgamesh(helper);
		FateTests.tap(player, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, true);
		check(helper, "the second tier", Vault.of(player).settings().tier() == 1);
		check(helper, "nothing opened", openGates(helper, player) == 0 && !Abilities.isActive(player, GateOfBabylon.KEY.identifier()));
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void holdingThrowsUntilTheSectionIsEmpty(GameTestHelper helper) {
		ServerPlayer player = gilgamesh(helper);
		int swords = 30;
		fill(player, Items.STONE_SWORD, swords);
		Abilities.press(player, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, false);
		helper.succeedWhen(() -> {
			check(helper, "the section is empty", count(player) == 0);
			check(helper, "the cast is over", !Abilities.isActive(player, GateOfBabylon.KEY.identifier()));
			int flew = thrown(helper, player).size();
			check(helper, "every sword flew, " + flew, flew == swords);
		});
	}

	@GameTest(maxTicks = OPENED_TICKS + THROWN_TICKS + 1)
	public void aThrownSwordStrikesWithItsAttack(GameTestHelper helper) {
		ServerPlayer player = gilgamesh(helper);
		Husk target = mob(helper, TARGET);
		player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
		oneGate(player);
		fill(player, Items.NETHERITE_SWORD, 1);
		FateTests.tap(player, GateOfBabylon.KEY.identifier(), GateOfBabylon.TARGETED_THROW, false);
		helper.runAfterDelay(OPENED_TICKS, () -> FateTests.tap(player, GateOfBabylon.KEY.identifier(), GateOfBabylon.TARGETED_THROW, false));
		helper.runAfterDelay(OPENED_TICKS + THROWN_TICKS, () -> {
			float lost = target.getMaxHealth() - target.getHealth();
			check(helper, "lost about 8, lost " + lost, lost <= NETHERITE_SWORD && lost >= NETHERITE_SWORD - HUSK_ARMOR_TAKES_AT_MOST);
			ItemStack sword = thrown(helper, player).getFirst().item();
			check(helper, "worn by one hit", sword.getDamageValue() == 1);
			check(helper, "named the Gate's in death messages", target.getLastDamageSource() != null && target.getLastDamageSource().is(Gates.DAMAGE));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = OPENED_TICKS + PAST_OLD_FALL_TICKS + 100)
	public void aWeaponThatHitsNothingFliesStraightAndGoesBackWhenItsTimeIsUp(GameTestHelper helper) {
		ServerPlayer player = gilgamesh(helper);
		Vault vault = Vault.of(player);
		vault.setSettings(vault.settings().withReturnSeconds(DRIFT_RETURN_SECONDS));
		oneGate(player);
		fill(player, Items.GOLDEN_SWORD, 1);
		Abilities.press(player, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, false);
		helper.runAfterDelay(OPENED_TICKS, () -> {
			Abilities.release(player, GateOfBabylon.KEY.identifier());
			TreasureEntity treasure = thrown(helper, player).getFirst();
			treasure.setPos(treasure.position().add(0, LIFT, 0));
			treasure.setDeltaMovement(new Vec3(DRIFT, 0, 0));
		});
		helper.runAfterDelay(OPENED_TICKS + PAST_OLD_FALL_TICKS, () -> {
			List<TreasureEntity> flying = thrown(helper, player);
			check(helper, "still flying", flying.size() == 1);
			check(helper, "straight, not falling", flying.getFirst().getDeltaMovement().y == 0);
		});
		helper.succeedWhen(() -> check(helper, "back when its time was up", helper.getTick() > OPENED_TICKS + PAST_OLD_FALL_TICKS && count(player) == 1));
	}

	@GameTest(maxTicks = OPENED_TICKS + 80)
	public void aWeaponUnloadedFarAwayStillGoesBack(GameTestHelper helper) {
		ServerPlayer player = gilgamesh(helper);
		Vault vault = Vault.of(player);
		vault.setSettings(vault.settings().withReturnSeconds(UNLOADED_RETURN_SECONDS));
		oneGate(player);
		fill(player, Items.GOLDEN_SWORD, 1);
		Abilities.press(player, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, false);
		helper.runAfterDelay(OPENED_TICKS, () -> Abilities.release(player, GateOfBabylon.KEY.identifier()));
		// Unloaded as soon as it is thrown, as if it had flown out of every loaded chunk.
		boolean[] unloaded = {false};
		helper.onEachTick(() -> thrown(helper, player).forEach(treasure -> {
			treasure.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
			unloaded[0] = true;
		}));
		helper.succeedWhen(() -> check(helper, "back", unloaded[0] && count(player) == 1));
	}

	@GameTest(maxTicks = GATE_OPEN_TICKS + 1)
	public void aGiantsGatesAndWeaponsAreAsBigAsHeIs(GameTestHelper helper) {
		ServerPlayer player = gilgamesh(helper);
		player.getAttribute(Attributes.SCALE).setBaseValue(GIANT);
		oneGate(player);
		fill(player, Items.GOLDEN_SWORD, 1);
		Abilities.press(player, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, false);
		helper.runAfterDelay(GATE_OPEN_TICKS, () -> {
			List<GateEntity> gates = helper.getLevel().getEntitiesOfClass(GateEntity.class, player.getBoundingBox().inflate(SEARCH * GIANT),
				gate -> player.getUUID().equals(gate.owner()));
			check(helper, "a gate at his size", gates.size() == 1 && gates.getFirst().scale() == (float) GIANT);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = OPENED_TICKS + THROWN_TICKS)
	public void aGiantsWeaponsAndTheirBoxesAreAsBigAsHeIs(GameTestHelper helper) {
		ServerPlayer player = gilgamesh(helper);
		player.getAttribute(Attributes.SCALE).setBaseValue(GIANT);
		oneGate(player);
		fill(player, Items.GOLDEN_SWORD, 1);
		Abilities.press(player, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, false);
		helper.runAfterDelay(OPENED_TICKS, () -> Abilities.release(player, GateOfBabylon.KEY.identifier()));
		helper.succeedWhen(() -> {
			List<TreasureEntity> thrown = thrown(helper, player);
			check(helper, "thrown", !thrown.isEmpty());
			check(helper, "at his size", thrown.getFirst().scale() == (float) GIANT);
			check(helper, "its box too", thrown.getFirst().getBbWidth() == (float) (Gates.TREASURE.getWidth() * GIANT));
		});
	}

	@GameTest(maxTicks = 120)
	public void aLandedWeaponGoesBackToItsSection(GameTestHelper helper) {
		ServerPlayer player = gilgamesh(helper);
		Vault vault = Vault.of(player);
		vault.setSettings(vault.settings().withReturnSeconds(1));
		oneGate(player);
		fill(player, Items.GOLDEN_SWORD, 1);
		Abilities.press(player, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, false);
		helper.runAfterDelay(OPENED_TICKS, () -> {
			Abilities.release(player, GateOfBabylon.KEY.identifier());
			check(helper, "thrown", count(player) == 0 && thrown(helper, player).size() == 1);
		});
		helper.succeedWhen(() -> {
			VaultSection section = vault.section(0);
			check(helper, "back", count(player) == 1 && thrown(helper, player).isEmpty());
			check(helper, "not worn: it hit only the ground", section.entries().getFirst().item().getDamageValue() == 0);
		});
	}

	private static void twoSwordsStrikeApart(GameTestHelper helper, int gap, int expectedHits) {
		Husk target = mob(helper, IMMUNITY_TARGET);
		target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(IMMUNITY_KNOCKBACK_RESISTANCE);
		Vec3 targetPosition = target.position();
		ServerPlayer first = FateTests.gilgamesh(helper, IMMUNITY_FIRST_STANDING, FateTests.MANA);
		ServerPlayer second = FateTests.gilgamesh(helper, IMMUNITY_SECOND_STANDING, FateTests.MANA);
		for (ServerPlayer player : List.of(first, second)) {
			player.setXRot(0.0F);
			oneGate(player);
			fill(player, Items.IRON_SWORD, 1);
			Abilities.press(player, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, false);
		}
		helper.runAfterDelay(OPENED_TICKS, () -> {
			Abilities.release(first, GateOfBabylon.KEY.identifier());
			Abilities.release(second, GateOfBabylon.KEY.identifier());
		});
		long[] launchedAt = {-1};
		float[] firstLoss = {0.0F};
		List<TreasureEntity> aimedSwords = new ArrayList<>();
		long[] contactTicks = {-1, -1};
		helper.onEachTick(() -> {
			if (launchedAt[0] < 0) {
				List<TreasureEntity> swords = Stream.of(first, second).flatMap(player -> thrown(helper, player).stream()).toList();
				if (swords.size() == 2) {
					Vec3 start = helper.absoluteVec(Vec3.atCenterOf(IMMUNITY_TARGET)).add(0, SWORD_HEIGHT, -SWORD_RUN_UP);
					for (int index = 0; index < swords.size(); index++) {
						TreasureEntity sword = swords.get(index);
						check(helper, "sword is still flying before it is aimed", sword.flying()
							&& !sword.getAttached(GateReturns.THROWN).landed()
							&& sword.getDeltaMovement().y == 0);
						sword.setPos(start.subtract(0, 0, index * gap));
						sword.setDeltaMovement(0, 0, SWORD_SPEED);
					}
					aimedSwords.addAll(swords);
					launchedAt[0] = helper.getTick();
				}
			} else {
				for (int index = 0; index < aimedSwords.size(); index++) {
					// A real (non-copy) sword bounces downward after entity contact, even if hit immunity rejects its damage.
					if (contactTicks[index] < 0 && aimedSwords.get(index).getDeltaMovement().y < 0) {
						contactTicks[index] = helper.getTick();
					}
				}
				if (firstLoss[0] == 0.0F) {
					firstLoss[0] = target.getMaxHealth() - target.getHealth();
				}
			}
		});
		helper.succeedWhen(() -> {
			check(helper, "the swords are on their way", launchedAt[0] >= 0 && helper.getTick() >= launchedAt[0] + gap + AFTER_LAST_HIT_TICKS);
			float lost = target.getMaxHealth() - target.getHealth();
			check(helper, "target stayed on the swords' ray", target.position().distanceTo(targetPosition) < HIT_EPSILON);
			check(helper, "both swords contacted exactly " + gap + " ticks apart: " + contactTicks[0] + ", " + contactTicks[1],
				contactTicks[0] >= 0 && contactTicks[1] - contactTicks[0] == gap);
			check(helper, "the first hit landed", firstLoss[0] > 0.0F);
			check(helper, "lost " + lost + " after a first hit of " + firstLoss[0] + ", expected " + expectedHits + " hits",
				Math.abs(lost - firstLoss[0] * expectedHits) < HIT_EPSILON);
		});
	}

	@GameTest(maxTicks = OPENED_TICKS + THROWN_TICKS)
	public void aSwordOneTickAfterAnotherIsHeldToVanillasRule(GameTestHelper helper) {
		twoSwordsStrikeApart(helper, 1, 1);
	}

	@GameTest(maxTicks = OPENED_TICKS + THROWN_TICKS)
	public void swordsThreeTicksApartBothDealFullDamage(GameTestHelper helper) {
		twoSwordsStrikeApart(helper, 3, 2);
	}

	@GameTest(maxTicks = OPENED_TICKS + THROWN_TICKS)
	public void aShieldFacingTheThrowStopsTheWeaponAtItsDisc(GameTestHelper helper) {
		ServerPlayer player = gilgamesh(helper);
		FateTestShields.Disc shield = throwAcrossShield(helper, player, FACING_HIM);
		helper.succeedWhen(() -> {
			List<TreasureEntity> thrown = thrown(helper, player);
			check(helper, "thrown", thrown.size() == 1);
			check(helper, "the shield took one hit", shield.hp() < shield.maxHp() && shield.hitTimes.size() == 1);
			check(helper, "stopped in front of it", thrown.getFirst().getZ() <= shield.getZ());
		});
	}

	@GameTest(maxTicks = OPENED_TICKS + THROWN_TICKS)
	public void aWeaponThrownFromBehindAShieldGoesThrough(GameTestHelper helper) {
		ServerPlayer player = gilgamesh(helper);
		FateTestShields.Disc shield = throwAcrossShield(helper, player, FACING_AWAY);
		helper.succeedWhen(() -> {
			List<TreasureEntity> thrown = thrown(helper, player);
			check(helper, "thrown", thrown.size() == 1);
			check(helper, "past it", thrown.getFirst().getZ() > shield.getZ());
			check(helper, "the shield untouched", shield.hp() == shield.maxHp() && shield.hitTimes.isEmpty());
		});
	}
}
