package ru.bagomot.kolobok;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import ru.bagomot.kolobok.block.StovePart;
import ru.bagomot.kolobok.entity.Outfit;
import ru.bagomot.kolobok.entity.Perk;
import ru.bagomot.kolobok.entity.Variant;
import ru.bagomot.kolobok.entity.song.SongLine;
import ru.bagomot.kolobok.entity.song.SongText;
import ru.bagomot.kolobok.entity.song.Syllable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

    @Test
    void theStoveHasAModelForEveryStateAndEveryTextureItNames() throws IOException {
        JsonObject variants = json("assets/kolobok/blockstates/stove_part.json").getAsJsonObject("variants");
        int states = 0;
        for (String facing : List.of("north", "east", "south", "west")) {
            for (boolean lit : new boolean[]{false, true}) {
                for (StovePart part : StovePart.values()) {
                    String key = "facing=" + facing + ",lit=" + lit + ",part=" + part.getSerializedName();
                    assertTrue(variants.has(key), key);
                    String model = variants.getAsJsonObject(key).get("model").getAsString();
                    JsonObject textures = json("assets/kolobok/models/" + model.substring("kolobok:".length()) + ".json")
                            .getAsJsonObject("textures");
                    for (String name : textures.keySet()) {
                        String texture = textures.get(name).getAsString();
                        if (texture.startsWith("kolobok:")) {
                            try (InputStream png = resource("assets/kolobok/textures/" + texture.substring(8) + ".png")) {
                                assertNotNull(png, texture);
                            }
                        }
                    }
                    states++;
                }
            }
        }
        assertEquals(states, variants.size(), "no stray variants");
    }

    @Test
    void everySongLineHasASubtitleInBothLanguages() throws IOException {
        JsonObject ru = json("assets/kolobok/lang/ru_ru.json");
        JsonObject en = json("assets/kolobok/lang/en_us.json");
        List<String> mobs = List.of("minecraft:rabbit", "minecraft:wolf", "minecraft:creeper");
        for (SongLine line : SongText.lines(1, mobs, null, true)) {
            assertTrue(ru.has(line.subtitle()) && en.has(line.subtitle()), line.subtitle());
            if (line.mob() != null) {
                String verse = "kolobok.verse." + line.mob().replace(':', '.');
                assertTrue(ru.has(verse) && en.has(verse), verse);
            }
        }
        for (String key : ru.keySet()) {
            if (key.startsWith("kolobok.verse.") || key.startsWith("kolobok.song.") || key.startsWith("kolobok.stove.")) {
                assertTrue(en.has(key), "en_us lacks " + key);
            }
        }
    }

    @Test
    void everyMobTheSongKnowsAndEveryPerkIsNamedInBothLanguages() throws IOException {
        JsonObject ru = json("assets/kolobok/lang/ru_ru.json");
        JsonObject en = json("assets/kolobok/lang/en_us.json");
        for (String mob : SongText.knownMobs()) {
            String key = "kolobok.verse." + mob.replace(':', '.');
            assertTrue(ru.has(key) && en.has(key), key);
        }
        for (Perk perk : Perk.values()) {
            // The fox is never sung about: its perk comes of being rescued from one.
            assertEquals(perk != Perk.FOX, SongText.knows(perk.mob()), "a perk the song cannot sing about: " + perk);
            assertEquals(perk, Perk.forMob(perk.mob()));
            for (String key : List.of(perk.key(), perk.key() + ".desc")) {
                assertTrue(ru.has(key) && en.has(key), key);
            }
        }
        for (Variant variant : Variant.values()) {
            String key = "kolobok.variant." + variant.serializedName();
            assertTrue(ru.has(key) && en.has(key), key);
        }
        for (String key : ru.keySet()) {
            if (key.startsWith("kolobok.perk.") || key.startsWith("kolobok.songbook.") || key.startsWith("kolobok.variant.")) {
                assertTrue(en.has(key), "en_us lacks " + key);
            }
        }
    }

    @Test
    void theSongbookHasItsModelTextureAndRecipe() throws IOException {
        json("assets/kolobok/items/songbook.json");
        json("assets/kolobok/models/item/songbook.json");
        try (InputStream png = resource("assets/kolobok/textures/item/songbook.png")) {
            assertNotNull(png);
        }
        json("data/kolobok/recipe/songbook.json");
        json("data/kolobok/advancement/recipes/misc/songbook.json");
    }

    @Test
    void doughAndTheRawKolobokHaveModelsRecipesAndANameForEveryDough() throws IOException {
        JsonObject ru = json("assets/kolobok/lang/ru_ru.json");
        JsonObject en = json("assets/kolobok/lang/en_us.json");
        for (String item : List.of("dough", "raw_kolobok")) {
            json("assets/kolobok/items/" + item + ".json");
            JsonObject model = json("assets/kolobok/models/item/" + item + ".json");
            String texture = model.getAsJsonObject("textures").get("layer0").getAsString();
            try (InputStream png = resource("assets/kolobok/textures/" + texture.substring("kolobok:".length()) + ".png")) {
                assertNotNull(png, texture);
            }
            json("data/kolobok/recipe/" + item + ".json");
            json("data/kolobok/advancement/recipes/misc/" + item + ".json");
            assertTrue(ru.has("item.kolobok." + item) && en.has("item.kolobok." + item), item);
            for (Variant variant : Variant.values()) {
                String key = "item.kolobok." + item + "." + variant.serializedName();
                assertTrue(variant == Variant.CLASSIC || ru.has(key) && en.has(key), key);
            }
        }
        for (String dough : List.of("mini", "borodinsky", "golden")) {
            json("data/kolobok/recipe/dough_" + dough + ".json");
            json("data/kolobok/advancement/recipes/misc/dough_" + dough + ".json");
        }
        try (InputStream png = resource("assets/kolobok/textures/entity/kolobok/dough.png")) {
            assertNotNull(png, "the raw Kolobok in the горнило");
        }
    }

    @Test
    void foxesAreAmongWhatScaresKolobok() throws IOException {
        var values = json("data/kolobok/tags/entity_type/scares_kolobok.json").getAsJsonArray("values");
        assertTrue(values.contains(new JsonPrimitive("minecraft:fox")));
    }

    @Test
    void theRollingPinHasItsModelAndTexture() throws IOException {
        json("assets/kolobok/items/rolling_pin.json");
        json("assets/kolobok/models/item/rolling_pin.json");
        try (InputStream png = resource("assets/kolobok/textures/item/rolling_pin.png")) {
            assertNotNull(png);
        }
        json("data/kolobok/recipe/rolling_pin.json");
        json("data/kolobok/loot_table/blocks/stove_part.json");
    }

    @Test
    void theHatsHaveModelsTexturesRecipesAndNames() throws IOException {
        JsonObject ru = json("assets/kolobok/lang/ru_ru.json");
        JsonObject en = json("assets/kolobok/lang/en_us.json");
        for (String hat : List.of("crown", "wreath", "kokoshnik")) {
            json("assets/kolobok/items/" + hat + ".json");
            json("data/kolobok/recipe/" + hat + ".json");
            json("data/kolobok/advancement/recipes/combat/" + hat + ".json");
            assertTrue(ru.has("item.kolobok." + hat) && en.has("item.kolobok." + hat), hat);
        }
        for (String model : List.of("crown", "wreath", "wreath_dyed", "kokoshnik")) {
            JsonObject textures = json("assets/kolobok/models/item/" + model + ".json").getAsJsonObject("textures");
            for (String name : textures.keySet()) {
                String texture = textures.get(name).getAsString();
                try (InputStream png = resource("assets/kolobok/textures/" + texture.substring("kolobok:".length()) + ".png")) {
                    assertNotNull(png, texture);
                }
            }
        }
        // Dyed like leather, and washed in a cauldron like it.
        var washable = json("data/minecraft/tags/item/cauldron_can_remove_dye.json").getAsJsonArray("values");
        for (String hat : List.of("wreath", "kokoshnik")) {
            assertEquals("minecraft:crafting_dye", json("data/kolobok/recipe/" + hat + "_dyed.json").get("type").getAsString());
            json("data/kolobok/advancement/recipes/misc/" + hat + "_dyed.json");
            assertTrue(washable.contains(new JsonPrimitive("kolobok:" + hat)), hat);
        }
        for (String key : List.of("item.kolobok.kladenets", "item.kolobok.kladenets.lore")) {
            assertTrue(ru.has(key) && en.has(key), key);
        }
    }

    @Test
    void everyOutfitHeWearsHasItsTexture() throws IOException {
        for (Outfit.Material material : Outfit.Material.values()) {
            // A leather helmet is an ушанка, never a шелом.
            if (material != Outfit.Material.LEATHER) {
                texture("entity/kolobok/outfit/helmet_" + material.serializedName());
            }
            texture("entity/kolobok/outfit/lapti_" + material.serializedName());
        }
        for (String name : List.of("ushanka", "lapti_dyed", "glasses")) {
            texture("entity/kolobok/outfit/" + name);
        }
    }

    @Test
    void theHeadSnoresAndSneezesFromFiles() throws IOException {
        JsonObject sounds = json("assets/kolobok/sounds.json");
        JsonObject ru = json("assets/kolobok/lang/ru_ru.json");
        JsonObject en = json("assets/kolobok/lang/en_us.json");
        for (String name : List.of("snore", "sneeze")) {
            assertTrue(sounds.has(name), name);
            String subtitle = sounds.getAsJsonObject(name).get("subtitle").getAsString();
            assertTrue(ru.has(subtitle) && en.has(subtitle), subtitle);
            try (InputStream ogg = resource("assets/kolobok/sounds/voice/" + name + ".ogg")) {
                assertNotNull(ogg, name + ".ogg");
            }
        }
    }

    private static void texture(String path) throws IOException {
        try (InputStream png = resource("assets/kolobok/textures/" + path + ".png")) {
            assertNotNull(png, path);
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
