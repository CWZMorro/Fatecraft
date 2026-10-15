package io.github.cwzmorro.fatecraft.chain;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Bound by Enkidu's chains: its icon and name while the bind holds; the bind cuts the attack itself. */
final class BoundEffect extends MobEffect {
	private static final int COLOUR = 0xE8C66A;

	BoundEffect() {
		super(MobEffectCategory.HARMFUL, COLOUR);
	}
}
