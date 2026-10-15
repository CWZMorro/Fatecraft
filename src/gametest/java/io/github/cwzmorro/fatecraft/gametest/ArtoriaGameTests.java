package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.combat.Attacks;
import io.github.cwzmorro.alayacore.combat.ServantBonus;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.artoria.ArtoriaAbility;
import io.github.cwzmorro.fatecraft.artoria.ArtoriaAttackEntity;
import io.github.cwzmorro.fatecraft.artoria.ArtoriaConfig;
import io.github.cwzmorro.fatecraft.artoria.ExcaliburNP;
import io.github.cwzmorro.fatecraft.artoria.ManaBurst;
import io.github.cwzmorro.fatecraft.artoria.ManaShot;
import io.github.cwzmorro.fatecraft.artoria.ManaSlash;
import io.github.cwzmorro.fatecraft.artoria.StrikeAir;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.weapon.CaliburnConfig;
import io.github.cwzmorro.fatecraft.weapon.EmiyaBowItem;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class ArtoriaGameTests {
	private static final BlockPos START = new BlockPos(2, 2, 2);
	private static final BlockPos TARGET = new BlockPos(2, 2, 7);
	private static final List<Identifier> SKILLS = List.of(StrikeAir.KEY.identifier(), ManaBurst.KEY.identifier(), ManaSlash.KEY.identifier(), ManaShot.KEY.identifier());
	private static final double EPSILON = 0.01;
	private static final int STRIKE_HIT_SETTLE_TICKS = 1;
	private static final int STRIKE_MODE = 0;
	private static final double STRIKE_TARGET_SHARE = 0.35, STRIKE_ANGLE_MARGIN = 15;
	private static final double SLASH_SIDE_SHARE = 0.6, SLASH_FORWARD_SHARE = 0.4;
	private static final double SLASH_ARC_MARGIN_DEGREES = 5, SLASH_ARC_TARGET_SHARE = 0.7;
	private static final int SLASH_SETTLE_TICKS = 2;
	private static final double SLASH_BEYOND_SHARE = 1.2, SLASH_CLOSE_SHARE = 0.15;
	private static final int SLASH_FIXTURE_MARGIN = 2;
	private static final int BURST_SETTLE_TICKS = 5;

	@GameTest
	public void defaultsMatchTheDesignedValues(GameTestHelper helper) {
		ArtoriaConfig designed = ArtoriaConfig.DEFAULTS;
		check(helper, "ranks C, A, C, B and A++", StrikeAir.RANK.equals(Rank.parse("C")) && ManaBurst.RANK.equals(Rank.parse("A"))
			&& ManaSlash.RANK.equals(Rank.parse("C")) && ManaShot.RANK.equals(Rank.parse("B")) && ExcaliburNP.RANK.equals(Rank.parse("A++")));
		check(helper, "cooldowns 8, 60, 10, 20 and Excalibur's 60 seconds", ArtoriaConfig.STRIKE_COOLDOWN == 8 && ArtoriaConfig.BURST_COOLDOWN == 60
			&& ArtoriaConfig.SLASH_COOLDOWN == 10 && ArtoriaConfig.SHOT_COOLDOWN == 20 && designed.excaliburCooldownSeconds() == 60);
		check(helper, "Strike Air knockback 1.5", designed.strikeKnockback() == 1.5);
		check(helper, "Mana Burst: 5 s, +75%, Speed III, 200 mana", designed.burstSeconds() == 5 && designed.attackBonus() == 0.75
			&& designed.speedAmplifier() == 2 && designed.burstMana() == 200);
		check(helper, "Mana Slash: 120 degrees, 5 blocks", designed.slashArcDegrees() == 120 && designed.slashRange() == 5);
		check(helper, "Mana Shot: 40 blocks, blast radius 4", designed.shotRange() == 40 && designed.blastRadius() == 4);
		check(helper, "Excalibur melee 16 at 1.6; Caliburn 12 at 1.6", designed.excaliburDamage() == 16 && designed.excaliburSpeed() == 1.6
			&& CaliburnConfig.DEFAULTS.damage() == 12 && CaliburnConfig.DEFAULTS.speed() == 1.6);
		check(helper, "Beam: 4 s wind-up, 200 blocks, 3 across widening to 12", designed.excaliburWindUpSeconds() == 4 && designed.excaliburBeamRange() == 200
			&& designed.excaliburStartRadius() * 2 == 3 && designed.excaliburEndRadius() * 2 == 12);
		check(helper, "Burst: 100 blocks, radius 16, light radius 10, height 64", designed.excaliburBurstRange() == 100 && designed.excaliburBurstRadius() == 16
			&& designed.excaliburBurstVisualRadius() == 10 && designed.excaliburBurstHeight() == 64);
		check(helper, "path: lead-in 2, furrow a third to three quarters, noise cell 3, roughness 0.6", designed.excaliburPathLeadIn() == 2
			&& designed.excaliburPathFurrowNear() == 0.33 && designed.excaliburPathFurrowFar() == 0.75 && designed.excaliburPathNoiseCell() == 3
			&& designed.excaliburPathRoughness() == 0.6);
		check(helper, "pits: radius 2, scale 1.6, spacing 9", designed.excaliburPathRadius() == 2 && designed.excaliburPathPitScale() == 1.6
			&& designed.excaliburPathSpacing() == 9);
		helper.succeed();
	}

	@GameTest
	public void everySkillPaysRankCostAcceptsEitherSwordAndRejectsOtherItems(GameTestHelper helper) {
		for (Identifier id : SKILLS) {
			ArtoriaAbility ability = (ArtoriaAbility) AlayaRegistries.ABILITY.getValue(id);
			ServerPlayer poor = player(helper);
			AlayaMana.update(poor, mana -> mana.withCurrent(0));
			Abilities.press(poor, id, 0, false);
			check(helper, "insufficient mana refuses", !Abilities.isActive(poor, id) && AlayaMana.available(poor) == 0);
			for (Item sword : List.of(FateWeapons.CALIBURN, FateWeapons.EXCALIBUR)) {
				ServerPlayer player = player(helper);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
				double mana = AlayaMana.available(player);
				Abilities.press(player, id, 0, false);
				check(helper, id + " rejects stick without payment", !Abilities.isActive(player, id) && AlayaMana.available(player) == mana);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(sword));
				Abilities.press(player, id, 0, false);
				check(helper, id + " accepts " + sword, Abilities.isActive(player, id));
				check(helper, "rank-derived cost", Math.abs(mana - AlayaMana.available(player) - ability.startCost(player, 0)) < EPSILON);
				if (!id.equals(ManaBurst.KEY.identifier())) {
					ArtoriaAttackEntity attack = attack(helper, player);
					check(helper, "skill rank metadata, not NP", attack.getAttached(Attacks.ATTACK).rank().equals(ability.rank())
						&& !attack.getAttached(Attacks.ATTACK).noblePhantasm());
				}
				Abilities.endAll(player);
				check(helper, "cooldown starts", Abilities.cooldownSecondsLeft(player, id, 0) == AlayaConfig.abilities().cooldownSeconds(id, ability, 0));
				double after = AlayaMana.available(player);
				Abilities.press(player, id, 0, false);
				check(helper, "cooldown refuses without paying", !Abilities.isActive(player, id) && AlayaMana.available(player) == after);
			}
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 140)
	public void manaBurstMultipliesAttackGivesSpeedThreeAndExpires(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		// A mock player's effects don't count down by themselves, as a real player's do.
		helper.onEachTick(player::baseTick);
		double base = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
		FateTests.tap(player, ManaBurst.KEY.identifier(), 0, false);
		check(helper, "+75% total attack", Math.abs(player.getAttributeValue(Attributes.ATTACK_DAMAGE) - base * (1 + FateConfig.artoria().attackBonus())) < EPSILON);
		check(helper, "Speed III for five seconds", player.getEffect(MobEffects.SPEED).getAmplifier() == 2 && player.getEffect(MobEffects.SPEED).getDuration() == 100);
		helper.runAfterDelay(101, () -> {
			check(helper, "buff ended", !Abilities.isActive(player, ManaBurst.KEY.identifier()));
			check(helper, "attack restored", Math.abs(player.getAttributeValue(Attributes.ATTACK_DAMAGE) - base) < EPSILON);
			check(helper, "speed cleaned", !player.hasEffect(MobEffects.SPEED));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = BURST_SETTLE_TICKS + 5)
	public void manaBurstOutlastsTheSwordAndRaisesBareHandedAndOtherWeaponHits(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		Husk target = target(helper, TARGET);
		ItemStack ironSword = new ItemStack(Items.IRON_SWORD);
		float fist = meleeHit(player, ItemStack.EMPTY, target);
		float sword = meleeHit(player, ironSword, target);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FateWeapons.CALIBURN));
		FateTests.tap(player, ManaBurst.KEY.identifier(), 0, false);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.runAfterDelay(BURST_SETTLE_TICKS, () -> {
			check(helper, "letting go of the sword does not end it", Abilities.isActive(player, ManaBurst.KEY.identifier()) && ManaBurst.active(player));
			double bonus = 1 + FateConfig.artoria().attackBonus();
			float boostedFist = meleeHit(player, ItemStack.EMPTY, target);
			check(helper, "a bare-handed hit: " + fist + " -> " + boostedFist, Math.abs(boostedFist - fist * bonus) < EPSILON);
			float boostedSword = meleeHit(player, ironSword, target);
			check(helper, "another weapon's hit: " + sword + " -> " + boostedSword, Math.abs(boostedSword - sword * bonus) < EPSILON);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void manaBurstRaisesManaSlashButNoOtherSkill(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		Husk target = target(helper, TARGET);
		target.setPos(player.position().add(0, 0, FateConfig.artoria().slashRange() * SLASH_FORWARD_SHARE));
		player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
		Vec3 spot = target.position();
		double slash = AlayaConfig.ranks().skillDamage(ManaSlash.RANK, ServantBonus.classAttack(player));
		ArtoriaAbility strike = (ArtoriaAbility) AlayaRegistries.ABILITY.getValue(StrikeAir.KEY);
		FateTests.tap(player, ManaBurst.KEY.identifier(), 0, false);
		FateTests.tap(player, ManaSlash.KEY.identifier(), 0, false);
		helper.runAfterDelay(Durations.ticks(FateConfig.artoria().slashSweepSeconds()) + SLASH_SETTLE_TICKS, () -> {
			double lost = target.getMaxHealth() - target.getHealth();
			check(helper, "Mana Slash raised: lost " + lost, Math.abs(lost - slash * (1 + FateConfig.artoria().attackBonus())) < EPSILON);
			target.setHealth(target.getMaxHealth());
			target.invulnerableTime = 0;
			target.setPos(spot);
			FateTests.tap(player, StrikeAir.KEY.identifier(), STRIKE_MODE, false);
			check(helper, "Strike Air not raised", Math.abs(target.getMaxHealth() - target.getHealth() - strike.damage(player)) < EPSILON);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 45)
	public void strikeAirInstantlyHitsRankCDeflectsSwordsAndArrowsWithoutMoving(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		Husk target = target(helper, TARGET);
		target.setPos(player.position().add(0, 0, FateConfig.artoria().strikeConeLength() * STRIKE_TARGET_SHARE));
		player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
		Vec3 start = player.position();
		Vec3 forward = target.position().subtract(start).normalize();
		StrikeAir ability = (StrikeAir) AlayaRegistries.ABILITY.getValue(StrikeAir.KEY);
		FateTests.tap(player, StrikeAir.KEY.identifier(), 0, false);
		ThrownWeaponEntity[] sword = {null};
		Arrow[] arrow = {null};
		Vec3[] slash = {null};
		helper.runAfterDelay(2, () -> {
			check(helper, "caster does not move during wind burst", player.position().distanceTo(start) < EPSILON);
			Vec3 centre = player.getBoundingBox().getCenter();
			sword[0] = EmiyaBowItem.shoot(helper.getLevel(), player, new ItemStack(Items.IRON_SWORD), centre.add(forward.scale(2)), forward.scale(-0.25), false);
			arrow[0] = helper.spawn(EntityType.ARROW, START);
			arrow[0].setPos(centre.add(forward.scale(2)).add(0, 0.5, 0));
			arrow[0].setDeltaMovement(forward.scale(-0.25));
			arrow[0].setNoGravity(true);
		});
		helper.runAfterDelay(3, () -> {
			check(helper, "sword deflected", sword[0].getDeltaMovement().dot(forward) > 0);
			check(helper, "arrow deflected", arrow[0].getDeltaMovement().dot(forward) > 0);
			sword[0].discard();
			arrow[0].discard();
		});
		helper.runAfterDelay(8, () -> {
			check(helper, "rank C slash damage: lost=" + (target.getMaxHealth() - target.getHealth()) + ", expected=" + ability.damage(player) + ", at=" + player.position(), Math.abs(target.getMaxHealth() - target.getHealth() - ability.damage(player)) < EPSILON);
			check(helper, "caster remains at cast position", player.position().distanceTo(start) < EPSILON);
			slash[0] = player.position();
		});
		helper.runAfterDelay(20, () -> {
			check(helper, "no backward jump after blast", slash[0].distanceTo(player.position()) < EPSILON);
			check(helper, "cast ended and eight second cooldown", !Abilities.isActive(player, StrikeAir.KEY.identifier()) && Abilities.cooldownSecondsLeft(player, StrikeAir.KEY.identifier(), 0) > 7);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 35)
	public void strikeAirKnocksHitHuskAwayFromCaster(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		Husk target = target(helper, TARGET);
		target.setPos(player.position().add(0, 0, FateConfig.artoria().strikeConeLength() * STRIKE_TARGET_SHARE));
		player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
		FateTests.tap(player, StrikeAir.KEY.identifier(), STRIKE_MODE, false);
		check(helper, "cone damage happens immediately on cast", target.getHealth() < target.getMaxHealth());
		helper.runAfterDelay(STRIKE_HIT_SETTLE_TICKS, () -> {
			check(helper, "wind slash hit the husk", target.getHealth() < target.getMaxHealth());
			Vec3 offset = target.position().subtract(player.position());
			Vec3 away = new Vec3(offset.x, 0, offset.z).normalize();
			check(helper, "hit husk has horizontal velocity away from caster", target.getDeltaMovement().dot(away) > EPSILON);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 35)
	public void strikeAirWithoutTargetDoesNotMoveCaster(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		Vec3 start = player.position();
		player.setXRot(-90);
		FateTests.tap(player, StrikeAir.KEY.identifier(), 0, false);
		helper.runAfterDelay(8, () -> {
			check(helper, "no target means no caster movement", player.position().distanceTo(start) < EPSILON);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 25)
	public void strikeAirExcludesTargetsOutsideLoadedCone(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		Vec3 start = player.position();
		player.setYRot(0);
		player.setXRot(0);
		double angle = Math.toRadians(FateConfig.artoria().strikeConeDegrees() / 2 + STRIKE_ANGLE_MARGIN);
		double distance = FateConfig.artoria().strikeConeLength() * STRIKE_TARGET_SHARE;
		Husk outside = target(helper, TARGET);
		outside.setPos(start.add(Math.sin(angle) * distance, 0, Math.cos(angle) * distance));
		Husk behind = target(helper, TARGET);
		behind.setPos(start.subtract(0, 0, distance));
		FateTests.tap(player, StrikeAir.KEY.identifier(), 0, false);
		helper.runAfterDelay(STRIKE_HIT_SETTLE_TICKS, () -> {
			check(helper, "outside cone is not damaged", outside.getHealth() == outside.getMaxHealth());
			check(helper, "outside cone is not knocked back", outside.getDeltaMovement().lengthSqr() < EPSILON);
			check(helper, "rear target is not hit", behind.getHealth() == behind.getMaxHealth());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60, padding = 16)
	public void manaSlashAndManaShotBlastLandTheirSkillRanks(GameTestHelper helper) {
		ServerPlayer gold = player(helper);
		// Rear and range targets can lie outside the generated structure; clearing their volume too keeps suffocation damage out of the results.
		int reach = (int) Math.ceil(FateConfig.artoria().slashRange() * SLASH_BEYOND_SHARE) + SLASH_FIXTURE_MARGIN;
		for (BlockPos pos : BlockPos.betweenClosed(START.offset(-reach, -SLASH_FIXTURE_MARGIN, -reach),
			START.offset(reach, SLASH_FIXTURE_MARGIN, reach))) {
			helper.setBlock(pos, Blocks.AIR);
		}
		Husk goldenTarget = target(helper, TARGET);
		gold.lookAt(EntityAnchorArgument.Anchor.EYES, goldenTarget.getEyePosition());
		double range = FateConfig.artoria().slashRange();
		Vec3 origin = gold.position();
		Husk side = target(helper, TARGET);
		side.setPos(origin.add(range * SLASH_SIDE_SHARE, 0, range * SLASH_FORWARD_SHARE));
		Husk close = target(helper, TARGET);
		close.setPos(origin.add(0, 0, range * SLASH_CLOSE_SHARE));
		Husk behind = target(helper, TARGET);
		behind.setPos(origin.add(0, 0, -range * SLASH_FORWARD_SHARE));
		Husk beyond = target(helper, TARGET);
		beyond.setPos(origin.add(0, 0, range * SLASH_BEYOND_SHARE));
		double halfArc = Math.toRadians(FateConfig.artoria().slashArcDegrees()) / 2;
		double margin = Math.toRadians(SLASH_ARC_MARGIN_DEGREES);
		Husk insideLeft = arcTarget(helper, origin, range, -halfArc + margin);
		Husk insideRight = arcTarget(helper, origin, range, halfArc - margin);
		Husk outsideLeft = arcTarget(helper, origin, range, -halfArc - margin);
		Husk outsideRight = arcTarget(helper, origin, range, halfArc + margin);
		ArtoriaAbility slash = (ArtoriaAbility) AlayaRegistries.ABILITY.getValue(ManaSlash.KEY);
		FateTests.tap(gold, ManaSlash.KEY.identifier(), 0, false);
		Husk[] shotTargets = new Husk[2];
		ArtoriaAbility shotAbility = (ArtoriaAbility) AlayaRegistries.ABILITY.getValue(ManaShot.KEY);
		// The sweep reaches the arc's far edge only on its last tick, so wait for the blast rather than a count of ticks.
		helper.startSequence().thenWaitUntil(() -> check(helper, "Mana Slash's sweep finishes", blasted(helper, gold))).thenExecute(() -> {
			check(helper, "Mana Slash configured rank hit: lost=" + (goldenTarget.getMaxHealth() - goldenTarget.getHealth()) + ", expected=" + slash.damage(gold), Math.abs(goldenTarget.getMaxHealth() - goldenTarget.getHealth() - slash.damage(gold)) < EPSILON);
			boolean sideInArc = Math.atan2(SLASH_SIDE_SHARE, SLASH_FORWARD_SHARE) <= halfArc;
			check(helper, "off-axis hit follows loaded arc", Math.abs(side.getMaxHealth() - side.getHealth() - (sideInArc ? slash.damage(gold) : 0)) < EPSILON);
			for (Husk inside : List.of(insideLeft, insideRight)) {
				check(helper, "hits just inside configured arc once", Math.abs(inside.getMaxHealth() - inside.getHealth() - slash.damage(gold)) < EPSILON);
			}
			for (Husk outside : List.of(outsideLeft, outsideRight)) {
				check(helper, "excludes just outside configured arc", outside.getHealth() == outside.getMaxHealth());
			}
			check(helper, "melee sweep starts near caster", Math.abs(close.getMaxHealth() - close.getHealth() - slash.damage(gold)) < EPSILON);
			check(helper, "crescent excludes rear targets", behind.getHealth() == behind.getMaxHealth());
			check(helper, "crescent stops at configured range", beyond.getHealth() == beyond.getMaxHealth());
			Abilities.endAll(gold);
			goldenTarget.discard();
			side.discard();
			close.discard();
			behind.discard();
			beyond.discard();
			for (Husk boundary : List.of(insideLeft, insideRight, outsideLeft, outsideRight)) {
				boundary.discard();
			}
			// Reuse the already-ticked mock: registering onEachTick from a scheduled callback mutates
			// GameTestInfo's task map during iteration (26.1.2 can crash its fastutil iterator).
			ServerPlayer shot = gold;
			Husk victim = target(helper, TARGET);
			Husk splash = target(helper, TARGET.east(2));
			shot.lookAt(EntityAnchorArgument.Anchor.EYES, victim.getEyePosition());
			shotTargets[0] = victim; shotTargets[1] = splash;
			FateTests.tap(shot, ManaShot.KEY.identifier(), 0, false);
			check(helper, "shot gathers light first", attack(helper, shot).phase() == ArtoriaAttackEntity.Phase.CHARGE && victim.getHealth() == victim.getMaxHealth());
		}).thenWaitUntil(() -> check(helper, "Mana Shot blasts", blasted(helper, gold))).thenExecute(() -> {
			Husk victim = shotTargets[0], splash = shotTargets[1];
			check(helper, "shot rank B direct hit", Math.abs(victim.getMaxHealth() - victim.getHealth() - shotAbility.damage(gold)) < EPSILON);
			check(helper, "golden blast hits neighbour once", Math.abs(splash.getMaxHealth() - splash.getHealth() - shotAbility.damage(gold)) < EPSILON);
		}).thenSucceed();
	}

	// The weapon's attributes are applied as a real player's are between swings (a mock player's only on its tick), with its swing charge at the same low point every time.
	private static float meleeHit(ServerPlayer player, ItemStack weapon, Husk target) {
		player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
		ItemAttributeModifiers modifiers = weapon.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		modifiers.forEach(EquipmentSlot.MAINHAND, (attribute, modifier) -> player.getAttribute(attribute).addOrUpdateTransientModifier(modifier));
		target.setHealth(target.getMaxHealth());
		target.invulnerableTime = 0;
		player.resetAttackStrengthTicker();
		player.attack(target);
		modifiers.forEach(EquipmentSlot.MAINHAND, (attribute, modifier) -> player.getAttribute(attribute).removeModifier(modifier.id()));
		return target.getMaxHealth() - target.getHealth();
	}

	private static Husk arcTarget(GameTestHelper helper, Vec3 origin, double range, double angle) {
		Husk target = target(helper, TARGET);
		target.setPos(origin.add(Math.sin(angle) * range * SLASH_ARC_TARGET_SHARE, 0,
			Math.cos(angle) * range * SLASH_ARC_TARGET_SHARE));
		return target;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		for (BlockPos pos : BlockPos.betweenClosed(START.offset(-2, -1, -2), START.offset(6, 18, 8))) {
			helper.setBlock(pos, Blocks.AIR);
		}
		ServerPlayer player = FateTests.servant(helper, START, FateTests.MANA, FateServants.ARTORIA);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FateWeapons.CALIBURN));
		return player;
	}

	private static Husk target(GameTestHelper helper, BlockPos pos) {
		Husk target = FateTests.mob(helper, pos);
		target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
		target.getAttribute(Attributes.ARMOR).setBaseValue(0);
		target.setHealth(target.getMaxHealth());
		return target;
	}

	// A light in a chunk that has yet to load its entities is not found, so it counts as not done.
	private static boolean blasted(GameTestHelper helper, ServerPlayer player) {
		return helper.getLevel().getEntitiesOfClass(ArtoriaAttackEntity.class, player.getBoundingBox().inflate(64), attack -> attack.owner() == player)
			.stream().anyMatch(attack -> attack.phase() == ArtoriaAttackEntity.Phase.BLAST);
	}

	private static ArtoriaAttackEntity attack(GameTestHelper helper, ServerPlayer player) {
		return helper.getLevel().getEntitiesOfClass(ArtoriaAttackEntity.class, player.getBoundingBox().inflate(64), attack -> attack.owner() == player).getFirst();
	}
}
