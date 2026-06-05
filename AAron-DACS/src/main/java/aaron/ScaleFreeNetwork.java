package aaron;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A scale-free network generated using the Barabasi-Albert preferential attachment model.
 * 
 * The network grows by adding nodes one at a time. Each new node connects to m 
 * existing nodes, with the probability of connecting to a node proportional to 
 * its current degree. This creates a hub-dominated network where a few nodes 
 * have many connections while most have few.
 */
public final class ScaleFreeNetwork implements Network {

    private final int nodeCount;
    private final int m;
    private final List<Set<Integer>> adjacencyList;
    private final int edgeCount;

    private ScaleFreeNetwork(int nodeCount, int m, 
                             List<Set<Integer>> adjacencyList, int edgeCount) {
        this.nodeCount = nodeCount;
        this.m = m;
        this.adjacencyList = adjacencyList;
        this.edgeCount = edgeCount;
    }

    /**
     * Creates a scale-free network using the Barabasi-Albert model.
     * 
     * @param nodeCount total number of nodes (must be greater than m)
     * @param m number of edges each new node creates (must be at least 1)
     * @param random random number generator seeded from graphSeed
     * @return a new scale-free network
     * @throws IllegalArgumentException if parameters are invalid
     */
    static ScaleFreeNetwork create(int nodeCount, int m, Random random) {
        if (m < 1) {
            throw new IllegalArgumentException("m must be at least 1");
        }
        if (nodeCount <= m) {
            throw new IllegalArgumentException("Node count must be greater than m");
        }

        List<Set<Integer>> adjacencyList = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            adjacencyList.add(new HashSet<>());
        }

        // Seed: complete graph on m+1 nodes
        for (int i = 0; i <= m; i++) {
            for (int j = i + 1; j <= m; j++) {
                adjacencyList.get(i).add(j);
                adjacencyList.get(j).add(i);
            }
        }

        // Preferential attachment via degree list: each node appears degree(i) times,
        // so uniform random index yields P(node) = degree(i) / sum(degrees).
        List<Integer> degreeList = new ArrayList<>();
        for (int i = 0; i <= m; i++) {
            int degree = adjacencyList.get(i).size();
            for (int d = 0; d < degree; d++) {
                degreeList.add(i);
            }
        }

        for (int newNode = m + 1; newNode < nodeCount; newNode++) {
            // LinkedHashSet preserves insertion order for deterministic degreeList updates
            Set<Integer> targets = new LinkedHashSet<>();

            while (targets.size() < m) {
                int index = random.nextInt(degreeList.size());
                int target = degreeList.get(index);
                targets.add(target);
            }

            for (int target : targets) {
                adjacencyList.get(newNode).add(target);
                adjacencyList.get(target).add(newNode);
                degreeList.add(newNode);
                degreeList.add(target);
            }
        }

        int edgeCount = 0;
        for (Set<Integer> neighbors : adjacencyList) {
            edgeCount += neighbors.size();
        }
        edgeCount /= 2;

        List<Set<Integer>> immutableList = new ArrayList<>(nodeCount);
        for (Set<Integer> neighbors : adjacencyList) {
            immutableList.add(Collections.unmodifiableSet(neighbors));
        }

        return new ScaleFreeNetwork(nodeCount, m, 
                                    Collections.unmodifiableList(immutableList), edgeCount);
    }

    /**
     * Creates a scale-free network with a new Random instance seeded by the given value.
     */
    public static ScaleFreeNetwork create(int nodeCount, int m, long seed) {
        return create(nodeCount, m, new Random(seed));
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
     * Returns the m parameter (edges per new node) used to construct this network.
     */
    public int m() {
        return m;
    }

    /**
     * Returns the average degree of nodes in the network.
     */
    public double averageDegree() {
        if (nodeCount == 0) return 0.0;
        return (2.0 * edgeCount) / nodeCount;
    }
}

