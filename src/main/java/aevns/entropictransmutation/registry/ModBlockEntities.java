package aevns.entropictransmutation.registry;

import aevns.entropictransmutation.EntropicTransmutation;
import aevns.entropictransmutation.block.entity.TransmuterBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EntropicTransmutation.MODID);

    @SuppressWarnings("ConstantConditions")
    public static final Supplier<BlockEntityType<TransmuterBlockEntity>> TRANSMUTER = BLOCK_ENTITY_TYPES.register(
            "transmuter",
            () -> BlockEntityType.Builder.of(TransmuterBlockEntity::new, ModBlocks.TRANSMUTER.get()).build(null)
    );

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
