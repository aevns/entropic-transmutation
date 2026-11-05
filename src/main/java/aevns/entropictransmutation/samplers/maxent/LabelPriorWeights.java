package aevns.entropictransmutation.samplers.maxent;

/**
 * The prior mass W_k of each unique value (before any constraint), normalized to sum to 1.
 *
 * With u = ln(1 + v / v0) (v0 = valueTransitionScale: linear below it, logarithmic above) and h = ln(1 + weberFraction),
 * W_k ~ g_k / (1 - sigma + sigma * rho_k), where sigma = valueSimilarity and rho_k is the kernel density of array
 * entries around value k in u (see {@link DensityPrior}). sigma = 0: every array entry equally likely a priori.
 * sigma = 1: in a continuum with entry density D(u), rho = (4h/3) D and W is uniform in u. For an isolated value
 * W ~ g / (1 + (g - 1) * sigma).
 */
final class LabelPriorWeights {
    private LabelPriorWeights() {}

    static double[] compute(double[] sortedUniqueValues, int[] groupSizes, Params params) {
        DensityPrior.validate(params.weberFraction, params.valueTransitionScale);
        double[] positions = new double[sortedUniqueValues.length];
        for (int i = 0; i < positions.length; i++) positions[i] = Math.log1p(sortedUniqueValues[i] / params.valueTransitionScale);
        double[] weights = DensityPrior.weights(positions, groupSizes, params.valueSimilarity,
                Math.log1p(params.weberFraction));
        double weightSum = 0;
        for (double weight : weights) weightSum += weight;
        for (int k = 0; k < weights.length; k++) weights[k] /= weightSum;
        return weights;
    }
}
