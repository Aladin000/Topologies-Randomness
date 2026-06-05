package experiments;

import aaron.TopologyForm;
import metrics.TrialMetrics;

/**
 * Result of a single simulation trial within an experiment.
 *
 * Combines the experiment configuration, the actual seeds used (after any
 * connectivity retries), the sequential trial index, Byzantine failure metadata,
 * and the computed metrics. Each instance maps directly to one row in the CSV output.
 *
 * @param topologyType the network topology form
 * @param nodeCount number of nodes
 * @param k base connectivity parameter
 * @param viewFraction fraction of neighbors visible each round
 * @param fanOut number of peers contacted per round
 * @param baseSeed the base seed from which graphSeed and simulationSeed were derived
 * @param graphSeed actual seed that produced a connected graph (may differ from initial seed after retries)
 * @param simulationSeed seed controlling source selection and peer selection
 * @param trial sequential trial index (1-based)
 * @param failureProbability probability each node is passive Byzantine; 0.0 = baseline
 * @param failedNodeCount number of nodes classified as failed in this trial
 * @param failedFraction actual fraction of nodes that failed (failedNodeCount / nodeCount)
 * @param activeNodeCount number of active (non-failed) nodes
 * @param sourceNode the initially informed node
 * @param sourceForcedActive true if the source was in the failed set and forced active
 * @param metrics computed per-trial performance metrics
 */
public record TrialResult(
    TopologyForm topologyType,
    int nodeCount,
    int k,
    double viewFraction,
    int fanOut,
    long baseSeed,
    long graphSeed,
    long simulationSeed,
    int trial,
    double failureProbability,
    int failedNodeCount,
    double failedFraction,
    int activeNodeCount,
    int sourceNode,
    boolean sourceForcedActive,
    TrialMetrics metrics
) {

    /** Backward-compatible factory for baseline experiments (no Byzantine failures). */
    public static TrialResult baseline(
            TopologyForm topologyType, int nodeCount, int k,
            double viewFraction, int fanOut,
            long baseSeed, long graphSeed, long simulationSeed,
            int trial, int sourceNode, TrialMetrics metrics) {
        return new TrialResult(topologyType, nodeCount, k, viewFraction, fanOut,
            baseSeed, graphSeed, simulationSeed, trial,
            0.0, 0, 0.0, nodeCount, sourceNode, false, metrics);
    }
}
