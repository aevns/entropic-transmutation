package aevns.entropictransmutation.registry;

import aevns.entropictransmutation.EntropicTransmutation;
import aevns.entropictransmutation.block.TransmuterAddonBlock;
import aevns.entropictransmutation.block.TransmuterBlock;
import aevns.entropictransmutation.block.TransmuterMonitorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EntropicTransmutation.MODID);

    public static final DeferredBlock<TransmuterBlock> TRANSMUTER =
            BLOCKS.register("transmuter", () -> new TransmuterBlock(machineProperties()));
    public static final DeferredBlock<TransmuterMonitorBlock> TRANSMUTER_MONITOR =
            BLOCKS.register("transmuter_monitor", () -> new TransmuterMonitorBlock(machineProperties()));
    public static final DeferredBlock<TransmuterAddonBlock> TYPE_SUPPRESSOR = addon("type_suppressor");
    public static final DeferredBlock<TransmuterAddonBlock> COUNT_SUPPRESSOR = addon("count_suppressor");
    public static final DeferredBlock<TransmuterAddonBlock> VOID_AMPLIFIER = addon("void_amplifier");

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    private static BlockBehaviour.Properties machineProperties() {
        return BlockBehaviour.Properties.of()
                .noOcclusion()
                .lightLevel(state -> 4)
                .strength(2.0f)
                .sound(SoundType.VAULT);
    }

    private static DeferredBlock<TransmuterAddonBlock> addon(String name) {
        return BLOCKS.register(name, () -> new TransmuterAddonBlock(BlockBehaviour.Properties.of()
                .noCollission()
                .noOcclusion()
                .lightLevel(state -> 7)
                .strength(2.0f)
                .sound(SoundType.BAMBOO_WOOD_HANGING_SIGN)
        ));
    }
}
