package simulation;

import aaron.TopologyForm;

/**
 * Complete configuration for a simulation trial, including derived topology parameters
 * and global constants. Contains all values needed to reproduce the trial.
 *
 * @param topologyType the network topology form
 * @param nodeCount number of nodes in the network
 * @param viewFraction fraction of neighbors visible each round
 * @param fanOut number of peers contacted per round
 * @param graphSeed seed controlling topology instantiation
 * @param simulationSeed seed controlling protocol randomness
 * @param k base connectivity parameter
 * @param m scale-free attachment parameter (equals k)
 * @param p random graph connection probability (derived: 2k / (N - 1))
 * @param beta Watts-Strogatz rewiring probability (0.0 for non-SW topologies)
 * @param maxRounds upper limit on simulation rounds
 * @param stableRounds consecutive no-progress rounds before stopping
 * @param failureProbability probability that each node is a passive Byzantine
 *        (non-forwarding) node; 0.0 means no failures
 */
public record SimulationConfig(
    TopologyForm topologyType,
    int nodeCount,
    double viewFraction,
    int fanOut,
    long graphSeed,
    long simulationSeed,
    int k,
    int m,
    double p,
    double beta,
    int maxRounds,
    int stableRounds,
    double failureProbability
) {

    /** Backward-compatible factory: no Byzantine failures. */
    public static SimulationConfig baseline(
            TopologyForm topologyType, int nodeCount, double viewFraction, int fanOut,
            long graphSeed, long simulationSeed, int k, int m, double p, double beta,
            int maxRounds, int stableRounds) {
        return new SimulationConfig(topologyType, nodeCount, viewFraction, fanOut,
            graphSeed, simulationSeed, k, m, p, beta, maxRounds, stableRounds, 0.0);
    }
}
