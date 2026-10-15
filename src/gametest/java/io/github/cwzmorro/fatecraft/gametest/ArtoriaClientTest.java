package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.artoria.ArtoriaAttackEntity;
import io.github.cwzmorro.fatecraft.artoria.ArtoriaConfig;
import io.github.cwzmorro.fatecraft.artoria.ExcaliburEntity;
import io.github.cwzmorro.fatecraft.artoria.ExcaliburNP;
import io.github.cwzmorro.fatecraft.artoria.ManaBurst;
import io.github.cwzmorro.fatecraft.artoria.ManaShot;
import io.github.cwzmorro.fatecraft.artoria.ManaSlash;
import io.github.cwzmorro.fatecraft.artoria.StrikeAir;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.phys.Vec3;

public class ArtoriaClientTest implements FabricClientGameTest {
	private static final int MANA_AURA_SETTLE_TICKS = 20;
	private static final int STRIKE_FIRST_TICK = 1, STRIKE_FOV = 55, STRIKE_PLAYER_FOV = 70;
	private static final int SHOT_CHARGE_SHOT_TICK = 10, SHOT_BEAM_TICK = 1, SHOT_BLAST_TICKS = 3, SHOT_FOV = 60;
	private static final Vec3 SHOT_SIDE = new Vec3(14, 103, 6), SHOT_LOOK = new Vec3(0, 102, 6);
	private static final Vec3 STRIKE_SIDE = new Vec3(17, 106, 8), STRIKE_TOP = new Vec3(1, 122, 8);
	private static final Vec3 STRIKE_LOOK = new Vec3(0, 102, 6);
	private static final int SLASH_FIRST_TICK = 1, SLASH_FADE_TICK = 1;
	private static final int SLASH_FOV = 45;
	private static final Vec3 SLASH_CAMERA = new Vec3(15, 111, 17), SLASH_LOOK = new Vec3(0, 102, 4);
	private static final Vec3 SLASH_OVERHEAD = new Vec3(0, 123, 6);
	private static final int VIEW_SETTLE_TICKS = FateClientTests.TICKS_TO_SETTLE;
	private static final int FLOOR_LENGTH = 60, FLOOR_CLEARANCE = 8;
	private static final String START = "tp @p 0 101 0 0 0";
	private static final String SHOT_TARGET = "summon husk 0 101 12 {NoAI:1b,NoGravity:1b,Invulnerable:1b}";
	private static final int SHOT_THIRD_PERSON_FOV = 80, EXCALIBUR_RENDER_DISTANCE = 16;
	private static final int COOLDOWN_WAIT_TICKS = Durations.ticks(ArtoriaConfig.SHOT_COOLDOWN) + 2 * VIEW_SETTLE_TICKS;
	private static final String BEAM_START = "tp @p 0 101 0 0 -5";
	private static final int CHARGE_SHOT_LEAD_TICKS = 10, HELD_SHOT_TICKS = 20;
	private static final int EXCALIBUR_FOV = 65;
	private static final Vec3 HELD_CAMERA = new Vec3(26, 106, 16), HELD_LOOK = new Vec3(0, 103, 70);
	private static final int HELD_FOV = 75;
	private static final String PLATFORM = "fill -15 90 10 15 100 55 stone";
	private static final String PLINTH = "fill -1 101 -1 0 104 0 stone";
	private static final String PLINTH_REMOVED = "fill -1 101 -1 0 104 0 air";
	private static final String ON_PLINTH = "tp @p 0 105 0 0 12";
	private static final int WAVE_SHOT_TICKS = 2, WAVE_TO_BURST_TICKS = 12, BURST_GROWTH_TICKS = 6;
	private static final Vec3 WAVE_CAMERA = new Vec3(24, 114, -10), WAVE_LOOK = new Vec3(0, 104, 18);
	private static final int WAVE_FOV = 70;
	private static final Vec3 BURST_CAMERA = new Vec3(65, 129, -12), BURST_LOOK = new Vec3(0, 126, 28);
	private static final int BURST_FOV = 75;
	private static final float SHOT_VIEW_YAW = 35, SHOT_VIEW_PITCH = 10;
	private static final double ATTACK_SEARCH = 64, EXCALIBUR_SEARCH = 220;
	private static final int MANA_MODE = 0;
	private static final int MANA_FOV = 45;
	private static final Vec3 MANA_LOOK = new Vec3(0, 102, 0);
	private static final Vec3 MANA_FRONT = new Vec3(0, 102.2, 5);
	private static final Vec3 MANA_SIDE = new Vec3(5, 102.2, 0);

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.ARTORIA);
			world.getServer().runCommand("gamemode creative @p");
			FateClientTests.flatFloor(world, FLOOR_LENGTH, FLOOR_CLEARANCE);
			world.getServer().runCommand("weather clear");
			FateClientTests.hold(world, FateWeapons.CALIBURN);
			context.runOnClient(client -> {
				client.options.hideGui = true;
				client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				client.options.fov().set(SHOT_THIRD_PERSON_FOV);
			});
			for (String time : FateClientTests.TIMES_OF_DAY) {
				world.getServer().runCommand("time set " + time);
				world.getServer().runCommand(START);
				context.waitTicks(VIEW_SETTLE_TICKS);
				world.getServer().runCommand("tick freeze");
				FateClientTests.tap(world, StrikeAir.KEY.identifier(), 0);
				step(context, world, STRIKE_FIRST_TICK);
				FateClientTests.view(context, STRIKE_SIDE, STRIKE_LOOK, STRIKE_FOV);
				shot(context, "artoria-strike-air-side-" + time, ArtoriaAttackEntity.Phase.SLASH);
				FateClientTests.view(context, STRIKE_TOP, STRIKE_LOOK, STRIKE_FOV);
				shot(context, "artoria-strike-air-top-" + time, ArtoriaAttackEntity.Phase.SLASH);
				fromPlayer(context, CameraType.FIRST_PERSON, "artoria-strike-air-first-person-" + time);
				fromPlayer(context, CameraType.THIRD_PERSON_BACK, "artoria-strike-air-third-person-" + time);
				end(world);
				world.getServer().runCommand(START);
				FateClientTests.tap(world, ManaSlash.KEY.identifier(), MANA_MODE);
				FateClientTests.view(context, SLASH_CAMERA, SLASH_LOOK, SLASH_FOV);
				int sweepTicks = Durations.ticks(FateConfig.artoria().slashSweepSeconds());
				step(context, world, SLASH_FIRST_TICK);
				shot(context, "artoria-mana-slash-start-" + time, ArtoriaAttackEntity.Phase.FLIGHT);
				step(context, world, sweepTicks - SLASH_FIRST_TICK - SLASH_FADE_TICK);
				shot(context, "artoria-mana-slash-sweep-" + time, ArtoriaAttackEntity.Phase.FLIGHT);
				FateClientTests.view(context, SLASH_OVERHEAD, SLASH_LOOK, SLASH_FOV);
				shot(context, "artoria-mana-slash-overhead-" + time, ArtoriaAttackEntity.Phase.FLIGHT);
				FateClientTests.view(context, SLASH_CAMERA, SLASH_LOOK, SLASH_FOV);
				step(context, world, SLASH_FADE_TICK);
				shot(context, "artoria-mana-slash-end-" + time, ArtoriaAttackEntity.Phase.BLAST);
				step(context, world, SLASH_FADE_TICK);
				shot(context, "artoria-mana-slash-fade-" + time, ArtoriaAttackEntity.Phase.BLAST);
				end(world);
				context.runOnClient(client -> client.setCameraEntity(client.player));
				world.getServer().runCommand(START);
				world.getServer().runCommand(SHOT_TARGET);
				FateClientTests.tap(world, ManaShot.KEY.identifier(), 0);
				step(context, world, SHOT_CHARGE_SHOT_TICK);
				shot(context, "artoria-mana-shot-charge-" + time, ArtoriaAttackEntity.Phase.CHARGE);
				step(context, world, Durations.ticks(FateConfig.artoria().chargeSeconds()) - SHOT_CHARGE_SHOT_TICK + SHOT_BEAM_TICK);
				fromPlayer(context, CameraType.FIRST_PERSON, "artoria-mana-shot-beam-first-person-" + time);
				FateClientTests.view(context, SHOT_SIDE, SHOT_LOOK, SHOT_FOV);
				context.takeScreenshot("artoria-mana-shot-beam-side-" + time);
				fromPlayer(context, CameraType.THIRD_PERSON_BACK, "artoria-mana-shot-beam-third-person-" + time);
				step(context, world, SHOT_BLAST_TICKS);
				shot(context, "artoria-mana-shot-blast-" + time, ArtoriaAttackEntity.Phase.BLAST);
				end(world);
				world.getServer().runCommand("kill @e[type=husk]");
				world.getServer().runCommand("tick unfreeze");
				context.waitTicks(COOLDOWN_WAIT_TICKS);
			}
			excalibur(context, world);
			manaBurst(context, world);
		}
	}

	private static void manaBurst(ClientGameTestContext context, TestSingleplayerContext world) {
		FateClientTests.hold(world, FateWeapons.EXCALIBUR);
		for (String time : FateClientTests.TIMES_OF_DAY) {
			world.getServer().runCommand("time set " + time);
			world.getServer().runCommand(START);
			context.runOnClient(client -> client.setCameraEntity(client.player));
			context.waitTicks(VIEW_SETTLE_TICKS);
			world.getServer().runCommand("tick freeze");
			FateClientTests.tap(world, ManaBurst.KEY.identifier(), MANA_MODE);
			step(context, world, MANA_AURA_SETTLE_TICKS);
			world.getServer().runOnServer(server -> {
				if (!Abilities.isActive(FateClientTests.player(server), ManaBurst.KEY.identifier())) {
					throw new AssertionError("Mana Burst must be active for aura screenshots");
				}
			});
			FateClientTests.view(context, MANA_FRONT, MANA_LOOK, MANA_FOV);
			context.takeScreenshot("artoria-mana-burst-front-" + time);
			FateClientTests.view(context, MANA_SIDE, MANA_LOOK, MANA_FOV);
			context.takeScreenshot("artoria-mana-burst-side-" + time);
			step(context, world, Durations.ticks(FateConfig.artoria().burstSeconds()) - MANA_AURA_SETTLE_TICKS);
			world.getServer().runOnServer(server -> {
				if (Abilities.isActive(FateClientTests.player(server), ManaBurst.KEY.identifier())) {
					throw new AssertionError("Mana Burst must end with its timed buff");
				}
			});
			world.getServer().runCommand("tick unfreeze");
			context.runOnClient(client -> client.setCameraEntity(client.player));
			context.waitTicks(Durations.ticks(ArtoriaConfig.BURST_COOLDOWN) + VIEW_SETTLE_TICKS);
		}
	}

	private static void excalibur(ClientGameTestContext context, TestSingleplayerContext world) {
		FateClientTests.hold(world, FateWeapons.EXCALIBUR);
		context.runOnClient(client -> client.options.renderDistance().set(EXCALIBUR_RENDER_DISTANCE));
		int windUp = Durations.ticks(FateConfig.artoria().excaliburWindUpSeconds());
		for (String time : FateClientTests.TIMES_OF_DAY) {
			world.getServer().runCommand("time set " + time);
			world.getServer().runCommand(BEAM_START);
			world.getServer().runCommand("gamerule alayacore:weapon_block_damage false");
			context.runOnClient(client -> {
				client.setCameraEntity(client.player);
				client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				client.options.fov().set(EXCALIBUR_FOV);
			});
			context.waitTicks(VIEW_SETTLE_TICKS);
			world.getServer().runCommand("tick freeze");
			FateClientTests.press(world, ExcaliburNP.KEY.identifier(), ExcaliburNP.HELD);
			step(context, world, windUp - CHARGE_SHOT_LEAD_TICKS);
			npShot(context, "excalibur-charge-" + time, ExcaliburEntity.Phase.CHARGE);
			step(context, world, CHARGE_SHOT_LEAD_TICKS + HELD_SHOT_TICKS);
			FateClientTests.view(context, HELD_CAMERA, HELD_LOOK, HELD_FOV);
			npShot(context, "excalibur-held-side-" + time, ExcaliburEntity.Phase.HELD);
			end(world);
			world.getServer().runCommand("tick unfreeze");
			world.getServer().runCommand("gamerule alayacore:weapon_block_damage true");
			// A thick target platform: the impact is visibly in the ground, not floating at the caster.
			world.getServer().runCommand(PLATFORM);
			// She stands raised on a plinth, so her aim down meets the platform far out; on the ground it would land at her feet.
			world.getServer().runCommand(PLINTH);
			world.getServer().runCommand(ON_PLINTH);
			context.runOnClient(client -> client.setCameraEntity(client.player));
			context.waitTicks(VIEW_SETTLE_TICKS);
			world.getServer().runCommand("tick freeze");
			FateClientTests.press(world, ExcaliburNP.KEY.identifier(), ExcaliburNP.BURST);
			step(context, world, windUp + 1);
			FateClientTests.release(world, ExcaliburNP.KEY.identifier());
			step(context, world, WAVE_SHOT_TICKS);
			FateClientTests.view(context, WAVE_CAMERA, WAVE_LOOK, WAVE_FOV);
			npShot(context, "excalibur-release-wave-" + time, ExcaliburEntity.Phase.WAVE);
			step(context, world, WAVE_TO_BURST_TICKS);
			FateClientTests.view(context, BURST_CAMERA, BURST_LOOK, BURST_FOV);
			npShot(context, "excalibur-burst-at-spot-" + time, ExcaliburEntity.Phase.BURST);
			// A few ticks later the column has grown to its full height, so its top is not the rising front.
			step(context, world, BURST_GROWTH_TICKS);
			npShot(context, "excalibur-burst-grown-" + time, ExcaliburEntity.Phase.BURST);
			end(world);
			world.getServer().runCommand(PLINTH_REMOVED);
			world.getServer().runCommand("tick unfreeze");
			context.runOnClient(client -> client.setCameraEntity(client.player));
			context.waitTicks(VIEW_SETTLE_TICKS);
		}
	}

	private static void npShot(ClientGameTestContext context, String name, ExcaliburEntity.Phase phase) {
		context.runOnClient(client -> {
			List<ExcaliburEntity> lights = client.level.getEntitiesOfClass(ExcaliburEntity.class, client.player.getBoundingBox().inflate(EXCALIBUR_SEARCH));
			if (lights.stream().noneMatch(light -> light.phase() == phase)) {
				throw new AssertionError("Missing Excalibur phase " + phase + " for " + name + "; present: "
					+ lights.stream().map(light -> light.phase() + " age " + light.phaseAge() + " reach " + light.reach() + " of " + light.waveDistance()).toList());
			}
		});
		context.takeScreenshot(name);
	}

	private static void step(ClientGameTestContext context, TestSingleplayerContext world, int ticks) {
		world.getServer().runCommand("tick step " + ticks);
		context.waitTicks(ticks + VIEW_SETTLE_TICKS);
	}

	private static void shot(ClientGameTestContext context, String name, ArtoriaAttackEntity.Phase phase) {
		context.runOnClient(client -> {
			client.player.setYRot(SHOT_VIEW_YAW);
			client.player.setXRot(SHOT_VIEW_PITCH);
			boolean present = client.level.getEntitiesOfClass(ArtoriaAttackEntity.class, client.player.getBoundingBox().inflate(ATTACK_SEARCH))
				.stream().anyMatch(attack -> attack.phase() == phase);
			if (!present) {
				throw new AssertionError("Missing phase " + phase + " for " + name);
			}
		});
		context.waitTicks(VIEW_SETTLE_TICKS);
		context.takeScreenshot(name);
		context.runOnClient(client -> {
			client.player.setYRot(0);
			client.player.setXRot(0);
		});
	}

	private static void fromPlayer(ClientGameTestContext context, CameraType camera, String name) {
		FateClientTests.setCamera(context, camera);
		context.runOnClient(client -> {
			client.setCameraEntity(client.player);
			client.options.hideGui = false;
			client.options.fov().set(STRIKE_PLAYER_FOV);
		});
		context.waitTicks(VIEW_SETTLE_TICKS);
		context.takeScreenshot(name);
		context.runOnClient(client -> client.options.hideGui = true);
	}

	private static void end(TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> Abilities.endAll(FateClientTests.player(server)));
	}
}
