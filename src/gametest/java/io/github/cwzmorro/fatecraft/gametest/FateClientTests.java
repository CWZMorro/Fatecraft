package io.github.cwzmorro.fatecraft.gametest;

import io.github.cwzmorro.alayacore.ability.Abilities;
import io.github.cwzmorro.alayacore.api.AlayaRegistries;
import io.github.cwzmorro.alayacore.network.ChooseServantPayload;
import io.github.cwzmorro.alayacore.servant.Servant;
import io.github.cwzmorro.alayacore.servant.ServantSelection;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class FateClientTests {
	static final int TICKS_TO_SETTLE = 10;
	static final String[] TIMES_OF_DAY = {"noon", "midnight"};
	static final String[] LIGHTING_NAMES = {"day", "night"};
	static final int FLOOR_Y = 100;
	private static final int FLOOR_LENGTH = 30;
	private static final int FLOOR_HALF_WIDTH = 20;
	private static final int FLOOR_CLEARANCE = 10;
	private static final int GROUND_DISTANCE = 2;
	static final int GROUND_PITCH = 50;
	private static final double VIEW_SEARCH = 200.0;
	// Names the only client tests to run, set from Gradle's clientTest property (see build.gradle).
	private static final String ONLY_PROPERTY = "fatecraft.clientTest";
	private static final double FRAME_PADDING = 1.15;
	private static @Nullable ArmorStand viewedPlayerCamera;
	private static boolean viewExtractionRegistered;

	private FateClientTests() {
	}

	static boolean skipped(FabricClientGameTest test) {
		String only = System.getProperty(ONLY_PROPERTY);
		if (only == null) {
			return false;
		}
		for (String prefix : only.split(",")) {
			if (test.getClass().getSimpleName().startsWith(prefix.trim())) {
				return false;
			}
		}
		return true;
	}

	static void becomeServant(ClientGameTestContext context, TestSingleplayerContext world, Servant servant) {
		// Alaya Core's selection screen opens on first join (its class is client code of another mod).
		context.waitFor(client -> client.screen != null);
		world.getServer().runOnServer(server -> ServantSelection.choose(player(server),
			new ChooseServantPayload(ChooseServantPayload.Kind.SERVANT, Optional.of(AlayaRegistries.SERVANT.getKey(servant)))));
		context.setScreen(() -> null);
		world.getClientLevel().waitForChunksRender();
	}

	static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	static void setGuiScale(ClientGameTestContext context, int scale) {
		context.runOnClient(client -> {
			client.options.guiScale().set(scale);
			client.resizeGui();
		});
		context.waitTicks(TICKS_TO_SETTLE);
	}

	// Vanilla leaves the local player out of the frame when the camera is elsewhere, so this adds its render state back.
	private static void includeViewedPlayer() {
		if (viewExtractionRegistered) {
			return;
		}
		viewExtractionRegistered = true;
		LevelRenderEvents.END_EXTRACTION.register(frame -> {
			Minecraft client = Minecraft.getInstance();
			if (viewedPlayerCamera == null || client.level == null || client.player == null
				|| frame.level() != client.level || viewedPlayerCamera.level() != client.level
				|| client.getCameraEntity() != viewedPlayerCamera || frame.camera().entity() != viewedPlayerCamera) {
				return;
			}
			var levelState = frame.levelState();
			var camera = levelState.cameraRenderState;
			var dispatcher = client.getEntityRenderDispatcher();
			if (!dispatcher.shouldRender(client.player, camera.cullFrustum, camera.pos.x, camera.pos.y, camera.pos.z)
				|| levelState.entityRenderStates.stream().anyMatch(state -> state instanceof AvatarRenderState avatar
					&& avatar.id == client.player.getId())) {
				return;
			}
			float partial = frame.deltaTracker().getGameTimeDeltaPartialTick(false);
			levelState.entityRenderStates.add(dispatcher.extractEntity(client.player, partial));
		});
	}

	static void viewFramed(ClientGameTestContext context, AABB bounds, Vec3 viewingDirection, int fov) {
		double aspect = context.computeOnClient(client -> (double) client.getWindow().getWidth() / client.getWindow().getHeight());
		double verticalHalfFov = Math.toRadians(fov) / 2;
		double horizontalHalfFov = Math.atan(Math.tan(verticalHalfFov) * aspect);
		double radius = new Vec3(bounds.getXsize(), bounds.getYsize(), bounds.getZsize()).length() / 2;
		double distance = FRAME_PADDING * radius / Math.sin(Math.min(verticalHalfFov, horizontalHalfFov));
		Vec3 centre = bounds.getCenter();
		view(context, centre.add(viewingDirection.normalize().scale(distance)), centre, fov);
	}

	static void view(ClientGameTestContext context, Vec3 at, Vec3 look, int fov) {
		viewNow(context, at, look, fov);
		context.waitTicks(TICKS_TO_SETTLE);
	}

	// A player whose camera is not himself sends no movement to the server, so for as long as it lasts the server doesn't see him move.
	static void viewNow(ClientGameTestContext context, Vec3 at, Vec3 look, int fov) {
		context.runOnClient(client -> {
			Vec3 direction = look.subtract(at);
			float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
			float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
			ArmorStand camera = new ArmorStand(client.level, at.x, at.y, at.z);
			camera.setNoGravity(true);
			camera.snapTo(at.subtract(0, camera.getEyeHeight(), 0), yaw, pitch);
			camera.setYHeadRot(yaw);
			camera.yHeadRotO = yaw;
			includeViewedPlayer();
			viewedPlayerCamera = camera;
			client.setCameraEntity(camera);
			client.options.setCameraType(CameraType.FIRST_PERSON);
			client.options.fov().set(fov);
		});
	}

	static void setCamera(ClientGameTestContext context, CameraType camera) {
		context.runOnClient(client -> {
			if (viewedPlayerCamera != null && client.getCameraEntity() == viewedPlayerCamera) {
				client.setCameraEntity(client.player);
			}
			viewedPlayerCamera = null;
			client.options.setCameraType(camera);
		});
		context.waitTicks(TICKS_TO_SETTLE);
	}

	static void lookFrom(ClientGameTestContext context, TestSingleplayerContext world, String name, int east, int up, int south, int yaw, int pitch) {
		CameraType before = context.computeOnClient(client -> client.options.getCameraType());
		boolean guiHidden = context.computeOnClient(client -> client.options.hideGui);
		context.runOnClient(client -> client.options.hideGui = true);
		setCamera(context, CameraType.FIRST_PERSON);
		world.getServer().runCommand("execute as @p at @s run summon armor_stand ~" + east + " ~" + up + " ~" + south
			+ " {Invisible:1b,NoGravity:1b,Marker:1b,Rotation:[" + yaw + "f," + pitch + "f]}");
		context.waitTicks(TICKS_TO_SETTLE);
		context.runOnClient(client -> client.setCameraEntity(client.level.getEntitiesOfClass(ArmorStand.class,
			client.player.getBoundingBox().inflate(VIEW_SEARCH)).getFirst()));
		context.waitTicks(TICKS_TO_SETTLE);
		context.takeScreenshot(name);
		context.runOnClient(client -> client.setCameraEntity(client.player));
		world.getServer().runCommand("kill @e[type=armor_stand]");
		context.runOnClient(client -> client.options.hideGui = guiHidden);
		setCamera(context, before);
	}

	static void giveMana(ServerPlayer player) {
		FateTests.addMana(player, FateTests.MANA);
	}

	static void clearNoon(TestSingleplayerContext world) {
		world.getServer().runCommand("time set noon");
		world.getServer().runCommand("weather clear");
	}

	static void hold(TestSingleplayerContext world, Item item) {
		world.getServer().runCommand("item replace entity @p weapon.mainhand with " + BuiltInRegistries.ITEM.getKey(item));
	}

	static void quietNoon(ClientGameTestContext context, TestSingleplayerContext world) {
		context.runOnClient(client -> {
			client.getToastManager().clear();
			client.options.hideGui = true;
		});
		clearNoon(world);
		faceSouth(world);
	}

	static void flatFloor(TestSingleplayerContext world) {
		flatFloor(world, FLOOR_LENGTH, FLOOR_CLEARANCE);
	}

	static void flatFloor(TestSingleplayerContext world, int length, int clearance) {
		world.getServer().runCommand("fill -" + FLOOR_HALF_WIDTH + " " + FLOOR_Y + " -" + FLOOR_HALF_WIDTH + " " + FLOOR_HALF_WIDTH + " " + FLOOR_Y + " "
			+ length + " stone");
		world.getServer().runCommand("fill -" + FLOOR_HALF_WIDTH + " " + (FLOOR_Y + 1) + " -" + FLOOR_HALF_WIDTH + " " + FLOOR_HALF_WIDTH + " "
			+ (FLOOR_Y + clearance) + " " + length + " air");
		world.getServer().runCommand("tp @p 0 " + (FLOOR_Y + 1) + " 0 0 0");
	}

	static void face(TestSingleplayerContext world, double yaw, double pitch) {
		world.getServer().runCommand("execute as @p at @s run tp @s ~ ~ ~ " + yaw + " " + pitch);
	}

	static void faceSouth(TestSingleplayerContext world) {
		face(world, 0, 0);
	}

	static void dropEaAhead(TestSingleplayerContext world) {
		world.getServer().runCommand("execute as @p at @s run summon item ~ ~ ~" + GROUND_DISTANCE + " {Item:{id:\"" + BuiltInRegistries.ITEM.getKey(FateWeapons.EA)
			+ "\",count:1},PickupDelay:" + Short.MAX_VALUE + "}");
		face(world, 0, GROUND_PITCH);
	}

	static void press(TestSingleplayerContext world, Identifier ability, int mode) {
		world.getServer().runOnServer(server -> Abilities.press(player(server), ability, mode, false));
	}

	static void release(TestSingleplayerContext world, Identifier ability) {
		world.getServer().runOnServer(server -> Abilities.release(player(server), ability));
	}

	static void tap(TestSingleplayerContext world, Identifier ability, int mode) {
		world.getServer().runOnServer(server -> {
			FateTests.tap(player(server), ability, mode, false);
		});
	}
}
