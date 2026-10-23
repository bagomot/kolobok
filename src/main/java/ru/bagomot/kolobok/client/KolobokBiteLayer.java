package ru.bagomot.kolobok.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;
import ru.bagomot.kolobok.Kolobok;

/**
 * A bitten Kolobok has a bite out of the top of his crust until the morning: the model drawn once more
 * with a texture that is empty but for the bite, the pale crumb inside and the tooth marks around it.
 */
final class KolobokBiteLayer extends RenderLayer<KolobokRenderState, KolobokModel> {

    private static final Identifier TEXTURE = Kolobok.id("textures/entity/kolobok/bitten.png");

    KolobokBiteLayer(RenderLayerParent<KolobokRenderState, KolobokModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, KolobokRenderState state, float yRot, float xRot) {
        if (state.bitten) {
            coloredCutoutModelCopyLayerRender(getParentModel(), TEXTURE, poseStack, collector, light, state, -1, 1);
        }
    }
}
