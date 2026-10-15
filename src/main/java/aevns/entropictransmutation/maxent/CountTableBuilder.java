package aevns.entropictransmutation.maxent;

import java.util.Arrays;

/** Builds the {@link CountTable} for one expected count n. All work here is offline. */
final class CountTableBuilder {
    private CountTableBuilder() {}

    static CountTable build(double expectedCount, LabelFamily labelFamily, double[] logCountPrior, Params params) {
        CountDistributionSolver solver = new CountDistributionSolver(labelFamily, logCountPrior);
        double[] rowBetas = placeRowsByFisherArclength(expectedCount, labelFamily, solver, params);
        int rowCount = rowBetas.length;

        double[] rowScaledS = new double[rowCount];
        int[][] rowEntries = new int[rowCount][];
        int[] aliasThresholds = new int[CountRange.STATE_COUNT], aliasCounts = new int[CountRange.STATE_COUNT];
        for (int row = 0; row < rowCount; row++) {
            solver.configureForBeta(rowBetas[row]);
            solver.solveLambdaForExpectedCount(expectedCount);
            AliasMethod.buildTables(solver.countProbabilities, aliasThresholds, aliasCounts);
            // Use the probabilities the quantized alias tables actually realize, so each row's s is exact.
            double[] realizedProbabilities = new double[CountRange.STATE_COUNT];
            for (int column = 0; column < CountRange.STATE_COUNT; column++)
                AliasMethod.addColumnMass(realizedProbabilities, column, aliasThresholds[column], aliasCounts[column]);
            double scaledS = 0;
            for (int count = 1; count < CountRange.STATE_COUNT; count++)
                scaledS += realizedProbabilities[count] * count * solver.meanScaledValueGivenCount[count];
            rowScaledS[row] = scaledS;
            rowEntries[row] = CountTable.packRow(aliasThresholds, aliasCounts,
                    solver.labelRowIndex, solver.labelRowMixThreshold);
        }

        Integer[] rowOrder = new Integer[rowCount];
        for (int row = 0; row < rowCount; row++) rowOrder[row] = row;
        Arrays.sort(rowOrder, (a, b) -> Double.compare(rowScaledS[a], rowScaledS[b]));
        double[] sortedScaledS = new double[rowCount];
        int[][] sortedRowEntries = new int[rowCount][];
        for (int row = 0; row < rowCount; row++) {
            sortedScaledS[row] = rowScaledS[rowOrder[row]];
            sortedRowEntries[row] = rowEntries[rowOrder[row]];
        }
        return new CountTable(sortedScaledS, sortedRowEntries);
    }

    /**
     * Chooses the betas of the stored rows, equally spaced in Fisher-Rao arclength along the fixed-E[count]
     * curve. The curve is first sampled at tanh-warped candidate betas spanning [minBeta, maxBeta], and the
     * arclength is integrated over those samples.
     */
    private static double[] placeRowsByFisherArclength(double expectedCount, LabelFamily labelFamily,
                                                       CountDistributionSolver solver, Params params) {
        int candidatesPerSign = params.betaCandidatesPerSign;
        int candidateCount = 2 * candidatesPerSign + 1;
        double[] candidateBetas = new double[candidateCount];
        double[] candidateSpeeds = new double[candidateCount];
        double tanhSharpness = 2.0, tanhNormalizer = Math.tanh(tanhSharpness);
        for (int i = 0; i <= candidatesPerSign; i++) {
            double normalizedMagnitude = Math.tanh(tanhSharpness * i / (double) candidatesPerSign) / tanhNormalizer;
            candidateBetas[candidatesPerSign + i] = labelFamily.maxBeta() * normalizedMagnitude;
            candidateBetas[candidatesPerSign - i] = labelFamily.minBeta() * normalizedMagnitude;
        }
        for (int i = 0; i < candidateCount; i++) {
            solver.configureForBeta(candidateBetas[i]);
            solver.solveLambdaForExpectedCount(expectedCount);
            candidateSpeeds[i] = solver.fisherSpeedWithRespectToBeta();
        }
        double[] cumulativeArclength = FisherArclength.cumulativeArclength(candidateBetas, candidateSpeeds);
        double totalArclength = cumulativeArclength[candidateCount - 1];
        int rowCount = params.targetRowsPerExpectedCount;
        double[] rowBetas = new double[rowCount];
        for (int row = 0; row < rowCount; row++) {
            double targetArclength = totalArclength * row / (rowCount - 1);
            rowBetas[row] = FisherArclength.positionAtArclength(candidateBetas, cumulativeArclength, targetArclength);
        }
        rowBetas[0] = candidateBetas[0];
        rowBetas[rowCount - 1] = candidateBetas[candidateCount - 1];
        return rowBetas;
    }
}
