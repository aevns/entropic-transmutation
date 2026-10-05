package aevns.entropictransmutation.data;

import aevns.entropictransmutation.mixins.EMCMappingHandlerMixin;
import it.unimi.dsi.fastutil.objects.*;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.event.EMCRemapEvent;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "entropictransmutation")
public class EmcSampler extends AbstractMaxEntropySampler {
    private EmcSampler() {
        throw new UnsupportedOperationException("MaxEntropySamplers cannot be instantiated.");
    }

    static ItemInfo[] itemData;
    static long[] itemEmc;

    @SubscribeEvent
    static void onEmcRemap(EMCRemapEvent event) {
        // Bad mixin required to get the emc map, as it's not accessible(?) through ProjectE's API
        Object2LongMap<ItemInfo> emcMap = EMCMappingHandlerMixin.getMap();
        if (emcMap == null) return;

        // We keep using ItemInfo, since it's a reference type anyway
        int size = emcMap.size();
        itemData = new ItemInfo[size];
        itemEmc = new long[size];

        ObjectIterator<Object2LongMap.Entry<ItemInfo>> iterator = Object2LongMaps.fastIterator(emcMap);
        int index = 0;
        while (iterator.hasNext()) {
            Object2LongMap.Entry<ItemInfo> entry = iterator.next();
            itemData[index] = entry.getKey();
            itemEmc[index] = entry.getLongValue();

            index++;
        }
    }
}
