package simulation;

import java.util.List;

/**
 * Records the events and state changes of a single simulation round.
 *
 * @param round the round number (1-based)
 * @param messages all messages sent during this round
 * @param newlyInformed nodes that became informed at the end of this round (sorted by ID)
 * @param totalInformed total number of informed nodes at the end of this round
 */
public record RoundResult(
    int round,
    List<Message> messages,
    List<Integer> newlyInformed,
    int totalInformed
) {

    /** Number of messages sent during this round. */
    public int messageCount() {
        return messages.size();
    }

    /** Number of nodes that became newly informed during this round. */
    public int newInfectionCount() {
        return newlyInformed.size();
    }
}
