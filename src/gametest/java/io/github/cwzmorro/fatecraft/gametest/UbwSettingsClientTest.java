package io.github.cwzmorro.fatecraft.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.ability.AbilitySettings;
import io.github.cwzmorro.alayacore.client.screen.PresetScreen;
import io.github.cwzmorro.alayacore.progression.TrueName;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.gate.GateOfBabylon;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.ubw.UbwRuntime;
import io.github.cwzmorro.fatecraft.ubw.UnlimitedBladeWorks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.server.level.ServerPlayer;

public class UbwSettingsClientTest implements FabricClientGameTest {
	private static final int GUI_SCALE = 3;
	private static final int[] OTHER_GUI_SCALES = {2, 4};
	private static final double SCROLL_TO_END = -100.0;
	private static final int UBW_ROW = 3;
	private static final double SWORD_SIZE = 2.0;
	private static final double VOLLEY = 5.0;
	private static final int FIELD_Y = 150;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.EMIYA);
			context.getInput().resizeWindow(PresetScreenClientTest.WIDE, PresetScreenClientTest.TALL);
			context.runOnClient(client -> client.getToastManager().clear());
			FateClientTests.clearNoon(world);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = FateClientTests.player(server);
				TrueName.realize(player);
				FateClientTests.giveMana(player);
				AbilitySettings.set(player, UnlimitedBladeWorks.KEY.identifier(), "sword_size", SWORD_SIZE);
				AbilitySettings.set(player, UnlimitedBladeWorks.KEY.identifier(), "volley_1", VOLLEY);
			});
			context.getInput().pressKey(InputConstants.KEY_B);
			context.waitForScreen(PresetScreen.class);
			FateClientTests.setGuiScale(context, GUI_SCALE);
			PresetScreenClientTest.click(context, PresetScreenClientTest.ROW_X,
				PresetScreenClientTest.FIRST_ROW_Y + UBW_ROW * PresetScreenClientTest.ROW_HEIGHT);
			PresetScreenClientTest.click(context, PresetScreenClientTest.TAB_X, PresetScreenClientTest.TAB_Y);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.runOnClient(client -> client.getToastManager().clear());
			context.takeScreenshot("ubw-settings-page");
			for (int scale : OTHER_GUI_SCALES) {
				FateClientTests.setGuiScale(context, scale);
				context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
				context.takeScreenshot("ubw-settings-page-gui-" + scale);
				scrollSettingsToEnd(context);
				context.takeScreenshot("ubw-settings-page-gui-" + scale + "-end");
			}
			FateClientTests.setGuiScale(context, GUI_SCALE);
			context.setScreen(() -> null);

			world.getServer().runCommand("tp @p 0 " + FIELD_Y + " 0 0 0");
			world.getServer().runCommand("fill -36 " + (FIELD_Y - 1) + " -24 36 " + (FIELD_Y - 1) + " 50 minecraft:grass_block");
			world.getServer().runCommand("forceload add -48 -48 48 64");
			context.runOnClient(client -> client.options.hideGui = true);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = FateClientTests.player(server);
				player.setNoGravity(true);
				FateTests.tap(player, UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.MARBLE, false);
				if (!UbwRuntime.hasMarble(player)) {
					throw new AssertionError("UBW did not open");
				}
			});
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			FateClientTests.setCamera(context, CameraType.THIRD_PERSON_BACK);
			FateClientTests.tap(world, UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.AOE_THROW);
			context.waitTicks(FateConfig.trace().formationTicks() + GateOfBabylon.VOLLEY_OPENS_OVER + FateClientTests.TICKS_TO_SETTLE);
			context.takeScreenshot("ubw-settings-big-swords");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = FateClientTests.player(server);
				Abilities.end(player, UnlimitedBladeWorks.KEY.identifier());
				UbwRuntime.cancel(player);
			});
		}
	}

	private static void scrollSettingsToEnd(ClientGameTestContext context) {
		double[] pos = context.computeOnClient(client -> {
			int scale = client.getWindow().getGuiScale();
			AbstractSliderButton last = client.screen.children().stream().filter(AbstractSliderButton.class::isInstance)
				.map(AbstractSliderButton.class::cast).reduce((first, second) -> second).orElseThrow();
			return new double[]{(last.getX() + last.getWidth() / 2.0) * scale, (last.getY() + last.getHeight() / 2.0) * scale};
		});
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.getInput().scroll(SCROLL_TO_END);
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
	}
}
