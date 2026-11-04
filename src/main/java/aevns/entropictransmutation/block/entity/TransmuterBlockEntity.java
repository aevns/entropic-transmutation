package aevns.entropictransmutation.block.entity;

import aevns.entropictransmutation.block.TransmuterAddonBlock;
import aevns.entropictransmutation.block.TransmuterBlock;
import aevns.entropictransmutation.registry.ModBlockEntities;
import aevns.entropictransmutation.registry.ModBlocks;
import aevns.entropictransmutation.samplers.EmcSampler;
import aevns.entropictransmutation.samplers.maxent.MaxEntSampler;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class TransmuterBlockEntity extends BlockEntity {
    public static final int MAX_TRANSMUTATION_TIME = 30;
    public static final int OUTPUT_COUNT = 4;
    /** Index into MaxEntSampler.SUPPORTED_EXPECTED_COUNTS used with no count suppressors: the largest stacks. */
    public static final int DEFAULT_COUNT_INDEX = MaxEntSampler.SUPPORTED_EXPECTED_COUNTS.length - 1;
    /** Type suppressors beyond this have no further effect. */
    public static final int MAX_TYPE_SUPPRESSORS = OUTPUT_COUNT - 1;
    /** Count suppressors beyond this have no further effect. */
    public static final int MAX_COUNT_SUPPRESSORS = DEFAULT_COUNT_INDEX;
    /** The most void amplifiers that can attach: 3 faces each on the transmuter and the monitor. */
    public static final int MAX_VOID_AMPLIFIERS = 6;

    /** Game time at which the current input transmutes, or -1 if no timer is running. */
    private long transmutationTime = -1;
    /** Suppresses per-slot notifications during multi-slot changes. */
    private boolean batching = false;

    private final ItemStackHandler inputSlot = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            onInventoryChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return hasPositiveSellValue(stack);
        }
    };

    private final ItemStackHandler outputSlots = new ItemStackHandler(OUTPUT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            onInventoryChanged();
        }
    };

    private final IItemHandler automationHandler = new AutomationHandler();

    public TransmuterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRANSMUTER.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // Persistence & sync
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("TransmutationTime", transmutationTime);
        tag.put("InputSlot", inputSlot.serializeNBT(registries));
        tag.put("OutputSlots", outputSlots.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        transmutationTime = tag.contains("TransmutationTime") ? tag.getLong("TransmutationTime") : -1;
        if (tag.contains("InputSlot")) {
            inputSlot.deserializeNBT(registries, tag.getCompound("InputSlot"));
        }
        if (tag.contains("OutputSlots")) {
            outputSlots.deserializeNBT(registries, tag.getCompound("OutputSlots"));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    // NeoForge's default onDataPacket calls loadWithComponents -> loadAdditional, so no override needed.

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    public boolean isInputEmpty() {
        return inputSlot.getStackInSlot(0).isEmpty();
    }

    public boolean isOutputEmpty() {
        for (int i = 0; i < outputSlots.getSlots(); i++) {
            if (!outputSlots.getStackInSlot(i).isEmpty()) return false;
        }
        return true;
    }

    /** Handler exposed to hoppers/pipes: slot 0 is insert-only input, slots 1..4 are extract-only outputs. */
    public IItemHandler getAutomationHandler() {
        return automationHandler;
    }

    /**
     * How many of each addon block are attached to the sides or back of this transmuter, or to the sides or back of
     * a monitor above it. Absent addons map to 0. Checked on demand, so it always reflects the current world state.
     */
    public Object2IntMap<Block> getAttachedAddons() {
        Object2IntMap<Block> addons = new Object2IntOpenHashMap<>();
        if (level == null) return addons;
        collectAttachedAddons(worldPosition, addons);
        BlockPos monitorPos = worldPosition.above();
        if (level.getBlockState(monitorPos).is(ModBlocks.TRANSMUTER_MONITOR)) {
            collectAttachedAddons(monitorPos, addons);
        }
        return addons;
    }

    /** Each type suppressor removes one sample, down to a single sample. */
    public static int sampleCount(int typeSuppressors) {
        return OUTPUT_COUNT - Math.min(typeSuppressors, MAX_TYPE_SUPPRESSORS);
    }

    /** Each count suppressor divides the expected stack size by 4, down to the smallest supported. */
    public static int countIndex(int countSuppressors) {
        return DEFAULT_COUNT_INDEX - Math.min(countSuppressors, MAX_COUNT_SUPPRESSORS);
    }

    /**
     * Each void amplifier halves the chance of sampling at all, and doubles the share when it does happen.
     * Returns both factors: sampling happens with chance 1 / multiplier, with the share scaled by multiplier.
     */
    public static int voidMultiplier(int voidAmplifiers) {
        return 1 << voidAmplifiers;
    }

    /** Ticks elapsed on the current transmutation timer, from 0 to MAX_TRANSMUTATION_TIME; 0 when idle. */
    public int getProgressTicks() {
        if (level == null || transmutationTime < 0) return 0;
        long remaining = transmutationTime - level.getGameTime();
        return (int) Math.max(0, Math.min(MAX_TRANSMUTATION_TIME, MAX_TRANSMUTATION_TIME - remaining));
    }

    /** Raw input handler for menu slots, which enforce the insert rules themselves via canTopUpInput. */
    public ItemStackHandler getInputHandler() {
        return inputSlot;
    }

    /** Raw output handler for menu slots, which must not accept insertion. */
    public ItemStackHandler getOutputHandler() {
        return outputSlots;
    }

    /** Automation and menus may top up the input, but only while there's no pending output. */
    public boolean canTopUpInput(ItemStack stack) {
        return isOutputEmpty() && inputSlot.isItemValid(0, stack);
    }

    /** Players may only insert into a completely idle machine. Safe to call on either side. */
    public boolean canAcceptFromPlayer(ItemStack stack) {
        return isInputEmpty() && isOutputEmpty() && inputSlot.isItemValid(0, stack);
    }

    /** Server only. Returns the remainder of the stack that didn't fit. */
    public ItemStack insertFromPlayer(ItemStack stack) {
        if (!canAcceptFromPlayer(stack)) return stack;
        return inputSlot.insertItem(0, stack, false);
    }

    /**
     * Server only. Removes the outputs if any exist, otherwise the input.
     * Stacks are returned whole and may exceed their max stack size.
     */
    public List<ItemStack> takeContents() {
        List<ItemStack> taken = drain(!isOutputEmpty() ? outputSlots : inputSlot);
        if (!taken.isEmpty()) onInventoryChanged();
        return taken;
    }

    /** Server only. Empties every slot without syncing; used when the block is broken. */
    public List<ItemStack> removeAllContents() {
        List<ItemStack> all = drain(inputSlot);
        all.addAll(drain(outputSlots));
        transmutationTime = -1;
        return all;
    }

    /** Called from the block's scheduled tick. */
    public void transmute(RandomSource rng) {
        if (level == null || isInputEmpty() || !isOutputEmpty()) return;
        if (transmutationTime >= 0 && level.getGameTime() < transmutationTime) {
            scheduleTick(); // an older tick fired early, e.g. after remove + re-insert
            return;
        }
        if (!EmcSampler.isReady()) {
            // Keep the input and try again once EMC data has had time to arrive
            transmutationTime = level.getGameTime() + MAX_TRANSMUTATION_TIME;
            scheduleTick();
            return;
        }
        transmutationTime = -1;

        Object2IntMap<Block> addons = getAttachedAddons();
        int sampleCount = sampleCount(addons.getInt(ModBlocks.TYPE_SUPPRESSOR.get()));
        int countIndex = countIndex(addons.getInt(ModBlocks.COUNT_SUPPRESSOR.get()));
        int voidMultiplier = voidMultiplier(addons.getInt(ModBlocks.VOID_AMPLIFIER.get()));

        ItemStack input = inputSlot.getStackInSlot(0);
        double share = input.getCount() * (double) IEMCProxy.INSTANCE.getSellValue(input) * voidMultiplier / sampleCount;
        if (share > 0) {
            boolean sampling = rng.nextInt(voidMultiplier) == 0;
            batching = true;
            try {
                inputSlot.setStackInSlot(0, ItemStack.EMPTY);
                for (int i = 0; sampling && i < sampleCount; i++) {
                    outputSlots.setStackInSlot(i, EmcSampler.sampleItemStack(countIndex, share, rng));
                }
            } finally {
                batching = false;
            }
            markDirtyAndSync();
        }

        if (isOutputEmpty()) {
            level.playSound(null, worldPosition, SoundEvents.VAULT_REJECT_REWARDED_PLAYER, SoundSource.BLOCKS,
                    0.125f, rng.nextFloat() * 0.4f + 0.8f);
        } else {
            level.playSound(null, worldPosition, SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS,
                    0.5f, rng.nextFloat() * 0.2f + 0.9f);
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private static boolean hasPositiveSellValue(ItemStack stack) {
        return !stack.isEmpty() && IEMCProxy.INSTANCE.getSellValue(stack) > 0;
    }

    /** Counts addons mounted on the horizontal faces of pos, other than the front, into addons. */
    private void collectAttachedAddons(BlockPos pos, Object2IntMap<Block> addons) {
        // The monitor always shares the transmuter's facing, so the same front applies to both
        Direction front = getBlockState().getValue(TransmuterBlock.FACING);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (side == front) continue;
            BlockState neighbor = level.getBlockState(pos.relative(side));
            // An addon faces away from the block it's mounted on
            if (neighbor.getBlock() instanceof TransmuterAddonBlock
                    && neighbor.getValue(TransmuterAddonBlock.FACING) == side) {
                addons.mergeInt(neighbor.getBlock(), 1, Integer::sum);
            }
        }
    }

    private List<ItemStack> drain(ItemStackHandler handler) {
        List<ItemStack> out = new ArrayList<>();
        batching = true;
        try {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    out.add(stack);
                    handler.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        } finally {
            batching = false;
        }
        return out;
    }

    private void onInventoryChanged() {
        if (batching || level == null || level.isClientSide) return;
        updateTimer();
        markDirtyAndSync();
    }

    /** Starts the timer when the input becomes non-empty; top-ups don't reset it. */
    private void updateTimer() {
        if (isInputEmpty() || !isOutputEmpty()) {
            transmutationTime = -1;
            return;
        }
        if (transmutationTime < 0) {
            transmutationTime = level.getGameTime() + MAX_TRANSMUTATION_TIME;
            scheduleTick();
        }
    }

    private void scheduleTick() {
        if (level == null || level.isClientSide) return;
        int delay = (int) Math.max(1, transmutationTime - level.getGameTime());
        level.scheduleTick(worldPosition, getBlockState().getBlock(), delay);
    }

    private void markDirtyAndSync() {
        if (level == null) return;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private class AutomationHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return 1 + OUTPUT_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? inputSlot.getStackInSlot(0) : outputSlots.getStackInSlot(slot - 1);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || !canTopUpInput(stack)) return stack;
            return inputSlot.insertItem(0, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot == 0) return ItemStack.EMPTY;
            return outputSlots.extractItem(slot - 1, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == 0 ? inputSlot.getSlotLimit(0) : outputSlots.getSlotLimit(slot - 1);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && inputSlot.isItemValid(0, stack);
        }
    }
}