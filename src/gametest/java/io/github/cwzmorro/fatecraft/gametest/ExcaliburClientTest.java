package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.artoria.ExcaliburEntity;
import io.github.cwzmorro.fatecraft.artoria.ExcaliburNP;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.phys.Vec3;

public class ExcaliburClientTest implements FabricClientGameTest {
	private static final int SETTLE = 10;
	private static final int TARGET_DISTANCE = 55;
	private static final int FOV = 65;
	private static final int RENDER_DISTANCE = 16;
	private static final double LIGHT_SEARCH = 220;
	private static final Vec3 TRAVEL_CAMERA = new Vec3(16, 110, -12);
	private static final Vec3 TRAVEL_LOOK = new Vec3(0.5, 102, 30);
	private static final Vec3 SIDE_CAMERA = new Vec3(-22, 104, 12);
	private static final Vec3 SIDE_LOOK = new Vec3(0.5, 103, 12);
	private static final Vec3 BEHIND_CAMERA = new Vec3(0.5, 104, -9);
	private static final Vec3 BEHIND_LOOK = new Vec3(0.5, 103, 30);
	private static final Vec3 CRATER_CLOSE_CAMERA = new Vec3(0.5, 116, 15.5);
	private static final Vec3 CRATER_CLOSE_LOOK = new Vec3(0.5, 100, 15.5);

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.ARTORIA);
			world.getServer().runCommand("gamemode creative @p");
			FateClientTests.clearNoon(world);
			FateClientTests.hold(world, FateWeapons.EXCALIBUR);
			world.getServer().runCommand("fill -24 80 -8 24 86 80 stone");
			world.getServer().runCommand("fill -24 87 -8 24 93 80 stone");
			world.getServer().runCommand("fill -24 94 -8 24 100 80 stone");
			world.getServer().runCommand("fill -16 101 -4 16 110 70 air");
			world.getServer().runCommand("setblock 0 102 " + TARGET_DISTANCE + " stone");
			world.getServer().runCommand("tp @p 0.5 101 0.5 90 0");
			context.runOnClient(client -> {
				client.options.hideGui = true;
				client.options.renderDistance().set(RENDER_DISTANCE);
			});
			context.waitTicks(SETTLE);
			world.getServer().runCommand("tick freeze");
			int charge = Durations.ticks(FateConfig.artoria().excaliburWindUpSeconds());
			FateClientTests.press(world, ExcaliburNP.KEY.identifier(), ExcaliburNP.BURST);
			step(context, world, charge + 1);
			FateClientTests.view(context, new Vec3(8, 105, -7), new Vec3(0, 103, 0), FOV);
			shot(context, "excalibur-charged-waiting", ExcaliburEntity.Phase.CHARGE);
			world.getServer().runCommand("tp @p 0.5 101 0.5 0 0");
			FateClientTests.release(world, ExcaliburNP.KEY.identifier());
			int travel = Durations.ticks(TARGET_DISTANCE / FateConfig.artoria().excaliburWaveSpeed());
			int early = Math.max(1, travel / 5);
			int mid = Math.max(early + 1, travel / 2);
			int arrival = travel - 1;
			step(context, world, early);
			FateClientTests.view(context, TRAVEL_CAMERA, TRAVEL_LOOK, FOV);
			shot(context, "excalibur-travel-early", ExcaliburEntity.Phase.WAVE);
			FateClientTests.view(context, SIDE_CAMERA, SIDE_LOOK, FOV);
			shot(context, "excalibur-travel-early-side", ExcaliburEntity.Phase.WAVE);
			FateClientTests.view(context, BEHIND_CAMERA, BEHIND_LOOK, FOV);
			shot(context, "excalibur-travel-early-behind-her", ExcaliburEntity.Phase.WAVE);
			step(context, world, mid - early);
			FateClientTests.view(context, TRAVEL_CAMERA, TRAVEL_LOOK, FOV);
			shot(context, "excalibur-travel-mid", ExcaliburEntity.Phase.WAVE);
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
			context.runOnClient(client -> client.setCameraEntity(client.player));
			context.waitTicks(SETTLE);
			shot(context, "excalibur-travel-mid-first-person", ExcaliburEntity.Phase.WAVE);
			step(context, world, arrival - mid);
			FateClientTests.view(context, TRAVEL_CAMERA, TRAVEL_LOOK, FOV);
			shot(context, "excalibur-travel-arrival", ExcaliburEntity.Phase.WAVE);
			step(context, world, travel - arrival + 5);
			FateClientTests.view(context, new Vec3(65, 132, -8), new Vec3(0, 124, 48), FOV);
			shot(context, "excalibur-end-explosion", ExcaliburEntity.Phase.BURST);
			world.getServer().runOnServer(server -> Abilities.endAll(FateClientTests.player(server)));
			step(context, world, 1);
			FateClientTests.view(context, new Vec3(0, 165, 30), new Vec3(0, 100, 30), FOV);
			context.takeScreenshot("excalibur-crater-line-top");
			FateClientTests.view(context, CRATER_CLOSE_CAMERA, CRATER_CLOSE_LOOK, FOV);
			context.takeScreenshot("excalibur-irregular-gouges-top-close");
			world.getServer().runCommand("gamerule alayacore:weapon_block_damage false");
			// Beam fires by itself at full charge along her look. Her cast counts every server tick, frozen or not (players
			// tick through tick freeze), so she must already face the shot's way.
			world.getServer().runCommand("tp @p 0.5 101 0.5 0 -5");
			FateClientTests.press(world, ExcaliburNP.KEY.identifier(), ExcaliburNP.HELD);
			step(context, world, charge + 1 + 5);
			FateClientTests.view(context, new Vec3(26, 106, 16), new Vec3(0, 103, 70), FOV);
			shot(context, "excalibur-beam-still-held", ExcaliburEntity.Phase.HELD);
			world.getServer().runOnServer(server -> Abilities.endAll(FateClientTests.player(server)));
			world.getServer().runCommand("tick unfreeze");
		}
	}

	private static void step(ClientGameTestContext context, TestSingleplayerContext world, int ticks) {
		world.getServer().runCommand("tick step " + ticks);
		context.waitTicks(ticks + SETTLE);
	}

	private static void shot(ClientGameTestContext context, String name, ExcaliburEntity.Phase phase) {
		context.runOnClient(client -> {
			if (client.level.getEntitiesOfClass(ExcaliburEntity.class, client.player.getBoundingBox().inflate(LIGHT_SEARCH))
				.stream().noneMatch(light -> light.phase() == phase)) {
				throw new AssertionError("Missing " + phase);
			}
		});
		context.takeScreenshot(name);
	}
}
