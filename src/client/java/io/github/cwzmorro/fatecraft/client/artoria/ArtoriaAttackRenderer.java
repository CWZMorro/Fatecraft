package io.github.cwzmorro.fatecraft.client.artoria;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.artoria.ArtoriaAttackEntity;
import io.github.cwzmorro.fatecraft.artoria.ArtoriaConfig;
import io.github.cwzmorro.fatecraft.client.effect.LightStrokes;
import io.github.cwzmorro.fatecraft.client.effect.LightTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Draws the light of Artoria's sword skills, each in its own look. */
public final class ArtoriaAttackRenderer extends EntityRenderer<ArtoriaAttackEntity, ArtoriaAttackRenderer.State> {
	public ArtoriaAttackRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	public static final class State extends EntityRenderState {
		ArtoriaAttackEntity.Kind kind = ArtoriaAttackEntity.Kind.WIND;
		ArtoriaAttackEntity.Phase phase = ArtoriaAttackEntity.Phase.SLASH;
		Vec3 direction = Vec3.ZERO;
		float phaseAge;
		Vec3 beamStart = Vec3.ZERO;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	// Mana Shot's beam runs from the blade to the entity, so it is culled by the whole beam, not the blast point.
	@Override
	protected AABB getBoundingBoxForCulling(ArtoriaAttackEntity entity) {
		AABB own = super.getBoundingBoxForCulling(entity);
		if (entity.kind() != ArtoriaAttackEntity.Kind.SHOT || entity.phase() != ArtoriaAttackEntity.Phase.BLAST) {
			return own;
		}
		return own.minmax(new AABB(entity.beamStart(), entity.beamStart()));
	}

	@Override
	public void extractRenderState(ArtoriaAttackEntity entity, State state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.kind = entity.kind();
		state.phase = entity.phase();
		state.phaseAge = entity.phaseAge() + partialTick;
		state.direction = entity.getLookAngle();
		state.beamStart = entity.beamStart().subtract(entity.getPosition(partialTick));
	}

	@Override
	public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
		Vec3 eye = camera.pos.subtract(state.x, state.y, state.z);
		LightTypes.submit(collector, poses, state.kind == ArtoriaAttackEntity.Kind.WIND ? LightTypes.BLENDED_OVER : LightTypes.ADDED,
			(pose, buffer) -> draw(pose, buffer, eye, state));
		if (state.kind == ArtoriaAttackEntity.Kind.SHOT) {
			LightTypes.submit(collector, poses, LightTypes.BLENDED, (pose, buffer) -> ManaShotLook.drawBlended(pose, buffer, eye, state, FateConfig.artoria()));
		}
		super.submit(state, poses, collector, camera);
	}

	private static void draw(PoseStack.Pose pose, VertexConsumer buffer, Vec3 eye, State s) {
		ArtoriaConfig c = FateConfig.artoria();
		Vec3 right = LightStrokes.perpendicular(s.direction);
		Vec3 up = s.direction.cross(right).normalize();
		switch (s.kind) {
			case WIND -> StrikeAirLook.draw(pose, buffer, eye, s, c, right, up);
			case GOLD -> ManaSlashLook.draw(pose, buffer, eye, s, c);
			case SHOT -> ManaShotLook.draw(pose, buffer, eye, s, c, right, up);
		}
	}
}
