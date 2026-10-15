package io.github.cwzmorro.fatecraft.weapon;

import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.combat.AttackData;
import io.github.cwzmorro.alayacore.combat.AttackSource;
import io.github.cwzmorro.alayacore.combat.Attacks;
import io.github.cwzmorro.alayacore.combat.ServantBonus;
import io.github.cwzmorro.alayacore.combat.WeaponBlocks;
import io.github.cwzmorro.alayacore.combat.WeaponHits;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.alayacore.rank.RankLetter;
import io.github.cwzmorro.alayacore.shield.Shields;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Ea's compressed air: held up and spinning, Ea destroys anything within {@link EaConfig#compressedAirRadius()} blocks of
 * whoever wields it, for mana every tick until the mana runs out.
 */
public final class CompressedAir {
	// Damage per second: the EX skill base.
	private static final Rank RANK = new Rank(RankLetter.EX, 0);
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Fatecraft.id("compressed_air"));

	private CompressedAir() {
	}

	static boolean canAfford(LivingEntity wielder) {
		return AlayaMana.available(wielder) >= FateConfig.ea().compressedAirManaPerTick();
	}

	static void tick(ServerLevel level, LivingEntity wielder, int ticksHeld) {
		if (!canAfford(wielder)) {
			wielder.stopUsingItem();
			return;
		}
		AlayaMana.spend(wielder, FateConfig.ea().compressedAirManaPerTick());
		if (WeaponHits.lands(ticksHeld)) {
			Vec3 centre = wielder.getBoundingBox().getCenter();
			hitEntities(level, wielder, centre);
			if (WeaponBlocks.allowed(level)) {
				breakBlocks(level, wielder, centre);
			}
		}
	}

	private static void hitEntities(ServerLevel level, LivingEntity wielder, Vec3 centre) {
		float damage = WeaponHits.damage(AlayaConfig.ranks().skillBaseDamage(RANK) + ServantBonus.classAttack(wielder));
		DamageSource source = new AttackSource(Attacks.damageType(level, DAMAGE), wielder, wielder,
			new AttackData(wielder.getUUID(), RANK, false, false));
		double radius = FateConfig.ea().compressedAirRadius();
		List<Entity> targets = level.getEntities(wielder, wielder.getBoundingBox().inflate(radius),
			target -> target.getBoundingBox().getCenter().closerThan(centre, radius));
		for (Entity target : targets) {
			Shields.hurt(level, target, source, damage);
		}
	}

	private static void breakBlocks(ServerLevel level, LivingEntity wielder, Vec3 centre) {
		BlockPos feet = wielder.blockPosition();
		double radius = FateConfig.ea().compressedAirRadius();
		int reach = (int) Math.ceil(radius);
		List<BlockPos> blocks = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-reach, 0, -reach), feet.offset(reach, reach, reach))) {
			if (Vec3.atCenterOf(pos).closerThan(centre, radius) && WeaponBlocks.breakable(level, pos)) {
				blocks.add(pos.immutable());
			}
		}
		for (BlockPos pos : Shields.unshielded(level, centre, blocks)) {
			level.destroyBlock(pos, false, wielder);
		}
	}
}
