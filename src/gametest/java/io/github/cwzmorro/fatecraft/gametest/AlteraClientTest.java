package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.util.Durations;
import io.github.cwzmorro.fatecraft.FateConfig;
import io.github.cwzmorro.fatecraft.altera.AlteraConfig;
import io.github.cwzmorro.fatecraft.altera.PhotonRay;
import io.github.cwzmorro.fatecraft.altera.PhotonRayEntity;
import io.github.cwzmorro.fatecraft.altera.TeardropGeometry;
import io.github.cwzmorro.fatecraft.altera.TeardropPhotonRay;
import io.github.cwzmorro.fatecraft.altera.TeardropPhotonRayEntity;
import io.github.cwzmorro.fatecraft.servant.FateServants;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import io.github.cwzmorro.fatecraft.weapon.WeaponOpening;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.SharedConstants;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public class AlteraClientTest implements FabricClientGameTest {
	private static final int DASH_FRAME_TICKS = 5;
	private static final double SKY_LINK_FRAME = 0.16;
	private static final double SKY_WIND_UP_FRAME = 0.9;
	private static final double SKY_BUILD_FRAME = 0.56;
	private static final int CHARGE_FOV = 65;
	private static final float CHARGE_VIEW_PITCH = -15;
	private static final int DASH_FOV = 95;
	private static final float DASH_VIEW_YAW = 65;
	private static final float DASH_VIEW_PITCH = 10;
	private static final int SKY_FOV = 70;
	private static final int CIRCLES_FOV = 55;
	private static final int WIDE_FOV = 100;
	// Tiled fills stay below the command volume limit; scenery still allows real excavation.
	private static final int GROUND_Y = 100;
	private static final int GROUND_TILE = 16;
	private static final int IMPACT_FOV = 90;
	private static final int SKY_THIN_FRAME_TICKS = 1;
	private static final int SKY_WIDENING_STEP_TICKS = 3;
	private static final int SKY_WIDE_STEP_TICKS = 10;
	private static final int SKY_FLOW_FRAME_TICKS = 24;
	// Camera offsets are relative to the aimed point and main seal, and scale with the seal, so screenshots stay centred and in frame when the aim or config changes.
	private static final Vec3 CIRCLES_VIEW = new Vec3(0, -28, -75);
	private static final Vec3 CIRCLES_LOOK = new Vec3(0, 7.5, 0);
	private static final Vec3 WIDENING_VIEW = new Vec3(5, -18, -68);
	private static final Vec3 WIDENING_LOOK = new Vec3(0, -2, 0);
	private static final Vec3 IMPACT_VIEW = new Vec3(8, 2, -64);
	private static final Vec3 IMPACT_LOOK = new Vec3(0, 5, 0);
	private static final Vec3 WIDE_VIEW = new Vec3(0, 32, -68);
	private static final double FRAMED_HEIGHT = 40;
	private static final float STRIKE_PITCH = 5;
	private static final double WIDE_LOOK_HEIGHT_SHARE = 1.125;
	private static final double VIEW_RADIUS = 80.0;
	private static final double EDGE_VIEW_DISTANCE = 40;
	private static final double VIEW_EYE_HEIGHT = 1.62;
	private static final double SIDE_LATERAL_RADII = 0.65;
	private static final double SIDE_LOOK_SHARE = 0.25;
	private static final double SIDE_DISTANCE_RADII = 0.9;
	private static final int SIDE_FOV = 100;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (FateClientTests.skipped(this)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			prepareSkyWorld(context, world);
			for (int i = 0; i < FateClientTests.TIMES_OF_DAY.length; i++) {
				String lighting = FateClientTests.LIGHTING_NAMES[i];
				world.getServer().runCommand("time set " + FateClientTests.TIMES_OF_DAY[i]);
				world.getServer().runCommand("tp @p 0 101 0 0 0");
				context.runOnClient(client -> {
					// LevelRenderer excludes LocalPlayer with any external camera, even in third person.
					client.setCameraEntity(client.player);
					client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
					client.options.fov().set(CHARGE_FOV);
				});
				context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
				context.runOnClient(client -> {
					client.options.hideGui = false;
					client.options.setCameraType(CameraType.FIRST_PERSON);
				});
				context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
				context.takeScreenshot("altera-sword-closed-first-" + lighting);
				FateClientTests.setCamera(context, CameraType.THIRD_PERSON_FRONT);
				context.takeScreenshot("altera-sword-closed-third-" + lighting);
				FateClientTests.press(world, PhotonRay.KEY.identifier(), PhotonRay.DASH_MODE);
				context.waitTicks(PhotonRay.chargeTicks() / 2);
				context.runOnClient(client -> {
					WeaponOpening opening = client.player.getMainHandItem().get(FateWeapons.OPENING);
					if (opening == null || !opening.open()) {
						throw new AssertionError("Charging must open the sword");
					}
				});
				context.takeScreenshot("altera-sword-charging-spin-third-" + lighting);
				FateClientTests.setCamera(context, CameraType.FIRST_PERSON);
				context.takeScreenshot("altera-sword-charging-spin-first-" + lighting);
				context.runOnClient(client -> client.player.setXRot(CHARGE_VIEW_PITCH));
				context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
				context.runOnClient(client -> client.player.setXRot(0));
				context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
				context.waitTicks(DASH_FRAME_TICKS);
				world.getServer().runCommand("tick freeze");
				context.runOnClient(client -> {
					client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
					client.options.fov().set(DASH_FOV);
					client.player.setYRot(DASH_VIEW_YAW);
					client.player.setXRot(DASH_VIEW_PITCH);
				});
				context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
				context.runOnClient(client -> {
					boolean dashing = client.level.getEntitiesOfClass(PhotonRayEntity.class, client.player.getBoundingBox().inflate(VIEW_RADIUS))
						.stream().anyMatch(PhotonRayEntity::dashing);
					if (!dashing || !client.player.getMainHandItem().has(FateWeapons.SPIN)) {
						throw new AssertionError("Dash must synchronize its cone state and spinning sword");
					}
				});
				context.takeScreenshot("altera-dash-cone-" + lighting);
				world.getServer().runCommand("tick unfreeze");
				world.getServer().runOnServer(server -> Abilities.endAll(FateClientTests.player(server)));
				skyStrike(context, world, lighting, TeardropPhotonRay.SPREAD_MODE);
				skyStrike(context, world, lighting, TeardropPhotonRay.CONDENSED_MODE);
			}
		}
	}

	static void prepareSkyWorld(ClientGameTestContext context, TestSingleplayerContext world) {
		FateClientTests.becomeServant(context, world, FateServants.ALTERA);
		world.getServer().runCommand("gamemode creative @p");
		world.getServer().runCommand("fill -16 101 -16 16 115 40 air");
		world.getServer().runCommand("weather clear");
		FateClientTests.hold(world, FateWeapons.PHOTON_RAY);
		context.runOnClient(client -> {
			client.options.hideGui = true;
			client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			// LevelRenderer also requires the strike's chunk section to be visible from the
			// external camera; shouldRender alone cannot bypass the view-area limit.
			double radius = TeardropGeometry.spreadRadius(FateConfig.altera());
			double extent = FateConfig.altera().teardropHeight() + radius * 4 + EDGE_VIEW_DISTANCE;
			client.options.renderDistance().set((int) Math.ceil(extent / 16) + 2);
		});
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE * 10);
		// Fills outside the initial five-chunk view fail with "position is not loaded".
		// Build the wide ground only after the expanded view has reached the server.
		ground(world, TeardropGeometry.spreadRadius(FateConfig.altera()) * 3 + EDGE_VIEW_DISTANCE);
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
	}

	private static void ground(TestSingleplayerContext world, double extent) {
		AlteraConfig config = FateConfig.altera();
		int edge = (int) Math.ceil(extent / GROUND_TILE) * GROUND_TILE;
		int depth = (int) Math.ceil(config.teardropDigBlocksPerSecond()
			* (config.teardropWindUpSeconds() + SKY_FLOW_FRAME_TICKS / (double) SharedConstants.TICKS_PER_SECOND)) + 2;
		for (int x = -edge; x < edge; x += GROUND_TILE) {
			for (int z = -edge; z < edge; z += GROUND_TILE) {
				world.getServer().runCommand("fill " + x + " " + (GROUND_Y - depth) + " " + z + " "
					+ (x + GROUND_TILE - 1) + " " + GROUND_Y + " " + (z + GROUND_TILE - 1) + " stone");
			}
		}
	}

	static void skyStrike(ClientGameTestContext context, TestSingleplayerContext world, String lighting, int mode) {
		boolean spread = mode == TeardropPhotonRay.SPREAD_MODE;
		String name = (spread ? "spread-" : "condensed-") + lighting;
		ground(world, TeardropGeometry.impactRadius(FateConfig.altera()) + 1);
		world.getServer().runCommand("tp @p 0 101 0 0 " + STRIKE_PITCH);
		world.getServer().runOnServer(server -> {
			var player = FateClientTests.player(server);
			// Spread excavates beneath the caster too. Frozen worlds still tick players,
			// so keep this creative caster hovering within tracking/lookup range of the fixed strike.
			player.getAbilities().flying = true;
			player.setDeltaMovement(Vec3.ZERO);
			player.onUpdateAbilities();
		});
		world.getServer().runCommand("summon armor_stand 18 101 -22 {Invisible:1b,NoGravity:1b,Marker:1b,Rotation:[20f,-23f]}");
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
		context.runOnClient(client -> {
			client.options.setCameraType(CameraType.FIRST_PERSON);
			client.options.fov().set(SKY_FOV);
			client.setCameraEntity(client.level.getEntitiesOfClass(ArmorStand.class,
				client.player.getBoundingBox().inflate(VIEW_RADIUS)).getFirst());
		});
		FateClientTests.press(world, TeardropPhotonRay.KEY.identifier(), mode);
		int windUp = Durations.ticks(FateConfig.altera().teardropWindUpSeconds());
		context.waitTicks((int) (windUp * SKY_LINK_FRAME));
		world.getServer().runCommand("tick freeze");
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
		if (spread) {
			context.runOnClient(client -> {
				client.setCameraEntity(client.player);
				client.options.setCameraType(CameraType.FIRST_PERSON);
				client.options.fov().set(SKY_FOV);
			});
			context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
			shot(context, "altera-teardrop-link-first-" + name, (int) (windUp * SKY_LINK_FRAME), false);
			FateClientTests.setCamera(context, CameraType.THIRD_PERSON_FRONT);
			shot(context, "altera-teardrop-link-third-" + name, (int) (windUp * SKY_LINK_FRAME), false);
			context.runOnClient(client -> {
				client.options.setCameraType(CameraType.FIRST_PERSON);
				client.setCameraEntity(client.level.getEntitiesOfClass(ArmorStand.class,
					client.player.getBoundingBox().inflate(VIEW_RADIUS)).getFirst());
			});
			view(context, false, byHeight(WIDE_VIEW), new Vec3(0, FateConfig.altera().teardropHeight() / 2, 0), WIDE_FOV);
			shot(context, "altera-teardrop-link-" + name, (int) (windUp * SKY_LINK_FRAME), false);
			view(context, true, bySeal(CIRCLES_VIEW), bySeal(CIRCLES_LOOK), CIRCLES_FOV);
			shot(context, "altera-teardrop-circles-building-" + name, (int) (windUp * SKY_BUILD_FRAME), false);
		}
		int linkTicks = context.computeOnClient(client -> client.level.getEntitiesOfClass(TeardropPhotonRayEntity.class,
			client.player.getBoundingBox().inflate(VIEW_RADIUS)).getFirst().heldTicks());
		world.getServer().runCommand("tick unfreeze");
		context.waitTicks(Math.max(0, (int) (windUp * SKY_WIND_UP_FRAME) - linkTicks - FateClientTests.TICKS_TO_SETTLE));
		world.getServer().runCommand("tick freeze");
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
		context.runOnClient(client -> {
			TeardropPhotonRayEntity ray = client.level.getEntitiesOfClass(TeardropPhotonRayEntity.class,
				client.player.getBoundingBox().inflate(VIEW_RADIUS)).getFirst();
			if (ray.firing() || ray.heldTicks() <= 0 || !client.player.getMainHandItem().get(FateWeapons.OPENING).open()) {
				throw new AssertionError("Sky wind-up must synchronize circles and opened sword before firing");
			}
		});
		if (spread) {
			view(context, true, bySeal(CIRCLES_VIEW), bySeal(CIRCLES_LOOK), CIRCLES_FOV);
			shot(context, "altera-teardrop-circles-" + name, (int) (windUp * SKY_WIND_UP_FRAME), false);
		}
		context.runOnClient(client -> {
			client.setCameraEntity(client.player);
			client.options.hideGui = false;
			client.options.setCameraType(CameraType.FIRST_PERSON);
			// Turn away from the locked column to inspect the held sword without beam overexposure.
			client.player.setYRot(180);
			client.player.setXRot(0);
		});
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
		context.takeScreenshot("altera-sword-flipped-first-" + name);
		FateClientTests.setCamera(context, CameraType.THIRD_PERSON_FRONT);
		context.takeScreenshot("altera-sword-flipped-third-" + name);
		context.runOnClient(client -> {
			client.player.setYRot(0);
			client.player.setXRot(STRIKE_PITCH);
			client.options.hideGui = true;
			client.options.setCameraType(CameraType.FIRST_PERSON);
			client.setCameraEntity(client.level.getEntitiesOfClass(ArmorStand.class,
				client.player.getBoundingBox().inflate(VIEW_RADIUS)).getFirst());
		});
		int sealTicks = context.computeOnClient(client -> client.level.getEntitiesOfClass(TeardropPhotonRayEntity.class,
			client.player.getBoundingBox().inflate(VIEW_RADIUS)).getFirst().heldTicks());
		world.getServer().runCommand("tick unfreeze");
		context.waitTicks(Math.max(0, windUp - sealTicks + SKY_THIN_FRAME_TICKS));
		world.getServer().runCommand("tick freeze");
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
		context.runOnClient(client -> {
			if (!client.level.getEntitiesOfClass(TeardropPhotonRayEntity.class,
				client.player.getBoundingBox().inflate(VIEW_RADIUS)).getFirst().firing()) {
				throw new AssertionError("Sky beam must be firing in the impact screenshot");
			}
		});
		view(context, true, bySeal(CIRCLES_VIEW), bySeal(CIRCLES_LOOK), CIRCLES_FOV);
		shot(context, "altera-teardrop-thin-" + name, windUp + SKY_THIN_FRAME_TICKS, true);
		if (spread) {
			world.getServer().runCommand("tick step " + SKY_WIDENING_STEP_TICKS);
			context.waitTicks(SKY_WIDENING_STEP_TICKS + FateClientTests.TICKS_TO_SETTLE);
			view(context, true, bySeal(WIDENING_VIEW), bySeal(WIDENING_LOOK), SKY_FOV);
			shot(context, "altera-teardrop-widening-" + name, windUp + SKY_THIN_FRAME_TICKS + SKY_WIDENING_STEP_TICKS, true);
		}
		world.getServer().runCommand("tick step " + SKY_WIDE_STEP_TICKS);
		context.waitTicks(SKY_WIDE_STEP_TICKS + FateClientTests.TICKS_TO_SETTLE);
		view(context, false, byBlast(IMPACT_VIEW), byBlast(IMPACT_LOOK), IMPACT_FOV);
		shot(context, "altera-teardrop-impact-" + name, windUp + SKY_WIDE_STEP_TICKS, true);
		view(context, false, byHeight(WIDE_VIEW), new Vec3(0, FateConfig.altera().teardropHeight() * WIDE_LOOK_HEIGHT_SHARE, 0), WIDE_FOV);
		shot(context, "altera-teardrop-wide-" + name, windUp + SKY_WIDE_STEP_TICKS, true);
		if (spread) {
			shot(context, "altera-teardrop-flow-" + name, windUp + SKY_FLOW_FRAME_TICKS, true);
			double radius = TeardropGeometry.spreadRadius(FateConfig.altera());
			double height = FateConfig.altera().teardropHeight();
			Vec3 middle = new Vec3(0, height / 2, 0);
			view(context, false, new Vec3(0, VIEW_EYE_HEIGHT, -radius - EDGE_VIEW_DISTANCE), middle, SKY_FOV);
			shot(context, "altera-teardrop-below-" + name, windUp + SKY_FLOW_FRAME_TICKS, true);
			Vec3 sideOffset = context.computeOnClient(client -> {
				TeardropPhotonRayEntity ray = client.level.getEntitiesOfClass(TeardropPhotonRayEntity.class,
					client.player.getBoundingBox().inflate(VIEW_RADIUS)).getFirst();
				Vec3 horizontal = new Vec3(radius * SIDE_LATERAL_RADII, 0, -radius * SIDE_DISTANCE_RADII);
				Vec3 at = ray.position().add(horizontal);
				int floor = client.level.getHeight(Heightmap.Types.MOTION_BLOCKING,
					Mth.floor(at.x), Mth.floor(at.z));
				return new Vec3(horizontal.x, floor + VIEW_EYE_HEIGHT - ray.getY(), horizontal.z);
			});
			view(context, false, sideOffset, new Vec3(0, height * SIDE_LOOK_SHARE, 0), SIDE_FOV);
			shot(context, "altera-teardrop-side-" + name, windUp + SKY_FLOW_FRAME_TICKS, true);
			view(context, false, new Vec3(0, height + radius * 3, -radius), new Vec3(0, height, 0), SKY_FOV);
			shot(context, "altera-teardrop-above-" + name, windUp + SKY_FLOW_FRAME_TICKS, true);
		}
		world.getServer().runCommand("tick unfreeze");
		FateClientTests.release(world, TeardropPhotonRay.KEY.identifier());
		context.runOnClient(client -> {
			client.setCameraEntity(client.player);
			client.options.setCameraType(CameraType.FIRST_PERSON);
		});
		world.getServer().runCommand("kill @e[type=armor_stand]");
	}

	private static Vec3 bySeal(Vec3 reference) {
		return reference.scale(TeardropGeometry.sealRadius(FateConfig.altera()) / TeardropGeometry.sealRadius(AlteraConfig.DEFAULTS));
	}

	private static Vec3 byBlast(Vec3 reference) {
		return reference.scale(TeardropGeometry.impactRadius(FateConfig.altera()) / TeardropGeometry.impactRadius(AlteraConfig.DEFAULTS));
	}

	private static Vec3 byHeight(Vec3 reference) {
		return reference.scale(FateConfig.altera().teardropHeight() / FRAMED_HEIGHT);
	}

	// The ray's held counters are set client-side, since tick freeze stops world physics but not those.
	private static void shot(ClientGameTestContext context, String name, int heldTicks, boolean firing) {
		Snapshot previous = context.computeOnClient(client -> {
			TeardropPhotonRayEntity ray = client.level.getEntitiesOfClass(TeardropPhotonRayEntity.class,
				client.player.getBoundingBox().inflate(VIEW_RADIUS)).getFirst();
			Snapshot snapshot = new Snapshot(ray.heldTicks(), ray.firing());
			ray.held(heldTicks, firing);
			return snapshot;
		});
		context.takeScreenshot(name);
		context.runOnClient(client -> client.level.getEntitiesOfClass(TeardropPhotonRayEntity.class,
			client.player.getBoundingBox().inflate(VIEW_RADIUS)).getFirst().held(previous.ticks, previous.firing));
	}

	private record Snapshot(int ticks, boolean firing) {
	}

	private static void view(ClientGameTestContext context, boolean atSeal, Vec3 offset, Vec3 look, int fov) {
		context.runOnClient(client -> {
			TeardropPhotonRayEntity ray = client.level.getEntitiesOfClass(TeardropPhotonRayEntity.class,
				client.player.getBoundingBox().inflate(VIEW_RADIUS)).getFirst();
			Vec3 origin = ray.position();
			if (atSeal) {
				origin = origin.add(TeardropGeometry.sky(FateConfig.altera(), 0));
			}
			Vec3 at = origin.add(offset);
			Vec3 direction = origin.add(look).subtract(at);
			float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
			float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
			// An untracked client-only camera cannot be hit by the broad beam, or have its pose
			// overwritten by a server armor-stand update/interpolation after tick step.
			ArmorStand camera = new ArmorStand(client.level, at.x, at.y, at.z);
			camera.setNoGravity(true);
			camera.snapTo(at.subtract(0, camera.getEyeHeight(), 0), yaw, pitch);
			client.setCameraEntity(camera);
			client.options.hideGui = true;
			client.options.setCameraType(CameraType.FIRST_PERSON);
			camera.setYHeadRot(yaw);
			camera.yHeadRotO = yaw;
			client.options.fov().set(fov);
		});
		context.waitTicks(FateClientTests.TICKS_TO_SETTLE);
	}
}
