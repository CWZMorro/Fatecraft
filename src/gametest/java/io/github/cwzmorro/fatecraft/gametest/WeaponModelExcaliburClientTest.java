package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.fatecraft.servant.FateServants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

// Named like Ea's test, so -PclientTest=WeaponModel runs both.
public class WeaponModelExcaliburClientTest implements FabricClientGameTest {
	private static final int STAND_DISTANCE = 2;
	private static final int DROPPED_DISTANCE = 1;
	private static final int SIDE_VIEW_ACROSS = 3;
	private static final int FRONT_VIEW_ALONG = STAND_DISTANCE + 3;
	private static final int WEST = 90;
	private static final int NORTH = 180;

	protected String mainHand() {
		return "excalibur";
	}

	protected String offHand() {
		return "air";
	}

	protected String screenshotPrefix() {
		return this.mainHand();
	}

	protected void stage(TestSingleplayerContext world) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.GILGAMESH);
			// A previous test may leave the camera behind the player or hide the hand with the GUI.
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
			context.runOnClient(client -> client.options.hideGui = false);
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("item replace entity @p weapon.mainhand with fatecraft:" + this.mainHand());
			boolean offHandEmpty = this.offHand().equals("air");
			String offHandId = offHandEmpty ? "minecraft:air" : "fatecraft:" + this.offHand();
			world.getServer().runCommand("item replace entity @p weapon.offhand with " + offHandId);
			FateClientTests.faceSouth(world);
			// An equipment entry can't be air: an empty off hand is left out.
			String offHandEquipment = offHandEmpty ? "" : ",offhand:{id:\"" + offHandId + "\",count:1}";
			world.getServer().runCommand("execute as @p at @s run summon armor_stand ~ ~ ~" + STAND_DISTANCE
				+ " {ShowArms:1b,NoGravity:1b,Rotation:[180f,0f],equipment:{mainhand:{id:\"fatecraft:" + this.mainHand()
				+ "\",count:1}" + offHandEquipment + "}}");
			this.stage(world);
			for (String time : FateClientTests.TIMES_OF_DAY) {
				world.getServer().runCommand("time set " + time);
				context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
				context.takeScreenshot(this.screenshotPrefix() + "-" + time + "-1-first-person");
				FateClientTests.setCamera(context, CameraType.THIRD_PERSON_BACK);
				context.takeScreenshot(this.screenshotPrefix() + "-" + time + "-2-third-person");
				FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
				viewFrom(context, world, this.screenshotPrefix() + "-" + time + "-3-armor-stand-side", SIDE_VIEW_ACROSS, STAND_DISTANCE, WEST, 0);
				viewFrom(context, world, this.screenshotPrefix() + "-" + time + "-4-armor-stand-front", 0, FRONT_VIEW_ALONG, NORTH, 0);
			}
			world.getServer().runCommand("kill @e[type=armor_stand]");

			world.getServer().runCommand("execute as @p at @s run summon item ~ ~ ~" + DROPPED_DISTANCE
				+ " {Item:{id:\"fatecraft:" + this.mainHand() + "\",count:1},PickupDelay:32767}");
			this.stage(world);
			for (String time : FateClientTests.TIMES_OF_DAY) {
				world.getServer().runCommand("time set " + time);
				viewFrom(context, world, this.screenshotPrefix() + "-" + time + "-5-on-the-ground", 0, 0, 0, FateClientTests.GROUND_PITCH);
			}
			world.getServer().runCommand("kill @e[type=item]");

			for (String time : FateClientTests.TIMES_OF_DAY) {
				world.getServer().runCommand("time set " + time);
				context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
				context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
				context.takeScreenshot(this.screenshotPrefix() + "-" + time + "-6-inventory");
				context.setScreen(() -> null);
			}
		}
	}

	private static void viewFrom(ClientGameTestContext context, TestSingleplayerContext world, String name, int east, int south, int yaw, int pitch) {
		context.runOnClient(client -> client.options.hideGui = true);
		world.getServer().runCommand("execute as @p at @s run tp @s ~" + east + " ~ ~" + south + " " + yaw + " " + pitch);
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
		context.takeScreenshot(name);
		world.getServer().runCommand("execute as @p at @s run tp @s ~-" + east + " ~ ~-" + south + " 0 0");
		context.runOnClient(client -> client.options.hideGui = false);
	}
}
