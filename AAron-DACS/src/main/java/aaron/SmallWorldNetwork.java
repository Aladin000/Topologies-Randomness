package aaron;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A small-world network generated using the Watts-Strogatz model.
 *
 * Construction proceeds in two phases:
 * <ol>
 *   <li>Build a ring lattice where each node connects to its 2k nearest neighbors.</li>
 *   <li>For each node i (0..N-1) and each right-side offset j (1..k), rewire the
 *       edge (i, (i+j) mod N) with probability beta to a uniformly random target
 *       that is not i and not already a neighbor of i.</li>
 * </ol>
 *
 * The resulting network retains high clustering (from the ring substrate) while
 * gaining short path lengths (from the rewired long-range edges).
 */
public final class SmallWorldNetwork implements Network {

    private final int nodeCount;
    private final int k;
    private final double beta;
    private final List<Set<Integer>> adjacencyList;
    private final int edgeCount;

    private SmallWorldNetwork(int nodeCount, int k, double beta,
                              List<Set<Integer>> adjacencyList, int edgeCount) {
        this.nodeCount = nodeCount;
        this.k = k;
        this.beta = beta;
        this.adjacencyList = adjacencyList;
        this.edgeCount = edgeCount;
    }

    /**
     * Creates a small-world network using the Watts-Strogatz model.
     *
     * @param nodeCount number of nodes (must satisfy 2k &lt; N)
     * @param k neighbors on each side in the base ring (degree = 2k)
     * @param beta rewiring probability in [0, 1]
     * @param seed random seed for rewiring decisions and target selection
     * @return a new small-world network
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static SmallWorldNetwork create(int nodeCount, int k, double beta, long seed) {
        return create(nodeCount, k, beta, new Random(seed));
    }

    static SmallWorldNetwork create(int nodeCount, int k, double beta, Random random) {
        if (nodeCount < 3) {
            throw new IllegalArgumentException("Node count must be at least 3");
        }
        if (k < 1) {
            throw new IllegalArgumentException("k must be at least 1");
        }
        if (2 * k >= nodeCount) {
            throw new IllegalArgumentException("k is too large for the given node count (2k must be < N)");
        }
        if (beta < 0.0 || beta > 1.0) {
            throw new IllegalArgumentException("beta must be in [0.0, 1.0]");
        }

        // Phase 1: ring lattice
        List<Set<Integer>> adj = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            adj.add(new HashSet<>());
        }
        for (int i = 0; i < nodeCount; i++) {
            for (int offset = 1; offset <= k; offset++) {
                int neighbor = Math.floorMod(i + offset, nodeCount);
                adj.get(i).add(neighbor);
                adj.get(neighbor).add(i);
            }
        }

        // Phase 2: rewire right-side edges with probability beta
        for (int i = 0; i < nodeCount; i++) {
            for (int offset = 1; offset <= k; offset++) {
                if (random.nextDouble() < beta) {
                    int oldTarget = Math.floorMod(i + offset, nodeCount);

                    if (!adj.get(i).contains(oldTarget)) {
                        continue;
                    }

                    int newTarget = pickRandomTarget(i, adj.get(i), nodeCount, random);
                    if (newTarget == -1) {
                        continue;
                    }

                    adj.get(i).remove(oldTarget);
                    adj.get(oldTarget).remove(i);
                    adj.get(i).add(newTarget);
                    adj.get(newTarget).add(i);
                }
            }
        }

        int edgeCount = 0;
        List<Set<Integer>> immutableList = new ArrayList<>(nodeCount);
        for (Set<Integer> neighbors : adj) {
            edgeCount += neighbors.size();
            immutableList.add(Collections.unmodifiableSet(neighbors));
        }
        edgeCount /= 2;

        return new SmallWorldNetwork(nodeCount, k, beta,
            Collections.unmodifiableList(immutableList), edgeCount);
    }

    /**
     * Picks a uniformly random node that is not {@code exclude} and not in {@code currentNeighbors}.
     * Returns -1 if no valid target exists.
     */
    private static int pickRandomTarget(int exclude, Set<Integer> currentNeighbors,
                                        int nodeCount, Random random) {
        int available = nodeCount - 1 - currentNeighbors.size();
        if (available <= 0) {
            return -1;
        }

        // Rejection sampling with a 3*N attempt cap as a safety bound when the
        // neighbour set is dense and valid targets are scarce.
        int maxAttempts = 3 * nodeCount;
        int attempts = 0;
        while (attempts < maxAttempts) {
            int candidate = random.nextInt(nodeCount);
            if (candidate != exclude && !currentNeighbors.contains(candidate)) {
                return candidate;
            }
            attempts++;
        }
        return -1;
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

    /** Returns the k parameter (ring half-width). */
    public int k() {
        return k;
    }

    /** Returns the beta (rewiring probability) used to construct this network. */
    public double beta() {
        return beta;
    }

    /** Returns the average degree of nodes in the network. */
    public double averageDegree() {
        if (nodeCount == 0) return 0.0;
        return (2.0 * edgeCount) / nodeCount;
    }
}
