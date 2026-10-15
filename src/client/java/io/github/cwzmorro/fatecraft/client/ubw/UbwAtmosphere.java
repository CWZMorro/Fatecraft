package io.github.cwzmorro.fatecraft.client.ubw;

import io.github.cwzmorro.fatecraft.ubw.UbwEnvironmentEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The marble's own air for a camera inside it: the sky's warm light and a haze of dust, coming on as the opening's front
 * crosses the field and going as a broken marble crumbles.
 */
public final class UbwAtmosphere {
	// The sky's light is the least light the light map gives any block or sky level (RGB, 0 to 1).
	private static final Vector3f SKY_LIGHT = new Vector3f(0.88F, 0.8F, 0.7F);
	private static final Vector3f DUST = new Vector3f(0.42F, 0.3F, 0.25F);
	private static final float DUST_FROM = 4;
	private static final float DUST_WHOLE = 85;

	private UbwAtmosphere() { }

	public static void light(LightmapRenderState state, float partialTick) {
		float share = (float) share(partialTick);
		if (share <= 0) {
			return;
		}
		state.ambientColor = new Vector3f(state.ambientColor).lerp(SKY_LIGHT, share);
	}

	public static void haze(FogData fog, float partialTick) {
		float share = (float) share(partialTick);
		if (share <= 0) {
			return;
		}
		fog.environmentalStart = Math.min(fog.environmentalStart, DUST_FROM);
		fog.environmentalEnd = Math.min(fog.environmentalEnd, DUST_WHOLE / share);
		float alpha = fog.color.w;
		Vector3f colour = new Vector3f(fog.color.x, fog.color.y, fog.color.z).lerp(DUST, share);
		fog.color.set(colour.x, colour.y, colour.z, alpha);
	}

	private static double share(float partialTick) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return 0;
		}
		Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
		double time = level.getGameTime() + partialTick;
		for (Entity entity : level.entitiesForRendering()) {
			if (!(entity instanceof UbwEnvironmentEntity environment) || !UbwEnvironmentRenderer.visible(environment, camera)) {
				continue;
			}
			double opened = Mth.clamp((time - environment.openedAt()) / environment.openingTiming().spreadTicks(), 0, 1);
			double crumbled = environment.shattering()
				? Mth.clamp((time - environment.shatteredAt()) / environment.shatterTicks(), 0, 1)
				: 0;
			return opened * (1 - crumbled);
		}
		return 0;
	}
}
