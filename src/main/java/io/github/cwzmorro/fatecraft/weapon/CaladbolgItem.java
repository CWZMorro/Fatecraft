package io.github.cwzmorro.fatecraft.weapon;

import io.github.cwzmorro.fatecraft.FateConfig;

/** Caladbolg's configurable attack; no enchantability or durability component. */
public final class CaladbolgItem extends ConfiguredMeleeItem {
	public CaladbolgItem(Properties properties) {
		super(properties, () -> FateConfig.emiyaWeapons().caladbolgDamage(), () -> FateConfig.emiyaWeapons().caladbolgSpeed());
	}
}
