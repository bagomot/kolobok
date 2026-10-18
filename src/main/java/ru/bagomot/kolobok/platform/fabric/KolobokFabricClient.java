package ru.bagomot.kolobok.platform.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import ru.bagomot.kolobok.client.KolobokClient;
import ru.bagomot.kolobok.client.KolobokModel;
import ru.bagomot.kolobok.client.KolobokRenderer;
import ru.bagomot.kolobok.client.SongPlayer;
import ru.bagomot.kolobok.client.StoveRenderer;
import ru.bagomot.kolobok.registry.KBlockEntities;
import ru.bagomot.kolobok.registry.KEntities;

public final class KolobokFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        KolobokClient.init(FabricLoader.getInstance().getConfigDir());
        ModelLayerRegistry.registerModelLayer(KolobokModel.LAYER, KolobokModel::createBodyLayer);
        // Both public through Fabric's transitive access wideners; the old Fabric registries for these
        // are deprecated in favour of them.
        EntityRenderers.register(KEntities.KOLOBOK.get(), KolobokRenderer::new);
        BlockEntityRenderers.register(KBlockEntities.STOVE.get(), StoveRenderer::new);
        // Once per rendered frame: the song is timed finer than ticks.
        LevelExtractionEvents.END_EXTRACTION.register(context -> SongPlayer.frame());
    }
}
