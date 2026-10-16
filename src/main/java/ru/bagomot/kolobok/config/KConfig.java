package ru.bagomot.kolobok.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import ru.bagomot.kolobok.Kolobok;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Two small JSON files in {@code config/kolobok/}: {@code server.json} with the rules
 * of the game, {@code client.json} with how Kolobok talks to this player. Plain JSON rather than a
 * loader config API, so both loaders read the same file.
 *
 * <p>A missing file is written with the defaults; a file from an older version is rewritten with the
 * new fields added; a broken file is left alone and the defaults are used.
 */
public final class KConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Server server = new Server();
    private static Client client = new Client();

    private KConfig() {
    }

    public static Server server() {
        return server;
    }

    public static Client client() {
        return client;
    }

    public static void loadServer(Path configDir) {
        server = load(configDir.resolve(Kolobok.MOD_ID).resolve("server.json"), Server.class, new Server());
    }

    public static void loadClient(Path configDir) {
        client = load(configDir.resolve(Kolobok.MOD_ID).resolve("client.json"), Client.class, new Client());
        client.clamp();
    }

    private static <T> T load(Path file, Class<T> type, T defaults) {
        T value = defaults;
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                T read = GSON.fromJson(reader, type);
                if (read != null) {
                    value = read;
                }
            } catch (IOException | JsonParseException e) {
                Kolobok.LOG.error("Could not read {}, using defaults", file, e);
                return defaults;
            }
        }
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(value, writer);
            }
        } catch (IOException e) {
            Kolobok.LOG.warn("Could not write {}", file, e);
        }
        return value;
    }

    /** Rules of the game. Every player on the server shares them. */
    public static final class Server {
        /** Milk tames an untamed Kolobok with a chance of one in this many. */
        public int tameChanceDenominator = 3;
    }

    /** How Kolobok talks to this player. */
    public static final class Client {
        /**
         * Share of internet memes, never a measure of rudeness: the humour rules
         * hold on every level. 0 «Сказка», 1 «Мультик», 2 «Интернет», 3 «Всё сразу». A line tagged
         * with level N is shown on levels N and up.
         */
        public int memeLevel = 2;
        /** Idle chatter, from 0 «молчун» to 4 «радио». Lines that answer something always show. */
        public int chattiness = 2;
        /** Where lines go: "bubbles", "chat" or "both". */
        public String speech = "bubbles";
        public boolean crumbs = true;
        /** Multiplies the volume of the voice, 0–1. */
        public float voiceVolume = 1.0F;

        void clamp() {
            memeLevel = Math.clamp(memeLevel, 0, 3);
            chattiness = Math.clamp(chattiness, 0, 4);
            voiceVolume = Math.clamp(voiceVolume, 0.0F, 1.0F);
            if (!"bubbles".equals(speech) && !"chat".equals(speech) && !"both".equals(speech)) {
                speech = "bubbles";
            }
        }

        public boolean bubbles() {
            return !"chat".equals(speech);
        }

        public boolean chat() {
            return !"bubbles".equals(speech);
        }
    }
}
