package aevns.entropictransmutation.maxent;

/**
 * Vose's alias method for sampling a discrete distribution with one column pick and one comparison.
 * Draw: pick column i uniformly, then return i if a uniform fixed-point number is below thresholds[i], else aliases[i].
 * Storing the tables is left to the caller, so it can interleave them with other per-entry data.
 */
final class AliasMethod {
    private AliasMethod() {}

    static void buildTables(double[] probabilities, int[] thresholdsOut, int[] aliasesOut) {
        int size = probabilities.length;
        double[] scaledProbabilities = new double[size], keepProbabilities = new double[size];
        int[] smallStack = new int[size], largeStack = new int[size];
        int smallCount = 0, largeCount = 0;
        for (int i = 0; i < size; i++) {
            scaledProbabilities[i] = probabilities[i] * size;
            if (scaledProbabilities[i] < 1) smallStack[smallCount++] = i; else largeStack[largeCount++] = i;
        }
        while (smallCount > 0 && largeCount > 0) {
            int small = smallStack[--smallCount], large = largeStack[--largeCount];
            keepProbabilities[small] = scaledProbabilities[small];
            aliasesOut[small] = large;
            scaledProbabilities[large] = scaledProbabilities[large] + scaledProbabilities[small] - 1;
            if (scaledProbabilities[large] < 1) smallStack[smallCount++] = large; else largeStack[largeCount++] = large;
        }
        while (largeCount > 0) {
            int large = largeStack[--largeCount];
            keepProbabilities[large] = 1;
            aliasesOut[large] = large;
        }
        while (smallCount > 0) {
            int small = smallStack[--smallCount];
            keepProbabilities[small] = 1;
            aliasesOut[small] = small;
        }
        for (int i = 0; i < size; i++) thresholdsOut[i] = FixedPoint.fromProbability(keepProbabilities[i]);
    }

    /**
     * Adds one column's share of the distribution the quantized tables actually realize:
     * 1/size of mass, split between the column and its alias by the stored threshold.
     */
    static void addColumnMass(double[] realizedProbabilitiesOut, int column, int threshold, int alias) {
        int size = realizedProbabilitiesOut.length;
        double keepProbability = FixedPoint.toProbability(threshold);
        realizedProbabilitiesOut[column] += keepProbability / size;
        realizedProbabilitiesOut[alias] += (1 - keepProbability) / size;
    }
}
