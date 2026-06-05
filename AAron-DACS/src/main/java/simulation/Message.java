package simulation;

/**
 * A single message event: one informed node contacting one peer during a gossip round.
 *
 * @param sender the node sending the message
 * @param receiver the node receiving the message
 * @param newInfection true if this message caused the receiver to become informed
 */
public record Message(int sender, int receiver, boolean newInfection) {}
