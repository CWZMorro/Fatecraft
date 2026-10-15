package io.github.cwzmorro.fatecraft;

import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.config.ConfigSection;
import io.github.cwzmorro.alayacore.config.ConfigTable;
import io.github.cwzmorro.fatecraft.altera.AlteraConfig;
import io.github.cwzmorro.fatecraft.artoria.ArtoriaConfig;
import io.github.cwzmorro.fatecraft.chain.EnkiduConfig;
import io.github.cwzmorro.fatecraft.gate.GateConfig;
import io.github.cwzmorro.fatecraft.shield.RhoAiasConfig;
import io.github.cwzmorro.fatecraft.trace.SwordBodyConfig;
import io.github.cwzmorro.fatecraft.trace.TraceConfig;
import io.github.cwzmorro.fatecraft.ubw.UbwConfig;
import io.github.cwzmorro.fatecraft.vault.VaultConfig;
import io.github.cwzmorro.fatecraft.weapon.BowConfig;
import io.github.cwzmorro.fatecraft.weapon.CaliburnConfig;
import io.github.cwzmorro.fatecraft.weapon.EaConfig;
import io.github.cwzmorro.fatecraft.weapon.EmiyaWeaponsConfig;

/** Fatecraft's table, {@code [fatecraft]}, in {@code config/alayacore.toml}: one section per feature. */
public final class FateConfig {
	// The farthest reach, in blocks, the config file accepts for an aim range or a beam length.
	public static final double MAX_REACH = 512.0;

	private record Values(GateConfig gate, EnkiduConfig enkidu, EaConfig ea, RhoAiasConfig rhoAias, AlteraConfig altera, EmiyaWeaponsConfig emiyaWeapons, TraceConfig trace, BowConfig bow, CaliburnConfig caliburn, ArtoriaConfig artoria, UbwConfig ubw, SwordBodyConfig swordBody, VaultConfig vault) {
		static final Values DEFAULTS = new Values(GateConfig.DEFAULTS, EnkiduConfig.DEFAULTS, EaConfig.DEFAULTS, RhoAiasConfig.DEFAULTS, AlteraConfig.DEFAULTS, EmiyaWeaponsConfig.DEFAULTS, TraceConfig.DEFAULTS, BowConfig.DEFAULTS, CaliburnConfig.DEFAULTS, ArtoriaConfig.DEFAULTS, UbwConfig.DEFAULTS, SwordBodyConfig.DEFAULTS, VaultConfig.DEFAULTS);

		static Values read(ConfigSection s) {
			return new Values(GateConfig.read(s.section("gate_of_babylon", null)), EnkiduConfig.read(s.section("enkidu", null)),
				EaConfig.read(s.section("ea", null)), RhoAiasConfig.read(s.section("rho_aias", null)), AlteraConfig.read(s.section("altera", null)),
				EmiyaWeaponsConfig.read(s.section("emiya_weapons", null)), TraceConfig.read(s.section("trace", null)), BowConfig.read(s.section("bow", null)), CaliburnConfig.read(s.section("caliburn", null)), ArtoriaConfig.read(s.section("artoria", null)), UbwConfig.read(s.section("unlimited_blade_works", null)), SwordBodyConfig.read(s.section("sword_body", null)), VaultConfig.read(s.section("vault", null)));
		}
	}

	private static final ConfigTable<Values> TABLE = AlayaConfig.register(Fatecraft.MOD_ID, " Fatecraft's settings.", Values.DEFAULTS, Values::read);

	private FateConfig() {
	}

	public static GateConfig gate() {
		return TABLE.get().gate();
	}

	public static EnkiduConfig enkidu() {
		return TABLE.get().enkidu();
	}

	public static EaConfig ea() {
		return TABLE.get().ea();
	}

	public static RhoAiasConfig rhoAias() {
		return TABLE.get().rhoAias();
	}

	public static AlteraConfig altera() {
		return TABLE.get().altera();
	}

	public static EmiyaWeaponsConfig emiyaWeapons() {
		return TABLE.get().emiyaWeapons();
	}

	public static TraceConfig trace() {
		return TABLE.get().trace();
	}

	public static BowConfig bow() {
		return TABLE.get().bow();
	}

	public static CaliburnConfig caliburn() {
		return TABLE.get().caliburn();
	}

	public static ArtoriaConfig artoria() {
		return TABLE.get().artoria();
	}

	public static UbwConfig ubw() {
		return TABLE.get().ubw();
	}

	public static SwordBodyConfig swordBody() {
		return TABLE.get().swordBody();
	}

	public static VaultConfig vault() {
		return TABLE.get().vault();
	}

	/** Loads this class, which registers the table. */
	public static void init() {
	}
}
