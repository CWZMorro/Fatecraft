package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.progression.TrueName;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.ubw.UbwConfig;
import io.github.cwzmorro.fatecraft.ubw.UbwController;
import io.github.cwzmorro.fatecraft.ubw.UbwEnvironmentEntity;
import io.github.cwzmorro.fatecraft.ubw.UbwRuntime;
import io.github.cwzmorro.fatecraft.ubw.UnlimitedBladeWorks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class UbwLookClientTest implements FabricClientGameTest {
	private static final int GATHERING = -UbwConfig.DEFAULTS.opening().chargeTicks() * 3 / 10;
	private static final int SURGING = -3;
	private static final int FLASH = 0;
	private static final int RING = UbwConfig.DEFAULTS.opening().ignitionTicks() + 6;
	private static final int REVEALING = UbwConfig.DEFAULTS.opening().spreadTicks() * 3 / 4;
	private static final int STANDING = UbwConfig.DEFAULTS.opening().dyingTicks() + 2;
	private static final double[] HIGH_GEAR = {150, 63};
	private static final double[] LOW_GEAR = {75, 32};
	private static final double GEAR_LOOK = 20.0;
	private static final double ABOVE = 10.0;
	private static final int LOOK_DOWN = 25;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.EMIYA);
			UbwClientTest.stage(context, world);
			world.getServer().runCommand("gamemode creative @p");
			for (String time : FateClientTests.TIMES_OF_DAY) {
				try {
					scene(context, world, time);
				} finally {
					world.getServer().runOnServer(server -> UbwRuntime.cancel(FateClientTests.player(server)));
					world.getServer().runCommand("tick unfreeze");
					FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
				}
			}
		}
	}

	private static void scene(ClientGameTestContext context, TestSingleplayerContext world, String time) {
		world.getServer().runCommand("time set " + time);
		world.getServer().runCommand("tp @p 0 150 0 0 0");
		BlockPos[] center = {BlockPos.ZERO};
		Vec3[] feet = {Vec3.ZERO};
		world.getServer().runOnServer(server -> {
			var player = FateClientTests.player(server);
			player.setNoGravity(true);
			TrueName.realize(player);
			FateClientTests.giveMana(player);
			feet[0] = player.position();
			center[0] = UbwController.placement(player, FateConfig.ubw());
		});
		Vec3 centre = Vec3.atLowerCornerOf(center[0]);
		AABB bounds = new AABB(centre.subtract(30, 30, 30), centre.add(30, 30, 30));
		world.getServer().runCommand("tick freeze");
		world.getServer().runOnServer(server ->
			FateTests.tap(FateClientTests.player(server), UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.MARBLE, false));
		context.waitFor(client -> !client.level.getEntitiesOfClass(UbwEnvironmentEntity.class, bounds).isEmpty());
		Vec3 near = feet[0].add(2.5, 1.7, -3.5);
		Vec3 him = feet[0].add(0, 0.9, 0);
		Vec3 overlook = centre.add(UbwClientTest.OVERLOOK);
		Vec3 field = centre.add(UbwClientTest.FIELD);
		String name = "ubw-look-" + time + "-";
		int age = -UbwConfig.DEFAULTS.opening().chargeTicks();
		UbwClientTest.step(context, world, GATHERING - age);
		UbwClientTest.shot(context, near, him, name + "1-gathering");
		firstPerson(context, world, name + "1b-gathering-first-person");
		UbwClientTest.step(context, world, SURGING - GATHERING);
		UbwClientTest.shot(context, near, him, name + "2-surging");
		firstPerson(context, world, name + "2b-surging-first-person");
		UbwClientTest.step(context, world, FLASH - SURGING);
		UbwClientTest.shot(context, near, him, name + "3-flash");
		UbwClientTest.step(context, world, RING - FLASH);
		UbwClientTest.shot(context, overlook, field, name + "4-ring");
		UbwClientTest.step(context, world, REVEALING - RING);
		UbwClientTest.shot(context, overlook, field, name + "5-revealing");
		UbwClientTest.step(context, world, STANDING - REVEALING);
		UbwClientTest.shot(context, centre.add(UbwClientTest.EYE_LEVEL), centre.add(UbwClientTest.EYE_LEVEL_LOOK), name + "6-floor-eye-level");
		firstPerson(context, world, name + "6b-field-first-person");
		UbwClientTest.shot(context, centre.add(0, ABOVE, -ABOVE), centre.add(0, 0, ABOVE / 2), name + "7-floor-above");
		UbwClientTest.shot(context, centre.add(0, 1.6, 0), centre.add(0, 1.6, 0).add(direction(HIGH_GEAR).scale(GEAR_LOOK)), name + "8-gears-below");
		Vec3 aside = centre.add(-8, 3, 8);
		UbwClientTest.shot(context, aside, centre.add(direction(LOW_GEAR).scale(GEAR_LOOK)), name + "9-gears-angle");
	}

	private static void firstPerson(ClientGameTestContext context, TestSingleplayerContext world, String name) {
		FateClientTests.face(world, 0, LOOK_DOWN);
		FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
		context.takeScreenshot(name);
	}

	private static Vec3 direction(double[] degrees) {
		double bearing = Math.toRadians(degrees[0]);
		double elevation = Math.toRadians(degrees[1]);
		return new Vec3(Math.cos(bearing) * Math.cos(elevation), Math.sin(elevation), Math.sin(bearing) * Math.cos(elevation));
	}
}
