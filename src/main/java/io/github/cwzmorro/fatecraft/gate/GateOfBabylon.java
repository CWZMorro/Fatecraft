package io.github.cwzmorro.fatecraft.gate;

import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.alayacore.ability.AbilityCast;
import io.github.cwzmorro.alayacore.ability.AbilityRules;
import io.github.cwzmorro.alayacore.api.AlayaMana;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.combat.Aim;
import io.github.cwzmorro.alayacore.rank.Rank;
import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.FateGameRules;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.vault.Vault;
import io.github.cwzmorro.fatecraft.vault.VaultEntry;
import io.github.cwzmorro.fatecraft.vault.VaultMenu;
import io.github.cwzmorro.fatecraft.vault.VaultSection;
import io.github.cwzmorro.fatecraft.vault.VaultSettings;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Gilgamesh's Gate of Babylon: gates throw the vault's chosen section, one item a gate, for a flat mana cost each. AOE and
 * targeted throws open the tier's volley on a wall ({@link GateWall}); single target throws while held and has no tiers.
 */
public final class GateOfBabylon extends Ability {
	public static final ResourceKey<Ability> KEY = ResourceKey.create(AlayaRegistries.ABILITY_KEY, Fatecraft.id("gate_of_babylon"));
	public static final int AOE_THROW = 0;
	public static final int SINGLE_TARGET = 1;
	public static final int TARGETED_THROW = 2;
	private static final int MODES = 3;
	private static final String RANK_LABEL = Rank.range(Rank.parse("E"), Rank.parse("A++"));
	public static final AttachmentType<GateSession> SESSION = AttachmentRegistry.create(Fatecraft.id("gate_session"));
	public static final int VOLLEY_OPENS_OVER = 20;
	private static final int PLACE_TRIES = 10;
	private static final double GATE_ROOM = 1.0;

	public GateOfBabylon() {
		super(new Ability.Properties().modes(MODES));
	}

	@Override
	public String rankLabel() {
		return RANK_LABEL;
	}

	@Override
	public @Nullable Component cannotStart(LivingEntity entity, int mode) {
		return entity instanceof ServerPlayer ? null : Component.translatable("ability.fatecraft.gate_of_babylon.players_only");
	}

	private static boolean changesTier(int mode, boolean sneaking) {
		return sneaking && mode != SINGLE_TARGET;
	}

	@Override
	public void onStart(AbilityCast cast) {
		if (!(cast.entity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
			cast.end();
			return;
		}
		if (changesTier(cast.mode(), cast.sneaking())) {
			nextTier(player);
			cast.end();
			return;
		}
		VaultSettings settings = Vault.of(player).settings();
		if (throwable(player, settings.thrownSection()) == 0) {
			player.sendOverlayMessage(Component.translatable("ability.fatecraft.gate_of_babylon.empty", Vault.sectionName(settings.thrownSection())));
			cast.end();
			return;
		}
		if (!AbilityRules.canAfford(AlayaMana.available(player), FateConfig.gate().manaPerWeapon())) {
			// Refused before any gate opens, as a press that can't pay is anywhere else.
			cast.endOutOfMana();
			return;
		}
		boolean copies = level.getGameRules().get(FateGameRules.GATE_OF_BABYLON_COPIES);
		GateWall wall = cast.mode() == SINGLE_TARGET ? null : GateWall.of(settings.volley(), level.getRandom());
		GateSession session = new GateSession(newId(level.getRandom()), settings.thrownSection(), copies, wall);
		player.setAttached(SESSION, session);
		if (session.wall() != null) {
			openVolley(level, player, session, cast.mode());
		}
	}

	private static long newId(RandomSource random) {
		long id = random.nextLong();
		return id == 0 ? 1 : id;
	}

	// A tap leaves a volley's gates waiting for the second press; single target throws only while held.
	@Override
	public void onTap(AbilityCast cast) {
		if (cast.mode() == SINGLE_TARGET) {
			cast.end();
		}
	}

	@Override
	public void onHoldStart(AbilityCast cast) {
		GateSession session = cast.entity().getAttached(SESSION);
		if (session != null) {
			session.firing = true;
			session.holding = true;
		}
	}

	@Override
	public void onPressedAgain(AbilityCast cast) {
		GateSession session = cast.entity().getAttached(SESSION);
		if (session == null || session.firing || !(cast.entity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (changesTier(cast.mode(), cast.sneaking())) {
			nextTier(player);
			session.gates().forEach(GateEntity::close);
			session.gates().clear();
			session.setWall(GateWall.of(Vault.of(player).settings().volley(), level.getRandom()));
			openVolley(level, player, session, cast.mode());
		} else {
			session.firing = true;
		}
	}

	@Override
	public void onTick(AbilityCast cast) {
		GateSession session = cast.entity().getAttached(SESSION);
		if (session == null || !(cast.entity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
			cast.end();
			return;
		}
		session.forgetGone();
		followWall(player, session, cast.mode());
		double perTick = Vault.of(player).settings().gatesPerSecond() / (double) SharedConstants.TICKS_PER_SECOND;
		boolean opening = session.holding && cast.held();
		if (opening) {
			for (session.toOpen += perTick; session.toOpen >= 1; session.toOpen--) {
				if (!openGate(level, player, session, cast.mode(), 0)) {
					session.toOpen = 0;
					break;
				}
			}
		}
		if (session.firing) {
			// Gates owed while none was ready don't all fire at once later.
			session.toFire = Math.min(session.toFire + perTick, perTick + 1);
			for (GateEntity gate = session.firstReady(); gate != null && session.toFire >= 1; gate = session.firstReady()) {
				if (!cast.pay(FateConfig.gate().manaPerWeapon())) {
					return;
				}
				session.toFire--;
				throwFrom(level, player, session, gate, cast.mode());
			}
			session.forgetGone();
			if (session.gates().isEmpty() && (!opening || throwable(player, session.section()) == 0 && !session.copy())) {
				cast.end();
			}
		}
	}

	@Override
	public void onEnd(AbilityCast cast) {
		GateSession session = cast.entity().removeAttached(SESSION);
		if (session != null) {
			session.gates().forEach(GateEntity::close);
		}
	}

	private static void nextTier(ServerPlayer player) {
		Vault vault = Vault.of(player);
		vault.setSettings(vault.settings().withNextTier());
		VaultSettings settings = vault.settings();
		player.sendOverlayMessage(Component.translatable("ability.fatecraft.gate_of_babylon.tier", VaultSettings.tierName(settings.tier()), settings.volley()));
	}

	private static void openVolley(ServerLevel level, ServerPlayer player, GateSession session, int mode) {
		GateWall wall = session.wall();
		if (wall == null) {
			return;
		}
		int gates = session.copy() ? wall.size() : Math.min(wall.size(), throwable(player, session.section()));
		for (int gate = 0; gate < gates; gate++) {
			openGate(level, player, session, mode, gate * VOLLEY_OPENS_OVER / gates);
		}
	}

	private static boolean openGate(ServerLevel level, ServerPlayer player, GateSession session, int mode, int delay) {
		RandomSource random = level.getRandom();
		GateWall wall = session.wall();
		int slot = session.freeSlot(random);
		Vec3 at;
		if (wall != null) {
			if (slot == GateWall.NO_SLOT) {
				return false;
			}
			at = wallPlace(wall, slot, player);
		} else {
			at = placeAround(level, Aim.point(player, FateConfig.gate().aimRange()), random);
			if (at == null) {
				return false;
			}
		}
		ItemStack item = take(player, session, random);
		if (item.isEmpty()) {
			return false;
		}
		session.gates().add(GateEntity.open(level, player, session, item, at, aim(player, at, mode), delay, slot));
		return true;
	}

	private static void followWall(ServerPlayer player, GateSession session, int mode) {
		GateWall wall = session.wall();
		if (wall == null || session.gates().isEmpty()) {
			return;
		}
		Vec3 target = mode == TARGETED_THROW ? Aim.point(player, FateConfig.gate().aimRange()) : null;
		Vec3 look = player.getLookAngle();
		for (GateEntity gate : session.gates()) {
			Vec3 at = wallPlace(wall, gate.slot(), player);
			gate.setPos(at);
			ThrownWeaponEntity.face(gate, target == null ? look : target.subtract(at));
		}
	}

	private static Vec3 wallPlace(GateWall wall, int slot, ServerPlayer player) {
		return wall.at(slot, player.position(), player.getYRot(), player.getScale());
	}

	private static Vec3 aim(ServerPlayer player, Vec3 from, int mode) {
		return mode == AOE_THROW ? player.getLookAngle() : Aim.point(player, FateConfig.gate().aimRange()).subtract(from);
	}

	private static void throwFrom(ServerLevel level, ServerPlayer player, GateSession session, GateEntity gate, int mode) {
		Vec3 direction = session.wall() == null ? aim(player, gate.position(), mode) : gate.facing();
		ItemStack item = gate.launch(direction);
		GateThrows.launch(level, player, item, gate.muzzle(), direction, session.section(), session.copy());
	}

	public static @Nullable Vec3 placeAround(ServerLevel level, Vec3 target, RandomSource random) {
		GateConfig config = FateConfig.gate();
		for (int attempt = 0; attempt < PLACE_TRIES; attempt++) {
			double distance = Mth.lerp(random.nextDouble(), config.singleTargetNearest(), config.singleTargetFarthest());
			double around = random.nextDouble() * Mth.TWO_PI;
			double up = Math.toRadians(Mth.lerp(random.nextDouble(), config.singleTargetMinDegrees(), config.singleTargetMaxDegrees()));
			Vec3 at = target.add(Math.cos(around) * Math.cos(up) * distance, Math.sin(up) * distance, Math.sin(around) * Math.cos(up) * distance);
			if (level.noCollision(AABB.ofSize(at, GATE_ROOM, GATE_ROOM, GATE_ROOM))) {
				return at;
			}
		}
		return null;
	}

	static int throwable(ServerPlayer player, int section) {
		int count = 0;
		for (VaultEntry entry : Vault.of(player).section(section).entries()) {
			if (GateThrows.throwable(entry.item())) {
				count += entry.count();
			}
		}
		return count;
	}

	private static ItemStack take(ServerPlayer player, GateSession session, RandomSource random) {
		int left = throwable(player, session.section());
		if (left == 0) {
			return ItemStack.EMPTY;
		}
		VaultSection section = Vault.of(player).section(session.section());
		List<VaultEntry> entries = section.entries();
		int pick = random.nextInt(left);
		for (int index = 0; index < entries.size(); index++) {
			VaultEntry entry = entries.get(index);
			if (!GateThrows.throwable(entry.item())) {
				continue;
			}
			if (pick < entry.count()) {
				if (session.copy()) {
					return entry.stack(1);
				}
				ItemStack item = section.take(index, entry.item(), 1);
				VaultMenu.refresh(player, session.section());
				return item;
			}
			pick -= entry.count();
		}
		return ItemStack.EMPTY;
	}
}
