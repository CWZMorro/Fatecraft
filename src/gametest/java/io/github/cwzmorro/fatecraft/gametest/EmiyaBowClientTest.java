package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.client.gate.TreasureDrawing;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.trace.Projections;
import io.github.cwzmorro.fatecraft.weapon.BowDraw;
import io.github.cwzmorro.fatecraft.weapon.EmiyaBowItem;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class EmiyaBowClientTest implements FabricClientGameTest {
	private static final int WIDTH = 1440;
	private static final int HEIGHT = 1080;
	private static final int RANGE = 30;
	private static final int DRAW_VIEW_FOV = 60;
	private static final int PLAYER_VIEW_FOV = 70;
	private static final double CAMERA_TARGET_HEIGHT = 1;
	private static final double CAMERA_SIDE = 4;
	private static final double SIDE_CAMERA_HEIGHT = 1.4;
	private static final double SIDE_CAMERA_DEPTH = 0.7;
	private static final double SIDE_CAMERA_AIM = 0.4;
	private static final double FLIGHT_CAMERA_HEIGHT = 1.6;
	private static final double FLIGHT_CAMERA_DEPTH = 9;
	private static final int DRAW_TICKS = BowItem.MAX_DRAW_DURATION + FateClientTests.TICKS_TO_SETTLE;
	private static final int GUI_SCALE = 3;
	private static final int SKY_Y = 150;
	private static final float POSE_EPSILON = 1e-5F;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.EMIYA);
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
			context.getInput().resizeWindow(WIDTH, HEIGHT);
			FateClientTests.setGuiScale(context, GUI_SCALE);
			FateClientTests.clearNoon(world);
			toTheSky(world);
			world.getServer().runOnServer(server -> {
				var player = FateClientTests.player(server);
				player.setNoGravity(true);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FateWeapons.EMIYA_BOW));
				player.getInventory().setItem(1, Projections.create(FateWeapons.CALADBOLG));
			});
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.runOnClient(client -> {
				client.options.hideGui = false;
				client.getToastManager().clear();
			});
			assertVanillaPose(context);
			drawProgression(context, world);
			context.getInput().holdKey(options -> options.keyUse);
			context.waitTicks(DRAW_TICKS);
			assertDrawing(context);
			context.takeScreenshot("emiya-bow-drawn-first-person");
			context.runOnClient(client -> client.options.hideGui = true);
			thirdPersonShots(context, world, "emiya-bow-caladbolg");
			context.getInput().releaseKey(options -> options.keyUse);
			context.waitTicks(1);
			context.takeScreenshot("emiya-bow-just-released");
			world.getServer().runOnServer(server -> FateClientTests.player(server).getInventory()
				.setItem(1, Projections.create(FateWeapons.KANSHOU)));
			toTheSky(world);
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
			context.runOnClient(client -> client.options.hideGui = false);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.getInput().holdKey(options -> options.keyUse);
			context.waitTicks(DRAW_TICKS);
			assertDrawing(context);
			context.takeScreenshot("emiya-bow-kanshou-drawn-first-person");
			context.runOnClient(client -> client.options.hideGui = true);
			thirdPersonShots(context, world, "emiya-bow-kanshou");
			context.getInput().releaseKey(options -> options.keyUse);
			world.getServer().runOnServer(server -> {
				var player = FateClientTests.player(server);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
				player.getInventory().setItem(1, new ItemStack(Items.ARROW, 4));
			});
			toTheSky(world);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.getInput().holdKey(options -> options.keyUse);
			context.waitTicks(DRAW_TICKS);
			thirdPersonShots(context, world, "vanilla-bow");
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
			context.runOnClient(client -> client.options.hideGui = false);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.takeScreenshot("vanilla-bow-drawn-first-person");
			context.runOnClient(client -> client.options.hideGui = true);
			context.getInput().releaseKey(options -> options.keyUse);
			toTheSky(world);
			idleShots(context, "vanilla-bow");
			toTheSky(world);
			world.getServer().runOnServer(server -> {
				var player = FateClientTests.player(server);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FateWeapons.EMIYA_BOW));
				player.getInventory().setItem(1, Projections.create(FateWeapons.CALADBOLG));
			});
			idleShots(context, "emiya-bow");
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			world.getServer().runOnServer(server -> server.overworld().getEntitiesOfClass(ThrownWeaponEntity.class,
				FateClientTests.player(server).getBoundingBox().inflate(RANGE * 10)).forEach(ThrownWeaponEntity::discard));
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
			world.getServer().runCommand("tick freeze");
			volley(context, world, false);
			flightView(context);
			context.takeScreenshot("emiya-bow-sword-flight");
			volley(context, world, true);
			flightView(context);
			context.takeScreenshot("emiya-bow-triple-volley");
			context.runOnClient(client -> {
				client.setCameraEntity(client.player);
				client.options.hideGui = false;
			});
			world.getServer().runCommand("tick unfreeze");
		} finally {
			// Release the shared extraction camera even when an assertion aborts the scenario.
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
			context.runOnClient(client -> {
				client.options.hideGui = false;
				client.options.fov().set(PLAYER_VIEW_FOV);
			});
		}
	}

	private static void toTheSky(TestSingleplayerContext world) {
		world.getServer().runCommand("tp @p 0 " + SKY_Y + " 0 0 0");
	}

	private static void drawProgression(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("tick freeze");
		try {
			context.getInput().holdKey(options -> options.keyUse);
			world.getServer().runCommand("tick step 1");
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			float start = progressShots(context, "start", false);
			float duration = context.computeOnClient(client -> client.player.getUseItem().get(EmiyaBowItem.BOW_DRAW).duration());
			world.getServer().runCommand("tick step " + Math.max(1, (int) Math.ceil(duration / 2)));
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			float partial = progressShots(context, "partial", false);
			world.getServer().runCommand("tick step " + BowItem.MAX_DRAW_DURATION);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			float full = progressShots(context, "full", false);
			if (!(start < 0.2F && partial > start && partial < 1 && full == 1)) {
				throw new AssertionError("Expected rising start/partial/full draw fractions, got " + start + "/" + partial + "/" + full);
			}
			context.getInput().releaseKey(options -> options.keyUse);
			world.getServer().runCommand("tick step 1");
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			progressShots(context, "released-rest", true);
		} finally {
			context.getInput().releaseKey(options -> options.keyUse);
			world.getServer().runCommand("tick unfreeze");
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
			context.runOnClient(client -> {
				client.options.hideGui = false;
				client.options.fov().set(PLAYER_VIEW_FOV);
			});
		}
		world.getServer().runOnServer(server -> FateClientTests.player(server).getInventory()
			.setItem(1, Projections.create(FateWeapons.CALADBOLG)));
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
	}

	private static float progressShots(ClientGameTestContext context, String stage, boolean released) {
		FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
		context.runOnClient(client -> {
			client.options.hideGui = false;
			client.options.fov().set(PLAYER_VIEW_FOV);
		});
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
		float fraction = progressShot(context, stage + "-first-person", released);
		context.runOnClient(client -> client.options.hideGui = true);
		aimCamera(context, CAMERA_SIDE, SIDE_CAMERA_HEIGHT, SIDE_CAMERA_DEPTH, SIDE_CAMERA_AIM);
		progressShot(context, stage + "-third-person-side", released);
		return fraction;
	}

	private static float progressShot(ClientGameTestContext context, String name, boolean released) {
		float fraction = context.computeOnClient(client -> {
			if (released) {
				if (client.player.isUsingItem() || client.player.getMainHandItem().has(EmiyaBowItem.BOW_DRAW)) {
					throw new AssertionError("Release must clear use and the nocked sword");
				}
				return 0.0F;
			}
			assertDrawing(client);
			BowDraw draw = client.player.getUseItem().get(EmiyaBowItem.BOW_DRAW);
			double time = client.level.getGameTime() + client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
			float sampled = draw.fraction(time);
			if (!Float.isFinite(sampled) || sampled < 0 || sampled > 1) {
				throw new AssertionError("Invalid synchronized draw fraction " + sampled);
			}
			return sampled;
		});
		context.takeScreenshot("emiya-bow-progress-" + name + "-fraction-" + Math.round(fraction * 1000));
		return fraction;
	}

	private static void idleShots(ClientGameTestContext context, String name) {
		// The hand is not drawn while the GUI is hidden.
		FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
		context.runOnClient(client -> client.options.hideGui = false);
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
		context.takeScreenshot(name + "-idle-first-person");
		context.runOnClient(client -> client.options.hideGui = true);
		FateClientTests.setCamera(context, CameraType.THIRD_PERSON_BACK);
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
		context.takeScreenshot(name + "-idle-third-person-back");
		FateClientTests.setCamera(context, CameraType.THIRD_PERSON_FRONT);
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
		context.takeScreenshot(name + "-idle-third-person-front");
	}

	private static void thirdPersonShots(ClientGameTestContext context, TestSingleplayerContext world, String name) {
		aimCamera(context, 0, SIDE_CAMERA_HEIGHT, CAMERA_SIDE, SIDE_CAMERA_AIM);
		assertDrawing(context);
		context.takeScreenshot(name + "-drawn-third-person-front");
		aimCamera(context, 0, SIDE_CAMERA_HEIGHT, -CAMERA_SIDE, SIDE_CAMERA_AIM);
		assertDrawing(context);
		context.takeScreenshot(name + "-drawn-third-person-back");
		FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
		aimCamera(context, CAMERA_SIDE, SIDE_CAMERA_HEIGHT, SIDE_CAMERA_DEPTH, SIDE_CAMERA_AIM);
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
		assertDrawing(context);
		context.takeScreenshot(name + "-drawn-third-person-side");
		context.runOnClient(client -> {
			client.setCameraEntity(client.player);
			client.options.fov().set(PLAYER_VIEW_FOV);
		});
		FateClientTests.setCamera(context, CameraType.THIRD_PERSON_FRONT);
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
	}

	private static void aimCamera(ClientGameTestContext context, double side, double height, double depth, double forward) {
		var origin = context.computeOnClient(client -> client.player.position());
		FateClientTests.view(context, origin.add(side, height, depth),
			origin.add(0, CAMERA_TARGET_HEIGHT, forward), DRAW_VIEW_FOV);
	}

	private static void flightView(ClientGameTestContext context) {
		FateClientTests.viewFramed(context, flightBounds(context),
			new Vec3(CAMERA_SIDE, FLIGHT_CAMERA_HEIGHT - CAMERA_TARGET_HEIGHT, FLIGHT_CAMERA_DEPTH), DRAW_VIEW_FOV);
	}

	private static AABB flightBounds(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			var shots = client.level.getEntitiesOfClass(ThrownWeaponEntity.class, client.player.getBoundingBox().inflate(RANGE));
			if (shots.isEmpty()) {
				throw new AssertionError("Expected synchronized sword shots for flight framing");
			}
			float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
			AABB bounds = null;
			for (ThrownWeaponEntity shot : shots) {
				var look = new TreasureDrawing.Look();
				TreasureDrawing.extract(client.getItemModelResolver(), look, shot.item(), shot, partial);
				double length = look.length() * shot.scale();
				Vec3 tail = shot.getPosition(partial);
				Vec3 facing = Vec3.directionFromRotation(shot.getXRot(partial), shot.getYRot(partial));
				Vec3 tip = tail.add(facing.scale(length));
				// The whole length as padding covers asymmetric hilts and the model's sideways extent.
				AABB shotBounds = new AABB(tail, tip).inflate(length);
				bounds = bounds == null ? shotBounds : bounds.minmax(shotBounds);
			}
			return bounds;
		});
	}

	private static void assertDrawing(ClientGameTestContext context) {
		context.runOnClient(EmiyaBowClientTest::assertDrawing);
	}

	private static AvatarRenderState assertDrawing(Minecraft client) {
		var player = client.player;
		ItemStack used = player.getUseItem();
		if (!player.isUsingItem() || (used.is(FateWeapons.EMIYA_BOW) && !used.has(EmiyaBowItem.BOW_DRAW))) {
			throw new AssertionError("Draw must remain active with synchronized sword ammo in every camera");
		}
		var state = client.getEntityRenderDispatcher().getPlayerRenderer(player).createRenderState(player, 0);
		HumanoidArm arm = player.getUsedItemHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		var bowPose = arm == HumanoidArm.LEFT ? state.leftArmPose : state.rightArmPose;
		ItemStack physicalStack = arm == HumanoidArm.LEFT ? state.leftHandItemStack : state.rightHandItemStack;
		if (bowPose != HumanoidModel.ArmPose.BOW_AND_ARROW || state.useItemHand != player.getUsedItemHand()
			|| !ItemStack.matches(physicalStack, used) || state.getUseItemStackForArm(arm.getOpposite()).is(used.getItem())) {
			throw new AssertionError("The bow pose must keep the hand in use and its held stack");
		}
		return state;
	}

	private static void assertVanillaPose(ClientGameTestContext context) {
		context.runOnClient(client -> {
			var player = client.player;
			ItemStack main = player.getMainHandItem();
			ItemStack off = player.getOffhandItem();
			HumanoidArm mainArm = player.getMainArm();
			float yaw = player.getYRot();
			float pitch = player.getXRot();
			float oldYaw = player.yRotO;
			float oldPitch = player.xRotO;
			try {
				for (HumanoidArm arm : HumanoidArm.values()) {
					player.setMainArm(arm);
					for (InteractionHand hand : InteractionHand.values()) {
						for (float[] aim : new float[][] {{0, 0}, {35, -25}, {-60, 30}}) {
							player.setYRot(aim[0]);
							player.yRotO = aim[0];
							player.setXRot(aim[1]);
							player.xRotO = aim[1];
							float[] emiya = poseSample(client, hand, new ItemStack(FateWeapons.EMIYA_BOW));
							float[] vanilla = poseSample(client, hand, new ItemStack(Items.BOW));
							for (int i = 0; i < emiya.length; i++) {
								if (!Float.isFinite(emiya[i]) || Math.abs(emiya[i] - vanilla[i]) > POSE_EPSILON) {
									throw new AssertionError("Vanilla pose mismatch " + arm + "/" + hand + " sample " + i
										+ ": " + emiya[i] + " != " + vanilla[i]);
								}
							}
						}
					}
				}
			} finally {
				player.stopUsingItem();
				player.setItemInHand(InteractionHand.MAIN_HAND, main);
				player.setItemInHand(InteractionHand.OFF_HAND, off);
				player.setMainArm(mainArm);
				player.setYRot(yaw);
				player.setXRot(pitch);
				player.yRotO = oldYaw;
				player.xRotO = oldPitch;
			}
		});
	}

	private static float[] poseSample(Minecraft client, InteractionHand hand, ItemStack bow) {
		var player = client.player;
		player.stopUsingItem();
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		if (bow.is(FateWeapons.EMIYA_BOW)) {
			bow.set(EmiyaBowItem.BOW_DRAW, new BowDraw(client.level.getGameTime(),
				BowItem.MAX_DRAW_DURATION, Projections.create(FateWeapons.CALADBOLG)));
		}
		player.setItemInHand(hand, bow);
		player.startUsingItem(hand);
		AvatarRenderState state = assertDrawing(client);
		var model = client.getEntityRenderDispatcher().getPlayerRenderer(player).getModel();
		model.setupAnim(state);
		ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
		float[] sample = new float[3 + parts.length * 3];
		sample[0] = state.bodyRot;
		sample[1] = state.yRot;
		sample[2] = state.xRot;
		for (int i = 0; i < parts.length; i++) {
			sample[3 + i * 3] = parts[i].xRot;
			sample[4 + i * 3] = parts[i].yRot;
			sample[5 + i * 3] = parts[i].zRot;
		}
		return sample;
	}

	private static void volley(ClientGameTestContext context, TestSingleplayerContext world, boolean triple) {
		world.getServer().runOnServer(server -> {
			var player = FateClientTests.player(server);
			server.overworld().getEntitiesOfClass(ThrownWeaponEntity.class, player.getBoundingBox().inflate(RANGE)).forEach(ThrownWeaponEntity::discard);
			ItemStack bow = player.getMainHandItem();
			bow.set(EmiyaBowItem.BOW_TRIPLE, triple);
			player.getInventory().setItem(1, triple ? new ItemStack(Items.DIAMOND_SWORD) : Projections.create(FateWeapons.CALADBOLG));
			if (triple) {
				player.getInventory().setItem(2, Projections.create(FateWeapons.KANSHOU));
				player.getInventory().setItem(3, Projections.create(FateWeapons.BAKUYA));
			}
			bow.getItem().releaseUsing(bow, server.overworld(), player, bow.getItem().getUseDuration(bow, player) - DRAW_TICKS);
		});
		world.getServer().runCommand("tick step 1");
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
		world.getServer().runOnServer(server -> {
			int expected = triple ? FateConfig.bow().tripleCount() : 1;
			if (server.overworld().getEntitiesOfClass(ThrownWeaponEntity.class,
				FateClientTests.player(server).getBoundingBox().inflate(RANGE)).size() != expected) {
				throw new AssertionError("Expected " + expected + " sword shots");
			}
		});
	}
}
