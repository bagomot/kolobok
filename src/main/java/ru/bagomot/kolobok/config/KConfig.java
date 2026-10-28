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
    }

    /**
     * Reads the file, brings every value into its range and writes it back. Clamped before writing:
     * Gson reads NaN and Infinity but refuses to write them, and the game must start whatever the
     * file says.
     */
    private static <T extends Section> T load(Path file, Class<T> type, T defaults) {
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
        value.clamp();
        try {
            // Serialised in full first, so that a failure leaves the old file whole.
            String json = GSON.toJson(value);
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                writer.write(json);
            }
        } catch (IOException | RuntimeException e) {
            Kolobok.LOG.warn("Could not write {}", file, e);
        }
        return value;
    }

    /**
     * @return the value within its range, or the fallback for NaN and the infinities
     */
    private static float clamp(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }

    /** One of the two files. */
    private interface Section {
        /** Brings every value into its range; the file may say anything. */
        void clamp();
    }

    /** Rules of the game. Every player on the server shares them. */
    public static final class Server implements Section {
        /** Milk tames an untamed Kolobok with a chance of one in this many. */
        public int tameChanceDenominator = 3;
        /** Foxes hunt Kolobki and carry them off, as in the tale. */
        public boolean foxHunting = true;
        /** A tamed Kolobok now and then runs off from his owner to sing to animals, and comes back. */
        public boolean escapades = true;
        /** A mob that has just hit his owner, he draws onto himself: «Эй, я вкуснее!» */
        public boolean luring = true;
        /** Now and then his song annoys a mob close by into going for him. */
        public boolean annoyingSong = true;
        /** A hungry owner may bite his sitting Kolobok. */
        public boolean biting = true;
        /** Now and then he jumps on a chicken and rides it. */
        public boolean chickenJockey = true;
        /** «Тянут-потянут»: ripe beetroot picked with one's dog, cat and Kolobok about gives six more. */
        public boolean repka = true;
        /** Game days without milk before he goes stale; 0, never. */
        public int staleDays = 3;
        /** «Покажи дорогу» looks for a village this many chunks off at most, as a dolphin looks for treasure. */
        public int guideSearchChunks = 50;
        /** Every player gets the guide, «Как печь Колобка», the first time they join. */
        public boolean guideBook = true;

        @Override
        public void clamp() {
            tameChanceDenominator = Math.max(1, tameChanceDenominator);
            staleDays = Math.clamp(staleDays, 0, 1000);
            guideSearchChunks = Math.clamp(guideSearchChunks, 1, 100);
        }
    }

    /** How Kolobok talks to this player. */
    public static final class Client implements Section {
        /**
         * Share of internet memes, never a measure of rudeness: the humour rules
         * hold on every level. 0 «Сказка», 1 «Мультик», 2 «Интернет», 3 «Всё сразу». A line tagged
         * with level N is shown on levels N and up.
         */
        public int memeLevel = 2;
        /** Idle chatter, from 0 «молчун» to 4 «радио». Lines that answer something always show. */
        public int chattiness = 2;
        /**
         * Where lines go: "bubbles", "chat" or "both". Only one's own Kolobki write in chat; anyone
         * else's always talk in bubbles, so that a crowd of them never floods it.
         */
        public String speech = "bubbles";
        /** Bread crumbs behind one's own Kolobok in the crumbs mode, or showing the way. */
        public boolean crumbs = true;
        /** Multiplies the volume of the voice, 0–1. */
        public float voiceVolume = 1.0F;

        @Override
        public void clamp() {
            memeLevel = Math.clamp(memeLevel, 0, 3);
            chattiness = Math.clamp(chattiness, 0, 4);
            voiceVolume = KConfig.clamp(voiceVolume, 0.0F, 1.0F, 1.0F);
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
