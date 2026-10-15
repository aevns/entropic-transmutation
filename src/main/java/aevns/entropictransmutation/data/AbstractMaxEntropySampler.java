package aevns.entropictransmutation.data;

import java.util.Arrays;

public abstract class AbstractMaxEntropySampler {
    protected final double[] betaGrid;
    protected final int betaGridSize;
    protected final AbstractMaxEntropySampler.PchipInterpolator interpolator;

    public AbstractMaxEntropySampler(int resolution, int randomSeed){
        betaGridSize = resolution;
    }

    protected static class PchipInterpolator {
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