package aevns.entropictransmutation.samplers.maxent;

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
    private double warmStartLambda = 0;

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

    /** Var[count] of the distribution currently in countProbabilities, with the given mean. */
    private double countVariance(double mean) {
        double variance = 0;
        for (int count = 0; count < CountRange.STATE_COUNT; count++) {
            double offset = count - mean;
            variance += countProbabilities[count] * offset * offset;
        }
        return variance;
    }

    /**
     * Newton's method on lambda, where E[count] is strictly decreasing with derivative -Var[count], warm-started from
     * the previous solve, which is usually a nearby beta (the jump back to the lowest beta after the candidate sweep
     * is the exception). Steps leaving the bracket found so far (a saturated distribution has almost no variance)
     * bisect it, or widen the search if it isn't closed yet. Leaves the solution in countProbabilities.
     */
    void solveLambdaForExpectedCount(double expectedCount) {
        double lambda = warmStartLambda;
        double low = Double.NEGATIVE_INFINITY, high = Double.POSITIVE_INFINITY;
        for (int iteration = 0; iteration < 200; iteration++) {
            double mean = expectedCountForLambda(lambda);
            if (mean > expectedCount) low = lambda; else high = lambda;
            double next = lambda + (mean - expectedCount) / countVariance(mean);
            if (Math.abs(next - lambda) <= 1e-15 * Math.max(1, Math.abs(lambda))) break;
            if (!(next > low && next < high)) {
                boolean bracketed = !Double.isInfinite(low) && !Double.isInfinite(high);
                next = bracketed ? 0.5 * (low + high) : lambda + Math.copySign(1 + Math.abs(lambda), mean - expectedCount);
                if (next == low || next == high) break;
            }
            lambda = next;
        }
        warmStartLambda = lambda;
        expectedCountForLambda(lambda);
    }

    /** Fisher-Rao speed d(arclength)/d(beta) along the fixed-E[count] curve, for the current solution. */
    double fisherSpeedWithRespectToBeta() {
        return FisherArclength.speedAlongFixedExpectedCountCurve(
                countProbabilities, meanScaledValueGivenCount, varianceScaledValueGivenCount);
    }
}
