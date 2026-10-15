package aevns.entropictransmutation.data;

import java.util.Arrays;
import java.util.Random;
import java.util.Comparator;
import java.util.SplittableRandom;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

public class MaxEntropyCollectionSampler {
    public final int valueCount;
    private final int[] valueRepeats;
    private final int[] valueIndexMap;
    public final int betaCount;
    private final PchipInterpolator eValToBetaInterpolator;
    private final double[] cumChanceMatrix;

    private static SplittableRandom random;

    public MaxEntropyCollectionSampler(double [] sortedEntryValues, int resolution, long randomSeed) {
        // Maybe pass a Random object in instead?
        random = new SplittableRandom(randomSeed);

        int count = 1;
        for (int i = 1; i < sortedEntryValues.length; i++) {
            double val = sortedEntryValues[i];
            if (val != sortedEntryValues[i - 1]) count++;
        }
        valueCount = count;

        double[] values = new double[valueCount];
        values[0] = sortedEntryValues[0];
        valueRepeats = new int[valueCount];
        valueRepeats[0] = 1;
        for (int i = 1, j = 0; i < sortedEntryValues.length; i++) {
            if (sortedEntryValues[i] == sortedEntryValues[i - 1]) {
                valueRepeats[j]++;
            } else {
                j++;
                values[j] = sortedEntryValues[i];
                valueRepeats[j] = 1;
            }
        }

        valueIndexMap = new int[valueCount];
        valueIndexMap[0] = 0;
        for (int i = 1; i <= valueCount; i++) {
            valueIndexMap[i] = valueIndexMap[i - 1] + valueRepeats[i - 1];
        }

        double mean = DoubleStream.of(sortedEntryValues).average().orElse(0.0);
        double variance = 0.0;
        for (double val : sortedEntryValues) variance += Math.pow(val - mean, 2);
        double stdDev = Math.sqrt(variance / sortedEntryValues.length);

        double[] betaGrid = new double[resolution];
        for (int i = 0; i < resolution; i++) {
            double lerp = (i * 2.0 / (resolution - 1) - 1.0) * (1 - 1e-5);
            betaGrid[i] = Math.tan(lerp * Math.PI / 2.0) / stdDev;
        }

        double minVal = values[0];
        double maxVal = values[valueCount - 1];
        double[] eValGrid = new double[resolution];
        double[][] cumChanceArrays = new double[resolution][valueCount];
        for (int i = 0; i < resolution; i++) {
            double beta = betaGrid[i];
            double valShift = beta > 0 ? minVal : maxVal;
            double expShift = Math.max(- beta * (maxVal - valShift), - beta * (minVal - valShift));
            double[] weights = new double[values.length];
            for (int j = 0; j < values.length; j++) {
                double weight = -beta * (values[j] - valShift);
                weights[j] = valueRepeats[j] * Math.exp(weight - expShift);
                cumChanceArrays[i][j] = j > 0 ? cumChanceArrays[i][j - 1] + weights[j] : weights[j];
            }
            eValGrid[i] = 0;
            double maxWeight = cumChanceArrays[i][values.length - 1];
            for (int j = 0; j < values.length; j++) {
                eValGrid[i] += weights[j] / maxWeight * values[j];
                cumChanceArrays[i][j] /= maxWeight;
            }
        }
        // I hate this, should probably look into fastutils or something, I miss numpy and pytorch
        Integer[] sortedIndices = IntStream.range(0, resolution).boxed().toArray(Integer[]::new);
        Arrays.sort(sortedIndices, Comparator.comparingDouble((Integer a) -> eValGrid[a]));
        // Needs to be strictly monotonic; only use the first entry for identical expectation values
        // hopefully this shouldn't occur, but if it does, should beta be averaged over identical eVal instead?
        count = 1;
        for (int i = 1; i < resolution; i++) {
            if (eValGrid[sortedIndices[i]] != eValGrid[sortedIndices[i - 1]]) count++;
        }
        betaCount = count;

        // Finally, we switch to barebones primitive arrays
        double[] eValSpline = new double[betaCount];
        eValSpline[0] = eValGrid[sortedIndices[0]];
        double[] betaSpline = new double[betaCount];
        betaSpline[0] = betaGrid[sortedIndices[0]];
        cumChanceMatrix = new double[betaCount * valueCount];
        System.arraycopy(cumChanceArrays[sortedIndices[0]], 0, cumChanceMatrix, 0, valueCount);
        for (int i = 1, j = 0; i < resolution; i++) {
            if (eValGrid[sortedIndices[i]] != eValGrid[sortedIndices[i - 1]]) {
                j++;
                eValSpline[j] = eValGrid[sortedIndices[i]];
                betaSpline[j] = betaGrid[sortedIndices[i]];
                System.arraycopy(cumChanceArrays[sortedIndices[i]], 0, cumChanceMatrix, j * valueCount, valueCount);
            }
        }
        eValToBetaInterpolator = new PchipInterpolator(eValSpline, betaSpline);
    }

    /**
     * Get a random collection index, sampled according to a maximum entropy distribution with a given mean value.
     * @param meanValue the expected value of the entry at the output index
     * @return a collection index
     */
    private int GetCollectionIndex(double meanValue){
        int betaIndex = eValToBetaInterpolator.ditheredIndex(meanValue);
        int index = Arrays.binarySearch(cumChanceMatrix, betaIndex * valueCount, (betaIndex + 1) * valueCount, random.nextDouble());
        if (index < 0) index = -index - 1;
        index -= betaIndex * valueCount;
        if (index >= valueCount) index = valueCount - 1;
        return valueIndexMap[index] + ((valueRepeats[index] > 1) ? random.nextInt(valueRepeats[index]) : 0);
    }

    /**
     * Get the index of a collection element and an associated count.
     * The output is distributed according to the joint maximum entropy distribution over
     * collection elements and counts, with mean total value meanValue and mean count meanCount.
     * <p>
     *     By default, counts can be any integer in the range [1, 64].
     * </p>
     * @param meanValue the expected total value of the entry at the output index, multiplied by the output count
     * @param meanCount the expected output count
     * @return a SampleStack record containing a collection index and count
     */
    public SampleStack GetCollectionIndexAndCount(double meanValue, int meanCount){
        int count = GetCount(meanCount); // ~ P(count)
        int index = GetCollectionIndex(meanValue / count); // ~ P(index | count)
        return new SampleStack(index, count); // ~ P(index, count) = P(index | count) * P(count)
    }

    private int GetCount(int expectedCount){
        return expectedCount;
    }

    public record SampleStack(int index, int count) {}

    private static class PchipInterpolator {
        public final double[] x, y, d;

        public PchipInterpolator(double[] x, double[] y) {
            this.x = x; this.y = y;
            int n = x.length;
            d = new double[n];
            double[] h = new double[n - 1];
            double[] delta = new double[n - 1];
            for (int i = 0; i < n - 1; i++) {
                h[i] = x[i + 1] - x[i];
                delta[i] = (y[i + 1] - y[i]) / h[i];
            }
            for (int i = 1; i < n - 1; i++) {
                if (delta[i - 1] * delta[i] <= 0) {
                    d[i] = 0.0;
                } else {
                    double w1 = 2.0 * h[i] + h[i - 1];
                    double w2 = h[i] + 2.0 * h[i - 1];
                    d[i] = (w1 + w2) / (w1 / delta[i - 1] + w2 / delta[i]);
                }
            }
            d[0] = delta[0];
            d[n - 1] = delta[n - 2];
        }

        public double interpolatedValue(double target) {
            int idx = Arrays.binarySearch(x, target);
            if (idx >= 0) return y[idx];
            idx = -idx - 2;
            if (idx < 0) idx = 0;
            if (idx >= x.length - 1) idx = x.length - 2;

            double h = x[idx + 1] - x[idx];
            double t = (target - x[idx]) / h;
            double t2 = t * t;
            double h00 = (2 * t - 3) * t2 + 1;
            double h10 = ((t - 2) * t + 1) * t;
            double h01 = (3 - 2 * t) * t2;
            double h11 = (t - 1) * t2;
            return h00 * y[idx] + h10 * h * d[idx] + h01 * y[idx + 1] + h11 * h * d[idx + 1];
        }

        public int ditheredIndex(double target) {
            int idx = Arrays.binarySearch(x, target);
            if (idx >= 0) return idx;
            idx = -idx - 2;
            if (idx < 0) idx = 0;
            if (idx > x.length - 1) idx = x.length - 2;

            double h = x[idx + 1] - x[idx];
            double t = (target - x[idx]) / h;
            double t2 = t * t;
            double h00 = (2 * t - 3) * t2 + 1;
            double h10 = ((t - 2) * t + 1) * t;
            double h01 = (3 - 2 * t) * t2;
            double h11 = (t - 1) * t2;
            double interp = h00 * y[idx] + h10 * h * d[idx] + h01 * y[idx + 1] + h11 * h * d[idx + 1];

            double lerpFactor = (interp - y[idx]) / (y[idx + 1] - y[idx]);
            return (random.nextDouble() > lerpFactor) ? idx : idx + 1;
        }
    }
}