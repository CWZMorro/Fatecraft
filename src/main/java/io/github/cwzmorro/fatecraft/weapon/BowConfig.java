package io.github.cwzmorro.fatecraft.weapon;

import io.github.cwzmorro.alayacore.config.ConfigSection;

/** EMIYA's bow's numbers in {@code [fatecraft.bow]}; an Archer's draw and arrow speed are Alaya Core's {@code [archer_bows]}. */
public record BowConfig(double swordSpeedMultiplier, double arrowDamagePerSpeed, double blastDivisor,
	double caladbolgBlast, int tripleCount, double volleySpacing) {
	public static final BowConfig DEFAULTS = new BowConfig(2.0, 2.0, 4.0, 6.0, 3, 0.6);
	private static final int MAX_TRIPLE_COUNT = 64;

	public static BowConfig read(ConfigSection s) {
		return new BowConfig(
			s.number("sword_speed_multiplier", " Sword shots relative to vanilla bow speed; straight flight.", DEFAULTS.swordSpeedMultiplier, 1.0, 20.0),
			s.number("arrow_damage_per_speed", " Vanilla arrow base damage per block/tick of speed.", DEFAULTS.arrowDamagePerSpeed, 0.0, Double.MAX_VALUE),
			s.number("blast_divisor", " Full sword shot ground blast: sword attack divided by this.", DEFAULTS.blastDivisor, 0.1, Double.MAX_VALUE),
			s.number("caladbolg_blast", " Caladbolg ground blast power.", DEFAULTS.caladbolgBlast, 0.0, 100.0),
			s.integer("triple_count", " Swords one triple shot fires side by side.", DEFAULTS.tripleCount, 1, MAX_TRIPLE_COUNT),
			s.number("volley_spacing", " Blocks between the swords of a triple shot, measured at the hand.", DEFAULTS.volleySpacing, 0.0, 8.0));
	}
}
