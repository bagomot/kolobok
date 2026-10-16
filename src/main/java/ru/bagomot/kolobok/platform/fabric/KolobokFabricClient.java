package ru.bagomot.kolobok.platform.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.entity.EntityRenderers;
import ru.bagomot.kolobok.client.KolobokClient;
import ru.bagomot.kolobok.client.KolobokModel;
import ru.bagomot.kolobok.client.KolobokRenderer;
import ru.bagomot.kolobok.registry.KEntities;

public final class KolobokFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        KolobokClient.init(FabricLoader.getInstance().getConfigDir());
        ModelLayerRegistry.registerModelLayer(KolobokModel.LAYER, KolobokModel::createBodyLayer);
        // Public through Fabric's transitive access wideners; the old Fabric registry for this is
        // deprecated in favour of it.
        EntityRenderers.register(KEntities.KOLOBOK.get(), KolobokRenderer::new);
    }
}
