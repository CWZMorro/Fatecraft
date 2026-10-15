package io.github.cwzmorro.fatecraft.altera;

import io.github.cwzmorro.alayacore.config.ConfigSection;
import io.github.cwzmorro.fatecraft.weapon.WeaponSpin;

/** Altera's configurable skill, sword, dash and sky-strike numbers. Rank damage and mana costs live in ranks. */
public record AlteraConfig(double meleeDamage, double attackSpeed, double crestSeconds, double crestMana, double crestAttackSpeedPercent,
	int speedAmplifier, int passiveAmplifier, double chargeSeconds, double dashBlocksPerSecond, double hitRadius,
	double frontReach, double frontDamageMultiplier, double knockbackDistance,
	double spinDegreesPerTick,
	double teardropWindUpSeconds, double teardropHeight, double teardropRadius, double teardropRange,
	double teardropDigBlocksPerSecond, double coneHpFactor, double beamDashSpeedFactor,
	double spreadRadius, double spreadImpactRadius, double spreadDamageMultiplier, double spreadFlareShare, double spreadDigBlocksPerSecond,
	int teardropBlocksPerTick, double coneLength, double coneRadius, double teardropSealShare, double teardropSideSealSpacing,
	double teardropSkyOffset) {
	public static final AlteraConfig DEFAULTS = new AlteraConfig(
		14.0, // meleeDamage
		2.0, // attackSpeed
		25.0, // crestSeconds
		90.0, // crestMana
		30.0, // crestAttackSpeedPercent
		1, // speedAmplifier
		0, // passiveAmplifier
		0.5, // chargeSeconds
		20.0, // dashBlocksPerSecond
		1.0, // hitRadius
		3.0, // frontReach
		1.0, // frontDamageMultiplier
		3.0, // knockbackDistance
		90.0, // spinDegreesPerTick
		4.0, // teardropWindUpSeconds
		80.0, // teardropHeight
		3.0, // teardropRadius
		128.0, // teardropRange
		2.0, // teardropDigBlocksPerSecond
		0.75, // coneHpFactor
		0.5, // beamDashSpeedFactor
		16.0, // spreadRadius
		20.0, // spreadImpactRadius
		0.2, // spreadDamageMultiplier
		0.4, // spreadFlareShare
		0.5, // spreadDigBlocksPerSecond
		2048, // teardropBlocksPerTick
		5.0, // coneLength
		2.1, // coneRadius
		1.2, // teardropSealShare
		2.05, // teardropSideSealSpacing
		6.0); // teardropSkyOffset
	// The cooldowns are overridable in the shared ability cooldown table.
	public static final double DASH_COOLDOWN_SECONDS = 0.0;
	public static final double TEARDROP_COOLDOWN_SECONDS = 60.0;
	public static final double CREST_COOLDOWN_SECONDS = 60.0;

	public static AlteraConfig read(ConfigSection s) {
		return new AlteraConfig(
			s.number("melee_damage", " Photon Ray's tooltip damage.", DEFAULTS.meleeDamage, 0.0, Double.MAX_VALUE),
			s.number("attack_speed", " Photon Ray's attacks per second.", DEFAULTS.attackSpeed, 0.1, 20.0),
			s.number("crest_seconds", " Crest of the Star duration.", DEFAULTS.crestSeconds, 0.05, Double.MAX_VALUE),
			s.number("crest_mana", " Mana to cast Crest of the Star.", DEFAULTS.crestMana, 0.0, Double.MAX_VALUE),
			s.number("crest_attack_speed_percent", " Crest of the Star's bonus on total attack speed, in percent.", DEFAULTS.crestAttackSpeedPercent, 0.0, 1000.0),
			s.integer("speed_amplifier", " Crest's Speed amplifier (0 is I).", DEFAULTS.speedAmplifier, 0, 255),
			s.integer("passive_amplifier", " Natural Body's Regeneration and Strength amplifier (0 is I).", DEFAULTS.passiveAmplifier, 0, 255),
			s.number("charge_seconds", " Hold this long before releasing Photon Ray.", DEFAULTS.chargeSeconds, 0.05, 60.0),
			s.number("dash_blocks_per_second", " Speed while Photon Ray moves her forward, in blocks a second.", DEFAULTS.dashBlocksPerSecond, 0.1, 200.0),
			s.number("hit_radius", " Radius of the swept dash hit.", DEFAULTS.hitRadius, 0.1, 8.0),
			s.number("front_reach", " Continuous damage reach in front of the sword.", DEFAULTS.frontReach, 0.1, 16.0),
			s.number("front_damage_multiplier", " Multiplies rank A beam damage per tick.", DEFAULTS.frontDamageMultiplier, 0.0, Double.MAX_VALUE),
			s.number("knockback_distance", " About how far, in blocks, Photon Ray knocks back what it hits (once each).", DEFAULTS.knockbackDistance, 0.0, 32.0),
			s.number("spin_degrees_per_tick", " Sword and cone-strand rotation during Photon Ray charge and dash.", DEFAULTS.spinDegreesPerTick, 0.0, WeaponSpin.DEGREES_PER_TURN),
			s.number("teardrop_wind_up_seconds", " Held wind-up before the sky strike.", DEFAULTS.teardropWindUpSeconds, 0.05, 60.0),
			s.number("teardrop_height", " Circles above the aimed point, in blocks.", DEFAULTS.teardropHeight, 1.0, 128.0),
			s.number("teardrop_radius", " Sky strike damage and excavation radius.", DEFAULTS.teardropRadius, 0.1, 16.0),
			s.number("teardrop_range", " Maximum crosshair aiming range.", DEFAULTS.teardropRange, 1.0, 256.0),
			s.number("teardrop_dig_blocks_per_second", " Excavation depth per second while firing.", DEFAULTS.teardropDigBlocksPerSecond, 0.0, 20.0),
			s.number("cone_hp_factor", " Dash cone HP as a share of rank A NP base damage.", DEFAULTS.coneHpFactor, 0.01, Double.MAX_VALUE),
			s.number("beam_dash_speed_factor", " Dash speed share while a beam presses on the cone.", DEFAULTS.beamDashSpeedFactor, 0.0, 1.0),
			s.number("spread_radius", " Spread scale used for circle artwork; the merged beam radius is derived from all three circles.", DEFAULTS.spreadRadius, 0.1, 64.0),
			s.number("spread_impact_radius", " Minimum splash radius per circle, merged into one ground blast from the circle spacing. No block breaking outside the beam.", DEFAULTS.spreadImpactRadius, 0.1, 80.0),
			s.number("spread_damage_multiplier", " Spread damage per second relative to Condensed Teardrop.", DEFAULTS.spreadDamageMultiplier, 0.0, Double.MAX_VALUE),
			s.number("spread_flare_share", " Share of the seal-to-ground drop over which Spread flares to full width, then falls straight.", DEFAULTS.spreadFlareShare, 0.01, 1.0),
			s.number("spread_dig_blocks_per_second", " Spread's excavation depth per second while firing.", DEFAULTS.spreadDigBlocksPerSecond, 0.0, 20.0),
			s.integer("teardrop_blocks_per_tick", " Most blocks Teardrop changes in one tick (digging and boiling water); the rest follow on later ticks, highest first.",
				DEFAULTS.teardropBlocksPerTick, 1, Integer.MAX_VALUE),
			s.number("cone_length", " Photon Ray's cone shield (and its light): length back from the sword tip, in blocks.", DEFAULTS.coneLength, 0.5, 16.0),
			s.number("cone_radius", " Photon Ray's cone shield (and its light): radius at its broad end, in blocks.", DEFAULTS.coneRadius, 0.5, 8.0),
			s.number("teardrop_seal_share", " Teardrop's circle radius as a share of the larger of its two beam radii; with the spacing below it sets Spread's footprint.",
				DEFAULTS.teardropSealShare, 0.1, 8.0),
			s.number("teardrop_side_seal_spacing", " Distance from the middle circle to each side circle, in circle radii.", DEFAULTS.teardropSideSealSpacing, 0.0,
				16.0),
			s.number("teardrop_sky_offset", " Blocks the circles stand to the side of the aimed point, so the beams fall at a slant.", DEFAULTS.teardropSkyOffset,
				0.0, 64.0));
	}
}
