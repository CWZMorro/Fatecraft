package io.github.cwzmorro.fatecraft.shield;

import io.github.cwzmorro.alayacore.config.ConfigSection;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.alayacore.shield.Shields;
import io.github.cwzmorro.alayacore.util.Percent;

/**
 * The {@code [fatecraft.rho_aias]} table: projection cost, the bounds and defaults of the player's distance and
 * size settings (blocks; size is across, the disc's diameter), and projectile resistance.
 */
public record RhoAiasConfig(double castCost, double minDistance, double maxDistance, double defaultDistance, double minSize,
	double maxSize, double defaultSize, double projectileDamagePercent,
	Rank blackRank, Rank bronzeRank, Rank silverRank, Rank trueNameRank, int lowerPetals, int trueNamePetals) {
	public static final RhoAiasConfig DEFAULTS = new RhoAiasConfig(700.0, 2.0, 16.0, 2.5, 5.0, 15.0, 5.5, 50.0,
		Rank.parse("B"), Rank.parse("B+"), Rank.parse("B++"), Rank.parse("A"), 4, 7);
	public static final double STEP = 0.5;

	private static final double DISTANCE_LIMIT = 16.0;
	private static final double MIN_DIAMETER = 0.2;
	private static final double MAX_DIAMETER = 2 * Shields.MAX_RADIUS;
	private static final int MAX_PETALS = 32;

	public static RhoAiasConfig read(ConfigSection s) {
		double minDistance = s.number("min_distance", " Least blocks in front of the caster's eyes the player can choose.", DEFAULTS.minDistance,
			0.0, DISTANCE_LIMIT - STEP);
		double minSize = s.number("min_size", " Least size across, in blocks, the player can choose.", DEFAULTS.minSize, MIN_DIAMETER, MAX_DIAMETER - STEP);
		double maxDistance = Math.max(s.number("max_distance", " Most blocks in front of the caster's eyes the player can choose.", DEFAULTS.maxDistance,
			0.0, DISTANCE_LIMIT), minDistance + STEP);
		double defaultDistance = s.number("default_distance", " Blocks in front of the caster's eyes for a player who never chose.",
			DEFAULTS.defaultDistance, minDistance, maxDistance);
		double maxSize = Math.max(s.number("max_size", " Most size across, in blocks, the player can choose.", DEFAULTS.maxSize,
			MIN_DIAMETER, MAX_DIAMETER), minSize + STEP);
		double defaultSize = s.number("default_size", " Size across, in blocks, for a player who never chose.", DEFAULTS.defaultSize, minSize, maxSize);
		return new RhoAiasConfig(
			s.number("cast_cost", " Mana to cast Rho Aias, at every rank.", DEFAULTS.castCost, 0.0, Double.MAX_VALUE),
			minDistance,
			maxDistance,
			Math.clamp(defaultDistance, minDistance, maxDistance),
			minSize,
			maxSize,
			Math.clamp(defaultSize, minSize, maxSize),
			s.number("projectile_damage_percent", " Damage kept from projectiles, except noble phantasms.", DEFAULTS.projectileDamagePercent,
				0.0, Percent.WHOLE),
			s.rank("black_rank", " Shield rank from the start, at Black.", DEFAULTS.blackRank),
			s.rank("bronze_rank", " Shield rank at Bronze.", DEFAULTS.bronzeRank),
			s.rank("silver_rank", " Shield rank from Silver, until true name realization.", DEFAULTS.silverRank),
			s.rank("true_name_rank", " Shield rank after true name realization.", DEFAULTS.trueNameRank),
			s.integer("lower_petals", " Gauge petals below the true-name rank.", DEFAULTS.lowerPetals, 1, MAX_PETALS),
			s.integer("true_name_petals", " Gauge petals from the true-name rank.", DEFAULTS.trueNamePetals, 1, MAX_PETALS)
		);
	}
}
