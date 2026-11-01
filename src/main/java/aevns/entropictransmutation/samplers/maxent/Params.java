package aevns.entropictransmutation.samplers.maxent;

/** Construction-time tuning parameters. All are fixed once the sampler is built. */
public final class Params {
    /**
     * Exponent alpha in the label prior W_k ~ g_k * rho_k^(-alpha), where g_k is the number of array entries with
     * unique value k and rho_k the kernel density of entries around it in log-value space (see LabelPriorWeights).
     * 0 = every array entry equally likely a priori; 1 = prior mass depends on the extent of log-value space a
     * region covers, not on how many entries crowd into it. Must be in [0, 1].
     */
    public double densityExponent = 1.0;
    /**
     * Width of the density kernel as a Weber fraction: the kernel has half-width ln(1 + weberFraction) in
     * u = ln(1 + v), i.e. values within this relative difference count as neighbours. Roughly the smallest
     * relative difference in magnitude people reliably notice. Must be positive.
     */
    public double weberFraction = 0.2;//0.15
    /** Prior weight of the null state (count 0, no label), relative to sum_k W_k = 1 for each nonzero count. */
    public double nullStatePrior = 1.0;
    /**
     * Same construction as densityExponent, applied to the counts 1..64 along u = ln(count): m(c) ~ rho_c^(-alpha).
     * 0 = every count equally likely a priori; 1 = counts too close in ratio to tell apart share one unit of weight
     * (so, e.g., 33 and 34 are not each a separate chance). Must be in [0, 1].
     */
    public double countDensityExponent = 1.0;
    /** Weber fraction for counts (kernel half-width ln(1 + countWeberFraction) in ln(count)). Must be positive. */
    public double countWeberFraction = 0.15;
    /** Saturation tolerance: the extreme value group must hold at least 1 - tolerance of the mass at the clamp. */
    public double saturationTolerance = 1e-5;
    /** Number of stored target-s rows per supported expected count n. */
    public int targetRowsPerExpectedCount = 256;
    /** Block size of the coarse label CDF (memory ~ 1 / labelBlockSize). */
    public int labelBlockSize = 8;
    /** Budget for the shared label CDF table; sets the number of label rows. */
    public long labelTableBudgetBytes = 20_000_000L;
    /** Candidate beta points per sign, log-spaced in |beta|, used to place target rows by Fisher arclength. */
    public int betaCandidatesPerSign = 3000;
}
