package io.github.cwzmorro.fatecraft.ubw;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.alayacore.ability.AbilityCast;
import io.github.cwzmorro.alayacore.ability.AbilityRules;
import io.github.cwzmorro.alayacore.ability.AbilitySetting;
import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.progression.TrueName;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import java.io.IOException;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/** Unlimited Blade Works: one mode opens or closes the marble, three fire its swords; the marble outlives the firing casts. */
public final class UnlimitedBladeWorks extends Ability {
	public static final ResourceKey<Ability> KEY = ResourceKey.create(AlayaRegistries.ABILITY_KEY, Fatecraft.id("unlimited_blade_works"));
	public static final int MARBLE = 0;
	public static final int AOE_THROW = 1;
	public static final int SINGLE_TARGET = 2;
	public static final int TARGETED_THROW = 3;
	private static final int MODES = 4;
	private static final AttachmentType<UbwFiring> FIRING = AttachmentRegistry.create(Fatecraft.id("ubw_firing"));
	private static final String EMIYA_ONLY = "ability.fatecraft.unlimited_blade_works.emiya_only";
	private static final String INSIDE_ONLY = "ability.fatecraft.unlimited_blade_works.inside_only";
	private static final String RECOVERY_PENDING = "ability.fatecraft.unlimited_blade_works.recovery_pending";
	private static final String INSUFFICIENT_MANA = "ability.fatecraft.unlimited_blade_works.insufficient_mana";
	private static final String BUSY = "ability.fatecraft.unlimited_blade_works.busy";
	private static final String CLASH_LOST = "ability.fatecraft.unlimited_blade_works.clash_lost";
	public static final String SHATTERING = "ability.fatecraft.unlimited_blade_works.shattering";

	public UnlimitedBladeWorks() {
		super(new Properties().modes(MODES).noblePhantasm());
	}

	@Override
	public List<Object> descriptionArguments() {
		UbwConfig config = FateConfig.ubw();
		return List.of(config.radius(), config.openingMana(), config.upkeepManaPerSecond(), config.groundSwordsPerTrace(), config.maxGroundSwords());
	}

	@Override
	public List<AbilitySetting> settings() {
		return UbwSettings.all();
	}

	/** Returns whether the entity, player or mob, can cast it: its servant has it, and a player's true name is realized. */
	public static boolean eligible(LivingEntity entity) {
		return Abilities.owns(entity, KEY.identifier()) && (!(entity instanceof ServerPlayer player) || TrueName.allows(player));
	}

	public static boolean canSwitch(LivingEntity entity, int mode) {
		return mode >= MARBLE && mode <= TARGETED_THROW && eligible(entity)
			&& (mode == MARBLE || UbwMembership.freeProjection(entity));
	}

	static boolean changesTier(int mode, boolean sneaking) {
		return sneaking && mode != SINGLE_TARGET;
	}

	public static int firingMode(LivingEntity entity) {
		UbwFiring firing = entity.getAttached(FIRING);
		return firing == null ? -1 : firing.mode();
	}

	@Override
	public double startCost(LivingEntity entity, int mode) {
		return 0;
	}

	@Override
	public @Nullable Component cannotStart(LivingEntity entity, int mode) {
		if (!(entity.level() instanceof ServerLevel level) || !eligible(entity)) {
			return Component.translatable(EMIYA_ONLY);
		}
		if (mode != MARBLE) {
			return UbwMembership.freeProjection(entity) ? null : Component.translatable(INSIDE_ONLY);
		}
		// A marble Enuma Elish broke crumbles to its end: no cancelling it early, no reopening before it has closed.
		if (UbwRuntime.shattering(entity)) {
			return Component.translatable(SHATTERING);
		}
		if (UbwRuntime.hasMarble(entity)) {
			// Closing it needs neither mana nor a recovered journal.
			return null;
		}
		if (!UbwRuntime.ready(level.getServer())) {
			return Component.translatable(RECOVERY_PENDING);
		}
		return AlayaMana.available(entity) >= FateConfig.ubw().openingMana() ? null : Component.translatable(AbilityRules.Refusal.NOT_ENOUGH_MANA.translationKey());
	}

	@Override
	public void onStart(AbilityCast cast) {
		LivingEntity entity = cast.entity();
		if (!(entity.level() instanceof ServerLevel) || !eligible(entity)) {
			cast.end();
			return;
		}
		if (cast.mode() == MARBLE) {
			if (UbwRuntime.hasMarble(entity)) {
				UbwRuntime.cancel(entity);
			} else {
				this.openMarble(entity);
			}
			cast.end();
			return;
		}
		if (!UbwMembership.freeProjection(entity)) {
			cast.end();
			return;
		}
		// Shift + press changes a player's tier; a mob fires the tier it has stored, the first by default.
		if (changesTier(cast.mode(), cast.sneaking()) && entity instanceof ServerPlayer player) {
			UbwSettings.nextTier(player);
			cast.end();
			return;
		}
		entity.setAttached(FIRING, new UbwFiring(entity, cast.mode()));
	}

	private void openMarble(LivingEntity caster) {
		String refused;
		try {
			UbwController.OpenResult result = UbwRuntime.open(caster);
			refused = result == UbwController.OpenResult.OPENED ? null : refusal(result);
		} catch (IOException failure) {
			refused = RECOVERY_PENDING;
		}
		if (refused != null && caster instanceof ServerPlayer player) {
			player.sendOverlayMessage(Component.translatable(refused));
		}
	}

	private static String refusal(UbwController.OpenResult result) {
		return switch (result) {
			case INSUFFICIENT_MANA -> INSUFFICIENT_MANA;
			case BUSY -> BUSY;
			case CLASH_LOST -> CLASH_LOST;
			case RECOVERY_PENDING -> RECOVERY_PENDING;
			case OPENED -> throw new IllegalArgumentException("an opening that succeeded is not a refusal");
		};
	}

	@Override
	public void onTap(AbilityCast cast) {
		if (cast.mode() == SINGLE_TARGET) {
			cast.end();
		}
	}

	@Override
	public void onHoldStart(AbilityCast cast) {
		UbwFiring firing = cast.entity().getAttached(FIRING);
		if (firing != null) {
			firing.hold();
		}
	}

	@Override
	public void onPressedAgain(AbilityCast cast) {
		UbwFiring firing = cast.entity().getAttached(FIRING);
		if (firing != null) {
			firing.again(cast.sneaking());
		}
	}

	@Override
	public void onReleased(AbilityCast cast) {
		cast.end();
	}

	@Override
	public void onTick(AbilityCast cast) {
		UbwFiring firing = cast.entity().getAttached(FIRING);
		if (firing == null || !UbwMembership.freeProjection(cast.entity())) {
			cast.end();
			return;
		}
		if (cast.entity().level() instanceof ServerLevel level && level.getServer().tickRateManager().runsNormally()) {
			firing.tick(cast);
		}
	}

	// The marble outlasts the cast: releasing, switching modes or finishing a volley never closes it.
	@Override
	public void onEnd(AbilityCast cast) {
		UbwFiring firing = cast.entity().removeAttached(FIRING);
		if (firing != null) {
			firing.clear();
		}
	}
}
