package aevns.entropictransmutation.registry;

import aevns.entropictransmutation.EntropicTransmutation;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EntropicTransmutation.MODID);

    public static final DeferredItem<BlockItem> TRANSMUTER = ITEMS.registerSimpleBlockItem(ModBlocks.TRANSMUTER);
    public static final DeferredItem<BlockItem> TRANSMUTER_MONITOR = ITEMS.registerSimpleBlockItem(ModBlocks.TRANSMUTER_MONITOR);
    public static final DeferredItem<BlockItem> TYPE_SUPPRESSOR = ITEMS.registerSimpleBlockItem(ModBlocks.TYPE_SUPPRESSOR);
    public static final DeferredItem<BlockItem> COUNT_SUPPRESSOR = ITEMS.registerSimpleBlockItem(ModBlocks.COUNT_SUPPRESSOR);
    public static final DeferredItem<BlockItem> VOID_AMPLIFIER = ITEMS.registerSimpleBlockItem(ModBlocks.VOID_AMPLIFIER);

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
