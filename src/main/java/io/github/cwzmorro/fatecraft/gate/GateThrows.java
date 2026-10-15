package io.github.cwzmorro.fatecraft.gate;

import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.item.WeaponStats;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.phys.Vec3;

/**
 * What the Gate of Babylon throws, and how: weapons (anything that adds main-hand attack damage, and spears) as
 * {@link TreasureEntity}, arrows as a fully drawn bow's, throwables as when thrown. All fly straight.
 */
public final class GateThrows {
	enum Kind {
		WEAPON, ARROW, THROWABLE, NONE
	}

	private GateThrows() {
	}

	static Kind kind(ItemStack stack) {
		// A spear's damage is its speed's, even one that adds no attack (wooden, golden).
		if (WeaponStats.attackDamage(stack) > WeaponStats.PLAYER_ATTACK_DAMAGE || stack.has(DataComponents.KINETIC_WEAPON)) {
			return Kind.WEAPON;
		}
		if (stack.getItem() instanceof ArrowItem) {
			return Kind.ARROW;
		}
		return stack.getItem() instanceof EnderpearlItem || stack.getItem() instanceof ProjectileItem ? Kind.THROWABLE : Kind.NONE;
	}

	public static boolean weapon(ItemStack stack) {
		return kind(stack) == Kind.WEAPON;
	}

	public static boolean throwable(ItemStack stack) {
		return kind(stack) != Kind.NONE;
	}

	static void launch(ServerLevel level, ServerPlayer owner, ItemStack item, Vec3 from, Vec3 direction, int section, boolean copy) {
		Vec3 velocity = direction.normalize().scale(AlayaConfig.thrownWeapons().blocksPerTick());
		switch (kind(item)) {
			case WEAPON -> TreasureEntity.launch(level, owner, item, from, velocity, section, copy);
			case ARROW -> {
				AbstractArrow arrow = ((ArrowItem) item.getItem()).createArrow(level, item, owner, null);
				arrow.setCritArrow(true);
				launch(level, owner, arrow, item, from, velocity, section, copy);
				// After the owner is set, which lets players pick it up.
				arrow.pickup = copy ? AbstractArrow.Pickup.DISALLOWED : AbstractArrow.Pickup.ALLOWED;
			}
			case THROWABLE -> {
				Projectile projectile = item.getItem() instanceof ProjectileItem projectileItem
					? projectileItem.asProjectile(level, from, item, Direction.getApproximateNearest(velocity))
					: new ThrownEnderpearl(level, owner, item);
				projectile.setOwner(owner);
				launch(level, owner, projectile, item, from, velocity, section, copy);
			}
			case NONE -> {
				if (!copy) {
					GateReturns.give(level.getServer(), owner.getUUID(), section, item);
				}
			}
		}
	}

	private static void launch(ServerLevel level, ServerPlayer owner, Projectile projectile, ItemStack item, Vec3 from, Vec3 velocity, int section,
		boolean copy) {
		projectile.setPos(from);
		projectile.shoot(velocity.x, velocity.y, velocity.z, (float) velocity.length(), 0.0F);
		projectile.setNoGravity(true);
		GateReturns.throwing(level, projectile, owner, section, copy, item.copyWithCount(1));
		level.addFreshEntity(projectile);
	}
}
