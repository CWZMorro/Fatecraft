package io.github.cwzmorro.fatecraft.ubw;

import com.mojang.serialization.Codec;
import io.github.cwzmorro.alayacore.ability.AbilitySetting;
import io.github.cwzmorro.alayacore.ability.AbilitySettings;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.vault.VaultSettings;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * A player's Unlimited Blade Works settings, chosen in the preset screen apart from the Gate of Babylon's: firing rate,
 * volley tiers, sword size, wall spacing and how the ground swords stand.
 */
public final class UbwSettings {
	public static final int TIERS = VaultSettings.TIERS;
	private static final double MAX_RATE = 100.0;
	private static final double MAX_VOLLEY = 400.0;
	// Sword size and wall spacing are multipliers of the usual (1); the range is kept within what still hits and reads.
	private static final double MIN_SIZE = 0.25;
	private static final double MAX_SIZE = 4.0;
	private static final double MIN_SPREAD = 0.5;
	private static final double MAX_SPREAD = 3.0;
	private static final double FACTOR_STEP = 0.25;
	private static final double COUNT_STEP = 1.0;
	// Ground sword amount multiplies the count the arsenal gives; spread is a share of the farthest the field reaches; tilt is in degrees.
	private static final double MAX_GROUND_AMOUNT = 4.0;
	private static final double MIN_GROUND_SPREAD = 0.2;
	private static final double GROUND_SPREAD_STEP = 0.05;
	private static final double MAX_GROUND_TILT = 30.0;
	private static final double TILT_STEP = 5.0;
	private static final String RATE = "rate";
	private static final String VOLLEY_PREFIX = "volley_";
	private static final String SIZE = "sword_size";
	private static final String SPREAD = "spread";
	private static final String GROUND_AMOUNT = "ground_amount";
	private static final String GROUND_SPREAD = "ground_spread";
	private static final String GROUND_TILT = "ground_tilt";
	private static final AttachmentType<Integer> TIER = AttachmentRegistry.create(Fatecraft.id("ubw_tier"),
		builder -> builder.persistent(Codec.intRange(0, TIERS - 1)).copyOnDeath());

	private UbwSettings() {
	}

	private static String volleyId(int tier) {
		return VOLLEY_PREFIX + (tier + 1);
	}

	private static AbilitySetting volleySetting(int tier) {
		return new AbilitySetting(volleyId(tier), 1.0, MAX_VOLLEY, COUNT_STEP, VaultSettings.defaults().volleys().get(tier));
	}

	private static AbilitySetting rateSetting() {
		return new AbilitySetting(RATE, 1.0, MAX_RATE, COUNT_STEP, VaultSettings.defaults().gatesPerSecond());
	}

	private static AbilitySetting sizeSetting() {
		return new AbilitySetting(SIZE, MIN_SIZE, MAX_SIZE, FACTOR_STEP, 1.0);
	}

	private static AbilitySetting spreadSetting() {
		return new AbilitySetting(SPREAD, MIN_SPREAD, MAX_SPREAD, FACTOR_STEP, 1.0);
	}

	private static AbilitySetting groundAmountSetting() {
		return new AbilitySetting(GROUND_AMOUNT, 0.0, MAX_GROUND_AMOUNT, FACTOR_STEP, 1.0);
	}

	private static AbilitySetting groundSpreadSetting() {
		return new AbilitySetting(GROUND_SPREAD, MIN_GROUND_SPREAD, 1.0, GROUND_SPREAD_STEP, 1.0);
	}

	private static AbilitySetting groundTiltSetting() {
		return new AbilitySetting(GROUND_TILT, 0.0, MAX_GROUND_TILT, TILT_STEP, 0.0);
	}

	static List<AbilitySetting> all() {
		List<AbilitySetting> all = new ArrayList<>();
		all.add(rateSetting());
		for (int tier = 0; tier < TIERS; tier++) {
			all.add(volleySetting(tier));
		}
		all.add(sizeSetting());
		all.add(spreadSetting());
		all.add(groundAmountSetting());
		all.add(groundSpreadSetting());
		all.add(groundTiltSetting());
		return List.copyOf(all);
	}

	private static double value(LivingEntity entity, AbilitySetting setting) {
		return AbilitySettings.value(entity, UnlimitedBladeWorks.KEY.identifier(), setting);
	}

	public static double rate(LivingEntity entity) {
		return value(entity, rateSetting());
	}

	public static int volley(LivingEntity entity) {
		return (int) value(entity, volleySetting(tier(entity)));
	}

	public static float swordSize(LivingEntity entity) {
		return (float) value(entity, sizeSetting());
	}

	public static double spread(LivingEntity entity) {
		return value(entity, spreadSetting());
	}

	public static double groundAmount(LivingEntity entity) {
		return value(entity, groundAmountSetting());
	}

	public static double groundSpread(LivingEntity entity) {
		return value(entity, groundSpreadSetting());
	}

	public static double groundTilt(LivingEntity entity) {
		return value(entity, groundTiltSetting());
	}

	public static int tier(LivingEntity entity) {
		return entity.getAttachedOrElse(TIER, 0);
	}

	static void nextTier(ServerPlayer player) {
		player.setAttached(TIER, (tier(player) + 1) % TIERS);
		player.sendOverlayMessage(
			Component.translatable("ability.fatecraft.unlimited_blade_works.tier", VaultSettings.tierName(tier(player)), volley(player)));
	}
}
