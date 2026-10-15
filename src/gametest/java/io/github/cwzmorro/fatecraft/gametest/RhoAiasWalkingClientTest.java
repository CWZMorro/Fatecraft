package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.util.HeldInFront;
import io.github.cwzmorro.fatecraft.client.shield.RhoAiasRenderer;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.shield.RhoAias;
import io.github.cwzmorro.fatecraft.shield.RhoAiasEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.phys.Vec3;

public class RhoAiasWalkingClientTest implements FabricClientGameTest {
	private static final double CLOSE = 0.00001;
	private static final int WALK_TICKS = 4;
	private static final float[] FRAME_PARTIALS = {0.25F, 0.75F};

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.EMIYA);
			FateClientTests.flatFloor(world);
			FateClientTests.clearNoon(world);
			context.runOnClient(client -> client.options.hideGui = true);
			world.getServer().runOnServer(server -> FateClientTests.giveMana(FateClientTests.player(server)));
			FateClientTests.press(world, RhoAias.KEY.identifier(), 0);
			context.waitTicks(RhoAiasEntity.SUMMON_TICKS + FateClientTests.TICKS_TO_SETTLE);
			FateClientTests.setCamera(context, CameraType.THIRD_PERSON_BACK);
			context.getInput().holdKey(options -> options.keyUp);
			try {
				context.waitTicks(WALK_TICKS);
				Vec3 first = checkPose(context);
				context.takeScreenshot("rho-aias-walking-1");
				context.waitTicks(WALK_TICKS);
				Vec3 second = checkPose(context);
				if (first.distanceTo(second) <= CLOSE) {
					throw new AssertionError("The caster must actually walk between frames");
				}
				context.takeScreenshot("rho-aias-walking-2");
			} finally {
				context.getInput().releaseKey(options -> options.keyUp);
				FateClientTests.release(world, RhoAias.KEY.identifier());
			}
		}
	}

	private static Vec3 checkPose(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			RhoAiasEntity shield = client.level.getEntitiesOfClass(RhoAiasEntity.class, client.player.getBoundingBox().inflate(20)).getFirst();
			if (shield.following() != client.player) {
				throw new AssertionError("Held shield must synchronize its owner");
			}
			RhoAiasRenderer renderer = (RhoAiasRenderer) client.getEntityRenderDispatcher().getRenderer(shield);
			for (float partial : FRAME_PARTIALS) {
				RhoAiasRenderer.State state = renderer.createRenderState();
				renderer.extractRenderState(shield, state, partial);
				Vec3 expected = HeldInFront.at(client.player.getEyePosition(partial), client.player.getViewVector(partial), shield.followDistance());
				if (new Vec3(state.x, state.y, state.z).distanceTo(expected) > CLOSE) {
					throw new AssertionError("Shield and caster must share the same frame pose");
				}
			}
			return client.player.position();
		});
	}
}
