package aevns.entropictransmutation.data;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;
import java.util.stream.IntStream;

public class MaxEntropyCountSampler {
    private final Random random;
    private final int betaGridSize;
    private final double[] betaGrid;
    private final PchipInterpolator expectationToBetaInterpolator;

    public MaxEntropyCountSampler(int min, int max, int resolution, int randomSeed) {
        random = new Random(randomSeed);
        betaGridSize = resolution;
        int n = max - min + 1;
        double stdDev = Math.sqrt((Math.pow(n, 2) - 1) / 12.0);

        betaGrid = new double[betaGridSize];
        for (int i = 0; i < betaGridSize; i++) {
            double lerp = (i * 2.0 / (betaGridSize - 1) - 1.0) * (1 - 1e-5);
            betaGrid[i] = Math.tan(lerp * Math.PI / 2.0) / stdDev;
        }

        // If we can get beta, we have
        // pmf = e^(-bn)*(1-e^-b) / (1-e^(-b*64))
        // cmf = (e^(63 b) (e^b - e^(-b m)))/(-1 + e^(64 b))
        // <E> = [sum from 0 to 63 of n e^(-bn)] * (1-e^-b) / (1-e^(-b*64))
        // split it! just multiply all entries at the end by the factor, or normalize!

        // number from cumulative odds (round up or down?)
        // still needs b from z spline
        // j = -log(e^(b (1 + n)) + c (1 - e^(b n)))/b

        double[] expectationGrid = new double[betaGridSize];
        for (int i = 0; i < betaGridSize; i++) {
            double beta = betaGrid[i];
            double valShift = beta > 0 ? 0 : n;
            double expShift = Math.max(- beta * (n - valShift), - beta * (- valShift));
            double expectation = 0;
            double weightSum = 0;
            for (int j = 0; j < n; j++) {
                double weight = -beta * (j - valShift);
                weight = Math.exp(weight - expShift);
                expectation += j * weight;
                weightSum += weight;
            }
            expectationGrid[i] = expectation / weightSum;
        }
        // I hate this, should probably look into fastutils or something, I miss numpy and pytorch
        Integer[] expectationIndices = IntStream.range(0, betaGridSize).boxed().toArray(Integer[]::new);
        Arrays.sort(expectationIndices, Comparator.comparingDouble(a -> expectationGrid[a]));
        // Needs to be strictly monotonic; only use the first entry for identical z
        // hopefully this shouldn't occur, but if it does, should beta be averaged over identical z instead?
        int interpPointCount = 1;
        for (int i = 1; i < betaGridSize; i++) {
            if (expectationGrid[expectationIndices[i]] != expectationGrid[expectationIndices[i - 1]]) interpPointCount++;
        }
        double[] expectationSpline = new double[interpPointCount];
        double[] betaSpline = new double[interpPointCount];
        expectationSpline[0] = expectationGrid[expectationIndices[0]];
        betaSpline[0] = betaGrid[expectationIndices[0]];
        for (int i = 1, j = 0; i < betaGridSize; i++) {
            if (expectationGrid[expectationIndices[i]] != expectationGrid[expectationIndices[i - 1]]) {
                j++;
                expectationSpline[j] = expectationGrid[expectationIndices[i]];
                betaSpline[j] = betaGrid[expectationIndices[i]];
            }
        }
        expectationToBetaInterpolator = new PchipInterpolator(expectationSpline, betaSpline);
    }

    private static class PchipInterpolator {
        private final double[] x, y, d;

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

        public double interpolate(double target) {
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
    }
}
