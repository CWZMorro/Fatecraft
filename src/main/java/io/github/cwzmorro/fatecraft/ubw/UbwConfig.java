package io.github.cwzmorro.fatecraft.ubw;

import io.github.cwzmorro.alayacore.config.ConfigSection;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.fatecraft.FateConfig;

/**
 * Unlimited Blade Works' field, opening, mana and crumble numbers. The ground swords follow EMIYA's arsenal:
 * {@code groundSwordsPerTrace} for each sword he has traced, never more than {@code maxGroundSwords}.
 */
public record UbwConfig(int radius, double openingMana, double upkeepManaPerSecond, int groundSwordsPerTrace, int maxGroundSwords,
	double shatterSeconds, double aimRange, UbwOpening.Timing opening) {
	public static final UbwConfig DEFAULTS = new UbwConfig(25, 3_000.0, 25.0, 40, 400, 3.0, 128.0, new UbwOpening.Timing(30, 6, 40, 52));

	// What the config file accepts: most ground swords per traced sword and in one field, and the crumble time in seconds.
	private static final int MAX_SWORDS_PER_TRACE = 1_000;
	private static final int MAX_SWORDS_IN_FIELD = 10_000;
	private static final double MIN_SHATTER_SECONDS = 0.05;
	private static final double MAX_SHATTER_SECONDS = 60.0;
	private static final int MAX_OPENING_TICKS = 1_200;

	public UbwConfig(int radius, double openingMana, double upkeepManaPerSecond) {
		this(radius, openingMana, upkeepManaPerSecond, DEFAULTS.groundSwordsPerTrace, DEFAULTS.maxGroundSwords, DEFAULTS.shatterSeconds,
			DEFAULTS.aimRange, DEFAULTS.opening);
	}

	public UbwConfig {
		if (groundSwordsPerTrace < 0 || maxGroundSwords < 0) {
			throw new IllegalArgumentException("Invalid UBW ground sword count");
		}
		if (radius < 1) {
			throw new IllegalArgumentException("UBW radius must be positive");
		}
		if (!Double.isFinite(openingMana) || openingMana < 0 || !Double.isFinite(upkeepManaPerSecond) || upkeepManaPerSecond < 0) {
			throw new IllegalArgumentException("UBW mana costs must be finite and nonnegative");
		}
		if (!Double.isFinite(shatterSeconds) || shatterSeconds <= 0) {
			throw new IllegalArgumentException("UBW crumble time must be positive");
		}
	}

	public int shatterTicks() {
		return Durations.ticks(this.shatterSeconds);
	}

	public static UbwConfig read(ConfigSection s) {
		int charge = s.integer("opening_charge_ticks", " Ticks gathering mana before the fire ignites and the terrain swap begins.",
			DEFAULTS.opening.chargeTicks(), 1, MAX_OPENING_TICKS);
		int ignition = s.integer("opening_ignition_ticks", " Ticks for the initial ring to ignite, after the charge.",
			DEFAULTS.opening.ignitionTicks(), 1, MAX_OPENING_TICKS - 2);
		int spread = s.integer("opening_spread_ticks", " Ticks after ignition starts until the front covers the entire field; after opening_ignition_ticks.",
			Math.max(DEFAULTS.opening.spreadTicks(), ignition + 1), ignition + 1, MAX_OPENING_TICKS - 1);
		int dying = s.integer("opening_dying_ticks", " Ticks after ignition starts until the last embers die; after opening_spread_ticks.",
			Math.max(DEFAULTS.opening.dyingTicks(), spread + 1), spread + 1, MAX_OPENING_TICKS);
		return new UbwConfig(
			s.integer("radius", " Reality marble terrain radius in blocks.", DEFAULTS.radius, 1, Integer.MAX_VALUE),
			s.number("opening_mana", " Mana to open the reality marble.", DEFAULTS.openingMana, 0.0, Double.MAX_VALUE),
			s.number("upkeep_mana_per_second", " Mana per second while the reality marble stands; no duration cap.", DEFAULTS.upkeepManaPerSecond,
				0.0, Double.MAX_VALUE),
			s.integer("ground_swords_per_trace",
				" Swords standing in the field for each sword EMIYA has traced, times his own amount setting; pulled slots never regrow.",
				DEFAULTS.groundSwordsPerTrace, 0, MAX_SWORDS_PER_TRACE),
			s.integer("max_ground_swords", " Most swords standing in one field, whatever the arsenal and settings (performance).",
				DEFAULTS.maxGroundSwords, 0, MAX_SWORDS_IN_FIELD),
			s.number("shatter_seconds", " Once Enuma Elish breaks the marble: seconds it crumbles away, powerless, before it closes.",
				DEFAULTS.shatterSeconds, MIN_SHATTER_SECONDS, MAX_SHATTER_SECONDS),
			s.number("aim_range", " Blocks along the crosshair the fired swords aim at a creature, else a block.", DEFAULTS.aimRange, 1.0,
				FateConfig.MAX_REACH), new UbwOpening.Timing(charge, ignition, spread, dying));
	}
}
