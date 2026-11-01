package aevns.entropictransmutation.samplers.maxent;

/**
 * The continuous family p(k | t) ~ W_k exp(-t x_k) over both signs of t, together with the discrete set of
 * stored rows (a {@link LabelTable}) that approximates it. Answers two questions for the count-side code:
 * "what are the log-partition, mean and variance at this t?" and "which two stored rows bracket this t, and
 * with what mixing probability reproduces the exact mean at t?".
 */
final class LabelFamily {
    /** Everything the count side needs to know about the label family at one t. */
    static final class Point {
        /** ln sum_k W_k exp(-t (x_k - x_ref)) in the sign-appropriate gap form (see LabelTiltBranch). */
        double logPartition;
        /** Var[x | t]. */
        double variance;
        /** E[x | t] as realized by mixing the two bracketing rows with mixThreshold. */
        double meanScaledValue;
        /** Lower bracketing stored row (global row index). The upper row is lowerLabelRow + 1. */
        int lowerLabelRow;
        /** Fixed-point probability of using the upper row instead of the lower one. */
        int mixThreshold;
        final double[] interpolationScratch = new double[3];
    }

    private final LabelTiltBranch positiveBranch, negativeBranch;
    /** Rows [0, negativeRowOffset) belong to the positive-t branch, the rest to the negative-t branch. */
    private final int negativeRowOffset;
    private final LabelTable table;
    private final double scaledValueSpan;

    LabelFamily(double[] scaledValues, double[] labelPriorWeights, Params params) {
        scaledValueSpan = scaledValues[scaledValues.length - 1] - scaledValues[0];
        positiveBranch = new LabelTiltBranch(+1, scaledValues, labelPriorWeights);
        negativeBranch = new LabelTiltBranch(-1, scaledValues, labelPriorWeights);
        for (LabelTiltBranch branch : new LabelTiltBranch[] {positiveBranch, negativeBranch}) {
            branch.findSaturationTau(params.saturationTolerance);
            branch.buildAdaptiveGrid();
        }
        long bytesPerLabelRow = 4L * LabelTable.blocksPerRow(scaledValues.length, params.labelBlockSize);
        int totalLabelRows = (int) Math.max(32, Math.min(2000, params.labelTableBudgetBytes / bytesPerLabelRow));
        int labelRowsPerBranch = Math.max(8, totalLabelRows / 2);
        positiveBranch.selectLabelRowNodes(labelRowsPerBranch);
        negativeBranch.selectLabelRowNodes(labelRowsPerBranch);
        negativeRowOffset = positiveBranch.labelRowGridIndices.length;
        double[] rowSignedT = new double[negativeRowOffset + negativeBranch.labelRowGridIndices.length];
        for (int row = 0; row < rowSignedT.length; row++) {
            LabelTiltBranch branch = row < negativeRowOffset ? positiveBranch : negativeBranch;
            int gridIndex = branch.labelRowGridIndices[row < negativeRowOffset ? row : row - negativeRowOffset];
            rowSignedT[row] = branch.signOfT * branch.gridTau[gridIndex];
        }
        table = new LabelTable(scaledValues, labelPriorWeights, rowSignedT, params.labelBlockSize);
    }

    LabelTable table() { return table; }

    /** Largest beta whose c = 1 label distribution is within the saturation tolerance of its extreme group. */
    double maxBeta() { return positiveBranch.saturationTau; }

    /** Most negative such beta. */
    double minBeta() { return -negativeBranch.saturationTau; }

    /**
     * Smallest |beta| at which any label weight changes by more than about 0.1% at any count, i.e.
     * 1e-3 / (MAX_COUNT * value span). Below this the label distributions are indistinguishable from beta = 0,
     * so beta candidates need not go lower. The beta range [this, saturation] can span many decades.
     */
    double smallestRelevantBetaMagnitude() { return 1e-3 / (CountRange.MAX_COUNT * scaledValueSpan); }

    /** Evaluates the family at signed t (|t| beyond the saturation point is treated as saturated). */
    void evaluateAtT(double signedT, Point out) {
        boolean positive = signedT >= 0;
        LabelTiltBranch branch = positive ? positiveBranch : negativeBranch;
        int rowOffset = positive ? 0 : negativeRowOffset;
        int[] labelRowGridIndices = branch.labelRowGridIndices;
        int lastRowPosition = labelRowGridIndices.length - 1;
        double tau = Math.min(Math.abs(signedT), branch.saturationTau);
        branch.interpolate(tau, out.interpolationScratch);
        out.logPartition = out.interpolationScratch[0];
        out.variance = out.interpolationScratch[2];

        int lowerLabelRow;
        double effectiveMeanGap;
        int mixThreshold = 0;
        if (tau >= branch.gridTau[labelRowGridIndices[lastRowPosition]]) {
            lowerLabelRow = lastRowPosition;
            effectiveMeanGap = branch.signOfT
                    * (table.rowMeanScaledValue(rowOffset + lastRowPosition) - branch.referenceValue);
        } else {
            int low = 0, high = lastRowPosition;
            while (high - low > 1) {
                int middle = (low + high) >>> 1;
                if (branch.gridTau[labelRowGridIndices[middle]] <= tau) low = middle; else high = middle;
            }
            lowerLabelRow = low;
            // Mix the two stored rows so the realized mean gap equals the exact interpolated mean gap at tau.
            double meanGapAtLowerRow = branch.signOfT
                    * (table.rowMeanScaledValue(rowOffset + lowerLabelRow) - branch.referenceValue);
            double meanGapAtUpperRow = branch.signOfT
                    * (table.rowMeanScaledValue(rowOffset + lowerLabelRow + 1) - branch.referenceValue);
            double mixWeight = meanGapAtLowerRow > meanGapAtUpperRow
                    ? (meanGapAtLowerRow - out.interpolationScratch[1]) / (meanGapAtLowerRow - meanGapAtUpperRow)
                    : 0;
            mixThreshold = FixedPoint.fromProbability(Math.min(1, Math.max(0, mixWeight)));
            effectiveMeanGap = meanGapAtLowerRow
                    - FixedPoint.toProbability(mixThreshold) * (meanGapAtLowerRow - meanGapAtUpperRow);
        }
        out.lowerLabelRow = rowOffset + lowerLabelRow;
        out.mixThreshold = mixThreshold;
        out.meanScaledValue = branch.referenceValue + branch.signOfT * effectiveMeanGap;
    }
}
