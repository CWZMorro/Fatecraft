package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.trace.SwordBody;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.phys.Vec3;

public class SwordBodyClientTest implements FabricClientGameTest {
	private static final int SETTLE_TICKS = 10;
	private static final int FOV = 45;
	private static final int FLOOR_LENGTH = 60;
	private static final int FLOOR_CLEARANCE = 8;
	private static final int FIRST_PERSON_PITCH = 20;
	private static final Vec3 LOOK = new Vec3(0, 102, 0);

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.EMIYA);
			world.getServer().runCommand("gamemode creative @p");
			FateClientTests.flatFloor(world, FLOOR_LENGTH, FLOOR_CLEARANCE);
			FateClientTests.clearNoon(world);
			context.runOnClient(client -> {
				client.options.hideGui = true;
				client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			});
			context.waitTicks(SETTLE_TICKS);
			world.getServer().runCommand("tick freeze");
			FateClientTests.tap(world, SwordBody.KEY.identifier(), 0);
			context.waitTicks(SETTLE_TICKS);
			world.getServer().runOnServer(server -> {
				if (!Abilities.isActive(FateClientTests.player(server), SwordBody.KEY.identifier())) {
					throw new AssertionError("Sword Body must be active for the screenshots");
				}
			});
			FateClientTests.view(context, new Vec3(0, 102.2, 3.5), LOOK, FOV);
			context.takeScreenshot("sword-body-front");
			FateClientTests.view(context, new Vec3(3.5, 102.2, 0), LOOK, FOV);
			context.takeScreenshot("sword-body-side");
			FateClientTests.view(context, new Vec3(0, 102.2, -3.5), LOOK, FOV);
			context.takeScreenshot("sword-body-back");
			FateClientTests.view(context, new Vec3(2.5, 102.4, 2.5), LOOK, FOV);
			context.takeScreenshot("sword-body-angle");
			FateClientTests.view(context, new Vec3(-1.6, 102.2, 1.6), LOOK, FOV);
			context.takeScreenshot("sword-body-close-right");
			FateClientTests.view(context, new Vec3(1.6, 102.2, -1.6), LOOK, FOV);
			context.takeScreenshot("sword-body-close-back-left");
			// Grazing the outer face of each arm and from above: how far the blades stand off the body.
			FateClientTests.view(context, new Vec3(0.5, 102.2, 2.5), new Vec3(0.5, 102.2, 0), FOV);
			context.takeScreenshot("sword-body-graze-left");
			FateClientTests.view(context, new Vec3(-0.5, 102.2, 2.5), new Vec3(-0.5, 102.2, 0), FOV);
			context.takeScreenshot("sword-body-graze-right");
			FateClientTests.view(context, new Vec3(0, 104.5, 0.05), new Vec3(0, 102, 0), FOV);
			context.takeScreenshot("sword-body-top");
			context.runOnClient(client -> client.options.hideGui = false);
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
			world.getServer().runCommand("tp @p 0 101 0 0 " + FIRST_PERSON_PITCH);
			context.waitTicks(SETTLE_TICKS);
			context.takeScreenshot("sword-body-first-person");
			world.getServer().runCommand("tick unfreeze");
		}
	}
}
