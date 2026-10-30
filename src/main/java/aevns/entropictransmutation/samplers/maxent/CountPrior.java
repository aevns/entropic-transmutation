package aevns.entropictransmutation.samplers.maxent;

/** The prior over counts, as log-weights. */
final class CountPrior {
    private CountPrior() {}

    /**
     * Index 0 is the log prior of the null state (count 0, no label); indices 1..MAX_COUNT are ln m(c) for the
     * labelled counts, where each labelled count's label mass is split by the label prior summing to 1.
     */
    static double[] logWeights(Params params) {
        double[] logWeights = new double[CountRange.STATE_COUNT];
        logWeights[0] = Math.log(params.nullStatePrior);
        for (int count = 1; count < CountRange.STATE_COUNT; count++)
            logWeights[count] = Math.log(params.countPrior == null ? 1.0 : params.countPrior[count]);
        return logWeights;
    }
}
