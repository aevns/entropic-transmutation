package aevns.entropictransmutation.data;

import java.util.Arrays;
import java.util.Random;
import java.util.Comparator;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

public class MaxEntropySplineSampler {
    private final int uniqueCount;
    private final int[] valueIndices;
    private final int[] valueMultiplicities;
    private final double[] betaGrid;
    private final int betaGridSize;

    private final PchipInterpolator interpolator;
    private final double[][] valueChanceMatrix;
    private final Random random;

    public MaxEntropySplineSampler(double[] sortedValues, int resolution, long randomSeed) {
        //double[] sortedValues = DoubleStream.of(values).sorted().toArray();
        random = new Random(randomSeed);
        betaGridSize = resolution;
        double minVal = sortedValues[0];
        double maxVal = sortedValues[sortedValues.length - 1];

        int count = 1;
        for (int i = 1; i < sortedValues.length; i++) {
            double val = sortedValues[i];
            if (val != sortedValues[i - 1]) count++;
        }
        uniqueCount = count;

        double[] uniqueValues = new double[uniqueCount];
        uniqueValues[0] = sortedValues[0];
        valueMultiplicities = new int[uniqueCount];
        valueMultiplicities[0] = 1;
        for (int i = 1, j = 0; i < sortedValues.length; i++) {
            if (sortedValues[i] == sortedValues[i - 1]) {
                valueMultiplicities[j]++;
            } else {
                j++;
                uniqueValues[j] = sortedValues[i];
                valueMultiplicities[j] = 1;
            }
        }

        valueIndices = new int[uniqueCount + 1];
        valueIndices[0] = 0;
        for (int i = 1; i <= uniqueCount; i++) {
            valueIndices[i] = valueIndices[i - 1] + valueMultiplicities[i - 1];
        }

        double mean = DoubleStream.of(sortedValues).average().orElse(0.0);
        double variance = 0.0;
        for (double val : sortedValues) variance += Math.pow(val - mean, 2);
        double stdDev = Math.sqrt(variance / sortedValues.length);

        betaGrid = new double[betaGridSize];
        for (int i = 0; i < betaGridSize; i++) {
            double lerp = (i * 2.0 / (betaGridSize - 1) - 1.0) * (1 - 1e-5);
            betaGrid[i] = Math.tan(lerp * Math.PI / 2.0) / stdDev;
        }

        double[] expVals = new double[betaGridSize];
        valueChanceMatrix = new double[betaGridSize][uniqueCount];
        for (int i = 0; i < betaGridSize; i++) {
            double beta = betaGrid[i];
            double valShift = beta > 0 ? minVal : maxVal;
            double maxLW = - beta * (maxVal - valShift);
            maxLW = Math.max(maxLW, - beta * (minVal - valShift));
            double[] weights = new double[uniqueValues.length];
            for (int j = 0; j < uniqueValues.length; j++) {
                double weight = -beta * (uniqueValues[j] - valShift);
                weights[j] = valueMultiplicities[j] * Math.exp(weight - maxLW);
                valueChanceMatrix[i][j] = j > 0 ? valueChanceMatrix[i][j - 1] + weights[j] : weights[j];
            }
            expVals[i] = valShift;
            double maxWeight = valueChanceMatrix[i][uniqueValues.length - 1];
            for (int j = 0; j < uniqueValues.length; j++) {
                expVals[i] += weights[j] * (uniqueValues[j] - valShift) / maxWeight;
                valueChanceMatrix[i][j] /= maxWeight;
            }
            //valueChanceMatrix[i][uniqueValues.length - 1] = 1.0;
        }
        Integer[] sortIdx = IntStream.range(0, betaGridSize).boxed().toArray(Integer[]::new);
        Arrays.sort(sortIdx, Comparator.comparingDouble(a -> expVals[a]));
        int uSplineCount = 1;
        for (int i = 1; i < betaGridSize; i++) {
            if (expVals[sortIdx[i]] != expVals[sortIdx[i - 1]]) uSplineCount++;
        }
        double[] splineX = new double[uSplineCount];
        double[] splineY = new double[uSplineCount];
        splineX[0] = expVals[sortIdx[0]];
        splineY[0] = betaGrid[sortIdx[0]];
        int sIdx = 0;
        for (int i = 1; i < betaGridSize; i++) {
            if (expVals[sortIdx[i]] != expVals[sortIdx[i - 1]]) {
                sIdx++;
                splineX[sIdx] = expVals[sortIdx[i]];
                splineY[sIdx] = betaGrid[sortIdx[i]];
            }
        }
        //double splineEMin = splineX[0];
        //double splineEMax = splineX[splineX.length - 1];
        interpolator = new PchipInterpolator(splineX, splineY);
    }

    public void BatchSample(double expectedValue, int expectedCount, int draws){
        double sampleExpectationValue = expectedValue / (expectedCount * draws);
        double beta = interpolator.interpolate(sampleExpectationValue);
        int index = Arrays.binarySearch(betaGrid, beta);
        if (index < 0) index = -index - 2;
        if (index < 0) index = 0;
        if (index > betaGridSize - 2) index = betaGridSize - 2;
        double lerpFactor = (beta - betaGrid[index]) / (betaGrid[index + 1] - betaGrid[index]);
        for (int i = 0; i < draws; i++) {
            GetValueIndex(index, lerpFactor);
            GetCount(expectedCount);
        }
    }

    private int GetValueIndex(int leftBetaIndex, double lerpFactor){
        double[] slice = valueChanceMatrix[(random.nextDouble() > lerpFactor) ? leftBetaIndex : leftBetaIndex + 1];
        int index = Arrays.binarySearch(slice, random.nextDouble());
        if (index < 0) index = -index - 1;
        if (index >= uniqueCount) index = uniqueCount - 1;
        return valueIndices[index] + ((valueMultiplicities[index] > 1) ? random.nextInt(valueMultiplicities[index]) : 0);
    }

    private int GetCount(int expectedCount){
        // If we can get beta, we have
        // pmf = e^(-bn)*(1-e^-b) / (1-e^(-b*64))
        // cmf = (e^(63 b) (e^b - e^(-b m)))/(-1 + e^(64 b))
        // <E> = sum from 0 to 63 of n e^(-bn)*(1-e^-b) / (1-e^(-b*64))
        return 1;
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