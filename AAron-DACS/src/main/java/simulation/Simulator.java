package simulation;

import aaron.AAron;
import aaron.Network;
import aaron.TopologyForm;
import peerselection.PeerSelector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Executes gossip dissemination trials on AAron networks.
 *
 * The {@link #simulate} method is a deterministic pure function: no global state,
 * no hidden randomness, no side effects. Given identical inputs, it produces
 * identical output.
 *
 * Topology parameters are derived from the base connectivity parameter k:
 * <ul>
 *   <li>Ring: neighborhood size = k, average degree = 2k</li>
 *   <li>Scale-free: attachment parameter m = k, average degree ≈ 2k</li>
 *   <li>Random: connection probability p = 2k / (N - 1), average degree ≈ 2k</li>
 *   <li>Small-world: base ring with k, rewired with probability {@link #BETA}</li>
 * </ul>
 */
public final class Simulator {

    /** Default base connectivity parameter. */
    public static final int DEFAULT_K = 3;

    /** Watts-Strogatz rewiring probability for small-world networks. */
    public static final double BETA = 0.1;

    /** Upper limit on simulation rounds (safety bound). */
    public static final int MAX_ROUNDS = 1000;

    /** Consecutive rounds without new infections before stopping. */
    public static final int STABLE_ROUNDS = 3;

    private Simulator() {}

    /**
     * Seed mask XOR'd with simulationSeed to derive the Byzantine failure RNG.
     * Keeps the failure stream independent of the main simulation RNG.
     */
    private static final long BYZANTINE_SEED_MASK = 0x425A_414E_5449_4E45L; // "BZANTINE" in ASCII

    /**
     * Runs a single gossip dissemination trial with optional Byzantine failures.
     *
     * @param topologyType the network topology form
     * @param nodeCount number of nodes (must satisfy topology-specific minimums)
     * @param k base connectivity parameter (ring half-width, BA m, ER degree driver)
     * @param viewFraction fraction of neighbors visible each round (strictly greater than 0.0, up to 1.0)
     * @param fanOut number of peers contacted per round (at least 1)
     * @param graphSeed seed controlling topology instantiation randomness
     * @param simulationSeed seed controlling source selection and peer selection
     * @param failureProbability probability each node is a passive Byzantine (non-forwarding) node; 0.0 = none
     * @return the complete simulation result
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static SimulationResult simulate(
            TopologyForm topologyType,
            int nodeCount,
            int k,
            double viewFraction,
            int fanOut,
            long graphSeed,
            long simulationSeed,
            double failureProbability) {

        validateParameters(topologyType, nodeCount, k, viewFraction, fanOut);
        if (failureProbability < 0.0 || failureProbability > 1.0) {
            throw new IllegalArgumentException("failureProbability must be in [0.0, 1.0]");
        }

        int m = k;
        double p = (2.0 * k) / (nodeCount - 1);

        Network network = buildNetwork(topologyType, nodeCount, k, m, p, graphSeed);

        double beta = (topologyType == TopologyForm.SMALL_WORLD) ? BETA : 0.0;

        SimulationConfig config = new SimulationConfig(
            topologyType, nodeCount, viewFraction, fanOut,
            graphSeed, simulationSeed, k, m, p, beta, MAX_ROUNDS, STABLE_ROUNDS,
            failureProbability
        );

        Random simRng = new Random(simulationSeed);
        int sourceNode = simRng.nextInt(nodeCount);

        Set<Integer> failedNodes;
        boolean sourceForcedActive = false;

        if (failureProbability > 0.0) {
            Random failRng = new Random(simulationSeed ^ BYZANTINE_SEED_MASK);
            failedNodes = new LinkedHashSet<>();
            for (int i = 0; i < nodeCount; i++) {
                if (failRng.nextDouble() < failureProbability) {
                    failedNodes.add(i);
                }
            }
            if (failedNodes.contains(sourceNode)) {
                failedNodes.remove(sourceNode);
                sourceForcedActive = true;
            }
            failedNodes = Set.copyOf(failedNodes);
        } else {
            failedNodes = Set.of();
        }

        List<RoundResult> rounds = executeRounds(
            network, sourceNode, nodeCount, viewFraction, fanOut, simRng, failedNodes
        );

        return new SimulationResult(config, network, sourceNode,
            failedNodes, sourceForcedActive, List.copyOf(rounds));
    }

    /**
     * Runs a single gossip dissemination trial (no Byzantine failures).
     * Backward-compatible entry point: delegates with failureProbability = 0.0.
     */
    public static SimulationResult simulate(
            TopologyForm topologyType,
            int nodeCount,
            int k,
            double viewFraction,
            int fanOut,
            long graphSeed,
            long simulationSeed) {

        return simulate(topologyType, nodeCount, k, viewFraction, fanOut,
            graphSeed, simulationSeed, 0.0);
    }

    /** Convenience overload using {@link #DEFAULT_K}. */
    public static SimulationResult simulate(
            TopologyForm topologyType,
            int nodeCount,
            double viewFraction,
            int fanOut,
            long graphSeed,
            long simulationSeed) {

        return simulate(topologyType, nodeCount, DEFAULT_K, viewFraction, fanOut, graphSeed, simulationSeed);
    }

    /**
     * Validates all simulation parameters, throwing {@link IllegalArgumentException}
     * with a descriptive message if any constraint is violated.
     */
    public static void validateParameters(
            TopologyForm topologyType, int nodeCount, int k, double viewFraction, int fanOut) {

        if (k < 1) {
            throw new IllegalArgumentException("k must be at least 1");
        }
        if (viewFraction <= 0.0 || viewFraction > 1.0) {
            throw new IllegalArgumentException("viewFraction must be in (0.0, 1.0]");
        }
        if (fanOut < 1) {
            throw new IllegalArgumentException("fanOut must be at least 1");
        }

        int minNodes = switch (topologyType) {
            case RANDOM, RING, SMALL_WORLD -> 2 * k + 1;
            case SCALE_FREE -> k + 1;
        };

        if (nodeCount < minNodes) {
            throw new IllegalArgumentException(
                "Node count must be at least " + minNodes + " for " + topologyType + " with k=" + k);
        }
    }

    private static Network buildNetwork(
            TopologyForm topologyType, int nodeCount, int k, int m, double p, long graphSeed) {

        return switch (topologyType) {
            case RANDOM -> AAron.random(nodeCount, p, graphSeed);
            case RING -> AAron.ring(nodeCount, k, graphSeed);
            case SCALE_FREE -> AAron.scaleFree(nodeCount, m, graphSeed);
            case SMALL_WORLD -> AAron.smallWorld(nodeCount, k, BETA, graphSeed);
        };
    }

    /**
     * Executes the synchronous gossip rounds until a stopping condition is met.
     *
     * Stopping conditions (any terminates):
     * 1. Fully informed: all nodes are informed
     * 2. Max rounds: MAX_ROUNDS reached
     * 3. Stalled: no new infections for STABLE_ROUNDS consecutive rounds
     *
     * Byzantine / failed nodes (in {@code failedNodes}) remain in the network
     * and can become informed, but never act as senders.
     */
    private static List<RoundResult> executeRounds(
            Network network,
            int sourceNode,
            int nodeCount,
            double viewFraction,
            int fanOut,
            Random simRng,
            Set<Integer> failedNodes) {

        Set<Integer> informed = new HashSet<>();
        informed.add(sourceNode);

        List<RoundResult> rounds = new ArrayList<>();
        int consecutiveStable = 0;

        for (int round = 1; round <= MAX_ROUNDS; round++) {

            // Senders: informed active nodes (exclude failed), sorted for deterministic RNG consumption
            List<Integer> senders = new ArrayList<>(informed);
            if (!failedNodes.isEmpty()) {
                senders.removeAll(failedNodes);
            }
            Collections.sort(senders);

            List<Message> messages = new ArrayList<>();
            Set<Integer> newlyInformedThisRound = new HashSet<>();

            for (int sender : senders) {
                List<Integer> peers = PeerSelector.selectPeers(
                    network, sender, viewFraction, fanOut, simRng
                );

                for (int receiver : peers) {
                    boolean isNew = !informed.contains(receiver)
                                    && !newlyInformedThisRound.contains(receiver);
                    messages.add(new Message(sender, receiver, isNew));
                    if (isNew) {
                        newlyInformedThisRound.add(receiver);
                    }
                }
            }

            // Apply infections at end of round (synchronous semantics)
            informed.addAll(newlyInformedThisRound);

            // Sort newly informed for deterministic output ordering
            List<Integer> newlyInformedSorted = new ArrayList<>(newlyInformedThisRound);
            Collections.sort(newlyInformedSorted);

            rounds.add(new RoundResult(
                round,
                List.copyOf(messages),
                List.copyOf(newlyInformedSorted),
                informed.size()
            ));

            if (informed.size() == nodeCount) {
                break;
            }

            if (newlyInformedThisRound.isEmpty()) {
                consecutiveStable++;
                if (consecutiveStable >= STABLE_ROUNDS) {
                    break;
                }
            } else {
                consecutiveStable = 0;
            }
        }

        return rounds;
    }
}
