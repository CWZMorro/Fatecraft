package io.github.cwzmorro.fatecraft.weapon;

import io.github.cwzmorro.alayacore.config.ConfigSection;

public record CaliburnConfig(double damage, double speed) {
	public static final CaliburnConfig DEFAULTS = new CaliburnConfig(12.0, 1.6);

	public static CaliburnConfig read(ConfigSection s) {
		return new CaliburnConfig(
			s.number("damage", " Caliburn tooltip attack damage.", DEFAULTS.damage, 0.0, Double.MAX_VALUE),
			s.number("speed", " Caliburn attacks per second.", DEFAULTS.speed, 0.1, 20.0));
	}
}
