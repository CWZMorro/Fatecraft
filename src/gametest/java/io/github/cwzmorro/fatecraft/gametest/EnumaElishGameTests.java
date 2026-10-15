package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;
import static io.github.cwzmorro.fatecraft.gametest.FateTests.mob;

import io.github.cwzmorro.alayacore.AlayaCore;
import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.beam.HeldBeam;
import io.github.cwzmorro.alayacore.combat.ContinuousSource;
import io.github.cwzmorro.alayacore.combat.ServantBonus;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.alayacore.servant.ServantChoice;
import io.github.cwzmorro.alayacore.servant.ServantStorage;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.weapon.EnumaElish;
import io.github.cwzmorro.fatecraft.weapon.EnumaElishBeam;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// The caster is a husk firing straight up, so the beam carves only sky above the test.
public class EnumaElishGameTests {
	private static final BlockPos CASTER = new BlockPos(3, 1, 3);
	private static final float STRAIGHT_UP = -90.0F;
	private static final int TARGET_HEIGHT = 8;
	private static final int SHIELD_HEIGHT = 5;
	private static final int FIRING_TICKS = 12;
	private static final int TEST_TICKS = FateTests.EA_WIND_UP_TICKS + FIRING_TICKS + 2;
	private static final int BEFORE_FIRING = 5;
	private static final double INPUT_EPSILON = 0.000001;
	private static final Rank SHIELD_RANK = Rank.parse("A");
	private static final double MARBLE_RADIUS = 2.0;
	private static final int MARBLE_CASTER_ASIDE = 2;
	private static final double IMPACT_EPSILON = 1e-6;
	private static final double HIT_EPSILON = 1e-3;
	private static final double SURVIVOR_HEALTH = 10_000.0;

	private static Husk caster(GameTestHelper helper) {
		Husk caster = mob(helper, CASTER);
		caster.setAttached(ServantStorage.CHOICE, ServantChoice.servant(AlayaRegistries.SERVANT.getKey(FateServants.GILGAMESH)));
		AlayaMana.getOrCreate(caster);
		caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FateWeapons.EA));
		caster.setXRot(STRAIGHT_UP);
		return caster;
	}

	private static void press(Husk caster) {
		Abilities.press(caster, EnumaElish.KEY.identifier(), 0, false);
	}

	@GameTest(maxTicks = TEST_TICKS)
	public void heldMovementRestoresOnRelease(GameTestHelper helper) {
		ServerPlayer player = FateTests.gilgamesh(helper, CASTER, 0);
		player.setGameMode(GameType.CREATIVE);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FateWeapons.EA));
		player.setXRot(STRAIGHT_UP);
		player.setSprinting(true);
		Abilities.press(player, EnumaElish.KEY.identifier(), 0, false);
		checkMovement(helper, player);
		helper.runAfterDelay(TEST_TICKS - 1, () -> {
			check(helper, "beam firing", !helper.getLevel().getEntitiesOfClass(EnumaElishBeam.class, player.getBoundingBox().inflate(4)).isEmpty());
			checkMovement(helper, player);
			Abilities.release(player, EnumaElish.KEY.identifier());
			check(helper, "restored input", !HeldBeam.restrictsMovement(player) && HeldBeam.movementMultiplier(player) == 1);
			player.setSprinting(true);
			check(helper, "sprint restored", player.isSprinting());
			helper.succeed();
		});
	}

	private static void checkMovement(GameTestHelper helper, ServerPlayer player) {
		check(helper, "configured bow input", HeldBeam.restrictsMovement(player)
			&& Math.abs(HeldBeam.movementMultiplier(player) - AlayaConfig.heldBeams().heldBeamMovementMultiplier()) < INPUT_EPSILON);
		check(helper, "sprint stopped", !player.isSprinting());
		player.setSprinting(true);
		check(helper, "sprint refused", !player.isSprinting());
		// Mock selection writes CHOICE directly, so its join-time freeze is still present; this fixture tests beam movement, not selection.
		player.getAttribute(Attributes.JUMP_STRENGTH).removeModifier(AlayaCore.id("choosing_servant"));
		player.setDeltaMovement(Vec3.ZERO);
		player.jumpFromGround();
		check(helper, "jump allowed", player.getDeltaMovement().y > 0);
	}

	@GameTest
	public void withoutEaInHandItIsRefused(GameTestHelper helper) {
		Husk caster = caster(helper);
		caster.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		press(caster);
		check(helper, "not cast", !Abilities.isActive(caster, EnumaElish.KEY.identifier()));
		helper.succeed();
	}

	@GameTest(maxTicks = TEST_TICKS)
	public void itWindsUpThenHitsAndCarvesWhatIsInItsPath(GameTestHelper helper) {
		Husk caster = caster(helper);
		Husk target = mob(helper, CASTER.above(TARGET_HEIGHT));
		BlockPos block = CASTER.above(TARGET_HEIGHT - 2);
		helper.setBlock(block, Blocks.STONE);
		press(caster);
		helper.runAfterDelay(FateConfig.ea().enumaElishWindUpTicks() - BEFORE_FIRING, () -> {
			check(helper, "no beam during the wind-up", helper.getLevel().getEntitiesOfClass(EnumaElishBeam.class, caster.getBoundingBox().inflate(4)).isEmpty());
			check(helper, "target untouched during the wind-up", target.getHealth() == target.getMaxHealth());
		});
		helper.runAfterDelay(TEST_TICKS - 1, () -> {
			check(helper, "a beam", !helper.getLevel().getEntitiesOfClass(EnumaElishBeam.class, caster.getBoundingBox().inflate(4)).isEmpty());
			check(helper, "target hit", target.getHealth() < target.getMaxHealth());
			helper.assertBlockNotPresent(Blocks.STONE, block);
			Abilities.release(caster, EnumaElish.KEY.identifier());
			check(helper, "cooldown after", Abilities.cooldownSecondsLeft(caster, EnumaElish.KEY.identifier(), 0) > 0);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = TEST_TICKS)
	public void eachHitAddsTheCastersClassAttackSplitOverTheSecond(GameTestHelper helper) {
		Husk caster = caster(helper);
		Mob target = helper.spawn(EntityType.IRON_GOLEM, CASTER.above(TARGET_HEIGHT));
		target.setNoAi(true);
		target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(SURVIVOR_HEALTH);
		target.setHealth((float) SURVIVOR_HEALTH);
		press(caster);
		double classAttack = ServantBonus.classAttack(caster);
		check(helper, "the caster is a servant with a class attack", classAttack > 0);
		helper.runAfterDelay(TEST_TICKS - 1, () -> {
			double perHit = ContinuousSource.perTick(AlayaConfig.ranks().beamDamagePerSecond(EnumaElish.RANK) + classAttack);
			double loss = target.getMaxHealth() - target.getHealth();
			check(helper, "hit", loss >= perHit - HIT_EPSILON);
			check(helper, "whole hits of the rank's rate plus the class attack's share: lost " + loss + ", a hit is " + perHit,
				Math.abs(loss / perHit - Math.rint(loss / perHit)) < HIT_EPSILON);
			Abilities.release(caster, EnumaElish.KEY.identifier());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = TEST_TICKS)
	public void creativeFiresItWithoutTheTrueNameOrMana(GameTestHelper helper) {
		ServerPlayer player = FateTests.gilgamesh(helper, CASTER, 0);
		player.setGameMode(GameType.CREATIVE);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FateWeapons.EA));
		player.setXRot(STRAIGHT_UP);
		// An Archer regenerates while casting, but far too little to pay for a beam: it fires only because creative pays nothing.
		AlayaMana.update(player, mana -> mana.withCurrent(0));
		Abilities.press(player, EnumaElish.KEY.identifier(), 0, false);
		check(helper, "cast", Abilities.isActive(player, EnumaElish.KEY.identifier()));
		helper.runAfterDelay(TEST_TICKS - 1, () -> {
			check(helper, "a beam", !helper.getLevel().getEntitiesOfClass(EnumaElishBeam.class, player.getBoundingBox().inflate(4)).isEmpty());
			Abilities.release(player, EnumaElish.KEY.identifier());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = TEST_TICKS)
	public void itBreaksAnyRealityMarbleItCrosses(GameTestHelper helper) {
		Husk caster = caster(helper);
		Vec3 center = helper.absoluteVec(Vec3.atCenterOf(CASTER.above(TARGET_HEIGHT)));
		FateTestMarbles.Marble marble = FateTestMarbles.open(mob(helper, CASTER.east(MARBLE_CASTER_ASIDE)), center, MARBLE_RADIUS);
		press(caster);
		helper.runAfterDelay(TEST_TICKS - 1, () -> {
			Vec3 impact = marble.impact;
			marble.close();
			check(helper, "the marble broke", impact != null);
			check(helper, "where the beam entered its shell, from below",
				Math.abs(impact.distanceTo(center) - MARBLE_RADIUS) < IMPACT_EPSILON && impact.y < center.y);
			Abilities.release(caster, EnumaElish.KEY.identifier());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = TEST_TICKS)
	public void aShieldStopsIt(GameTestHelper helper) {
		Husk caster = caster(helper);
		FateTestShields.Disc shield = helper.spawn(FateTestShields.DISC, Vec3.atCenterOf(CASTER.above(SHIELD_HEIGHT)));
		shield.setXRot(90.0F);
		shield.deploy(SHIELD_RANK);
		Husk behind = mob(helper, CASTER.above(TARGET_HEIGHT));
		BlockPos block = CASTER.above(TARGET_HEIGHT - 1);
		helper.setBlock(block, Blocks.STONE);
		press(caster);
		helper.runAfterDelay(TEST_TICKS - 1, () -> {
			check(helper, "the shield took it", shield.hp() < shield.maxHp());
			check(helper, "once each time a hit lands", shield.hitTimes.stream().distinct().count() == shield.hitTimes.size());
			check(helper, "nothing behind is hurt", behind.getHealth() == behind.getMaxHealth());
			helper.assertBlockPresent(Blocks.STONE, block);
			Abilities.release(caster, EnumaElish.KEY.identifier());
			helper.succeed();
		});
	}
}
