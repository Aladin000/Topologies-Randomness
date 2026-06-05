package experiments;

import aaron.TopologyForm;

/**
 * Defines the experimental variables for a simulation experiment.
 *
 * @param topologyType the network topology form
 * @param nodeCount number of nodes (N)
 * @param k base connectivity parameter (ring half-width, BA attachment count, ER degree driver)
 * @param viewFraction fraction of neighbors visible each round (strictly greater than 0.0, up to 1.0)
 * @param fanOut number of peers contacted per round (at least 1)
 */
public record ExperimentConfig(
    TopologyForm topologyType,
    int nodeCount,
    int k,
    double viewFraction,
    int fanOut
) {}
