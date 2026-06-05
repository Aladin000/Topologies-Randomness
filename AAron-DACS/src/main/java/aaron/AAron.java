package aaron;

/**
 * Factory for creating AAron network instances.
 * 
 * AAron is the network model. This class provides a unified way to create
 * network instances in different structural forms.
 * 
 * The seed parameter in stochastic topology generators (Random, Scale-free)
 * corresponds to the graphSeed in the experimental configuration. It controls
 * structural randomness (which edges appear) and is independent of the
 * simulationSeed that controls protocol randomness.
 * 
 * Ring-lattice networks are deterministic; the seed parameter is accepted
 * for API uniformity but ignored.
 */
public final class AAron {

    private AAron() {}

    /**
     * Creates a random network (Erdos-Renyi model).
     * 
     * @param nodeCount number of nodes
     * @param edgeProbability probability of each edge existing
     * @param seed graph seed controlling which edges appear
     * @return a new random network
     */
    public static RandomNetwork random(int nodeCount, double edgeProbability, long seed) {
        return RandomNetwork.create(nodeCount, edgeProbability, seed);
    }

    /**
     * Creates a ring-lattice network.
     * Ring-lattice is deterministic: the seed is accepted for API uniformity
     * but ignored, since the topology is fully determined by nodeCount and k.
     * 
     * @param nodeCount number of nodes
     * @param k neighbors on each side (total degree will be 2k)
     * @param seed graph seed (ignored for ring-lattice)
     * @return a new ring network
     */
    public static RingNetwork ring(int nodeCount, int k, long seed) {
        return RingNetwork.create(nodeCount, k);
    }

    /**
     * Creates a scale-free network (Barabasi-Albert model).
     * 
     * @param nodeCount number of nodes
     * @param m number of edges each new node creates
     * @param seed graph seed controlling which nodes get attached to
     * @return a new scale-free network
     */
    public static ScaleFreeNetwork scaleFree(int nodeCount, int m, long seed) {
        return ScaleFreeNetwork.create(nodeCount, m, seed);
    }

    /**
     * Creates a small-world network (Watts-Strogatz model).
     * 
     * @param nodeCount number of nodes
     * @param k neighbors on each side in the base ring (degree = 2k)
     * @param beta rewiring probability in [0, 1]
     * @param seed graph seed controlling rewiring decisions
     * @return a new small-world network
     */
    public static SmallWorldNetwork smallWorld(int nodeCount, int k, double beta, long seed) {
        return SmallWorldNetwork.create(nodeCount, k, beta, seed);
    }
}

