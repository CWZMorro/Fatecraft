package io.github.cwzmorro.fatecraft.ubw;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.marble.MarbleSphere;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** The marble's sphere, the one shape the server's terrain, the client's costs and the swept counters share. */
public final class UbwMembership {
	private static final double ENVIRONMENT_SEARCH = 256;

	private UbwMembership() { }

	public static boolean contains(BlockPos center, int radius, Vec3 point) {
		return MarbleSphere.contains(Vec3.atLowerCornerOf(center), radius, point);
	}

	public static double entry(BlockPos center, int radius, Vec3 from, Vec3 to) {
		return MarbleSphere.entry(Vec3.atLowerCornerOf(center), radius, from, to);
	}

	/** Returns whether the entity stands in its own marble, so projects for free: the client asks the synced environment. */
	public static boolean freeProjection(LivingEntity entity) {
		if (!Abilities.owns(entity, UnlimitedBladeWorks.KEY.identifier())) {
			return false;
		}
		if (entity.level() instanceof ServerLevel) {
			return UbwRuntime.inside(entity, entity.position());
		}
		for (UbwEnvironmentEntity environment : entity.level().getEntitiesOfClass(UbwEnvironmentEntity.class,
			entity.getBoundingBox().inflate(ENVIRONMENT_SEARCH))) {
			if (environment.active() && !environment.shattering() && entity.getUUID().equals(environment.owner())
				&& contains(environment.center(), environment.radius(), entity.position())) {
				return true;
			}
		}
		return false;
	}
}
