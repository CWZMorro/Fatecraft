package io.github.cwzmorro.fatecraft.client.altera;

import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.altera.PhotonRayEntity;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Photon Ray's charge under the crosshair, drawn in vanilla's attack-indicator place with its sprites: a blue bar filling
 * with the server's charge, then the full indicator once release can fire. Shown only while charging.
 */
public final class PhotonRayChargeHud {
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_background");
	private static final Identifier PROGRESS = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_progress");
	private static final Identifier FULL = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_full");
	// Vanilla's attack indicator geometry: 16 wide, a 4-high bar or 16-high full sprite, set below the crosshair's centre.
	private static final int WIDTH = 16;
	private static final int BAR_HEIGHT = 4;
	private static final int FULL_HEIGHT = 16;
	private static final int CROSSHAIR_HALF = 7;
	private static final int BELOW_CENTRE = FULL_HEIGHT - CROSSHAIR_HALF;
	private static final float FILL_STEPS = WIDTH + 1;

	private PhotonRayChargeHud() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, Fatecraft.id("photon_ray_charge"), PhotonRayChargeHud::extract);
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || minecraft.getDebugOverlay().showDebugScreen()) {
			return;
		}
		List<PhotonRayEntity> rays = minecraft.level.getEntitiesOfClass(PhotonRayEntity.class,
			player.getBoundingBox().inflate(PhotonRayEntity.SWORD_AHEAD + PhotonRayEntity.coneLength()), ray -> ray.owner() == player && !ray.dashing());
		if (rays.isEmpty()) {
			return;
		}
		PhotonRayEntity ray = rays.getFirst();
		int x = graphics.guiWidth() / 2 - WIDTH / 2;
		int y = graphics.guiHeight() / 2 + BELOW_CENTRE;
		if (ray.charged()) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FULL, x, y, WIDTH, FULL_HEIGHT);
			return;
		}
		int filled = (int) (ray.chargeProgress(deltaTracker.getGameTimeDeltaPartialTick(false)) * FILL_STEPS);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, WIDTH, BAR_HEIGHT);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PROGRESS, WIDTH, BAR_HEIGHT, 0, 0, x, y, filled, BAR_HEIGHT, PhotonRayPalette.CHARGE_BAR);
	}
}
