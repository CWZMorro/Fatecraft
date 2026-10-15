package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.combat.ForcedAdvance;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.network.ChooseServantPayload;
import io.github.cwzmorro.alayacore.servant.Servant;
import io.github.cwzmorro.alayacore.servant.ServantChoice;
import io.github.cwzmorro.alayacore.servant.ServantSelection;
import io.github.cwzmorro.alayacore.servant.ServantStorage;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.altera.PhotonRay;
import io.github.cwzmorro.fatecraft.altera.PhotonRayEntity;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.weapon.EaConfig;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

final class FateTests {
	static final double MANA = 1_000_000.0;
	static final double ALTERA_MANA = 10_000.0;
	// A compile-time constant for test time limits (tests run with the default config).
	static final int EA_WIND_UP_TICKS = (int) (EaConfig.DEFAULT_ENUMA_ELISH_WIND_UP_SECONDS * SharedConstants.TICKS_PER_SECOND);
	// The framework runs a test's per-tick actions in no fixed order, so what must happen just before or after a player's
	// abilities tick shares their action: a runner's movement goes first, as her client moves on what the server set the
	// tick before, and a check of what the abilities just did goes after, before the world moves it on.
	private static final Map<ServerPlayer, Runnable> MOVEMENT = new WeakHashMap<>();
	private static final Map<ServerPlayer, Runnable> AFTER_ABILITIES = new WeakHashMap<>();
	static final List<Item> WEAPONS_AND_THROWABLES = List.of(Items.NETHERITE_SWORD, Items.DIAMOND_SWORD, Items.IRON_SWORD,
		Items.GOLDEN_SWORD, Items.COPPER_SWORD, Items.STONE_SWORD, Items.WOODEN_SWORD, Items.NETHERITE_AXE, Items.DIAMOND_AXE, Items.IRON_AXE,
		Items.MACE, Items.TRIDENT, Items.NETHERITE_SPEAR, Items.DIAMOND_SPEAR, Items.IRON_SPEAR, Items.GOLDEN_SPEAR, Items.COPPER_SPEAR,
		Items.STONE_SPEAR, Items.WOODEN_SPEAR, Items.BOW, Items.CROSSBOW, Items.ARROW, Items.SPECTRAL_ARROW, Items.TIPPED_ARROW,
		Items.SPLASH_POTION, Items.LINGERING_POTION, Items.ENDER_PEARL, Items.SNOWBALL, Items.EGG, Items.FIRE_CHARGE, Items.WIND_CHARGE,
		Items.EXPERIENCE_BOTTLE, Items.FIREWORK_ROCKET);

	private FateTests() {
	}

	static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("fatecraft-gametest", path);
	}

	static void check(GameTestHelper helper, String what, boolean condition) {
		if (!condition) {
			throw helper.assertionException(Component.literal(what));
		}
	}

	static List<String> configProblems(Path file) {
		return AlayaConfig.load(file, Map.of(), Map.of()).stream().filter(problem -> !problem.contains("is missing")).toList();
	}

	@SuppressWarnings("removal")
	static ServerPlayer player(GameTestHelper helper, BlockPos pos) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.snapTo(helper.absoluteVec(Vec3.atBottomCenterOf(pos)), 0.0F, 0.0F);
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		// Mock players hold no chunk tickets, so force-load the chunks around them to keep nearby ability entities ticking
		// when the test grid puts their spawn just across the structure's chunk boundary.
		BlockPos at = player.blockPosition();
		helper.getLevel().getServer().getCommands().performPrefixedCommand(helper.getLevel().getServer().createCommandSourceStack(),
			"forceload add " + (at.getX() - 16) + " " + (at.getZ() - 16) + " " + (at.getX() + 16) + " " + (at.getZ() + 16));
		return player;
	}

	// Answers the first-join screen as a human, as every player who acts in a world has: an undecided one is held where it joined.
	static ServerPlayer human(GameTestHelper helper, BlockPos pos) {
		return human(player(helper, pos));
	}

	static ServerPlayer human(ServerPlayer player) {
		ServantSelection.choose(player, new ChooseServantPayload(ChooseServantPayload.Kind.HUMAN, Optional.empty()));
		return player;
	}

	static ServerPlayer gilgamesh(GameTestHelper helper, BlockPos pos, double mana) {
		return servant(helper, pos, mana, FateServants.GILGAMESH);
	}

	// Inverse of absolutePos, derived from its basis, since the helper's relativePos adds a spurious half-turn.
	static BlockPos relativeBlock(GameTestHelper helper, BlockPos absolute) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		BlockPos x = helper.absolutePos(BlockPos.ZERO.east()).subtract(origin);
		BlockPos z = helper.absolutePos(BlockPos.ZERO.south()).subtract(origin);
		BlockPos delta = absolute.subtract(origin);
		return new BlockPos(delta.getX() * x.getX() + delta.getZ() * x.getZ(), delta.getY(),
			delta.getX() * z.getX() + delta.getZ() * z.getZ());
	}

	// Game-test players don't tick, so the test ticks the abilities; set as servant directly, so no other test's player loses him.
	static ServerPlayer servant(GameTestHelper helper, BlockPos pos, double mana, Servant servant) {
		ServerPlayer player = human(helper, pos);
		player.setGameMode(GameType.SURVIVAL);
		player.setAttached(ServantStorage.CHOICE, ServantChoice.servant(AlayaRegistries.SERVANT.getKey(servant)));
		addMana(player, mana);
		helper.onEachTick(() -> {
			Runnable move = MOVEMENT.get(player);
			if (move != null) {
				move.run();
			}
			Abilities.tick(player);
			Runnable after = AFTER_ABILITIES.get(player);
			if (after != null) {
				after.run();
			}
		});
		return player;
	}

	static void afterAbilities(ServerPlayer player, Runnable action) {
		AFTER_ABILITIES.put(player, action);
	}

	static void addMana(LivingEntity entity, double mana) {
		AlayaMana.update(entity, data -> data.withMax(data.max() + mana));
		AlayaMana.addCurrent(entity, mana);
	}

	static ServerPlayer altera(GameTestHelper helper, BlockPos pos, double mana) {
		// The generated empty test structure surrounds its small volume with barriers, so clear a sky lane as wide as the
		// cone and as long as a second of Photon Ray's run plus the cone, whatever the config.
		int radius = (int) Math.ceil(PhotonRayEntity.coneRadius());
		int height = (int) Math.ceil(FateConfig.altera().dashBlocksPerSecond() + PhotonRayEntity.coneLength());
		for (BlockPos block : BlockPos.betweenClosed(pos.offset(-radius, 0, -radius), pos.offset(radius, height, radius))) {
			helper.setBlock(block, Blocks.AIR);
		}
		ServerPlayer player = servant(helper, pos, mana, FateServants.ALTERA);
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		player.setXRot(-90.0F);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FateWeapons.PHOTON_RAY));
		return player;
	}

	static final int LANE_LENGTH = 48;
	private static final int LANE_BEHIND = 3;
	private static final int LANE_HALF_WIDTH = 8;
	private static final int LANE_HEIGHT = 5;
	private static final int LANE_LOAD_MARGIN = 16;

	// Game-test players have no client to move them, so each tick this does what her client does: one vanilla movement step, with gravity and the push of ForcedAdvance.
	static ServerPlayer runner(GameTestHelper helper, BlockPos pos, double mana) {
		ServerPlayer player = altera(helper, pos, mana);
		player.setXRot(0.0F);
		for (BlockPos block : BlockPos.betweenClosed(pos.offset(-LANE_HALF_WIDTH, 0, -LANE_BEHIND),
			pos.offset(LANE_HALF_WIDTH, LANE_HEIGHT, LANE_LENGTH))) {
			helper.setBlock(block, Blocks.AIR);
		}
		for (BlockPos block : BlockPos.betweenClosed(pos.offset(-LANE_HALF_WIDTH, -1, -LANE_BEHIND), pos.offset(LANE_HALF_WIDTH, -1, LANE_LENGTH))) {
			helper.setBlock(block, Blocks.STONE);
		}
		BlockPos end = helper.absolutePos(pos.offset(0, 0, LANE_LENGTH));
		helper.getLevel().getServer().getCommands().performPrefixedCommand(helper.getLevel().getServer().createCommandSourceStack(),
			"forceload add " + (end.getX() - LANE_LOAD_MARGIN) + " " + (end.getZ() - LANE_LOAD_MARGIN) + " "
				+ (end.getX() + LANE_LOAD_MARGIN) + " " + (end.getZ() + LANE_LOAD_MARGIN));
		MOVEMENT.put(player, () -> {
			if (ForcedAdvance.active(player)) {
				player.travel(Vec3.ZERO);
			}
		});
		return player;
	}

	static void hold(ServerPlayer player) {
		Abilities.press(player, PhotonRay.KEY.identifier(), PhotonRay.DASH_MODE, false);
	}

	static void letGo(ServerPlayer player) {
		Abilities.release(player, PhotonRay.KEY.identifier());
	}

	static void become(GameTestHelper helper, ServerPlayer player, Servant servant) {
		Identifier id = AlayaRegistries.SERVANT.getKey(servant);
		MinecraftServer server = helper.getLevel().getServer();
		Map<UUID, Identifier> owners = new HashMap<>(server.globalAttachments().getAttachedOrCreate(ServantStorage.OWNERS));
		owners.values().remove(id);
		server.globalAttachments().setAttached(ServantStorage.OWNERS, Map.copyOf(owners));
		ServantSelection.open(player);
		ServantSelection.choose(player, new ChooseServantPayload(ChooseServantPayload.Kind.SERVANT, Optional.of(id)));
	}

	static void tap(ServerPlayer player, Identifier ability, int mode, boolean sneaking) {
		Abilities.press(player, ability, mode, sneaking);
		Abilities.release(player, ability);
	}

	// A husk, so daylight doesn't burn it. Spawned the way /summon does, since the helper's own spawn is a structure's and so
	// refused inside Unlimited Blade Works; such a spawn is sometimes a baby, half as tall, so it is made an adult.
	static Husk mob(GameTestHelper helper, BlockPos pos) {
		Husk mob = EntityType.HUSK.spawn(helper.getLevel(), helper.absolutePos(pos), EntitySpawnReason.COMMAND);
		mob.setBaby(false);
		mob.setPersistenceRequired();
		mob.setNoAi(true);
		mob.setNoGravity(true);
		return mob;
	}

	static Husk mobServant(GameTestHelper helper, BlockPos pos, double mana, Servant servant) {
		Husk mob = mob(helper, pos);
		mob.setAttached(ServantStorage.CHOICE, ServantChoice.servant(AlayaRegistries.SERVANT.getKey(servant)));
		AlayaMana.getOrCreate(mob);
		addMana(mob, mana);
		return mob;
	}
}
