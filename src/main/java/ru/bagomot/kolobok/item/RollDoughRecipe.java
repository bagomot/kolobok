package ru.bagomot.kolobok.item;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.item.crafting.TransmuteResult;
import ru.bagomot.kolobok.registry.KRecipes;

/**
 * Dough and the rolling pin give a raw Kolobok: the vanilla transmute recipe, so the result keeps
 * the dough's components (what it is, a name given on the anvil) and the recipe book shows it, but
 * the rolling pin stays in the grid as it was, wear and enchantments included.
 *
 * <p>A recipe of its own rather than a crafting remainder on the rolling pin: a remainder would also
 * come back from vanilla's repair recipe, which combines two damaged rolling pins into one.
 */
public final class RollDoughRecipe extends TransmuteRecipe {

    public static final MapCodec<RollDoughRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    Recipe.CommonInfo.MAP_CODEC.forGetter(r -> r.commonInfo),
                    CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(r -> r.bookInfo),
                    Ingredient.CODEC.fieldOf("input").forGetter(r -> r.dough),
                    Ingredient.CODEC.fieldOf("material").forGetter(r -> r.pin),
                    TransmuteResult.CODEC.fieldOf("result").forGetter(r -> r.rolled))
            .apply(i, RollDoughRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, RollDoughRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, r -> r.commonInfo,
            CraftingRecipe.CraftingBookInfo.STREAM_CODEC, r -> r.bookInfo,
            Ingredient.CONTENTS_STREAM_CODEC, r -> r.dough,
            Ingredient.CONTENTS_STREAM_CODEC, r -> r.pin,
            TransmuteResult.STREAM_CODEC, r -> r.rolled,
            RollDoughRecipe::new);

    // TransmuteRecipe keeps its input, material and result to itself, and the codecs need them back.
    // The common and book info are NormalCraftingRecipe's, open to subclasses.
    private final Ingredient dough;
    private final Ingredient pin;
    private final TransmuteResult rolled;

    public RollDoughRecipe(Recipe.CommonInfo info, CraftingRecipe.CraftingBookInfo book, Ingredient dough,
                           Ingredient pin, TransmuteResult rolled) {
        super(info, book, dough, pin, MinMaxBounds.Ints.exactly(1), rolled, false);
        this.dough = dough;
        this.pin = pin;
        this.rolled = rolled;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = CraftingRecipe.defaultCraftingReminder(input);
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (!stack.isEmpty() && pin.test(stack) && !dough.test(stack)) {
                remaining.set(slot, stack.copyWithCount(1));
            }
        }
        return remaining;
    }

    @Override
    public RecipeSerializer<TransmuteRecipe> getSerializer() {
        @SuppressWarnings("unchecked")
        RecipeSerializer<TransmuteRecipe> serializer = (RecipeSerializer<TransmuteRecipe>) (RecipeSerializer<?>) KRecipes.ROLL_DOUGH.get();
        return serializer;
    }
}
