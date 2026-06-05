package simulation;

import aaron.TopologyForm;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SimulatorTest {

    // --- regression: determinism ---

    @Test
    void determinismForRingTopology() {
        SimulationResult r1 = Simulator.simulate(TopologyForm.RING, 50, 0.6, 2, 42L, 100L);
        SimulationResult r2 = Simulator.simulate(TopologyForm.RING, 50, 0.6, 2, 42L, 100L);
        assertResultsIdentical(r1, r2);
    }

    @Test
    void determinismForRandomTopology() {
        SimulationResult r1 = Simulator.simulate(TopologyForm.RANDOM, 50, 0.6, 2, 42L, 100L);
        SimulationResult r2 = Simulator.simulate(TopologyForm.RANDOM, 50, 0.6, 2, 42L, 100L);
        assertResultsIdentical(r1, r2);
    }

    @Test
    void determinismForScaleFreeTopology() {
        SimulationResult r1 = Simulator.simulate(TopologyForm.SCALE_FREE, 50, 0.6, 2, 42L, 100L);
        SimulationResult r2 = Simulator.simulate(TopologyForm.SCALE_FREE, 50, 0.6, 2, 42L, 100L);
        assertResultsIdentical(r1, r2);
    }

    // --- regression: graph reproducibility ---

    @Test
    void sameGraphSeedProducesSameNetworkRandom() {
        SimulationResult r1 = Simulator.simulate(TopologyForm.RANDOM, 50, 0.6, 2, 42L, 100L);
        SimulationResult r2 = Simulator.simulate(TopologyForm.RANDOM, 50, 0.6, 2, 42L, 200L);
        assertEquals(r1.network().edges(), r2.network().edges());
    }

    @Test
    void sameGraphSeedProducesSameNetworkScaleFree() {
        SimulationResult r1 = Simulator.simulate(TopologyForm.SCALE_FREE, 50, 0.6, 2, 42L, 100L);
        SimulationResult r2 = Simulator.simulate(TopologyForm.SCALE_FREE, 50, 0.6, 2, 42L, 200L);
        assertEquals(r1.network().edges(), r2.network().edges());
    }

    // --- regression: simulation reproducibility ---

    @Test
    void sameConfigProducesIdenticalRoundByRoundResults() {
        SimulationResult r1 = Simulator.simulate(TopologyForm.RANDOM, 100, 0.8, 3, 42L, 100L);
        SimulationResult r2 = Simulator.simulate(TopologyForm.RANDOM, 100, 0.8, 3, 42L, 100L);

        assertEquals(r1.sourceNode(), r2.sourceNode());
        assertEquals(r1.totalRounds(), r2.totalRounds());

        for (int i = 0; i < r1.rounds().size(); i++) {
            RoundResult rr1 = r1.rounds().get(i);
            RoundResult rr2 = r2.rounds().get(i);
            assertEquals(rr1.totalInformed(), rr2.totalInformed());
            assertEquals(rr1.messageCount(), rr2.messageCount());
            assertEquals(rr1.newlyInformed(), rr2.newlyInformed());
        }
    }

    // --- regression: controlled difference ---

    @Test
    void differentSimulationSeedSameGraphDifferentDynamics() {
        SimulationResult r1 = Simulator.simulate(TopologyForm.RANDOM, 100, 0.6, 2, 42L, 100L);
        SimulationResult r2 = Simulator.simulate(TopologyForm.RANDOM, 100, 0.6, 2, 42L, 200L);

        // Same graph
        assertEquals(r1.network().edges(), r2.network().edges());

        // Different dynamics (source node or round-level messages differ)
        boolean differ = r1.sourceNode() != r2.sourceNode();
        if (!differ) {
            for (int i = 0; i < Math.min(r1.rounds().size(), r2.rounds().size()); i++) {
                if (!r1.rounds().get(i).messages().equals(r2.rounds().get(i).messages())) {
                    differ = true;
                    break;
                }
            }
        }
        assertTrue(differ, "Different simulationSeeds should produce different dynamics");
    }

    // --- synchronous round semantics ---

    @Test
    void onlySourceNodeSendsInRoundOne() {
        SimulationResult result = Simulator.simulate(TopologyForm.RING, 20, 1.0, 2, 42L, 100L);

        RoundResult round1 = result.rounds().get(0);
        assertEquals(1, round1.round());

        Set<Integer> senders = new HashSet<>();
        for (Message msg : round1.messages()) {
            senders.add(msg.sender());
        }
        assertEquals(Set.of(result.sourceNode()), senders);
    }

    @Test
    void newlyInformedSendInNextRound() {
        SimulationResult result = Simulator.simulate(TopologyForm.RING, 20, 1.0, 2, 42L, 100L);

        if (result.rounds().size() < 2) return;

        RoundResult round1 = result.rounds().get(0);
        RoundResult round2 = result.rounds().get(1);

        Set<Integer> round2Senders = new HashSet<>();
        for (Message msg : round2.messages()) {
            round2Senders.add(msg.sender());
        }

        // Source must still send in round 2 (SI model)
        assertTrue(round2Senders.contains(result.sourceNode()));

        // Nodes informed in round 1 must send in round 2
        for (int node : round1.newlyInformed()) {
            assertTrue(round2Senders.contains(node),
                "Node " + node + " informed in round 1 should send in round 2");
        }
    }

    // --- SI model: informed nodes send every round ---

    @Test
    void informedNodesSendEverySubsequentRound() {
        SimulationResult result = Simulator.simulate(TopologyForm.RING, 30, 1.0, 2, 42L, 100L);

        Set<Integer> expectedSenders = new HashSet<>();
        expectedSenders.add(result.sourceNode());

        for (RoundResult round : result.rounds()) {
            Set<Integer> actualSenders = new HashSet<>();
            for (Message msg : round.messages()) {
                actualSenders.add(msg.sender());
            }
            assertEquals(expectedSenders, actualSenders,
                "In round " + round.round() + ", all previously informed nodes should send");

            expectedSenders.addAll(round.newlyInformed());
        }
    }

    // --- stopping: fully informed ---

    @Test
    void completeGraphFullyInformsInOneRound() {
        // N=7, k=3: ring degree = 6 = N-1, i.e. complete graph.
        // vf=1.0, fanOut=6: source contacts all 6 neighbours in round 1.
        SimulationResult result = Simulator.simulate(TopologyForm.RING, 7, 1.0, 6, 42L, 100L);

        assertEquals(1, result.totalRounds());
        assertEquals(7, result.rounds().get(0).totalInformed());
        assertEquals(6, result.rounds().get(0).newInfectionCount());
    }

    @Test
    void lastRoundReportsFullCoverage() {
        SimulationResult result = Simulator.simulate(TopologyForm.RING, 7, 1.0, 6, 42L, 100L);

        RoundResult lastRound = result.rounds().get(result.rounds().size() - 1);
        assertEquals(result.config().nodeCount(), lastRound.totalInformed());
    }

    // --- stopping: max rounds cap ---

    @Test
    void totalRoundsDoesNotExceedMaxRounds() {
        SimulationResult result = Simulator.simulate(TopologyForm.RING, 50, 0.6, 2, 42L, 100L);
        assertTrue(result.totalRounds() <= Simulator.MAX_ROUNDS);
    }

    // --- stopping: stalled dissemination ---

    @Test
    void stalledDisseminationStopsAfterStableRounds() {
        SimulationResult result = Simulator.simulate(
            TopologyForm.RING, 50, 0.2, 1, 42L, 100L);

        var rounds = result.rounds();
        int finalInformed = rounds.getLast().totalInformed();
        if (finalInformed < 50) {
            int trailingStable = 0;
            for (int i = rounds.size() - 1; i >= 0; i--) {
                if (rounds.get(i).newInfectionCount() == 0) {
                    trailingStable++;
                } else {
                    break;
                }
            }
            assertEquals(Simulator.STABLE_ROUNDS, trailingStable,
                "Stalled simulation should have exactly STABLE_ROUNDS trailing empty rounds");
        }
    }

    // --- source node selection ---

    @Test
    void sourceNodeIsReproducible() {
        SimulationResult r1 = Simulator.simulate(TopologyForm.RING, 100, 0.6, 2, 42L, 100L);
        SimulationResult r2 = Simulator.simulate(TopologyForm.RING, 100, 0.6, 2, 42L, 100L);
        assertEquals(r1.sourceNode(), r2.sourceNode());
    }

    @Test
    void sourceNodeChangesWithDifferentSimulationSeed() {
        int firstSource = Simulator.simulate(
            TopologyForm.RING, 100, 0.6, 2, 42L, 1L).sourceNode();

        boolean found = false;
        for (long seed = 2; seed <= 50; seed++) {
            int source = Simulator.simulate(
                TopologyForm.RING, 100, 0.6, 2, 42L, seed).sourceNode();
            if (source != firstSource) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Different simulationSeeds should produce different source nodes");
    }

    @Test
    void sourceNodeIsWithinRange() {
        for (long seed = 0; seed < 20; seed++) {
            SimulationResult result = Simulator.simulate(
                TopologyForm.RING, 50, 0.6, 2, 42L, seed);
            assertTrue(result.sourceNode() >= 0 && result.sourceNode() < 50);
        }
    }

    // --- message correctness ---

    @Test
    void allMessagesFollowEdges() {
        SimulationResult result = Simulator.simulate(TopologyForm.SCALE_FREE, 50, 0.6, 2, 42L, 100L);

        for (RoundResult round : result.rounds()) {
            for (Message msg : round.messages()) {
                assertTrue(result.network().neighbors(msg.sender()).contains(msg.receiver()),
                    "Message from " + msg.sender() + " to " + msg.receiver() +
                    " must follow an edge (round " + round.round() + ")");
            }
        }
    }

    @Test
    void newInfectionFlagsAreConsistentWithNewlyInformed() {
        SimulationResult result = Simulator.simulate(TopologyForm.RING, 30, 0.8, 2, 42L, 100L);

        for (RoundResult round : result.rounds()) {
            int flagCount = 0;
            for (Message msg : round.messages()) {
                if (msg.newInfection()) {
                    flagCount++;
                }
            }
            assertEquals(round.newlyInformed().size(), flagCount,
                "newInfection flag count should match newlyInformed size (round " + round.round() + ")");
        }
    }

    @Test
    void newInfectionTargetsMatchNewlyInformedList() {
        SimulationResult result = Simulator.simulate(TopologyForm.RING, 30, 0.8, 2, 42L, 100L);

        for (RoundResult round : result.rounds()) {
            Set<Integer> infectedFromMessages = new HashSet<>();
            for (Message msg : round.messages()) {
                if (msg.newInfection()) {
                    infectedFromMessages.add(msg.receiver());
                }
            }
            assertEquals(new HashSet<>(round.newlyInformed()), infectedFromMessages,
                "newInfection receivers should match newlyInformed (round " + round.round() + ")");
        }
    }

    // --- state progression ---

    @Test
    void totalInformedIsMonotonicallyNonDecreasing() {
        SimulationResult result = Simulator.simulate(TopologyForm.RANDOM, 100, 0.6, 2, 42L, 100L);

        int prev = 1;
        for (RoundResult round : result.rounds()) {
            assertTrue(round.totalInformed() >= prev,
                "totalInformed should not decrease (round " + round.round() + ")");
            prev = round.totalInformed();
        }
    }

    @Test
    void totalInformedStartsAtOneBeforeFirstRound() {
        SimulationResult result = Simulator.simulate(TopologyForm.RING, 20, 1.0, 2, 42L, 100L);

        // After round 1, totalInformed includes source + newly informed
        RoundResult round1 = result.rounds().get(0);
        assertEquals(1 + round1.newInfectionCount(), round1.totalInformed());
    }

    @Test
    void roundNumbersAreSequential() {
        SimulationResult result = Simulator.simulate(TopologyForm.RING, 50, 0.6, 2, 42L, 100L);

        for (int i = 0; i < result.rounds().size(); i++) {
            assertEquals(i + 1, result.rounds().get(i).round());
        }
    }

    @Test
    void newlyInformedNodesAreSorted() {
        SimulationResult result = Simulator.simulate(TopologyForm.RANDOM, 100, 0.8, 3, 42L, 100L);

        for (RoundResult round : result.rounds()) {
            for (int i = 1; i < round.newlyInformed().size(); i++) {
                assertTrue(round.newlyInformed().get(i) > round.newlyInformed().get(i - 1),
                    "newlyInformed should be sorted (round " + round.round() + ")");
            }
        }
    }

    // --- configuration record ---

    @Test
    void configRecordsAllParameters() {
        SimulationResult result = Simulator.simulate(TopologyForm.RANDOM, 100, 0.6, 2, 42L, 100L);

        SimulationConfig config = result.config();
        assertEquals(TopologyForm.RANDOM, config.topologyType());
        assertEquals(100, config.nodeCount());
        assertEquals(0.6, config.viewFraction());
        assertEquals(2, config.fanOut());
        assertEquals(42L, config.graphSeed());
        assertEquals(100L, config.simulationSeed());
        assertEquals(Simulator.DEFAULT_K, config.k());
        assertEquals(Simulator.DEFAULT_K, config.m());
        assertEquals((2.0 * Simulator.DEFAULT_K) / 99, config.p(), 1e-10);
        assertEquals(0.0, config.beta(), 1e-10);
        assertEquals(Simulator.MAX_ROUNDS, config.maxRounds());
        assertEquals(Simulator.STABLE_ROUNDS, config.stableRounds());
    }

    // --- all four topologies produce valid results ---

    @Test
    void ringSimulationProducesValidResult() {
        SimulationResult result = Simulator.simulate(TopologyForm.RING, 50, 0.8, 2, 42L, 100L);

        assertTrue(result.totalRounds() > 0);
        assertTrue(result.sourceNode() >= 0 && result.sourceNode() < 50);
        assertFalse(result.rounds().isEmpty());
    }

    @Test
    void randomSimulationProducesValidResult() {
        SimulationResult result = Simulator.simulate(TopologyForm.RANDOM, 50, 0.8, 2, 42L, 100L);

        assertTrue(result.totalRounds() > 0);
        assertTrue(result.sourceNode() >= 0 && result.sourceNode() < 50);
        assertFalse(result.rounds().isEmpty());
    }

    @Test
    void scaleFreeSimulationProducesValidResult() {
        SimulationResult result = Simulator.simulate(TopologyForm.SCALE_FREE, 50, 0.8, 2, 42L, 100L);

        assertTrue(result.totalRounds() > 0);
        assertTrue(result.sourceNode() >= 0 && result.sourceNode() < 50);
        assertFalse(result.rounds().isEmpty());
    }

    @Test
    void smallWorldSimulationProducesValidResult() {
        SimulationResult result = Simulator.simulate(TopologyForm.SMALL_WORLD, 50, 0.8, 2, 42L, 100L);

        assertTrue(result.totalRounds() > 0);
        assertTrue(result.sourceNode() >= 0 && result.sourceNode() < 50);
        assertFalse(result.rounds().isEmpty());
    }

    @Test
    void determinismForSmallWorldTopology() {
        SimulationResult r1 = Simulator.simulate(TopologyForm.SMALL_WORLD, 50, 0.6, 2, 42L, 100L);
        SimulationResult r2 = Simulator.simulate(TopologyForm.SMALL_WORLD, 50, 0.6, 2, 42L, 100L);
        assertResultsIdentical(r1, r2);
    }

    @Test
    void configRecordsBetaForSmallWorld() {
        SimulationResult result = Simulator.simulate(TopologyForm.SMALL_WORLD, 50, 0.6, 2, 42L, 100L);
        assertEquals(Simulator.BETA, result.config().beta(), 1e-10);
    }

    @Test
    void configRecordsZeroBetaForNonSmallWorld() {
        SimulationResult result = Simulator.simulate(TopologyForm.RING, 50, 0.6, 2, 42L, 100L);
        assertEquals(0.0, result.config().beta(), 1e-10);
    }

    // --- parameter validation ---

    @Test
    void tooFewNodesThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            Simulator.simulate(TopologyForm.RING, 3, 0.6, 2, 42L, 100L));
    }

    @Test
    void invalidViewFractionThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            Simulator.simulate(TopologyForm.RING, 50, 0.0, 2, 42L, 100L));
        assertThrows(IllegalArgumentException.class, () ->
            Simulator.simulate(TopologyForm.RING, 50, 1.1, 2, 42L, 100L));
        assertThrows(IllegalArgumentException.class, () ->
            Simulator.simulate(TopologyForm.RING, 50, -0.1, 2, 42L, 100L));
    }

    @Test
    void invalidFanOutThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            Simulator.simulate(TopologyForm.RING, 50, 0.6, 0, 42L, 100L));
        assertThrows(IllegalArgumentException.class, () ->
            Simulator.simulate(TopologyForm.RING, 50, 0.6, -1, 42L, 100L));
    }

    @Test
    void scaleFreeMinimumNodeCountIsEnforced() {
        // K=3, so minimum for scale-free is K+1=4
        assertThrows(IllegalArgumentException.class, () ->
            Simulator.simulate(TopologyForm.SCALE_FREE, 3, 0.6, 2, 42L, 100L));

        // K+1 = 4 should work
        assertDoesNotThrow(() ->
            Simulator.simulate(TopologyForm.SCALE_FREE, 4, 1.0, 3, 42L, 100L));
    }

    @Test
    void randomMinimumNodeCountIsEnforced() {
        // K=3, so minimum for random is 2K+1=7
        assertThrows(IllegalArgumentException.class, () ->
            Simulator.simulate(TopologyForm.RANDOM, 6, 0.6, 2, 42L, 100L));

        assertDoesNotThrow(() ->
            Simulator.simulate(TopologyForm.RANDOM, 7, 1.0, 3, 42L, 100L));
    }

    // 
    // Exhaustive determinism stress tests
    // 

    /**
     * Runs the same simulation 3 times and asserts byte-identical output
     * for every round, every message, every metric.
     */
    private void assertStrictDeterminism(TopologyForm topology, int n, int k,
                                         double vf, int fo, long graphSeed,
                                         long simSeed, double fp) {
        SimulationResult r1 = Simulator.simulate(topology, n, k, vf, fo, graphSeed, simSeed, fp);
        SimulationResult r2 = Simulator.simulate(topology, n, k, vf, fo, graphSeed, simSeed, fp);
        SimulationResult r3 = Simulator.simulate(topology, n, k, vf, fo, graphSeed, simSeed, fp);

        assertResultsIdentical(r1, r2);
        assertResultsIdentical(r1, r3);

        assertEquals(r1.failedNodes(), r2.failedNodes());
        assertEquals(r1.failedNodes(), r3.failedNodes());
        assertEquals(r1.sourceForcedActive(), r2.sourceForcedActive());
        assertEquals(r1.sourceForcedActive(), r3.sourceForcedActive());
    }

    @Test
    void determinismStress_allTopologies_baseline() {
        int[] sizes = {20, 50, 100, 200};
        double[] vfs = {0.2, 0.6, 1.0};
        int[] fos = {1, 3, 5};
        long[] seeds = {1L, 42L, 999L, Long.MAX_VALUE};

        for (TopologyForm t : TopologyForm.values()) {
            for (int n : sizes) {
                int k = 3;
                int minN = (t == TopologyForm.SCALE_FREE) ? k + 1 : 2 * k + 1;
                if (n < minN) continue;

                for (double vf : vfs) {
                    for (int fo : fos) {
                        for (long seed : seeds) {
                            assertStrictDeterminism(t, n, k, vf, fo, seed, seed + 7, 0.0);
                        }
                    }
                }
            }
        }
    }

    @Test
    void determinismStress_allTopologies_byzantine() {
        int[] sizes = {30, 100, 200};
        double[] fps = {0.05, 0.15, 0.30, 0.50, 0.90, 1.0};
        long[] seeds = {1L, 42L, 12345L};

        for (TopologyForm t : TopologyForm.values()) {
            int k = 3;
            int minN = (t == TopologyForm.SCALE_FREE) ? k + 1 : 2 * k + 1;

            for (int n : sizes) {
                if (n < minN) continue;

                for (double fp : fps) {
                    for (long seed : seeds) {
                        assertStrictDeterminism(t, n, k, 0.8, 2, seed, seed + 7, fp);
                    }
                }
            }
        }
    }

    @Test
    void determinismStress_highK() {
        for (TopologyForm t : TopologyForm.values()) {
            for (int k : new int[]{3, 6, 9}) {
                int minN = (t == TopologyForm.SCALE_FREE) ? k + 1 : 2 * k + 1;
                int n = Math.max(minN, 50);
                assertStrictDeterminism(t, n, k, 1.0, 3, 42L, 100L, 0.0);
                assertStrictDeterminism(t, n, k, 1.0, 3, 42L, 100L, 0.30);
            }
        }
    }

    @Test
    void determinismStress_largeNetwork() {
        for (TopologyForm t : TopologyForm.values()) {
            assertStrictDeterminism(t, 500, 3, 1.0, 3, 42L, 100L, 0.0);
            assertStrictDeterminism(t, 500, 3, 1.0, 3, 42L, 100L, 0.15);
        }
    }

    @Test
    void determinism_rngIsolation_fpDoesNotAffectBaseline() {
        for (TopologyForm t : TopologyForm.values()) {
            int k = 3;
            int minN = (t == TopologyForm.SCALE_FREE) ? k + 1 : 2 * k + 1;
            int n = Math.max(minN, 50);

            SimulationResult baseline = Simulator.simulate(t, n, k, 0.8, 2, 42L, 100L);
            SimulationResult explicitZero = Simulator.simulate(t, n, k, 0.8, 2, 42L, 100L, 0.0);

            assertResultsIdentical(baseline, explicitZero);
        }
    }

    @Test
    void determinism_networkConstruction_independentOfSimSeed() {
        for (TopologyForm t : TopologyForm.values()) {
            int k = 3;
            int minN = (t == TopologyForm.SCALE_FREE) ? k + 1 : 2 * k + 1;
            int n = Math.max(minN, 100);

            SimulationResult r1 = Simulator.simulate(t, n, k, 0.8, 2, 42L, 100L, 0.0);
            SimulationResult r2 = Simulator.simulate(t, n, k, 0.8, 2, 42L, 999L, 0.0);

            assertEquals(r1.network().edges(), r2.network().edges(),
                "Same graphSeed must produce same network regardless of simulationSeed for " + t);
            assertEquals(r1.network().edgeCount(), r2.network().edgeCount());
        }
    }

    @Test
    void determinism_differentSeeds_produceDifferentResults() {
        for (TopologyForm t : TopologyForm.values()) {
            int k = 3;
            int minN = (t == TopologyForm.SCALE_FREE) ? k + 1 : 2 * k + 1;
            int n = Math.max(minN, 50);

            SimulationResult r1 = Simulator.simulate(t, n, k, 0.8, 2, 42L, 100L, 0.0);
            SimulationResult r2 = Simulator.simulate(t, n, k, 0.8, 2, 42L, 200L, 0.0);

            boolean differ = (r1.sourceNode() != r2.sourceNode())
                          || (r1.totalRounds() != r2.totalRounds());
            assertTrue(differ,
                "Different simSeeds should (very likely) produce different results for " + t);
        }
    }

    // --- helper ---

    private void assertResultsIdentical(SimulationResult r1, SimulationResult r2) {
        assertEquals(r1.sourceNode(), r2.sourceNode());
        assertEquals(r1.totalRounds(), r2.totalRounds());
        assertEquals(r1.network().edges(), r2.network().edges());

        for (int i = 0; i < r1.rounds().size(); i++) {
            RoundResult rr1 = r1.rounds().get(i);
            RoundResult rr2 = r2.rounds().get(i);
            assertEquals(rr1.round(), rr2.round());
            assertEquals(rr1.messages(), rr2.messages());
            assertEquals(rr1.newlyInformed(), rr2.newlyInformed());
            assertEquals(rr1.totalInformed(), rr2.totalInformed());
        }
    }
}
