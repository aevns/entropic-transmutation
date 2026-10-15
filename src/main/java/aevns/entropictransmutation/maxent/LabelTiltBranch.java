package aevns.entropictransmutation.maxent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * One sign of t in the label family p(k | t) ~ W_k exp(-t x_k), where x_k are the scaled unique values.
 * Works in a bounded "gap" form with tau = |t|:
 *   logPartition(tau) = ln sum_k W_k exp(-signOfT * tau * (x_k - referenceValue))   (<= ln of total weight, bounded)
 *   meanGap(tau)      = signOfT * (E[x] - referenceValue) >= 0, decreasing, derivative = -variance
 *   variance(tau)     = Var[x]
 * referenceValue = x_min for signOfT = +1 and x_max for signOfT = -1, so every exponential is <= 1.
 */
final class LabelTiltBranch {
    final int signOfT;
    final double[] scaledValues, labelPriorWeights;
    final int uniqueCount;
    final double referenceValue, referenceWeight;
    /** Smallest tau at which the extreme value group holds at least 1 - saturationTolerance of the mass. */
    double saturationTau;

    // Adaptive dense grid in tau, sorted ascending; node 0 is tau = 0 and the last node is saturationTau.
    double[] gridTau, gridLogPartition, gridMeanGap, gridVariance;
    int gridNodeCount;
    /** Grid node indices chosen as stored label CDF rows, ascending. */
    int[] labelRowGridIndices;

    private final double[] scratchWeights;

    static final int MAX_EXTRA_GRID_NODES = 12000;
    static final double LOG_PARTITION_TOLERANCE = 1e-8;
    static final double MEAN_GAP_RELATIVE_TOLERANCE = 1e-4;

    LabelTiltBranch(int signOfT, double[] scaledValues, double[] labelPriorWeights) {
        this.signOfT = signOfT;
        this.scaledValues = scaledValues;
        this.labelPriorWeights = labelPriorWeights;
        this.uniqueCount = scaledValues.length;
        this.referenceValue = signOfT > 0 ? scaledValues[0] : scaledValues[uniqueCount - 1];
        this.referenceWeight = signOfT > 0 ? labelPriorWeights[0] : labelPriorWeights[uniqueCount - 1];
        this.scratchWeights = new double[uniqueCount];
    }

    /** Fraction of mass NOT in the extreme value group (the group that dominates as tau grows). */
    double massOutsideExtremeGroup(double t) {
        double outside = 0;
        if (signOfT > 0) {
            for (int k = 1; k < uniqueCount; k++)
                outside += labelPriorWeights[k] * Math.exp(-t * (scaledValues[k] - referenceValue));
        } else {
            for (int k = 0; k < uniqueCount - 1; k++)
                outside += labelPriorWeights[k] * Math.exp(t * (scaledValues[k] - referenceValue));
        }
        return outside / (referenceWeight + outside);
    }

    void findSaturationTau(double tolerance) {
        double upper = 1.0, lower;
        if (massOutsideExtremeGroup(upper) <= tolerance) {
            while (upper > 1e-30 && massOutsideExtremeGroup(upper / 2) <= tolerance) upper /= 2;
            lower = upper / 2;
        } else {
            lower = upper;
            upper *= 2;
            while (massOutsideExtremeGroup(upper) > tolerance) {
                lower = upper;
                upper *= 2;
                if (upper > 1e30) throw new IllegalStateException("cannot saturate extreme group");
            }
        }
        for (int iteration = 0; iteration < 60; iteration++) {
            double middle = 0.5 * (lower + upper);
            if (massOutsideExtremeGroup(middle) <= tolerance) upper = middle; else lower = middle;
        }
        saturationTau = upper;
    }

    /** Returns {tau, logPartition, meanGap, variance} evaluated exactly (O(uniqueCount)). */
    double[] evaluateNode(double tau) {
        double weightSum = 0, weightedGapSum = 0;
        for (int k = 0; k < uniqueCount; k++) {
            double gap = scaledValues[k] - referenceValue;
            double weight = labelPriorWeights[k] * Math.exp(-signOfT * tau * gap);
            scratchWeights[k] = weight;
            weightSum += weight;
            weightedGapSum += weight * gap;
        }
        double meanOffset = weightedGapSum / weightSum, varianceSum = 0;
        for (int k = 0; k < uniqueCount; k++) {
            double deviation = (scaledValues[k] - referenceValue) - meanOffset;
            varianceSum += scratchWeights[k] * deviation * deviation;
        }
        return new double[] {tau, Math.log(weightSum), signOfT * meanOffset, varianceSum / weightSum};
    }

    /** Cubic Hermite interpolation on u in [0,1]; slopes are given pre-multiplied by the interval width. */
    static double cubicHermite(double y0, double y1, double scaledSlope0, double scaledSlope1, double u) {
        double u2 = u * u, u3 = u2 * u;
        return (2 * u3 - 3 * u2 + 1) * y0 + (u3 - 2 * u2 + u) * scaledSlope0
                + (-2 * u3 + 3 * u2) * y1 + (u3 - u2) * scaledSlope1;
    }

    /** Recursively bisects [left, right] wherever the Hermite interpolant misses the exact midpoint node. */
    private void refineInterval(double[] left, double[] right, int depth, List<double[]> extraNodes, int maxExtraNodes) {
        double width = right[0] - left[0];
        double midTau = left[0] + 0.5 * width;
        if (depth >= 40 || midTau <= left[0] || midTau >= right[0] || extraNodes.size() > maxExtraNodes) return;
        double[] mid = evaluateNode(midTau);
        double interpolatedLogPartition = cubicHermite(left[1], right[1], -left[2] * width, -right[2] * width, 0.5);
        double interpolatedMeanGap = cubicHermite(left[2], right[2], -left[3] * width, -right[3] * width, 0.5);
        if (Math.abs(interpolatedLogPartition - mid[1]) > LOG_PARTITION_TOLERANCE
                || Math.abs(interpolatedMeanGap - mid[2]) > MEAN_GAP_RELATIVE_TOLERANCE * (left[2] - right[2]) + 1e-14) {
            extraNodes.add(mid);
            refineInterval(left, mid, depth + 1, extraNodes, maxExtraNodes);
            refineInterval(mid, right, depth + 1, extraNodes, maxExtraNodes);
        }
    }

    void buildAdaptiveGrid() {
        List<double[]> nodes = new ArrayList<>();
        nodes.add(evaluateNode(0));
        int initialLogSpacedNodes = 96;
        for (int i = 0; i <= initialLogSpacedNodes; i++) {
            double tau = (i == initialLogSpacedNodes)
                    ? saturationTau
                    : saturationTau * Math.pow(10, -9.0 + 9.0 * i / initialLogSpacedNodes);
            nodes.add(evaluateNode(tau));
        }
        List<double[]> extraNodes = new ArrayList<>();
        for (int i = 0; i + 1 < nodes.size(); i++)
            refineInterval(nodes.get(i), nodes.get(i + 1), 0, extraNodes, MAX_EXTRA_GRID_NODES);
        nodes.addAll(extraNodes);
        nodes.sort(Comparator.comparingDouble(node -> node[0]));
        gridNodeCount = nodes.size();
        gridTau = new double[gridNodeCount];
        gridLogPartition = new double[gridNodeCount];
        gridMeanGap = new double[gridNodeCount];
        gridVariance = new double[gridNodeCount];
        for (int i = 0; i < gridNodeCount; i++) {
            double[] node = nodes.get(i);
            gridTau[i] = node[0];
            gridLogPartition[i] = node[1];
            gridMeanGap[i] = node[2];
            gridVariance[i] = node[3];
        }
    }

    /** Chooses label CDF rows (a subset of grid nodes) equally spaced in the integral of sigma d(tau). */
    void selectLabelRowNodes(int rowCount) {
        if (rowCount >= gridNodeCount) {
            labelRowGridIndices = new int[gridNodeCount];
            for (int i = 0; i < gridNodeCount; i++) labelRowGridIndices[i] = i;
            return;
        }
        // The label family is one-parameter, so its Fisher speed along tau is just sigma(tau) = sqrt(variance).
        double[] gridSpeed = new double[gridNodeCount];
        for (int i = 0; i < gridNodeCount; i++) gridSpeed[i] = Math.sqrt(gridVariance[i]);
        double[] cumulativeArclength = FisherArclength.cumulativeArclength(gridTau, gridSpeed);
        double totalArclength = cumulativeArclength[gridNodeCount - 1];
        TreeSet<Integer> chosenNodes = new TreeSet<>();
        chosenNodes.add(0);
        chosenNodes.add(gridNodeCount - 1);
        for (int rowNumber = 1; rowNumber < rowCount - 1; rowNumber++) {
            double target = totalArclength * rowNumber / (rowCount - 1);
            chosenNodes.add(FisherArclength.nearestPositionIndex(cumulativeArclength, target));
        }
        labelRowGridIndices = new int[chosenNodes.size()];
        int position = 0;
        for (int node : chosenNodes) labelRowGridIndices[position++] = node;
    }

    /** Writes {logPartition, meanGap, variance} at tau into out (tau beyond the grid uses the last node). */
    void interpolate(double tau, double[] out) {
        if (tau >= gridTau[gridNodeCount - 1]) {
            out[0] = gridLogPartition[gridNodeCount - 1];
            out[1] = gridMeanGap[gridNodeCount - 1];
            out[2] = gridVariance[gridNodeCount - 1];
            return;
        }
        int low = 0, high = gridNodeCount - 1;
        while (high - low > 1) {
            int middle = (low + high) >>> 1;
            if (gridTau[middle] <= tau) low = middle; else high = middle;
        }
        double width = gridTau[high] - gridTau[low], u = (tau - gridTau[low]) / width;
        out[0] = cubicHermite(gridLogPartition[low], gridLogPartition[high],
                -gridMeanGap[low] * width, -gridMeanGap[high] * width, u);
        double meanGap = cubicHermite(gridMeanGap[low], gridMeanGap[high],
                -gridVariance[low] * width, -gridVariance[high] * width, u);
        out[1] = Math.min(gridMeanGap[low], Math.max(gridMeanGap[high], meanGap));
        out[2] = gridVariance[low] + u * (gridVariance[high] - gridVariance[low]);
    }
}
