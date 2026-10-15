package io.github.cwzmorro.fatecraft.client.artoria;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.fatecraft.artoria.ArtoriaAttackEntity;
import io.github.cwzmorro.fatecraft.artoria.ArtoriaConfig;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import net.minecraft.world.phys.Vec3;

/** Mana Slash's white-hot sweep with gold edges, fading where it ends. */
public final class ManaSlashLook {
	private static final int POINTS = 65, RADIAL_STEPS = 16;
	private static final int[] GOLD = {255, 193, 28, 245}, HOT = {255, 253, 225, 255};
	// The leading edge and the softer gold sector joining it to the blade are sized for the default reach.
	private static final double REFERENCE_REACH = ArtoriaConfig.DEFAULTS.slashRange();
	private static final double BAND_WIDTH = 1.5, BLADE_ROOT = 0.55, SECTOR_ALPHA = 0.65;
	private static final double CHEST_DROP = 0.4, SWING_TILT = 0.15;
	private static final double HALO_HALF_WIDTH = 0.75, GOLD_HALF_WIDTH = 0.58, HOT_HALF_WIDTH = 0.32;
	private static final double TAIL_WIDTH = 0.18, TAIL_ALPHA = 0.45, HEAD_CAP_SHARE = 0.08;
	// Only brightness fades: the completed slash neither rises nor shrinks.
	private static final double FADE_POWER = 0.65;

	private ManaSlashLook() {
	}

	static void draw(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, ArtoriaAttackRenderer.State s, ArtoriaConfig c) {
		boolean fading = s.phase == ArtoriaAttackEntity.Phase.BLAST;
		double progress = fading ? 1 : Math.min(1, s.phaseAge / Durations.ticks(c.slashSweepSeconds()));
		double age = fading ? Math.min(1, s.phaseAge / Durations.ticks(c.slashFadeSeconds())) : 0;
		double fade = Math.pow(1 - age, FADE_POWER);
		double arc = Math.toRadians(c.slashArcDegrees());
		double scale = Math.min(1, c.slashRange() / REFERENCE_REACH);
		Vec3 forward = new Vec3(s.direction.x, 0, s.direction.z).normalize();
		Vec3 across = LightStrokes.perpendicular(forward);
		Vec3[] centres = new Vec3[POINTS];
		double[] halo = new double[POINTS], gold = new double[POINTS], hot = new double[POINTS], alpha = new double[POINTS];
		for (int i = 0; i < POINTS; i++) {
			double t = i / (double) (POINTS - 1);
			double swept = progress * t;
			double taper = taper(t);
			Vec3 radial = radial(forward, across, arc, swept);
			Vec3 centre = edge(forward, across, arc, swept, c.slashRange()).subtract(radial.scale(BAND_WIDTH / 2 * scale * taper));
			centres[i] = centre;
			halo[i] = HALO_HALF_WIDTH * scale * taper;
			gold[i] = GOLD_HALF_WIDTH * scale * taper;
			hot[i] = HOT_HALF_WIDTH * scale * taper;
			alpha[i] = (TAIL_ALPHA + (1 - TAIL_ALPHA) * t) * fade * Math.min(1, taper / TAIL_WIDTH);
			if (i > 0) {
				double previousSwept = progress * (i - 1) / (POINTS - 1);
				Vec3 previousRadial = radial(forward, across, arc, previousSwept);
				Vec3 previousEdge = edge(forward, across, arc, previousSwept, c.slashRange());
				Vec3 hereEdge = edge(forward, across, arc, swept, c.slashRange());
				for (int step = 1; step <= RADIAL_STEPS; step++) {
					double before = (step - 1) / (double) RADIAL_STEPS;
					double here = step / (double) RADIAL_STEPS;
					double beforeRadius = BLADE_ROOT + (c.slashRange() - BLADE_ROOT) * before;
					double hereRadius = BLADE_ROOT + (c.slashRange() - BLADE_ROOT) * here;
					LightStrokes.quad(pose, buffer, new Vec3[]{
						previousEdge.subtract(previousRadial.scale(c.slashRange() - beforeRadius)),
						previousEdge.subtract(previousRadial.scale(c.slashRange() - hereRadius)),
						hereEdge.subtract(radial.scale(c.slashRange() - hereRadius)),
						hereEdge.subtract(radial.scale(c.slashRange() - beforeRadius))},
						new int[][]{LightStrokes.faded(GOLD, alpha[i - 1] * SECTOR_ALPHA * Math.sin(before * Math.PI)),
							LightStrokes.faded(GOLD, alpha[i - 1] * SECTOR_ALPHA * Math.sin(here * Math.PI)),
							LightStrokes.faded(GOLD, alpha[i] * SECTOR_ALPHA * Math.sin(here * Math.PI)),
							LightStrokes.faded(GOLD, alpha[i] * SECTOR_ALPHA * Math.sin(before * Math.PI))});
				}
				for (int band = 1; band < LightStrokes.ACROSS.length; band++) {
					LightStrokes.quad(pose, buffer, new Vec3[]{
						centres[i - 1].add(previousRadial.scale(halo[i - 1] * LightStrokes.ACROSS[band - 1])),
						centres[i - 1].add(previousRadial.scale(halo[i - 1] * LightStrokes.ACROSS[band])),
						centre.add(radial.scale(halo[i] * LightStrokes.ACROSS[band])),
						centre.add(radial.scale(halo[i] * LightStrokes.ACROSS[band - 1]))},
						new int[][]{LightStrokes.faded(GOLD, alpha[i - 1] * LightStrokes.ACROSS_ALPHA[band - 1]),
							LightStrokes.faded(GOLD, alpha[i - 1] * LightStrokes.ACROSS_ALPHA[band]),
							LightStrokes.faded(GOLD, alpha[i] * LightStrokes.ACROSS_ALPHA[band]),
							LightStrokes.faded(GOLD, alpha[i] * LightStrokes.ACROSS_ALPHA[band - 1])});
				}
			}
		}
		LightStrokes.strip(pose, buffer, eye, centres, halo, GOLD, alpha);
		LightStrokes.strip(pose, buffer, eye, centres, gold, GOLD, alpha);
		LightStrokes.strip(pose, buffer, eye, centres, hot, HOT, alpha);
	}

	private static double taper(double t) {
		return (TAIL_WIDTH + (1 - TAIL_WIDTH) * Math.sqrt(t))
			* Math.sin(Math.PI / 2 * Math.min(1, (1 - t) / HEAD_CAP_SHARE))
			* Math.min(1, t / HEAD_CAP_SHARE);
	}

	private static Vec3 radial(Vec3 forward, Vec3 across, double arc, double swept) {
		double angle = (swept - 0.5) * arc;
		return across.scale(Math.sin(angle)).add(forward.scale(Math.cos(angle)));
	}

	private static Vec3 edge(Vec3 forward, Vec3 across, double arc, double swept, double reach) {
		double angle = (swept - 0.5) * arc;
		return radial(forward, across, arc, swept).scale(reach)
			.add(0, -CHEST_DROP - Math.sin(angle) * SWING_TILT, 0);
	}
}
