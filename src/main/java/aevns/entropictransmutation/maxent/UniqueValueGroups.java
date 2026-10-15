package aevns.entropictransmutation.maxent;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * A sorted array of positive values collapsed into unique values with their multiplicities.
 * Group k occupies the contiguous entry range [groupStartIndex[k], groupStartIndex[k] + groupSize[k]),
 * so an entry index is recovered from a group with one uniform draw and no mapping array.
 */
final class UniqueValueGroups {
    final int uniqueCount;
    final double[] uniqueValues;
    final int[] groupStartIndex, groupSize;
    /** Mean over all entries (not over unique values), summed in entry order. */
    final double entryMean;

    UniqueValueGroups(double[] sortedValues) {
        int entryCount = sortedValues.length;
        for (int i = 0; i < entryCount; i++) {
            if (!(sortedValues[i] > 0)) throw new IllegalArgumentException("values must be > 0");
            if (i > 0 && sortedValues[i] < sortedValues[i - 1]) throw new IllegalArgumentException("values must be sorted");
        }
        int[] groupStartScratch = new int[entryCount + 1];
        int groups = 0;
        for (int i = 0; i < entryCount; i++)
            if (i == 0 || sortedValues[i] != sortedValues[i - 1]) groupStartScratch[groups++] = i;
        uniqueCount = groups;
        if (uniqueCount < 2) throw new IllegalArgumentException("need at least 2 distinct values");
        groupStartIndex = Arrays.copyOf(groupStartScratch, uniqueCount);
        groupSize = new int[uniqueCount];
        uniqueValues = new double[uniqueCount];
        for (int i = 0; i < uniqueCount; i++) {
            int groupEnd = (i + 1 < uniqueCount) ? groupStartIndex[i + 1] : entryCount;
            groupSize[i] = groupEnd - groupStartIndex[i];
            uniqueValues[i] = sortedValues[groupStartIndex[i]];
        }
        double valueSum = 0;
        for (double value : sortedValues) valueSum += value;
        entryMean = valueSum / entryCount;
    }

    /** Picks an entry index uniformly within the group (consumes a random long only if the group has duplicates). */
    int pickEntryIndex(int uniqueIndex, SplittableRandom rng) {
        int size = groupSize[uniqueIndex];
        int entryIndex = groupStartIndex[uniqueIndex];
        if (size > 1) entryIndex += (int) ((FixedPoint.highBits(rng.nextLong()) * size) >>> 32);
        return entryIndex;
    }

    long approximateMemoryBytes() {
        return 4L * (groupStartIndex.length + groupSize.length);
    }
}
