package cli;

import aaron.TopologyForm;
import metrics.MetricsCalculator;
import metrics.TrialMetrics;
import simulation.SimulationResult;
import simulation.Simulator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonEventLogWriterTest {

    private static SimulationResult runTrial(TopologyForm topology) {
        return Simulator.simulate(topology, 20, 1.0, 2, 42L, 100L);
    }

    // --- JSON structure ---

    @Test
    void jsonContainsAllTopLevelKeys() {
        SimulationResult result = runTrial(TopologyForm.RING);
        TrialMetrics metrics = MetricsCalculator.compute(result);
        String json = JsonEventLogWriter.toJson(result, metrics);

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        assertTrue(root.has("configuration"));
        assertTrue(root.has("network"));
        assertTrue(root.has("sourceNode"));
        assertTrue(root.has("rounds"));
        assertTrue(root.has("result"));
    }

    // --- Configuration block ---

    @Test
    void configurationContainsAll12Fields() {
        SimulationResult result = runTrial(TopologyForm.RING);
        TrialMetrics metrics = MetricsCalculator.compute(result);
        String json = JsonEventLogWriter.toJson(result, metrics);

        JsonObject config = JsonParser.parseString(json).getAsJsonObject()
            .getAsJsonObject("configuration");

        assertEquals("Ring", config.get("topologyType").getAsString());
        assertEquals(20, config.get("nodeCount").getAsInt());
        assertEquals(1.0, config.get("viewFraction").getAsDouble());
        assertEquals(2, config.get("fanOut").getAsInt());
        assertEquals(42L, config.get("graphSeed").getAsLong());
        assertEquals(100L, config.get("simulationSeed").getAsLong());
        assertEquals(Simulator.DEFAULT_K, config.get("k").getAsInt());
        assertEquals(Simulator.DEFAULT_K, config.get("m").getAsInt());
        assertTrue(config.has("p"));
        assertTrue(config.has("beta"));
        assertEquals(Simulator.MAX_ROUNDS, config.get("maxRounds").getAsInt());
        assertEquals(Simulator.STABLE_ROUNDS, config.get("stableRounds").getAsInt());
    }

    @Test
    void configurationTopologyNames() {
        for (TopologyForm topology : TopologyForm.values()) {
            SimulationResult result = runTrial(topology);
            TrialMetrics metrics = MetricsCalculator.compute(result);
            String json = JsonEventLogWriter.toJson(result, metrics);

            JsonObject config = JsonParser.parseString(json).getAsJsonObject()
                .getAsJsonObject("configuration");

            String expected = switch (topology) {
                case RANDOM -> "Random";
                case RING -> "Ring";
                case SCALE_FREE -> "ScaleFree";
                case SMALL_WORLD -> "SmallWorld";
            };
            assertEquals(expected, config.get("topologyType").getAsString());
        }
    }

    // --- Network block ---

    @Test
    void networkContainsEdgesArray() {
        SimulationResult result = runTrial(TopologyForm.RING);
        TrialMetrics metrics = MetricsCalculator.compute(result);
        String json = JsonEventLogWriter.toJson(result, metrics);

        JsonObject network = JsonParser.parseString(json).getAsJsonObject()
            .getAsJsonObject("network");

        assertTrue(network.has("edges"));
        JsonArray edges = network.getAsJsonArray("edges");
        assertTrue(edges.size() > 0);

        // Each edge is a [from, to] pair
        JsonArray firstEdge = edges.get(0).getAsJsonArray();
        assertEquals(2, firstEdge.size());
    }

    @Test
    void networkEdgeCountMatchesSimulation() {
        SimulationResult result = runTrial(TopologyForm.RING);
        TrialMetrics metrics = MetricsCalculator.compute(result);
        String json = JsonEventLogWriter.toJson(result, metrics);

        JsonArray edges = JsonParser.parseString(json).getAsJsonObject()
            .getAsJsonObject("network")
            .getAsJsonArray("edges");

        assertEquals(result.network().edgeCount(), edges.size());
    }

    @Test
    void networkEdgesAreSorted() {
        SimulationResult result = runTrial(TopologyForm.RING);
        TrialMetrics metrics = MetricsCalculator.compute(result);
        String json = JsonEventLogWriter.toJson(result, metrics);

        JsonArray edges = JsonParser.parseString(json).getAsJsonObject()
            .getAsJsonObject("network")
            .getAsJsonArray("edges");

        for (int i = 1; i < edges.size(); i++) {
            JsonArray prev = edges.get(i - 1).getAsJsonArray();
            JsonArray curr = edges.get(i).getAsJsonArray();
            int prevFrom = prev.get(0).getAsInt();
            int prevTo = prev.get(1).getAsInt();
            int currFrom = curr.get(0).getAsInt();
            int currTo = curr.get(1).getAsInt();

            assertTrue(prevFrom < currFrom || (prevFrom == currFrom && prevTo <= currTo),
                "Edges should be sorted by (from, to)");
        }
    }

    // --- Source node ---

    @Test
    void sourceNodeMatchesSimulationResult() {
        SimulationResult result = runTrial(TopologyForm.RING);
        TrialMetrics metrics = MetricsCalculator.compute(result);
        String json = JsonEventLogWriter.toJson(result, metrics);

        int sourceNode = JsonParser.parseString(json).getAsJsonObject()
            .get("sourceNode").getAsInt();

        assertEquals(result.sourceNode(), sourceNode);
    }

    // --- Rounds ---

    @Test
    void roundsCountMatchesSimulation() {
        SimulationResult result = runTrial(TopologyForm.RING);
        TrialMetrics metrics = MetricsCalculator.compute(result);
        String json = JsonEventLogWriter.toJson(result, metrics);

        JsonArray rounds = JsonParser.parseString(json).getAsJsonObject()
            .getAsJsonArray("rounds");

        assertEquals(result.totalRounds(), rounds.size());
    }

    @Test
    void roundStructureContainsAllFields() {
        SimulationResult result = runTrial(TopologyForm.RING);
        TrialMetrics metrics = MetricsCalculator.compute(result);
        String json = JsonEventLogWriter.toJson(result, metrics);

        JsonObject round = JsonParser.parseString(json).getAsJsonObject()
            .getAsJsonArray("rounds")
            .get(0).getAsJsonObject();

        assertTrue(round.has("round"));
        assertTrue(round.has("messages"));
        assertTrue(round.has("newlyInformed"));
        assertTrue(round.has("totalInformed"));
        assertTrue(round.has("messageCount"));
    }

    @Test
    void messageStructureContainsAllFields() {
        SimulationResult result = runTrial(TopologyForm.RING);
        TrialMetrics metrics = MetricsCalculator.compute(result);
        String json = JsonEventLogWriter.toJson(result, metrics);

        JsonObject msg = JsonParser.parseString(json).getAsJsonObject()
            .getAsJsonArray("rounds")
            .get(0).getAsJsonObject()
            .getAsJsonArray("messages")
            .get(0).getAsJsonObject();

        assertTrue(msg.has("sender"));
        assertTrue(msg.has("receiver"));
        assertTrue(msg.has("newInfection"));
    }

    @Test
    void roundNumbersAreSequential() {
        SimulationResult result = runTrial(TopologyForm.RING);
        TrialMetrics metrics = MetricsCalculator.compute(result);
        String json = JsonEventLogWriter.toJson(result, metrics);

        JsonArray rounds = JsonParser.parseString(json).getAsJsonObject()
            .getAsJsonArray("rounds");

        for (int i = 0; i < rounds.size(); i++) {
            assertEquals(i + 1, rounds.get(i).getAsJsonObject().get("round").getAsInt());
        }
    }

    // --- Result (metrics) block ---

    @Test
    void resultContainsAllMetricFields() {
        SimulationResult result = runTrial(TopologyForm.RING);
        TrialMetrics metrics = MetricsCalculator.compute(result);
        String json = JsonEventLogWriter.toJson(result, metrics);

        JsonObject resultObj = JsonParser.parseString(json).getAsJsonObject()
            .getAsJsonObject("result");

        assertTrue(resultObj.has("T_end"));
        assertTrue(resultObj.has("Omega"));
        assertTrue(resultObj.has("M"));
        assertTrue(resultObj.has("alpha"));
        assertTrue(resultObj.has("L_0.5"));
        assertTrue(resultObj.has("L_0.9"));
        assertTrue(resultObj.has("L_1.0"));
        assertTrue(resultObj.has("F_eff"));
        assertTrue(resultObj.has("R_run"));
    }

    @Test
    void resultMetricsMatchComputedValues() {
        SimulationResult result = runTrial(TopologyForm.RING);
        TrialMetrics metrics = MetricsCalculator.compute(result);
        String json = JsonEventLogWriter.toJson(result, metrics);

        JsonObject resultObj = JsonParser.parseString(json).getAsJsonObject()
            .getAsJsonObject("result");

        assertEquals(metrics.tEnd(), resultObj.get("T_end").getAsInt());
        assertEquals(metrics.omega(), resultObj.get("Omega").getAsInt());
        assertEquals(metrics.messageComplexity(), resultObj.get("M").getAsDouble(), 1e-10);
        assertEquals(metrics.alpha(), resultObj.get("alpha").getAsDouble(), 1e-10);
        assertEquals(metrics.latency50(), resultObj.get("L_0.5").getAsInt());
        assertEquals(metrics.latency90(), resultObj.get("L_0.9").getAsInt());
        assertEquals(metrics.latency100(), resultObj.get("L_1.0").getAsInt());
        assertEquals(metrics.effectualFanout(), resultObj.get("F_eff").getAsDouble(), 1e-10);
        assertEquals(metrics.reliability(), resultObj.get("R_run").getAsInt());
    }

    // --- Determinism ---

    @Test
    void sameInputProducesIdenticalJson() {
        SimulationResult result1 = runTrial(TopologyForm.RING);
        TrialMetrics metrics1 = MetricsCalculator.compute(result1);
        String json1 = JsonEventLogWriter.toJson(result1, metrics1);

        SimulationResult result2 = runTrial(TopologyForm.RING);
        TrialMetrics metrics2 = MetricsCalculator.compute(result2);
        String json2 = JsonEventLogWriter.toJson(result2, metrics2);

        assertEquals(json1, json2);
    }

    // --- All topologies produce valid JSON ---

    @Test
    void allTopologiesProduceValidJson() {
        for (TopologyForm topology : TopologyForm.values()) {
            SimulationResult result = runTrial(topology);
            TrialMetrics metrics = MetricsCalculator.compute(result);
            String json = JsonEventLogWriter.toJson(result, metrics);

            assertDoesNotThrow(() -> JsonParser.parseString(json).getAsJsonObject(),
                "JSON should be valid for " + topology);
        }
    }

    // --- Byzantine JSON output ---

    private static SimulationResult runByzantineTrial() {
        return Simulator.simulate(TopologyForm.RING, 20, 3, 1.0, 2, 42L, 100L, 0.30);
    }

    @Test
    void byzantineJson_containsFailureProbability() {
        SimulationResult result = runByzantineTrial();
        TrialMetrics metrics = MetricsCalculator.compute(result);
        String json = JsonEventLogWriter.toJson(result, metrics);

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        assertTrue(root.has("failureProbability"),
            "Byzantine JSON must include failureProbability at root");
        assertEquals(0.30, root.get("failureProbability").getAsDouble(), 1e-10);
    }

    @Test
    void byzantineJson_containsSourceForcedActive() {
        SimulationResult result = runByzantineTrial();
        TrialMetrics metrics = MetricsCalculator.compute(result);
        String json = JsonEventLogWriter.toJson(result, metrics);

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        assertTrue(root.has("sourceForcedActive"),
            "Byzantine JSON must include sourceForcedActive");
    }

    @Test
    void byzantineJson_containsFailedNodesArray() {
        SimulationResult result = runByzantineTrial();
        TrialMetrics metrics = MetricsCalculator.compute(result);
        String json = JsonEventLogWriter.toJson(result, metrics);

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        assertTrue(root.has("failedNodes"),
            "Byzantine JSON must include failedNodes array");
        JsonArray failedArr = root.getAsJsonArray("failedNodes");
        assertEquals(result.failedNodes().size(), failedArr.size(),
            "failedNodes array size must match simulation result");
    }

    @Test
    void byzantineJson_failedNodesAreSorted() {
        SimulationResult result = runByzantineTrial();
        TrialMetrics metrics = MetricsCalculator.compute(result);
        String json = JsonEventLogWriter.toJson(result, metrics);

        JsonArray failedArr = JsonParser.parseString(json).getAsJsonObject()
            .getAsJsonArray("failedNodes");

        for (int i = 1; i < failedArr.size(); i++) {
            assertTrue(failedArr.get(i - 1).getAsInt() <= failedArr.get(i).getAsInt(),
                "failedNodes array must be sorted");
        }
    }

    @Test
    void byzantineJson_configContainsFailureProbability() {
        SimulationResult result = runByzantineTrial();
        TrialMetrics metrics = MetricsCalculator.compute(result);
        String json = JsonEventLogWriter.toJson(result, metrics);

        JsonObject config = JsonParser.parseString(json).getAsJsonObject()
            .getAsJsonObject("configuration");

        assertTrue(config.has("failureProbability"),
            "Byzantine config block must include failureProbability");
        assertEquals(0.30, config.get("failureProbability").getAsDouble(), 1e-10);
    }

    @Test
    void baselineJson_omitsByzantineFields() {
        SimulationResult result = runTrial(TopologyForm.RING);
        TrialMetrics metrics = MetricsCalculator.compute(result);
        String json = JsonEventLogWriter.toJson(result, metrics);

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        assertFalse(root.has("failureProbability"),
            "Baseline JSON must NOT include failureProbability");
        assertFalse(root.has("sourceForcedActive"),
            "Baseline JSON must NOT include sourceForcedActive");
        assertFalse(root.has("failedNodes"),
            "Baseline JSON must NOT include failedNodes");

        JsonObject config = root.getAsJsonObject("configuration");
        assertFalse(config.has("failureProbability"),
            "Baseline config must NOT include failureProbability");
    }
}
