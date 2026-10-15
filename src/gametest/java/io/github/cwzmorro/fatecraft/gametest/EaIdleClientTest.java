package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import io.github.cwzmorro.fatecraft.weapon.WeaponSpin;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class EaIdleClientTest implements FabricClientGameTest {
	private static final int SPIN_SHOT_TICKS = 20;
	private static final float DEGREES_PER_TICK = 1.0F;
	private static final Vec3 DISPLAY = new Vec3(0.5, -60, 4.5);
	private static final double DISTANCE = 3;
	private static final int FOV = 60;
	private static final double SEARCH_RADIUS = 20;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.GILGAMESH);
			world.getServer().runCommand("weather clear");
			FateClientTests.hold(world, FateWeapons.EA);
			FateClientTests.faceSouth(world);
			for (String time : FateClientTests.TIMES_OF_DAY) {
				world.getServer().runCommand("time set " + time);
				context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
				context.takeScreenshot("ea-idle-" + time + "-first-person");
				FateClientTests.setCamera(context, CameraType.THIRD_PERSON_BACK);
				context.takeScreenshot("ea-idle-" + time + "-third-person");
				FateClientTests.setCamera(context, CameraType.FIRST_PERSON);

				FateClientTests.dropEaAhead(world);
				context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
				context.takeScreenshot("ea-idle-" + time + "-ground");
				world.getServer().runCommand("kill @e[type=item]");
				FateClientTests.faceSouth(world);

				context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
				context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
				context.takeScreenshot("ea-idle-" + time + "-inventory");
				context.setScreen(() -> null);

				world.getServer().runCommand("summon item_display " + DISPLAY.x + " " + DISPLAY.y + " " + DISPLAY.z
					+ " {item:{id:\"fatecraft:ea\",count:1},item_display:\"none\"}");
				context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
				world.getServer().runOnServer(server -> {
					Display.ItemDisplay display = server.overworld().getEntitiesOfClass(Display.ItemDisplay.class,
						FateClientTests.player(server).getBoundingBox().inflate(SEARCH_RADIUS)).getFirst();
					ItemStack ea = new ItemStack(FateWeapons.EA);
					ea.set(FateWeapons.SPIN, new WeaponSpin(server.overworld().getGameTime(), DEGREES_PER_TICK));
					display.setItemStack(ea);
				});
				context.waitTicks(SPIN_SHOT_TICKS);
				FateClientTests.view(context, DISPLAY.add(0, 1, -DISTANCE), DISPLAY.add(0, 1, 0), FOV);
				context.takeScreenshot("ea-spin-" + time + "-close");
				world.getServer().runCommand("kill @e[type=item_display]");
				FateClientTests.faceSouth(world);
			}
		}
	}
}
