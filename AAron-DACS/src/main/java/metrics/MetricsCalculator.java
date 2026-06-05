package metrics;

import simulation.RoundResult;
import simulation.SimulationResult;

import java.util.List;

/**
 * Computes per-trial performance metrics from simulation results.
 *
 * This module operates purely on simulation output data.
 * It does not interact with networks or peer selection.
 */
public final class MetricsCalculator {

    private MetricsCalculator() {}

    /**
     * Computes all per-trial metrics from a simulation result.
     *
     * @param result the complete simulation output
     * @return the computed per-trial metrics
     */
    public static TrialMetrics compute(SimulationResult result) {
        int nodeCount = result.config().nodeCount();
        List<RoundResult> rounds = result.rounds();

        int tEnd = rounds.size();

        int omega = 0;
        for (RoundResult round : rounds) {
            omega += round.messageCount();
        }

        double messageComplexity = (double) omega / (nodeCount - 1);

        int finalInformed = rounds.isEmpty() ? 1 : rounds.getLast().totalInformed();
        double alpha = (double) finalInformed / nodeCount;

        int latency50 = computeLatency(rounds, nodeCount, 0.5);
        int latency90 = computeLatency(rounds, nodeCount, 0.9);
        int latency100 = computeLatency(rounds, nodeCount, 1.0);

        double effectualFanout = (double) omega / finalInformed;

        int reliability = (finalInformed == nodeCount) ? 1 : 0;

        return new TrialMetrics(
            tEnd, omega, messageComplexity, alpha,
            latency50, latency90, latency100,
            effectualFanout, reliability
        );
    }

    /**
     * Finds the first round at the end of which at least the given fraction of nodes are informed.
     *
     * @return the round number (1-based), or -1 if the threshold is never reached
     */
    private static int computeLatency(List<RoundResult> rounds, int nodeCount, double threshold) {
        for (RoundResult round : rounds) {
            if ((double) round.totalInformed() / nodeCount >= threshold) {
                return round.round();
            }
        }
        return -1;
    }
}
