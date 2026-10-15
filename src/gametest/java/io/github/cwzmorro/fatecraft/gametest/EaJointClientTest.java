package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import io.github.cwzmorro.fatecraft.weapon.WeaponSpin;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class EaJointClientTest implements FabricClientGameTest {
	private static final int[] ANGLES = {0, 45, 90, 135};
	private static final float DEGREES_PER_TICK = 1.0F;
	private static final Vec3 DISPLAY = new Vec3(0.5, 102, 8.5);
	private static final double[] JOINTS_ABOVE_DISPLAY = {1.2, 0.665};
	private static final double DISTANCE = 0.5;
	private static final int FOV = 20;
	private static final double BELOW = 0.4;
	private static final double WIDE_DISTANCE = 4;
	private static final int WIDE_FOV = 60;
	private static final double SEARCH_RADIUS = 20;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.GILGAMESH);
			world.getServer().runCommand("gamemode creative @p");
			world.getServer().runCommand("fill -10 100 -10 10 100 20 stone");
			world.getServer().runCommand("fill -10 101 -10 10 108 20 air");
			FateClientTests.clearNoon(world);
			world.getServer().runCommand("tp @p 0 101 0 0 0");
			world.getServer().runCommand("summon item_display " + DISPLAY.x + " " + DISPLAY.y + " " + DISPLAY.z
				+ " {item:{id:\"fatecraft:ea\",count:1},item_display:\"none\"}");
			context.runOnClient(client -> client.options.hideGui = true);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			world.getServer().runCommand("tick freeze");
			for (int angle : ANGLES) {
				world.getServer().runOnServer(server -> {
					Display.ItemDisplay display = server.overworld().getEntitiesOfClass(Display.ItemDisplay.class,
						FateClientTests.player(server).getBoundingBox().inflate(SEARCH_RADIUS)).getFirst();
					ItemStack ea = new ItemStack(FateWeapons.EA);
					ea.set(FateWeapons.SPIN, new WeaponSpin(server.overworld().getGameTime() - angle, DEGREES_PER_TICK));
					display.setItemStack(ea);
				});
				// Frozen ticks send no entity data: step one so the client has the stack.
				world.getServer().runCommand("tick step 1");
				context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
				FateClientTests.view(context, DISPLAY.add(0, 1, -WIDE_DISTANCE), DISPLAY.add(0, 1, 0), WIDE_FOV);
				context.takeScreenshot("ea-joint-wide-" + angle);
				for (int i = 0; i < JOINTS_ABOVE_DISPLAY.length; i++) {
					Vec3 joint = DISPLAY.add(0, JOINTS_ABOVE_DISPLAY[i], 0);
					FateClientTests.view(context, joint.add(0, 0, -DISTANCE), joint, FOV);
					context.takeScreenshot("ea-joint" + i + "-side-" + angle);
					FateClientTests.view(context, joint.add(0, -BELOW, -DISTANCE), joint, FOV);
					context.takeScreenshot("ea-joint" + i + "-below-" + angle);
				}
			}
			world.getServer().runCommand("tick unfreeze");
		}
	}
}
