package io.github.cwzmorro.fatecraft.client.weapon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.GsonHelper;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * A weapon's 3D mesh, from {@code assets/<namespace>/models/weapon/<name>.json}: shared vertices and 4 indices per quad,
 * expanded here into one flat array per part. Positions are in blocks, the grip at the block centre; texture coordinates
 * run 0 to 1 across the texture the item model definition gives it.
 */
public record WeaponMesh(List<Part> parts, Vector3fc min, Vector3fc max) {
	public static final int STRIDE = 8;
	private static final int VERTICES_PER_QUAD = 4;

	/**
	 * @param spin        turning direction when the weapon spins (1, -1), 0 for a part that never turns
	 * @param pivot       the point it turns around, spinning or opening
	 * @param axis        the (unit) direction it turns about
	 * @param openDegrees how far it turns about the pivot when the weapon is open
	 * @param openOffset  how far it is moved, after turning, when the weapon is open
	 * @param emissive    drawn again with the glow texture
	 * @param translucent drawn blended after the opaque parts, from both sides
	 * @param quads       {@link #STRIDE} numbers per vertex, 4 vertices per quad
	 */
	public record Part(String name, int spin, Vector3fc pivot, Vector3fc axis, float openDegrees, Vector3fc openOffset, boolean emissive,
		boolean translucent, float[] quads) {
		public boolean opens() {
			return this.openDegrees != 0.0F || this.openOffset.lengthSquared() != 0.0F;
		}
	}

	static WeaponMesh parse(JsonObject json) {
		List<Part> parts = new ArrayList<>();
		Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
		Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
		for (JsonElement element : GsonHelper.getAsJsonArray(json, "parts")) {
			JsonObject part = element.getAsJsonObject();
			String name = GsonHelper.getAsString(part, "name");
			JsonArray vertices = GsonHelper.getAsJsonArray(part, "vertices");
			JsonArray indices = GsonHelper.getAsJsonArray(part, "quads");
			if (vertices.size() % STRIDE != 0 || indices.size() % VERTICES_PER_QUAD != 0) {
				throw new IllegalArgumentException("Part " + name + " has a broken vertex or quad list");
			}
			// Expanded once here, so drawing walks one flat array.
			float[] quads = new float[indices.size() * STRIDE];
			for (int q = 0; q < indices.size(); q++) {
				int vertex = indices.get(q).getAsInt() * STRIDE;
				for (int k = 0; k < STRIDE; k++) {
					quads[q * STRIDE + k] = vertices.get(vertex + k).getAsFloat();
				}
			}
			int spin = GsonHelper.getAsInt(part, "spin", 0);
			JsonObject open = GsonHelper.getAsJsonObject(part, "open", new JsonObject());
			float openDegrees = GsonHelper.getAsFloat(open, "degrees", 0.0F);
			Vector3f openOffset = vector(open, "offset");
			Vector3f axis = vector(part, "axis");
			if ((spin != 0 || openDegrees != 0.0F) && axis.lengthSquared() == 0) {
				throw new IllegalArgumentException("Part " + name + " turns but has no axis");
			}
			parts.add(new Part(name, spin, vector(part, "pivot"), axis.lengthSquared() == 0 ? axis : axis.normalize(), openDegrees, openOffset,
				GsonHelper.getAsBoolean(part, "emissive", false), GsonHelper.getAsBoolean(part, "translucent", false), quads));
			// The extents cover the closed pose and the open pose's lift (not its turning, which is small beside it).
			for (int i = 0; i < quads.length; i += STRIDE) {
				min.set(Math.min(min.x, quads[i]), Math.min(min.y, quads[i + 1]), Math.min(min.z, quads[i + 2]));
				max.set(Math.max(max.x, quads[i]), Math.max(max.y, quads[i + 1]), Math.max(max.z, quads[i + 2]));
				min.set(Math.min(min.x, quads[i] + openOffset.x), Math.min(min.y, quads[i + 1] + openOffset.y), Math.min(min.z, quads[i + 2] + openOffset.z));
				max.set(Math.max(max.x, quads[i] + openOffset.x), Math.max(max.y, quads[i + 1] + openOffset.y), Math.max(max.z, quads[i + 2] + openOffset.z));
			}
		}
		return new WeaponMesh(List.copyOf(parts), min, max);
	}

	private static Vector3f vector(JsonObject json, String key) {
		if (!json.has(key)) {
			return new Vector3f();
		}
		JsonArray array = GsonHelper.getAsJsonArray(json, key);
		return new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
	}
}
