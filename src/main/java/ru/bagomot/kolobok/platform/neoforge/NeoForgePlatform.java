package ru.bagomot.kolobok.platform.neoforge;

import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import ru.bagomot.kolobok.platform.services.Platform;

import java.nio.file.Path;

public final class NeoForgePlatform implements Platform {

    @Override
    public String platformName() {
        return "NeoForge";
    }

    @Override
    public Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLEnvironment.isProduction();
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() {
        return CreativeModeTab.builder();
    }
}
