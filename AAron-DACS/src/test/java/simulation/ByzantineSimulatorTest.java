package simulation;

import aaron.TopologyForm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for passive Byzantine (non-forwarding) node behavior in the simulator.
 */
class ByzantineSimulatorTest {

    // --- baseline identity: fp=0.0 must be byte-identical to the old path ---

    @Test
    void zeroFailureProbability_identicalToBaseline() {
        SimulationResult baseline = Simulator.simulate(
            TopologyForm.RING, 50, 3, 0.8, 2, 42L, 100L);
        SimulationResult withZeroFp = Simulator.simulate(
            TopologyForm.RING, 50, 3, 0.8, 2, 42L, 100L, 0.0);

        assertEquals(baseline.sourceNode(), withZeroFp.sourceNode());
        assertEquals(baseline.totalRounds(), withZeroFp.totalRounds());
        assertTrue(withZeroFp.failedNodes().isEmpty());
        assertFalse(withZeroFp.sourceForcedActive());

        for (int i = 0; i < baseline.rounds().size(); i++) {
            RoundResult rb = baseline.rounds().get(i);
            RoundResult rz = withZeroFp.rounds().get(i);
            assertEquals(rb.messages(), rz.messages());
            assertEquals(rb.newlyInformed(), rz.newlyInformed());
            assertEquals(rb.totalInformed(), rz.totalInformed());
        }
    }

    @Test
    void zeroFailureProbability_identicalAcrossTopologies() {
        for (TopologyForm t : TopologyForm.values()) {
            int minN = (t == TopologyForm.SCALE_FREE) ? 4 : 7;
            int n = Math.max(minN, 30);
            SimulationResult baseline = Simulator.simulate(t, n, 3, 0.8, 2, 42L, 100L);
            SimulationResult withZeroFp = Simulator.simulate(t, n, 3, 0.8, 2, 42L, 100L, 0.0);
            assertEquals(baseline.sourceNode(), withZeroFp.sourceNode());
            assertEquals(baseline.totalRounds(), withZeroFp.totalRounds());
        }
    }

    // --- determinism: same seeds + fp must always produce same failed set ---

    @Test
    void byzantineDeterminism_sameSeedsProduceSameResult() {
        SimulationResult r1 = Simulator.simulate(
            TopologyForm.RING, 100, 3, 0.8, 2, 42L, 100L, 0.30);
        SimulationResult r2 = Simulator.simulate(
            TopologyForm.RING, 100, 3, 0.8, 2, 42L, 100L, 0.30);

        assertEquals(r1.failedNodes(), r2.failedNodes());
        assertEquals(r1.sourceForcedActive(), r2.sourceForcedActive());
        assertEquals(r1.sourceNode(), r2.sourceNode());
        assertEquals(r1.totalRounds(), r2.totalRounds());

        for (int i = 0; i < r1.rounds().size(); i++) {
            assertEquals(r1.rounds().get(i).messages(), r2.rounds().get(i).messages());
        }
    }

    @Test
    void byzantineDeterminism_differentSimSeedsProduceDifferentFailedSets() {
        SimulationResult r1 = Simulator.simulate(
            TopologyForm.RING, 100, 3, 0.8, 2, 42L, 100L, 0.30);
        SimulationResult r2 = Simulator.simulate(
            TopologyForm.RING, 100, 3, 0.8, 2, 42L, 200L, 0.30);

        assertNotEquals(r1.failedNodes(), r2.failedNodes(),
            "Different simulationSeeds should (very likely) produce different failed sets");
    }

    // --- failed nodes do not forward ---

    @Test
    void failedNodes_neverAppearAsSenders() {
        SimulationResult result = Simulator.simulate(
            TopologyForm.RING, 50, 3, 1.0, 3, 42L, 100L, 0.30);

        assertFalse(result.failedNodes().isEmpty(), "With fp=0.30, expect some failed nodes");

        for (RoundResult round : result.rounds()) {
            for (Message msg : round.messages()) {
                assertFalse(result.failedNodes().contains(msg.sender()),
                    "Failed node " + msg.sender() + " should never send");
            }
        }
    }

    @Test
    void failedNodes_canBecomeInformed() {
        SimulationResult result = Simulator.simulate(
            TopologyForm.RING, 30, 3, 1.0, 3, 42L, 100L, 0.15);

        Set<Integer> allInformed = new HashSet<>();
        allInformed.add(result.sourceNode());
        for (RoundResult round : result.rounds()) {
            allInformed.addAll(round.newlyInformed());
        }

        Set<Integer> informedFailed = new HashSet<>(result.failedNodes());
        informedFailed.retainAll(allInformed);

        // At least some failed nodes should have received the message (they're in the network)
        // This isn't guaranteed for all seeds, but with a ring topology and full view, very likely
        assertFalse(informedFailed.isEmpty() && !result.failedNodes().isEmpty(),
            "Failed nodes should be reachable as receivers in a connected ring");
    }

    // --- source forced active ---

    @Test
    void sourceForcedActive_sourceNeverInFailedSet() {
        for (long seed = 1; seed <= 50; seed++) {
            SimulationResult result = Simulator.simulate(
                TopologyForm.RING, 20, 3, 1.0, 3, 42L, seed, 0.50);
            assertFalse(result.failedNodes().contains(result.sourceNode()),
                "Source must never be in the final failed set");
        }
    }

    @Test
    void sourceForcedActive_flagSetCorrectly() {
        boolean sawForced = false;
        boolean sawNotForced = false;
        for (long seed = 1; seed <= 200; seed++) {
            SimulationResult result = Simulator.simulate(
                TopologyForm.RING, 10, 3, 1.0, 3, 42L, seed, 0.50);
            if (result.sourceForcedActive()) sawForced = true;
            else sawNotForced = true;
            if (sawForced && sawNotForced) break;
        }
        assertTrue(sawForced, "With fp=0.50 over many seeds, source should be forced active at least once");
        assertTrue(sawNotForced, "Source should not always be forced active");
    }

    // --- failed node count is plausible ---

    @ParameterizedTest
    @ValueSource(doubles = {0.15, 0.30})
    void failedNodeCount_isPlausible(double fp) {
        int totalFailed = 0;
        int totalNodes = 0;
        int trials = 100;
        int n = 100;

        for (long seed = 1; seed <= trials; seed++) {
            SimulationResult result = Simulator.simulate(
                TopologyForm.RING, n, 3, 0.8, 2, 42L, seed, fp);
            totalFailed += result.failedNodes().size();
            totalNodes += n;
        }

        double observedFraction = (double) totalFailed / totalNodes;
        assertEquals(fp, observedFraction, 0.05,
            "Observed failure fraction should be close to failureProbability");
    }

    // --- topology is unchanged ---

    @Test
    void byzantineNodes_doNotAlterTopology() {
        SimulationResult baseline = Simulator.simulate(
            TopologyForm.RANDOM, 50, 3, 0.8, 2, 42L, 100L, 0.0);
        SimulationResult byzantine = Simulator.simulate(
            TopologyForm.RANDOM, 50, 3, 0.8, 2, 42L, 100L, 0.30);

        assertEquals(baseline.network().edges(), byzantine.network().edges(),
            "Byzantine nodes must not change the topology");
        assertEquals(baseline.network().nodeCount(), byzantine.network().nodeCount());
    }

    // --- config records failureProbability ---

    @Test
    void configRecordsFailureProbability() {
        SimulationResult result = Simulator.simulate(
            TopologyForm.RING, 30, 3, 0.8, 2, 42L, 100L, 0.15);
        assertEquals(0.15, result.config().failureProbability());
    }

    @Test
    void baselineConfigHasZeroFailureProbability() {
        SimulationResult result = Simulator.simulate(
            TopologyForm.RING, 30, 3, 0.8, 2, 42L, 100L);
        assertEquals(0.0, result.config().failureProbability());
    }

    // --- validation ---

    @Test
    void invalidFailureProbability_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
            Simulator.simulate(TopologyForm.RING, 30, 3, 0.8, 2, 42L, 100L, -0.1));
        assertThrows(IllegalArgumentException.class, () ->
            Simulator.simulate(TopologyForm.RING, 30, 3, 0.8, 2, 42L, 100L, 1.1));
    }

    @Test
    void failureProbabilityBoundaryValues_areAccepted() {
        assertDoesNotThrow(() ->
            Simulator.simulate(TopologyForm.RING, 30, 3, 0.8, 2, 42L, 100L, 0.0));
        assertDoesNotThrow(() ->
            Simulator.simulate(TopologyForm.RING, 30, 3, 0.8, 2, 42L, 100L, 1.0));
    }

    // --- high failure: coverage degrades ---

    @Test
    void highFailureProbability_reducesCoverage() {
        double totalAlphaBaseline = 0;
        double totalAlphaByzantine = 0;
        int trials = 30;

        for (long seed = 1; seed <= trials; seed++) {
            SimulationResult baseline = Simulator.simulate(
                TopologyForm.RING, 50, 3, 0.6, 2, 42L, seed, 0.0);
            SimulationResult byzantine = Simulator.simulate(
                TopologyForm.RING, 50, 3, 0.6, 2, 42L, seed, 0.30);

            int bInformed = baseline.rounds().isEmpty() ? 1 :
                baseline.rounds().getLast().totalInformed();
            int zInformed = byzantine.rounds().isEmpty() ? 1 :
                byzantine.rounds().getLast().totalInformed();

            totalAlphaBaseline += (double) bInformed / 50;
            totalAlphaByzantine += (double) zInformed / 50;
        }

        assertTrue(totalAlphaByzantine / trials <= totalAlphaBaseline / trials,
            "Average coverage under fp=0.30 should not exceed baseline coverage");
    }

    // --- failed nodes appear as receivers ---

    @Test
    void failedNodes_appearAsReceivers() {
        SimulationResult result = Simulator.simulate(
            TopologyForm.RING, 50, 3, 1.0, 3, 42L, 100L, 0.30);

        assertFalse(result.failedNodes().isEmpty());

        Set<Integer> receivers = new HashSet<>();
        for (RoundResult round : result.rounds()) {
            for (Message msg : round.messages()) {
                receivers.add(msg.receiver());
            }
        }

        Set<Integer> failedReceivers = new HashSet<>(result.failedNodes());
        failedReceivers.retainAll(receivers);

        assertFalse(failedReceivers.isEmpty(),
            "At least some failed nodes must appear as message receivers in a connected ring");
    }

    // --- max rounds cap ---

    @Test
    void maxRoundsCap_stopsAtMaxRounds() {
        // fp=1.0 means all nodes fail except source (forced active).
        // Only the source sends; it can inform neighbours but they never forward.
        // On a large enough ring with low k, gossip cannot complete and keeps
        // trying (source sends every round without 3 consecutive stable rounds
        // because it always contacts already-informed neighbours producing messages).
        // Use a ring large enough that source's neighbourhood is a small fraction.
        SimulationResult result = Simulator.simulate(
            TopologyForm.RING, 200, 3, 1.0, 3, 42L, 100L, 1.0);

        // With fp=1.0, only the source is active; it can inform at most 2k=6 direct neighbours.
        // The other 193 nodes can never be informed (no forwarding).
        // The simulation should terminate either at MAX_ROUNDS or by stable-round rule.
        assertTrue(result.totalRounds() <= Simulator.MAX_ROUNDS,
            "totalRounds must not exceed MAX_ROUNDS");

        int finalInformed = result.rounds().getLast().totalInformed();
        assertTrue(finalInformed < 200,
            "With fp=1.0 on 200-node ring, full coverage should be impossible");
    }

    // --- fp=1.0 edge case: only source is active ---

    @Test
    void fullFailure_onlySourceSends() {
        SimulationResult result = Simulator.simulate(
            TopologyForm.RING, 20, 3, 1.0, 3, 42L, 100L, 1.0);

        assertTrue(result.sourceForcedActive(),
            "Source must be forced active when fp=1.0");
        assertEquals(19, result.failedNodes().size(),
            "All nodes except source should be failed");
        assertFalse(result.failedNodes().contains(result.sourceNode()));

        for (RoundResult round : result.rounds()) {
            for (Message msg : round.messages()) {
                assertEquals(result.sourceNode(), msg.sender(),
                    "Only the source should ever send when fp=1.0");
            }
        }
    }
}
