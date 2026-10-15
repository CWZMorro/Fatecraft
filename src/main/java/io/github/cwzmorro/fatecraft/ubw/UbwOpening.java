package io.github.cwzmorro.fatecraft.ubw;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/**
 * The opening, shared by the server's terrain swap and the client's fire: mana gathers, then a ring of fire spreads from
 * the caster's feet; its front is the swap cursor, with the field behind it. Ages are ticks of the dimension's game time
 * since ignition (negative while the mana gathers); distances are horizontal, from the caster's position at the cast.
 */
public final class UbwOpening {
	public static final double IGNITION_REACH = 4.0;
	private static final double SPAN_MARGIN = 1.0;
	private static final double RIM_INSET = 1.5;
	private static final double UNLIT = -1.0;

	/** Configured opening ticks: charge before ignition; ignition, complete spread and dying measured from ignition's start. */
	public record Timing(int chargeTicks, int ignitionTicks, int spreadTicks, int dyingTicks) {
		public static final StreamCodec<ByteBuf, Timing> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Timing::chargeTicks,
			ByteBufCodecs.VAR_INT, Timing::ignitionTicks,
			ByteBufCodecs.VAR_INT, Timing::spreadTicks,
			ByteBufCodecs.VAR_INT, Timing::dyingTicks,
			Timing::new
		);

		public Timing {
			if (chargeTicks < 1 || ignitionTicks < 1 || spreadTicks <= ignitionTicks || dyingTicks <= spreadTicks) {
				throw new IllegalArgumentException("UBW opening timings must be positive and ordered");
			}
		}
	}

	private UbwOpening() { }

	public static double span(double offsetX, double offsetZ, int radius) {
		return Math.sqrt(offsetX * offsetX + offsetZ * offsetZ) + radius + SPAN_MARGIN;
	}

	public static double reach(double age, double span, Timing timing) {
		if (age < 0) {
			return UNLIT;
		}
		if (age <= timing.ignitionTicks()) {
			return IGNITION_REACH * Mth.clamp(age / timing.ignitionTicks(), 0, 1);
		}
		return Mth.lerp(Math.min(1, (age - timing.ignitionTicks()) / (timing.spreadTicks() - timing.ignitionTicks())), IGNITION_REACH, span);
	}

	public static double floorRadius(int radius, double floor) {
		return Math.sqrt(Math.max(0, (double) radius * radius - floor * floor)) - RIM_INSET;
	}
}
