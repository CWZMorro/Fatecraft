package io.github.cwzmorro.fatecraft.trace;

import io.github.cwzmorro.alayacore.config.ConfigSection;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.alayacore.util.Percent;
import io.github.cwzmorro.fatecraft.FateConfig;

/** Projection balance in {@code [fatecraft.trace]}. */
public record TraceConfig(double pairedCost, double caladbolgCost, double attackMultiplier, int uses, double traceCost, double formationSeconds,
	double excaliburManaPercent, double excaliburDrainPercent, double excaliburGrandFloorPercent, double aimRange, int volley) {
	public static final TraceConfig DEFAULTS = new TraceConfig(20.0, 100.0, 7.0, 5, 0.0, 0.3, 99.0, 5.0, 1.0, 128.0, 3);
	private static final double MAX_FORMATION_SECONDS = 10.0;
	private static final int MAX_VOLLEY = 64;

	public int formationTicks() {
		return Durations.ticks(this.formationSeconds);
	}

	public static TraceConfig read(ConfigSection s) {
		return new TraceConfig(
			s.number("paired_cost", " Mana per Kanshou or Bakuya projected.", DEFAULTS.pairedCost, 0.0, Double.MAX_VALUE),
			s.number("caladbolg_cost", " Mana per Caladbolg projected.", DEFAULTS.caladbolgCost, 0.0, Double.MAX_VALUE),
			s.number("attack_multiplier", " Other swords: mana per point of base attack.", DEFAULTS.attackMultiplier, 0.0, Double.MAX_VALUE),
			s.integer("uses", " Uses of a projected sword, except Kanshou and Bakuya.", DEFAULTS.uses, 1, Integer.MAX_VALUE),
			s.number("trace_cost", " Mana to learn a sword: tracing is free, so zero.", DEFAULTS.traceCost, 0.0, Double.MAX_VALUE),
			s.number("formation_seconds", " Trace On: blue sword formation before firing, in seconds.", DEFAULTS.formationSeconds, 0.05, MAX_FORMATION_SECONDS),
			s.number("excalibur_mana_percent", " Projecting Excalibur costs this share of his maximum mana.", DEFAULTS.excaliburManaPercent, 0.0, Percent.WHOLE),
			s.number("excalibur_drain_percent", " After projecting Excalibur he loses this share of his maximum health every second until he dies.", DEFAULTS.excaliburDrainPercent, 0.0, Percent.WHOLE),
			s.number("excalibur_grand_floor_percent", " A Grand EMIYA is drained only down to this share of his maximum health and kept there; anyone else dies.", DEFAULTS.excaliburGrandFloorPercent, 0.0, Percent.WHOLE),
			s.number("aim_range", " Trace On: blocks along the crosshair the swords aim at a creature, else a block.", DEFAULTS.aimRange, 1.0,
				FateConfig.MAX_REACH),
			s.integer("volley", " Trace On: swords projected and shot at once.", DEFAULTS.volley, 1, MAX_VOLLEY));
	}
}
