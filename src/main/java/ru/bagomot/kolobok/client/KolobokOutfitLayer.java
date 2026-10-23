package ru.bagomot.kolobok.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Draws what Kolobok wears: the hat from the anchor on top of him, which never rolls; the sunglasses
 * on his face, which does; the лапти on the ground under him, stepping in turn as he walks.
 *
 * <p>His own hats are item models, the very ones a player wears: the game already resolved the head
 * item into {@code state.headItem}. They are drawn on a «head» the size of a player's, his 8-pixel
 * core, so they fit him as they fit a player; the crown sits a size too big and askew, the wreath
 * wider and lower, on the forehead.
 */
public final class KolobokOutfitLayer extends RenderLayer<KolobokRenderState, KolobokModel> {

    private static final float PX = 1.0F / 16.0F;
    /** From the top of the ball down to his core's centre and to its bottom, a player's neck. */
    private static final float CORE_CENTRE = 5.0F;
    private static final float CORE_BOTTOM = 9.0F;
    /** Half the gap between the лапти centres. */
    private static final float LAPOT_SIDE = 3.0F;
    /** A step lifts a лапоть this high and swings it this far. */
    private static final float STEP_LIFT = 1.5F;
    private static final float STEP_SWING = 2.0F;

    private final ModelPart shelom;
    private final ModelPart aventail;
    private final ModelPart fur;
    private final ModelPart badge;
    private final ModelPart lapot;
    private final ModelPart glasses;

    public KolobokOutfitLayer(RenderLayerParent<KolobokRenderState, KolobokModel> parent, EntityModelSet models) {
        super(parent);
        ModelPart helmet = models.bakeLayer(OutfitModels.HELMET);
        shelom = helmet.getChild("shelom");
        aventail = helmet.getChild("aventail");
        ModelPart ushanka = models.bakeLayer(OutfitModels.USHANKA);
        fur = ushanka.getChild("fur");
        badge = ushanka.getChild("badge");
        lapot = models.bakeLayer(OutfitModels.LAPOT).getChild("lapot");
        glasses = models.bakeLayer(OutfitModels.GLASSES).getChild("glasses");
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, KolobokRenderState state, float yRot, float xRot) {
        if (state.isInvisible) {
            return;
        }
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
        KolobokModel model = getParentModel();
        submitHat(poseStack, collector, light, overlay, state, model);
        if (state.glasses) {
            poseStack.pushPose();
            model.root().translateAndRotate(poseStack);
            model.body().translateAndRotate(poseStack);
            part(collector, poseStack, glasses, OutfitModels.GLASSES_TEXTURE, light, overlay, -1, state);
            poseStack.popPose();
        }
        if (state.lapti != null) {
            for (int side = 0; side < 2; side++) {
                submitLapot(poseStack, collector, light, overlay, state, model, side);
            }
        }
    }

    private void submitHat(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, KolobokRenderState state,
                           KolobokModel model) {
        poseStack.pushPose();
        model.root().translateAndRotate(poseStack);
        model.hat().translateAndRotate(poseStack);
        switch (state.hat) {
            case HELMET -> {
                part(collector, poseStack, shelom, state.helmetTexture, light, overlay, -1, state);
                if (state.aventail) {
                    part(collector, poseStack, aventail, state.helmetTexture, light, overlay, -1, state);
                }
            }
            case USHANKA -> {
                part(collector, poseStack, fur, OutfitModels.USHANKA_TEXTURE, light, overlay, state.hatColour, state);
                part(collector, poseStack, badge, OutfitModels.USHANKA_TEXTURE, light, overlay, -1, state);
            }
            case CROWN, WREATH, KOKOSHNIK -> {
                if (!state.headItem.isEmpty()) {
                    poseStack.translate(0.0F, CORE_CENTRE * PX, 0.0F);
                    switch (state.hat) {
                        // A size too big: down over the brows, and askew.
                        case CROWN -> {
                            poseStack.translate(0.0F, 0.6F * PX, 0.0F);
                            poseStack.rotateDegrees(Axis.ZP, -10.0F);
                            poseStack.scale(1.15F, 1.15F, 1.15F);
                        }
                        // Wider than his head and lower, on the forehead.
                        case WREATH -> {
                            poseStack.translate(0.0F, 1.0F * PX, 0.0F);
                            poseStack.scale(1.2F, 1.0F, 1.2F);
                        }
                        default -> {
                        }
                    }
                    poseStack.translate(0.0F, (CORE_BOTTOM - CORE_CENTRE) * PX, 0.0F);
                    CustomHeadLayer.translateToHead(poseStack, CustomHeadLayer.Transforms.DEFAULT);
                    state.headItem.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
                }
            }
            default -> {
            }
        }
        poseStack.popPose();
    }

    /**
     * One лапоть, under the left or right half of him. Walking, they step in turn: the one in the air
     * swings forward, the one on the ground back.
     */
    private void submitLapot(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, KolobokRenderState state,
                             KolobokModel model, int side) {
        float phase = state.walkAnimationPos * 0.6662F + side * Mth.PI;
        float speed = Math.min(1.0F, state.walkAnimationSpeed);
        float lift = Math.max(0.0F, Mth.sin(phase)) * STEP_LIFT * speed;
        float swing = Mth.cos(phase) * STEP_SWING * speed;
        poseStack.pushPose();
        model.root().translateAndRotate(poseStack);
        poseStack.translate((side == 0 ? LAPOT_SIDE : -LAPOT_SIDE) * PX, (24.0F - lift) * PX, swing * PX);
        part(collector, poseStack, lapot, state.lapti, light, overlay, state.laptiColour, state);
        poseStack.popPose();
    }

    private static void part(SubmitNodeCollector collector, PoseStack poseStack, ModelPart part, Identifier texture,
                             int light, int overlay, int tint, KolobokRenderState state) {
        collector.submitModelPart(part, poseStack, RenderTypes.entityCutout(texture), light, overlay, null, tint, state.outlineColor);
    }
}
