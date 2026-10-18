package ru.bagomot.kolobok.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.FormattedCharSequence;
import ru.bagomot.kolobok.entity.Variant;

import java.util.List;

public final class KolobokRenderState extends LivingEntityRenderState {
    public Variant variant = Variant.CLASSIC;
    /** Index of the face plate to show; see {@link KolobokModel}. */
    public int face;
    /** Rolling angle of the body, radians. */
    public float roll;
    public boolean sitting;
    /** Burnt in the oven: drawn with a darker crust. */
    public boolean burnt;
    /** «Легенда»: got away from the dragon, drawn with a golden crust. */
    public boolean golden;
    /** Three days without milk: drawn duller. */
    public boolean stale;
    /** Drawn at a player's shoulder or head rather than where the entity stands. */
    public boolean onPlayer;
    /** On the camera player's own head in first person: drawn not at all, like a shoulder parrot. */
    public boolean hidden;
    /** Speech bubble lines, already wrapped; empty when he is quiet. */
    public List<FormattedCharSequence> bubble = List.of();
    public int bubbleWidth;
}
