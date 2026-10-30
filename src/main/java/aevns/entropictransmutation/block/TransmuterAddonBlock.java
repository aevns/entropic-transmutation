package aevns.entropictransmutation.block;

import aevns.entropictransmutation.EntropicTransmutation;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock ;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class TransmuterAddonBlock extends HorizontalDirectionalBlock  {

    public static final MapCodec<TransmuterAddonBlock> CODEC = simpleCodec(TransmuterAddonBlock::new);

    public TransmuterAddonBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public MapCodec<TransmuterAddonBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace();
        if (facing.getAxis().isHorizontal()) {
            return this.defaultBlockState().setValue(FACING, facing);
        }
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos attachedPos = pos.relative(facing.getOpposite());
        BlockState attachedState = level.getBlockState(attachedPos);
        return attachedState.is(EntropicTransmutation.TRANSMUTER) || attachedState.is(EntropicTransmutation.TRANSMUTER_MONITOR) && (facing != attachedState.getValue(FACING)); // can be a BlockTag
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
        Direction facing = state.getValue(FACING);
        if (direction == facing.getOpposite()) {
            if (!state.canSurvive(level, pos)) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        // In a real class, you would use a switch(state.getValue(FACING))
        // to return a rotated VoxelShape variation based on how the block faces!
        return switch (state.getValue(FACING)) {
            case NORTH -> Block.box(4.0, 4.0, 13.0, 12.0, 12.0, 16.0);
            case SOUTH -> Block.box(4.0, 4.0, 0.0, 12.0, 12.0, 3.0);
            case WEST -> Block.box(13.0, 4.0, 4.0, 16.0, 12.0, 12.0);
            default -> Block.box(0.0, 4.0, 4.0, 3.0, 12.0, 12.0);
        };
    }
}