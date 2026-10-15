package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.altera.PhotonRay;
import io.github.cwzmorro.fatecraft.altera.PhotonRayEntity;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import io.github.cwzmorro.fatecraft.weapon.WeaponSpin;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.SharedConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class PhotonRayClientTest implements FabricClientGameTest {
	private static final double QUARTER = 0.25;
	private static final double THREE_QUARTERS = 0.75;
	private static final int FLASH_FRAME_TICKS = 2;
	private static final int DASH_FRAME_TICKS = 5;
	private static final int CHARGE_FOV = 70;
	private static final int DASH_FOV = 95;
	private static final String FLOOR = "fill -8 100 -8 8 100 64 stone";
	private static final String CLEAR = "fill -8 101 -8 8 108 64 air";
	private static final String START = "tp @p 0 101 0 0 0";
	private static final Vec3 SIDE_CAMERA = new Vec3(9, 103, 9);
	private static final Vec3 SIDE_LOOK = new Vec3(0, 102, 9);
	private static final int SIDE_FOV = 70;
	private static final int SIDE_FRAMES = 4;
	private static final double SIDE_FROM_Z = 3;
	private static final int RUN_SAMPLES = 20;
	private static final double STEP_LEAST = 0.5;
	private static final double STEP_MOST = 1.6;
	private static final double STEP_SIDEWAYS = 0.2;
	private static final int SERVER_LAG_TICKS = 4;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.ALTERA);
			world.getServer().runCommand("gamemode creative @p");
			FateClientTests.clearNoon(world);
			world.getServer().runCommand(FLOOR);
			world.getServer().runCommand(CLEAR);
			world.getServer().runCommand(START);
			FateClientTests.hold(world, FateWeapons.PHOTON_RAY);
			context.runOnClient(client -> {
				client.options.hideGui = false;
				client.options.fov().set(CHARGE_FOV);
			});
			FateClientTests.setCamera(context, CameraType.THIRD_PERSON_BACK);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);

			int needed = PhotonRay.chargeTicks();
			FateClientTests.press(world, PhotonRay.KEY.identifier(), PhotonRay.DASH_MODE);
			int quarter = (int) Math.round(needed * QUARTER);
			context.waitTicks(quarter);
			checkCharge(context, QUARTER, false);
			context.takeScreenshot("photon-ray-charge-25");
			context.waitTicks((int) Math.round(needed * THREE_QUARTERS) - quarter);
			checkCharge(context, THREE_QUARTERS, false);
			context.takeScreenshot("photon-ray-charge-75");
			context.waitFor(client -> chargingRay(client) != null && chargingRay(client).charged());
			context.waitTicks(FLASH_FRAME_TICKS);
			context.takeScreenshot("photon-ray-charge-locked-flash");
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
			checkCharge(context, 1, true);
			context.takeScreenshot("photon-ray-charge-locked-first");

			// Held, she runs from the moment the charge fills; the first run frame is the one she is in now.
			context.waitTicks(DASH_FRAME_TICKS);
			context.runOnClient(client -> {
				client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				client.options.fov().set(DASH_FOV);
				WeaponSpin spin = client.player.getMainHandItem().get(FateWeapons.SPIN);
				if (spin == null || spin.degreesPerTick() != (float) FateConfig.altera().spinDegreesPerTick()) {
					throw new AssertionError("The run must spin the sword at the configured rate: " + spin);
				}
			});
			context.takeScreenshot("photon-ray-run-back");
			stop(context, world);

			sideFrames(context, world);
			stop(context, world);

			firstPerson(context, world);
			stop(context, world);

			smoothness(context, world);
			stop(context, world);
		}
	}

	private static void stop(ClientGameTestContext context, TestSingleplayerContext world) {
		FateClientTests.release(world, PhotonRay.KEY.identifier());
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
		world.getServer().runCommand(START);
		FateClientTests.setCamera(context, CameraType.THIRD_PERSON_BACK);
	}

	private static void startRunning(ClientGameTestContext context, TestSingleplayerContext world) {
		FateClientTests.press(world, PhotonRay.KEY.identifier(), PhotonRay.DASH_MODE);
		context.waitFor(client -> chargingRay(client) != null && chargingRay(client).dashing());
	}

	// The camera is set only once she is running, because with the camera on something else she sends the server nothing and the run stalls.
	private static void sideFrames(ClientGameTestContext context, TestSingleplayerContext world) {
		startRunning(context, world);
		context.waitFor(client -> client.player.getZ() > SIDE_FROM_Z);
		context.runOnClient(client -> client.options.hideGui = true);
		FateClientTests.viewNow(context, SIDE_CAMERA, SIDE_LOOK, SIDE_FOV);
		for (int frame = 0; frame < SIDE_FRAMES; frame++) {
			context.runOnClient(client -> {
				PhotonRayEntity ray = chargingRay(client);
				if (ray == null || !ray.dashing()) {
					throw new AssertionError("The cone must be up for every run frame: " + ray + " at " + client.player.position());
				}
			});
			context.takeScreenshot("photon-ray-run-side-" + frame);
			context.waitTicks(1);
		}
		context.runOnClient(client -> client.options.hideGui = false);
	}

	private static void firstPerson(ClientGameTestContext context, TestSingleplayerContext world) {
		startRunning(context, world);
		FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
		context.takeScreenshot("photon-ray-run-first");
	}

	private static void smoothness(ClientGameTestContext context, TestSingleplayerContext world) {
		startRunning(context, world);
		double speed = FateConfig.altera().dashBlocksPerSecond() / SharedConstants.TICKS_PER_SECOND;
		Vec3 last = context.computeOnClient(client -> client.player.position());
		StringBuilder steps = new StringBuilder();
		for (int sample = 0; sample < RUN_SAMPLES; sample++) {
			context.waitTicks(1);
			Vec3 now = context.computeOnClient(client -> client.player.position());
			double step = now.z - last.z;
			steps.append(String.format(" %.2f", step));
			if (step < speed * STEP_LEAST || step > speed * STEP_MOST || Math.abs(now.x - last.x) > speed * STEP_SIDEWAYS) {
				throw new AssertionError("Her run must be one steady step a tick (" + speed + "), forward: tick " + sample + ", steps" + steps);
			}
			last = now;
		}
		double behind = context.computeOnClient(client -> client.player.getZ())
			- world.getServer().computeOnServer(server -> FateClientTests.player(server).getZ());
		if (Math.abs(behind) > speed * SERVER_LAG_TICKS) {
			throw new AssertionError("The server must follow her client within " + SERVER_LAG_TICKS + " ticks of running: " + behind);
		}
	}

	private static void checkCharge(ClientGameTestContext context, double share, boolean locked) {
		context.runOnClient(client -> {
			PhotonRayEntity ray = chargingRay(client);
			if (ray == null || ray.charged() != locked || Math.abs(ray.chargeProgress(0) - share) > QUARTER) {
				throw new AssertionError("Charge cone expected near " + share + (locked ? ", locked" : ", unlocked") + ": "
					+ (ray == null ? "none" : ray.chargeProgress(0) + ", locked " + ray.charged()));
			}
		});
	}

	static @Nullable PhotonRayEntity chargingRay(Minecraft client) {
		List<PhotonRayEntity> rays = client.level.getEntitiesOfClass(PhotonRayEntity.class,
			client.player.getBoundingBox().inflate(PhotonRayEntity.SWORD_AHEAD + PhotonRayEntity.coneLength()), ray -> ray.owner() == client.player);
		return rays.isEmpty() ? null : rays.getFirst();
	}
}
