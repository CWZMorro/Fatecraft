package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;

public class WeaponModelAvalonClientTest extends WeaponModelExcaliburClientTest {
	private static final double SIDE_DISTANCE = 3;
	private static final int USE_TICKS = 30;
	private static final int VIEW_TICKS = 5;
	private boolean beside;
	private volatile boolean sideCamera;

	@Override
	protected String mainHand() {
		return this.beside ? "excalibur" : "avalon";
	}

	@Override
	protected String offHand() {
		return this.beside ? "avalon" : "air";
	}

	@Override
	protected String screenshotPrefix() {
		return this.mainHand() + "-" + this.offHand();
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		// Vanilla excludes LocalPlayer whenever the camera entity is different. For this diagnostic camera,
		// extract the real player's render state too: preserve the actual use pose, skin and equipped items.
		LevelRenderEvents.END_EXTRACTION.register(frame -> {
			Minecraft client = Minecraft.getInstance();
			if (this.sideCamera && client.player != null && client.getCameraEntity() != client.player) {
				float partial = frame.deltaTracker().getGameTimeDeltaPartialTick(false);
				frame.levelState().entityRenderStates.add(client.getEntityRenderDispatcher().extractEntity(client.player, partial));
			}
		});
		this.beside = false;
		super.runTest(context);
		this.beside = true;
		super.runTest(context);
		if (FateClientTests.skipped(this)) {
			return;
		}
		used(context, InteractionHand.MAIN_HAND);
		used(context, InteractionHand.OFF_HAND);
	}

	private void used(ClientGameTestContext context, InteractionHand hand) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.ARTORIA);
			FateClientTests.clearNoon(world);
			world.getServer().runCommand("item replace entity @p weapon.mainhand with "
				+ (hand == InteractionHand.MAIN_HAND ? "fatecraft:avalon" : "fatecraft:excalibur"));
			world.getServer().runCommand("item replace entity @p weapon.offhand with "
				+ (hand == InteractionHand.OFF_HAND ? "fatecraft:avalon" : "minecraft:air"));
			FateClientTests.faceSouth(world);
			String prefix = "avalon-used-" + (hand == InteractionHand.MAIN_HAND ? "main" : "off");
			context.runOnClient(client -> client.options.hideGui = true);
			FateClientTests.setCamera(context, CameraType.THIRD_PERSON_FRONT);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.takeScreenshot(prefix.replace("used", "carried") + "-front");
			sideShot(context, prefix.replace("used", "carried") + "-side");
			context.runOnClient(client -> client.options.hideGui = false);
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.runOnClient(client -> {
				client.options.hideGui = false;
				client.options.keyUse.setDown(true);
				client.gameMode.useItem(client.player, hand);
			});
			context.waitTicks(USE_TICKS);
			context.runOnClient(client -> {
				if (!client.player.isUsingItem()) {
					throw new AssertionError("Artoria must actually be using Avalon");
				}
				if (client.player.getUseItem().has(FateWeapons.OPENING)) {
					throw new AssertionError("Avalon stays closed: using it must not create WeaponOpening");
				}
			});
			context.takeScreenshot(prefix + "-first-person");
			context.runOnClient(client -> client.options.hideGui = true);
			FateClientTests.setCamera(context, CameraType.THIRD_PERSON_FRONT);
			context.waitTicks(VIEW_TICKS);
			context.takeScreenshot(prefix + "-front");
			sideShot(context, prefix + "-side");
			context.runOnClient(client -> {
				client.options.keyUse.setDown(false);
				client.gameMode.releaseUsingItem(client.player);
				client.options.hideGui = false;
			});
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
		} finally {
			this.sideCamera = false;
		}
	}

	private void sideShot(ClientGameTestContext context, String name) {
		FateClientTests.setCamera(context, CameraType.THIRD_PERSON_BACK);
		context.runOnClient(client -> {
			ArmorStand camera = new ArmorStand(client.level, client.player.getX() + SIDE_DISTANCE,
				client.player.getY(), client.player.getZ());
			camera.snapTo(client.player.getX() + SIDE_DISTANCE, client.player.getY(), client.player.getZ(), 90, 0);
			camera.setYHeadRot(90);
			camera.yHeadRotO = 90;
			client.setCameraEntity(camera);
			this.sideCamera = true;
		});
		context.waitTicks(VIEW_TICKS);
		context.takeScreenshot(name);
		context.runOnClient(client -> {
			this.sideCamera = false;
			client.setCameraEntity(client.player);
		});
	}
}
