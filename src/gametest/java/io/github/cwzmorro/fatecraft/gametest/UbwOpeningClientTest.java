package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.UbwClientTest.check;

import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.progression.TrueName;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.client.ubw.UbwFireRing;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.ubw.UbwController;
import io.github.cwzmorro.fatecraft.ubw.UbwEnvironmentEntity;
import io.github.cwzmorro.fatecraft.ubw.UbwOpening;
import io.github.cwzmorro.fatecraft.ubw.UbwRuntime;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class UbwOpeningClientTest implements FabricClientGameTest {
	private static final UbwOpening.Timing TIMING = new UbwOpening.Timing(60, 12, 80, 104);

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.EMIYA);
			UbwClientTest.stage(context, world);
			world.getServer().runCommand("gamemode creative @p");
			world.getServer().runCommand("tp @p 0 150 0 0 0");
			world.getServer().runCommand("tick freeze");
			Vec3[] center = {Vec3.ZERO};
			try {
				world.getServer().runOnServer(server -> {
					Path file = Files.createTempFile("fatecraft-client-opening-", ".toml");
					try {
						Files.writeString(file, """
							[fatecraft.unlimited_blade_works]
							opening_charge_ticks = %d
							opening_ignition_ticks = %d
							opening_spread_ticks = %d
							opening_dying_ticks = %d
							""".formatted(TIMING.chargeTicks(), TIMING.ignitionTicks(), TIMING.spreadTicks(), TIMING.dyingTicks()));
						check(FateTests.configProblems(file).isEmpty(), "Valid custom timing");
						var player = FateClientTests.player(server);
						player.setNoGravity(true);
						TrueName.realize(player);
						FateClientTests.giveMana(player);
						center[0] = Vec3.atLowerCornerOf(UbwController.placement(player, FateConfig.ubw()));
						check(UbwRuntime.open(player) == UbwController.OpenResult.OPENED, "Actual staged opening");
					} finally {
						AlayaConfig.load(server.registryAccess());
						Files.deleteIfExists(file);
					}
				});
				AABB bounds = new AABB(center[0].subtract(30, 30, 30), center[0].add(30, 30, 30));
				context.waitFor(client -> !client.level.getEntitiesOfClass(UbwEnvironmentEntity.class, bounds).isEmpty());
				context.runOnClient(client -> {
					UbwEnvironmentEntity environment = client.level.getEntitiesOfClass(UbwEnvironmentEntity.class, bounds).getFirst();
					check(environment.openingTiming().equals(TIMING), "Client received captured server timing");
					check(!FateConfig.ubw().opening().equals(TIMING), "Local config has already returned to defaults");
					check(UbwFireRing.progress(TIMING.dyingTicks() / 2, environment.openingTiming()) == 0.5, "Fire follows configured lifetime");
				});
				UbwClientTest.step(context, world, 30);
				UbwClientTest.shot(context, new Vec3(2.5, 151.7, -3.5), new Vec3(0, 150.9, 0), "ubw-opening-config-mana");
				UbwClientTest.step(context, world, 48);
				UbwClientTest.shot(context, center[0].add(UbwClientTest.OVERLOOK), center[0].add(UbwClientTest.FIELD), "ubw-opening-config-ring");
				UbwClientTest.step(context, world, 22);
				UbwClientTest.shot(context, center[0].add(UbwClientTest.OVERLOOK), center[0].add(UbwClientTest.FIELD),
					"ubw-opening-config-half-spread");
				UbwClientTest.step(context, world, 66);
				UbwClientTest.shot(context, center[0].add(0, 10, -10), center[0].add(0, 0, 5), "ubw-opening-config-standing");
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			} finally {
				world.getServer().runOnServer(server -> UbwRuntime.cancel(FateClientTests.player(server)));
				world.getServer().runCommand("tick unfreeze");
				FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
				context.runOnClient(client -> client.options.hideGui = false);
			}
		}
	}
}
