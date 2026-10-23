package ru.bagomot.kolobok.entity;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Prediction;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import ru.bagomot.kolobok.Kolobok;
import ru.bagomot.kolobok.block.StoveBlockEntity;
import ru.bagomot.kolobok.block.StovePart;
import ru.bagomot.kolobok.block.StovePartBlock;
import ru.bagomot.kolobok.block.StovePattern;
import ru.bagomot.kolobok.config.KConfig;
import ru.bagomot.kolobok.entity.ai.CatOnStoveGoal;
import ru.bagomot.kolobok.entity.ai.Goals;
import ru.bagomot.kolobok.entity.song.Phonetics;
import ru.bagomot.kolobok.entity.song.Song;
import ru.bagomot.kolobok.entity.song.SongText;
import ru.bagomot.kolobok.entity.song.Syllable;
import ru.bagomot.kolobok.entity.song.Voice;
import ru.bagomot.kolobok.item.SongbookItem;
import ru.bagomot.kolobok.registry.KItems;
import ru.bagomot.kolobok.registry.KSounds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Колобок. A tamable pet that does not die while he has a stove, sings his way out of trouble and
 * rides on his owner's shoulder.
 *
 * <p>He is born in the горнило of a Russian stove: hot and asleep in there, then, cooled, he opens
 * his eyes, hops out and rolls away from whoever is looking. Every twenty seconds of the chase his
 * head spins and he stops to sing a verse. A player who crouches beside him and keeps still for the
 * whole verse has listened; milk then may tame him, and every song listened to makes that likelier.
 *
 * <p>Tamed, he sometimes climbs into a cold, empty горнило and sits there a while. Hurt badly, he
 * goes to warm up on top of a warm stove nearby until he is whole again; three days without milk
 * and he goes stale, and heals there slowly. Killed, he is dead, and what he wore falls.
 *
 * <p>He wears what his owner puts on him: a helmet, boots, his own crown, wreath or kokoshnik, tinted
 * glass for sunglasses (see {@link Outfit}). Sitting in a metal helmet he sometimes plays the Head
 * from «Руслан и Людмила». Following or wandering, now and then he runs off to sing to animals.
 */
public class KolobokEntity extends TamableAnimal {

    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(KolobokEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_FLAGS =
            SynchedEntityData.defineId(KolobokEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(KolobokEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MOOD =
            SynchedEntityData.defineId(KolobokEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_SAY_KEY =
            SynchedEntityData.defineId(KolobokEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_SAY_SEED =
            SynchedEntityData.defineId(KolobokEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SAY_UNTIL =
            SynchedEntityData.defineId(KolobokEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_RIDE_SLOT =
            SynchedEntityData.defineId(KolobokEntity.class, EntityDataSerializers.BYTE);
    /** Entity id of the player he sits on, or -1. Ids are per session, so this is never saved. */
    private static final EntityDataAccessor<Integer> DATA_CARRIER =
            SynchedEntityData.defineId(KolobokEntity.class, EntityDataSerializers.INT);
    /** Game time the current song started at; 0 while he is not singing. */
    private static final EntityDataAccessor<Integer> DATA_SING_START =
            SynchedEntityData.defineId(KolobokEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SING_SEED =
            SynchedEntityData.defineId(KolobokEntity.class, EntityDataSerializers.INT);
    /** 0: the whole song; otherwise the one verse he sings, counted from 1. */
    private static final EntityDataAccessor<Byte> DATA_SING_PART =
            SynchedEntityData.defineId(KolobokEntity.class, EntityDataSerializers.BYTE);
    /** Ids of the mobs he got away from, in that order, comma-separated: there is no list type. */
    private static final EntityDataAccessor<String> DATA_VERSES =
            SynchedEntityData.defineId(KolobokEntity.class, EntityDataSerializers.STRING);

    public static final int FLAG_STALE = 1;
    public static final int FLAG_SOGGY = 1 << 1;
    public static final int FLAG_BURNT = 1 << 2;
    public static final int FLAG_BITTEN = 1 << 3;
    public static final int FLAG_GLASSES = 1 << 4;
    public static final int FLAG_CRUMBS = 1 << 5;
    public static final int FLAG_SING_NEW = 1 << 6;
    public static final int FLAG_SING_HEROBRINE = 1 << 7;

    /** How long a line stays in the bubble. */
    public static final int SAY_TICKS = 100;
    /** Least time between two lines about being hurt. */
    private static final int HURT_LINE_GAP = 40;
    /** Two presses of Shift this close together take him off the player. */
    private static final int DOUBLE_SHIFT_TICKS = 10;

    /** Every twenty seconds of being chased his head spins and he stops to sing a verse. */
    private static final int CHASE_STOP_TICKS = 400;
    /** The first stop comes sooner, ten seconds after he cools. */
    private static final int FIRST_STOP_TICKS = 200;
    /** A chase is only a chase while a player is this close. */
    private static final double CHASE_RANGE = 12.0;
    private static final int DIZZY_TICKS = 30;
    /** After a song someone listened to, he stays put this long, pleased: time to offer milk. */
    private static final int CHARMED_TICKS = 60;
    /** A listener crouches this close… */
    private static final double LISTEN_RANGE = 3.0;
    /** …moves less than this from where he started listening… */
    private static final double LISTEN_STILL = 0.1;
    /** …and settles down within this many ticks of the first note. */
    private static final int LISTEN_JOIN_TICKS = 20;
    /** A tamed Kolobok sings on his own every eight to fifteen minutes, if anyone is there to hear. */
    private static final int SONG_GAP_MIN = 9600;
    private static final int SONG_GAP_MAX = 18000;
    /** With nobody about, he looks again this much later. */
    private static final int SONG_RETRY = 600;
    /** Anyone this close is an audience; when the last of them leaves, he stops singing. */
    private static final double AUDIENCE_RANGE = 12.0;
    /** In guard mode he walks back once he is this far from his post. */
    private static final double GUARD_RANGE = 3.0;
    /** Herobrine sneaks into one night song in fifty. */
    private static final float HEROBRINE_CHANCE = 0.02F;

    /** A tamed Kolobok thinks about climbing into a горнило once every one to three minutes… */
    private static final int VISIT_CHECK_MIN = 1200;
    private static final int VISIT_CHECK_SPREAD = 2400;
    /** …of a stove this close… */
    private static final int VISIT_RANGE = 10;
    /** …and sits in it for half a minute to two minutes. */
    private static final int VISIT_MIN = 600;
    private static final int VISIT_SPREAD = 1800;
    /** Following, he climbs out once his owner is this far off. */
    private static final double VISIT_OWNER_RANGE = 10.0;
    private static final int HOP_TICKS = 10;

    /**
     * Below half his health, a tamed Kolobok goes to warm up on a stove: the nearest warm one within
     * the simulation distance with a place free on top. He looks once a second…
     */
    private static final int STOVE_CHECK = 20;
    /** …again ten seconds after finding none… */
    private static final int STOVE_RETRY = 200;
    /** …and a minute after he gave up on getting to one, was woken on it or got off it. */
    private static final int STOVE_TRIP_GAP = 1200;
    /** He gives up on a stove once he has got no nearer to it for this long. */
    private static final int STOVE_STUCK_TICKS = 200;
    /** On a warm stove he gets back a point of health a second; stale, a point in five seconds. */
    private static final int HEAL_GAP_FRESH = 20;
    private static final int HEAL_GAP_STALE = 100;
    /** Three days without milk and he goes stale: slower, with a harder crust. */
    private static final int STALE_TICKS = 72000;
    /** A verse is earned by being alive half a minute after a mob's hit at most… */
    private static final int VERSE_WINDOW = 600;
    /** …and either this far from the mob… */
    private static final double VERSE_DISTANCE = 16.0;
    /** …or the mob not having gone for him this long. */
    private static final int VERSE_LOST_TARGET = 100;
    /** Each song, a monster within earshot is annoyed by it with this chance, any other mob with the less. */
    private static final float ANNOY_MONSTER = 0.2F;
    private static final float ANNOY_OTHER = 0.05F;
    private static final double ANNOY_RANGE = 8.0;
    /** An annoyed mob with no attack of its own chases him and butts him for this long… */
    private static final int BUTT_TICKS = 200;
    /** …a butt a second at most. */
    private static final int BUTT_GAP = 20;
    /** He draws a mob off his owner at most every half a minute, from this far. */
    private static final int LURE_GAP = 600;
    private static final double LURE_RANGE = 16.0;
    /** The mob has to have hit the owner this recently. */
    private static final int LURE_HIT_TICKS = 30;
    /** He keeps away from mobs that are after him and from those that scare him. */
    private static final double GET_AWAY_RANGE = 8.0;
    private static final float FEAR_RANGE = 6.0F;
    private static final TagKey<EntityType<?>> SCARES_KOLOBOK = TagKey.create(Registries.ENTITY_TYPE, Kolobok.id("scares_kolobok"));
    /** In a fox's mouth for two minutes at most; then foxes leave him be for a minute. */
    private static final int CAUGHT_TICKS = 2400;
    private static final int FOX_SAFE_TICKS = 1200;
    /** Berries this close to the fox make it drop him. */
    private static final double BERRY_RANGE = 6.0;
    /** As his song ends, a fox this close with its eye on him grabs him. */
    private static final double FOX_GRAB_RANGE = 2.5;
    /** Where he sits in a fox's jaws: in front of it, low. */
    private static final double FOX_MOUTH_FORWARD = 0.62;
    private static final double FOX_MOUTH_UP = 0.08;
    /** Now and then he rolls up to a fox to say hello, as in the tale. */
    private static final int FOX_CURIOSITY_CHANCE = 300;
    private static final double FOX_CURIOSITY_RANGE = 12.0;

    /** «Тут как тут»: left this far behind, he turns up next to his owner. */
    private static final double ENDER_RANGE = 24.0;
    /** «Бессонница»: phantoms come for a player who has not slept for three days. */
    private static final int SLEEPLESS_TICKS = 72000;
    private static final int PHANTOM_WARNING_GAP = 6000;

    /** In лапти he walks instead of rolling, a fifth slower. */
    private static final double LAPTI_SPEED = -0.2;

    /**
     * «Голова»: sitting or on guard in a metal helmet, once a minute he may fall asleep as the Head,
     * one time in twenty, if there is room above him to grow…
     */
    private static final int HEAD_ACT_CHECK = 1200;
    private static final int HEAD_ACT_CHANCE = 20;
    private static final double HEAD_ACT_ROOM = 2.0;
    /** …and snores so for a minute. */
    private static final int HEAD_ACT_TICKS = 1200;
    private static final int SNORE_GAP = 40;
    /** Tickled with a feather, he sneezes a gust: this far, in a cone this wide… */
    private static final double SNEEZE_RANGE = 6.0;
    private static final double SNEEZE_HALF_ANGLE = Math.toRadians(30.0);
    private static final double SNEEZE_PUSH = 1.2;
    /** …with an echo a little later, at most every five seconds, and he stays awake a moment. */
    private static final int SNEEZE_ECHO_TICKS = 8;
    private static final int SNEEZE_GAP = 100;
    private static final int GOLOVA_AWAKE_TICKS = 40;
    /** Hit by a player, the Head rolls off about three blocks. */
    private static final double HEAD_ROLL_SPEED = 0.5;

    /**
     * Escapades. Following or wandering, a tamed Kolobok thinks about running off every twenty to
     * forty seconds; he goes to animals he sees with one chance in three, alone with one in twelve…
     */
    private static final int ESCAPADE_CHECK = 400;
    private static final int ESCAPADE_CHANCE = 3;
    private static final int ESCAPADE_ALONE_CHANCE = 12;
    /** …but not within a minute and a half of the last one. */
    private static final int ESCAPADE_GAP = 1800;
    /** He sees an audience this far off, and rolls off this far when there is none. */
    private static final double ESCAPADE_RANGE = 48.0;
    private static final int ESCAPADE_ALONE_RANGE = 24;
    /** Following, he never gets further than this from his owner; left further behind, he hurries back. */
    private static final double ESCAPADE_LEASH = 64.0;
    /** Animals this close to each other are one herd; he picks the biggest he can see. */
    private static final double HERD_RANGE = 6.0;
    /** He sings once he is this close to them… */
    private static final double CONCERT_ARRIVE = 4.0;
    /** …then hangs about for half a minute to a minute and a half… */
    private static final int LINGER_MIN = 600;
    private static final int LINGER_SPREAD = 1200;
    private static final int LINGER_ROAM = 6;
    /** …and following, rolls back until his owner is this close. */
    private static final double ESCAPADE_HOME = 6.0;
    private static final int ESCAPADE_STUCK_TICKS = 200;
    /** A song is a concert: animals this close come up and watch, players this close get going. */
    private static final double CONCERT_RANGE = 8.0;
    private static final double CONCERT_SPOT = 2.5;
    private static final double CONCERT_PLAYERS = 6.0;
    private static final int CONCERT_SPEED_TICKS = 400;

    /**
     * Client-side per-tick work: rolling, blinking, mumbling, steam. Installed by the client
     * initialiser so that this class never mentions a client type and still loads on a dedicated
     * server.
     */
    public static Consumer<KolobokEntity> clientTicker = entity -> {
    };

    // Client-only state, owned by the client code. Plain fields: nothing here is synced.
    public float clientRoll;
    public float clientRollO;
    public float clientRollSpeed;
    public int clientBlinkTicks;
    public int clientNextBlink;
    public int clientLastSayUntil;
    /** The line in the bubble, in this client's language, or {@code null}. */
    public @Nullable Component clientLine;
    public List<Syllable> clientMumble = List.of();
    public int clientMumbleIndex;
    /** The song being played here, its start and the next sample to play. */
    public @Nullable Song clientSong;
    public int clientSongStart;
    public int clientSongNext;
    /** The song's current line for the bubble, or {@code null}. */
    public @Nullable Component clientSongLine;
    /** Ticks the mouth stays open after a sung note. */
    public int clientMouthTicks;
    /** How big he is drawn: 1, or up to 1.75 as the Head. */
    public float clientHeadScale = 1.0F;
    public float clientHeadScaleO = 1.0F;

    private int nextIdleChatter;
    private boolean carrierWasShifting;
    private int lastCarrierShiftPress = -100;

    /** HOT: ticks until he has cooled. */
    private int hotTicks;
    /** HOT or IN_STOVE: the mouth of the stove whose горнило he is in. */
    private @Nullable BlockPos oven;
    /** IN_STOVE: ticks left to sit there, and what he goes back to doing after. */
    private int visitTicks;
    private KState afterVisit = KState.FOLLOW;
    private int nextVisitCheck;
    /** A hop into or out of the горнило in progress, and its tick. Not saved: a hop is half a second. */
    private @Nullable Hop hop;
    private int hopTick;
    /** RESTING: the mouth of the stove he warms up on, and what he goes back to doing after. */
    private @Nullable BlockPos restStove;
    private KState afterRest = KState.FOLLOW;
    /** He does not set off for a stove before this tick. */
    private int nextStoveTrip;
    /** On his way to a stove: his owner does not call him back. */
    private boolean goingToStove;
    /** Game time he last had milk, or was baked: three days later he is stale. */
    private long lastFed;
    private @Nullable BlockPos guardPost;

    /** Songs each player has listened to through, while he was not tamed. */
    private final Map<UUID, Integer> listened = new HashMap<>();
    /** Who is listening to the current song, and where each one settled. */
    private final Map<UUID, Vec3> listeners = new HashMap<>();
    /** Who moved, stood up or walked off during the current song: they missed it. */
    private final Set<UUID> fidgeted = new HashSet<>();
    private int songEnd;
    /** The verse the next stop of the chase is for. */
    private int nextVerse = 1;
    private int chaseTicks;
    private int dizzyTicks;
    private int charmedTicks;
    private int nextSong;

    /** The last mob that hit him whose verse he does not have yet, and when. */
    private @Nullable UUID attacker;
    private @Nullable String attackerType;
    private long attackerHit;
    /** When that mob last had him as its target. */
    private long attackerAimed;
    /** A verse was earned: the whole song comes as soon as he can sing it. */
    private boolean newVerseToSing;
    private int nextPhantomWarning;
    /** Mobs the song annoyed that have no attack of their own: they butt him until this game time. */
    private final Map<UUID, Long> butting = new HashMap<>();
    private long nextButt;
    /** Mobs that have had their chance to be annoyed by the current song. */
    private final Set<UUID> heardSong = new HashSet<>();
    private int nextLure;
    /** CAUGHT: ticks before he wriggles free himself, and what he goes back to. */
    private int caughtTicks;
    private KState afterCaught = KState.FOLLOW;
    /** Foxes leave him be until this game time. */
    private long foxSafeUntil;
    /** «Не на ту напала»: once got out of a fox's mouth with berries. */
    private boolean foxRescued;
    /** Rolling up to a fox: he does not fear it for that while. */
    private boolean curiousAboutFox;
    /** HEAD_ACT: ticks left of the Head's sleep, and what he goes back to after it. */
    private int headActTicks;
    private KState afterHeadAct = KState.SIT;
    private int nextHeadActCheck = HEAD_ACT_CHECK;
    /** Awake for a moment after a sneeze, with the Head's moustache. */
    private int golovaAwakeTicks;
    private int nextSneeze;
    /** The tick the sneeze's echo is due on, or 0. */
    private int sneezeEcho;
    /** The Head has given away its sword: once per Kolobok. */
    private boolean kladenetsGiven;
    /** Where an escapade has got to, the animal he runs to, the spot he heads for or hangs about at. */
    private Escapade escapade = Escapade.NONE;
    private @Nullable Mob escapadeAudience;
    private Vec3 escapadeSpot = Vec3.ZERO;
    private int escapadeTicks;
    private int nextEscapade = ESCAPADE_GAP;
    /** The next look at running off goes ahead whatever the dice say; for tests. */
    private boolean forceEscapade;
    /** {@link #DATA_VERSES} parsed, and the string it was parsed from. */
    private String versesRaw = "";
    private List<String> verses = List.of();

    public KolobokEntity(EntityType<? extends KolobokEntity> type, Level level) {
        super(type, level);
        // Spread out, or every Kolobok loaded with the world speaks up on the same tick.
        nextIdleChatter = 200 + random.nextInt(1200);
        nextSong = SONG_GAP_MIN + random.nextInt(SONG_GAP_MAX - SONG_GAP_MIN);
        nextVisitCheck = VISIT_CHECK_MIN + random.nextInt(VISIT_CHECK_SPREAD);
        // Fresh from the oven; a saved one reads his own time over this.
        lastFed = level.getGameTime();
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    // ------------------------------------------------------------------------------------ data

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_VARIANT, Variant.CLASSIC.ordinal());
        entityData.define(DATA_FLAGS, (byte) 0);
        entityData.define(DATA_STATE, KState.RUNAWAY.ordinal());
        entityData.define(DATA_MOOD, Mood.HAPPY.ordinal());
        entityData.define(DATA_SAY_KEY, "");
        entityData.define(DATA_SAY_SEED, 0);
        entityData.define(DATA_SAY_UNTIL, 0);
        entityData.define(DATA_RIDE_SLOT, (byte) RideSlot.NONE.ordinal());
        entityData.define(DATA_CARRIER, -1);
        entityData.define(DATA_SING_START, 0);
        entityData.define(DATA_SING_SEED, 0);
        entityData.define(DATA_SING_PART, (byte) 0);
        entityData.define(DATA_VERSES, "");
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_VARIANT.equals(accessor)) {
            refreshDimensions();
        }
    }

    public Variant getVariant() {
        return Variant.byId(entityData.get(DATA_VARIANT));
    }

    public void setVariant(Variant variant) {
        entityData.set(DATA_VARIANT, variant.ordinal());
    }

    public boolean hasFlag(int flag) {
        return (entityData.get(DATA_FLAGS) & flag) != 0;
    }

    public void setFlag(int flag, boolean on) {
        int flags = entityData.get(DATA_FLAGS) & 0xFF;
        entityData.set(DATA_FLAGS, (byte) (on ? flags | flag : flags & ~flag));
    }

    public KState getKState() {
        return KState.byId(entityData.get(DATA_STATE));
    }

    public void setKState(KState state) {
        entityData.set(DATA_STATE, state.ordinal());
        // The vanilla sit flag drives SitWhenOrderedToGoal and the sitting pose; keep them in step.
        setOrderedToSit(state == KState.SIT);
        setInSittingPose(state == KState.SIT);
    }

    public Mood getMood() {
        return Mood.byId(entityData.get(DATA_MOOD));
    }

    public void setMood(Mood mood) {
        entityData.set(DATA_MOOD, mood.ordinal());
    }

    public RideSlot getRideSlot() {
        return RideSlot.byId(entityData.get(DATA_RIDE_SLOT));
    }

    public String getSayKey() {
        return entityData.get(DATA_SAY_KEY);
    }

    public int getSaySeed() {
        return entityData.get(DATA_SAY_SEED);
    }

    public int getSayUntil() {
        return entityData.get(DATA_SAY_UNTIL);
    }

    public int getSingStart() {
        return entityData.get(DATA_SING_START);
    }

    public boolean isSinging() {
        return getSingStart() != 0;
    }

    /**
     * @return the player he sits on, if any and if that player is in this level
     */
    public @Nullable Player getCarrier() {
        int id = entityData.get(DATA_CARRIER);
        if (id < 0) {
            return null;
        }
        return level().getEntity(id) instanceof Player player ? player : null;
    }

    /**
     * @return how many songs that player has listened to through
     */
    public int songsListenedBy(Player player) {
        return listened.getOrDefault(player.getUUID(), 0);
    }

    /**
     * @return ids of the mobs he got away from, in the order he did
     */
    public List<String> verses() {
        String raw = entityData.get(DATA_VERSES);
        if (!raw.equals(versesRaw)) {
            versesRaw = raw;
            verses = SongText.parseVerses(raw);
        }
        return verses;
    }

    public boolean hasPerk(Perk perk) {
        return perk == Perk.FOX ? foxRescued : verses().contains(perk.mob());
    }

    /**
     * @return what he has learned, in the order he learned it; the fox's perk, which has no verse,
     *         last. Server side: the fox's perk is not synced.
     */
    public List<Perk> perks() {
        List<Perk> out = new ArrayList<>();
        for (String mob : verses()) {
            Perk perk = Perk.forMob(mob);
            if (perk != null && perk != Perk.FOX) {
                out.add(perk);
            }
        }
        if (foxRescued) {
            out.add(Perk.FOX);
        }
        return out;
    }

    // ------------------------------------------------------------------------------------ save

    private static final Codec<Map<UUID, Integer>> LISTENED_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT);

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Variant", getVariant().serializedName());
        output.putByte("Flags", entityData.get(DATA_FLAGS));
        // Sitting on a player is a session thing: the carrier is gone after a restart. The Head's sleep
        // is a minute long; he wakes up from it.
        KState state = switch (getKState()) {
            case ON_PLAYER -> KState.FOLLOW;
            case HEAD_ACT -> afterHeadAct;
            default -> getKState();
        };
        output.putString("State", state.name());
        output.putInt("HotTicks", hotTicks);
        output.storeNullable("Oven", BlockPos.CODEC, oven);
        output.putInt("VisitTicks", visitTicks);
        output.putString("AfterVisit", afterVisit.name());
        output.storeNullable("RestStove", BlockPos.CODEC, restStove);
        output.putString("AfterRest", afterRest.name());
        output.putLong("LastFed", lastFed);
        output.storeNullable("GuardPost", BlockPos.CODEC, guardPost);
        output.store("Listened", LISTENED_CODEC, listened);
        output.putInt("NextVerse", nextVerse);
        output.store("Verses", Codec.STRING.listOf(), verses());
        output.putBoolean("NewVerse", newVerseToSing);
        output.putInt("CaughtTicks", caughtTicks);
        output.putString("AfterCaught", afterCaught.name());
        output.putLong("FoxSafeUntil", foxSafeUntil);
        output.putBoolean("FoxRescued", foxRescued);
        output.putBoolean("Kladenets", kladenetsGiven);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setVariant(Variant.byName(input.getStringOr("Variant", Variant.CLASSIC.serializedName())));
        entityData.set(DATA_FLAGS, input.getByteOr("Flags", (byte) 0));
        KState fallback = isTame() ? (isOrderedToSit() ? KState.SIT : KState.FOLLOW) : KState.RUNAWAY;
        KState state;
        try {
            state = KState.valueOf(input.getStringOr("State", fallback.name()));
        } catch (IllegalArgumentException e) {
            state = fallback;
        }
        setKState(state);
        hotTicks = input.getIntOr("HotTicks", 0);
        oven = input.read("Oven", BlockPos.CODEC).orElse(null);
        visitTicks = input.getIntOr("VisitTicks", 0);
        try {
            afterVisit = KState.valueOf(input.getStringOr("AfterVisit", KState.FOLLOW.name()));
        } catch (IllegalArgumentException e) {
            afterVisit = KState.FOLLOW;
        }
        restStove = input.read("RestStove", BlockPos.CODEC).orElse(null);
        try {
            afterRest = KState.valueOf(input.getStringOr("AfterRest", KState.FOLLOW.name()));
        } catch (IllegalArgumentException e) {
            afterRest = KState.FOLLOW;
        }
        lastFed = input.getLongOr("LastFed", level().getGameTime());
        guardPost = input.read("GuardPost", BlockPos.CODEC).orElse(null);
        listened.clear();
        listened.putAll(input.read("Listened", LISTENED_CODEC).orElse(Map.of()));
        nextVerse = input.getIntOr("NextVerse", 1);
        entityData.set(DATA_VERSES, SongText.joinVerses(input.read("Verses", Codec.STRING.listOf()).orElse(List.of())));
        newVerseToSing = input.getBooleanOr("NewVerse", false);
        caughtTicks = input.getIntOr("CaughtTicks", CAUGHT_TICKS);
        try {
            afterCaught = KState.valueOf(input.getStringOr("AfterCaught", KState.FOLLOW.name()));
        } catch (IllegalArgumentException e) {
            afterCaught = KState.FOLLOW;
        }
        foxSafeUntil = input.getLongOr("FoxSafeUntil", 0L);
        foxRescued = input.getBooleanOr("FoxRescued", false);
        kladenetsGiven = input.getBooleanOr("Kladenets", false);
        applyPerks();
        applyStale();
    }

    // ------------------------------------------------------------------------------------ spawning

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                  EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        // There is no wild Kolobok: this runs for /summon and the creative spawn egg, which give a
        // cooled, untamed one.
        setKState(KState.RUNAWAY);
        chaseTicks = CHASE_STOP_TICKS - FIRST_STOP_TICKS;
        if (groupData == null) {
            groupData = new AgeableMob.AgeableMobGroupData(false);
        }
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    /**
     * Fresh out of the oven: hot, asleep in the горнило until he cools. The stove places him first.
     *
     * @param stove the stove's mouth
     */
    public void bake(Variant variant, boolean burnt, BlockPos stove, int coolTicks) {
        setVariant(variant);
        setFlag(FLAG_BURNT, burnt);
        setKState(KState.HOT);
        hotTicks = coolTicks;
        oven = stove.immutable();
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    @Override
    protected boolean canBeABaby() {
        return false;
    }

    @Override
    public boolean isFood(ItemStack itemStack) {
        return false;
    }

    @Override
    public boolean canMate(Animal partner) {
        return false;
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        EntityDimensions dimensions = super.getDefaultDimensions(pose);
        return getVariant() == Variant.MINI ? dimensions.scale(0.75F) : dimensions;
    }

    // ------------------------------------------------------------------------------------ goals

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.4));
        goalSelector.addGoal(1, new GetAwayGoal());
        goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        goalSelector.addGoal(3, new SingGoal());
        goalSelector.addGoal(3, new AvoidEntityGoal<>(this, LivingEntity.class, FEAR_RANGE, 1.0, 1.3, this::fears) {
            @Override
            public boolean canUse() {
                return keepsAway() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return keepsAway() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(4, new AvoidEntityGoal<>(this, Player.class, 8.0F, 1.0, 1.25) {
            @Override
            public boolean canUse() {
                return getKState() == KState.RUNAWAY && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return getKState() == KState.RUNAWAY && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(4, new WarmUpGoal());
        goalSelector.addGoal(4, new EscapadeGoal());
        goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.1, 6.0F, 2.0F) {
            @Override
            public boolean canUse() {
                return getKState() == KState.FOLLOW && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return getKState() == KState.FOLLOW && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(5, new ReturnToPostGoal());
        goalSelector.addGoal(5, new VisitStoveGoal());
        goalSelector.addGoal(6, new FoxCuriosityGoal());
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return roams() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return roams() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    private boolean roams() {
        KState state = getKState();
        return state == KState.RUNAWAY || state == KState.WANDER || state == KState.FOLLOW;
    }

    /**
     * While hot, in the горнило, resting, in a fox's mouth, on a player or asleep as the Head, the
     * whole AI step is skipped: nothing may move him. In the горнило and on a player the server places
     * him itself; a hop is placed tick by tick too.
     */
    @Override
    protected boolean isImmobile() {
        KState state = getKState();
        return state == KState.ON_PLAYER || state == KState.HEAD_ACT || isSheltered() || hop != null || super.isImmobile();
    }

    /**
     * Neither the world nor a push moves him: he is on a player, in the горнило, or hopping in or
     * out of it, through the stove's own blocks.
     */
    private boolean isPinned() {
        KState state = getKState();
        return state == KState.ON_PLAYER || state == KState.HOT || state == KState.IN_STOVE || hop != null;
    }

    /**
     * Hot or sitting in the горнило, resting on the stove or in a fox's mouth: nothing but the void
     * and {@code /kill} hurts him, and no mob sees him as a target.
     */
    public boolean isSheltered() {
        KState state = getKState();
        return state == KState.HOT || state == KState.IN_STOVE || state == KState.RESTING || state == KState.CAUGHT;
    }

    // ------------------------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        if (hop != null && !level().isClientSide()) {
            hopTick();
        }
        noPhysics = isPinned();
        super.tick();
        if (level() instanceof ServerLevel serverLevel) {
            switch (getKState()) {
                case ON_PLAYER -> carryTick();
                case HOT -> hotTick(serverLevel);
                case IN_STOVE -> visitTick(serverLevel);
                case CAUGHT -> caughtTick(serverLevel);
                case RUNAWAY -> chaseTick();
                case RESTING -> restTick(serverLevel);
                case HEAD_ACT -> headActTick(serverLevel);
                default -> {
                }
            }
            if (charmedTicks > 0) {
                charmedTicks--;
            }
            // A spinning head belongs to the chase; tamed mid-spin, he stops at once.
            if (getKState() != KState.RUNAWAY) {
                dizzyTicks = 0;
            }
            if (tickCount % 20 == 0) {
                freshnessTick(serverLevel);
                outfitTick(serverLevel);
                // Sat down, hurt or called off while a song held his escapade: it is over.
                if (escapade != Escapade.NONE && !mayEscape()) {
                    endEscapade();
                }
                verseTick(serverLevel);
                perkTick(serverLevel);
                maybeStartHeadAct(serverLevel);
                // The new verse waits for someone to sing it to, and for him to get to a stove if he
                // is on his way.
                if (newVerseToSing && !isSinging() && !isSheltered() && !goingToStove && hasAudience(serverLevel)) {
                    startSong(0, true);
                }
            }
            if (tickCount % 10 == 0) {
                buttTick(serverLevel);
            }
            songTick(serverLevel);
            idleSong(serverLevel);
            if (sneezeEcho != 0 && tickCount >= sneezeEcho) {
                sneezeEcho = 0;
                playSound(KSounds.SNEEZE.get(), 0.4F, voice() * 0.9F);
            }
            updateMood();
            idleChatter();
        } else {
            clientTicker.accept(this);
        }
    }

    private void updateMood() {
        KState state = getKState();
        Mood mood;
        if (state == KState.HEAD_ACT) {
            mood = golovaAwakeTicks > 0 ? Mood.GOLOVA : Mood.GOLOVA_SLEEP;
        } else if (state == KState.HOT || state == KState.RESTING) {
            mood = Mood.SLEEP;
        } else if (state == KState.CAUGHT) {
            mood = Mood.SCARED;
        } else if (isSinging()) {
            mood = Mood.SING;
        } else if (dizzyTicks > 0) {
            mood = Mood.DIZZY;
        } else if (isPanicking()) {
            mood = Mood.SCARED;
        } else if ((state == KState.RUNAWAY || escapade == Escapade.GOING) && getNavigation().isInProgress()) {
            mood = Mood.SLY;
        } else if (state == KState.SIT && level().isDarkOutside()) {
            mood = Mood.SLEEP;
        } else {
            mood = Mood.HAPPY;
        }
        if (mood != getMood()) {
            setMood(mood);
        }
    }

    private void idleChatter() {
        if (--nextIdleChatter > 0) {
            return;
        }
        // The server talks at «radio» rate; each client keeps as much of it as its chattiness setting
        // asks for. Event lines are never filtered.
        nextIdleChatter = 600 + random.nextInt(1200);
        if (getKState() == KState.HEAD_ACT) {
            // The Head talks in its sleep.
            say("golova_dream");
            return;
        }
        if (getMood() == Mood.SLEEP || isSinging() || isSheltered()) {
            return;
        }
        say(getKState() == KState.SIT && level().isDarkOutside() ? "night" : "idle");
    }

    /**
     * Shows a line from {@code kolobok.say.<category>.*} in the bubble and mumbles it. The server only
     * picks the category and a seed; each client picks the line in its own language and meme level.
     */
    public void say(String category) {
        if (level().isClientSide()) {
            return;
        }
        entityData.set(DATA_SAY_KEY, category);
        entityData.set(DATA_SAY_SEED, random.nextInt());
        entityData.set(DATA_SAY_UNTIL, (int) level().getGameTime() + SAY_TICKS);
    }

    // ------------------------------------------------------------------------------------ the stove

    /**
     * Hot in the горнило until he cools. If the stove came apart under him, he is out in the world at
     * once.
     */
    private void hotTick(ServerLevel level) {
        Direction facing = ovenFacing(level);
        if (facing == null) {
            dropOutOfStove(level, KState.RUNAWAY);
            chaseTicks = CHASE_STOP_TICKS - FIRST_STOP_TICKS;
            say("cooled");
            return;
        }
        sitInOven(facing);
        if (--hotTicks <= 0) {
            cool(level, facing);
        }
    }

    /**
     * @return the facing of the stove whose горнило he is in, if it still stands whole
     */
    private @Nullable Direction ovenFacing(ServerLevel level) {
        if (oven == null || !level.isLoaded(oven)) {
            return null;
        }
        BlockState state = level.getBlockState(oven);
        if (!(state.getBlock() instanceof StovePartBlock) || state.getValue(StovePartBlock.PART) != StovePart.MOUTH) {
            return null;
        }
        Direction facing = state.getValue(StovePartBlock.FACING);
        return tickCount % 20 != 0 || StovePattern.isIntact(level, oven, facing) ? facing : null;
    }

    /** In the горнило, between hops: right in his place, looking out of the mouth. */
    private void sitInOven(Direction facing) {
        if (hop != null || oven == null) {
            return;
        }
        Vec3 spot = StovePattern.gornilo(oven, facing);
        setPos(spot.x, spot.y, spot.z);
        setDeltaMovement(Vec3.ZERO);
        face(facing.toYRot());
    }

    private void face(float yaw) {
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    /**
     * «Чё уставились?» He opens his eyes, hops out of the горнило and the chase begins.
     */
    private void cool(ServerLevel level, Direction facing) {
        hotTicks = 0;
        if (!hopOut(level, facing, KState.RUNAWAY)) {
            // Something blocks the mouth: he waits in there a little longer.
            hotTicks = 40;
            return;
        }
        chaseTicks = CHASE_STOP_TICKS - FIRST_STOP_TICKS;
        say("cooled");
    }

    // ------------------------------------------------------------------------------------ the горнило

    /**
     * Climbs into that stove's горнило with a hop from the ground in front of it, to sit there a
     * while. Called by the goal that walked him there.
     */
    void climbIntoOven(BlockPos mouth, Direction facing) {
        afterVisit = getKState();
        oven = mouth.immutable();
        visitTicks = VISIT_MIN + random.nextInt(VISIT_SPREAD);
        getNavigation().stop();
        setKState(KState.IN_STOVE);
        startHop(StovePattern.gornilo(mouth, facing), facing.getOpposite().toYRot());
        say("in_stove");
    }

    private void visitTick(ServerLevel level) {
        Direction facing = ovenFacing(level);
        if (facing == null) {
            dropOutOfStove(level, afterVisit);
            return;
        }
        sitInOven(facing);
        if (hop != null) {
            return;
        }
        boolean ownerLeft = afterVisit == KState.FOLLOW && getOwner() instanceof Player owner
                && (owner.level() != level || distanceToSqr(owner) > VISIT_OWNER_RANGE * VISIT_OWNER_RANGE);
        if (--visitTicks <= 0 || ownerLeft) {
            if (!hopOut(level, facing, afterVisit)) {
                visitTicks = 40;
            }
        }
    }

    /**
     * Out of the горнило, hopping onto the ground in front of the stove.
     *
     * @return {@code false} when the way out is blocked
     */
    private boolean hopOut(ServerLevel level, Direction facing, KState then) {
        Vec3 out = Vec3.atBottomCenterOf(StovePattern.front(oven, facing));
        if (!level.noCollision(this, getDimensions(Pose.STANDING).makeBoundingBox(out))) {
            return false;
        }
        setKState(then);
        oven = null;
        startHop(out, facing.toYRot());
        return true;
    }

    /**
     * The stove came apart around him: he is put down in front of where its mouth was, or on top of
     * the blocks if the front is blocked.
     */
    private void dropOutOfStove(ServerLevel level, KState then) {
        hop = null;
        BlockPos mouth = oven != null ? oven : blockPosition();
        oven = null;
        setKState(then);
        noPhysics = false;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            Vec3 spot = Vec3.atBottomCenterOf(mouth.relative(side));
            if (level.noCollision(this, getDimensions(Pose.STANDING).makeBoundingBox(spot))) {
                setPos(spot.x, spot.y, spot.z);
                return;
            }
        }
        Vec3 top = Vec3.atBottomCenterOf(mouth.above(2));
        setPos(top.x, top.y, top.z);
    }

    private void startHop(Vec3 to, float yaw) {
        hop = new Hop(position(), to, HOP_TICKS, 0.4);
        hopTick = 0;
        face(yaw);
        noPhysics = true;
    }

    private void hopTick() {
        hop.place(this, ++hopTick);
        if (hop.done(hopTick)) {
            hop = null;
            noPhysics = isPinned();
        }
    }

    /**
     * @return whether he sits in a горнило now, or is hopping in or out
     */
    public boolean isInOven() {
        KState state = getKState();
        return state == KState.HOT || state == KState.IN_STOVE;
    }

    /**
     * Touching a hot Kolobok burns a little: «Горячо! Сам ты горячий!»
     */
    private void burn(Player player) {
        if (level() instanceof ServerLevel serverLevel) {
            player.hurtServer(serverLevel, damageSources().hotFloor(), 1.0F);
            say("hot");
        }
    }

    // ------------------------------------------------------------------------------------ chase and song

    /**
     * Untamed, he rolls away from players. Every twenty seconds of being chased his head spins and he
     * stops to sing the next verse — the chance to listen.
     */
    private void chaseTick() {
        if (isSinging()) {
            return;
        }
        if (charmedTicks > 0) {
            return;
        }
        if (dizzyTicks > 0) {
            if (--dizzyTicks == 0) {
                startSong(nextVerse);
                // The song wraps the count around its own number of verses, two or three; six is
                // a whole number of rounds of either.
                nextVerse = nextVerse % 6 + 1;
            }
            return;
        }
        if (level().getNearestPlayer(this, CHASE_RANGE) == null) {
            return;
        }
        if (++chaseTicks >= CHASE_STOP_TICKS) {
            chaseTicks = 0;
            dizzyTicks = DIZZY_TICKS;
            getNavigation().stop();
        }
    }

    /**
     * A tamed Kolobok sings the whole song every eight to fifteen minutes, unless he is asleep or
     * there is nobody about to hear it; then he tries again a little later.
     */
    private void idleSong(ServerLevel level) {
        if (!isTame() || --nextSong > 0) {
            return;
        }
        if (isSinging() || getMood() == Mood.SLEEP || isSheltered() || goingToStove || !hasAudience(level)) {
            nextSong = SONG_RETRY;
            return;
        }
        nextSong = SONG_GAP_MIN + random.nextInt(SONG_GAP_MAX - SONG_GAP_MIN);
        startSong(0);
    }

    /**
     * @return whether anyone is about to hear him: a player or any mob
     */
    private boolean hasAudience(ServerLevel level) {
        return !level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(AUDIENCE_RANGE),
                e -> e != this && e.isAlive() && (e instanceof Player player ? !player.isSpectator() : e instanceof Mob)).isEmpty();
    }

    /**
     * @param part 0 for the whole song, otherwise the verse to sing
     */
    public void startSong(int part) {
        startSong(part, false);
    }

    /**
     * @param part     0 for the whole song, otherwise the verse to sing
     * @param newVerse whether he sings to show off the verse just earned, which is then always
     *                 among the three mobs
     */
    public void startSong(int part, boolean newVerse) {
        if (level().isClientSide()) {
            return;
        }
        int now = Math.max(1, (int) level().getGameTime());
        int seed = random.nextInt();
        if (newVerse) {
            newVerseToSing = false;
        }
        setFlag(FLAG_SING_NEW, newVerse);
        setFlag(FLAG_SING_HEROBRINE, level().isDarkOutside() && random.nextFloat() < HEROBRINE_CHANCE);
        entityData.set(DATA_SING_SEED, seed);
        entityData.set(DATA_SING_PART, (byte) part);
        entityData.set(DATA_SING_START, now);
        // The bubble is the song's now.
        entityData.set(DATA_SAY_UNTIL, now);
        songEnd = now + composeSong().ticks();
        listeners.clear();
        fidgeted.clear();
        heardSong.clear();
        getNavigation().stop();
        if (level() instanceof ServerLevel level) {
            gatherAudience(level);
        }
    }

    /**
     * Composes the song he is singing from the synced data alone, so that every client hears the
     * same one.
     */
    public Song composeSong() {
        List<String> verses = verses();
        String newest = hasFlag(FLAG_SING_NEW) && !verses.isEmpty() ? verses.getLast() : null;
        return Song.compose(entityData.get(DATA_SING_SEED), entityData.get(DATA_SING_PART), verses, newest,
                hasFlag(FLAG_SING_HEROBRINE), songVoice());
    }

    /**
     * @return his manner of singing; the pitch is {@link #voice()}. The strongest character wins:
     *         a шелом or the crown, then Колобочек, then soggy, then stale
     */
    public Voice songVoice() {
        Outfit.Hat hat = Outfit.hat(getItemBySlot(EquipmentSlot.HEAD));
        if (hat != null && hat.manner() != null) {
            return hat.manner();
        }
        if (getVariant() == Variant.MINI) {
            return Voice.MINI;
        }
        if (hasFlag(FLAG_SOGGY)) {
            return Voice.SOGGY;
        }
        return hasFlag(FLAG_STALE) ? Voice.STALE : Voice.NORMAL;
    }

    private void stopSong() {
        entityData.set(DATA_SING_START, 0);
        listeners.clear();
        fidgeted.clear();
    }

    private void songTick(ServerLevel level) {
        if (!isSinging()) {
            return;
        }
        int now = (int) level.getGameTime();
        if (now >= songEnd) {
            finishSong(level);
            return;
        }
        int elapsed = now - getSingStart();
        if (!isTame()) {
            followListeners(level, elapsed);
        }
        if (elapsed % 10 == 0) {
            for (Mob animal : concertAudience(level)) {
                animal.getLookControl().setLookAt(this);
            }
        }
        if (elapsed % 20 == 10) {
            if (!hasAudience(level)) {
                stopSong();
                say("alone");
                return;
            }
            annoyListeners(level);
        }
    }

    // ------------------------------------------------------------------------------------ concert

    /**
     * @return whether he would sing to that mob: an animal or a villager, awake, not a fox (foxes are
     *         a story of their own) and not another Kolobok
     */
    private static boolean isAudience(Mob mob) {
        return (mob instanceof Animal || mob instanceof AbstractVillager) && !(mob instanceof KolobokEntity)
                && !(mob instanceof Fox) && mob.isAlive() && !mob.isNoAi() && !mob.isSleeping();
    }

    /**
     * @return the animals within earshot that come up and watch him sing: those run on goals (a brain
     *         would overrule whatever he asks of them), free to walk (not ridden, carrying anyone,
     *         leashed or told to sit) and not panicking
     */
    private List<Mob> concertAudience(ServerLevel level) {
        return level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(CONCERT_RANGE), mob -> isAudience(mob)
                && Goals.runsOnGoals(mob) && !mob.isVehicle() && !mob.isPassenger() && !mob.isLeashed()
                && !(mob instanceof TamableAnimal pet && pet.isOrderedToSit())
                && !(mob instanceof PathfinderMob pathfinder && pathfinder.isPanicking()));
    }

    /**
     * A song is a concert: the animals within earshot come up to him and watch.
     */
    private void gatherAudience(ServerLevel level) {
        for (Mob animal : concertAudience(level)) {
            Vec3 way = animal.position().subtract(position()).multiply(1.0, 0.0, 1.0);
            if (way.lengthSqr() <= CONCERT_SPOT * CONCERT_SPOT) {
                continue;
            }
            Vec3 seat = position().add(way.normalize().scale(CONCERT_SPOT));
            animal.getNavigation().moveTo(seat.x, seat.y, seat.z, 0.9);
        }
    }

    /**
     * The whole song sung through by a tamed Kolobok sends the players about him on their way with a
     * spring in their step.
     */
    private void cheer(ServerLevel level) {
        for (Player player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(CONCERT_PLAYERS),
                player -> !player.isSpectator())) {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, CONCERT_SPEED_TICKS, 0), this);
        }
    }

    // ------------------------------------------------------------------------------------ annoying

    /**
     * Not everyone likes the song. Once per song, every mob within earshot may take offence: a
     * monster one time in five, any other mob one in twenty.
     */
    private void annoyListeners(ServerLevel level) {
        for (Mob mob : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(ANNOY_RANGE),
                mob -> mob.getTarget() == null && mayProvoke(mob))) {
            if (!heardSong.add(mob.getUUID())) {
                continue;
            }
            if (random.nextFloat() < (mob instanceof Enemy ? ANNOY_MONSTER : ANNOY_OTHER)) {
                provoke(level, mob);
            }
        }
    }

    /**
     * @return whether he can set that mob on himself: not his own kind, not a fox (foxes hunt him
     *         anyway), not a boss, not his owner's pet, and not a mob run by a brain, whose
     *         targets live elsewhere
     */
    private boolean mayProvoke(Mob mob) {
        if (mob == this || !mob.isAlive() || mob.isNoAi() || mob instanceof KolobokEntity || mob instanceof Fox
                || mob instanceof WitherBoss || mob instanceof EnderDragon || !Goals.runsOnGoals(mob)) {
            return false;
        }
        return !(mob instanceof OwnableEntity pet && getOwner() != null && pet.getOwner() == getOwner());
    }

    /**
     * The mob goes for him: one that can attack, by targeting him; one that cannot, by chasing him
     * and butting. Either way, if he gets away from it, it is a verse.
     */
    private void provoke(ServerLevel level, Mob mob) {
        if (mob instanceof Enemy || mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            // As if he had hit it: the mob's own «who hurt me» goal, which comes before its others,
            // keeps him as the target instead of letting it go back to whoever it was after.
            mob.setLastHurtByMob(this);
            mob.setTarget(this);
        } else {
            butting.put(mob.getUUID(), level.getGameTime() + BUTT_TICKS);
        }
        noteAttacker(level, mob);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getEyeY() + 0.4, mob.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * @return whether that mob is after him: aiming at him, or set on butting him
     */
    private boolean isAfterMe(Mob mob) {
        return mob.getTarget() == this || butting.containsKey(mob.getUUID());
    }

    /** Annoyed mobs that cannot attack run after him and butt him. */
    private void buttTick(ServerLevel level) {
        if (butting.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<UUID, Long>> it = butting.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (!(level.getEntity(entry.getKey()) instanceof Mob mob) || !mob.isAlive() || now > entry.getValue()
                    || mob.distanceToSqr(this) > VERSE_DISTANCE * VERSE_DISTANCE || isSheltered()
                    || getKState() == KState.ON_PLAYER) {
                it.remove();
                continue;
            }
            mob.getNavigation().moveTo(this, 1.25);
            if (mob.distanceToSqr(this) < 1.5 * 1.5 && now >= nextButt && mob.hasLineOfSight(this)) {
                nextButt = now + BUTT_GAP;
                mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
                hurtServer(level, damageSources().mobAttack(mob), 1.0F);
            }
        }
    }

    // ------------------------------------------------------------------------------------ fears and the fox

    /**
     * @return whether he runs from that: a fox, a wild wolf, a hare, a polar bear or a witch (the
     *         tag), unless he has a verse about that mob already. A fox he does not see while he
     *         sings, nor while he rolls up to one, nor ever after he was rescued from one.
     */
    private boolean fears(LivingEntity entity) {
        if (!entity.is(SCARES_KOLOBOK) || verses().contains(EntityType.getKey(entity.getType()).toString())) {
            return false;
        }
        if (entity instanceof TamableAnimal pet && pet.isTame()) {
            return false;
        }
        if (entity instanceof Fox) {
            return !isSinging() && !curiousAboutFox && !hasPerk(Perk.FOX);
        }
        return true;
    }

    /**
     * @return whether he is free to run from what is after him or scares him
     */
    private boolean keepsAway() {
        KState state = getKState();
        return (state == KState.FOLLOW || state == KState.WANDER || state == KState.GUARD || state == KState.RUNAWAY)
                && hop == null && !isSinging();
    }

    /**
     * @return whether a fox may go for him now
     */
    public boolean foxMayHunt() {
        return KConfig.server().foxHunting && !isSheltered() && getKState() != KState.ON_PLAYER && hop == null
                && !isPassenger() && !hasPerk(Perk.FOX) && level().getGameTime() >= foxSafeUntil;
    }

    /**
     * In the fox's jaws. The fox goes on with its own business, carrying him, until berries tempt it,
     * it jumps into water, or two minutes pass and he wriggles out himself. No verse: the fox is
     * never sung about.
     */
    private void getCaught(Fox fox) {
        if (isSinging()) {
            stopSong();
        }
        getNavigation().stop();
        dizzyTicks = 0;
        charmedTicks = 0;
        KState state = getKState();
        afterCaught = !isTame() ? KState.RUNAWAY
                : state == KState.SIT || state == KState.GUARD || state == KState.WANDER ? state : KState.FOLLOW;
        startRiding(fox, true, true);
        fox.setTarget(null);
        setKState(KState.CAUGHT);
        caughtTicks = CAUGHT_TICKS;
        say("caught");
    }

    private void caughtTick(ServerLevel level) {
        if (!(getVehicle() instanceof Fox fox) || !fox.isAlive()) {
            letGo(level, null, false);
            return;
        }
        if (--caughtTicks <= 0 || fox.isInWater() || fox.isInLava()) {
            letGo(level, fox, false);
            return;
        }
        if (tickCount % 10 != 0) {
            return;
        }
        boolean berries = fox.getItemBySlot(EquipmentSlot.MAINHAND).is(ItemTags.FOX_FOOD)
                || !level.getEntitiesOfClass(ItemEntity.class, fox.getBoundingBox().inflate(BERRY_RANGE),
                item -> item.getItem().is(ItemTags.FOX_FOOD)).isEmpty();
        if (berries) {
            letGo(level, fox, true);
        }
    }

    /**
     * Out of the fox's mouth, with a hop away from it.
     *
     * @param rescued whether berries did it, which earns «Не на ту напала» the first time
     */
    private void letGo(ServerLevel level, @Nullable Fox fox, boolean rescued) {
        stopRiding();
        setKState(afterCaught);
        foxSafeUntil = level.getGameTime() + FOX_SAFE_TICKS;
        if (fox != null) {
            fox.setTarget(null);
            Vec3 away = position().subtract(fox.position()).multiply(1.0, 0.0, 1.0);
            if (away.lengthSqr() < 1.0E-4) {
                away = Vec3.directionFromRotation(0.0F, fox.getYRot());
            }
            away = away.normalize().scale(0.45);
            setDeltaMovement(away.x, 0.4, away.z);
        }
        if (rescued && !foxRescued) {
            foxRescued = true;
            if (getOwner() instanceof ServerPlayer owner) {
                owner.sendSystemMessage(Component.translatable("kolobok.verse.perk",
                        Component.translatable(Perk.FOX.key()), Component.translatable(Perk.FOX.key() + ".desc")));
            }
        }
        say(rescued ? "rescued" : "fox_escape");
    }

    /**
     * In a fox's jaws: in front of it and low, turning with it.
     */
    @Override
    public Vec3 getVehicleAttachmentPoint(Entity vehicle) {
        if (vehicle instanceof Fox fox) {
            Vec3 mouth = fox.position()
                    .add(Vec3.directionFromRotation(0.0F, fox.yBodyRot).scale(FOX_MOUTH_FORWARD))
                    .add(0.0, FOX_MOUTH_UP, 0.0);
            return vehicle.getPassengerRidingPosition(this).subtract(mouth);
        }
        return super.getVehicleAttachmentPoint(vehicle);
    }

    /**
     * A rider mob steers its vehicle in vanilla; a fox carrying him must go on steering itself.
     */
    @Override
    public boolean canControlVehicle() {
        return !(getVehicle() instanceof Fox) && super.canControlVehicle();
    }

    /**
     * Listening, as the fox's flattery: crouch close, settle within the first second and keep still
     * to the last note.
     */
    private void followListeners(ServerLevel level, int elapsed) {
        for (Player player : level.players()) {
            UUID id = player.getUUID();
            boolean close = player.distanceToSqr(this) <= LISTEN_RANGE * LISTEN_RANGE && !player.isSpectator();
            Vec3 settled = listeners.get(id);
            if (settled == null) {
                if (close && player.isShiftKeyDown() && elapsed <= LISTEN_JOIN_TICKS && !fidgeted.contains(id)) {
                    listeners.put(id, player.position());
                }
            } else if (!close || !player.isShiftKeyDown()
                    || settled.distanceToSqr(player.position()) > LISTEN_STILL * LISTEN_STILL) {
                listeners.remove(id);
                fidgeted.add(id);
            }
        }
    }

    private void finishSong(ServerLevel level) {
        Set<UUID> heard = new HashSet<>(listeners.keySet());
        stopSong();
        // «И от тебя уйду!» — and a fox that crept up while he sang has him, without a word.
        if (foxMayHunt()) {
            Fox fox = level.getNearestEntity(Fox.class, TargetingConditions.forNonCombat().ignoreLineOfSight(), this,
                    getX(), getY(), getZ(), getBoundingBox().inflate(FOX_GRAB_RANGE));
            if (fox != null && fox.getTarget() == this && !fox.isSleeping()) {
                getCaught(fox);
                return;
            }
        }
        if (isTame() && entityData.get(DATA_SING_PART) == 0) {
            cheer(level);
        }
        if (isTame() || heard.isEmpty()) {
            return;
        }
        for (UUID id : heard) {
            listened.merge(id, 1, Integer::sum);
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 0.6, getZ(), 8, 0.3, 0.3, 0.3, 0.0);
        charmedTicks = CHARMED_TICKS;
        say("listened");
    }

    // ------------------------------------------------------------------------------------ death and the stove's warmth

    /**
     * Dead: all he wore falls where he was, whole and whatever the game rules say of mob loot, as a
     * horse's saddle and chest do. Before the mob's own drop chances get a go, which would drop a
     * helmet one time in twelve, battered.
     */
    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            ItemStack stack = getItemBySlot(slot);
            if (!stack.isEmpty()) {
                setItemSlot(slot, ItemStack.EMPTY);
                if (!EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                    spawnAtLocation(level, stack);
                }
            }
        }
        if (hasFlag(FLAG_GLASSES)) {
            setFlag(FLAG_GLASSES, false);
            spawnAtLocation(level, new ItemStack(Items.TINTED_GLASS));
        }
        super.dropAllDeathLoot(level, source);
    }

    /**
     * Hops up onto that place on top of the stove to warm up there, shooing off a cat sitting on it.
     * Called by the goal that walked him to the ground beside it.
     *
     * @return whether he is going up; {@code false} if someone other than a cat has the place now
     */
    boolean climbOntoStove(StoveBlockEntity stove, Vec3 place) {
        List<LivingEntity> there = CatOnStoveGoal.sittingOn(level(), place, this);
        for (LivingEntity entity : there) {
            if (!(entity instanceof Cat)) {
                return false;
            }
        }
        for (LivingEntity cat : there) {
            CatOnStoveGoal.shoo((Cat) cat, place);
        }
        afterRest = getKState();
        restStove = stove.getBlockPos().immutable();
        goingToStove = false;
        getNavigation().stop();
        setKState(KState.RESTING);
        startHop(place, stove.facing().toYRot());
        say(!there.isEmpty() ? "shoo" : hasFlag(FLAG_STALE) ? "rest_stale" : "rest");
        return true;
    }

    /**
     * On the stove he gets back a point of health a second, or one in five seconds if he is stale,
     * until he is whole. If the stove goes cold or comes apart, he gets off.
     */
    private void restTick(ServerLevel level) {
        if (hop != null) {
            return;
        }
        if (tickCount % 20 == 0 && !onWarmStove(level)) {
            getOffStove(level, "stove_cold");
            return;
        }
        if (tickCount % (hasFlag(FLAG_STALE) ? HEAL_GAP_STALE : HEAL_GAP_FRESH) == 0) {
            heal(1.0F);
        }
        if (getHealth() >= getMaxHealth()) {
            getOffStove(level, "rested");
        }
    }

    /**
     * @return whether the stove he lies on still stands and is warm. One partly out of the loaded
     *         chunks is taken to be, so that looking at it loads nothing.
     */
    private boolean onWarmStove(ServerLevel level) {
        if (restStove == null) {
            return false;
        }
        if (!level.isLoaded(restStove)) {
            return true;
        }
        if (!(level.getBlockEntity(restStove) instanceof StoveBlockEntity stove)) {
            return false;
        }
        return !StovePattern.isLoaded(level, restStove, stove.facing())
                || stove.isWarm() && StovePattern.isIntact(level, restStove, stove.facing());
    }

    /**
     * Off the stove, with a hop down to free ground beside it: tamed, back to what he was doing;
     * untamed (only in older worlds, where a «killed» one rested there too), the chase starts over.
     */
    private void getOffStove(ServerLevel level, String line) {
        BlockPos mouth = restStove;
        restStove = null;
        nextStoveTrip = tickCount + STOVE_TRIP_GAP;
        if (isTame()) {
            setKState(afterRest == KState.WANDER || afterRest == KState.GUARD ? afterRest : KState.FOLLOW);
        } else {
            setKState(KState.RUNAWAY);
            chaseTicks = CHASE_STOP_TICKS - FIRST_STOP_TICKS;
        }
        BlockPos ground = mouth == null ? null : groundBelow(level, mouth);
        if (ground != null) {
            Vec3 to = Vec3.atBottomCenterOf(ground);
            Vec3 way = to.subtract(position());
            startHop(to, (float) Math.toDegrees(Math.atan2(-way.x, way.z)));
        }
        say(line);
    }

    /**
     * @return free ground beside the stove under the place he lies on, or {@code null} if there is
     *         none or the stove is gone: then he just rolls off
     */
    private @Nullable BlockPos groundBelow(ServerLevel level, BlockPos mouth) {
        if (!level.isLoaded(mouth) || !(level.getBlockEntity(mouth) instanceof StoveBlockEntity stove)
                || !StovePattern.isLoaded(level, mouth, stove.facing())) {
            return null;
        }
        Direction facing = stove.facing();
        List<Vec3> places = StovePattern.topSlots(mouth, facing);
        int nearest = 0;
        for (int i = 1; i < places.size(); i++) {
            if (places.get(i).distanceToSqr(position()) < places.get(nearest).distanceToSqr(position())) {
                nearest = i;
            }
        }
        if (places.get(nearest).distanceToSqr(position()) > 0.5 * 0.5) {
            return null;
        }
        for (BlockPos ground : StovePattern.beside(mouth, facing, StovePattern.TOP.get(nearest))) {
            if (canStandAt(level, ground)) {
                return ground;
            }
        }
        return null;
    }

    /**
     * Walks him onto that very point on the ground: by path while it is far, then the last steps
     * straight, since a path counts as done half a block short of its last block.
     *
     * @return whether he stands on it
     */
    private boolean walkOnto(Vec3 target, double speed) {
        double flat = position().multiply(1, 0, 1).distanceToSqr(target.multiply(1, 0, 1));
        boolean level = Math.abs(getY() - target.y) < 0.5;
        if (onGround() && level && flat < 0.3 * 0.3) {
            return true;
        }
        if (level && flat < 1.5 * 1.5) {
            getNavigation().stop();
            getMoveControl().setWantedPosition(target.x, target.y, target.z, speed);
        } else if (getNavigation().isDone() && tickCount % 10 == 0) {
            pathTo(target, speed);
        }
        return false;
    }

    /**
     * Sets off along a path to that point's block. Not {@code moveTo(x, y, z)}, whose path stops a
     * block short.
     */
    private void pathTo(Vec3 target, double speed) {
        getNavigation().moveTo(getNavigation().createPath(BlockPos.containing(target), 0), speed);
    }

    /**
     * @return whether he fits on that block, with something solid under it
     */
    private boolean canStandAt(Level level, BlockPos ground) {
        return level.noCollision(this, getDimensions(Pose.STANDING).makeBoundingBox(Vec3.atBottomCenterOf(ground)))
                && level.getBlockState(ground.below()).isFaceSturdy(level, ground.below(), Direction.UP);
    }

    // ------------------------------------------------------------------------------------ freshness

    /**
     * Once a second: three days after his last milk he goes stale.
     */
    private void freshnessTick(ServerLevel level) {
        boolean stale = level.getGameTime() - lastFed >= STALE_TICKS;
        if (stale != hasFlag(FLAG_STALE)) {
            setFlag(FLAG_STALE, stale);
            applyStale();
            if (stale) {
                say("stale");
            }
        }
    }

    /**
     * Milk: fresh again, for another three days.
     */
    private void freshen() {
        lastFed = level().getGameTime();
        if (hasFlag(FLAG_STALE)) {
            setFlag(FLAG_STALE, false);
            applyStale();
        }
    }

    /**
     * Stale, he rolls slower and his crust has hardened into armour.
     */
    private void applyStale() {
        boolean stale = hasFlag(FLAG_STALE);
        modifier(Attributes.MOVEMENT_SPEED, Kolobok.id("stale/speed"), -0.15,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE, stale);
        modifier(Attributes.ARMOR, Kolobok.id("stale/crust"), 4.0, AttributeModifier.Operation.ADD_VALUE, stale);
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return !isSheltered() && super.canBeSeenAsEnemy();
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return isSheltered() || hasPerk(Perk.CREEPER) || super.ignoreExplosion(explosion);
    }

    // ------------------------------------------------------------------------------------ verses

    /**
     * A mob hit him and he has no verse about it yet: if he gets away, he will have.
     */
    private void noteAttacker(ServerLevel level, DamageSource source) {
        if (source.getEntity() instanceof Mob mob) {
            noteAttacker(level, mob);
        }
    }

    /**
     * That mob is after him, by a hit, by his song or because he drew it off his owner.
     */
    private void noteAttacker(ServerLevel level, Mob mob) {
        if (mob instanceof Fox || mob instanceof KolobokEntity) {
            return;
        }
        String type = EntityType.getKey(mob.getType()).toString();
        if (verses().contains(type)) {
            return;
        }
        attacker = mob.getUUID();
        attackerType = type;
        attackerHit = level.getGameTime();
        attackerAimed = attackerHit;
    }

    private void forgetAttacker() {
        attacker = null;
        attackerType = null;
    }

    /**
     * Got away: within half a minute of the hit he is alive and the mob is gone, sixteen blocks off,
     * or has not gone for him for five seconds. Checked once a second.
     */
    private void verseTick(ServerLevel level) {
        if (attacker == null || attackerType == null) {
            return;
        }
        long now = level.getGameTime();
        if (now - attackerHit > VERSE_WINDOW) {
            forgetAttacker();
            return;
        }
        boolean away;
        if (!(level.getEntity(attacker) instanceof Mob mob) || !mob.isAlive()) {
            away = true;
        } else {
            if (isAfterMe(mob)) {
                attackerAimed = now;
            }
            away = mob.distanceToSqr(this) >= VERSE_DISTANCE * VERSE_DISTANCE || now - attackerAimed >= VERSE_LOST_TARGET;
        }
        if (away) {
            String type = attackerType;
            forgetAttacker();
            learnVerse(type);
        }
    }

    /**
     * Adds a verse about that mob to his song, with its perk if it has one, and tells the owner. He
     * sings the whole song with it as soon as he can.
     *
     * @return whether the verse is new
     */
    public boolean learnVerse(String mob) {
        // Herobrine is a rumour, and the fox is never sung about.
        if (level().isClientSide() || SongText.HEROBRINE.equals(mob) || Perk.FOX.mob().equals(mob) || verses().contains(mob)) {
            return false;
        }
        List<String> next = new ArrayList<>(verses());
        next.add(mob);
        entityData.set(DATA_VERSES, SongText.joinVerses(next));
        float health = getMaxHealth();
        applyPerks();
        if (getMaxHealth() > health) {
            heal(getMaxHealth() - health);
        }
        newVerseToSing = true;
        if (getOwner() instanceof ServerPlayer owner) {
            String key = SongText.knows(mob) ? "kolobok.verse.learned" : "kolobok.verse.learned_unknown";
            owner.sendSystemMessage(Component.translatable(key, getDisplayName(), SongWords.mob(mob)));
            Perk perk = Perk.forMob(mob);
            if (perk != null) {
                owner.sendSystemMessage(Component.translatable("kolobok.verse.perk",
                        Component.translatable(perk.key()), Component.translatable(perk.key() + ".desc")));
            }
        }
        return true;
    }

    // ------------------------------------------------------------------------------------ perks

    /**
     * The perks that are attribute modifiers, added or removed to match his verses. Safe to call
     * again: a modifier already there is left alone.
     */
    private void applyPerks() {
        perkModifier(Perk.RABBIT, Attributes.MOVEMENT_SPEED, 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        perkModifier(Perk.POLAR_BEAR, Attributes.MAX_HEALTH, 4.0, AttributeModifier.Operation.ADD_VALUE);
        perkModifier(Perk.ZOMBIE, Attributes.ARMOR, 2.0, AttributeModifier.Operation.ADD_VALUE);
        perkModifier(Perk.RAVAGER, Attributes.KNOCKBACK_RESISTANCE, 1.0, AttributeModifier.Operation.ADD_VALUE);
    }

    private void perkModifier(Perk perk, Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
        modifier(attribute, Kolobok.id("verse/" + perk.path()), amount, operation, hasPerk(perk));
    }

    /**
     * Adds or removes that modifier; one already there is left alone.
     */
    private void modifier(Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation,
                          boolean on) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (!on) {
            instance.removeModifier(id);
        } else if (!instance.hasModifier(id)) {
            instance.addPermanentModifier(new AttributeModifier(id, amount, operation));
        }
    }

    /**
     * The perks that act on their own, once a second: «Тут как тут» and «Бессонница».
     */
    private void perkTick(ServerLevel level) {
        if (!(getOwner() instanceof ServerPlayer owner) || owner.level() != level) {
            return;
        }
        lureTick(level, owner);
        KState state = getKState();
        if (hasPerk(Perk.ENDERMAN) && (state == KState.FOLLOW || state == KState.WANDER) && !goingToStove
                && escapade == Escapade.NONE && distanceToSqr(owner) > ENDER_RANGE * ENDER_RANGE) {
            Vec3 from = position();
            tryToTeleportToOwner();
            if (!position().equals(from)) {
                level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 0.3, from.z, 16, 0.3, 0.3, 0.3, 0.1);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.CHORUS_FRUIT_TELEPORT, getSoundSource(),
                        0.6F, 1.2F);
            }
        }
        if (hasPerk(Perk.PHANTOM) && tickCount >= nextPhantomWarning && level.isDarkOutside()
                && distanceToSqr(owner) < 16.0 * 16.0
                && owner.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)) >= SLEEPLESS_TICKS) {
            nextPhantomWarning = tickCount + PHANTOM_WARNING_GAP;
            say("phantoms");
        }
    }

    /**
     * A mob has just hit his owner: he calls it off onto himself — «Эй, я вкуснее!» — and rolls away
     * from it. If he gets away, it is a verse like any other.
     */
    private void lureTick(ServerLevel level, ServerPlayer owner) {
        KState state = getKState();
        if (tickCount < nextLure || !(state == KState.FOLLOW || state == KState.WANDER || state == KState.GUARD) || hop != null
                || distanceToSqr(owner) > LURE_RANGE * LURE_RANGE) {
            return;
        }
        if (!(owner.getLastHurtByMob() instanceof Mob mob) || owner.tickCount - owner.getLastHurtByMobTimestamp() > LURE_HIT_TICKS
                || mob.getTarget() == this || mob.distanceToSqr(this) > LURE_RANGE * LURE_RANGE || !mayProvoke(mob)) {
            return;
        }
        nextLure = tickCount + LURE_GAP;
        if (isSinging()) {
            stopSong();
        }
        provoke(level, mob);
        say("lure");
    }

    /**
     * @return whether a perk turns that damage away entirely
     */
    private boolean wardedOff(DamageSource source) {
        if (source.is(DamageTypeTags.IS_EXPLOSION) && hasPerk(Perk.CREEPER)) {
            return true;
        }
        if (source.is(DamageTypeTags.IS_FIRE) && hasPerk(Perk.BLAZE)) {
            return true;
        }
        if (source.is(DamageTypes.WITHER) && hasPerk(Perk.WITHER)) {
            return true;
        }
        if (source.getEntity() instanceof Witch && hasPerk(Perk.WITCH)
                && (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC))) {
            return true;
        }
        // «Сухарь»: every other arrow bounces off the crust.
        return source.is(DamageTypeTags.IS_PROJECTILE) && hasPerk(Perk.SKELETON) && random.nextBoolean();
    }

    @Override
    public boolean fireImmune() {
        return hasPerk(Perk.BLAZE) || super.fireImmune();
    }

    /**
     * «Кощеева закалка»: withering never takes; «Не та бабка»: a witch's splash potions do nothing.
     * Checked here rather than in {@code canBeAffected}, which NeoForge deprecates.
     */
    @Override
    public boolean addEffect(MobEffectInstance effect, @Nullable Entity source) {
        if (effect.is(MobEffects.WITHER) && hasPerk(Perk.WITHER) || source instanceof Witch && hasPerk(Perk.WITCH)) {
            return false;
        }
        return super.addEffect(effect, source);
    }

    /**
     * «Липкое тесто»: like a spider, he climbs whatever wall he rolls into.
     */
    @Override
    public boolean onClimbable() {
        return super.onClimbable() || hasPerk(Perk.SPIDER) && horizontalCollision && !isPinned();
    }

    @Override
    public boolean dampensVibrations() {
        return hasPerk(Perk.WARDEN) || super.dampensVibrations();
    }

    // ------------------------------------------------------------------------------------ shoulder and head

    private void carryTick() {
        Player carrier = getCarrier();
        if (carrier == null || !carrier.isAlive() || carrier.isSpectator() || carrier.isSleeping()
                || carrier.isFallFlying() || carrier.isInWater() && carrier.isSwimming()) {
            getOffPlayer(carrier != null && carrier.isAlive());
            return;
        }
        boolean shifting = carrier.isShiftKeyDown();
        if (shifting && !carrierWasShifting) {
            if (tickCount - lastCarrierShiftPress <= DOUBLE_SHIFT_TICKS) {
                carrierWasShifting = true;
                getOffPlayer(true);
                say("dismount");
                return;
            }
            lastCarrierShiftPress = tickCount;
        }
        carrierWasShifting = shifting;
        snapTo(carrier);
    }

    private void snapTo(Player carrier) {
        Vec3 spot = getRideSlot().spot(carrier, carrier.position(), carrier.yBodyRot);
        setPos(spot);
        setDeltaMovement(Vec3.ZERO);
        setYRot(carrier.yBodyRot);
        yBodyRot = carrier.yBodyRot;
        yHeadRot = carrier.getYHeadRot();
        fallDistance = 0;
    }

    private void getOnPlayer(Player player, RideSlot slot) {
        entityData.set(DATA_CARRIER, player.getId());
        entityData.set(DATA_RIDE_SLOT, (byte) slot.ordinal());
        setKState(KState.ON_PLAYER);
        getNavigation().stop();
        carrierWasShifting = player.isShiftKeyDown();
        lastCarrierShiftPress = -100;
        snapTo(player);
    }

    /**
     * @param carrierStillHere whether the player is around to follow; when he is not (logged out,
     *                         changed dimension), Kolobok sits down where he was dropped
     */
    private void getOffPlayer(boolean carrierStillHere) {
        Player carrier = getCarrier();
        entityData.set(DATA_CARRIER, -1);
        entityData.set(DATA_RIDE_SLOT, (byte) RideSlot.NONE.ordinal());
        setKState(carrierStillHere ? KState.FOLLOW : KState.SIT);
        noPhysics = false;
        if (carrier != null && carrierStillHere) {
            // Down in front of the player, where he can see him; at his feet if a wall is in the way.
            Vec3 look = Vec3.directionFromRotation(0, carrier.yBodyRot).scale(0.8);
            Vec3 front = carrier.position().add(look.x, 0, look.z);
            boolean free = level().noCollision(this, getDimensions(Pose.STANDING).makeBoundingBox(front));
            setPos(free ? front : carrier.position());
        }
    }

    /**
     * Shoulder, then head, then down — Shift and right-click with an empty hand. A
     * parrot keeps its shoulder; Kolobok takes the other one.
     */
    private void cycleRide(Player player) {
        RideSlot slot = getKState() == KState.ON_PLAYER ? getRideSlot() : RideSlot.NONE;
        switch (slot) {
            case NONE -> {
                RideSlot shoulder = player.getShoulderParrotRight().isEmpty()
                        ? RideSlot.SHOULDER_RIGHT
                        : player.getShoulderParrotLeft().isEmpty() ? RideSlot.SHOULDER_LEFT : RideSlot.HEAD;
                getOnPlayer(player, shoulder);
                say(shoulder == RideSlot.HEAD ? "head" : "shoulder");
            }
            case SHOULDER_LEFT, SHOULDER_RIGHT -> {
                getOnPlayer(player, RideSlot.HEAD);
                say("head");
            }
            case HEAD -> {
                getOffPlayer(true);
                say("dismount");
            }
        }
    }

    /**
     * On a player he can be clicked only while that player holds Shift. Otherwise he would catch every
     * click aimed past the player's own head.
     */
    @Override
    public boolean isPickable() {
        if (getKState() == KState.ON_PLAYER) {
            Player carrier = getCarrier();
            return carrier != null && carrier.isShiftKeyDown();
        }
        return super.isPickable();
    }

    @Override
    public boolean isPushable() {
        return !isPinned() && !isSheltered() && super.isPushable();
    }

    /**
     * On the shoulder his box overlaps the player's a little. Pushing would shove the player sideways
     * every tick (the client pushes its own player) and count him into entity cramming.
     */
    @Override
    protected void pushEntities() {
        if (!isPinned()) {
            super.pushEntities();
        }
    }

    @Override
    public boolean isNoGravity() {
        return isPinned() || super.isNoGravity();
    }

    // ------------------------------------------------------------------------------------ modes

    /**
     * The rolling pin's click: follow → follow with crumbs → guard → wander → follow.
     */
    private void nextMode() {
        KState state = getKState();
        boolean crumbs = hasFlag(FLAG_CRUMBS);
        String mode;
        if (state == KState.FOLLOW && !crumbs) {
            setFlag(FLAG_CRUMBS, true);
            mode = "crumbs";
        } else if (state == KState.FOLLOW) {
            setFlag(FLAG_CRUMBS, false);
            setKState(KState.GUARD);
            guardPost = blockPosition();
            mode = "guard";
        } else if (state == KState.GUARD) {
            setKState(KState.WANDER);
            guardPost = null;
            mode = "wander";
        } else {
            setFlag(FLAG_CRUMBS, false);
            setKState(KState.FOLLOW);
            guardPost = null;
            mode = "follow";
        }
        getNavigation().stop();
        say("mode_" + mode);
    }

    // ------------------------------------------------------------------------------------ interaction

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean owner = isTame() && isOwnedBy(player);

        if (getKState() == KState.HOT) {
            burn(player);
            return InteractionResult.SUCCESS;
        }
        if (getKState() == KState.RESTING && stack.isEmpty() && owner) {
            if (level() instanceof ServerLevel serverLevel && hop == null) {
                getOffStove(serverLevel, "woken");
            }
            return InteractionResult.SUCCESS;
        }
        // Called out of the горнило: he comes out and follows. Nothing else changes his state in there.
        if (getKState() == KState.IN_STOVE && owner && (stack.isEmpty() || stack.is(KItems.ROLLING_PIN.get()))) {
            if (level() instanceof ServerLevel serverLevel && hop == null) {
                afterVisit = KState.FOLLOW;
                visitTicks = 0;
                Direction facing = ovenFacing(serverLevel);
                if (facing != null && hopOut(serverLevel, facing, KState.FOLLOW)) {
                    say("stove_out");
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (getKState() == KState.CAUGHT) {
            if (!level().isClientSide()) {
                say("caught");
            }
            return InteractionResult.SUCCESS;
        }
        if (getKState() == KState.HEAD_ACT) {
            // Anyone may tickle the Head with a feather or a spear; its owner wakes it.
            if (stack.is(Items.FEATHER) || stack.is(ItemTags.SPEARS)) {
                if (level() instanceof ServerLevel serverLevel) {
                    sneeze(serverLevel);
                }
                return InteractionResult.SUCCESS;
            }
            if (stack.isEmpty() && owner) {
                if (!level().isClientSide()) {
                    endHeadAct("golova_end");
                }
                return InteractionResult.SUCCESS;
            }
        }
        if (stack.is(KItems.SONGBOOK.get())) {
            return writeSongbook(stack, owner);
        }
        // On the stove he only takes milk.
        if (getKState() == KState.RESTING && !stack.is(Items.MILK_BUCKET)) {
            return InteractionResult.PASS;
        }
        if (stack.is(Items.SHEARS) && owner && hasAnyOutfit()) {
            return shearOutfit(player, hand, stack);
        }
        if (isWearable(stack)) {
            return dress(player, stack, owner);
        }
        if (stack.isEmpty() && owner && player.isShiftKeyDown()) {
            if (!level().isClientSide()) {
                cycleRide(player);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.isEmpty() && owner && getKState() != KState.ON_PLAYER) {
            if (!level().isClientSide()) {
                boolean sit = getKState() != KState.SIT;
                setKState(sit ? KState.SIT : KState.FOLLOW);
                getNavigation().stop();
                say(sit ? "sit" : "follow");
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(KItems.ROLLING_PIN.get()) && owner && !player.isShiftKeyDown()
                && getKState() != KState.ON_PLAYER) {
            if (!level().isClientSide()) {
                nextMode();
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.MILK_BUCKET)) {
            return drinkMilk(player, hand, stack);
        }
        String refusal = refusalFor(stack);
        if (refusal != null) {
            if (!level().isClientSide()) {
                say(refusal);
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    /**
     * Milk is «сметанка»: it heals, keeps him fresh for three more days, and an untamed Kolobok whose
     * song the player has listened to may decide to stay. The first song gives the configured chance,
     * 1/3 by default; each further song adds half of it again.
     */
    private InteractionResult drinkMilk(Player player, InteractionHand hand, ItemStack stack) {
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (isTame() && getHealth() >= getMaxHealth() && !hasFlag(FLAG_STALE)) {
            say("full");
            return InteractionResult.SUCCESS;
        }
        int songs = songsListenedBy(player);
        if (!isTame() && songs == 0) {
            say("unheard");
            return InteractionResult.SUCCESS;
        }
        player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
        heal(6.0F);
        freshen();
        if (isTame()) {
            say("milk");
        } else if (random.nextFloat() < tameChance(songs)) {
            tame(player);
            setKState(KState.FOLLOW);
            stopSong();
            charmedTicks = 0;
            getNavigation().stop();
            level().broadcastEntityEvent(this, (byte) 7);
            say("tamed");
        } else {
            level().broadcastEntityEvent(this, (byte) 6);
            say("milk_untamed");
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * Writes his song and perks into the owner's songbook. The real stack in hand is written,
     * so this works in creative mode too.
     */
    private InteractionResult writeSongbook(ItemStack stack, boolean owner) {
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!owner) {
            say("songbook_stranger");
            return InteractionResult.SUCCESS;
        }
        SongbookItem.write(stack, this);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
        say("songbook");
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * @return the chance that milk tames him after that many songs listened to
     */
    public static float tameChance(int songs) {
        if (songs <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, (1.0F + (songs - 1) * 0.5F) / Math.max(1, KConfig.server().tameChanceDenominator));
    }

    private static @Nullable String refusalFor(ItemStack stack) {
        if (stack.is(Items.WHEAT)) {
            return "food_wheat";
        }
        if (stack.is(Items.BREAD)) {
            return "food_bread";
        }
        if (stack.is(Items.CAKE)) {
            return "food_cake";
        }
        if (stack.is(Items.PUMPKIN_PIE)) {
            return "food_pumpkin_pie";
        }
        if (stack.is(Items.TURTLE_HELMET)) {
            return "turtle";
        }
        // A pumpkin, a skull: things that go on a head, but not on his.
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable != null && equippable.slot() == EquipmentSlot.HEAD) {
            return "outfit_refuse";
        }
        return null;
    }

    // ------------------------------------------------------------------------------------ outfit

    /**
     * @return whether he would put that on: a helmet or one of his hats, boots, tinted glass
     */
    private static boolean isWearable(ItemStack stack) {
        if (stack.is(Items.TINTED_GLASS) || Outfit.isBoots(stack)) {
            return true;
        }
        Outfit.Hat hat = Outfit.hat(stack);
        return hat != null && hat != Outfit.Hat.NONE;
    }

    /**
     * @return whether he wears anything: sunglasses, a hat or лапти
     */
    public boolean hasAnyOutfit() {
        return hasFlag(FLAG_GLASSES) || !getItemBySlot(EquipmentSlot.HEAD).isEmpty() || !getItemBySlot(EquipmentSlot.FEET).isEmpty();
    }

    /**
     * Puts it on him, one of the stack: a hat or a helmet on his head, boots on his feet, tinted glass
     * over his eyes. What he wore there before goes back to the player. Only his owner dresses him.
     */
    private InteractionResult dress(Player player, ItemStack stack, boolean owner) {
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!owner) {
            say("dress_stranger");
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.TINTED_GLASS)) {
            if (hasFlag(FLAG_GLASSES)) {
                say("dress_same");
                return InteractionResult.SUCCESS;
            }
            stack.consume(1, player);
            setFlag(FLAG_GLASSES, true);
            playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 1.0F, 1.0F);
            gameEvent(GameEvent.EQUIP, player);
            say("dress_glasses");
            return InteractionResult.SUCCESS_SERVER;
        }
        boolean boots = Outfit.isBoots(stack);
        EquipmentSlot slot = boots ? EquipmentSlot.FEET : EquipmentSlot.HEAD;
        ItemStack old = getItemBySlot(slot);
        if (ItemStack.isSameItemSameComponents(old, stack)) {
            say("dress_same");
            return InteractionResult.SUCCESS;
        }
        // The game plays the item's own equip sound.
        setItemSlot(slot, stack.consumeAndReturn(1, player));
        if (!old.isEmpty() && !player.addItem(old)) {
            player.drop(old, false, Prediction.SERVER_ONLY);
        }
        Outfit.Hat hat = Outfit.hat(getItemBySlot(EquipmentSlot.HEAD));
        say(boots ? "dress_lapti" : "dress_" + (hat == null ? Outfit.Hat.NONE : hat).name().toLowerCase(Locale.ROOT));
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * Shears take one thing off him at a time, as off a wolf: the glasses, then the hat, then the
     * лапти. It falls at his feet.
     */
    private InteractionResult shearOutfit(Player player, InteractionHand hand, ItemStack shears) {
        if (!(level() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack off;
        if (hasFlag(FLAG_GLASSES)) {
            setFlag(FLAG_GLASSES, false);
            off = new ItemStack(Items.TINTED_GLASS);
        } else {
            EquipmentSlot slot = getItemBySlot(EquipmentSlot.HEAD).isEmpty() ? EquipmentSlot.FEET : EquipmentSlot.HEAD;
            off = getItemBySlot(slot);
            setItemSlot(slot, ItemStack.EMPTY);
        }
        shears.hurtAndBreak(1, player, hand);
        playSound(SoundEvents.SHEARS_SNIP, 1.0F, 1.0F);
        gameEvent(GameEvent.SHEAR, player);
        spawnAtLocation(level, off);
        say("undress");
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * Once a second. A dispenser may put on him what he will not wear: he spits it out. In лапти he
     * walks rather than rolls, and slower.
     */
    private void outfitTick(ServerLevel level) {
        ItemStack head = getItemBySlot(EquipmentSlot.HEAD);
        if (!head.isEmpty() && Outfit.hat(head) == null) {
            setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            spawnAtLocation(level, head);
            say(head.is(Items.TURTLE_HELMET) ? "turtle" : "outfit_refuse");
        }
        modifier(Attributes.MOVEMENT_SPEED, Kolobok.id("outfit/lapti"), LAPTI_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
                !getItemBySlot(EquipmentSlot.FEET).isEmpty());
    }

    /**
     * A dispenser dresses him too, but only in what goes on a head or on feet.
     */
    @Override
    protected boolean canDispenserEquipIntoSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.FEET;
    }

    // ------------------------------------------------------------------------------------ the Head

    /**
     * Once a second: sitting or on guard in a metal helmet, now and then he nods off and dreams he is
     * the Head from «Руслан и Людмила», the giant one in a helmet that blew the knight away.
     */
    private void maybeStartHeadAct(ServerLevel level) {
        KState state = getKState();
        if (tickCount < nextHeadActCheck || !isTame() || !(state == KState.SIT || state == KState.GUARD)) {
            return;
        }
        nextHeadActCheck = tickCount + HEAD_ACT_CHECK;
        if (random.nextInt(HEAD_ACT_CHANCE) == 0) {
            startHeadAct(level);
        }
    }

    /**
     * Grows into the Head and falls asleep, if he sits in a metal helmet with room above him to grow.
     *
     * @return whether he did
     */
    public boolean startHeadAct(ServerLevel level) {
        KState state = getKState();
        if (!(state == KState.SIT || state == KState.GUARD) || !Outfit.isMetalHelmet(getItemBySlot(EquipmentSlot.HEAD))
                || isSinging() || hop != null) {
            return false;
        }
        AABB box = getBoundingBox();
        if (!level.noCollision(this, box.setMaxY(box.maxY + HEAD_ACT_ROOM))) {
            return false;
        }
        afterHeadAct = state;
        headActTicks = HEAD_ACT_TICKS;
        golovaAwakeTicks = 0;
        getNavigation().stop();
        setKState(KState.HEAD_ACT);
        return true;
    }

    private void headActTick(ServerLevel level) {
        if (!Outfit.isMetalHelmet(getItemBySlot(EquipmentSlot.HEAD)) || --headActTicks <= 0) {
            endHeadAct("golova_end");
            return;
        }
        if (golovaAwakeTicks > 0) {
            golovaAwakeTicks--;
        } else if (headActTicks % SNORE_GAP == 0) {
            playSound(KSounds.SNORE.get(), 0.7F, voice());
        }
    }

    /**
     * Wakes up from being the Head, back to sitting or guarding: «Приснится же…».
     */
    private void endHeadAct(String line) {
        headActTicks = 0;
        golovaAwakeTicks = 0;
        setKState(afterHeadAct == KState.GUARD ? KState.GUARD : KState.SIT);
        say(line);
    }

    /**
     * @return whether he is the Head now, for the renderer
     */
    public boolean isHead() {
        return getKState() == KState.HEAD_ACT;
    }

    /**
     * Tickled, the Head sneezes: «Куда ты, витязь неразумный?» A gust blows everyone in front of it a
     * few blocks back, without a scratch.
     */
    private void sneeze(ServerLevel level) {
        if (tickCount < nextSneeze) {
            return;
        }
        nextSneeze = tickCount + SNEEZE_GAP;
        sneezeEcho = tickCount + SNEEZE_ECHO_TICKS;
        golovaAwakeTicks = GOLOVA_AWAKE_TICKS;
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
        Vec3 mouth = position().add(0.0, getBbHeight() * 0.6, 0.0).add(forward.scale(0.8));
        playSound(KSounds.SNEEZE.get(), 1.0F, voice());
        level.sendParticles(ParticleTypes.GUST, mouth.x, mouth.y, mouth.z, 1, 0.0, 0.0, 0.0, 0.0);
        for (int i = 1; i <= 4; i++) {
            Vec3 puff = mouth.add(forward.scale(i * 1.2));
            level.sendParticles(ParticleTypes.SMALL_GUST, puff.x, puff.y, puff.z, 2, 0.3 * i, 0.2 * i, 0.3 * i, 0.0);
        }
        double cone = Math.cos(SNEEZE_HALF_ANGLE);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SNEEZE_RANGE),
                entity -> entity != this && entity.getVehicle() != this && entity.isAlive() && !entity.isSpectator())) {
            Vec3 way = entity.position().subtract(position()).multiply(1.0, 0.0, 1.0);
            double distance = way.length();
            if (distance > SNEEZE_RANGE || distance > 1.0E-3 && way.scale(1.0 / distance).dot(forward) < cone) {
                continue;
            }
            entity.knockback(SNEEZE_PUSH, getX() - entity.getX(), getZ() - entity.getZ(), damageSources().mobAttack(this), 0.0F);
        }
        say("golova_sneeze");
    }

    /**
     * Hit by a player, the Head rolls off — «голова покатилась» — and wakes up. The first time, where
     * it lay there is a sword: the Меч-кладенец, wooden but his own.
     */
    private void headRollsOff(ServerLevel level, Player player) {
        boolean sword = !kladenetsGiven;
        if (sword) {
            kladenetsGiven = true;
            ItemStack kladenets = new ItemStack(Items.WOODEN_SWORD);
            kladenets.set(DataComponents.ITEM_NAME, Component.translatable("item.kolobok.kladenets"));
            kladenets.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.kolobok.kladenets.lore"))));
            kladenets.set(DataComponents.RARITY, Rarity.UNCOMMON);
            spawnAtLocation(level, kladenets);
        }
        endHeadAct(sword ? "golova_kladenets" : "golova_hit");
        Vec3 away = position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
        if (away.lengthSqr() < 1.0E-4) {
            away = Vec3.directionFromRotation(0.0F, player.getYRot());
        }
        away = away.normalize().scale(HEAD_ROLL_SPEED);
        setDeltaMovement(away.x, 0.35, away.z);
        needsSync = true;
    }

    /**
     * @return whether the Head has given away its sword
     */
    public boolean gaveKladenets() {
        return kladenetsGiven;
    }

    // ------------------------------------------------------------------------------------ escapades

    /**
     * @return whether he may be on an escapade now: tamed, following or wandering, whole enough, not
     *         on his way to a stove; following, with his owner near enough
     */
    private boolean mayEscape() {
        KState state = getKState();
        if (!isTame() || !KConfig.server().escapades || !(state == KState.FOLLOW || state == KState.WANDER)
                || goingToStove || hop != null || isPassenger() || getHealth() < getMaxHealth() / 2.0F) {
            return false;
        }
        if (state == KState.WANDER) {
            return true;
        }
        return getOwner() instanceof Player owner && owner.level() == level() && !owner.isSpectator()
                && distanceToSqr(owner) <= ESCAPADE_LEASH * ESCAPADE_LEASH;
    }

    /**
     * @return the animal to sing to: of the peaceful ones and villagers he can see, the one with the
     *         biggest herd round it, within the area his owner keeps alive; {@code null} if none
     */
    private @Nullable Mob findAudience(ServerLevel level) {
        Player owner = getKState() == KState.FOLLOW && getOwner() instanceof Player player ? player : null;
        double ownerRange = Math.max(1, level.getServer().getPlayerList().getSimulationDistance() - 1) * 16.0;
        List<Mob> seen = level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(ESCAPADE_RANGE, 8.0, ESCAPADE_RANGE),
                mob -> isAudience(mob) && !fears(mob) && distanceToSqr(mob) <= ESCAPADE_RANGE * ESCAPADE_RANGE
                        && (owner == null || mob.position().multiply(1.0, 0.0, 1.0)
                        .distanceToSqr(owner.position().multiply(1.0, 0.0, 1.0)) <= ownerRange * ownerRange));
        Mob best = null;
        int bestHerd = 0;
        int looked = 0;
        Collections.shuffle(seen, new Random(random.nextLong()));
        for (Mob mob : seen) {
            if (looked++ >= 8) {
                break;
            }
            int herd = 0;
            for (Mob other : seen) {
                if (other.distanceToSqr(mob) <= HERD_RANGE * HERD_RANGE) {
                    herd++;
                }
            }
            if (herd > bestHerd && hasLineOfSight(mob)) {
                best = mob;
                bestHerd = herd;
            }
        }
        return best;
    }

    private void endEscapade() {
        if (escapade == Escapade.NONE) {
            return;
        }
        escapade = Escapade.NONE;
        escapadeAudience = null;
        nextEscapade = tickCount + ESCAPADE_GAP + random.nextInt(ESCAPADE_CHECK);
        getNavigation().stop();
    }

    /**
     * Has him think about running off at the next chance, and go if he can; for tests.
     */
    public void escapeNow() {
        nextEscapade = 0;
        forceEscapade = true;
    }

    /**
     * @return where his escapade has got to, for tests: none, going, lingering or coming back
     */
    public String escapadePhase() {
        return escapade.name().toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------------------------ damage

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        // On a player he is out of the world's way: a low ceiling or a fall is the player's problem.
        if (getKState() == KState.ON_PLAYER
                && (source.is(DamageTypes.IN_WALL) || source.is(DamageTypeTags.IS_FALL))) {
            return false;
        }
        boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        // A fox's bite is no wound: it carries him off.
        if (!bypass && source.getEntity() instanceof Fox fox && foxMayHunt()) {
            getCaught(fox);
            return false;
        }
        // Fresh from the oven nothing hurts him, and whoever hits him burns a hand.
        if (getKState() == KState.HOT && !bypass) {
            if (source.getDirectEntity() instanceof Player player) {
                burn(player);
            }
            return false;
        }
        if (!bypass && (isSheltered() || wardedOff(source))) {
            return false;
        }
        if (getKState() == KState.HEAD_ACT && !bypass) {
            if (source.getEntity() instanceof Player player) {
                headRollsOff(level, player);
                return false;
            }
            // A mob's bite wakes the Head; the bite itself is real.
            endHeadAct("golova_end");
        }
        boolean hurt = super.hurtServer(level, source, damage);
        if (!hurt || isSheltered() || !isAlive()) {
            return hurt;
        }
        if (isSinging()) {
            stopSong();
        }
        noteAttacker(level, source);
        // Fire or a cactus hurt him every few ticks; one «Ой!» per two seconds is plenty.
        if ((int) level.getGameTime() >= getSayUntil() - SAY_TICKS + HURT_LINE_GAP) {
            say("hurt");
        }
        return hurt;
    }

    // ------------------------------------------------------------------------------------ voice

    /**
     * Voice = dough × outfit × mood, clamped to 0.67–1.33 so the one-octave melody stays inside the
     * game's 0.5–2.0 pitch range.
     */
    public float voice() {
        return Outfit.pitch(getVariant(), Outfit.voice(getItemBySlot(EquipmentSlot.HEAD)), getMood());
    }

    @Override
    public float getVoicePitch() {
        return voice() * (1.0F + (random.nextFloat() - random.nextFloat()) * 0.05F);
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        // He talks instead; see idleChatter().
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return KSounds.mumble(Phonetics.syllables("ой").getFirst());
    }

    @Override
    protected SoundEvent getDeathSound() {
        return KSounds.mumble(new Syllable(Syllable.NO_ONSET, 'а', true));
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {
        // Rolling bread makes no footsteps; the crust crunch arrives with the other sounds.
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.NEUTRAL;
    }

    // ------------------------------------------------------------------------------------ goal classes

    /**
     * Stands still while he sings, while his head spins and for a moment after a song someone
     * listened to, facing the nearest player.
     */
    private final class SingGoal extends Goal {

        SingGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return isSinging() || dizzyTicks > 0 || charmedTicks > 0;
        }

        @Override
        public void start() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            Player player = level().getNearestPlayer(KolobokEntity.this, 8.0);
            if (player != null) {
                getLookControl().setLookAt(player);
            }
        }
    }

    /**
     * In guard mode he keeps to his post.
     */
    private final class ReturnToPostGoal extends Goal {

        ReturnToPostGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return getKState() == KState.GUARD && guardPost != null
                    && distanceToSqr(Vec3.atBottomCenterOf(guardPost)) > GUARD_RANGE * GUARD_RANGE;
        }

        @Override
        public boolean canContinueToUse() {
            return getKState() == KState.GUARD && !getNavigation().isDone();
        }

        @Override
        public void start() {
            Vec3 post = Vec3.atBottomCenterOf(guardPost);
            getNavigation().moveTo(post.x, post.y, post.z, 1.0);
        }
    }

    /**
     * Runs from a mob that is after him: one his song annoyed, one he drew off his owner, or any
     * other with its eye on him.
     */
    private final class GetAwayGoal extends Goal {

        private @Nullable Mob from;

        GetAwayGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (tickCount % 5 != 0 || !keepsAway() || !(level() instanceof ServerLevel level)) {
                return false;
            }
            from = level.getNearestEntity(level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(GET_AWAY_RANGE),
                    mob -> mob.isAlive() && isAfterMe(mob)), getX(), getY(), getZ());
            if (from == null) {
                return false;
            }
            Vec3 away = DefaultRandomPos.getPosAway(KolobokEntity.this, 12, 6, from.position());
            if (away == null || from.distanceToSqr(away) < from.distanceToSqr(KolobokEntity.this)) {
                return false;
            }
            return getNavigation().moveTo(away.x, away.y, away.z, 1.35);
        }

        @Override
        public boolean canContinueToUse() {
            return keepsAway() && from != null && from.isAlive() && !getNavigation().isDone();
        }

        @Override
        public void stop() {
            from = null;
        }
    }

    /**
     * Tamed, he now and then climbs into the горнило of a cold, empty stove nearby and sits there a
     * while. Following, only while his owner is close.
     */
    private final class VisitStoveGoal extends Goal {

        private static final int WALK_TICKS = 200;

        private @Nullable StoveBlockEntity stove;
        private int giveUp;

        VisitStoveGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
        }

        private boolean visiting() {
            KState state = getKState();
            return isTame() && (state == KState.FOLLOW || state == KState.WANDER) && !isSinging() && hop == null;
        }

        @Override
        public boolean canUse() {
            if (tickCount < nextVisitCheck || !visiting() || !onGround()) {
                return false;
            }
            nextVisitCheck = tickCount + VISIT_CHECK_MIN + random.nextInt(VISIT_CHECK_SPREAD);
            if (getKState() == KState.FOLLOW
                    && !(getOwner() instanceof Player owner && distanceToSqr(owner) < 8.0 * 8.0)) {
                return false;
            }
            for (StoveBlockEntity candidate : StovePattern.nearby(level(), blockPosition(), VISIT_RANGE)) {
                if (canVisit(candidate)) {
                    stove = candidate;
                    return true;
                }
            }
            return false;
        }

        private boolean canVisit(StoveBlockEntity candidate) {
            BlockPos mouth = candidate.getBlockPos();
            Direction facing = candidate.facing();
            if (!candidate.canTakeVisitor() || !StovePattern.isIntact(level(), mouth, facing)) {
                return false;
            }
            BlockPos front = StovePattern.front(mouth, facing);
            if (!level().noCollision(KolobokEntity.this, getDimensions(Pose.STANDING).makeBoundingBox(Vec3.atBottomCenterOf(front)))) {
                return false;
            }
            var path = getNavigation().createPath(front, 0);
            return path != null && path.canReach();
        }

        @Override
        public void start() {
            giveUp = WALK_TICKS;
            walk();
        }

        private void walk() {
            pathTo(Vec3.atBottomCenterOf(StovePattern.front(stove.getBlockPos(), stove.facing())), 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return stove != null && giveUp > 0 && visiting() && stove.canTakeVisitor();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            // Climbed in, he froze with this goal still running; the odd ticks that tick only such
            // goals come back before the check that would stop it.
            if (stove == null) {
                return;
            }
            giveUp--;
            Vec3 front = Vec3.atBottomCenterOf(StovePattern.front(stove.getBlockPos(), stove.facing()));
            if (walkOnto(front, 1.0)) {
                climbIntoOven(stove.getBlockPos(), stove.facing());
                stove = null;
            }
        }

        @Override
        public void stop() {
            stove = null;
            if (getKState() != KState.IN_STOVE) {
                getNavigation().stop();
            }
        }
    }

    /**
     * Below half his health, a tamed Kolobok sets off for the nearest warm stove within the
     * simulation distance that has a place free on top, any stove at all, rolls up beside it and hops
     * onto the place. If only a cat sits in the way, it is shooed off. A stove he gets no nearer to,
     * he gives up on for a while.
     */
    private final class WarmUpGoal extends Goal {

        private @Nullable StoveBlockEntity stove;
        private Vec3 place = Vec3.ZERO;
        private BlockPos launch = BlockPos.ZERO;
        private double nearest;
        private int stuck;

        WarmUpGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
        }

        private boolean free() {
            KState state = getKState();
            return isTame() && (state == KState.FOLLOW || state == KState.WANDER || state == KState.GUARD)
                    && !isSinging() && hop == null;
        }

        @Override
        public boolean canUse() {
            if (tickCount < nextStoveTrip || getHealth() >= getMaxHealth() / 2.0F || !free()
                    || !(level() instanceof ServerLevel level)) {
                return false;
            }
            // Not «tickCount % 20»: goals are looked at every other tick, odd or even by the entity's id.
            nextStoveTrip = tickCount + STOVE_CHECK;
            double range = level.getServer().getPlayerList().getSimulationDistance() * 16.0;
            List<StoveBlockEntity> stoves = StoveBlockEntity.loaded(level);
            stoves.sort(Comparator.comparingDouble(s -> s.getBlockPos().distToCenterSqr(position())));
            for (StoveBlockEntity candidate : stoves) {
                if (candidate.getBlockPos().distToCenterSqr(position()) > range * range) {
                    break;
                }
                if (pick(level, candidate, false) || pick(level, candidate, true)) {
                    stove = candidate;
                    return true;
                }
            }
            nextStoveTrip = tickCount + STOVE_RETRY;
            return false;
        }

        /**
         * Picks a place on that stove, and free ground beside it to hop up from.
         *
         * @param shooing whether a place with only a cat on it will do
         */
        private boolean pick(ServerLevel level, StoveBlockEntity candidate, boolean shooing) {
            BlockPos mouth = candidate.getBlockPos();
            Direction facing = candidate.facing();
            if (candidate.isRemoved() || !candidate.isWarm() || !StovePattern.isLoaded(level, mouth, facing)
                    || !StovePattern.isIntact(level, mouth, facing)) {
                return false;
            }
            List<Vec3> places = StovePattern.topSlots(mouth, facing);
            for (int i = 0; i < places.size(); i++) {
                List<LivingEntity> there = CatOnStoveGoal.sittingOn(level, places.get(i), KolobokEntity.this);
                boolean free = shooing
                        ? !there.isEmpty() && there.stream().allMatch(entity -> entity instanceof Cat)
                        : there.isEmpty();
                if (!free) {
                    continue;
                }
                for (BlockPos ground : StovePattern.beside(mouth, facing, StovePattern.TOP.get(i))) {
                    if (canStandAt(level, ground)) {
                        place = places.get(i);
                        launch = ground;
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public void start() {
            goingToStove = true;
            nearest = Double.MAX_VALUE;
            stuck = 0;
            walk();
            say("to_stove");
        }

        /** A far stove is beyond one path: each walk gets him as near as it can, then the next. */
        private void walk() {
            pathTo(Vec3.atBottomCenterOf(launch), 1.1);
        }

        @Override
        public boolean canContinueToUse() {
            return stove != null && !stove.isRemoved() && stove.isWarm() && free() && stuck < STOVE_STUCK_TICKS;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            // As with the горнило: on the stove he froze with this goal still running.
            if (stove == null) {
                return;
            }
            Vec3 target = Vec3.atBottomCenterOf(launch);
            if (walkOnto(target, 1.1)) {
                climbOntoStove(stove, place);
                stove = null;
                return;
            }
            double distance = position().distanceTo(target);
            if (distance < nearest - 0.25) {
                nearest = distance;
                stuck = 0;
            } else {
                stuck++;
            }
        }

        @Override
        public void stop() {
            goingToStove = false;
            stove = null;
            if (getKState() != KState.RESTING) {
                // Run off, his place taken or the stove cold, he looks again soon; stuck, not for a while.
                nextStoveTrip = Math.max(nextStoveTrip, tickCount + (stuck >= STOVE_STUCK_TICKS ? STOVE_TRIP_GAP : STOVE_RETRY));
                getNavigation().stop();
            }
        }
    }

    /** Where an escapade has got to. */
    private enum Escapade {
        NONE,
        /** Rolling off to the animals, or to a spot to look about. */
        GOING,
        /** Sung to them, or got there: hanging about a while. */
        LINGERING,
        /** Following: rolling back to his owner. */
        RETURNING
    }

    /**
     * Escapades: «Я от бабушки ушёл». Following or wandering, a tamed Kolobok now and then runs off.
     * Animals he can see are an audience: he rolls up to the biggest herd, sings them his song, which
     * gathers them round, hangs about a while and, if he follows someone, rolls back. With nobody to
     * sing to he sometimes just rolls off a little way to look about. A far trip is walked a path at a
     * time, as to a stove; one he gets no nearer on he gives up.
     *
     * <p>The song takes over from this goal while it lasts, so where the escapade has got to is kept
     * on the entity, and the goal picks it up again afterwards.
     */
    private final class EscapadeGoal extends Goal {

        private double nearest;
        private int stuck;
        private int nextRoam;

        EscapadeGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (escapade != Escapade.NONE) {
                if (mayEscape()) {
                    return true;
                }
                endEscapade();
                return false;
            }
            if (tickCount < nextEscapade || !mayEscape() || !onGround() || isSinging() || isInWater()
                    || !(level() instanceof ServerLevel level)) {
                return false;
            }
            nextEscapade = tickCount + ESCAPADE_CHECK + random.nextInt(ESCAPADE_CHECK);
            boolean forced = forceEscapade;
            forceEscapade = false;
            Mob audience = findAudience(level);
            if (audience != null && (forced || random.nextInt(ESCAPADE_CHANCE) == 0)) {
                escapadeAudience = audience;
                escapadeSpot = audience.position();
                escapade = Escapade.GOING;
                say("escapade");
                return true;
            }
            if (audience == null && (forced || random.nextInt(ESCAPADE_ALONE_CHANCE) == 0)) {
                Vec3 spot = LandRandomPos.getPos(KolobokEntity.this, ESCAPADE_ALONE_RANGE, 7);
                if (spot != null && spot.distanceToSqr(position()) >= (ESCAPADE_ALONE_RANGE / 2.0) * (ESCAPADE_ALONE_RANGE / 2.0)) {
                    escapadeAudience = null;
                    escapadeSpot = spot;
                    escapade = Escapade.GOING;
                    say("escapade_alone");
                    return true;
                }
            }
            return false;
        }

        @Override
        public void start() {
            nearest = Double.MAX_VALUE;
            stuck = 0;
            nextRoam = 0;
        }

        @Override
        public boolean canContinueToUse() {
            return escapade != Escapade.NONE && mayEscape() && stuck < ESCAPADE_STUCK_TICKS;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            switch (escapade) {
                case GOING -> going();
                case LINGERING -> lingering();
                case RETURNING -> returning();
                default -> {
                }
            }
        }

        /** Rolls to the animals, following them if they wander, and sings once he is there. */
        private void going() {
            Mob audience = escapadeAudience;
            if (audience != null) {
                if (!audience.isAlive() || audience.isRemoved()) {
                    // The audience is gone: he looks about where it was.
                    escapadeAudience = null;
                } else {
                    escapadeSpot = audience.position();
                }
            }
            double arrive = audience != null ? CONCERT_ARRIVE : 1.5;
            if (distanceToSqr(escapadeSpot) <= arrive * arrive) {
                getNavigation().stop();
                escapade = Escapade.LINGERING;
                escapadeSpot = position();
                escapadeTicks = LINGER_MIN + random.nextInt(LINGER_SPREAD);
                if (audience != null && level() instanceof ServerLevel level && hasAudience(level)) {
                    startSong(0);
                    // The concert was his song for now.
                    nextSong = Math.max(nextSong, SONG_GAP_MIN);
                }
                return;
            }
            walkTowards(escapadeSpot, 1.2);
        }

        /** Hangs about where he sang, rolling from place to place, then sets off back. */
        private void lingering() {
            if (--escapadeTicks <= 0) {
                if (getKState() == KState.FOLLOW) {
                    escapade = Escapade.RETURNING;
                    nearest = Double.MAX_VALUE;
                    stuck = 0;
                    say("escapade_back");
                } else {
                    endEscapade();
                }
                return;
            }
            if (tickCount >= nextRoam && getNavigation().isDone()) {
                nextRoam = tickCount + 60 + random.nextInt(80);
                Vec3 roam = DefaultRandomPos.getPosTowards(KolobokEntity.this, LINGER_ROAM, 3, escapadeSpot, Math.PI / 2.0);
                if (roam != null) {
                    getNavigation().moveTo(roam.x, roam.y, roam.z, 0.8);
                }
            }
        }

        /** Rolls back until his owner is close; one he cannot get to fetches him the vanilla way. */
        private void returning() {
            if (!(getOwner() instanceof Player owner)) {
                endEscapade();
                return;
            }
            if (distanceToSqr(owner) <= ESCAPADE_HOME * ESCAPADE_HOME) {
                endEscapade();
                say("escapade_home");
                return;
            }
            walkTowards(owner.position(), 1.1);
        }

        /** A path at a time towards it; no nearer for ten seconds and he gives up. */
        private void walkTowards(Vec3 target, double speed) {
            if (getNavigation().isDone() || tickCount % 20 == 0) {
                pathTo(target, speed);
            }
            double distance = position().distanceTo(target);
            if (distance < nearest - 0.25) {
                nearest = distance;
                stuck = 0;
            } else {
                stuck++;
            }
        }

        @Override
        public void stop() {
            getNavigation().stop();
            // Given up, or the owner called him off; a song only pauses the escapade.
            if (stuck >= ESCAPADE_STUCK_TICKS || !mayEscape()) {
                endEscapade();
            }
        }
    }

    /**
     * As in the tale, he is not afraid of the fox at first: now and then he rolls up to one to say
     * hello. Rare: a check a second, one in three hundred.
     */
    private final class FoxCuriosityGoal extends Goal {

        private @Nullable Fox fox;

        FoxCuriosityGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            KState state = getKState();
            // Shifted by the id as vanilla shifts the ticks it looks at goals on, or half of all Kolobki
            // would never get here.
            if ((tickCount + getId()) % 20 != 0 || !(level() instanceof ServerLevel level) || !foxMayHunt() || isSinging()
                    || random.nextInt(FOX_CURIOSITY_CHANCE) != 0
                    || !(state == KState.FOLLOW || state == KState.WANDER || state == KState.RUNAWAY)) {
                return false;
            }
            fox = level.getNearestEntity(Fox.class, TargetingConditions.forNonCombat(), KolobokEntity.this,
                    getX(), getY(), getZ(), getBoundingBox().inflate(FOX_CURIOSITY_RANGE));
            return fox != null;
        }

        @Override
        public void start() {
            curiousAboutFox = true;
            getNavigation().moveTo(fox, 0.9);
            say("fox_lure");
        }

        @Override
        public boolean canContinueToUse() {
            return fox != null && fox.isAlive() && foxMayHunt() && !getNavigation().isDone() && distanceToSqr(fox) > 2.0 * 2.0;
        }

        @Override
        public void stop() {
            curiousAboutFox = false;
            fox = null;
        }
    }
}
