package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;

import io.github.cwzmorro.alayacore.item.WeaponRules;
import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.weapon.EmiyaWeaponsConfig;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import io.github.cwzmorro.fatecraft.weapon.PairedThrow;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class EmiyaWeaponsGameTests {
	private static final double EPS = 1e-4;
	private static final Vec3 START = new Vec3(3, 3, 2);
	private static final int FLIGHT_TICKS = 12;
	private static final int CURVE_TARGET_Z = 6;
	private static final double CURVE_SWING = 0.75;
	private static final double HIT_REACH = 0.5;

	@SuppressWarnings("removal")
	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = FateTests.human(helper.makeMockServerPlayerInLevel());
		player.setGameMode(GameType.SURVIVAL);
		player.snapTo(helper.absoluteVec(START));
		player.setYRot(0);
		player.setXRot(0);
		return player;
	}

	private static void pair(ServerPlayer player, boolean reversed) {
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(reversed ? FateWeapons.BAKUYA : FateWeapons.KANSHOU));
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(reversed ? FateWeapons.KANSHOU : FateWeapons.BAKUYA));
	}

	private static void stats(GameTestHelper helper, ServerPlayer player, ItemStack stack, double damage, double speed) {
		stack.getItem().inventoryTick(stack, helper.getLevel(), player, EquipmentSlot.MAINHAND);
		ItemAttributeModifiers attributes = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		check(helper, "damage " + damage, Math.abs(attributes.compute(Attributes.ATTACK_DAMAGE, 1, EquipmentSlot.MAINHAND) - damage) < EPS);
		check(helper, "speed " + speed, Math.abs(attributes.compute(Attributes.ATTACK_SPEED, 4, EquipmentSlot.MAINHAND) - speed) < EPS);
	}

	@GameTest
	public void meleeAloneBothOrdersAndBonusRemoval(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		EmiyaWeaponsConfig config = FateConfig.emiyaWeapons();
		for (boolean reversed : new boolean[] {false, true}) {
			pair(player, reversed);
			ItemStack sword = player.getMainHandItem();
			stats(helper, player, sword, config.pairedDamage(), config.pairedSpeed());
			ItemStack stored = new ItemStack(sword.getItem());
			stored.getItem().inventoryTick(stored, helper.getLevel(), player, null);
			check(helper, "inventory sword has no paired bonus", Math.abs(stored.get(DataComponents.ATTRIBUTE_MODIFIERS)
				.compute(Attributes.ATTACK_DAMAGE, 1, EquipmentSlot.MAINHAND) - config.singleDamage()) < EPS);
			player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			stats(helper, player, sword, config.singleDamage(), config.singleSpeed());
			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(sword.getItem()));
			stats(helper, player, sword, config.singleDamage(), config.singleSpeed());
		}
		ItemStack caladbolg = new ItemStack(FateWeapons.CALADBOLG);
		stats(helper, player, caladbolg, config.caladbolgDamage(), config.caladbolgSpeed());
		check(helper, "Caladbolg is not enchantable or damageable", !caladbolg.isEnchantable() && !caladbolg.isDamageableItem());
		stats(helper, player, new ItemStack(FateWeapons.HRUNTING), config.hruntingDamage(), config.hruntingSpeed());
		helper.succeed();
	}

	@GameTest
	public void goldenDurabilityMeleeBreakAndDeathRules(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		Husk target = helper.spawn(EntityType.HUSK, 5, 3, 5);
		for (Item item : new Item[] {FateWeapons.KANSHOU, FateWeapons.BAKUYA}) {
			ItemStack stack = new ItemStack(item);
			check(helper, "golden sword durability", stack.getMaxDamage() == new ItemStack(Items.GOLDEN_SWORD).getMaxDamage());
			check(helper, "not unbreakable", !stack.has(DataComponents.UNBREAKABLE));
			stack.setDamageValue(stack.getMaxDamage() - 1);
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			item.hurtEnemy(stack, target, player);
			check(helper, "last melee use breaks", stack.isEmpty());
		}
		pair(player, false);
		player.getInventory().add(new ItemStack(FateWeapons.CALADBOLG));
		player.getInventory().dropAll();
		check(helper, "both swords dropped", helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4))
			.stream().filter(entity -> entity.getItem().is(FateWeapons.KANSHOU) || entity.getItem().is(FateWeapons.BAKUYA)).count() == 2);
		check(helper, "only Caladbolg kept", player.getAttachedOrElse(WeaponRules.KEPT, List.of()).size() == 1);
		WeaponRules.giveBack(player);
		check(helper, "Caladbolg returned", player.getInventory().contains(stack -> stack.is(FateWeapons.CALADBOLG)));
		helper.succeed();
	}

	@GameTest
	public void throwRefusedWithoutDifferentPartners(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		for (Item partner : new Item[] {Items.AIR, FateWeapons.KANSHOU, FateWeapons.CALADBOLG, FateWeapons.HRUNTING}) {
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FateWeapons.KANSHOU));
			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(partner));
			check(helper, "refused without Bakuya", !player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).consumesAction());
			check(helper, "sword retained", !player.getMainHandItem().isEmpty());
		}
		check(helper, "no projectiles", helper.getLevel().getEntitiesOfClass(ThrownWeaponEntity.class, player.getBoundingBox().inflate(16)).isEmpty());
		helper.succeed();
	}

	@GameTest
	public void bothThrownStraightInEitherOrder(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		for (HumanoidArm arm : HumanoidArm.values()) {
			player.setMainArm(arm);
			for (boolean reversed : new boolean[] {false, true}) {
				pair(player, reversed);
				Item main = player.getMainHandItem().getItem();
				check(helper, "right-click accepted", player.getOffhandItem().use(helper.getLevel(), player, InteractionHand.OFF_HAND).consumesAction());
				check(helper, "wind-up holds both swords", !player.getMainHandItem().isEmpty() && !player.getOffhandItem().isEmpty());
				check(helper, "attachment registered for sync", PairedThrow.START.isSynced());
				Long start = player.getAttached(PairedThrow.START);
				check(helper, "start tick set on the server at use", start != null && start == helper.getLevel().getGameTime());
				check(helper, "both held swords carry the start tick",
					start.equals(player.getMainHandItem().get(PairedThrow.PAIRED_THROW_START))
						&& start.equals(player.getOffhandItem().get(PairedThrow.PAIRED_THROW_START)));
				player.setAttached(PairedThrow.START, helper.getLevel().getGameTime() - PairedThrow.MAIN_RELEASE);
				PairedThrow.tick(player);
				check(helper, "main releases first", player.getMainHandItem().isEmpty() && !player.getOffhandItem().isEmpty());
				check(helper, "one sword at first release", helper.getLevel().getEntitiesOfClass(ThrownWeaponEntity.class, player.getBoundingBox().inflate(16)).size() == 1);
				player.setAttached(PairedThrow.START, helper.getLevel().getGameTime() - PairedThrow.OFF_RELEASE);
				PairedThrow.tick(player);
				check(helper, "hands consumed", player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty());
				player.removeAttached(PairedThrow.START);
				var swords = helper.getLevel().getEntitiesOfClass(ThrownWeaponEntity.class, player.getBoundingBox().inflate(16));
				check(helper, "two swords", swords.size() == 2);
				for (ThrownWeaponEntity sword : swords) {
					check(helper, "released sword clears equipment pose", sword.item().get(PairedThrow.PAIRED_THROW_START) == null);
					Vec3 from = sword.position();
					// The look vector crossed with world up gives the player's right, without using the throw code.
					Vec3 right = player.getLookAngle().cross(Vec3.Y_AXIS).normalize();
					double side = from.subtract(player.getEyePosition()).dot(right);
					int expectedSide = (arm == HumanoidArm.RIGHT ? 1 : -1) * (sword.item().is(main) ? 1 : -1);
					check(helper, "each sword leaves its own hand's side: " + arm + ", reversed=" + reversed,
						side * expectedSide > 0);
					Vec3 velocity = sword.getDeltaMovement();
					sword.tick();
					check(helper, "constant velocity", sword.getDeltaMovement().distanceTo(velocity) < EPS);
					check(helper, "straight flight without gravity", sword.position().distanceTo(from.add(velocity)) < EPS);
					check(helper, "paired throw damage", Math.abs(sword.item().get(DataComponents.ATTRIBUTE_MODIFIERS)
						.compute(Attributes.ATTACK_DAMAGE, 1, EquipmentSlot.MAINHAND) - FateConfig.emiyaWeapons().pairedDamage()) < EPS);
					sword.discard();
				}
			}
		}
		helper.succeed();
	}

	@GameTest
	public void bothCurveOntoAimedTargetInAnX(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		Husk target = helper.spawn(EntityType.HUSK, 3, 4, CURVE_TARGET_Z);
		target.setNoAi(true);
		target.setNoGravity(true);
		pair(player, false);
		Item main = player.getMainHandItem().getItem();
		player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		player.setAttached(PairedThrow.START, helper.getLevel().getGameTime() - PairedThrow.OFF_RELEASE);
		PairedThrow.tick(player);
		player.removeAttached(PairedThrow.START);
		var swords = helper.getLevel().getEntitiesOfClass(ThrownWeaponEntity.class, player.getBoundingBox().inflate(16));
		check(helper, "two swords", swords.size() == 2);
		Vec3 eye = player.getEyePosition();
		Vec3 aim = target.getBoundingBox().getCenter().subtract(eye).normalize();
		Vec3 right = aim.cross(Vec3.Y_AXIS).normalize();
		for (ThrownWeaponEntity sword : swords) {
			int side = sword.item().is(main) ? 1 : -1;
			double widest = 0;
			for (int tick = 0; tick < FLIGHT_TICKS && !sword.isRemoved(); tick++) {
				sword.tick();
				widest = Math.max(widest, sword.position().subtract(eye).dot(right) * side);
			}
			check(helper, "swings out to its own side " + side + ": " + widest, widest > CURVE_SWING);
			check(helper, "comes back in across the aim line, crossing its partner", sword.getDeltaMovement().dot(right) * side < 0);
			check(helper, "strikes the target", sword.isRemoved() && target.getBoundingBox().inflate(HIT_REACH).contains(sword.position()));
		}
		check(helper, "target hurt", target.getHealth() < target.getMaxHealth());
		helper.succeed();
	}

	@GameTest(maxTicks = FLIGHT_TICKS + 5)
	public void firstTargetOnlyNoPickupOrExplosion(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		pair(player, false);
		Husk first = helper.spawn(EntityType.HUSK, 3, 4, 5);
		Husk behind = helper.spawn(EntityType.HUSK, 3, 4, 7);
		Husk beside = helper.spawn(EntityType.HUSK, 5, 4, 5);
		for (Husk target : new Husk[] {first, behind, beside}) {
			target.setNoAi(true);
			target.setNoGravity(true);
		}
		player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.runAfterDelay(PairedThrow.MAIN_RELEASE, () -> PairedThrow.tick(player));
		helper.runAfterDelay(PairedThrow.OFF_RELEASE, () -> PairedThrow.tick(player));
		helper.runAfterDelay(FLIGHT_TICKS, () -> {
			check(helper, "first hit", first.getHealth() < first.getMaxHealth());
			check(helper, "no piercing", behind.getHealth() == behind.getMaxHealth());
			check(helper, "no area damage", beside.getHealth() == beside.getMaxHealth());
			check(helper, "both broken on impact", helper.getLevel().getEntitiesOfClass(ThrownWeaponEntity.class, player.getBoundingBox().inflate(30)).isEmpty());
			check(helper, "no pickups", helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(10))
				.stream().noneMatch(entity -> entity.getItem().is(FateWeapons.KANSHOU) || entity.getItem().is(FateWeapons.BAKUYA)));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = FLIGHT_TICKS + 5)
	public void blockImpactDestroysBoth(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		pair(player, false);
		for (int x = 2; x <= 4; x++) {
			for (int y = 3; y <= 6; y++) {
				helper.setBlock(x, y, 5, Blocks.STONE);
			}
		}
		player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.runAfterDelay(PairedThrow.MAIN_RELEASE, () -> PairedThrow.tick(player));
		helper.runAfterDelay(PairedThrow.OFF_RELEASE, () -> PairedThrow.tick(player));
		helper.runAfterDelay(FLIGHT_TICKS, () -> {
			check(helper, "broken at wall", helper.getLevel().getEntitiesOfClass(ThrownWeaponEntity.class, player.getBoundingBox().inflate(30)).isEmpty());
			check(helper, "not recoverable", helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(10))
				.stream().noneMatch(entity -> entity.getItem().is(FateWeapons.KANSHOU) || entity.getItem().is(FateWeapons.BAKUYA)));
			helper.assertBlockPresent(Blocks.STONE, 3, 4, 5);
			helper.succeed();
		});
	}
}
