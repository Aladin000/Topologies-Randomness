package metrics;

import aaron.TopologyForm;
import simulation.SimulationResult;
import simulation.Simulator;
import simulation.RoundResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetricsCalculatorTest {

    // --- exact values on a complete graph (N=7, k=3, vf=1.0, fanOut=6) ---
    // Source infects all 6 neighbours in round 1, so T_end=1.

    @Test
    void completeGraphMetrics() {
        SimulationResult result = Simulator.simulate(TopologyForm.RING, 7, 1.0, 6, 42L, 100L);
        TrialMetrics metrics = MetricsCalculator.compute(result);

        assertEquals(1, metrics.tEnd());
        assertEquals(6, metrics.omega());
        assertEquals(6.0 / 6, metrics.messageComplexity(), 1e-10);   // M = 6 / (7-1) = 1.0
        assertEquals(1.0, metrics.alpha(), 1e-10);
        assertEquals(1, metrics.latency50());
        assertEquals(1, metrics.latency90());
        assertEquals(1, metrics.latency100());
        assertEquals(6.0 / 7, metrics.effectualFanout(), 1e-10);     // F_eff = 6/7
        assertEquals(1, metrics.reliability());
    }

    // --- T_end = number of rounds ---

    @Test
    void tEndMatchesRoundCount() {
        SimulationResult result = Simulator.simulate(TopologyForm.RING, 50, 0.8, 2, 42L, 100L);
        TrialMetrics metrics = MetricsCalculator.compute(result);

        assertEquals(result.totalRounds(), metrics.tEnd());
    }

    // --- Omega = sum of message counts ---

    @Test
    void omegaMatchesSumOfMessageCounts() {
        SimulationResult result = Simulator.simulate(TopologyForm.RANDOM, 50, 0.6, 2, 42L, 100L);
        TrialMetrics metrics = MetricsCalculator.compute(result);

        int expectedOmega = 0;
        for (RoundResult round : result.rounds()) {
            expectedOmega += round.messageCount();
        }
        assertEquals(expectedOmega, metrics.omega());
    }

    // --- M = Omega / (N - 1) ---

    @Test
    void messageComplexityIsOmegaDividedByNMinusOne() {
        SimulationResult result = Simulator.simulate(TopologyForm.RING, 50, 0.8, 2, 42L, 100L);
        TrialMetrics metrics = MetricsCalculator.compute(result);

        double expected = (double) metrics.omega() / (50 - 1);
        assertEquals(expected, metrics.messageComplexity(), 1e-10);
    }

    // --- alpha = |I(T_end)| / N ---

    @Test
    void alphaIsCorrectForFullCoverage() {
        SimulationResult result = Simulator.simulate(TopologyForm.RING, 7, 1.0, 6, 42L, 100L);
        TrialMetrics metrics = MetricsCalculator.compute(result);

        assertEquals(1.0, metrics.alpha(), 1e-10);
    }

    @Test
    void alphaMatchesFinalInformedDividedByN() {
        SimulationResult result = Simulator.simulate(TopologyForm.RING, 50, 0.8, 2, 42L, 100L);
        TrialMetrics metrics = MetricsCalculator.compute(result);

        int finalInformed = result.rounds().getLast().totalInformed();
        double expected = (double) finalInformed / 50;
        assertEquals(expected, metrics.alpha(), 1e-10);
    }

    // --- L_x: latency thresholds ---

    @Test
    void latencyThresholdsAreNonDecreasing() {
        SimulationResult result = Simulator.simulate(TopologyForm.RING, 50, 0.8, 2, 42L, 100L);
        TrialMetrics metrics = MetricsCalculator.compute(result);

        if (metrics.latency50() != -1 && metrics.latency90() != -1) {
            assertTrue(metrics.latency50() <= metrics.latency90());
        }
        if (metrics.latency90() != -1 && metrics.latency100() != -1) {
            assertTrue(metrics.latency90() <= metrics.latency100());
        }
    }

    @Test
    void latency100MatchesTEndWhenFullyInformed() {
        SimulationResult result = Simulator.simulate(TopologyForm.RING, 7, 1.0, 6, 42L, 100L);
        TrialMetrics metrics = MetricsCalculator.compute(result);

        assertEquals(metrics.tEnd(), metrics.latency100());
    }

    @Test
    void latencyMatchesFirstRoundMeetingThreshold() {
        SimulationResult result = Simulator.simulate(TopologyForm.RING, 50, 1.0, 2, 42L, 100L);
        TrialMetrics metrics = MetricsCalculator.compute(result);

        int n = result.config().nodeCount();

        // Verify L_0.5 by manual scan
        int expectedL50 = -1;
        for (RoundResult round : result.rounds()) {
            if ((double) round.totalInformed() / n >= 0.5) {
                expectedL50 = round.round();
                break;
            }
        }
        assertEquals(expectedL50, metrics.latency50());

        // Verify L_0.9 by manual scan
        int expectedL90 = -1;
        for (RoundResult round : result.rounds()) {
            if ((double) round.totalInformed() / n >= 0.9) {
                expectedL90 = round.round();
                break;
            }
        }
        assertEquals(expectedL90, metrics.latency90());
    }

    // --- F_eff: effectual fanout ---

    @Test
    void effectualFanoutIsOmegaDividedByInformed() {
        SimulationResult result = Simulator.simulate(TopologyForm.RING, 50, 0.8, 2, 42L, 100L);
        TrialMetrics metrics = MetricsCalculator.compute(result);

        int finalInformed = result.rounds().getLast().totalInformed();
        double expected = (double) metrics.omega() / finalInformed;
        assertEquals(expected, metrics.effectualFanout(), 1e-10);
    }

    // --- R_run: reliability ---

    @Test
    void reliabilityIsOneWhenFullyInformed() {
        SimulationResult result = Simulator.simulate(TopologyForm.RING, 7, 1.0, 6, 42L, 100L);
        TrialMetrics metrics = MetricsCalculator.compute(result);

        assertEquals(1.0, metrics.alpha(), 1e-10);
        assertEquals(1, metrics.reliability());
    }

    @Test
    void reliabilityConsistentWithAlpha() {
        SimulationResult result = Simulator.simulate(TopologyForm.RING, 50, 0.8, 2, 42L, 100L);
        TrialMetrics metrics = MetricsCalculator.compute(result);

        if (metrics.alpha() == 1.0) {
            assertEquals(1, metrics.reliability());
        } else {
            assertEquals(0, metrics.reliability());
        }
    }

    @Test
    void reliabilityConsistentWithLatency100() {
        SimulationResult result = Simulator.simulate(TopologyForm.RING, 50, 0.8, 2, 42L, 100L);
        TrialMetrics metrics = MetricsCalculator.compute(result);

        if (metrics.reliability() == 1) {
            assertTrue(metrics.latency100() > 0);
        } else {
            assertEquals(-1, metrics.latency100());
        }
    }

    // --- determinism: same input, same metrics ---

    @Test
    void sameSimulationProducesSameMetrics() {
        SimulationResult r1 = Simulator.simulate(TopologyForm.RANDOM, 100, 0.6, 3, 42L, 100L);
        SimulationResult r2 = Simulator.simulate(TopologyForm.RANDOM, 100, 0.6, 3, 42L, 100L);

        TrialMetrics m1 = MetricsCalculator.compute(r1);
        TrialMetrics m2 = MetricsCalculator.compute(r2);

        assertEquals(m1, m2);
    }

    // --- all four topologies ---

    @Test
    void metricsComputeForRing() {
        SimulationResult result = Simulator.simulate(TopologyForm.RING, 50, 0.8, 2, 42L, 100L);
        TrialMetrics metrics = MetricsCalculator.compute(result);
        assertMetricsAreSane(metrics, 50);
    }

    @Test
    void metricsComputeForRandom() {
        SimulationResult result = Simulator.simulate(TopologyForm.RANDOM, 50, 0.8, 2, 42L, 100L);
        TrialMetrics metrics = MetricsCalculator.compute(result);
        assertMetricsAreSane(metrics, 50);
    }

    @Test
    void metricsComputeForScaleFree() {
        SimulationResult result = Simulator.simulate(TopologyForm.SCALE_FREE, 50, 0.8, 2, 42L, 100L);
        TrialMetrics metrics = MetricsCalculator.compute(result);
        assertMetricsAreSane(metrics, 50);
    }

    @Test
    void metricsComputeForSmallWorld() {
        SimulationResult result = Simulator.simulate(TopologyForm.SMALL_WORLD, 50, 0.8, 2, 42L, 100L);
        TrialMetrics metrics = MetricsCalculator.compute(result);
        assertMetricsAreSane(metrics, 50);
    }

    // --- helper ---

    private void assertMetricsAreSane(TrialMetrics metrics, int nodeCount) {
        assertTrue(metrics.tEnd() > 0);
        assertTrue(metrics.tEnd() <= Simulator.MAX_ROUNDS);
        assertTrue(metrics.omega() >= 0);
        assertTrue(metrics.alpha() > 0.0 && metrics.alpha() <= 1.0);
        assertTrue(metrics.messageComplexity() >= 0.0);
        assertTrue(metrics.effectualFanout() >= 0.0);
        assertTrue(metrics.reliability() == 0 || metrics.reliability() == 1);

        if (metrics.latency50() != -1) {
            assertTrue(metrics.latency50() >= 1 && metrics.latency50() <= metrics.tEnd());
        }
        if (metrics.latency90() != -1) {
            assertTrue(metrics.latency90() >= 1 && metrics.latency90() <= metrics.tEnd());
        }
        if (metrics.latency100() != -1) {
            assertTrue(metrics.latency100() >= 1 && metrics.latency100() <= metrics.tEnd());
        }
    }
}
