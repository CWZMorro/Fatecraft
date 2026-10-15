package io.github.cwzmorro.fatecraft.gametest;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.cwzmorro.fatecraft.client.weapon.HeldPose;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

// A test-only source poses a weapon no feature poses (Hrunting): held poses are not tied to one feature.
public class HeldPoseClientTest implements FabricClientGameTest {
	private static final String MARKER = "fatecraft_test_held_pose";
	private static final double MIN_CHANGED_SHARE = 0.01;
	// A colour channel must change by more than this (of 255) for its pixel to count: grass shimmer stays below it.
	private static final int CHANNEL_TOLERANCE = 24;
	private static final Vec3 DISPLAY = new Vec3(0.5, -60, 4.5);
	private static final double DISTANCE = 3;
	private static final int FOV = 60;
	private static final double SEARCH_RADIUS = 20;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		HeldPose.register((stack, time, pose) -> {
			if (stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains(MARKER)) {
				pose.raise();
				pose.tipDown(1);
			}
		});
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.EMIYA);
			FateClientTests.clearNoon(world);
			// An item display stands still (a held weapon sways with the arm), so only the pose can change the view.
			world.getServer().runCommand("summon item_display " + DISPLAY.x + " " + DISPLAY.y + " " + DISPLAY.z
				+ " {item:{id:\"fatecraft:hrunting\",count:1},item_display:\"none\"}");
			context.runOnClient(client -> client.options.hideGui = true);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			FateClientTests.view(context, DISPLAY.add(0, 1, -DISTANCE), DISPLAY.add(0, 1, 0), FOV);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			Path plain = context.takeScreenshot("held-pose-hrunting-plain");
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			Path still = context.takeScreenshot("held-pose-hrunting-plain-again");
			world.getServer().runOnServer(server -> {
				Display.ItemDisplay display = server.overworld().getEntitiesOfClass(Display.ItemDisplay.class,
					FateClientTests.player(server).getBoundingBox().inflate(SEARCH_RADIUS)).getFirst();
				ItemStack stack = display.getItemStack().copy();
				CompoundTag tag = new CompoundTag();
				tag.putBoolean(MARKER, true);
				stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
				display.setItemStack(stack);
			});
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			Path posed = context.takeScreenshot("held-pose-hrunting-posed");
			context.runOnClient(client -> client.options.hideGui = false);
			world.getServer().runCommand("kill @e[type=item_display]");
			FateClientTests.faceSouth(world);
			double noise = changedShare(plain, still);
			double changed = changedShare(still, posed);
			if (changed < noise + MIN_CHANGED_SHARE) {
				throw new AssertionError("A test-only held pose source must move a weapon no feature poses; changed share " + changed
					+ ", unposed frames differ by " + noise);
			}
		}
	}

	private static double changedShare(Path first, Path second) {
		try (InputStream a = Files.newInputStream(first); InputStream b = Files.newInputStream(second);
			NativeImage before = NativeImage.read(a); NativeImage after = NativeImage.read(b)) {
			int changed = 0;
			for (int y = 0; y < before.getHeight(); y++) {
				for (int x = 0; x < before.getWidth(); x++) {
					int one = before.getPixel(x, y);
					int two = after.getPixel(x, y);
					for (int shift = 0; shift < Integer.SIZE - Byte.SIZE; shift += Byte.SIZE) {
						if (Math.abs((one >> shift & 0xFF) - (two >> shift & 0xFF)) > CHANNEL_TOLERANCE) {
							changed++;
							break;
						}
					}
				}
			}
			return (double) changed / (before.getWidth() * before.getHeight());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
