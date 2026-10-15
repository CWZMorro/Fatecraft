package io.github.cwzmorro.fatecraft.client.ubw;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.cwzmorro.fatecraft.client.effect.LightTypes;
import io.github.cwzmorro.fatecraft.ubw.UbwConfig;
import io.github.cwzmorro.fatecraft.ubw.UbwEnvironmentEntity;
import io.github.cwzmorro.fatecraft.ubw.UbwMembership;
import io.github.cwzmorro.fatecraft.ubw.UbwOpening;
import io.github.cwzmorro.fatecraft.ubw.UbwShatter;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Draws the marble's scenery in the world, for cameras inside it. */
public final class UbwEnvironmentRenderer extends EntityRenderer<UbwEnvironmentEntity, UbwEnvironmentRenderer.State> {
	public UbwEnvironmentRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	public static final class State extends EntityRenderState {
		BlockPos center = BlockPos.ZERO;
		int radius;
		int floorY;
		boolean active;
		double age;
		double originX;
		double originZ;
		double span;
		UbwOpening.Timing timing = UbwConfig.DEFAULTS.opening();
		double shatterAge;
		int shatterTicks;
		Vec3 impact = Vec3.ZERO;
		double shatterSpan;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(UbwEnvironmentEntity environment, State state, float partialTick) {
		super.extractRenderState(environment, state, partialTick);
		state.center = environment.center();
		state.radius = environment.radius();
		state.floorY = environment.floorY();
		state.active = environment.active();
		// Negative while his mana gathers, before the ring ignites.
		state.age = environment.level().getGameTime() - environment.openedAt() + partialTick;
		state.originX = environment.origin().x();
		state.originZ = environment.origin().z();
		state.span = environment.span();
		state.timing = environment.openingTiming();
		state.shatterTicks = environment.shatterTicks();
		if (environment.shattering()) {
			state.shatterAge = Math.max(0, environment.level().getGameTime() - environment.shatteredAt() + partialTick);
			// An opening broken before it finished stays as far as it had spread.
			state.age = Math.min(state.age, Math.max(0, environment.shatteredAt() - environment.openedAt()));
			state.impact = environment.impact();
			state.shatterSpan = environment.shatterSpan();
		}
	}

	public static boolean visible(UbwEnvironmentEntity environment, Vec3 camera) {
		return environment.active() && UbwMembership.contains(environment.center(), environment.radius(), camera);
	}

	@Override
	protected AABB getBoundingBoxForCulling(UbwEnvironmentEntity environment) {
		return new AABB(environment.center()).inflate(environment.radius());
	}

	private static UbwCrumble crumble(State state) {
		if (state.shatterTicks == 0) {
			return null;
		}
		double cracks = UbwShatter.cracks(state.shatterAge, state.shatterTicks, state.shatterSpan);
		double fallen = UbwShatter.fall(state.shatterAge, state.shatterTicks, state.shatterSpan);
		return new UbwCrumble(state.impact, cracks, fallen);
	}

	@Override
	public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.active) {
			return;
		}
		boolean inside = UbwMembership.contains(state.center, state.radius, camera.pos);
		double age = state.age;
		// His gathering mana shows to anyone near; only cameras inside the sphere see the scenery and the flash.
		boolean gathering = state.shatterTicks == 0 && UbwMana.shows(age, state.timing.chargeTicks());
		if (!inside && !gathering) {
			return;
		}
		poses.pushPose();
		// The synced center, not the interpolated entity position, is the sphere's origin.
		poses.translate(state.center.getX() - state.x, state.center.getY() - state.y, state.center.getZ() - state.z);
		Vec3 eye = camera.pos.subtract(Vec3.atLowerCornerOf(state.center));
		int radius = state.radius;
		double floor = state.floorY + 1 - state.center.getY();
		if (gathering) {
			UbwMana.Charge charge = new UbwMana.Charge(eye, new Vec3(state.originX, floor, state.originZ), age, state.timing.chargeTicks());
			LightTypes.submit(collector, poses, LightTypes.BLENDED, (pose, buffer) -> UbwMana.body(pose, buffer, charge));
			LightTypes.submit(collector, poses, LightTypes.ADDED, (pose, buffer) -> UbwMana.glow(pose, buffer, charge));
			if (inside) {
				LightTypes.submit(collector, poses, LightTypes.BLENDED_OVER, (pose, buffer) -> UbwMana.flash(pose, buffer, charge));
			}
		}
		// Nothing of the field shows while the mana gathers, before the ring ignites.
		if (!inside || age < 0) {
			poses.popPose();
			return;
		}
		double reach = UbwOpening.reach(age, state.span, state.timing);
		// The scenery replaces the real world only behind the opening's front of fire.
		UbwReveal reveal = age >= state.timing.spreadTicks() ? UbwReveal.WHOLE : new UbwReveal(state.originX, state.originZ, reach);
		UbwCrumble crumble = crumble(state);
		collector.submitCustomGeometry(poses, UbwRenderTypes.SKY,
			(pose, buffer) -> UbwSky.draw(pose, buffer, radius, reveal, crumble));
		collector.submitCustomGeometry(poses, UbwRenderTypes.GEARS,
			(pose, buffer) -> UbwGears.draw(pose, buffer, eye, radius, reveal, crumble, age));
		collector.order(UbwRenderTypes.VEIL_ORDER).submitCustomGeometry(poses, UbwRenderTypes.VEIL,
			(pose, buffer) -> UbwGears.veil(pose, buffer, radius, reveal, crumble, age));
		if (crumble != null) {
			LightTypes.submit(collector, poses, LightTypes.SOLID, (pose, buffer) -> UbwCracks.plates(pose, buffer, eye, radius, floor, crumble));
			LightTypes.submit(collector, poses, LightTypes.BLENDED, (pose, buffer) -> UbwCracks.dust(pose, buffer, eye, radius, floor, crumble));
			LightTypes.submit(collector, poses, LightTypes.ADDED, (pose, buffer) -> UbwSky.edges(pose, buffer, eye, crumble));
			LightTypes.submit(collector, poses, LightTypes.ADDED,
				(pose, buffer) -> UbwCracks.glow(pose, buffer, eye, radius, floor, crumble));
			LightTypes.submit(collector, poses, LightTypes.BLENDED,
				(pose, buffer) -> UbwCracks.rift(pose, buffer, eye, radius, floor, crumble));
		} else if (UbwFireRing.progress(age, state.timing) < 1) {
			double rim = UbwOpening.floorRadius(radius, floor);
			UbwFireRing.Ring ring = new UbwFireRing.Ring(eye, state.originX, state.originZ, floor, rim, age, reach, state.span, state.timing);
			LightTypes.submit(collector, poses, LightTypes.BLENDED, (pose, buffer) -> UbwFireRing.wall(pose, buffer, ring));
			LightTypes.submit(collector, poses, LightTypes.ADDED, (pose, buffer) -> UbwFireRing.glow(pose, buffer, ring));
		}
		poses.popPose();
	}
}
