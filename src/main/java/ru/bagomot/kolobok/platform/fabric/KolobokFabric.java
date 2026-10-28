package ru.bagomot.kolobok.platform.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import ru.bagomot.kolobok.Kolobok;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.event.KolobokEvents;
import ru.bagomot.kolobok.registry.KEntities;
import ru.bagomot.kolobok.registry.KRegistry;

/**
 * Fabric entry point. Mod initialisers run while the built-in registries are still open, so the
 * catalogues go straight in, in the order they are declared, each after the ones it refers to (see
 * {@link KRegistry#all()}).
 */
public final class KolobokFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        Kolobok.init(new FabricPlatform());
        for (KRegistry<?> catalogue : KRegistry.all()) {
            registerAll(catalogue);
        }
        FabricDefaultAttributeRegistry.register(KEntities.KOLOBOK.get(), KolobokEntity.createAttributes());
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> KolobokEvents.onEntityLoad(entity));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> KolobokEvents.allowDamage(entity, source));
        // In creative too, unlike NeoForge's BlockDropsEvent; the handler leaves creative out.
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) ->
                KolobokEvents.onBlockBroken(level, player, pos, state));
        // Once the player is in the level, as NeoForge's PlayerLoggedInEvent; the connection's JOIN
        // may come before.
        ServerPlayerEvents.JOIN.register(KolobokEvents::onPlayerJoin);
        // Before the levels tick, as NeoForge's ServerTickEvent.Pre.
        ServerTickEvents.START_SERVER_TICK.register(KolobokEvents::onServerTickStart);
        ServerLifecycleEvents.SERVER_STOPPED.register(KolobokEvents::onServerStopped);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (hand == InteractionHand.MAIN_HAND) {
                KolobokEvents.onEntityInteract(player, entity);
            }
            return InteractionResult.PASS;
        });
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
