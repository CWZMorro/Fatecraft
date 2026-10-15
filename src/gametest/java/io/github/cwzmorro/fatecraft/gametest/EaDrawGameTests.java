package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;

import io.github.cwzmorro.alayacore.network.ChooseServantPayload;
import io.github.cwzmorro.alayacore.progression.TrueName;
import io.github.cwzmorro.alayacore.servant.ServantSelection;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.weapon.EaDraw;
import io.github.cwzmorro.fatecraft.weapon.EaDrawEntity;
import io.github.cwzmorro.fatecraft.weapon.EaVault;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public class EaDrawGameTests {
	private static final BlockPos STANDING = new BlockPos(1, 1, 1);
	private static final int RISEN_TICKS = EaDraw.RISE_START + EaDraw.RISE_TICKS + 1;
	private static final double BELOW_THE_WORLD = 70.0;

	private static ServerPlayer player(GameTestHelper helper) {
		return FateTests.player(helper, STANDING);
	}

	private static ServerPlayer newGilgamesh(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		FateTests.become(helper, player, FateServants.GILGAMESH);
		return player;
	}

	private static ServerPlayer gilgamesh(GameTestHelper helper) {
		ServerPlayer player = newGilgamesh(helper);
		AdvancementHolder trueName = helper.getLevel().getServer().getAdvancements().get(Fatecraft.id("gilgamesh/true_name"));
		trueName.value().criteria().keySet().forEach(criterion -> player.getAdvancements().award(trueName, criterion));
		return player;
	}

	private static int keys(ServerPlayer player) {
		return player.getInventory().countItem(FateWeapons.BAB_ILU);
	}

	private static boolean useKey(GameTestHelper helper, ServerPlayer player) {
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FateWeapons.BAB_ILU));
		return player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).consumesAction();
	}

	private static List<EaDrawEntity> rising(GameTestHelper helper, ServerPlayer player) {
		return helper.getLevel().getEntitiesOfClass(EaDrawEntity.class, player.getBoundingBox().inflate(EaDraw.AHEAD * 2));
	}

	@GameTest
	public void becomingGilgameshGivesTheKeyOnce(GameTestHelper helper) {
		ServerPlayer player = newGilgamesh(helper);
		check(helper, "has the key", keys(player) == 1);
		ServantSelection.open(player);
		ServantSelection.choose(player, new ChooseServantPayload(ChooseServantPayload.Kind.HUMAN, Optional.empty()));
		FateTests.become(helper, player, FateServants.GILGAMESH);
		check(helper, "not a second one", keys(player) == 1);
		helper.succeed();
	}

	@GameTest
	public void eaWaitsForTheTrueName(GameTestHelper helper) {
		ServerPlayer player = newGilgamesh(helper);
		player.setGameMode(GameType.SURVIVAL);
		check(helper, "true name locked", !TrueName.isRealized(player));
		check(helper, "refused", !useKey(helper, player));
		check(helper, "nothing drawn", rising(helper, player).isEmpty());
		check(helper, "no raised-key animation", !player.isUsingItem());
		check(helper, "no cooldown on refusal", !player.getCooldowns().isOnCooldown(player.getMainHandItem()));
		// A refusal neither issues nor loses Ea: the same locked player can draw it once in creative.
		player.setGameMode(GameType.CREATIVE);
		check(helper, "creative after refused draw", useKey(helper, player));
		check(helper, "Ea still available", rising(helper, player).size() == 1);
		helper.succeed();
	}

	@GameTest(maxTicks = RISEN_TICKS + 2)
	public void creativeDrawsEaWithoutTheTrueName(GameTestHelper helper) {
		ServerPlayer player = newGilgamesh(helper);
		player.setGameMode(GameType.CREATIVE);
		check(helper, "true name remains locked", !TrueName.isRealized(player));
		check(helper, "drawn", useKey(helper, player));
		List<EaDrawEntity> drawn = rising(helper, player);
		check(helper, "Ea rises", drawn.size() == 1);
		check(helper, "authorized raised-key animation", player.isUsingItem());
		EaDrawEntity ea = drawn.getFirst();
		helper.runAfterDelay(RISEN_TICKS, () -> {
			check(helper, "creative can take the summoned Ea", ea.interact(player, InteractionHand.MAIN_HAND, ea.position()).consumesAction());
			check(helper, "Ea in hand", player.getMainHandItem().is(FateWeapons.EA));
			check(helper, "owned Ea", player.getUUID().equals(player.getMainHandItem().get(EaVault.OWNER)));
			check(helper, "creative did not unlock the true name", !TrueName.isRealized(player));
			helper.succeed();
		});
	}

	@GameTest
	public void onlyGilgameshCanUseTheKey(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		check(helper, "refused", !useKey(helper, player));
		check(helper, "nothing drawn", rising(helper, player).isEmpty());
		helper.succeed();
	}

	@GameTest(maxTicks = RISEN_TICKS + 2)
	public void theKeyDrawsEaWhichRisesAndIsTaken(GameTestHelper helper) {
		ServerPlayer player = gilgamesh(helper);
		check(helper, "drawn", useKey(helper, player));
		check(helper, "the key cools down, as an ender pearl", player.getCooldowns().isOnCooldown(player.getMainHandItem()));
		List<EaDrawEntity> drawn = rising(helper, player);
		check(helper, "Ea rises", drawn.size() == 1);
		check(helper, "only once while it is out", !useKey(helper, player) && rising(helper, player).size() == 1);
		EaDrawEntity ea = drawn.getFirst();
		check(helper, "not before it has risen", !ea.interact(player, InteractionHand.MAIN_HAND, ea.position()).consumesAction());
		helper.runAfterDelay(RISEN_TICKS, () -> {
			check(helper, "taken", ea.interact(player, InteractionHand.MAIN_HAND, ea.position()).consumesAction());
			check(helper, "in the main hand", player.getMainHandItem().is(FateWeapons.EA));
			check(helper, "his", player.getUUID().equals(player.getMainHandItem().get(EaVault.OWNER)));
			helper.succeed();
		});
	}

	@GameTest
	public void theKeyPutsEaBack(GameTestHelper helper) {
		ServerPlayer player = gilgamesh(helper);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(FateWeapons.EA));
		check(helper, "stored", useKey(helper, player));
		check(helper, "out of the hand", player.getOffhandItem().isEmpty());
		check(helper, "the key cools down", player.getCooldowns().isOnCooldown(player.getMainHandItem()));
		check(helper, "and drawn again", useKey(helper, player) && rising(helper, player).size() == 1);
		helper.succeed();
	}

	@GameTest
	public void eaInTheVoidGoesBackBehindTheVault(GameTestHelper helper) {
		ServerPlayer player = gilgamesh(helper);
		useKey(helper, player);
		rising(helper, player).forEach(EaDrawEntity::discard);
		check(helper, "out of the vault", !useKey(helper, player));
		ItemStack ea = new ItemStack(FateWeapons.EA);
		ea.set(EaVault.OWNER, player.getUUID());
		Vec3 below = player.position().with(Direction.Axis.Y, helper.getLevel().getMinY() - BELOW_THE_WORLD);
		helper.getLevel().addFreshEntity(new ItemEntity(helper.getLevel(), below.x, below.y, below.z, ea));
		helper.runAfterDelay(2, () -> {
			check(helper, "back: the key draws it again", useKey(helper, player));
			helper.succeed();
		});
	}

	private static void dropIntoTheVoid(GameTestHelper helper, ServerPlayer player, ItemStack ea) {
		Vec3 below = player.position().with(Direction.Axis.Y, helper.getLevel().getMinY() - BELOW_THE_WORLD);
		helper.getLevel().addFreshEntity(new ItemEntity(helper.getLevel(), below.x, below.y, below.z, ea));
	}

	private static ServerPlayer gilgameshWithoutEa(GameTestHelper helper) {
		ServerPlayer player = gilgamesh(helper);
		useKey(helper, player);
		rising(helper, player).forEach(EaDrawEntity::discard);
		check(helper, "out of the vault", !useKey(helper, player));
		return player;
	}

	@GameTest
	public void anOwnerlessEaGetsItsFirstHolderAndGoesBackToThemFromTheVoid(GameTestHelper helper) {
		ServerPlayer player = gilgameshWithoutEa(helper);
		ItemStack ea = new ItemStack(FateWeapons.EA);
		check(helper, "no owner at first", !ea.has(EaVault.OWNER));
		player.setItemInHand(InteractionHand.MAIN_HAND, ea);
		ea.getItem().inventoryTick(ea, helper.getLevel(), player, EquipmentSlot.MAINHAND);
		check(helper, "the one holding it owns it", player.getUUID().equals(ea.get(EaVault.OWNER)));
		ServerPlayer other = FateTests.player(helper, STANDING);
		ea.getItem().inventoryTick(ea, helper.getLevel(), other, EquipmentSlot.MAINHAND);
		check(helper, "and only the first", player.getUUID().equals(ea.get(EaVault.OWNER)));
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		dropIntoTheVoid(helper, player, ea);
		helper.runAfterDelay(2, () -> {
			check(helper, "back behind the vault of the first holder", useKey(helper, player));
			helper.succeed();
		});
	}

	@GameTest
	public void anOwnerlessEaInTheVoidIsLost(GameTestHelper helper) {
		ServerPlayer player = gilgameshWithoutEa(helper);
		dropIntoTheVoid(helper, player, new ItemStack(FateWeapons.EA));
		helper.runAfterDelay(2, () -> {
			check(helper, "nobody's Ea is gone", !useKey(helper, player));
			helper.succeed();
		});
	}
}
