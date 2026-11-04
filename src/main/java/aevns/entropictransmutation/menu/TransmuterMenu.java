package aevns.entropictransmutation.menu;

import aevns.entropictransmutation.block.entity.TransmuterBlockEntity;
import aevns.entropictransmutation.registry.ModBlocks;
import aevns.entropictransmutation.registry.ModMenuTypes;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/** The transmuter's inventory as seen through a monitor: one input, the output samples, and the addon summary. */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class TransmuterMenu extends AbstractContainerMenu {
    // Item positions within the GUI; slot backgrounds sit 1 px up and to the left of these
    public static final int INPUT_X = 44;
    public static final int INPUT_Y = 35;
    public static final int OUTPUT_X = 98;
    public static final int OUTPUT_Y = 26;

    private static final int DATA_PROGRESS = 0;
    private static final int DATA_TYPE_SUPPRESSORS = 1;
    private static final int DATA_COUNT_SUPPRESSORS = 2;
    private static final int DATA_VOID_AMPLIFIERS = 3;
    private static final int DATA_SIZE = 4;

    private static final int OUTPUT_START = 1;
    private static final int INVENTORY_START = OUTPUT_START + TransmuterBlockEntity.OUTPUT_COUNT;
    private static final int INVENTORY_END = INVENTORY_START + 36;

    private final ContainerLevelAccess access;
    private final ContainerData data;

    /** Client side: wraps the block entity's synced copy, or empty stand-ins if it hasn't arrived yet. */
    public TransmuterMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()) instanceof TransmuterBlockEntity transmuter
                        ? transmuter : null,
                new SimpleContainerData(DATA_SIZE));
    }

    /** Server side. */
    public TransmuterMenu(int containerId, Inventory playerInventory, TransmuterBlockEntity transmuter) {
        this(containerId, playerInventory, transmuter, new ServerData(transmuter));
    }

    private TransmuterMenu(int containerId, Inventory playerInventory,
                           @Nullable TransmuterBlockEntity transmuter, ContainerData data) {
        super(ModMenuTypes.TRANSMUTER.get(), containerId);
        this.access = transmuter != null && transmuter.getLevel() != null
                ? ContainerLevelAccess.create(transmuter.getLevel(), transmuter.getBlockPos())
                : ContainerLevelAccess.NULL;
        this.data = data;

        IItemHandler input = transmuter != null ? transmuter.getInputHandler() : new ItemStackHandler(1);
        IItemHandler outputs = transmuter != null
                ? transmuter.getOutputHandler()
                : new ItemStackHandler(TransmuterBlockEntity.OUTPUT_COUNT);

        addSlot(new SlotItemHandler(input, 0, INPUT_X, INPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                if (transmuter == null) return super.mayPlace(stack);
                return !stack.isEmpty() && transmuter.canTopUpInput(stack);
            }

            // Merging into an occupied slot grows the stack in place, which the handler doesn't see on its own
            @Override
            public void setChanged() {
                if (transmuter != null) transmuter.getInputHandler().setStackInSlot(0, getItem());
            }
        });
        for (int i = 0; i < TransmuterBlockEntity.OUTPUT_COUNT; i++) {
            addSlot(new SlotItemHandler(outputs, i, OUTPUT_X + (i % 2) * 18, OUTPUT_Y + (i / 2) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        addDataSlots(data);
    }

    /** Reads live values from the block entity, scanning for attached addons at most once per tick. */
    private static final class ServerData implements ContainerData {
        private final TransmuterBlockEntity transmuter;
        private Object2IntMap<Block> addons = Object2IntMaps.emptyMap();
        private long addonsScanTime = -1;

        private ServerData(TransmuterBlockEntity transmuter) {
            this.transmuter = transmuter;
        }

        private int addonCount(Block addon) {
            long gameTime = transmuter.getLevel().getGameTime();
            if (gameTime != addonsScanTime) {
                addons = transmuter.getAttachedAddons();
                addonsScanTime = gameTime;
            }
            return addons.getInt(addon);
        }

        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> transmuter.getProgressTicks();
                case DATA_TYPE_SUPPRESSORS -> addonCount(ModBlocks.TYPE_SUPPRESSOR.get());
                case DATA_COUNT_SUPPRESSORS -> addonCount(ModBlocks.COUNT_SUPPRESSOR.get());
                case DATA_VOID_AMPLIFIERS -> addonCount(ModBlocks.VOID_AMPLIFIER.get());
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_SIZE;
        }
    }

    /** Ticks elapsed on the transmutation timer, from 0 to TransmuterBlockEntity.MAX_TRANSMUTATION_TIME. */
    public int getProgressTicks() {
        return data.get(DATA_PROGRESS);
    }

    public int getTypeSuppressors() {
        return data.get(DATA_TYPE_SUPPRESSORS);
    }

    public int getCountSuppressors() {
        return data.get(DATA_COUNT_SUPPRESSORS);
    }

    public int getVoidAmplifiers() {
        return data.get(DATA_VOID_AMPLIFIERS);
    }

    /** Index in this menu's slot list of the given output sample's slot. */
    public static int outputSlotIndex(int output) {
        return OUTPUT_START + output;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.TRANSMUTER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        // Work on a copy and write it back with set, so the block entity's handlers see the change
        ItemStack stack = slot.getItem().copy();
        ItemStack original = stack.copy();
        if (index < INVENTORY_START) {
            if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, OUTPUT_START, false)) {
            return ItemStack.EMPTY;
        }

        slot.set(stack);
        slot.onTake(player, stack);
        return original;
    }
}
