package io.github.cwzmorro.fatecraft.client.altera;

/** Photon Ray's light colours, shared by its cone and its charge bar. */
final class PhotonRayPalette {
	// The cone's bands are blended so they stay deep blue over a bright daytime sky; added light is kept for the core, highlights and flash.
	static final int[] OUTER_BAND = {20, 60, 205, 120};
	static final int[] INNER_BAND = {50, 135, 255, 140};
	static final int[] HIGHLIGHT = {120, 215, 255, 110};
	static final int[] WHITE = {215, 250, 255, 225};
	static final int[] MOTE_HALO = {40, 130, 255, 200};
	static final int CHARGE_BAR = 0xFF41B4FF;

	private PhotonRayPalette() {
	}
}
