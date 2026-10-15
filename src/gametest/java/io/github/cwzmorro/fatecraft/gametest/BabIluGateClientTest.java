package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.progression.TrueName;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.weapon.EaDrawEntity;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

public class BabIluGateClientTest implements FabricClientGameTest {
	private static final double DRAW_SEARCH_RADIUS = 32.0;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.GILGAMESH);
			world.getServer().runCommand("advancement revoke @p only fatecraft:gilgamesh/true_name");
			world.getServer().runCommand("gamemode survival @p");
			FateClientTests.hold(world, FateWeapons.BAB_ILU);
			world.getServer().runCommand("item replace entity @p weapon.offhand with minecraft:air");
			FateClientTests.faceSouth(world);
			FateClientTests.setCamera(context, CameraType.THIRD_PERSON_FRONT);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.runOnClient(client -> {
				if (client.player.getMainHandItem().use(client.level, client.player, InteractionHand.MAIN_HAND) != InteractionResult.CONSUME) {
					throw new AssertionError("Bab-ilu predicted a success swing before server authorization");
				}
			});
			context.getInput().holdKey(options -> options.keyUse);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.runOnClient(client -> {
				if (client.player.isUsingItem() || client.player.swinging) {
					throw new AssertionError("Locked Bab-ilu animated on the client");
				}
			});
			world.getServer().runOnServer(server -> {
				ServerPlayer player = FateClientTests.player(server);
				if (TrueName.isRealized(player) || !player.level().getEntitiesOfClass(EaDrawEntity.class, player.getBoundingBox().inflate(DRAW_SEARCH_RADIUS)).isEmpty()) {
					throw new AssertionError("Locked survival player drew Ea");
				}
			});
			context.takeScreenshot("bab-ilu-gate-locked");
			context.getInput().releaseKey(options -> options.keyUse);
			world.getServer().runCommand("gamemode creative @p");
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.getInput().holdKey(options -> options.keyUse);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = FateClientTests.player(server);
				if (TrueName.isRealized(player) || player.level().getEntitiesOfClass(EaDrawEntity.class, player.getBoundingBox().inflate(DRAW_SEARCH_RADIUS)).size() != 1) {
					throw new AssertionError("Creative Gilgamesh did not summon Ea with a locked true name");
				}
			});
			context.takeScreenshot("bab-ilu-gate-creative");
			context.getInput().releaseKey(options -> options.keyUse);
		}
	}
}
