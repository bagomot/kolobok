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

    /**
     * Outlines as the model draws them, for a stove facing north, then turned for every facing. The
     * openings of the подпечье and the горнило are cut out, so the crosshair reaches the logs, the
     * bread and a Kolobok sitting inside.
     */
    private static final Map<StovePart, Map<Direction, VoxelShape>> SHAPES = new EnumMap<>(StovePart.class);
    /** Whatever the outline, the stove is solid to walk into, but for the narrow chimney. */
    private static final Map<Direction, VoxelShape> CHIMNEY = Shapes.rotateHorizontal(northShape(StovePart.CHIMNEY));

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

    /**
     * The подпечье and the bottom of the горнило open the mouth's block, the rest of the горнило the
     * block above; the chimney is a column. The back of the горнило stays inside the outline: nothing
     * there to point at.
     */
    private static VoxelShape northShape(StovePart part) {
        return switch (part) {
            case MOUTH -> Shapes.join(Shapes.block(), Shapes.or(podpechye(), gornilo(0)), BooleanOp.ONLY_FIRST);
            case UPPER_FRONT_MIDDLE -> Shapes.join(Shapes.block(), gornilo(16), BooleanOp.ONLY_FIRST);
            case CHIMNEY -> Shapes.or(Block.box(3, 0, 3, 13, 16, 13), Block.box(2, 12, 2, 14, 16, 14));
            default -> Shapes.block();
        };
    }

    private static VoxelShape podpechye() {
        return arch(StovePattern.PODPECHYE_X0, StovePattern.PODPECHYE_X1, StovePattern.PODPECHYE_Y0,
                StovePattern.PODPECHYE_Y1, StovePattern.PODPECHYE_DEPTH, 0);
    }

    /** The front block's share of the горнило, the block's floor this high above the mouth's. */
    private static VoxelShape gornilo(int cellY) {
        return arch(StovePattern.GORNILO_X0, StovePattern.GORNILO_X1, StovePattern.GORNILO_FLOOR,
                StovePattern.GORNILO_FLOOR + StovePattern.GORNILO_HEIGHT, 16, cellY);
    }

    /**
     * A round-topped opening from the front face, as tools/stove carves it: full width up to three
     * pixels below the top, then narrower by one, two and four pixels a side. Heights are counted
     * from the mouth's floor; what is outside the block whose floor is {@code cellY} is left out.
     */
    private static VoxelShape arch(int x0, int x1, int y0, int y1, int depth, int cellY) {
        int[][] rows = {{0, y0, y1 - 3}, {1, y1 - 3, y1 - 2}, {2, y1 - 2, y1 - 1}, {4, y1 - 1, y1}};
        VoxelShape out = Shapes.empty();
        for (int[] row : rows) {
            int bottom = Math.max(row[1] - cellY, 0);
            int top = Math.min(row[2] - cellY, 16);
            if (bottom < top) {
                out = Shapes.or(out, Block.box(x0 + row[0], bottom, 0, x1 - row[0], top, depth));
            }
        }
        return out;
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
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(PART) == StovePart.CHIMNEY ? CHIMNEY.get(state.getValue(FACING)) : Shapes.block();
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
        if (stove == null) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        // The fire is lit in the горнило, through the устье: the mouth's block and the one above it.
        StovePart part = state.getValue(PART);
        if (StoveBlockEntity.isIgniter(stack) && (part == StovePart.MOUTH || part == StovePart.UPPER_FRONT_MIDDLE)) {
            return stove.ignite(stack, player, hand);
        }
        return stove.useItem(stack, player, hand);
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
     * Flames over the coals at the back of the горнило, smoke drifting out of the устье and the
     * crackle of a vanilla furnace, while it burns.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) != StovePart.MOUTH || !state.getValue(LIT)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double across = StovePattern.GORNILO_X0 + 2 + random.nextDouble() * (StovePattern.GORNILO_X1 - StovePattern.GORNILO_X0 - 4);
        double deep = StovePattern.COALS_FROM + random.nextDouble() * 8;
        Vec3 fire = StovePattern.local(pos, facing, across, StovePattern.GORNILO_FLOOR + 1 + random.nextDouble() * 3, deep);
        if (random.nextDouble() < 0.1) {
            level.playLocalSound(fire.x, fire.y, fire.z, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS,
                    1.0F, 1.0F, false);
        }
        level.addParticle(ParticleTypes.FLAME, fire.x, fire.y, fire.z, 0.0, 0.0, 0.0);
        if (random.nextInt(4) == 0) {
            Vec3 smoke = StovePattern.local(pos, facing, across, StovePattern.GORNILO_FLOOR + StovePattern.GORNILO_HEIGHT - 2, 1);
            level.addParticle(ParticleTypes.SMOKE, smoke.x, smoke.y, smoke.z,
                    facing.getStepX() * 0.01, 0.02, facing.getStepZ() * 0.01);
        }
    }
}
