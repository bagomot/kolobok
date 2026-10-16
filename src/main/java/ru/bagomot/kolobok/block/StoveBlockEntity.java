package ru.bagomot.kolobok.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.entity.Variant;
import ru.bagomot.kolobok.registry.KBlockEntities;
import ru.bagomot.kolobok.registry.KEntities;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The stove's insides, kept by the mouth: the kneading trough, the fire and the bread in the oven.
 * No screen: right-clicks put things in, an empty hand shows what is there above the hotbar, and
 * Shift with an empty hand takes the dough back out.
 *
 * <p>Dough is wheat (three, or one for Колобочек), an egg and «сметанка» — a bucket of milk.
 * Cocoa beans make it borodinsky, a gold ingot golden. Once the dough is ready and the stove burns,
 * 30 seconds later a hot Kolobok sits on the hearth.
 */
public class StoveBlockEntity extends BlockEntity {

    public static final int BAKE_TICKS = 600;
    public static final int MAX_WHEAT = 3;
    /** More fuel than this is refused: twenty minutes of fire. */
    public static final int MAX_FUEL = 24000;
    /** A fresh Kolobok cools for two minutes, or thirty seconds by a window. */
    public static final int COOL_TICKS = 2400;
    public static final int COOL_TICKS_BY_WINDOW = 600;

    private int wheat;
    private boolean egg;
    private boolean milk;
    private boolean cocoa;
    private boolean gold;
    /** Ticks of fire left. */
    private int fuel;
    private int progress;
    /** Lava or a blaze rod went into the fire: whatever bakes now comes out burnt. */
    private boolean tooHot;

    public StoveBlockEntity(BlockPos pos, BlockState state) {
        super(KBlockEntities.STOVE.get(), pos, state);
    }

    private Direction facing() {
        return getBlockState().getValue(StovePartBlock.FACING);
    }

    public boolean doughReady() {
        return wheat > 0 && egg && milk;
    }

    // ------------------------------------------------------------------------------------ ticking

    public static void serverTick(Level level, BlockPos pos, BlockState state, StoveBlockEntity stove) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (level.getGameTime() % 20 == 0 && !StovePattern.isIntact(level, pos, stove.facing())) {
            StovePattern.disassembleDamaged(serverLevel, pos, state);
            return;
        }
        if (stove.fuel > 0) {
            stove.fuel--;
        }
        if (stove.doughReady() && stove.progress < BAKE_TICKS) {
            stove.progress++;
        }
        if (stove.progress >= BAKE_TICKS && stove.doughReady()) {
            stove.bake(serverLevel);
        }
        if (stove.fuel <= 0) {
            stove.tooHot = false;
            StovePattern.setLit(level, pos, stove.facing(), false);
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

    private void bake(ServerLevel level) {
        Direction facing = facing();
        Vec3 hearth = StovePattern.hearth(worldPosition, facing);
        // One at a time: the next waits in the oven until the hearth is free.
        if (!level.getEntitiesOfClass(KolobokEntity.class, new AABB(hearth, hearth).inflate(0.4)).isEmpty()) {
            return;
        }
        KolobokEntity kolobok = KEntities.KOLOBOK.get().create(level, EntitySpawnReason.TRIGGERED);
        if (kolobok == null) {
            return;
        }
        int cool = StovePattern.hasWindow(level, worldPosition, facing) ? COOL_TICKS_BY_WINDOW : COOL_TICKS;
        kolobok.snapTo(hearth.x, hearth.y, hearth.z, facing.toYRot(), 0.0F);
        kolobok.bake(variant(level), tooHot, worldPosition, cool);
        level.addFreshEntity(kolobok);
        level.playSound(null, hearth.x, hearth.y, hearth.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.4F);
        wheat = 0;
        egg = false;
        milk = false;
        cocoa = false;
        gold = false;
        progress = 0;
    }

    private Variant variant(ServerLevel level) {
        if (wheat < MAX_WHEAT) {
            return Variant.MINI;
        }
        if (level.isDarkOutside()
                && level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, worldPosition) == MoonPhase.FULL_MOON) {
            return Variant.MOON;
        }
        if (gold) {
            return Variant.GOLDEN;
        }
        return cocoa ? Variant.BORODINSKY : Variant.CLASSIC;
    }

    // ------------------------------------------------------------------------------------ interaction

    /**
     * An ingredient or fuel goes in. On the client every candidate is accepted, so that a bucket of
     * milk is never drunk instead; the server has the last word.
     */
    InteractionResult useItem(ItemStack stack, Player player, InteractionHand hand) {
        boolean ingredient = stack.is(Items.WHEAT) || stack.is(ItemTags.EGGS) || stack.is(Items.MILK_BUCKET)
                || stack.is(Items.COCOA_BEANS) || stack.is(Items.GOLD_INGOT);
        boolean fuelItem = isFuel(stack);
        if (!ingredient && !fuelItem) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!StovePattern.isIntact(level, worldPosition, facing())) {
            StovePattern.disassembleDamaged(serverLevel, worldPosition, getBlockState());
            return InteractionResult.SUCCESS_SERVER;
        }
        Component refusal = ingredient ? addIngredient(stack, player, hand) : addFuel(serverLevel, stack, player, hand);
        player.sendOverlayMessage(refusal != null ? refusal : status());
        setChanged();
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * @return why it was refused, or {@code null} when it went in
     */
    private Component addIngredient(ItemStack stack, Player player, InteractionHand hand) {
        SoundEvent sound = SoundEvents.CROP_PLANTED;
        if (stack.is(Items.WHEAT)) {
            if (wheat >= MAX_WHEAT) {
                return Component.translatable("kolobok.stove.enough_wheat");
            }
            wheat++;
        } else if (stack.is(ItemTags.EGGS)) {
            if (egg) {
                return Component.translatable("kolobok.stove.already", stack.getHoverName());
            }
            egg = true;
        } else if (stack.is(Items.MILK_BUCKET)) {
            if (milk) {
                return Component.translatable("kolobok.stove.already", stack.getHoverName());
            }
            milk = true;
            sound = SoundEvents.BUCKET_EMPTY;
        } else if (stack.is(Items.COCOA_BEANS)) {
            if (cocoa) {
                return Component.translatable("kolobok.stove.already", stack.getHoverName());
            }
            cocoa = true;
        } else {
            if (gold) {
                return Component.translatable("kolobok.stove.already", stack.getHoverName());
            }
            gold = true;
        }
        if (stack.is(Items.MILK_BUCKET)) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
        } else {
            stack.consume(1, player);
        }
        level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        return null;
    }

    // NeoForge deprecates Item#getCraftingRemainder for its own stack-aware extension; the vanilla
    // method is the one both loaders share, and the vanilla furnace uses it too.
    @SuppressWarnings("deprecation")
    private Component addFuel(ServerLevel level, ItemStack stack, Player player, InteractionHand hand) {
        int burn = burnTime(level, stack, player);
        if (burn <= 0) {
            return Component.translatable("kolobok.stove.not_fuel");
        }
        if (fuel + burn > MAX_FUEL) {
            return Component.translatable("kolobok.stove.hot_enough");
        }
        fuel += burn;
        if (stack.is(Items.LAVA_BUCKET) || stack.is(Items.BLAZE_ROD)) {
            tooHot = true;
        }
        ItemStackTemplate remainder = stack.getItem().getCraftingRemainder();
        if (remainder != null) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, remainder.create()));
        } else {
            stack.consume(1, player);
        }
        level.playSound(null, worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6F, 1.0F);
        StovePattern.setLit(level, worldPosition, facing(), true);
        return null;
    }

    /**
     * Shows what is in the trough and how long the fire lasts; with Shift, takes the dough back.
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
            List<ItemStack> dough = takeDough();
            if (dough.isEmpty()) {
                player.sendOverlayMessage(Component.translatable("kolobok.stove.trough_empty"));
            } else {
                for (ItemStack stack : dough) {
                    player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
                }
                level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 1.0F);
                player.sendOverlayMessage(status());
            }
        } else {
            player.sendOverlayMessage(status());
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private List<ItemStack> takeDough() {
        List<ItemStack> out = new ArrayList<>();
        if (wheat > 0) {
            out.add(new ItemStack(Items.WHEAT, wheat));
        }
        if (egg) {
            out.add(new ItemStack(Items.EGG));
        }
        if (milk) {
            out.add(new ItemStack(Items.MILK_BUCKET));
        }
        if (cocoa) {
            out.add(new ItemStack(Items.COCOA_BEANS));
        }
        if (gold) {
            out.add(new ItemStack(Items.GOLD_INGOT));
        }
        wheat = 0;
        egg = false;
        milk = false;
        cocoa = false;
        gold = false;
        progress = 0;
        setChanged();
        return out;
    }

    /**
     * «Квашня: пшеница 3/3, яйцо ✔, сметанка ✘ · дрова 0:40».
     */
    public Component status() {
        MutableComponent text = Component.translatable("kolobok.stove.trough", wheat, MAX_WHEAT, mark(egg), mark(milk));
        if (cocoa) {
            text.append(", ").append(Component.translatable("kolobok.stove.cocoa"));
        }
        if (gold) {
            text.append(", ").append(Component.translatable("kolobok.stove.gold"));
        }
        text.append(" · ");
        if (fuel > 0) {
            int seconds = (fuel + 19) / 20;
            text.append(Component.translatable("kolobok.stove.fuel", "%d:%02d".formatted(seconds / 60, seconds % 60)));
        } else {
            text.append(Component.translatable("kolobok.stove.cold"));
        }
        if (doughReady() && fuel > 0) {
            text.append(" · ").append(progress >= BAKE_TICKS
                    ? Component.translatable("kolobok.stove.hearth_busy")
                    : Component.translatable("kolobok.stove.baking", progress * 100 / BAKE_TICKS));
        }
        if (tooHot) {
            text.append(" · ").append(Component.translatable("kolobok.stove.too_hot"));
        }
        return text;
    }

    private static String mark(boolean present) {
        return present ? "✔" : "✘";
    }

    // ------------------------------------------------------------------------------------ fuel

    /**
     * Anything the vanilla furnace burns, except tools and weapons: a wooden sword in hand should not
     * end up in the fire by a slip of the finger.
     */
    static boolean isFuel(ItemStack stack) {
        return stack.has(DataComponents.COOKING_FUEL) && !stack.isDamageableItem();
    }

    /**
     * The fuel's burn time as a furnace would have it. Vanilla resolves it from data, against the
     * block that burns it; the stove counts as an ordinary furnace.
     */
    private int burnTime(ServerLevel level, ItemStack stack, Player player) {
        LootContext context = new LootContext.Builder(new LootParams.Builder(level)
                .withParameter(LootContextParams.BLOCK_STATE, getBlockState())
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, this)
                .withOptionalParameter(LootContextParams.INTERACTING_ENTITY, player)
                .withOptionalParameter(LootContextParams.TOOL, stack)
                .create(LootContextParamSets.BLOCK_INTERACT))
                .create(Optional.empty());
        return ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, context, 0);
    }

    // ------------------------------------------------------------------------------------ removal and saving

    /**
     * The stove comes apart: the dough falls out where the mouth was. The fire is lost, as in a
     * vanilla furnace.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            for (ItemStack stack : takeDough()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        wheat = input.getIntOr("wheat", 0);
        egg = input.getBooleanOr("egg", false);
        milk = input.getBooleanOr("milk", false);
        cocoa = input.getBooleanOr("cocoa", false);
        gold = input.getBooleanOr("gold", false);
        fuel = input.getIntOr("fuel", 0);
        progress = input.getIntOr("progress", 0);
        tooHot = input.getBooleanOr("too_hot", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("wheat", wheat);
        output.putBoolean("egg", egg);
        output.putBoolean("milk", milk);
        output.putBoolean("cocoa", cocoa);
        output.putBoolean("gold", gold);
        output.putInt("fuel", fuel);
        output.putInt("progress", progress);
        output.putBoolean("too_hot", tooHot);
    }
}
