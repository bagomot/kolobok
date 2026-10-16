package ru.bagomot.kolobok.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Where on the owner Kolobok sits.
 *
 * <p>He does not ride the player: 26.3 refuses any vehicle whose entity type is not saved, and the
 * player type is not ({@code Entity.startRiding}); a player's own passengers are not saved either,
 * so a rider would vanish with the next server stop. Instead he stays an ordinary entity that the
 * server moves to the spot below every tick and the client draws there at the player's interpolated
 * position. Both sides call {@link #spot} so they agree on where that is.
 */
public enum RideSlot {
    NONE(0.0, 0.0),
    HEAD(0.0, 0.0),
    SHOULDER_LEFT(0.52, 0.0),
    SHOULDER_RIGHT(-0.52, 0.0);

    private static final RideSlot[] VALUES = values();

    /** Sideways from the player's centre, blocks; positive is the player's left. */
    private final double side;
    /** Forward from the player's centre, blocks. */
    private final double forward;

    RideSlot(double side, double forward) {
        this.side = side;
        this.forward = forward;
    }

    public static RideSlot byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }

    public boolean isShoulder() {
        return this == SHOULDER_LEFT || this == SHOULDER_RIGHT;
    }

    /**
     * @param carrier   the player
     * @param position  the player's position, interpolated on the client
     * @param bodyYaw   the player's body rotation in degrees, interpolated on the client
     * @return where Kolobok's feet go
     */
    public Vec3 spot(Player carrier, Vec3 position, float bodyYaw) {
        double height = carrier.getBbHeight();
        double y;
        if (this == HEAD) {
            // The player model stands about a pixel taller than its box.
            y = height + 0.09;
        } else {
            // The shoulder line of the player model sits at three quarters of the standing height;
            // crouching lowers the head but the shoulders follow the box.
            y = carrier.hasPose(Pose.CROUCHING) ? height * 0.70 : height * 0.775;
        }
        float rad = bodyYaw * Mth.DEG_TO_RAD;
        double sin = Mth.sin(rad);
        double cos = Mth.cos(rad);
        // Minecraft's yaw: forward is (-sin, cos), the body's left is (cos, sin).
        double dx = -sin * forward + cos * side;
        double dz = cos * forward + sin * side;
        return position.add(dx, y, dz);
    }
}
