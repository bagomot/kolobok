package ru.bagomot.kolobok.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import ru.bagomot.kolobok.Kolobok;
import ru.bagomot.kolobok.entity.KolobokEntity;
import ru.bagomot.kolobok.registry.KComponents;
import ru.bagomot.kolobok.registry.KEntities;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Узелок: a stick and wool. Shown to one's own Kolobok, it takes him in, whole: name, verses,
 * outfit, cheeks. Used on a block, it lets him out there. So he travels through portals in his
 * owner's pocket.
 *
 * <p>Wool that does not burn: lava and fire leave the bindle be. Anything else that destroys it on
 * the ground lets him out, as a shulker box spills what it holds; a full bindle dropped on the ground
 * never despawns (see {@code KolobokEvents}).
 */
public class BindleItem extends Item {

    /** What the game saves about where he was in the world rather than about him. */
    private static final List<String> PLACE = List.of("UUID", "Pos", "Motion", "Rotation", "fall_distance", "OnGround",
            "PortalCooldown");
    /**
     * The most a Kolobok in a bindle may weigh, in bytes of his saved data. The bindle goes to every
     * client that sees it, which reads at most 2 MiB of such data in one go; a Kolobok is a few
     * kilobytes, his cheeks full of books a lot more.
     */
    private static final int MAX_BYTES = 256 * 1024;

    public BindleItem(Item.Properties properties) {
        super(properties);
    }

    public static Item.Properties properties() {
        return new Item.Properties().stacksTo(1).fireResistant();
    }

    /**
     * @return whether a Kolobok sits in that bindle
     */
    public static boolean isFull(ItemStack stack) {
        return stack.has(KComponents.STORED_KOLOBOK.get());
    }

    /**
     * A bindle holds things itself: it goes in no shulker box or bundle, as a shulker box goes in no
     * other, or one item could hold boxes of bindles of boxes past what a client can be sent.
     */
    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    /**
     * @return whether that Kolobok is light enough for a bindle; one with his cheeks stuffed with
     *         books is not
     */
    public static boolean fits(KolobokEntity kolobok) {
        return save(kolobok).sizeInBytes() <= MAX_BYTES;
    }

    /**
     * Saves that Kolobok into the bindle. Taking him out of the world is the caller's business.
     */
    public static void store(ItemStack bindle, KolobokEntity kolobok) {
        bindle.set(KComponents.STORED_KOLOBOK.get(), new StoredKolobok(CustomData.of(save(kolobok)),
                Optional.ofNullable(kolobok.getCustomName()), kolobok.getVariant(), kolobok.verses().size()));
    }

    private static CompoundTag save(KolobokEntity kolobok) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(kolobok.problemPath(), Kolobok.LOG)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, kolobok.registryAccess());
            kolobok.saveWithoutId(output);
            CompoundTag tag = output.buildResult();
            PLACE.forEach(tag::remove);
            return tag;
        }
    }

    /**
     * Lets the Kolobok out at that point, with an id of his own: copies from a creative bindle never
     * clash.
     *
     * @return him, or {@code null} if he could not be made
     */
    public static @Nullable KolobokEntity release(ServerLevel level, StoredKolobok stored, Vec3 at, float yaw) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(() -> "bindle", Kolobok.LOG)) {
            ValueInput input = TagValueInput.create(reporter, level.registryAccess(), stored.entity().copyTag());
            Entity entity = EntityType.create(KEntities.KOLOBOK.get(), input, level, EntitySpawnReason.BUCKET).orElse(null);
            if (!(entity instanceof KolobokEntity kolobok)) {
                return null;
            }
            kolobok.setUUID(UUID.randomUUID());
            kolobok.snapTo(at.x, at.y, at.z, yaw, 0.0F);
            kolobok.outOfBindle();
            // Another mod may keep him out of the world; then the bindle keeps him.
            return level.addFreshEntity(kolobok) ? kolobok : null;
        }
    }

    /**
     * Used on a block, a full bindle lets him out on it, facing the player. In creative mode the
     * bindle keeps him, as a spawn egg stays in hand.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        StoredKolobok stored = stack.get(KComponents.STORED_KOLOBOK.get());
        if (stored == null) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos clicked = context.getClickedPos();
        BlockPos at = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty()
                ? clicked : clicked.relative(context.getClickedFace());
        Player player = context.getPlayer();
        float yaw = player != null ? player.getYRot() + 180.0F : 0.0F;
        if (release(serverLevel, stored, Vec3.atBottomCenterOf(at), yaw) == null) {
            return InteractionResult.FAIL;
        }
        if (player == null || !player.hasInfiniteMaterials()) {
            stack.remove(KComponents.STORED_KOLOBOK.get());
        }
        level.playSound(null, at, SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.ENTITY_PLACE, at);
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * Destroyed on the ground — by a cactus or an explosion; fire cannot — it lets him out where it
     * lay.
     */
    @Override
    public void onDestroyed(ItemEntity itemEntity) {
        StoredKolobok stored = itemEntity.getItem().get(KComponents.STORED_KOLOBOK.get());
        if (stored != null && itemEntity.level() instanceof ServerLevel level) {
            release(level, stored, itemEntity.position(), itemEntity.getYRot());
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        StoredKolobok stored = stack.get(KComponents.STORED_KOLOBOK.get());
        if (stored == null) {
            builder.accept(Component.translatable("item.kolobok.bindle.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        builder.accept(stored.name().orElse(Component.translatable("entity.kolobok.kolobok")).copy().withStyle(ChatFormatting.GOLD));
        builder.accept(Component.translatable("item.kolobok.bindle.dough." + stored.variant().serializedName())
                .withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.kolobok.bindle.verses", stored.verses()).withStyle(ChatFormatting.GRAY));
    }
}
