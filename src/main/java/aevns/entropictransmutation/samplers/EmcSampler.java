package aevns.entropictransmutation.samplers;

import aevns.entropictransmutation.mixins.EMCMappingHandlerMixin;
import aevns.entropictransmutation.samplers.maxent.MaxEntSampler;
import aevns.entropictransmutation.samplers.maxent.Params;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.*;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.event.EMCRemapEvent;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Comparator;

@EventBusSubscriber(modid = "entropictransmutation")
public final class EmcSampler {
    public static final Logger LOGGER = LogUtils.getLogger();

    private EmcSampler() {
        throw new UnsupportedOperationException("MaxEntropySamplers cannot be instantiated.");
    }

    static ItemInfo[] itemData;
    static double[] itemValues;
    static Params params;
    public static MaxEntSampler sampler;

    @SubscribeEvent
    static void onEmcRemap(EMCRemapEvent event) {
        // Bad mixin required to get the emc map, as it's not accessible(?) through ProjectE's API
        Object2LongMap<ItemInfo> emcMap = EMCMappingHandlerMixin.getMap();
        if (emcMap == null) return;

        // We keep using ItemInfo, since it's a reference type anyway
        int size = emcMap.size();
        Integer[] indices = new Integer[size];
        ItemInfo[] unsortedItemData = new ItemInfo[size];
        double[] unsortedItemValues = new double[size];

        ObjectIterator<Object2LongMap.Entry<ItemInfo>> iterator = Object2LongMaps.fastIterator(emcMap);
        int index = 0;
        while (iterator.hasNext()) {
            Object2LongMap.Entry<ItemInfo> entry = iterator.next();
            String modId = entry.getKey().getItem().unwrapKey()
                    .map(key -> key.location().getNamespace())
                    .orElse("minecraft");
            if (true) { //modId.equals("minecraft")) {
                indices[index] = index;
                unsortedItemData[index] = entry.getKey();
                unsortedItemValues[index] = entry.getLongValue();
                index++;
            }
        }
        final int newSize = index;
        Arrays.sort(indices, 0, newSize, Comparator.comparingDouble(i -> unsortedItemValues[i]));
        itemData = new ItemInfo[newSize];
        itemValues = new double[newSize];
        for (int i = 0; i < newSize; i++) {
            itemData[i] = unsortedItemData[indices[i]];
            itemValues[i] = unsortedItemValues[indices[i]];
        }
        LOGGER.info(Arrays.toString(itemValues));
        LOGGER.info("Creating max entropy sampler for {} items [EMC].", newSize);
        params = new Params();
        sampler = new MaxEntSampler(itemValues, itemData, params);
        LOGGER.info("EMC Sampler memory footprint is roughly {} MB.", sampler.approximateMemoryBytes() / (1 << 20));
    }

    public static ItemStack SampleItemStack(int countIndex, double meanTotalValue, RandomSource rng) {
        if (itemData == null || itemValues == null || params == null || sampler == null) {
            LOGGER.error("Cannot sample item stack: EMC data has not been remapped.");
            return ItemStack.EMPTY;
        }
        MaxEntSampler.Sample sample = sampler.sample(countIndex, meanTotalValue, rng);
        if (sample.labelIndex == -1 || sample.count == 0) return ItemStack.EMPTY;
        ItemStack stack = itemData[sample.labelIndex].createStack();
        stack.setCount(sample.count);
        return stack;
    }
}