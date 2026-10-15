package io.github.cwzmorro.fatecraft.gametest;

import com.mojang.authlib.GameProfile;
import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.progression.TrueName;
import io.github.cwzmorro.alayacore.servant.ServantChoice;
import io.github.cwzmorro.alayacore.servant.ServantStorage;
import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.client.ubw.UbwEnvironmentRenderer;
import io.github.cwzmorro.fatecraft.client.ubw.UbwFireRing;
import io.github.cwzmorro.fatecraft.gate.GateEntity;
import io.github.cwzmorro.fatecraft.gate.GateOfBabylon;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.ubw.Ubw;
import io.github.cwzmorro.fatecraft.ubw.UbwConfig;
import io.github.cwzmorro.fatecraft.ubw.UbwController;
import io.github.cwzmorro.fatecraft.ubw.UbwEnvironmentEntity;
import io.github.cwzmorro.fatecraft.ubw.UbwGroundSwordEntity;
import io.github.cwzmorro.fatecraft.ubw.UbwLayout;
import io.github.cwzmorro.fatecraft.ubw.UbwMembership;
import io.github.cwzmorro.fatecraft.ubw.UbwRuntime;
import io.github.cwzmorro.fatecraft.ubw.UbwSwords;
import io.github.cwzmorro.fatecraft.ubw.UnlimitedBladeWorks;
import io.github.cwzmorro.fatecraft.vault.Vault;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class UbwClientTest implements FabricClientGameTest {
	private static final int FOV = 76;
	static final Vec3 OVERLOOK = new Vec3(-9, 8, -17);
	static final Vec3 FIELD = new Vec3(3, 0, 4);
	static final Vec3 EYE_LEVEL = new Vec3(4, 1.6, -8);
	static final Vec3 EYE_LEVEL_LOOK = new Vec3(4, 1.0, 14);

	static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	static void stage(ClientGameTestContext context, TestSingleplayerContext world) {
		context.getInput().resizeWindow(1440, 1080);
		context.runOnClient(client -> {
			client.options.hideGui = true;
			client.getToastManager().clear();
		});
		FateClientTests.clearNoon(world);
		world.getServer().runCommand("fill -36 149 -24 36 149 50 minecraft:grass_block");
		world.getServer().runCommand("forceload add -64 -64 64 64");
	}

	static void finishOpening(ServerPlayer player) {
		try {
			UbwRuntime.finishOpening(player);
		} catch (IOException failure) {
			throw new UncheckedIOException(failure);
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.EMIYA);
			stage(context, world);
			world.getServer().runCommand("tp @p 0 150 0 0 0");
			BlockPos[] center = {BlockPos.ZERO};
			Map<BlockPos, BlockState> originals = new HashMap<>();
			BlockPos[] chest = {BlockPos.ZERO};
			Vec3[] caster = {Vec3.ZERO};
			world.getServer().runOnServer(server -> {
				var player = FateClientTests.player(server);
				player.setNoGravity(true);
				TrueName.realize(player);
				FateClientTests.giveMana(player);
				caster[0] = player.position();
				check(FateConfig.ubw().radius() == 25 && FateConfig.ubw().groundSwordsPerTrace() == UbwConfig.DEFAULTS.groundSwordsPerTrace()
					&& FateConfig.ubw().maxGroundSwords() == UbwConfig.DEFAULTS.maxGroundSwords(),
					"This test needs the default UBW radius and ground sword counts");
				check(Abilities.owns(player, UnlimitedBladeWorks.KEY.identifier()) && UbwRuntime.ready(server),
					"UBW must be owned and ready");
				center[0] = UbwController.placement(player, FateConfig.ubw());
				var level = player.level();
				chest[0] = center[0].offset(-3, 0, 4);
				level.setBlock(chest[0], Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
				((ChestBlockEntity) level.getBlockEntity(chest[0])).setItem(0, new ItemStack(Items.DIAMOND, 3));
				originals.putAll(UbwTests.originals(level, UbwLayout.sphere(level, center[0], FateConfig.ubw(), 149, 1, Ubw.palette())));
			});
			Vec3 centre = Vec3.atLowerCornerOf(center[0]);
			Vec3 inside = centre.add(0, 3.4, -10);
			Vec3 look = centre.add(0, 6, 12);
			Vec3 outside = centre.add(34, 7, -10);
			Vec3 outsideLook = outside.add(20, 2, 16);
			AABB bounds = new AABB(centre.subtract(30, 30, 30), centre.add(30, 30, 30));
			shot(context, inside, look, "ubw-01-before-inside-camera");
			shot(context, outside, outsideLook, "ubw-02-before-outside-camera");
			shot(context, outside, centre.add(0, 3, 0), "ubw-02b-before-outside-toward-field");
			world.getServer().runCommand("tick freeze");
			// Open through the real ability and input path; the freeze makes the opening shots repeatable.
			world.getServer().runOnServer(server -> {
				long began = System.nanoTime();
				FateTests.tap(FateClientTests.player(server), UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.MARBLE, false);
				check(UbwMembership.freeProjection(FateClientTests.player(server)), "NP must construct a live marble");
				Fatecraft.LOGGER.info("UBW default-radius client construction: {} ms", (System.nanoTime() - began) / 1_000_000.0);
			});
			context.waitFor(client -> !client.level.getEntitiesOfClass(UbwEnvironmentEntity.class, bounds).isEmpty());
			double[] frozenMana = {0};
			world.getServer().runOnServer(server -> {
				var player = FateClientTests.player(server);
				AlayaMana.update(player, data -> data.withCurrent(data.max()));
				frozenMana[0] = AlayaMana.get(player).current();
			});
			context.waitTicks(48);
			world.getServer().runOnServer(server ->
				check(AlayaMana.get(FateClientTests.player(server)).current() == frozenMana[0], "Frozen callbacks must not charge UBW upkeep"));
			Vec3 ignition = caster[0].add(3.5, 2.2, 4.5);
			Vec3 feet = caster[0].add(0, 0.6, 0);
			Vec3 overlook = centre.add(OVERLOOK);
			Vec3 field = centre.add(FIELD);
			int igniting = UbwConfig.DEFAULTS.opening().ignitionTicks() / 2;
			int half = UbwConfig.DEFAULTS.opening().spreadTicks() / 2;
			int most = UbwConfig.DEFAULTS.opening().spreadTicks() * 4 / 5;
			step(context, world, UbwConfig.DEFAULTS.opening().chargeTicks() + igniting);
			shot(context, ignition, feet, "ubw-03-opening-ignition");
			step(context, world, half - igniting);
			shot(context, overlook, field, "ubw-04-opening-mid");
			step(context, world, most - half);
			shot(context, overlook, field, "ubw-05-opening-spread");
			step(context, world, UbwConfig.DEFAULTS.opening().dyingTicks() - most);
			int[] count = {0};
			world.getServer().runOnServer(server -> {
				count[0] = UbwRuntime.groundSwords(FateClientTests.player(server), FateConfig.ubw());
				check(server.overworld().getEntitiesOfClass(UbwGroundSwordEntity.class, bounds).size() == count[0],
					count[0] + " real ground slots, the arsenal's");
			});
			context.waitFor(client -> client.level.getEntitiesOfClass(UbwGroundSwordEntity.class, bounds).size() == count[0]);
			shot(context, overlook, field, "ubw-05b-opening-complete");
			shot(context, inside, look, "ubw-06-inside-clouds-gears-swords");
			shot(context, outside, outsideLook, "ubw-07-outside-unaffected");
			shot(context, outside, centre.add(0, 3, 0), "ubw-07b-outside-toward-field");
			context.runOnClient(client -> {
				var environment = client.level.getEntitiesOfClass(UbwEnvironmentEntity.class, bounds).getFirst();
				check(!UbwEnvironmentRenderer.visible(environment, client.gameRenderer.getMainCamera().position()),
					"Outside camera must not submit UBW scenery");
			});
			shot(context, inside, look, "ubw-08-late-reentry-no-fire");
			context.runOnClient(client -> {
				var environment = client.level.getEntitiesOfClass(UbwEnvironmentEntity.class, bounds).getFirst();
				check(UbwEnvironmentRenderer.visible(environment, client.gameRenderer.getMainCamera().position()), "Reentered camera sees the field");
				check(UbwFireRing.progress(client.level.getGameTime() - environment.openedAt(), environment.openingTiming()) == 1, "Late entry cannot replay opening");
			});
			// A joined guest player over a mock connection, as GameTestHelper makes them, so no second client is needed.
			ServerPlayer[] guest = {null};
			EmbeddedChannel[] channel = {null};
			world.getServer().runOnServer(server -> {
				var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "ubw-guest"), false);
				guest[0] = new ServerPlayer(server, server.overworld(), cookie.gameProfile(), cookie.clientInformation());
				var connection = new Connection(PacketFlow.SERVERBOUND);
				// Consume this headless NPC's outbound packets and answer native keepalives; screenshots can take minutes.
				channel[0] = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
					@Override
					public void write(ChannelHandlerContext handler, Object packet, ChannelPromise promise) {
						if (packet instanceof ClientboundKeepAlivePacket keepalive) {
							server.execute(() -> guest[0].connection.handleKeepAlive(new ServerboundKeepAlivePacket(keepalive.getId())));
						}
						ReferenceCountUtil.release(packet);
						promise.setSuccess();
					}
				}, connection);
				server.getPlayerList().placeNewPlayer(connection, guest[0], cookie);
				guest[0].connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
				FateTests.human(guest[0]);
				guest[0].setGameMode(GameType.SURVIVAL);
				guest[0].setNoGravity(true);
			});
			try {
				Vec3[] pulled = {Vec3.ZERO};
				UUID[] slot = {null};
				world.getServer().runOnServer(server -> {
					var swords = server.overworld().getEntitiesOfClass(UbwGroundSwordEntity.class, bounds);
					var sword = swords.stream().min(Comparator.comparingDouble(candidate -> candidate.position().distanceToSqr(centre))).orElseThrow();
					pulled[0] = sword.position();
					slot[0] = sword.getUUID();
					guest[0].snapTo(pulled[0].add(1, 0, 0), 90, 0);
					for (int inventorySlot = 0; inventorySlot < guest[0].getInventory().getContainerSize(); inventorySlot++) {
						guest[0].getInventory().setItem(inventorySlot, new ItemStack(Items.DIRT, 64));
					}
					sword.interact(guest[0], InteractionHand.MAIN_HAND, Vec3.ZERO);
					check(!sword.isRemoved(), "Full human inventory must retain the slot");
					guest[0].getInventory().clearContent();
					sword.interact(guest[0], InteractionHand.MAIN_HAND, Vec3.ZERO);
					sword.interact(guest[0], InteractionHand.MAIN_HAND, Vec3.ZERO);
					int received = guest[0].getInventory().countItem(sword.item().getItem());
					check(sword.isRemoved() && received == 1, "Human pull yields exactly one projection, never duplicates");
				});
				context.waitFor(client -> client.level.getEntitiesOfClass(UbwGroundSwordEntity.class, bounds).stream()
					.noneMatch(sword -> sword.getUUID().equals(slot[0])));
				shot(context, pulled[0].add(3, 2.4, -4), pulled[0].add(0, 0.5, 0), "ubw-09-pulled-slot");
				world.getServer().runOnServer(server -> {
					guest[0].snapTo(centre.add(34, 0, 0), 90, 0);
					var enemy = EntityType.HUSK.create(server.overworld(), EntitySpawnReason.TRIGGERED);
					check(enemy != null, "Husk fixture must exist");
					enemy.setNoAi(true);
					enemy.setNoGravity(true);
					enemy.setInvulnerable(true);
					enemy.setPos(centre.add(5, 0, 7));
					server.overworld().addFreshEntity(enemy);
				});
				step(context, world, 121);
				world.getServer().runOnServer(server -> check(server.overworld().getEntitiesOfClass(ThrownWeaponEntity.class, bounds).isEmpty(),
					"No autonomous offense over three intervals with a live hostile"));
				context.waitTicks(48);
				world.getServer().runOnServer(server -> {
					check(server.overworld().getEntitiesOfClass(UbwGroundSwordEntity.class, bounds).size() == count[0] - 1,
						"Pulled slot must not regrow");
					check(server.overworld().getEntitiesOfClass(ThrownWeaponEntity.class, bounds).isEmpty(),
						"Frozen callbacks must not summon offensive copies");
				});
				shot(context, centre.add(12, 5, -3), centre.add(5, 5, 7), "ubw-10-hostile-no-autonomous-offense");
				world.getServer().runCommand("kill @e[type=husk]");
				world.getServer().runOnServer(server -> server.overworld().getEntitiesOfClass(ThrownWeaponEntity.class, bounds)
					.forEach(ThrownWeaponEntity::discard));
				FateClientTests.tap(world, UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.AOE_THROW);
				Map<UUID, Integer> frozenFormation = new HashMap<>();
				world.getServer().runOnServer(server -> server.overworld().getEntitiesOfClass(ThrownWeaponEntity.class, bounds)
					.forEach(copy -> frozenFormation.put(copy.getUUID(), copy.formationRemaining())));
				context.waitTicks(48);
				world.getServer().runOnServer(server -> {
					var copies = server.overworld().getEntitiesOfClass(ThrownWeaponEntity.class, bounds);
					check(copies.size() == frozenFormation.size() && copies.stream().allMatch(copy -> copy.heldProjection()
						&& frozenFormation.get(copy.getUUID()) != null && frozenFormation.get(copy.getUUID()) == copy.formationRemaining()),
						"Frozen manual formation must not progress or release");
				});
				step(context, world, GateOfBabylon.VOLLEY_OPENS_OVER);
				world.getServer().runOnServer(server ->
					check(!server.overworld().getEntitiesOfClass(ThrownWeaponEntity.class, bounds).isEmpty(), "Actual NP manual sword formation"));
				shot(context, centre.add(9, 4, -16), centre.add(0, 2, -12), "ubw-11-manual-blue-formation");
				FateClientTests.tap(world, UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.AOE_THROW);
				context.waitTicks(48);
				world.getServer().runOnServer(server -> check(server.overworld().getEntitiesOfClass(ThrownWeaponEntity.class, bounds).stream()
					.allMatch(ThrownWeaponEntity::heldProjection), "Frozen queued manual fire cannot launch"));
				step(context, world, FateConfig.trace().formationTicks() + 2);
				shot(context, inside, look, "ubw-12-manual-fired");
				world.getServer().runOnServer(server -> {
					Abilities.end(FateClientTests.player(server), UnlimitedBladeWorks.KEY.identifier());
					server.overworld().getEntitiesOfClass(ThrownWeaponEntity.class, bounds).forEach(ThrownWeaponEntity::discard);
					guest[0].setAttached(ServantStorage.CHOICE, ServantChoice.servant(AlayaRegistries.SERVANT.getKey(FateServants.GILGAMESH)));
					FateClientTests.giveMana(guest[0]);
					Vault.of(guest[0]).setSettings(Vault.of(guest[0]).settings().withGatesPerSecond(20));
					Vault.of(guest[0]).section(0).insert(new ItemStack(Items.DIAMOND_AXE));
					FateTests.tap(guest[0], GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, false);
					check(Abilities.isActive(guest[0], GateOfBabylon.KEY.identifier()), "Actual guest GoB cast starts");
				});
				step(context, world, GateOfBabylon.VOLLEY_OPENS_OVER);
				ThrownWeaponEntity[] original = {null};
				world.getServer().runOnServer(server -> FateTests.tap(guest[0], GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW, false));
				// Real GoB dispatch occurs on its next ability tick, not inside the press handler.
				step(context, world, 1);
				world.getServer().runOnServer(server -> {
					// The embedded guest has no gameplay packets to call ServerPlayer.doTick; exercise its real ability callback after one native step.
					Abilities.tick(guest[0]);
					var incoming = server.overworld().getEntitiesOfClass(ThrownWeaponEntity.class, bounds.inflate(60),
						candidate -> guest[0].getUUID().equals(candidate.owner()));
					check(!incoming.isEmpty(), "Real guest GoB dispatch required: active=" + Abilities.isActive(guest[0], GateOfBabylon.KEY.identifier())
						+ " gates=" + server.overworld().getEntitiesOfClass(GateEntity.class, bounds.inflate(60)).size());
					original[0] = incoming.getFirst();
					check(UbwSwords.incoming(original[0]), "Counter fixture must be an actually dispatched GoB weapon");
					// Already-inside entry allows the opposing copy to be inspected inside the opaque cloud background.
					original[0].setPos(centre.add(10, 2, 0));
					original[0].setDeltaMovement(-3, 0, 0);
				});
				step(context, world, 1);
				shot(context, centre.add(6, 4, -7), centre.add(10, 2, 0), "ubw-13-exact-gob-opposing-copy");
				world.getServer().runOnServer(server -> check(server.overworld().getEntitiesOfClass(ThrownWeaponEntity.class, bounds,
					candidate -> FateClientTests.player(server).getUUID().equals(candidate.owner())
						&& ItemStack.isSameItemSameComponents(candidate.item(), original[0].item())).size() == 1, "One exact opposing weapon"));
				step(context, world, FateConfig.trace().formationTicks() + 3);
				world.getServer().runOnServer(server -> check(UbwSwords.countered(original[0]), "Opposing copy must really clash"));
				context.takeScreenshot("ubw-14-counter-clashed");
				world.getServer().runOnServer(server -> {
					long began = System.nanoTime();
					FateTests.tap(FateClientTests.player(server), UnlimitedBladeWorks.KEY.identifier(), UnlimitedBladeWorks.MARBLE, false);
					check(!UbwRuntime.hasMarble(FateClientTests.player(server)), "Manual NP cancellation must restore the default sphere");
					for (var entry : originals.entrySet()) {
						check(server.overworld().getBlockState(entry.getKey()).equals(entry.getValue()), "Original block not restored at " + entry.getKey());
					}
					var restored = server.overworld().getBlockEntity(chest[0]);
					check(restored instanceof ChestBlockEntity box && box.getItem(0).is(Items.DIAMOND) && box.getItem(0).getCount() == 3,
						"Original chest inventory must be restored");
					check(server.overworld().getEntitiesOfClass(UbwGroundSwordEntity.class, bounds).isEmpty(), "No ground props after close");
					Fatecraft.LOGGER.info("UBW default-radius client cancellation/restoration: {} ms", (System.nanoTime() - began) / 1_000_000.0);
				});
				context.waitFor(client -> client.level.getEntitiesOfClass(UbwEnvironmentEntity.class, bounds).isEmpty()
					&& client.level.getEntitiesOfClass(UbwGroundSwordEntity.class, bounds).isEmpty());
				shot(context, inside, look, "ubw-15-after-inside-camera-restored");
				shot(context, outside, outsideLook, "ubw-16-after-outside-camera");
				shot(context, outside, centre.add(0, 3, 0), "ubw-16b-after-outside-toward-field");
				UbwClientChecks.placement(context, world, center[0]);
			} finally {
				world.getServer().runOnServer(server -> {
					UbwRuntime.cancel(FateClientTests.player(server));
					server.getPlayerList().remove(guest[0]);
					channel[0].finishAndReleaseAll();
				});
				world.getServer().runCommand("tick unfreeze");
				FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
				context.runOnClient(client -> client.options.hideGui = false);
			}
		}
	}

	static void shot(ClientGameTestContext context, Vec3 at, Vec3 look, String name) {
		FateClientTests.view(context, at, look, FOV);
		context.takeScreenshot(name);
	}

	static void step(ClientGameTestContext context, TestSingleplayerContext world, int ticks) {
		long[] until = {0};
		world.getServer().runOnServer(server -> until[0] = server.overworld().getGameTime() + ticks);
		world.getServer().runCommand("tick step " + ticks);
		// Client ticks alone can finish before a slow server has processed the requested steps.
		context.waitFor(client -> client.level.getGameTime() >= until[0]);
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
	}
}
