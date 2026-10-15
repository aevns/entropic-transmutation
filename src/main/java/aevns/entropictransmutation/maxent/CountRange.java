package aevns.entropictransmutation.maxent;

/** The supported counts: 0..MAX_COUNT inclusive. */
final class CountRange {
    static final int MAX_COUNT = 64;
    /** Number of count states, including count 0 (the null state). */
    static final int STATE_COUNT = MAX_COUNT + 1;

    private CountRange() {}
}
