package ru.bagomot.kolobok.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ru.bagomot.kolobok.config.KConfig;
import ru.bagomot.kolobok.entity.song.Phonetics;
import ru.bagomot.kolobok.entity.song.Syllable;
import ru.bagomot.kolobok.registry.KSounds;

import java.util.List;
import java.util.function.Consumer;

/**
 * Колобок. A tamable pet that never really dies, sings his way out of trouble and rides on his
 * owner's shoulder. So far: dough variants, sit and follow, the shoulder and the head, speech
 * bubbles and mumbling.
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

    /**
     * Client-side per-tick work: rolling, blinking, mumbling. Installed by the client initialiser so
     * that this class never mentions a client type and still loads on a dedicated server.
     */
    public static Consumer<KolobokEntity> clientTicker = entity -> {
    };

    // Client-only state, owned by the client ticker. Plain fields: nothing here is synced.
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

    private int nextIdleChatter;
    private boolean carrierWasShifting;
    private int lastCarrierShiftPress = -100;

    public KolobokEntity(EntityType<? extends KolobokEntity> type, Level level) {
        super(type, level);
        // Spread out, or every Kolobok loaded with the world speaks up on the same tick.
        nextIdleChatter = 200 + random.nextInt(1200);
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

    // ------------------------------------------------------------------------------------ save

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Variant", getVariant().serializedName());
        output.putByte("Flags", entityData.get(DATA_FLAGS));
        // Sitting on a player is a session thing: the carrier is gone after a restart.
        KState state = getKState() == KState.ON_PLAYER ? KState.FOLLOW : getKState();
        output.putString("State", state.name());
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
    }

    // ------------------------------------------------------------------------------------ spawning

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                  EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        // There is no wild Kolobok: this runs for /summon and the creative spawn egg, which give a
        // cooled, untamed one.
        setKState(KState.RUNAWAY);
        if (groupData == null) {
            groupData = new AgeableMob.AgeableMobGroupData(false);
        }
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
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
        goalSelector.addGoal(4, new FollowOwnerGoal(this, 1.1, 6.0F, 2.0F) {
            @Override
            public boolean canUse() {
                return getKState() == KState.FOLLOW && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return getKState() == KState.FOLLOW && super.canContinueToUse();
            }
        });
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
     * Skips the whole AI step while he sits on a player: no goal may move him, the server places him
     * itself every tick.
     */
    @Override
    protected boolean isImmobile() {
        return getKState() == KState.ON_PLAYER || super.isImmobile();
    }

    // ------------------------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        boolean onPlayer = getKState() == KState.ON_PLAYER;
        noPhysics = onPlayer;
        super.tick();
        if (level() instanceof ServerLevel) {
            if (onPlayer) {
                carryTick();
            }
            updateMood();
            idleChatter();
        } else {
            clientTicker.accept(this);
        }
    }

    private void updateMood() {
        Mood mood;
        if (isPanicking()) {
            mood = Mood.SCARED;
        } else if (getKState() == KState.SIT && level().isDarkOutside()) {
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
        if (getMood() == Mood.SLEEP) {
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
        return getKState() != KState.ON_PLAYER && super.isPushable();
    }

    /**
     * On the shoulder his box overlaps the player's a little. Pushing would shove the player sideways
     * every tick (the client pushes its own player) and count him into entity cramming.
     */
    @Override
    protected void pushEntities() {
        if (getKState() != KState.ON_PLAYER) {
            super.pushEntities();
        }
    }

    @Override
    public boolean isNoGravity() {
        return getKState() == KState.ON_PLAYER || super.isNoGravity();
    }

    // ------------------------------------------------------------------------------------ interaction

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean owner = isTame() && isOwnedBy(player);

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
     * Milk is «сметанка»: it heals, and an untamed Kolobok may decide to stay.
     *
     * <p>v0.1 stand-in: the full rule only lets milk tame a Kolobok whose song the
     * player has listened to, and the song arrives in v0.2/v0.3. Until then milk alone tames with the
     * same 1/3 chance.
     */
    private InteractionResult drinkMilk(Player player, InteractionHand hand, ItemStack stack) {
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        boolean useful = !isTame() || getHealth() < getMaxHealth();
        if (!useful) {
            say("full");
            return InteractionResult.SUCCESS;
        }
        player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
        heal(6.0F);
        if (isTame()) {
            say("milk");
        } else if (random.nextInt(KConfig.server().tameChanceDenominator) == 0) {
            tame(player);
            setKState(KState.FOLLOW);
            getNavigation().stop();
            level().broadcastEntityEvent(this, (byte) 7);
            say("tamed");
        } else {
            level().broadcastEntityEvent(this, (byte) 6);
            say("milk_untamed");
        }
        return InteractionResult.SUCCESS_SERVER;
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
        boolean hurt = super.hurtServer(level, source, damage);
        // Fire or a cactus hurt him every few ticks; one «Ой!» per two seconds is plenty.
        boolean quiet = (int) level.getGameTime() >= getSayUntil() - SAY_TICKS + HURT_LINE_GAP;
        if (hurt && isAlive() && quiet) {
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
}
