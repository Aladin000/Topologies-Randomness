package simulation;

import aaron.Network;

import java.util.List;
import java.util.Set;

/**
 * Complete output of a single simulation trial: configuration, network,
 * source node, Byzantine failure information, and the round-by-round event log.
 *
 * @param config the full configuration used for this trial
 * @param network the network instance used (retained for edge serialization)
 * @param sourceNode the initially informed node
 * @param failedNodes set of node IDs classified as passive Byzantine (non-forwarding)
 * @param sourceForcedActive true if the source was originally in the failed set and was forced active
 * @param rounds the per-round event log
 */
public record SimulationResult(
    SimulationConfig config,
    Network network,
    int sourceNode,
    Set<Integer> failedNodes,
    boolean sourceForcedActive,
    List<RoundResult> rounds
) {

    /** Number of rounds until termination (T_end). */
    public int totalRounds() {
        return rounds.size();
    }
}
