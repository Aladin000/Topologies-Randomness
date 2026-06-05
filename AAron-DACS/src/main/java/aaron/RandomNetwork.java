package aaron;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A random network generated using the Erdos-Renyi model.
 * 
 * For n nodes and edge probability p, each possible edge (i,j) exists
 * independently with probability p. This creates a well-mixed network
 * with random connectivity.
 */
public final class RandomNetwork implements Network {

    private final int nodeCount;
    private final double edgeProbability;
    private final List<Set<Integer>> adjacencyList;
    private final int edgeCount;

    private RandomNetwork(int nodeCount, double edgeProbability, 
                          List<Set<Integer>> adjacencyList, int edgeCount) {
        this.nodeCount = nodeCount;
        this.edgeProbability = edgeProbability;
        this.adjacencyList = adjacencyList;
        this.edgeCount = edgeCount;
    }

    /**
     * Creates a random network using the Erdos-Renyi model.
     * 
     * @param nodeCount number of nodes (must be at least 2)
     * @param edgeProbability probability of each edge existing (0 to 1)
     * @param random random number generator seeded from graphSeed
     * @return a new random network
     * @throws IllegalArgumentException if parameters are invalid
     */
    static RandomNetwork create(int nodeCount, double edgeProbability, Random random) {
        if (nodeCount < 2) {
            throw new IllegalArgumentException("Node count must be at least 2");
        }
        if (edgeProbability < 0.0 || edgeProbability > 1.0) {
            throw new IllegalArgumentException("Edge probability must be between 0 and 1");
        }

        List<Set<Integer>> adjacencyList = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            adjacencyList.add(new HashSet<>());
        }

        int edgeCount = 0;

        for (int i = 0; i < nodeCount; i++) {
            for (int j = i + 1; j < nodeCount; j++) {
                if (random.nextDouble() < edgeProbability) {
                    adjacencyList.get(i).add(j);
                    adjacencyList.get(j).add(i);
                    edgeCount++;
                }
            }
        }

        List<Set<Integer>> immutableList = new ArrayList<>(nodeCount);
        for (Set<Integer> neighbors : adjacencyList) {
            immutableList.add(Collections.unmodifiableSet(neighbors));
        }

        return new RandomNetwork(nodeCount, edgeProbability, 
                                 Collections.unmodifiableList(immutableList), edgeCount);
    }

    /**
     * Creates a random network with a new Random instance seeded by the given value.
     */
    public static RandomNetwork create(int nodeCount, double edgeProbability, long seed) {
        return create(nodeCount, edgeProbability, new Random(seed));
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
     * Returns the edge probability used to construct this network.
     */
    public double edgeProbability() {
        return edgeProbability;
    }

    /**
     * Returns the average degree of nodes in the network.
     */
    public double averageDegree() {
        if (nodeCount == 0) return 0.0;
        return (2.0 * edgeCount) / nodeCount;
    }
}

