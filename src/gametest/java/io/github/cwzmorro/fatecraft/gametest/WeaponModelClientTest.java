package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.weapon.EaDraw;
import io.github.cwzmorro.fatecraft.weapon.EnumaElish;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

public class WeaponModelClientTest implements FabricClientGameTest {
	private static final int HELD_AFTER_TAKING_TICKS = 20;
	private static final int FULL_HD_WIDTH = 1920;
	private static final int FULL_HD_HEIGHT = 1080;
	private static final int SPIN_SHOT_TICKS = 3;
	private static final int UNLIMITED_FRAMERATE = 260;
	private static final int STANDS = 100;
	private static final int STANDS_PER_ROW = 25;
	private static final double STAND_SPACING = 0.6;
	private static final double ROW_SPACING = 1.5;
	private static final double FIRST_ROW_DISTANCE = 4;
	private static final int FRAME_SAMPLES = 200;
	private static final int GATHERED_SHOT_BEFORE_FIRING = 3 * FateClientTests.TICKS_TO_SETTLE;
	private static final int BEAM_PITCH = -10;
	private static final int SIDE_VIEW_ACROSS = 40;
	private static final int SIDE_VIEW_UP = 8;
	private static final int SIDE_VIEW_ALONG = 50;
	private static final int SIDE_VIEW_PITCH = 5;
	private static final int SIDE_VIEW_YAW = 90;
	private static final int LOOK_UP_PITCH = -40;
	private static final int LOOK_UP_THE_LATTICE_PITCH = -75;
	private static final int KEY_LIGHT_SHOT_TICKS = 50;
	private static final int FROM_BELOW_SHOT_TICKS = 90;
	private static final int LATTICE_SHOT_TICKS = 120;
	private static final int NIGHT_SHOT_TICKS = 160;
	private static final int RISING_SHOT_TICKS = 215;
	private static final int LATTICE_VIEW_UP = 28;
	private static final int LATTICE_VIEW_BACK = 80;
	private static final int LATTICE_VIEW_PITCH = -18;
	private static final int GATE_PITCH = 50;
	private static final int RISEN_PITCH = 10;
	private static final int VIEW_DELAY = 2 * FateClientTests.TICKS_TO_SETTLE;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.GILGAMESH);
			context.runOnClient(client -> {
				client.options.framerateLimit().set(UNLIMITED_FRAMERATE);
				client.options.enableVsync().set(false);
			});

			FateClientTests.clearNoon(world);
			FateClientTests.hold(world, FateWeapons.EA);
			FateClientTests.faceSouth(world);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.takeScreenshot("ea-01-first-person");

			world.getServer().runCommand("time set midnight");
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.takeScreenshot("ea-02-first-person-night-glow");
			world.getServer().runCommand("time set noon");

			FateClientTests.setCamera(context, CameraType.THIRD_PERSON_BACK);
			context.takeScreenshot("ea-03-third-person-back");
			FateClientTests.setCamera(context, CameraType.THIRD_PERSON_FRONT);
			context.takeScreenshot("ea-04-third-person-front");

			world.getServer().runOnServer(server -> {
				ServerPlayer player = FateClientTests.player(server);
				player.startUsingItem(InteractionHand.MAIN_HAND);
			});
			context.waitTicks(SPIN_SHOT_TICKS);
			context.takeScreenshot("ea-04b-spinning");
			context.waitTicks(SPIN_SHOT_TICKS);
			context.takeScreenshot("ea-04c-spinning-later");
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
			context.takeScreenshot("ea-04d-spinning-first-person");
			world.getServer().runCommand("time set midnight");
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.takeScreenshot("ea-04e-spinning-night-glow");
			world.getServer().runCommand("time set noon");
			world.getServer().runOnServer(server -> FateClientTests.player(server).stopUsingItem());

			FateClientTests.dropEaAhead(world);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.takeScreenshot("ea-05-on-the-ground");
			world.getServer().runCommand("kill @e[type=item]");
			FateClientTests.faceSouth(world);

			context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.takeScreenshot("ea-06-inventory");
			context.setScreen(() -> null);

			world.getServer().runCommand("time set midnight");
			context.getInput().holdKey(options -> options.keyUse);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.takeScreenshot("ea-08-compressed-air-first-person");
			FateClientTests.setCamera(context, CameraType.THIRD_PERSON_FRONT);
			context.takeScreenshot("ea-09-compressed-air-third-person");
			world.getServer().runCommand("time set noon");
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.takeScreenshot("ea-10-compressed-air-day");
			context.getInput().releaseKey(options -> options.keyUse);
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);

			world.getServer().runCommand("advancement grant @p only fatecraft:gilgamesh/true_name");
			FateClientTests.face(world, 0, BEAM_PITCH);
			FateClientTests.press(world, EnumaElish.KEY.identifier(), 0);
			FateClientTests.setCamera(context, CameraType.THIRD_PERSON_BACK);
			context.takeScreenshot("ea-11-enuma-elish-wind-up");
			context.waitTicks(FateConfig.ea().enumaElishWindUpTicks() - GATHERED_SHOT_BEFORE_FIRING);
			FateClientTests.setCamera(context, CameraType.THIRD_PERSON_FRONT);
			context.takeScreenshot("ea-11b-enuma-elish-gathered");
			FateClientTests.setCamera(context, CameraType.THIRD_PERSON_BACK);
			context.takeScreenshot("ea-12-enuma-elish-back");
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
			context.takeScreenshot("ea-13-enuma-elish-first-person");
			FateClientTests.setCamera(context, CameraType.THIRD_PERSON_FRONT);
			context.takeScreenshot("ea-14-enuma-elish-front");
			lookFromTheSide(context, world, "ea-15-enuma-elish-side");
			world.getServer().runCommand("time set midnight");
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			lookFromTheSide(context, world, "ea-16-enuma-elish-side-night");
			FateClientTests.release(world, EnumaElish.KEY.identifier());
			world.getServer().runCommand("time set noon");
			FateClientTests.faceSouth(world);
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);

			FateClientTests.hold(world, FateWeapons.BAB_ILU);
			world.getServer().runCommand("item replace entity @p weapon.offhand with fatecraft:ea");
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			FateClientTests.face(world, 0, LOOK_UP_PITCH);
			context.getInput().holdKey(options -> options.keyUse);
			context.waitTicks(KEY_LIGHT_SHOT_TICKS);
			context.takeScreenshot("ea-17-draw-key-light");
			context.getInput().releaseKey(options -> options.keyUse);
			FateClientTests.face(world, 0, LOOK_UP_THE_LATTICE_PITCH);
			context.waitTicks(FROM_BELOW_SHOT_TICKS - KEY_LIGHT_SHOT_TICKS);
			context.takeScreenshot("ea-17b-draw-lattice-from-below");
			// At 1080p too, where a line's width in pixels can be judged.
			int width = context.computeOnClient(client -> client.getWindow().getWidth());
			int height = context.computeOnClient(client -> client.getWindow().getHeight());
			context.getInput().resizeWindow(FULL_HD_WIDTH, FULL_HD_HEIGHT);
			context.takeScreenshot("ea-17c-draw-lattice-from-below-1080p");
			context.getInput().resizeWindow(width, height);
			context.waitTicks(LATTICE_SHOT_TICKS - FROM_BELOW_SHOT_TICKS - VIEW_DELAY);
			FateClientTests.lookFrom(context, world, "ea-18-draw-lattice", 0, LATTICE_VIEW_UP, -LATTICE_VIEW_BACK, 0, LATTICE_VIEW_PITCH);
			world.getServer().runCommand("time set midnight");
			context.waitTicks(NIGHT_SHOT_TICKS - LATTICE_SHOT_TICKS - VIEW_DELAY);
			FateClientTests.lookFrom(context, world, "ea-19-draw-lattice-night", 0, LATTICE_VIEW_UP, -LATTICE_VIEW_BACK, 0, LATTICE_VIEW_PITCH);
			FateClientTests.face(world, 0, GATE_PITCH);
			context.waitTicks(RISING_SHOT_TICKS - NIGHT_SHOT_TICKS);
			context.takeScreenshot("ea-20-draw-rising");
			FateClientTests.face(world, 0, RISEN_PITCH);
			context.waitTicks(EaDraw.RISE_START + EaDraw.RISE_TICKS - RISING_SHOT_TICKS);
			context.takeScreenshot("ea-21-draw-risen");
			// Taken with a right-click held on: Ea waits for the next press before it compresses the air.
			context.getInput().holdKey(options -> options.keyUse);
			context.waitTicks(HELD_AFTER_TAKING_TICKS);
			if (!context.computeOnClient(client -> client.player.getMainHandItem().is(FateWeapons.EA))) {
				throw new AssertionError("Ea was not taken into the main hand");
			}
			if (context.computeOnClient(client -> client.player.isUsingItem())) {
				throw new AssertionError("Ea started compressing the air from the right-click that took it");
			}
			context.getInput().releaseKey(options -> options.keyUse);
			context.takeScreenshot("ea-22-draw-taken");
			world.getServer().runCommand("time set noon");
			FateClientTests.faceSouth(world);

			double sword = frameMillis(context, world, "minecraft:netherite_sword");
			double ea = frameMillis(context, world, "fatecraft:ea");
			context.takeScreenshot("ea-07-on-armor-stands");
			Fatecraft.LOGGER.info("Frame time with {} armor stands holding: netherite swords {} ms, Ea {} ms", STANDS,
				String.format("%.2f", sword), String.format("%.2f", ea));
		}
	}

	private static double frameMillis(ClientGameTestContext context, TestSingleplayerContext world, String item) {
		world.getServer().runCommand("kill @e[type=armor_stand]");
		for (int i = 0; i < STANDS; i++) {
			double x = (i % STANDS_PER_ROW - STANDS_PER_ROW / 2) * STAND_SPACING;
			double z = FIRST_ROW_DISTANCE + i / STANDS_PER_ROW * ROW_SPACING;
			world.getServer().runCommand("execute as @p at @s run summon armor_stand ~" + x + " ~ ~" + z + " "
				+ "{ShowArms:1b,NoGravity:1b,Rotation:[180f,0f],equipment:{mainhand:{id:\"" + item + "\",count:1}}}");
		}
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
		long total = 0;
		for (int i = 0; i < FRAME_SAMPLES; i++) {
			context.waitTick();
			total += context.computeOnClient(Minecraft::getFrameTimeNs);
		}
		return total / 1e6 / FRAME_SAMPLES;
	}

	private static void lookFromTheSide(ClientGameTestContext context, TestSingleplayerContext world, String name) {
		FateClientTests.lookFrom(context, world, name, SIDE_VIEW_ACROSS, SIDE_VIEW_UP, SIDE_VIEW_ALONG, SIDE_VIEW_YAW, SIDE_VIEW_PITCH);
	}
}
