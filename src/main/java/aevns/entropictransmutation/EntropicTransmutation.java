package aevns.entropictransmutation;

import aevns.entropictransmutation.registry.ModBlockEntities;
import aevns.entropictransmutation.registry.ModBlocks;
import aevns.entropictransmutation.registry.ModCreativeTabs;
import aevns.entropictransmutation.registry.ModItems;
import aevns.entropictransmutation.registry.ModMenuTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(EntropicTransmutation.MODID)
public class EntropicTransmutation {
    public static final String MODID = "entropictransmutation";

    public EntropicTransmutation(IEventBus modEventBus) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
    }
}
