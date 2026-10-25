package ru.bagomot.kolobok.compat.rei;

import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;
import ru.bagomot.kolobok.compat.Hints;
import ru.bagomot.kolobok.registry.KItems;

import java.util.ArrayList;
import java.util.List;

/**
 * «Русская печь»: a raw Kolobok over the fire, logs under it, a Kolobok out, with the time and the
 * condition, if any, on the right. Laid out like REI's furnace.
 */
public final class StoveDisplayCategory implements DisplayCategory<StoveDisplay> {

    private static final int HEIGHT = 64;
    private static final int WIDTH = 150;

    @Override
    public CategoryIdentifier<? extends StoveDisplay> getCategoryIdentifier() {
        return StoveDisplay.CATEGORY;
    }

    @Override
    public Component getTitle() {
        return Hints.stoveName();
    }

    @Override
    public Renderer getIcon() {
        return EntryStacks.of(KItems.RAW_KOLOBOK.get());
    }

    @Override
    public List<Widget> setupDisplay(StoveDisplay display, Rectangle bounds) {
        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));
        int x = bounds.x + 8;
        int y = bounds.y + 5;
        widgets.add(Widgets.createSlot(new Point(x, y)).entries(display.getInputEntries().get(0)).markInput());
        widgets.add(Widgets.createBurningFire(new Point(x + 1, y + 20)).animationDurationTicks(300));
        widgets.add(Widgets.createSlot(new Point(x, y + 36)).entries(display.getInputEntries().get(1)).markInput());
        widgets.add(Widgets.createArrow(new Point(x + 24, y + 18)).animationDurationTicks(200));
        widgets.add(Widgets.createResultSlotBackground(new Point(x + 61, y + 19)));
        widgets.add(Widgets.createSlot(new Point(x + 61, y + 19)).entries(display.getOutputEntries().getFirst())
                .disableBackground().markOutput());
        int line = 0;
        for (Component note : display.baking().notes()) {
            widgets.add(Widgets.createLabel(new Point(x + 86, y + 2 + line * 10), note)
                    .leftAligned().noShadow().color(0xFF404040, 0xFFBBBBBB));
            line++;
        }
        return widgets;
    }

    @Override
    public int getDisplayHeight() {
        return HEIGHT;
    }

    @Override
    public int getDisplayWidth(StoveDisplay display) {
        return WIDTH;
    }
}
