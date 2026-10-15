package aevns.entropictransmutation.maxent;

import java.util.Arrays;
import java.util.SplittableRandom;

/** Builds a sampler on synthetic heavy-tailed data, checks constraints exactly and by Monte Carlo, and times sampling. */
public final class Demo {
    public static void main(String[] args) {
        int entryCount = args.length > 0 ? Integer.parseInt(args[0]) : 100_000;
        SplittableRandom dataRng = new SplittableRandom(1);
        double[] values = new double[entryCount];
        for (int i = 0; i < entryCount; i++) {
            double value = Math.exp(2.0 + 1.8 * standardNormal(dataRng));
            double roundingUnit = Math.pow(10, Math.floor(Math.log10(value)) - 2);   // 3 significant digits
            values[i] = Math.max(0.01, Math.round(value / roundingUnit) * roundingUnit);
        }
        Arrays.sort(values);
        String[] labels = new String[entryCount];
        for (int i = 0; i < entryCount; i++) labels[i] = "L" + i;

        Params params = new Params();
        long buildStartNanos = System.nanoTime();
        MaxEntSampler sampler = new MaxEntSampler(values, labels, params);
        System.out.printf("built in %.2f s, K=%d unique, memory %.1f MB%n",
                (System.nanoTime() - buildStartNanos) / 1e9, sampler.uniqueValueCount(),
                sampler.approximateMemoryBytes() / 1e6);

        for (int expectedCountIndex = 0; expectedCountIndex < 4; expectedCountIndex++) {
            double expectedCount = MaxEntSampler.SUPPORTED_EXPECTED_COUNTS[expectedCountIndex];
            double minS = sampler.minTargetS(expectedCountIndex), maxS = sampler.maxTargetS(expectedCountIndex);
            System.out.printf("n=%.2f  s range [%.4g, %.4g]  (n*vmin=%.4g, n*vmax=%.4g)%n",
                    expectedCount, minS, maxS, expectedCount * values[0], expectedCount * values[entryCount - 1]);
            double worstCountError = 0, worstSError = 0;
            for (double quantile : new double[] {0.0, 0.02, 0.1, 0.3, 0.5, 0.7, 0.9, 0.98, 1.0}) {
                double targetS = minS * Math.pow(maxS / minS, quantile);
                double[] realized = sampler.exactRealizedMoments(expectedCountIndex, targetS);
                worstCountError = Math.max(worstCountError, Math.abs(realized[0] - expectedCount) / expectedCount);
                worstSError = Math.max(worstSError, Math.abs(realized[1] - targetS) / targetS);
            }
            System.out.printf("   exact realized residuals: max rel err E[N]=%.2e  E[NV]=%.2e%n",
                    worstCountError, worstSError);
        }

        // Monte Carlo sanity check
        int monteCarloCountIndex = 1;
        double monteCarloS = Math.sqrt(sampler.minTargetS(monteCarloCountIndex) * sampler.maxTargetS(monteCarloCountIndex));
        SplittableRandom samplingRng = new SplittableRandom(7);
        int drawCount = 4_000_000;
        double countSum = 0, countValueSum = 0, countValueSquareSum = 0;
        int nullDraws = 0;
        for (int i = 0; i < drawCount; i++) {
            long bits = sampler.sampleBits(monteCarloCountIndex, monteCarloS, samplingRng);
            int count = MaxEntSampler.unpackCount(bits), labelIndex = MaxEntSampler.unpackLabelIndex(bits);
            if (count == 0) {
                nullDraws++;
                if (labelIndex != -1) throw new AssertionError();
                continue;
            }
            double countValue = count * values[labelIndex];
            countSum += count;
            countValueSum += countValue;
            countValueSquareSum += countValue * countValue;
        }
        double meanCount = countSum / drawCount, meanCountValue = countValueSum / drawCount;
        double standardError = Math.sqrt((countValueSquareSum / drawCount - meanCountValue * meanCountValue) / drawCount);
        System.out.printf("MC (n=1, s=%.4g): mean N=%.4f (target 1), mean NV=%.4g +- %.2g, P(count=0)=%.4f%n",
                monteCarloS, meanCount, meanCountValue, standardError, nullDraws / (double) drawCount);

        // timing (warm)
        int timedDraws = 20_000_000;
        long checksum = 0;
        double timingMinS = sampler.minTargetS(2), timingMaxS = sampler.maxTargetS(2);
        double[] timingTargets = new double[1024];
        for (int i = 0; i < timingTargets.length; i++)
            timingTargets[i] = timingMinS * Math.pow(timingMaxS / timingMinS, dataRng.nextDouble());
        for (int repetition = 0; repetition < 2; repetition++) {
            long timingStartNanos = System.nanoTime();
            for (int i = 0; i < timedDraws; i++) checksum += sampler.sampleBits(2, timingTargets[i & 1023], samplingRng);
            double nanosPerSample = (System.nanoTime() - timingStartNanos) / (double) timedDraws;
            System.out.printf("warm timing (n=4): %.1f ns/sample%n", nanosPerSample);
        }
        System.out.println("(checksum " + checksum + ")");
    }

    private static double standardNormal(SplittableRandom rng) {
        double u = rng.nextDouble(), v = rng.nextDouble();
        return Math.sqrt(-2 * Math.log(1 - u)) * Math.cos(2 * Math.PI * v);
    }
}
