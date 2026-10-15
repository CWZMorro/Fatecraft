package io.github.cwzmorro.fatecraft.weapon;

import io.github.cwzmorro.alayacore.config.ConfigSection;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.fatecraft.FateConfig;
import net.minecraft.SharedConstants;

/**
 * The {@code [fatecraft.ea]} section of {@code config/alayacore.toml}: Ea's melee, compressed air, and Enuma
 * Elish's wind-up and beam shape. Enuma Elish's damage, drain and cooldown come from the rank and ability tables.
 */
public record EaConfig(double meleeDamage, double attackSpeed, double compressedAirRadius, double compressedAirManaPerSecond,
	double enumaElishSpinDegreesPerTick, double compressedAirSpinDegreesPerTick, double enumaElishWindUpSeconds, double enumaElishLength,
	double enumaElishStartRadius, double enumaElishEndRadius) {
	// A compile-time constant, so game tests can bound their time by it.
	public static final double DEFAULT_ENUMA_ELISH_WIND_UP_SECONDS = 4.0;
	public static final EaConfig DEFAULTS = new EaConfig(5.0, 1.0, 6.0, 100.0, 90.0, 90.0, DEFAULT_ENUMA_ELISH_WIND_UP_SECONDS, 200.0, 1.5, 6.0);
	private static final double MAX_WIND_UP_SECONDS = 30.0;
	private static final double MAX_RADIUS = 64.0;

	public static EaConfig read(ConfigSection s) {
		return new EaConfig(
			s.number("melee_damage", " Ea's tooltip damage, the player's own 1 included.", DEFAULTS.meleeDamage, 0.0, Double.MAX_VALUE),
			s.number("attack_speed", " Ea's attacks per second.", DEFAULTS.attackSpeed, 0.1, 20.0),
			s.number("compressed_air_radius", " Blocks around the wielder that compressed air destroys.", DEFAULTS.compressedAirRadius, 0.1, MAX_RADIUS),
			s.number("compressed_air_mana_per_second", " Mana per second while Ea's compressed air is held.", DEFAULTS.compressedAirManaPerSecond, 0.0,
				Double.MAX_VALUE),
			s.number("enuma_elish_spin_degrees_per_tick", " Degrees per tick while Enuma Elish is cast; each cylinder keeps its direction.",
				DEFAULTS.enumaElishSpinDegreesPerTick, 0.0, WeaponSpin.DEGREES_PER_TURN),
			s.number("compressed_air_spin_degrees_per_tick", " Degrees per tick while compressed air is held; each cylinder keeps its direction.",
				DEFAULTS.compressedAirSpinDegreesPerTick, 0.0, WeaponSpin.DEGREES_PER_TURN),
			s.number("enuma_elish_wind_up_seconds", " Seconds Ea spins before Enuma Elish's beam fires.", DEFAULTS.enumaElishWindUpSeconds, 0.05,
				MAX_WIND_UP_SECONDS),
			s.number("enuma_elish_length", " Enuma Elish's beam length, in blocks.", DEFAULTS.enumaElishLength, 1.0, FateConfig.MAX_REACH),
			s.number("enuma_elish_start_radius", " Enuma Elish's beam radius at Ea.", DEFAULTS.enumaElishStartRadius, 0.1, MAX_RADIUS),
			s.number("enuma_elish_end_radius", " Enuma Elish's beam radius at its far end.", DEFAULTS.enumaElishEndRadius, 0.1, MAX_RADIUS)
		);
	}

	public double compressedAirManaPerTick() {
		return this.compressedAirManaPerSecond / SharedConstants.TICKS_PER_SECOND;
	}

	public int enumaElishWindUpTicks() {
		return Durations.ticks(this.enumaElishWindUpSeconds);
	}
}
