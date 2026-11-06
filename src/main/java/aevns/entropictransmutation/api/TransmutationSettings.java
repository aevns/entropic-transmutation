package aevns.entropictransmutation.api;

import aevns.entropictransmutation.block.entity.TransmuterBlockEntity;
import aevns.entropictransmutation.registry.ETAddons;
import aevns.entropictransmutation.samplers.maxent.MaxEntSampler;

import java.util.function.ToIntFunction;

/**
 * The settings of one transmutation, starting from a machine with no addons. Addon effects adjust them through methods
 * that keep them in range, so that addons can be combined in any amount.
 */
public final class TransmutationSettings {
    /** Index into MaxEntSampler.SUPPORTED_EXPECTED_COUNTS with no count reduction: the largest stacks. */
    public static final int DEFAULT_COUNT_INDEX = MaxEntSampler.SUPPORTED_EXPECTED_COUNTS.length - 1;
    private static final int MAX_VOID_MULTIPLIER = 1 << 30;

    private int sampleCount = TransmuterBlockEntity.OUTPUT_COUNT;
    private int countIndex = DEFAULT_COUNT_INDEX;
    private int voidMultiplier = 1;

    /** The settings given by every registered addon, with attached copies counted by attachedCount. */
    public static TransmutationSettings forAddons(ToIntFunction<TransmuterAddon> attachedCount) {
        TransmutationSettings settings = new TransmutationSettings();
        for (TransmuterAddon addon : ETAddons.REGISTRY) {
            addon.applyTo(settings, attachedCount.applyAsInt(addon));
        }
        return settings;
    }

    /** How many output slots are filled, each with an equal share of the input's value. */
    public int sampleCount() {
        return sampleCount;
    }

    /** Index into MaxEntSampler.SUPPORTED_EXPECTED_COUNTS of the expected size of output stacks. */
    public int countIndex() {
        return countIndex;
    }

    /** Sampling happens with chance 1 / multiplier, with the share scaled by multiplier. */
    public int voidMultiplier() {
        return voidMultiplier;
    }

    /** Removes samples, down to a single sample. */
    public void reduceSampleCount(int samples) {
        sampleCount = Math.max(1, sampleCount - samples);
    }

    /** Moves down the supported expected stack sizes, each 4 times smaller, as far as the smallest. */
    public void reduceExpectedCount(int steps) {
        countIndex = Math.max(0, countIndex - steps);
    }

    /** Doubles the void multiplier the given number of times. */
    public void doubleVoidMultiplier(int times) {
        voidMultiplier = (int) Math.min(MAX_VOID_MULTIPLIER, (long) voidMultiplier << Math.min(times, 30));
    }
}
