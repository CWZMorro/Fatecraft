package io.github.cwzmorro.fatecraft.client.shield;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.cwzmorro.alayacore.util.HeldInFront;
import io.github.cwzmorro.fatecraft.client.effect.LightTypes;
import io.github.cwzmorro.fatecraft.shield.RhoAiasEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Draws Rho Aias: the summon for its first seconds, then the flower, its petals shattering and growing back with its HP. */
public final class RhoAiasRenderer extends EntityRenderer<RhoAiasEntity, RhoAiasRenderer.State> {
	private static final double CULLING_REACH = 3.0;
	private static final RenderType[] TYPES = {LightTypes.BLENDED, LightTypes.ADDED};

	public RhoAiasRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	record Shatter(int petal, double age) {
	}

	public static final class State extends EntityRenderState {
		Vec3 facing = new Vec3(0, 0, 1);
		double radius;
		double ticks;
		double[] bloom = new double[0];
		final List<Shatter> shatters = new ArrayList<>();
		final List<RhoAiasMemory.Hit> hits = new ArrayList<>();
		long now;
		float partialTick;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected AABB getBoundingBoxForCulling(RhoAiasEntity entity) {
		return super.getBoundingBoxForCulling(entity).inflate(entity.radius() * CULLING_REACH);
	}

	// A held shield follows the owner's interpolated view, which avoids server-pose jitter while walking.
	@Override
	public void extractRenderState(RhoAiasEntity entity, State state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		long now = entity.level().getGameTime();
		RhoAiasMemory memory = RhoAiasMemory.of(entity, now);
		int petals = entity.petals();
		LivingEntity owner = entity.following();
		state.facing = owner == null ? entity.getViewVector(partialTick) : owner.getViewVector(partialTick);
		if (owner != null) {
			Vec3 at = HeldInFront.at(owner.getEyePosition(partialTick), state.facing, entity.followDistance());
			state.x = at.x;
			state.y = at.y;
			state.z = at.z;
		}
		state.radius = entity.radius();
		state.ticks = now - entity.castTime() + partialTick;
		state.now = now;
		state.partialTick = partialTick;
		if (state.bloom.length != petals) {
			state.bloom = new double[petals];
		}
		state.shatters.clear();
		for (int k = 0; k < petals; k++) {
			long broken = memory.brokenAt(k);
			if (broken != Long.MIN_VALUE) {
				state.shatters.add(new Shatter(k, now - broken + partialTick));
				state.bloom[k] = 0.0;
				continue;
			}
			long regrown = memory.regrownAt(k);
			double regrowing = regrown == Long.MIN_VALUE ? 1.0 : Math.clamp((now - regrown + partialTick) / RhoAiasLook.BLOOM_TICKS, 0.0, 1.0);
			state.bloom[k] = Math.min(RhoAiasLook.summonBloom(k, petals, state.ticks), regrowing);
		}
		state.hits.clear();
		state.hits.addAll(memory.hits());
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		Vec3 eye = camera.pos.subtract(state.x, state.y, state.z);
		int petals = state.bloom.length;
		for (RenderType type : TYPES) {
			LightTypes.submit(collector, poseStack, type, (pose, buffer) -> {
				RhoAiasLook.summon(pose, buffer, type, eye, state.facing, state.radius, state.ticks);
				RhoAiasLook.flower(pose, buffer, type, eye, state.facing, state.radius, state.bloom, state.ticks);
				for (Shatter shatter : state.shatters) {
					RhoAiasLook.shatter(pose, buffer, type, state.facing, state.radius, petals, shatter.petal(), shatter.age());
				}
				for (RhoAiasMemory.Hit hit : state.hits) {
					RhoAiasLook.hit(pose, buffer, type, eye, state.facing, state.radius, hit.point(), state.now - hit.time() + state.partialTick);
				}
			});
		}
		super.submit(state, poseStack, collector, camera);
	}
}
