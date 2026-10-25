package ru.bagomot.kolobok;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import ru.bagomot.kolobok.advancement.Tale;
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
import java.nio.file.Files;
import java.nio.file.Path;
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

    /**
     * Every line the code has him say needs a level-0 line in both languages, or he says nothing at
     * all. The categories are read from the code itself: the arguments of {@code say}, and of the
     * calls that pass a line on to it.
     */
    @Test
    void everyLineTheCodeSaysHasTextInBothLanguages() throws IOException {
        String code = source("entity/KolobokEntity.java") + source("event/KolobokEvents.java");
        Set<String> said = new TreeSet<>();
        Matcher call = Pattern.compile("(?:say|endHeadAct|getOffStove)\\(([^;]*?)\\);", Pattern.DOTALL).matcher(code);
        while (call.find()) {
            Matcher word = Pattern.compile("\"([a-z_]+)\"").matcher(call.group(1));
            while (word.find()) {
                // A prefix the code completes ("dress_" and the hat) is checked by the hats' own test.
                if (!word.group(1).endsWith("_")) {
                    said.add(word.group(1));
                }
            }
        }
        // What he says on meeting a mob and on new лапти, these methods return; every word in them is a line.
        for (String picker : List.of("String meeting(", "String laptiLine(")) {
            int start = code.indexOf(picker);
            assertTrue(start >= 0, picker);
            Matcher word = Pattern.compile("\"([a-z_]+)\"").matcher(code.substring(start, code.indexOf("\n    }\n", start)));
            int found = 0;
            while (word.find()) {
                said.add(word.group(1));
                found++;
            }
            assertTrue(found >= 3, picker + " picks only " + found);
        }
        assertTrue(said.size() > 50, "found only " + said);
        Map<String, Set<String>> ru = categories(json("assets/kolobok/lang/ru_ru.json"));
        Map<String, Set<String>> en = categories(json("assets/kolobok/lang/en_us.json"));
        for (String category : said) {
            assertTrue(ru.containsKey(category) && ru.get(category).contains("0"), "no ru_ru line for " + category);
            assertTrue(en.containsKey(category) && en.get(category).contains("0"), "no en_us line for " + category);
        }
    }

    @Test
    void theBindleHasItsModelsTexturesRecipeAndNames() throws IOException {
        JsonObject definition = json("assets/kolobok/items/bindle.json");
        assertEquals("kolobok:stored_kolobok", definition.getAsJsonObject("model").get("component").getAsString());
        json("assets/kolobok/models/item/bindle.json");
        json("assets/kolobok/models/item/bindle_full.json");
        texture("item/bindle");
        texture("item/bindle_full");
        json("data/kolobok/recipe/bindle.json");
        json("data/kolobok/advancement/recipes/misc/bindle.json");
        for (String lang : List.of("ru_ru", "en_us")) {
            JsonObject names = json("assets/kolobok/lang/" + lang + ".json");
            for (Variant variant : Variant.values()) {
                assertTrue(names.has("item.kolobok.bindle.dough." + variant.serializedName()), lang + " " + variant);
            }
            assertTrue(names.has("item.kolobok.bindle.verses"), lang);
            assertTrue(names.has("item.kolobok.bindle.empty"), lang);
        }
    }

    @Test
    void theBiteAndTheChickenJockeyHaveTheirFiles() throws IOException {
        texture("entity/kolobok/bitten");
        JsonObject sounds = json("assets/kolobok/sounds.json");
        String subtitle = sounds.getAsJsonObject("chicken_jockey").get("subtitle").getAsString();
        assertTrue(json("assets/kolobok/lang/ru_ru.json").has(subtitle));
        assertTrue(json("assets/kolobok/lang/en_us.json").has(subtitle));
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
    void everySoundHasASubtitleInBothLanguagesAndItsOwnFilesExist() throws IOException {
        JsonObject sounds = json("assets/kolobok/sounds.json");
        JsonObject ru = json("assets/kolobok/lang/ru_ru.json");
        JsonObject en = json("assets/kolobok/lang/en_us.json");
        for (String name : List.of("sneeze_flour", "crunch", "crunch_stale", "boing", "splat")) {
            assertTrue(sounds.has(name), name);
        }
        for (Map.Entry<String, JsonElement> event : sounds.entrySet()) {
            JsonObject sound = event.getValue().getAsJsonObject();
            String subtitle = sound.get("subtitle").getAsString();
            assertTrue(ru.has(subtitle) && en.has(subtitle), event.getKey() + ": " + subtitle);
            for (JsonElement file : sound.getAsJsonArray("sounds")) {
                String id = file.isJsonObject() ? file.getAsJsonObject().get("name").getAsString() : file.getAsString();
                // Vanilla samples are the game's; ours must be in the jar.
                if (id.startsWith("kolobok:")) {
                    try (InputStream ogg = resource("assets/kolobok/sounds/" + id.substring("kolobok:".length()) + ".ogg")) {
                        assertNotNull(ogg, id);
                    }
                }
            }
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

    /**
     * «Сказка о Колобке»: one tab growing from one root, every advancement titled and described in
     * both languages, with an icon that exists, and every event of the tale both told by the code and
     * waited for by an advancement.
     */
    @Test
    void theTaleAdvancementsHangTogether() throws IOException {
        JsonObject ru = json("assets/kolobok/lang/ru_ru.json");
        JsonObject en = json("assets/kolobok/lang/en_us.json");
        Path dir = sourceDir("src/main/resources").resolve("data/kolobok/advancement/tale");
        Set<String> names = new TreeSet<>();
        try (var files = Files.list(dir)) {
            files.forEach(file -> names.add(file.getFileName().toString().replace(".json", "")));
        }
        assertTrue(names.size() >= 27, "found only " + names);
        Set<String> awaited = new TreeSet<>();
        int roots = 0;
        for (String name : names) {
            JsonObject advancement = json("data/kolobok/advancement/tale/" + name + ".json");
            if (advancement.has("parent")) {
                String parent = advancement.get("parent").getAsString();
                assertTrue(parent.startsWith("kolobok:tale/") && names.contains(parent.substring("kolobok:tale/".length())),
                        name + " hangs from " + parent);
            } else {
                roots++;
                assertTrue(advancement.getAsJsonObject("display").has("background"), "the root draws the tab");
            }
            JsonObject display = advancement.getAsJsonObject("display");
            for (String part : List.of("title", "description")) {
                String key = display.getAsJsonObject(part).get("translate").getAsString();
                assertTrue(ru.has(key) && en.has(key), key);
            }
            String icon = display.getAsJsonObject("icon").get("id").getAsString();
            if (icon.startsWith("kolobok:")) {
                json("assets/kolobok/items/" + icon.substring("kolobok:".length()) + ".json");
            }
            for (var criterion : advancement.getAsJsonObject("criteria").entrySet()) {
                JsonObject value = criterion.getValue().getAsJsonObject();
                if (value.get("trigger").getAsString().equals("kolobok:tale")) {
                    String event = value.getAsJsonObject("conditions").get("event").getAsString();
                    assertTrue(java.util.Arrays.stream(Tale.values()).anyMatch(t -> t.getSerializedName().equals(event)),
                            name + " waits for no such event: " + event);
                    awaited.add(event);
                }
            }
        }
        assertEquals(1, roots, "one tab");
        String code = source("entity/KolobokEntity.java") + source("event/KolobokEvents.java")
                + source("block/StoveBlockEntity.java") + source("item/RollingPinItem.java");
        for (Tale event : Tale.values()) {
            assertTrue(awaited.contains(event.getSerializedName()), "no advancement waits for " + event);
            assertTrue(code.contains("Tale." + event.name()), "the code never tells " + event);
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

    /**
     * @return a source file of the mod, under {@code src/main/java/ru/bagomot/kolobok}, found from
     *         whichever build node the tests run in
     */
    private static String source(String path) throws IOException {
        return Files.readString(sourceDir("src/main/java/ru/bagomot/kolobok").resolve(path));
    }

    /**
     * @return that directory of the project, found from whichever build node the tests run in
     */
    private static Path sourceDir(String path) throws IOException {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path found = dir.resolve(path);
            if (Files.isDirectory(found)) {
                return found;
            }
        }
        throw new IOException("no " + path);
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
