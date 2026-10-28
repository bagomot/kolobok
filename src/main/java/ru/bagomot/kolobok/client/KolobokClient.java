package ru.bagomot.kolobok.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import ru.bagomot.kolobok.config.KConfig;
import ru.bagomot.kolobok.entity.KState;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.entity.song.Phonetics;
import ru.bagomot.kolobok.entity.song.Syllable;
import ru.bagomot.kolobok.registry.KSounds;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    /** The Head is this much bigger than he is, and gets there in twenty ticks. */
    private static final float HEAD_SCALE = 1.75F;
    private static final float HEAD_GROWTH = (HEAD_SCALE - 1.0F) / 20.0F;
    /**
     * Categories that are chatter rather than an answer; the chattiness setting thins them out. The
     * Head's sleep-talk comes from the same timer as the idle chatter.
     */
    private static final List<String> CHATTER = List.of("idle", "night", "new_moon", "sulk", "golova_dream");
    /** A crumb a block, and none spawned out of this range of the camera. */
    private static final double CRUMBS_RANGE = 48.0;
    /** He dances while a jukebox plays this close, as a parrot does. */
    private static final double DANCE_RANGE = 3.46;

    /**
     * Each Kolobok's crumbs in this level, counted, by UUID: a count rather than the crumbs, which hold
     * their level. By UUID, so that one who leaves the tracking range and comes back keeps his count.
     */
    private static final Map<UUID, CrumbParticle.Trail> TRAILS = new HashMap<>();
    private static final LevelWatch TRAILS_LEVEL = new LevelWatch();

    private KolobokClient() {
    }

    public static void init(Path configDir) {
        KConfig.loadClient(configDir);
        KolobokEntity.clientTicker = KolobokClient::tick;
    }

    private static void tick(KolobokEntity entity) {
        roll(entity);
        bounce(entity);
        growHead(entity);
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
        crumbs(entity);
        drip(entity);
        dance(entity);
    }

    /**
     * Crumbs: in the crumbs mode, or showing the way, he leaves a crumb a block on the ground. Only his
     * owner sees them, and only the last 48; the server knows nothing of them.
     */
    private static void crumbs(KolobokEntity entity) {
        if (TRAILS_LEVEL.changed()) {
            TRAILS.clear();
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!entity.dropsCrumbs() || !KConfig.client().crumbs || minecraft.player == null || !entity.isOwnedBy(minecraft.player)) {
            entity.clientCrumbPath = 0.0F;
            return;
        }
        if (!entity.onGround()) {
            return;
        }
        double dx = entity.getX() - entity.xo;
        double dz = entity.getZ() - entity.zo;
        entity.clientCrumbPath += (float) Math.sqrt(dx * dx + dz * dz);
        if (entity.clientCrumbPath < 1.0F) {
            return;
        }
        entity.clientCrumbPath -= 1.0F;
        if (minecraft.gameRenderer.mainCamera().position().distanceToSqr(entity.position()) > CRUMBS_RANGE * CRUMBS_RANGE) {
            return;
        }
        RandomSource random = entity.getRandom();
        CrumbParticle.Trail trail = TRAILS.computeIfAbsent(entity.getUUID(), uuid -> new CrumbParticle.Trail());
        minecraft.particleEngine.add(new CrumbParticle(minecraft.level, entity.getX() + (random.nextDouble() - 0.5) * 0.3,
                entity.getY(), entity.getZ() + (random.nextDouble() - 0.5) * 0.3, trail));
    }

    /**
     * Soggy, he drips.
     */
    private static void drip(KolobokEntity entity) {
        if (!entity.hasFlag(KolobokEntity.FLAG_SOGGY) || entity.getRandom().nextInt(8) != 0) {
            return;
        }
        RandomSource random = entity.getRandom();
        double spread = entity.getBbWidth() * 0.4;
        entity.level().addParticle(ParticleTypes.DRIPPING_WATER, entity.getX() + (random.nextDouble() - 0.5) * spread,
                entity.getY() + entity.getBbHeight() * 0.3, entity.getZ() + (random.nextDouble() - 0.5) * spread, 0.0, 0.0, 0.0);
    }

    /**
     * He stops dancing once the jukebox is gone or he is out of its reach, as a parrot does.
     */
    private static void dance(KolobokEntity entity) {
        if (entity.clientDancing && (entity.clientJukebox == null
                || !entity.clientJukebox.closerToCenterThan(entity.position(), DANCE_RANGE)
                || !entity.level().getBlockState(entity.clientJukebox).is(Blocks.JUKEBOX))) {
            entity.clientDancing = false;
            entity.clientJukebox = null;
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
        // In лапти he walks instead.
        boolean rolling = distance > STILL && entity.onGround() && entity.getKState() != KState.ON_PLAYER
                && !entity.isInSittingPose() && entity.getItemBySlot(EquipmentSlot.FEET).isEmpty();
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

    /**
     * Jumping he stretches up, landing he squashes flat and springs back. His hat keeps to the top of
     * his head a moment late: it jumps after him and lands after him.
     */
    private static void bounce(KolobokEntity entity) {
        entity.clientSquashO = entity.clientSquash;
        entity.clientHatLagO = entity.clientHatLag;
        double dy = entity.getY() - entity.yo;
        boolean airborne = !entity.onGround() && !entity.isPassenger() && !entity.isInWater()
                && entity.getKState() != KState.ON_PLAYER;
        float target = airborne ? (float) Mth.clamp(dy * 0.6, -0.08, 0.15) : 0.0F;
        if (!airborne && entity.clientAirborne && entity.clientLastDy < -0.1) {
            // Landed: squashed the harder, the faster he fell.
            entity.clientSquashSpeed -= (float) Math.min(0.3, -entity.clientLastDy * 0.5);
        }
        entity.clientSquashSpeed = entity.clientSquashSpeed * 0.65F + (target - entity.clientSquash) * 0.3F;
        entity.clientSquash = Mth.clamp(entity.clientSquash + entity.clientSquashSpeed, -0.35F, 0.25F);
        // The top of his head went up this many pixels; the hat, left behind, takes half of the way back
        // each tick. It may sink into him a little, but hangs no more than three pixels above.
        float headUp = (float) (dy * 16.0) + (entity.clientSquash - entity.clientSquashO) * 10.0F;
        entity.clientHatLag = Mth.clamp((entity.clientHatLag + headUp) * 0.5F, -3.0F, 0.5F);
        entity.clientAirborne = airborne;
        entity.clientLastDy = dy;
    }

    /**
     * The Head grows over a second as he falls asleep in it, and shrinks back as he wakes.
     */
    private static void growHead(KolobokEntity entity) {
        entity.clientHeadScaleO = entity.clientHeadScale;
        float target = entity.isHead() ? HEAD_SCALE : 1.0F;
        entity.clientHeadScale += Mth.clamp(target - entity.clientHeadScale, -HEAD_GROWTH, HEAD_GROWTH);
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
                if (speaksInChat(entity)) {
                    Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(
                            Component.translatable("kolobok.say.chat", entity.getDisplayName(), line));
                }
            }
        } else if (entity.clientLine != null && now >= until) {
            entity.clientLine = null;
        }
    }

    /**
     * @return whether his lines go to this player's chat: in the chat modes, and only his owner's, so
     *         that the Kolobki of a whole server never flood it
     */
    static boolean speaksInChat(KolobokEntity entity) {
        Player player = Minecraft.getInstance().player;
        return KConfig.client().chat() && player != null && entity.isOwnedBy(player);
    }

    /**
     * @return whether his lines show in a bubble over him: always, unless they go to this player's chat
     *         alone
     */
    static boolean speaksInBubble(KolobokEntity entity) {
        return KConfig.client().bubbles() || !speaksInChat(entity);
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
        SongPlayer.voice(entity, KSounds.mumble(syllable), volume, pitch);
    }

    /**
     * @return whether he is moving his mouth right now, for the renderer
     */
    static boolean isMumbling(KolobokEntity entity) {
        return entity.clientMumbleIndex < entity.clientMumble.size();
    }

    /**
     * Tells when the client has gone into another level, or out to the menu, without keeping the level
     * it was in, so that the client's caches by UUID can start afresh. They hold nothing of a level:
     * noticing late only lets them grow a little longer.
     */
    static final class LevelWatch {
        private WeakReference<ClientLevel> level = new WeakReference<>(null);

        /**
         * @return whether the client's level is another one than at the last call
         */
        boolean changed() {
            ClientLevel now = Minecraft.getInstance().level;
            if (level.get() == now) {
                return false;
            }
            level = new WeakReference<>(now);
            return true;
        }
    }
}
