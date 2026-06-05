package experiments;

import aaron.AAron;
import aaron.Network;
import aaron.NetworkValidator;
import aaron.TopologyForm;
import metrics.TrialMetrics;
import simulation.Simulator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.io.StringWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentRunnerTest {

    private static final long BASE_SEED = 42L;
    private static final int K = Simulator.DEFAULT_K;

    // --- Determinism ---

    @Test
    void determinism_sameBaseSeedProducesIdenticalResults() {
        ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 50, K, 0.8, 2);

        List<TrialResult> run1 = ExperimentRunner.run(config, 2, 3, BASE_SEED);
        List<TrialResult> run2 = ExperimentRunner.run(config, 2, 3, BASE_SEED);

        assertEquals(run1.size(), run2.size());
        for (int i = 0; i < run1.size(); i++) {
            TrialResult r1 = run1.get(i);
            TrialResult r2 = run2.get(i);
            assertEquals(r1.graphSeed(), r2.graphSeed());
            assertEquals(r1.simulationSeed(), r2.simulationSeed());
            assertEquals(r1.trial(), r2.trial());
            assertEquals(r1.metrics(), r2.metrics());
        }
    }

    @Test
    void differentBaseSeedProducesDifferentResults() {
        ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 50, K, 0.8, 2);

        List<TrialResult> run1 = ExperimentRunner.run(config, 1, 3, BASE_SEED);
        List<TrialResult> run2 = ExperimentRunner.run(config, 1, 3, BASE_SEED + 1);

        assertNotEquals(run1.get(0).graphSeed(), run2.get(0).graphSeed());
    }

    // --- Trial count and indexing ---

    @Test
    void trialCount_GtimesSTrialsProduced() {
        ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 30, K, 1.0, 2);

        int G = 3;
        int S = 4;
        List<TrialResult> results = ExperimentRunner.run(config, G, S, BASE_SEED);

        assertEquals(G * S, results.size());
    }

    @Test
    void trialIndices_areSequentialStartingFromOne() {
        ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 30, K, 1.0, 2);

        List<TrialResult> results = ExperimentRunner.run(config, 2, 3, BASE_SEED);

        for (int i = 0; i < results.size(); i++) {
            assertEquals(i + 1, results.get(i).trial());
        }
    }

    // --- Graph instance structure ---

    @Test
    void sameGraphSeedUsedForAllTrialsWithinInstance() {
        ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 30, K, 1.0, 2);

        int G = 3;
        int S = 4;
        List<TrialResult> results = ExperimentRunner.run(config, G, S, BASE_SEED);

        for (int g = 0; g < G; g++) {
            long graphSeed = results.get(g * S).graphSeed();
            for (int s = 1; s < S; s++) {
                assertEquals(graphSeed, results.get(g * S + s).graphSeed(),
                    "All trials within graph instance " + g + " should share the same graphSeed");
            }
        }
    }

    @Test
    void differentGraphInstancesUseDifferentGraphSeeds() {
        ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 30, K, 1.0, 2);

        int G = 3;
        List<TrialResult> results = ExperimentRunner.run(config, G, 2, BASE_SEED);

        Set<Long> graphSeeds = new HashSet<>();
        for (int g = 0; g < G; g++) {
            graphSeeds.add(results.get(g * 2).graphSeed());
        }
        assertEquals(G, graphSeeds.size(), "Each graph instance should have a distinct graphSeed");
    }

    @Test
    void differentSimulationSeedsWithinGraphInstance() {
        ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 30, K, 1.0, 2);

        int S = 5;
        List<TrialResult> results = ExperimentRunner.run(config, 1, S, BASE_SEED);

        Set<Long> simSeeds = new HashSet<>();
        for (TrialResult r : results) {
            simSeeds.add(r.simulationSeed());
        }
        assertEquals(S, simSeeds.size(), "Each trial should have a distinct simulationSeed");
    }

    // --- Config fields propagation ---

    @Test
    void resultFieldsMatchConfig() {
        ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 40, K, 0.7, 3);

        List<TrialResult> results = ExperimentRunner.run(config, 1, 1, BASE_SEED);

        TrialResult r = results.getFirst();
        assertEquals(TopologyForm.RING, r.topologyType());
        assertEquals(40, r.nodeCount());
        assertEquals(0.7, r.viewFraction());
        assertEquals(3, r.fanOut());
        assertEquals(BASE_SEED, r.baseSeed());
    }

    // --- Metrics sanity ---

    @Test
    void metricsAreValid() {
        ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 50, K, 1.0, 3);

        List<TrialResult> results = ExperimentRunner.run(config, 1, 2, BASE_SEED);

        for (TrialResult r : results) {
            TrialMetrics m = r.metrics();
            assertTrue(m.tEnd() >= 1);
            assertTrue(m.omega() >= 1);
            assertTrue(m.alpha() > 0.0 && m.alpha() <= 1.0);
            assertTrue(m.messageComplexity() > 0);
            assertTrue(m.effectualFanout() > 0);
            assertTrue(m.reliability() == 0 || m.reliability() == 1);
        }
    }

    // --- Connectivity enforcement ---

    @Nested
    class ConnectivityEnforcement {

        @Test
        void ringAlwaysConnected_noRetryNeeded() {
            long seed = 12345L;
            long result = ExperimentRunner.findConnectedSeed(TopologyForm.RING, 30, K, seed, 0);
            assertEquals(seed, result, "Ring is always connected; should return startSeed unchanged");
        }

        @Test
        void scaleFreeAlwaysConnected_noRetryNeeded() {
            long seed = 67890L;
            long result = ExperimentRunner.findConnectedSeed(TopologyForm.SCALE_FREE, 30, K, seed, 0);
            assertEquals(seed, result, "Scale-free is connected by construction; should return startSeed unchanged");
        }

        @Test
        void findConnectedSeed_returnsConnectedGraph_random() {
            long connectedSeed = ExperimentRunner.findConnectedSeed(
                TopologyForm.RANDOM, 50, K, BASE_SEED, 100
            );

            double p = (2.0 * K) / (50 - 1);
            Network network = AAron.random(50, p, connectedSeed);

            assertTrue(NetworkValidator.isConnected(network),
                "Graph built with returned seed must be connected");
        }

        @Test
        void findConnectedSeed_deterministic() {
            long seed1 = ExperimentRunner.findConnectedSeed(TopologyForm.RANDOM, 50, K, BASE_SEED, 100);
            long seed2 = ExperimentRunner.findConnectedSeed(TopologyForm.RANDOM, 50, K, BASE_SEED, 100);
            assertEquals(seed1, seed2, "Same inputs must return the same connected seed");
        }

        @Test
        void allTrialNetworksAreConnected_random() {
            ExperimentConfig config = new ExperimentConfig(TopologyForm.RANDOM, 50, K, 0.8, 2);

            List<TrialResult> results = ExperimentRunner.run(config, 3, 2, BASE_SEED);

            for (TrialResult r : results) {
                double p = (2.0 * K) / (r.nodeCount() - 1);
                Network network = AAron.random(r.nodeCount(), p, r.graphSeed());
                assertTrue(NetworkValidator.isConnected(network),
                    "Every trial must use a connected graph (seed=" + r.graphSeed() + ")");
            }
        }

        @Test
        void allTrialNetworksAreConnected_scaleFree() {
            ExperimentConfig config = new ExperimentConfig(TopologyForm.SCALE_FREE, 50, K, 0.8, 2);

            List<TrialResult> results = ExperimentRunner.run(config, 3, 2, BASE_SEED);

            for (TrialResult r : results) {
                Network network = AAron.scaleFree(r.nodeCount(), K, r.graphSeed());
                assertTrue(NetworkValidator.isConnected(network),
                    "Every scale-free trial must use a connected graph");
            }
        }

        @Test
        void smallWorldAlwaysConnected_noRetryNeeded() {
            long seed = 11111L;
            long result = ExperimentRunner.findConnectedSeed(TopologyForm.SMALL_WORLD, 30, K, seed, 0);
            assertEquals(seed, result, "Small-world is connected by construction; should return startSeed unchanged");
        }
    }

    // --- All topology types produce valid results ---

    @Test
    void randomTopology_producesValidResults() {
        ExperimentConfig config = new ExperimentConfig(TopologyForm.RANDOM, 50, K, 0.8, 2);
        List<TrialResult> results = ExperimentRunner.run(config, 2, 2, BASE_SEED);
        assertEquals(4, results.size());
        for (TrialResult r : results) {
            assertTrue(r.metrics().tEnd() >= 1);
        }
    }

    @Test
    void ringTopology_producesValidResults() {
        ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 50, K, 0.8, 2);
        List<TrialResult> results = ExperimentRunner.run(config, 2, 2, BASE_SEED);
        assertEquals(4, results.size());
        for (TrialResult r : results) {
            assertTrue(r.metrics().tEnd() >= 1);
        }
    }

    @Test
    void scaleFreeTopology_producesValidResults() {
        ExperimentConfig config = new ExperimentConfig(TopologyForm.SCALE_FREE, 50, K, 0.8, 2);
        List<TrialResult> results = ExperimentRunner.run(config, 2, 2, BASE_SEED);
        assertEquals(4, results.size());
        for (TrialResult r : results) {
            assertTrue(r.metrics().tEnd() >= 1);
        }
    }

    @Test
    void smallWorldTopology_producesValidResults() {
        ExperimentConfig config = new ExperimentConfig(TopologyForm.SMALL_WORLD, 50, K, 0.8, 2);
        List<TrialResult> results = ExperimentRunner.run(config, 2, 2, BASE_SEED);
        assertEquals(4, results.size());
        for (TrialResult r : results) {
            assertTrue(r.metrics().tEnd() >= 1);
        }
    }

    // --- Parameter validation ---

    @Test
    void invalidGraphInstances_throwsException() {
        ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 30, K, 1.0, 2);
        assertThrows(IllegalArgumentException.class,
            () -> ExperimentRunner.run(config, 0, 1, BASE_SEED));
    }

    @Test
    void invalidTrialsPerGraph_throwsException() {
        ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 30, K, 1.0, 2);
        assertThrows(IllegalArgumentException.class,
            () -> ExperimentRunner.run(config, 1, 0, BASE_SEED));
    }

    @Test
    void negativeMaxRetries_throwsException() {
        ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 30, K, 1.0, 2);
        assertThrows(IllegalArgumentException.class,
            () -> ExperimentRunner.run(config, 1, 1, BASE_SEED, -1));
    }

    // --- CsvWriter ---

    @Nested
    class CsvWriterTests {

        @Test
        void csvHeader_matchesDocumentedFormat() {
            List<TrialResult> results = List.of();
            String csv = CsvWriter.toCsv(results);

            String expectedHeader = "topology,nodeCount,k,viewFraction,fanOut,baseSeed,graphSeed,simulationSeed,trial," +
                                    "T_end,Omega,M,alpha,L_0.5,L_0.9,L_1.0,F_eff,R_run";
            assertEquals(expectedHeader, csv.split("\n")[0]);
        }

        @Test
        void csvRowCount_matchesTrialCount() {
            ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 30, K, 1.0, 2);
            List<TrialResult> results = ExperimentRunner.run(config, 2, 3, BASE_SEED);

            String csv = CsvWriter.toCsv(results);
            String[] lines = csv.split("\n");

            assertEquals(1 + results.size(), lines.length, "Header + one row per trial");
        }

        @Test
        void csvRowFields_correctColumnCount() {
            ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 30, K, 1.0, 2);
            List<TrialResult> results = ExperimentRunner.run(config, 1, 1, BASE_SEED);

            String csv = CsvWriter.toCsv(results);
            String[] lines = csv.split("\n");

            String[] headerFields = lines[0].split(",");
            String[] dataFields = lines[1].split(",");

            assertEquals(18, headerFields.length, "Header should have 18 columns");
            assertEquals(18, dataFields.length, "Data row should have 18 columns");
        }

        @Test
        void csvTopologyNames_correctFormat() {
            ExperimentConfig randomConfig = new ExperimentConfig(TopologyForm.RANDOM, 30, K, 1.0, 2);
            ExperimentConfig ringConfig = new ExperimentConfig(TopologyForm.RING, 30, K, 1.0, 2);
            ExperimentConfig sfConfig = new ExperimentConfig(TopologyForm.SCALE_FREE, 30, K, 1.0, 2);

            String randomCsv = CsvWriter.toCsv(ExperimentRunner.run(randomConfig, 1, 1, BASE_SEED));
            String ringCsv = CsvWriter.toCsv(ExperimentRunner.run(ringConfig, 1, 1, BASE_SEED));
            String sfCsv = CsvWriter.toCsv(ExperimentRunner.run(sfConfig, 1, 1, BASE_SEED));

            assertTrue(randomCsv.split("\n")[1].startsWith("Random,"));
            assertTrue(ringCsv.split("\n")[1].startsWith("Ring,"));
            assertTrue(sfCsv.split("\n")[1].startsWith("ScaleFree,"));

            ExperimentConfig swConfig = new ExperimentConfig(TopologyForm.SMALL_WORLD, 30, K, 1.0, 2);
            String swCsv = CsvWriter.toCsv(ExperimentRunner.run(swConfig, 1, 1, BASE_SEED));
            assertTrue(swCsv.split("\n")[1].startsWith("SmallWorld,"));
        }

        @Test
        void csvTrialIndex_matchesResultTrialField() {
            ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 30, K, 1.0, 2);
            List<TrialResult> results = ExperimentRunner.run(config, 1, 3, BASE_SEED);

            String csv = CsvWriter.toCsv(results);
            String[] lines = csv.split("\n");

            for (int i = 0; i < results.size(); i++) {
                String[] fields = lines[i + 1].split(",");
                assertEquals(String.valueOf(i + 1), fields[8], "Trial index in CSV should be sequential");
            }
        }

        @Test
        void writeToWriter_matchesToCsv() throws Exception {
            ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 30, K, 1.0, 2);
            List<TrialResult> results = ExperimentRunner.run(config, 1, 2, BASE_SEED);

            StringWriter sw = new StringWriter();
            CsvWriter.write(results, sw);

            assertEquals(CsvWriter.toCsv(results), sw.toString());
        }

        @Test
        void csvValues_areParseable() {
            ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 50, K, 0.6, 2);
            List<TrialResult> results = ExperimentRunner.run(config, 1, 1, BASE_SEED);

            String csv = CsvWriter.toCsv(results);
            String[] fields = csv.split("\n")[1].split(",");

            // Verify numeric fields are parseable
            assertDoesNotThrow(() -> Integer.parseInt(fields[1]));    // nodeCount
            assertDoesNotThrow(() -> Integer.parseInt(fields[2]));    // k
            assertDoesNotThrow(() -> Double.parseDouble(fields[3]));  // viewFraction
            assertDoesNotThrow(() -> Integer.parseInt(fields[4]));    // fanOut
            assertDoesNotThrow(() -> Long.parseLong(fields[5]));      // baseSeed
            assertDoesNotThrow(() -> Long.parseLong(fields[6]));      // graphSeed
            assertDoesNotThrow(() -> Long.parseLong(fields[7]));      // simulationSeed
            assertDoesNotThrow(() -> Integer.parseInt(fields[8]));    // trial
            assertDoesNotThrow(() -> Integer.parseInt(fields[9]));    // T_end
            assertDoesNotThrow(() -> Integer.parseInt(fields[10]));   // Omega
            assertDoesNotThrow(() -> Double.parseDouble(fields[11])); // M
            assertDoesNotThrow(() -> Double.parseDouble(fields[12])); // alpha
            assertDoesNotThrow(() -> Integer.parseInt(fields[13]));   // L_0.5
            assertDoesNotThrow(() -> Integer.parseInt(fields[14]));   // L_0.9
            assertDoesNotThrow(() -> Integer.parseInt(fields[15]));   // L_1.0
            assertDoesNotThrow(() -> Double.parseDouble(fields[16])); // F_eff
            assertDoesNotThrow(() -> Integer.parseInt(fields[17]));   // R_run
        }

        // --- Byzantine CSV ---

        @Test
        void byzantineCsvHeader_matchesDocumentedFormat() {
            String csv = CsvWriter.toByzantineCsv(List.of());

            String expectedHeader = "topology,nodeCount,k,viewFraction,fanOut," +
                "baseSeed,graphSeed,simulationSeed,trial," +
                "failureProbability,failedNodeCount,failedFraction,activeNodeCount," +
                "sourceNode,sourceForcedActive," +
                "T_end,Omega,M,alpha,L_0.5,L_0.9,L_1.0,F_eff,R_run";
            assertEquals(expectedHeader, csv.split("\n")[0]);
        }

        @Test
        void byzantineCsvRowFields_correctColumnCount() {
            ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 30, K, 1.0, 2);
            List<TrialResult> results = ExperimentRunner.run(config, 1, 1, BASE_SEED, 0.30);

            String csv = CsvWriter.toByzantineCsv(results);
            String[] lines = csv.split("\n");

            String[] headerFields = lines[0].split(",");
            String[] dataFields = lines[1].split(",");

            assertEquals(24, headerFields.length, "Byzantine header should have 24 columns");
            assertEquals(24, dataFields.length, "Byzantine data row should have 24 columns");
        }

        @Test
        void byzantineCsvRowCount_matchesTrialCount() {
            ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 30, K, 1.0, 2);
            List<TrialResult> results = ExperimentRunner.run(config, 2, 3, BASE_SEED, 0.15);

            String csv = CsvWriter.toByzantineCsv(results);
            String[] lines = csv.split("\n");

            assertEquals(1 + results.size(), lines.length, "Header + one row per trial");
        }

        @Test
        void byzantineCsvValues_areParseable() {
            ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 50, K, 0.8, 2);
            List<TrialResult> results = ExperimentRunner.run(config, 1, 1, BASE_SEED, 0.30);

            String csv = CsvWriter.toByzantineCsv(results);
            String[] fields = csv.split("\n")[1].split(",");

            assertDoesNotThrow(() -> Double.parseDouble(fields[9]));   // failureProbability
            assertDoesNotThrow(() -> Integer.parseInt(fields[10]));    // failedNodeCount
            assertDoesNotThrow(() -> Double.parseDouble(fields[11]));  // failedFraction
            assertDoesNotThrow(() -> Integer.parseInt(fields[12]));    // activeNodeCount
            assertDoesNotThrow(() -> Integer.parseInt(fields[13]));    // sourceNode
            // field[14] is sourceForcedActive (boolean as "true"/"false")
            assertTrue(fields[14].equals("true") || fields[14].equals("false"),
                "sourceForcedActive must be 'true' or 'false'");
            assertDoesNotThrow(() -> Integer.parseInt(fields[15]));    // T_end
            assertDoesNotThrow(() -> Integer.parseInt(fields[16]));    // Omega
            assertDoesNotThrow(() -> Double.parseDouble(fields[17]));  // M
            assertDoesNotThrow(() -> Double.parseDouble(fields[18]));  // alpha
            assertDoesNotThrow(() -> Integer.parseInt(fields[19]));    // L_0.5
            assertDoesNotThrow(() -> Integer.parseInt(fields[20]));    // L_0.9
            assertDoesNotThrow(() -> Integer.parseInt(fields[21]));    // L_1.0
            assertDoesNotThrow(() -> Double.parseDouble(fields[22]));  // F_eff
            assertDoesNotThrow(() -> Integer.parseInt(fields[23]));    // R_run
        }

        @Test
        void byzantineCsv_failureProbabilityMatchesTrialResult() {
            ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 30, K, 1.0, 2);
            double fp = 0.15;
            List<TrialResult> results = ExperimentRunner.run(config, 1, 1, BASE_SEED, fp);

            String csv = CsvWriter.toByzantineCsv(results);
            String[] fields = csv.split("\n")[1].split(",");

            assertEquals(fp, Double.parseDouble(fields[9]), 1e-12,
                "failureProbability in CSV must match trial result");
        }

        @Test
        void byzantineWriteToWriter_matchesToByzantineCsv() throws Exception {
            ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 30, K, 1.0, 2);
            List<TrialResult> results = ExperimentRunner.run(config, 1, 2, BASE_SEED, 0.15);

            StringWriter sw = new StringWriter();
            CsvWriter.writeByzantine(results, sw);

            assertEquals(CsvWriter.toByzantineCsv(results), sw.toString());
        }
    }

    // --- Byzantine ExperimentRunner ---

    @Nested
    class ByzantineRunnerTests {

        @Test
        void byzantineRun_deterministic() {
            ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 50, K, 0.8, 2);
            double fp = 0.30;

            List<TrialResult> run1 = ExperimentRunner.run(config, 2, 3, BASE_SEED, fp);
            List<TrialResult> run2 = ExperimentRunner.run(config, 2, 3, BASE_SEED, fp);

            assertEquals(run1.size(), run2.size());
            for (int i = 0; i < run1.size(); i++) {
                assertEquals(run1.get(i).metrics(), run2.get(i).metrics());
                assertEquals(run1.get(i).failedNodeCount(), run2.get(i).failedNodeCount());
                assertEquals(run1.get(i).sourceForcedActive(), run2.get(i).sourceForcedActive());
            }
        }

        @Test
        void byzantineRun_failureProbabilityRecorded() {
            ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 30, K, 1.0, 2);
            double fp = 0.15;

            List<TrialResult> results = ExperimentRunner.run(config, 1, 2, BASE_SEED, fp);

            for (TrialResult r : results) {
                assertEquals(fp, r.failureProbability());
            }
        }

        @Test
        void byzantineRun_failedFieldsConsistent() {
            ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 50, K, 1.0, 3);
            double fp = 0.30;

            List<TrialResult> results = ExperimentRunner.run(config, 2, 3, BASE_SEED, fp);

            for (TrialResult r : results) {
                assertEquals(r.nodeCount() - r.failedNodeCount(), r.activeNodeCount(),
                    "activeNodeCount must equal nodeCount - failedNodeCount");
                assertEquals((double) r.failedNodeCount() / r.nodeCount(), r.failedFraction(), 1e-12,
                    "failedFraction must equal failedNodeCount / nodeCount");
                assertTrue(r.failedNodeCount() >= 0);
                assertTrue(r.failedNodeCount() < r.nodeCount(),
                    "At least the source must be active");
            }
        }

        @Test
        void baselineRun_zeroFailureFields() {
            ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 30, K, 1.0, 2);

            List<TrialResult> results = ExperimentRunner.run(config, 1, 2, BASE_SEED);

            for (TrialResult r : results) {
                assertEquals(0.0, r.failureProbability());
                assertEquals(0, r.failedNodeCount());
                assertEquals(0.0, r.failedFraction());
                assertEquals(r.nodeCount(), r.activeNodeCount());
                assertFalse(r.sourceForcedActive());
            }
        }
    }

    // --- Immutability ---

    @Test
    void resultListIsImmutable() {
        ExperimentConfig config = new ExperimentConfig(TopologyForm.RING, 30, K, 1.0, 2);
        List<TrialResult> results = ExperimentRunner.run(config, 1, 1, BASE_SEED);
        assertThrows(UnsupportedOperationException.class, () -> results.add(null));
    }

    // --- Seed generation is independent of retries ---

    @Test
    void seedGenerationUpfront_independentOfRetries() {
        ExperimentConfig ringConfig = new ExperimentConfig(TopologyForm.RING, 30, K, 1.0, 2);

        List<TrialResult> withZeroRetries = ExperimentRunner.run(ringConfig, 2, 2, BASE_SEED, 0);
        List<TrialResult> withManyRetries = ExperimentRunner.run(ringConfig, 2, 2, BASE_SEED, 100);

        for (int i = 0; i < withZeroRetries.size(); i++) {
            assertEquals(withZeroRetries.get(i).simulationSeed(),
                         withManyRetries.get(i).simulationSeed(),
                "Simulation seeds must be identical regardless of maxRetries parameter");
        }
    }
}
