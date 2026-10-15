package io.github.cwzmorro.fatecraft.client.weapon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import io.github.cwzmorro.fatecraft.client.effect.LightTypes;
import io.github.cwzmorro.fatecraft.weapon.EaDraw;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The vault opening as Ea is drawn, seen by every player in the dimension: golden light up from the raised key into a red
 * lattice that grows, pulses and sinks back. The gate and Ea rising out of it are {@link EaDrawRenderer}'s.
 */
public final class EaDrawEffect {
	// Tuning numbers for the lattice and the key's light, in blocks unless noted.
	private static final double POINT_UP = 3.0;
	private static final double HEIGHT = 110.0;
	private static final double REACH = 15.0;
	private static final int LINES = 300;
	private static final double SPACING = 1.6;
	private static final double BRANCH_GAP_SHORTEST = 0.3;
	private static final double BRANCH_GAP_LONGEST = 1.5;
	private static final double BRANCH_SHORTEST = 1.5;
	private static final double BRANCH_LONGEST = 4.0;
	private static final double BRANCH_RISE = 0.4;
	private static final double BRANCH_LEAN = 0.6;
	private static final double RAGGED_TOP = 0.15;
	private static final double LINE_HALF_WIDTH = 0.008;
	// A line is never drawn narrower than about three pixels at 1080p, so its soft edges show instead of breaking into pixels; drawn wider, it gets fainter.
	private static final double LEAST_ANGLE = 0.004;
	private static final double LEAST_LIGHT_ANGLE = 0.0015;
	private static final List<LightStrokes.Layer> RED = List.of(
		new LightStrokes.Layer(LightTypes.BLENDED_OVER, 1.4, new int[]{150, 0, 15, 110}),
		new LightStrokes.Layer(LightTypes.ADDED, 3.2, new int[]{255, 10, 30, 30}),
		new LightStrokes.Layer(LightTypes.ADDED, 1.0, new int[]{255, 35, 55, 175}));
	private static final LightStrokes.Layer RUNNING = new LightStrokes.Layer(LightTypes.ADDED, 0.5, new int[]{255, 200, 215, 230});
	private static final double RUNNING_LENGTH = 3.0;
	private static final double RUNNING_EVERY = 30.0;
	private static final double RUNNING_SPEED = 1.2;
	private static final double FEWER_LINES = 48.0;
	private static final double FEWEST_LINES = 110.0;
	private static final double PULSE_PER_TICK = 0.5;
	private static final double PULSE_DEPTH = 0.15;
	private static final double GROWING_EDGE = 4.0;
	private static final double KEY_UP = 0.45;
	private static final double KEY_RIGHT = 0.4;
	private static final double KEY_AHEAD = 0.3;
	private static final double KEY_LIGHT_HALF_WIDTH = 0.12;
	private static final int KEY_LIGHT_POINTS = 12;
	private static final double KEY_LIGHT_HIDDEN = 1.5;
	private static final double KEY_LIGHT_SHOWN = 4.0;
	private static final double FADE_TICKS = 10.0;
	private static final List<LightStrokes.Layer> GOLD = List.of(
		new LightStrokes.Layer(LightTypes.ADDED, 2.2, new int[]{255, 160, 30, 90}),
		new LightStrokes.Layer(LightTypes.ADDED, 1.0, new int[]{255, 236, 170, 230}));

	private record Line(Vec3 branch, Vec3 foot, double top, double phase) {
	}

	private record Branching(Line line, double height) {
	}

	private record Draw(Vec3 spot, Vec3 across, Vec3 depth, int drawer, long startTick) {
	}

	private static final List<Line> LATTICE = lattice(new Random(EaDraw.class.getName().hashCode()));
	private static final List<Draw> draws = new ArrayList<>();
	private static long frameTick;
	private static float partialTick;
	private static Vec3 camera = Vec3.ZERO;

	private EaDrawEffect() {
	}

	public static void register() {
		LevelRenderEvents.END_EXTRACTION.register(EaDrawEffect::extract);
		LevelRenderEvents.COLLECT_SUBMITS.register(EaDrawEffect::submit);
	}

	public static void start(Vec3 spot, float yaw, int drawer) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		Vec3 depth = Vec3.directionFromRotation(0, yaw);
		draws.add(new Draw(spot, depth.cross(Vec3.Y_AXIS), depth, drawer, minecraft.level.getGameTime()));
	}

	private static void extract(LevelExtractionContext context) {
		frameTick = context.level().getGameTime();
		partialTick = context.deltaTracker().getGameTimeDeltaPartialTick(false);
		camera = context.camera().position();
		draws.removeIf(draw -> frameTick - draw.startTick() > EaDraw.LATTICE_END);
	}

	private static List<Line> lattice(Random random) {
		List<Line> lines = new ArrayList<>();
		PriorityQueue<Branching> branchings = new PriorityQueue<>(Comparator.comparingDouble(Branching::height));
		Line first = new Line(Vec3.ZERO, Vec3.ZERO, top(random), random.nextDouble() * RUNNING_EVERY);
		lines.add(first);
		branchings.add(new Branching(first, branchGap(random)));
		while (!branchings.isEmpty() && lines.size() < LINES) {
			Branching branching = branchings.poll();
			Line from = branching.line();
			double away = from.foot().horizontalDistanceSqr() == 0 ? random.nextDouble() * Mth.TWO_PI : Math.atan2(from.foot().z, from.foot().x);
			double angle = away + random.nextGaussian() * BRANCH_LEAN;
			double out = Mth.lerp(random.nextDouble(), BRANCH_SHORTEST, BRANCH_LONGEST);
			Vec3 foot = new Vec3(from.foot().x + Math.cos(angle) * out, branching.height() + out * BRANCH_RISE,
				from.foot().z + Math.sin(angle) * out);
			if (withinReach(foot) && roomAt(lines, foot)) {
				Line line = new Line(new Vec3(from.foot().x, branching.height(), from.foot().z), foot, top(random), random.nextDouble() * RUNNING_EVERY);
				lines.add(line);
				branchings.add(new Branching(line, foot.y + branchGap(random)));
			}
			double next = branching.height() + branchGap(random);
			if (next < from.top() - BRANCH_LONGEST) {
				branchings.add(new Branching(from, next));
			}
		}
		return List.copyOf(lines);
	}

	private static double top(Random random) {
		return HEIGHT * (1 - RAGGED_TOP * random.nextDouble());
	}

	private static double branchGap(Random random) {
		return Mth.lerp(random.nextDouble(), BRANCH_GAP_SHORTEST, BRANCH_GAP_LONGEST);
	}

	private static boolean withinReach(Vec3 at) {
		double reach = Math.min(REACH, at.y);
		return Math.abs(at.x) <= reach && Math.abs(at.z) <= reach;
	}

	private static boolean roomAt(List<Line> lines, Vec3 at) {
		for (Line line : lines) {
			double x = line.foot().x - at.x;
			double z = line.foot().z - at.z;
			if (x * x + z * z < SPACING * SPACING) {
				return false;
			}
		}
		return true;
	}

	private static void submit(LevelRenderContext context) {
		PoseStack poseStack = context.poseStack();
		for (Draw draw : List.copyOf(draws)) {
			double ticks = frameTick - draw.startTick() + partialTick;
			Vec3 point = draw.spot().add(0, POINT_UP, 0).subtract(camera);
			int every = point.length() < FEWER_LINES ? 1 : point.length() < FEWEST_LINES ? 2 : 3;
			for (LightStrokes.Layer layer : RED) {
				LightTypes.submit(context.submitNodeCollector(), poseStack, layer.type(), (pose, buffer) -> drawLattice(pose, buffer, draw, point, ticks, every, layer));
			}
			LightTypes.submit(context.submitNodeCollector(), poseStack, RUNNING.type(), (pose, buffer) -> drawRunning(pose, buffer, draw, point, ticks, every));
			Vec3 key = keyAt(draw);
			if (key != null) {
				for (LightStrokes.Layer layer : GOLD) {
					LightTypes.submit(context.submitNodeCollector(), poseStack, layer.type(), (pose, buffer) -> drawKeyLight(pose, buffer, key, point, ticks, layer));
				}
			}
		}
	}

	private static @Nullable Vec3 keyAt(Draw draw) {
		ClientLevel level = Minecraft.getInstance().level;
		Entity drawer = level == null ? null : level.getEntity(draw.drawer());
		if (drawer == null) {
			return null;
		}
		Vec3 look = drawer.getViewVector(partialTick);
		Vec3 right = look.cross(Vec3.Y_AXIS).normalize();
		return drawer.getEyePosition(partialTick).add(0, KEY_UP, 0).add(right.scale(KEY_RIGHT)).add(look.scale(KEY_AHEAD)).subtract(camera);
	}

	private static double reach(double ticks) {
		double grown = Math.clamp((ticks - EaDraw.LATTICE_START) / (EaDraw.LATTICE_GROWN - EaDraw.LATTICE_START), 0.0, 1.0);
		double sunk = Math.clamp((ticks - EaDraw.LATTICE_COLLAPSE) / (EaDraw.LATTICE_END - EaDraw.LATTICE_COLLAPSE), 0.0, 1.0);
		return HEIGHT * grown * (1 - sunk);
	}

	private static void drawLattice(PoseStack.Pose pose, VertexConsumer buffer, Draw draw, Vec3 point, double ticks, int every, LightStrokes.Layer layer) {
		double reach = reach(ticks);
		double pulse = 1 - PULSE_DEPTH * (0.5 + 0.5 * Math.sin(ticks * PULSE_PER_TICK));
		for (int i = 0; i < LATTICE.size(); i += every) {
			Line line = LATTICE.get(i);
			if (line.branch().y >= reach) {
				continue;
			}
			Vec3 foot = line.foot().y <= reach ? line.foot() : line.branch().lerp(line.foot(), (reach - line.branch().y) / (line.foot().y - line.branch().y));
			double top = Math.min(line.top(), reach);
			boolean cut = top < line.top();
			// Shown in full at the foot once the line has grown past it by the soft edge.
			double footShown = cut ? Math.clamp((top - foot.y) / GROWING_EDGE, 0.0, 1.0) : 1.0;
			if (!line.branch().equals(foot)) {
				stroke(pose, buffer, draw, point, new Vec3[]{line.branch(), foot}, new double[]{1.0, footShown}, pulse, layer);
			}
			if (top > foot.y) {
				Vec3 upTo = new Vec3(foot.x, top, foot.z);
				if (cut && top - foot.y > GROWING_EDGE) {
					stroke(pose, buffer, draw, point, new Vec3[]{foot, new Vec3(foot.x, top - GROWING_EDGE, foot.z), upTo}, new double[]{1.0, 1.0, 0.0}, pulse,
						layer);
				} else {
					stroke(pose, buffer, draw, point, new Vec3[]{foot, upTo}, new double[]{footShown, cut ? 0.0 : 1.0}, pulse, layer);
				}
			}
		}
	}

	private static void drawRunning(PoseStack.Pose pose, VertexConsumer buffer, Draw draw, Vec3 point, double ticks, int every) {
		double reach = reach(ticks);
		for (int i = 0; i < LATTICE.size(); i += every) {
			Line line = LATTICE.get(i);
			double top = Math.min(line.top(), reach);
			double first = line.foot().y + (line.phase() + ticks * RUNNING_SPEED) % RUNNING_EVERY;
			for (double head = first; head - RUNNING_LENGTH < top; head += RUNNING_EVERY) {
				double from = Math.max(head - RUNNING_LENGTH, line.foot().y);
				double to = Math.min(head, top);
				if (to > from) {
					stroke(pose, buffer, draw, point, new Vec3[]{new Vec3(line.foot().x, from, line.foot().z), new Vec3(line.foot().x, to, line.foot().z)},
						new double[]{0.0, 1.0}, 1.0, RUNNING);
				}
			}
		}
	}

	private static void stroke(PoseStack.Pose pose, VertexConsumer buffer, Draw draw, Vec3 point, Vec3[] local, double[] shown, double pulse,
		LightStrokes.Layer layer) {
		Vec3[] points = new Vec3[local.length];
		double[] halfWidths = new double[local.length];
		double[] alphas = new double[local.length];
		for (int i = 0; i < local.length; i++) {
			points[i] = point.add(draw.across().scale(local[i].x)).add(0, local[i].y, 0).add(draw.depth().scale(local[i].z));
			double drawn = Math.max(LINE_HALF_WIDTH, points[i].length() * LEAST_ANGLE);
			double lit = Math.max(LINE_HALF_WIDTH, points[i].length() * LEAST_LIGHT_ANGLE);
			halfWidths[i] = drawn * layer.widthShare();
			alphas[i] = shown[i] * pulse * lit / drawn;
		}
		LightStrokes.strip(pose, buffer, Vec3.ZERO, points, halfWidths, layer.rgba(), alphas);
	}

	private static void drawKeyLight(PoseStack.Pose pose, VertexConsumer buffer, Vec3 key, Vec3 point, double ticks, LightStrokes.Layer layer) {
		double shown = Math.clamp(ticks / FADE_TICKS, 0.0, 1.0) * Math.clamp((EaDraw.KEY_LIGHT_END - ticks) / FADE_TICKS, 0.0, 1.0);
		if (shown <= 0) {
			return;
		}
		Vec3[] points = new Vec3[KEY_LIGHT_POINTS];
		double[] halfWidths = new double[KEY_LIGHT_POINTS];
		double[] alphas = new double[KEY_LIGHT_POINTS];
		for (int i = 0; i < KEY_LIGHT_POINTS; i++) {
			points[i] = key.lerp(point, i / (double) (KEY_LIGHT_POINTS - 1));
			halfWidths[i] = KEY_LIGHT_HALF_WIDTH * layer.widthShare();
			alphas[i] = shown * Math.clamp((points[i].length() - KEY_LIGHT_HIDDEN) / (KEY_LIGHT_SHOWN - KEY_LIGHT_HIDDEN), 0.0, 1.0);
		}
		LightStrokes.strip(pose, buffer, Vec3.ZERO, points, halfWidths, layer.rgba(), alphas);
	}
}
