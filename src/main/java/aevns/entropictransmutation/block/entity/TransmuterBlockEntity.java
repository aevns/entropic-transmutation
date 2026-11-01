package aevns.entropictransmutation.block.entity;

import aevns.entropictransmutation.EntropicTransmutation;


import aevns.entropictransmutation.block.TransmuterBlock;
import aevns.entropictransmutation.samplers.EmcSampler;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;

import static java.lang.Math.min;

public class TransmuterBlockEntity extends BlockEntity {
    public static final int MAX_TRANSMUTATION_TIME = 30;
    private long transmutationTime = -1;
    public final ItemStackHandler inputSlot = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            if (getLevel() != null && !getLevel().isClientSide) {
                attemptTransmutation();
                setChanged();
                getLevel().sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2);
            }
        }
    };
    public final ItemStackHandler outputSlots = new ItemStackHandler(4) {
        @Override
        protected void onContentsChanged(int slot) {
            if (getLevel() != null && !getLevel().isClientSide) {
                attemptTransmutation();
                setChanged();
                getLevel().sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2);
            }
        }
    };

    public TransmuterBlockEntity(BlockPos pos, BlockState state) {
        super(EntropicTransmutation.TRANSMUTER_BLOCK_ENTITY.get(), pos, state);
    }

    @ParametersAreNonnullByDefault
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("TransmutationTime", transmutationTime);
        tag.put("InputSlot", inputSlot.serializeNBT(registries));
        tag.put("OutputSlots", outputSlots.serializeNBT(registries));
    }

    @ParametersAreNonnullByDefault
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        transmutationTime = tag.getLong("TransmutationTime");
        if (tag.contains("InputSlot")) {
            inputSlot.deserializeNBT(registries, tag.getCompound("InputSlot"));
        }
        if (tag.contains("OutputSlots")) {
            outputSlots.deserializeNBT(registries, tag.getCompound("OutputSlots"));
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @MethodsReturnNonnullByDefault
    @ParametersAreNonnullByDefault
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putLong("TransmutationTime", transmutationTime);
        tag.put("InputSlot", this.inputSlot.serializeNBT(registries));
        tag.put("OutputSlots", this.outputSlots.serializeNBT(registries));
        return tag;
    }

    @ParametersAreNonnullByDefault
    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        transmutationTime = tag.getLong("TransmutationTime");
        if (tag.contains("InputSlot")) {
            this.inputSlot.deserializeNBT(registries, tag.getCompound("InputSlot"));
        }
        if (tag.contains("OutputSlots")) {
            this.outputSlots.deserializeNBT(registries, tag.getCompound("OutputSlots"));
        }
    }

    public boolean isInputEmpty() {
        return inputSlot.getStackInSlot(0).isEmpty();
    }

    public boolean isOutputEmpty() {
        return outputSlots.getStackInSlot(0).isEmpty()
                && outputSlots.getStackInSlot(1).isEmpty()
                && outputSlots.getStackInSlot(2).isEmpty()
                && outputSlots.getStackInSlot(3).isEmpty();
    }

    protected void attemptTransmutation() {
        if (level != null && !isInputEmpty() && isOutputEmpty()) {
            transmutationTime = level.getGameTime() + MAX_TRANSMUTATION_TIME;
            if (getBlockState().getBlock() instanceof TransmuterBlock transmuterBlock) {
                transmuterBlock.scheduleTransmutation(this.level, this.worldPosition);
            }
        }
    }

    public void transmute(RandomSource rng) {
        this.transmutationTime = -1;
        if (level != null && !isInputEmpty() && isOutputEmpty()) {
            double value = inputSlot.getStackInSlot(0).getCount() * IEMCProxy.INSTANCE.getValue(inputSlot.getStackInSlot(0));
            if (value > 0)
            {
                long startTime = System.nanoTime();
                inputSlot.setStackInSlot(0, ItemStack.EMPTY);
                outputSlots.setStackInSlot(0, EmcSampler.SampleItemStack(0, value / 4.0, rng));
                outputSlots.setStackInSlot(1, EmcSampler.SampleItemStack(0, value / 4.0, rng));
                outputSlots.setStackInSlot(2, EmcSampler.SampleItemStack(0, value / 4.0, rng));
                outputSlots.setStackInSlot(3, EmcSampler.SampleItemStack(0, value / 4.0, rng));
                long endTime = System.nanoTime();
                long durationNs = endTime - startTime;
                double durationMs = durationNs / 1_000.0;
                EntropicTransmutation.LOGGER.info("Time spent transmuting: {} μs", durationMs);
            }
            if (isOutputEmpty()) {
                level.playSound(null, getBlockPos(), SoundEvents.VAULT_REJECT_REWARDED_PLAYER, SoundSource.BLOCKS, 0.125f, rng.nextFloat() * 0.4f + 0.8f);
            } else {
                level.playSound(null, getBlockPos(), SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 0.5f, rng.nextFloat() * 0.2f + 0.9f);
            }
        }
    }
}