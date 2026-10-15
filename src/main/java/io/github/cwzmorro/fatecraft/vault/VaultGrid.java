package io.github.cwzmorro.fatecraft.vault;

import java.util.List;

/** How far a vault section's grid scrolls: more rows open once half of those open are taken. */
public final class VaultGrid {
	private static final int HALF_OCCUPANCY = 2;

	private VaultGrid() {
	}

	public static int lastScrollRow(List<VaultEntry> entries, int columns, int visibleRows) {
		int occupied = (int) entries.stream().filter(entry -> !entry.isEmpty()).count();
		int rowsForOccupancy = occupied * HALF_OCCUPANCY / columns + 1;
		int rowsForPlacement = entries.size() / columns + 1;
		return Math.max(0, Math.max(rowsForOccupancy, rowsForPlacement) - visibleRows);
	}
}
