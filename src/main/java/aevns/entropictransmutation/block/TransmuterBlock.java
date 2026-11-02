package aevns.entropictransmutation.block;

import aevns.entropictransmutation.block.entity.TransmuterBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.EnumMap;
import java.util.Map;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class TransmuterBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<TransmuterBlock> CODEC = simpleCodec(TransmuterBlock::new);

    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 11, 16),
            Block.box(0, 11, 4, 16, 16, 16)
    ).optimize();
    private static final Map<Direction, VoxelShape> SHAPES = rotations(NORTH_SHAPE);

    private static final VoxelShape NORTH_SUPPORT_SHAPE = Shapes.join(
            Shapes.block(), Block.box(1, 1, 0, 15, 15, 1), BooleanOp.ONLY_FIRST
    ).optimize();
    private static final Map<Direction, VoxelShape> SUPPORT_SHAPES = rotations(NORTH_SUPPORT_SHAPE);

    private static Map<Direction, VoxelShape> rotations(VoxelShape north) {
        return Util.make(new EnumMap<>(Direction.class), map -> {
            VoxelShape shape = north;
            for (Direction dir : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                map.put(dir, shape);
                shape = rotateY(shape);
            }
        });
    }

    private static VoxelShape rotateY(VoxelShape shape) {
        VoxelShape[] result = {Shapes.empty()};
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) ->
                result[0] = Shapes.or(result[0], Shapes.box(1 - z2, y1, x1, 1 - z1, y2, x2)));
        return result[0].optimize();
    }

    public TransmuterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public MapCodec<TransmuterBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SUPPORT_SHAPES.get(state.getValue(FACING));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TransmuterBlockEntity(pos, state);
    }

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof TransmuterBlockEntity transmuter)
                || stack.isEmpty()
                || !transmuter.canAcceptFromPlayer(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            player.setItemInHand(hand, transmuter.insertFromPlayer(stack));
        }
        level.playSound(player, pos, SoundEvents.VAULT_INSERT_ITEM, SoundSource.BLOCKS,
                0.5f, level.getRandom().nextFloat() * 0.2f + 0.9f);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof TransmuterBlockEntity transmuter)
                || (transmuter.isInputEmpty() && transmuter.isOutputEmpty())) {
            return InteractionResult.PASS;
        }
        boolean hadOutput = !transmuter.isOutputEmpty();
        if (!level.isClientSide) {
            Direction face = state.getValue(FACING);
            for (ItemStack stack : transmuter.takeContents()) {
                giveOrEject(player, level, pos, face, stack);
            }
        }
        level.playSound(player, pos,
                hadOutput ? SoundEvents.VAULT_EJECT_ITEM : SoundEvents.VAULT_INSERT_ITEM_FAIL,
                SoundSource.BLOCKS, 0.25f, level.getRandom().nextFloat() * 0.2f + 0.9f);
        return InteractionResult.SUCCESS_NO_ITEM_USED;
    }

    /** Splits a possibly-oversized stack into legal chunks, fills the inventory, ejects the rest. */
    private static void giveOrEject(Player player, Level level, BlockPos pos, Direction face, ItemStack stack) {
        int max = Math.max(1, stack.getMaxStackSize());
        while (!stack.isEmpty()) {
            ItemStack chunk = stack.split(max);
            player.getInventory().add(chunk); // shrinks chunk by whatever fit
            if (!chunk.isEmpty()) {
                ejectFromFace(level, pos, face, chunk);
            }
        }
    }

    /**
     * Same placement as Block.popResourceFromFace, but without its doTileDrops
     * gamerule check, which would delete the player's items when that rule is off.
     */
    private static void ejectFromFace(Level level, BlockPos pos, Direction face, ItemStack stack) {
        RandomSource rng = level.random;
        double halfW = EntityType.ITEM.getWidth() / 2.0;
        double halfH = EntityType.ITEM.getHeight() / 2.0;
        int sx = face.getStepX(), sy = face.getStepY(), sz = face.getStepZ();

        double x = pos.getX() + 0.5 + (sx == 0 ? Mth.nextDouble(rng, -0.25, 0.25) : sx * (0.5 + halfW));
        double y = pos.getY() + 0.5 + (sy == 0 ? Mth.nextDouble(rng, -0.25, 0.25) : sy * (0.5 + halfH)) - halfH;
        double z = pos.getZ() + 0.5 + (sz == 0 ? Mth.nextDouble(rng, -0.25, 0.25) : sz * (0.5 + halfW));

        double vx = sx == 0 ? Mth.nextDouble(rng, -0.1, 0.1) : sx * 0.1;
        double vy = sy == 0 ? Mth.nextDouble(rng, 0.0, 0.1) : sy * 0.1;
        double vz = sz == 0 ? Mth.nextDouble(rng, -0.1, 0.1) : sz * 0.1;

        ItemEntity entity = new ItemEntity(level, x, y, z, stack, vx, vy, vz);
        entity.setDefaultPickUpDelay();
        level.addFreshEntity(entity);
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TransmuterBlockEntity transmuter) {
            for (ItemStack stack : transmuter.removeAllContents()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource rng) {
        if (level.getBlockEntity(pos) instanceof TransmuterBlockEntity transmuter) {
            transmuter.transmute(rng);
        }
    }
}