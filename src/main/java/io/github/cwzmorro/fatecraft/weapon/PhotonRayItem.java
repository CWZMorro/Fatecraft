package io.github.cwzmorro.fatecraft.weapon;

import io.github.cwzmorro.fatecraft.FateConfig;

public final class PhotonRayItem extends ConfiguredMeleeItem {
	public PhotonRayItem(Properties properties) {
		super(properties, () -> FateConfig.altera().meleeDamage(), () -> FateConfig.altera().attackSpeed());
	}
}
