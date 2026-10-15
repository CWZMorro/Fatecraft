package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateClientTests.setGuiScale;

import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.client.vault.VaultScreen;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.vault.Vault;
import io.github.cwzmorro.fatecraft.vault.VaultAbility;
import io.github.cwzmorro.fatecraft.vault.VaultSection;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

public class VaultClientTest implements FabricClientGameTest {
	private static final int WIDE = 1920;
	private static final int TALL = 1080;
	private static final int SMALL_WIDE = 854;
	private static final int SMALL_TALL = 480;
	private static final int[] GUI_SCALES = {2, 3, 4};
	private static final int SCALE_FOR_THE_TAB = 3;
	private static final int ARROW_ENTRIES = 40;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.GILGAMESH);
			world.getServer().runOnServer(server -> {
				Vault vault = Vault.of(FateClientTests.player(server));
				vault.section(0).insert(new ItemStack(FateWeapons.EA));
				ItemStack named = new ItemStack(Items.NETHERITE_SWORD);
				named.set(DataComponents.CUSTOM_NAME, Component.literal("Merodach"));
				vault.section(0).insert(named);
				for (Item item : FateTests.WEAPONS_AND_THROWABLES) {
					fill(vault.section(0), item, 1);
				}
				fill(vault.section(0), Items.ARROW, ARROW_ENTRIES);
				vault.section(1).insert(new ItemStack(Items.GOLDEN_APPLE, 3));
			});
			// The advancement toasts would cover the screen's right side.
			context.runOnClient(client -> client.getToastManager().clear());
			context.getInput().resizeWindow(WIDE, TALL);
			FateClientTests.press(world, VaultAbility.KEY.identifier(), 0);
			context.waitFor(client -> client.screen instanceof VaultScreen);
			for (int scale : GUI_SCALES) {
				setGuiScale(context, scale);
				context.takeScreenshot("vault-scale-" + scale);
			}

			setGuiScale(context, SCALE_FOR_THE_TAB);
			ScreenRectangle tab = context.computeOnClient(client -> ((VaultScreen) client.screen).settingsTab());
			context.getInput().setCursorPos((tab.left() + tab.width() / 2.0) * SCALE_FOR_THE_TAB, (tab.top() + tab.height() / 2.0) * SCALE_FOR_THE_TAB);
			context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.takeScreenshot("vault-gate-of-babylon-tab");

			context.getInput().resizeWindow(SMALL_WIDE, SMALL_TALL);
			setGuiScale(context, GUI_SCALES[0]);
			context.takeScreenshot("vault-small-window");
			context.setScreen(() -> null);
		}
	}

	private static void fill(VaultSection section, Item item, int entries) {
		for (int stack = 0; stack < entries * FateConfig.vault().stackMultiplier(); stack++) {
			section.insert(new ItemStack(item, item.getDefaultMaxStackSize()));
		}
	}
}
