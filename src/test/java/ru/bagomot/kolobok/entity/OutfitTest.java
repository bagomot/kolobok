package ru.bagomot.kolobok.entity;

import org.junit.jupiter.api.Test;
import ru.bagomot.kolobok.entity.song.Voice;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutfitTest {

    /** Every factor a hat can give his voice: the hats' own and each metal's шелом. */
    private static List<Float> hatFactors() {
        List<Float> out = new ArrayList<>();
        for (Outfit.Hat hat : Outfit.Hat.values()) {
            out.add(hat.voice());
        }
        for (Outfit.Material material : Outfit.Material.values()) {
            out.add(material.helmetVoice());
        }
        return out;
    }

    @Test
    void anyDoughOutfitAndMoodKeepTheVoiceWhereTheMelodyFits() {
        for (Variant variant : Variant.values()) {
            for (float hat : hatFactors()) {
                for (Mood mood : Mood.values()) {
                    float pitch = Outfit.pitch(variant, hat, mood);
                    assertTrue(pitch >= 0.67F && pitch <= 1.33F, variant + " " + hat + " " + mood + ": " + pitch);
                }
            }
        }
        // The extremes are clamped rather than let out of the game's range.
        assertEquals(1.33F, Outfit.pitch(Variant.MINI, Outfit.Hat.KOKOSHNIK.voice(), Mood.SCARED));
        assertEquals(0.67F, Outfit.pitch(Variant.CLASSIC, Outfit.Material.NETHERITE.helmetVoice(), Mood.HAPPY));
    }

    @Test
    void theHeavierTheHelmetTheLowerHeSingsAndTheGirlishHatsRaiseIt() {
        Outfit.Material lowest = Outfit.Material.NETHERITE;
        for (Outfit.Material material : Outfit.Material.values()) {
            assertTrue(material.helmetVoice() >= lowest.helmetVoice(), material.name());
            assertTrue(material.helmetVoice() < 1.0F, material.name());
        }
        assertTrue(Outfit.Hat.CROWN.voice() < 1.0F);
        assertTrue(Outfit.Hat.USHANKA.voice() < 1.0F);
        assertTrue(Outfit.Hat.WREATH.voice() > 1.0F);
        assertTrue(Outfit.Hat.KOKOSHNIK.voice() > Outfit.Hat.WREATH.voice());
        assertEquals(1.0F, Outfit.Hat.NONE.voice());
    }

    @Test
    void onlyAHelmetAndTheCrownChangeHowHeSings() {
        assertEquals(Voice.HELMET, Outfit.Hat.HELMET.manner());
        assertEquals(Voice.CROWN, Outfit.Hat.CROWN.manner());
        for (Outfit.Hat hat : List.of(Outfit.Hat.NONE, Outfit.Hat.USHANKA, Outfit.Hat.WREATH, Outfit.Hat.KOKOSHNIK)) {
            assertNull(hat.manner(), hat.name());
        }
    }
}
