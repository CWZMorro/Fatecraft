package io.github.cwzmorro.fatecraft.weapon;

import io.github.cwzmorro.fatecraft.FateConfig;

/** Artoria's starting sword, with reloadable melee balance. */
public final class CaliburnItem extends ConfiguredMeleeItem {
	public CaliburnItem(Properties properties) {
		super(properties, () -> FateConfig.caliburn().damage(), () -> FateConfig.caliburn().speed());
	}
}
