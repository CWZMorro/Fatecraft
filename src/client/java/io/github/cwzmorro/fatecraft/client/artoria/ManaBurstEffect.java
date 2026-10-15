package io.github.cwzmorro.fatecraft.client.artoria;

import io.github.cwzmorro.fatecraft.GoldenRatio;
import io.github.cwzmorro.fatecraft.artoria.ManaBurst;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import io.github.cwzmorro.fatecraft.client.effect.LightTypes;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Mana Burst's soft blue mana streaming off her body while the buff lasts. */
public final class ManaBurstEffect {
	private static final int BODY_WISPS = 12;
	private static final int POINTS = 13;
	private static final double RISE_TICKS = 22;
	private static final double BODY_RADIUS = 0.42;
	private static final double OUTWARD_DRIFT = 0.18;
	private static final double WISP_LENGTH = 0.65;
	private static final double WISP_WIDTH = 0.13;
	private static final double SWAY = 0.06;
	private static final double TURN_SPEED = 0.018;
	private static final double FLICKER_SPEED = 0.3;
	private static final double FLICKER_BASE = 0.65;
	private static final double FLICKER_RANGE = 0.2;
	private static final double HALO_WIDTH = 2.4;
	private static final int[] BLUE = {90, 190, 255, 125};
	private static final int[] HALO = {45, 135, 255, 35};
	private static List<Wisp> wisps = List.of();

	private ManaBurstEffect() {
	}

	public static void register() {
		LevelRenderEvents.END_EXTRACTION.register(context -> {
			List<Wisp> frame = new ArrayList<>();
			float partial = context.deltaTracker().getGameTimeDeltaPartialTick(false);
			double ticks = context.level().getGameTime() + partial;
			for (Player player : context.level().players()) {
				if (!ManaBurst.active(player)) {
					continue;
				}
				Vec3 body = player.getPosition(partial).subtract(context.camera().position());
				// Entity-specific phase keeps different casters from flickering in unison.
				double clock = ticks + player.getId();
				for (int lane = 0; lane < BODY_WISPS; lane++) {
					frame.add(bodyWisp(body, player.getBbHeight(), lane, clock));
				}
			}
			wisps = List.copyOf(frame);
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			List<Wisp> frame = wisps;
			if (frame.isEmpty()) {
				return;
			}
			LightTypes.submit(context.submitNodeCollector(), context.poseStack(), LightTypes.BLENDED_OVER, (pose, buffer) -> {
				for (Wisp wisp : frame) {
					double[] halo = new double[POINTS];
					for (int i = 0; i < POINTS; i++) {
						halo[i] = wisp.widths()[i] * HALO_WIDTH;
					}
					LightStrokes.strip(pose, buffer, Vec3.ZERO, wisp.points(), halo, HALO, wisp.alpha());
					LightStrokes.strip(pose, buffer, Vec3.ZERO, wisp.points(), wisp.widths(), BLUE, wisp.alpha());
				}
			});
		});
	}

	private static Wisp bodyWisp(Vec3 body, double height, int lane, double ticks) {
		Vec3[] points = new Vec3[POINTS];
		double[] widths = new double[POINTS];
		double[] alpha = new double[POINTS];
		double phase = (ticks / RISE_TICKS + lane * GoldenRatio.SHARE) % 1;
		double angle = Math.TAU * lane / BODY_WISPS + ticks * TURN_SPEED;
		double bottom = phase * (height + WISP_LENGTH) - WISP_LENGTH;
		double flicker = FLICKER_BASE + FLICKER_RANGE * Math.sin(ticks * FLICKER_SPEED + lane);
		for (int i = 0; i < POINTS; i++) {
			double t = i / (double) (POINTS - 1);
			double y = bottom + t * WISP_LENGTH;
			double radius = BODY_RADIUS + t * OUTWARD_DRIFT;
			double turn = angle + SWAY * Math.sin(t * Math.TAU + lane);
			points[i] = body.add(Math.cos(turn) * radius, y, Math.sin(turn) * radius);
			widths[i] = WISP_WIDTH * Math.sin(t * Math.PI);
			alpha[i] = y < 0 || y > height ? 0 : Math.sin(t * Math.PI) * Math.sin(phase * Math.PI) * flicker;
		}
		return new Wisp(points, widths, alpha);
	}

	private record Wisp(Vec3[] points, double[] widths, double[] alpha) {
	}
}
