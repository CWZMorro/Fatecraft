package io.github.cwzmorro.fatecraft.ubw;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;

/** A marble's sphere of blocks, from the placement, floor depth and materials the caller gives. */
public final class UbwLayout {
	public record Palette(BlockState shell, BlockState floor, BlockState interior) {
		public Palette {
			Objects.requireNonNull(shell);
			Objects.requireNonNull(floor);
			Objects.requireNonNull(interior);
			// Only the originals may hold stored items, so the marble's own blocks have no block entities.
			if (shell.hasBlockEntity() || floor.hasBlockEntity() || interior.hasBlockEntity()) {
				throw new IllegalArgumentException("UBW palette cannot contain block entities");
			}
		}
	}

	private UbwLayout() { }

	public static Map<BlockPos, BlockState> sphere(LevelHeightAccessor world, BlockPos center, UbwConfig config, int floorY, int floorDepth,
		Palette palette) {
		int radius = config.radius();
		if (floorDepth < 1 || floorDepth > 2L * radius + 1 || Math.abs((long) floorY - center.getY()) > radius) {
			throw new IllegalArgumentException("Floor must be bounded inside the sphere");
		}
		Math.toIntExact((long) center.getX() - radius);
		Math.toIntExact((long) center.getX() + radius);
		Math.toIntExact((long) center.getZ() - radius);
		Math.toIntExact((long) center.getZ() + radius);
		Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		long outer = (long) radius * radius;
		long inner = (long) (radius - 1) * (radius - 1);
		for (long dx = -radius; dx <= radius; dx++) {
			for (long dz = -radius; dz <= radius; dz++) {
				long horizontal = dx * dx + dz * dz;
				if (horizontal > outer) {
					continue;
				}
				long bottom = Math.max((long) world.getMinY(), (long) center.getY() - radius);
				long top = Math.min((long) world.getMaxY() - 1, (long) center.getY() + radius);
				for (long y = bottom; y <= top; y++) {
					long dy = y - center.getY();
					if (dy * dy > outer - horizontal) {
						continue;
					}
					BlockState state;
					if (horizontal + dy * dy > inner) {
						state = palette.shell();
					} else if (y > floorY) {
						state = palette.interior();
					} else if (y > (long) floorY - floorDepth) {
						state = palette.floor();
					} else {
						continue;
					}
					blocks.put(new BlockPos((int) (center.getX() + dx), (int) y, (int) (center.getZ() + dz)), state);
				}
			}
		}
		return blocks;
	}
}
