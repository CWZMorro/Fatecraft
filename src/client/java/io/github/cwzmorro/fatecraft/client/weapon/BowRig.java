package io.github.cwzmorro.fatecraft.client.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import org.joml.Vector3fc;

/**
 * A bow mesh's string and nock, from its item model's {@code bow} field ({@link WeaponMeshRenderer.Unbaked}), in the bow's
 * own frame (blocks). The string is pulled back from its fixed tips as vanilla draws its pull stages.
 *
 * @param string       the string part's name in the mesh
 * @param nock         the string centre at rest, on the string's plane, where a nocked sword's end rests
 * @param stringBottom the height of the string's fixed bottom tip
 * @param stringTop    the height of its fixed top tip
 * @param grip         where the grip is along the draw, the limbs' pivots
 * @param front        the bow's front along the draw, past which a nocked sword's tip is never drawn, as vanilla's arrowhead stays on the bow
 */
public record BowRig(String string, Vector3fc nock, float stringBottom, float stringTop, float grip, float front) {
	public static final Codec<BowRig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("string").forGetter(BowRig::string),
			ExtraCodecs.VECTOR3F.fieldOf("nock").forGetter(BowRig::nock),
			Codec.FLOAT.fieldOf("string_bottom").forGetter(BowRig::stringBottom),
			Codec.FLOAT.fieldOf("string_top").forGetter(BowRig::stringTop),
			Codec.FLOAT.fieldOf("grip").forGetter(BowRig::grip),
			Codec.FLOAT.fieldOf("front").forGetter(BowRig::front))
		.apply(instance, BowRig::new));
	// Grip to string at full draw: the string comes back to the chest, about a player's reach from the bow hand.
	private static final float FULL_DRAW = 0.65F;

	float nockX(float draw, double length) {
		float travel = Math.min(this.grip + FULL_DRAW - this.nock.x(), Math.max(0, (float) length - (this.nock.x() - this.front)));
		return this.nock.x() + travel * Mth.clamp(draw, 0, 1);
	}

	float stringWeight(float y) {
		return Mth.clamp(y <= this.nock.y() ? (y - this.stringBottom) / (this.nock.y() - this.stringBottom)
			: (this.stringTop - y) / (this.stringTop - this.nock.y()), 0, 1);
	}

	float stringSlope(float quadY, float travel) {
		return quadY < this.nock.y() ? travel / (this.nock.y() - this.stringBottom) : -travel / (this.stringTop - this.nock.y());
	}
}
