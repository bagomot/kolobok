package ru.bagomot.kolobok.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ru.bagomot.kolobok.Kolobok;
import ru.bagomot.kolobok.advancement.Tale;
import ru.bagomot.kolobok.advancement.TaleTrigger;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.entity.Variant;
import ru.bagomot.kolobok.item.DoughItem;
import ru.bagomot.kolobok.registry.KBlockEntities;
import ru.bagomot.kolobok.registry.KEntities;
import ru.bagomot.kolobok.registry.KItems;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * The stove's insides, kept by the mouth: the firewood in the подпечье, whatever is in the горнило
 * and the fire. No screen: right-clicks put things in, an empty hand takes out what is done and shows
 * the rest above the hotbar, and Shift with empty hands takes the firewood back. Anything else in the
 * hand only shows that, as a furnace opens its screen whatever the hand holds.
 *
 * <p>The подпечье holds up to a stack of logs of one kind. Flint and steel or a fire charge in the
 * горнило lights the stove, and it burns log after log, each as long as it would in a furnace, until
 * the wood runs out; more can go in while it burns. The горнило takes a raw Kolobok, rolled from
 * dough at the crafting table, or a stack of anything a smoker cooks; the whole stack cooks at once,
 * while the fire burns.
 */
public class StoveBlockEntity extends BlockEntity {

    public static final int BAKE_TICKS = 600;
    /**
     * A whole stack cooks in the time a furnace takes over this many items. The recipe's time is the
     * furnace's; a smoker runs it twice as fast.
     */
    public static final int COOK_FACTOR = 2;
    /** A fresh Kolobok cools for two minutes, or thirty seconds by a window. */
    public static final int COOL_TICKS = 2400;
    public static final int COOL_TICKS_BY_WINDOW = 600;
    /** A stove that has burned stays warm this long: cats like it, and it heals a Kolobok. */
    public static final int WARM_TICKS = 6000;

    /**
     * The stoves standing in the loaded chunks of each server level. Weak both ways: a stove holds its
     * level, so a strong set would keep a closed world in memory.
     */
    private static final Map<Level, Set<StoveBlockEntity>> LOADED = Collections.synchronizedMap(new WeakHashMap<>());

    /** The подпечье: logs of one kind, up to a stack. */
    private ItemStack logs = ItemStack.EMPTY;
    /** How long one of those logs burns. */
    private int logTicks;
    /** The горнило: a raw Kolobok, food to cook, or the food once cooked. */
    private ItemStack oven = ItemStack.EMPTY;
    private boolean cooked;
    /** Experience owed for the cooked food, paid when it is taken out. */
    private float experience;
    private int progress;
    private int total;
    /** Ticks of fire left in the log that burns now. */
    private int burn;
    /** Lava or a blaze rod went in for heat: the Kolobok baking now comes out burnt. */
    private boolean tooHot;
    /** Who put the raw Kolobok in, for the tale's advancements. */
    private @Nullable UUID baker;
    /** When the fire last burned, for the warmth. */
    private long lastBurned = Long.MIN_VALUE / 2;
    /** The name the furnace was given on an anvil, for when it is a furnace again. */
    private @Nullable Component furnaceName;

    /** Client: game time the fire and progress were last synced at, to run them on between syncs. */
    private long syncedAt;

    public StoveBlockEntity(BlockPos pos, BlockState state) {
        super(KBlockEntities.STOVE.get(), pos, state);
    }

    public @Nullable Component furnaceName() {
        return furnaceName;
    }

    void setFurnaceName(@Nullable Component name) {
        furnaceName = name;
        setChanged();
    }

    public Direction facing() {
        return getBlockState().getValue(StovePartBlock.FACING);
    }

    public boolean isLit() {
        return getBlockState().getValue(StovePartBlock.LIT);
    }

    public ItemStack logs() {
        return logs;
    }

    public ItemStack oven() {
        return oven;
    }

    public boolean isCooked() {
        return cooked;
    }

    public boolean isTooHot() {
        return tooHot;
    }

    /**
     * @return how far the bread or food is, 0–1, running on between syncs while the fire burns
     */
    public float progress(float partialTick) {
        if (total <= 0) {
            return cooked ? 1.0F : 0.0F;
        }
        return Mth.clamp((progress + sinceSync(partialTick)) / total, 0.0F, 1.0F);
    }

    /**
     * @return ticks the fire still needs to finish what is in the горнило, or 0 if there is nothing
     *         to cook
     */
    public float cookLeft(float partialTick) {
        return hasWork() ? Math.max(0.0F, total - progress - sinceSync(partialTick)) : 0.0F;
    }

    /**
     * @return ticks of fire left while it burns: the burning log, then all the wood in the подпечье
     */
    public float fireLeft(float partialTick) {
        if (!isLit()) {
            return 0.0F;
        }
        return Math.max(0.0F, burn - sinceSync(partialTick)) + (float) logs.getCount() * logTicks;
    }

    /** On the client, the ticks the fire has burned since the last sync; on the server, none. */
    private float sinceSync(float partialTick) {
        return level != null && level.isClientSide() && isLit() ? level.getGameTime() - syncedAt + partialTick : 0.0F;
    }

    /**
     * @return whether the stove burns or burned recently enough to be warm on top
     */
    public boolean isWarm() {
        return isLit() || level != null && level.getGameTime() - lastBurned < WARM_TICKS;
    }

    // ------------------------------------------------------------------------------------ loaded stoves

    /**
     * @return the stoves standing in that level's loaded chunks; server side only
     */
    public static List<StoveBlockEntity> loaded(Level level) {
        synchronized (LOADED) {
            Set<StoveBlockEntity> stoves = LOADED.get(level);
            return stoves == null ? List.of() : new ArrayList<>(stoves);
        }
    }

    /** In place in a loaded chunk: a stove just built, or its chunk just loaded. */
    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (level instanceof ServerLevel) {
            synchronized (LOADED) {
                LOADED.computeIfAbsent(level, l -> Collections.newSetFromMap(new WeakHashMap<>())).add(this);
            }
        }
    }

    /** Taken apart, or its chunk unloaded. */
    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) {
            synchronized (LOADED) {
                Set<StoveBlockEntity> stoves = LOADED.get(level);
                if (stoves != null) {
                    stoves.remove(this);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------ ticking

    /**
     * Runs only while the stove burns. The fire takes the next log when it runs low and goes out
     * when there is no wood left; whatever is in the горнило cooks meanwhile.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, StoveBlockEntity stove) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // Once a second, and on the tick the fire goes out: a cold stove has no ticker to notice later.
        if ((level.getGameTime() % 20 == 0 || stove.burn <= 1) && !StovePattern.isIntact(level, pos, stove.facing())) {
            StovePattern.disassemble(serverLevel, pos, state);
            return;
        }
        if (stove.logTicks <= 0 && !stove.logs.isEmpty()) {
            // Saved before the stove remembered it.
            stove.logTicks = stove.burnTime(serverLevel, stove.logs);
            stove.sync();
        }
        if (stove.burn <= 0 && !stove.takeLog(serverLevel)) {
            stove.goOut();
            return;
        }
        stove.burn--;
        stove.lastBurned = level.getGameTime();
        if (stove.hasWork() && ++stove.progress >= stove.total) {
            stove.finish(serverLevel);
        }
        // Saved with its chunk, but no neighbour needs to hear of every tick; real changes go through
        // sync(), which tells them.
        level.blockEntityChanged(pos);
    }

    /**
     * Smoke from the chimney: a tall slow column, like a campfire's.
     */
    public static void clientTick(Level level, BlockPos pos, BlockState state, StoveBlockEntity stove) {
        RandomSource random = level.getRandom();
        if (random.nextInt(3) != 0) {
            return;
        }
        Vec3 top = StovePattern.chimneyTop(pos, stove.facing());
        level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
                top.x + (random.nextDouble() - 0.5) * 0.25, top.y + random.nextDouble() * 0.5,
                top.z + (random.nextDouble() - 0.5) * 0.25, 0.0, 0.07, 0.0);
    }

    /**
     * @return whether there is something in the горнило that the fire would cook
     */
    private boolean hasWork() {
        return !oven.isEmpty() && !cooked && total > 0;
    }

    private boolean takeLog(ServerLevel level) {
        if (logs.isEmpty()) {
            return false;
        }
        int time = burnTime(level, logs);
        logTicks = time;
        logs.shrink(1);
        if (logs.isEmpty()) {
            logs = ItemStack.EMPTY;
        }
        burn += Math.max(1, time);
        sync();
        return true;
    }

    private void goOut() {
        burn = 0;
        if (level != null) {
            lastBurned = level.getGameTime();
            StovePattern.setLit(level, worldPosition, facing(), false);
        }
        sync();
    }

    private void finish(ServerLevel level) {
        if (oven.is(KItems.RAW_KOLOBOK.get())) {
            bake(level);
        } else {
            cook(level);
        }
        progress = 0;
        total = 0;
        sync();
    }

    /**
     * The raw Kolobok is baked: he sits in the горнило, hot and asleep, until he cools.
     */
    private void bake(ServerLevel level) {
        Direction facing = facing();
        KolobokEntity kolobok = KEntities.KOLOBOK.get().create(level, EntitySpawnReason.TRIGGERED);
        if (kolobok == null) {
            return;
        }
        Vec3 spot = StovePattern.gornilo(worldPosition, facing);
        boolean window = StovePattern.hasWindow(level, worldPosition, facing);
        kolobok.snapTo(spot.x, spot.y, spot.z, facing.toYRot(), 0.0F);
        kolobok.bake(variant(level), tooHot, worldPosition, window ? COOL_TICKS_BY_WINDOW : COOL_TICKS, baker, window);
        Component name = oven.get(DataComponents.CUSTOM_NAME);
        if (name != null) {
            kolobok.setCustomName(name);
        }
        level.addFreshEntity(kolobok);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.4F);
        oven = ItemStack.EMPTY;
        tooHot = false;
        baker = null;
    }

    private Variant variant(ServerLevel level) {
        Variant dough = DoughItem.variant(oven);
        if (dough != Variant.MINI && level.isDarkOutside()
                && level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, worldPosition) == MoonPhase.FULL_MOON) {
            return Variant.MOON;
        }
        return dough;
    }

    /**
     * The whole stack is cooked at once, and stays in the горнило until taken out.
     */
    private void cook(ServerLevel level) {
        Optional<RecipeHolder<SmokingRecipe>> recipe = recipeFor(level, oven);
        if (recipe.isEmpty()) {
            return;
        }
        int count = oven.getCount();
        ItemStack result = recipe.get().value().assemble(new SingleRecipeInput(oven));
        int made = result.getCount() * count;
        oven = result.copyWithCount(Math.min(made, result.getMaxStackSize()));
        if (made > oven.getCount()) {
            Vec3 front = Vec3.atCenterOf(StovePattern.front(worldPosition, facing()));
            Containers.dropItemStack(level, front.x, front.y, front.z, result.copyWithCount(made - oven.getCount()));
        }
        experience += recipe.get().value().experience() * count;
        cooked = true;
        level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3F, 1.8F);
    }

    private static Optional<RecipeHolder<SmokingRecipe>> recipeFor(ServerLevel level, ItemStack stack) {
        return level.recipeAccess().getRecipeFor(RecipeType.SMOKING, new SingleRecipeInput(stack), level);
    }

    // ------------------------------------------------------------------------------------ interaction

    /**
     * @return whether that lights the stove: flint and steel or a fire charge, as for a campfire
     */
    public static boolean isIgniter(ItemStack stack) {
        return stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE);
    }

    /**
     * @return whether the stove takes that from a hand: logs, a raw Kolobok, lava or a blaze rod for
     *         heat, food, and through the устье flint and steel or a fire charge. Any food counts, on
     *         the client too, so that none is ever eaten instead; the server has the last word.
     */
    private static boolean takes(ItemStack stack, boolean throughMouth) {
        return throughMouth && isIgniter(stack) || stack.is(ItemTags.LOGS_THAT_BURN) || stack.is(KItems.RAW_KOLOBOK.get())
                || isHeat(stack) || stack.has(DataComponents.FOOD);
    }

    private static boolean isHeat(ItemStack stack) {
        return stack.is(Items.LAVA_BUCKET) || stack.is(Items.BLAZE_ROD);
    }

    /**
     * A click with something in the hand. What the stove takes goes in, or lights it; anything else
     * only shows the status and takes nothing out, as a furnace opens its screen whatever the hand
     * holds. With such a thing in the main hand, the off hand has its turn here: the main hand has
     * the click, so vanilla would never give the off hand one.
     *
     * @param throughMouth whether the click is on the устье: the mouth's block or the one above it
     */
    InteractionResult useItem(ItemStack stack, Player player, InteractionHand hand, boolean throughMouth) {
        if (takes(stack, throughMouth)) {
            return click(serverLevel -> put(serverLevel, stack, player, hand, throughMouth));
        }
        return hand == InteractionHand.MAIN_HAND
                ? click(serverLevel -> offHandOrStatus(serverLevel, player, throughMouth))
                : InteractionResult.PASS;
    }

    /**
     * An empty hand takes out what is in the горнило: the cooked food with its experience, or what was
     * put in and has not been cooked yet. With Shift, which vanilla lets through only with both hands
     * empty, it takes the firewood back. With nothing to take, the off hand has its turn.
     */
    InteractionResult useEmptyHand(Player player, boolean throughMouth) {
        return click(serverLevel -> {
            if (player.isShiftKeyDown() && !logs.isEmpty()) {
                give(player, logs);
                logs = ItemStack.EMPTY;
            } else if (!player.isShiftKeyDown() && !oven.isEmpty()) {
                if (cooked && experience > 0.0F) {
                    award(serverLevel, player.position(), experience);
                }
                give(player, oven);
                oven = ItemStack.EMPTY;
                cooked = false;
                experience = 0.0F;
                progress = 0;
                total = 0;
            } else {
                offHandOrStatus(serverLevel, player, throughMouth);
                return;
            }
            playSound(SoundEvents.ITEM_PICKUP, 0.3F);
            sync();
            player.sendOverlayMessage(status());
        });
    }

    /**
     * The main hand did nothing: what the stove takes from the off hand goes in, but food only if the
     * stove cooks it, since an off hand often just holds something to eat. Otherwise the click shows
     * the status.
     */
    private void offHandOrStatus(ServerLevel serverLevel, Player player, boolean throughMouth) {
        ItemStack offHand = player.getOffhandItem();
        if (takes(offHand, throughMouth) && (!offHand.has(DataComponents.FOOD) || recipeFor(serverLevel, offHand).isPresent())) {
            put(serverLevel, offHand, player, InteractionHand.OFF_HAND, throughMouth);
        } else {
            player.sendOverlayMessage(status());
        }
    }

    /**
     * Every click starts the same: the client only predicts it, and a stove found damaged, its blocks
     * changed by something that skips the removal hook such as a command, comes apart instead.
     */
    private InteractionResult click(Consumer<ServerLevel> action) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (StovePattern.isIntact(serverLevel, worldPosition, facing())) {
            action.accept(serverLevel);
        } else {
            StovePattern.disassemble(serverLevel, worldPosition, getBlockState());
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * Logs go into the подпечье, a raw Kolobok or food into the горнило, lava or a blaze rod into the
     * fire for heat, and flint and steel or a fire charge through the устье lights the stove.
     */
    private void put(ServerLevel serverLevel, ItemStack stack, Player player, InteractionHand hand, boolean throughMouth) {
        if (throughMouth && isIgniter(stack)) {
            ignite(serverLevel, stack, player, hand);
            return;
        }
        boolean heat = isHeat(stack);
        Component refusal;
        if (stack.is(ItemTags.LOGS_THAT_BURN)) {
            refusal = addLogs(serverLevel, stack, player);
        } else if (stack.is(KItems.RAW_KOLOBOK.get())) {
            refusal = addRawKolobok(serverLevel, stack, player);
        } else if (heat) {
            refusal = addHeat(stack, player, hand);
        } else {
            refusal = addFood(serverLevel, stack, player);
        }
        if (refusal != null) {
            player.sendOverlayMessage(refusal);
            return;
        }
        sync();
        player.sendOverlayMessage(heat ? Component.translatable("kolobok.stove.too_hot")
                : hasWork() && !isLit() ? notLit() : status());
    }

    /**
     * Flint and steel or a fire charge in the горнило lights the wood in the подпечье.
     */
    private void ignite(ServerLevel serverLevel, ItemStack stack, Player player, InteractionHand hand) {
        Component refusal = null;
        if (isLit()) {
            refusal = Component.translatable("kolobok.stove.already_lit");
        } else if (logs.isEmpty() && burn <= 0) {
            refusal = Component.translatable("kolobok.stove.nothing_to_light");
        } else if (hasKolobokInside(serverLevel)) {
            refusal = Component.translatable("kolobok.stove.kolobok_inside");
        }
        if (refusal != null) {
            player.sendOverlayMessage(refusal);
            return;
        }
        if (burn <= 0) {
            takeLog(serverLevel);
        }
        StovePattern.setLit(serverLevel, worldPosition, facing(), true);
        boolean charge = stack.is(Items.FIRE_CHARGE);
        serverLevel.playSound(null, worldPosition, charge ? SoundEvents.FIRECHARGE_USE : SoundEvents.FLINTANDSTEEL_USE,
                SoundSource.BLOCKS, 1.0F, serverLevel.getRandom().nextFloat() * 0.4F + 0.8F);
        serverLevel.gameEvent(player, GameEvent.BLOCK_CHANGE, worldPosition);
        if (charge) {
            stack.consume(1, player);
        } else {
            stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
        }
        sync();
        player.sendOverlayMessage(status());
    }

    /**
     * @return why they were refused, or {@code null} when they went in
     */
    private @Nullable Component addLogs(ServerLevel level, ItemStack stack, Player player) {
        if (!logs.isEmpty() && !ItemStack.isSameItemSameComponents(logs, stack)) {
            return Component.translatable("kolobok.stove.other_logs", logs.getHoverName());
        }
        int room = stack.getMaxStackSize() - logs.getCount();
        if (room <= 0) {
            return Component.translatable("kolobok.stove.logs_full");
        }
        int moved = Math.min(room, stack.getCount());
        if (logs.isEmpty()) {
            logs = stack.copyWithCount(moved);
            logTicks = burnTime(level, logs);
        } else {
            logs.grow(moved);
        }
        stack.consume(moved, player);
        playSound(SoundEvents.WOOD_PLACE, 1.0F);
        return null;
    }

    private @Nullable Component addRawKolobok(ServerLevel level, ItemStack stack, Player player) {
        Component busy = ovenBusy(level);
        if (busy != null) {
            return busy;
        }
        oven = stack.copyWithCount(1);
        stack.consume(1, player);
        cooked = false;
        progress = 0;
        total = BAKE_TICKS;
        baker = player.getUUID();
        playSound(SoundEvents.SLIME_BLOCK_PLACE, 0.8F);
        TaleTrigger.award(player, Tale.INTO_THE_OVEN);
        return null;
    }

    private @Nullable Component addFood(ServerLevel level, ItemStack stack, Player player) {
        Optional<RecipeHolder<SmokingRecipe>> recipe = recipeFor(level, stack);
        if (recipe.isEmpty()) {
            return Component.translatable("kolobok.stove.not_food");
        }
        if (!oven.isEmpty() && !cooked && ItemStack.isSameItemSameComponents(oven, stack)) {
            // More of the same: it joins what is cooking and is done with it.
            int moved = Math.min(oven.getMaxStackSize() - oven.getCount(), stack.getCount());
            if (moved <= 0) {
                return Component.translatable("kolobok.stove.oven_full");
            }
            oven.grow(moved);
            stack.consume(moved, player);
            playSound(SoundEvents.ITEM_FRAME_ADD_ITEM, 1.0F);
            return null;
        }
        Component busy = ovenBusy(level);
        if (busy != null) {
            return busy;
        }
        int count = stack.getCount();
        oven = stack.copyWithCount(count);
        stack.consume(count, player);
        cooked = false;
        experience = 0.0F;
        progress = 0;
        total = Math.max(1, recipe.get().value().cookingTime() * COOK_FACTOR);
        playSound(SoundEvents.ITEM_FRAME_ADD_ITEM, 1.0F);
        return null;
    }

    private @Nullable Component addHeat(ItemStack stack, Player player, InteractionHand hand) {
        if (tooHot) {
            return Component.translatable("kolobok.stove.hot_enough");
        }
        tooHot = true;
        if (stack.is(Items.LAVA_BUCKET)) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
            playSound(SoundEvents.BUCKET_EMPTY_LAVA, 1.0F);
        } else {
            stack.consume(1, player);
            playSound(SoundEvents.BLAZE_SHOOT, 0.5F);
        }
        return null;
    }

    /**
     * @return what keeps the горнило from taking more, or {@code null} when it is free
     */
    private @Nullable Component ovenBusy(ServerLevel level) {
        if (!oven.isEmpty()) {
            return Component.translatable("kolobok.stove.oven_busy", oven.getHoverName());
        }
        if (hasKolobokInside(level)) {
            return Component.translatable("kolobok.stove.kolobok_inside");
        }
        return null;
    }

    /** Something waits in the горнило, but the stove is cold. */
    private Component notLit() {
        return Component.translatable(logs.isEmpty() ? "kolobok.stove.no_logs" : "kolobok.stove.not_lit");
    }

    /**
     * @return whether a Kolobok sits in the горнило: a fresh one cooling, or one who climbed in
     */
    public boolean hasKolobokInside(Level level) {
        return !level.getEntitiesOfClass(KolobokEntity.class, StovePattern.gorniloSpace(worldPosition, facing())).isEmpty();
    }

    /**
     * @return whether a Kolobok may climb into the горнило now: cold, empty, nobody in it
     */
    public boolean canTakeVisitor() {
        return level != null && !isLit() && oven.isEmpty() && !hasKolobokInside(level);
    }

    private static void give(Player player, ItemStack stack) {
        player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
    }

    /** As a furnace pays it out: the fraction is a chance of one more point. */
    private static void award(ServerLevel level, Vec3 at, float experience) {
        int points = Mth.floor(experience);
        if (level.getRandom().nextFloat() < Mth.frac(experience)) {
            points++;
        }
        ExperienceOrb.award(level, at, points);
    }

    /**
     * «Горнило: сырой колобок, печётся 40% · подпечье: дубовое бревно ×12 · топится».
     */
    public Component status() {
        MutableComponent oven = Component.translatable("kolobok.stove.status.oven", ovenStatus());
        MutableComponent wood = logs.isEmpty()
                ? Component.translatable("kolobok.stove.status.no_logs")
                : Component.translatable("kolobok.stove.status.logs", logs.getHoverName(), logs.getCount());
        Component fire = Component.translatable(isLit() ? "kolobok.stove.status.lit" : "kolobok.stove.status.cold");
        MutableComponent text = oven.append(" · ").append(wood).append(" · ").append(fire);
        if (tooHot) {
            text.append(" · ").append(Component.translatable("kolobok.stove.status.too_hot").withStyle(ChatFormatting.GOLD));
        }
        return text;
    }

    /**
     * @return what is in the горнило and how far it is: «Сырой колобок, печётся: 45%», «пусто»
     */
    public Component ovenStatus() {
        if (oven.isEmpty()) {
            return level != null && hasKolobokInside(level)
                    ? Component.translatable("kolobok.stove.status.kolobok")
                    : Component.translatable("kolobok.stove.status.empty");
        }
        if (cooked) {
            return Component.translatable("kolobok.stove.status.done", oven.getHoverName(), oven.getCount());
        }
        int percent = Math.round(progress(0.0F) * 100.0F);
        boolean bread = oven.is(KItems.RAW_KOLOBOK.get());
        if (!isLit()) {
            return bread
                    ? Component.translatable("kolobok.stove.status.bread_waiting", oven.getHoverName())
                    : Component.translatable("kolobok.stove.status.waiting", oven.getHoverName(), oven.getCount());
        }
        return bread
                ? Component.translatable("kolobok.stove.status.baking", oven.getHoverName(), percent)
                : Component.translatable("kolobok.stove.status.cooking", oven.getHoverName(), oven.getCount(), percent);
    }

    // The lines the stove's labels and Jade show, from what the client has.

    /** Ticks as «1:05», minutes and seconds, a second started counting whole. */
    public static String time(float ticks) {
        int seconds = Mth.ceil(ticks / 20.0F);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    /**
     * @return «Дрова: 12/64», the wood in the подпечье out of a stack
     */
    public MutableComponent woodLabel() {
        int max = logs.isEmpty() ? Item.DEFAULT_MAX_STACK_SIZE : logs.getMaxStackSize();
        return Component.translatable("kolobok.stove.hover.logs", logs.getCount(), max);
    }

    /**
     * @return how long the wood lasts while the stove burns, or {@code null} while it is cold
     */
    public @Nullable Component fireLabel(float partialTick) {
        return isLit() ? Component.translatable("kolobok.stove.hover.lasts", time(fireLeft(partialTick))) : null;
    }

    /**
     * @return how long until what is in the горнило is done, or {@code null} unless it is cooking now
     */
    public @Nullable Component cookLabel(float partialTick) {
        return isLit() && !oven.isEmpty() && !cooked
                ? Component.translatable("kolobok.stove.hover.time", time(cookLeft(partialTick)))
                : null;
    }

    /**
     * @return what the горнило tells: done, how long until it is, or that it waits for the fire;
     *         {@code null} with nothing in it
     */
    public @Nullable Component ovenLabel(float partialTick) {
        if (oven.isEmpty()) {
            return null;
        }
        if (cooked) {
            return Component.translatable("kolobok.stove.hover.done");
        }
        Component cooking = cookLabel(partialTick);
        return cooking != null ? cooking : Component.translatable("kolobok.stove.hover.waiting");
    }

    private void playSound(SoundEvent sound, float volume) {
        if (level != null) {
            level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, 1.0F);
        }
    }

    // ------------------------------------------------------------------------------------ fuel

    /**
     * A log's burn time as a furnace would have it. Vanilla resolves it from data, against the block
     * that burns it; the stove counts as an ordinary furnace.
     */
    private int burnTime(ServerLevel level, ItemStack stack) {
        LootContext context = new LootContext.Builder(new LootParams.Builder(level)
                .withParameter(LootContextParams.BLOCK_STATE, getBlockState())
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, this)
                .withOptionalParameter(LootContextParams.TOOL, stack)
                .create(LootContextParamSets.BLOCK_INTERACT))
                .create(Optional.empty());
        return ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, context, 0);
    }

    // ------------------------------------------------------------------------------------ syncing, removal and saving

    /**
     * Saves and sends the change to the clients that draw the stove: the logs, the fire, what is in
     * the горнило and how far it is. Called on changes, never every tick.
     */
    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Only what the clients draw and tell, for the renderer, the labels and Jade. Who baked the
     * Kolobok, the experience owed and the furnace's name stay on the server.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(problemPath(), Kolobok.LOG)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            saveShown(output);
            return output.buildResult();
        }
    }

    /**
     * The stove comes apart: the firewood and whatever is in the горнило fall out where the mouth
     * was, and the experience owed for cooked food is paid out there. As in a vanilla furnace, the
     * fire is lost and the experience is not.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            for (ItemStack stack : new ItemStack[]{logs, oven}) {
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                }
            }
            if (level instanceof ServerLevel serverLevel && cooked && experience > 0.0F) {
                award(serverLevel, Vec3.atCenterOf(pos), experience);
            }
            logs = ItemStack.EMPTY;
            oven = ItemStack.EMPTY;
            experience = 0.0F;
        }
    }

    /** The furnace's name, for the furnace the mouth drops: its loot table copies it from here. */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CUSTOM_NAME, furnaceName);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        logs = input.read("logs", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        logTicks = input.getIntOr("log_ticks", 0);
        oven = input.read("oven", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        cooked = input.getBooleanOr("cooked", false);
        experience = input.getFloatOr("experience", 0.0F);
        progress = input.getIntOr("progress", 0);
        total = input.getIntOr("total", 0);
        burn = input.getIntOr("burn", 0);
        tooHot = input.getBooleanOr("too_hot", false);
        baker = input.read("baker", UUIDUtil.CODEC).orElse(null);
        lastBurned = input.getLongOr("last_burned", Long.MIN_VALUE / 2);
        furnaceName = parseCustomNameSafe(input, "furnace_name");
        syncedAt = level != null ? level.getGameTime() : 0L;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        saveShown(output);
        output.putFloat("experience", experience);
        output.storeNullable("baker", UUIDUtil.CODEC, baker);
        output.storeNullable("furnace_name", ComponentSerialization.CODEC, furnaceName);
    }

    /** What is saved and also sent to the clients. */
    private void saveShown(ValueOutput output) {
        output.store("logs", ItemStack.OPTIONAL_CODEC, logs);
        output.putInt("log_ticks", logTicks);
        output.store("oven", ItemStack.OPTIONAL_CODEC, oven);
        output.putBoolean("cooked", cooked);
        output.putInt("progress", progress);
        output.putInt("total", total);
        output.putInt("burn", burn);
        output.putBoolean("too_hot", tooHot);
        output.putLong("last_burned", lastBurned);
    }
}
