package ru.bagomot.kolobok.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import ru.bagomot.kolobok.item.RollDoughRecipe;

public final class KRecipes {

    public static final KRegistry<RecipeSerializer<?>> REGISTRY = new KRegistry<>(Registries.RECIPE_SERIALIZER);

    /** Dough and the rolling pin give a raw Kolobok; the rolling pin stays. */
    public static final KRegistry.Entry<RecipeSerializer<?>, RecipeSerializer<RollDoughRecipe>> ROLL_DOUGH = REGISTRY.register(
            "roll_dough",
            key -> new RecipeSerializer<>(RollDoughRecipe.MAP_CODEC, RollDoughRecipe.STREAM_CODEC));

    private KRecipes() {
    }

    /** Loads the class, which declares the entries. */
    public static void init() {
    }
}
