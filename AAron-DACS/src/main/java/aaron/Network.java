package aaron;

import java.util.Set;

/**
 * Represents an AAron network instance.
 * 
 * A network consists of nodes (identified by integers from 0 to nodeCount-1)
 * connected by undirected edges. Each implementation corresponds to a different
 * structural form of AAron.
 */
public interface Network {

    /**
     * Returns the number of nodes in the network.
     */
    int nodeCount();

    /**
     * Returns the number of edges in the network.
     */
    int edgeCount();

    /**
     * Returns the set of neighbors for the given node.
     * 
     * @param node a node identifier (0 to nodeCount-1)
     * @return the set of node identifiers that are neighbors of the given node
     * @throws IllegalArgumentException if node is out of range
     */
    Set<Integer> neighbors(int node);

    /**
     * Returns all edges in the network.
     * Each edge appears once (not duplicated for both directions).
     */
    Set<Edge> edges();
}

