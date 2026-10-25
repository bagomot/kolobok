package ru.bagomot.kolobok.guide;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import ru.bagomot.kolobok.registry.KRecipes;

/**
 * A book and a brick make the guide, «Как печь Колобка». A recipe of its own because what comes out
 * depends on whether Modonomicon is installed: its book or a written one.
 */
public final class GuideBookRecipe extends CustomRecipe {

    public static final GuideBookRecipe INSTANCE = new GuideBookRecipe();
    public static final MapCodec<GuideBookRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, GuideBookRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    private GuideBookRecipe() {
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return input.ingredientCount() == 2 && count(input, Items.BOOK) == 1 && count(input, Items.BRICK) == 1;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return GuideBook.create();
    }

    private static int count(CraftingInput input, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int slot = 0; slot < input.size(); slot++) {
            if (input.getItem(slot).is(item)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public RecipeSerializer<GuideBookRecipe> getSerializer() {
        return KRecipes.GUIDE_BOOK.get();
    }
}
