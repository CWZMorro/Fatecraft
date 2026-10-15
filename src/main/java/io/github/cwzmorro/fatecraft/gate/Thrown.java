package io.github.cwzmorro.fatecraft.gate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/**
 * Marks what the Gate of Babylon threw. {@code returnAt} is the game tick it goes back to the vault, or a copy vanishes:
 * its owner's return time after it was thrown, and again after it lands.
 */
public record Thrown(UUID owner, int section, boolean copy, boolean landed, long returnAt) {
	public static final Codec<Thrown> CODEC = RecordCodecBuilder.create(i -> i.group(
		UUIDUtil.CODEC.fieldOf("owner").forGetter(Thrown::owner),
		Codec.INT.fieldOf("section").forGetter(Thrown::section),
		Codec.BOOL.fieldOf("copy").forGetter(Thrown::copy),
		Codec.BOOL.fieldOf("landed").forGetter(Thrown::landed),
		Codec.LONG.fieldOf("return_at").forGetter(Thrown::returnAt)
	).apply(i, Thrown::new));

	public Thrown landedUntil(long returnAt) {
		return new Thrown(this.owner, this.section, this.copy, true, returnAt);
	}
}
