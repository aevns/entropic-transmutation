package aevns.entropictransmutation.samplers.maxent;

import java.util.Arrays;

/** The prior mass W_k of each unique value (before any constraint), normalized to sum to 1. */
final class LabelPriorWeights {
    private LabelPriorWeights() {}

    /**
     * W_k ~ groupSize_k^(1 - a) * w(v_k)^b, with a = multiplicityDiscountExponent and b = perceptualWeightExponent.
     * w(v) = 0.5 * ln((1 + r v) / (1 + v / r)) is the width of a value's neighbourhood in u = ln(1 + v) when
     * unique values are exponentially spaced with ratio r (estimated as the median adjacent ratio unless given).
     */
    static double[] compute(double[] uniqueValues, int[] groupSizes, Params params) {
        int uniqueCount = uniqueValues.length;
        double spacingRatio = params.valueSpacingRatio;
        if (Double.isNaN(spacingRatio)) {
            double[] adjacentRatios = new double[uniqueCount - 1];
            for (int i = 0; i + 1 < uniqueCount; i++) adjacentRatios[i] = uniqueValues[i + 1] / uniqueValues[i];
            Arrays.sort(adjacentRatios);
            spacingRatio = adjacentRatios[adjacentRatios.length / 2];
        }
        double[] weights = new double[uniqueCount];
        double weightSum = 0;
        for (int i = 0; i < uniqueCount; i++) {
            double perceptualWeight = 0.5 * Math.log((1 + spacingRatio * uniqueValues[i])
                    / (1 + uniqueValues[i] / spacingRatio));
            weights[i] = Math.pow(groupSizes[i], 1 - params.multiplicityDiscountExponent)
                    * Math.pow(perceptualWeight, params.perceptualWeightExponent);
            weightSum += weights[i];
        }
        for (int i = 0; i < uniqueCount; i++) weights[i] /= weightSum;
        return weights;
    }
}
