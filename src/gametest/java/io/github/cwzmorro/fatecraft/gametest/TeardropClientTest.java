package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.fatecraft.altera.TeardropPhotonRay;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

// Run alone with -PclientTest=Teardrop.
public class TeardropClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			AlteraClientTest.prepareSkyWorld(context, world);
			for (int i = 0; i < FateClientTests.TIMES_OF_DAY.length; i++) {
				String lighting = FateClientTests.LIGHTING_NAMES[i];
				world.getServer().runCommand("time set " + FateClientTests.TIMES_OF_DAY[i]);
				AlteraClientTest.skyStrike(context, world, lighting, TeardropPhotonRay.SPREAD_MODE);
				AlteraClientTest.skyStrike(context, world, lighting, TeardropPhotonRay.CONDENSED_MODE);
				world.getServer().runOnServer(server -> {
					if (Abilities.cooldownSecondsLeft(FateClientTests.player(server), TeardropPhotonRay.KEY.identifier(),
						TeardropPhotonRay.SPREAD_MODE) != 0) {
						throw new AssertionError("Creative Teardrop casts must have no cooldown");
					}
				});
			}
		}
	}
}
