package ru.bagomot.kolobok.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import ru.bagomot.kolobok.Kolobok;
import ru.bagomot.kolobok.block.StovePartBlock;
import ru.bagomot.kolobok.block.StovePattern;

/**
 * Бабушкина скалка, the remote control for everything. Hits like a wooden sword, but knocks back.
 *
 * <p>Shift and right-click on a furnace builds the Russian stove around it. Right-click on one's own
 * Kolobok switches his mode; that click is handled by Kolobok himself.
 */
public class RollingPinItem extends Item {

    private static final float DAMAGE = 3.0F;
    private static final float SPEED = -2.4F;

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
     * With Shift held, vanilla skips the furnace's own click and comes straight here, so building the
     * stove needs no event.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (player == null || !player.isSecondaryUseActive() || !level.getBlockState(pos).is(Blocks.FURNACE)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        StovePattern.Check check = StovePattern.check(level, pos);
        switch (check) {
            case OK -> {
                StovePattern.assemble(serverLevel, pos);
                AABB box = StovePattern.bounds(pos, level.getBlockState(pos).getValue(StovePartBlock.FACING));
                serverLevel.sendParticles(ParticleTypes.CLOUD, box.getCenter().x, box.getCenter().y, box.getCenter().z,
                        24, box.getXsize() / 3, box.getYsize() / 3, box.getZsize() / 3, 0.02);
                level.playSound(null, pos, SoundEvents.VILLAGER_WORK_MASON, SoundSource.BLOCKS, 1.0F, 1.0F);
                player.sendOverlayMessage(Component.translatable("kolobok.stove.built"));
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
