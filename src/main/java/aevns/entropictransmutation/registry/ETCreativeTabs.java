package aevns.entropictransmutation.registry;

import aevns.entropictransmutation.EntropicTransmutation;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ETCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EntropicTransmutation.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TRANSMUTER_TAB = CREATIVE_MODE_TABS.register(
            "transmuter_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.entropictransmutation"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> ETItems.TRANSMUTER.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ETItems.TRANSMUTER.get());
                        output.accept(ETItems.TRANSMUTER_MONITOR.get());
                        output.accept(ETItems.TYPE_SUPPRESSOR.get());
                        output.accept(ETItems.COUNT_SUPPRESSOR.get());
                        output.accept(ETItems.VOID_AMPLIFIER.get());
                    })
                    .build()
    );

    private ETCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
