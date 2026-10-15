package io.github.cwzmorro.fatecraft.client.weapon;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.cwzmorro.fatecraft.Fatecraft;
import io.github.cwzmorro.fatecraft.client.gate.TreasureDrawing;
import io.github.cwzmorro.fatecraft.client.weapon.WeaponMesh.Part;
import io.github.cwzmorro.fatecraft.weapon.FateWeapons;
import io.github.cwzmorro.fatecraft.weapon.WeaponOpening;
import io.github.cwzmorro.fatecraft.weapon.WeaponSpin;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Draws a {@link WeaponMesh} as an item: the item model names it as a {@code minecraft:special} model of type {@link #TYPE}
 * and the {@code base} model gives the poses. Textures are block atlas sprites, the only atlas with mipmaps, so a large
 * texture drawn small does not shimmer. Parts spin or open with {@link FateWeapons#SPIN} or {@link FateWeapons#OPENING}.
 */
public final class WeaponMeshRenderer implements SpecialModelRenderer<WeaponMeshRenderer.Pose> {
	public static final Identifier TYPE = Fatecraft.id("weapon_mesh");
	public static final String TEXTURE_FOLDER = "weapon/";
	private static final int WHITE = 0xFFFFFFFF;
	// Mesh coordinates place the grip at the block centre; whole-item turns leave it in the hand.
	private static final float GRIP = 0.5F;
	private static final float FIRST_PERSON_TIP_DOWN = 165.0F;
	private static final float THIRD_PERSON_TIP_DOWN = 120.0F;
	private static final float CAST_GRIP_LIFT = 1.05F;
	private static final double RAISED_SIDE_FIRST_PERSON = -0.25;
	private static final double RAISED_SIDE_THIRD_PERSON = -0.15;
	private static final double RAISED_LIFT = 0.35;
	private static final double RAISED_TURN_FIRST_PERSON = -35;
	private static final double RAISED_TURN_THIRD_PERSON = -60;
	// An empty-hand display undoes the centred item origin and vanilla's block item turn (degrees about Z, Y, X; Z and Y mirrored for the left arm), then its shift (blocks; sideways mirrored).
	private static final double HELD_ORIGIN_ROLL = 78.05;
	private static final double HELD_ORIGIN_YAW = 13.365;
	private static final double HELD_ORIGIN_PITCH = 102.25;
	private static final double HELD_ORIGIN_SIDE = 0.14142136;
	private static final double HELD_ORIGIN_UP = -0.08;
	private static final double HELD_ORIGIN_FORWARD = -0.14142136;
	private static final Predicate<Part> ALL = part -> true;
	private static final Predicate<Part> OPAQUE = part -> !part.translucent();
	// Vanilla's translucent item pipeline, but seen from both sides (the parts are open surfaces) and without writing depth; the quads are sorted by depth at upload.
	private static final RenderPipeline TRANSLUCENT_PIPELINE = RenderPipeline.builder(RenderPipelines.ITEM_SNIPPET)
		.withLocation(Fatecraft.id("pipeline/weapon_translucent"))
		.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
		.withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
		.withCull(false)
		.build();
	private static final RenderType TRANSLUCENT = mipmapped("translucent", TRANSLUCENT_PIPELINE, true);
	private static final RenderType BODY = mipmapped("body", RenderPipelines.ITEM_CUTOUT, false);
	// Vanilla's glowing eyes, the same way: blended, with neither the lightmap nor the directional shading of items, so the glow is as bright as it is painted.
	private static final RenderType GLOW = mipmapped("glow", RenderPipelines.EYES, true);

	private final Identifier meshId;
	private final boolean firstPerson;
	private final boolean glowWhileSpinning;
	private final boolean wholeSpin;
	private final @Nullable BowRig bow;
	private final @Nullable EmptyHand emptyHand;
	private final SpriteGetter sprites;
	private final SpriteId textureId;
	private final @Nullable SpriteId glowId;
	// Scratch space; the render thread is the only caller.
	private final Vector3f position = new Vector3f();
	private final Vector3f normal = new Vector3f();
	private final Quaternionf turn = new Quaternionf();
	private final Matrix4f partPose = new Matrix4f();
	private final Matrix3f partNormal = new Matrix3f();
	private final HeldPose held = new HeldPose();

	private WeaponMeshRenderer(Identifier meshId, SpriteGetter sprites, SpriteId textureId, @Nullable SpriteId glowId, boolean firstPerson,
		boolean glowWhileSpinning, boolean wholeSpin, @Nullable BowRig bow, @Nullable EmptyHand emptyHand) {
		this.meshId = meshId;
		this.firstPerson = firstPerson;
		this.glowWhileSpinning = glowWhileSpinning;
		this.wholeSpin = wholeSpin;
		this.bow = bow;
		this.emptyHand = emptyHand;
		this.sprites = sprites;
		this.textureId = textureId;
		this.glowId = glowId;
	}

	@Override
	public @Nullable Pose extractArgument(ItemStack stack) {
		WeaponSpin spin = stack.get(FateWeapons.SPIN);
		WeaponOpening opening = stack.get(FateWeapons.OPENING);
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (this.firstPerson && this.emptyHand != null && minecraft.player != null
			&& minecraft.player.isUsingItem() && minecraft.player.getUseItem() == stack) {
			HumanoidArm arm = minecraft.player.getUsedItemHand() == InteractionHand.MAIN_HAND
				? minecraft.player.getMainArm() : minecraft.player.getMainArm().getOpposite();
			return new Pose(0, 0, 0, 0, null, false, null, arm, true);
		}
		if (level == null) {
			return null;
		}
		double time = level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		boolean posed = this.held.apply(stack, time);
		boolean dark = this.glowWhileSpinning && spin == null;
		if (spin == null && opening == null && !posed && !dark) {
			return null;
		}
		ItemStack nocked = this.held.nocked();
		TreasureDrawing.Look sword = null;
		if (nocked != null && minecraft.player != null) {
			sword = new TreasureDrawing.Look();
			TreasureDrawing.extract(minecraft.getItemModelResolver(), sword, nocked, minecraft.player, 0);
		}
		return new Pose(spin == null ? 0.0F : spin.degrees(time), opening == null ? 0.0F : opening.opening(time), this.held.tipDown(),
			this.held.draw(), sword, this.held.raised(), this.held.overlay(), null, !dark);
	}

	@Override
	public void submit(@Nullable Pose pose, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
		if (pose != null && pose.emptyHand() != null && this.emptyHand != null) {
			this.submitEmptyHand(this.emptyHand, pose.emptyHand(), poseStack, collector, light);
			return;
		}
		WeaponMesh mesh = WeaponMeshes.get(this.meshId);
		if (mesh == null) {
			return;
		}
		// Looked up at draw time, like vanilla's shield: the atlases aren't ready yet when models bake.
		poseStack.pushPose();
		if (pose != null && pose.raised()) {
			// Raise the grip in front and turn the blade upright, independent of the tip-down pose.
			poseStack.translate(this.firstPerson ? RAISED_SIDE_FIRST_PERSON : RAISED_SIDE_THIRD_PERSON, RAISED_LIFT, 0);
			poseStack.translate(GRIP, GRIP, GRIP);
			poseStack.mulPose(this.turn.rotationZ((float) Math.toRadians(this.firstPerson ? RAISED_TURN_FIRST_PERSON : RAISED_TURN_THIRD_PERSON)));
			poseStack.translate(-GRIP, -GRIP, -GRIP);
		}
		if (pose != null) {
			poseStack.translate(GRIP, GRIP + (this.firstPerson ? CAST_GRIP_LIFT * pose.tipDown() : 0), GRIP);
			poseStack.mulPose(this.turn.rotationZ((float) Math.toRadians(
				(this.firstPerson ? FIRST_PERSON_TIP_DOWN : THIRD_PERSON_TIP_DOWN) * pose.tipDown())));
			if (this.wholeSpin) {
				poseStack.mulPose(this.turn.rotationY((float) Math.toRadians(pose.spin())));
			}
			poseStack.translate(-GRIP, -GRIP, -GRIP);
		}
		TextureAtlasSprite texture = this.sprites.get(this.textureId);
		this.submitParts(collector, poseStack, BODY, mesh, texture, OPAQUE, pose, light, overlay);
		// After the opaque parts, so what is behind them shows through.
		this.submitParts(collector, poseStack, TRANSLUCENT, mesh, texture, Part::translucent, pose, light, overlay);
		if (this.glowId != null && (pose == null || pose.glows())) {
			TextureAtlasSprite glow = this.sprites.get(this.glowId);
			this.submitParts(collector, poseStack, GLOW, mesh, glow, part -> part.emissive() && !part.translucent(), pose, LightCoordsUtil.FULL_BRIGHT, overlay);
			// The unshaded eyes pass would erase the blade's facets, so its glow keeps directional normals like its body.
			this.submitParts(collector, poseStack, TRANSLUCENT, mesh, glow, part -> part.emissive() && part.translucent(), pose, LightCoordsUtil.FULL_BRIGHT, overlay);
		}
		if (pose != null && pose.overlay() != null) {
			pose.overlay().draw(collector, poseStack, mesh);
		}
		if (foil) {
			this.submitParts(collector, poseStack, RenderTypes.glint(), mesh, texture, ALL, pose, light, overlay);
		}
		if (this.bow != null && pose != null && pose.sword() != null && !pose.sword().isEmpty()) {
			poseStack.pushPose();
			// The sword at its held length in either camera, its rear end at the string; only the uniform held-item scale applies.
			poseStack.translate(this.bow.nockX(pose.draw(), pose.swordLength()) - pose.sword().length() / 2,
				this.bow.nock().y(), this.bow.nock().z());
			BowPose.submitNocked(poseStack, collector, pose.sword(), outline);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	private void submitEmptyHand(EmptyHand hand, HumanoidArm arm, PoseStack poseStack, SubmitNodeCollector collector, int light) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.player.isInvisible()) {
			return;
		}
		int side = arm == HumanoidArm.RIGHT ? 1 : -1;
		poseStack.pushPose();
		poseStack.translate(GRIP, GRIP, GRIP);
		poseStack.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(-side * HELD_ORIGIN_ROLL))
			.rotateY((float) Math.toRadians(-side * HELD_ORIGIN_YAW)).rotateX((float) Math.toRadians(HELD_ORIGIN_PITCH)));
		poseStack.translate(side * HELD_ORIGIN_SIDE, HELD_ORIGIN_UP, HELD_ORIGIN_FORWARD);
		poseStack.translate(side * hand.offset().x(), hand.offset().y(), hand.offset().z());
		poseStack.mulPose(new Quaternionf().rotationX((float) Math.toRadians(hand.pitch())));
		AvatarRenderer<?> renderer = minecraft.getEntityRenderDispatcher().getPlayerRenderer(minecraft.player);
		Identifier skin = minecraft.player.getSkin().body().texturePath();
		if (arm == HumanoidArm.RIGHT) {
			renderer.renderRightHand(poseStack, collector, light, skin, minecraft.player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
		} else {
			renderer.renderLeftHand(poseStack, collector, light, skin, minecraft.player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
		}
		poseStack.popPose();
	}

	public static void submitFloating(Identifier meshId, Identifier texture, PoseStack poseStack, SubmitNodeCollector collector) {
		WeaponMesh mesh = WeaponMeshes.get(meshId);
		if (mesh == null) {
			return;
		}
		WeaponMeshRenderer renderer = new WeaponMeshRenderer(meshId, Minecraft.getInstance().getAtlasManager(),
			new SpriteId(Sheets.BLOCKS_MAPPER.sheet(), texture), null, false, false, false, null, null);
		TextureAtlasSprite sprite = renderer.sprites.get(renderer.textureId);
		renderer.submitParts(collector, poseStack, BODY, mesh, sprite, OPAQUE, null, LightCoordsUtil.FULL_BRIGHT, 0);
		renderer.submitParts(collector, poseStack, TRANSLUCENT, mesh, sprite, Part::translucent, null, LightCoordsUtil.FULL_BRIGHT, 0);
	}

	private static RenderType mipmapped(String name, RenderPipeline pipeline, boolean translucent) {
		RenderSetup.RenderSetupBuilder setup = RenderSetup.builder(pipeline)
			.withTexture("Sampler0", Sheets.BLOCKS_MAPPER.sheet(), () -> RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE,
				AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.NEAREST, true))
			.useLightmap()
			.affectsCrumbling()
			.setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE);
		if (translucent) {
			setup.setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET).sortOnUpload();
		}
		return RenderType.create("fatecraft_weapon_mesh_" + name, setup.createRenderSetup());
	}

	private void submitParts(SubmitNodeCollector collector, PoseStack poseStack, RenderType type, WeaponMesh mesh, TextureAtlasSprite sprite,
		Predicate<Part> drawn, @Nullable Pose pose, int light, int overlay) {
		collector.submitCustomGeometry(poseStack, type, (matrices, buffer) -> {
			for (Part part : mesh.parts()) {
				if (drawn.test(part)) {
					this.emit(matrices, buffer, part, sprite, pose, light, overlay);
				}
			}
		});
	}

	private void emit(PoseStack.Pose pose, VertexConsumer buffer, Part part, TextureAtlasSprite sprite, @Nullable Pose parts, int light, int overlay) {
		Matrix4f matrix = pose.pose();
		Matrix3f normals = pose.normal();
		if (parts != null && (part.spin() != 0 || part.opens())) {
			Vector3fc offset = part.openOffset();
			matrix = this.partPose.set(matrix).translate(offset.x() * parts.opening(), offset.y() * parts.opening(), offset.z() * parts.opening());
			normals = this.partNormal.set(normals);
			if (part.openDegrees() != 0.0F) {
				this.turn(matrix, normals, part, part.openDegrees() * parts.opening());
			}
			if (part.spin() != 0) {
				this.turn(matrix, normals, part, parts.spin() * part.spin());
			}
		}
		float[] quads = part.quads();
		BowRig rig = this.bow;
		float travel = rig != null && parts != null && part.name().equals(rig.string())
			? rig.nockX(parts.draw(), parts.swordLength()) - rig.nock().x() : 0;
		float shear = 0;
		for (int i = 0; i < quads.length; i += WeaponMesh.STRIDE) {
			if (travel != 0 && i % (WeaponMesh.STRIDE * 4) == 0) {
				// A centre vertex belongs to either string half: use its quad's side for the normal derivative.
				float quadY = (quads[i + 1] + quads[i + WeaponMesh.STRIDE + 1]
					+ quads[i + WeaponMesh.STRIDE * 2 + 1] + quads[i + WeaponMesh.STRIDE * 3 + 1]) / 4;
				shear = rig.stringSlope(quadY, travel);
			}
			float x = quads[i] + (travel == 0 ? 0 : travel * rig.stringWeight(quads[i + 1]));
			matrix.transformPosition(x, quads[i + 1], quads[i + 2], this.position);
			normals.transform(quads[i + 5], quads[i + 6] - shear * quads[i + 5], quads[i + 7], this.normal).normalize();
			buffer.addVertex(this.position.x, this.position.y, this.position.z, WHITE, sprite.getU(quads[i + 3]),
				sprite.getV(quads[i + 4]), overlay, light, this.normal.x, this.normal.y, this.normal.z);
		}
	}

	private void turn(Matrix4f matrix, Matrix3f normals, Part part, float degrees) {
		Vector3fc pivot = part.pivot();
		Vector3fc axis = part.axis();
		this.turn.rotationAxis((float) Math.toRadians(degrees), axis.x(), axis.y(), axis.z());
		matrix.rotateAround(this.turn, pivot.x(), pivot.y(), pivot.z());
		normals.rotate(this.turn);
	}

	/**
	 * What moves the parts this frame.
	 *
	 * @param spin      the spin angle in degrees
	 * @param opening   how open the weapon is, 0 (closed) to 1
	 * @param tipDown   eased fraction of the held tip-down pose
	 * @param draw      how far a bow is drawn, 0 to 1
	 * @param sword     the sword nocked on a drawn bow, or null
	 * @param raised    the held-charge pose
	 * @param overlay   drawn over the mesh, or null
	 * @param emptyHand the arm drawn empty in its place, or null
	 * @param glows     whether the emissive parts are drawn glowing
	 */
	public record Pose(float spin, float opening, float tipDown, float draw, TreasureDrawing.@Nullable Look sword, boolean raised,
		HeldPose.@Nullable Overlay overlay, @Nullable HumanoidArm emptyHand, boolean glows) {
		double swordLength() {
			return this.sword == null || this.sword.isEmpty() ? Double.POSITIVE_INFINITY : this.sword.length();
		}
	}

	/**
	 * The player's empty arm drawn in the item's place while the local player uses it in first person, from the ordinary
	 * held-item origin.
	 *
	 * @param offset where the hand goes, blocks (sideways mirrored for the left arm)
	 * @param pitch  how far the arm is turned about the sideways axis, degrees
	 */
	public record EmptyHand(Vector3fc offset, float pitch) {
		public static final Codec<EmptyHand> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				ExtraCodecs.VECTOR3F.fieldOf("offset").forGetter(EmptyHand::offset),
				Codec.FLOAT.fieldOf("pitch").forGetter(EmptyHand::pitch))
			.apply(instance, EmptyHand::new));
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		WeaponMesh mesh = WeaponMeshes.get(this.meshId);
		if (mesh != null) {
			// Real quad vertices let projection light trace the mesh rather than its bounding box.
			for (Part part : mesh.parts()) {
				float[] quads = part.quads();
				for (int i = 0; i < quads.length; i += WeaponMesh.STRIDE) {
					output.accept(new Vector3f(quads[i], quads[i + 1], quads[i + 2]));
				}
			}
		}
	}

	/**
	 * {@code {"type": "fatecraft:weapon_mesh", "mesh": "<namespace>:<name>", "texture": "<sprite>", "glow": "<sprite>"}}:
	 * the mesh in {@code models/weapon/} with its texture and optional glow as block atlas sprites. Optional keys:
	 * {@code glow_while_spinning}, {@code whole_spin} (the whole weapon spins about its grip), {@code bow}, {@code empty_hand}.
	 */
	public record Unbaked(Identifier mesh, Identifier texture, Optional<Identifier> glow, boolean firstPerson, boolean glowWhileSpinning,
		boolean wholeSpin, Optional<BowRig> bow, Optional<EmptyHand> emptyHand) implements SpecialModelRenderer.Unbaked<Pose> {
		public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				Identifier.CODEC.fieldOf("mesh").forGetter(Unbaked::mesh),
				Identifier.CODEC.fieldOf("texture").forGetter(Unbaked::texture),
				Identifier.CODEC.optionalFieldOf("glow").forGetter(Unbaked::glow),
				Codec.BOOL.optionalFieldOf("first_person", false).forGetter(Unbaked::firstPerson),
				Codec.BOOL.optionalFieldOf("glow_while_spinning", false).forGetter(Unbaked::glowWhileSpinning),
				Codec.BOOL.optionalFieldOf("whole_spin", false).forGetter(Unbaked::wholeSpin),
				BowRig.CODEC.optionalFieldOf("bow").forGetter(Unbaked::bow),
				EmptyHand.CODEC.optionalFieldOf("empty_hand").forGetter(Unbaked::emptyHand))
			.apply(instance, Unbaked::new));

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public WeaponMeshRenderer bake(SpecialModelRenderer.BakingContext context) {
			Identifier atlas = Sheets.BLOCKS_MAPPER.sheet();
			return new WeaponMeshRenderer(this.mesh, context.sprites(), new SpriteId(atlas, this.texture),
				this.glow.map(id -> new SpriteId(atlas, id)).orElse(null), this.firstPerson, this.glowWhileSpinning, this.wholeSpin,
				this.bow.orElse(null), this.emptyHand.orElse(null));
		}
	}
}
