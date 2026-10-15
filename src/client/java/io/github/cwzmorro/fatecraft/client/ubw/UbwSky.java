package io.github.cwzmorro.fatecraft.client.ubw;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The cloud-shaded boundary of the marble's voxel shell, drawn without an opaque block model so the sky shows. */
final class UbwSky {
	private record Vertex(Vec3 point, int red, int green, int blue) { }

	private static final int BAND_RINGS = 48;
	private static final int BAND_SEGMENTS = 96;
	static final double BAND_INSET = 1.0;
	private static final int SHRED_RINGS = 3;
	private static final int SHRED_SEGMENTS = 4;
	private static final int SHRED_SALT = 11;
	static final double FULL = 255;
	private static final int FACE_VERTICES = 4;
	private static final double HALF_FACE = 0.5;
	private static final double CORNER_JITTER = 0.65;
	private static final double EDGE_WIDTH = 0.055;
	private static final int[] EDGE = {255, 112, 60, 190};
	private static int madeRadius;
	private static List<Vertex[]> boundary = List.of();
	private static List<Vertex[]> band = List.of();
	private static List<Vertex[]> fragments = List.of();

	private UbwSky() { }

	// During the opening a smooth sphere stands in for the voxel shell, whose stepped faces would blend into a staircase.
	static void draw(PoseStack.Pose pose, VertexConsumer buffer, int radius, UbwReveal reveal, @Nullable UbwCrumble crumble) {
		if (madeRadius != radius) {
			boundary = boundary(radius);
			band = band(radius - BAND_INSET);
			fragments = fragments(radius - BAND_INSET);
			madeRadius = radius;
		}
		double[] shares = new double[FACE_VERTICES];
		List<Vertex[]> faces = crumble != null ? fragments : reveal != UbwReveal.WHOLE ? band : boundary;
		for (int index = 0; index < faces.size(); index++) {
			Vertex[] face = faces.get(index);
			// Each broken pane falls on its own; the shared shreds are only for the smooth band.
			int anchor = crumble != null ? index : anchor(index);
			Vec3 middle = crumble == null ? Vec3.ZERO : centre(faces.get(anchor));
			double broken = crumble == null ? 0 : crumble.broken(middle, LightStrokes.hash(anchor, SHRED_SALT));
			double left = UbwCrumble.shown(broken);
			boolean shown = false;
			for (int i = 0; i < face.length; i++) {
				shares[i] = reveal.share(face[i].point()) * left;
				shown |= shares[i] > 0;
			}
			if (shown) {
				emit(pose, buffer, face, shares, middle, broken);
			}
		}
	}

	private static int anchor(int index) {
		int ring = index / BAND_SEGMENTS / SHRED_RINGS * SHRED_RINGS + SHRED_RINGS / 2;
		int segment = index % BAND_SEGMENTS / SHRED_SEGMENTS * SHRED_SEGMENTS + SHRED_SEGMENTS / 2;
		return ring * BAND_SEGMENTS + segment;
	}

	private static Vec3 centre(Vertex[] face) {
		Vec3 sum = Vec3.ZERO;
		for (Vertex vertex : face) {
			sum = sum.add(vertex.point());
		}
		return sum.scale(1.0 / face.length);
	}

	private static void emit(PoseStack.Pose pose, VertexConsumer buffer, Vertex[] face, double[] shares, Vec3 centre, double broken) {
		Vec3 axis = LightStrokes.perpendicular(centre.normalize());
		for (int i = 0; i < face.length; i++) {
			Vec3 point = UbwCrumble.falling(face[i].point(), centre, axis, broken);
			buffer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
				.setColor(face[i].red(), face[i].green(), face[i].blue(), (int) (FULL * shares[i]));
		}
	}

	static void edges(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, UbwCrumble crumble) {
		for (int index = 0; index < fragments.size(); index++) {
			Vertex[] face = fragments.get(index);
			Vec3 middle = centre(face);
			double broken = crumble.broken(middle, LightStrokes.hash(index, SHRED_SALT));
			double left = UbwCrumble.shown(broken);
			if (broken <= 0 || left <= 0) {
				continue;
			}
			Vec3 axis = LightStrokes.perpendicular(middle.normalize());
			// A closed loop: the face's corners, then its first again (a triangle repeats its last corner, which is skipped).
			Vec3[] points = new Vec3[face.length + 1];
			double[] widths = new double[points.length];
			for (int i = 0; i < points.length; i++) {
				points[i] = UbwCrumble.falling(face[i % face.length].point(), middle, axis, broken);
				widths[i] = EDGE_WIDTH;
			}
			LightStrokes.strip(pose, buffer, eye, points, widths, LightStrokes.faded(EDGE, left));
		}
	}

	private static List<Vertex[]> fragments(double radius) {
		List<Vertex[]> result = new ArrayList<>();
		for (int ring = 0; ring < BAND_RINGS; ring += SHRED_RINGS) {
			for (int segment = 0; segment < BAND_SEGMENTS; segment += SHRED_SEGMENTS) {
				Vertex a = vertex(corner(ring, segment, radius));
				Vertex b = vertex(corner(ring, segment + SHRED_SEGMENTS, radius));
				Vertex c = vertex(corner(ring + SHRED_RINGS, segment + SHRED_SEGMENTS, radius));
				Vertex d = vertex(corner(ring + SHRED_RINGS, segment, radius));
				result.add(new Vertex[]{a, b, c, c});
				result.add(new Vertex[]{a, c, d, d});
			}
		}
		return List.copyOf(result);
	}

	private static Vec3 corner(int ring, int segment, double radius) {
		int wrapped = segment % BAND_SEGMENTS;
		int seed = ring * BAND_SEGMENTS + wrapped;
		double jitter = ring == 0 || ring == BAND_RINGS ? 0 : CORNER_JITTER;
		double elevation = Math.PI * (ring + jitter * (LightStrokes.hash(seed, SHRED_SALT) - 0.5)) / BAND_RINGS - Math.PI / 2;
		double bearing = Mth.TWO_PI * (wrapped + jitter * (LightStrokes.hash(seed, SHRED_SALT + 1) - 0.5)) / BAND_SEGMENTS;
		return new Vec3(Math.cos(elevation) * Math.cos(bearing), Math.sin(elevation), Math.cos(elevation) * Math.sin(bearing)).scale(radius);
	}

	private static List<Vertex[]> band(double radius) {
		List<Vertex[]> result = new ArrayList<>();
		for (int ring = 0; ring < BAND_RINGS; ring++) {
			for (int segment = 0; segment < BAND_SEGMENTS; segment++) {
				result.add(new Vertex[]{
					vertex(polar(ring, segment, radius)), vertex(polar(ring, segment + 1, radius)),
					vertex(polar(ring + 1, segment + 1, radius)), vertex(polar(ring + 1, segment, radius))});
			}
		}
		return List.copyOf(result);
	}

	private static Vec3 polar(int ring, int segment, double radius) {
		double elevation = Math.PI * ring / BAND_RINGS - Math.PI / 2;
		double bearing = Mth.TWO_PI * segment / BAND_SEGMENTS;
		double ground = Math.cos(elevation);
		return new Vec3(ground * Math.cos(bearing), Math.sin(elevation), ground * Math.sin(bearing)).scale(radius);
	}

	private static List<Vertex[]> boundary(int radius) {
		List<Vertex[]> result = new ArrayList<>();
		long outer = (long) radius * radius;
		long inner = (long) (radius - 1) * (radius - 1);
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				long horizontal = (long) x * x + (long) z * z;
				if (horizontal > outer) {
					continue;
				}
				int top = (int) Math.floor(Math.sqrt(outer - horizontal));
				int inside = horizontal > inner ? -1 : (int) Math.floor(Math.sqrt(inner - horizontal));
				for (int y = -top; y <= top; y++) {
					if (Math.abs(y) <= inside) {
						// Everything between is inside the hollow: skip to its far edge.
						y = inside;
						continue;
					}
					for (Direction side : Direction.values()) {
						long neighbourX = (long) x + side.getStepX();
						long neighbourY = (long) y + side.getStepY();
						long neighbourZ = (long) z + side.getStepZ();
						long square = neighbourX * neighbourX + neighbourY * neighbourY + neighbourZ * neighbourZ;
						if (square > outer || square <= inner) {
							result.add(face(x, y, z, side));
						}
					}
				}
			}
		}
		return List.copyOf(result);
	}

	private static Vertex[] face(int x, int y, int z, Direction side) {
		Vec3 center = new Vec3(
			x + HALF_FACE + side.getStepX() * HALF_FACE,
			y + HALF_FACE + side.getStepY() * HALF_FACE,
			z + HALF_FACE + side.getStepZ() * HALF_FACE);
		Vec3 normal = new Vec3(side.getStepX(), side.getStepY(), side.getStepZ());
		Vec3 across = side.getAxis() == Direction.Axis.Y ? new Vec3(HALF_FACE, 0, 0) : new Vec3(0, HALF_FACE, 0);
		Vec3 along = normal.cross(across);
		Vec3[] corners = {
			center.subtract(across).subtract(along),
			center.add(across).subtract(along),
			center.add(across).add(along),
			center.subtract(across).add(along)};
		Vertex[] vertices = new Vertex[corners.length];
		for (int i = 0; i < corners.length; i++) {
			vertices[i] = vertex(corners[i]);
		}
		return vertices;
	}

	private static Vertex vertex(Vec3 corner) {
		// The pose-baked position is camera-relative, so the fixed center-relative cloud direction is kept in the colour.
		Vec3 direction = corner.normalize();
		return new Vertex(corner, channel(direction.x), channel(direction.y), channel(direction.z));
	}

	static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 point, double share) {
		Vec3 direction = point.normalize();
		buffer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
			.setColor(channel(direction.x), channel(direction.y), channel(direction.z), (int) (FULL * share));
	}

	private static int channel(double component) {
		return (int) ((component + 1) * FULL / 2);
	}
}
