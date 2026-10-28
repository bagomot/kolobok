package ru.bagomot.kolobok.config;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KConfigTest {

    /** Gson reads these but cannot write them back; the game used to crash on start. */
    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity", "-Infinity", "1e39"})
    void aVolumeThatIsNoNumberFallsBackToTheDefault(String volume, @TempDir Path dir) throws IOException {
        Path file = dir.resolve("kolobok").resolve("client.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"voiceVolume\": " + volume + ", \"chattiness\": 3}");
        KConfig.loadClient(dir);
        assertEquals(1.0F, KConfig.client().voiceVolume);
        assertEquals(3, KConfig.client().chattiness, "the rest of the file is kept");
        assertTrue(Files.readString(file).contains("\"voiceVolume\": 1.0"), "rewritten with the default");
    }
}
