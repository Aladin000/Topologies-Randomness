package aaron;

/**
 * The structural forms that AAron can take.
 */
public enum TopologyForm {
    
    /**
     * A well-mixed network with random connectivity.
     * Generated using the Erdos-Renyi model.
     */
    RANDOM,

    /**
     * A structured, locality-constrained network.
     * Each node is connected to its k nearest neighbors in a ring.
     */
    RING,

    /**
     * A hub-dominated network with uneven connectivity.
     * Generated using the Barabasi-Albert preferential attachment model.
     */
    SCALE_FREE,

    /**
     * A network with high clustering and short path lengths.
     * Generated using the Watts-Strogatz rewiring model over a ring lattice.
     */
    SMALL_WORLD
}

