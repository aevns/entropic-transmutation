package aevns.entropictransmutation.samplers.maxent;

/**
 * The prior mass W_k of each unique value (before any constraint), normalized to sum to 1.
 *
 * With u = ln(1 + v), g_k the number of array entries holding unique value k, and h = ln(1 + weberFraction):
 *
 *   rho_k = sum_j g_j * (1 - ((u_k - u_j) / h)^2)   over j with |u_k - u_j| <= h   (Epanechnikov kernel density of
 *                                                                                   entries around value k)
 *   W_k  ~ g_k * rho_k^(-alpha)
 *
 * alpha = 0 gives W ~ g (every array entry equally likely a priori). alpha = 1 divides out the local density of
 * entries, so the prior mass of a region of log-value space depends on how much of it there is, not on how many
 * entries crowd into it: in a continuum with entry density D(u), rho = (4h/3) D and W is uniform in u. Between
 * the two, W ~ g^(1-alpha) for an isolated value and interpolates for clumped ones.
 * The kernel includes k itself, so rho_k >= g_k and W_k <= g_k^(1-alpha).
 *
 * Cost is O(K): the unique values arrive sorted, so a sliding window keeps running sums of g, g*d and g*d^2.
 */
final class LabelPriorWeights {
    private LabelPriorWeights() {}

    static double[] compute(double[] sortedUniqueValues, int[] groupSizes, Params params) {
        double alpha = params.densityExponent, weberFraction = params.weberFraction;
        if (!(alpha >= 0 && alpha <= 1)) throw new IllegalArgumentException("densityExponent must be in [0, 1]");
        if (!(weberFraction > 0) || Double.isInfinite(weberFraction))
            throw new IllegalArgumentException("weberFraction must be positive and finite");
        int uniqueCount = sortedUniqueValues.length;
        double halfWidth = Math.log1p(weberFraction);

        double origin = Math.log1p(sortedUniqueValues[0]);
        double[] position = new double[uniqueCount];
        for (int i = 0; i < uniqueCount; i++) position[i] = Math.log1p(sortedUniqueValues[i]) - origin;

        // Sliding window [windowStart, windowEnd) around k holding sums of g, g*d and g*d^2 where d is the offset
        // from the current point. Moving the anchor from one point to the next shifts the sums algebraically, so
        // every quantity stays on the scale of one kernel width (global prefix sums would cancel catastrophically
        // for narrow kernels).
        double[] weights = new double[uniqueCount];
        double weightSum = 0;
        int windowStart = 0, windowEnd = 0;
        double sumCount = 0, sumOffset = 0, sumSquaredOffset = 0;
        double anchor = position[0];
        for (int k = 0; k < uniqueCount; k++) {
            double shift = position[k] - anchor;      // d_j' = d_j - shift
            anchor = position[k];
            if (shift > 2 * halfWidth) {
                // The old and new windows cannot overlap: restart from exact zeros instead of carrying rounding
                // residue across the gap (it would be amplified by the large shift).
                sumCount = sumOffset = sumSquaredOffset = 0;
                int restart = k;
                while (restart > 0 && anchor - position[restart - 1] <= halfWidth) restart--;
                windowStart = windowEnd = restart;
            } else {
                sumSquaredOffset += -2 * shift * sumOffset + shift * shift * sumCount;
                sumOffset -= shift * sumCount;
                while (windowStart < windowEnd && anchor - position[windowStart] > halfWidth) {
                    double g = groupSizes[windowStart], d = position[windowStart] - anchor;
                    sumCount -= g; sumOffset -= g * d; sumSquaredOffset -= g * d * d;
                    windowStart++;
                }
            }
            while (windowEnd < uniqueCount && position[windowEnd] - anchor <= halfWidth) {
                double g = groupSizes[windowEnd], d = position[windowEnd] - anchor;
                sumCount += g; sumOffset += g * d; sumSquaredOffset += g * d * d;
                windowEnd++;
            }
            double density = sumCount - sumSquaredOffset / (halfWidth * halfWidth);
            density = Math.max(density, groupSizes[k]);   // guards rounding; exact value is >= g_k
            weights[k] = groupSizes[k] * Math.pow(density, -alpha);
            weightSum += weights[k];
        }
        for (int k = 0; k < uniqueCount; k++) weights[k] /= weightSum;
        return weights;
    }
}
