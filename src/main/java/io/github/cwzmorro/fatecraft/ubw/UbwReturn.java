package io.github.cwzmorro.fatecraft.ubw;

import io.github.cwzmorro.fatecraft.ubw.UbwCheckpoint.Operation;
import java.io.IOException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Moves entities out of restored terrain to a safe spot, during recovery too, without loading new chunks. */
final class UbwReturn {
	private UbwReturn() { }

	static void occupants(ServerLevel level, Operation operation) throws IOException {
		AABB bounds = new AABB(operation.originals().getFirst().pos());
		for (UbwCheckpoint.Original original : operation.originals()) {
			bounds = bounds.minmax(new AABB(original.pos()));
		}
		int radius = operation.volume().isPresent() ? operation.volume().get().radius()
			: (int) Math.ceil(Math.max(bounds.getXsize(), bounds.getZsize()));
		occupants(level, bounds, radius, true);
	}

	// The marble's own scenery and ground swords never move: they go with it. Unless strict (the final restoration), an entity with no safe spot yet stays put.
	static void occupants(ServerLevel level, AABB bounds, int radius, boolean strict) throws IOException {
		for (Entity entity : level.getEntities((Entity) null, bounds, candidate -> !candidate.isRemoved()
			&& !(candidate instanceof UbwEnvironmentEntity) && !(candidate instanceof UbwGroundSwordEntity))) {
			// An entity whose spot is still clear stays, even with nothing under it.
			if (!level.getBlockCollisions(entity, entity.getBoundingBox()).iterator().hasNext()) {
				continue;
			}
			Vec3 safe = column(level, entity, entity.blockPosition());
			for (int ring = 1; safe == null && ring <= radius; ring++) {
				for (int x = -ring; safe == null && x <= ring; x++) {
					for (int z = -ring; safe == null && z <= ring; z++) {
						if (Math.abs(x) != ring && Math.abs(z) != ring) {
							continue;
						}
						safe = column(level, entity, entity.blockPosition().offset(x, 0, z));
					}
				}
			}
			if (safe == null && !strict) {
				continue;
			}
			if (safe == null) {
				throw new IOException("No loaded safe UBW return position for " + entity.getType() + " " + entity.getUUID());
			}
			entity.teleportTo(safe.x, safe.y, safe.z);
			entity.resetFallDistance();
		}
	}

	private static @Nullable Vec3 column(ServerLevel level, Entity entity, BlockPos origin) {
		for (int y = Math.max(origin.getY(), level.getMinY()); y < level.getMaxY(); y++) {
			BlockPos pos = new BlockPos(origin.getX(), y, origin.getZ());
			Vec3 centered = Vec3.atBottomCenterOf(pos);
			AABB clearance = entity.getType().getDimensions().makeBoundingBox(centered)
				.minmax(entity.getBoundingBox().move(centered.subtract(entity.position())));
			if (!loaded(level, clearance)) {
				return null;
			}
			// Vanilla checks floor height, dangerous blocks and entity-type clearance.
			Vec3 safe = DismountHelper.findSafeDismountLocation(entity.getType(), level, pos, true);
			if (safe == null) {
				continue;
			}
			AABB moved = entity.getBoundingBox().move(safe.subtract(entity.position()));
			if (moved.minY < level.getMinY() || moved.maxY > level.getMaxY() || !level.getWorldBorder().isWithinBounds(moved)) {
				continue;
			}
			if (loaded(level, moved) && level.noCollision(entity, moved)) {
				return safe;
			}
		}
		return null;
	}

	private static boolean loaded(ServerLevel level, AABB box) {
		BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
		BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
		for (int x = SectionPos.blockToSectionCoord(min.getX()); x <= SectionPos.blockToSectionCoord(max.getX()); x++) {
			for (int z = SectionPos.blockToSectionCoord(min.getZ()); z <= SectionPos.blockToSectionCoord(max.getZ()); z++) {
				if (level.getChunkSource().getChunkNow(x, z) == null) {
					return false;
				}
			}
		}
		return true;
	}
}
