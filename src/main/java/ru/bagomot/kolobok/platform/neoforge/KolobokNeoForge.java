package ru.bagomot.kolobok.platform.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import ru.bagomot.kolobok.Kolobok;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.registry.KEntities;
import ru.bagomot.kolobok.registry.KRegistry;

/**
 * NeoForge entry point. No client references, so a dedicated server loads it cleanly. The registries
 * are frozen when the constructor runs; each catalogue goes in when NeoForge reopens its registry.
 */
@Mod(Kolobok.MOD_ID)
public final class KolobokNeoForge {

    public KolobokNeoForge(IEventBus modBus, ModContainer container) {
        Kolobok.init(new NeoForgePlatform());
        modBus.addListener(RegisterEvent.class, KolobokNeoForge::register);
        modBus.addListener(EntityAttributeCreationEvent.class,
                event -> event.put(KEntities.KOLOBOK.get(), KolobokEntity.createAttributes().build()));
    }

    private static void register(RegisterEvent event) {
        for (KRegistry<?> catalogue : KRegistry.all()) {
            registerAll(event, catalogue);
        }
    }

    private static <T> void registerAll(RegisterEvent event, KRegistry<T> catalogue) {
        if (!event.getRegistryKey().equals(catalogue.registryKey())) {
            return;
        }
        event.register(catalogue.registryKey(), helper -> {
            for (KRegistry.Entry<T, ? extends T> entry : catalogue.entries()) {
                helper.register(entry.key(), entry.create());
            }
        });
    }
}
