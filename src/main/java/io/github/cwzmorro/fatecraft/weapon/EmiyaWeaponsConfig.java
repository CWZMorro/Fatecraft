package io.github.cwzmorro.fatecraft.weapon;

import io.github.cwzmorro.alayacore.config.ConfigSection;

/** EMIYA's item balance in {@code [fatecraft.emiya_weapons]}, not projection or bow abilities. */
public record EmiyaWeaponsConfig(double singleDamage, double singleSpeed, double pairedDamage, double pairedSpeed,
	double caladbolgDamage, double caladbolgSpeed, double hruntingDamage, double hruntingSpeed, double pairedTargetRange, double pairedThrowSpeed) {
	public static final EmiyaWeaponsConfig DEFAULTS = new EmiyaWeaponsConfig(6.0, 1.6, 10.0, 3.2, 12.0, 1.6, 11.0, 1.6, 32.0, 3.0);

	public static EmiyaWeaponsConfig read(ConfigSection s) {
		return new EmiyaWeaponsConfig(
			s.number("single_damage", " Kanshou or Bakuya alone: tooltip attack damage.", DEFAULTS.singleDamage, 0.0, Double.MAX_VALUE),
			s.number("single_speed", " Kanshou or Bakuya alone: attacks per second.", DEFAULTS.singleSpeed, 0.1, 20.0),
			s.number("paired_damage", " Each sword with its partner in the other hand: tooltip damage.", DEFAULTS.pairedDamage, 0.0, Double.MAX_VALUE),
			s.number("paired_speed", " Each sword with its partner: attacks per second.", DEFAULTS.pairedSpeed, 0.1, 20.0),
			s.number("caladbolg_damage", " Caladbolg: tooltip attack damage.", DEFAULTS.caladbolgDamage, 0.0, Double.MAX_VALUE),
			s.number("caladbolg_speed", " Caladbolg: attacks per second.", DEFAULTS.caladbolgSpeed, 0.1, 20.0),
			s.number("hrunting_damage", " Hrunting: tooltip attack damage.", DEFAULTS.hruntingDamage, 0.0, Double.MAX_VALUE),
			s.number("hrunting_speed", " Hrunting: attacks per second.", DEFAULTS.hruntingSpeed, 0.1, 20.0),
			s.number("paired_target_range", " Thrown Kanshou and Bakuya: how far, in blocks, the creature under the crosshair is that they curve"
				+ " in from both sides to hit, crossing in an X; with none, they fly straight.", DEFAULTS.pairedTargetRange, 0.0, Double.MAX_VALUE),
			s.number("paired_throw_speed", " Thrown Kanshou and Bakuya: how fast they fly, in blocks a tick.", DEFAULTS.pairedThrowSpeed, 0.1, 10.0));
	}
}
