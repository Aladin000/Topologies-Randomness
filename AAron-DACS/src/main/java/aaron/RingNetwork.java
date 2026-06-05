package aaron;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A ring-lattice network where each node is connected to its k nearest 
 * neighbors on each side in a ring arrangement.
 * 
 * For n nodes and parameter k, each node i is connected to nodes
 * (i-k), (i-k+1), ..., (i-1), (i+1), ..., (i+k) modulo n.
 * This gives each node a degree of 2k.
 */
public final class RingNetwork implements Network {

    private final int nodeCount;
    private final int k;
    private final List<Set<Integer>> adjacencyList;
    private final int edgeCount;

    private RingNetwork(int nodeCount, int k, List<Set<Integer>> adjacencyList, int edgeCount) {
        this.nodeCount = nodeCount;
        this.k = k;
        this.adjacencyList = adjacencyList;
        this.edgeCount = edgeCount;
    }

    /**
     * Creates a ring-lattice network.
     * 
     * @param nodeCount number of nodes (must be at least 3)
     * @param k number of neighbors on each side (degree will be 2k)
     * @return a new ring-lattice network
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static RingNetwork create(int nodeCount, int k) {
        if (nodeCount < 3) {
            throw new IllegalArgumentException("Node count must be at least 3");
        }
        if (k < 1) {
            throw new IllegalArgumentException("k must be at least 1");
        }
        if (2 * k >= nodeCount) {
            throw new IllegalArgumentException("k is too large for the given node count");
        }

        List<Set<Integer>> adjacencyList = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            adjacencyList.add(new HashSet<>());
        }

        int edgeCount = 0;
        for (int i = 0; i < nodeCount; i++) {
            for (int offset = 1; offset <= k; offset++) {
                int neighbor = Math.floorMod(i + offset, nodeCount);
                if (adjacencyList.get(i).add(neighbor)) {
                    adjacencyList.get(neighbor).add(i);
                    edgeCount++;
                }
            }
        }

        List<Set<Integer>> immutableList = new ArrayList<>(nodeCount);
        for (Set<Integer> neighbors : adjacencyList) {
            immutableList.add(Collections.unmodifiableSet(neighbors));
        }

        return new RingNetwork(nodeCount, k, Collections.unmodifiableList(immutableList), edgeCount);
    }

    @Override
    public int nodeCount() {
        return nodeCount;
    }

    @Override
    public int edgeCount() {
        return edgeCount;
    }

    @Override
    public Set<Integer> neighbors(int node) {
        if (node < 0 || node >= nodeCount) {
            throw new IllegalArgumentException("Node " + node + " is out of range [0, " + nodeCount + ")");
        }
        return adjacencyList.get(node);
    }

    @Override
    public Set<Edge> edges() {
        Set<Edge> edges = new HashSet<>();
        for (int i = 0; i < nodeCount; i++) {
            for (int neighbor : adjacencyList.get(i)) {
                if (i < neighbor) {
                    edges.add(new Edge(i, neighbor));
                }
            }
        }
        return Collections.unmodifiableSet(edges);
    }

    /**
     * Returns the k parameter used to construct this network.
     * Each node has 2k neighbors.
     */
    public int k() {
        return k;
    }

    /**
     * Returns the degree of each node (2k for a ring-lattice).
     */
    public int degree() {
        return 2 * k;
    }

    /**
     * Returns the average degree of nodes in the network.
     */
    public double averageDegree() {
        if (nodeCount == 0) return 0.0;
        return (2.0 * edgeCount) / nodeCount;
    }
}

