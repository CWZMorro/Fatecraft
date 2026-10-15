package io.github.cwzmorro.fatecraft.artoria;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.alayacore.ability.SkillEffects;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Avalon protects its user, not the world: raised, it stops all damage but /kill's, whatever its source. */
public final class Avalon {
	public static final ResourceKey<Ability> KEY = ResourceKey.create(AlayaRegistries.ABILITY_KEY, Fatecraft.id("avalon"));
	public static final AttachmentType<Vec3> ANCHOR = AttachmentRegistry.create(Fatecraft.id("avalon_anchor"));
	private static final AttachmentType<Boolean> PASSIVE = AttachmentRegistry.create(Fatecraft.id("avalon_passive"));
	// Squared distance in blocks: moved further than this from where she raised it, she is put back.
	private static final double ANCHOR_TOLERANCE_SQR = 1.0E-6;
	// Regeneration's period halves each level up; the shift is capped so it stays an int.
	private static final int MAX_INTERVAL_SHIFT = 30;
	private static final int REFRESH_MARGIN = 25;
	private static final int PASSIVE_INTERVAL = SharedConstants.TICKS_PER_SECOND;
	private static final Identifier HELD = Fatecraft.id("avalon_held");
	private static final Identifier CARRIED = Fatecraft.id("avalon_carried");

	private Avalon() {
	}

	public static boolean owns(LivingEntity entity) {
		return Abilities.owns(entity, KEY.identifier());
	}

	public static boolean active(LivingEntity entity) {
		return entity.isUsingItem() && entity.getUseItem().is(FateWeapons.AVALON) && entity.isAlive() && owns(entity);
	}

	public static void begin(LivingEntity entity) {
		Abilities.endAll(entity);
		entity.setAttached(ANCHOR, entity.position());
		entity.setDeltaMovement(Vec3.ZERO);
		regenerate(entity, HELD, FateConfig.artoria().avalonActiveAmplifier());
	}

	public static void tick(LivingEntity entity) {
		if (!active(entity)) {
			entity.stopUsingItem();
			return;
		}
		entity.setDeltaMovement(Vec3.ZERO);
		entity.fallDistance = 0;
		Vec3 anchor = entity.getAttached(ANCHOR);
		if (anchor != null && entity.position().distanceToSqr(anchor) > ANCHOR_TOLERANCE_SQR) {
			entity.setPos(anchor.x, anchor.y, anchor.z);
			if (entity instanceof ServerPlayer player) {
				player.connection.teleport(anchor.x, anchor.y, anchor.z, player.getYRot(), player.getXRot());
			}
		}
		regenerate(entity, HELD, FateConfig.artoria().avalonActiveAmplifier());
	}

	private static void regenerate(LivingEntity entity, Identifier source, int amplifier) {
		int interval = Math.max(1, SkillEffects.REGENERATION_PERIOD_TICKS >> Math.min(amplifier, MAX_INTERVAL_SHIFT));
		// A constant duration refreshed each tick would never heal, so the duration keeps vanilla's countdown phase.
		int duration = REFRESH_MARGIN + interval + Math.floorMod(-entity.tickCount - REFRESH_MARGIN, interval);
		SkillEffects.give(entity, source, new MobEffectInstance(MobEffects.REGENERATION, duration, amplifier, false, false, true));
	}

	public static void end(LivingEntity entity) {
		if (entity.removeAttached(ANCHOR) != null) {
			SkillEffects.take(entity, HELD, MobEffects.REGENERATION);
		}
	}

	/** Heals the players carrying Avalon, anywhere in their inventory, while someone who can use it is online. */
	public static void passive(List<ServerPlayer> players) {
		boolean online = players.stream().anyMatch(Avalon::owns);
		for (ServerPlayer player : players) {
			if (online && player.getInventory().contains(stack -> stack.is(FateWeapons.AVALON))) {
				regenerate(player, CARRIED, FateConfig.artoria().avalonPassiveAmplifier());
				player.setAttached(PASSIVE, true);
			} else if (player.removeAttached(PASSIVE) != null && !active(player)) {
				SkillEffects.take(player, CARRIED, MobEffects.REGENERATION);
			}
		}
	}

	public static void register() {
		// LivingEntity.kill(ServerLevel), used by /kill in 26.1.2, damages with GENERIC_KILL.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!active(entity) || source.is(DamageTypes.GENERIC_KILL));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.hasAttached(ANCHOR) && !active(player)) {
					end(player);
				}
			}
			if (server.getTickCount() % PASSIVE_INTERVAL == 0) {
				passive(server.getPlayerList().getPlayers());
			}
		});
		AvalonLoot.register();
	}
}
