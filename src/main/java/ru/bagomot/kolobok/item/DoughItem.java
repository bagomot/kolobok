package ru.bagomot.kolobok.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import ru.bagomot.kolobok.entity.Variant;
import ru.bagomot.kolobok.registry.KComponents;

/**
 * Тесто and the сырой колобок rolled from it. Both are made at the crafting table: wheat, an egg and
 * «сметанка» (a bucket of milk) give the dough, cocoa or gold makes it borodinsky or golden, and a
 * single wheat only stretches to Колобочек. The dough and the rolling pin give the raw Kolobok, which
 * goes into the горнило by hand. What the dough is lives in {@link KComponents#DOUGH} and only
 * changes the name.
 */
public class DoughItem extends Item {

    public DoughItem(Item.Properties properties) {
        super(properties);
    }

    public static Variant variant(ItemStack stack) {
        return stack.getOrDefault(KComponents.DOUGH.get(), Variant.CLASSIC);
    }

    /**
     * @return dough or a raw Kolobok of that variant; the classic one without the component, as crafted
     */
    public static ItemStack of(Item item, Variant variant) {
        ItemStack stack = new ItemStack(item);
        if (variant != Variant.CLASSIC) {
            stack.set(KComponents.DOUGH.get(), variant);
        }
        return stack;
    }

    /**
     * «Бородинское тесто», «Сырой Колобочек»: {@code item.kolobok.<id>.<variant>} for every dough
     * but the classic one.
     */
    @Override
    public Component getName(ItemStack stack) {
        Variant variant = variant(stack);
        return variant == Variant.CLASSIC
                ? super.getName(stack)
                : Component.translatable(getDescriptionId() + "." + variant.serializedName());
    }
}
