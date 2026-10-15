package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.PhotonRayClientTest.chargingRay;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.combat.ForcedAdvance;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.altera.PhotonRay;
import io.github.cwzmorro.fatecraft.altera.TeardropGeometry;
import io.github.cwzmorro.fatecraft.altera.TeardropPhotonRay;
import io.github.cwzmorro.fatecraft.altera.TeardropPhotonRayEntity;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class AlteraStrikeClientTest implements FabricClientGameTest {
	private static final int GROUND_Y = 100;
	private static final int GROUND_DEPTH = 4;
	private static final int GROUND_TILE = 16;
	private static final int GROUND_HALF_WIDTH = 48;
	private static final int GROUND_NEAR = -16;
	private static final int GROUND_FAR = 144;
	private static final Vec3 CASTER = new Vec3(0.5, 140, 0.5);
	private static final Vec3 TARGET = new Vec3(0.5, GROUND_Y - 1, 110.5);
	private static final int POOL_HALF_WIDTH = 5;
	private static final int POOL_DEPTH = 2;
	private static final int RENDER_CHUNKS = 10;
	private static final int CHUNK_WAIT_TICKS = 1200;
	private static final Vec3 CONE_VIEW = new Vec3(8, 0.5, 0);
	private static final Vec3 CONE_LOOK = new Vec3(0, -0.6, 0);
	private static final int CONE_FOV = 60;
	private static final int STEP_SYNC_TICKS = 2;
	private static final Vec3 POOL_VIEW = new Vec3(14, 9, -14);
	private static final int POOL_FOV = 60;
	private static final Vec3 NEAR_VIEW = new Vec3(-35, -30, -45);
	private static final int NEAR_FOV = 70;
	private static final int FAR_FOV = 60;
	private static final double HEAD_CLEARANCE = 1.0;
	private static final Vec3 TURN_VIEW = new Vec3(40, 135, 30);
	private static final Vec3 TURN_LOOK = new Vec3(-12, 115, 108);
	private static final int TURN_FOV = 70;
	private static final float TURN_DEGREES = 15;
	private static final int TURN_TICKS = 30;
	private static final double LEAST_SWEEP = 10;
	private static final int FIRING_TICKS = 20;
	private static final double SEARCH = 200;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.ALTERA);
			world.getServer().runCommand("gamemode creative @p");
			FateClientTests.clearNoon(world);
			FateClientTests.hold(world, FateWeapons.PHOTON_RAY);
			context.runOnClient(client -> {
				client.options.hideGui = true;
				client.options.renderDistance().set(RENDER_CHUNKS);
				// As the options screen does: the server tracks entities only as far as the client says it can see.
				client.options.broadcastOptions();
			});
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE * 10);
			world.getServer().runCommand("tp @p " + CASTER.x + " " + CASTER.y + " " + CASTER.z + " 0 0");
			world.getServer().runOnServer(server -> {
				var player = FateClientTests.player(server);
				player.getAbilities().flying = true;
				player.setDeltaMovement(Vec3.ZERO);
				player.onUpdateAbilities();
			});
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE * 10);
			ground(world);
			// The strike lands over a hundred blocks out: its chunks must be on the client, or its entity never shows up there.
			BlockPos floor = BlockPos.containing(TARGET).below(POOL_DEPTH);
			int views = context.computeOnClient(client -> client.options.renderDistance().get());
			int serverViews = world.getServer().computeOnServer(server -> server.getPlayerList().getViewDistance());
			int waited = 0;
			while (!context.computeOnClient(client -> client.level.getBlockState(floor).is(Blocks.STONE))) {
				if (waited > CHUNK_WAIT_TICKS) {
					throw new AssertionError("The strike's ground never reached the client: render " + views + ", server view " + serverViews);
				}
				context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
				waited += FateClientTests.TICKS_TO_SETTLE;
			}
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE * 10);
			spin(context, world);
			strike(context, world);
		}
	}

	private static void ground(TestSingleplayerContext world) {
		for (int x = -GROUND_HALF_WIDTH; x < GROUND_HALF_WIDTH; x += GROUND_TILE) {
			for (int z = GROUND_NEAR; z < GROUND_FAR; z += GROUND_TILE) {
				world.getServer().runCommand("fill " + x + " " + (GROUND_Y - GROUND_DEPTH) + " " + z + " " + (x + GROUND_TILE - 1) + " "
					+ GROUND_Y + " " + (z + GROUND_TILE - 1) + " stone");
			}
		}
		int x = (int) Math.floor(TARGET.x);
		int z = (int) Math.floor(TARGET.z);
		world.getServer().runCommand("fill " + (x - POOL_HALF_WIDTH) + " " + (GROUND_Y - POOL_DEPTH + 1) + " " + (z - POOL_HALF_WIDTH) + " "
			+ (x + POOL_HALF_WIDTH) + " " + GROUND_Y + " " + (z + POOL_HALF_WIDTH) + " water");
	}

	private static void spin(ClientGameTestContext context, TestSingleplayerContext world) {
		Vec3 home = context.computeOnClient(client -> client.player.position());
		for (int i = 0; i < FateClientTests.TIMES_OF_DAY.length; i++) {
			String lighting = FateClientTests.LIGHTING_NAMES[i];
			world.getServer().runCommand("time set " + FateClientTests.TIMES_OF_DAY[i]);
			FateClientTests.press(world, PhotonRay.KEY.identifier(), PhotonRay.DASH_MODE);
			context.waitFor(client -> chargingRay(client) != null && chargingRay(client).charged());
			// Frozen, her own client still runs her on, so she is held still for the frames (the cone's tick steps her again).
			world.getServer().runCommand("tick freeze");
			holdStill(context, world);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			Vec3 eye = context.computeOnClient(client -> client.player.getEyePosition());
			FateClientTests.view(context, eye.add(CONE_VIEW), eye.add(CONE_LOOK), CONE_FOV);
			int before = context.computeOnClient(client -> chargingRay(client).tickCount);
			context.takeScreenshot("altera-strike-spin-" + lighting + "-a");
			world.getServer().runCommand("tick step 1");
			context.waitTicks(STEP_SYNC_TICKS);
			holdStill(context, world);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			int after = context.computeOnClient(client -> chargingRay(client).tickCount);
			if (after != before + 1) {
				throw new AssertionError("The two spin frames must be one tick apart: " + before + " -> " + after);
			}
			context.takeScreenshot("altera-strike-spin-" + lighting + "-b");
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
			context.runOnClient(client -> client.options.hideGui = false);
			context.takeScreenshot("altera-strike-spin-" + lighting + "-first");
			context.runOnClient(client -> client.options.hideGui = true);
			world.getServer().runCommand("tick unfreeze");
			world.getServer().runOnServer(server -> Abilities.endAll(FateClientTests.player(server)));
			// She ran on from where she stood; the next frames and the strike start from there.
			world.getServer().runCommand("tp @p " + home.x + " " + home.y + " " + home.z);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
		}
		world.getServer().runCommand("time set noon");
	}

	private static void holdStill(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> ForcedAdvance.stop(FateClientTests.player(server)));
		context.runOnClient(client -> ForcedAdvance.stop(client.player));
	}

	private static void strike(ClientGameTestContext context, TestSingleplayerContext world) {
		Vec3 eye = context.computeOnClient(client -> client.player.getEyePosition());
		Vec3 toTarget = TARGET.subtract(eye);
		double pitch = Math.toDegrees(Math.atan2(-toTarget.y, Math.hypot(toTarget.x, toTarget.z)));
		FateClientTests.face(world, 0, pitch);
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
		FateClientTests.view(context, TARGET.add(POOL_VIEW), TARGET, POOL_FOV);
		context.takeScreenshot("altera-strike-water-before");
		FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
		FateClientTests.press(world, TeardropPhotonRay.KEY.identifier(), TeardropPhotonRay.CONDENSED_MODE);
		context.waitTicks(Durations.ticks(FateConfig.altera().teardropWindUpSeconds()) + FIRING_TICKS);
		world.getServer().runCommand("tick freeze");
		world.getServer().runOnServer(server -> {
			var player = FateClientTests.player(server);
			var strikes = player.level().getEntitiesOfClass(TeardropPhotonRayEntity.class, player.getBoundingBox().inflate(SEARCH));
			if (!Abilities.isActive(player, TeardropPhotonRay.KEY.identifier()) || strikes.isEmpty()) {
				throw new AssertionError("Teardrop must still be held after its wind-up: active "
					+ Abilities.isActive(player, TeardropPhotonRay.KEY.identifier()) + ", strikes " + strikes);
			}
		});
		String tracking = world.getServer().computeOnServer(server -> "server view " + server.getPlayerList().getViewDistance()
			+ ", requested " + FateClientTests.player(server).requestedViewDistance() + ", strike at "
			+ FateClientTests.player(server).level().getEntitiesOfClass(TeardropPhotonRayEntity.class,
				FateClientTests.player(server).getBoundingBox().inflate(SEARCH)).getFirst().position());
		if (!context.computeOnClient(client -> !client.level.getEntitiesOfClass(TeardropPhotonRayEntity.class,
			client.player.getBoundingBox().inflate(SEARCH)).isEmpty())) {
			throw new AssertionError("The client never saw the strike: " + tracking);
		}
		Vec3 spot = context.computeOnClient(client -> sky(client).position());
		Vec3 seal = spot.add(TeardropGeometry.sky(FateConfig.altera(), 0));
		Vec3 beamEnd = context.computeOnClient(client -> sky(client).target());
		if (eye.distanceTo(seal) < 100 || !context.computeOnClient(client -> sky(client).firing())) {
			throw new AssertionError("The strike must be firing with its circles over a hundred blocks from the caster: " + eye.distanceTo(seal));
		}
		FateClientTests.view(context, TARGET.add(POOL_VIEW), TARGET, POOL_FOV);
		context.takeScreenshot("altera-strike-water-after");
		FateClientTests.view(context, seal.add(NEAR_VIEW), seal, NEAR_FOV);
		context.takeScreenshot("altera-strike-circles-near");
		// Her own view, just clear of her drawn head.
		FateClientTests.view(context, eye.add(seal.subtract(eye).normalize().scale(HEAD_CLEARANCE)), seal, FAR_FOV);
		context.takeScreenshot("altera-strike-circles-far");
		FateClientTests.view(context, TURN_VIEW, TURN_LOOK, TURN_FOV);
		context.takeScreenshot("altera-strike-follow-before");
		world.getServer().runCommand("tick unfreeze");
		FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
		FateClientTests.face(world, TURN_DEGREES, pitch);
		context.waitTicks(TURN_TICKS);
		world.getServer().runCommand("tick freeze");
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
		context.runOnClient(client -> {
			TeardropPhotonRayEntity ray = sky(client);
			if (!ray.position().equals(spot) || ray.target().distanceTo(beamEnd) < LEAST_SWEEP) {
				throw new AssertionError("Only the Condensed beam may turn: circles " + spot + " -> " + ray.position()
					+ ", beam " + beamEnd + " -> " + ray.target());
			}
		});
		FateClientTests.view(context, TURN_VIEW, TURN_LOOK, TURN_FOV);
		context.takeScreenshot("altera-strike-follow-after");
		world.getServer().runCommand("tick unfreeze");
		FateClientTests.release(world, TeardropPhotonRay.KEY.identifier());
		FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
	}

	private static TeardropPhotonRayEntity sky(Minecraft client) {
		return client.level.getEntitiesOfClass(TeardropPhotonRayEntity.class, client.player.getBoundingBox().inflate(SEARCH)).getFirst();
	}
}
