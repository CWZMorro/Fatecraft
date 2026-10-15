package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import io.github.cwzmorro.fatecraft.client.weapon.ThrowPose;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import io.github.cwzmorro.fatecraft.weapon.PairedThrow;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class EmiyaThrowClientTest implements FabricClientGameTest {
	private static final int[] KEYFRAMES = {3, PairedThrow.MAIN_RELEASE, 8, 12};
	private static final String[] SHOTS = {"wind-up", "release", "follow-through-flight", "follow-through"};
	private static final int FLIGHT_FRAME = 2;
	private static final int AGE_TOLERANCE = 1;
	private static final int FOV = 70;
	private static final double FLIGHT_SEARCH = 30;
	private static final int WIDTH = 1440;
	private static final int HEIGHT = 1080;
	private static final Vec3 SIDE_EYE = new Vec3(2.2, 66.6, 3.2);
	private static final Vec3 SIDE_LOOK = new Vec3(0, 66.2, 0);
	private static final Vec3 FLIGHT_EYE = new Vec3(12, 68, 6);
	private static final Vec3 FLIGHT_LOOK = new Vec3(0, 66, 6);
	private static final Vec3 BEHIND_EYE = new Vec3(0, 69, -4);
	private static final Vec3 BEHIND_LOOK = new Vec3(0, 66, 8);
	private static final Vec3 ABOVE_EYE = new Vec3(0, 75, 5);
	private static final Vec3 ABOVE_LOOK = new Vec3(0, 66, 6);
	private static final Vec3 TARGET = new Vec3(0, 65, 10);
	private static final int TRAIL_END = 13;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.EMIYA);
			context.getInput().resizeWindow(WIDTH, HEIGHT);
			FateClientTests.clearNoon(world);
			world.getServer().runCommand("gamemode creative");
			world.getServer().runCommand("fill -6 64 -6 6 64 30 stone");
			world.getServer().runCommand("tp @p 0 65 0 0 0");
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			world.getClientLevel().waitForChunksRender();
			context.runOnClient(client -> client.options.hideGui = true);
			FateClientTests.view(context, SIDE_EYE, SIDE_LOOK, FOV);
			world.getServer().runCommand("tick freeze");
			long[] authoredStart = new long[1];
			world.getServer().runOnServer(server -> {
				ServerPlayer player = FateClientTests.player(server);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FateWeapons.KANSHOU));
				player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(FateWeapons.BAKUYA));
				player.getMainHandItem().use(server.overworld(), player, InteractionHand.MAIN_HAND);
				Long start = player.getAttached(PairedThrow.START);
				if (start == null || start != server.overworld().getGameTime()) {
					throw new AssertionError("Server must set the throw start tick at use");
				}
				authoredStart[0] = start;
			});
			int previous = 0;
			for (int frame = 0; frame < KEYFRAMES.length; frame++) {
				world.getServer().runCommand("tick step " + (KEYFRAMES[frame] - previous));
				previous = KEYFRAMES[frame];
				context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
				if (frame == FLIGHT_FRAME) {
					FateClientTests.view(context, FLIGHT_EYE, FLIGHT_LOOK, FOV);
				} else if (frame == FLIGHT_FRAME + 1) {
					FateClientTests.view(context, SIDE_EYE, SIDE_LOOK, FOV);
				}
				int expectedAge = KEYFRAMES[frame];
				context.runOnClient(client -> {
					Long start = client.player.getAttached(PairedThrow.START);
					if (start == null || start != authoredStart[0]) {
						throw new AssertionError("The server's throw start tick must be synced to the client");
					}
					if (expectedAge < PairedThrow.MAIN_RELEASE && (!start.equals(client.player.getMainHandItem().get(PairedThrow.PAIRED_THROW_START))
						|| !start.equals(client.player.getOffhandItem().get(PairedThrow.PAIRED_THROW_START)))) {
						throw new AssertionError("The wind-up must also reach the client through the held items");
					}
					AvatarRenderState state = (AvatarRenderState) client.getEntityRenderDispatcher().extractEntity(client.player, 0);
					float age = ThrowPose.age(state);
					if (!ThrowPose.active(age) || Math.abs(age - expectedAge) > AGE_TOLERANCE) {
						throw new AssertionError("Third-person renderer must use the synced throw age: " + age);
					}
				});
				context.takeScreenshot("emiya-pair-" + SHOTS[frame]);
				if (frame == FLIGHT_FRAME) {
					FateClientTests.view(context, BEHIND_EYE, BEHIND_LOOK, FOV);
					context.takeScreenshot("emiya-pair-flight-behind");
					FateClientTests.view(context, ABOVE_EYE, ABOVE_LOOK, FOV);
					context.takeScreenshot("emiya-pair-flight-above");
				} else {
					FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
					context.runOnClient(client -> client.options.hideGui = false);
					context.takeScreenshot("emiya-pair-first-person-" + SHOTS[frame]);
					context.runOnClient(client -> client.options.hideGui = true);
					FateClientTests.view(context, SIDE_EYE, SIDE_LOOK, FOV);
				}
			}
			world.getServer().runOnServer(server -> {
				if (server.overworld().getEntitiesOfClass(ThrownWeaponEntity.class,
					FateClientTests.player(server).getBoundingBox().inflate(FLIGHT_SEARCH)).size() != 2) {
					throw new AssertionError("Both swords must still be in flight");
				}
			});
			context.runOnClient(client -> client.options.hideGui = false);
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
			context.takeScreenshot("emiya-pair-first-person-follow-through");
			context.runOnClient(client -> client.options.hideGui = true);
			trail(context, world, "no-target");
			world.getServer().runOnServer(server -> {
				Husk target = new Husk(EntityType.HUSK, server.overworld());
				target.snapTo(TARGET.x, TARGET.y, TARGET.z, 0, 0);
				target.setNoAi(true);
				// Alive for both strikes, so neither sword is let go to fly straight on.
				target.setInvulnerable(true);
				server.overworld().addFreshEntity(target);
			});
			trail(context, world, "target");
			context.runOnClient(client -> {
				client.setCameraEntity(client.player);
				client.options.hideGui = false;
			});
			world.getServer().runCommand("tick unfreeze");
		}
	}

	private static void trail(ClientGameTestContext context, TestSingleplayerContext world, String name) {
		world.getServer().runCommand("tick step " + PairedThrow.DURATION);
		context.waitTicks(PairedThrow.DURATION + FateClientTests.TICKS_TO_SETTLE);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = FateClientTests.player(server);
			server.overworld().getEntitiesOfClass(ThrownWeaponEntity.class, player.getBoundingBox().inflate(FLIGHT_SEARCH)).forEach(ThrownWeaponEntity::discard);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FateWeapons.KANSHOU));
			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(FateWeapons.BAKUYA));
			player.getMainHandItem().use(server.overworld(), player, InteractionHand.MAIN_HAND);
		});
		FateClientTests.view(context, ABOVE_EYE, ABOVE_LOOK, FOV);
		world.getServer().runCommand("tick step " + PairedThrow.MAIN_RELEASE);
		for (int age = PairedThrow.MAIN_RELEASE; age <= TRAIL_END; age++) {
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.takeScreenshot("emiya-pair-trail-" + name + "-" + age);
			world.getServer().runCommand("tick step 1");
		}
	}
}
