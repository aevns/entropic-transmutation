package aevns.entropictransmutation.maxent;

/** Construction-time tuning parameters. All are fixed once the sampler is built. */
public final class Params {
    /**
     * Exponent a in the label prior W_k ~ groupSize_k^(1 - a) * perceptualWeight(v_k)^b.
     * 0 = every array entry equally likely a priori; 1 = every unique value equally likely a priori.
     */
    public double multiplicityDiscountExponent = 0.5;
    /** Exponent b on the perceptual factor w(v) (0 disables it). */
    public double perceptualWeightExponent = 1.0;
    /** Ratio r for exponentially spaced values in w(v); NaN = estimate as median of v_{k+1}/v_k. */
    public double valueSpacingRatio = Double.NaN;
    /** Prior weight of the null state (count 0, no label), relative to sum_k W_k = 1 for each nonzero count. */
    public double nullStatePrior = 1.0;
    /** Count prior m(c), c = 1..64 (index 0 ignored). null means m == 1. */
    public double[] countPrior = null;
    /** Saturation tolerance: the extreme value group must hold at least 1 - tolerance of the mass at the clamp. */
    public double saturationTolerance = 1e-5;
    /** Number of stored target-s rows per supported expected count n. */
    public int targetRowsPerExpectedCount = 256;
    /** Block size of the coarse label CDF (memory ~ 1 / labelBlockSize). */
    public int labelBlockSize = 8;
    /** Budget for the shared label CDF table; sets the number of label rows. */
    public long labelTableBudgetBytes = 20_000_000L;
    /** Candidate beta points per sign of beta, used to place target rows by Fisher arclength. */
    public int betaCandidatesPerSign = 1500;
}
