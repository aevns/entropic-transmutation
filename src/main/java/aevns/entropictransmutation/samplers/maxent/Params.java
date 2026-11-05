package aevns.entropictransmutation.samplers.maxent;

/** Construction-time tuning parameters. All are fixed once the sampler is built. */
public final class Params {
    /**
     * Similarity sigma of two distinct items of equal value, in the label prior W_k ~ g_k / (1 - sigma + sigma * rho_k),
     * where g_k is the number of array entries with unique value k and rho_k the kernel density of entries around it
     * in log-value space (see LabelPriorWeights). 0 = every array entry equally likely a priori; 1 = prior mass depends
     * on the extent of log-value space a region covers, not on how many entries crowd into it. In between, a crowded
     * value counts as at most 1 / sigma items, so the default never treats one as more than about 10 distinct items.
     * Must be in [0, 1].
     */
    public double valueSimilarity = 0.1;
    /**
     * Width of the density kernel as a Weber fraction: the kernel has half-width ln(1 + weberFraction) in
     * u = ln(1 + v / valueTransitionScale), i.e. values within this relative difference count as neighbours.
     * Roughly the smallest relative difference in magnitude people reliably notice. Must be positive.
     */
    public double weberFraction = 0.15;
    /**
     * Transition scale v0 of the perceptual axis u = ln(1 + v / v0), in the same units as the values. Below v0
     * differences count in absolute terms (u is linear in v), above it in relative terms (u is logarithmic), like
     * the linear segment of the sRGB transfer function. It is the scale below which values are too small to
     * tell apart by ratio. Larger v0 pools more of the small values into shared cells. Must be positive.
     */
    public double valueTransitionScale = 10.0;
    /** Prior weight of the null state (count 0, no label), relative to sum_k W_k = 1 for each nonzero count. */
    public double nullStatePrior = 1.0;
    /**
     * Weber fraction for counts (kernel half-width ln(1 + countWeberFraction) along u = ln(1 + c / countTransitionScale)).
     * Counts too close in ratio to tell apart share weight, so 33 and 34 are not each a separate chance. As this tends
     * to 0 every count keeps its own weight, for a uniform prior. Must be positive.
     */
    public double countWeberFraction = 0.15;
    /**
     * Transition scale c0 of the count axis u = ln(1 + c / c0), as valueTransitionScale is for values. Values below 1
     * make small counts more distinct from each other (the axis tends to ln c as c0 -> 0); larger values pool
     * small counts. Must be positive.
     */
    public double countTransitionScale = 1.0;
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
