package io.github.cwzmorro.fatecraft.gate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.WorldData;
import io.github.cwzmorro.fatecraft.vault.Vault;
import io.github.cwzmorro.fatecraft.vault.VaultMenu;
import io.github.cwzmorro.fatecraft.vault.VaultSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * How the Gate of Babylon's thrown items return to their vault section: after the owner's return time, or at once when
 * the owner picks them up (an offline owner gets them on joining). Copies, arrows and throwables never return. A returning
 * item is kept with the world until it is back, so none is lost in unloaded land.
 */
public final class GateReturns {
	public static final AttachmentType<Thrown> THROWN = AttachmentRegistry.create(Fatecraft.id("thrown"), builder -> builder
		.persistent(Thrown.CODEC));
	private static final int SWEEP_TICKS = 20;

	private record Pending(UUID owner, int section, ItemStack stack, long returnAt) {
		static final Codec<Pending> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("owner").forGetter(Pending::owner),
			Codec.INT.fieldOf("section").forGetter(Pending::section),
			ItemStack.CODEC.fieldOf("stack").forGetter(Pending::stack),
			Codec.LONG.fieldOf("return_at").forGetter(Pending::returnAt)
		).apply(i, Pending::new));
	}

	private record Returning(int section, ItemStack stack) {
		static final Codec<Returning> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("section").forGetter(Returning::section),
			ItemStack.CODEC.fieldOf("stack").forGetter(Returning::stack)
		).apply(i, Returning::new));
	}

	// Both kept with the world: thrown items (not copies) not back yet, and items waiting for an offline owner to join.
	private static final SavedDataType<WorldData<Map<UUID, Pending>>> PENDING = WorldData.type("pending_treasure",
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, Pending.CODEC), HashMap::new);
	private static final SavedDataType<WorldData<Map<UUID, List<Returning>>>> RETURNING = WorldData.type("returning_treasure",
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, Returning.CODEC.listOf()), Map::of);

	private GateReturns() {
	}

	static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			WorldData<Map<UUID, List<Returning>>> data = WorldData.of(server, RETURNING);
			Map<UUID, List<Returning>> returning = new HashMap<>(data.get());
			List<Returning> mine = returning.remove(handler.player.getUUID());
			if (mine != null) {
				data.set(Map.copyOf(returning));
				mine.forEach(item -> put(handler.player, item.section(), item.stack()));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % SWEEP_TICKS == 0) {
				sweep(server);
			}
		});
		ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, loaded) -> reason != EntitySpawnReason.LOAD || !gone(level, entity));
		ServerEntityEvents.ENTITY_UNLOAD.register(GateReturns::unloaded);
	}

	static Thrown throwing(ServerLevel level, Entity entity, ServerPlayer owner, int section, boolean copy, ItemStack stack) {
		Thrown thrown = new Thrown(owner.getUUID(), section, copy, false, returnAt(level, owner.getUUID()));
		entity.setAttached(THROWN, thrown);
		if (!copy) {
			pending(level.getServer()).put(entity.getUUID(), new Pending(thrown.owner(), section, stack.copy(), thrown.returnAt()));
		}
		return thrown;
	}

	static void landed(ServerLevel level, Entity entity) {
		Thrown thrown = entity.getAttached(THROWN);
		if (thrown == null || thrown.landed()) {
			return;
		}
		long returnAt = returnAt(level, thrown.owner());
		entity.setAttached(THROWN, thrown.landedUntil(returnAt));
		update(level.getServer(), entity, item -> new Pending(item.owner(), item.section(), item.stack(), returnAt));
	}

	static void restack(ServerLevel level, Entity entity, ItemStack stack) {
		update(level.getServer(), entity, item -> new Pending(item.owner(), item.section(), stack.copy(), item.returnAt()));
	}

	static void forget(ServerLevel level, Entity entity) {
		take(level.getServer(), entity.getUUID());
	}

	static void sendBack(ServerLevel level, Entity entity) {
		giveBack(level.getServer(), entity);
		entity.discard();
	}

	private static void giveBack(MinecraftServer server, Entity entity) {
		Pending item = take(server, entity.getUUID());
		if (item != null) {
			give(server, item.owner(), item.section(), item.stack());
		}
	}

	static boolean timeUp(Entity entity) {
		Thrown thrown = entity.getAttached(THROWN);
		return thrown != null && entity.level().getGameTime() >= thrown.returnAt();
	}

	public static void tickThrown(Projectile projectile) {
		if (projectile.level() instanceof ServerLevel level && timeUp(projectile)) {
			sendBack(level, projectile);
		}
	}

	public static void tickArrow(AbstractArrow arrow, boolean inGround) {
		Thrown thrown = arrow.getAttached(THROWN);
		if (thrown == null || !inGround || !(arrow.level() instanceof ServerLevel level)) {
			return;
		}
		if (thrown.copy()) {
			arrow.discard();
		} else {
			landed(level, arrow);
		}
	}

	public static boolean ownerPicksUp(AbstractArrow arrow, Player player) {
		Thrown thrown = arrow.getAttached(THROWN);
		if (thrown == null || thrown.copy() || !thrown.owner().equals(player.getUUID()) || !(player instanceof ServerPlayer owner)) {
			return false;
		}
		Pending item = take(owner.level().getServer(), arrow.getUUID());
		put(owner, thrown.section(), item != null ? item.stack() : arrow.getPickupItemStackOrigin());
		return true;
	}

	public static void pickedUp(AbstractArrow arrow) {
		if (arrow.level() instanceof ServerLevel level) {
			forget(level, arrow);
		}
	}

	public static void give(MinecraftServer server, UUID owner, int section, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player != null) {
			put(player, section, stack);
			return;
		}
		WorldData<Map<UUID, List<Returning>>> data = WorldData.of(server, RETURNING);
		Map<UUID, List<Returning>> returning = new HashMap<>(data.get());
		List<Returning> theirs = new ArrayList<>(returning.getOrDefault(owner, List.of()));
		theirs.add(new Returning(section, stack.copy()));
		returning.put(owner, List.copyOf(theirs));
		data.set(Map.copyOf(returning));
	}

	private static void put(ServerPlayer player, int section, ItemStack stack) {
		int index = Vault.sectionIndex(section);
		Vault.of(player).section(index).insert(stack.copy());
		VaultMenu.refresh(player, index);
	}

	private static long returnAt(ServerLevel level, UUID owner) {
		Player player = level.getServer().getPlayerList().getPlayer(owner);
		int ticks = player == null ? VaultSettings.defaults().returnTicks() : Vault.of(player).settings().returnTicks();
		return level.getGameTime() + ticks;
	}

	private static boolean gone(ServerLevel level, Entity entity) {
		Thrown thrown = entity.getAttached(THROWN);
		if (thrown == null) {
			return false;
		}
		return thrown.copy() ? level.getGameTime() >= thrown.returnAt() : !pending(level.getServer()).containsKey(entity.getUUID());
	}

	// An arrow lying in the ground (vanilla's own despawn) or anything fallen out of the world goes back now; one used up on a hit is gone.
	private static void unloaded(Entity entity, ServerLevel level) {
		Thrown thrown = entity.getAttached(THROWN);
		Entity.RemovalReason reason = entity.getRemovalReason();
		if (thrown == null || thrown.copy() || reason == null || !reason.shouldDestroy()
			|| !pending(level.getServer()).containsKey(entity.getUUID())) {
			return;
		}
		boolean lying = entity instanceof AbstractArrow && thrown.landed();
		if (lying || entity instanceof TreasureEntity || entity.getY() < level.getMinY()) {
			giveBack(level.getServer(), entity);
		} else {
			forget(level, entity);
		}
	}

	private static void sweep(MinecraftServer server) {
		Map<UUID, Pending> pending = pending(server);
		long now = server.overworld().getGameTime();
		List<Map.Entry<UUID, Pending>> due = new ArrayList<>();
		for (Iterator<Map.Entry<UUID, Pending>> it = pending.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Pending> entry = it.next();
			if (entry.getValue().returnAt() <= now) {
				due.add(entry);
				it.remove();
			}
		}
		for (Map.Entry<UUID, Pending> entry : due) {
			for (ServerLevel level : server.getAllLevels()) {
				Entity entity = level.getEntity(entry.getKey());
				if (entity != null) {
					entity.discard();
				}
			}
			give(server, entry.getValue().owner(), entry.getValue().section(), entry.getValue().stack());
		}
	}

	private static Map<UUID, Pending> pending(MinecraftServer server) {
		WorldData<Map<UUID, Pending>> data = WorldData.of(server, PENDING);
		if (!(data.get() instanceof HashMap)) {
			data.set(new HashMap<>(data.get()));
		}
		data.setDirty();
		return data.get();
	}

	private static @Nullable Pending take(MinecraftServer server, UUID entity) {
		return pending(server).remove(entity);
	}

	private static void update(MinecraftServer server, Entity entity, UnaryOperator<Pending> change) {
		pending(server).computeIfPresent(entity.getUUID(), (id, item) -> change.apply(item));
	}
}
