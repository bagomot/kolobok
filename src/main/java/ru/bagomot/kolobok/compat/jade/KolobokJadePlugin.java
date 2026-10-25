package ru.bagomot.kolobok.compat.jade;

import ru.bagomot.kolobok.block.StovePartBlock;
import ru.bagomot.kolobok.entity.KolobokEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade, when installed: what the stove's labels said and more about the stove, and what can be told
 * about a Kolobok by looking at him. Everything is read from what the client already has, so it works
 * on servers without Jade too. Fabric finds the plugin by the {@code jade} entrypoint, NeoForge by
 * the annotation.
 */
@WailaPlugin
public final class KolobokJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(StoveProvider.INSTANCE, StovePartBlock.class);
        registration.registerEntityComponent(KolobokProvider.INSTANCE, KolobokEntity.class);
        registration.markAsClientFeature(StoveProvider.INSTANCE.getUid());
        registration.markAsClientFeature(KolobokProvider.INSTANCE.getUid());
    }
}
