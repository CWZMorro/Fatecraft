package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.altera.TeardropPhotonRayEntity;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.weapon.EnumaElishBeam;
import java.util.Comparator;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.phys.Vec3;

public class ClashClientTest implements FabricClientGameTest {
	private static final int FIRING_FRAME = 15;
	private static final double SEARCH_RADIUS = 200.0;
	private static final Vec3 AIM = new Vec3(0.5, 101, 0.5);

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.GILGAMESH);
			world.getServer().runCommand("gamemode spectator @p");
			world.getServer().runCommand("tp @p 0 101 -12 0 0");
			world.getServer().runCommand("fill -16 100 -16 16 100 24 stone");
			world.getServer().runCommand("fill -8 101 -8 8 160 16 air");
			FateClientTests.clearNoon(world);
			world.getServer().runCommand("summon husk 0.5 105 0.5 {NoAI:1b,NoGravity:1b,Tags:[\"clash_ea\"]}");
			world.getServer().runCommand("summon husk 0.5 101 10.5 {NoAI:1b,NoGravity:1b,Tags:[\"clash_altera\"]}");
			world.getServer().runOnServer(server -> {
				var player = FateClientTests.player(server);
				var mobs = player.level().getEntitiesOfClass(Husk.class, player.getBoundingBox().inflate(SEARCH_RADIUS));
				Husk ea = mobs.stream().max(Comparator.comparingDouble(Husk::getY)).orElseThrow();
				Husk altera = mobs.stream().min(Comparator.comparingDouble(Husk::getY)).orElseThrow();
				ClashScene.start(ea, altera, AIM);
			});
			context.waitTicks(FateConfig.ea().enumaElishWindUpTicks() + FIRING_FRAME);
			world.getServer().runCommand("tick freeze");
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.runOnClient(client -> {
				EnumaElishBeam ea = client.level.getEntitiesOfClass(EnumaElishBeam.class,
					client.player.getBoundingBox().inflate(SEARCH_RADIUS)).getFirst();
				TeardropPhotonRayEntity sky = client.level.getEntitiesOfClass(TeardropPhotonRayEntity.class,
					client.player.getBoundingBox().inflate(SEARCH_RADIUS)).getFirst();
				if (!sky.firing() || ea.reach() >= EnumaElishBeam.length() || sky.reach() >= sky.beamLength()) {
					throw new AssertionError("Both beams must synchronize clash clipping before capture");
				}
			});
			FateClientTests.lookFrom(context, world, "beam-clash-wide", 24, 18, -18, 39, -3);
			FateClientTests.lookFrom(context, world, "beam-clash-contact", 14, 26, -4, 41, 5);
			world.getServer().runCommand("tick unfreeze");
		}
	}
}
