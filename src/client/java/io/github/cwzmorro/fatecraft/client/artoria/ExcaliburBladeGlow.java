package io.github.cwzmorro.fatecraft.client.artoria;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.cwzmorro.fatecraft.artoria.Artoria;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import io.github.cwzmorro.fatecraft.client.effect.LightTypes;
import io.github.cwzmorro.fatecraft.client.weapon.HeldPose;
import io.github.cwzmorro.fatecraft.client.weapon.WeaponMesh;
import java.util.Arrays;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The glow of a blade gathering light (Excalibur's charge, Mana Shot's), drawn after the mesh in the item's own space, so it
 * follows the hand in either camera.
 */
public final class ExcaliburBladeGlow {
	private static final int[] CORE = {255, 250, 215, 255};
	private static final int[] GOLD = {255, 205, 90, 200};
	private static final int[] FLAME = {255, 140, 30, 150};
	private static final int SAMPLES = 24;
	// A stroke's edges fade out, so its core is wider than the blade's share of 1 to look as wide as the blade.
	private static final double CORE_WIDTH = 1.4;
	private static final double GOLD_WIDTH = 2.0;
	private static final double GOLD_ALPHA = 0.8;
	private static final double HALO_WIDTH = 2.8;
	private static final double HALO_ALPHA = 0.35;
	private static final double FLAME_WIDTH = 3.4;
	private static final double FLAME_ALPHA = 0.35;
	private static final double TIP_RUN = 0.7;
	private static final double FLICKER = 0.45;
	private static final double FLICKER_WAVES = 14;
	private static final double FLICKER_SPEED = 0.7;
	private static final double MILLIS_PER_TICK = 50;
	private static final int SLICES = 32;
	private static final double GUARD_SHARE = 0.5;
	private static @Nullable WeaponMesh measured;
	private static Blade blade = new Blade(Vec3.ZERO, Vec3.ZERO, 0);

	private ExcaliburBladeGlow() {
	}

	private record Blade(Vec3 base, Vec3 tip, double width) {
	}

	public static void register() {
		HeldPose.register((stack, time, pose) -> {
			boolean charge = Boolean.TRUE.equals(stack.get(Artoria.EXCALIBUR_CHARGE));
			if (charge) {
				pose.raise();
			}
			if (charge || Boolean.TRUE.equals(stack.get(Artoria.MANA_SHOT_CHARGE))) {
				pose.overlay(ExcaliburBladeGlow::draw);
			}
		});
	}

	public static void draw(SubmitNodeCollector collector, PoseStack poseStack, WeaponMesh mesh) {
		Blade measuredBlade = measure(mesh);
		if (measuredBlade.width() == 0) {
			return;
		}
		double ticks = Util.getMillis() / MILLIS_PER_TICK;
		LightTypes.submit(collector, poseStack, LightTypes.BLENDED, (pose, buffer) -> {
			Vec3 eye = eye(pose);
			layer(pose, buffer, eye, measuredBlade, HALO_WIDTH, FLAME, FLAME_ALPHA, ticks, FLICKER);
			layer(pose, buffer, eye, measuredBlade, HALO_WIDTH, GOLD, HALO_ALPHA, ticks, 0);
		});
		LightTypes.submit(collector, poseStack, LightTypes.ADDED, (pose, buffer) -> {
			Vec3 eye = eye(pose);
			layer(pose, buffer, eye, measuredBlade, FLAME_WIDTH, FLAME, FLAME_ALPHA, ticks, FLICKER);
			layer(pose, buffer, eye, measuredBlade, GOLD_WIDTH, GOLD, GOLD_ALPHA, ticks, 0);
			layer(pose, buffer, eye, measuredBlade, CORE_WIDTH, CORE, 1, ticks, 0);
		});
	}

	private static Vec3 eye(PoseStack.Pose pose) {
		Vector3f at = new Matrix4f(pose.pose()).invert().transformPosition(new Vector3f());
		return new Vec3(at.x, at.y, at.z);
	}

	private static void layer(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, Blade blade, double widthShare, int[] color, double alpha,
		double ticks, double flicker) {
		Vec3 along = blade.tip().subtract(blade.base());
		Vec3 end = blade.tip().add(along.scale(TIP_RUN));
		Vec3 previous = null;
		Vec3 previousSide = null;
		double previousAlpha = 0;
		for (int i = 0; i <= SAMPLES; i++) {
			double t = i / (double) SAMPLES;
			Vec3 point = blade.base().lerp(end, t);
			// Along the blade the light is full; past the tip it narrows and fades out.
			double beyond = Mth.clamp((t * (1 + TIP_RUN) - 1) / TIP_RUN, 0, 1);
			double swell = 1 + flicker * Math.sin(t * FLICKER_WAVES - ticks * FLICKER_SPEED);
			double half = blade.width() * widthShare * swell * Math.sqrt(1 - beyond);
			Vec3 side = along.cross(point.subtract(eye)).normalize().scale(half);
			double here = alpha * (1 - beyond);
			if (previous != null) {
				for (int k = 1; k < LightStrokes.ACROSS.length; k++) {
					LightStrokes.quad(pose, buffer, new Vec3[]{previous.add(previousSide.scale(LightStrokes.ACROSS[k - 1])),
							previous.add(previousSide.scale(LightStrokes.ACROSS[k])), point.add(side.scale(LightStrokes.ACROSS[k])),
							point.add(side.scale(LightStrokes.ACROSS[k - 1]))},
						new int[][]{LightStrokes.faded(color, previousAlpha * LightStrokes.ACROSS_ALPHA[k - 1]),
							LightStrokes.faded(color, previousAlpha * LightStrokes.ACROSS_ALPHA[k]),
							LightStrokes.faded(color, here * LightStrokes.ACROSS_ALPHA[k]), LightStrokes.faded(color, here * LightStrokes.ACROSS_ALPHA[k - 1])});
				}
			}
			previous = point;
			previousSide = side;
			previousAlpha = here;
		}
	}

	private static Blade measure(WeaponMesh mesh) {
		if (mesh == measured) {
			return blade;
		}
		Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
		Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
		for (WeaponMesh.Part part : mesh.parts()) {
			if (!part.emissive()) {
				continue;
			}
			float[] quads = part.quads();
			for (int i = 0; i < quads.length; i += WeaponMesh.STRIDE) {
				min.min(new Vector3f(quads[i], quads[i + 1], quads[i + 2]));
				max.max(new Vector3f(quads[i], quads[i + 1], quads[i + 2]));
			}
		}
		if (min.x > max.x) {
			measured = mesh;
			blade = measureBeyondGuard(mesh);
			return blade;
		}
		Vector3f size = max.sub(min, new Vector3f());
		Vector3f middle = min.add(max, new Vector3f()).mul(0.5F);
		int axis = size.x >= size.y && size.x >= size.z ? 0 : size.y >= size.z ? 1 : 2;
		Vector3f base = new Vector3f(middle);
		Vector3f tip = new Vector3f(middle);
		base.setComponent(axis, min.get(axis));
		tip.setComponent(axis, max.get(axis));
		float width = 0;
		for (int other = 0; other < 3; other++) {
			if (other != axis) {
				width = Math.max(width, size.get(other));
			}
		}
		measured = mesh;
		blade = new Blade(new Vec3(base.x, base.y, base.z), new Vec3(tip.x, tip.y, tip.z), width / 2);
		return blade;
	}

	private static float sliceWidth(float[][] low, float[][] high, int slice) {
		return Math.max(high[slice][0] - low[slice][0], high[slice][1] - low[slice][1]);
	}

	// A mesh without emissive parts (Caliburn) is cut into slices along its longest side; the widest slice is the guard and the blade is the longer side beyond it.
	private static Blade measureBeyondGuard(WeaponMesh mesh) {
		Vector3f min = new Vector3f(mesh.min());
		Vector3f size = mesh.max().sub(min, new Vector3f());
		int axis = size.x >= size.y && size.x >= size.z ? 0 : size.y >= size.z ? 1 : 2;
		if (size.get(axis) <= 0) {
			return new Blade(Vec3.ZERO, Vec3.ZERO, 0);
		}
		// Per slice: the lowest and highest point on each of the two axes across the blade.
		float[][] low = new float[SLICES][2];
		float[][] high = new float[SLICES][2];
		for (int slice = 0; slice < SLICES; slice++) {
			Arrays.fill(low[slice], Float.POSITIVE_INFINITY);
			Arrays.fill(high[slice], Float.NEGATIVE_INFINITY);
		}
		for (WeaponMesh.Part part : mesh.parts()) {
			float[] quads = part.quads();
			for (int i = 0; i < quads.length; i += WeaponMesh.STRIDE) {
				int slice = Mth.clamp((int) ((quads[i + axis] - min.get(axis)) / size.get(axis) * SLICES), 0, SLICES - 1);
				for (int side = 0; side < 2; side++) {
					float across = quads[i + (axis + 1 + side) % 3];
					low[slice][side] = Math.min(low[slice][side], across);
					high[slice][side] = Math.max(high[slice][side], across);
				}
			}
		}
		int guard = 0;
		float guardWidth = 0;
		for (int slice = 0; slice < SLICES; slice++) {
			float sliceWidth = sliceWidth(low, high, slice);
			if (sliceWidth > guardWidth) {
				guardWidth = sliceWidth;
				guard = slice;
			}
		}
		boolean upward = SLICES - 1 - guard >= guard;
		// The guard spans several slices: the blade starts at the first one that is clearly narrower.
		int step = upward ? 1 : -1;
		int nearest = guard + step;
		while (nearest >= 0 && nearest < SLICES && sliceWidth(low, high, nearest) > guardWidth * GUARD_SHARE) {
			nearest += step;
		}
		int first = upward ? nearest : 0;
		int last = upward ? SLICES - 1 : nearest;
		float width = 0;
		float[] bladeLow = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
		float[] bladeHigh = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
		for (int slice = first; slice <= last; slice++) {
			for (int side = 0; side < 2; side++) {
				if (low[slice][side] > high[slice][side]) {
					continue;
				}
				width = Math.max(width, high[slice][side] - low[slice][side]);
				bladeLow[side] = Math.min(bladeLow[side], low[slice][side]);
				bladeHigh[side] = Math.max(bladeHigh[side], high[slice][side]);
			}
		}
		if (width <= 0) {
			return new Blade(Vec3.ZERO, Vec3.ZERO, 0);
		}
		float sliceLength = size.get(axis) / SLICES;
		float lower = min.get(axis) + first * sliceLength;
		float upper = min.get(axis) + (last + 1) * sliceLength;
		Vector3f base = new Vector3f();
		Vector3f tip = new Vector3f();
		for (int side = 0; side < 2; side++) {
			float middle = (bladeLow[side] + bladeHigh[side]) / 2;
			base.setComponent((axis + 1 + side) % 3, middle);
			tip.setComponent((axis + 1 + side) % 3, middle);
		}
		base.setComponent(axis, upward ? lower : upper);
		tip.setComponent(axis, upward ? upper : lower);
		return new Blade(new Vec3(base.x, base.y, base.z), new Vec3(tip.x, tip.y, tip.z), width / 2);
	}
}
