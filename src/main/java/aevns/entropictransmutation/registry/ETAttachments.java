package aevns.entropictransmutation.registry;

import aevns.entropictransmutation.EntropicTransmutation;
import aevns.entropictransmutation.samplers.EmcSampler;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ETAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, EntropicTransmutation.MODID);

    /**
     * Each level's item stack sampler, made on first use with the level's random source. Not saved: it holds no state
     * worth keeping, and it's discarded along with the level.
     */
    public static final Supplier<AttachmentType<EmcSampler.LevelSampler>> LEVEL_SAMPLER = ATTACHMENT_TYPES.register(
            "level_sampler",
            () -> AttachmentType.builder((IAttachmentHolder holder) -> new EmcSampler.LevelSampler(((Level) holder).random))
                    .build());

    private ETAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
