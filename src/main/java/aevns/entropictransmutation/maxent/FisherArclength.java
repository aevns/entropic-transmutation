package aevns.entropictransmutation.maxent;

/**
 * Fisher-Rao arclength helpers. Along a curve of distributions p_theta, the speed is sqrt(Var(score)), and
 * the arclength is the integral of speed d(theta). Rows spaced equally in arclength are about equally
 * distinguishable (equal Hellinger / total-variation distance to first order), independent of how the
 * curve is parameterized.
 */
final class FisherArclength {
    private FisherArclength() {}

    /**
     * Speed along the curve where E[count] is held fixed while beta varies, for p(c, k) in an exponential
     * family with statistics (c * x, c):
     *   speed^2 = Var(c x) - Cov(c, c x)^2 / Var(c),
     * the variance of c*x left over after removing what is explained by c. Computed on the centered statistic
     * c * (x - a), a = E[c x] / E[c], which has the same residual but avoids cancellation.
     * The null state (count 0) contributes zero to every sum, so only counts 1..n are summed.
     *
     * @param countProbabilities           p(c) for c = 0..n
     * @param meanScaledValueGivenCount    E[x | c]
     * @param varianceScaledValueGivenCount Var[x | c]
     */
    static double speedAlongFixedExpectedCountCurve(double[] countProbabilities,
                                                    double[] meanScaledValueGivenCount,
                                                    double[] varianceScaledValueGivenCount) {
        int stateCount = countProbabilities.length;
        double expectedCount = 0, expectedCountTimesValue = 0;
        for (int count = 1; count < stateCount; count++) {
            expectedCount += countProbabilities[count] * count;
            expectedCountTimesValue += countProbabilities[count] * count * meanScaledValueGivenCount[count];
        }
        double countWeightedMeanValue = expectedCountTimesValue / expectedCount;
        double countSecondMoment = 0, countValueCovarianceTerm = 0, valueVarianceTerm = 0, centeredCountValueMean = 0;
        for (int count = 1; count < stateCount; count++) {
            double centeredMean = meanScaledValueGivenCount[count] - countWeightedMeanValue;
            countSecondMoment += countProbabilities[count] * count * count;
            countValueCovarianceTerm += countProbabilities[count] * count * count * centeredMean;
            valueVarianceTerm += countProbabilities[count] * count * count
                    * (varianceScaledValueGivenCount[count] + centeredMean * centeredMean);
            centeredCountValueMean += countProbabilities[count] * count * centeredMean;
        }
        double countVariance = countSecondMoment - expectedCount * expectedCount;
        double countValueCovariance = countValueCovarianceTerm - expectedCount * centeredCountValueMean;
        double valueVariance = valueVarianceTerm - centeredCountValueMean * centeredCountValueMean;
        double residualVariance = valueVariance
                - (countVariance > 0 ? countValueCovariance * countValueCovariance / countVariance : 0);
        return Math.sqrt(Math.max(residualVariance, 0));
    }

    /** Trapezoid-rule cumulative arclength at each position; element 0 is 0. positions must be ascending. */
    static double[] cumulativeArclength(double[] positions, double[] speeds) {
        double[] cumulative = new double[positions.length];
        for (int i = 1; i < positions.length; i++)
            cumulative[i] = cumulative[i - 1] + 0.5 * (speeds[i - 1] + speeds[i]) * (positions[i] - positions[i - 1]);
        return cumulative;
    }

    /** Position at which the cumulative arclength reaches target, by linear interpolation between positions. */
    static double positionAtArclength(double[] positions, double[] cumulative, double targetArclength) {
        int low = 0, high = positions.length - 1;
        while (high - low > 1) {
            int middle = (low + high) >>> 1;
            if (cumulative[middle] <= targetArclength) low = middle; else high = middle;
        }
        double span = cumulative[high] - cumulative[low];
        double fraction = span > 0 ? (targetArclength - cumulative[low]) / span : 0;
        return positions[low] + fraction * (positions[high] - positions[low]);
    }

    /** Index of the position whose cumulative arclength is nearest to the target. */
    static int nearestPositionIndex(double[] cumulative, double targetArclength) {
        int low = 0, high = cumulative.length - 1;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (cumulative[middle] >= targetArclength) high = middle; else low = middle + 1;
        }
        int pick = low;
        if (low > 0 && targetArclength - cumulative[low - 1] < cumulative[low] - targetArclength) pick = low - 1;
        return pick;
    }
}
