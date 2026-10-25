package ru.bagomot.kolobok.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;
import ru.bagomot.kolobok.entity.Outfit;
import ru.bagomot.kolobok.entity.Variant;

import java.util.List;

public final class KolobokRenderState extends LivingEntityRenderState {
    public Variant variant = Variant.CLASSIC;
    /** Index of the face plate to show; see {@link KolobokModel}. */
    public int face;
    /** Rolling angle of the body, radians. */
    public float roll;
    /** Stretched up (above 0) or squashed (below 0) by a jump or a landing. */
    public float squash;
    /** Pixels the hat lags below (above 0) or above (below 0) the top of his head. */
    public float hatLag;
    public boolean sitting;
    /** Burnt in the oven: drawn with a darker crust. */
    public boolean burnt;
    /** «Легенда»: got away from the dragon, drawn with a golden crust. */
    public boolean golden;
    /** Three days without milk: drawn duller. */
    public boolean stale;
    /** A tan from the fire: a darker crust for five minutes. */
    public boolean tan;
    /** Soggy: paler, swollen with water. */
    public boolean soggy;
    /** Bitten: a bite out of his crust until the morning. */
    public boolean bitten;
    /** How puffed out his cheeks are, 0–3. */
    public int cheeks;
    /** A jukebox plays close by. */
    public boolean dancing;
    /** Drawn at a player's shoulder or head rather than where the entity stands. */
    public boolean onPlayer;
    /** On the camera player's own head in first person: drawn not at all, like a shoulder parrot. */
    public boolean hidden;
    /** What is on his head. His own hats are drawn from {@code headItem}. */
    public Outfit.Hat hat = Outfit.Hat.NONE;
    /** The шелом's metal; the бармица only on a chainmail one. */
    public @Nullable Identifier helmetTexture;
    public boolean aventail;
    /** The ушанка's fur colour, the leather helmet's dye. */
    public int hatColour = -1;
    /** The лапти's texture, or {@code null} without boots or as the Head; dyed ones are tinted. */
    public @Nullable Identifier lapti;
    public int laptiColour = -1;
    public boolean glasses;
    /** The Head from «Руслан и Людмила»: he grows to 1.75 over a second. */
    public float headScale = 1.0F;
    /** Speech bubble lines, already wrapped; empty when he is quiet. */
    public List<FormattedCharSequence> bubble = List.of();
    public int bubbleWidth;
}
