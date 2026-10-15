package io.github.cwzmorro.fatecraft.chain;

import io.github.cwzmorro.alayacore.combat.Attacks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

// Its damage type is in alayacore:ignores_defence, so armor and Protection count for little; Resistance and totems work as usual.
final class ChainDamage {
	private ChainDamage() {
	}

	static void tighten(ServerLevel level, LivingEntity caster, LivingEntity target, double amount) {
		DamageSource source = new DamageSource(Attacks.damageType(level, Chains.DAMAGE), caster);
		target.hurtServer(level, source, (float) amount);
	}
}
