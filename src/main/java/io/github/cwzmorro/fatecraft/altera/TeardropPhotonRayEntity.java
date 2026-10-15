package io.github.cwzmorro.fatecraft.altera;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.beam.HeldBeam;
import io.github.cwzmorro.alayacore.clash.BeamClashes;
import io.github.cwzmorro.alayacore.clash.ClashBeam;
import io.github.cwzmorro.alayacore.combat.Aim;
import io.github.cwzmorro.alayacore.combat.AttackData;
import io.github.cwzmorro.alayacore.combat.AttackOrigin;
import io.github.cwzmorro.alayacore.combat.Attacks;
import io.github.cwzmorro.alayacore.combat.ContinuousSource;
import io.github.cwzmorro.alayacore.combat.ServantBonus;
import io.github.cwzmorro.alayacore.combat.WeaponBlocks;
import io.github.cwzmorro.alayacore.config.AlayaConfig;
import io.github.cwzmorro.alayacore.shield.ShieldEntity;
import io.github.cwzmorro.alayacore.shield.Shields;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.weapon.WeaponOpening;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Teardrop Photon Ray's sky strike. It strikes from the top down: water and blocks without collision boil away, solid blocks
 * go a layer at a time at the mode's dig rate, and only what has a clear line back to the beam's source is hurt.
 */
public final class TeardropPhotonRayEntity extends Entity implements ClashBeam, AttackOrigin {
	private static final double LANE_SPACING = 0.5;
	private static final double LEAST_DROP = 1.0;
	private static final int STEAM_PUFFS = 24;
	private static final double STEAM_SPREAD = 0.4;
	private static final double STEAM_SPEED = 0.05;
	private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(TeardropPhotonRayEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> HELD = SynchedEntityData.defineId(TeardropPhotonRayEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> FIRING = SynchedEntityData.defineId(TeardropPhotonRayEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> SPREAD = SynchedEntityData.defineId(TeardropPhotonRayEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Float> DEPTH = SynchedEntityData.defineId(TeardropPhotonRayEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> REACH = SynchedEntityData.defineId(TeardropPhotonRayEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Vector3fc> TARGET = SynchedEntityData.defineId(TeardropPhotonRayEntity.class, EntityDataSerializers.VECTOR3);
	private @Nullable LivingEntity caster;
	private ItemStack sword = ItemStack.EMPTY;
	private Vec3 aimDirection = Vec3.ZERO;
	private int digTicks;
	private final Map<Long, Integer> dug = new HashMap<>();
	private Vec3 targetBefore = Vec3.ZERO;
	private Vec3 targetNow = Vec3.ZERO;

	public TeardropPhotonRayEntity(EntityType<? extends TeardropPhotonRayEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public void charge(LivingEntity owner, boolean spread) {
		this.entityData.set(SPREAD, spread);
		this.caster = owner;
		this.sword = owner.getMainHandItem();
		this.entityData.set(OWNER, owner.getId());
		this.setAttached(Attacks.ATTACK, new AttackData(owner.getUUID(), TeardropPhotonRay.RANK, true, true));
		WeaponOpening.set(this.sword, this.level().getGameTime(), true);
		WeaponFlip.set(this.sword, this.level().getGameTime(), true);
		this.aimDirection = owner.getLookAngle();
		this.setPos(Aim.point(owner, this.aimDirection, FateConfig.altera().teardropRange()));
	}

	public void held(int ticks, boolean firing) {
		this.entityData.set(HELD, ticks);
		this.entityData.set(FIRING, firing);
	}

	public int heldTicks() {
		return this.entityData.get(HELD);
	}

	public boolean firing() {
		return this.entityData.get(FIRING);
	}

	public boolean spread() {
		return this.entityData.get(SPREAD);
	}

	public Identifier modeId() {
		return this.spread() ? TeardropPhotonRay.SPREAD : TeardropPhotonRay.CONDENSED;
	}

	public float depth() {
		return this.entityData.get(DEPTH);
	}

	public Vec3 target() {
		return this.position().add(new Vec3(this.entityData.get(TARGET)));
	}

	public Vec3 targetOffset(float partialTick) {
		return this.targetBefore.lerp(this.targetNow, partialTick);
	}

	public @Nullable LivingEntity owner() {
		Entity found = this.level().getEntity(this.entityData.get(OWNER));
		return found instanceof LivingEntity living ? living : this.caster;
	}

	@Override
	public void remove(RemovalReason reason) {
		if (!this.sword.isEmpty()) {
			WeaponOpening.set(this.sword, this.level().getGameTime(), false);
			WeaponFlip.set(this.sword, this.level().getGameTime(), false);
		}
		super.remove(reason);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			this.targetBefore = this.targetNow;
			this.targetNow = new Vec3(this.entityData.get(TARGET));
			return;
		}
		LivingEntity owner = this.caster;
		if (owner == null || !owner.isAlive() || owner.level() != level || owner.getMainHandItem() != this.sword
			|| !Abilities.isActive(owner, TeardropPhotonRay.KEY.identifier())) {
			this.discard();
			return;
		}
		if (this.firing() && !this.spread()) {
			this.follow(owner);
		}
	}

	private void follow(LivingEntity owner) {
		this.aimDirection = HeldBeam.turned(this.aimDirection, owner.getLookAngle());
		Vec3 point = Aim.point(owner, this.aimDirection, FateConfig.altera().teardropRange());
		if (point.y <= this.beamOrigin().y - LEAST_DROP) {
			this.entityData.set(TARGET, point.subtract(this.position()).toVector3f());
		}
	}

	@Override
	public void beamEffects(ServerLevel level) {
		LivingEntity owner = this.owner();
		if (owner == null) {
			return;
		}
		AlteraConfig config = FateConfig.altera();
		this.entityData.set(DEPTH, (float) this.surfaceDepth(level));
		Vec3 origin = this.beamOrigin();
		Vec3 bottom = this.bottom();
		double radius = this.beamRadius();
		double impactRadius = this.spread() ? TeardropGeometry.impactRadius(config) : radius;
		HeldBeam.Clip clip = HeldBeam.clip(level, this);
		double reach = clip.reach();
		this.entityData.set(REACH, (float) reach);
		DamageSource source = ContinuousSource.of(level, Altera.DAMAGE, this, owner);
		float damage = BeamClashes.damage(this, ContinuousSource.perTick((AlayaConfig.ranks().beamDamagePerSecond(TeardropPhotonRay.RANK)
			+ ServantBonus.classAttack(owner)) * (this.spread() ? config.spreadDamageMultiplier() : 1)));
		clip.hurtShield(level, source, damage);
		AABB volume = new AABB(bottom, origin).inflate(impactRadius);
		for (Entity target : level.getEntities(this, volume, target -> target != owner && !(target instanceof ShieldEntity))) {
			Vec3 centre = target.getBoundingBox().getCenter();
			double share = this.spread() ? this.spreadDamageShare(centre, origin, bottom, reach, radius, impactRadius)
				: condensedDamageShare(centre, origin, bottom, reach, radius);
			if (share > 0 && this.exposed(level, centre)) {
				Shields.hurt(level, target, source, (float) (damage * share));
			}
		}
		if (reach >= this.beamLength() && WeaponBlocks.allowed(level)) {
			this.excavate(level, origin, bottom, this.spread() ? config.spreadDigBlocksPerSecond() : config.teardropDigBlocksPerSecond());
		}
	}

	private double surfaceDepth(ServerLevel level) {
		Vec3 origin = this.beamOrigin();
		Vec3 target = this.target();
		double floor = target.y - level.getMinY();
		BlockHitResult hit = level.clip(new ClipContext(origin, TeardropGeometry.bottom(origin, target, floor), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS ? floor : Mth.clamp(target.y - hit.getLocation().y, 0, floor);
	}

	private boolean exposed(ServerLevel level, Vec3 centre) {
		return level.clip(new ClipContext(centre, this.attackOrigin(centre), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
			CollisionContext.empty())).getType() == HitResult.Type.MISS;
	}

	// The light falls along the beam: a hit comes from straight back up the beam, level with its source.
	@Override
	public Vec3 attackOrigin(Vec3 target) {
		Vec3 along = this.beamDirection();
		return target.subtract(along.scale(target.subtract(this.beamOrigin()).dot(along)));
	}

	private record Contact(long lane, BlockPos pos) {
	}

	// One firing tick of the excavation both modes share: at most the configured number of blocks change a tick, highest first.
	private void excavate(ServerLevel level, Vec3 origin, Vec3 bottom, double blocksPerSecond) {
		this.digTicks++;
		// The first layer breaks as the beam lands; the next each time the front has moved down another block.
		int due = Mth.floor(this.digTicks * blocksPerSecond / SharedConstants.TICKS_PER_SECOND) + 1;
		int budget = FateConfig.altera().teardropBlocksPerTick();
		List<BlockPos> steamed = new ArrayList<>();
		while (budget > 0) {
			// Lanes meeting the same block share it: it breaks once and counts as the layer of each of them.
			Map<BlockPos, List<Long>> ready = new HashMap<>();
			for (Contact contact : this.spread() ? this.columns(level, origin, bottom) : this.lanes(level, origin, bottom)) {
				if (this.removable(level, contact, due)) {
					ready.computeIfAbsent(contact.pos(), pos -> new ArrayList<>()).add(contact.lane());
				}
			}
			// Nothing under a shield goes until it breaks: a lane whose first block it shelters waits.
			List<BlockPos> highestFirst = Shields.unshielded(level, this, new ArrayList<>(ready.keySet()));
			if (highestFirst.isEmpty()) {
				break;
			}
			highestFirst.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed());
			for (BlockPos pos : highestFirst.subList(0, Math.min(budget, highestFirst.size()))) {
				this.remove(level, pos, ready.get(pos), steamed);
			}
			budget -= Math.min(budget, highestFirst.size());
		}
		for (int i = 0; i < Math.min(STEAM_PUFFS, steamed.size()); i++) {
			Vec3 at = Vec3.atCenterOf(steamed.get(level.getRandom().nextInt(steamed.size())));
			// Seen from as far as the beam itself, not only within vanilla's short particle range.
			level.sendParticles(ParticleTypes.CLOUD, true, false, at.x, at.y, at.z, 1, STEAM_SPREAD, STEAM_SPREAD, STEAM_SPREAD, STEAM_SPEED);
		}
	}

	private List<Contact> columns(ServerLevel level, Vec3 origin, Vec3 bottom) {
		List<Contact> contacts = new ArrayList<>();
		AABB footprint = new AABB(origin, bottom).inflate(this.beamRadius());
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = Mth.floor(footprint.minX); x <= Mth.floor(footprint.maxX); x++) {
			for (int z = Mth.floor(footprint.minZ); z <= Mth.floor(footprint.maxZ); z++) {
				// Unloaded columns report the world's floor, so the walk never starts there.
				int top = Math.min(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), Mth.floor(origin.y));
				for (int y = top - 1; y >= level.getMinY(); y--) {
					pos.set(x, y, z);
					if (!this.insideSpread(Vec3.atCenterOf(pos), origin, bottom)) {
						break;
					}
					if (!level.getBlockState(pos).isAir()) {
						contacts.add(new Contact(BlockPos.asLong(x, 0, z), pos.immutable()));
						break;
					}
				}
			}
		}
		return contacts;
	}

	private List<Contact> lanes(ServerLevel level, Vec3 origin, Vec3 bottom) {
		List<Contact> contacts = new ArrayList<>();
		Vec3 along = bottom.subtract(origin).normalize();
		Vec3 u = along.cross(Math.abs(along.x) < Math.abs(along.z) ? Vec3.X_AXIS : Vec3.Z_AXIS).normalize();
		Vec3 v = along.cross(u);
		double radius = this.beamRadius();
		double length = (origin.y - level.getMinY()) / -along.y;
		int steps = Mth.ceil(radius / LANE_SPACING);
		for (int i = -steps; i <= steps; i++) {
			for (int j = -steps; j <= steps; j++) {
				Vec3 offset = u.scale(i * LANE_SPACING).add(v.scale(j * LANE_SPACING));
				if (offset.length() > radius) {
					continue;
				}
				Vec3 from = origin.add(offset);
				BlockHitResult hit = level.clip(new ClipContext(from, from.add(along.scale(length)), ClipContext.Block.COLLIDER,
					ClipContext.Fluid.WATER, CollisionContext.empty()));
				if (hit.getType() != HitResult.Type.MISS) {
					contacts.add(new Contact(BlockPos.asLong(i, 0, j), hit.getBlockPos()));
				}
			}
		}
		return contacts;
	}

	private boolean removable(ServerLevel level, Contact contact, int due) {
		BlockState state = level.getBlockState(contact.pos());
		return WeaponBlocks.breakable(level, contact.pos()) && (state.getFluidState().is(FluidTags.WATER)
			|| state.getCollisionShape(level, contact.pos()).isEmpty() || this.dug.getOrDefault(contact.lane(), 0) < due);
	}

	private void remove(ServerLevel level, BlockPos pos, List<Long> lanes, List<BlockPos> steamed) {
		if (WeaponBlocks.vaporize(level, pos)) {
			steamed.add(pos);
			return;
		}
		if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
			for (long lane : lanes) {
				this.dug.merge(lane, 1, Integer::sum);
			}
		}
		WeaponBlocks.breakWithoutDrops(level, pos);
	}

	private boolean insideSpread(Vec3 point, Vec3 origin, Vec3 bottom) {
		Vec3 axisAtHeight = origin.lerp(bottom, (point.y - origin.y) / (bottom.y - origin.y));
		double profileShare = (origin.y - point.y) / (origin.y - this.getY());
		return point.y <= origin.y && Math.hypot(point.x - axisAtHeight.x, point.z - axisAtHeight.z)
			<= TeardropGeometry.spreadRadiusAt(FateConfig.altera(), profileShare);
	}

	private static double condensedDamageShare(Vec3 centre, Vec3 origin, Vec3 bottom, double reach, double radius) {
		Vec3 along = bottom.subtract(origin).normalize();
		double distance = centre.subtract(origin).dot(along);
		return distance >= 0 && distance <= Math.min(reach, origin.distanceTo(bottom) + radius)
			&& centre.distanceTo(origin.add(along.scale(distance))) <= radius ? 1 : 0;
	}

	private double spreadDamageShare(Vec3 centre, Vec3 origin, Vec3 bottom, double reach, double radius, double impactRadius) {
		Vec3 direction = bottom.subtract(origin).normalize();
		double along = centre.subtract(origin).dot(direction);
		if (this.insideSpread(centre, origin, bottom) && centre.y >= bottom.y - radius
			&& (reach >= origin.distanceTo(bottom) || along >= 0 && along <= reach)) {
			return 1;
		}
		// Splash stays on the original surface, not the moving excavation bottom.
		Vec3 surface = origin.lerp(bottom, (this.getY() - origin.y) / (bottom.y - origin.y));
		double horizontal = Math.hypot(centre.x - surface.x, centre.z - surface.z);
		if (horizontal > radius && horizontal < impactRadius && reach >= origin.distanceTo(bottom)
			&& Math.abs(centre.y - this.getY()) <= impactRadius - radius) {
			return (impactRadius - horizontal) / (impactRadius - radius);
		}
		return 0;
	}

	public Vec3 bottom() {
		return TeardropGeometry.bottom(this.beamOrigin(), this.target(), this.depth());
	}

	public float reach() {
		return this.entityData.get(REACH);
	}

	@Override
	public Entity beamEntity() {
		return this;
	}

	@Override
	public Vec3 beamOrigin() {
		return this.position().add(TeardropGeometry.sky(FateConfig.altera(), 0));
	}

	@Override
	public Vec3 beamDirection() {
		return this.bottom().subtract(this.beamOrigin()).normalize();
	}

	@Override
	public double beamLength() {
		return this.beamOrigin().distanceTo(this.bottom());
	}

	@Override
	public double beamRadius() {
		AlteraConfig config = FateConfig.altera();
		return this.spread() ? TeardropGeometry.spreadRadius(config) : config.teardropRadius();
	}

	@Override
	public @Nullable LivingEntity beamOwner() {
		return this.owner();
	}

	@Override
	public double beamDrainPerSecond() {
		return AlayaConfig.ranks().beamDrainPerSecond(TeardropPhotonRay.RANK);
	}

	@Override
	public boolean beamLive() {
		LivingEntity owner = this.owner();
		return this.firing() && owner != null && owner.isAlive() && owner.level() == this.level()
			&& Abilities.isActive(owner, TeardropPhotonRay.KEY.identifier());
	}

	@Override
	public void loseClash() {
		LivingEntity owner = this.owner();
		if (owner != null) {
			Abilities.end(owner, TeardropPhotonRay.KEY.identifier());
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
		builder.define(OWNER, 0);
		builder.define(HELD, 0);
		builder.define(FIRING, false);
		builder.define(SPREAD, false);
		builder.define(DEPTH, 0.0F);
		builder.define(REACH, Float.MAX_VALUE);
		builder.define(TARGET, new Vector3f());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}
}
