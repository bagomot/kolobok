package ru.bagomot.kolobok.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.equipment.Equippable;
import ru.bagomot.kolobok.item.BindleItem;
import ru.bagomot.kolobok.item.DoughItem;
import ru.bagomot.kolobok.item.RollingPinItem;
import ru.bagomot.kolobok.item.SongbookItem;

public final class KItems {

    public static final KRegistry<Item> REGISTRY = new KRegistry<>(Registries.ITEM);

    /** Бабушкина скалка. */
    public static final KRegistry.Entry<Item, RollingPinItem> ROLLING_PIN = REGISTRY.register(
            "rolling_pin",
            key -> new RollingPinItem(RollingPinItem.properties().setId(key)));

    /** Тесто: wheat, an egg and «сметанка», kneaded at the crafting table. */
    public static final KRegistry.Entry<Item, DoughItem> DOUGH = REGISTRY.register(
            "dough",
            key -> new DoughItem(new Item.Properties().stacksTo(16).setId(key)));

    /** Сырой колобок: the dough rolled with the rolling pin, ready for the горнило. */
    public static final KRegistry.Entry<Item, DoughItem> RAW_KOLOBOK = REGISTRY.register(
            "raw_kolobok",
            key -> new DoughItem(new Item.Properties().stacksTo(1).setId(key)));

    /** Песенник: book and wheat. */
    public static final KRegistry.Entry<Item, SongbookItem> SONGBOOK = REGISTRY.register(
            "songbook",
            key -> new SongbookItem(SongbookItem.properties().setId(key)));

    /** Корона Царь-Колобка: five gold ingots and an emerald. A little big for him, which he is proud of. */
    public static final KRegistry.Entry<Item, Item> CROWN = REGISTRY.register(
            "crown",
            key -> new Item(hat(SoundEvents.ARMOR_EQUIP_GOLD).rarity(Rarity.UNCOMMON).setId(key)));

    /** Венок: four flowers. Dyed, its flowers are all of the dye's colour. */
    public static final KRegistry.Entry<Item, Item> WREATH = REGISTRY.register(
            "wreath",
            key -> new Item(hat(SoundEvents.ARMOR_EQUIP_GENERIC).setId(key)));

    /** Кокошник: wool, a gold ingot and an amethyst shard. Red unless dyed. */
    public static final KRegistry.Entry<Item, Item> KOKOSHNIK = REGISTRY.register(
            "kokoshnik",
            key -> new Item(hat(SoundEvents.ARMOR_EQUIP_LEATHER).setId(key)));

    /** Узелок: a stick and three wool. Carries a Kolobok, through portals too. */
    public static final KRegistry.Entry<Item, BindleItem> BINDLE = REGISTRY.register(
            "bindle",
            key -> new BindleItem(BindleItem.properties().setId(key)));

    /** Creative tab only: there is no wild Kolobok. */
    public static final KRegistry.Entry<Item, SpawnEggItem> KOLOBOK_SPAWN_EGG = REGISTRY.register(
            "kolobok_spawn_egg",
            key -> new SpawnEggItem(new Item.Properties().spawnEgg(KEntities.KOLOBOK.get()).setId(key)));

    private KItems() {
    }

    /**
     * A hat for anyone's head, Kolobok's or a player's. It has no equipment asset, so the game draws
     * it on a head as its item model, the way it draws a carved pumpkin.
     */
    private static Item.Properties hat(Holder<SoundEvent> equipSound) {
        return new Item.Properties()
                .stacksTo(1)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD)
                        .setEquipSound(equipSound)
                        .setDamageOnHurt(false)
                        .build());
    }

    /** Loads the class, which declares the entries. */
    public static void init() {
    }
}
