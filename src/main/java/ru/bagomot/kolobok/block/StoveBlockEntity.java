package ru.bagomot.kolobok.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.entity.Variant;
import ru.bagomot.kolobok.item.DoughItem;
import ru.bagomot.kolobok.registry.KBlockEntities;
import ru.bagomot.kolobok.registry.KEntities;
import ru.bagomot.kolobok.registry.KItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The stove's insides, kept by the mouth: the firewood in the подпечье, whatever is in the горнило,
 * the fire, and the Kolobki this stove has baked. No screen: right-clicks put things in, an empty
 * hand takes out what is done and shows the rest above the hotbar, and Shift with an empty hand
 * takes the firewood back.
 *
 * <p>The подпечье holds up to a stack of logs of one kind. The stove burns them only when there is
 * something to cook: whenever the fire runs low it takes the next log, which burns as long as it
 * would in a furnace. The горнило takes one raw Kolobok, rolled from dough at the crafting table, or
 * a stack of anything a smoker cooks; the whole stack cooks at once.
 *
 * <p>The top has room for five Kolobki, so a stove bakes five and no more: it remembers each one, and
 * forgets one only when he is really gone (see {@link KolobokEntity#die}).
 */
public class StoveBlockEntity extends BlockEntity {

    public static final int BAKE_TICKS = 600;
    /**
     * A whole stack cooks in the time a furnace takes over this many items. The recipe's time is the
     * furnace's; a smoker runs it twice as fast.
     */
    public static final int COOK_FACTOR = 2;
    public static final int MAX_KOLOBKI = 5;
    /** A fresh Kolobok cools for two minutes, or thirty seconds by a window. */
    public static final int COOL_TICKS = 2400;
    public static final int COOL_TICKS_BY_WINDOW = 600;
    /** A stove that has burned stays warm this long: cats like it. */
    public static final int WARM_TICKS = 6000;

    /** The подпечье: logs of one kind, up to a stack. */
    private ItemStack logs = ItemStack.EMPTY;
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
    /** When the fire last burned, for the warmth. */
    private long lastBurned = Long.MIN_VALUE / 2;
    /** The Kolobki this stove baked and has room for on top. */
    private final List<UUID> kolobki = new ArrayList<>();

    /** Client: game time the progress was last synced at, to run it on smoothly between syncs. */
    private long syncedAt;

    public StoveBlockEntity(BlockPos pos, BlockState state) {
        super(KBlockEntities.STOVE.get(), pos, state);
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
        float done = progress;
        if (level != null && level.isClientSide() && isLit()) {
            done += level.getGameTime() - syncedAt + partialTick;
        }
        return Mth.clamp(done / total, 0.0F, 1.0F);
    }

    /**
     * @return whether the stove burns or burned recently enough to be warm on top
     */
    public boolean isWarm() {
        return isLit() || level != null && level.getGameTime() - lastBurned < WARM_TICKS;
    }

    // ------------------------------------------------------------------------------------ its Kolobki

    public int kolobkiCount() {
        return kolobki.size();
    }

    public boolean isFull() {
        return kolobki.size() >= MAX_KOLOBKI;
    }

    public boolean remembers(UUID kolobok) {
        return kolobki.contains(kolobok);
    }

    /**
     * Takes in a Kolobok who counts this stove his home but whom it does not remember, such as one
     * baked before the stove was taken apart and built again.
     *
     * @return whether he is one of its Kolobki now
     */
    public boolean adopt(UUID kolobok) {
        if (remembers(kolobok)) {
            return true;
        }
        if (isFull()) {
            return false;
        }
        kolobki.add(kolobok);
        setChanged();
        return true;
    }

    /**
     * He is really gone ({@code /kill}, the void): his place on top is free, and the stove can bake
     * another.
     */
    public void forget(UUID kolobok) {
        if (kolobki.remove(kolobok)) {
            setChanged();
        }
    }

    // ------------------------------------------------------------------------------------ ticking

    /**
     * Runs only while the stove burns. The fire takes the next log when it runs low and there is
     * still something to cook; with nothing left to cook, or no wood, it goes out.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, StoveBlockEntity stove) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // Once a second, and on the tick the fire goes out: a cold stove has no ticker to notice later.
        if ((level.getGameTime() % 20 == 0 || stove.burn <= 1) && !StovePattern.isIntact(level, pos, stove.facing())) {
            StovePattern.disassembleDamaged(serverLevel, pos, state);
            return;
        }
        if (stove.burn <= 0 && !(stove.hasWork() && stove.takeLog(serverLevel))) {
            stove.goOut();
            return;
        }
        stove.burn--;
        stove.lastBurned = level.getGameTime();
        if (stove.hasWork() && ++stove.progress >= stove.total) {
            stove.finish(serverLevel);
        }
        stove.setChanged();
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

    /**
     * Lights the fire if there is work for it, taking a log from the подпечье when none burns.
     */
    private void kindle(ServerLevel level) {
        if (isLit() || !hasWork() || burn <= 0 && !takeLog(level)) {
            return;
        }
        StovePattern.setLit(level, worldPosition, facing(), true);
        level.playSound(null, worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6F, 1.0F);
    }

    private boolean takeLog(ServerLevel level) {
        if (logs.isEmpty()) {
            return false;
        }
        int time = burnTime(level, logs);
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
        int cool = StovePattern.hasWindow(level, worldPosition, facing) ? COOL_TICKS_BY_WINDOW : COOL_TICKS;
        kolobok.snapTo(spot.x, spot.y, spot.z, facing.toYRot(), 0.0F);
        kolobok.bake(variant(level), tooHot, worldPosition, cool);
        Component name = oven.get(DataComponents.CUSTOM_NAME);
        if (name != null) {
            kolobok.setCustomName(name);
        }
        level.addFreshEntity(kolobok);
        kolobki.add(kolobok.getUUID());
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.4F);
        oven = ItemStack.EMPTY;
        tooHot = false;
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
     * Logs go into the подпечье, a raw Kolobok or food into the горнило, lava or a blaze rod into the
     * fire for heat. On the client every candidate is accepted, so that food is never eaten instead;
     * the server has the last word.
     */
    InteractionResult useItem(ItemStack stack, Player player, InteractionHand hand) {
        boolean log = stack.is(ItemTags.LOGS_THAT_BURN);
        boolean raw = stack.is(KItems.RAW_KOLOBOK.get());
        boolean heat = stack.is(Items.LAVA_BUCKET) || stack.is(Items.BLAZE_ROD);
        boolean food = stack.has(DataComponents.FOOD);
        if (!log && !raw && !heat && !food) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!StovePattern.isIntact(level, worldPosition, facing())) {
            StovePattern.disassembleDamaged(serverLevel, worldPosition, getBlockState());
            return InteractionResult.SUCCESS_SERVER;
        }
        Component refusal;
        if (log) {
            refusal = addLogs(stack, player);
        } else if (raw) {
            refusal = addRawKolobok(serverLevel, stack, player);
        } else if (heat) {
            refusal = addHeat(stack, player, hand);
        } else {
            refusal = addFood(serverLevel, stack, player);
        }
        if (refusal != null) {
            player.sendOverlayMessage(refusal);
            return InteractionResult.SUCCESS_SERVER;
        }
        kindle(serverLevel);
        sync();
        player.sendOverlayMessage(hasWork() && !isLit()
                ? Component.translatable("kolobok.stove.no_logs")
                : status());
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * @return why they were refused, or {@code null} when they went in
     */
    private @Nullable Component addLogs(ItemStack stack, Player player) {
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
        if (isFull()) {
            return Component.translatable("kolobok.stove.full", MAX_KOLOBKI);
        }
        oven = stack.copyWithCount(1);
        stack.consume(1, player);
        cooked = false;
        progress = 0;
        total = BAKE_TICKS;
        playSound(SoundEvents.SLIME_BLOCK_PLACE, 0.8F);
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
        sync();
        return Component.translatable("kolobok.stove.too_hot");
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

    /**
     * An empty hand takes out what is in the горнило: the cooked food with its experience, or what was
     * put in and has not been cooked yet. With nothing there it shows the status; with Shift it takes
     * the firewood back.
     */
    InteractionResult useEmptyHand(Player player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!StovePattern.isIntact(level, worldPosition, facing())) {
            StovePattern.disassembleDamaged(serverLevel, worldPosition, getBlockState());
            return InteractionResult.SUCCESS_SERVER;
        }
        if (player.isShiftKeyDown()) {
            if (logs.isEmpty()) {
                player.sendOverlayMessage(status());
                return InteractionResult.SUCCESS_SERVER;
            }
            give(player, logs);
            logs = ItemStack.EMPTY;
        } else if (!oven.isEmpty()) {
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
            player.sendOverlayMessage(status());
            return InteractionResult.SUCCESS_SERVER;
        }
        playSound(SoundEvents.ITEM_PICKUP, 0.3F);
        sync();
        player.sendOverlayMessage(status());
        return InteractionResult.SUCCESS_SERVER;
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
     * «Горнило: сырой колобок, печётся 40% · подпечье: дубовое бревно ×12 · горит · колобков 2/5».
     */
    public Component status() {
        MutableComponent oven = Component.translatable("kolobok.stove.status.oven", ovenStatus());
        MutableComponent wood = logs.isEmpty()
                ? Component.translatable("kolobok.stove.status.no_logs")
                : Component.translatable("kolobok.stove.status.logs", logs.getHoverName(), logs.getCount());
        Component fire = Component.translatable(isLit() ? "kolobok.stove.status.lit" : "kolobok.stove.status.cold");
        MutableComponent text = oven.append(" · ").append(wood).append(" · ").append(fire).append(" · ")
                .append(Component.translatable("kolobok.stove.status.kolobki", kolobki.size(), MAX_KOLOBKI));
        if (tooHot) {
            text.append(" · ").append(Component.translatable("kolobok.stove.status.too_hot").withStyle(ChatFormatting.GOLD));
        }
        if (isFull()) {
            text.append(" · ").append(Component.translatable("kolobok.stove.status.full").withStyle(ChatFormatting.RED));
        }
        return text;
    }

    private Component ovenStatus() {
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
     * Saves and sends the change to the clients that draw the stove: the logs, what is in the горнило
     * and how far it is. Called on changes, never every tick.
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

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    /**
     * The stove comes apart: the firewood and whatever is in the горнило fall out where the mouth
     * was. The fire is lost, as in a vanilla furnace, and so is the stove's memory of its Kolobki.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            for (ItemStack stack : new ItemStack[]{logs, oven}) {
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                }
            }
            logs = ItemStack.EMPTY;
            oven = ItemStack.EMPTY;
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        logs = input.read("logs", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        oven = input.read("oven", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        cooked = input.getBooleanOr("cooked", false);
        experience = input.getFloatOr("experience", 0.0F);
        progress = input.getIntOr("progress", 0);
        total = input.getIntOr("total", 0);
        burn = input.getIntOr("burn", 0);
        tooHot = input.getBooleanOr("too_hot", false);
        lastBurned = input.getLongOr("last_burned", Long.MIN_VALUE / 2);
        kolobki.clear();
        kolobki.addAll(input.read("kolobki", UUIDUtil.STRING_CODEC.listOf()).orElse(List.of()));
        syncedAt = level != null ? level.getGameTime() : 0L;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("logs", ItemStack.OPTIONAL_CODEC, logs);
        output.store("oven", ItemStack.OPTIONAL_CODEC, oven);
        output.putBoolean("cooked", cooked);
        output.putFloat("experience", experience);
        output.putInt("progress", progress);
        output.putInt("total", total);
        output.putInt("burn", burn);
        output.putBoolean("too_hot", tooHot);
        output.putLong("last_burned", lastBurned);
        output.store("kolobki", UUIDUtil.STRING_CODEC.listOf(), kolobki);
    }
}
