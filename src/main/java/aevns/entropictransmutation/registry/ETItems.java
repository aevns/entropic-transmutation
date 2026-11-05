package aevns.entropictransmutation.registry;

import aevns.entropictransmutation.EntropicTransmutation;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ETItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EntropicTransmutation.MODID);

    public static final DeferredItem<BlockItem> TRANSMUTER = ITEMS.registerSimpleBlockItem(ETBlocks.TRANSMUTER);
    public static final DeferredItem<BlockItem> TRANSMUTER_MONITOR = ITEMS.registerSimpleBlockItem(ETBlocks.TRANSMUTER_MONITOR);
    public static final DeferredItem<BlockItem> TYPE_SUPPRESSOR = ITEMS.registerSimpleBlockItem(ETBlocks.TYPE_SUPPRESSOR);
    public static final DeferredItem<BlockItem> COUNT_SUPPRESSOR = ITEMS.registerSimpleBlockItem(ETBlocks.COUNT_SUPPRESSOR);
    public static final DeferredItem<BlockItem> VOID_AMPLIFIER = ITEMS.registerSimpleBlockItem(ETBlocks.VOID_AMPLIFIER);

    private ETItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
