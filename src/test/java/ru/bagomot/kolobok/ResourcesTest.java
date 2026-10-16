package ru.bagomot.kolobok;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import ru.bagomot.kolobok.entity.song.Syllable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the assets the code relies on by name, so a missing file is a test failure rather than a
 * silent gap in game.
 */
class ResourcesTest {

    private static final Pattern SAY_KEY = Pattern.compile("kolobok\\.say\\.([a-z_]+)\\.(\\d)\\.(\\d+)");

    @Test
    void everySyllableIsSungAndMumbledFromAFile() throws IOException {
        JsonObject sounds = json("assets/kolobok/sounds.json");
        for (String name : Syllable.allSampleNames()) {
            assertTrue(sounds.has("sing." + name), "sing." + name);
            assertTrue(sounds.has("mumble." + name), "mumble." + name);
            try (InputStream ogg = resource("assets/kolobok/sounds/voice/" + name + ".ogg")) {
                assertNotNull(ogg, name + ".ogg");
            }
        }
    }

    @Test
    void speechCategoriesMatchAcrossLanguagesAndNumberWithoutGaps() throws IOException {
        Map<String, Set<String>> ru = categories(json("assets/kolobok/lang/ru_ru.json"));
        Map<String, Set<String>> en = categories(json("assets/kolobok/lang/en_us.json"));
        assertEquals(ru.keySet(), en.keySet(), "the same events must speak in both languages");
        for (Map.Entry<String, Set<String>> e : ru.entrySet()) {
            // Meme level 0 is always on, so every event needs a level-0 line to say anything at all.
            assertTrue(e.getValue().contains("0"), "no level-0 line in ru_ru for " + e.getKey());
            assertTrue(en.get(e.getKey()).contains("0"), "no level-0 line in en_us for " + e.getKey());
        }
    }

    /**
     * @return category → levels present; fails if line numbers within a level skip, since the client
     *         stops counting at the first missing number
     */
    private static Map<String, Set<String>> categories(JsonObject lang) {
        Map<String, Set<String>> levels = new TreeMap<>();
        Map<String, Integer> counts = new TreeMap<>();
        Map<String, Integer> maxIndex = new TreeMap<>();
        for (String key : lang.keySet()) {
            Matcher m = SAY_KEY.matcher(key);
            if (!m.matches()) {
                continue;
            }
            levels.computeIfAbsent(m.group(1), k -> new TreeSet<>()).add(m.group(2));
            String level = m.group(1) + "." + m.group(2);
            counts.merge(level, 1, Integer::sum);
            maxIndex.merge(level, Integer.parseInt(m.group(3)), Math::max);
        }
        for (String level : counts.keySet()) {
            assertEquals(counts.get(level) - 1, maxIndex.get(level), "gap in line numbers of " + level);
        }
        return levels;
    }

    private static JsonObject json(String path) throws IOException {
        try (InputStream in = resource(path)) {
            assertNotNull(in, path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static InputStream resource(String path) {
        return ResourcesTest.class.getClassLoader().getResourceAsStream(path);
    }
}
