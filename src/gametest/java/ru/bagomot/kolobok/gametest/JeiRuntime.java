package ru.bagomot.kolobok.gametest;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** A JEI plugin of the gametests' own, only to get hold of JEI's runtime and open its screens. */
public final class JeiRuntime implements IModPlugin {

    private static volatile @Nullable IJeiRuntime runtime;

    static @Nullable IJeiRuntime get() {
        return runtime;
    }

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath("kolobok-gametest", "jei");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }
}
