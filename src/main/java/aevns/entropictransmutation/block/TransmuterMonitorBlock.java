package aevns.entropictransmutation.block;

import aevns.entropictransmutation.EntropicTransmutation;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;

import java.util.Map;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class TransmuterMonitorBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<TransmuterMonitorBlock> CODEC = simpleCodec(TransmuterMonitorBlock::new);
    private static final VoxelShape NORTH_SHAPE = Block.box(0, 0, 4, 16, 16, 16);
    private static final Map<Direction, VoxelShape> SHAPES = TransmuterBlock.rotations(NORTH_SHAPE);

    public TransmuterMonitorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public MapCodec<TransmuterMonitorBlock> codec() {return CODEC;}

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState transmuterState = context.getLevel().getBlockState(context.getClickedPos().below());
        if (transmuterState.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return this.defaultBlockState().setValue(
                    HorizontalDirectionalBlock.FACING,
                    transmuterState.getValue(HorizontalDirectionalBlock.FACING)
            );
        }
        return this.defaultBlockState().setValue(FACING, Direction.NORTH);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockState attachedState = level.getBlockState(pos.relative(Direction.DOWN));
        boolean validBlock = attachedState.is(EntropicTransmutation.TRANSMUTER_BLOCK);
        return validBlock && (facing == attachedState.getValue(FACING));
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos
    ) {
        if (direction == Direction.DOWN) {
            if (!state.canSurvive(level, pos)) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    // No block entity here, so capability caches pointing at this position must be invalidated manually
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock())) {
            level.invalidateCapabilities(pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!state.is(newState.getBlock())) {
            level.invalidateCapabilities(pos);
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }
}