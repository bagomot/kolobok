package ru.bagomot.kolobok.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import ru.bagomot.kolobok.Kolobok;
import ru.bagomot.kolobok.compat.Hints;
import ru.bagomot.kolobok.registry.KComponents;
import ru.bagomot.kolobok.registry.KItems;

import java.util.List;

/**
 * JEI, when installed: the stove's baking as a category of its own, the guide's recipe, which JEI
 * cannot see in code, a page of hints on each item, and every dough told apart by what it is.
 */
@JeiPlugin
public final class KolobokJeiPlugin implements IModPlugin {

    public static final IRecipeType<Hints.Baking> STOVE = IRecipeType.create(Hints.STOVE, Hints.Baking.class);

    @Override
    public Identifier getPluginUid() {
        return Kolobok.id("jei");
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerFromDataComponentTypes(KItems.DOUGH.get(), KComponents.DOUGH.get());
        registration.registerFromDataComponentTypes(KItems.RAW_KOLOBOK.get(), KComponents.DOUGH.get());
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new StoveCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(STOVE, Hints.bakings());
        ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> guide = ResourceKey.create(Registries.RECIPE, Hints.GUIDE_RECIPE);
        registration.addRecipes(RecipeTypes.CRAFTING, List.<RecipeHolder<CraftingRecipe>>of(new RecipeHolder<>(guide, Hints.guideRecipe())));
        for (Hints.Info info : Hints.infos()) {
            registration.addItemStackInfo(info.stacks(), info.lines().toArray(Component[]::new));
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(STOVE, Hints.stations().toArray(ItemStack[]::new));
    }
}
