package experiments;

import aaron.AAron;
import aaron.Network;
import aaron.NetworkValidator;
import aaron.TopologyForm;
import metrics.MetricsCalculator;
import metrics.TrialMetrics;
import simulation.SimulationResult;
import simulation.Simulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Coordinates systematic experiment execution with Monte Carlo replication.
 *
 * For each experiment configuration, runs G graph instances x S trials per graph
 * instance = T total trials. Seeds are generated deterministically from a base seed.
 *
 * Connectivity enforcement: before running trials on a graph instance, the graph
 * is built and checked for connectivity. If disconnected, the graphSeed is
 * incremented until a connected graph is found (bounded retries). The actual
 * seed that produced the connected graph is logged in the output.
 */
public final class ExperimentRunner {

    public static final int DEFAULT_MAX_RETRIES = 100;

    private ExperimentRunner() {}

    /**
     * Runs a complete experiment: G graph instances x S trials per graph = T total trials.
     *
     * All seeds are generated upfront from baseSeed for determinism: the seed
     * sequence is independent of retry outcomes during connectivity enforcement.
     *
     * @param config experiment configuration (topology, N, viewFraction, fanOut)
     * @param graphInstances number of distinct graph instances (G)
     * @param trialsPerGraph number of trials per graph instance (S)
     * @param baseSeed base seed for deterministic generation of graphSeeds and simulationSeeds
     * @param maxRetries maximum connectivity retries per graph instance
     * @param failureProbability probability each node is passive Byzantine; 0.0 = baseline
     * @return list of T = G x S trial results, ordered by (graph instance, trial within graph)
     * @throws IllegalArgumentException if graphInstances &lt; 1, trialsPerGraph &lt; 1, or maxRetries &lt; 0
     * @throws IllegalStateException if a connected graph cannot be found within maxRetries
     */
    public static List<TrialResult> run(
            ExperimentConfig config,
            int graphInstances,
            int trialsPerGraph,
            long baseSeed,
            int maxRetries,
            double failureProbability) {

        Simulator.validateParameters(
            config.topologyType(), config.nodeCount(), config.k(),
            config.viewFraction(), config.fanOut());

        if (graphInstances < 1) {
            throw new IllegalArgumentException("graphInstances must be at least 1");
        }
        if (trialsPerGraph < 1) {
            throw new IllegalArgumentException("trialsPerGraph must be at least 1");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be non-negative");
        }

        Random seedRng = new Random(baseSeed);

        long[] graphSeeds = new long[graphInstances];
        for (int g = 0; g < graphInstances; g++) {
            graphSeeds[g] = seedRng.nextLong();
        }

        long[][] simSeeds = new long[graphInstances][trialsPerGraph];
        for (int g = 0; g < graphInstances; g++) {
            for (int s = 0; s < trialsPerGraph; s++) {
                simSeeds[g][s] = seedRng.nextLong();
            }
        }

        List<TrialResult> results = new ArrayList<>();
        int trialIndex = 1;

        for (int g = 0; g < graphInstances; g++) {
            long connectedSeed = findConnectedSeed(
                config.topologyType(), config.nodeCount(), config.k(), graphSeeds[g], maxRetries
            );

            for (int s = 0; s < trialsPerGraph; s++) {
                SimulationResult simResult = Simulator.simulate(
                    config.topologyType(), config.nodeCount(), config.k(),
                    config.viewFraction(), config.fanOut(),
                    connectedSeed, simSeeds[g][s], failureProbability
                );

                TrialMetrics metrics = MetricsCalculator.compute(simResult);
                int failedCount = simResult.failedNodes().size();

                results.add(new TrialResult(
                    config.topologyType(), config.nodeCount(), config.k(),
                    config.viewFraction(), config.fanOut(),
                    baseSeed, connectedSeed, simSeeds[g][s],
                    trialIndex, failureProbability, failedCount,
                    (double) failedCount / config.nodeCount(),
                    config.nodeCount() - failedCount,
                    simResult.sourceNode(), simResult.sourceForcedActive(),
                    metrics
                ));

                trialIndex++;
            }
        }

        return List.copyOf(results);
    }

    /**
     * Runs a complete experiment with no Byzantine failures (backward-compatible).
     */
    public static List<TrialResult> run(
            ExperimentConfig config,
            int graphInstances,
            int trialsPerGraph,
            long baseSeed,
            int maxRetries) {

        return run(config, graphInstances, trialsPerGraph, baseSeed, maxRetries, 0.0);
    }

    /**
     * Runs a single simulation trial with connectivity enforcement and optional
     * Byzantine failures.
     *
     * @param topologyType the network topology form
     * @param nodeCount number of nodes
     * @param k base connectivity parameter
     * @param viewFraction fraction of neighbors visible each round
     * @param fanOut number of peers contacted per round
     * @param graphSeed starting seed for topology generation
     * @param simulationSeed seed for protocol randomness
     * @param failureProbability probability each node is passive Byzantine; 0.0 = none
     * @return the complete simulation result
     */
    public static SimulationResult runSingleTrial(
            TopologyForm topologyType,
            int nodeCount,
            int k,
            double viewFraction,
            int fanOut,
            long graphSeed,
            long simulationSeed,
            double failureProbability) {

        Simulator.validateParameters(topologyType, nodeCount, k, viewFraction, fanOut);

        long connectedSeed = findConnectedSeed(
            topologyType, nodeCount, k, graphSeed, DEFAULT_MAX_RETRIES
        );
        return Simulator.simulate(
            topologyType, nodeCount, k, viewFraction, fanOut,
            connectedSeed, simulationSeed, failureProbability
        );
    }

    /**
     * Runs a single simulation trial with no Byzantine failures (backward-compatible).
     */
    public static SimulationResult runSingleTrial(
            TopologyForm topologyType,
            int nodeCount,
            int k,
            double viewFraction,
            int fanOut,
            long graphSeed,
            long simulationSeed) {

        return runSingleTrial(topologyType, nodeCount, k, viewFraction, fanOut,
            graphSeed, simulationSeed, 0.0);
    }

    /**
     * Convenience overload using {@link #DEFAULT_MAX_RETRIES} and no Byzantine failures.
     */
    public static List<TrialResult> run(
            ExperimentConfig config,
            int graphInstances,
            int trialsPerGraph,
            long baseSeed) {

        return run(config, graphInstances, trialsPerGraph, baseSeed, DEFAULT_MAX_RETRIES);
    }

    /**
     * Convenience overload using {@link #DEFAULT_MAX_RETRIES} with Byzantine failures.
     */
    public static List<TrialResult> run(
            ExperimentConfig config,
            int graphInstances,
            int trialsPerGraph,
            long baseSeed,
            double failureProbability) {

        return run(config, graphInstances, trialsPerGraph, baseSeed, DEFAULT_MAX_RETRIES, failureProbability);
    }

    /**
     * Finds a graphSeed that produces a connected network, starting from startSeed
     * and incrementing on each retry.
     *
     * The retry is deterministic: the same startSeed always produces the same
     * sequence of attempts and the same final connected graph.
     *
     * @return the first seed (startSeed + offset) that produces a connected graph
     * @throws IllegalStateException if no connected graph is found within maxRetries + 1 attempts
     */
    static long findConnectedSeed(
            TopologyForm topologyType,
            int nodeCount,
            int k,
            long startSeed,
            int maxRetries) {

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            long seed = startSeed + attempt;

            Network network = switch (topologyType) {
                case RANDOM -> AAron.random(nodeCount, (2.0 * k) / (nodeCount - 1), seed);
                case RING -> AAron.ring(nodeCount, k, seed);
                case SCALE_FREE -> AAron.scaleFree(nodeCount, k, seed);
                case SMALL_WORLD -> AAron.smallWorld(nodeCount, k, Simulator.BETA, seed);
            };

            if (NetworkValidator.isConnected(network)) {
                return seed;
            }
        }

        throw new IllegalStateException(
            "Could not find connected " + topologyType + " graph with N=" + nodeCount +
            " after " + (maxRetries + 1) + " attempts starting from seed " + startSeed +
            ". Parameters may make connectivity too unlikely (k=" + k + ")."
        );
    }
}
