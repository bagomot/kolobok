package ru.bagomot.kolobok.platform.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import ru.bagomot.kolobok.Kolobok;
import ru.bagomot.kolobok.client.KolobokClient;
import ru.bagomot.kolobok.client.KolobokModel;
import ru.bagomot.kolobok.client.KolobokRenderer;
import ru.bagomot.kolobok.registry.KEntities;

/**
 * Client half of the NeoForge entry point; FML constructs it on the physical client only.
 */
@Mod(value = Kolobok.MOD_ID, dist = Dist.CLIENT)
public final class KolobokNeoForgeClient {

    public KolobokNeoForgeClient(IEventBus modBus, ModContainer container) {
        KolobokClient.init(FMLPaths.CONFIGDIR.get());
        modBus.addListener(EntityRenderersEvent.RegisterLayerDefinitions.class,
                event -> event.registerLayerDefinition(KolobokModel.LAYER, KolobokModel::createBodyLayer));
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerEntityRenderer(KEntities.KOLOBOK.get(), KolobokRenderer::new));
    }
}
