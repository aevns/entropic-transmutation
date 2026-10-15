package aevns.entropictransmutation.maxent;

/**
 * For a given beta, finds the count distribution p(c) ~ exp(logWeightBeforeLambda[c] - lambda * c) whose mean
 * equals a requested expected count, by a monotone 1D solve for lambda. Offline use only (not on the sampling path).
 *
 * Here logWeightBeforeLambda[c] = ln(prior(c)) + ln sum_k W_k exp(-beta c x_k) for c >= 1, and
 * ln(nullStatePrior) for c = 0.
 */
final class CountDistributionSolver {
    private final LabelFamily labelFamily;
    private final double[] logPrior;
    private final LabelFamily.Point labelPoint = new LabelFamily.Point();

    final double[] logWeightBeforeLambda = new double[CountRange.STATE_COUNT];
    final double[] countProbabilities = new double[CountRange.STATE_COUNT];
    /** E[x | c] under the stored label rows, mixed to match the exact family mean at t = beta * c. */
    final double[] meanScaledValueGivenCount = new double[CountRange.STATE_COUNT];
    final double[] varianceScaledValueGivenCount = new double[CountRange.STATE_COUNT];
    final int[] labelRowIndex = new int[CountRange.STATE_COUNT];
    final int[] labelRowMixThreshold = new int[CountRange.STATE_COUNT];

    CountDistributionSolver(LabelFamily labelFamily, double[] logPrior) {
        this.labelFamily = labelFamily;
        this.logPrior = logPrior;
    }

    void configureForBeta(double beta) {
        logWeightBeforeLambda[0] = logPrior[0];
        for (int count = 1; count < CountRange.STATE_COUNT; count++) {
            labelFamily.evaluateAtT(beta * count, labelPoint);
            varianceScaledValueGivenCount[count] = labelPoint.variance;
            labelRowIndex[count] = labelPoint.lowerLabelRow;
            labelRowMixThreshold[count] = labelPoint.mixThreshold;
            meanScaledValueGivenCount[count] = labelPoint.meanScaledValue;
            logWeightBeforeLambda[count] = logPrior[count] + labelPoint.logPartition;
        }
    }

    /** Fills countProbabilities for the given lambda and returns E[count]. */
    double expectedCountForLambda(double lambda) {
        double maxLogWeight = logWeightBeforeLambda[0];
        for (int count = 1; count < CountRange.STATE_COUNT; count++)
            maxLogWeight = Math.max(maxLogWeight, logWeightBeforeLambda[count] - lambda * count);
        double normalizer = 0, countSum = 0;
        for (int count = 0; count < CountRange.STATE_COUNT; count++) {
            double weight = Math.exp(logWeightBeforeLambda[count] - lambda * count - maxLogWeight);
            countProbabilities[count] = weight;
            normalizer += weight;
            countSum += weight * count;
        }
        for (int count = 0; count < CountRange.STATE_COUNT; count++) countProbabilities[count] /= normalizer;
        return countSum / normalizer;
    }

    /** Bisection on lambda (E[count] is strictly decreasing in lambda); leaves the solution in countProbabilities. */
    void solveLambdaForExpectedCount(double expectedCount) {
        double low = -1, high = 1;
        while (expectedCountForLambda(low) < expectedCount) low *= 2;
        while (expectedCountForLambda(high) > expectedCount) high *= 2;
        for (int iteration = 0; iteration < 200; iteration++) {
            double middle = 0.5 * (low + high);
            if (expectedCountForLambda(middle) > expectedCount) low = middle; else high = middle;
            if (high - low <= 1e-15 * Math.max(1, Math.abs(middle))) break;
        }
        expectedCountForLambda(0.5 * (low + high));
    }

    /** Fisher-Rao speed d(arclength)/d(beta) along the fixed-E[count] curve, for the current solution. */
    double fisherSpeedWithRespectToBeta() {
        return FisherArclength.speedAlongFixedExpectedCountCurve(
                countProbabilities, meanScaledValueGivenCount, varianceScaledValueGivenCount);
    }
}
