package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.beam.HeldBeam;
import io.github.cwzmorro.alayacore.combat.Aim;
import io.github.cwzmorro.alayacore.combat.Attacks;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.progression.TrueName;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.alayacore.shield.ShieldEntity;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.artoria.Artoria;
import io.github.cwzmorro.fatecraft.artoria.ArtoriaConfig;
import io.github.cwzmorro.fatecraft.artoria.ExcaliburEntity;
import io.github.cwzmorro.fatecraft.artoria.ExcaliburNP;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.shield.FateShields;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ExcaliburGameTests {
	private static final BlockPos START = new BlockPos(3, 12, 3);
	private static final double EPSILON = 0.01;
	private static final double LIGHT_SEARCH = 220;
	private static final double PLAYER_FIST_DAMAGE = 1, PLAYER_ATTACK_SPEED = 4;
	private static final double SHAPE_RADIUS = 2, DEEP_SHAPE_DEPTH = 3;
	private static final int RAGGED_SHAPE_EXTENT = 8;
	private static final int WIND_UP = Durations.ticks(FateConfig.artoria().excaliburWindUpSeconds());
	// Test structures sit only 5 to 6 blocks apart, while the burst's targets stand a burst radius out and the front test's ground and explosion run some 80 blocks; padding gives each its own ground.
	private static final int BURST_PADDING = 24, FRONT_PADDING = 96;
	private static final int FURROW_RUN = 60, FURROW_THIRDS = 3, FURROW_EDGE_VARIETY = 10;
	private static final double FURROW_OFF_CENTRE = 0.5, FURROW_EDGE_STEP = 0.05, FURROW_WANDER = 0.1;
	private static final int SURGE_EDGE_MARGIN = 1;
	private static final int FIELD_MARGIN = 6, FIELD_DEPTH = 4, WALL_DISTANCE = 90, WALL_HALF_WIDTH = 3, WALL_HEIGHT = 8;
	private static final int NO_DIG_CHECK_TICKS = 6, PAST_LEAD_IN = 8;
	private static final int SHIELD_HEIGHT = 7, AIMED_HEIGHT = 12, SURGE_OFFSET = 2, SURGE_HEIGHT = 10, ASIDE = 12, BESIDE_ASIDE = 2;
	private static final Rank SHIELD_RANK = Rank.parse("EX");
	private static final double BESIDE_LINE = 3.5;
	private static final int SELF_BLAST_DISTANCE = 5, SELF_BLAST_SETTLE_TICKS = 4;
	private static final double SELF_BLAST_HEALTH = 1024;
	private static final int JUMP_BOOST_TICKS = 20, JUMP_BOOST_AMPLIFIER = 2;

	private static ServerPlayer caster(GameTestHelper helper, boolean realized) {
		ServerPlayer player = FateTests.servant(helper, START, FateTests.MANA, FateServants.ARTORIA);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FateWeapons.EXCALIBUR));
		player.setXRot(-90);
		if (realized) {
			TrueName.realize(player);
		}
		return player;
	}

	private static Vec3 aim(ServerPlayer player) {
		return Aim.point(player, player.getLookAngle(), FateConfig.artoria().excaliburBurstRange());
	}

	private static ExcaliburEntity light(GameTestHelper helper, ServerPlayer player) {
		List<ExcaliburEntity> lights = helper.getLevel().getEntitiesOfClass(ExcaliburEntity.class, player.getBoundingBox().inflate(LIGHT_SEARCH), e -> e.beamOwner() == player);
		check(helper, "Excalibur's light is still loaded", !lights.isEmpty());
		return lights.getFirst();
	}

	private static double cooldown() {
		return AlayaConfig.abilities().cooldownSeconds(ExcaliburNP.KEY.identifier(), AlayaRegistries.ABILITY.getValue(ExcaliburNP.KEY), ExcaliburNP.HELD);
	}

	private static Husk target(GameTestHelper helper, BlockPos pos) {
		Husk mob = FateTests.mob(helper, pos);
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
		mob.getAttribute(Attributes.ARMOR).setBaseValue(0);
		mob.setHealth(mob.getMaxHealth());
		return mob;
	}

	@GameTest
	public void bothModesRequireExcaliburAndTrueNameWithoutStartingCooldown(GameTestHelper helper) {
		ServerPlayer player = caster(helper, false);
		for (int mode : new int[]{ExcaliburNP.HELD, ExcaliburNP.BURST}) {
			double mana = AlayaMana.available(player);
			Abilities.press(player, ExcaliburNP.KEY.identifier(), mode, false);
			check(helper, "true name refused", !Abilities.isActive(player, ExcaliburNP.KEY.identifier()));
			check(helper, "refusal costs nothing", AlayaMana.available(player) == mana);
			check(helper, "refusal has no cooldown", Abilities.cooldownSecondsLeft(player, ExcaliburNP.KEY.identifier(), mode) == 0);
		}
		TrueName.realize(player);
		check(helper, "Artoria can realize her true name", TrueName.isRealized(player));
		for (ItemStack stack : new ItemStack[]{ItemStack.EMPTY, new ItemStack(FateWeapons.CALIBURN)}) {
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			for (int mode : new int[]{ExcaliburNP.HELD, ExcaliburNP.BURST}) {
				Abilities.press(player, ExcaliburNP.KEY.identifier(), mode, false);
				check(helper, "Excalibur only", !Abilities.isActive(player, ExcaliburNP.KEY.identifier()));
			}
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void heldWindUpThenRankHitCarveDrainAndSixtySecondCooldown(GameTestHelper helper) {
		ServerPlayer player = caster(helper, true);
		Husk victim = target(helper, START.above(12));
		BlockPos block = START.above(9);
		helper.setBlock(block, Blocks.STONE);
		double mana = AlayaMana.available(player);
		double normal = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
		Abilities.press(player, ExcaliburNP.KEY.identifier(), ExcaliburNP.HELD, false);
		helper.runAfterDelay(WIND_UP - 5, () -> {
			checkBeamMovement(helper, player);
			check(helper, "charge leaves attribute unchanged", Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - normal) < EPSILON);
			check(helper, "charging, not live", light(helper, player).phase() == ExcaliburEntity.Phase.CHARGE && !light(helper, player).beamLive());
			check(helper, "no damage during charge", victim.getHealth() == victim.getMaxHealth());
			helper.assertBlockPresent(Blocks.STONE, block);
		});
		helper.runAfterDelay(WIND_UP + 12, () -> {
			ExcaliburEntity beam = light(helper, player);
			check(helper, "live A++ NP clash beam", beam.beamLive() && beam.getAttached(Attacks.ATTACK).rank().equals(ExcaliburNP.RANK)
				&& beam.getAttached(Attacks.ATTACK).noblePhantasm() && beam.getAttached(Attacks.ATTACK).canClash());
			check(helper, "held dimensions", beam.beamLength() == FateConfig.artoria().excaliburBeamRange()
				&& ExcaliburEntity.radiusAt(0) == FateConfig.artoria().excaliburStartRadius()
				&& ExcaliburEntity.radiusAt(beam.beamLength()) == FateConfig.artoria().excaliburEndRadius());
			check(helper, "rank beam hit", victim.getHealth() < victim.getMaxHealth());
			check(helper, "mana drained", AlayaMana.available(player) < mana);
			helper.assertBlockNotPresent(Blocks.STONE, block);
			checkBeamMovement(helper, player);
			check(helper, "firing leaves attribute unchanged", Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - normal) < EPSILON);
			Abilities.release(player, ExcaliburNP.KEY.identifier());
			check(helper, "release restores input", !HeldBeam.restrictsMovement(player)
				&& Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - normal) < EPSILON);
			check(helper, "the configured cooldown", Abilities.cooldownSecondsLeft(player, ExcaliburNP.KEY.identifier(), ExcaliburNP.HELD) == cooldown());
			check(helper, "ended entity", beam.isRemoved());
			helper.succeed();
		});
	}

	private static void checkBeamMovement(GameTestHelper helper, ServerPlayer player) {
		check(helper, "configured bow input", HeldBeam.restrictsMovement(player)
			&& Math.abs(HeldBeam.movementMultiplier(player) - AlayaConfig.heldBeams().heldBeamMovementMultiplier()) < EPSILON);
		player.setSprinting(true);
		check(helper, "sprint refused", !player.isSprinting());
		player.setDeltaMovement(Vec3.ZERO);
		player.jumpFromGround();
		check(helper, "jump allowed", player.getDeltaMovement().y > 0);
	}

	@GameTest(maxTicks = 20)
	public void beamSlowsInputAllowsJumpStopsSprintAndEndsOnSwap(GameTestHelper helper) {
		ServerPlayer player = caster(helper, true);
		double normal = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
		Abilities.press(player, ExcaliburNP.KEY.identifier(), ExcaliburNP.HELD, false);
		check(helper, "bow-style input scale", Math.abs(HeldBeam.movementMultiplier(player)
			- AlayaConfig.heldBeams().heldBeamMovementMultiplier()) < EPSILON);
		check(helper, "no attribute debuff", Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - normal) < EPSILON);
		player.setSprinting(true);
		check(helper, "held-beam sprint rejected", !player.isSprinting());
		player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, JUMP_BOOST_TICKS, JUMP_BOOST_AMPLIFIER));
		player.setDeltaMovement(Vec3.ZERO);
		player.jumpFromGround();
		check(helper, "normal held-beam jump with boost", player.getDeltaMovement().y > 0);
		player.setSprinting(false);
		Abilities.release(player, ExcaliburNP.KEY.identifier());
		check(helper, "early release restores input", !HeldBeam.restrictsMovement(player)
			&& Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - normal) < EPSILON);
		player.setGameMode(GameType.CREATIVE);
		Abilities.press(player, ExcaliburNP.KEY.identifier(), ExcaliburNP.HELD, false);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.runAfterDelay(2, () -> {
			check(helper, "swap ends cast without changing speed", !Abilities.isActive(player, ExcaliburNP.KEY.identifier())
				&& !HeldBeam.restrictsMovement(player)
				&& Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - normal) < EPSILON);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 120)
	public void heldIsStoppedByShieldWithoutHittingOrCarvingBehindIt(GameTestHelper helper) {
		ServerPlayer player = caster(helper, true);
		FateTestShields.Disc shield = helper.spawn(FateTestShields.DISC, Vec3.atCenterOf(START.above(7)));
		shield.setXRot(90); shield.deploy(Rank.parse("A"));
		Husk victim = target(helper, START.above(12));
		BlockPos block = START.above(11);
		helper.setBlock(block, Blocks.STONE);
		Abilities.press(player, ExcaliburNP.KEY.identifier(), 0, false);
		helper.runAfterDelay(WIND_UP + 12, () -> {
			check(helper, "shield took damage", shield.hp() < shield.maxHp());
			check(helper, "beam clipped", light(helper, player).reach() < 7);
			check(helper, "target behind untouched", victim.getHealth() == victim.getMaxHealth());
			helper.assertBlockPresent(Blocks.STONE, block);
			Abilities.end(player, ExcaliburNP.KEY.identifier());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 120)
	public void heldKeepsTheBlocksBehindAShieldBesideItsLine(GameTestHelper helper) {
		ServerPlayer player = caster(helper, true);
		FateTestShields.Disc shield = helper.spawn(FateTestShields.DISC, Vec3.atCenterOf(START.above(SHIELD_HEIGHT)).add(BESIDE_LINE, 0, 0));
		shield.setXRot(90); shield.deploy(Rank.parse("A"));
		BlockPos behind = START.above(SHIELD_HEIGHT + 2).east();
		BlockPos open = START.above(SHIELD_HEIGHT + 2).west();
		helper.setBlock(behind, Blocks.STONE);
		helper.setBlock(open, Blocks.STONE);
		Abilities.press(player, ExcaliburNP.KEY.identifier(), 0, false);
		helper.runAfterDelay(WIND_UP + 12, () -> {
			check(helper, "beam not stopped by the shield beside it", light(helper, player).reach() > SHIELD_HEIGHT + 2);
			helper.assertBlockPresent(Blocks.STONE, behind);
			helper.assertBlockNotPresent(Blocks.STONE, open);
			Abilities.end(player, ExcaliburNP.KEY.identifier());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 145, padding = BURST_PADDING)
	public void burstTravelsThenEruptsOnceWithFalloffAndCondensedDigRadius(GameTestHelper helper) {
		ServerPlayer player = caster(helper, true);
		BlockPos impact = START.above(24);
		helper.setBlock(impact, Blocks.STONE);
		Vec3 spot = aim(player);
		Husk centre = target(helper, impact.above());
		int radius = (int) FateConfig.artoria().excaliburBurstRadius();
		Husk edge = target(helper, impact.above().east(radius));
		Husk outside = target(helper, impact.above().east(radius + 1));
		Husk tooHigh = target(helper, impact.above(65));
		BlockPos dug = impact.below(5);
		BlockPos stays = impact.below(5).east(radius + 1);
		helper.setBlock(dug, Blocks.STONE);
		helper.setBlock(stays, Blocks.STONE);
		helper.setBlock(impact.below(6), Blocks.BEDROCK);
		// The dig sample blocks must not hide the aimed spot, so aim while the line is still clear.
		helper.setBlock(dug, Blocks.AIR);
		helper.setBlock(impact.below(6), Blocks.AIR);
		double mana = AlayaMana.available(player);
		double cost = AlayaConfig.ranks().burstCost(ExcaliburNP.RANK);
		Abilities.press(player, ExcaliburNP.KEY.identifier(), 1, false);
		check(helper, "hold continues charging", Abilities.isActive(player, ExcaliburNP.KEY.identifier()));
		helper.runAfterDelay(WIND_UP + 1, () -> {
			Abilities.release(player, ExcaliburNP.KEY.identifier());
			check(helper, "rank burst mana", Math.abs(mana - AlayaMana.available(player) - cost) < EPSILON);
			helper.setBlock(dug, Blocks.STONE);
			helper.setBlock(impact.below(6), Blocks.BEDROCK);
		});
		helper.runAfterDelay(WIND_UP - 5, () -> check(helper, "burst waits for charge", centre.getHealth() == centre.getMaxHealth()));
		helper.runAfterDelay(WIND_UP + 15, () -> {
			ExcaliburEntity burst = light(helper, player);
			check(helper, "upward burst at aimed spot", burst.phase() == ExcaliburEntity.Phase.BURST && burst.position().distanceTo(spot) < EPSILON);
			double base = AlayaConfig.ranks().npBaseDamage(ExcaliburNP.RANK);
			check(helper, "full A++ centre hit", Math.abs(centre.getMaxHealth() - centre.getHealth() - base) < EPSILON);
			check(helper, "half A++ at edge", Math.abs(edge.getMaxHealth() - edge.getHealth() - base / 2) < EPSILON);
			check(helper, "outside radius unharmed", outside.getHealth() == outside.getMaxHealth());
			check(helper, "above 64 blocks unharmed", tooHigh.getHealth() == tooHigh.getMaxHealth());
			helper.assertBlockNotPresent(Blocks.STONE, dug);
			helper.assertBlockPresent(Blocks.STONE, stays);
			helper.assertBlockPresent(Blocks.BEDROCK, impact.below(6));
		});
		helper.runAfterDelay(WIND_UP + 45, () -> {
			check(helper, "burst ends with its cooldown", !Abilities.isActive(player, ExcaliburNP.KEY.identifier())
				&& Abilities.cooldownSecondsLeft(player, ExcaliburNP.KEY.identifier(), ExcaliburNP.BURST) > cooldown() - 1);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 145, padding = BURST_PADDING)
	public void burstHitsAndDigsWhatNoShieldStandsBefore(GameTestHelper helper) {
		burstPast(helper, null);
	}

	@GameTest(maxTicks = 145, padding = BURST_PADDING)
	public void burstSparesWhatIsBehindAShield(GameTestHelper helper) {
		burstPast(helper, helper.spawn(FateTestShields.DISC, Vec3.atCenterOf(START.above(SHIELD_HEIGHT))));
	}

	@GameTest(maxTicks = 145, padding = BURST_PADDING)
	public void burstSparesWhatIsBehindRhoAias(GameTestHelper helper) {
		burstPast(helper, helper.spawn(FateShields.RHO_AIAS, Vec3.atCenterOf(START.above(SHIELD_HEIGHT))));
	}

	private static void burstPast(GameTestHelper helper, @Nullable ShieldEntity shield) {
		ServerPlayer player = caster(helper, true);
		if (shield != null) {
			shield.setXRot(90);
			shield.deploy(SHIELD_RANK);
		}
		Husk aimed = target(helper, START.above(AIMED_HEIGHT));
		Husk underSurge = target(helper, START.offset(SURGE_OFFSET, SURGE_HEIGHT, 0));
		Husk aside = target(helper, START.offset(ASIDE, AIMED_HEIGHT + 1, 0));
		BlockPos sheltered = START.above(AIMED_HEIGHT + 2);
		// Beside the husk aside, not over its head (that would suffocate it).
		BlockPos open = START.offset(ASIDE, AIMED_HEIGHT + 2, BESIDE_ASIDE);
		helper.setBlock(sheltered, Blocks.STONE);
		helper.setBlock(open, Blocks.STONE);
		Vec3 spot = aim(player);
		Abilities.press(player, ExcaliburNP.KEY.identifier(), ExcaliburNP.BURST, false);
		helper.runAfterDelay(WIND_UP + 1, () -> Abilities.release(player, ExcaliburNP.KEY.identifier()));
		int travel = Durations.ticks(spot.distanceTo(player.getEyePosition()) / FateConfig.artoria().excaliburWaveSpeed());
		helper.runAfterDelay(WIND_UP + 1 + travel + 3, () -> {
			ExcaliburEntity burst = light(helper, player);
			check(helper, "burst at the aimed husk", burst.phase() == ExcaliburEntity.Phase.BURST && burst.position().distanceTo(spot) < EPSILON);
			boolean shielded = shield != null;
			check(helper, shielded ? "aimed husk behind the shield unharmed" : "aimed husk hit",
				(aimed.getHealth() == aimed.getMaxHealth()) == shielded);
			check(helper, shielded ? "surge spares what is behind the shield" : "surge hits what it passes over",
				(underSurge.getHealth() == underSurge.getMaxHealth()) == shielded);
			check(helper, "the blast still lands outside the shield's shadow", aside.getHealth() < aside.getMaxHealth());
			if (shielded) {
				check(helper, "the shield took the hits", shield.hp() < shield.maxHp());
				helper.assertBlockPresent(Blocks.STONE, sheltered);
			} else {
				helper.assertBlockNotPresent(Blocks.STONE, sheltered);
			}
			helper.assertBlockNotPresent(Blocks.STONE, open);
			Abilities.end(player, ExcaliburNP.KEY.identifier());
			helper.succeed();
		});
	}

	@GameTest
	public void burstRangeIsOneHundredAndFalloffIsLinear(GameTestHelper helper) {
		ServerPlayer player = caster(helper, true);
		double range = FateConfig.artoria().excaliburBurstRange();
		Vec3 end = aim(player);
		check(helper, "100 block range (endpoint " + end.distanceTo(player.getEyePosition()) + " away)",
			range == 100 && Math.abs(end.distanceTo(player.getEyePosition()) - 100) < EPSILON);
		double base = AlayaConfig.ranks().npBaseDamage(ExcaliburNP.RANK);
		check(helper, "centre", ExcaliburEntity.burstDamage(0) == base);
		check(helper, "midpoint", ExcaliburEntity.burstDamage(FateConfig.artoria().excaliburBurstRadius() / 2) == base * 0.75);
		check(helper, "edge half", ExcaliburEntity.burstDamage(FateConfig.artoria().excaliburBurstRadius()) == base * 0.5);
		check(helper, "only gameplay explosion enlarged by default", ArtoriaConfig.DEFAULTS.excaliburBurstRadius() == 16
			&& ArtoriaConfig.DEFAULTS.excaliburBurstVisualRadius() == 10);
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void creativeNeedsNoTrueNameManaOrCooldownAndSwapCleansCharge(GameTestHelper helper) {
		ServerPlayer player = caster(helper, false);
		player.setGameMode(GameType.CREATIVE);
		AlayaMana.update(player, mana -> mana.withCurrent(0));
		ItemStack sword = player.getMainHandItem();
		Abilities.press(player, ExcaliburNP.KEY.identifier(), 0, false);
		helper.runAfterDelay(WIND_UP + 5, () -> {
			check(helper, "creative beam fires", light(helper, player).phase() == ExcaliburEntity.Phase.HELD);
			Abilities.end(player, ExcaliburNP.KEY.identifier());
			check(helper, "creative no cooldown", Abilities.cooldownSecondsLeft(player, ExcaliburNP.KEY.identifier(), 0) == 0);
			Abilities.press(player, ExcaliburNP.KEY.identifier(), 1, false);
			check(helper, "creative burst with zero mana", Abilities.isActive(player, ExcaliburNP.KEY.identifier()));
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		});
		helper.runAfterDelay(WIND_UP + 7, () -> {
			check(helper, "swapping ends cast and original pose", !Abilities.isActive(player, ExcaliburNP.KEY.identifier()) && !sword.has(Artoria.EXCALIBUR_CHARGE));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void beamFiresWhileHeldAndBurstWaitsForReleaseUsingFinalLook(GameTestHelper helper) {
		ServerPlayer player = caster(helper, true);
		player.setGameMode(GameType.CREATIVE);
		double normal = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
		for (int mode : new int[]{ExcaliburNP.HELD, ExcaliburNP.BURST}) {
			int start = mode * (WIND_UP + 5);
			boolean beam = mode == ExcaliburNP.HELD;
			helper.runAfterDelay(start + 1, () -> {
				// Beam is pressed facing away from its final aim, which is straight up, so a live beam never crosses the other tests.
				if (beam) {
					player.setXRot(0); player.setYRot(90);
				} else {
					player.setXRot(-90); player.setYRot(0);
				}
				Abilities.press(player, ExcaliburNP.KEY.identifier(), mode, false);
			});
			if (beam) {
				helper.runAfterDelay(start + WIND_UP - 2, () -> {
					player.setXRot(-90); player.setYRot(0);
				});
			}
			helper.runAfterDelay(start + WIND_UP + 2, () -> {
				ExcaliburEntity light = light(helper, player);
				if (beam) {
					Vec3 finalLook = player.getLookAngle();
					check(helper, "Beam fires without release", light.phase() == ExcaliburEntity.Phase.HELD);
					check(helper, "fire snapshots final direction", light.getLookAngle().dot(finalLook) > 1 - EPSILON);
					Abilities.release(player, ExcaliburNP.KEY.identifier());
					check(helper, "release ends Beam", light.isRemoved() && !Abilities.isActive(player, ExcaliburNP.KEY.identifier()));
				} else {
					check(helper, "fully charged still waits for release", light.phase() == ExcaliburEntity.Phase.CHARGE);
					check(helper, "Burst has no movement restriction", Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - normal) < EPSILON);
					player.setXRot(0); player.setYRot(90);
					Vec3 finalLook = player.getLookAngle();
					Abilities.release(player, ExcaliburNP.KEY.identifier());
					check(helper, "release fires selected mode", light.phase() == ExcaliburEntity.Phase.WAVE);
					check(helper, "release snapshots final direction", light.getLookAngle().dot(finalLook) > 1 - EPSILON);
					Abilities.end(player, ExcaliburNP.KEY.identifier());
					helper.succeed();
				}
			});
		}
	}

	@GameTest(maxTicks = 120, padding = FRONT_PADDING)
	public void burstNeverTouchesTheGroundUnderOrBehindHer(GameTestHelper helper) {
		ServerPlayer player = caster(helper, true);
		player.setGameMode(GameType.CREATIVE);
		player.setXRot(0); player.setYRot(0);
		ArtoriaConfig c = FateConfig.artoria();
		// A stone field round her and a wall far beyond the lead-in, so the end explosion cannot reach back to her feet either.
		for (BlockPos pos : BlockPos.betweenClosed(START.offset(-FIELD_MARGIN, -FIELD_DEPTH, -FIELD_MARGIN), START.offset(FIELD_MARGIN, -1, WALL_DISTANCE))) {
			helper.setBlock(pos, Blocks.STONE);
		}
		for (BlockPos pos : BlockPos.betweenClosed(START.offset(-WALL_HALF_WIDTH, 0, WALL_DISTANCE), START.offset(WALL_HALF_WIDTH, WALL_HEIGHT, WALL_DISTANCE))) {
			helper.setBlock(pos, Blocks.STONE);
		}
		Vec3 before = player.position();
		float health = player.getHealth();
		Abilities.press(player, ExcaliburNP.KEY.identifier(), ExcaliburNP.BURST, false);
		helper.runAfterDelay(WIND_UP + 1, () -> Abilities.release(player, ExcaliburNP.KEY.identifier()));
		helper.runAfterDelay(WIND_UP + 1 + NO_DIG_CHECK_TICKS, () -> {
			check(helper, "the burst is travelling", light(helper, player).phase() == ExcaliburEntity.Phase.WAVE);
			check(helper, "she is unharmed by her own burst", player.getHealth() == health);
			check(helper, "she is neither moved nor dropped by her own burst", player.position().distanceTo(before) < EPSILON);
			// Under, beside and behind her feet, and inside the lead-in in front of them, nothing is torn; further out the furrow is.
			for (BlockPos spot : List.of(START.below(), START.offset(-1, -1, 0), START.offset(1, -1, 0), START.offset(0, -1, -1), START.offset(0, -1, -FIELD_MARGIN),
				START.offset(0, -1, (int) Math.ceil(c.excaliburPathLeadIn()) - 1))) {
				helper.assertBlockPresent(Blocks.STONE, spot);
			}
			helper.assertBlockNotPresent(Blocks.STONE, START.offset(0, -1, (int) Math.ceil(c.excaliburPathLeadIn()) + PAST_LEAD_IN));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 120, padding = BURST_PADDING)
	public void endExplosionHurtsAndDigsUnderHerWhenSheIsWithinIt(GameTestHelper helper) {
		ServerPlayer player = caster(helper, true);
		// Enough health to live through the blast, so the hit is measured on a living caster.
		player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(SELF_BLAST_HEALTH);
		player.setHealth(player.getMaxHealth());
		for (BlockPos pos : BlockPos.betweenClosed(START.offset(-FIELD_MARGIN, -FIELD_DEPTH, -FIELD_MARGIN), START.offset(FIELD_MARGIN, -1, FIELD_MARGIN))) {
			helper.setBlock(pos, Blocks.STONE);
		}
		player.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(Vec3.atCenterOf(START.offset(0, -1, SELF_BLAST_DISTANCE))));
		check(helper, "she stands within the end explosion's radius", SELF_BLAST_DISTANCE < FateConfig.artoria().excaliburBurstRadius());
		float health = player.getHealth();
		Abilities.press(player, ExcaliburNP.KEY.identifier(), ExcaliburNP.BURST, false);
		helper.runAfterDelay(WIND_UP + 1, () -> Abilities.release(player, ExcaliburNP.KEY.identifier()));
		helper.runAfterDelay(WIND_UP + 1 + SELF_BLAST_SETTLE_TICKS, () -> {
			check(helper, "the end explosion went off", light(helper, player).phase() == ExcaliburEntity.Phase.BURST);
			check(helper, "her own end explosion hurts her: " + health + " -> " + player.getHealth(), player.getHealth() < health);
			helper.assertBlockNotPresent(Blocks.STONE, START.below());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 160, padding = FRONT_PADDING)
	public void frontCarvesShallowSpacedCratersThenBigExplosion(GameTestHelper helper) {
		ServerPlayer player = caster(helper, true);
		player.setGameMode(GameType.CREATIVE);
		player.setXRot(0); player.setYRot(0);
		ArtoriaConfig c = FateConfig.artoria();
		int length = (int) Math.ceil(c.excaliburBurstRadius() * 2 + c.excaliburPathSpacing() * 3);
		int width = (int) Math.ceil(c.excaliburBurstRadius()) + 2;
		for (BlockPos pos : BlockPos.betweenClosed(START.offset(-width, -4, 0), START.offset(width, -1, length + width))) {
			helper.setBlock(pos, Blocks.STONE);
		}
		BlockPos impact = START.offset(0, 1, length);
		helper.setBlock(impact, Blocks.STONE);
		Vec3 spot = aim(player);
		Vec3 origin = player.getEyePosition();
		BlockPos big = impact.below(3);
		Husk[] pathVictim = new Husk[1], underSurge = new Husk[1], besideSurge = new Husk[1];
		int half = (int) Math.floor(c.excaliburPathWidth() / 2);
		int sideZ = (int) Math.round(c.excaliburPathSpacing() * 2);
		Abilities.press(player, ExcaliburNP.KEY.identifier(), ExcaliburNP.BURST, false);
		helper.runAfterDelay(WIND_UP + 1, () -> {
			Abilities.release(player, ExcaliburNP.KEY.identifier());
			pathVictim[0] = target(helper, START.offset(0, 0, (int) Math.round(c.excaliburPathSpacing())));
			// Spawned after the aim snapshot, so neither is picked as the target; one under the surge's edge, one just past it.
			underSurge[0] = target(helper, START.offset(half - SURGE_EDGE_MARGIN, 0, sideZ));
			besideSurge[0] = target(helper, START.offset(half + SURGE_EDGE_MARGIN, 0, sideZ));
		});
		int travel = Durations.ticks(spot.distanceTo(player.getEyePosition()) / c.excaliburWaveSpeed());
		helper.runAfterDelay(WIND_UP + travel + 3, () -> {
			ExcaliburEntity beam = light(helper, player);
			check(helper, "end explosion still at crosshair", beam.phase() == ExcaliburEntity.Phase.BURST && beam.position().distanceTo(spot) < EPSILON);
			helper.assertBlockPresent(Blocks.STONE, START.below());
			// The first pit chance, a spacing out from her, its middle placed by the entity's own noise at this test's position
			// (which the test grid moves every run).
			double along = ExcaliburEntity.firstPitChance();
			double distance = origin.distanceTo(spot);
			double progress = ExcaliburEntity.pitProgress(along, distance);
			Vec3 look = beam.getLookAngle();
			Vec3 direction = new Vec3(look.x, 0, look.z).normalize();
			Vec3 middle = origin.add(look.scale(along))
				.add(new Vec3(-direction.z, 0, direction.x).scale(ExcaliburEntity.pitSide(origin, 0, ExcaliburEntity.pitRadius(origin, 0, progress))));
			BlockPos column = BlockPos.containing(middle.x, origin.y - player.getEyeHeight() - 1, middle.z);
			// The furrow tears that column, and a dug pit goes on down from the furrow's floor (or the furrow from the pit's),
			// so the torn depth there is the two together: no deeper.
			int torn = (int) Math.ceil(ExcaliburEntity.furrowDepthAt(origin, spot, column.getX(), column.getZ()))
				+ (ExcaliburEntity.pitTaken(origin, 0) ? (int) Math.ceil(ExcaliburEntity.pitDepth(origin, 0, progress)) : 0);
			BlockPos small = column.subtract(helper.absolutePos(BlockPos.ZERO));
			check(helper, "the first pit chance lies on the torn path", torn > 0);
			helper.assertBlockNotPresent(Blocks.STONE, small.below(torn - 1));
			helper.assertBlockPresent(Blocks.STONE, small.below(torn));
			check(helper, "pits near her are shallower than the same pit towards the impact",
				ExcaliburEntity.pitDepth(origin, 0, progress) < ExcaliburEntity.pitDepth(origin, 0, 1));
			// The furrow near her is bounded by its own rough width there (pits near her are smaller still).
			double nearProgress = (along + 1) / distance;
			helper.assertBlockPresent(Blocks.STONE, small.east((int) Math.ceil(ExcaliburEntity.furrowHalfWidth(nearProgress) * (1 + c.excaliburPathRoughness())) + 1));
			helper.assertBlockNotPresent(Blocks.STONE, big);
			check(helper, "passing front damages path target", pathVictim[0].getHealth() < pathVictim[0].getMaxHealth());
			check(helper, "what the surge passes over is hit", underSurge[0].getHealth() < underSurge[0].getMaxHealth());
			check(helper, "beside the surge is not hit", besideSurge[0].getHealth() == besideSurge[0].getMaxHealth());
			helper.succeed();
		});
	}

	@GameTest
	public void burstAimSelectsEntityBeforeBlockAndFallsBackToBlock(GameTestHelper helper) {
		ServerPlayer player = caster(helper, true);
		player.setXRot(0); player.setYRot(0);
		BlockPos wall = START.offset(0, 1, 20);
		helper.setBlock(wall, Blocks.STONE);
		Vec3 block = aim(player);
		Husk victim = target(helper, START.offset(0, 0, 10));
		Vec3 centre = victim.getBoundingBox().getCenter();
		check(helper, "living target selected before block", aim(player).distanceTo(centre) < EPSILON);
		Abilities.press(player, ExcaliburNP.KEY.identifier(), ExcaliburNP.BURST, false);
		// Releasing the entity directly takes the same snapshot without waiting for the wind-up.
		light(helper, player).release(player);
		check(helper, "release endpoint is entity centre", Math.abs(light(helper, player).waveDistance() - player.getEyePosition().distanceTo(centre)) < EPSILON);
		Abilities.end(player, ExcaliburNP.KEY.identifier());
		victim.discard();
		check(helper, "no entity falls back to block", aim(player).distanceTo(block) < EPSILON);
		Husk behind = target(helper, START.offset(0, 0, 25));
		check(helper, "entity behind solid block not selected", aim(player).distanceTo(block) < EPSILON);
		behind.discard();
		helper.succeed();
	}

	@GameTest
	public void gougesVaryDeterministicallyGrowAndStretchAlongTravel(GameTestHelper helper) {
		Vec3 surface = new Vec3(0.5, 0, 0.5);
		Vec3 south = new Vec3(0, 0, 1);
		ArtoriaConfig c = FateConfig.artoria();
		check(helper, "smaller near caster", ExcaliburEntity.pathScale(surface, 0, 0) < ExcaliburEntity.pathScale(surface, 0, 1));
		check(helper, "successive crater sizes differ", ExcaliburEntity.pathScale(surface, 0, 0.5) != ExcaliburEntity.pathScale(surface, 1, 0.5));
		check(helper, "spacing varies independently", ExcaliburEntity.pathNoise(surface, 0, 3) != ExcaliburEntity.pathNoise(surface, 1, 3));
		check(helper, "depth varies independently", ExcaliburEntity.pathNoise(surface, 0, 2) != ExcaliburEntity.pathNoise(surface, 0, 1));
		BlockPos along = new BlockPos(0, -1, 3), across = new BlockPos(3, -1, 0);
		check(helper, "elongated along travel", ExcaliburEntity.gougeContains(surface, along, south, SHAPE_RADIUS, DEEP_SHAPE_DEPTH, c.excaliburPathElongation())
			&& !ExcaliburEntity.gougeContains(surface, across, south, SHAPE_RADIUS, DEEP_SHAPE_DEPTH, c.excaliburPathElongation()));
		check(helper, "centre reaches configured bowl depth", ExcaliburEntity.gougeContains(surface, new BlockPos(0, -3, 0), south, SHAPE_RADIUS, DEEP_SHAPE_DEPTH, c.excaliburPathElongation()));
		boolean slopedRim = false;
		for (int x = -RAGGED_SHAPE_EXTENT; x <= RAGGED_SHAPE_EXTENT; x++) {
			for (int z = -RAGGED_SHAPE_EXTENT; z <= RAGGED_SHAPE_EXTENT; z++) {
				BlockPos rim = new BlockPos(x, -1, z);
				slopedRim |= ExcaliburEntity.gougeContains(surface, rim, south, SHAPE_RADIUS, DEEP_SHAPE_DEPTH, c.excaliburPathElongation())
					&& !ExcaliburEntity.gougeContains(surface, rim.below(2), south, SHAPE_RADIUS, DEEP_SHAPE_DEPTH, c.excaliburPathElongation());
			}
		}
		check(helper, "rough rim slopes up from deepest centre", slopedRim);
		for (int i = 0; i < 12; i++) {
			check(helper, "hash repeats", ExcaliburEntity.pathScale(surface, i, 0.5) == ExcaliburEntity.pathScale(surface, i, 0.5));
		}
		helper.succeed();
	}

	@GameTest
	public void furrowIsContinuousRoundedWideningDeepeningAndOrganic(GameTestHelper helper) {
		Vec3 origin = new Vec3(0.5, 0, 0.5);
		ArtoriaConfig c = FateConfig.artoria();
		int run = FURROW_RUN;
		int wandering = 0;
		double nearWidth = 0, farWidth = 0;
		for (int z = 0; z <= run; z++) {
			double progress = z / (double) run;
			check(helper, "continuous along the line", ExcaliburEntity.furrowDepth(origin, progress, 0, 0.5, z + 0.5) > 0);
			check(helper, "rounded across", ExcaliburEntity.furrowDepth(origin, progress, 0, 0.5, z + 0.5)
				> ExcaliburEntity.furrowDepth(origin, progress, FURROW_OFF_CENTRE, 0.5, z + 0.5));
			check(helper, "deterministic", ExcaliburEntity.furrowDepth(origin, progress, 0, 0.5, z + 0.5) == ExcaliburEntity.furrowDepth(origin, progress, 0, 0.5, z + 0.5));
			double edge = 0;
			while (ExcaliburEntity.furrowDepth(origin, progress, edge + FURROW_EDGE_STEP, edge + FURROW_EDGE_STEP + 0.5, z + 0.5) > 0) {
				edge += FURROW_EDGE_STEP;
			}
			double smooth = ExcaliburEntity.furrowHalfWidth(progress);
			if (Math.abs(edge / smooth - 1) > FURROW_WANDER) {
				wandering++;
			}
			if (z < run / FURROW_THIRDS) {
				nearWidth += edge;
			}
			if (z > run - run / FURROW_THIRDS) {
				farWidth += edge;
			}
		}
		check(helper, "wider towards the impact", farWidth > nearWidth);
		check(helper, "deeper towards the impact", ExcaliburEntity.furrowDepth(origin, 1, 0, 0.5, 0.5) > ExcaliburEntity.furrowDepth(origin, 0, 0, 0.5, 0.5));
		check(helper, "edge wanders off the smooth taper, not a straight cut", wandering > FURROW_EDGE_VARIETY);
		helper.succeed();
	}

	@GameTest
	public void excaliburMeleeIsSixteenAtOnePointSix(GameTestHelper helper) {
		ServerPlayer player = caster(helper, false);
		ItemStack sword = player.getMainHandItem();
		FateWeapons.EXCALIBUR.inventoryTick(sword, helper.getLevel(), player, EquipmentSlot.MAINHAND);
		double[] damage = {PLAYER_FIST_DAMAGE};
		double[] speed = {PLAYER_ATTACK_SPEED};
		sword.get(DataComponents.ATTRIBUTE_MODIFIERS).forEach(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
				damage[0] += modifier.amount();
			}
			if (attribute.equals(Attributes.ATTACK_SPEED)) {
				speed[0] += modifier.amount();
			}
		});
		check(helper, "16 attack", damage[0] == 16);
		check(helper, "1.6 attack speed", Math.abs(speed[0] - 1.6) < EPSILON);
		helper.succeed();
	}
}
