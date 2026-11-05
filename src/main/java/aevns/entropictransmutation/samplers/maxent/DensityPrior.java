package aevns.entropictransmutation.samplers.maxent;

/**
 * Prior weights corrected for perceived similarity, after Leinster and Cobbold's similarity-sensitive diversity.
 * With K_ij = 1 - ((x_i - x_j) / halfWidth)^2 an Epanechnikov kernel along a perceptual axis x (the caller chooses x,
 * e.g. a log, and K_ij = 0 beyond halfWidth), two distinct items have similarity Z_ij = sigma * K_ij, and each item
 * has Z_ii = 1. Sigma is the most similar two distinct items can be, reached when they sit at the same position.
 *
 * Solving Z w = 1 would give the unconstrained maximum-diversity distribution. It can go negative, so each item is
 * given the one-step approximation 1 / (Z 1)_i instead. For an item of unique position k, with multiplicity g_k:
 *
 *   w_k = g_k / (1 - sigma + sigma * rho_k),   rho_k = sum_j g_j * K_kj
 *
 * rho_k includes k itself, so rho_k >= g_k. Sigma = 0 leaves w = g. Sigma = 1 gives g / rho, where a region's total
 * weight depends on how much of the axis it covers rather than on how many items crowd into it. In between, g items
 * at one position count as g / (1 + (g - 1) * sigma) items, which levels off at 1 / sigma.
 * Cost is O(K): positions arrive sorted, so a sliding window keeps running sums of g, g*d and g*d^2.
 */
final class DensityPrior {
    private DensityPrior() {}

    static void validate(double weberFraction, double transitionScale) {
        if (!(transitionScale > 0) || Double.isInfinite(transitionScale))
            throw new IllegalArgumentException("transition scale must be positive and finite");
        if (!(weberFraction > 0) || Double.isInfinite(weberFraction))
            throw new IllegalArgumentException("Weber fraction must be positive and finite");
    }

    /** Unnormalized weights; sortedPositions must be non-decreasing. */
    static double[] weights(double[] sortedPositions, int[] multiplicities, double similarity, double halfWidth) {
        if (!(similarity >= 0 && similarity <= 1)) throw new IllegalArgumentException("similarity must be in [0, 1]");
        int uniqueCount = sortedPositions.length;
        double origin = sortedPositions[0];
        double[] position = new double[uniqueCount];
        for (int i = 0; i < uniqueCount; i++) position[i] = sortedPositions[i] - origin;

        // Sliding window [windowStart, windowEnd) around k holding sums of g, g*d and g*d^2 where d is the offset
        // from the current point. Moving the anchor from one point to the next shifts the sums algebraically, so
        // every quantity stays on the scale of one kernel width (global prefix sums would cancel catastrophically
        // for narrow kernels).
        double[] weights = new double[uniqueCount];
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
                    double g = multiplicities[windowStart], d = position[windowStart] - anchor;
                    sumCount -= g; sumOffset -= g * d; sumSquaredOffset -= g * d * d;
                    windowStart++;
                }
            }
            while (windowEnd < uniqueCount && position[windowEnd] - anchor <= halfWidth) {
                double g = multiplicities[windowEnd], d = position[windowEnd] - anchor;
                sumCount += g; sumOffset += g * d; sumSquaredOffset += g * d * d;
                windowEnd++;
            }
            double density = sumCount - sumSquaredOffset / (halfWidth * halfWidth);
            density = Math.max(density, multiplicities[k]);   // guards rounding; exact value is >= g_k
            weights[k] = multiplicities[k] / (1 - similarity + similarity * density);
        }
        return weights;
    }
}
