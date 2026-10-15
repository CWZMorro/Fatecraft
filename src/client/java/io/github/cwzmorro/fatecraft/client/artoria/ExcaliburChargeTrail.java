package io.github.cwzmorro.fatecraft.client.artoria;

import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.artoria.ExcaliburEntity;
import io.github.cwzmorro.fatecraft.artoria.ExcaliburNP;
import io.github.cwzmorro.fatecraft.client.effect.LightTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Excalibur Beam's charge sparkles and motes. Each stays where it was born, so walking while she charges leaves a trail;
 * the lights outlive the charge and the beam.
 */
public final class ExcaliburChargeTrail {
	private static final List<ExcaliburRenderer.ChargeLight> LIGHTS = new ArrayList<>();
	private static final Map<UUID, Integer> SEEN_AGE = new HashMap<>();
	private static ClientLevel level;
	private static List<ExcaliburRenderer.ChargeLight> frame = List.of();
	private static Vec3 camera = Vec3.ZERO;
	private static double clock;

	private ExcaliburChargeTrail() {
	}

	public static List<Vec3> birthPositions() {
		return frame.stream().map(ExcaliburRenderer.ChargeLight::origin).toList();
	}

	public static void register() {
		LevelRenderEvents.END_EXTRACTION.register(context -> {
			if (level != context.level()) {
				LIGHTS.clear();
				SEEN_AGE.clear();
				level = context.level();
			}
			clock = level.getGameTime() + context.deltaTracker().getGameTimeDeltaPartialTick(false);
			camera = context.camera().position();
			LIGHTS.removeIf(light -> clock - light.born() >= ExcaliburRenderer.chargeLifetime());
			Set<UUID> charging = new HashSet<>();
			for (Entity entity : level.entitiesForRendering()) {
				if (entity instanceof ExcaliburEntity light && light.mode() == ExcaliburNP.HELD
					&& light.phase() == ExcaliburEntity.Phase.CHARGE) {
					charging.add(light.getUUID());
					emit(light);
				}
			}
			// Lights already born stay where they are after their charge ends.
			SEEN_AGE.keySet().retainAll(charging);
			frame = List.copyOf(LIGHTS);
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
			List<ExcaliburRenderer.ChargeLight> lights = frame;
			if (lights.isEmpty()) {
				return;
			}
			Vec3 eye = camera;
			double ticks = clock;
			LightTypes.submit(context.submitNodeCollector(), context.poseStack(), LightTypes.BLENDED, (pose, buffer) -> {
				for (ExcaliburRenderer.ChargeLight light : lights) {
					ExcaliburRenderer.drawChargeLight(pose, buffer, eye, light, ticks, false);
				}
			});
			LightTypes.submit(context.submitNodeCollector(), context.poseStack(), LightTypes.ADDED, (pose, buffer) -> {
				for (ExcaliburRenderer.ChargeLight light : lights) {
					ExcaliburRenderer.drawChargeLight(pose, buffer, eye, light, ticks, true);
				}
			});
		});
	}

	// The first sight of a charge fills every lane at once, as if already under way, all born where she stands now.
	private static void emit(ExcaliburEntity light) {
		int age = light.phaseAge();
		Integer seen = SEEN_AGE.put(light.getUUID(), age);
		if (seen != null && seen == age) {
			return;
		}
		double life = ExcaliburRenderer.chargeLifetime();
		// The light is held in front of her eyes; she stands behind it.
		Vec3 caster = light.position().subtract(light.getLookAngle().scale(FateConfig.artoria().excaliburStartRadius()));
		for (int lane = 0; lane < ExcaliburRenderer.chargeLanes(); lane++) {
			double cycle = age / life + ExcaliburRenderer.chargePhase(lane);
			if (seen == null) {
				double into = (cycle - Math.floor(cycle)) * life;
				LIGHTS.add(ExcaliburRenderer.chargeLight(caster, level.getGameTime() - into, lane, age - into));
			} else if (Math.floor(cycle) > Math.floor(seen / life + ExcaliburRenderer.chargePhase(lane))) {
				LIGHTS.add(ExcaliburRenderer.chargeLight(caster, level.getGameTime(), lane, age));
			}
		}
	}
}
