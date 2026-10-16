package ru.bagomot.kolobok.entity;

/**
 * What Kolobok is doing. Singing is not a state but an overlay on top of one.
 */
public enum KState {
    /** Fresh out of the stove, motionless on the hearth. */
    HOT,
    COOLING,
    /** Not tamed: rolls away and sings. */
    RUNAWAY,
    FOLLOW,
    SIT,
    /** Stands at a spot and keeps watch. */
    GUARD,
    WANDER,
    /** Lying on the stove after a "death", 5 minutes. */
    RESTING,
    /** Flattened for 60 s, invulnerable. */
    PANCAKE,
    /** In a fox's mouth. */
    CAUGHT,
    /** On the owner's shoulder or head; see {@link RideSlot}. */
    ON_PLAYER,
    ON_CHICKEN,
    BOWLING,
    GUIDING,
    /** Cosplaying the Head from «Руслан и Людмила». */
    HEAD_ACT;

    private static final KState[] VALUES = values();

    public static KState byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : RUNAWAY;
    }
}
