package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.gate.GateOfBabylon;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.vault.Vault;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantments;

public class GateOfBabylonClientTest implements FabricClientGameTest {
	private static final int FRAME_SAMPLES = 200;
	private static final int UNLIMITED_FRAMERATE = 260;
	private static final List<Item> WEAPONS = List.of(Items.NETHERITE_SWORD, Items.DIAMOND_SWORD, Items.GOLDEN_SWORD, Items.IRON_SWORD,
		Items.NETHERITE_AXE, Items.DIAMOND_AXE, Items.TRIDENT, Items.MACE, Items.NETHERITE_SPEAR, Items.DIAMOND_SPEAR, Items.GOLDEN_SPEAR);
	private static final int OF_EACH = FateConfig.vault().stackMultiplier();
	private static final int OPENED_TICKS = 35;
	private static final int IN_THE_AIR_TICKS = 4;
	private static final int LANDED_TICKS = 40;
	private static final int LARGE_TIER = 2;
	private static final int THROW_PITCH = 15;
	private static final int EAST = -90;
	private static final int SIDE_ACROSS = 16;
	private static final int SIDE_UP = 3;
	private static final int SIDE_YAW = 90;
	private static final int SIDE_PITCH = 5;
	private static final int BEHIND_ACROSS = 4;
	private static final int BEHIND_UP = 2;
	private static final int BEHIND = -7;
	private static final int BEHIND_YAW = 25;
	private static final int TARGET_AHEAD = 12;
	private static final int SINGLE_TARGET_TICKS = 30;
	private static final int SHOWCASE_SECTION = 1;
	private static final int SHARPNESS = 5;
	private static final int GIANT = 2;
	private static final int CLOSE_SECTION = 2;
	private static final int CLOSE_GATES = 2;
	private static final int SIDE_ON_ACROSS = 4;
	private static final int SIDE_ON_UP = 2;
	private static final int SIDE_ON_SOUTH = -1;
	private static final int ABOVE = 14;
	private static final int LOOKING_DOWN = 90;
	private static final int CLOSE_ACROSS = 6;
	private static final int CLOSE_UP = 4;
	private static final int CLOSE_AHEAD = 3;
	private static final int CLOSE_YAW = 125;
	private static final int CLOSE_PITCH = 20;

	private static final int DEFAULT_VIEW_CHUNKS = 12;
	private static final int[] AFAR = {64, 128, 176, 200};
	private static final int AFAR_UP = 2;
	private static final int CHUNKS_TO_LOAD_TICKS = 200;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			FateClientTests.becomeServant(context, world, FateServants.GILGAMESH);
			FateClientTests.quietNoon(context, world);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = FateClientTests.player(server);
				FateClientTests.giveMana(player);
				for (Item weapon : WEAPONS) {
					Vault.of(player).section(0).insert(new ItemStack(weapon, OF_EACH));
				}
			});
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);

			FateClientTests.tap(world, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW);
			context.waitTicks(OPENED_TICKS);
			FateClientTests.setCamera(context, CameraType.THIRD_PERSON_FRONT);
			context.takeScreenshot("gob-01-volley-waiting-front");
			FateClientTests.lookFrom(context, world, "gob-01b-volley-waiting-behind", BEHIND_ACROSS, BEHIND_UP, BEHIND, BEHIND_YAW, SIDE_PITCH);
			FateClientTests.face(world, EAST, 0);
			FateClientTests.setCamera(context, CameraType.THIRD_PERSON_FRONT);
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			context.takeScreenshot("gob-02-volley-waiting-side");
			FateClientTests.faceSouth(world);

			// Thrown a little downward, so the weapons land in sight.
			FateClientTests.setCamera(context, CameraType.THIRD_PERSON_BACK);
			FateClientTests.face(world, 0, THROW_PITCH);
			FateClientTests.tap(world, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW);
			context.waitTicks(IN_THE_AIR_TICKS);
			context.takeScreenshot("gob-03-thrown-from-behind");
			context.waitTicks(LANDED_TICKS);
			context.takeScreenshot("gob-04-landed");
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
			FateClientTests.faceSouth(world);

			world.getServer().runCommand("time set midnight");
			world.getServer().runOnServer(server -> {
				Vault vault = Vault.of(FateClientTests.player(server));
				for (int tier = vault.settings().tier(); tier != LARGE_TIER; tier = vault.settings().tier()) {
					vault.setSettings(vault.settings().withNextTier());
				}
			});
			FateClientTests.tap(world, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW);
			context.waitTicks(OPENED_TICKS);
			FateClientTests.setCamera(context, CameraType.THIRD_PERSON_FRONT);
			context.takeScreenshot("gob-05-large-volley-night");
			world.getServer().runOnServer(server -> Abilities.endAll(FateClientTests.player(server)));
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);

			context.runOnClient(client -> {
				client.options.framerateLimit().set(UNLIMITED_FRAMERATE);
				client.options.enableVsync().set(false);
			});
			double none = frameMillis(context);
			world.getServer().runOnServer(server -> {
				Vault vault = Vault.of(FateClientTests.player(server));
				vault.setSettings(vault.settings().withNextTier());
				for (int sword = 0; sword < vault.settings().volley(); sword++) {
					vault.section(0).insert(new ItemStack(Items.IRON_SWORD));
				}
			});
			FateClientTests.tap(world, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW);
			context.waitTicks(OPENED_TICKS);
			double huge = frameMillis(context);
			context.takeScreenshot("gob-05b-huge-volley-night");
			Fatecraft.LOGGER.info("Frame time: no gates {} ms, a volley of 200 gates {} ms", String.format("%.2f", none), String.format("%.2f", huge));
			world.getServer().runOnServer(server -> Abilities.endAll(FateClientTests.player(server)));
			world.getServer().runCommand("time set noon");

			world.getServer().runCommand("execute as @p at @s run summon husk ~ ~ ~" + TARGET_AHEAD + " {NoAI:1b,PersistenceRequired:1b,Invulnerable:1b}");
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
			FateClientTests.press(world, GateOfBabylon.KEY.identifier(), GateOfBabylon.SINGLE_TARGET);
			context.waitTicks(SINGLE_TARGET_TICKS);
			FateClientTests.lookFrom(context, world, "gob-06-single-target-side", SIDE_ACROSS, SIDE_UP, TARGET_AHEAD / 2, SIDE_YAW, SIDE_PITCH);
			FateClientTests.release(world, GateOfBabylon.KEY.identifier());
			world.getServer().runCommand("kill @e[type=husk]");

			world.getServer().runOnServer(server -> {
				ServerPlayer player = FateClientTests.player(server);
				Vault vault = Vault.of(player);
				while (vault.settings().tier() != 0) {
					vault.setSettings(vault.settings().withNextTier());
				}
				vault.setSettings(vault.settings().withThrownSection(SHOWCASE_SECTION));
				for (ItemStack item : showcase(player)) {
					vault.section(SHOWCASE_SECTION).insert(item);
				}
			});
			FateClientTests.setCamera(context, CameraType.THIRD_PERSON_FRONT);
			FateClientTests.tap(world, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW);
			context.waitTicks(OPENED_TICKS);
			context.takeScreenshot("gob-07-every-look-front");
			FateClientTests.lookFrom(context, world, "gob-07b-every-look-from-above", 0, ABOVE, 0, 0, LOOKING_DOWN);
			FateClientTests.lookFrom(context, world, "gob-07c-every-look-close", CLOSE_ACROSS, CLOSE_UP, CLOSE_AHEAD, CLOSE_YAW, CLOSE_PITCH);
			FateClientTests.setCamera(context, CameraType.THIRD_PERSON_BACK);
			FateClientTests.face(world, 0, THROW_PITCH);
			FateClientTests.tap(world, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW);
			context.waitTicks(IN_THE_AIR_TICKS);
			context.takeScreenshot("gob-07d-every-look-thrown");
			context.waitTicks(LANDED_TICKS);
			FateClientTests.faceSouth(world);
			world.getServer().runCommand("attribute @p minecraft:scale base set " + GIANT);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = FateClientTests.player(server);
				for (ItemStack item : showcase(player)) {
					Vault.of(player).section(SHOWCASE_SECTION).insert(item);
				}
			});
			FateClientTests.setCamera(context, CameraType.THIRD_PERSON_FRONT);
			FateClientTests.tap(world, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW);
			context.waitTicks(OPENED_TICKS);
			context.takeScreenshot("gob-08-giant-volley");
			world.getServer().runOnServer(server -> Abilities.endAll(FateClientTests.player(server)));
			world.getServer().runCommand("attribute @p minecraft:scale base set 1");

			world.getServer().runOnServer(server -> {
				ServerPlayer player = FateClientTests.player(server);
				Vault vault = Vault.of(player);
				vault.setSettings(vault.settings().withVolleyChanged(0, CLOSE_GATES - vault.settings().volley()).withThrownSection(CLOSE_SECTION));
				ItemStack axes = new ItemStack(Items.NETHERITE_AXE, CLOSE_GATES);
				axes.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), SHARPNESS);
				vault.section(CLOSE_SECTION).insert(axes);
			});
			FateClientTests.tap(world, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW);
			context.waitTicks(OPENED_TICKS);
			FateClientTests.lookFrom(context, world, "gob-09-enchanted-axes-edge-down", SIDE_ON_ACROSS, SIDE_ON_UP, SIDE_ON_SOUTH, SIDE_YAW, 0);
			world.getServer().runOnServer(server -> Abilities.endAll(FateClientTests.player(server)));
			FateClientTests.setCamera(context, CameraType.FIRST_PERSON);

			context.runOnClient(client -> client.options.renderDistance().set(DEFAULT_VIEW_CHUNKS));
			context.waitTicks(CHUNKS_TO_LOAD_TICKS);
			FateClientTests.faceSouth(world);
			FateClientTests.tap(world, GateOfBabylon.KEY.identifier(), GateOfBabylon.AOE_THROW);
			context.waitTicks(OPENED_TICKS);
			for (String time : FateClientTests.TIMES_OF_DAY) {
				world.getServer().runCommand("time set " + time);
				for (int distance : AFAR) {
					farLook(context, "gob-10-afar-" + distance + "-" + time, distance);
				}
			}
			world.getServer().runOnServer(server -> Abilities.endAll(FateClientTests.player(server)));
		}
	}

	private static void farLook(ClientGameTestContext context, String name, int distance) {
		farCamera(context, distance, AFAR_UP);
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
		context.takeScreenshot(name);
		context.runOnClient(client -> client.setCameraEntity(client.player));
	}

	static void farCamera(ClientGameTestContext context, int distance, int up) {
		context.runOnClient(client -> {
			ArmorStand camera = new ArmorStand(client.level, client.player.getX(), client.player.getY() + up, client.player.getZ() + distance);
			camera.snapTo(camera.position(), 180.0F, 0.0F);
			camera.setYHeadRot(180.0F);
			camera.yHeadRotO = camera.getYHeadRot();
			client.setCameraEntity(camera);
		});
	}

	private static List<ItemStack> showcase(ServerPlayer player) {
		ItemStack enchanted = new ItemStack(Items.DIAMOND_SWORD);
		enchanted.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), SHARPNESS);
		return List.of(new ItemStack(Items.TRIDENT), enchanted, new ItemStack(Items.NETHERITE_SPEAR), new ItemStack(Items.MACE),
			new ItemStack(Items.IRON_SWORD), new ItemStack(Items.ARROW), PotionContents.createItemStack(Items.TIPPED_ARROW, Potions.POISON),
			new ItemStack(Items.SPECTRAL_ARROW));
	}

	private static double frameMillis(ClientGameTestContext context) {
		long total = 0;
		for (int i = 0; i < FRAME_SAMPLES; i++) {
			context.waitTick();
			total += context.computeOnClient(Minecraft::getFrameTimeNs);
		}
		return total / 1e6 / FRAME_SAMPLES;
	}
}
