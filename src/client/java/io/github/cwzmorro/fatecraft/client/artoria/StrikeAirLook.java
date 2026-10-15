package io.github.cwzmorro.fatecraft.client.artoria;

import static io.github.cwzmorro.fatecraft.client.artoria.ArtoriaLook.HASH_ANGLE;
import static io.github.cwzmorro.fatecraft.client.artoria.ArtoriaLook.HASH_DEPTH;
import static io.github.cwzmorro.fatecraft.client.artoria.ArtoriaLook.HASH_RADIUS;
import static io.github.cwzmorro.fatecraft.client.artoria.ArtoriaLook.hash;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.fatecraft.artoria.ArtoriaConfig;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import net.minecraft.world.phys.Vec3;

/** Dense translucent spiralling wind flowing out from the sword in a cone. */
public final class StrikeAirLook {
	private static final int[] AIR = {218, 250, 255, 155};
	private static final int[] WHITE = {245, 255, 255, 145};
	// Blocks ahead of the eye where the drawn wind starts, so the camera is never inside it.
	private static final double MUZZLE = 0.8;
	private static final int STREAMS = 160, RINGS = 36, POINTS = 25;
	private static final double FLOW = 0.65, TURNS = 1.4, STREAM_LENGTH = 0.24;
	private static final double START_RADIUS = 0.12, WIDTH = 0.14, WIDTH_FADE = 0.65;
	private static final double INNER_SHARE = 0.22, RING_ARC = 0.8, RING_WIDTH = 0.10;

	private StrikeAirLook() {
	}

	static void draw(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, ArtoriaAttackRenderer.State s,
		ArtoriaConfig c, Vec3 right, Vec3 up) {
		double age = Math.min(1, s.phaseAge / Durations.ticks(c.strikeEffectSeconds()));
		double fade = Math.pow(1 - age, WIDTH_FADE);
		double endRadius = c.strikeConeLength() * Math.tan(Math.toRadians(c.strikeConeDegrees()) / 2);
		for (int stream = 0; stream < STREAMS + RINGS; stream++) {
			boolean ring = stream >= STREAMS;
			double seed = hash(stream, HASH_ANGLE);
			double start = (hash(stream, HASH_DEPTH) + age * FLOW) % 1;
			double radialShare = INNER_SHARE + (1 - INNER_SHARE) * hash(stream, HASH_RADIUS);
			Vec3[] path = new Vec3[POINTS];
			double[] widths = new double[POINTS], alpha = new double[POINTS];
			for (int i = 0; i < POINTS; i++) {
				double t = i / (double) (POINTS - 1);
				double depth = ring ? start : start + t * STREAM_LENGTH;
				double radius = (START_RADIUS + depth * endRadius) * (ring ? 1 : radialShare);
				double angle = seed * Math.TAU + age * Math.TAU + t * (ring ? Math.TAU * RING_ARC : TURNS);
				path[i] = s.direction.scale(MUZZLE + depth * (c.strikeConeLength() - MUZZLE))
					.add(right.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
				widths[i] = (ring ? RING_WIDTH : WIDTH) * (1 - Math.min(1, depth) * WIDTH_FADE);
				alpha[i] = depth > 1 ? 0 : Math.sin(t * Math.PI) * Math.pow(1 - depth, WIDTH_FADE) * fade;
			}
			LightStrokes.strip(pose, buffer, eye, path, widths, ring ? WHITE : AIR, alpha);
		}
	}
}
