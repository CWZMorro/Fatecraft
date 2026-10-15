package io.github.cwzmorro.fatecraft.gate;

import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.thrown.ThrownWeaponEntity;
import io.github.cwzmorro.fatecraft.Fatecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Registers the Gate of Babylon: the ability, its gates and thrown weapons, and how thrown items come back. */
public final class Gates {
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Fatecraft.id("gate_of_babylon"));
	private static final float BOX = 0.5F;
	// Tracking range in chunks and update interval in ticks: the gates cover the maximum vanilla client chunk view; vanilla tracking still caps it to each viewer's distance.
	private static final int GATE_TRACKING_CHUNKS = 32;
	private static final int GATE_UPDATE_TICKS = 1;
	public static final EntityType<GateEntity> GATE = Fatecraft.entity("gate",
		EntityType.Builder.<GateEntity>of(GateEntity::new, MobCategory.MISC).sized(BOX, BOX).noSave().clientTrackingRange(GATE_TRACKING_CHUNKS)
			.updateInterval(GATE_UPDATE_TICKS));
	public static final EntityType<TreasureEntity> TREASURE = Fatecraft.entity("treasure", ThrownWeaponEntity.type(TreasureEntity::new));

	private Gates() {
	}

	public static void init() {
		Registry.register(AlayaRegistries.ABILITY, GateOfBabylon.KEY, new GateOfBabylon());
		GateReturns.register();
	}
}
