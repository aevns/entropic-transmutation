package aevns.entropictransmutation.registry;

import aevns.entropictransmutation.EntropicTransmutation;
import aevns.entropictransmutation.api.TransmutationSettings;
import aevns.entropictransmutation.api.TransmuterAddon;
import aevns.entropictransmutation.block.entity.TransmuterBlockEntity;
import com.google.common.base.Suppliers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class ETAddons {
    private static final DeferredRegister<TransmuterAddon> ADDONS =
            DeferredRegister.create(TransmuterAddon.REGISTRY_KEY, EntropicTransmutation.MODID);

    /** Synced, so that registry ids identify addons between the server's menu and the client's screen. */
    public static final Registry<TransmuterAddon> REGISTRY = ADDONS.makeRegistry(builder -> builder.sync(true));

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            EntropicTransmutation.MODID, "textures/gui/container/transmuter.png");
    private static final int SYMBOL_V = 32;
    /** The most addons that can attach: 3 faces each on the transmuter and the monitor. */
    private static final int MAX_ATTACHED = 6;

    public static final DeferredHolder<TransmuterAddon, TransmuterAddon> TYPE_SUPPRESSOR = ADDONS.register(
            "type_suppressor",
            () -> new TransmuterAddon(ETBlocks.TYPE_SUPPRESSOR, TransmuterBlockEntity.OUTPUT_COUNT - 1,
                    (count, settings) -> settings.reduceSampleCount(count),
                    new TransmuterAddon.Appearance(TEXTURE, 176, SYMBOL_V, 0xB07CF0, 0x5A2C96, ChatFormatting.LIGHT_PURPLE)));
    public static final DeferredHolder<TransmuterAddon, TransmuterAddon> COUNT_SUPPRESSOR = ADDONS.register(
            "count_suppressor",
            () -> new TransmuterAddon(ETBlocks.COUNT_SUPPRESSOR, TransmutationSettings.DEFAULT_COUNT_INDEX,
                    (count, settings) -> settings.reduceExpectedCount(count),
                    new TransmuterAddon.Appearance(TEXTURE, 184, SYMBOL_V, 0x2FCAD8, 0x0B6670, ChatFormatting.AQUA)));
    public static final DeferredHolder<TransmuterAddon, TransmuterAddon> VOID_AMPLIFIER = ADDONS.register(
            "void_amplifier",
            () -> new TransmuterAddon(ETBlocks.VOID_AMPLIFIER, MAX_ATTACHED,
                    (count, settings) -> settings.doubleVoidMultiplier(count),
                    new TransmuterAddon.Appearance(TEXTURE, 192, SYMBOL_V, 0xFF962E, 0x9C4300, ChatFormatting.GOLD)));

    private static final Supplier<Map<Block, TransmuterAddon>> BY_BLOCK = Suppliers.memoize(() -> {
        Map<Block, TransmuterAddon> byBlock = new IdentityHashMap<>();
        for (TransmuterAddon addon : REGISTRY) {
            byBlock.put(addon.block().get(), addon);
        }
        return byBlock;
    });

    private ETAddons() {
    }

    public static void register(IEventBus modEventBus) {
        ADDONS.register(modEventBus);
    }

    /** The addon a block provides, or null if it isn't registered as one. */
    @Nullable
    public static TransmuterAddon forBlock(Block block) {
        return BY_BLOCK.get().get(block);
    }
}
