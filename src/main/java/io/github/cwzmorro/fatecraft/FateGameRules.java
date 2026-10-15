package io.github.cwzmorro.fatecraft;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/** Fatecraft's game rules ({@code /gamerule fatecraft:…}). */
public final class FateGameRules {
	/** The Gate of Babylon throws copies, leaving the vault as it is; off by default. */
	public static final GameRule<Boolean> GATE_OF_BABYLON_COPIES = GameRuleBuilder.forBoolean(false)
		.category(GameRuleCategory.MOBS)
		.buildAndRegister(Fatecraft.id("gate_of_babylon_copies"));

	private FateGameRules() {
	}

	public static void init() {
	}
}
