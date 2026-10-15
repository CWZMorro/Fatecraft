package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;
import static io.github.cwzmorro.fatecraft.gametest.FateTests.mob;

import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.item.LostToVoid;
import io.github.cwzmorro.alayacore.item.WeaponRules;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.alayacore.servant.ServantStartingItems;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.trace.Traces;
import io.github.cwzmorro.fatecraft.weapon.CaliburnConfig;
import io.github.cwzmorro.fatecraft.weapon.EaConfig;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import io.github.cwzmorro.fatecraft.weapon.WeaponSpin;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class WeaponGameTests {
	private static final BlockPos FLOOR = new BlockPos(1, 1, 1);
	private static final BlockPos WIELDER = new BlockPos(7, 2, 7);
	private static final int NEAR = 3;
	private static final int FAR = 8;
	private static final int SHIELD_GAP = 2;
	private static final float FACING_WEST = 90.0F;
	private static final Rank SHIELD_RANK = Rank.parse("EX");
	private static final double EPS = 1e-4;
	private static final int TICKS_IN_LAVA = 40;
	private static final float EXPLOSION_POWER = 4.0F;
	private static final double BELOW_THE_WORLD = 100.0;
	private static final List<ItemStack> LOST_TO_VOID = new ArrayList<>();

	static {
		LostToVoid.register(Items.ECHO_SHARD, (level, item) -> LOST_TO_VOID.add(item.getItem().copy()));
	}

	@SuppressWarnings("removal")
	private static ServerPlayer player(GameTestHelper helper) {
		return helper.makeMockServerPlayerInLevel();
	}

	private static ItemEntity dropEa(GameTestHelper helper, Vec3 at) {
		ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(FateWeapons.EA));
		helper.getLevel().addFreshEntity(item);
		return item;
	}

	@GameTest
	public void spinInterpolatesAtConfiguredAbilitySpeeds(GameTestHelper helper) {
		for (double speed : new double[] {EaConfig.DEFAULTS.enumaElishSpinDegreesPerTick(), EaConfig.DEFAULTS.compressedAirSpinDegreesPerTick()}) {
			WeaponSpin spin = new WeaponSpin(0, (float)speed);
			double wrapTick = WeaponSpin.DEGREES_PER_TURN / speed;
			check(helper, "quarter tick advances a quarter of the speed", Math.abs(spin.degrees(0.25) - speed * 0.25) < EPS);
			check(helper, "just before wrap", Math.abs(spin.degrees(wrapTick - 0.25) - (WeaponSpin.DEGREES_PER_TURN - speed * 0.25)) < EPS);
			check(helper, "just after wrap", Math.abs(spin.degrees(wrapTick + 0.25) - speed * 0.25) < EPS);
		}
		helper.succeed();
	}

	@GameTest
	public void eaHitsWithTheConfiguredDamageAndSpeed(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		ItemAttributeModifiers ea = new ItemStack(FateWeapons.EA).getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		double damage = ea.compute(Attributes.ATTACK_DAMAGE, player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE), EquipmentSlot.MAINHAND);
		double speed = ea.compute(Attributes.ATTACK_SPEED, player.getAttributeBaseValue(Attributes.ATTACK_SPEED), EquipmentSlot.MAINHAND);
		check(helper, "attack damage " + damage, Math.abs(damage - EaConfig.DEFAULTS.meleeDamage()) < EPS);
		check(helper, "attack speed " + speed, Math.abs(speed - EaConfig.DEFAULTS.attackSpeed()) < EPS);
		helper.succeed();
	}

	@GameTest
	public void caliburnHasConfiguredMeleeAndSharedWeaponRules(GameTestHelper helper) {
		ServerPlayer player = FateTests.player(helper, FLOOR);
		ItemStack sword = new ItemStack(FateWeapons.CALIBURN);
		CaliburnConfig config = FateConfig.caliburn();
		sword.getItem().inventoryTick(sword, helper.getLevel(), player, EquipmentSlot.MAINHAND);
		ItemAttributeModifiers stats = sword.get(DataComponents.ATTRIBUTE_MODIFIERS);
		check(helper, "Caliburn damage follows config", Math.abs(stats.compute(Attributes.ATTACK_DAMAGE,
			player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE), EquipmentSlot.MAINHAND) - config.damage()) < EPS);
		check(helper, "Caliburn speed follows config", Math.abs(stats.compute(Attributes.ATTACK_SPEED,
			player.getAttributeBaseValue(Attributes.ATTACK_SPEED), EquipmentSlot.MAINHAND) - config.speed()) < EPS);
		check(helper, "unbreakable sword", !sword.isDamageableItem() && sword.is(Traces.SWORDS));
		check(helper, "shared death and drop tags", sword.is(FateWeapons.KEPT_ON_DEATH) && sword.is(FateWeapons.INDESTRUCTIBLE));
		check(helper, "Fatecraft's tags fill Alaya Core's", sword.is(WeaponRules.KEPT_ON_DEATH) && sword.is(WeaponRules.INDESTRUCTIBLE));
		player.getInventory().add(sword);
		player.getInventory().dropAll();
		WeaponRules.giveBack(player);
		check(helper, "Caliburn returned after death", player.getInventory().contains(stack -> stack.is(FateWeapons.CALIBURN)));
		helper.succeed();
	}

	@GameTest
	public void artoriaGetsCaliburnOnceOnSelectionOrReturningJoin(GameTestHelper helper) {
		ServerPlayer chosen = FateTests.player(helper, FLOOR);
		FateTests.become(helper, chosen, FateServants.ARTORIA);
		check(helper, "Artoria selection gives Caliburn", chosen.getInventory().contains(stack -> stack.is(FateWeapons.CALIBURN)));
		chosen.getInventory().clearContent();
		ServerPlayerEvents.JOIN.invoker().onJoin(chosen);
		check(helper, "returning join never replaces a lost sword", chosen.getInventory().isEmpty());
		ServerPlayer returning = FateTests.servant(helper, FLOOR, 0, FateServants.ARTORIA);
		ServerPlayerEvents.JOIN.invoker().onJoin(returning);
		check(helper, "an existing Artoria gets the starting sword", returning.getInventory().contains(stack -> stack.is(FateWeapons.CALIBURN)));
		returning.getInventory().clearContent();
		ServantStartingItems.give(returning);
		check(helper, "gift only once", returning.getInventory().isEmpty());
		ServerPlayer human = FateTests.player(helper, FLOOR);
		ServantStartingItems.give(human);
		check(helper, "human receives nothing", human.getInventory().isEmpty());
		helper.succeed();
	}

	@GameTest(maxTicks = TICKS_IN_LAVA + 1)
	public void droppedWeaponsSurviveLavaAndExplosions(GameTestHelper helper) {
		helper.setBlock(FLOOR, Blocks.LAVA);
		ItemEntity inLava = dropEa(helper, helper.absoluteVec(Vec3.atCenterOf(FLOOR)));
		ItemEntity blasted = dropEa(helper, helper.absoluteVec(Vec3.atCenterOf(FLOOR.east(3))));
		Vec3 centre = blasted.position();
		helper.getLevel().explode(null, centre.x, centre.y, centre.z, EXPLOSION_POWER, Level.ExplosionInteraction.TNT);
		helper.runAfterDelay(TICKS_IN_LAVA, () -> {
			check(helper, "survives lava", inLava.isAlive());
			check(helper, "survives an explosion", blasted.isAlive());
			check(helper, "never despawns", inLava.getAge() < 0);
			helper.succeed();
		});
	}

	@GameTest
	public void compressedAirHitsAndBreaksWhatIsAroundButNotBelow(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		player.setGameMode(GameType.SURVIVAL);
		player.snapTo(helper.absoluteVec(Vec3.atBottomCenterOf(WIELDER)));
		ItemStack ea = new ItemStack(FateWeapons.EA);
		player.setItemInHand(InteractionHand.MAIN_HAND, ea);
		Husk near = mob(helper, WIELDER.east(NEAR));
		Husk far = mob(helper, WIELDER.east(FAR));
		BlockPos beside = WIELDER.west(NEAR);
		BlockPos below = WIELDER.below();
		helper.setBlock(beside, Blocks.STONE);
		helper.setBlock(below, Blocks.STONE);
		double mana = AlayaMana.getOrCreate(player).current();

		player.startUsingItem(InteractionHand.MAIN_HAND);
		ea.getItem().onUseTick(helper.getLevel(), player, ea, ea.getUseDuration(player));
		check(helper, "hits what is within reach", near.getHealth() < near.getMaxHealth());
		check(helper, "not what is farther", far.getHealth() == far.getMaxHealth());
		helper.assertBlockNotPresent(Blocks.STONE, beside);
		helper.assertBlockPresent(Blocks.STONE, below);
		check(helper, "pays mana", AlayaMana.get(player).current() < mana);
		helper.succeed();
	}

	@GameTest
	public void compressedAirSparesWhatIsBehindAShield(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		player.setGameMode(GameType.SURVIVAL);
		player.snapTo(helper.absoluteVec(Vec3.atBottomCenterOf(WIELDER)));
		ItemStack ea = new ItemStack(FateWeapons.EA);
		player.setItemInHand(InteractionHand.MAIN_HAND, ea);
		FateTestShields.Disc shield = helper.spawn(FateTestShields.DISC, Vec3.atCenterOf(WIELDER.east(SHIELD_GAP)));
		shield.setYRot(FACING_WEST);
		shield.deploy(SHIELD_RANK);
		Husk behind = mob(helper, WIELDER.east(NEAR));
		Minecart cart = helper.spawn(EntityType.MINECART, Vec3.atBottomCenterOf(WIELDER.east(NEAR).north()));
		cart.setNoGravity(true);

		player.startUsingItem(InteractionHand.MAIN_HAND);
		ea.getItem().onUseTick(helper.getLevel(), player, ea, ea.getUseDuration(player));
		check(helper, "nothing living behind the shield is hurt", behind.getHealth() == behind.getMaxHealth());
		check(helper, "nor anything else behind it", !cart.isRemoved() && cart.getDamage() == 0);
		check(helper, "the shield took the hits", shield.hp() < shield.maxHp());
		helper.succeed();
	}

	@GameTest
	public void compressedAirStopsWithoutMana(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		player.setGameMode(GameType.SURVIVAL);
		ItemStack ea = new ItemStack(FateWeapons.EA);
		player.setItemInHand(InteractionHand.MAIN_HAND, ea);
		AlayaMana.update(player, mana -> mana.withCurrent(0));
		check(helper, "refused", !ea.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).consumesAction());
		helper.succeed();
	}

	@GameTest
	public void weaponsAreKeptThroughDeath(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		player.getInventory().add(new ItemStack(FateWeapons.EA));
		player.getInventory().dropAll();
		check(helper, "not dropped", helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4)).isEmpty());
		check(helper, "kept", player.getAttached(WeaponRules.KEPT) != null);
		WeaponRules.giveBack(player);
		check(helper, "given back", player.getInventory().contains(stack -> stack.is(FateWeapons.EA)));
		check(helper, "nothing left kept", player.getAttached(WeaponRules.KEPT) == null);
		helper.succeed();
	}

	@GameTest
	public void itemsLostToTheVoidAskTheirOwnHandler(GameTestHelper helper) {
		Level level = helper.getLevel();
		ItemEntity lost = new ItemEntity(level, 0, level.getMinY() - BELOW_THE_WORLD, 0, new ItemStack(Items.ECHO_SHARD));
		ItemEntity other = new ItemEntity(level, 0, level.getMinY() - BELOW_THE_WORLD, 0, new ItemStack(Items.AMETHYST_SHARD));
		int before = LOST_TO_VOID.size();
		lost.tick();
		other.tick();
		check(helper, "both fell out of the world", lost.isRemoved() && other.isRemoved());
		check(helper, "only the registered item's handler ran, once", LOST_TO_VOID.size() == before + 1 && LOST_TO_VOID.getLast().is(Items.ECHO_SHARD));
		helper.succeed();
	}
}
