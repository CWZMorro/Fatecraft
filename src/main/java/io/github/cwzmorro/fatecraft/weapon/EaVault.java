package io.github.cwzmorro.fatecraft.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.cwzmorro.alayacore.item.LostToVoid;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.WorldData;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * Where a Gilgamesh's Ea is while it isn't drawn: behind the vault, drawn with the key. Ea remembers whose it is
 * ({@link #OWNER}); one that falls into the void or is left behind goes back to its owner's vault, at once or when they
 * next join.
 */
public final class EaVault {
	public static final DataComponentType<UUID> OWNER = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Fatecraft.id("ea_owner"),
		DataComponentType.<UUID>builder().persistent(UUIDUtil.CODEC).build());

	/**
	 * A player's vault.
	 *
	 * @param issued whether the player has been given their Ea yet (on the first draw after true name realization)
	 * @param ea     the Ea behind the vault, if it is there and not drawn
	 */
	public record Vault(boolean issued, Optional<ItemStack> ea) {
		static final Vault EMPTY = new Vault(false, Optional.empty());
		static final Codec<Vault> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.fieldOf("issued").forGetter(Vault::issued),
			ItemStack.CODEC.optionalFieldOf("ea").forGetter(Vault::ea)
		).apply(i, Vault::new));
	}

	static final AttachmentType<Vault> VAULT = AttachmentRegistry.create(Fatecraft.id("ea_vault"), builder -> builder
		.initializer(() -> Vault.EMPTY)
		.persistent(Vault.CODEC)
		.copyOnDeath());

	private static final SavedDataType<WorldData<Map<UUID, ItemStack>>> RETURNING = WorldData.type("returning_ea",
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, ItemStack.CODEC), Map::of);

	private EaVault() {
	}

	static void register() {
		LostToVoid.register(FateWeapons.EA, (level, item) -> returnToOwner(level.getServer(), item.getItem().copy()));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			WorldData<Map<UUID, ItemStack>> data = WorldData.of(server, RETURNING);
			Map<UUID, ItemStack> returning = new HashMap<>(data.get());
			ItemStack ea = returning.remove(handler.player.getUUID());
			if (ea != null) {
				data.set(Map.copyOf(returning));
				put(handler.player, ea);
			}
		});
	}

	static @Nullable ItemStack take(ServerPlayer player) {
		Vault vault = player.getAttachedOrCreate(VAULT);
		ItemStack ea = vault.issued() ? vault.ea().orElse(null) : new ItemStack(FateWeapons.EA);
		if (ea != null) {
			ea.set(OWNER, player.getUUID());
			player.setAttached(VAULT, new Vault(true, Optional.empty()));
		}
		return ea;
	}

	static void put(ServerPlayer player, ItemStack ea) {
		player.setAttached(VAULT, new Vault(true, Optional.of(ea)));
	}

	/** Sends {@code ea} back to its owner's vault: now if they are online, else when they join. An Ea of nobody's is lost. */
	public static void returnToOwner(MinecraftServer server, ItemStack ea) {
		UUID owner = ea.get(OWNER);
		if (owner == null) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player != null) {
			put(player, ea);
			return;
		}
		WorldData<Map<UUID, ItemStack>> data = WorldData.of(server, RETURNING);
		Map<UUID, ItemStack> returning = new HashMap<>(data.get());
		returning.put(owner, ea);
		data.set(Map.copyOf(returning));
	}
}
