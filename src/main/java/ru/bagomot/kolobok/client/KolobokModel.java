package ru.bagomot.kolobok.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import ru.bagomot.kolobok.Kolobok;

/**
 * Model «Б»: a Ø10 pixel ball from a core 8×8×8 and three 10×6×6 crossbars, one per axis, which
 * gives the 6–8–10–8–6 silhouette.
 *
 * <p>The face is a flat plate in front of the forward crossbar, one plate per face in the texture
 * atlas; exactly one is visible. It is a child of {@code body}, so it rolls with him. {@code hat} is a
 * sibling of {@code body} rather than a child: the hat must not roll.
 *
 * <p>Cheeks puff out at the lower corners of the face when he carries something in them, bigger the
 * fuller they are; they take their crust from the core's sides.
 *
 * <p>Texture layout, 64×64: core at (0,0), X bar at (0,16), Y bar at (32,0), Z bar at (0,28), faces
 * 12×6 each in two columns from (32,16). {@code tools/textures/GenTextures.java} draws it.
 */
public final class KolobokModel extends EntityModel<KolobokRenderState> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Kolobok.id("kolobok"), "main");

    public static final int FACE_HAPPY = 0;
    public static final int FACE_BLINK = 1;
    public static final int FACES = 12;

    /** Radius of the ball in blocks: rolling turns the body by distance over this. */
    public static final float RADIUS = 5.0F / 16.0F;

    private static final float CENTRE_Y = 24.0F - 5.0F;
    /** How big a cheek is at each puff, 1–3. */
    private static final float[] CHEEK_SCALE = {0.0F, 0.75F, 1.0F, 1.3F};

    private final ModelPart body;
    private final ModelPart hat;
    private final ModelPart[] faces = new ModelPart[FACES];
    private final ModelPart cheekLeft;
    private final ModelPart cheekRight;

    public KolobokModel(ModelPart root) {
        super(root);
        body = root.getChild("body");
        hat = root.getChild("hat");
        for (int i = 0; i < FACES; i++) {
            faces[i] = body.getChild("face_" + i);
        }
        cheekLeft = body.getChild("cheek_left");
        cheekRight = body.getChild("cheek_right");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F)
                        .texOffs(0, 16).addBox(-5.0F, -3.0F, -3.0F, 10.0F, 6.0F, 6.0F)
                        .texOffs(32, 0).addBox(-3.0F, -5.0F, -3.0F, 6.0F, 10.0F, 6.0F)
                        .texOffs(0, 28).addBox(-3.0F, -3.0F, -5.0F, 6.0F, 6.0F, 10.0F),
                PartPose.offset(0.0F, CENTRE_Y, 0.0F));
        for (int i = 0; i < FACES; i++) {
            body.addOrReplaceChild(
                    "face_" + i,
                    CubeListBuilder.create()
                            .texOffs(32 + 12 * (i % 2), 16 + 6 * (i / 2))
                            .addBox(-3.0F, -3.0F, -5.05F, 6.0F, 6.0F, 0.0F),
                    PartPose.ZERO);
        }
        for (int side = -1; side <= 1; side += 2) {
            body.addOrReplaceChild(
                    side < 0 ? "cheek_right" : "cheek_left",
                    CubeListBuilder.create().texOffs(0, 44).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F),
                    PartPose.offset(side * 3.5F, 1.5F, -4.0F));
        }
        // Hats hang off this anchor; it sits on top of the ball and never rolls.
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.offset(0.0F, CENTRE_Y - 5.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    public ModelPart body() {
        return body;
    }

    /** The anchor hats sit on: the top of the ball, which never rolls. */
    public ModelPart hat() {
        return hat;
    }

    @Override
    public void setupAnim(KolobokRenderState state) {
        super.setupAnim(state);
        body.xRot = state.roll;
        if (state.lapti != null) {
            // Up on his лапти, bobbing a little with each step.
            float speed = Math.min(1.0F, state.walkAnimationSpeed);
            body.y -= OutfitModels.LAPTI_LIFT + Math.abs(Mth.sin(state.walkAnimationPos * 0.6662F)) * 0.5F * speed;
        }
        if (state.sitting) {
            // A little squashed when he sits, bottom kept on the ground.
            body.yScale = 0.9F;
            body.xScale = 1.05F;
            body.zScale = 1.05F;
            body.y += 5.0F * (1.0F - body.yScale);
        }
        if (state.dancing) {
            // As a parrot at a jukebox: bobbing to the beat, swaying from side to side.
            body.y -= Math.abs(Mth.sin(state.ageInTicks * 0.6F)) * 1.5F;
            body.zRot = Mth.sin(state.ageInTicks * 0.3F) * 0.25F;
            hat.zRot = body.zRot;
        }
        for (int i = 0; i < FACES; i++) {
            faces[i].visible = i == state.face;
        }
        float cheek = CHEEK_SCALE[Math.clamp(state.cheeks, 0, 3)];
        for (ModelPart part : new ModelPart[]{cheekLeft, cheekRight}) {
            part.visible = cheek > 0.0F;
            part.xScale = cheek;
            part.yScale = cheek;
            part.zScale = cheek;
        }
        hat.y = body.y - 5.0F * body.yScale;
    }
}
