package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.UbwClientTest.check;

import io.github.cwzmorro.fatecraft.ubw.UbwController;
import io.github.cwzmorro.fatecraft.ubw.UbwRuntime;
import io.github.cwzmorro.fatecraft.ubw.UbwTerrain;
import java.io.IOException;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;

public class UbwSessionClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		List<Object> first;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			first = world.getServer().computeOnServer(UbwSessionClientTest::state);
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			List<Object> second = world.getServer().computeOnServer(UbwSessionClientTest::state);
			for (int i = 0; i < first.size(); i++) {
				check(second.get(i) != first.get(i), "Second session's UBW state " + i + " is its own, not the first session's");
			}
		}
	}

	private static List<Object> state(MinecraftServer server) {
		check(UbwRuntime.ready(server), "UBW runtime recovered at server start");
		try {
			return List.of(UbwController.of(server), UbwTerrain.of(server));
		} catch (IOException failure) {
			throw new IllegalStateException(failure);
		}
	}
}
