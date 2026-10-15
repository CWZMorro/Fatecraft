package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;

import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.bow.ArcherBows;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.gamerule.AlayaGameRules;
import io.github.cwzmorro.alayacore.item.WeaponStats;
import io.github.cwzmorro.alayacore.progression.TrueName;
import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.trace.Projections;
import io.github.cwzmorro.fatecraft.weapon.EmiyaBowItem;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import java.util.Comparator;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class EmiyaBowGameTests {
	private static final double EPS = 1e-4;
	// Arrow speed is read after vanilla's own inaccuracy and drag, so it only matches the formula this closely.
	private static final double ARROW_SPEED_TOLERANCE = 0.15;
	private static final BlockPos PLAYER = new BlockPos(3, 3, 2);
	private static final Vec3 START = new Vec3(3.5, 4.5, 2.5);
	private static final Vec3 FORWARD = new Vec3(0, 0, 3);

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = FateTests.servant(helper, PLAYER, 0, FateServants.EMIYA);
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		return player;
	}

	private static boolean release(GameTestHelper helper, ServerPlayer player, ItemStack bow, int ticks) {
		return bow.getItem().releaseUsing(bow, helper.getLevel(), player, bow.getItem().getUseDuration(bow, player) - ticks);
	}

	@GameTest
	public void projectionsFirstAndNoSwordEvenCreative(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		ItemStack bow = new ItemStack(FateWeapons.EMIYA_BOW);
		player.setItemInHand(InteractionHand.MAIN_HAND, bow);
		player.getInventory().setItem(1, new ItemStack(Items.ARROW));
		check(helper, "no sword refuses draw", !bow.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).consumesAction());
		check(helper, "no sword refuses release", !release(helper, player, bow, 20));
		player.setGameMode(GameType.CREATIVE);
		check(helper, "creative still requires sword", !bow.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).consumesAction());
		player.getInventory().setItem(2, new ItemStack(Items.DIAMOND_SWORD));
		player.getInventory().setItem(8, Projections.create(Items.IRON_SWORD));
		check(helper, "projection selected before real", EmiyaBowItem.ammo(player, false).is(Items.IRON_SWORD));
		check(helper, "shoots with sword", release(helper, player, bow, 20));
		check(helper, "projection consumed even creative", player.getInventory().getItem(8).isEmpty());
		check(helper, "real sword retained", player.getInventory().getItem(2).is(Items.DIAMOND_SWORD));
		helper.succeed();
	}

	@GameTest
	public void tripleCountsAndCaladbolgException(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		ItemStack bow = new ItemStack(FateWeapons.EMIYA_BOW);
		player.setItemInHand(InteractionHand.MAIN_HAND, bow);
		bow.set(EmiyaBowItem.BOW_TRIPLE, true);
		for (int count = 0; count <= FateConfig.bow().tripleCount(); count++) {
			player.getInventory().setItem(1, Projections.create(FateWeapons.CALADBOLG));
			for (int i = 0; i < count; i++) {
				player.getInventory().setItem(2 + i, new ItemStack(Items.IRON_SWORD));
			}
			check(helper, "triple release result", release(helper, player, bow, 20) == (count > 0));
			var shots = helper.getLevel().getEntitiesOfClass(ThrownWeaponEntity.class, player.getBoundingBox().inflate(8));
			check(helper, "triple shoots available count " + count, shots.size() == count);
			check(helper, "Caladbolg never consumed in triple", player.getInventory().getItem(1).is(FateWeapons.CALADBOLG));
			shots.forEach(ThrownWeaponEntity::discard);
		}
		bow.set(EmiyaBowItem.BOW_TRIPLE, false);
		check(helper, "normal shoots Caladbolg", release(helper, player, bow, 20));
		check(helper, "normal consumes Caladbolg", player.getInventory().getItem(1).isEmpty());
		helper.succeed();
	}

	@GameTest
	public void ordinaryEyeAimOriginAndParallelVolley(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		for (HumanoidArm arm : HumanoidArm.values()) {
			player.setMainArm(arm);
			for (InteractionHand hand : InteractionHand.values()) {
				for (float[] aim : new float[][] {{0, 0}, {35, -25}, {-60, 30}}) {
					player.setYRot(aim[0]);
					player.setXRot(aim[1]);
					Vec3 direction = player.getLookAngle();
					Vec3 right = new Vec3(-direction.z, 0, direction.x).normalize();
					Vec3 origin = player.getEyePosition().add(direction.scale(EmiyaBowItem.NOCK_FORWARD));
					Vec3 velocity = direction.scale(ArcherBows.VANILLA_SPEED * FateConfig.bow().swordSpeedMultiplier());
					for (boolean triple : new boolean[] {false, true}) {
						player.getInventory().clearContent();
						ItemStack bow = new ItemStack(FateWeapons.EMIYA_BOW);
						bow.set(EmiyaBowItem.BOW_TRIPLE, triple);
						player.setItemInHand(hand, bow);
						int count = triple ? FateConfig.bow().tripleCount() : 1;
						for (int i = 0; i < count; i++) {
							player.getInventory().setItem(1 + i, new ItemStack(Items.IRON_SWORD));
						}
						check(helper, "ordinary release succeeds", release(helper, player, bow, 20));
						var shots = helper.getLevel().getEntitiesOfClass(ThrownWeaponEntity.class, player.getBoundingBox().inflate(8));
						shots.sort(Comparator.comparingDouble(shot -> shot.position().subtract(origin).dot(right)));
						check(helper, "ordinary volley count", shots.size() == count);
						for (int i = 0; i < count; i++) {
							Vec3 expected = origin.add(right.scale(triple ? (i - (count - 1) / 2.0) * FateConfig.bow().volleySpacing() : 0));
							check(helper, "eye/aim tail origin and configured spacing", shots.get(i).position().distanceTo(expected) < EPS);
							check(helper, "parallel full-power velocity preserved", shots.get(i).getDeltaMovement().distanceTo(velocity) < EPS);
							check(helper, "renderer tail marker preserved", shots.get(i).item().getOrDefault(FateWeapons.THROWN_FROM_TAIL, false));
						}
						shots.forEach(ThrownWeaponEntity::discard);
					}
				}
			}
		}
		helper.succeed();
	}

	@GameTest
	public void projectedAndRealVanishOnEntityHit(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		for (boolean projected : new boolean[] {false, true}) {
			var target = FateTests.mob(helper, new BlockPos(3, 4, 5));
			ItemStack sword = projected ? Projections.create(Items.IRON_SWORD) : new ItemStack(Items.IRON_SWORD);
			ThrownWeaponEntity shot = EmiyaBowItem.shoot(helper.getLevel(), player, sword, helper.absoluteVec(START), FORWARD, true);
			shot.tick();
			check(helper, "entity hit damages", target.getHealth() < target.getMaxHealth());
			check(helper, "shot disappears on hit", shot.isRemoved());
			target.discard();
		}
		check(helper, "no recoverable item", helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8)).isEmpty());
		helper.succeed();
	}

	@GameTest
	public void partialShotBreaksWithoutBlast(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		helper.setBlock(3, 4, 5, Blocks.GLASS);
		for (boolean projected : new boolean[] {false, true}) {
			ItemStack sword = projected ? Projections.create(Items.IRON_SWORD) : new ItemStack(Items.IRON_SWORD);
			ThrownWeaponEntity shot = EmiyaBowItem.shoot(helper.getLevel(), player, sword, helper.absoluteVec(START), FORWARD, false);
			shot.tick();
			check(helper, "shot breaks on ground", shot.isRemoved());
			helper.assertBlockPresent(Blocks.GLASS, 3, 4, 5);
		}
		helper.succeed();
	}

	@GameTest(environment = "fatecraft-gametest:isolated/bow_block_damage")
	public void blastPowerAndGameRuleAndShooterDamage(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		check(helper, "attack over the configured divisor", Math.abs(EmiyaBowItem.blastPower(new ItemStack(Items.IRON_SWORD))
			- WeaponStats.attackDamage(new ItemStack(Items.IRON_SWORD)) / FateConfig.bow().blastDivisor()) < EPS);
		check(helper, "Caladbolg's configured power", EmiyaBowItem.blastPower(Projections.create(FateWeapons.CALADBOLG))
			== FateConfig.bow().caladbolgBlast());
		var level = helper.getLevel();
		player.snapTo(helper.absoluteVec(new Vec3(3.5, 4, 4)));
		boolean previous = level.getGameRules().get(AlayaGameRules.WEAPON_BLOCK_DAMAGE);
		check(helper, "block damage on by default", previous);
		try {
			for (boolean breaks : new boolean[] {false, true}) {
				level.getGameRules().set(AlayaGameRules.WEAPON_BLOCK_DAMAGE, breaks, level.getServer());
				helper.setBlock(3, 4, 5, Blocks.GLASS);
				player.invulnerableTime = 0;
				player.setHealth(player.getMaxHealth());
				float health = player.getHealth();
				ThrownWeaponEntity shot = EmiyaBowItem.shoot(level, player, new ItemStack(Items.IRON_SWORD), helper.absoluteVec(START), FORWARD, true);
				shot.tick();
				check(helper, "full shot consumed", shot.isRemoved());
				check(helper, "shooter hurt by blast", player.getHealth() < health);
				check(helper, "game rule governs blocks only", level.getBlockState(helper.absolutePos(new BlockPos(3, 4, 5))).is(Blocks.GLASS) != breaks);
				check(helper, "no fire", !level.getBlockState(helper.absolutePos(new BlockPos(3, 5, 5))).is(Blocks.FIRE));
			}
		} finally {
			level.getGameRules().set(AlayaGameRules.WEAPON_BLOCK_DAMAGE, previous, level.getServer());
		}
		helper.succeed();
	}

	@GameTest
	public void drawByBarAndOrdinaryArcherArrowSpeed(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		ItemStack bow = new ItemStack(Items.BOW);
		player.setItemInHand(InteractionHand.MAIN_HAND, bow);
		for (double mana : new double[] {1000, 3000, 10000}) {
			AlayaMana.addMax(player, mana - AlayaMana.get(player).max());
			for (boolean realized : new boolean[] {false, true}) {
				if (realized) {
					TrueName.realize(player);
				} else {
					TrueName.unrealize(player);
				}
				AlayaMana.update(player, value -> value.withMax(mana));
				double bar = AlayaConfig.progression().barFill(Math.log10(mana / 1000), realized);
				double drawTicks = BowItem.MAX_DRAW_DURATION - AlayaConfig.archerBows().archerDrawReduction() * bar;
				check(helper, "draw time by bar", Math.abs(ArcherBows.drawTicks(ArcherBows.bar(player)) - drawTicks) < EPS);
				int ticks = (int) Math.ceil(drawTicks);
				check(helper, "full draw threshold", ArcherBows.power(player, ticks) == 1);
				check(helper, "tick before is partial", ArcherBows.power(player, ticks - 1) < 1);
				player.getInventory().setItem(1, new ItemStack(Items.ARROW));
				check(helper, "ordinary bow fired", release(helper, player, bow, ticks));
				var arrows = helper.getLevel().getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(8));
				check(helper, "one vanilla arrow", arrows.size() == 1);
				check(helper, "Archer speed scaled by bar", Math.abs(arrows.getFirst().getDeltaMovement().length()
					- ArcherBows.VANILLA_SPEED * (1 + AlayaConfig.archerBows().archerSpeedBonus() * bar)) < ARROW_SPEED_TOLERANCE);
				arrows.forEach(AbstractArrow::discard);
			}
		}
		check(helper, "true-name full bar takes the whole reduction off", ArcherBows.drawTicks(ArcherBows.bar(player))
			== BowItem.MAX_DRAW_DURATION - AlayaConfig.archerBows().archerDrawReduction());
		check(helper, "normal bow remains vanilla draw for humans", BowItem.getPowerForTime(20) == 1);
		helper.succeed();
	}
}
