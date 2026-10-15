package io.github.cwzmorro.fatecraft.trace;

import com.mojang.serialization.Codec;
import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.api.AlayaServants;
import io.github.cwzmorro.alayacore.mana.ManaData;
import io.github.cwzmorro.alayacore.progression.Grand;
import io.github.cwzmorro.alayacore.util.Percent;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The price of a projected Excalibur: nearly all of EMIYA's mana, then a health drain that only death ends. The drain
 * sets health directly, past Sword Body and armor, and its killing blow bypasses totems; only a Grand survives, held at a
 * health floor.
 */
public final class ExcaliburStrain {
	// Game time of the last drain step; present while the drain runs. Saved with the player, not copied on death.
	private static final AttachmentType<Long> LAST_STEP = AttachmentRegistry.create(Fatecraft.id("excalibur_strain"),
		builder -> builder.persistent(Codec.LONG));

	private ExcaliburStrain() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> server.getPlayerList().getPlayers().forEach(ExcaliburStrain::tick));
		AlayaServants.BECAME_SERVANT.register((player, servant) -> player.removeAttached(LAST_STEP));
	}

	public static boolean active(LivingEntity entity) {
		return entity.hasAttached(LAST_STEP);
	}

	public static double manaCost(LivingEntity entity) {
		ManaData mana = AlayaMana.get(entity);
		return mana == null ? Double.POSITIVE_INFINITY : mana.max() * Percent.toFraction(FateConfig.trace().excaliburManaPercent());
	}

	public static void begin(LivingEntity entity) {
		entity.setAttached(LAST_STEP, entity.level().getGameTime());
	}

	/** Drains the ticks since the previous step, so calling it more than once a tick is safe. */
	public static void tick(ServerPlayer player) {
		if (!active(player)) {
			return;
		}
		if (!player.isAlive() || !Traces.canTrace(player)) {
			player.removeAttached(LAST_STEP);
			return;
		}
		long now = player.level().getGameTime();
		long elapsed = now - player.getAttachedOrElse(LAST_STEP, now);
		if (elapsed <= 0 || AlayaMana.spendsFree(player) || player.isSpectator()) {
			return;
		}
		player.setAttached(LAST_STEP, now);
		float drain = (float) (player.getMaxHealth() * Percent.toFraction(FateConfig.trace().excaliburDrainPercent())
			* elapsed / SharedConstants.TICKS_PER_SECOND);
		if (Grand.isGrand(player)) {
			// A Grand is drained only down to the floor and held there; the drain stays on him.
			float floor = (float) (player.getMaxHealth() * Percent.toFraction(FateConfig.trace().excaliburGrandFloorPercent()));
			if (player.getHealth() > floor) {
				player.setHealth(Math.max(floor, player.getHealth() - drain));
			}
		} else if (player.getHealth() > drain) {
			player.setHealth(player.getHealth() - drain);
		} else if (player.level() instanceof ServerLevel level) {
			player.removeAttached(LAST_STEP);
			player.hurtServer(level, level.damageSources().genericKill(), player.getHealth());
		}
	}
}
