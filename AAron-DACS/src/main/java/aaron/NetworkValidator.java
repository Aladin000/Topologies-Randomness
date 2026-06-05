package aaron;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates structural properties of networks.
 * 
 * Provides checks to ensure generated network instances are well-formed
 * before running experiments.
 */
public final class NetworkValidator {

    private NetworkValidator() {}

    /**
     * Result of validating a network.
     */
    public record ValidationResult(
        boolean valid,
        List<String> errors,
        NetworkStats stats
    ) {
        public static ValidationResult success(NetworkStats stats) {
            return new ValidationResult(true, List.of(), stats);
        }

        public static ValidationResult failure(List<String> errors, NetworkStats stats) {
            return new ValidationResult(false, List.copyOf(errors), stats);
        }
    }

    /**
     * Summary statistics for a network.
     */
    public record NetworkStats(
        int nodeCount,
        int edgeCount,
        int minDegree,
        int maxDegree,
        double averageDegree,
        int componentCount
    ) {}

    /**
     * Validates structural properties of a network and computes summary statistics.
     * 
     * Checks performed:
     * - No self-loops
     * - Symmetric adjacency (if i has neighbor j, then j has neighbor i)
     * - Valid node references (all neighbors are in valid range)
     * 
     * Also computes: node count, edge count, min/max/average degree, component count.
     * Does not check connectivity; use {@link #validateForSimulation} for that.
     * 
     * @param network the network to validate
     * @return validation result with errors (if any) and statistics
     */
    public static ValidationResult validate(Network network) {
        List<String> errors = new ArrayList<>();
        int nodeCount = network.nodeCount();
        
        int edgeCount = 0;
        int minDegree = Integer.MAX_VALUE;
        int maxDegree = 0;
        long degreeSum = 0;

        for (int i = 0; i < nodeCount; i++) {
            var neighbors = network.neighbors(i);
            int degree = neighbors.size();
            
            minDegree = Math.min(minDegree, degree);
            maxDegree = Math.max(maxDegree, degree);
            degreeSum += degree;
            edgeCount += degree;

            if (neighbors.contains(i)) {
                errors.add("Node " + i + " has a self-loop");
            }

            for (int neighbor : neighbors) {
                if (neighbor < 0 || neighbor >= nodeCount) {
                    errors.add("Node " + i + " has invalid neighbor " + neighbor);
                } else if (!network.neighbors(neighbor).contains(i)) {
                    errors.add("Asymmetric edge: " + i + " -> " + neighbor + " but not reverse");
                }
            }
        }

        // Each edge counted twice in degreeSum
        edgeCount /= 2;

        if (nodeCount == 0) {
            minDegree = 0;
        }

        double averageDegree = nodeCount > 0 ? (double) degreeSum / nodeCount : 0.0;
        int componentCount = countComponents(network);

        NetworkStats stats = new NetworkStats(
            nodeCount, edgeCount, minDegree, maxDegree, averageDegree, componentCount
        );

        if (errors.isEmpty()) {
            return ValidationResult.success(stats);
        } else {
            return ValidationResult.failure(errors, stats);
        }
    }

    /**
     * Counts the number of connected components in the network using BFS.
     */
    private static int countComponents(Network network) {
        int nodeCount = network.nodeCount();
        if (nodeCount == 0) return 0;

        boolean[] visited = new boolean[nodeCount];
        int components = 0;

        for (int start = 0; start < nodeCount; start++) {
            if (visited[start]) continue;
            
            components++;

            List<Integer> queue = new ArrayList<>();
            queue.add(start);
            visited[start] = true;

            int head = 0;
            while (head < queue.size()) {
                int current = queue.get(head++);
                for (int neighbor : network.neighbors(current)) {
                    if (!visited[neighbor]) {
                        visited[neighbor] = true;
                        queue.add(neighbor);
                    }
                }
            }
        }

        return components;
    }

    /**
     * Validates that a ring-lattice network has the expected structure.
     * 
     * @param network the ring network to validate
     * @param expectedK the expected k parameter
     * @return validation result
     */
    public static ValidationResult validateRing(RingNetwork network, int expectedK) {
        ValidationResult baseResult = validate(network);
        List<String> errors = new ArrayList<>(baseResult.errors());

        int expectedDegree = 2 * expectedK;
        for (int i = 0; i < network.nodeCount(); i++) {
            int degree = network.neighbors(i).size();
            if (degree != expectedDegree) {
                errors.add("Node " + i + " has degree " + degree + ", expected " + expectedDegree);
            }
        }

        if (errors.isEmpty()) {
            return ValidationResult.success(baseResult.stats());
        } else {
            return ValidationResult.failure(errors, baseResult.stats());
        }
    }

    /**
     * Checks if a network is connected (has exactly one component).
     * 
     * @param network the network to check
     * @return true if the network is connected
     */
    public static boolean isConnected(Network network) {
        return countComponents(network) == 1;
    }

    /**
     * Validates a network for use in simulation.
     * Requires the network to be connected (single component).
     * 
     * @param network the network to validate
     * @return validation result with connectivity check included
     */
    public static ValidationResult validateForSimulation(Network network) {
        ValidationResult baseResult = validate(network);
        List<String> errors = new ArrayList<>(baseResult.errors());

        if (baseResult.stats().componentCount() != 1) {
            errors.add("Network is not connected: has " + baseResult.stats().componentCount() + 
                       " components. Full dissemination is not possible.");
        }

        if (errors.isEmpty()) {
            return ValidationResult.success(baseResult.stats());
        } else {
            return ValidationResult.failure(errors, baseResult.stats());
        }
    }

    /**
     * Validates a network and throws if it is not suitable for simulation.
     * 
     * @param network the network to validate
     * @throws IllegalArgumentException if the network is invalid or not connected
     */
    public static void requireValidForSimulation(Network network) {
        ValidationResult result = validateForSimulation(network);
        if (!result.valid()) {
            throw new IllegalArgumentException("Network is not valid for simulation: " + 
                                               String.join("; ", result.errors()));
        }
    }
}

