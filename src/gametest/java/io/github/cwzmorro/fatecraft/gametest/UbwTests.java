package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.FateTests.check;

import io.github.cwzmorro.fatecraft.ubw.UbwLayout;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class UbwTests {
	static final int QUIET_WRITE = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
	static final UbwLayout.Palette PALETTE = new UbwLayout.Palette(Blocks.GLASS.defaultBlockState(), Blocks.DIRT.defaultBlockState(),
		Blocks.AIR.defaultBlockState());

	@FunctionalInterface
	interface Checked {
		void run() throws IOException;
	}

	private UbwTests() {
	}

	static void rejected(GameTestHelper helper, String what, Checked action) throws IOException {
		boolean rejected = false;
		try {
			action.run();
		} catch (IOException expected) {
			rejected = true;
		}
		check(helper, what, rejected);
	}

	static Map<BlockPos, BlockState> originals(ServerLevel level, Map<BlockPos, BlockState> layout) {
		Map<BlockPos, BlockState> originals = new HashMap<>();
		for (BlockPos pos : layout.keySet()) {
			originals.put(pos, level.getBlockState(pos));
		}
		return originals;
	}

	static void prepare(ServerLevel level, BlockPos center, int radius) {
		for (int x = (center.getX() - radius) >> 4; x <= (center.getX() + radius) >> 4; x++) {
			for (int z = (center.getZ() - radius) >> 4; z <= (center.getZ() + radius) >> 4; z++) {
				level.getChunk(x, z);
			}
		}
	}
}
