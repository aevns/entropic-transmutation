package aevns.entropictransmutation;

import aevns.entropictransmutation.client.TransmuterScreen;
import aevns.entropictransmutation.registry.ETMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@EventBusSubscriber(modid = EntropicTransmutation.MODID, value = Dist.CLIENT)
public class EntropicTransmutationClient {
    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ETMenuTypes.TRANSMUTER.get(), TransmuterScreen::new);
    }
}
