package ru.bagomot.kolobok.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import ru.bagomot.kolobok.entity.song.SongLine;
import ru.bagomot.kolobok.entity.song.SongText;

/**
 * The song's words as the player reads them: the bubble, the songbook, the chat. All of it is
 * translatable and resolved on the client, so the server never needs the mod's language files.
 * The song itself is Russian everywhere.
 */
public final class SongWords {

    private SongWords() {
    }

    /**
     * @return the line in the reader's language
     */
    public static Component line(SongLine line) {
        if (line.mob() == null) {
            return Component.translatable(line.subtitle());
        }
        return Component.translatable(line.subtitle(), mob(line.mob()));
    }

    /**
     * @return the mob as the song names it, in the genitive («крипера»); a mob the song does not
     *         know, by its own name
     */
    public static Component mob(String id) {
        if (SongText.knows(id)) {
            return Component.translatable("kolobok.verse." + id.replace(':', '.'));
        }
        Identifier key = Identifier.tryParse(id);
        if (key == null) {
            return Component.literal(id);
        }
        return BuiltInRegistries.ENTITY_TYPE.getOptional(key).map(EntityType::getDescription)
                .orElse(Component.literal(key.getPath()));
    }
}
