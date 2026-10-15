package io.github.cwzmorro.fatecraft.trace;

import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.alayacore.ability.AbilityCast;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.combat.Aim;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.gate.GateWall;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import io.github.cwzmorro.fatecraft.weapon.PairedSwordItem;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Projects one traced item, into empty hands or as a targeted volley of copies that break on use. */
public final class Project extends Ability {
	public static final int PROJECT = 0;
	public static final int TRACE_ON = 1;
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Fatecraft.id("trace_on"));
	private static final int MODES = TRACE_ON + 1;
	private static final List<String> MODE_NAMES = List.of("ability.fatecraft.project.mode.1", "ability.fatecraft.project.mode.2");
	// Every Project ability's id is this followed by its item's namespace and path.
	private static final Identifier ROOT = Fatecraft.id("project/");
	private final Item item;

	public Project(Item item) {
		super(new Properties().modes(MODES));
		this.item = item;
	}

	public static ResourceKey<Ability> key(Identifier item) {
		return ResourceKey.create(AlayaRegistries.ABILITY_KEY, ROOT.withSuffix(item.getNamespace() + "/" + item.getPath()));
	}

	public static @Nullable Item item(Identifier ability) {
		if (!ability.getNamespace().equals(ROOT.getNamespace()) || !ability.getPath().startsWith(ROOT.getPath())) {
			return null;
		}
		String[] parts = ability.getPath().substring(ROOT.getPath().length()).split("/", 2);
		return parts.length == 2 ? BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(parts[0], parts[1])).orElse(null) : null;
	}

	public static String modeName(int mode) {
		return MODE_NAMES.get(mode);
	}

	@Override
	public @Nullable Component cannotStart(LivingEntity entity, int mode) {
		if (!Traces.canTrace(entity) || !Traces.known(entity).contains(BuiltInRegistries.ITEM.getKey(this.item))) {
			return Component.translatable("ability.fatecraft.trace.emiya_only");
		}
		if (mode == TRACE_ON) {
			if (!Traces.sword(this.item.getDefaultInstance())) {
				return Component.translatable("ability.fatecraft.trace.swords_only");
			}
			return Traces.volleyable(this.item) ? null : Component.translatable("ability.fatecraft.trace.not_volley");
		}
		return Projections.emptyHands(entity) == 0 ? Component.translatable("ability.fatecraft.trace.hands_busy") : null;
	}

	@Override
	public double startCost(LivingEntity entity, int mode) {
		if (Traces.strained(this.item)) {
			return ExcaliburStrain.manaCost(entity);
		}
		if (Projections.free(entity)) {
			return 0;
		}
		int count = mode == TRACE_ON ? FateConfig.trace().volley() : PairedSwordItem.paired(this.item) ? Projections.emptyHands(entity) : 1;
		return count * Projections.cost(this.item);
	}

	@Override
	public void onStart(AbilityCast cast) {
		LivingEntity entity = cast.entity();
		if (cast.mode() == TRACE_ON && entity.level() instanceof ServerLevel level) {
			volley(level, entity, Projections.create(this.item), FateConfig.trace().volley());
		} else if (cast.mode() == PROJECT) {
			if (Traces.strained(this.item)) {
				ExcaliburStrain.begin(entity);
			}
			if (PairedSwordItem.paired(this.item)) {
				Projections.fillPair(entity, this.item);
			} else if (Projections.emptyHands(entity) > 0) {
				InteractionHand hand = entity.getMainHandItem().isEmpty() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
				entity.setItemInHand(hand, Projections.create(this.item));
			}
		}
		cast.end();
	}

	private static void volley(ServerLevel level, LivingEntity owner, ItemStack sword, int count) {
		GateWall wall = GateWall.of(count, level.getRandom());
		for (int slot = 0; slot < count; slot++) {
			Vec3 from = wall.at(slot, owner.position(), owner.getYRot(), owner.getScale());
			Vec3 direction = Aim.point(owner, FateConfig.trace().aimRange()).subtract(from);
			// Even a tagged sword with no attack modifier is still a sword projectile.
			ThrownWeaponEntity copy = ThrownWeaponEntity.launch(level, FateWeapons.THROWN_WEAPON, owner, sword, from,
				direction.normalize().scale(AlayaConfig.thrownWeapons().blocksPerTick()), DAMAGE);
			copy.form(FateConfig.trace().formationTicks());
		}
	}
}
