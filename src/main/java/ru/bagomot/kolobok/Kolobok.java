package ru.bagomot.kolobok;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.bagomot.kolobok.config.KConfig;
import ru.bagomot.kolobok.platform.services.Platform;
import ru.bagomot.kolobok.registry.KBlockEntities;
import ru.bagomot.kolobok.registry.KBlocks;
import ru.bagomot.kolobok.registry.KComponents;
import ru.bagomot.kolobok.registry.KEntities;
import ru.bagomot.kolobok.registry.KItems;
import ru.bagomot.kolobok.registry.KRecipes;
import ru.bagomot.kolobok.registry.KSounds;
import ru.bagomot.kolobok.registry.KTabs;

/**
 * Common entry point. Each loader's entry class hands over its {@link Platform} and then feeds the
 * catalogues in {@code registry} to its own registration mechanism.
 */
public final class Kolobok {

    public static final String MOD_ID = "kolobok";
    public static final Logger LOG = LoggerFactory.getLogger("Kolobok");

    private static Platform platform;

    private Kolobok() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static Platform platform() {
        return platform;
    }

    /**
     * Fills the registry catalogues and loads the server config. Called once, before the loader
     * registers anything.
     */
    public static void init(Platform loader) {
        platform = loader;
        // Class loading order is registration order inside a catalogue; touching them here makes it
        // explicit instead of depending on who mentions which class first.
        KSounds.init();
        KBlocks.init();
        KBlockEntities.init();
        KEntities.init();
        KComponents.init();
        KItems.init();
        KRecipes.init();
        KTabs.init();
        KConfig.loadServer(loader.configDirectory());
        LOG.info("Kolobok on {}: жили-были", loader.platformName());
    }
}
