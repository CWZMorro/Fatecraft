package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateClientTests.setGuiScale;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.client.screen.PresetScreen;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.shield.RhoAias;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.entity.player.Player;

public class PresetScreenClientTest implements FabricClientGameTest {
	static final int WIDE = 1920;
	static final int TALL = 1080;
	private static final int[] GUI_SCALES = {2, 3, 4};
	private static final double MANA = 5_000;
	// Layout of PresetScreen (its 276 × 262 panel is centred): a row of the ability list, the closed tab, and the description box.
	private static final int PRESET_X = 156;
	private static final int PRESET_Y = 18;
	private static final int SMALL_WIDTH = 960;
	private static final int SMALL_HEIGHT = 720;
	private static final int SMALL_SCALE = 3;
	static final int ROW_X = 60;
	static final int FIRST_ROW_Y = 38;
	static final int ROW_HEIGHT = 13;
	static final int TAB_X = 288;
	static final int TAB_Y = 14;
	private static final int DESCRIPTION_X = 140;
	private static final int DESCRIPTION_Y = 225;
	private static final int GATE_OF_BABYLON_ROW = 1;
	private static final double SCROLL_TO_END = -100.0;
	private static final int CONFIG_TAP_SECONDS = 123;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.GILGAMESH);
			open(context, world);
			for (int scale : GUI_SCALES) {
				setGuiScale(context, scale);
				click(context, ROW_X, FIRST_ROW_Y + GATE_OF_BABYLON_ROW * ROW_HEIGHT);
				context.takeScreenshot("preset-gate-of-babylon-gui-" + scale);
				moveTo(context, DESCRIPTION_X, DESCRIPTION_Y);
				context.getInput().scroll(SCROLL_TO_END);
				context.waitTick();
				context.takeScreenshot("preset-gate-of-babylon-modes-gui-" + scale);
			}
			context.setScreen(() -> null);
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.EMIYA);
			open(context, world);
			for (int scale : GUI_SCALES) {
				setGuiScale(context, scale);
				click(context, ROW_X, FIRST_ROW_Y);
				context.takeScreenshot("preset-rho-aias-gui-" + scale);
				click(context, TAB_X, TAB_Y);
				context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
				context.takeScreenshot("preset-rho-aias-settings-gui-" + scale);
				closeSettings(context);
			}
			context.getInput().resizeWindow(SMALL_WIDTH, SMALL_HEIGHT);
			setGuiScale(context, SMALL_SCALE);
			click(context, ROW_X, FIRST_ROW_Y);
			context.takeScreenshot("preset-rho-aias-small");
			click(context, TAB_X, TAB_Y);
			context.takeScreenshot("preset-rho-aias-settings-small");
			closeSettings(context);
			checkDurationReload(context, world);
			context.setScreen(() -> null);
		}
	}

	private static void checkDurationReload(ClientGameTestContext context, TestSingleplayerContext world) {
		Path config = FabricLoader.getInstance().getConfigDir().resolve("alayacore.toml");
		try {
			String original = Files.readString(config);
			try {
				Files.writeString(config, original.replaceFirst("tap_seconds = [0-9]+", "tap_seconds = " + CONFIG_TAP_SECONDS));
				world.getServer().runCommand("alayacore config reload");
				context.waitFor(client -> AlayaConfig.shields().tapSeconds() == CONFIG_TAP_SECONDS);
				context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
				String expected = context.computeOnClient(client -> Durations.text(CONFIG_TAP_SECONDS).getString());
				boolean described = context.computeOnClient(client -> Ability.description(RhoAias.KEY.identifier()).stream()
					.anyMatch(line -> line.getString().contains(expected)));
				if (!described) {
					throw new AssertionError("Rho Aias description did not use synced duration " + expected);
				}
				context.takeScreenshot("preset-rho-aias-reloaded-small");
			} finally {
				Files.writeString(config, original);
				world.getServer().runCommand("alayacore config reload");
			}
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	private static void open(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			Player player = FateClientTests.player(server);
			AlayaMana.addMax(player, MANA);
			AlayaMana.addCurrent(player, MANA / 2);
		});
		context.getInput().resizeWindow(WIDE, TALL);
		context.runOnClient(client -> client.getToastManager().clear());
		context.getInput().pressKey(InputConstants.KEY_B);
		context.waitForScreen(PresetScreen.class);
	}

	private static void moveTo(ClientGameTestContext context, int x, int y) {
		double[] pos = context.computeOnClient(client -> {
			int scale = client.getWindow().getGuiScale();
			Button preset = client.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
				.filter(button -> button.getMessage().getString().equals("1")).findFirst().orElseThrow();
			return new double[]{(preset.getX() - PRESET_X + x) * scale, (preset.getY() - PRESET_Y + y) * scale};
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
	}

	private static void closeSettings(ClientGameTestContext context) {
		double[] pos = context.computeOnClient(client -> {
			AbstractSliderButton slider = client.screen.children().stream().filter(AbstractSliderButton.class::isInstance)
				.map(AbstractSliderButton.class::cast).findFirst().orElseThrow();
			int scale = client.getWindow().getGuiScale();
			return new double[]{(slider.getX() + slider.getWidth() / 2.0) * scale, (slider.getY() - TAB_Y) * scale};
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
	}

	static void click(ClientGameTestContext context, int x, int y) {
		moveTo(context, x, y);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
	}
}
