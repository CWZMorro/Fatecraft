package io.github.cwzmorro.fatecraft.vault;

import io.github.cwzmorro.alayacore.config.ConfigSection;
import java.util.List;

/** Vault stack capacity, and the Gate of Babylon and Enkidu settings new players start with; saved ones stay as they are. */
public record VaultConfig(int stackMultiplier, int smallVolley, int mediumVolley, int largeVolley, int hugeVolley,
	int gatesPerSecond, int returnSeconds, int chains) {
	public static final VaultConfig DEFAULTS = new VaultConfig(
		10, // stackMultiplier
		8, // smallVolley
		25, // mediumVolley
		54, // largeVolley
		200, // hugeVolley
		25, // gatesPerSecond
		60, // returnSeconds
		12); // chains
	private static final int MAX_STACK_MULTIPLIER = 1024;

	public static VaultConfig read(ConfigSection s) {
		return new VaultConfig(
			s.integer("stack_multiplier", " Each vault cell holds this many of the item's ordinary stacks.", DEFAULTS.stackMultiplier, 1,
				MAX_STACK_MULTIPLIER),
			s.integer("small_volley", " Default gates in tier I.", DEFAULTS.smallVolley, VaultSettings.MIN, VaultSettings.MAX_VOLLEY),
			s.integer("medium_volley", " Default gates in tier II.", DEFAULTS.mediumVolley, VaultSettings.MIN, VaultSettings.MAX_VOLLEY),
			s.integer("large_volley", " Default gates in tier III.", DEFAULTS.largeVolley, VaultSettings.MIN, VaultSettings.MAX_VOLLEY),
			s.integer("huge_volley", " Default gates in tier IV.", DEFAULTS.hugeVolley, VaultSettings.MIN, VaultSettings.MAX_VOLLEY),
			s.integer("gates_per_second", " Default firing speed for a new player's vault.", DEFAULTS.gatesPerSecond,
				VaultSettings.MIN, VaultSettings.MAX_GATES_PER_SECOND),
			s.integer("return_seconds", " Default thrown-item return delay for a new player's vault.", DEFAULTS.returnSeconds,
				VaultSettings.MIN, VaultSettings.MAX_RETURN_SECONDS),
			s.integer("chains", " Default number of Enkidu's chains for a new player's vault.", DEFAULTS.chains,
				VaultSettings.MIN, VaultSettings.MAX_CHAINS));
	}

	public List<Integer> volleys() {
		return List.of(this.smallVolley, this.mediumVolley, this.largeVolley, this.hugeVolley);
	}
}
