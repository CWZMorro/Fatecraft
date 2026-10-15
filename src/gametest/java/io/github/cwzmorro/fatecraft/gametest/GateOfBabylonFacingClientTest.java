package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.combat.Aim;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.gate.GateEntity;
import io.github.cwzmorro.fatecraft.gate.GateOfBabylon;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.vault.Vault;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class GateOfBabylonFacingClientTest implements FabricClientGameTest {
	private static final double CLOSE = 0.001;
	private static final int OPENED_TICKS = 40;
	private static final int DISTANT = 100;
	private static final int DISTANT_UP = 4;
	// The client test's tiny default render distance cannot draw sections 100 blocks from its camera.
	private static final int VIEW_CHUNKS = 12;
	private static final int VOLLEY = 8;
	private static final int TURN_YAW = -35;
	private static final double SEARCH = 32.0;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.GILGAMESH);
			FateClientTests.flatFloor(world);
			FateClientTests.clearNoon(world);
			context.runOnClient(client -> {
				client.options.hideGui = true;
				client.options.renderDistance().set(VIEW_CHUNKS);
			});
			world.getServer().runOnServer(server -> {
				ServerPlayer player = FateClientTests.player(server);
				FateClientTests.giveMana(player);
				Vault.of(player).section(0).insert(new ItemStack(Items.IRON_SWORD, VOLLEY));
			});
			FateClientTests.tap(world, GateOfBabylon.KEY.identifier(), GateOfBabylon.TARGETED_THROW);
			context.waitTicks(OPENED_TICKS);
			world.getServer().runCommand("execute as @p at @s run summon husk ~8 ~ ~12 {NoAI:1b,PersistenceRequired:1b}");
			FateClientTests.face(world, TURN_YAW, 0);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = FateClientTests.player(server);
				Vec3 target = Aim.point(player, FateConfig.gate().aimRange());
				for (GateEntity gate : player.level().getEntitiesOfClass(GateEntity.class, player.getBoundingBox().inflate(SEARCH))) {
					if (gate.facing().distanceTo(target.subtract(gate.position()).normalize()) > CLOSE) {
						throw new AssertionError("Every waiting gate must turn toward its current aim");
					}
				}
			});
			context.runOnClient(client -> {
				var gates = client.level.getEntitiesOfClass(GateEntity.class, client.player.getBoundingBox().inflate(SEARCH));
				if (gates.size() != VOLLEY) {
					throw new AssertionError("The waiting volley must remain visible after turning");
				}
				for (GateEntity gate : gates) {
					if (!gate.shouldRenderAtSqrDistance(DISTANT * DISTANT)) {
						throw new AssertionError("The gate must not use the tiny collision-box distance cutoff");
					}
				}
			});
			FateClientTests.lookFrom(context, world, "gob-target-facing", 12, 4, 8, 130, 10);
			// At night the glow shows even at this distance.
			world.getServer().runCommand("time set midnight");
			// A client-only camera avoids the armor stand's own shorter server tracking range.
			GateOfBabylonClientTest.farCamera(context, DISTANT, DISTANT_UP);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.waitFor(client -> client.level.getEntitiesOfClass(GateEntity.class, client.player.getBoundingBox().inflate(SEARCH)).stream()
				.allMatch(gate -> client.levelRenderer.isSectionCompiledAndVisible(gate.blockPosition())));
			context.takeScreenshot("gob-distant-100");
			context.runOnClient(client -> client.setCameraEntity(client.player));
			world.getServer().runOnServer(server -> Abilities.endAll(FateClientTests.player(server)));
		}
	}
}
