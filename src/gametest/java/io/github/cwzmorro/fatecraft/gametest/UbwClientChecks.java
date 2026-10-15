package io.github.cwzmorro.fatecraft.gametest;

import static io.github.cwzmorro.fatecraft.gametest.UbwClientTest.check;

import io.github.cwzmorro.fatecraft.ubw.UbwController;
import io.github.cwzmorro.fatecraft.ubw.UbwRuntime;
import io.github.cwzmorro.fatecraft.ubw.UbwTerrain;
import io.github.cwzmorro.fatecraft.ubw.UnlimitedBladeWorks;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class UbwClientChecks {
	static void placement(ClientGameTestContext context, TestSingleplayerContext world, BlockPos center) {
		Path[] pending = {null};
		BlockPos[] field = {center};
		world.getServer().runOnServer(server -> {
			var player = FateClientTests.player(server);
			player.setGameMode(GameType.SURVIVAL);
			player.setPos(center.getX(), center.getY(), center.getZ());
			FateTests.tap(player, UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.MARBLE, false);
			check(UbwRuntime.hasMarble(player), "Native client placement field opened");
			try {
				field[0] = UbwController.of(server).status(player.getUUID()).orElseThrow().center();
			} catch (IOException failure) {
				throw new IllegalStateException(failure);
			}
		});
		try {
			for (int phase = 0; phase < 2; phase++) {
				if (phase == 1) {
					world.getServer().runOnServer(server -> {
						try {
							Path path = UbwTerrain.of(server).checkpointPath();
							pending[0] = path.resolveSibling(path.getFileName() + ".pending");
							Files.createDirectory(pending[0]);
							UbwRuntime.cancel(FateClientTests.player(server));
							check(UbwRuntime.hasMarble(FateClientTests.player(server)) && !UbwRuntime.ready(server), "Retained closing placement field");
						} catch (IOException failure) {
							throw new IllegalStateException(failure);
						}
					});
				}
				// Cap boundary: primary door block is outside, upper half is inside the radius25 sphere.
				attempt(context, world, field[0].offset(0, -1, 0), Items.STONE, false, phase);
				attempt(context, world, field[0].offset(0, -27, 0), Items.OAK_DOOR, false, phase);
				attempt(context, world, field[0].offset(30, -1, 0), Items.STONE, true, phase);
			}
		} finally {
			world.getServer().runOnServer(server -> {
				try {
					if (pending[0] != null) {
						Files.deleteIfExists(pending[0]);
					}
				} catch (IOException failure) {
					throw new IllegalStateException(failure);
				}
				UbwRuntime.reloaded(server);
				UbwRuntime.cancel(FateClientTests.player(server));
			});
		}
	}

	private static void attempt(ClientGameTestContext context, TestSingleplayerContext world, BlockPos support, Item item, boolean allowed,
		int phase) {
		BlockPos target = support.above();
		BlockPos upper = target.above();
		BlockState[] old = new BlockState[3];
		ItemStack expected = new ItemStack(item, 3);
		expected.set(DataComponents.CUSTOM_NAME, Component.literal("ubw-client-placement-" + phase));
		Vec3 feet = Vec3.atBottomCenterOf(target).add(2, 0, 0);
		world.getServer().runOnServer(server -> {
			var player = FateClientTests.player(server);
			var level = player.level();
			old[0] = level.getBlockState(support);
			old[1] = level.getBlockState(target);
			old[2] = level.getBlockState(upper);
			level.setBlock(support, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			level.setBlock(upper, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			player.setPos(feet);
			player.getInventory().setSelectedSlot(0);
			player.getInventory().setItem(0, expected.copy());
			player.setYRot(0);
			player.containerMenu.broadcastChanges();
		});
		context.runOnClient(client -> {
			client.player.setPos(feet);
			client.player.setYRot(0);
			client.player.getInventory().setSelectedSlot(0);
		});
		context.waitTicks(12);
		context.runOnClient(client -> {
			check(ItemStack.isSameItemSameComponents(client.player.getMainHandItem(), expected) && client.player.getMainHandItem().getCount() == 3,
				"Native inventory initial sync");
			client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(support).add(0, 0.5, 0), Direction.UP, support, false));
		});
		context.waitTicks(12);
		context.runOnClient(client -> {
			ItemStack actual = client.player.getMainHandItem();
			check(actual.getCount() == (allowed ? 2 : 3) && ItemStack.isSameItemSameComponents(actual, expected),
				"Client authoritative count/components after native placement: phase=" + phase + " item=" + item + " count=" + actual.getCount());
			check(allowed ? !client.level.getBlockState(target).isAir() : client.level.getBlockState(target).isAir() && client.level.getBlockState(upper).isAir(),
				"No client ghost placement after rejection");
		});
		world.getServer().runOnServer(server -> {
			var player = FateClientTests.player(server);
			var level = player.level();
			ItemStack actual = player.getMainHandItem();
			check(actual.getCount() == (allowed ? 2 : 3) && ItemStack.isSameItemSameComponents(actual, expected),
				"Server item retained exactly without duplication");
			check(allowed ? !level.getBlockState(target).isAir() : level.getBlockState(target).isAir() && level.getBlockState(upper).isAir(),
				"Server denial precedes mutation");
			level.setBlock(upper, old[2], Block.UPDATE_ALL);
			level.setBlock(target, old[1], Block.UPDATE_ALL);
			level.setBlock(support, old[0], Block.UPDATE_ALL);
		});
	}
}
