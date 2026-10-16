package ru.bagomot.kolobok.entity;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
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
import net.minecraft.world.entity.Mob;
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
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import ru.bagomot.kolobok.Kolobok;
import ru.bagomot.kolobok.block.StovePart;
import ru.bagomot.kolobok.block.StovePartBlock;
import ru.bagomot.kolobok.block.StovePattern;
import ru.bagomot.kolobok.config.KConfig;
import ru.bagomot.kolobok.entity.song.Phonetics;
import ru.bagomot.kolobok.entity.song.Song;
import ru.bagomot.kolobok.entity.song.SongText;
import ru.bagomot.kolobok.entity.song.Syllable;
import ru.bagomot.kolobok.entity.song.Voice;
import ru.bagomot.kolobok.item.SongbookItem;
import ru.bagomot.kolobok.registry.KItems;
import ru.bagomot.kolobok.registry.KSounds;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Колобок. A tamable pet that never really dies, sings his way out of trouble and rides on his
 * owner's shoulder.
 *
 * <p>He is born in the Russian stove: hot and asleep on the hearth, then, cooled, he opens his eyes
 * and rolls away from whoever is looking. Every twenty seconds of the chase his head spins and he
 * stops to sing a verse. A player who crouches beside him and keeps still for the whole verse has
 * listened; milk then may tame him, and every song listened to makes that likelier.
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
    /** A tamed Kolobok sings on his own every three to six minutes. */
    private static final int SONG_GAP_MIN = 3600;
    private static final int SONG_GAP_MAX = 7200;
    /** In guard mode he walks back once he is this far from his post. */
    private static final double GUARD_RANGE = 3.0;
    /** Herobrine sneaks into one night song in fifty. */
    private static final float HEROBRINE_CHANCE = 0.02F;

    /** After a «death» he lies on his stove for five minutes… */
    private static final int REST_TICKS = 6000;
    /** …or, with no stove to get to, is a pancake for a minute. */
    private static final int PANCAKE_TICKS = 1200;
    /** Mobs this close forget about a pancake. */
    private static final double PANCAKE_FORGET_RANGE = 16.0;
    /** A verse is earned by being alive half a minute after a mob's hit at most… */
    private static final int VERSE_WINDOW = 600;
    /** …and either this far from the mob… */
    private static final double VERSE_DISTANCE = 16.0;
    /** …or the mob not having gone for him this long. */
    private static final int VERSE_LOST_TARGET = 100;
    /** «Тут как тут»: left this far behind, he turns up next to his owner. */
    private static final double ENDER_RANGE = 24.0;
    /** «Бессонница»: phantoms come for a player who has not slept for three days. */
    private static final int SLEEPLESS_TICKS = 72000;
    private static final int PHANTOM_WARNING_GAP = 6000;

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

    private int nextIdleChatter;
    private boolean carrierWasShifting;
    private int lastCarrierShiftPress = -100;

    /** HOT: ticks until he has cooled. */
    private int hotTicks;
    /** The mouth of the stove he was baked in, and its dimension: his home. */
    private @Nullable BlockPos home;
    private @Nullable ResourceKey<Level> homeDimension;
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

    /** RESTING or PANCAKE: ticks left. */
    private int escapeTicks;
    /** The last mob that hit him whose verse he does not have yet, and when. */
    private @Nullable UUID attacker;
    private @Nullable String attackerType;
    private long attackerHit;
    /** When that mob last had him as its target. */
    private long attackerAimed;
    /** A verse was earned: the whole song comes as soon as he can sing it. */
    private boolean newVerseToSing;
    private int nextPhantomWarning;
    /** {@link #DATA_VERSES} parsed, and the string it was parsed from. */
    private String versesRaw = "";
    private List<String> verses = List.of();

    public KolobokEntity(EntityType<? extends KolobokEntity> type, Level level) {
        super(type, level);
        // Spread out, or every Kolobok loaded with the world speaks up on the same tick.
        nextIdleChatter = 200 + random.nextInt(1200);
        nextSong = SONG_GAP_MIN + random.nextInt(SONG_GAP_MAX - SONG_GAP_MIN);
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
        return verses().contains(perk.mob());
    }

    /**
     * @return the mouth of the stove he was baked in, or {@code null}
     */
    public @Nullable BlockPos getHome() {
        return home;
    }

    /**
     * Makes that stove his home: he runs back to it when «killed».
     */
    public void setHome(BlockPos stove) {
        home = stove.immutable();
        homeDimension = level().dimension();
    }

    // ------------------------------------------------------------------------------------ save

    private static final Codec<Map<UUID, Integer>> LISTENED_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT);

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Variant", getVariant().serializedName());
        output.putByte("Flags", entityData.get(DATA_FLAGS));
        // Sitting on a player is a session thing: the carrier is gone after a restart.
        KState state = getKState() == KState.ON_PLAYER ? KState.FOLLOW : getKState();
        output.putString("State", state.name());
        output.putInt("HotTicks", hotTicks);
        output.storeNullable("Home", BlockPos.CODEC, home);
        output.storeNullable("HomeDimension", Level.RESOURCE_KEY_CODEC, homeDimension);
        output.storeNullable("GuardPost", BlockPos.CODEC, guardPost);
        output.store("Listened", LISTENED_CODEC, listened);
        output.putInt("NextVerse", nextVerse);
        output.store("Verses", Codec.STRING.listOf(), verses());
        output.putInt("EscapeTicks", escapeTicks);
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
        home = input.read("Home", BlockPos.CODEC).orElse(null);
        homeDimension = input.read("HomeDimension", Level.RESOURCE_KEY_CODEC).orElse(null);
        guardPost = input.read("GuardPost", BlockPos.CODEC).orElse(null);
        listened.clear();
        listened.putAll(input.read("Listened", LISTENED_CODEC).orElse(Map.of()));
        nextVerse = input.getIntOr("NextVerse", 1);
        entityData.set(DATA_VERSES, SongText.joinVerses(input.read("Verses", Codec.STRING.listOf()).orElse(List.of())));
        escapeTicks = input.getIntOr("EscapeTicks", 0);
        applyPerks();
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
     * Fresh out of the stove: hot, asleep on the hearth until he cools. The stove places him first.
     *
     * @param stove the stove's mouth, from now on his home
     */
    public void bake(Variant variant, boolean burnt, BlockPos stove, int coolTicks) {
        setVariant(variant);
        setFlag(FLAG_BURNT, burnt);
        setKState(KState.HOT);
        hotTicks = coolTicks;
        setHome(stove);
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
        goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        goalSelector.addGoal(3, new SingGoal());
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
     * While hot, resting, flat or on a player, the whole AI step is skipped: nothing may move him.
     * Hot, he lies on the hearth; on a player, the server places him itself every tick.
     */
    @Override
    protected boolean isImmobile() {
        return getKState() == KState.ON_PLAYER || isSheltered() || super.isImmobile();
    }

    /** Neither the world nor a push moves him: he is on a player or lying on the hearth. */
    private boolean isPinned() {
        KState state = getKState();
        return state == KState.ON_PLAYER || state == KState.HOT;
    }

    /**
     * Hot on the hearth, resting on the stove or flat as a pancake: nothing but the void and
     * {@code /kill} hurts him, and no mob sees him as a target.
     */
    public boolean isSheltered() {
        KState state = getKState();
        return state == KState.HOT || state == KState.RESTING || state == KState.PANCAKE;
    }

    // ------------------------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        noPhysics = isPinned();
        super.tick();
        if (level() instanceof ServerLevel serverLevel) {
            switch (getKState()) {
                case ON_PLAYER -> carryTick();
                case HOT -> hotTick();
                case RUNAWAY -> chaseTick();
                case RESTING -> restTick(serverLevel);
                case PANCAKE -> pancakeTick(serverLevel);
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
                verseTick(serverLevel);
                perkTick(serverLevel);
            }
            if (newVerseToSing && !isSinging() && !isSheltered()) {
                startSong(0, true);
            }
            songTick(serverLevel);
            idleSong();
            updateMood();
            idleChatter();
        } else {
            clientTicker.accept(this);
        }
    }

    private void updateMood() {
        KState state = getKState();
        Mood mood;
        if (state == KState.HOT || state == KState.RESTING) {
            mood = Mood.SLEEP;
        } else if (state == KState.PANCAKE) {
            mood = Mood.DIZZY;
        } else if (isSinging()) {
            mood = Mood.SING;
        } else if (dizzyTicks > 0) {
            mood = Mood.DIZZY;
        } else if (isPanicking()) {
            mood = Mood.SCARED;
        } else if (state == KState.RUNAWAY && getNavigation().isInProgress()) {
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

    private void hotTick() {
        // If the stove came apart under him, he is out in the world at once.
        boolean stoveGone = tickCount % 20 == 0 && !homeStoveStands();
        if (--hotTicks <= 0 || stoveGone) {
            cool();
        }
    }

    private boolean homeStoveStands() {
        if (home == null || !level().dimension().equals(homeDimension)) {
            return false;
        }
        BlockState state = level().getBlockState(home);
        return state.getBlock() instanceof StovePartBlock && state.getValue(StovePartBlock.PART) == StovePart.MOUTH;
    }

    /**
     * «Чё уставились?» He opens his eyes, hops off the hearth and the chase begins.
     */
    private void cool() {
        hotTicks = 0;
        setKState(KState.RUNAWAY);
        noPhysics = false;
        Vec3 out = Vec3.directionFromRotation(0.0F, getYRot());
        setDeltaMovement(out.x * 0.3, 0.3, out.z * 0.3);
        chaseTicks = CHASE_STOP_TICKS - FIRST_STOP_TICKS;
        say("cooled");
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
     * A tamed Kolobok sings the whole song every few minutes, unless he is asleep.
     */
    private void idleSong() {
        if (!isTame() || --nextSong > 0) {
            return;
        }
        nextSong = SONG_GAP_MIN + random.nextInt(SONG_GAP_MAX - SONG_GAP_MIN);
        if (!isSinging() && getMood() != Mood.SLEEP && !isSheltered()) {
            startSong(0);
        }
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
        getNavigation().stop();
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
     *         Колобочек, then soggy, then stale (outfits come before all of them)
     */
    public Voice songVoice() {
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
        if (!isTame()) {
            followListeners(level, now - getSingStart());
        }
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

    // ------------------------------------------------------------------------------------ «doesn't die»

    /**
     * He never dies: «killed», he gets away to his stove, or, with none to get to, goes flat for a
     * minute. Only what bypasses invulnerability ({@code /kill}, the void) is a real death.
     */
    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel serverLevel && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            setHealth(1.0F);
            escape(serverLevel);
            return;
        }
        super.die(source);
    }

    private void escape(ServerLevel level) {
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.3, getZ(), 12, 0.25, 0.25, 0.25, 0.02);
        stopSong();
        if (getKState() == KState.ON_PLAYER) {
            getOffPlayer(false);
        }
        stopRiding();
        getNavigation().stop();
        dizzyTicks = 0;
        charmedTicks = 0;
        // Whatever was killing him stays behind: no withering on the stove.
        removeAllEffects();
        clearFire();
        setDeltaMovement(Vec3.ZERO);
        resetFallDistance();

        Direction stove = homeStove(level);
        Vec3 bench = stove == null ? null : StovePattern.bench(home, stove);
        if (bench != null && level.noCollision(this, getDimensions(Pose.STANDING).makeBoundingBox(bench))) {
            teleportTo(bench.x, bench.y, bench.z);
            float yaw = stove.toYRot();
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
            setKState(KState.RESTING);
            escapeTicks = REST_TICKS;
            addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 0));
            level.sendParticles(ParticleTypes.POOF, bench.x, bench.y + 0.3, bench.z, 12, 0.25, 0.25, 0.25, 0.02);
            say("rest");
        } else {
            setKState(KState.PANCAKE);
            escapeTicks = PANCAKE_TICKS;
            // Flattened is not «got away»: no verse for whoever did it.
            forgetAttacker();
            loseTargets(level);
            say("pancake");
        }
    }

    /**
     * @return the facing of his home stove, if it stands whole in this dimension and is loaded
     */
    private @Nullable Direction homeStove(ServerLevel level) {
        if (home == null || !level.dimension().equals(homeDimension) || !level.isLoaded(home)) {
            return null;
        }
        BlockState state = level.getBlockState(home);
        if (!(state.getBlock() instanceof StovePartBlock) || state.getValue(StovePartBlock.PART) != StovePart.MOUTH) {
            return null;
        }
        Direction facing = state.getValue(StovePartBlock.FACING);
        for (StovePart part : StovePart.values()) {
            if (!level.isLoaded(StovePattern.posOf(home, facing, part))) {
                return null;
            }
        }
        return StovePattern.isIntact(level, home, facing) ? facing : null;
    }

    private void restTick(ServerLevel level) {
        boolean stoveGone = tickCount % 20 == 0 && homeStove(level) == null;
        if (--escapeTicks <= 0 || stoveGone) {
            wake("rested");
        }
    }

    private void pancakeTick(ServerLevel level) {
        if (tickCount % 20 == 0) {
            loseTargets(level);
        }
        if (--escapeTicks <= 0) {
            wake("unflat");
        }
    }

    /**
     * Up from the stove or puffed back into shape: tamed, he follows again; untamed, the chase
     * starts over.
     */
    private void wake(String line) {
        escapeTicks = 0;
        if (isTame()) {
            setKState(KState.FOLLOW);
        } else {
            setKState(KState.RUNAWAY);
            chaseTicks = CHASE_STOP_TICKS - FIRST_STOP_TICKS;
        }
        say(line);
    }

    private void loseTargets(ServerLevel level) {
        for (Mob mob : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(PANCAKE_FORGET_RANGE),
                mob -> mob.getTarget() == this)) {
            mob.setTarget(null);
        }
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
        if (!(source.getEntity() instanceof Mob mob) || mob instanceof Fox || mob instanceof KolobokEntity) {
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
            if (mob.getTarget() == this) {
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
        if (level().isClientSide() || SongText.HEROBRINE.equals(mob) || verses().contains(mob)) {
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
        AttributeInstance instance = getAttribute(attribute);
        if (instance == null) {
            return;
        }
        Identifier id = Kolobok.id("verse/" + perk.path());
        if (!hasPerk(perk)) {
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
        KState state = getKState();
        if (hasPerk(Perk.ENDERMAN) && (state == KState.FOLLOW || state == KState.WANDER)
                && distanceToSqr(owner) > ENDER_RANGE * ENDER_RANGE) {
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
        if (getKState() == KState.PANCAKE) {
            if (!level().isClientSide()) {
                say("pancake");
            }
            return InteractionResult.SUCCESS;
        }
        if (getKState() == KState.RESTING && stack.isEmpty() && owner) {
            if (!level().isClientSide()) {
                wake("woken");
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(KItems.SONGBOOK.get())) {
            return writeSongbook(stack, owner);
        }
        // On the stove he only takes milk.
        if (getKState() == KState.RESTING && !stack.is(Items.MILK_BUCKET)) {
            return InteractionResult.PASS;
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
     * Milk is «сметанка»: it heals, and an untamed Kolobok whose song the player has listened to may
     * decide to stay. The first song gives the configured chance, 1/3 by default; each further song
     * adds half of it again.
     */
    private InteractionResult drinkMilk(Player player, InteractionHand hand, ItemStack stack) {
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (isTame() && getHealth() >= getMaxHealth()) {
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
     * Writes his song, perks and home into the owner's songbook. The real stack in hand is written,
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
        return null;
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
        boolean hurt = super.hurtServer(level, source, damage);
        // Having got away (see die), he has said his line already.
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
     * game's 0.5–2.0 pitch range. Outfits arrive in v0.5.
     */
    public float voice() {
        return Mth.clamp(getVariant().voice() * getMood().voice(), 0.67F, 1.33F);
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
}
