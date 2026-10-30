package aevns.entropictransmutation.samplers.maxent;

import net.minecraft.util.RandomSource;

/**
 * Samples (labelIndex, count) from the maximum-entropy distribution (relative to a prior) with
 * E[N] = n (n in {1/4, 1, 4, 16}) and E[N*V] = s, count in 0..64. Count 0 is a single null state
 * (labelIndex = -1).
 *
 * Model (x = v / valueScale):
 *   p(null) = nullStatePrior / Z,   p(k, c) = W_k m(c) exp(-lambda c - beta c x_k) / Z   (c >= 1)
 * Sampling order: count c first, then label from p(k | t = beta c), a one-parameter family shared by all n.
 *
 * This class only wires the pieces together:
 *   UniqueValueGroups   sorted entries -> unique values and multiplicities, and back to an entry index
 *   LabelPriorWeights   prior mass per unique value
 *   LabelFamily         the label distributions p(k | t), and the LabelTable of stored rows that realizes them
 *   CountTableBuilder   per-n tables of (count, label-row) draws indexed by target s (see CountTable)
 */
public final class MaxEntSampler {
    public static final int MAX_COUNT = CountRange.MAX_COUNT;
    public static final double[] SUPPORTED_EXPECTED_COUNTS = {0.25, 1, 4, 16};

    /** Result of a draw. labelIndex is -1 when count == 0. */
    public static final class Sample {
        public final int labelIndex, count;
        Sample(int labelIndex, int count) { this.labelIndex = labelIndex; this.count = count; }
        @Override public String toString() { return "(" + labelIndex + ", " + count + ")"; }
    }

    private final Object[] labels;
    private final UniqueValueGroups groups;
    private final double valueScale, inverseValueScale;
    private final LabelFamily labelFamily;
    private final LabelTable labelTable;
    private final CountTable[] countTables = new CountTable[SUPPORTED_EXPECTED_COUNTS.length];

    public MaxEntSampler(double[] values, Object[] labels, Params params) {
        if (values.length != labels.length || values.length == 0) throw new IllegalArgumentException("size mismatch");
        this.labels = labels;
        groups = new UniqueValueGroups(values);
        valueScale = groups.entryMean;
        inverseValueScale = 1.0 / valueScale;
        double[] scaledValues = new double[groups.uniqueCount];
        for (int i = 0; i < groups.uniqueCount; i++) scaledValues[i] = groups.uniqueValues[i] * inverseValueScale;

        double[] labelPriorWeights = LabelPriorWeights.compute(groups.uniqueValues, groups.groupSize, params);
        labelFamily = new LabelFamily(scaledValues, labelPriorWeights, params);
        labelTable = labelFamily.table();
        double[] logCountPrior = CountPrior.logWeights(params);
        for (int i = 0; i < countTables.length; i++)
            countTables[i] = CountTableBuilder.build(SUPPORTED_EXPECTED_COUNTS[i], labelFamily, logCountPrior, params);
    }

    // ---------------------------------------------------------------- sampling

    /**
     * Draws a sample at target s (raw units; clamped to the supported range for the given n). Packed result:
     * count in the high 32 bits, labelIndex + 1 in the low 32 bits (so count 0 gives labelIndex -1).
     */
    public long sampleBits(int expectedCountIndex, double s, RandomSource rng) {
        long countAndLabelRow = countTables[expectedCountIndex].drawCountAndLabelRow(s * inverseValueScale, rng);
        int count = (int) (countAndLabelRow >>> 32);
        if (count == 0) return 0L;
        int uniqueIndex = labelTable.sampleUniqueIndex((int) countAndLabelRow, rng.nextLong());
        int labelIndex = groups.pickEntryIndex(uniqueIndex, rng);
        return ((long) count << 32) | (labelIndex + 1);
    }

    public static int unpackCount(long bits) { return (int) (bits >>> 32); }
    public static int unpackLabelIndex(long bits) { return (int) bits - 1; }

    public Sample sample(int expectedCountIndex, double s, RandomSource rng) {
        long bits = sampleBits(expectedCountIndex, s, rng);
        return new Sample(unpackLabelIndex(bits), unpackCount(bits));
    }

    public String label(int labelIndex) { return labels[labelIndex].toString(); }

    // ---------------------------------------------------------------- introspection / verification

    /** Lower end of the clamp interval for s (raw units) for the given expected-count index. */
    public double minTargetS(int expectedCountIndex) {
        return countTables[expectedCountIndex].minScaledTarget() * valueScale;
    }

    /** Upper end of the clamp interval for s (raw units) for the given expected-count index. */
    public double maxTargetS(int expectedCountIndex) {
        return countTables[expectedCountIndex].maxScaledTarget() * valueScale;
    }

    public int uniqueValueCount() { return groups.uniqueCount; }

    public long approximateMemoryBytes() {
        long bytes = labelTable.approximateMemoryBytes() + groups.approximateMemoryBytes();
        for (CountTable table : countTables) bytes += table.approximateMemoryBytes();
        return bytes;
    }

    /** Exact {E[N], E[N V]} (raw V units) realized by the sampler at target s, computed from the stored tables. */
    public double[] exactRealizedMoments(int expectedCountIndex, double s) {
        double[] scaledMoments = countTables[expectedCountIndex]
                .realizedMoments(s * inverseValueScale, labelTable::rowMeanScaledValue);
        return new double[] {scaledMoments[0], scaledMoments[1] * valueScale};
    }
}
