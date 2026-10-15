package io.github.cwzmorro.fatecraft.chain;

import io.github.cwzmorro.alayacore.config.ConfigSection;
import io.github.cwzmorro.fatecraft.FateConfig;
import net.minecraft.SharedConstants;
import net.minecraft.util.Mth;

/**
 * The {@code [fatecraft.enkidu]} section of {@code config/alayacore.toml}. The chain count is each player's own, set in the
 * vault screen.
 */
public record EnkiduConfig(double bindMana, double tightenManaPerSecond, double firstTighten, double tightenPerSecond, double chainHpAtNoSync,
	double chainHpAtFullSync, double chainHpPerDivinity, double tightenPerDivinity, double boundAttackKept,
	double aimRange, double nearestRange, double noAttackStrain) {
	public static final EnkiduConfig DEFAULTS = new EnkiduConfig(500.0, 50.0, 19.0, 14.0, 100.0, 500.0, 1.4, 1.15, 0.65, 48.0, 26.0, 0.1);
	private static final double MAX_PER_DIVINITY = 10.0;

	public static EnkiduConfig read(ConfigSection s) {
		return new EnkiduConfig(
			s.number("bind_mana", " Mana to bind.", DEFAULTS.bindMana, 0.0, Double.MAX_VALUE),
			s.number("tighten_mana_per_second", " Mana a second while tightening.", DEFAULTS.tightenManaPerSecond, 0.0, Double.MAX_VALUE),
			s.number("first_tighten", " The first tightening hit, against no divinity.", DEFAULTS.firstTighten, 0.0, Double.MAX_VALUE),
			s.number("tighten_per_second", " Then one hit of this every second, against no divinity.", DEFAULTS.tightenPerSecond, 0.0, Double.MAX_VALUE),
			s.number("chain_hp_at_no_sync", " The chain's HP, against no divinity, at Gilgamesh's 0% sync…", DEFAULTS.chainHpAtNoSync, 1.0,
				Double.MAX_VALUE),
			s.number("chain_hp_at_full_sync", " …and at 100% sync, in between in step with sync.", DEFAULTS.chainHpAtFullSync, 1.0, Double.MAX_VALUE),
			s.number("chain_hp_per_divinity", " The chain's HP is multiplied by this to the power of the target's divinity.", DEFAULTS.chainHpPerDivinity,
				1.0, MAX_PER_DIVINITY),
			s.number("tighten_per_divinity", " The tightening, and the cut to the bound target's attack, by this to the power of its divinity.",
				DEFAULTS.tightenPerDivinity, 1.0, MAX_PER_DIVINITY),
			s.number("bound_attack_kept", " Share of its attack damage a bound target with no divinity keeps.", DEFAULTS.boundAttackKept, 0.0, 1.0),
			s.number("aim_range", " Blocks along the crosshair a target is bound at…", DEFAULTS.aimRange, 1.0, FateConfig.MAX_REACH),
			s.number("nearest_range", " …else the nearest within this many blocks.", DEFAULTS.nearestRange, 1.0, FateConfig.MAX_REACH),
			s.number("no_attack_strain", " A bound target with no attack wears the chain down by this share of its max health instead.",
				DEFAULTS.noAttackStrain, 0.0, 1.0)
		);
	}

	/** Returns the chain's HP binding a target of {@code divinity}, for a caster at {@code sync} (0 to 1). */
	public double chainHp(double sync, double divinity) {
		return Mth.lerp(Mth.clamp(sync, 0.0, 1.0), this.chainHpAtNoSync, this.chainHpAtFullSync) * Math.pow(this.chainHpPerDivinity, divinity);
	}

	public double tighten(boolean first, double divinity) {
		return (first ? this.firstTighten : this.tightenPerSecond) * Math.pow(this.tightenPerDivinity, divinity);
	}

	public double attackKept(double divinity) {
		return this.boundAttackKept / Math.pow(this.tightenPerDivinity, divinity);
	}

	public double tightenManaPerTick() {
		return this.tightenManaPerSecond / SharedConstants.TICKS_PER_SECOND;
	}
}
