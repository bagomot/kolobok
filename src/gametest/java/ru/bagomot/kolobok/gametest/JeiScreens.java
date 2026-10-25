package ru.bagomot.kolobok.gametest;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.item.ItemStack;
import ru.bagomot.kolobok.compat.jei.KolobokJeiPlugin;
import ru.bagomot.kolobok.guide.GuideBook;
import ru.bagomot.kolobok.registry.KItems;

import java.util.List;

/**
 * JEI's screens, apart from {@link GuideVisualTest} so that its class loads without JEI: the stove's
 * recipes, the guide's recipe and the rolling pin's hint page.
 */
final class JeiScreens {

    private JeiScreens() {
    }

    static void show(ClientGameTestContext context) {
        IJeiRuntime runtime = context.computeOnClient(mc -> JeiRuntime.get());
        if (runtime == null) {
            throw new AssertionError("JEI is installed but gave no runtime");
        }
        IFocusFactory focus = runtime.getJeiHelpers().getFocusFactory();
        context.runOnClient(mc -> runtime.getRecipesGui().showTypes(List.of(KolobokJeiPlugin.STOVE)));
        context.waitTicks(10);
        context.takeScreenshot("guide-jei-01-stove");
        context.runOnClient(mc -> runtime.getRecipesGui().show(
                focus.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, GuideBook.create())));
        context.waitTicks(10);
        context.takeScreenshot("guide-jei-02-guide-recipe");
        // The rolling pin's hint page alone, as JEI opens it for the pin.
        context.runOnClient(mc -> {
            List<IFocus<?>> pin = List.of(focus.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK,
                    new ItemStack(KItems.ROLLING_PIN.get())));
            IRecipeManager recipes = runtime.getRecipeManager();
            runtime.getRecipesGui().showRecipes(recipes.getRecipeCategory(RecipeTypes.INFORMATION),
                    recipes.createRecipeLookup(RecipeTypes.INFORMATION).limitFocus(pin).get().toList(), pin);
        });
        context.waitTicks(10);
        context.takeScreenshot("guide-jei-03-hints");
        context.setScreen(() -> null);
    }

    /** The stove's category for the README, see {@link ShowcaseVisualTest}. */
    static void showcase(ClientGameTestContext context) {
        IJeiRuntime runtime = context.computeOnClient(mc -> JeiRuntime.get());
        if (runtime == null) {
            throw new AssertionError("JEI is installed but gave no runtime");
        }
        context.runOnClient(mc -> runtime.getRecipesGui().showTypes(List.of(KolobokJeiPlugin.STOVE)));
        context.waitTicks(10);
        ShowcaseVisualTest.shot(context, "jei");
        context.setScreen(() -> null);
    }
}
