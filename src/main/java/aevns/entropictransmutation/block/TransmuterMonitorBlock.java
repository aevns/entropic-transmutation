package aevns.entropictransmutation.block;

import aevns.entropictransmutation.block.entity.TransmuterBlockEntity;
import aevns.entropictransmutation.menu.TransmuterMenu;
import aevns.entropictransmutation.registry.ETBlocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.BlockHitResult;
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
    private static final Component CONTAINER_TITLE = Component.translatable("container.entropictransmutation.transmuter");

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
        boolean validBlock = attachedState.is(ETBlocks.TRANSMUTER);
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

    /** Addons held against a face they can attach to are placed there instead of opening the menu. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (TransmuterAddonBlock.canAttach(stack, state, player, hand, hitResult)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    /** Opens the menu of the transmuter below, which owns the inventory this monitor displays. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos.below()) instanceof TransmuterBlockEntity transmuter)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            player.openMenu(new SimpleMenuProvider(
                    (containerId, playerInventory, p) -> new TransmuterMenu(containerId, playerInventory, transmuter),
                    CONTAINER_TITLE
            ), transmuter.getBlockPos());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
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