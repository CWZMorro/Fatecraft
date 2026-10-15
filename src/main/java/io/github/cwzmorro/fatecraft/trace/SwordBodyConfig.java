package io.github.cwzmorro.fatecraft.trace;

import io.github.cwzmorro.alayacore.config.ConfigSection;
import io.github.cwzmorro.alayacore.util.Percent;

/** The {@code [fatecraft.sword_body]} table: how long the blades last and what they give EMIYA. */
public record SwordBodyConfig(double seconds, double thornsPercent, double reductionPercent, int regenerationAmplifier) {
	// A compile-time constant, so game tests can bound their time by it.
	public static final double DEFAULT_SECONDS = 20.0;
	public static final SwordBodyConfig DEFAULTS = new SwordBodyConfig(DEFAULT_SECONDS, 20.0, 25.0, 0);
	public static final double COOLDOWN_SECONDS = 60.0;

	private static final double LONGEST_SECONDS = 600.0;
	private static final int HIGHEST_AMPLIFIER = 255;

	public static SwordBodyConfig read(ConfigSection s) {
		return new SwordBodyConfig(
			s.number("seconds", " How long the blades stay through his body.", DEFAULTS.seconds, 0.05, LONGEST_SECONDS),
			s.number("thorns_percent", " Share of the damage a melee attacker deals that is dealt back to them.", DEFAULTS.thornsPercent, 0.0, Percent.WHOLE),
			s.number("reduction_percent", " Share of all incoming damage the blades take off.", DEFAULTS.reductionPercent, 0.0, Percent.WHOLE),
			s.integer("regeneration_amplifier", " Regeneration amplifier while the blades stay (0 = I).", DEFAULTS.regenerationAmplifier, 0, HIGHEST_AMPLIFIER));
	}
}
