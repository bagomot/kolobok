package ru.bagomot.kolobok.compat.rei;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.plugin.common.displays.DefaultInformationDisplay;
import me.shedaniel.rei.plugin.common.displays.crafting.DefaultCustomShapelessDisplay;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import ru.bagomot.kolobok.compat.Hints;
import ru.bagomot.kolobok.guide.GuideBook;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * REI, when installed: the same as for JEI, the stove's baking, the guide's recipe and a page of
 * hints on each item. Fabric finds the plugin by the {@code rei_client} entrypoint, NeoForge by the
 * annotated {@code KolobokReiNeoForge}.
 */
public class KolobokReiPlugin implements REIClientPlugin {

    @Override
    public void registerCategories(CategoryRegistry registry) {
        registry.add(new StoveDisplayCategory());
        for (ItemStack station : Hints.stations()) {
            registry.addWorkstations(StoveDisplay.CATEGORY, EntryStacks.of(station));
        }
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        for (Hints.Baking baking : Hints.bakings()) {
            registry.add(new StoveDisplay(baking));
        }
        List<EntryIngredient> inputs = new ArrayList<>();
        for (Item item : Hints.GUIDE_INPUTS) {
            inputs.add(EntryIngredients.of(item));
        }
        registry.add(new DefaultCustomShapelessDisplay(inputs, List.of(EntryIngredients.of(GuideBook.create())),
                Optional.of(Hints.GUIDE_RECIPE)));
        for (Hints.Info info : Hints.infos()) {
            registry.add(DefaultInformationDisplay.createFromEntries(EntryIngredients.ofItemStacks(info.stacks()),
                    info.stacks().getFirst().getHoverName()).lines(info.lines()));
        }
    }
}
