package aevns.entropictransmutation.samplers;

import aevns.entropictransmutation.EntropicTransmutation;
import aevns.entropictransmutation.api.TransmutationEligibilityEvent;
import aevns.entropictransmutation.config.ETConfig;
import aevns.entropictransmutation.mixins.EMCMappingHandlerMixin;
import aevns.entropictransmutation.registry.ETAttachments;

import com.mojang.logging.LogUtils;
import io.github.aevns.fastmaxent.JointModel;
import it.unimi.dsi.fastutil.objects.*;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.event.EMCRemapEvent;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.stream.IntStream;

@EventBusSubscriber(modid = EntropicTransmutation.MODID)
public final class EmcSampler {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The supported expected stack sizes, indexed by count index: each 4 times the one before. */
    public static final double[] SUPPORTED_EXPECTED_COUNTS = {0.25, 1, 4, 16};
    /** The stack sizes an output can have, 1 to 64; an empty slot is the sampler's null outcome. */
    private static final double[] COUNTS = IntStream.rangeClosed(1, 64).asDoubleStream().toArray();

    /** The eligible items, sorted by value, and the model built over their values; null until EMC data arrives. */
    @Nullable
    private static ItemInfo[] items;
    @Nullable
    private static JointModel model;

    private EmcSampler() {
    }

    /** Whether EMC data has arrived, so that level samplers can draw. */
    public static boolean isReady() {
        return model != null;
    }

    /** Whether an item may appear in transmutation outputs, by the config's filter and then any event listeners. */
    private static boolean isEligible(ItemInfo item, Predicate<ItemInfo> configFilter) {
        return NeoForge.EVENT_BUS.post(new TransmutationEligibilityEvent(item, configFilter.test(item))).isEligible();
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

        Predicate<ItemInfo> configFilter = ETConfig.outputFilter();
        ObjectIterator<Object2LongMap.Entry<ItemInfo>> iterator = Object2LongMaps.fastIterator(emcMap);
        int index = 0;
        while (iterator.hasNext()) {
            Object2LongMap.Entry<ItemInfo> entry = iterator.next();
            if (isEligible(entry.getKey(), configFilter)) {
                indices[index] = index;
                unsortedItemData[index] = entry.getKey();
                unsortedItemValues[index] = entry.getLongValue();
                index++;
            }
        }
        final int newSize = index;
        if (newSize == 0) {
            LOGGER.warn("No items are eligible for transmutation; the EMC sampler is unavailable.");
            install(null, null);
            return;
        }

        Arrays.sort(indices, 0, newSize, Comparator.comparingDouble(i -> unsortedItemValues[i]));
        ItemInfo[] sortedItemData = new ItemInfo[newSize];
        double[] sortedItemValues = new double[newSize];
        for (int i = 0; i < newSize; i++) {
            sortedItemData[i] = unsortedItemData[indices[i]];
            sortedItemValues[i] = unsortedItemValues[indices[i]];
        }

        if (sortedItemValues[0] == sortedItemValues[newSize - 1]) {
            LOGGER.warn("All items eligible for transmutation have the same value; the EMC sampler is unavailable.");
            install(null, null);
            return;
        }

        LOGGER.info("Creating max entropy sampler for {} items [EMC].", newSize);
        JointModel newModel = new JointModel(sortedItemValues, ETConfig.valuePrior().weights(sortedItemValues), COUNTS,
                countWeights(), ETConfig.nullStateWeight(), SUPPORTED_EXPECTED_COUNTS, ETConfig.tableParams());
        install(sortedItemData, newModel);
        LOGGER.info("EMC Sampler memory footprint is roughly {} MB.", newModel.approximateMemoryBytes() / (1 << 20));
    }

    /**
     * Replaces the items and model, and moves every level sampler that already exists onto them. Levels without one
     * get theirs from the new model when they first need it.
     */
    private static void install(@Nullable ItemInfo[] newItems, @Nullable JointModel newModel) {
        items = newItems;
        model = newModel;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.hasData(ETAttachments.LEVEL_SAMPLER)) level.getData(ETAttachments.LEVEL_SAMPLER).reset();
        }
    }

    /**
     * Drops the items and model with the server that built them. In single player the game keeps running, and the next
     * world's server only rebuilds once a player joins, so machines must wait until then rather than use these.
     */
    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        items = null;
        model = null;
    }

    /** The count prior, scaled to a mean of 1, so that the null state's weight is relative to an average stack size. */
    private static double[] countWeights() {
        double[] weights = ETConfig.countPrior().weights(COUNTS);
        for (int i = 0; i < weights.length; i++) weights[i] *= weights.length;
        return weights;
    }

    /** The lowest mean stack value that a draw can honor; lower values are raised to this. Requires isReady(). */
    public static double minMeanStackValue(int countIndex) {
        return model.minTargetS(countIndex);
    }

    /** The level's sampler. The same one is returned for as long as the level exists. */
    public static LevelSampler forLevel(ServerLevel level) {
        return level.getData(ETAttachments.LEVEL_SAMPLER);
    }

    /**
     * Draws item stacks with one level's random source. Kept on the level as a data attachment, and moved onto the
     * new model by each rebuild. Server thread only, as samplers aren't thread-safe.
     */
    public static final class LevelSampler {
        private final RandomSource random;
        @Nullable
        private ItemInfo[] samplerItems;
        @Nullable
        private JointModel.Sampler sampler;

        public LevelSampler(RandomSource random) {
            this.random = random;
            reset();
        }

        private void reset() {
            samplerItems = items;
            sampler = model != null ? model.newSampler(random::nextLong) : null;
        }

        /** Draws a random stack, or an empty one for the null outcome. Requires isReady(). */
        public ItemStack sampleItemStack(int countIndex, double meanStackValue) {
            JointModel.Sample sample = sampler.sample(countIndex, meanStackValue);
            if (sample.quantityIndex() < 0) return ItemStack.EMPTY;
            ItemStack stack = samplerItems[sample.categoryIndex()].createStack();
            stack.setCount((int) COUNTS[sample.quantityIndex()]);
            return stack;
        }
    }
}
