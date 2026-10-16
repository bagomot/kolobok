package ru.bagomot.kolobok.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import ru.bagomot.kolobok.Kolobok;

/**
 * Model «Б»: a Ø10 pixel ball from a core 8×8×8 and three 10×6×6 crossbars, one per axis, which
 * gives the 6–8–10–8–6 silhouette.
 *
 * <p>The face is a flat plate in front of the forward crossbar, one plate per face in the texture
 * atlas; exactly one is visible. It is a child of {@code body}, so it rolls with him. {@code hat} is a
 * sibling of {@code body} rather than a child: the hat must not roll.
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

    private final ModelPart body;
    private final ModelPart hat;
    private final ModelPart[] faces = new ModelPart[FACES];

    public KolobokModel(ModelPart root) {
        super(root);
        body = root.getChild("body");
        hat = root.getChild("hat");
        for (int i = 0; i < FACES; i++) {
            faces[i] = body.getChild("face_" + i);
        }
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
        // Outfits hang off this anchor in v0.5; it sits on top of the ball and never rolls.
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.offset(0.0F, CENTRE_Y - 5.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(KolobokRenderState state) {
        super.setupAnim(state);
        body.xRot = state.roll;
        if (state.sitting) {
            // A little squashed when he sits, bottom kept on the ground.
            body.yScale = 0.9F;
            body.xScale = 1.05F;
            body.zScale = 1.05F;
            body.y += 5.0F * (1.0F - body.yScale);
        }
        for (int i = 0; i < FACES; i++) {
            faces[i].visible = i == state.face;
        }
        hat.y = body.y - 5.0F * body.yScale;
    }
}
