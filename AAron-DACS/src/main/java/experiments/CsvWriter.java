package experiments;

import aaron.TopologyForm;
import metrics.TrialMetrics;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;

/**
 * Writes per-trial experiment results to CSV format.
 *
 * Each row represents one simulation trial. The header matches the format
 * defined in AAron.md. The k column records the base connectivity parameter
 * for full reproducibility.
 */
public final class CsvWriter {

    private static final String HEADER =
        "topology,nodeCount,k,viewFraction,fanOut,baseSeed,graphSeed,simulationSeed,trial," +
        "T_end,Omega,M,alpha,L_0.5,L_0.9,L_1.0,F_eff,R_run";

    private static final String BYZANTINE_HEADER =
        "topology,nodeCount,k,viewFraction,fanOut,baseSeed,graphSeed,simulationSeed,trial," +
        "failureProbability,failedNodeCount,failedFraction,activeNodeCount,sourceNode,sourceForcedActive," +
        "T_end,Omega,M,alpha,L_0.5,L_0.9,L_1.0,F_eff,R_run";

    private CsvWriter() {}

    /**
     * Writes trial results as baseline CSV (18 columns) to the given writer.
     *
     * @param results the trial results to write
     * @param writer the output destination
     * @throws IOException if an I/O error occurs
     */
    public static void write(List<TrialResult> results, Writer writer) throws IOException {
        writer.write(HEADER);
        writer.write('\n');
        for (TrialResult result : results) {
            writer.write(formatRow(result));
            writer.write('\n');
        }
    }

    /**
     * Writes trial results as Byzantine CSV (24 columns) to the given writer.
     * Includes failureProbability, failedNodeCount, failedFraction,
     * activeNodeCount, sourceNode, and sourceForcedActive columns.
     *
     * @param results the trial results to write
     * @param writer the output destination
     * @throws IOException if an I/O error occurs
     */
    public static void writeByzantine(List<TrialResult> results, Writer writer) throws IOException {
        writer.write(BYZANTINE_HEADER);
        writer.write('\n');
        for (TrialResult result : results) {
            writer.write(formatByzantineRow(result));
            writer.write('\n');
        }
    }

    /**
     * Returns trial results as a baseline CSV string.
     *
     * @param results the trial results to format
     * @return the complete CSV content including header
     */
    public static String toCsv(List<TrialResult> results) {
        StringWriter sw = new StringWriter();
        try {
            write(results, sw);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sw.toString();
    }

    /**
     * Returns trial results as a Byzantine CSV string.
     *
     * @param results the trial results to format
     * @return the complete CSV content including header
     */
    public static String toByzantineCsv(List<TrialResult> results) {
        StringWriter sw = new StringWriter();
        try {
            writeByzantine(results, sw);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sw.toString();
    }

    private static String formatRow(TrialResult r) {
        TrialMetrics m = r.metrics();
        return String.join(",",
            formatTopology(r.topologyType()),
            String.valueOf(r.nodeCount()),
            String.valueOf(r.k()),
            String.valueOf(r.viewFraction()),
            String.valueOf(r.fanOut()),
            String.valueOf(r.baseSeed()),
            String.valueOf(r.graphSeed()),
            String.valueOf(r.simulationSeed()),
            String.valueOf(r.trial()),
            String.valueOf(m.tEnd()),
            String.valueOf(m.omega()),
            String.valueOf(m.messageComplexity()),
            String.valueOf(m.alpha()),
            String.valueOf(m.latency50()),
            String.valueOf(m.latency90()),
            String.valueOf(m.latency100()),
            String.valueOf(m.effectualFanout()),
            String.valueOf(m.reliability())
        );
    }

    private static String formatByzantineRow(TrialResult r) {
        TrialMetrics m = r.metrics();
        return String.join(",",
            formatTopology(r.topologyType()),
            String.valueOf(r.nodeCount()),
            String.valueOf(r.k()),
            String.valueOf(r.viewFraction()),
            String.valueOf(r.fanOut()),
            String.valueOf(r.baseSeed()),
            String.valueOf(r.graphSeed()),
            String.valueOf(r.simulationSeed()),
            String.valueOf(r.trial()),
            String.valueOf(r.failureProbability()),
            String.valueOf(r.failedNodeCount()),
            String.valueOf(r.failedFraction()),
            String.valueOf(r.activeNodeCount()),
            String.valueOf(r.sourceNode()),
            String.valueOf(r.sourceForcedActive()),
            String.valueOf(m.tEnd()),
            String.valueOf(m.omega()),
            String.valueOf(m.messageComplexity()),
            String.valueOf(m.alpha()),
            String.valueOf(m.latency50()),
            String.valueOf(m.latency90()),
            String.valueOf(m.latency100()),
            String.valueOf(m.effectualFanout()),
            String.valueOf(m.reliability())
        );
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
