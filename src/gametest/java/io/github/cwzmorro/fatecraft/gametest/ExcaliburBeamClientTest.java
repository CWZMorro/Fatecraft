package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.artoria.ExcaliburEntity;
import io.github.cwzmorro.fatecraft.artoria.ExcaliburNP;
import io.github.cwzmorro.fatecraft.client.artoria.ExcaliburChargeTrail;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.phys.Vec3;

// The cast counts every server tick even under tick freeze, so the walk and shots must fit inside the charge.
public class ExcaliburBeamClientTest implements FabricClientGameTest {
	private static final int SETTLE = 10;
	private static final int FIRST_LIGHTS = 5;
	private static final int WALK_TICKS = 35;
	private static final int FIRING_TICKS = 5;
	private static final int FOV = 65;
	private static final int FLOOR_LENGTH = 40, FLOOR_CLEARANCE = 10;
	private static final double MIN_WALK = 0.5;
	private static final double MAX_FADED_SHARE = 0.5;
	private static final Vec3 SIDE = new Vec3(6, 104, -6);
	private static final Vec3 LOOK = new Vec3(1.5, 102.5, 0.5);
	private static final Vec3 ABOVE = new Vec3(1.5, 109, -3);
	private static final Vec3 ABOVE_LOOK = new Vec3(1.5, 101, 1);
	private static final Vec3 BEAM_SIDE = new Vec3(15, 105, -8);
	private static final Vec3 BEAM_LOOK = new Vec3(0, 103, 24);

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.ARTORIA);
			world.getServer().runCommand("gamemode creative @p");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("time set midnight");
			world.getServer().runCommand("gamerule alayacore:weapon_block_damage false");
			FateClientTests.flatFloor(world, FLOOR_LENGTH, FLOOR_CLEARANCE);
			FateClientTests.hold(world, FateWeapons.EXCALIBUR);
			world.getServer().runCommand("tp @p 0.5 101 0.5 0 0");
			context.runOnClient(client -> {
				client.options.hideGui = true;
				client.options.renderDistance().set(16);
			});
			context.waitTicks(SETTLE);
			int charge = Durations.ticks(FateConfig.artoria().excaliburWindUpSeconds());
			FateClientTests.press(world, ExcaliburNP.KEY.identifier(), ExcaliburNP.HELD);
			context.waitTicks(FIRST_LIGHTS);
			FateClientTests.view(context, ABOVE, ABOVE_LOOK, FOV);
			context.takeScreenshot("beam-01-charge-above-before-walk");
			List<Vec3> births = context.computeOnClient(client -> ExcaliburChargeTrail.birthPositions());
			// Back to her own camera, so the key moves her.
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
			Vec3 before = context.computeOnClient(client -> client.player.position());
			context.getInput().holdKey(options -> options.keyLeft);
			context.waitTicks(WALK_TICKS);
			context.getInput().releaseKey(options -> options.keyLeft);
			context.runOnClient(client -> {
				Vec3 after = client.player.position();
				if (after.distanceTo(before) < MIN_WALK) {
					throw new AssertionError("She did not walk sideways: " + before + " -> " + after);
				}
				List<Vec3> now = ExcaliburChargeTrail.birthPositions();
				if (births.stream().filter(now::contains).count() < births.size() * (1 - MAX_FADED_SHARE)) {
					throw new AssertionError("Charge lights moved with her");
				}
			});
			assertPhase(context, ExcaliburEntity.Phase.CHARGE);
			context.takeScreenshot("beam-02-charge-first-person-after-walk");
			FateClientTests.view(context, ABOVE, ABOVE_LOOK, FOV);
			context.takeScreenshot("beam-03-charge-trail-above-after-walk");
			int elapsed = FIRST_LIGHTS + 3 * FateClientTests.TICKS_TO_SETTLE + WALK_TICKS;
			// The key stays held: the beam fires on its own at full charge.
			context.waitTicks(charge - elapsed + FIRING_TICKS);
			assertPhase(context, ExcaliburEntity.Phase.HELD);
			context.takeScreenshot("beam-04-firing-still-held-above");
			FateClientTests.view(context, SIDE, LOOK, FOV);
			context.takeScreenshot("beam-05-firing-still-held-side");
			FateClientTests.view(context, BEAM_SIDE, BEAM_LOOK, FOV);
			context.takeScreenshot("beam-06-firing-still-held-along");
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
			context.takeScreenshot("beam-07-firing-first-person");
			FateClientTests.release(world, ExcaliburNP.KEY.identifier());
			context.waitTicks(SETTLE);
			context.runOnClient(client -> {
				if (client.level.getEntitiesOfClass(ExcaliburEntity.class, client.player.getBoundingBox().inflate(FateConfig.artoria().excaliburBeamRange()))
					.stream().anyMatch(light -> light.phase() == ExcaliburEntity.Phase.HELD)) {
					throw new AssertionError("Release did not stop Beam");
				}
			});
			FateClientTests.view(context, SIDE, LOOK, FOV);
			context.takeScreenshot("beam-08-after-release-lights-stay");
		}
	}

	private static void assertPhase(ClientGameTestContext context, ExcaliburEntity.Phase phase) {
		context.runOnClient(client -> {
			if (client.level.getEntitiesOfClass(ExcaliburEntity.class, client.player.getBoundingBox().inflate(FateConfig.artoria().excaliburBeamRange()))
				.stream().noneMatch(light -> light.phase() == phase)) {
				throw new AssertionError("Missing " + phase);
			}
		});
	}
}
