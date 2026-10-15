package io.github.cwzmorro.fatecraft.weapon;

import io.github.cwzmorro.fatecraft.FateConfig;

/** Hrunting's reloadable melee stats; an unbreakable sword. */
public final class HruntingItem extends ConfiguredMeleeItem {
	public HruntingItem(Properties properties) {
		super(properties, () -> FateConfig.emiyaWeapons().hruntingDamage(), () -> FateConfig.emiyaWeapons().hruntingSpeed());
	}
}
