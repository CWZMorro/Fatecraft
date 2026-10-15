package io.github.cwzmorro.fatecraft.client.chain;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.cwzmorro.fatecraft.chain.ChainBind;
import io.github.cwzmorro.fatecraft.chain.ChainLayout;
import io.github.cwzmorro.fatecraft.client.effect.GateRipple;
import io.github.cwzmorro.fatecraft.client.effect.LightTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Enkidu binding a target: gates open round it and chains shoot out to coil round it, pulling back when it is let go. */
public final class ChainBindRenderer extends EntityRenderer<ChainBind, ChainBindRenderer.State> {
	private static final double GATE_RADIUS = 0.8;
	private static final double COIL_TURNS = 1.25;
	private static final double COIL_OUT = 0.25;
	private static final double COIL_TIGHTENED = 0.8;
	private static final int COIL_POINTS = 24;
	private static final double COIL_END_INSET = 0.15;

	public ChainBindRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	public static final class State extends EntityRenderState {
		final List<Vec3> gates = new ArrayList<>();
		AABB target = new AABB(0, 0, 0, 0, 0, 0);
		long seed;
		int boundAt;
		int releasedAt;
		boolean tightening;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ChainBind bind, State state, float partialTick) {
		super.extractRenderState(bind, state, partialTick);
		Vec3 at = bind.getPosition(partialTick);
		state.gates.clear();
		ChainLayout.gates(bind.origin(), bind.radius(), bind.seed(), bind.chains()).forEach(gate -> state.gates.add(gate.subtract(at)));
		Entity target = bind.level().getEntity(bind.targetId());
		AABB box = target != null ? target.getBoundingBox().move(target.getPosition(partialTick).subtract(target.position())) : bind.getBoundingBox();
		state.target = box.move(at.scale(-1));
		state.seed = bind.seed();
		state.boundAt = bind.boundAt();
		state.releasedAt = bind.releasedAt();
		state.tightening = bind.tightening();
	}

	@Override
	protected AABB getBoundingBoxForCulling(ChainBind bind) {
		return bind.getBoundingBox().inflate(bind.radius() + GATE_RADIUS);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		double ticks = state.ageInTicks;
		double opened = GateRipple.opened(ticks, 0, ChainLayout.OPEN_TICKS, state.releasedAt, ChainLayout.RELEASE_TICKS);
		Vec3 toCamera = camera.pos.subtract(state.x, state.y, state.z);
		Vec3 centre = state.target.getCenter();
		List<List<Vec3>> paths = new ArrayList<>();
		List<Double> lengths = new ArrayList<>();
		for (int chain = 0; chain < state.gates.size(); chain++) {
			List<Vec3> path = this.path(state, state.gates.get(chain), centre, chain, ticks);
			paths.add(path);
			lengths.add(this.shown(state, path, ticks));
		}
		for (int chain = 0; chain < state.gates.size(); chain++) {
			Vec3 gate = state.gates.get(chain);
			List<Vec3> path = paths.get(chain);
			Vec3 facing = path.get(1).subtract(path.get(0)).normalize();
			long seed = state.seed ^ chain;
			double radius = GATE_RADIUS * opened;
			int segments = GateRipple.segments(radius, toCamera.subtract(gate).length());
			poseStack.pushPose();
			poseStack.translate(gate.x, gate.y, gate.z);
			LightTypes.submit(collector, poseStack, GateRipple.UNDER_TYPE,
				(pose, buffer) -> GateRipple.drawUnder(pose, buffer, facing, radius, ticks, opened, seed, segments));
			LightTypes.submit(collector, poseStack, GateRipple.TYPE,
				(pose, buffer) -> GateRipple.draw(pose, buffer, toCamera.subtract(gate), facing, radius, ticks, opened, seed, segments));
			poseStack.popPose();
		}
		LightTypes.submit(collector, poseStack, LightTypes.BLENDED, (pose, buffer) -> {
			for (int chain = 0; chain < paths.size(); chain++) {
				List<Vec3> path = paths.get(chain);
				double length = lengths.get(chain);
				ChainDrawing.links(pose, buffer, path, length, toCamera);
				Vec3[] head = headOf(path, length);
				if (head != null) {
					ChainDrawing.spike(pose, buffer, head[0], head[1]);
				}
			}
		});
		LightTypes.submit(collector, poseStack, LightTypes.ADDED, (pose, buffer) -> {
			for (int chain = 0; chain < paths.size(); chain++) {
				ChainDrawing.glow(pose, buffer, paths.get(chain), lengths.get(chain), toCamera, ticks, state.tightening);
			}
		});
		super.submit(state, poseStack, collector, camera);
	}

	private List<Vec3> path(State state, Vec3 gate, Vec3 centre, int chain, double ticks) {
		AABB box = state.target;
		double ring = Math.max(box.getXsize(), box.getZsize()) / 2 + COIL_OUT;
		Vec3 toGate = gate.subtract(centre);
		Vec3 flat = new Vec3(toGate.x, 0, toGate.z);
		double start = Math.atan2(flat.z, flat.x);
		double startY = Mth.clamp(gate.y, box.minY, box.maxY);
		Vec3 onBox = new Vec3(centre.x + Math.cos(start) * ring, startY, centre.z + Math.sin(start) * ring);
		List<Vec3> path = new ArrayList<>();
		path.add(gate);
		path.add(onBox);
		if (ticks >= state.boundAt && state.releasedAt < 0) {
			double tight = state.tightening ? COIL_TIGHTENED : 1.0;
			double endY = chain % 2 == 0 ? box.minY + box.getYsize() * COIL_END_INSET : box.maxY - box.getYsize() * COIL_END_INSET;
			for (int k = 1; k <= COIL_POINTS; k++) {
				double t = k / (double) COIL_POINTS;
				double angle = start + (chain % 2 == 0 ? 1 : -1) * COIL_TURNS * Mth.TWO_PI * t;
				double radius = ring * Mth.lerp(t, 1.0, tight);
				path.add(new Vec3(centre.x + Math.cos(angle) * radius, Mth.lerp(t, startY, endY), centre.z + Math.sin(angle) * radius));
			}
		}
		return path;
	}

	private double shown(State state, List<Vec3> path, double ticks) {
		double total = 0;
		for (int k = 1; k < path.size(); k++) {
			total += path.get(k).distanceTo(path.get(k - 1));
		}
		if (state.releasedAt >= 0) {
			return total * (1 - Math.clamp((ticks - state.releasedAt) / ChainLayout.RELEASE_TICKS, 0.0, 1.0));
		}
		return Math.min(total, Math.max(0, ticks - ChainLayout.OPEN_TICKS) * ChainLayout.CHAIN_SPEED);
	}

	private static Vec3 @Nullable [] headOf(List<Vec3> path, double length) {
		double walked = 0;
		for (int k = 1; k < path.size(); k++) {
			Vec3 from = path.get(k - 1);
			Vec3 segment = path.get(k).subtract(from);
			double segmentLength = segment.length();
			if (segmentLength > 1.0E-6 && walked + segmentLength >= length) {
				Vec3 along = segment.scale(1 / segmentLength);
				return length <= 0 ? null : new Vec3[]{from.add(along.scale(length - walked)), along};
			}
			walked += segmentLength;
		}
		return null;
	}
}
