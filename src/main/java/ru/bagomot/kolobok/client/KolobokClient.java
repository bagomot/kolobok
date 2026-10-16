package ru.bagomot.kolobok.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import ru.bagomot.kolobok.config.KConfig;
import ru.bagomot.kolobok.entity.KState;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.entity.song.Phonetics;
import ru.bagomot.kolobok.entity.song.Syllable;
import ru.bagomot.kolobok.registry.KSounds;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Client side of Kolobok that is not rendering: rolling, blinking, choosing the line to say and
 * mumbling it. Runs from the entity's own client tick.
 */
public final class KolobokClient {

    /** Rolling below this many blocks per tick counts as standing still. */
    private static final float STILL = 0.003F;
    private static final float TWO_PI = Mth.TWO_PI;
    /** Mumbling never runs longer than this many syllables. */
    private static final int MUMBLE_MAX = 8;
    private static final int MUMBLE_STEP_TICKS = 2;
    /** Categories that are chatter rather than an answer; the chattiness setting thins them out. */
    private static final List<String> CHATTER = List.of("idle", "night");

    private KolobokClient() {
    }

    public static void init(Path configDir) {
        KConfig.loadClient(configDir);
        KolobokEntity.clientTicker = KolobokClient::tick;
    }

    private static void tick(KolobokEntity entity) {
        roll(entity);
        blink(entity);
        speech(entity);
        mumble(entity);
        SongPlayer.tick(entity);
        if (entity.clientMouthTicks > 0) {
            entity.clientMouthTicks--;
        }
        if (entity.getKState() == KState.HOT) {
            steam(entity);
        }
    }

    /**
     * A fresh Kolobok steams until he has cooled.
     */
    private static void steam(KolobokEntity entity) {
        RandomSource random = entity.getRandom();
        if (random.nextInt(3) != 0) {
            return;
        }
        double spread = entity.getBbWidth() * 0.35;
        entity.level().addParticle(ParticleTypes.WHITE_SMOKE,
                entity.getX() + (random.nextDouble() - 0.5) * spread, entity.getY() + entity.getBbHeight() * 0.9,
                entity.getZ() + (random.nextDouble() - 0.5) * spread, 0.0, 0.03, 0.0);
    }

    /**
     * The body turns by the distance rolled over the radius, so he honestly rolls and his face goes
     * under. When he stops he rocks back face-forward like a roly-poly toy.
     */
    private static void roll(KolobokEntity entity) {
        entity.clientRollO = entity.clientRoll;
        double dx = entity.getX() - entity.xo;
        double dz = entity.getZ() - entity.zo;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        boolean rolling = distance > STILL && entity.onGround() && entity.getKState() != KState.ON_PLAYER
                && !entity.isInSittingPose();
        if (rolling) {
            entity.clientRollSpeed = distance / KolobokModel.RADIUS;
        } else {
            float upright = Math.round(entity.clientRoll / TWO_PI) * TWO_PI;
            entity.clientRollSpeed = entity.clientRollSpeed * 0.72F + (upright - entity.clientRoll) * 0.22F;
        }
        entity.clientRoll += entity.clientRollSpeed;
        // Keep the angle small once he has settled, without a visible jump.
        if (!rolling && Math.abs(entity.clientRollSpeed) < 1.0E-3F) {
            float turns = Math.round(entity.clientRoll / TWO_PI) * TWO_PI;
            entity.clientRoll -= turns;
            entity.clientRollO -= turns;
        }
    }

    private static void blink(KolobokEntity entity) {
        if (entity.clientBlinkTicks > 0) {
            entity.clientBlinkTicks--;
        }
        if (--entity.clientNextBlink <= 0) {
            entity.clientBlinkTicks = 3;
            entity.clientNextBlink = 60 + entity.getRandom().nextInt(40);
        }
    }

    private static void speech(KolobokEntity entity) {
        int until = entity.getSayUntil();
        int now = (int) entity.level().getGameTime();
        if (until != entity.clientLastSayUntil) {
            entity.clientLastSayUntil = until;
            Component line = until > now ? pickLine(entity.getSayKey(), entity.getSaySeed()) : null;
            entity.clientLine = line;
            if (line != null) {
                startMumble(entity, line.getString());
                if (KConfig.client().chat()) {
                    Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(
                            Component.translatable("kolobok.say.chat", entity.getDisplayName(), line));
                }
            }
        } else if (entity.clientLine != null && now >= until) {
            entity.clientLine = null;
        }
    }

    /**
     * Picks a line from {@code kolobok.say.<category>.<level>.<n>} in the current language. Lines of
     * meme level up to the configured one are candidates; the server's seed chooses among them, so
     * a player hears one line per event however often the bubble is redrawn.
     *
     * @return the line, or {@code null} when chattiness drops this one or there is no line at all
     */
    static Component pickLine(String category, int seed) {
        if (category.isEmpty()) {
            return null;
        }
        KConfig.Client config = KConfig.client();
        if (CHATTER.contains(category) && Math.floorMod(seed >>> 8, 4) >= config.chattiness) {
            return null;
        }
        Language language = Language.getInstance();
        List<String> keys = new ArrayList<>();
        for (int level = 0; level <= config.memeLevel; level++) {
            for (int n = 0; ; n++) {
                String key = "kolobok.say." + category + "." + level + "." + n;
                if (!language.has(key)) {
                    break;
                }
                keys.add(key);
            }
        }
        if (keys.isEmpty()) {
            return null;
        }
        return Component.translatable(keys.get(Math.floorMod(seed, keys.size())));
    }

    private static void startMumble(KolobokEntity entity, String text) {
        List<Syllable> syllables = Phonetics.syllables(text);
        entity.clientMumble = syllables.size() > MUMBLE_MAX ? syllables.subList(0, MUMBLE_MAX) : syllables;
        entity.clientMumbleIndex = 0;
    }

    private static void mumble(KolobokEntity entity) {
        if (entity.clientMumbleIndex >= entity.clientMumble.size() || entity.tickCount % MUMBLE_STEP_TICKS != 0) {
            return;
        }
        int i = entity.clientMumbleIndex++;
        Syllable syllable = entity.clientMumble.get(i);
        // Speech melody: a small rise and fall over the phrase, a lift on the first syllable.
        float contour = 1.0F + 0.06F * Mth.sin(i * 1.3F) + (i == 0 ? 0.05F : 0.0F);
        float pitch = entity.voice() * contour;
        float volume = 0.55F * KConfig.client().voiceVolume;
        if (volume <= 0.0F || entity.isSilent()) {
            return;
        }
        entity.level().playLocalSound(entity.getX(), entity.getY() + 0.3, entity.getZ(),
                KSounds.mumble(syllable), SoundSource.NEUTRAL, volume, pitch, false);
    }

    /**
     * @return whether he is moving his mouth right now, for the renderer
     */
    static boolean isMumbling(KolobokEntity entity) {
        return entity.clientMumbleIndex < entity.clientMumble.size();
    }
}
