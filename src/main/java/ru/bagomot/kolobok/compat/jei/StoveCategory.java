package ru.bagomot.kolobok.compat.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.FormattedText;
import ru.bagomot.kolobok.compat.Hints;
import ru.bagomot.kolobok.registry.KItems;

import java.util.ArrayList;
import java.util.List;

/**
 * «Русская печь»: a raw Kolobok in the горнило, logs in the подпечье, a Kolobok out, with the time
 * and the condition, if any, on the right. Laid out like JEI's furnace.
 */
public final class StoveCategory extends AbstractRecipeCategory<Hints.Baking> {

    private static final int WIDTH = 150;
    private static final int HEIGHT = 54;

    public StoveCategory(IGuiHelper gui) {
        super(KolobokJeiPlugin.STOVE, Hints.stoveName(), gui.createDrawableItemLike(KItems.RAW_KOLOBOK.get()), WIDTH, HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Hints.Baking recipe, IFocusGroup focuses) {
        builder.addInputSlot(1, 1).setStandardSlotBackground().addItemStacks(recipe.raw());
        builder.addInputSlot(1, 37).setStandardSlotBackground().addItemStacks(Hints.logs());
        builder.addOutputSlot(61, 19).setOutputSlotBackground().add(recipe.kolobok());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Hints.Baking recipe, IFocusGroup focuses) {
        builder.addAnimatedRecipeFlameWidget(300).setPosition(1, 20);
        builder.addAnimatedRecipeArrowWidget(200).setPosition(26, 18);
        List<FormattedText> notes = new ArrayList<>(recipe.notes());
        builder.addText(notes, WIDTH - 88, HEIGHT - 4).setPosition(88, 2).setColor(0xFF808080);
    }
}
