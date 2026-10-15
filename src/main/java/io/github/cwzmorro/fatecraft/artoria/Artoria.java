package io.github.cwzmorro.fatecraft.artoria;

import io.github.cwzmorro.alayacore.ability.PassiveAbility;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.fatecraft.Fatecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Artoria's sword skills and Excalibur noble phantasm. */
public final class Artoria {
	public static final EntityType<ArtoriaAttackEntity> ATTACK = Fatecraft.entity("artoria_attack",
		EntityType.Builder.<ArtoriaAttackEntity>of(ArtoriaAttackEntity::new, MobCategory.MISC).sized(1, 1)
			.noSave().clientTrackingRange(8).updateInterval(1));

	public static final EntityType<ExcaliburEntity> EXCALIBUR = Fatecraft.entity("excalibur",
		EntityType.Builder.<ExcaliburEntity>of(ExcaliburEntity::new, MobCategory.MISC).sized(1, 1)
			.noSave().clientTrackingRange(16).updateInterval(1));

	// Like an ender pearl's ticket, it outlives its last renewal by this many ticks.
	private static final long EXCALIBUR_TICKET_TIMEOUT = 40;
	public static final TicketType EXCALIBUR_TICKET = Registry.register(BuiltInRegistries.TICKET_TYPE, Fatecraft.id("excalibur"),
		new TicketType(EXCALIBUR_TICKET_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION));
	public static final DataComponentType<Boolean> EXCALIBUR_CHARGE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
		Fatecraft.id("excalibur_charge"), DataComponentType.<Boolean>builder().networkSynchronized(ByteBufCodecs.BOOL).ignoreSwapAnimation().build());
	public static final DataComponentType<Boolean> MANA_SHOT_CHARGE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
		Fatecraft.id("mana_shot_charge"), DataComponentType.<Boolean>builder().networkSynchronized(ByteBufCodecs.BOOL).ignoreSwapAnimation().build());

	private Artoria() {
	}

	public static void init() {
		Avalon.register();
		Registry.register(AlayaRegistries.ABILITY, StrikeAir.KEY, new StrikeAir());
		Registry.register(AlayaRegistries.ABILITY, ManaBurst.KEY, new ManaBurst());
		Registry.register(AlayaRegistries.ABILITY, ManaSlash.KEY, new ManaSlash());
		Registry.register(AlayaRegistries.ABILITY, ManaShot.KEY, new ManaShot());
		Registry.register(AlayaRegistries.ABILITY, ExcaliburNP.KEY, new ExcaliburNP());
		Registry.register(AlayaRegistries.ABILITY, Avalon.KEY, new PassiveAbility());
	}
}
