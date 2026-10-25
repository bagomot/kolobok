package ru.bagomot.kolobok.platform.neoforge;

import me.shedaniel.rei.forge.REIPluginClient;
import ru.bagomot.kolobok.compat.rei.KolobokReiPlugin;

/** REI on NeoForge finds its plugins by this annotation, which only its NeoForge jar has. */
@REIPluginClient
public final class KolobokReiNeoForge extends KolobokReiPlugin {
}
