package ru.bagomot.kolobok.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import ru.bagomot.kolobok.item.RollingPinItem;
import ru.bagomot.kolobok.item.SongbookItem;

public final class KItems {

    public static final KRegistry<Item> REGISTRY = new KRegistry<>(Registries.ITEM);

    /** Бабушкина скалка. */
    public static final KRegistry.Entry<Item, RollingPinItem> ROLLING_PIN = REGISTRY.register(
            "rolling_pin",
            key -> new RollingPinItem(RollingPinItem.properties().setId(key)));

    /** Песенник: book and wheat. */
    public static final KRegistry.Entry<Item, SongbookItem> SONGBOOK = REGISTRY.register(
            "songbook",
            key -> new SongbookItem(SongbookItem.properties().setId(key)));

    /** Creative tab only: there is no wild Kolobok. */
    public static final KRegistry.Entry<Item, SpawnEggItem> KOLOBOK_SPAWN_EGG = REGISTRY.register(
            "kolobok_spawn_egg",
            key -> new SpawnEggItem(new Item.Properties().spawnEgg(KEntities.KOLOBOK.get()).setId(key)));

    private KItems() {
    }

    /** Loads the class, which declares the entries. */
    public static void init() {
    }
}
