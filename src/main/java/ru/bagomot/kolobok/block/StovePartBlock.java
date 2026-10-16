package ru.bagomot.kolobok.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import ru.bagomot.kolobok.registry.KBlockEntities;

import java.util.EnumMap;
import java.util.Map;

/**
 * One of the thirteen blocks of an assembled Russian stove. Not in any inventory: it exists only
 * while the stove stands, and every part turns back into a brick or the furnace when any of them
 * goes.
 *
 * <p>The mouth holds the stove's block entity; a click on any other part is passed to it.
 */
public class StovePartBlock extends BaseEntityBlock {

    public static final EnumProperty<StovePart> PART = EnumProperty.create("part", StovePart.class);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** Shapes as the model draws them, for a stove facing north, then turned for every facing. */
    private static final Map<StovePart, Map<Direction, VoxelShape>> SHAPES = new EnumMap<>(StovePart.class);

    static {
        for (StovePart part : StovePart.values()) {
            SHAPES.put(part, Shapes.rotateHorizontal(northShape(part)));
        }
    }

    public StovePartBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(PART, StovePart.MOUTH)
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false));
    }

    /** The mouth's niche and the space under the stove are open; the chimney is a column. */
    private static VoxelShape northShape(StovePart part) {
        return switch (part) {
            case MOUTH -> Shapes.join(Shapes.block(),
                    Shapes.or(Block.box(2, StovePattern.HEARTH, 0, 14, 16, 10), Block.box(2, 3, 0, 14, 8, 6)),
                    BooleanOp.ONLY_FIRST);
            case UPPER_FRONT_MIDDLE -> Shapes.join(Shapes.block(), Block.box(2, 0, 0, 14, 4, 10), BooleanOp.ONLY_FIRST);
            case CHIMNEY -> Shapes.or(Block.box(3, 0, 3, 13, 16, 13), Block.box(2, 12, 2, 14, 16, 14));
            default -> Shapes.block();
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING, LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(PART)).get(state.getValue(FACING));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    // ------------------------------------------------------------------------------------ block entity

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == StovePart.MOUTH ? new StoveBlockEntity(pos, state) : null;
    }

    /**
     * Only a burning stove ticks: the server counts fuel and baking, the client blows smoke.
     */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                             BlockEntityType<T> type) {
        if (state.getValue(PART) != StovePart.MOUTH || !state.getValue(LIT)) {
            return null;
        }
        return createTickerHelper(type, KBlockEntities.STOVE.get(),
                level.isClientSide() ? StoveBlockEntity::clientTick : StoveBlockEntity::serverTick);
    }

    private static @Nullable StoveBlockEntity stoveOf(Level level, BlockPos pos, BlockState state) {
        BlockPos mouth = StovePattern.mouthOf(pos, state.getValue(FACING), state.getValue(PART));
        return level.getBlockEntity(mouth) instanceof StoveBlockEntity stove ? stove : null;
    }

    // ------------------------------------------------------------------------------------ interaction

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        StoveBlockEntity stove = stoveOf(level, pos, state);
        return stove == null ? InteractionResult.TRY_WITH_EMPTY_HAND : stove.useItem(stack, player, hand);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        StoveBlockEntity stove = stoveOf(level, pos, state);
        return stove == null ? InteractionResult.PASS : stove.useEmptyHand(player);
    }

    // ------------------------------------------------------------------------------------ removal

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        StovePattern.disassemble(level, pos, state);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    /**
     * Pick-block gives what the part was built from. NeoForge deprecates this for its own
     * extension; the vanilla method is the one both loaders share.
     */
    @Override
    @SuppressWarnings("deprecation")
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(StovePattern.original(state.getValue(PART), state.getValue(FACING)).getBlock());
    }

    // ------------------------------------------------------------------------------------ fire

    /**
     * Flames and smoke deep in the mouth and the crackle of a vanilla furnace, while it burns.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) != StovePart.MOUTH || !state.getValue(LIT)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        // The firebox is four pixels behind the block's middle.
        Vec3 fire = Vec3.atBottomCenterOf(pos).add(facing.getStepX() * -0.25, StovePattern.HEARTH / 16.0,
                facing.getStepZ() * -0.25);
        if (random.nextDouble() < 0.1) {
            level.playLocalSound(fire.x, fire.y, fire.z, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS,
                    1.0F, 1.0F, false);
        }
        double side = random.nextDouble() * 0.3 - 0.15;
        double x = fire.x + (facing.getAxis() == Direction.Axis.Z ? side : 0);
        double z = fire.z + (facing.getAxis() == Direction.Axis.X ? side : 0);
        double y = fire.y + random.nextDouble() * 0.25;
        level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0, 0.0, 0.0);
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMOKE, x + facing.getStepX() * 0.3, y + 0.2, z + facing.getStepZ() * 0.3,
                    0.0, 0.0, 0.0);
        }
    }
}
