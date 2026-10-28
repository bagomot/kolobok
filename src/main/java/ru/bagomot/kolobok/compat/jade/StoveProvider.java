package ru.bagomot.kolobok.compat.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import ru.bagomot.kolobok.Kolobok;
import ru.bagomot.kolobok.block.StoveBlockEntity;
import ru.bagomot.kolobok.block.StovePartBlock;
import ru.bagomot.kolobok.block.StovePattern;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Any block of the stove looked at: what is in the горнило and when it is done, the wood and how
 * long it lasts, and whether the stove is too hot, still warm or has a window. With Jade these
 * replace the labels the stove draws itself.
 */
public enum StoveProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID = Kolobok.id("stove");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockState state = accessor.getBlockState();
        BlockPos mouth = StovePattern.mouthOf(accessor.getPosition(),
                state.getValue(StovePartBlock.FACING), state.getValue(StovePartBlock.PART));
        if (!(accessor.getLevel().getBlockEntity(mouth) instanceof StoveBlockEntity stove)) {
            return;
        }
        tooltip.add(Component.translatable("kolobok.stove.status.oven", stove.ovenStatus()));
        Component cooking = stove.cookLabel(0.0F);
        if (cooking != null) {
            tooltip.add(cooking);
        }
        Component fire = stove.fireLabel(0.0F);
        tooltip.add(stove.woodLabel().append(" · ")
                .append(fire != null ? fire : Component.translatable("kolobok.stove.status.cold")));
        if (stove.isTooHot()) {
            tooltip.add(Component.translatable("kolobok.stove.too_hot").withStyle(ChatFormatting.GOLD));
        } else if (!stove.isLit() && stove.isWarm()) {
            tooltip.add(Component.translatable("kolobok.jade.stove.warm"));
        }
        if (StovePattern.hasWindow(accessor.getLevel(), mouth, stove.facing())) {
            tooltip.add(Component.translatable("kolobok.jade.stove.window"));
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
