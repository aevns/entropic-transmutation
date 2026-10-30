package aevns.entropictransmutation.samplers.maxent;

import net.minecraft.util.RandomSource;

import java.util.function.IntToDoubleFunction;

/**
 * For one expected count n: a set of stored target-s rows (ascending, in scaled units). Each row holds, for every
 * count c, an alias-method entry for drawing c and the pair of label rows to mix for drawing the label given c.
 * Between two stored rows, one of them is chosen at random with probability linear in s, which keeps both
 * E[N] = n and E[N V] = s exact for any s inside the stored range.
 *
 * Layout of packedRows: INTS_PER_COUNT_ENTRY ints per (targetRow, count), interleaved so one cache line serves
 * both the alias draw and the label-row lookup. Only this class knows that layout.
 */
final class CountTable {
    private static final int INTS_PER_COUNT_ENTRY = 4;
    private static final int ALIAS_THRESHOLD = 0;          // fixed-point probability of keeping the drawn column
    private static final int ALIAS_COUNT = 1;              // count to return otherwise
    private static final int LABEL_ROW = 2;                // lower label row for this count
    private static final int LABEL_ROW_MIX_THRESHOLD = 3;  // fixed-point probability of using LABEL_ROW + 1 instead

    private final double[] scaledTargets;
    private final int[] packedRows;

    /** Packs the entries of one target row (arrays are indexed by count, length CountRange.STATE_COUNT). */
    static int[] packRow(int[] aliasThresholds, int[] aliasCounts, int[] labelRows, int[] labelRowMixThresholds) {
        int[] entries = new int[CountRange.STATE_COUNT * INTS_PER_COUNT_ENTRY];
        for (int count = 0; count < CountRange.STATE_COUNT; count++) {
            int entryOffset = INTS_PER_COUNT_ENTRY * count;
            entries[entryOffset + ALIAS_THRESHOLD] = aliasThresholds[count];
            entries[entryOffset + ALIAS_COUNT] = aliasCounts[count];
            // The null state has no label, so its label fields are unused.
            entries[entryOffset + LABEL_ROW] = count == 0 ? 0 : labelRows[count];
            entries[entryOffset + LABEL_ROW_MIX_THRESHOLD] = count == 0 ? 0 : labelRowMixThresholds[count];
        }
        return entries;
    }

    /** rowsInTargetOrder[r] must have been produced by packRow for the row whose target is sortedScaledTargets[r]. */
    CountTable(double[] sortedScaledTargets, int[][] rowsInTargetOrder) {
        this.scaledTargets = sortedScaledTargets;
        int rowInts = CountRange.STATE_COUNT * INTS_PER_COUNT_ENTRY;
        packedRows = new int[sortedScaledTargets.length * rowInts];
        for (int row = 0; row < sortedScaledTargets.length; row++)
            System.arraycopy(rowsInTargetOrder[row], 0, packedRows, row * rowInts, rowInts);
    }

    double minScaledTarget() { return scaledTargets[0]; }

    double maxScaledTarget() { return scaledTargets[scaledTargets.length - 1]; }

    long approximateMemoryBytes() {
        return 4L * packedRows.length + 8L * scaledTargets.length;
    }

    /** Lower of the two stored rows bracketing scaledS; clamped to the first/last row outside the stored range. */
    private int lowerTargetRow(double scaledS) {
        int rowCount = scaledTargets.length;
        if (scaledS <= scaledTargets[0]) return 0;
        if (scaledS >= scaledTargets[rowCount - 1]) return rowCount - 1;
        int low = 0, high = rowCount - 1;
        while (high - low > 1) {
            int middle = (low + high) >>> 1;
            if (scaledTargets[middle] <= scaledS) low = middle; else high = middle;
        }
        return low;
    }

    /** Probability of using row lowerRow + 1 instead of lowerRow; zero when clamped. */
    private double upperRowWeight(double scaledS, int lowerRow) {
        if (lowerRow >= scaledTargets.length - 1 || scaledS <= scaledTargets[0]) return 0;
        return (scaledS - scaledTargets[lowerRow]) / (scaledTargets[lowerRow + 1] - scaledTargets[lowerRow]);
    }

    /**
     * Draws a count and the label row to use for it, packed as (count << 32) | labelRow. Count 0 (the null state)
     * returns 0 and has no label row. Consumes two random longs.
     */
    long drawCountAndLabelRow(double scaledS, RandomSource rng) {
        int lowerRow = lowerTargetRow(scaledS);
        double upperRowMixProbability = upperRowWeight(scaledS, lowerRow);
        // First long: high half chooses between the two target rows; low half decides the alias keep/redirect.
        long rowMixAndAliasRandom = rng.nextLong();
        int targetRow = lowerRow;
        if (FixedPoint.highBits(rowMixAndAliasRandom) < (long) (upperRowMixProbability * FixedPoint.SCALE)) targetRow++;
        // Second long: high half picks the alias column; low half decides the label-row mix.
        long columnAndLabelRowRandom = rng.nextLong();
        int column = (int) ((FixedPoint.highBits(columnAndLabelRowRandom) * CountRange.STATE_COUNT) >>> 32);
        int columnEntry = (targetRow * CountRange.STATE_COUNT + column) * INTS_PER_COUNT_ENTRY;
        int count = FixedPoint.isBelow(FixedPoint.lowBits(rowMixAndAliasRandom), packedRows[columnEntry + ALIAS_THRESHOLD])
                ? column
                : packedRows[columnEntry + ALIAS_COUNT];
        if (count == 0) return 0L;
        int countEntry = (targetRow * CountRange.STATE_COUNT + count) * INTS_PER_COUNT_ENTRY;
        int labelRow = packedRows[countEntry + LABEL_ROW]
                + (FixedPoint.isBelow(FixedPoint.lowBits(columnAndLabelRowRandom),
                        packedRows[countEntry + LABEL_ROW_MIX_THRESHOLD]) ? 1 : 0);
        return ((long) count << 32) | labelRow;
    }

    /**
     * Exact {E[N], E[N x]} (scaled units) realized by drawCountAndLabelRow at scaledS, computed from the stored
     * tables rather than by sampling.
     */
    double[] realizedMoments(double scaledS, IntToDoubleFunction rowMeanScaledValue) {
        int lowerRow = lowerTargetRow(scaledS);
        double upperWeight = upperRowWeight(scaledS, lowerRow);
        double expectedCount = 0, expectedCountTimesValue = 0;
        for (int rowOffsetFromLower = 0; rowOffsetFromLower < 2; rowOffsetFromLower++) {
            int targetRow = lowerRow + rowOffsetFromLower;
            if (targetRow >= scaledTargets.length) continue;
            double rowWeight = rowOffsetFromLower == 0 ? 1 - upperWeight : upperWeight;
            if (rowWeight == 0) continue;
            double[] countProbabilities = new double[CountRange.STATE_COUNT];
            for (int column = 0; column < CountRange.STATE_COUNT; column++) {
                int entryOffset = (targetRow * CountRange.STATE_COUNT + column) * INTS_PER_COUNT_ENTRY;
                AliasMethod.addColumnMass(countProbabilities, column,
                        packedRows[entryOffset + ALIAS_THRESHOLD], packedRows[entryOffset + ALIAS_COUNT]);
            }
            for (int count = 1; count < CountRange.STATE_COUNT; count++) {
                int entryOffset = (targetRow * CountRange.STATE_COUNT + count) * INTS_PER_COUNT_ENTRY;
                int labelRow = packedRows[entryOffset + LABEL_ROW];
                double upperLabelRowMix = FixedPoint.toProbability(packedRows[entryOffset + LABEL_ROW_MIX_THRESHOLD]);
                double lowerRowMean = rowMeanScaledValue.applyAsDouble(labelRow);
                double meanScaledValue = lowerRowMean;
                if (upperLabelRowMix > 0)
                    meanScaledValue = (1 - upperLabelRowMix) * lowerRowMean
                            + upperLabelRowMix * rowMeanScaledValue.applyAsDouble(labelRow + 1);
                expectedCount += rowWeight * countProbabilities[count] * count;
                expectedCountTimesValue += rowWeight * countProbabilities[count] * count * meanScaledValue;
            }
        }
        return new double[] {expectedCount, expectedCountTimesValue};
    }
}
