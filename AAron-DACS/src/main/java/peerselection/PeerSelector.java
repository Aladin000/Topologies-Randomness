package peerselection;

import aaron.Network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Selects communication peers for gossip rounds using per-round view sampling.
 *
 * Each round, a node's peer selection happens in two steps:
 * 1. View sampling: a random subset of neighbors becomes the view
 * 2. Peer selection: peers are chosen uniformly at random from the view, without replacement
 *
 * The view changes each round, modeling variable connectivity and partial visibility.
 */
public final class PeerSelector {

    private PeerSelector() {}

    /**
     * Computes the view size for a node with the given degree.
     *
     * @param degree number of neighbors (must be at least 1)
     * @param viewFraction fraction of neighbors in view (strictly greater than 0.0, up to 1.0)
     * @return the view size, always at least 1
     */
    public static int computeViewSize(int degree, double viewFraction) {
        if (degree < 1) {
            throw new IllegalArgumentException("Degree must be at least 1");
        }
        if (viewFraction <= 0.0 || viewFraction > 1.0) {
            throw new IllegalArgumentException("viewFraction must be in (0.0, 1.0]");
        }
        return Math.max(1, (int) Math.round(viewFraction * degree));
    }

    /**
     * Selects peers for a node to contact in a gossip round.
     *
     * @param neighbors the node's neighbor set from the network topology
     * @param viewFraction fraction of neighbors in view (strictly greater than 0.0, up to 1.0)
     * @param fanOut desired number of peers to contact (at least 1)
     * @param random RNG controlled by simulationSeed
     * @return the selected peers; may be fewer than fanOut if view is smaller
     */
    public static List<Integer> selectPeers(Set<Integer> neighbors,
                                            double viewFraction,
                                            int fanOut,
                                            Random random) {
        if (viewFraction <= 0.0 || viewFraction > 1.0) {
            throw new IllegalArgumentException("viewFraction must be in (0.0, 1.0]");
        }
        if (fanOut < 1) {
            throw new IllegalArgumentException("fanOut must be at least 1");
        }
        if (neighbors.isEmpty()) {
            return List.of();
        }

        int viewSize = computeViewSize(neighbors.size(), viewFraction);
        int effectiveFanOut = Math.min(fanOut, viewSize);

        // Sort neighbors for deterministic ordering before random sampling
        List<Integer> sorted = new ArrayList<>(neighbors);
        Collections.sort(sorted);

        List<Integer> view = sampleWithoutReplacement(sorted, viewSize, random);

        List<Integer> sortedView = new ArrayList<>(view);
        Collections.sort(sortedView);
        return sampleWithoutReplacement(sortedView, effectiveFanOut, random);
    }

    /**
     * Convenience method that extracts neighbors from the network before selecting peers.
     *
     * @param network the network topology
     * @param node the node selecting peers
     * @param viewFraction fraction of neighbors in view (strictly greater than 0.0, up to 1.0)
     * @param fanOut desired number of peers to contact (at least 1)
     * @param random RNG controlled by simulationSeed
     * @return the selected peers
     */
    public static List<Integer> selectPeers(Network network,
                                            int node,
                                            double viewFraction,
                                            int fanOut,
                                            Random random) {
        return selectPeers(network.neighbors(node), viewFraction, fanOut, random);
    }

    /**
     * Samples k elements uniformly at random without replacement from the given pool.
     * Uses a partial Fisher-Yates shuffle.
     * The pool must be in deterministic (sorted) order for reproducible results.
     */
    private static List<Integer> sampleWithoutReplacement(List<Integer> pool, int k, Random random) {
        List<Integer> working = new ArrayList<>(pool);
        int n = working.size();
        k = Math.min(k, n);

        for (int i = 0; i < k; i++) {
            int j = i + random.nextInt(n - i);
            Integer temp = working.get(i);
            working.set(i, working.get(j));
            working.set(j, temp);
        }

        return List.copyOf(working.subList(0, k));
    }
}
