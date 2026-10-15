package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.fatecraft.chain.ChainLayout;
import io.github.cwzmorro.fatecraft.chain.Enkidu;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerPlayer;

public class EnkiduClientTest implements FabricClientGameTest {
	private static final int TARGET_AHEAD = 7;
	private static final int SHOOTING_TICKS = ChainLayout.OPEN_TICKS + 1;
	private static final int BOUND_TICKS = 40;
	private static final int RELEASING_TICKS = ChainLayout.RELEASE_TICKS / 2;
	private static final int SIDE_ACROSS = 9;
	private static final int SIDE_UP = 2;
	private static final int WEST = 90;
	private static final int NORTH = 180;
	private static final int FRONT_BEYOND = 6;
	private static final int LEVEL = 5;
	private static final int BELOW_ACROSS = 2;
	private static final int BELOW_UP = 1;
	private static final int LOOKING_UP = -50;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.GILGAMESH);
			FateClientTests.quietNoon(context, world);
			world.getServer().runCommand("execute as @p at @s run summon husk ~ ~ ~" + TARGET_AHEAD + " {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b}");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = FateClientTests.player(server);
				FateClientTests.giveMana(player);
			});
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);

			FateClientTests.tap(world, Enkidu.KEY.identifier(), Enkidu.BIND);
			context.waitTicks(SHOOTING_TICKS);
			context.takeScreenshot("enkidu-01-chains-shooting");
			context.waitTicks(BOUND_TICKS);
			context.takeScreenshot("enkidu-02-bound");
			FateClientTests.lookFrom(context, world, "enkidu-03-bound-side", SIDE_ACROSS, SIDE_UP, TARGET_AHEAD / 2, WEST, LEVEL);
			FateClientTests.lookFrom(context, world, "enkidu-04-bound-front", 0, SIDE_UP, TARGET_AHEAD + FRONT_BEYOND, NORTH, LEVEL);
			FateClientTests.lookFrom(context, world, "enkidu-04b-bound-from-below", BELOW_ACROSS, BELOW_UP, TARGET_AHEAD, NORTH, LOOKING_UP);

			FateClientTests.press(world, Enkidu.KEY.identifier(), Enkidu.TIGHTEN);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.takeScreenshot("enkidu-05-tightening");
			world.getServer().runCommand("time set midnight");
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			FateClientTests.lookFrom(context, world, "enkidu-06-tightening-night-side", SIDE_ACROSS, SIDE_UP, TARGET_AHEAD / 2, WEST, LEVEL);
			FateClientTests.release(world, Enkidu.KEY.identifier());
			world.getServer().runCommand("time set noon");

			FateClientTests.tap(world, Enkidu.KEY.identifier(), Enkidu.BIND);
			context.waitTicks(RELEASING_TICKS);
			context.takeScreenshot("enkidu-07-letting-go");
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
		}
	}
}
