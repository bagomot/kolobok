package ru.bagomot.kolobok;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The mod's own translations, read from its jar on the server. A dedicated server knows only the
 * English names of things; what the server has to write as plain text for a player — a book's title
 * and author, which are no translatable text — it takes from here, in that player's language.
 */
public final class ModLang {

    private static final String FALLBACK = "en_us";
    /** Only the languages the mod has: a client names its language itself, and anything at all. */
    private static final Map<String, Optional<Map<String, String>>> LANGUAGES = new ConcurrentHashMap<>();

    private ModLang() {
    }

    /**
     * @return that key's text in the player's language, in English if the mod has no such language
     *         or line, or the key itself
     */
    public static String get(ServerPlayer player, String key) {
        String code = player.clientInformation().language().toLowerCase(Locale.ROOT);
        String text = language(code).map(lines -> lines.get(key)).orElse(null);
        if (text == null) {
            text = language(FALLBACK).map(lines -> lines.get(key)).orElse(null);
        }
        return text != null ? text : key;
    }

    private static Optional<Map<String, String>> language(String code) {
        if (!code.matches("[a-z0-9_]{1,16}")) {
            return Optional.empty();
        }
        Optional<Map<String, String>> known = LANGUAGES.get(code);
        if (known != null) {
            return known;
        }
        Optional<Map<String, String>> read = read(code);
        if (read.isPresent()) {
            LANGUAGES.put(code, read);
        }
        return read;
    }

    private static Optional<Map<String, String>> read(String code) {
        try (InputStream in = ModLang.class.getResourceAsStream("/assets/" + Kolobok.MOD_ID + "/lang/" + code + ".json")) {
            if (in == null) {
                return Optional.empty();
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> lines = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    lines.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
            return Optional.of(Map.copyOf(lines));
        } catch (IOException | RuntimeException e) {
            Kolobok.LOG.warn("Could not read the {} translations", code, e);
            return Optional.empty();
        }
    }
}
