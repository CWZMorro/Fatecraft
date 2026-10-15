package io.github.cwzmorro.fatecraft.client.artoria;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.cwzmorro.fatecraft.GoldenRatio;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import net.minecraft.world.phys.Vec3;

/** The random channels and the star glint Artoria's attacks share. */
final class ArtoriaLook {
	// Separate hash channels keep angle, radius and depth unrelated and steady from frame to frame.
	static final int HASH_ANGLE = 1, HASH_RADIUS = 5, HASH_DEPTH = 7;
	private static final long HASH_CHANNEL = 0xD1B54A32D192ED03L;
	private static final long HASH_MIX = 0xBF58476D1CE4E5B9L;
	private static final int HASH_SHIFT = 30, HASH_BITS = 53, HASH_DISCARD = 11;

	private ArtoriaLook() {
	}

	static double hash(int index, int channel) {
		long bits = (index + 1L) * GoldenRatio.HASH_STEP ^ channel * HASH_CHANNEL;
		bits = (bits ^ (bits >>> HASH_SHIFT)) * HASH_MIX;
		bits ^= bits >>> HASH_SHIFT;
		return (bits >>> HASH_DISCARD) * Math.scalb(1.0, -HASH_BITS);
	}

	static void star(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, Vec3 at, Vec3 u, Vec3 v, double size, double width, int[] color) {
		LightStrokes.line(pose, buffer, eye, at.subtract(u.scale(size)), at.add(u.scale(size)), width, color);
		LightStrokes.line(pose, buffer, eye, at.subtract(v.scale(size)), at.add(v.scale(size)), width, color);
	}
}
