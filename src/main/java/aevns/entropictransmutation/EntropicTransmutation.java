package aevns.entropictransmutation;

import aevns.entropictransmutation.config.ETConfig;
import aevns.entropictransmutation.registry.ETAddons;
import aevns.entropictransmutation.registry.ETBlockEntities;
import aevns.entropictransmutation.registry.ETBlocks;
import aevns.entropictransmutation.registry.ETCreativeTabs;
import aevns.entropictransmutation.registry.ETItems;
import aevns.entropictransmutation.registry.ETMenuTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(EntropicTransmutation.MODID)
public class EntropicTransmutation {
    public static final String MODID = "entropictransmutation";

    public EntropicTransmutation(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, ETConfig.SPEC);
        ETBlocks.register(modEventBus);
        ETItems.register(modEventBus);
        ETAddons.register(modEventBus);
        ETBlockEntities.register(modEventBus);
        ETMenuTypes.register(modEventBus);
        ETCreativeTabs.register(modEventBus);
    }
}
