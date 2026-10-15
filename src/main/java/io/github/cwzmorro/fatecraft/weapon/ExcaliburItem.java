package io.github.cwzmorro.fatecraft.weapon;

import io.github.cwzmorro.fatecraft.FateConfig;

public final class ExcaliburItem extends ConfiguredMeleeItem {
	public ExcaliburItem(Properties properties) {
		super(properties, () -> FateConfig.artoria().excaliburDamage(), () -> FateConfig.artoria().excaliburSpeed());
	}
}
