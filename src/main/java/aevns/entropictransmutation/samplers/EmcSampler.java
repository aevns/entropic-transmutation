package aevns.entropictransmutation.samplers;

import aevns.entropictransmutation.EntropicTransmutation;
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

@EventBusSubscriber(modid = EntropicTransmutation.MODID)
public final class EmcSampler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static ItemInfo[] itemData;
    private static MaxEntSampler sampler;

    private EmcSampler() {
    }

    /** Whether EMC data has arrived, so that sampleItemStack can be called. */
    public static boolean isReady() {
        return sampler != null;
    }

    /** Whether an item may appear in transmutation outputs. Item blacklists would apply here. */
    private static boolean isEligible(ItemInfo item) {
        return true;
    }

    @SubscribeEvent
    static void onEmcRemap(EMCRemapEvent event) {
        // ProjectE's API doesn't expose the full EMC map, so it's read through a mixin
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
            if (isEligible(entry.getKey())) {
                indices[index] = index;
                unsortedItemData[index] = entry.getKey();
                unsortedItemValues[index] = entry.getLongValue();
                index++;
            }
        }
        final int newSize = index;
        if (newSize == 0) {
            LOGGER.warn("No items are eligible for transmutation; the EMC sampler is unavailable.");
            sampler = null;
            itemData = null;
            return;
        }

        Arrays.sort(indices, 0, newSize, Comparator.comparingDouble(i -> unsortedItemValues[i]));
        ItemInfo[] sortedItemData = new ItemInfo[newSize];
        double[] sortedItemValues = new double[newSize];
        for (int i = 0; i < newSize; i++) {
            sortedItemData[i] = unsortedItemData[indices[i]];
            sortedItemValues[i] = unsortedItemValues[indices[i]];
        }

        LOGGER.info("Creating max entropy sampler for {} items [EMC].", newSize);
        itemData = sortedItemData;
        sampler = new MaxEntSampler(sortedItemValues, new Params());
        LOGGER.info("EMC Sampler memory footprint is roughly {} MB.", sampler.approximateMemoryBytes() / (1 << 20));
    }

    /** The lowest mean stack value that sampleItemStack can honor; lower values are raised to this. Requires isReady(). */
    public static double minMeanTotalValue(int countIndex) {
        return sampler.minTargetS(countIndex);
    }

    /** Draws a random stack, or an empty one for the null outcome. Requires isReady(). */
    public static ItemStack sampleItemStack(int countIndex, double meanTotalValue, RandomSource rng) {
        MaxEntSampler.Sample sample = sampler.sample(countIndex, meanTotalValue, rng);
        if (sample.count == 0) return ItemStack.EMPTY;
        ItemStack stack = itemData[sample.labelIndex].createStack();
        stack.setCount(sample.count);
        return stack;
    }
}
