package io.github.cwzmorro.fatecraft.artoria;

import io.github.cwzmorro.alayacore.config.ConfigSection;

/** Artoria's skill, Excalibur and Avalon numbers; cooldowns are in the shared ability table. */
public record ArtoriaConfig(double strikeConeDegrees, double strikeConeLength, double strikeEffectSeconds, double strikeKnockback,
	double stopDistance, double deflectRadius,
	double burstSeconds, double burstMana, double attackBonus, int speedAmplifier, double slashRange,
	double slashRadius, double slashArcDegrees, double slashSweepSeconds, double slashFadeSeconds, double shotRange, double blastRadius, double chargeSeconds,
	double effectSeconds, double excaliburDamage, double excaliburSpeed,
	double excaliburWindUpSeconds, double excaliburBeamRange, double excaliburStartRadius, double excaliburEndRadius,
	double excaliburBurstRange, double excaliburBurstRadius, double excaliburBurstHeight, double excaliburEdgeDamage,
	double excaliburCooldownSeconds, double excaliburWaveSpeed, double excaliburLightSeconds,
	double excaliburPathRadius, double excaliburPathDepth, double excaliburPathSpacing, double excaliburPathGroundReach, double excaliburPathDamageShare,
	double excaliburBurstVisualRadius, double excaliburPathScaleMin, double excaliburPathScaleMax, double excaliburPathVariation, double excaliburPathElongation,
	double excaliburPathPitScale, double excaliburPathNoiseCell, double excaliburPathRoughness,
	double excaliburPathWidth, double excaliburPathFurrowNear, double excaliburPathFurrowFar,
	double excaliburPathLeadIn, double excaliburPathHeight,
	double excaliburPathDepthGrowth, double excaliburPathPitThreshold, double excaliburPathRimVariation, double excaliburPathRimStart,
	double excaliburPathSurgeSwell, double excaliburPathMinRun,
	double avalonLootChance, int avalonPassiveAmplifier, int avalonActiveAmplifier) {
	public static final ArtoriaConfig DEFAULTS = new ArtoriaConfig(
		60, // strikeConeDegrees
		12, // strikeConeLength
		0.5, // strikeEffectSeconds
		1.5, // strikeKnockback
		1.5, // stopDistance
		3, // deflectRadius
		5, // burstSeconds
		200, // burstMana
		0.75, // attackBonus
		2, // speedAmplifier
		5, // slashRange
		2, // slashRadius
		120, // slashArcDegrees
		0.2, // slashSweepSeconds
		0.15, // slashFadeSeconds
		40, // shotRange
		4, // blastRadius
		0.75, // chargeSeconds
		0.5, // effectSeconds
		16, // excaliburDamage
		1.6, // excaliburSpeed
		4, // excaliburWindUpSeconds
		200, // excaliburBeamRange
		1.5, // excaliburStartRadius
		6, // excaliburEndRadius
		100, // excaliburBurstRange
		16, // excaliburBurstRadius
		64, // excaliburBurstHeight
		0.5, // excaliburEdgeDamage
		60, // excaliburCooldownSeconds
		100, // excaliburWaveSpeed
		1.5, // excaliburLightSeconds
		2, // excaliburPathRadius
		1, // excaliburPathDepth
		9, // excaliburPathSpacing
		8, // excaliburPathGroundReach
		0.25, // excaliburPathDamageShare
		10, // excaliburBurstVisualRadius
		0.45, // excaliburPathScaleMin
		1.35, // excaliburPathScaleMax
		0.35, // excaliburPathVariation
		1.7, // excaliburPathElongation
		1.6, // excaliburPathPitScale
		3, // excaliburPathNoiseCell
		0.6, // excaliburPathRoughness
		18, // excaliburPathWidth
		0.33, // excaliburPathFurrowNear
		0.75, // excaliburPathFurrowFar
		2, // excaliburPathLeadIn
		10, // excaliburPathHeight
		0.5, // excaliburPathDepthGrowth
		0, // excaliburPathPitThreshold
		0.15, // excaliburPathRimVariation
		0.65, // excaliburPathRimStart
		0.67, // excaliburPathSurgeSwell
		1, // excaliburPathMinRun
		0.0001, // avalonLootChance
		0, // avalonPassiveAmplifier
		2); // avalonActiveAmplifier
	// Default cooldowns in seconds, each overridable in the shared ability cooldown table.
	public static final double STRIKE_COOLDOWN = 8;
	public static final double BURST_COOLDOWN = 60;
	public static final double SLASH_COOLDOWN = 10;
	public static final double SHOT_COOLDOWN = 20;

	public static ArtoriaConfig read(ConfigSection s) {
		ArtoriaConfig d = DEFAULTS;
		return new ArtoriaConfig(
			s.number("strike_cone_degrees", " Strike Air full cone opening angle.", d.strikeConeDegrees, 1, 160),
			s.number("strike_cone_length", " Strike Air instant cone reach.", d.strikeConeLength, 0.1, 128),
			s.number("strike_effect_seconds", " Strike Air wind burst visual lifetime.", d.strikeEffectSeconds, 0.1, 10),
			s.number("strike_knockback", " Strike Air cone blast knockback strength.", d.strikeKnockback, 0.0, 16.0),
			s.number("stop_distance", " Mana Shot's sword tip, this far ahead of the eye, and its charge star's size.", d.stopDistance, 0, 8),
			s.number("deflect_radius", " Strike Air projectile deflection radius.", d.deflectRadius, 0.1, 16),
			s.number("burst_seconds", " Mana Burst duration.", d.burstSeconds, 0.05, 60),
			s.number("burst_mana", " Mana to cast Mana Burst.", d.burstMana, 0, Double.MAX_VALUE),
			s.number("attack_bonus", " Mana Burst total attack damage bonus (0.75 = +75%).", d.attackBonus, 0, 10),
			s.integer("speed_amplifier", " Mana Burst Speed amplifier (2 = III).", d.speedAmplifier, 0, 255),
			s.number("slash_range", " Mana Slash melee sector reach (not projectile travel).", d.slashRange, 0.1, 256),
			s.number("slash_radius", " Mana Slash vertical hit tolerance.", d.slashRadius, 0.1, 16),
			s.number("slash_arc_degrees", " Mana Slash full sword-sweep arc in degrees.", d.slashArcDegrees, 1, 180),
			s.number("slash_sweep_seconds", " Mana Slash side-to-side blade sweep duration.", d.slashSweepSeconds, 0.05, 10),
			s.number("slash_fade_seconds", " Mana Slash trail fade after the swing.", d.slashFadeSeconds, 0.05, 10),
			s.number("shot_range", " Mana Shot beam range.", d.shotRange, 0.1, 256),
			s.number("blast_radius", " Mana Shot impact blast radius.", d.blastRadius, 0.1, 32),
			s.number("charge_seconds", " Mana Shot light gathering duration.", d.chargeSeconds, 0.05, 10),
			s.number("effect_seconds", " Mana Shot impact light lifetime.", d.effectSeconds, 0.05, 10),
			s.number("excalibur_damage", " Excalibur total melee attack damage.", d.excaliburDamage, 0, 1024),
			s.number("excalibur_speed", " Excalibur melee attacks per second.", d.excaliburSpeed, 0.1, 20),
			s.number("excalibur_wind_up_seconds", " Excalibur light gathering time for both modes.", d.excaliburWindUpSeconds, 0.05, 30),
			s.number("excalibur_beam_range", " Excalibur Beam length.", d.excaliburBeamRange, 1, 512),
			s.number("excalibur_start_radius", " Beam radius at the sword.", d.excaliburStartRadius, 0.1, 32),
			s.number("excalibur_end_radius", " Beam radius at full reach.", d.excaliburEndRadius, 0.1, 64),
			s.number("excalibur_burst_range", " Burst crosshair targeting range.", d.excaliburBurstRange, 1, 256),
			s.number("excalibur_burst_radius", " Burst damage and excavation radius.", d.excaliburBurstRadius, 0.1, 32),
			s.number("excalibur_burst_height", " Upward burst height.", d.excaliburBurstHeight, 1, 256),
			s.number("excalibur_edge_damage", " Fraction of centre damage at the burst edge.", d.excaliburEdgeDamage, 0, 1),
			s.number("excalibur_cooldown_seconds", " Default cooldown of each Excalibur mode; ability table may override.", d.excaliburCooldownSeconds, 0, 3600),
			s.number("excalibur_wave_speed", " Golden release wave travel speed, blocks per second.", d.excaliburWaveSpeed, 1, 512),
			s.number("excalibur_light_seconds", " Upward burst light lifetime.", d.excaliburLightSeconds, 0.05, 10),
			s.number("excalibur_path_radius", " Base radius of the furrow's larger pits.", d.excaliburPathRadius, 0.1, 8),
			s.number("excalibur_path_depth", " Furrow base depth below the surface, scaled along the path.", d.excaliburPathDepth, 0.1, 4),
			s.number("excalibur_path_spacing", " Average distance between chances of a larger pit along the furrow.", d.excaliburPathSpacing, 1, 64),
			s.number("excalibur_path_ground_reach", " Maximum ground search below the release line; airborne shots do not dig far below.", d.excaliburPathGroundReach, 1, 32),
			s.number("excalibur_path_damage_share", " Fraction of NP burst damage dealt once as the front passes a target.", d.excaliburPathDamageShare, 0, 1),
			s.number("excalibur_burst_visual_radius", " Accepted burst light size, independent of damage and excavation.", d.excaliburBurstVisualRadius, 0.1, 32),
			s.number("excalibur_path_scale_min", " Furrow and pit size share near the caster.", d.excaliburPathScaleMin, 0.1, 2),
			s.number("excalibur_path_scale_max", " Furrow and pit size share near impact.", d.excaliburPathScaleMax, 0.1, 3),
			s.number("excalibur_path_variation", " Deterministic furrow depth and pit size, spacing and sideways variation.", d.excaliburPathVariation, 0, 0.8),
			s.number("excalibur_path_elongation", " Larger pit length relative to width, along travel.", d.excaliburPathElongation, 1, 4),
			s.number("excalibur_path_pit_scale", " Larger pit size relative to the furrow at the same point.", d.excaliburPathPitScale, 1, 4),
			s.number("excalibur_path_noise_cell", " Blocks between the furrow edge's noise bumps; larger is smoother.", d.excaliburPathNoiseCell, 1, 16),
			s.number("excalibur_path_roughness", " How far the furrow's edges wander, as a share of its width.", d.excaliburPathRoughness, 0, 0.9),
			s.number("excalibur_path_width", " Full width of the travelling surge as drawn; what it passes over is hit.", d.excaliburPathWidth, 1, 64),
			s.number("excalibur_path_furrow_near", " Furrow width as a share of the surge's, near the caster.", d.excaliburPathFurrowNear, 0.05, 1.5),
			s.number("excalibur_path_furrow_far", " Furrow width as a share of the surge's, at the impact.", d.excaliburPathFurrowFar, 0.05, 1.5),
			s.number("excalibur_path_lead_in", " Blocks out from the caster before the travelling surge's tearing and path damage begin; nothing under or behind her is touched.", d.excaliburPathLeadIn, 0, 32),
			s.number("excalibur_path_height", " Height of the travelling blade of light.", d.excaliburPathHeight, 1, 32),
			s.number("excalibur_path_depth_growth", " The furrow and its pits are this share deeper at the impact than at the caster.", d.excaliburPathDepthGrowth, 0, 4),
			s.number("excalibur_path_pit_threshold", " A pit chance digs a larger pit when its noise (-1 to 1) is above this; 0 digs about half.", d.excaliburPathPitThreshold, -1, 1),
			s.number("excalibur_path_rim_variation", " How far a pit's rim wanders, as a share of its radius.", d.excaliburPathRimVariation, 0, 0.9),
			s.number("excalibur_path_rim_start", " Share of a pit's radius where its rim starts to wander.", d.excaliburPathRimStart, 0, 0.99),
			s.number("excalibur_path_surge_swell", " The surge swells to its full width over this many of its half widths travelled.", d.excaliburPathSurgeSwell, 0.01, 16),
			s.number("excalibur_path_min_run", " A burst whose ground run is shorter than this, in blocks, leaves no furrow.", d.excaliburPathMinRun, 0, 32),
			s.number("avalon_loot_chance", " Chance per bonus chest or trial vault reward; once per world. 0.0001 = 1 in 10000.", d.avalonLootChance, 0, 1),
			s.integer("avalon_passive_amplifier", " Carried Avalon regeneration while Artoria is online (0 = I).", d.avalonPassiveAmplifier, 0, 255),
			s.integer("avalon_active_amplifier", " Artoria's held Avalon regeneration (2 = III).", d.avalonActiveAmplifier, 0, 255));
	}
}
