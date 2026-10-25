package ru.bagomot.kolobok.advancement;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * What happens in the tale, for {@link TaleTrigger}: one value per kind of moment an advancement can
 * wait for. A data pack names them in lower case, {@code "event": "stove_built"}.
 */
public enum Tale implements StringRepresentable {
    /** The rolling pin built a Russian stove. */
    STOVE_BUILT,
    /** A raw Kolobok went into the горнило: «в печку сажён». */
    INTO_THE_OVEN,
    /** He cooled by a window: «на окошке стужён». Goes to whoever put him in the oven. */
    BY_THE_WINDOW,
    /** Cooled, he hopped out and rolled away from whoever put him in the oven. */
    RAN_AWAY,
    /** He took milk, his «сметанка», from the player. */
    SOUR_CREAM,
    /** He climbed onto the player's head. */
    ON_HEAD,
    /** His owner put a helmet on him that makes a шелом. */
    HELMET,
    /** His owner put the crown on him. */
    CROWN,
    /** The Head, tickled, sneezed the player away. */
    SNEEZED_AWAY,
    /** The Head, hit, left the player the Меч-кладенец. */
    KLADENETS,
    /** He got away from a mob: {@code mob} is its id, {@code count} the verses in his song now. */
    VERSE,
    /** In the crown, with verses about the Wither and the dragon. */
    IMMORTAL,
    /** Berries got him out of a fox's mouth. Goes to whoever threw them, or else to his owner. */
    FOX_RESCUE,
    /** The rolling pin bowled him: {@code count} is how many he knocked over. */
    BOWLED,
    /** He jumped on a chicken. */
    CHICKEN_JOCKEY,
    /** Riding a chicken, he glided over lava and landed whole. */
    LAVA_CHICKEN,
    /** His hungry owner bit him. */
    BITE,
    /** Fire gave him a tan instead of a burn. */
    TAN,
    /** He was taken into a witch's hut. */
    WITCH_HUT,
    /** He sang his song through: {@code count} is how many animals stood listening. */
    CONCERT,
    /** «Тянут-потянут»: the dog, the cat and Kolobok pulled up the beetroot with the player. */
    REPKA;

    public static final Codec<Tale> CODEC = StringRepresentable.fromEnum(Tale::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
