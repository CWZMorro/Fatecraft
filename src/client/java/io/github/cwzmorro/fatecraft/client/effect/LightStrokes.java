package io.github.cwzmorro.fatecraft.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Strokes of light for the weapons' and gates' effects: flat strips turned to face the camera, soft at their edges. Strokes
 * closer to the camera than {@link #NEAREST_TO_CAMERA} are left out: up close they would fill the screen.
 */
public final class LightStrokes {
	private static final double NEAREST_TO_CAMERA = 1.0;
	// An axis whose up component is above this is crossed with east instead of up, which it nearly runs along.
	private static final double MOSTLY_VERTICAL = 0.9;
	// Strokes are full along the middle and nothing at the edges, so the edges blend away like a soft-edged sprite's.
	public static final double[] ACROSS = {-1.0, -0.5, 0.0, 0.5, 1.0};
	public static final double[] ACROSS_ALPHA = {0.0, 0.6, 1.0, 0.6, 0.0};
	private static final double LEAST_HALF_ANGLE = 0.002;
	private static final double FACING_FALLOFF = 2.0;
	private static final double HASH_LANE = 12.9898;
	private static final double HASH_SALT = 78.233;
	private static final double HASH_SCALE = 43758.5453;

	public record Layer(RenderType type, double widthShare, int[] rgba) {
	}

	private LightStrokes() {
	}

	// Stable from frame to frame: a repeatable pseudo-random number from 0 to 1 for a lane, different for each salt.
	public static double hash(int lane, int salt) {
		return Mth.frac(Math.sin(lane * HASH_LANE + salt * HASH_SALT) * HASH_SCALE);
	}

	public static void strip(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, Vec3[] points, double[] halfWidths, int[] rgba) {
		strip(pose, buffer, camera, points, halfWidths, rgba, null);
	}

	public static void strip(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, Vec3[] points, double[] halfWidths, int[] rgba,
		double @Nullable [] alphaShares) {
		Vec3 previousSide = null;
		for (int i = 0; i < points.length; i++) {
			Vec3 along = points[Math.min(i + 1, points.length - 1)].subtract(points[Math.max(i - 1, 0)]);
			Vec3 across = along.cross(points[i].subtract(camera));
			double halfWidth = Math.max(halfWidths[i], points[i].distanceTo(camera) * LEAST_HALF_ANGLE);
			Vec3 side = across.lengthSqr() == 0 ? Vec3.ZERO : across.normalize().scale(halfWidth);
			if (previousSide != null && distance(camera, points[i - 1], points[i]) >= NEAREST_TO_CAMERA) {
				double before = alphaShares == null ? 1.0 : alphaShares[i - 1];
				double here = alphaShares == null ? 1.0 : alphaShares[i];
				for (int k = 1; k < ACROSS.length; k++) {
					quad(pose, buffer, new Vec3[]{
							points[i - 1].add(previousSide.scale(ACROSS[k - 1])), points[i - 1].add(previousSide.scale(ACROSS[k])),
							points[i].add(side.scale(ACROSS[k])), points[i].add(side.scale(ACROSS[k - 1]))},
						new int[][]{faded(rgba, before * ACROSS_ALPHA[k - 1]), faded(rgba, before * ACROSS_ALPHA[k]),
							faded(rgba, here * ACROSS_ALPHA[k]), faded(rgba, here * ACROSS_ALPHA[k - 1])});
				}
			}
			previousSide = side;
		}
	}

	public static void tube(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, Vec3[] centres, double[] radii, Vec3 u, Vec3 v, int sides, int[] rgba) {
		tube(pose, buffer, camera, centres, radii, u, v, sides, rgba, null);
	}

	public static void tube(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, Vec3[] centres, double[] radii,
		Vec3 u, Vec3 v, int sides, int[] rgba, double @Nullable [] alphaShares) {
		Vec3 axis = centres[centres.length - 1].subtract(centres[0]).normalize();
		for (int i = 1; i < centres.length; i++) {
			for (int side = 0; side < sides; side++) {
				double a0 = Mth.TWO_PI * side / sides;
				double a1 = Mth.TWO_PI * (side + 1) / sides;
				Vec3[] corners = {round(centres[i - 1], radii[i - 1], u, v, a0), round(centres[i - 1], radii[i - 1], u, v, a1),
					round(centres[i], radii[i], u, v, a1), round(centres[i], radii[i], u, v, a0)};
				Vec3[] centresOf = {centres[i - 1], centres[i - 1], centres[i], centres[i]};
				int[][] colours = new int[corners.length][];
				for (int k = 0; k < corners.length; k++) {
					double along = alphaShares == null ? 1 : alphaShares[k < 2 ? i - 1 : i];
					colours[k] = faded(rgba, along * facing(camera, axis, centresOf[k], corners[k]));
				}
				quad(pose, buffer, corners, colours);
			}
		}
	}

	private static double facing(Vec3 camera, Vec3 axis, Vec3 centre, Vec3 at) {
		Vec3 out = at.subtract(centre);
		Vec3 view = at.subtract(camera);
		Vec3 viewRound = view.subtract(axis.scale(view.dot(axis)));
		return out.lengthSqr() == 0 || viewRound.lengthSqr() == 0 ? 1.0 : Math.pow(Math.abs(out.normalize().dot(viewRound.normalize())), FACING_FALLOFF);
	}

	public static Vec3 round(Vec3 centre, double radius, Vec3 u, Vec3 v, double angle) {
		return centre.add(u.scale(Math.cos(angle) * radius)).add(v.scale(Math.sin(angle) * radius));
	}

	public static void band(PoseStack.Pose pose, VertexConsumer buffer, Vec3[] points, Vec3 side, double[] halfWidths, int[] rgba, double[] alphaShares) {
		for (int i = 1; i < points.length; i++) {
			Vec3 before = side.scale(halfWidths[i - 1]);
			Vec3 here = side.scale(halfWidths[i]);
			for (int k = 1; k < ACROSS.length; k++) {
				quad(pose, buffer, new Vec3[]{
						points[i - 1].add(before.scale(ACROSS[k - 1])), points[i - 1].add(before.scale(ACROSS[k])),
						points[i].add(here.scale(ACROSS[k])), points[i].add(here.scale(ACROSS[k - 1]))},
					new int[][]{faded(rgba, alphaShares[i - 1] * ACROSS_ALPHA[k - 1]), faded(rgba, alphaShares[i - 1] * ACROSS_ALPHA[k]),
						faded(rgba, alphaShares[i] * ACROSS_ALPHA[k]), faded(rgba, alphaShares[i] * ACROSS_ALPHA[k - 1])});
			}
		}
	}

	public static void line(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, Vec3 a, Vec3 b, double halfWidth, int[] rgba) {
		strip(pose, buffer, camera, new Vec3[]{a, b}, new double[]{halfWidth, halfWidth}, rgba);
	}

	// Unlike strip it allocates nothing and is drawn however near the camera, for the many short strokes of one effect.
	public static void stroke(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, Vec3 start, Vec3 end,
		double startWidth, double endWidth, double startAlpha, double endAlpha, int[] rgba) {
		double dx = end.x - start.x;
		double dy = end.y - start.y;
		double dz = end.z - start.z;
		double sx = dy * (start.z - eye.z) - dz * (start.y - eye.y);
		double sy = dz * (start.x - eye.x) - dx * (start.z - eye.z);
		double sz = dx * (start.y - eye.y) - dy * (start.x - eye.x);
		double length = Math.sqrt(sx * sx + sy * sy + sz * sz);
		if (length == 0) {
			return;
		}
		sx /= length;
		sy /= length;
		sz /= length;
		startWidth = Math.max(startWidth, start.distanceTo(eye) * LEAST_HALF_ANGLE);
		endWidth = Math.max(endWidth, end.distanceTo(eye) * LEAST_HALF_ANGLE);
		for (int k = 1; k < ACROSS.length; k++) {
			strokeVertex(pose, buffer, start, sx, sy, sz, startWidth * ACROSS[k - 1], rgba, startAlpha * ACROSS_ALPHA[k - 1]);
			strokeVertex(pose, buffer, start, sx, sy, sz, startWidth * ACROSS[k], rgba, startAlpha * ACROSS_ALPHA[k]);
			strokeVertex(pose, buffer, end, sx, sy, sz, endWidth * ACROSS[k], rgba, endAlpha * ACROSS_ALPHA[k]);
			strokeVertex(pose, buffer, end, sx, sy, sz, endWidth * ACROSS[k - 1], rgba, endAlpha * ACROSS_ALPHA[k - 1]);
		}
	}

	public static Vec3 perpendicular(Vec3 axis) {
		return axis.cross(Math.abs(axis.y) < MOSTLY_VERTICAL ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
	}

	public static int[] faded(int[] rgba, double share) {
		return new int[]{rgba[0], rgba[1], rgba[2], alpha(rgba, share)};
	}

	public static int[] mix(int[] a, int[] b, double t) {
		int[] out = new int[a.length];
		for (int k = 0; k < a.length; k++) {
			out[k] = (int) Math.round(Mth.lerp(t, a[k], b[k]));
		}
		return out;
	}

	private static int alpha(int[] rgba, double share) {
		return (int) (rgba[3] * Mth.clamp(share, 0.0, 1.0));
	}

	public static void quad(PoseStack.Pose pose, VertexConsumer buffer, Vec3[] corners, int[][] colours) {
		for (int k = 0; k < corners.length; k++) {
			vertex(pose, buffer, corners[k], colours[k]);
		}
	}

	public static void planeVertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 centre, Vec3 u, Vec3 v, double radius, double cos, double sin, int[] rgba,
		double share) {
		buffer.addVertex(pose, (float) (centre.x + (u.x * cos + v.x * sin) * radius), (float) (centre.y + (u.y * cos + v.y * sin) * radius),
			(float) (centre.z + (u.z * cos + v.z * sin) * radius)).setColor(rgba[0], rgba[1], rgba[2], alpha(rgba, share));
	}

	public static void disc(PoseStack.Pose pose, VertexConsumer buffer, Vec3 centre, Vec3 u, Vec3 v, double radius, int[] rgba, double alpha, int segments) {
		for (int i = 0; i < segments; i++) {
			double a = i * Mth.TWO_PI / segments;
			double b = (i + 1) * Mth.TWO_PI / segments;
			planeVertex(pose, buffer, centre, u, v, 0, 0, 0, rgba, alpha);
			planeVertex(pose, buffer, centre, u, v, radius, Math.cos(a), Math.sin(a), rgba, 0);
			planeVertex(pose, buffer, centre, u, v, radius, Math.cos(b), Math.sin(b), rgba, 0);
			planeVertex(pose, buffer, centre, u, v, 0, 0, 0, rgba, alpha);
		}
	}

	public static void facingDisc(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, Vec3 centre, double radius, int[] rgba, double alpha, int segments) {
		Vec3 facing = eye.subtract(centre).normalize();
		Vec3 u = perpendicular(facing);
		disc(pose, buffer, centre, u, facing.cross(u), radius, rgba, alpha, segments);
	}

	private static double distance(Vec3 point, Vec3 a, Vec3 b) {
		Vec3 path = b.subtract(a);
		Vec3 start = a.subtract(point);
		double t = path.lengthSqr() == 0 ? 0.0 : Mth.clamp(-start.dot(path) / path.lengthSqr(), 0.0, 1.0);
		return start.add(path.scale(t)).length();
	}

	private static void strokeVertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 at, double sx, double sy, double sz,
		double across, int[] rgba, double alpha) {
		buffer.addVertex(pose, (float) (at.x + sx * across), (float) (at.y + sy * across), (float) (at.z + sz * across))
			.setColor(rgba[0], rgba[1], rgba[2], (int) (rgba[3] * alpha));
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 at, int[] rgba) {
		buffer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(rgba[0], rgba[1], rgba[2], rgba[3]);
	}
}
