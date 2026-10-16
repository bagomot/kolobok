package ru.bagomot.kolobok.entity.song;

import org.jspecify.annotations.Nullable;

/**
 * One line of the song.
 *
 * @param sung     the Russian words as they are sung, with an apostrophe after each stressed vowel
 * @param template the melody the line is set to
 * @param breath   whether a verse ends here; a quarter's breath follows
 * @param subtitle lang key of the line in the player's language
 * @param mob      id of the entity type the line names, the subtitle's argument; {@code null} for the
 *                 fixed lines
 */
public record SongLine(String sung, Melody.Template template, boolean breath, String subtitle, @Nullable String mob) {

    public SongLine(String sung, Melody.Template template, boolean breath, String subtitle) {
        this(sung, template, breath, subtitle, null);
    }
}
