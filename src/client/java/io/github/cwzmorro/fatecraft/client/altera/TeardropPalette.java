package io.github.cwzmorro.fatecraft.client.altera;

/** Teardrop Photon Ray's light colours (RGBA), shared by its beam, its red line to Mars and its orbs. */
final class TeardropPalette {
	// WHITE is Spread's light (halo, haze, shells, splash); RED is Condensed's beam; LINK_RED is the line from her sword to the circles; MAGENTA the orbs.
	static final int[] WHITE = {245, 255, 255, 255};
	static final int[] CYAN = {70, 225, 255, 210};
	static final int[] HALO = {40, 185, 255, 70};
	static final int[] SPREAD_HAZE = {105, 210, 255, 40};
	static final int[] VOLUME_WHITE = {225, 255, 255, 110};
	static final int[] VOLUME_CYAN = {90, 225, 255, 90};
	static final int[] SPREAD_CORE = {215, 245, 255, 100};
	static final int[] SPREAD_HALO = {100, 180, 255, 25};
	static final int[] RED = {255, 12, 8, 230};
	static final int[] RED_CORE = {255, 45, 25, 255};
	static final int[] RED_HALO = {255, 8, 5, 70};
	static final int[] LINK_RED = {255, 35, 35, 220};
	static final int[] MAGENTA = {255, 35, 245, 210};
	static final int[] GOLD = {255, 235, 100, 210};

	private TeardropPalette() {
	}
}
