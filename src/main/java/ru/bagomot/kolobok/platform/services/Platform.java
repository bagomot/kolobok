package ru.bagomot.kolobok.platform.services;

import net.minecraft.world.item.CreativeModeTab;

import java.nio.file.Path;

/**
 * The few facts about the running loader that common code needs.
 *
 * <p>Kept small on purpose: every method here is implemented twice. Anything vanilla classes can
 * answer belongs in common code.
 */
public interface Platform {

    /**
     * @return a human-readable loader name for logs
     */
    String platformName();

    /**
     * @return the game's config directory; Kolobok keeps its files in {@code kolobok/} under it
     */
    Path configDirectory();

    /**
     * @return whether the game runs from a development environment
     */
    boolean isDevelopmentEnvironment();

    /**
     * @return whether a mod of that id is installed: the soft integrations ask before they act
     */
    boolean isModLoaded(String modId);

    /**
     * @return a builder for a mod's own creative tab. Vanilla's wants a fixed row and column, which
     *         both loaders replace with their own paging.
     */
    CreativeModeTab.Builder creativeTabBuilder();
}
