package aevns.entropictransmutation.samplers.maxent;

import java.util.Arrays;

/** The prior over counts, as log-weights. */
final class CountPrior {
    private CountPrior() {}

    /**
     * The count weights m(c), c = 1..MAX_COUNT, normalized to mean 1 (so the null-state prior keeps its meaning
     * relative to the label masses, which sum to 1 per count). m(c) ~ rho_c^(-alpha), where rho_c is the kernel
     * density of the integers 1..MAX_COUNT around c along u = ln c, with half-width ln(1 + countWeberFraction):
     * counts so close in ratio that they are hard to tell apart share weight, while small counts, which are
     * distinguishable, keep full weight each. With the default Weber fraction counts 1..6 are fully distinct.
     */
    static double[] countWeights(Params params) {
        DensityPrior.validate(params.countDensityExponent, params.countWeberFraction);
        // The density is evaluated with the integers continued past MAX_COUNT by one kernel width, so the top counts
        // are not mistaken for uncrowded just because no larger count exists. Only counts 1..MAX_COUNT get weights.
        int extendedCount = (int) Math.ceil(CountRange.MAX_COUNT * (1 + params.countWeberFraction)) + 1;
        double[] positions = new double[extendedCount];
        int[] ones = new int[extendedCount];
        for (int i = 0; i < extendedCount; i++) { positions[i] = Math.log(i + 1); ones[i] = 1; }
        double[] extendedWeights = DensityPrior.weights(positions, ones, params.countDensityExponent,
                Math.log1p(params.countWeberFraction));
        double[] weights = Arrays.copyOf(extendedWeights, CountRange.MAX_COUNT);
        double sum = 0;
        for (double weight : weights) sum += weight;
        for (int i = 0; i < weights.length; i++) weights[i] *= weights.length / sum;
        return weights;
    }

    /**
     * Index 0 is the log prior of the null state (count 0, no label); indices 1..MAX_COUNT are ln m(c) for the
     * labelled counts, where each labelled count's label mass is split by the label prior summing to 1.
     */
    static double[] logWeights(Params params) {
        double[] logWeights = new double[CountRange.STATE_COUNT];
        logWeights[0] = Math.log(params.nullStatePrior);
        double[] countWeights = countWeights(params);
        for (int count = 1; count < CountRange.STATE_COUNT; count++)
            logWeights[count] = Math.log(countWeights[count - 1]);
        return logWeights;
    }
}
