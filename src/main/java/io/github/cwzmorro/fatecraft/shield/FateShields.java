package io.github.cwzmorro.fatecraft.shield;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.fatecraft.Fatecraft;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/** Registers Fatecraft's shields: Rho Aias, its ability and the caster's projection. */
public final class FateShields {
	// The entity's box is only a point (ShieldEntity fits it to the disc); the tracking range is in chunks, the update interval in ticks.
	private static final float BOX = 0.5F;
	private static final int TRACKING_CHUNKS = 10;
	private static final int UPDATE_TICKS = 1;
	public static final EntityType<RhoAiasEntity> RHO_AIAS = Fatecraft.entity("rho_aias",
		EntityType.Builder.<RhoAiasEntity>of(RhoAiasEntity::new, MobCategory.MISC).sized(BOX, BOX)
			.clientTrackingRange(TRACKING_CHUNKS).updateInterval(UPDATE_TICKS));
	public static final AttachmentType<Projection> PROJECTION = AttachmentRegistry.create(Fatecraft.id("rho_aias_projection"),
		builder -> builder.persistent(Projection.CODEC).copyOnDeath());

	private static final AttachmentType<Map<UUID, Long>> DISMISSED = AttachmentRegistry.create(Fatecraft.id("dismissed_rho_aias"),
		builder -> builder.initializer(Map::of).persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG)));

	public record Projection(UUID shield, long expiresAt, ResourceKey<Level> dimension, ChunkPos chunk) {
		static final Codec<Projection> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("shield").forGetter(Projection::shield),
			Codec.LONG.fieldOf("expires_at").forGetter(Projection::expiresAt),
			Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Projection::dimension),
			ChunkPos.CODEC.fieldOf("chunk").forGetter(Projection::chunk)
		).apply(i, Projection::new));
	}

	private FateShields() {
	}

	public static void init() {
		Registry.register(AlayaRegistries.ABILITY, RhoAias.KEY, new RhoAias());
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof RhoAiasEntity shield) {
				Map<UUID, Long> dismissed = level.getServer().globalAttachments().getAttachedOrCreate(DISMISSED);
				if (dismissed.containsKey(shield.getUUID())) {
					Map<UUID, Long> remaining = new HashMap<>(dismissed);
					remaining.remove(shield.getUUID());
					level.getServer().globalAttachments().setAttached(DISMISSED, Map.copyOf(remaining));
					shield.discard();
				}
			}
		});
	}

	static void dismissUnloaded(ServerLevel level, Projection projection) {
		Map<UUID, Long> dismissed = new HashMap<>(level.getServer().globalAttachments().getAttachedOrCreate(DISMISSED));
		dismissed.values().removeIf(expiry -> expiry <= level.getGameTime());
		dismissed.put(projection.shield(), projection.expiresAt());
		level.getServer().globalAttachments().setAttached(DISMISSED, Map.copyOf(dismissed));
	}
}
