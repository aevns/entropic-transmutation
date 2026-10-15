package aevns.entropictransmutation.maxent;

/**
 * Stored label distributions p(k | t) ~ W_k exp(-t x_k) for a fixed set of rows t_r, one per row.
 * Each row is a coarse CDF (one entry per block of unique values); drawing finds the block by binary search
 * and then picks within the block exactly by recomputing its few weights. This cuts memory by the block size
 * with no approximation beyond the 32-bit CDF quantization.
 */
final class LabelTable {
    private final int uniqueCount, blockSize, blocksPerRow;
    /** Interleaved [W_0, x_0, W_1, x_1, ...] for cache-friendly block scans. */
    private final double[] interleavedWeightAndScaledValue;
    /** rowCount x blocksPerRow, unsigned 32-bit fixed point xor Integer.MIN_VALUE so signed comparison works. */
    private final int[] blockCdf;
    private final double[] rowSignedT;
    /** Realized E[x] of each row under the quantized CDF. */
    private final double[] rowMeanScaledValue;

    static int blocksPerRow(int uniqueCount, int blockSize) {
        return (uniqueCount + blockSize - 1) / blockSize;
    }

    LabelTable(double[] scaledValues, double[] labelPriorWeights, double[] rowSignedT, int blockSize) {
        this.uniqueCount = scaledValues.length;
        this.blockSize = blockSize;
        this.blocksPerRow = blocksPerRow(uniqueCount, blockSize);
        this.rowSignedT = rowSignedT;
        interleavedWeightAndScaledValue = new double[2 * uniqueCount];
        for (int i = 0; i < uniqueCount; i++) {
            interleavedWeightAndScaledValue[2 * i] = labelPriorWeights[i];
            interleavedWeightAndScaledValue[2 * i + 1] = scaledValues[i];
        }
        blockCdf = new int[rowSignedT.length * blocksPerRow];
        buildBlockCdf();
        rowMeanScaledValue = new double[rowSignedT.length];
        for (int row = 0; row < rowSignedT.length; row++) rowMeanScaledValue[row] = computeRowMeanScaledValue(row);
    }

    int rowCount() { return rowSignedT.length; }

    double rowMeanScaledValue(int row) { return rowMeanScaledValue[row]; }

    long approximateMemoryBytes() {
        return 4L * blockCdf.length + 8L * interleavedWeightAndScaledValue.length;
    }

    private double referenceValue(double t, int blockStart, int blockEnd) {
        // Anchoring at the value that dominates for this sign of t keeps every exponent <= 0.
        return t >= 0
                ? interleavedWeightAndScaledValue[2 * blockStart + 1]
                : interleavedWeightAndScaledValue[2 * (blockEnd - 1) + 1];
    }

    private void buildBlockCdf() {
        double[] weights = new double[uniqueCount];
        for (int row = 0; row < rowSignedT.length; row++) {
            double t = rowSignedT[row];
            double reference = referenceValue(t, 0, uniqueCount);
            double totalWeight = 0;
            for (int k = 0; k < uniqueCount; k++) {
                weights[k] = interleavedWeightAndScaledValue[2 * k]
                        * Math.exp(-t * (interleavedWeightAndScaledValue[2 * k + 1] - reference));
                totalWeight += weights[k];
            }
            double cumulativeWeight = 0;
            long previousCdf = 0;
            for (int block = 0; block < blocksPerRow; block++) {
                int blockStart = block * blockSize, blockEnd = Math.min(blockStart + blockSize, uniqueCount);
                for (int k = blockStart; k < blockEnd; k++) cumulativeWeight += weights[k];
                long cdfFixedPoint = Math.round(cumulativeWeight / totalWeight * FixedPoint.SCALE);
                if (cdfFixedPoint > FixedPoint.MAX_UNSIGNED) cdfFixedPoint = FixedPoint.MAX_UNSIGNED;
                if (cdfFixedPoint < previousCdf) cdfFixedPoint = previousCdf;
                if (block == blocksPerRow - 1) cdfFixedPoint = FixedPoint.MAX_UNSIGNED;
                previousCdf = cdfFixedPoint;
                blockCdf[row * blocksPerRow + block] = ((int) cdfFixedPoint) ^ Integer.MIN_VALUE;
            }
        }
    }

    /**
     * Draws a unique-value index from the given row. The high 32 bits of the random long locate the block in
     * the coarse CDF; the low 32 bits pick within the block.
     */
    int sampleUniqueIndex(int row, long random) {
        int cdfKey = ((int) FixedPoint.highBits(random)) ^ Integer.MIN_VALUE;
        int rowOffset = row * blocksPerRow;
        int low = 0, high = blocksPerRow - 1;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (blockCdf[rowOffset + middle] > cdfKey) high = middle; else low = middle + 1;
        }
        int blockStart = low * blockSize, blockEnd = Math.min(blockStart + blockSize, uniqueCount);
        double t = rowSignedT[row];
        double reference = referenceValue(t, blockStart, blockEnd);
        double[] blockWeights = new double[blockSize];
        double blockTotal = 0;
        for (int k = blockStart; k < blockEnd; k++) {
            double weight = interleavedWeightAndScaledValue[2 * k]
                    * Math.exp(-t * (interleavedWeightAndScaledValue[2 * k + 1] - reference));
            blockWeights[k - blockStart] = weight;
            blockTotal += weight;
        }
        double target = FixedPoint.lowBits(random) * (1.0 / FixedPoint.SCALE) * blockTotal;
        int chosen = blockEnd - 1;
        double accumulated = 0;
        for (int k = blockStart; k < blockEnd; k++) {
            accumulated += blockWeights[k - blockStart];
            if (accumulated > target) { chosen = k; break; }
        }
        return chosen;
    }

    private double blockMeanScaledValue(double t, int blockStart, int blockEnd) {
        double reference = referenceValue(t, blockStart, blockEnd);
        double totalWeight = 0, weightedValueSum = 0;
        for (int k = blockStart; k < blockEnd; k++) {
            double weight = interleavedWeightAndScaledValue[2 * k]
                    * Math.exp(-t * (interleavedWeightAndScaledValue[2 * k + 1] - reference));
            totalWeight += weight;
            weightedValueSum += weight * interleavedWeightAndScaledValue[2 * k + 1];
        }
        return weightedValueSum / totalWeight;
    }

    /** Exact E[x | row] under the stored (quantized) block CDF. */
    private double computeRowMeanScaledValue(int row) {
        double t = rowSignedT[row], mean = 0;
        double previousCdf = 0;
        for (int block = 0; block < blocksPerRow; block++) {
            double currentCdf = (block == blocksPerRow - 1)
                    ? FixedPoint.SCALE
                    : Integer.toUnsignedLong(blockCdf[row * blocksPerRow + block] ^ Integer.MIN_VALUE);
            double blockMass = (currentCdf - previousCdf) / FixedPoint.SCALE;
            previousCdf = currentCdf;
            if (blockMass <= 0) continue;
            mean += blockMass * blockMeanScaledValue(t, block * blockSize,
                    Math.min(block * blockSize + blockSize, uniqueCount));
        }
        return mean;
    }
}
