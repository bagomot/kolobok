package ru.bagomot.kolobok.compat.rei;

import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;
import me.shedaniel.rei.api.common.plugins.REICommonPlugin;
import ru.bagomot.kolobok.item.DoughItem;
import ru.bagomot.kolobok.registry.KItems;

/**
 * REI's common half: every dough and raw Kolobok told apart by what it is, by the
 * {@code kolobok:dough} component, as JEI tells them apart. Without it REI takes them all for one
 * item, and the golden raw Kolobok's recipes list all five bakes. Fabric finds the plugin by the
 * {@code rei_common} entrypoint, NeoForge by the annotated {@code KolobokReiCommonNeoForge}.
 */
public class KolobokReiCommonPlugin implements REICommonPlugin {

    @Override
    public void registerItemComparators(ItemComparatorRegistry registry) {
        // Without the component it is classic, as DoughItem reads it.
        registry.register((context, stack) -> DoughItem.variant(stack).ordinal(), KItems.DOUGH.get(),
                KItems.RAW_KOLOBOK.get());
    }
}
