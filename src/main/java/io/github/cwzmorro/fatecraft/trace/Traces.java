package io.github.cwzmorro.fatecraft.trace;

import com.mojang.serialization.Codec;
import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.ability.Ability;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.fatecraft.Fatecraft;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.registry.RegistryEntryAddedCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The items a servant has traced, kept as identities rather than stacks: saved, kept through death and synced only to their owner. */
public final class Traces {
	public static final TagKey<Item> SWORDS = TagKey.create(Registries.ITEM, Fatecraft.id("swords"));
	// Divine constructs (Ea) can't be traced; volley-excluded weapons are only projected by hand; strained ones cost the Excalibur strain.
	private static final TagKey<Item> DIVINE_CONSTRUCTS = TagKey.create(Registries.ITEM, Fatecraft.id("divine_constructs"));
	private static final TagKey<Item> NOT_VOLLEY_WEAPONS = TagKey.create(Registries.ITEM, Fatecraft.id("not_volley_weapons"));
	private static final TagKey<Item> STRAINED_PROJECTIONS = TagKey.create(Registries.ITEM, Fatecraft.id("strained_projections"));
	private static final TagKey<Item> STARTING_TRACES = TagKey.create(Registries.ITEM, Fatecraft.id("starting_traces"));
	public static final AttachmentType<List<Identifier>> STORAGE = AttachmentRegistry.create(Fatecraft.id("traces"), builder -> builder
		.initializer(List::of)
		.persistent(Codec.list(Identifier.CODEC))
		.copyOnDeath()
		.syncWith(Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));

	private Traces() {
	}

	/** Returns whether the entity's servant has Trace; what it learned outlasts a change of servant. */
	public static boolean canTrace(LivingEntity entity) {
		return Abilities.owns(entity, Trace.KEY.identifier());
	}

	public static boolean sword(ItemStack stack) {
		return stack.is(ItemTags.SWORDS) || stack.is(SWORDS);
	}

	public static boolean volleyable(Item item) {
		return !item.getDefaultInstance().is(NOT_VOLLEY_WEAPONS);
	}

	public static boolean divine(ItemStack stack) {
		return stack.is(DIVINE_CONSTRUCTS);
	}

	public static boolean strained(Item item) {
		return item.getDefaultInstance().is(STRAINED_PROJECTIONS);
	}

	/** Returns the entity's traced items; the starting ones are implicit, so the owner's client has them at once. */
	public static List<Identifier> known(LivingEntity entity) {
		List<Identifier> known = new ArrayList<>();
		BuiltInRegistries.ITEM.getTagOrEmpty(STARTING_TRACES).forEach(holder -> known.add(BuiltInRegistries.ITEM.getKey(holder.value())));
		for (Identifier id : entity.getAttachedOrElse(STORAGE, List.of())) {
			if (!known.contains(id) && !divine(BuiltInRegistries.ITEM.getValue(id).getDefaultInstance())) {
				known.add(id);
			}
		}
		return List.copyOf(known);
	}

	public static void learn(LivingEntity entity, ItemStack sword) {
		if (canTrace(entity) && sword(sword) && !divine(sword)) {
			record(entity, sword);
		}
	}

	/** Learns a craftable {@code weapon} a clash with the Gate of Babylon countered, even one that is not a sword. */
	public static void learnCounter(LivingEntity entity, ItemStack weapon) {
		if (canTrace(entity) && !divine(weapon)) {
			record(entity, weapon);
		}
	}

	private static void record(LivingEntity entity, ItemStack stack) {
		List<Identifier> known = new ArrayList<>(known(entity));
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (!known.contains(id)) {
			known.add(id);
			entity.setAttached(STORAGE, List.copyOf(known));
		}
	}

	/**
	 * Returns the servant's abilities with a Project for each traced item, on server and client alike. It asks the servant's
	 * own list for Trace, not {@link #canTrace}, which would come back here.
	 */
	public static List<ResourceKey<Ability>> owned(LivingEntity entity, List<ResourceKey<Ability>> servantAbilities) {
		if (!servantAbilities.contains(Trace.KEY)) {
			return servantAbilities;
		}
		List<ResourceKey<Ability>> owned = new ArrayList<>(servantAbilities);
		for (Identifier id : known(entity)) {
			ResourceKey<Ability> key = Project.key(id);
			if (AlayaRegistries.ABILITY.containsKey(key.identifier()) && !owned.contains(key)) {
				owned.add(key);
			}
		}
		return List.copyOf(owned);
	}

	// One dormant Project per item, since data-pack tags load after the registries freeze; only traced ones are owned.
	public static void init() {
		Projections.init();
		Registry.register(AlayaRegistries.ABILITY, Trace.KEY, new Trace());
		SwordBody.register();
		ExcaliburStrain.register();
		for (Item item : BuiltInRegistries.ITEM) {
			registerProject(BuiltInRegistries.ITEM.getKey(item), item);
		}
		RegistryEntryAddedCallback.event(BuiltInRegistries.ITEM).register((rawId, id, item) -> registerProject(id, item));
	}

	private static void registerProject(Identifier id, Item item) {
		Registry.register(AlayaRegistries.ABILITY, Project.key(id), new Project(item));
	}
}
