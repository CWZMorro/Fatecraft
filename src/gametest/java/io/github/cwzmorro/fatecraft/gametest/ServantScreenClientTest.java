package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateClientTests.setGuiScale;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.servant.Servant;
import io.github.cwzmorro.alayacore.servant.ServantClasses;
import io.github.cwzmorro.alayacore.servant.ServantStorage;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public class ServantScreenClientTest implements FabricClientGameTest {
	private static final int WIDE = 1920;
	private static final int TALL = 1080;
	private static final int[] GUI_SCALES = {2, 3, 4};
	private static final int OVERFLOW_GUI_HEIGHT = 240;
	private static final int SMALL_SCALE = 3;
	private static final int SMALL_GUI_WIDTH = 320;
	private static final int SCREEN_MARGIN = 8;
	private static final String NEXT = "screen.alayacore.servant_selection.next";
	private static final String CHOOSE = "screen.alayacore.servant_selection.choose";
	private static final String CONTENTS = "screen.alayacore.servant_selection.contents";
	private static final int PANEL_MIDDLE_ABOVE_CHOOSE = 80;
	private static final double SCROLL_TO_END = -100.0;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			waitForSelection(context);
			context.getInput().resizeWindow(WIDE, TALL);
			Identifier gilgamesh = AlayaRegistries.SERVANT.getKey(FateServants.GILGAMESH);
			Identifier emiya = AlayaRegistries.SERVANT.getKey(FateServants.EMIYA);
			Identifier altera = AlayaRegistries.SERVANT.getKey(FateServants.ALTERA);
			Identifier artoria = AlayaRegistries.SERVANT.getKey(FateServants.ARTORIA);
			for (int scale : GUI_SCALES) {
				setGuiScale(context, scale);
				int pages = contentsPages(context);
				int from = 0;
				checkBounds(context);
				int chooseY = widgetY(context, CHOOSE);
				int arrowY = widgetY(context, NEXT);
				for (Shot shot : List.of(new Shot("contents", 0, false), new Shot("human", pages, true), new Shot("random", pages + 1, true),
					new Shot("gilgamesh", position(pages, servantIndex(context, gilgamesh)), true), new Shot("emiya", position(pages, servantIndex(context, emiya)), true),
					new Shot("altera", position(pages, servantIndex(context, altera)), true), new Shot("artoria", position(pages, servantIndex(context, artoria)), true))) {
					step(context, pages, from, shot.index());
					from = shot.index();
					checkChoose(context, shot.choosable());
					if (widgetY(context, CHOOSE) != chooseY || widgetY(context, NEXT) != arrowY) {
						throw new AssertionError("Selection controls moved while cycling: " + shot.name());
					}
					context.takeScreenshot("screen-" + shot.name() + "-gui-" + scale);
					if (shot.name().equals("altera") || shot.name().equals("artoria")) {
						scrollToEnd(context);
						context.takeScreenshot("screen-" + shot.name() + "-passives-gui-" + scale);
					}
				}
				step(context, pages, from, position(pages, servantIndex(context, gilgamesh)));
				context.getInput().resizeWindow(WIDE, OVERFLOW_GUI_HEIGHT * scale);
				context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
				context.takeScreenshot("screen-gilgamesh-overflow-gui-" + scale);
				scrollToEnd(context);
				context.takeScreenshot("screen-gilgamesh-scrolled-gui-" + scale);
				context.getInput().resizeWindow(WIDE, TALL);
				context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
				step(context, pages, position(pages, servantIndex(context, gilgamesh)), 0);
			}

			context.getInput().resizeWindow(SMALL_GUI_WIDTH * SMALL_SCALE, OVERFLOW_GUI_HEIGHT * SMALL_SCALE);
			setGuiScale(context, SMALL_SCALE);
			checkBounds(context);
			context.takeScreenshot("screen-contents-small");
			int smallPages = contentsPages(context);
			step(context, smallPages, 0, position(smallPages, servantIndex(context, gilgamesh)));
			context.takeScreenshot("screen-gilgamesh-small");
			context.getInput().resizeWindow(WIDE, TALL);

			// Another player has EMIYA. The first-join screen can't be closed from inside, so the test closes it and
			// reopens it with the Spirit Origin Changer, which reads who has whom.
			context.setScreen(() -> null);
			world.getServer().runOnServer(server -> {
				Map<UUID, Identifier> owners = new HashMap<>(server.globalAttachments().getAttachedOrCreate(ServantStorage.OWNERS));
				owners.put(UUID.randomUUID(), emiya);
				server.globalAttachments().setAttached(ServantStorage.OWNERS, Map.copyOf(owners));
			});
			world.getServer().runCommand("give @a alayacore:spirit_origin_changer");
			context.waitFor(client -> client.getConnection().hasClientLoaded());
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.getInput().pressKey(options -> options.keyUse);
			waitForSelection(context);
			for (int scale : GUI_SCALES) {
				setGuiScale(context, scale);
				int pages = contentsPages(context);
				step(context, pages, 0, position(pages, servantIndex(context, emiya)));
				checkChoose(context, false);
				context.takeScreenshot("screen-emiya-taken-gui-" + scale);
				step(context, pages, position(pages, servantIndex(context, emiya)), 0);
			}
			context.setScreen(() -> null);
		}
	}

	private static void checkBounds(ClientGameTestContext context) {
		context.runOnClient(client -> {
			for (var child : client.screen.children()) {
				if (child instanceof AbstractWidget widget && (widget.getX() < SCREEN_MARGIN || widget.getY() < SCREEN_MARGIN
					|| widget.getX() + widget.getWidth() > client.screen.width - SCREEN_MARGIN
					|| widget.getY() + widget.getHeight() > client.screen.height - SCREEN_MARGIN)) {
					throw new AssertionError("Selection control outside screen margin: " + widget.getMessage().getString());
				}
			}
		});
	}

	private static int widgetY(ClientGameTestContext context, String key) {
		return context.computeOnClient(client -> {
			AbstractWidget widget = find(client.screen.children(), key);
			if (widget == null) {
				throw new AssertionError("No widget '" + key + "' on " + client.screen);
			}
			return widget.getY();
		});
	}

	private static void waitForSelection(ClientGameTestContext context) {
		context.waitFor(client -> client.screen != null && find(client.screen.children(), CHOOSE) != null);
	}

	private static void checkChoose(ClientGameTestContext context, boolean expected) {
		boolean active = choosable(context);
		if (active != expected) {
			throw new AssertionError("Choose active: expected " + expected + ", got " + active);
		}
	}

	private record Shot(String name, int index, boolean choosable) {
	}

	private static int servantIndex(ClientGameTestContext context, Identifier servant) {
		int index = 0;
		// The classes of the world the test is in: the data the screen shows.
		for (Identifier classId : context.computeOnClient(client -> ServantClasses.ordered(client.level.registryAccess()).keySet())) {
			for (Servant candidate : AlayaRegistries.SERVANT) {
				if (candidate.servantClass().identifier().equals(classId)) {
					if (AlayaRegistries.SERVANT.getKey(candidate).equals(servant)) {
						return index;
					}
					index++;
				}
			}
		}
		throw new AssertionError("no such servant: " + servant);
	}

	private static int position(int contentsPages, int servantIndex) {
		return contentsPages + 2 + servantIndex;
	}

	private static int contentsPages(ClientGameTestContext context) {
		int pages = 0;
		while (!choosable(context)) {
			click(context, NEXT);
			pages++;
		}
		click(context, CONTENTS);
		return pages;
	}

	private static boolean choosable(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			AbstractWidget choose = find(client.screen.children(), CHOOSE);
			return choose != null && choose.active;
		});
	}

	private static void step(ClientGameTestContext context, int pages, int from, int to) {
		int entries = position(pages, AlayaRegistries.SERVANT.size());
		for (int i = 0; i < Math.floorMod(to - from, entries); i++) {
			click(context, NEXT);
		}
	}

	private static void scrollToEnd(ClientGameTestContext context) {
		double[] middle = context.computeOnClient(client -> {
			AbstractWidget choose = find(client.screen.children(), CHOOSE);
			if (choose == null) {
				throw new AssertionError("No Choose button on " + client.screen);
			}
			int scale = client.getWindow().getGuiScale();
			return new double[]{(choose.getX() + choose.getWidth() / 2.0) * scale, (choose.getY() - PANEL_MIDDLE_ABOVE_CHOOSE) * scale};
		});
		context.getInput().setCursorPos(middle[0], middle[1]);
		context.getInput().scroll(SCROLL_TO_END);
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
	}

	private static void click(ClientGameTestContext context, String translationKey) {
		double[] windowPos = context.computeOnClient(client -> {
			AbstractWidget widget = find(client.screen.children(), translationKey);
			if (widget == null) {
				throw new AssertionError("No widget '" + translationKey + "' on " + client.screen);
			}
			int scale = client.getWindow().getGuiScale();
			return new double[]{(widget.getX() + widget.getWidth() / 2.0) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale};
		});
		context.getInput().setCursorPos(windowPos[0], windowPos[1]);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
	}

	private static @Nullable AbstractWidget find(List<? extends GuiEventListener> listeners, String translationKey) {
		for (GuiEventListener listener : listeners) {
			if (listener instanceof AbstractWidget widget && widget.visible
				&& widget.getMessage().getContents() instanceof TranslatableContents contents && contents.getKey().equals(translationKey)) {
				return widget;
			}
			if (listener instanceof ContainerEventHandler container) {
				AbstractWidget found = find(container.children(), translationKey);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}
}
