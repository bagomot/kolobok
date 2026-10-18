package ru.bagomot.kolobok.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A scripted jump from one point to another, placed a tick at a time: into the горнило and out of
 * it, or a cat up onto the stove and down again. A real jump would catch on the edge it clears.
 *
 * <p>Going up, the jumper rises first and moves over at the end, as a cat springs up and then steps
 * onto the ledge; going down is the same path backwards, over the edge first and then the drop. A
 * little lift on top makes it a hop rather than a slide.
 *
 * @param ticks how long it takes
 * @param lift  how far above the plain path it goes at its middle, in blocks
 */
public record Hop(Vec3 from, Vec3 to, int ticks, double lift) {

    /**
     * @return where the jumper is that many ticks in
     */
    public Vec3 at(int tick) {
        double t = Mth.clamp(tick / (double) ticks, 0.0, 1.0);
        boolean up = to.y >= from.y;
        // Down is up played backwards.
        double s = up ? t : 1.0 - t;
        Vec3 low = up ? from : to;
        Vec3 high = up ? to : from;
        double across = s * s * s;
        double rise = 1.0 - (1.0 - s) * (1.0 - s);
        return new Vec3(Mth.lerp(across, low.x, high.x),
                Mth.lerp(rise, low.y, high.y) + lift * Math.sin(Math.PI * s),
                Mth.lerp(across, low.z, high.z));
    }

    public boolean done(int tick) {
        return tick >= ticks;
    }

    /**
     * Puts the jumper where it should be on that tick, with no motion of its own.
     */
    public void place(Entity entity, int tick) {
        Vec3 pos = at(tick);
        entity.setPos(pos.x, pos.y, pos.z);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.resetFallDistance();
    }
}
