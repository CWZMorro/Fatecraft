package io.github.cwzmorro.fatecraft.client.altera;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Teardrop's seal artwork, built once, on planes square to the ray. */
final class TeardropSeals {
	private static final int SEGMENTS = 192;
	private static final int NOTCHES = 48;
	private static final int TEETH = 32;
	private static final int GLYPHS = 20;
	private static final int OVAL_SEGMENTS = 20;
	private static final double RIM_INNER = 0.968;
	private static final double RIM_STEP = 0.022;
	private static final double EDGE = 0.008;
	private static final double HALO_WIDTH = 0.085;
	private static final double GLYPH_ORBIT = 0.53;
	private static final double GLYPH_LONG = 0.075;
	private static final double GLYPH_SHORT = 0.043;
	private static final double SQUARE_TURN = Math.PI / 4;
	// Seal proportions are normalized to the outer rim.
	private static final double MAIN_FACE_RADIUS = 0.72;
	private static final double SATELLITE_FACE_RADIUS = 0.96;
	private static final double MAIN_INNER_RADIUS = 0.43;
	private static final double SATELLITE_INNER_RADIUS = 0.53;
	private static final double MAIN_TOOTH_ROOT = 0.64;
	private static final double SATELLITE_TOOTH_ROOT = 0.67;
	private static final double MAIN_TOOTH_TIP = 0.88;
	private static final double SATELLITE_TOOTH_TIP = 0.86;
	private static final double SATELLITE_OUTER_NOTCH = 0.91;
	private static final double SATELLITE_INNER_NOTCH = 0.56;
	private static final double SATELLITE_BAND_INNER = 0.65;
	private static final double SATELLITE_BAND_OUTER = 0.66;
	private static final int SATELLITE_SQUARES = 3;
	private static final double SATELLITE_SQUARE_RADIUS = 0.43;
	private static final double SATELLITE_SQUARE_STEP = 0.095;
	private static final double SATELLITE_CENTRE_RADIUS = 0.105;
	private static final double MAIN_BAND_INNER = 0.425;
	private static final double MAIN_BAND_OUTER = 0.437;
	private static final double MAIN_BAND_GLOW = 0.431;
	private static final double MAIN_GLOW_DIVISOR = 3;
	private static final int SQUARE_SIDES = 4;
	private static final int TRIANGLE_SIDES = 3;
	private static final int OCTAGRAM_SQUARES = 2;
	private static final double OCTAGRAM_BAND_INNER = 0.702;
	private static final double OCTAGRAM_BAND_OUTER = 0.714;
	private static final double OCTAGRAM_BAND_GLOW = 0.708;
	private static final double DISC_FILL_RADIUS = 0.94;
	private static final double DISC_BAND_INNER = 0.976;
	private static final double DISC_BAND_OUTER = 0.988;
	private static final double DISC_BAND_GLOW = 0.98;
	private static final double SMALL_RING_INNER = 0.986;
	private static final int FAINT_ALPHA_THRESHOLD = 128;
	private static final double FAINT_EDGE_SCALE = 3;
	private static final double LAYER_LIFT = 0.002;
	private static final int WHITE = 0xf4f8ffff;
	private static final int RED = 0x689b1438;
	private static final int DARK_RED = 0x16430f28;
	private static final int RED_MARK = 0xffff5370;
	private static final int BLUE = 0xfa93dfff;
	private static final int SKY_BLUE = 0xfa7278ff;
	private static final int GREEN = 0xf450ff78;
	private static final int GREEN_FILL = 0x0848cc87;
	private static final int BLUE_FILL = 0x663a8ed9;
	private static final int SOURCE_WHITE = 0x40ffffff;
	private static final int VIOLET_HALO = 0x687574ff;
	private static final int GREEN_HALO = 0x605bff80;
	private static final int RED_HALO = 0x44ff315c;
	private static final int BLUE_HALO = 0x607489ff;
	private static final double BACKGROUND_INNER = 0.76;
	private static final double BACKGROUND_TOOTH = 0.9;
	private static final double SCALLOP_ORBIT = 0.7;
	private static final double SCALLOP_RADIUS = 0.035;
	private static final int SCALLOPS = 48;
	// Tessellated once when the class loads, after the constants above.
	static final Artwork MAIN = seal(false);
	static final Artwork SATELLITE = seal(true);
	static final Artwork OCTAGRAM = octagram();
	static final Artwork BLUE_DISC = blueDisc();
	static final Artwork GREEN_RIM = rim();
	static final Artwork SMALL_RING = smallRing();
	static final Artwork BACKGROUND = background();

	private TeardropSeals() {
	}

	static void draw(Artwork artwork, PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, Vec3 centre, Vec3 u, Vec3 v,
		double radius, double rotation, double alpha, boolean added) {
		draw(artwork, pose, buffer, eye, centre, u, v, radius, rotation, alpha, added, false);
	}

	static void draw(Artwork artwork, PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, Vec3 centre, Vec3 u, Vec3 v,
		double radius, double rotation, double alpha, boolean added, boolean source) {
		Mesh mesh = added ? artwork.glow : artwork.face;
		double cos = Math.cos(rotation);
		double sin = Math.sin(rotation);
		Vec3 normal = u.cross(v);
		double sign = normal.dot(eye.subtract(centre)) < 0 ? -1 : 1;
		for (int i = 0; i < mesh.colours.length; i++) {
			double x = (mesh.xy[i * 2] * cos - mesh.xy[i * 2 + 1] * sin) * radius;
			double y = (mesh.xy[i * 2] * sin + mesh.xy[i * 2 + 1] * cos) * radius;
			int colour = mesh.colours[i];
			if (source && (colour == BLUE_FILL || colour == RED || colour == DARK_RED)) {
				colour = SOURCE_WHITE;
			}
			double lift = mesh.layers[i] * LAYER_LIFT * radius * sign;
			buffer.addVertex(pose, (float) (centre.x + u.x * x + v.x * y + normal.x * lift),
				(float) (centre.y + u.y * x + v.y * y + normal.y * lift),
				(float) (centre.z + u.z * x + v.z * y + normal.z * lift))
				.setColor(ARGB.red(colour), ARGB.green(colour), ARGB.blue(colour), (int) (ARGB.alpha(colour) * alpha));
		}
	}

	private static Artwork seal(boolean satellite) {
		Builder face = new Builder();
		Builder glow = new Builder();
		face.band(satellite ? SATELLITE_INNER_RADIUS : MAIN_INNER_RADIUS,
			satellite ? SATELLITE_FACE_RADIUS : MAIN_FACE_RADIUS, RED);
		face.layer++;
		face.band(0, satellite ? SATELLITE_INNER_RADIUS : MAIN_INNER_RADIUS, DARK_RED);
		face.layer++;
		notched(face, 1, satellite ? RED_MARK : BLUE);
		glow.softRing(1, HALO_WIDTH, satellite ? RED_HALO : VIOLET_HALO);
		double root = satellite ? SATELLITE_TOOTH_ROOT : MAIN_TOOTH_ROOT;
		double tip = satellite ? SATELLITE_TOOTH_TIP : MAIN_TOOTH_TIP;
		for (int i = 0; i < TEETH; i++) {
			double a = Mth.TWO_PI * i / TEETH;
			double b = Mth.TWO_PI * (i + 1) / TEETH;
			face.triangle(root * Math.cos(a), root * Math.sin(a), tip * Math.cos((a + b) / 2),
				tip * Math.sin((a + b) / 2), root * Math.cos(b), root * Math.sin(b), RED_MARK);
		}
		if (satellite) {
			notched(face, SATELLITE_OUTER_NOTCH, RED_MARK);
			notched(face, SATELLITE_INNER_NOTCH, RED_MARK);
			face.band(SATELLITE_BAND_INNER, SATELLITE_BAND_OUTER, RED_MARK);
			for (int i = 0; i < SATELLITE_SQUARES; i++) {
				face.polygon(SATELLITE_SQUARE_RADIUS - i * SATELLITE_SQUARE_STEP, SQUARE_SIDES, SQUARE_TURN, RED_MARK, false);
			}
			face.polygon(SATELLITE_CENTRE_RADIUS, SQUARE_SIDES, SQUARE_TURN, WHITE, true);
			face.polygon(SATELLITE_OUTER_NOTCH, TRIANGLE_SIDES, 0, RED_MARK, false);
			face.polygon(SATELLITE_OUTER_NOTCH, TRIANGLE_SIDES, Math.PI, RED_MARK, false);
		} else {
			face.band(MAIN_BAND_INNER, MAIN_BAND_OUTER, BLUE);
			glow.softRing(MAIN_BAND_GLOW, HALO_WIDTH / MAIN_GLOW_DIVISOR, BLUE_HALO);
			for (int i = 0; i < GLYPHS; i++) {
				double a = Mth.TWO_PI * i / GLYPHS;
				oval(face, a);
			}
		}
		glow.softRing(tip, HALO_WIDTH, RED_HALO);
		return new Artwork(face.mesh(), glow.mesh());
	}

	private static void notched(Builder face, double radius, int colour) {
		for (int i = 0; i < SEGMENTS; i++) {
			int step = i % (SEGMENTS / NOTCHES);
			double outer = radius + (step == 0 ? RIM_STEP : 0);
			double inner = radius * RIM_INNER + (step == 2 ? RIM_STEP / 2 : 0);
			face.arc(inner, outer, Mth.TWO_PI * i / SEGMENTS, Mth.TWO_PI * (i + 1) / SEGMENTS, colour, colour);
		}
	}

	private static void oval(Builder face, double angle) {
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		for (int i = 0; i < OVAL_SEGMENTS; i++) {
			double a = Mth.TWO_PI * i / OVAL_SEGMENTS;
			double b = Mth.TWO_PI * (i + 1) / OVAL_SEGMENTS;
			double ax = GLYPH_ORBIT + Math.cos(a) * GLYPH_SHORT;
			double ay = Math.sin(a) * GLYPH_LONG;
			double bx = GLYPH_ORBIT + Math.cos(b) * GLYPH_SHORT;
			double by = Math.sin(b) * GLYPH_LONG;
			face.line(ax * cos - ay * sin, ax * sin + ay * cos, bx * cos - by * sin, bx * sin + by * cos, EDGE / 2, WHITE);
		}
		// Different red strokes within each oval read as inscriptions rather than dotted beads.
		double reach = GLYPH_SHORT / 2;
		face.line((GLYPH_ORBIT - reach) * cos, (GLYPH_ORBIT - reach) * sin,
			(GLYPH_ORBIT + reach) * cos, (GLYPH_ORBIT + reach) * sin, EDGE / 2, RED_MARK);
		face.line(GLYPH_ORBIT * cos, GLYPH_ORBIT * sin,
			GLYPH_ORBIT * cos - GLYPH_LONG / 2 * sin, GLYPH_ORBIT * sin + GLYPH_LONG / 2 * cos, EDGE / 2, RED_MARK);
	}

	private static Artwork octagram() {
		Builder face = new Builder();
		Builder glow = new Builder();
		for (int i = 0; i < OCTAGRAM_SQUARES; i++) {
			face.polygon(1, SQUARE_SIDES, i * SQUARE_TURN, GREEN_FILL, true);
			face.polygon(1, SQUARE_SIDES, i * SQUARE_TURN, GREEN, false);
			glow.polygon(1, SQUARE_SIDES, i * SQUARE_TURN, GREEN_HALO, false);
		}
		face.band(OCTAGRAM_BAND_INNER, OCTAGRAM_BAND_OUTER, SKY_BLUE);
		glow.softRing(OCTAGRAM_BAND_GLOW, HALO_WIDTH / 2, BLUE_HALO);
		return new Artwork(face.mesh(), glow.mesh());
	}

	private static Artwork blueDisc() {
		Builder face = new Builder();
		Builder glow = new Builder();
		face.band(0, DISC_FILL_RADIUS, BLUE_FILL);
		face.arcBand(DISC_FILL_RADIUS, 1, BLUE_FILL, BLUE_FILL & 0xffffff);
		face.band(DISC_BAND_INNER, DISC_BAND_OUTER, WHITE);
		face.band(MAIN_BAND_INNER, MAIN_BAND_OUTER, BLUE);
		glow.softRing(MAIN_BAND_GLOW, HALO_WIDTH / MAIN_GLOW_DIVISOR, BLUE_HALO);
		glow.softRing(DISC_BAND_GLOW, HALO_WIDTH, BLUE_HALO);
		return new Artwork(face.mesh(), glow.mesh());
	}

	private static Artwork rim() {
		Builder face = new Builder();
		Builder glow = new Builder();
		notched(face, 1, GREEN);
		glow.softRing(1, HALO_WIDTH, GREEN_HALO);
		return new Artwork(face.mesh(), glow.mesh());
	}

	private static Artwork smallRing() {
		Builder face = new Builder();
		Builder glow = new Builder();
		face.band(SMALL_RING_INNER, 1, GREEN);
		glow.softRing(1, HALO_WIDTH, GREEN_HALO);
		return new Artwork(face.mesh(), glow.mesh());
	}

	private static Artwork background() {
		Builder face = new Builder();
		Builder glow = new Builder();
		face.band(RIM_INNER, 1, WHITE);
		face.band(BACKGROUND_INNER - EDGE, BACKGROUND_INNER, WHITE);
		for (int i = 0; i < TEETH; i++) {
			double a = Mth.TWO_PI * i / TEETH;
			double b = Mth.TWO_PI * (i + 1) / TEETH;
			face.triangle(BACKGROUND_INNER * Math.cos(a), BACKGROUND_INNER * Math.sin(a),
				BACKGROUND_TOOTH * Math.cos((a + b) / 2), BACKGROUND_TOOTH * Math.sin((a + b) / 2),
				BACKGROUND_INNER * Math.cos(b), BACKGROUND_INNER * Math.sin(b), WHITE);
		}
		for (int i = 0; i < SCALLOPS; i++) {
			double angle = i * Mth.TWO_PI / SCALLOPS;
			double x = Math.cos(angle) * SCALLOP_ORBIT;
			double y = Math.sin(angle) * SCALLOP_ORBIT;
			for (int j = 0; j < OVAL_SEGMENTS; j++) {
				double a = j * Mth.TWO_PI / OVAL_SEGMENTS;
				double b = (j + 1) * Mth.TWO_PI / OVAL_SEGMENTS;
				face.line(x + Math.cos(a) * SCALLOP_RADIUS, y + Math.sin(a) * SCALLOP_RADIUS,
					x + Math.cos(b) * SCALLOP_RADIUS, y + Math.sin(b) * SCALLOP_RADIUS, EDGE / 2, WHITE);
			}
		}
		glow.softRing(1, HALO_WIDTH, BLUE_HALO);
		return new Artwork(face.mesh(), glow.mesh());
	}

	record Artwork(Mesh face, Mesh glow) {
		Artwork {
			glow = glowingEdges(face, glow);
		}
	}

	private static Mesh glowingEdges(Mesh face, Mesh glow) {
		int edges = 0;
		for (int colour : face.colours) {
			if (emits(colour)) {
				edges++;
			}
		}
		int original = glow.colours.length;
		float[] xy = new float[(original + edges) * 2];
		int[] colours = new int[original + edges];
		int[] layers = new int[original + edges];
		System.arraycopy(glow.xy, 0, xy, 0, glow.xy.length);
		System.arraycopy(glow.colours, 0, colours, 0, original);
		System.arraycopy(glow.layers, 0, layers, 0, original);
		int index = original;
		for (int i = 0; i < face.colours.length; i++) {
			int colour = face.colours[i];
			if (emits(colour)) {
				xy[index * 2] = face.xy[i * 2];
				xy[index * 2 + 1] = face.xy[i * 2 + 1];
				colours[index] = (colour & 0xffffff) | (colour == WHITE ? 0xb0000000 : 0x30000000);
				layers[index] = face.layers[i];
				index++;
			}
		}
		return new Mesh(xy, colours, layers);
	}

	private static boolean emits(int colour) {
		return colour == WHITE || colour == BLUE || colour == SKY_BLUE || colour == GREEN || colour == RED_MARK;
	}

	private record Mesh(float[] xy, int[] colours, int[] layers) {
	}

	private static final class Builder {
		private final List<Float> xy = new ArrayList<>();
		private final List<Integer> colours = new ArrayList<>();
		private final List<Integer> layers = new ArrayList<>();
		private int layer;

		void vertex(double x, double y, int colour) {
			this.xy.add((float) x);
			this.xy.add((float) y);
			this.colours.add(colour);
			this.layers.add(this.layer);
		}

		void triangle(double ax, double ay, double bx, double by, double cx, double cy, int colour) {
			this.vertex(ax, ay, colour);
			this.vertex(bx, by, colour);
			this.vertex(cx, cy, colour);
			this.vertex(cx, cy, colour);
		}

		void band(double inner, double outer, int colour) {
			this.arcBand(inner, outer, colour, colour);
		}

		void arcBand(double inner, double outer, int inside, int outside) {
			for (int i = 0; i < SEGMENTS; i++) {
				this.arc(inner, outer, Mth.TWO_PI * i / SEGMENTS, Mth.TWO_PI * (i + 1) / SEGMENTS, inside, outside);
			}
		}

		void arc(double inner, double outer, double a, double b, int inside, int outside) {
			this.vertex(inner * Math.cos(a), inner * Math.sin(a), inside);
			this.vertex(outer * Math.cos(a), outer * Math.sin(a), outside);
			this.vertex(outer * Math.cos(b), outer * Math.sin(b), outside);
			this.vertex(inner * Math.cos(b), inner * Math.sin(b), inside);
		}

		void softRing(double radius, double width, int colour) {
			this.arcBand(radius - width, radius, colour & 0xffffff, colour);
			this.arcBand(radius, radius + width, colour, colour & 0xffffff);
		}

		void line(double ax, double ay, double bx, double by, double width, int colour) {
			double length = Math.hypot(bx - ax, by - ay);
			double dx = -(by - ay) / length * width;
			double dy = (bx - ax) / length * width;
			this.vertex(ax - dx, ay - dy, colour);
			this.vertex(ax + dx, ay + dy, colour);
			this.vertex(bx + dx, by + dy, colour);
			this.vertex(bx - dx, by - dy, colour);
		}

		void polygon(double radius, int sides, double rotation, int colour, boolean fill) {
			for (int i = 0; i < sides; i++) {
				double a = Mth.TWO_PI * i / sides + rotation;
				double b = Mth.TWO_PI * (i + 1) / sides + rotation;
				double ax = radius * Math.cos(a);
				double ay = radius * Math.sin(a);
				double bx = radius * Math.cos(b);
				double by = radius * Math.sin(b);
				if (fill) {
					this.triangle(0, 0, ax, ay, bx, by, colour);
				} else {
					this.line(ax, ay, bx, by, (colour >>> 24) < FAINT_ALPHA_THRESHOLD ? EDGE * FAINT_EDGE_SCALE : EDGE / 2, colour);
				}
			}
		}

		Mesh mesh() {
			float[] positions = new float[this.xy.size()];
			int[] rgba = new int[this.colours.size()];
			int[] depths = new int[this.layers.size()];
			for (int i = 0; i < positions.length; i++) {
				positions[i] = this.xy.get(i);
			}
			for (int i = 0; i < rgba.length; i++) {
				rgba[i] = this.colours.get(i);
				depths[i] = this.layers.get(i);
			}
			return new Mesh(positions, rgba, depths);
		}
	}
}
