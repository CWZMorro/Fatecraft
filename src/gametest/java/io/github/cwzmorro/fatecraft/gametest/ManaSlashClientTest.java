package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.artoria.ArtoriaAttackEntity;
import io.github.cwzmorro.fatecraft.artoria.ManaSlash;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.phys.Vec3;

public final class ManaSlashClientTest implements FabricClientGameTest {
	// Close enough to read the layers while keeping the whole arc in frame.
	private static final Vec3 CAMERA = new Vec3(9, 107, 12), LOOK = new Vec3(0, 102, 2);
	private static final Vec3 LOW_CAMERA = new Vec3(9, 103, 12);
	private static final Vec3 REAR_CAMERA = new Vec3(0, 105, -11), TOP_CAMERA = new Vec3(0, 116, 2);
	private static final int FOV = 45, SETTLE_TICKS = FateClientTests.TICKS_TO_SETTLE, MODE = 0;
	private static final int FLOOR_LENGTH = 20, FLOOR_CLEARANCE = 10;
	private static final double DEFAULT_REACH = 5, DEFAULT_ARC = 120;

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
			context.runOnClient(client -> client.options.hideGui = true);
			world.getServer().runOnServer(server -> {
				if (FateConfig.artoria().slashRange() != DEFAULT_REACH || FateConfig.artoria().slashArcDegrees() != DEFAULT_ARC) {
					throw new AssertionError("This test assumes the default five-block reach and 120-degree arc");
				}
			});
			for (String time : FateClientTests.TIMES_OF_DAY) {
				world.getServer().runCommand("time set " + time);
				world.getServer().runCommand("tp @p 0 101 0 0 0");
				context.waitTicks(SETTLE_TICKS);
				FateClientTests.view(context, CAMERA, LOOK, FOV);
				world.getServer().runCommand("tick freeze");
				FateClientTests.tap(world, ManaSlash.KEY.identifier(), MODE);
				context.waitTicks(SETTLE_TICKS);
				context.takeScreenshot("mana-slash-" + time + "-tick-0");
				int sweep = Durations.ticks(FateConfig.artoria().slashSweepSeconds());
				int fade = Durations.ticks(FateConfig.artoria().slashFadeSeconds());
				for (int tick = 1; tick < sweep + fade; tick++) {
					world.getServer().runCommand("tick step 1");
					context.waitTicks(SETTLE_TICKS);
					ArtoriaAttackEntity.Phase phase = tick < sweep ? ArtoriaAttackEntity.Phase.FLIGHT : ArtoriaAttackEntity.Phase.BLAST;
					int expectedAge = tick < sweep ? tick : tick - sweep;
					context.runOnClient(client -> {
						if (client.level.getEntitiesOfClass(ArtoriaAttackEntity.class, client.player.getBoundingBox().inflate(DEFAULT_REACH))
							.stream().noneMatch(attack -> attack.kind() == ArtoriaAttackEntity.Kind.GOLD && attack.phase() == phase
								&& attack.phaseAge() == expectedAge)) {
							throw new AssertionError("Missing Mana Slash keyframe phase " + phase);
						}
					});
					context.takeScreenshot("mana-slash-" + time + "-tick-" + tick);
					if (tick == sweep) {
						FateClientTests.view(context, LOW_CAMERA, LOOK, FOV);
						context.takeScreenshot("mana-slash-" + time + "-low-front");
						FateClientTests.view(context, REAR_CAMERA, LOOK, FOV);
						context.takeScreenshot("mana-slash-" + time + "-centred-front");
						FateClientTests.view(context, TOP_CAMERA, LOOK, FOV);
						context.takeScreenshot("mana-slash-" + time + "-full-arc-top");
						FateClientTests.view(context, CAMERA, LOOK, FOV);
					}
				}
				world.getServer().runCommand("tick step 1");
				context.waitTicks(SETTLE_TICKS);
				context.runOnClient(client -> {
					if (!client.level.getEntitiesOfClass(ArtoriaAttackEntity.class, client.player.getBoundingBox().inflate(DEFAULT_REACH)).isEmpty()) {
						throw new AssertionError("Mana Slash must disappear at the end of its fade");
					}
				});
				context.takeScreenshot("mana-slash-" + time + "-ended");
				world.getServer().runCommand("tick unfreeze");
				context.runOnClient(client -> client.setCameraEntity(client.player));
				context.waitTicks(SETTLE_TICKS);
			}
		}
	}
}
