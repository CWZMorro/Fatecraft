package io.github.cwzmorro.fatecraft.client.weapon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import io.github.cwzmorro.fatecraft.client.effect.LightTypes;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * Ea's wind, shared by compressed air and Enuma Elish, which is the same wind gathered and let loose: slashes of red
 * light, pointed at both ends and widest in the middle, and the thin pale bolts among them.
 */
final class WindLight {
	// A wide red glow and a thin white-pink core are added, so it burns at night; a dark red is blended over them, so it stays deep red against a bright sky.
	static final List<LightStrokes.Layer> RED_LIGHT = List.of(
		new LightStrokes.Layer(LightTypes.BLENDED_OVER, 1.0, new int[]{140, 0, 10, 120}),
		new LightStrokes.Layer(LightTypes.ADDED, 1.0, new int[]{255, 20, 35, 150}),
		new LightStrokes.Layer(LightTypes.ADDED, 0.3, new int[]{255, 215, 225, 255}));
	static final int[] BOLT = {255, 175, 190, 220};

	private WindLight() {
	}

	static void slash(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, Vec3[] points, double[] halfWidths, LightStrokes.Layer layer, double fade) {
		double[] tapered = new double[points.length];
		for (int i = 0; i < points.length; i++) {
			tapered[i] = halfWidths[i] * layer.widthShare() * Math.sin(Math.PI * i / (points.length - 1));
		}
		LightStrokes.strip(pose, buffer, camera, points, tapered, LightStrokes.faded(layer.rgba(), fade));
	}
}
