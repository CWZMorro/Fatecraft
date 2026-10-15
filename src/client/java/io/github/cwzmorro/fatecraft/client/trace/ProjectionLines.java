package io.github.cwzmorro.fatecraft.client.trace;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import io.github.cwzmorro.fatecraft.client.effect.LightTypes;
import io.github.cwzmorro.fatecraft.client.gate.TreasureDrawing;
import io.github.cwzmorro.fatecraft.client.gate.TreasureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A projected sword forming in the air (Trace On): soft blue lines trace its outline from hilt to tip over the first part
 * of its formation, then the sword fills in and the lines fade out.
 */
public final class ProjectionLines implements TreasureRenderer.FormingLook {
	private static final int[] PROJECTION_BLUE = {70, 190, 255, 230};
	private static final double PROJECTION_WIDTH = 0.012;
	private static final double PROJECTION_HALO = 3;
	// The lines trace the sword over this first share of its formation and the sword fills in over the rest; the lines fade out from this share on.
	private static final double TRACE_SHARE = 0.5;
	private static final double LINES_FADE_FROM = 0.75;

	@Override
	public float fill(float progress) {
		return (float) Math.max(0, (progress - TRACE_SHARE) / (1 - TRACE_SHARE));
	}

	@Override
	public void submit(PoseStack poses, SubmitNodeCollector collector, TreasureDrawing.Look look, Vec3 facing, Vec3 eye, double scale, float progress) {
		double traced = Math.min(1, progress / TRACE_SHARE);
		double alpha = 1 - Mth.smoothstep(Math.max(0, (progress - LINES_FADE_FROM) / (1 - LINES_FADE_FROM)));
		for (TreasureDrawing.OutlineLine line : TreasureDrawing.outline(look, facing, eye, scale)) {
			double a = line.fromShare();
			double b = line.toShare();
			if (Math.min(a, b) > traced) {
				continue;
			}
			Vec3 start = line.from();
			Vec3 end = line.to();
			if (a > traced) {
				start = end.lerp(start, (traced - b) / (a - b));
			} else if (b > traced) {
				end = start.lerp(end, (traced - a) / (b - a));
			}
			Vec3 from = start;
			Vec3 to = end;
			collector.submitCustomGeometry(poses, LightTypes.ADDED, (pose, buffer) -> {
				LightStrokes.strip(pose, buffer, eye, new Vec3[]{from, to},
					new double[]{PROJECTION_WIDTH * scale, PROJECTION_WIDTH * scale}, PROJECTION_BLUE, new double[]{alpha, alpha});
				LightStrokes.strip(pose, buffer, eye, new Vec3[]{from, to},
					new double[]{PROJECTION_WIDTH * PROJECTION_HALO * scale, PROJECTION_WIDTH * PROJECTION_HALO * scale},
					PROJECTION_BLUE, new double[]{alpha / PROJECTION_HALO, alpha / PROJECTION_HALO});
			});
		}
	}
}
