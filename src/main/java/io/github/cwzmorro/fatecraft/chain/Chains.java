package io.github.cwzmorro.fatecraft.chain;

import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.fatecraft.Fatecraft;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Registers Enkidu: the ability, its bind and links, the bound effect and the tightening's damage type. */
public final class Chains {
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Fatecraft.id("enkidu"));
	// The bind's box is only a point at the target's feet; the tracking range is in chunks.
	private static final float BIND_BOX = 0.5F;
	private static final float LINK_BOX = 0.8F;
	private static final int TRACKING_CHUNKS = 10;
	public static final EntityType<ChainBind> BIND = Fatecraft.entity("chain_bind",
		EntityType.Builder.<ChainBind>of(ChainBind::new, MobCategory.MISC).sized(BIND_BOX, BIND_BOX).noSave().clientTrackingRange(TRACKING_CHUNKS));
	public static final EntityType<ChainLink> LINK = Fatecraft.entity("chain_link",
		EntityType.Builder.<ChainLink>of(ChainLink::new, MobCategory.MISC).sized(LINK_BOX, LINK_BOX).noSave().clientTrackingRange(TRACKING_CHUNKS));
	public static final Identifier BOUND_ID = Fatecraft.id("bound");
	public static final Holder<MobEffect> BOUND = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, BOUND_ID, new BoundEffect());
	public static final AttachmentType<Integer> BINDING = AttachmentRegistry.create(Fatecraft.id("binding"));

	private Chains() {
	}

	public static void init() {
		Registry.register(AlayaRegistries.ABILITY, Enkidu.KEY, new Enkidu());
	}
}
