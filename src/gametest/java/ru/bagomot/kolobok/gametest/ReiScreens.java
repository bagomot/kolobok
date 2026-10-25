package ru.bagomot.kolobok.gametest;

import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.plugin.common.BuiltinPlugin;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import ru.bagomot.kolobok.compat.rei.StoveDisplay;
import ru.bagomot.kolobok.guide.GuideBook;
import ru.bagomot.kolobok.registry.KItems;

/**
 * REI's screens, apart from {@link GuideVisualTest} so that its class loads without REI: the stove's
 * recipes, the guide's recipe and the rolling pin's hint page.
 */
final class ReiScreens {

    private ReiScreens() {
    }

    static void show(ClientGameTestContext context) {
        context.runOnClient(mc -> ViewSearchBuilder.builder().addCategory(StoveDisplay.CATEGORY).open());
        context.waitTicks(10);
        context.takeScreenshot("guide-rei-01-stove");
        context.runOnClient(mc -> ViewSearchBuilder.builder().addRecipesFor(EntryStacks.of(GuideBook.create())).open());
        context.waitTicks(10);
        context.takeScreenshot("guide-rei-02-guide-recipe");
        context.runOnClient(mc -> ViewSearchBuilder.builder().addRecipesFor(EntryStacks.of(KItems.ROLLING_PIN.get()))
                .setPreferredOpenedCategory(BuiltinPlugin.INFO).open());
        context.waitTicks(10);
        context.takeScreenshot("guide-rei-03-hints");
        context.setScreen(() -> null);
    }
}
