package io.github.cwzmorro.fatecraft.artoria;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.beam.BeamVolume;
import io.github.cwzmorro.alayacore.beam.HeldBeam;
import io.github.cwzmorro.alayacore.clash.BeamClashes;
import io.github.cwzmorro.alayacore.clash.ClashBeam;
import io.github.cwzmorro.alayacore.combat.Aim;
import io.github.cwzmorro.alayacore.combat.AttackData;
import io.github.cwzmorro.alayacore.combat.Attacks;
import io.github.cwzmorro.alayacore.combat.ContinuousSource;
import io.github.cwzmorro.alayacore.combat.WeaponBlocks;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.shield.Shields;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.GoldenRatio;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Excalibur's light: its charge, Beam's held beam (a clash beam), and Burst's travelling wave and upward burst. */
public final class ExcaliburEntity extends Entity implements ClashBeam {
	public enum Phase { CHARGE, HELD, WAVE, BURST }

	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Fatecraft.id("excalibur"));
	private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(ExcaliburEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(ExcaliburEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(ExcaliburEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> REACH = SynchedEntityData.defineId(ExcaliburEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> WAVE_DISTANCE = SynchedEntityData.defineId(ExcaliburEntity.class, EntityDataSerializers.FLOAT);
	// The held beam carves again once it has turned more than one degree (as a cosine) or reached further.
	private static final double RECARVE_TURN_COS = Math.cos(Math.toRadians(1));
	// Independent noise channels for a pit's size, depth, spacing and side, the furrow's width and whether a pit is dug.
	private static final int SIZE_SEED = 1, DEPTH_SEED = 2, SPACING_SEED = 3, SIDE_SEED = 4, WIDTH_SEED = 5, PIT_SEED = 6;
	private static final double FINE_FREQUENCY = 2, FINE_OCTAVE = 0.5;
	private static final int FINE_SEED_STEP = 16;
	private static final int LATTICE_X = 73856093, LATTICE_Z = 19349663;
	private static final double PROBE_LIFT = 1;
	private static final int TORN_PARTICLES = 3;
	private static final double TORN_SPREAD = 0.3;
	private static final int TICKET_RADIUS = 2;
	// The path's noise uses stable integer hashing (HASH_STEP along x and pit index, this along z and seed), never world RNG.
	private static final long CRATER_HASH_Z = 0xC2B2AE3D27D4EB4FL;
	private static final int CRATER_HASH_SHIFT = 33;
	private static final long CRATER_HASH_MASK = 0xFFFFL;

	private @Nullable UUID caster;
	private Vec3 destination = Vec3.ZERO;
	private Vec3 waveStart = Vec3.ZERO;
	private @Nullable Vec3 carvedDirection;
	private double carvedReach;
	private ItemStack chargingSword = ItemStack.EMPTY;
	private double nextCrater;
	private int craterIndex;
	private final Set<UUID> pathHits = new HashSet<>();

	public ExcaliburEntity(EntityType<? extends ExcaliburEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public void charge(LivingEntity owner, int mode) {
		this.caster = owner.getUUID();
		this.entityData.set(MODE, mode);
		this.chargingSword = owner.getMainHandItem();
		this.setAttached(Attacks.ATTACK, new AttackData(owner.getUUID(), ExcaliburNP.RANK, mode == ExcaliburNP.HELD, true));
		this.follow(owner, owner.getLookAngle());
	}

	public int mode() {
		return this.entityData.get(MODE);
	}

	public void clearCharge() {
		this.chargingSword.remove(Artoria.EXCALIBUR_CHARGE);
	}

	public Phase phase() {
		return Phase.values()[this.entityData.get(PHASE)];
	}

	public int phaseAge() {
		return this.entityData.get(AGE);
	}

	public double reach() {
		return this.entityData.get(REACH);
	}

	public double waveDistance() {
		return this.entityData.get(WAVE_DISTANCE);
	}

	public static double waveTravel(double ticks) {
		return ticks * FateConfig.artoria().excaliburWaveSpeed() / SharedConstants.TICKS_PER_SECOND;
	}

	private void phase(Phase phase) {
		this.entityData.set(PHASE, phase.ordinal());
		this.entityData.set(AGE, 0);
	}

	/** Fixes the aim as Beam fires or Burst is released; the press never does. */
	public void release(LivingEntity owner) {
		if (this.phase() != Phase.CHARGE) {
			return;
		}
		this.clearCharge();
		this.follow(owner, owner.getLookAngle());
		if (this.mode() == ExcaliburNP.HELD) {
			this.phase(Phase.HELD);
		} else {
			this.waveStart = owner.getEyePosition();
			this.destination = Aim.point(owner, owner.getLookAngle(), FateConfig.artoria().excaliburBurstRange());
			this.entityData.set(WAVE_DISTANCE, (float) this.waveStart.distanceTo(this.destination));
			this.setPos(this.waveStart);
			this.face(this.destination.subtract(this.waveStart).normalize());
			this.entityData.set(REACH, 0f);
			// No pit under her own feet: the first chance comes a spacing out.
			this.nextCrater = firstPitChance();
			this.phase(Phase.WAVE);
			if (this.level() instanceof ServerLevel level) {
				this.holdChunks(level);
			}
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		LivingEntity owner = this.beamOwner();
		if (owner == null || !owner.isAlive() || !Abilities.isActive(owner, ExcaliburNP.KEY.identifier())) {
			this.clearCharge();
			this.discard();
			return;
		}
		this.entityData.set(AGE, this.phaseAge() + 1);
		ArtoriaConfig c = FateConfig.artoria();
		if (this.phase() == Phase.WAVE || this.phase() == Phase.BURST) {
			this.holdChunks(level);
		}
		switch (this.phase()) {
			case CHARGE -> this.follow(owner, owner.getLookAngle());
			case HELD -> this.follow(owner, HeldBeam.turned(this.getLookAngle(), owner.getLookAngle()));
			case WAVE -> {
				double distance = this.waveStart.distanceTo(this.destination);
				double travelled = Math.min(distance, waveTravel(this.phaseAge()));
				this.advanceFront(level, owner, this.reach(), travelled);
				this.entityData.set(REACH, (float) travelled);
				if (travelled >= distance) {
					this.setPos(this.destination);
					this.phase(Phase.BURST);
					this.burst(level, owner);
				}
			}
			case BURST -> {
				if (this.phaseAge() >= Durations.ticks(c.excaliburLightSeconds())) {
					Abilities.end(owner, ExcaliburNP.KEY.identifier());
				}
			}
		}
	}

	// A shot landing beyond the players' simulation distance would otherwise be unloaded with its chunk, so the landing chunk is ticketed from release.
	private void holdChunks(ServerLevel level) {
		Vec3 front = this.phase() == Phase.WAVE ? this.waveStart.add(this.getLookAngle().scale(this.reach())) : this.position();
		level.getChunkSource().addTicketWithRadius(Artoria.EXCALIBUR_TICKET, ChunkPos.containing(BlockPos.containing(front)), TICKET_RADIUS);
		level.getChunkSource().addTicketWithRadius(Artoria.EXCALIBUR_TICKET, ChunkPos.containing(BlockPos.containing(this.destination)), TICKET_RADIUS);
	}

	private void advanceFront(ServerLevel level, LivingEntity owner, double previous, double travelled) {
		ArtoriaConfig c = FateConfig.artoria();
		Vec3 direction = this.getLookAngle();
		// Nothing under or behind her is hit or dug: the front's effects begin a lead-in out from her.
		double begin = Math.max(previous, c.excaliburPathLeadIn());
		if (travelled > begin) {
			Vec3 from = this.waveStart.add(direction.scale(begin));
			Vec3 to = this.waveStart.add(direction.scale(travelled));
			DamageSource source = level.damageSources().source(DAMAGE, this, owner);
			// What the drawn surge passes over is hit: the same half width the renderer spans. A shield between her and it takes the hit.
			double reach = surgeHalfWidth(travelled);
			for (Entity target : level.getEntities(this, new AABB(from, to).inflate(reach), target -> target != owner)) {
				if (BeamVolume.contains(from, direction, travelled - begin, ignored -> reach, target.getBoundingBox().getCenter())) {
					Shields.strike(level, this.waveStart, target, source,
						(float) (AlayaConfig.ranks().npBaseDamage(ExcaliburNP.RANK) * c.excaliburPathDamageShare()), this.pathHits);
				}
			}
		}
		boolean digs = WeaponBlocks.allowed(level);
		if (digs) {
			this.furrow(level, owner, previous, travelled);
		}
		while (this.nextCrater <= travelled) {
			if (digs && pitTaken(this.waveStart, this.craterIndex)) {
				this.pit(level, owner, this.nextCrater);
			}
			this.nextCrater += c.excaliburPathSpacing() * (1 + c.excaliburPathVariation() * pathNoise(this.waveStart, this.craterIndex, SPACING_SEED));
			this.craterIndex++;
		}
	}

	private void furrow(ServerLevel level, LivingEntity owner, double previous, double travelled) {
		ArtoriaConfig c = FateConfig.artoria();
		Vec3 span = this.destination.subtract(this.waveStart);
		double run = Math.hypot(span.x, span.z);
		double distance = span.length();
		if (run < c.excaliburPathMinRun()) {
			return;
		}
		Vec3 forward = new Vec3(span.x / run, 0, span.z / run);
		double from = Math.max(c.excaliburPathLeadIn(), previous / distance * run);
		double to = travelled / distance * run;
		if (to <= from) {
			return;
		}
		boolean last = travelled >= distance;
		double widest = furrowReach();
		Vec3 a = this.waveStart.add(forward.scale(from)), b = this.waveStart.add(forward.scale(to));
		for (int x = Mth.floor(Math.min(a.x, b.x) - widest); x <= Mth.floor(Math.max(a.x, b.x) + widest); x++) {
			for (int z = Mth.floor(Math.min(a.z, b.z) - widest); z <= Mth.floor(Math.max(a.z, b.z) + widest); z++) {
				double dx = x + 0.5 - this.waveStart.x, dz = z + 0.5 - this.waveStart.z;
				double along = dx * forward.x + dz * forward.z;
				if (along < from || (last ? along > to : along >= to)) {
					continue;
				}
				double depth = furrowDepthAt(this.waveStart, this.destination, x, z);
				if (depth <= 0) {
					continue;
				}
				double progress = Mth.clamp(along / run, 0, 1);
				BlockHitResult hit = ground(level, new Vec3(x + 0.5, Mth.lerp(progress, this.waveStart.y, this.destination.y), z + 0.5), owner);
				if (hit != null) {
					tear(level, this.waveStart, hit.getBlockPos(), hit.getLocation().y, depth);
				}
			}
		}
	}

	public static @Nullable BlockHitResult ground(Level level, Vec3 line, Entity searcher) {
		BlockHitResult hit = level.clip(new ClipContext(line.add(0, PROBE_LIFT, 0), line.subtract(0, FateConfig.artoria().excaliburPathGroundReach(), 0),
			ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, searcher));
		return hit.getType() == HitResult.Type.BLOCK ? hit : null;
	}

	private static void tear(ServerLevel level, Vec3 origin, BlockPos top, double surface, double depth) {
		List<BlockPos> column = new ArrayList<>();
		for (BlockPos pos = top; surface - (pos.getY() + 1) < depth; pos = pos.below()) {
			column.add(pos);
		}
		Shields.unshielded(level, origin, column);
		if (column.contains(top) && WeaponBlocks.breakable(level, top)) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, level.getBlockState(top)), top.getX() + 0.5, surface, top.getZ() + 0.5,
				TORN_PARTICLES, TORN_SPREAD, 0, TORN_SPREAD, 0);
		}
		for (BlockPos pos : column) {
			WeaponBlocks.breakWithoutDrops(level, pos);
		}
	}

	/**
	 * Returns the furrow's depth at a ground column {@code lateral} blocks off the line at {@code progress} (0 at the caster,
	 * 1 at the impact), or 0 outside it. Ground noise bumps its edges and floor, so it wanders like torn ground, not a cut.
	 */
	public static double furrowDepth(Vec3 origin, double progress, double lateral, double x, double z) {
		ArtoriaConfig c = FateConfig.artoria();
		double t = Mth.clamp(progress, 0, 1);
		double half = furrowHalfWidth(t) * (1 + c.excaliburPathRoughness() * groundNoise(origin, x, z, WIDTH_SEED));
		if (Math.abs(lateral) >= half) {
			return 0;
		}
		double depth = c.excaliburPathDepth() * Mth.lerp(t, c.excaliburPathScaleMin(), c.excaliburPathScaleMax()) * (1 + c.excaliburPathDepthGrowth() * t)
			* (1 + c.excaliburPathVariation() * groundNoise(origin, x, z, DEPTH_SEED));
		return depth * Math.sqrt(1 - lateral * lateral / (half * half));
	}

	/**
	 * Returns the depth, in blocks, the furrow of a surge from {@code origin} towards {@code destination} tears at ground
	 * column {@code x}, {@code z}, or 0 off it.
	 */
	public static double furrowDepthAt(Vec3 origin, Vec3 destination, int x, int z) {
		Vec3 span = destination.subtract(origin);
		double run = Math.hypot(span.x, span.z);
		double forwardX = span.x / run, forwardZ = span.z / run;
		double dx = x + 0.5 - origin.x, dz = z + 0.5 - origin.z;
		double along = dx * forwardX + dz * forwardZ;
		return furrowDepth(origin, Mth.clamp(along / run, 0, 1), dz * forwardX - dx * forwardZ, x + 0.5, z + 0.5);
	}

	public static double furrowHalfWidth(double progress) {
		ArtoriaConfig c = FateConfig.artoria();
		return c.excaliburPathWidth() / 2 * Mth.lerp(Mth.clamp(progress, 0, 1), c.excaliburPathFurrowNear(), c.excaliburPathFurrowFar());
	}

	public static double furrowReach() {
		ArtoriaConfig c = FateConfig.artoria();
		return Math.max(furrowHalfWidth(0), furrowHalfWidth(1)) * (1 + c.excaliburPathRoughness());
	}

	/**
	 * Returns half the surge's width once it has run {@code travelled} blocks, swelling from nothing at her sword; its
	 * drawing and its hits both use it.
	 */
	public static double surgeHalfWidth(double travelled) {
		ArtoriaConfig c = FateConfig.artoria();
		double half = c.excaliburPathWidth() / 2;
		return half * Mth.smoothstep(Mth.clamp(travelled / (half * c.excaliburPathSurgeSwell()), 0, 1));
	}

	public static double groundNoise(Vec3 origin, double x, double z, int seed) {
		double cell = FateConfig.artoria().excaliburPathNoiseCell();
		return (valueNoise(origin, x / cell, z / cell, seed)
			+ FINE_OCTAVE * valueNoise(origin, FINE_FREQUENCY * x / cell, FINE_FREQUENCY * z / cell, seed + FINE_SEED_STEP)) / (1 + FINE_OCTAVE);
	}

	private static double valueNoise(Vec3 origin, double x, double z, int seed) {
		int ix = Mth.floor(x), iz = Mth.floor(z);
		double fx = Mth.smoothstep(x - ix), fz = Mth.smoothstep(z - iz);
		return Mth.lerp(fz, Mth.lerp(fx, lattice(origin, ix, iz, seed), lattice(origin, ix + 1, iz, seed)),
			Mth.lerp(fx, lattice(origin, ix, iz + 1, seed), lattice(origin, ix + 1, iz + 1, seed)));
	}

	private static double lattice(Vec3 origin, int x, int z, int seed) {
		return pathNoise(origin, x * LATTICE_X ^ z * LATTICE_Z, seed);
	}

	private void pit(ServerLevel level, LivingEntity owner, double along) {
		ArtoriaConfig c = FateConfig.artoria();
		Vec3 direction = new Vec3(this.getLookAngle().x, 0, this.getLookAngle().z).normalize();
		Vec3 side = new Vec3(-direction.z, 0, direction.x);
		double progress = pitProgress(along, this.waveStart.distanceTo(this.destination));
		double radius = pitRadius(this.waveStart, this.craterIndex, progress);
		double depthValue = pitDepth(this.waveStart, this.craterIndex, progress);
		Vec3 sample = this.waveStart.add(this.getLookAngle().scale(along)).add(side.scale(pitSide(this.waveStart, this.craterIndex, radius)));
		BlockHitResult hit = ground(level, sample, owner);
		if (hit == null) {
			return;
		}
		Vec3 surface = hit.getLocation();
		int r = Mth.ceil(radius * c.excaliburPathElongation() * (1 + c.excaliburPathRimVariation()));
		int depth = Mth.ceil(depthValue);
		BlockPos middle = BlockPos.containing(surface);
		List<BlockPos> gouged = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-r, -depth, -r), middle.offset(r, 0, r))) {
			if (gougeContains(surface, pos, direction, radius, depthValue, c.excaliburPathElongation())) {
				gouged.add(pos.immutable());
			}
		}
		// What a shield stands in front of, seen from where she released it, stays.
		for (BlockPos pos : Shields.unshielded(level, this.waveStart, gouged)) {
			WeaponBlocks.breakWithoutDrops(level, pos);
		}
	}

	public static double pathNoise(Vec3 origin, int index, int seed) {
		long hash = BlockPos.containing(origin).asLong() ^ index * GoldenRatio.HASH_STEP ^ seed * CRATER_HASH_Z;
		hash ^= hash >>> CRATER_HASH_SHIFT;
		hash *= CRATER_HASH_Z;
		hash ^= hash >>> CRATER_HASH_SHIFT;
		return (hash & CRATER_HASH_MASK) / (double) CRATER_HASH_MASK * 2 - 1;
	}

	public static double firstPitChance() {
		return Math.max(FateConfig.artoria().excaliburPathSpacing(), FateConfig.artoria().excaliburPathLeadIn());
	}

	public static boolean pitTaken(Vec3 origin, int index) {
		return pathNoise(origin, index, PIT_SEED) > FateConfig.artoria().excaliburPathPitThreshold();
	}

	public static double pitProgress(double along, double distance) {
		return along / Math.max(FateConfig.artoria().excaliburPathSpacing(), distance);
	}

	public static double pitRadius(Vec3 origin, int index, double progress) {
		return FateConfig.artoria().excaliburPathRadius() * pitScale(origin, index, progress);
	}

	/** Returns pit {@code index}'s depth in blocks at {@code progress}, below the ground under its middle (the furrow's floor, where it ran). */
	public static double pitDepth(Vec3 origin, int index, double progress) {
		ArtoriaConfig c = FateConfig.artoria();
		return c.excaliburPathDepth() * pitScale(origin, index, progress) * (1 + c.excaliburPathDepthGrowth() * progress)
			* (1 + c.excaliburPathVariation() * pathNoise(origin, index, DEPTH_SEED));
	}

	public static double pitSide(Vec3 origin, int index, double radius) {
		return radius * FateConfig.artoria().excaliburPathVariation() * pathNoise(origin, index, SIDE_SEED);
	}

	private static double pitScale(Vec3 origin, int index, double progress) {
		return pathScale(origin, index, progress) * FateConfig.artoria().excaliburPathPitScale();
	}

	public static double pathScale(Vec3 origin, int index, double progress) {
		ArtoriaConfig c = FateConfig.artoria();
		return Mth.lerp(Mth.clamp(progress, 0, 1), c.excaliburPathScaleMin(), c.excaliburPathScaleMax())
			* (1 + c.excaliburPathVariation() * pathNoise(origin, index, SIZE_SEED));
	}

	public static boolean gougeContains(Vec3 surface, BlockPos pos, Vec3 direction, double radius, double depth, double elongation) {
		double x = Mth.clamp(surface.x, pos.getX(), pos.getX() + 1) - surface.x;
		double z = Mth.clamp(surface.z, pos.getZ(), pos.getZ() + 1) - surface.z;
		double along = (x * direction.x + z * direction.z) / elongation;
		double across = -x * direction.z + z * direction.x;
		double distance = Math.hypot(along, across);
		long column = pos.getX() * GoldenRatio.HASH_STEP ^ pos.getZ() * CRATER_HASH_Z;
		ArtoriaConfig c = FateConfig.artoria();
		double rim = Mth.clamp((distance / radius - c.excaliburPathRimStart()) / (1 - c.excaliburPathRimStart()), 0, 1);
		double effective = radius * (1 + c.excaliburPathRimVariation() * rim * pathNoise(surface, (int) column, SIZE_SEED));
		if (pos.getY() >= surface.y || pos.getY() + 1 <= surface.y - depth) {
			return false;
		}
		double below = Math.max(0, surface.y - (pos.getY() + 1)) / depth;
		return distance * distance / (effective * effective) + below * below < 1;
	}

	public static double radiusAt(double distance) {
		ArtoriaConfig c = FateConfig.artoria();
		return Mth.lerp(Mth.clamp(distance / c.excaliburBeamRange(), 0, 1), c.excaliburStartRadius(), c.excaliburEndRadius());
	}

	public static double burstDamage(double distance) {
		ArtoriaConfig c = FateConfig.artoria();
		return AlayaConfig.ranks().npBaseDamage(ExcaliburNP.RANK)
			* Mth.lerp(Mth.clamp(distance / c.excaliburBurstRadius(), 0, 1), 1, c.excaliburEdgeDamage());
	}

	private void burst(ServerLevel level, LivingEntity owner) {
		ArtoriaConfig c = FateConfig.artoria();
		double r = c.excaliburBurstRadius();
		AABB box = new AABB(this.position().add(-r, 0, -r), this.position().add(r, c.excaliburBurstHeight(), r));
		DamageSource source = level.damageSources().source(DAMAGE, this, owner);
		// A shield between where she released it and a target or block takes the hit and keeps it whole. She is hit too when within it.
		Set<UUID> struck = new HashSet<>();
		for (Entity target : level.getEntities(this, box)) {
			Vec3 centre = target.getBoundingBox().getCenter();
			double distance = Math.hypot(centre.x - this.getX(), centre.z - this.getZ());
			if (distance <= r && centre.y >= this.getY() && centre.y <= this.getY() + c.excaliburBurstHeight()) {
				Shields.strike(level, this.waveStart, target, source, (float) burstDamage(distance), struck);
			}
		}
		if (WeaponBlocks.allowed(level)) {
			int radius = Mth.ceil(r);
			BlockPos middle = BlockPos.containing(this.position());
			List<BlockPos> excavated = new ArrayList<>();
			for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-radius, -radius, -radius), middle.offset(radius, radius, radius))) {
				if (Vec3.atCenterOf(pos).distanceToSqr(this.position()) <= r * r) {
					excavated.add(pos.immutable());
				}
			}
			for (BlockPos pos : Shields.unshielded(level, this.waveStart, excavated)) {
				WeaponBlocks.breakWithoutDrops(level, pos);
			}
		}
	}

	@Override
	public void beamEffects(ServerLevel level) {
		LivingEntity owner = this.beamOwner();
		if (this.phase() != Phase.HELD || owner == null) {
			return;
		}
		HeldBeam.Clip clip = HeldBeam.clip(level, this);
		this.entityData.set(REACH, (float) clip.reach());
		Vec3 direction = this.getLookAngle();
		DamageSource source = ContinuousSource.of(level, DAMAGE, this, owner);
		float damage = BeamClashes.damage(this, ContinuousSource.perTick(AlayaConfig.ranks().beamDamagePerSecond(ExcaliburNP.RANK)));
		clip.hurtShield(level, source, damage);
		BeamVolume.hit(level, this, owner, source, damage, this.position(), direction, clip.reach(), ExcaliburEntity::radiusAt);
		if (WeaponBlocks.allowed(level) && (this.carvedDirection == null
			|| clip.reach() > this.carvedReach || this.carvedDirection.dot(direction) < RECARVE_TURN_COS)) {
			BeamVolume.carve(level, this.position(), direction, clip.reach(), ExcaliburEntity::radiusAt);
			this.carvedDirection = direction;
			this.carvedReach = clip.reach();
		}
	}

	private void follow(LivingEntity owner, Vec3 direction) {
		this.setPos(owner.getEyePosition().add(direction.scale(FateConfig.artoria().excaliburStartRadius())));
		this.face(direction);
	}

	private void face(Vec3 direction) {
		this.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
		this.setXRot((float) Math.toDegrees(-Math.asin(Mth.clamp(direction.y, -1, 1))));
	}

	@Override
	public Entity beamEntity() {
		return this;
	}

	@Override
	public Vec3 beamOrigin() {
		return this.position();
	}

	@Override
	public Vec3 beamDirection() {
		return this.getLookAngle();
	}

	@Override
	public double beamLength() {
		return FateConfig.artoria().excaliburBeamRange();
	}

	@Override
	public double beamRadius() {
		return FateConfig.artoria().excaliburEndRadius();
	}

	@Override
	public double beamDrainPerSecond() {
		return AlayaConfig.ranks().beamDrainPerSecond(ExcaliburNP.RANK);
	}

	@Override
	public @Nullable LivingEntity beamOwner() {
		Entity entity = this.caster == null || !(this.level() instanceof ServerLevel level) ? null : level.getEntity(this.caster);
		return entity instanceof LivingEntity living ? living : null;
	}

	@Override
	public boolean beamLive() {
		LivingEntity owner = this.beamOwner();
		return this.phase() == Phase.HELD && owner != null && owner.isAlive() && Abilities.isActive(owner, ExcaliburNP.KEY.identifier());
	}

	@Override
	public void loseClash() {
		LivingEntity owner = this.beamOwner();
		if (owner != null) {
			Abilities.end(owner, ExcaliburNP.KEY.identifier());
		}
		this.discard();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(PHASE, Phase.CHARGE.ordinal());
		builder.define(AGE, 0);
		builder.define(MODE, ExcaliburNP.HELD);
		builder.define(REACH, (float) FateConfig.artoria().excaliburBeamRange());
		builder.define(WAVE_DISTANCE, 0f);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}
}
