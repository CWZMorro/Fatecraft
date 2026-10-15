package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.combat.AttackData;
import io.github.cwzmorro.alayacore.shield.ShieldEntity;
import io.github.cwzmorro.alayacore.shield.ShieldShape;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

// A plain disc shield like Rho Aias, for the tests of attacks against a shield.
public final class FateTestShields implements ModInitializer {
	private static final double RADIUS = 3.0;
	private static final float SIZE = 0.5F;
	static final EntityType<Disc> DISC = register();

	static final class Disc extends ShieldEntity {
		private static final ShieldShape SHAPE = new ShieldShape.Disc(RADIUS);
		final List<Long> hitTimes = new ArrayList<>();

		private Disc(EntityType<Disc> type, Level level) {
			super(type, level);
		}

		@Override
		public ShieldShape shape() {
			return SHAPE;
		}

		@Override
		protected void onHit(DamageSource source, @Nullable AttackData attack, Vec3 point, double hpTaken) {
			this.hitTimes.add(this.level().getGameTime());
		}
	}

	private static EntityType<Disc> register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, FateTests.id("disc_shield"));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<Disc>of(Disc::new, MobCategory.MISC).sized(SIZE, SIZE).build(key));
	}

	@Override
	public void onInitialize() {
	}
}
