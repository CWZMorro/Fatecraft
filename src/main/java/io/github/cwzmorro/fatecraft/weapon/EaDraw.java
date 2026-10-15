package io.github.cwzmorro.fatecraft.weapon;

/**
 * Drawing Ea, shared by the server (when Ea rises) and the client (what is drawn), in ticks from the key's
 * use: golden light goes up from the raised key; the red lattice grows up and out over the spot in front, pulses and
 * collapses from the top; a golden gate opens on the spot and Ea rises out of it, handle up.
 */
public final class EaDraw {
	// Timeline in ticks from the key's use: golden light until 3 s; the lattice grows from 2 s to 2.8 s and collapses from 8.5 s to 10 s;
	// the gate opens as it collapses; Ea rises from 9.25 s for 3 s.
	public static final int KEY_LIGHT_END = 60;
	public static final int LATTICE_START = 40;
	public static final int LATTICE_GROWN = 56;
	public static final int LATTICE_COLLAPSE = 170;
	public static final int LATTICE_END = 200;
	public static final int GATE_START = LATTICE_COLLAPSE;
	public static final int RISE_START = 185;
	public static final int RISE_TICKS = 60;
	public static final double AHEAD = 1.0;

	private EaDraw() {
	}

	public static double risen(double ticks) {
		return Math.clamp((ticks - RISE_START) / RISE_TICKS, 0.0, 1.0);
	}
}
