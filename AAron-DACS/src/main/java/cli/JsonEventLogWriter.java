package cli;

import aaron.Edge;
import aaron.TopologyForm;
import metrics.TrialMetrics;
import simulation.Message;
import simulation.RoundResult;
import simulation.SimulationConfig;
import simulation.SimulationResult;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Serializes a simulation result and its metrics to the JSON event log format
 * defined in AAron.md.
 *
 * The JSON event log is used for visualization replay and debugging. It contains
 * the full configuration, network structure, source node, per-round events, and
 * computed metrics.
 */
public final class JsonEventLogWriter {

    private JsonEventLogWriter() {}

    /**
     * Converts a simulation result and metrics to a pretty-printed JSON string.
     *
     * @param result the complete simulation output
     * @param metrics the computed per-trial metrics
     * @return the JSON event log as a string
     */
    public static String toJson(SimulationResult result, TrialMetrics metrics) {
        JsonObject root = new JsonObject();

        root.add("configuration", buildConfiguration(result.config()));
        root.add("network", buildNetwork(result));
        root.addProperty("sourceNode", result.sourceNode());

        if (result.config().failureProbability() > 0.0) {
            root.addProperty("failureProbability", result.config().failureProbability());
            root.addProperty("sourceForcedActive", result.sourceForcedActive());

            JsonArray failedArr = new JsonArray();
            List<Integer> sortedFailed = new ArrayList<>(result.failedNodes());
            Collections.sort(sortedFailed);
            for (int node : sortedFailed) {
                failedArr.add(node);
            }
            root.add("failedNodes", failedArr);
        }

        root.add("rounds", buildRounds(result.rounds()));
        root.add("result", buildMetrics(metrics));

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(root);
    }

    private static JsonObject buildConfiguration(SimulationConfig config) {
        JsonObject obj = new JsonObject();
        obj.addProperty("topologyType", formatTopology(config.topologyType()));
        obj.addProperty("nodeCount", config.nodeCount());
        obj.addProperty("viewFraction", config.viewFraction());
        obj.addProperty("fanOut", config.fanOut());
        obj.addProperty("graphSeed", config.graphSeed());
        obj.addProperty("simulationSeed", config.simulationSeed());
        obj.addProperty("k", config.k());
        obj.addProperty("m", config.m());
        obj.addProperty("p", config.p());
        obj.addProperty("beta", config.beta());
        obj.addProperty("maxRounds", config.maxRounds());
        obj.addProperty("stableRounds", config.stableRounds());
        if (config.failureProbability() > 0.0) {
            obj.addProperty("failureProbability", config.failureProbability());
        }
        return obj;
    }

    private static JsonObject buildNetwork(SimulationResult result) {
        JsonObject obj = new JsonObject();

        List<Edge> sortedEdges = new ArrayList<>(result.network().edges());
        sortedEdges.sort(Comparator.comparingInt(Edge::from).thenComparingInt(Edge::to));

        JsonArray edges = new JsonArray();
        for (Edge edge : sortedEdges) {
            JsonArray pair = new JsonArray();
            pair.add(edge.from());
            pair.add(edge.to());
            edges.add(pair);
        }
        obj.add("edges", edges);
        return obj;
    }

    private static JsonArray buildRounds(List<RoundResult> rounds) {
        JsonArray arr = new JsonArray();
        for (RoundResult round : rounds) {
            JsonObject obj = new JsonObject();
            obj.addProperty("round", round.round());

            JsonArray messages = new JsonArray();
            for (Message msg : round.messages()) {
                JsonObject msgObj = new JsonObject();
                msgObj.addProperty("sender", msg.sender());
                msgObj.addProperty("receiver", msg.receiver());
                msgObj.addProperty("newInfection", msg.newInfection());
                messages.add(msgObj);
            }
            obj.add("messages", messages);

            JsonArray newlyInformed = new JsonArray();
            for (int nodeId : round.newlyInformed()) {
                newlyInformed.add(nodeId);
            }
            obj.add("newlyInformed", newlyInformed);

            obj.addProperty("totalInformed", round.totalInformed());
            obj.addProperty("messageCount", round.messageCount());

            arr.add(obj);
        }
        return arr;
    }

    private static JsonObject buildMetrics(TrialMetrics metrics) {
        JsonObject obj = new JsonObject();
        obj.addProperty("T_end", metrics.tEnd());
        obj.addProperty("Omega", metrics.omega());
        obj.addProperty("M", metrics.messageComplexity());
        obj.addProperty("alpha", metrics.alpha());
        obj.addProperty("L_0.5", metrics.latency50());
        obj.addProperty("L_0.9", metrics.latency90());
        obj.addProperty("L_1.0", metrics.latency100());
        obj.addProperty("F_eff", metrics.effectualFanout());
        obj.addProperty("R_run", metrics.reliability());
        return obj;
    }

    private static String formatTopology(TopologyForm type) {
        return switch (type) {
            case RANDOM -> "Random";
            case RING -> "Ring";
            case SCALE_FREE -> "ScaleFree";
            case SMALL_WORLD -> "SmallWorld";
        };
    }
}
