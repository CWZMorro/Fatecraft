package io.github.cwzmorro.fatecraft;

/** The golden ratio's three uses for spreading things evenly without repeats, shared by the server's layouts and the client's drawing. */
public final class GoldenRatio {
	public static final double ANGLE = Math.PI * (3 - Math.sqrt(5));
	public static final double SHARE = 2 / (1 + Math.sqrt(5));
	public static final long HASH_STEP = 0x9E3779B97F4A7C15L;

	private GoldenRatio() {
	}
}
