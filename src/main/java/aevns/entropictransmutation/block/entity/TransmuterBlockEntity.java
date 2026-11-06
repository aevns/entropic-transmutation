package aevns.entropictransmutation.block.entity;

import aevns.entropictransmutation.api.TransmutationSettings;
import aevns.entropictransmutation.api.TransmuterAddon;
import aevns.entropictransmutation.block.TransmuterAddonBlock;
import aevns.entropictransmutation.block.TransmuterBlock;
import aevns.entropictransmutation.registry.ETAddons;
import aevns.entropictransmutation.registry.ETBlockEntities;
import aevns.entropictransmutation.registry.ETBlocks;
import aevns.entropictransmutation.samplers.EmcSampler;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class TransmuterBlockEntity extends BlockEntity {
    public static final int MAX_TRANSMUTATION_TIME = 30;
    public static final int OUTPUT_COUNT = 4;
    /** Value an input must fall short of the minimum mean payout by for the machine to be certain to explode. */
    private static final double CERTAIN_EXPLOSION_SHORTFALL = 20000;
    private static final float EXPLOSION_POWER = 1.0f;

    /** One transmutation of the current input, with the addons attached at the time. */
    private record Transmutation(TransmutationSettings settings, double inputValue) {
        double share() {
            return inputValue * settings.voidMultiplier() / settings.sampleCount();
        }

        /** How far the input's value is below the lowest mean total payout the sampler can honor; negative if it isn't. */
        double shortfall() {
            return EmcSampler.minMeanStackValue(settings.countIndex()) * settings.sampleCount() / settings.voidMultiplier()
                    - inputValue;
        }
    }

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
        super(ETBlockEntities.TRANSMUTER.get(), pos, state);
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
    // Shared with the block, menu and screen
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

    /**
     * Handler exposed to hoppers/pipes on every side, of both the transmuter and its monitor. Slot 0 is the input, which
     * can only be inserted into, and only while no output is waiting; slots 1..4 are the outputs, which can only be
     * extracted from. Output stacks of unstackable items can be larger than their max stack size.
     */
    public IItemHandler getAutomationHandler() {
        return automationHandler;
    }

    /**
     * How many of each addon are attached to the sides or back of this transmuter, or to the sides or back of
     * a monitor above it. Absent addons map to 0. Checked on demand, so it always reflects the current world state.
     */
    public Object2IntMap<TransmuterAddon> getAttachedAddons(ServerLevel level) {
        Object2IntMap<TransmuterAddon> addons = new Object2IntOpenHashMap<>();
        collectAttachedAddons(level, worldPosition, addons);
        BlockPos monitorPos = worldPosition.above();
        if (level.getBlockState(monitorPos).is(ETBlocks.TRANSMUTER_MONITOR)) {
            collectAttachedAddons(level, monitorPos, addons);
        }
        return addons;
    }

    /** Ticks elapsed on the current transmutation timer, from 0 to MAX_TRANSMUTATION_TIME; 0 when idle. */
    public int getProgressTicks(ServerLevel level) {
        if (transmutationTime < 0) return 0;
        long remaining = transmutationTime - level.getGameTime();
        return (int) Math.max(0, Math.min(MAX_TRANSMUTATION_TIME, MAX_TRANSMUTATION_TIME - remaining));
    }

    /** Comparator signal from the output slots. Limited to 15, since unstackable outputs can overfill their slots. */
    public int getOutputSignal() {
        return Math.min(15, ItemHandlerHelper.calcRedstoneFromInventory(outputSlots));
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

    /** Server only, for stacks that pass canAcceptFromPlayer. Returns the remainder that did not fit. */
    public ItemStack insertFromPlayer(ItemStack stack) {
        return inputSlot.insertItem(0, stack, false);
    }

    /**
     * Server only, for a machine that is not empty. Removes the outputs if any exist, otherwise the input.
     * Stacks are returned whole and may exceed their max stack size.
     */
    public List<ItemStack> takeContents() {
        List<ItemStack> taken = drain(!isOutputEmpty() ? outputSlots : inputSlot);
        onInventoryChanged();
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
    public void transmute(ServerLevel level, RandomSource rng) {
        if (isInputEmpty() || !isOutputEmpty()) return;
        if (level.getGameTime() < transmutationTime) {
            scheduleTick(level); // an older tick fired early, e.g. after remove + re-insert
            return;
        }
        if (!EmcSampler.isReady()) {
            // Keep the input and try again once EMC data has had time to arrive
            transmutationTime = level.getGameTime() + MAX_TRANSMUTATION_TIME;
            scheduleTick(level);
            return;
        }
        transmutationTime = -1;

        Transmutation transmutation = planTransmutation(level);
        double share = transmutation.share();
        if (share > 0) {
            if (rng.nextDouble() * CERTAIN_EXPLOSION_SHORTFALL < transmutation.shortfall()) {
                level.playSound(null, worldPosition, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS,
                        1.0f, 1.0f);
                explode(level);
                return;
            }

            TransmutationSettings settings = transmutation.settings();
            boolean sampling = rng.nextInt(settings.voidMultiplier()) == 0;
            batching = true;
            inputSlot.setStackInSlot(0, ItemStack.EMPTY);
            for (int i = 0; sampling && i < settings.sampleCount(); i++) {
                outputSlots.setStackInSlot(i, EmcSampler.sampleItemStack(settings.countIndex(), share, rng));
            }
            batching = false;
            markDirtyAndSync(level);
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

    private Transmutation planTransmutation(ServerLevel level) {
        ItemStack input = inputSlot.getStackInSlot(0);
        return new Transmutation(TransmutationSettings.forAddons(getAttachedAddons(level)::getInt),
                input.getCount() * (double) IEMCProxy.INSTANCE.getSellValue(input));
    }

    /** Destroys the machine and its input, without dropping either. Removed first so it can't shield the blast. */
    private void explode(ServerLevel level) {
        inputSlot.setStackInSlot(0, ItemStack.EMPTY);
        level.removeBlock(worldPosition, false);
        level.explode(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                EXPLOSION_POWER, Level.ExplosionInteraction.BLOCK);
    }

    private static boolean hasPositiveSellValue(ItemStack stack) {
        return !stack.isEmpty() && IEMCProxy.INSTANCE.getSellValue(stack) > 0;
    }

    /** Counts addons mounted on the horizontal faces of pos, other than the front, into addons. */
    private void collectAttachedAddons(ServerLevel level, BlockPos pos, Object2IntMap<TransmuterAddon> addons) {
        // The monitor always shares the transmuter's facing, so the same front applies to both
        Direction front = getBlockState().getValue(TransmuterBlock.FACING);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (side == front) continue;
            BlockState neighbor = level.getBlockState(pos.relative(side));
            // An addon faces away from the block it's mounted on
            if (neighbor.getBlock() instanceof TransmuterAddonBlock
                    && neighbor.getValue(TransmuterAddonBlock.FACING) == side) {
                TransmuterAddon addon = ETAddons.forBlock(neighbor.getBlock());
                if (addon != null) addons.mergeInt(addon, 1, Integer::sum);
            }
        }
    }

    private List<ItemStack> drain(ItemStackHandler handler) {
        List<ItemStack> out = new ArrayList<>();
        batching = true;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                out.add(stack);
                handler.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        batching = false;
        return out;
    }

    private void onInventoryChanged() {
        if (batching || !(this.level instanceof ServerLevel level)) return;
        updateTimer(level);
        markDirtyAndSync(level);
    }

    /** Starts the timer when the input becomes non-empty; top-ups don't reset it. */
    private void updateTimer(ServerLevel level) {
        if (isInputEmpty() || !isOutputEmpty()) {
            transmutationTime = -1;
            return;
        }
        if (transmutationTime < 0) {
            transmutationTime = level.getGameTime() + MAX_TRANSMUTATION_TIME;
            scheduleTick(level);
            if (EmcSampler.isReady() && planTransmutation(level).shortfall() > 0) {
                level.playSound(null, worldPosition, SoundEvents.VAULT_AMBIENT, SoundSource.BLOCKS, 1.0f, 1.0f);
            }
        }
    }

    private void scheduleTick(ServerLevel level) {
        int delay = (int) Math.max(1, transmutationTime - level.getGameTime());
        level.scheduleTick(worldPosition, getBlockState().getBlock(), delay);
    }

    private void markDirtyAndSync(ServerLevel level) {
        setChanged(); // updates comparators around this block
        BlockPos above = worldPosition.above();
        BlockState aboveState = level.getBlockState(above);
        if (aboveState.is(ETBlocks.TRANSMUTER_MONITOR)) {
            level.updateNeighbourForOutputSignal(above, aboveState.getBlock());
        }
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