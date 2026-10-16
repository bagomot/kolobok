package ru.bagomot.kolobok.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import ru.bagomot.kolobok.Kolobok;

public final class KTabs {

    public static final KRegistry<CreativeModeTab> REGISTRY = new KRegistry<>(Registries.CREATIVE_MODE_TAB);

    /** «Сказка о Колобке». */
    public static final KRegistry.Entry<CreativeModeTab, CreativeModeTab> KOLOBOK = REGISTRY.register(
            "kolobok",
            key -> Kolobok.platform().creativeTabBuilder()
                    .title(Component.translatable("itemGroup.kolobok"))
                    .icon(() -> new ItemStack(KItems.KOLOBOK_SPAWN_EGG.get()))
                    .displayItems((parameters, output) -> {
                        for (KRegistry.Entry<Item, ? extends Item> item : KItems.REGISTRY.entries()) {
                            output.accept(item.get());
                        }
                    })
                    .build());

    private KTabs() {
    }

    /** Loads the class, which declares the entries. */
    public static void init() {
    }
}
