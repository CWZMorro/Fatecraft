package io.github.cwzmorro.fatecraft.weapon;

import com.mojang.serialization.Codec;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.item.WeaponRules;
import io.github.cwzmorro.alayacore.item.WeaponStats;
import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.network.EaDrawPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;

/** Fatecraft's weapons: their items, melee stats and the tags that put them under Alaya Core's {@link WeaponRules}. */
public final class FateWeapons {
	private static final float BEAM_BOX = 0.5F;
	private static final int BEAM_TRACKING_CHUNKS = 16;
	private static final float EA_DRAW_WIDTH = 0.8F;
	private static final float EA_DRAW_HEIGHT = 2.6F;
	// A player's own attack speed, which a weapon's modifiers add to.
	private static final double PLAYER_ATTACKS_PER_SECOND = 4.0;
	private static final List<Item> ITEMS = new ArrayList<>();

	public static final Item BAB_ILU = register("bab_ilu", BabIluItem::new, ItemAttributeModifiers.EMPTY);
	public static final Item EA = register("ea", EaItem::new, melee(FateConfig.ea().meleeDamage(), FateConfig.ea().attackSpeed()));
	public static final Item EXCALIBUR = register("excalibur", ExcaliburItem::new,
		melee(FateConfig.artoria().excaliburDamage(), FateConfig.artoria().excaliburSpeed()));
	public static final Item CALIBURN = register("caliburn", CaliburnItem::new,
		melee(FateConfig.caliburn().damage(), FateConfig.caliburn().speed()));
	public static final Item AVALON = register("avalon", AvalonItem::new,
		properties(ItemAttributeModifiers.EMPTY).component(DataComponents.LORE, tooltip("item.fatecraft.avalon.tooltip")));
	public static final Item PHOTON_RAY = register("photon_ray", PhotonRayItem::new,
		melee(FateConfig.altera().meleeDamage(), FateConfig.altera().attackSpeed()));
	public static final Item KANSHOU = register("kanshou", PairedSwordItem::new,
		melee(FateConfig.emiyaWeapons().singleDamage(), FateConfig.emiyaWeapons().singleSpeed()));
	public static final Item BAKUYA = register("bakuya", PairedSwordItem::new,
		melee(FateConfig.emiyaWeapons().singleDamage(), FateConfig.emiyaWeapons().singleSpeed()));
	public static final Item CALADBOLG = register("caladbolg", CaladbolgItem::new,
		melee(FateConfig.emiyaWeapons().caladbolgDamage(), FateConfig.emiyaWeapons().caladbolgSpeed()));
	public static final Item EMIYA_BOW = register("emiya_bow", EmiyaBowItem::new, ItemAttributeModifiers.EMPTY);
	public static final Item HRUNTING = register("hrunting", HruntingItem::new,
		melee(FateConfig.emiyaWeapons().hruntingDamage(), FateConfig.emiyaWeapons().hruntingSpeed()));
	// Kept through the holder's death: Ea and every weapon except Kanshou and Bakuya.
	public static final TagKey<Item> KEPT_ON_DEATH = TagKey.create(Registries.ITEM, Fatecraft.id("kept_on_death"));
	public static final TagKey<Item> INDESTRUCTIBLE = TagKey.create(Registries.ITEM, Fatecraft.id("indestructible"));
	public static final EntityType<EnumaElishBeam> ENUMA_ELISH_BEAM = Fatecraft.entity("enuma_elish_beam",
		EntityType.Builder.<EnumaElishBeam>of(EnumaElishBeam::new, MobCategory.MISC).sized(BEAM_BOX, BEAM_BOX).noSave().clientTrackingRange(BEAM_TRACKING_CHUNKS));
	public static final EntityType<EaDrawEntity> EA_DRAW = Fatecraft.entity("ea_draw",
		EntityType.Builder.<EaDrawEntity>of(EaDrawEntity::new, MobCategory.MISC).sized(EA_DRAW_WIDTH, EA_DRAW_HEIGHT));
	public static final EntityType<ThrownWeaponEntity> THROWN_WEAPON = Fatecraft.entity("thrown_weapon", ThrownWeaponEntity.type(ThrownWeaponEntity::new));
	private static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Fatecraft.id("main"));
	// Changing the spin or opening component doesn't replay the hand's swap animation.
	public static final DataComponentType<WeaponSpin> SPIN = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Fatecraft.id("weapon_spin"),
		DataComponentType.<WeaponSpin>builder().networkSynchronized(WeaponSpin.STREAM_CODEC).ignoreSwapAnimation().build());
	public static final DataComponentType<WeaponOpening> OPENING = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Fatecraft.id("weapon_opening"),
		DataComponentType.<WeaponOpening>builder().networkSynchronized(WeaponOpening.STREAM_CODEC).ignoreSwapAnimation().build());
	// A thrown weapon whose place is its tail, not its middle (a sword shot from a bow).
	public static final DataComponentType<Boolean> THROWN_FROM_TAIL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
		Fatecraft.id("thrown_from_tail"), DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());

	private FateWeapons() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, ItemAttributeModifiers attributes) {
		return register(name, factory, properties(attributes));
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Fatecraft.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
		ITEMS.add(item);
		return item;
	}

	private static Item.Properties properties(ItemAttributeModifiers attributes) {
		return new Item.Properties().stacksTo(1).attributes(attributes);
	}

	// Plain colour like the item's other tooltip lines, not the lore's italic purple.
	private static ItemLore tooltip(String translationKey) {
		List<Component> lines = List.of(Component.translatable(translationKey));
		return new ItemLore(lines, lines);
	}

	static ItemAttributeModifiers melee(double damage, double attacksPerSecond) {
		return ItemAttributeModifiers.builder()
			.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage - WeaponStats.PLAYER_ATTACK_DAMAGE,
				AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, attacksPerSecond - PLAYER_ATTACKS_PER_SECOND,
				AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.build();
	}

	public static void init() {
		PairedThrow.init();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB, FabricCreativeModeTab.builder()
			.title(Component.translatable(TAB.identifier().toLanguageKey("itemGroup")))
			.icon(() -> new ItemStack(EA))
			.displayItems((parameters, output) -> ITEMS.forEach(output::accept))
			.build());
		Registry.register(AlayaRegistries.ABILITY, EnumaElish.KEY, new EnumaElish());
		PayloadTypeRegistry.clientboundPlay().register(EaDrawPayload.TYPE, EaDrawPayload.STREAM_CODEC);
		EaVault.register();
	}
}
