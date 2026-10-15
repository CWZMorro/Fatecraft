package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.ability.AbilitySetting;
import io.github.cwzmorro.alayacore.ability.AbilitySettings;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.shield.RhoAias;
import io.github.cwzmorro.fatecraft.shield.RhoAiasEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerPlayer;

public class RhoAiasSettingsClientTest implements FabricClientGameTest {
	private static final double LARGEST_DISTANCE = 16.0;
	private static final double LARGEST_SIZE = 15.0;
	private static final double DROP_TOLERANCE = 0.1;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.EMIYA);
			FateClientTests.quietNoon(context, world);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = FateClientTests.player(server);
				FateClientTests.giveMana(player);
				for (AbilitySetting setting : AlayaRegistries.ABILITY.getValueOrThrow(RhoAias.KEY).settings()) {
					AbilitySettings.set(player, RhoAias.KEY.identifier(), setting.id(), setting.max());
				}
				Abilities.press(player, RhoAias.KEY.identifier(), 0, false);
			});
			double[] placed = world.getServer().computeOnServer(server -> {
				ServerPlayer player = FateClientTests.player(server);
				RhoAiasEntity shield = RhoAiasEntity.of(player);
				return new double[]{shield.radius(), shield.position().distanceTo(player.getEyePosition())};
			});
			if (placed[0] != LARGEST_SIZE / 2 || Math.abs(placed[1] - LARGEST_DISTANCE) > DROP_TOLERANCE) {
				throw new AssertionError("Rho Aias radius " + placed[0] + ", distance " + placed[1]);
			}
			FateClientTests.setCamera(context, CameraType.THIRD_PERSON_BACK);
			context.waitTicks(RhoAiasEntity.SUMMON_TICKS + FateClientTests.TICKS_TO_SETTLE);
			context.takeScreenshot("rho-aias-settings-max-from-behind");
			FateClientTests.release(world, RhoAias.KEY.identifier());
		}
	}
}
