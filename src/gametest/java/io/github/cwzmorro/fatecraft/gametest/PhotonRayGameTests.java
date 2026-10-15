package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.combat.ContinuousSource;
import io.github.cwzmorro.alayacore.combat.ForcedAdvance;
import io.github.cwzmorro.alayacore.combat.ServantBonus;
import io.github.cwzmorro.alayacore.combat.WeaponBlocks;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.progression.TrueName;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.alayacore.shield.ShieldEntity;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.altera.Altera;
import io.github.cwzmorro.fatecraft.altera.AlteraConfig;
import io.github.cwzmorro.fatecraft.altera.PhotonRay;
import io.github.cwzmorro.fatecraft.altera.PhotonRayEntity;
import io.github.cwzmorro.fatecraft.altera.TeardropGeometry;
import io.github.cwzmorro.fatecraft.altera.TeardropPhotonRay;
import io.github.cwzmorro.fatecraft.altera.TeardropPhotonRayEntity;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.shield.FateShields;
import io.github.cwzmorro.fatecraft.weapon.EnumaElish;
import io.github.cwzmorro.fatecraft.weapon.EnumaElishBeam;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class PhotonRayGameTests {
	private static final BlockPos ORIGIN = new BlockPos(3, 8, 3);
	private static final BlockPos RUN_START = new BlockPos(64, 8, 12);
	private static final int TEST_TIMEOUT = 100;
	private static final double EPSILON = 0.01;
	private static final double RUN_SLACK = 2.5;
	private static final double DRAIN_SLACK_TICKS = 2;
	private static final int SETTLE_TICKS = 5;
	private static final int BESIDE_RUN = 3;
	private static final int TURN_TICKS = 5;
	private static final int JUMP_TICKS = 8;
	private static final double JUMP_HEIGHT = 0.5;
	private static final double TARGET_HP = 1_000.0;
	private static final int FIRST_TARGET = 8;
	private static final int SECOND_TARGET = 12;
	private static final int THIRD_TARGET = 16;
	private static final int STONE_AHEAD = 6;
	private static final int BEDROCK_AHEAD = 14;
	private static final int ATTACKER_AHEAD = 40;
	private static final int PRESS_WAIT = 45;
	private static final int SKY_TIMEOUT = 420;
	private static final int SECOND_TICKS = 20;
	private static final int SKY_TARGET_HEIGHT = 8;
	private static final String SKY_STRUCTURE = "fatecraft-gametest:teardrop";
	private static final int FIXTURE_LIFT = 64;
	private static final int LOWER_FIXTURE = 0, UPPER_FIXTURE = 1;
	private static final double MIDWAY = 0.5;
	private static final double BELL_TOP_SHARE = 0.01;
	private static final double BELL_OUTSIDE_SEAL = 1.25;
	private static final double BELL_GROUND_EDGE = 0.95;
	private static final double ARROW_SPEED = 2.0;
	private static final double ARROW_AHEAD = 4.0;
	private static final float LAST_HP = 0.5F;
	private static final double FIRING_SECONDS_AT_MOST = 1.5;
	private static final double PREVIOUS_DIG_BLOCKS_PER_SECOND = 4.0;
	private static final double MINIMUM_DIG_SLOWDOWN = 2.0;
	private static final int DIG_PROBE_MARGIN = 1;
	private static final int DIG_TIME_MULTIPLIER = 2;
	private static final int GROUND_HALF_WIDTH = 4;
	private static final int ROOF_OFFSET = 2;
	private static final int ROOF_HEIGHT = 3;
	private static final int ROOF_LAYERS = 3;
	private static final int COVER_MARGIN = 5;
	private static final int POOL_DEPTH = 2;
	private static final int POOL_HALF_WIDTH = 4;
	private static final int REFILL_SECONDS = 3;
	private static final int DEEP_POOL_HALF_WIDTH = 20;
	private static final int DEEP_POOL_BUDGET_TICKS = 3;
	private static final int BEAM_FRAME_TICKS = 5;
	private static final int BEAM_BREAK_WAIT = 150;
	private static final int BEAM_TIMEOUT = FateTests.EA_WIND_UP_TICKS + BEAM_BREAK_WAIT + SETTLE_TICKS;
	private static final int SHIELD_AHEAD = 10;
	private static final int BEHIND_SHIELD = 2;
	private static final double CARVE_REACH = 2.0;
	private static final float FACING_NORTH = 180.0F;
	private static final float FACING_UP = -90.0F;
	private static final Rank SHIELD_RANK = Rank.parse("EX");
	private static final int SHELTER_OFFSET = 8;

	private static double teardropPerSecond(ServerPlayer caster) {
		return AlayaConfig.ranks().beamDamagePerSecond(TeardropPhotonRay.RANK) + ServantBonus.classAttack(caster);
	}

	@GameTest
	public void twoSeparateNpsBelongToAltera(GameTestHelper helper) {
		check(helper, "both NPs granted", FateServants.ALTERA.abilities().contains(PhotonRay.KEY)
			&& FateServants.ALTERA.abilities().contains(TeardropPhotonRay.KEY));
		check(helper, "one dash mode", AlayaRegistries.ABILITY.getValue(PhotonRay.KEY.identifier()).modes() == 1);
		check(helper, "two Teardrop modes", AlayaRegistries.ABILITY.getValue(TeardropPhotonRay.KEY.identifier()).modes() == 2);
		check(helper, "description shows the merged blast's actual radius", new TeardropPhotonRay().descriptionArguments().getLast()
			.equals(TeardropGeometry.impactRadius(FateConfig.altera())));
		helper.succeed();
	}

	@GameTest
	public void defaultsMatchTheDesignedValues(GameTestHelper helper) {
		AlteraConfig defaults = AlteraConfig.DEFAULTS;
		check(helper, "ranks A and A++", PhotonRay.RANK.equals(Rank.parse("A")) && TeardropPhotonRay.RANK.equals(Rank.parse("A++")));
		check(helper, "Photon Ray melee 14 at 2.0", defaults.meleeDamage() == 14 && defaults.attackSpeed() == 2);
		check(helper, "dash 20 blocks a second, knockback 3, spin 90 degrees a tick", defaults.dashBlocksPerSecond() == 20
			&& defaults.knockbackDistance() == 3 && defaults.spinDegreesPerTick() == 90);
		check(helper, "cone 5 long and 2.1 wide, HP 0.75 of the rank A base", defaults.coneLength() == 5 && defaults.coneRadius() == 2.1
			&& defaults.coneHpFactor() == 0.75);
		check(helper, "Teardrop circles 80 blocks up, at most 2,048 blocks a tick", defaults.teardropHeight() == 80
			&& defaults.teardropBlocksPerTick() == 2048);
		check(helper, "digging 2 and 0.5 blocks a second, Spread 20% of the damage", defaults.teardropDigBlocksPerSecond() == 2
			&& defaults.spreadDigBlocksPerSecond() == 0.5 && defaults.spreadDamageMultiplier() == 0.2);
		check(helper, "Spread's beam about 58 blocks, with its impact about 75", Math.floor(TeardropGeometry.spreadRadius(defaults)) == 58
			&& Math.floor(TeardropGeometry.impactRadius(defaults)) == 75);
		helper.succeed();
	}

	@GameTest
	public void timeoutsCoverTheConfiguredTimings(GameTestHelper helper) {
		check(helper, "a second of running fits the run timeout", charge() + SECOND_TICKS < TEST_TIMEOUT);
		check(helper, "the lane holds the longest run a test makes", ATTACKER_AHEAD < FateTests.LANE_LENGTH
			&& FateTests.LANE_LENGTH >= (charge() + SECOND_TICKS) * FateConfig.altera().dashBlocksPerSecond() / SECOND_TICKS);
		check(helper, "the held beam's limit fits the sky timeout",
			skyWindUp() + AlayaConfig.ranks().beamMaxHoldSeconds() * SECOND_TICKS + SECOND_TICKS < SKY_TIMEOUT);
		helper.succeed();
	}

	@GameTest
	public void dashChargesForExactlyHalfASecond(GameTestHelper helper) {
		check(helper, "half-second default", AlteraConfig.DEFAULTS.chargeSeconds() == 0.5);
		ServerPlayer early = FateTests.altera(helper, ORIGIN, FateTests.ALTERA_MANA);
		TrueName.realize(early);
		Abilities.press(early, PhotonRay.KEY.identifier(), PhotonRay.DASH_MODE, false);
		check(helper, "spins immediately at press", early.getMainHandItem().has(FateWeapons.SPIN));
		int charge = Durations.ticks(FateConfig.altera().chargeSeconds());
		for (int tick = 0; tick < charge - 1; tick++) {
			Abilities.tick(early);
		}
		check(helper, "one tick too soon: she is not moving", !ForcedAdvance.active(early) && !dashRay(helper, early).dashing());
		Abilities.release(early, PhotonRay.KEY.identifier());
		check(helper, "one tick too soon cancels", !Abilities.isActive(early, PhotonRay.KEY.identifier()));
		double mana = AlayaMana.get(early).current();
		Abilities.press(early, PhotonRay.KEY.identifier(), PhotonRay.DASH_MODE, false);
		for (int tick = 0; tick < charge; tick++) {
			Abilities.tick(early);
		}
		check(helper, "no further wait once charged: she moves", dashRay(helper, early).dashing() && ForcedAdvance.active(early));
		check(helper, "starting to move costs nothing", AlayaMana.get(early).current() >= mana);
		Abilities.endAll(early);
		check(helper, "ending lets her go", !ForcedAdvance.active(early));
		helper.succeed();
	}

	// Runs alone: looking level, its Condensed beam slants up to the aim range away, out of its arena, where it would clash with the sky strikes of the tests beside it.
	@GameTest(maxTicks = SKY_TIMEOUT, structure = SKY_STRUCTURE, environment = "fatecraft-gametest:isolated/teardrop_follows_look")
	public void circlesStayLockedAndOnlyTheCondensedBeamFollowsTheLook(GameTestHelper helper) {
		ServerPlayer condensed = FateTests.altera(helper, fixture(helper, UPPER_FIXTURE), 0);
		ServerPlayer spread = FateTests.altera(helper, fixture(helper, LOWER_FIXTURE), 0);
		condensed.setGameMode(GameType.CREATIVE);
		spread.setGameMode(GameType.CREATIVE);
		Abilities.press(condensed, TeardropPhotonRay.KEY.identifier(), TeardropPhotonRay.CONDENSED_MODE, false);
		Abilities.press(spread, TeardropPhotonRay.KEY.identifier(), TeardropPhotonRay.SPREAD_MODE, false);
		Vec3 condensedSpot = sky(helper, condensed).position();
		Vec3 spreadSpot = sky(helper, spread).position();
		condensed.setXRot(0);
		spread.setXRot(0);
		helper.startSequence().thenIdle(skyWindUp() + SECOND_TICKS).thenExecute(() -> {
			for (ServerPlayer caster : new ServerPlayer[]{condensed, spread}) {
				TeardropPhotonRayEntity ray = sky(helper, caster);
				check(helper, "firing", ray.firing());
				Vec3 spot = caster == condensed ? condensedSpot : spreadSpot;
				check(helper, "spot locked from press", ray.position().equals(spot));
				check(helper, "sword flipped", caster.getMainHandItem().get(Altera.FLIP).flipped());
				caster.teleportTo(caster.getX() + insideSpread(), caster.getY(), caster.getZ());
				caster.setYRot(90);
			}
		}).thenIdle(SECOND_TICKS).thenExecute(() -> {
			check(helper, "condensed circles stay put", sky(helper, condensed).position().equals(condensedSpot));
			check(helper, "spread stays put", sky(helper, spread).position().equals(spreadSpot));
			check(helper, "condensed beam turned after the look", sky(helper, condensed).target().distanceTo(condensedSpot) > EPSILON);
			check(helper, "spread beam stays on its spot", sky(helper, spread).target().equals(spreadSpot));
			Abilities.release(condensed, TeardropPhotonRay.KEY.identifier());
			Abilities.release(spread, TeardropPhotonRay.KEY.identifier());
			check(helper, "flip reverses", !condensed.getMainHandItem().get(Altera.FLIP).flipped());
		}).thenSucceed();
	}

	@GameTest
	public void refusesBeforeTrueNameAndWithoutSword(GameTestHelper helper) {
		ServerPlayer player = FateTests.altera(helper, ORIGIN, FateTests.ALTERA_MANA);
		for (Identifier key : new Identifier[]{PhotonRay.KEY.identifier(), TeardropPhotonRay.KEY.identifier()}) {
			Abilities.press(player, key, 0, false);
			check(helper, "NP locked: " + key, !Abilities.isActive(player, key));
		}
		TrueName.realize(player);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		for (Identifier key : new Identifier[]{PhotonRay.KEY.identifier(), TeardropPhotonRay.KEY.identifier()}) {
			Abilities.press(player, key, 0, false);
			check(helper, "needs main-hand sword: " + key, !Abilities.isActive(player, key));
		}
		helper.succeed();
	}

	@GameTest(maxTicks = TEST_TIMEOUT, structure = SKY_STRUCTURE)
	public void creativeRunsWithoutTrueNameOrMana(GameTestHelper helper) {
		ServerPlayer player = FateTests.runner(helper, RUN_START, 0);
		player.setGameMode(GameType.CREATIVE);
		AlayaMana.update(player, mana -> mana.withCurrent(0));
		Vec3 start = player.position();
		FateTests.hold(player);
		check(helper, "creative cast starts", Abilities.isActive(player, PhotonRay.KEY.identifier()));
		// A sequence, unlike runAfterDelay, checks after the tick's own actions (her abilities and her run) have happened.
		helper.startSequence().thenIdle(charge() - 2).thenExecute(() -> check(helper, "charge survives: position=" + player.position()
			+ ", opening=" + player.getMainHandItem().get(FateWeapons.OPENING), Abilities.isActive(player, PhotonRay.KEY.identifier())
			&& player.position().equals(start))).thenIdle(SECOND_TICKS + 2).thenExecute(() -> {
			double run = player.position().distanceTo(start);
			check(helper, "a second of holding runs her a second's blocks: " + run,
				Math.abs(run - FateConfig.altera().dashBlocksPerSecond()) < RUN_SLACK);
			check(helper, "level, the way she looks (south)", Math.abs(player.getX() - start.x) < EPSILON && player.getZ() > start.z);
			check(helper, "creative running spends no mana", AlayaMana.get(player).current() == 0);
			check(helper, "still going while held", Abilities.isActive(player, PhotonRay.KEY.identifier()));
			FateTests.letGo(player);
			check(helper, "letting go ends it", !Abilities.isActive(player, PhotonRay.KEY.identifier()) && !ForcedAdvance.active(player));
			check(helper, "spin cleaned", !player.getMainHandItem().has(FateWeapons.SPIN));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = TEST_TIMEOUT, structure = SKY_STRUCTURE)
	public void runningCostsNothingToStartThenTheHeldBeamRatePerSecond(GameTestHelper helper) {
		ServerPlayer player = FateTests.runner(helper, RUN_START, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		double mana = AlayaMana.get(player).current();
		double perSecond = AlayaConfig.ranks().beamDrainPerSecond(PhotonRay.RANK);
		double slack = DRAIN_SLACK_TICKS * perSecond / SECOND_TICKS;
		double[] atSecond = {0};
		FateTests.hold(player);
		helper.startSequence().thenIdle(charge()).thenExecute(() -> {
			check(helper, "moving, nothing paid yet: " + (mana - AlayaMana.get(player).current()), ForcedAdvance.active(player)
				&& mana - AlayaMana.get(player).current() <= slack);
			atSecond[0] = AlayaMana.get(player).current();
		}).thenIdle(SECOND_TICKS).thenExecute(() -> {
			double spent = atSecond[0] - AlayaMana.get(player).current();
			check(helper, "a second of running costs the rank's held-beam rate: " + spent, Math.abs(spent - perSecond) <= slack);
			FateTests.letGo(player);
			atSecond[0] = AlayaMana.get(player).current();
		}).thenIdle(SETTLE_TICKS).thenExecute(() -> {
			check(helper, "letting go stops the drain", AlayaMana.get(player).current() >= atSecond[0]);
		}).thenSucceed();
	}

	@GameTest(maxTicks = TEST_TIMEOUT, structure = SKY_STRUCTURE)
	public void runningStopsWhenManaRunsOut(GameTestHelper helper) {
		ServerPlayer player = FateTests.runner(helper, RUN_START, 0);
		TrueName.realize(player);
		double perTick = AlayaConfig.ranks().beamDrainPerSecond(PhotonRay.RANK) / SECOND_TICKS;
		AlayaMana.update(player, mana -> mana.withCurrent(perTick * SETTLE_TICKS));
		FateTests.hold(player);
		helper.startSequence().thenIdle(charge() + 3 * SETTLE_TICKS).thenExecute(() -> {
			check(helper, "ended with the key still down", !Abilities.isActive(player, PhotonRay.KEY.identifier())
				&& !ForcedAdvance.active(player));
			check(helper, "she ran a little first", player.getZ() > helper.absoluteVec(Vec3.atBottomCenterOf(RUN_START)).z);
		}).thenSucceed();
	}

	@GameTest(maxTicks = TEST_TIMEOUT, structure = SKY_STRUCTURE)
	public void runningFollowsTheLookAndJumps(GameTestHelper helper) {
		ServerPlayer player = FateTests.runner(helper, RUN_START, 0);
		player.setGameMode(GameType.CREATIVE);
		double floor = player.getY();
		Vec3[] mark = {null};
		FateTests.hold(player);
		helper.startSequence().thenIdle(charge() + TURN_TICKS).thenExecute(() -> {
			mark[0] = player.position();
			player.setYRot(90.0F);
		}).thenIdle(TURN_TICKS).thenExecute(() -> {
			Vec3 turned = player.position().subtract(mark[0]);
			check(helper, "turned to the west with her look: " + turned, turned.x < -RUN_SLACK && Math.abs(turned.z) < turned.length() / 2);
			player.setYRot(0.0F);
			check(helper, "on the floor, able to jump", player.onGround());
			player.jumpFromGround();
			mark[0] = player.position();
		}).thenIdle(JUMP_TICKS).thenExecute(() -> {
			check(helper, "the jump takes her up: " + (player.getY() - floor), player.getY() - floor > JUMP_HEIGHT);
			check(helper, "and she keeps running", player.getZ() - mark[0].z > RUN_SLACK);
		}).thenIdle(SECOND_TICKS).thenExecute(() -> {
			check(helper, "she comes down to the floor under gravity", Math.abs(player.getY() - floor) < EPSILON);
			check(helper, "still running", Abilities.isActive(player, PhotonRay.KEY.identifier()));
			FateTests.letGo(player);
		}).thenSucceed();
	}

	@GameTest(maxTicks = TEST_TIMEOUT, structure = SKY_STRUCTURE)
	public void runningBreaksStoneWithoutDropsButPressesOnAtBedrock(GameTestHelper helper) {
		ServerPlayer player = FateTests.runner(helper, RUN_START, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		BlockPos stone = RUN_START.south(STONE_AHEAD);
		BlockPos bedrock = RUN_START.south(BEDROCK_AHEAD);
		for (int up = 0; up < 2; up++) {
			helper.setBlock(stone.above(up), Blocks.STONE);
			helper.setBlock(bedrock.above(up), Blocks.BEDROCK);
		}
		FateTests.hold(player);
		double[] atWall = {0};
		helper.runAfterDelay(charge() + PRESS_WAIT, () -> {
			helper.assertBlockNotPresent(Blocks.STONE, stone);
			helper.assertBlockNotPresent(Blocks.STONE, stone.above());
			helper.assertBlockPresent(Blocks.BEDROCK, bedrock);
			check(helper, "cannot run through unbreakable blocks", player.getZ() < helper.absolutePos(bedrock).getZ());
			check(helper, "at the wall she keeps running into it, the key still down", Abilities.isActive(player, PhotonRay.KEY.identifier())
				&& ForcedAdvance.active(player));
			check(helper, "no item drops", helper.getLevel().getEntitiesOfClass(ItemEntity.class,
				new AABB(Vec3.atCenterOf(helper.absolutePos(RUN_START)), Vec3.atCenterOf(helper.absolutePos(bedrock))).inflate(2)).isEmpty());
			atWall[0] = AlayaMana.get(player).current();
		});
		helper.runAfterDelay(charge() + PRESS_WAIT + SECOND_TICKS, () -> {
			check(helper, "still pressing on, still draining", Abilities.isActive(player, PhotonRay.KEY.identifier())
				&& AlayaMana.get(player).current() < atWall[0]);
			FateTests.letGo(player);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = TEST_TIMEOUT, structure = SKY_STRUCTURE)
	public void runPiercesThreeTargetsAndKnocksEachBackOnce(GameTestHelper helper) {
		ServerPlayer player = FateTests.runner(helper, RUN_START, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		Cow first = loose(helper, RUN_START.south(FIRST_TARGET));
		Cow second = loose(helper, RUN_START.south(SECOND_TARGET));
		Cow third = loose(helper, RUN_START.south(THIRD_TARGET));
		Husk beside = durable(helper, RUN_START.south(SECOND_TARGET).east(BESIDE_RUN));
		double[] before = {first.getZ(), second.getZ(), third.getZ(), beside.getZ()};
		FateTests.hold(player);
		helper.runAfterDelay(charge() + SECOND_TICKS, () -> {
			Cow[] targets = {first, second, third};
			for (int i = 0; i < targets.length; i++) {
				check(helper, "each pierced for at least rank A", TARGET_HP - targets[i].getHealth() >= PhotonRay.damage(player) - EPSILON);
				check(helper, "one pass does not repeat the pierce", TARGET_HP - targets[i].getHealth() < 2 * PhotonRay.damage(player));
				check(helper, "knocked on ahead of her: " + (targets[i].getZ() - before[i]), targets[i].getZ() - before[i] > 1);
			}
			FateTests.letGo(player);
			check(helper, "no burst at the stop: what she did not hit is untouched", beside.getHealth() == TARGET_HP && beside.getZ() == before[3]);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = TEST_TIMEOUT, structure = SKY_STRUCTURE)
	public void frontDamageContinuesEveryTick(GameTestHelper helper) {
		ServerPlayer player = FateTests.runner(helper, RUN_START, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		Husk target = durable(helper, RUN_START.south(4));
		helper.onEachTick(() -> target.setPos(player.position().add(0, PhotonRayEntity.CHEST_HEIGHT, PhotonRayEntity.SWORD_AHEAD + 1)));
		FateTests.hold(player);
		float[] before = {0};
		helper.startSequence().thenIdle(charge() + 3).thenExecute(() -> before[0] = target.getHealth())
			.thenIdle(1).thenExecute(() -> {
				check(helper, "front loses HP on next tick despite immunity timer", target.getHealth() < before[0]);
				check(helper, "front damage per tick, not a repeated piercing hit", before[0] - target.getHealth() < PhotonRay.damage(player));
			}).thenSucceed();
	}

	@GameTest(maxTicks = TEST_TIMEOUT, structure = SKY_STRUCTURE)
	public void aTargetIsKnockedBackOnlyOnceNotEveryTick(GameTestHelper helper) {
		ServerPlayer player = FateTests.runner(helper, RUN_START, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		Cow target = loose(helper, RUN_START.south(FIRST_TARGET));
		double[] pushed = {0};
		FateTests.hold(player);
		helper.startSequence().thenWaitUntil(() -> check(helper, "pierced and pushed on", target.getDeltaMovement().z > 0))
			.thenExecute(() -> pushed[0] = target.getDeltaMovement().z)
			.thenIdle(1).thenExecute(() -> check(helper, "the push only fades after it: " + pushed[0] + " then " + target.getDeltaMovement().z,
				pushed[0] > 0 && target.getDeltaMovement().z <= pushed[0]))
			.thenExecute(() -> FateTests.letGo(player)).thenSucceed();
	}

	@GameTest(maxTicks = TEST_TIMEOUT, structure = SKY_STRUCTURE)
	public void swordStopsFrontAttacksButNotBehind(GameTestHelper helper) {
		ServerPlayer player = FateTests.runner(helper, RUN_START, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		Husk front = durable(helper, RUN_START.south(ATTACKER_AHEAD));
		Husk behind = durable(helper, RUN_START.north(2));
		FateTests.hold(player);
		helper.runAfterDelay(charge() + 2, () -> {
			float before = player.getHealth();
			player.invulnerableTime = 0;
			player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(front), 10.0F);
			check(helper, "front intercepted", player.getHealth() == before);
			Husk side = durable(helper, RUN_START.east(8));
			side.setPos(player.position().add(8, PhotonRayEntity.CHEST_HEIGHT, 0));
			player.invulnerableTime = 0;
			player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(side), 10.0F);
			check(helper, "body covered from side", player.getHealth() == before);
			check(helper, "tip 2.5 ahead, back radius 2.1", PhotonRayEntity.SWORD_AHEAD == 2.5 && PhotonRayEntity.coneRadius() == 2.1);
			player.invulnerableTime = 0;
			player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(behind), 10.0F);
			check(helper, "behind hurts", player.getHealth() < before);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = TEST_TIMEOUT, structure = SKY_STRUCTURE)
	public void runPressesAgainstASolidShieldAndSparesWhatIsBehindIt(GameTestHelper helper) {
		ServerPlayer player = FateTests.runner(helper, RUN_START, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		ShieldEntity shield = facingNorth(helper.spawn(FateShields.RHO_AIAS, Vec3.atCenterOf(RUN_START.south(SHIELD_AHEAD))));
		Husk behind = durable(helper, RUN_START.south(SHIELD_AHEAD + BEHIND_SHIELD));
		FateTests.hold(player);
		helper.runAfterDelay(charge() + PRESS_WAIT, () -> {
			check(helper, "held before the shield: " + player.getZ() + " vs " + shield.getZ(), player.getZ() < shield.getZ());
			check(helper, "still running into it", Abilities.isActive(player, PhotonRay.KEY.identifier()) && ForcedAdvance.active(player));
			check(helper, "nothing behind it is hurt", behind.getHealth() == TARGET_HP);
			check(helper, "the shield took the hits", shield.hp() < shield.maxHp());
			FateTests.letGo(player);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = TEST_TIMEOUT, structure = SKY_STRUCTURE)
	public void aHeldShieldIsAManaExchangeAndBreaksWhenItsHolderCannotPay(GameTestHelper helper) {
		ServerPlayer player = FateTests.runner(helper, RUN_START, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		ServerPlayer holder = FateTests.gilgamesh(helper, RUN_START.south(SHIELD_AHEAD + BEHIND_SHIELD), FateTests.ALTERA_MANA);
		ShieldEntity shield = facingNorth(helper.spawn(FateShields.RHO_AIAS, Vec3.atCenterOf(RUN_START.south(SHIELD_AHEAD))));
		Husk wearer = durable(helper, RUN_START.south(ATTACKER_AHEAD));
		double[] holderMana = {0};
		boolean[] broke = {false};
		FateTests.hold(player);
		helper.onEachTick(() -> {
			if (broke[0]) {
				AlayaMana.update(holder, mana -> mana.withCurrent(0.0));
			} else if (!shield.isRemoved()) {
				shield.hold(holder);
			}
		});
		helper.startSequence().thenExecute(() -> holderMana[0] = AlayaMana.get(holder).current())
			.thenWaitUntil(() -> check(helper, "she has reached the shield", player.getZ() > shield.getZ() - CARVE_REACH))
			.thenIdle(SECOND_TICKS).thenExecute(() -> {
				check(helper, "while held it is up and she is held back", !shield.isRemoved() && player.getZ() < shield.getZ());
				check(helper, "she keeps running into it", Abilities.isActive(player, PhotonRay.KEY.identifier()));
				check(helper, "its holder pays: " + AlayaMana.get(holder).current(), AlayaMana.get(holder).current() < holderMana[0]);
				// Nothing left to repair it with: all but its last HP worn off at once, her next tick of contact breaks it.
				broke[0] = true;
				shield.hurtAt(helper.getLevel(), helper.getLevel().damageSources().mobAttack(wearer), (float) (shield.hp() - LAST_HP),
					shield.position());
			}).thenWaitUntil(() -> check(helper, "the shield breaks once its holder cannot pay", shield.isRemoved()))
			.thenIdle(SECOND_TICKS).thenExecute(() -> {
				check(helper, "she runs on through it: " + player.getZ() + " vs " + shield.getZ(), player.getZ() > shield.getZ());
				FateTests.letGo(player);
			}).thenSucceed();
	}

	@GameTest(maxTicks = TEST_TIMEOUT, structure = SKY_STRUCTURE)
	public void runKeepsTheBlocksBehindAShieldThatIsNotSolidWhileShesBeforeIt(GameTestHelper helper) {
		ServerPlayer player = FateTests.runner(helper, RUN_START, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		ShieldEntity shield = facingNorth(helper.spawn(FateTestShields.DISC, Vec3.atCenterOf(RUN_START.south(SHIELD_AHEAD))));
		BlockPos behind = RUN_START.south(SHIELD_AHEAD + 1);
		helper.setBlock(behind, Blocks.STONE);
		FateTests.hold(player);
		helper.startSequence().thenWaitUntil(() -> check(helper, "within the carve's reach of the shield", player.getZ() > shield.getZ() - CARVE_REACH))
			.thenExecute(() -> {
				helper.assertBlockPresent(Blocks.STONE, behind);
				FateTests.letGo(player);
			}).thenSucceed();
	}

	private static <T extends ShieldEntity> T facingNorth(T shield) {
		shield.setYRot(FACING_NORTH);
		shield.deploy(SHIELD_RANK);
		return shield;
	}

	@GameTest
	public void chargeLocksExactlyWhenReleaseCanFire(GameTestHelper helper) {
		ServerPlayer player = FateTests.altera(helper, ORIGIN, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		Vec3 start = player.position();
		double mana = AlayaMana.get(player).current();
		int needed = PhotonRay.chargeTicks();
		Abilities.press(player, PhotonRay.KEY.identifier(), PhotonRay.DASH_MODE, false);
		PhotonRayEntity early = dashRay(helper, player);
		check(helper, "cone present and empty at the start of the charge", !early.charged() && early.chargeProgress(0) == 0);
		check(helper, "sword spins at the configured rate", player.getMainHandItem().get(FateWeapons.SPIN).degreesPerTick()
			== (float) FateConfig.altera().spinDegreesPerTick());
		float before = 0;
		for (int tick = 0; tick < needed - 1; tick++) {
			Abilities.tick(player);
			check(helper, "charge grows every tick", early.chargeProgress(0) > before);
			before = early.chargeProgress(0);
		}
		check(helper, "not locked one tick before the charge completes", !early.charged() && early.chargeProgress(0) < 1);
		Abilities.release(player, PhotonRay.KEY.identifier());
		check(helper, "early release cancels the cast and the cone", !Abilities.isActive(player, PhotonRay.KEY.identifier()) && early.isRemoved());
		check(helper, "early release pays nothing", AlayaMana.get(player).current() == mana);
		check(helper, "early release closes the sword without moving her", !player.getMainHandItem().get(FateWeapons.OPENING).open()
			&& !player.getMainHandItem().has(FateWeapons.SPIN) && player.position().equals(start));
		Abilities.press(player, PhotonRay.KEY.identifier(), PhotonRay.DASH_MODE, false);
		PhotonRayEntity full = dashRay(helper, player);
		for (int tick = 0; tick < needed; tick++) {
			Abilities.tick(player);
		}
		check(helper, "locked once the charge completes", full.charged() && full.chargeProgress(0) == 1);
		check(helper, "a full charge is already moving her", full.dashing() && ForcedAdvance.active(player));
		Abilities.release(player, PhotonRay.KEY.identifier());
		check(helper, "letting go ends the run and the cone", !Abilities.isActive(player, PhotonRay.KEY.identifier()) && full.isRemoved()
			&& !ForcedAdvance.active(player));
		helper.succeed();
	}

	@GameTest
	public void tapsDoNothingAndCleanOpening(GameTestHelper helper) {
		ServerPlayer player = FateTests.altera(helper, ORIGIN, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		Vec3 before = player.position();
		FateTests.tap(player, PhotonRay.KEY.identifier(), PhotonRay.DASH_MODE, false);
		check(helper, "tap cancels charge and closes sword", !player.getMainHandItem().get(FateWeapons.OPENING).open());
		FateTests.tap(player, TeardropPhotonRay.KEY.identifier(), TeardropPhotonRay.CONDENSED_MODE, false);
		check(helper, "sky strike tap ends without moving", !Abilities.isActive(player, TeardropPhotonRay.KEY.identifier()) && player.position().equals(before));
		helper.succeed();
	}

	@GameTest(maxTicks = SKY_TIMEOUT)
	public void creativeSkyStrikeIsFreeAndEndsAfterFifteenSeconds(GameTestHelper helper) {
		ServerPlayer player = FateTests.altera(helper, ORIGIN, 0);
		player.setGameMode(GameType.CREATIVE);
		AlayaMana.update(player, mana -> mana.withCurrent(0));
		durable(helper, ORIGIN.above(SKY_TARGET_HEIGHT));
		Abilities.press(player, TeardropPhotonRay.KEY.identifier(), TeardropPhotonRay.CONDENSED_MODE, false);
		check(helper, "creative needs no true name", Abilities.isActive(player, TeardropPhotonRay.KEY.identifier()));
		helper.runAfterDelay(skyWindUp() + SECOND_TICKS, () -> {
			check(helper, "creative firing", sky(helper, player).firing());
			check(helper, "creative mana remains zero", AlayaMana.get(player).current() == 0);
		});
		int end = skyWindUp() + AlayaConfig.ranks().beamMaxHoldSeconds() * SECOND_TICKS;
		helper.runAfterDelay(end - 2, () -> check(helper, "still firing before limit", Abilities.isActive(player, TeardropPhotonRay.KEY.identifier())));
		helper.runAfterDelay(end + 2, () -> {
			check(helper, "ends at held beam limit", !Abilities.isActive(player, TeardropPhotonRay.KEY.identifier()));
			check(helper, "sword closes", !player.getMainHandItem().get(FateWeapons.OPENING).open());
			check(helper, "sky entity cleaned", helper.getLevel().getEntitiesOfClass(TeardropPhotonRayEntity.class,
				player.getBoundingBox().inflate(FateConfig.altera().teardropRange()), ray -> ray.owner() == player).isEmpty());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = SKY_TIMEOUT)
	public void skyWindUpThenAimedDamageAndReleaseCleanup(GameTestHelper helper) {
		ServerPlayer player = FateTests.altera(helper, ORIGIN, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		Husk target = durable(helper, ORIGIN.above(SKY_TARGET_HEIGHT));
		Abilities.press(player, TeardropPhotonRay.KEY.identifier(), TeardropPhotonRay.CONDENSED_MODE, false);
		helper.runAfterDelay(skyWindUp() - 2, () -> {
			check(helper, "wind-up cannot hurt", target.getHealth() == TARGET_HP);
			check(helper, "circles centered above living aim", sky(helper, player).position().distanceTo(target.getBoundingBox().getCenter()) < EPSILON);
			check(helper, "wind-up sword open", player.getMainHandItem().get(FateWeapons.OPENING).open());
		});
		helper.runAfterDelay(skyWindUp() + SECOND_TICKS, () -> {
			check(helper, "target hurt after wind-up", target.getHealth() < TARGET_HP);
			check(helper, "held A++ rate", TARGET_HP - target.getHealth()
				<= teardropPerSecond(player) * FIRING_SECONDS_AT_MOST + EPSILON);
			Abilities.release(player, TeardropPhotonRay.KEY.identifier());
			check(helper, "release ends", !Abilities.isActive(player, TeardropPhotonRay.KEY.identifier()));
			double cooldown = AlayaConfig.abilities().cooldownSeconds(TeardropPhotonRay.KEY.identifier(),
				AlayaRegistries.ABILITY.getValue(TeardropPhotonRay.KEY.identifier()), TeardropPhotonRay.CONDENSED_MODE);
			check(helper, "release starts configured mode cooldown", Math.abs(Abilities.cooldownSecondsLeft(player,
				TeardropPhotonRay.KEY.identifier(), TeardropPhotonRay.CONDENSED_MODE) - cooldown) < EPSILON);
			check(helper, "release closes original sword", !player.getMainHandItem().get(FateWeapons.OPENING).open());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = SKY_TIMEOUT)
	public void skyBreaksStoneWithoutDropsAndKeepsBedrock(GameTestHelper helper) {
		ServerPlayer player = FateTests.altera(helper, ORIGIN, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		durable(helper, ORIGIN.above(SKY_TARGET_HEIGHT));
		BlockPos stone = ORIGIN.above(SKY_TARGET_HEIGHT).east();
		// Beside, not above: the beam strikes from the top, so bedrock above would shield the stone.
		BlockPos bedrock = stone.east();
		BlockPos deeper = stone.below(4);
		helper.setBlock(stone, Blocks.STONE);
		helper.setBlock(deeper, Blocks.STONE);
		helper.setBlock(bedrock, Blocks.BEDROCK);
		Abilities.press(player, TeardropPhotonRay.KEY.identifier(), TeardropPhotonRay.CONDENSED_MODE, false);
		helper.runAfterDelay(skyWindUp() + SECOND_TICKS, () -> {
			helper.assertBlockNotPresent(Blocks.STONE, stone);
			helper.assertBlockNotPresent(Blocks.STONE, deeper);
			helper.assertBlockPresent(Blocks.BEDROCK, bedrock);
			check(helper, "no sky strike drops", helper.getLevel().getEntitiesOfClass(ItemEntity.class,
				player.getBoundingBox().inflate(SKY_TARGET_HEIGHT + 2)).isEmpty());
			Abilities.release(player, TeardropPhotonRay.KEY.identifier());
			helper.succeed();
		});
	}

	@GameTest(structure = SKY_STRUCTURE)
	public void condensedExcavationTakesTwiceAsLong(GameTestHelper helper) {
		checkSlowerExcavation(helper, TeardropPhotonRay.CONDENSED_MODE);
	}

	@GameTest(structure = SKY_STRUCTURE)
	public void spreadExcavationTakesTwiceAsLong(GameTestHelper helper) {
		checkSlowerExcavation(helper, TeardropPhotonRay.SPREAD_MODE);
	}

	// Steps the beam's effects directly, so the order entities and abilities tick in can't matter.
	private static void checkSlowerExcavation(GameTestHelper helper, int mode) {
		AlteraConfig config = FateConfig.altera();
		check(helper, "default excavation takes at least twice as long", AlteraConfig.DEFAULTS.teardropDigBlocksPerSecond()
			<= PREVIOUS_DIG_BLOCKS_PER_SECOND / MINIMUM_DIG_SLOWDOWN);
		check(helper, "default Spread excavation is slower than Condensed", AlteraConfig.DEFAULTS.spreadDigBlocksPerSecond()
			< AlteraConfig.DEFAULTS.teardropDigBlocksPerSecond());
		double rate = mode == TeardropPhotonRay.SPREAD_MODE ? config.spreadDigBlocksPerSecond() : config.teardropDigBlocksPerSecond();
		BlockPos origin = fixture(helper, mode);
		ServerPlayer player = FateTests.altera(helper, origin, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		Abilities.press(player, TeardropPhotonRay.KEY.identifier(), mode, false);
		TeardropPhotonRayEntity ray = sky(helper, player);
		BlockPos surface = helper.absolutePos(origin.above(SKY_TARGET_HEIGHT));
		ray.setPos(Vec3.atCenterOf(surface));
		// The beam takes the top layer as it lands, then one more each time the front has moved down a block.
		int firstSecondLayers = (int) Math.floor(rate);
		BlockPos probe = surface.below(firstSecondLayers + DIG_PROBE_MARGIN);
		// Solid ground wider than the beam: a slanted Condensed beam would otherwise also strike the column's sides.
		solid(helper, probe.offset(-GROUND_HALF_WIDTH, 0, -GROUND_HALF_WIDTH), surface.offset(GROUND_HALF_WIDTH, 0, GROUND_HALF_WIDTH));
		ray.held(skyWindUp(), true);
		for (int tick = 0; tick < SECOND_TICKS; tick++) {
			ray.beamEffects(helper.getLevel());
		}
		check(helper, "surface still excavates", helper.getLevel().getBlockState(surface).isAir());
		check(helper, "the front is the configured blocks per second down", helper.getLevel().getBlockState(probe.above()).isAir());
		check(helper, "deeper layer survives one second at the loaded excavation rate", helper.getLevel().getBlockState(probe).is(Blocks.STONE));
		for (int tick = SECOND_TICKS; tick < SECOND_TICKS * DIG_TIME_MULTIPLIER; tick++) {
			ray.beamEffects(helper.getLevel());
		}
		check(helper, "deeper layer breaks after twice the firing time", helper.getLevel().getBlockState(probe).isAir());
		Abilities.release(player, TeardropPhotonRay.KEY.identifier());
		helper.succeed();
	}

	@GameTest(structure = SKY_STRUCTURE)
	public void condensedDigsFromTheTopDown(GameTestHelper helper) {
		checkTopDown(helper, TeardropPhotonRay.CONDENSED_MODE);
	}

	@GameTest(structure = SKY_STRUCTURE)
	public void spreadDigsFromTheTopDown(GameTestHelper helper) {
		checkTopDown(helper, TeardropPhotonRay.SPREAD_MODE);
	}

	private static void checkTopDown(GameTestHelper helper, int mode) {
		AlteraConfig config = FateConfig.altera();
		double rate = mode == TeardropPhotonRay.SPREAD_MODE ? config.spreadDigBlocksPerSecond() : config.teardropDigBlocksPerSecond();
		BlockPos origin = fixture(helper, mode);
		ServerPlayer player = FateTests.altera(helper, origin, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		Abilities.press(player, TeardropPhotonRay.KEY.identifier(), mode, false);
		TeardropPhotonRayEntity ray = sky(helper, player);
		BlockPos surface = helper.absolutePos(origin.above(SKY_TARGET_HEIGHT));
		ray.setPos(Vec3.atCenterOf(surface));
		solid(helper, surface.offset(-GROUND_HALF_WIDTH, -1, -GROUND_HALF_WIDTH), surface.offset(GROUND_HALF_WIDTH, 0, GROUND_HALF_WIDTH));
		BlockPos covered = surface.east(ROOF_OFFSET);
		BlockPos roof = covered.above(ROOF_HEIGHT);
		BlockPos open = surface.west(ROOF_OFFSET);
		// Wider than one column: Condensed falls slightly slanted from the off-centre seals.
		solid(helper, roof.offset(-1, 0, -1), roof.offset(1, 0, 1));
		ray.held(skyWindUp(), true);
		ray.beamEffects(helper.getLevel());
		check(helper, "the roof above the aimed level goes as the beam lands", helper.getLevel().getBlockState(roof).isAir());
		check(helper, "open ground goes at once", helper.getLevel().getBlockState(open).isAir());
		check(helper, "ground under the roof waits for the next layer", helper.getLevel().getBlockState(covered).is(Blocks.STONE));
		for (int tick = 0; tick < Math.ceil(SECOND_TICKS / rate); tick++) {
			ray.beamEffects(helper.getLevel());
		}
		check(helper, "then the ground under the roof goes", helper.getLevel().getBlockState(covered).isAir());
		Abilities.release(player, TeardropPhotonRay.KEY.identifier());
		helper.succeed();
	}

	@GameTest(maxTicks = SKY_TIMEOUT, structure = SKY_STRUCTURE)
	public void condensedSparesTheCoveredUntilTheirRoofIsDug(GameTestHelper helper) {
		checkCover(helper, TeardropPhotonRay.CONDENSED_MODE);
	}

	@GameTest(maxTicks = SKY_TIMEOUT, structure = SKY_STRUCTURE)
	public void spreadSparesTheCoveredUntilTheirRoofIsDug(GameTestHelper helper) {
		checkCover(helper, TeardropPhotonRay.SPREAD_MODE);
	}

	private static void checkCover(GameTestHelper helper, int mode) {
		AlteraConfig config = FateConfig.altera();
		double rate = mode == TeardropPhotonRay.SPREAD_MODE ? config.spreadDigBlocksPerSecond() : config.teardropDigBlocksPerSecond();
		BlockPos origin = fixture(helper, mode);
		ServerPlayer player = FateTests.altera(helper, origin, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		BlockPos aim = origin.above(SKY_TARGET_HEIGHT);
		Husk open = stationary(helper, aim);
		// West, away from the seals: the open target's line back up to them must miss this roof.
		Husk covered = stationary(helper, aim.west(ROOF_OFFSET));
		BlockPos roof = helper.absolutePos(aim.west(ROOF_OFFSET).above(ROOF_HEIGHT));
		solid(helper, roof.offset(-1, 0, -1), roof.offset(1, ROOF_LAYERS - 1, 1));
		Abilities.press(player, TeardropPhotonRay.KEY.identifier(), mode, false);
		// The roof's last layer breaks this many firing ticks in; the beam hits on every tick after it.
		int roofGone = (int) Math.ceil((ROOF_LAYERS - 1) * SECOND_TICKS / rate);
		helper.runAfterDelay(skyWindUp() + roofGone - COVER_MARGIN, () -> {
			check(helper, "open target is hit", open.getHealth() < TARGET_HP);
			check(helper, "covered target is safe while its roof stands", covered.getHealth() == TARGET_HP);
		});
		helper.runAfterDelay(skyWindUp() + roofGone + COVER_MARGIN, () -> {
			check(helper, "covered target is hit once its roof is dug away", covered.getHealth() < TARGET_HP);
			Abilities.release(player, TeardropPhotonRay.KEY.identifier());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = SKY_TIMEOUT, structure = SKY_STRUCTURE)
	public void spreadSparesWhatIsUnderAShieldBesideItsLine(GameTestHelper helper) {
		BlockPos origin = fixture(helper, TeardropPhotonRay.SPREAD_MODE);
		ServerPlayer player = FateTests.altera(helper, origin, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		BlockPos aim = origin.above(SKY_TARGET_HEIGHT);
		Husk open = stationary(helper, aim);
		BlockPos sheltered = aim.west(SHELTER_OFFSET);
		Husk covered = stationary(helper, sheltered);
		ShieldEntity shield = helper.spawn(FateTestShields.DISC, Vec3.atCenterOf(sheltered.above(ROOF_HEIGHT)));
		shield.setXRot(FACING_UP);
		shield.deploy(SHIELD_RANK);
		BlockPos kept = sheltered.north();
		BlockPos dug = aim.east(SHELTER_OFFSET).north();
		helper.setBlock(kept, Blocks.STONE);
		helper.setBlock(dug, Blocks.STONE);
		Abilities.press(player, TeardropPhotonRay.KEY.identifier(), TeardropPhotonRay.SPREAD_MODE, false);
		helper.runAfterDelay(skyWindUp() + SECOND_TICKS, () -> {
			check(helper, "the open target is hit", open.getHealth() < TARGET_HP);
			check(helper, "the target under the shield is not", covered.getHealth() == TARGET_HP);
			check(helper, "the shield took its hits", shield.hp() < shield.maxHp());
			helper.assertBlockPresent(Blocks.STONE, kept);
			helper.assertBlockNotPresent(Blocks.STONE, dug);
			Abilities.release(player, TeardropPhotonRay.KEY.identifier());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = SKY_TIMEOUT, structure = SKY_STRUCTURE)
	public void condensedVaporizesWaterThatDoesNotReturn(GameTestHelper helper) {
		checkVaporizes(helper, TeardropPhotonRay.CONDENSED_MODE);
	}

	@GameTest(maxTicks = SKY_TIMEOUT, structure = SKY_STRUCTURE)
	public void spreadVaporizesWaterThatDoesNotReturn(GameTestHelper helper) {
		checkVaporizes(helper, TeardropPhotonRay.SPREAD_MODE);
	}

	private static void checkVaporizes(GameTestHelper helper, int mode) {
		BlockPos origin = fixture(helper, mode);
		ServerPlayer player = FateTests.altera(helper, origin, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		BlockPos aim = origin.above(SKY_TARGET_HEIGHT);
		stationary(helper, aim);
		BlockPos middle = helper.absolutePos(aim.below(POOL_DEPTH));
		solid(helper, middle.offset(-POOL_HALF_WIDTH - 1, -1, -POOL_HALF_WIDTH - 1), middle.offset(POOL_HALF_WIDTH + 1, POOL_DEPTH - 1,
			POOL_HALF_WIDTH + 1));
		for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-POOL_HALF_WIDTH, 0, -POOL_HALF_WIDTH),
			middle.offset(POOL_HALF_WIDTH, POOL_DEPTH - 1, POOL_HALF_WIDTH))) {
			helper.getLevel().setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
		}
		BlockPos logged = middle.east();
		helper.getLevel().setBlockAndUpdate(logged, Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
		BlockPos centre = middle.above(POOL_DEPTH - 1);
		BlockPos rim = centre.east(POOL_HALF_WIDTH);
		BlockPos sample = middle.below(POOL_DEPTH);
		helper.getLevel().setBlockAndUpdate(sample, Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
		check(helper, "vaporizing drains a waterlogged block", WeaponBlocks.vaporize(helper.getLevel(), sample)
			&& helper.getLevel().getBlockState(sample).is(Blocks.OAK_STAIRS)
			&& !helper.getLevel().getBlockState(sample).getValue(BlockStateProperties.WATERLOGGED));
		Abilities.press(player, TeardropPhotonRay.KEY.identifier(), mode, false);
		helper.runAfterDelay(skyWindUp() + SECOND_TICKS, () -> {
			check(helper, "water under the beam boils away", !helper.getLevel().getFluidState(centre).is(FluidTags.WATER)
				&& !helper.getLevel().getFluidState(middle).is(FluidTags.WATER));
			check(helper, "a waterlogged block under the beam loses its water", !helper.getLevel().getFluidState(logged).is(FluidTags.WATER));
		});
		helper.runAfterDelay(skyWindUp() + SECOND_TICKS * REFILL_SECONDS, () -> {
			check(helper, "the pool does not flow back while the beam is there", !helper.getLevel().getFluidState(centre).is(FluidTags.WATER)
				&& !helper.getLevel().getFluidState(middle).is(FluidTags.WATER));
			if (mode == TeardropPhotonRay.CONDENSED_MODE) {
				check(helper, "water outside the Condensed beam stays", helper.getLevel().getFluidState(rim).isSource());
			}
			Abilities.release(player, TeardropPhotonRay.KEY.identifier());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = SKY_TIMEOUT, structure = SKY_STRUCTURE)
	public void spreadBoilsADeepPoolWithinItsBlockBudget(GameTestHelper helper) {
		int budget = FateConfig.altera().teardropBlocksPerTick();
		BlockPos origin = fixture(helper, TeardropPhotonRay.SPREAD_MODE);
		ServerPlayer player = FateTests.altera(helper, origin, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		BlockPos aim = origin.above(SKY_TARGET_HEIGHT);
		stationary(helper, aim);
		// Deep enough to hold several ticks' worth of the loaded budget.
		int side = DEEP_POOL_HALF_WIDTH * 2 + 1;
		int depth = Mth.ceil(DEEP_POOL_BUDGET_TICKS * budget / (double) (side * side)) + 1;
		BlockPos top = helper.absolutePos(aim.below());
		BlockPos low = top.offset(-DEEP_POOL_HALF_WIDTH, 1 - depth, -DEEP_POOL_HALF_WIDTH);
		BlockPos high = top.offset(DEEP_POOL_HALF_WIDTH, 0, DEEP_POOL_HALF_WIDTH);
		solid(helper, low.offset(-1, -1, -1), high.offset(1, 0, 1));
		for (BlockPos pos : BlockPos.betweenClosed(low, high)) {
			helper.getLevel().setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
		}
		int[] water = {count(helper, low, high)};
		int[] most = {0};
		helper.onEachTick(() -> {
			int now = count(helper, low, high);
			most[0] = Math.max(most[0], water[0] - now);
			water[0] = now;
		});
		Abilities.press(player, TeardropPhotonRay.KEY.identifier(), TeardropPhotonRay.SPREAD_MODE, false);
		helper.runAfterDelay(skyWindUp() + SECOND_TICKS, () -> {
			check(helper, "never more than " + budget + " blocks boiled in one tick, saw " + most[0], most[0] > 0 && most[0] <= budget);
			check(helper, "the deep pool still ends up dry: " + water[0] + " left", water[0] == 0);
			Abilities.release(player, TeardropPhotonRay.KEY.identifier());
			helper.succeed();
		});
	}

	private static int count(GameTestHelper helper, BlockPos from, BlockPos to) {
		int water = 0;
		for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
			if (helper.getLevel().getFluidState(pos).is(FluidTags.WATER)) {
				water++;
			}
		}
		return water;
	}

	private static void solid(GameTestHelper helper, BlockPos from, BlockPos to) {
		for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
			helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
		}
	}

	@GameTest(maxTicks = SKY_TIMEOUT)
	public void skyDrainsRankManaPerSecondAndStopsWithoutSword(GameTestHelper helper) {
		ServerPlayer player = FateTests.altera(helper, ORIGIN, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		durable(helper, ORIGIN.above(SKY_TARGET_HEIGHT));
		double before = AlayaMana.get(player).current();
		Abilities.press(player, TeardropPhotonRay.KEY.identifier(), TeardropPhotonRay.CONDENSED_MODE, false);
		helper.runAfterDelay(skyWindUp() - 2, () -> check(helper, "wind-up is free", AlayaMana.get(player).current() == before));
		double[] mana = {0};
		int[] held = {0};
		ItemStack[] sword = {ItemStack.EMPTY};
		helper.startSequence().thenIdle(skyWindUp() + 2).thenExecute(() -> {
			mana[0] = AlayaMana.get(player).current();
			held[0] = sky(helper, player).heldTicks();
		}).thenIdle(SECOND_TICKS + 2).thenExecute(() -> {
			double drain = mana[0] - AlayaMana.get(player).current();
			int elapsed = sky(helper, player).heldTicks() - held[0];
			double expected = AlayaConfig.ranks().beamDrainPerSecond(TeardropPhotonRay.RANK) * elapsed / SECOND_TICKS;
			check(helper, "rank A++ drain over " + elapsed + " ticks: " + drain,
				elapsed >= SECOND_TICKS && Math.abs(drain - expected) < EPSILON);
			sword[0] = player.getMainHandItem();
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		}).thenIdle(2).thenExecute(() -> {
			check(helper, "losing sword ends", !Abilities.isActive(player, TeardropPhotonRay.KEY.identifier()));
			check(helper, "moved sword closes", !sword[0].get(FateWeapons.OPENING).open());
		}).thenSucceed();
	}

	@GameTest(maxTicks = SKY_TIMEOUT)
	public void skyEndsWhenManaCannotPayFiringTick(GameTestHelper helper) {
		ServerPlayer player = FateTests.altera(helper, ORIGIN, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		Husk target = durable(helper, ORIGIN.above(SKY_TARGET_HEIGHT));
		AlayaMana.update(player, mana -> mana.withCurrent(0));
		Abilities.press(player, TeardropPhotonRay.KEY.identifier(), TeardropPhotonRay.CONDENSED_MODE, false);
		helper.runAfterDelay(skyWindUp() + 2, () -> {
			check(helper, "insufficient mana ends on first firing tick", !Abilities.isActive(player, TeardropPhotonRay.KEY.identifier()));
			check(helper, "unpaid beam never hurts", target.getHealth() == TARGET_HP);
			check(helper, "mana refusal closes sword", !player.getMainHandItem().get(FateWeapons.OPENING).open());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = SKY_TIMEOUT, structure = SKY_STRUCTURE)
	public void condensedKeepsFullDamageButDoesNotReachBeyondItsRadius(GameTestHelper helper) {
		BlockPos origin = fixture(helper, UPPER_FIXTURE);
		ServerPlayer player = FateTests.altera(helper, origin, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		Husk centre = stationary(helper, origin.above(SKY_TARGET_HEIGHT));
		Husk outside = stationary(helper, origin.above(SKY_TARGET_HEIGHT).east(beyondCondensed()));
		Abilities.press(player, TeardropPhotonRay.KEY.identifier(), TeardropPhotonRay.CONDENSED_MODE, false);
		check(helper, "condensed id and radius", sky(helper, player).modeId().equals(TeardropPhotonRay.CONDENSED)
			&& sky(helper, player).beamRadius() == FateConfig.altera().teardropRadius());
		helper.runAfterDelay(skyWindUp() + SECOND_TICKS, () -> {
			double perHit = ContinuousSource.perTick(teardropPerSecond(player));
			double loss = TARGET_HP - centre.getHealth();
			check(helper, "condensed hits its centre at full rate", loss >= perHit - EPSILON
				&& Math.abs(loss / perHit - Math.rint(loss / perHit)) < EPSILON);
			check(helper, "condensed misses a mob just past its radius", outside.getHealth() == TARGET_HP);
			Abilities.release(player, TeardropPhotonRay.KEY.identifier());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = SKY_TIMEOUT, structure = SKY_STRUCTURE)
	public void spreadMiddleHasFallingGroundSplashWithoutOuterCrater(GameTestHelper helper) {
		BlockPos origin = fixture(helper, LOWER_FIXTURE);
		ServerPlayer player = FateTests.altera(helper, origin, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		BlockPos aim = origin.above(SKY_TARGET_HEIGHT);
		int splashOffset = splashOffset();
		stationary(helper, aim);
		Husk direct = stationary(helper, aim.east(beyondCondensed()));
		Husk edge = stationary(helper, aim.east(insideSpread()));
		Husk splash = stationary(helper, aim.south(splashOffset));
		int outsideOffset = (int) Math.ceil(TeardropGeometry.impactRadius(FateConfig.altera()) / Math.sqrt(2)) + 2;
		Husk outside = stationary(helper, aim.south(outsideOffset).east(outsideOffset));
		BlockPos innerStone = aim.west(beyondCondensed());
		BlockPos outerStone = aim.north(splashOffset);
		BlockPos deepStone = innerStone.below(deepBelow());
		helper.setBlock(innerStone, Blocks.STONE);
		helper.setBlock(outerStone, Blocks.STONE);
		helper.setBlock(deepStone, Blocks.STONE);
		Abilities.press(player, TeardropPhotonRay.KEY.identifier(), TeardropPhotonRay.SPREAD_MODE, false);
		check(helper, "spread id, flag and merged radius", sky(helper, player).modeId().equals(TeardropPhotonRay.SPREAD)
			&& sky(helper, player).spread() && sky(helper, player).beamRadius() == TeardropGeometry.spreadRadius(FateConfig.altera()));
		helper.runAfterDelay(skyWindUp() + SECOND_TICKS, () -> {
			double directLoss = TARGET_HP - direct.getHealth();
			double splashLoss = TARGET_HP - splash.getHealth();
			check(helper, "spread hits just past Condensed's radius (" + directLoss + ") and just inside its own ("
				+ (TARGET_HP - edge.getHealth()) + "); ray firing=" + sky(helper, player).firing() + ", held=" + sky(helper, player).heldTicks()
				+ ", reach=" + sky(helper, player).reach() + ", depth=" + sky(helper, player).depth()
				+ ", edge at " + edge.position() + ", mana=" + AlayaMana.get(player).current(), directLoss > 0 && edge.getHealth() < TARGET_HP);
			double perHit = ContinuousSource.perTick(teardropPerSecond(player) * FateConfig.altera().spreadDamageMultiplier());
			check(helper, "spread uses configured damage share", directLoss >= perHit - EPSILON
				&& Math.abs(directLoss / perHit - Math.rint(directLoss / perHit)) < EPSILON);
			check(helper, "ground splash is weaker than direct column", splashLoss > 0 && splashLoss < directLoss);
			check(helper, "linear annular falloff", Math.abs(splashLoss / directLoss - (TeardropGeometry.impactRadius(FateConfig.altera())
				- splashOffset) / (TeardropGeometry.impactRadius(FateConfig.altera()) - TeardropGeometry.spreadRadius(FateConfig.altera()))) < EPSILON);
			check(helper, "beyond the drawn splash is untouched", outside.getHealth() == TARGET_HP);
			helper.assertBlockNotPresent(Blocks.STONE, innerStone);
			helper.assertBlockPresent(Blocks.STONE, outerStone);
			helper.assertBlockPresent(Blocks.STONE, deepStone);
			ItemStack sword = player.getMainHandItem();
			Abilities.release(player, TeardropPhotonRay.KEY.identifier());
			check(helper, "spread release closes sword and ends cast", !Abilities.isActive(player, TeardropPhotonRay.KEY.identifier())
				&& !sword.get(FateWeapons.OPENING).open());
			check(helper, "spread cooldown matches condensed", Math.abs(Abilities.cooldownSecondsLeft(player,
				TeardropPhotonRay.KEY.identifier(), TeardropPhotonRay.SPREAD_MODE) - AlteraConfig.TEARDROP_COOLDOWN_SECONDS) < EPSILON);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = SKY_TIMEOUT, structure = SKY_STRUCTURE)
	public void spreadSingleBeamCoversAllThreeSealSitesAndDigsAtTheSameRate(GameTestHelper helper) {
		BlockPos origin = fixture(helper, LOWER_FIXTURE);
		ServerPlayer player = FateTests.altera(helper, origin, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		BlockPos aim = origin.above(SKY_TARGET_HEIGHT);
		stationary(helper, aim);
		Abilities.press(player, TeardropPhotonRay.KEY.identifier(), TeardropPhotonRay.SPREAD_MODE, false);
		TeardropPhotonRayEntity ray = sky(helper, player);
		Husk[] targets = new Husk[TeardropGeometry.SEALS];
		BlockPos[] stones = new BlockPos[TeardropGeometry.SEALS];
		BlockPos[] outerStones = new BlockPos[TeardropGeometry.SEALS];
		BlockPos[] deepStones = new BlockPos[TeardropGeometry.SEALS];
		BlockPos[] bedrock = new BlockPos[TeardropGeometry.SEALS];
		for (int side = -TeardropGeometry.SIDE_SEALS; side <= TeardropGeometry.SIDE_SEALS; side++) {
			int index = side + TeardropGeometry.SIDE_SEALS;
			Vec3 at = ray.position().add(TeardropGeometry.surface(FateConfig.altera(), side));
			targets[index] = stationary(helper, aim);
			targets[index].setPos(at);
			stones[index] = BlockPos.containing(at).north(beyondCondensed());
			outerStones[index] = BlockPos.containing(at).north(splashOffset());
			deepStones[index] = stones[index].below(deepBelow());
			bedrock[index] = stones[index].east();
			check(helper, "column " + index + " fixture lies inside the reserved structure: " + at + " in " + helper.getBounds()
				+ ", splash stone " + outerStones[index], overArena(helper, at)
				&& overArena(helper, Vec3.atCenterOf(outerStones[index])));
			helper.getLevel().setBlockAndUpdate(stones[index], Blocks.STONE.defaultBlockState());
			helper.getLevel().setBlockAndUpdate(outerStones[index], Blocks.STONE.defaultBlockState());
			helper.getLevel().setBlockAndUpdate(deepStones[index], Blocks.STONE.defaultBlockState());
			helper.getLevel().setBlockAndUpdate(bedrock[index], Blocks.BEDROCK.defaultBlockState());
		}
		helper.runAfterDelay(skyWindUp() - 2, () -> {
			for (Husk target : targets) {
				check(helper, "no side column damage during wind-up", target.getHealth() == TARGET_HP);
			}
		});
		helper.runAfterDelay(skyWindUp() + SECOND_TICKS, () -> {
			double middleLoss = TARGET_HP - targets[TeardropGeometry.SIDE_SEALS].getHealth();
			check(helper, "middle output fires", middleLoss > 0);
			for (int index = 0; index < TeardropGeometry.SEALS; index++) {
				check(helper, "column " + index + " target ticks outside the central chunk", targets[index].tickCount >= skyWindUp());
				check(helper, "column " + index + " matches middle damage: loss=" + (TARGET_HP - targets[index].getHealth())
					+ ", middleLoss=" + middleLoss, Math.abs(TARGET_HP - targets[index].getHealth() - middleLoss) < EPSILON);
				check(helper, "column " + index + " digs its own surface", helper.getLevel().getBlockState(stones[index]).isAir());
				check(helper, "column " + index + " does not dig its splash", helper.getLevel().getBlockState(outerStones[index]).is(Blocks.STONE));
				check(helper, "column " + index + " keeps gradual depth", helper.getLevel().getBlockState(deepStones[index]).is(Blocks.STONE));
				check(helper, "column " + index + " keeps bedrock", helper.getLevel().getBlockState(bedrock[index]).is(Blocks.BEDROCK));
			}
			Abilities.release(player, TeardropPhotonRay.KEY.identifier());
			helper.succeed();
		});
	}

	@GameTest(structure = SKY_STRUCTURE)
	public void spreadGapBetweenSealSitesReceivesOneFullBeamHitAndDigs(GameTestHelper helper) {
		BlockPos origin = fixture(helper, LOWER_FIXTURE);
		ServerPlayer player = FateTests.altera(helper, origin, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		BlockPos aim = origin.above(SKY_TARGET_HEIGHT);
		stationary(helper, aim);
		Abilities.press(player, TeardropPhotonRay.KEY.identifier(), TeardropPhotonRay.SPREAD_MODE, false);
		TeardropPhotonRayEntity ray = sky(helper, player);
		AlteraConfig config = FateConfig.altera();
		Vec3 middle = ray.position();
		Vec3 side = middle.add(TeardropGeometry.surface(config, TeardropGeometry.SIDE_SEALS));
		Vec3 gap = middle.lerp(side, MIDWAY);
		Husk target = stationary(helper, aim);
		target.setPos(gap);
		check(helper, "sample lies between the direct-hit columns", middle.distanceTo(gap) > config.spreadRadius());
		for (int seal = -TeardropGeometry.SIDE_SEALS; seal <= TeardropGeometry.SIDE_SEALS; seal++) {
			check(helper, "merged radius covers the whole seal", TeardropGeometry.sky(config, seal)
				.distanceTo(TeardropGeometry.sky(config, 0)) + TeardropGeometry.sealRadius(config) <= ray.beamRadius() + EPSILON);
		}
		Vec3 seal = ray.beamOrigin();
		Vec3 upperCentre = seal.lerp(middle, BELL_TOP_SHARE)
			.add(0, 0, TeardropGeometry.spreadRadiusAt(config, BELL_TOP_SHARE) * BELL_OUTSIDE_SEAL);
		Husk upper = stationary(helper, aim);
		upper.setPos(upperCentre.subtract(0, upper.getBoundingBox().getCenter().y - upper.getY(), 0));
		Husk groundEdge = stationary(helper, aim);
		groundEdge.setPos(middle.add(0, 0, ray.beamRadius() * BELL_GROUND_EDGE));
		check(helper, "bell begins at the middle outer ring radius", Math.abs(TeardropGeometry.spreadRadiusAt(config, 0)
			- TeardropGeometry.spreadStartRadius(config)) < EPSILON);
		check(helper, "bell reaches full width at the configured flare share", Math.abs(TeardropGeometry.spreadRadiusAt(config,
			config.spreadFlareShare()) - ray.beamRadius()) < EPSILON);
		BlockPos stone = BlockPos.containing(gap).north();
		helper.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
		ray.held(skyWindUp(), true);
		ray.beamEffects(helper.getLevel());
		double perHit = ContinuousSource.perTick(teardropPerSecond(player) * config.spreadDamageMultiplier());
		check(helper, "gap receives exactly one full-rate hit", Math.abs(TARGET_HP - target.getHealth() - perHit) < EPSILON);
		check(helper, "gap is excavated by the single beam", helper.getLevel().getBlockState(stone).isAir());
		check(helper, "outside the middle seal just below it is untouched", upper.getHealth() == TARGET_HP);
		check(helper, "near full width at the ground receives a full beam hit",
			Math.abs(TARGET_HP - groundEdge.getHealth() - perHit) < EPSILON);
		Abilities.release(player, TeardropPhotonRay.KEY.identifier());
		helper.succeed();
	}

	@GameTest(maxTicks = TEST_TIMEOUT, structure = SKY_STRUCTURE)
	public void frontArrowMeetsConeAndRearArrowHurtsOwner(GameTestHelper helper) {
		ServerPlayer player = FateTests.runner(helper, RUN_START, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		FateTests.hold(player);
		PhotonRayEntity[] ray = {null};
		float[] health = {0};
		Arrow[] front = {null};
		helper.startSequence().thenIdle(charge() + 2).thenExecute(() -> {
			ray[0] = dashRay(helper, player);
			health[0] = player.getHealth();
			front[0] = arrow(helper, ray[0].position().add(0, 0, ARROW_AHEAD), new Vec3(0, 0, -ARROW_SPEED));
		}).thenIdle(3).thenExecute(() -> {
			check(helper, "front arrow takes cone HP", ray[0].hp() < ray[0].maxHp());
			check(helper, "front arrow leaves owner unharmed", player.getHealth() == health[0]);
			check(helper, "front arrow stopped", front[0].isRemoved() || front[0].getDeltaMovement().z >= 0);
			Arrow rear = arrow(helper, player.position().subtract(0, 0, ARROW_AHEAD), new Vec3(0, 0, ARROW_SPEED));
			// Mock players aren't projectile collision targets; deliver vanilla's arrow hit to exercise ALLOW_DAMAGE.
			player.invulnerableTime = 0;
			player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().arrow(rear, null), (float) ARROW_SPEED);
			check(helper, "rear arrow reaches owner", player.getHealth() < health[0]);
		}).thenSucceed();
	}

	@GameTest(maxTicks = TEST_TIMEOUT, structure = SKY_STRUCTURE)
	public void coneBreaksAtRankABaseShareAndStopsTheRunThere(GameTestHelper helper) {
		float coneHp = (float) (AlayaConfig.ranks().npDamage(PhotonRay.RANK, 0.0) * FateConfig.altera().coneHpFactor());
		ServerPlayer player = FateTests.runner(helper, RUN_START, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		Husk attacker = durable(helper, RUN_START.south(ATTACKER_AHEAD));
		FateTests.hold(player);
		Vec3[] atBreak = {null};
		helper.startSequence().thenIdle(charge() + 2).thenExecute(() -> {
			PhotonRayEntity ray = dashRay(helper, player);
			check(helper, "cone HP is the rank A base share", Math.abs(ray.maxHp() - coneHp) < EPSILON);
			check(helper, "rank A", ray.rank().equals(PhotonRay.RANK));
			ray.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), coneHp - LAST_HP);
			check(helper, "survives below threshold", !ray.isRemoved() && ray.hp() == LAST_HP);
		}).thenIdle(1).thenExecute(() -> {
			// A shield takes each attack once a tick: the breaking hit comes on the next.
			PhotonRayEntity ray = dashRay(helper, player);
			atBreak[0] = player.position();
			ray.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), LAST_HP);
			check(helper, "breaks exactly at threshold", ray.isRemoved() && ray.hp() == 0);
		}).thenIdle(3).thenExecute(() -> {
			check(helper, "she stops where the cone breaks", !ForcedAdvance.active(player) && player.position().distanceTo(atBreak[0]) < EPSILON);
			check(helper, "cast and spin cleaned", !Abilities.isActive(player, PhotonRay.KEY.identifier())
				&& !player.getMainHandItem().has(FateWeapons.SPIN));
		}).thenSucceed();
	}

	@GameTest(maxTicks = BEAM_TIMEOUT, structure = SKY_STRUCTURE)
	public void heldEnumaElishSlowsTheRunBeforeBreakingCone(GameTestHelper helper) {
		ServerPlayer player = FateTests.runner(helper, RUN_START, FateTests.ALTERA_MANA);
		TrueName.realize(player);
		ServerPlayer beamCaster = FateTests.gilgamesh(helper, RUN_START.south(ATTACKER_AHEAD), FateTests.ALTERA_MANA);
		beamCaster.setGameMode(GameType.CREATIVE);
		beamCaster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FateWeapons.EA));
		beamCaster.setYRot(FACING_NORTH);
		beamCaster.setXRot(0.0F);
		Abilities.press(beamCaster, EnumaElish.KEY.identifier(), 0, false);
		Vec3 start = player.position();
		int windUp = FateConfig.ea().enumaElishWindUpTicks();
		int runStart = windUp - charge() + 1;
		helper.runAfterDelay(runStart, () -> FateTests.hold(player));
		helper.runAfterDelay(windUp + BEAM_FRAME_TICKS, () -> {
			check(helper, "real beam firing", !helper.getLevel().getEntitiesOfClass(EnumaElishBeam.class,
				beamCaster.getBoundingBox().inflate(EnumaElishBeam.length())).isEmpty());
			double freeDistance = (windUp + BEAM_FRAME_TICKS - runStart - PhotonRay.chargeTicks()) * FateConfig.altera().dashBlocksPerSecond()
				/ SECOND_TICKS;
			check(helper, "pressing beam slows movement before break: " + player.position().distanceTo(start) + " of " + freeDistance,
				player.position().distanceTo(start) > 0 && player.position().distanceTo(start) < freeDistance);
			// She keeps pressing on until the cone breaks, so the beam's own wearing, far slower than this wait, is left to its last HP.
			PhotonRayEntity ray = dashRay(helper, player);
			ray.hurtAt(helper.getLevel(), helper.getLevel().damageSources().mobAttack(beamCaster), (float) (ray.hp() - LAST_HP), ray.position());
		});
		helper.runAfterDelay(windUp + BEAM_BREAK_WAIT, () -> {
			check(helper, "beam break ends the run: active=" + Abilities.isActive(player, PhotonRay.KEY.identifier()) + ", at " + player.position()
				+ ", cones=" + helper.getLevel().getEntitiesOfClass(PhotonRayEntity.class, player.getBoundingBox().inflate(FateTests.LANE_LENGTH))
					.stream().map(cone -> cone.hp() + "/" + cone.maxHp()).toList(),
				!Abilities.isActive(player, PhotonRay.KEY.identifier()) && !ForcedAdvance.active(player));
			Abilities.release(beamCaster, EnumaElish.KEY.identifier());
			helper.succeed();
		});
	}

	private static PhotonRayEntity dashRay(GameTestHelper helper, ServerPlayer player) {
		return helper.getLevel().getEntitiesOfClass(PhotonRayEntity.class,
			player.getBoundingBox().inflate(PhotonRayEntity.coneLength()), ray -> ray.owner() == player).getFirst();
	}

	private static Arrow arrow(GameTestHelper helper, Vec3 at, Vec3 velocity) {
		Arrow arrow = new Arrow(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.ARROW), null);
		arrow.setNoGravity(true);
		arrow.setDeltaMovement(velocity);
		helper.getLevel().addFreshEntity(arrow);
		return arrow;
	}

	private static int charge() {
		return PhotonRay.chargeTicks() + 1;
	}

	private static int skyWindUp() {
		return Durations.ticks(FateConfig.altera().teardropWindUpSeconds());
	}

	private static int beyondCondensed() {
		return (int) Math.ceil(FateConfig.altera().teardropRadius()) + 1;
	}

	private static int insideSpread() {
		return (int) Math.floor(TeardropGeometry.spreadRadius(FateConfig.altera())) - 1;
	}

	private static int splashOffset() {
		AlteraConfig config = FateConfig.altera();
		return (int) Math.round(Mth.lerp(MIDWAY / 2, TeardropGeometry.spreadRadius(config), TeardropGeometry.impactRadius(config)));
	}

	private static int deepBelow() {
		return (int) Math.ceil(FateConfig.altera().spreadDigBlocksPerSecond() * FIRING_SECONDS_AT_MOST) + 1;
	}

	private static int fixtureGap() {
		AlteraConfig config = FateConfig.altera();
		return (int) Math.ceil(config.teardropHeight() + TeardropGeometry.impactRadius(config)) + SKY_TARGET_HEIGHT;
	}

	private static BlockPos fixture(GameTestHelper helper, int index) {
		Vec3 centre = helper.getBounds().getCenter();
		BlockPos absolute = BlockPos.containing(centre.x, helper.getBounds().minY, centre.z).above(FIXTURE_LIFT + index * fixtureGap());
		BlockPos relative = FateTests.relativeBlock(helper, absolute);
		check(helper, "fixture conversion round-trips inside its reserved arena", helper.absolutePos(relative).equals(absolute)
			&& overArena(helper, Vec3.atCenterOf(absolute)));
		check(helper, "fixture " + index + " and its aimed level fit under the build limit at " + absolute,
			!helper.getLevel().isOutsideBuildHeight(absolute.above(SKY_TARGET_HEIGHT)));
		return relative;
	}

	private static TeardropPhotonRayEntity sky(GameTestHelper helper, ServerPlayer player) {
		return helper.getLevel().getEntitiesOfClass(TeardropPhotonRayEntity.class,
			player.getBoundingBox().inflate(FateConfig.altera().teardropRange()), ray -> ray.owner() == player).stream().findFirst()
			.orElseThrow(() -> helper.assertionException(Component.literal("the caster has no sky strike: active="
				+ Abilities.isActive(player, TeardropPhotonRay.KEY.identifier()) + ", alive=" + player.isAlive()
				+ ", main hand=" + player.getMainHandItem() + ", at " + player.position())));
	}

	private static boolean overArena(GameTestHelper helper, Vec3 at) {
		AABB arena = helper.getBounds();
		return at.x >= arena.minX && at.x <= arena.maxX && at.z >= arena.minZ && at.z <= arena.maxZ;
	}

	private static Husk stationary(GameTestHelper helper, BlockPos pos) {
		Husk target = durable(helper, pos);
		target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		return target;
	}

	private static Cow loose(GameTestHelper helper, BlockPos pos) {
		Cow target = EntityType.COW.spawn(helper.getLevel(), helper.absolutePos(pos), EntitySpawnReason.COMMAND);
		target.setNoGravity(true);
		target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(TARGET_HP);
		target.setHealth((float) TARGET_HP);
		return target;
	}

	private static Husk durable(GameTestHelper helper, BlockPos pos) {
		Husk target = FateTests.mob(helper, pos);
		target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(TARGET_HP);
		target.getAttribute(Attributes.ARMOR).setBaseValue(0);
		target.setHealth((float) TARGET_HP);
		return target;
	}
}
