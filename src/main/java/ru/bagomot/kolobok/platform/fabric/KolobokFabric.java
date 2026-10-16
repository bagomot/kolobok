package ru.bagomot.kolobok.platform.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import ru.bagomot.kolobok.Kolobok;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.registry.KEntities;
import ru.bagomot.kolobok.registry.KRegistry;

/**
 * Fabric entry point. Mod initialisers run while the built-in registries are still open, so the
 * catalogues go straight in, in the game's own registry order.
 */
public final class KolobokFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        Kolobok.init(new FabricPlatform());
        for (KRegistry<?> catalogue : KRegistry.all()) {
            registerAll(catalogue);
        }
        FabricDefaultAttributeRegistry.register(KEntities.KOLOBOK.get(), KolobokEntity.createAttributes());
    }

    private static <T> void registerAll(KRegistry<T> catalogue) {
        @SuppressWarnings("unchecked")
        Registry<T> registry = (Registry<T>) BuiltInRegistries.REGISTRY.getValue(catalogue.registryKey().identifier());
        if (registry == null) {
            throw new IllegalStateException("No registry " + catalogue.registryKey());
        }
        for (KRegistry.Entry<T, ? extends T> entry : catalogue.entries()) {
            Registry.register(registry, entry.key(), entry.create());
        }
    }
}
