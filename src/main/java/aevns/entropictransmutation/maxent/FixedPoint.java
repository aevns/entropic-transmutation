package aevns.entropictransmutation.maxent;

/**
 * Probabilities as unsigned 32-bit fixed point (value = bits / 2^32), stored in signed ints.
 * Comparing against random 32-bit chunks needs no floating point, and an int takes half the space of a double.
 */
final class FixedPoint {
    static final double SCALE = 4294967296.0;       // 2^32
    static final long MAX_UNSIGNED = 4294967295L;   // 2^32 - 1: the closest representable value to 1.0

    private FixedPoint() {}

    /** Rounds p in [0, 1] to fixed point, clamping 1.0 to the largest representable value. */
    static int fromProbability(double probability) {
        long fixed = Math.round(probability * SCALE);
        if (fixed > MAX_UNSIGNED) fixed = MAX_UNSIGNED;
        return (int) fixed;
    }

    static double toProbability(int fixed) {
        return Integer.toUnsignedLong(fixed) / SCALE;
    }

    /** The upper 32 bits of a random long, as an unsigned value in a long. */
    static long highBits(long random) {
        return random >>> 32;
    }

    /** The lower 32 bits of a random long, as an unsigned value in a long. */
    static long lowBits(long random) {
        return random & 0xFFFFFFFFL;
    }

    /** True with probability threshold / 2^32 when unsignedRandom32 is uniform on [0, 2^32). */
    static boolean isBelow(long unsignedRandom32, int threshold) {
        return unsignedRandom32 < Integer.toUnsignedLong(threshold);
    }
}
