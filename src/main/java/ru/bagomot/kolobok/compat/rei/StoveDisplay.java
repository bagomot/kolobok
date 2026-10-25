package ru.bagomot.kolobok.compat.rei;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import org.jspecify.annotations.Nullable;
import ru.bagomot.kolobok.compat.Hints;

import java.util.List;

/** A raw Kolobok baked in the stove; made on the client only, so never sent and with no serializer. */
public final class StoveDisplay extends BasicDisplay {

    public static final CategoryIdentifier<StoveDisplay> CATEGORY = CategoryIdentifier.of(Hints.STOVE);

    private final Hints.Baking baking;

    public StoveDisplay(Hints.Baking baking) {
        super(List.of(EntryIngredients.ofItemStacks(baking.raw()), EntryIngredients.ofItemStacks(Hints.logs())),
                List.of(EntryIngredients.of(baking.kolobok())));
        this.baking = baking;
    }

    public Hints.Baking baking() {
        return baking;
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return CATEGORY;
    }

    @Override
    public @Nullable DisplaySerializer<? extends Display> getSerializer() {
        return null;
    }
}
