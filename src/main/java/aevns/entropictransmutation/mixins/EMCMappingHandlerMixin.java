package aevns.entropictransmutation.mixins;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import moze_intel.projecte.api.ItemInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "moze_intel.projecte.emc.EMCMappingHandler", remap = false)
public interface EMCMappingHandlerMixin {
    @Accessor("emc")
    static Object2LongMap<ItemInfo> getMap() {
        throw new IllegalStateException("EntropicTransmutation failed to access emc mapping.");
    }
}