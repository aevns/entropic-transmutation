package aevns.entropictransmutation.registry;

import aevns.entropictransmutation.EntropicTransmutation;
import aevns.entropictransmutation.block.entity.TransmuterBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

@EventBusSubscriber(modid = EntropicTransmutation.MODID)
public final class ModCapabilities {
    private ModCapabilities() {
    }

    @SubscribeEvent
    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.TRANSMUTER.get(),
                (be, side) -> be.getAutomationHandler()
        );
        // The monitor has no inventory of its own; automation is handled by the transmuter below it
        event.registerBlock(
                Capabilities.ItemHandler.BLOCK,
                (level, pos, state, be, side) ->
                        level.getBlockEntity(pos.below()) instanceof TransmuterBlockEntity transmuter
                                ? transmuter.getAutomationHandler()
                                : null,
                ModBlocks.TRANSMUTER_MONITOR.get()
        );
    }
}
