package ru.bagomot.kolobok.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import ru.bagomot.kolobok.Kolobok;
import ru.bagomot.kolobok.advancement.Tale;
import ru.bagomot.kolobok.advancement.TaleTrigger;
import ru.bagomot.kolobok.block.StovePattern;
import ru.bagomot.kolobok.entity.KolobokEntity;

/**
 * Бабушкина скалка, the remote control for everything. Hits like a wooden sword, but knocks back.
 *
 * <p>Shift and right-click on a furnace builds the Russian stove around it; at the crafting table it
 * rolls dough into a raw Kolobok and stays in the grid. Right-click on one's own
 * Kolobok switches his mode; that click is handled by Kolobok himself. Right-click anywhere else
 * bowls one's own Kolobok at whatever the player points at: «Катись!»
 */
public class RollingPinItem extends Item {

    private static final float DAMAGE = 3.0F;
    private static final float SPEED = -2.4F;
    /** Half a second between two bowls. */
    private static final int BOWL_COOLDOWN = 10;

    public RollingPinItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * A wooden sword's properties with one more point of knockback.
     */
    public static Item.Properties properties() {
        return new Item.Properties()
                .sword(ToolMaterial.WOOD, DAMAGE, SPEED)
                .attributes(ItemAttributeModifiers.builder()
                        .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID,
                                DAMAGE + ToolMaterial.WOOD.attackDamageBonus(), AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, SPEED,
                                AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ATTACK_KNOCKBACK, new AttributeModifier(Kolobok.id("rolling_pin_knockback"), 1.0,
                                AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                        .build());
    }

    /**
     * «Катись!»: the nearest of the player's own Kolobki, on him or close by, rolls at what the player
     * points at, up to 24 blocks off. With none about, the pin does nothing.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // Vanilla never lets a spectator use an item; nor does the pin, whoever calls it.
        KolobokEntity kolobok = player.isSpectator() ? null : KolobokEntity.bowlerFor(player);
        if (kolobok == null) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel) {
            HitResult hit = ProjectileUtil.getHitResultOnViewVector(player, entity -> entity instanceof LivingEntity
                    && entity != kolobok && !entity.isSpectator() && entity.isPickable(), KolobokEntity.BOWL_RANGE);
            kolobok.bowl(hit.getLocation());
            player.getCooldowns().addCooldown(player.getItemInHand(hand), BOWL_COOLDOWN);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * With Shift held, vanilla skips the furnace's own click and comes straight here, so building the
     * stove needs no event. Vanilla has checked that the player may change the furnace's block; the
     * twelve bricks change too, so each is checked the same way first, and if any may not change,
     * nothing does.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (player == null || player.isSpectator() || !player.isSecondaryUseActive() || !level.getBlockState(pos).is(Blocks.FURNACE)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        StovePattern.Check check = StovePattern.check(level, pos);
        switch (check) {
            case OK -> {
                Direction face = context.getClickedFace();
                ItemStack pin = context.getItemInHand();
                // mayUseItemAt is given the spot in front of the face and checks the block behind it.
                Direction facing = StovePattern.assemble(serverLevel, pos,
                        at -> level.mayInteract(player, at) && player.mayUseItemAt(at.relative(face), face, pin));
                if (facing == null) {
                    return InteractionResult.FAIL;
                }
                AABB box = StovePattern.bounds(pos, facing);
                serverLevel.sendParticles(ParticleTypes.CLOUD, box.getCenter().x, box.getCenter().y, box.getCenter().z,
                        24, box.getXsize() / 3, box.getYsize() / 3, box.getZsize() / 3, 0.02);
                level.playSound(null, pos, SoundEvents.VILLAGER_WORK_MASON, SoundSource.BLOCKS, 1.0F, 1.0F);
                player.sendOverlayMessage(Component.translatable("kolobok.stove.built"));
                TaleTrigger.award(player, Tale.STOVE_BUILT);
            }
            case MISSING_BRICKS -> player.sendOverlayMessage(Component.translatable("kolobok.stove.missing_bricks"));
            case BURNING -> player.sendOverlayMessage(Component.translatable("kolobok.stove.furnace_burning"));
            case NOT_EMPTY -> player.sendOverlayMessage(Component.translatable("kolobok.stove.furnace_not_empty"));
            case NOT_A_FURNACE -> {
                return InteractionResult.PASS;
            }
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
