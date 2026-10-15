package io.github.cwzmorro.fatecraft.gate;

import io.github.cwzmorro.alayacore.config.ConfigSection;
import io.github.cwzmorro.fatecraft.FateConfig;

/**
 * The {@code [fatecraft.gate_of_babylon]} section of {@code config/alayacore.toml}. The volleys, the gates a
 * second and the return time are each player's own, set in the vault screen; how the weapons fly and strike is
 * Alaya Core's {@code [thrown_weapons]}.
 */
public record GateConfig(double manaPerWeapon, double aimRange, double singleTargetNearest, double singleTargetFarthest,
	double singleTargetMinDegrees, double singleTargetMaxDegrees) {
	public static final GateConfig DEFAULTS = new GateConfig(5.0, 128.0, 12.0, 20.0, 20.0, 90.0);
	private static final double MAX_SINGLE_TARGET_DISTANCE = 128.0;

	public static GateConfig read(ConfigSection s) {
		double minDegrees = s.number("single_target_min_degrees", " Single-target gates: least elevation above the ground.",
			DEFAULTS.singleTargetMinDegrees, 0.0, 90.0);
		double maxDegrees = s.number("single_target_max_degrees", " Single-target gates: greatest elevation above the ground.",
			DEFAULTS.singleTargetMaxDegrees, minDegrees, 90.0);
		return new GateConfig(
			s.number("mana_per_weapon", " Mana for every weapon, arrow or throwable thrown, the same for each.", DEFAULTS.manaPerWeapon, 0.0,
				Double.MAX_VALUE),
			s.number("aim_range", " Blocks along the crosshair the Gate of Babylon aims at a creature, else a block.", DEFAULTS.aimRange, 1.0,
				FateConfig.MAX_REACH),
			s.number("single_target_nearest", " Single target: the nearest a gate opens to the target, in blocks…", DEFAULTS.singleTargetNearest, 0.0,
				MAX_SINGLE_TARGET_DISTANCE),
			s.number("single_target_farthest", " …and the farthest.", DEFAULTS.singleTargetFarthest, 0.0, MAX_SINGLE_TARGET_DISTANCE),
			minDegrees, Math.max(minDegrees, maxDegrees)
		);
	}
}
