package cli;

import aaron.TopologyForm;
import experiments.CsvWriter;
import experiments.ExperimentConfig;
import experiments.ExperimentRunner;
import experiments.ExperimentSuite;
import experiments.TrialResult;
import metrics.MetricsCalculator;
import metrics.TrialMetrics;
import simulation.SimulationResult;

import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Command-line interface for AAron gossip epidemic protocol simulation.
 *
 * <p>Provides six modes:
 * <ul>
 *   <li>{@code single}: one trial with explicit seeds, writes a JSON event log.</li>
 *   <li>{@code batch}:  G x S trials, writes a CSV summary.</li>
 *   <li>{@code rq1}:    full RQ1 experiment suite, writes a CSV.</li>
 *   <li>{@code rq2}:    full RQ2 experiment suite, writes a CSV.</li>
 *   <li>{@code rq1b}:   RQ1 with Byzantine failures (parallel), writes a CSV.</li>
 *   <li>{@code rq2b}:   RQ2 with Byzantine failures (parallel), writes a CSV.</li>
 * </ul>
 *
 * <p>The base connectivity parameter k is accepted as input (default 3); derived
 * parameters m and p are computed internally.
 * The CLI does not contain simulation logic; it parses input and triggers execution.
 */
@Command(name = "aaron",
         mixinStandardHelpOptions = false,
         version = "AAron 1.0.0",
         header = {
             "",
             "  AAron  ●─●─●  1.0.0 · gossip-epidemic protocol simulator",
             "",
             "  Simulates how information spreads through a network by gossip: one source",
             "  node starts informed; every round, each informed node pushes the message",
             "  to a random subset of its neighbors. AAron reports per-round events and",
             "  9 summary metrics covering speed, reach, cost, and reliability.",
             ""
         },
         synopsisHeading = "USAGE%n",
         customSynopsis = {"  aaron [command] [options]"},
         commandListHeading = "%nCOMMANDS%n",
         optionListHeading = "%nOPTIONS%n",
         footerHeading = "%nEXAMPLES%n",
         footer = {
             "  ./aaron -V                       print version",
             "  ./aaron single --help            one trial   (try this first)",
             "  ./aaron batch  --help            custom parameter sweep",
             "  ./aaron rq1 -o data/rq1.csv      reproduce thesis RQ1",
             "  ./aaron rq2 -o data/rq2.csv      reproduce thesis RQ2",
             "  ./aaron rq1b -o data/rq1b.csv    reproduce thesis RQ1B (Byzantine)",
             "  ./aaron rq2b -o data/rq2b.csv    reproduce thesis RQ2B (Byzantine)",
             ""
         },
         subcommands = {
             AaronCli.SingleCommand.class,
             AaronCli.BatchCommand.class,
             AaronCli.RQ1Command.class,
             AaronCli.RQ2Command.class,
             AaronCli.RQ1BCommand.class,
             AaronCli.RQ2BCommand.class
         })
public class AaronCli implements Runnable {

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "show this help")
    boolean helpRequested;

    @Option(names = {"-V", "--version"}, versionHelp = true, description = "show version")
    boolean versionRequested;

    @Override
    public void run() {
        CommandLine cmd = new CommandLine(this)
            .setColorScheme(CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.AUTO));
        configureHelpLayout(cmd);
        reorderHelpSections(cmd);
        cmd.usage(System.err);
    }

    // --- single ---

    @Command(name = "single",
             sortOptions = false,
             synopsisHeading = "USAGE%n",
             customSynopsis = {"  aaron single [options]", ""},
             description = {
                 "one simulation trial → JSON (network, every round, metrics)",
                 "Builds one network, runs one gossip dissemination, and writes a JSON",
                 "file containing the network, every round's events, and the 9 summary",
                 "metrics listed below.",
             },
             optionListHeading = "",
             footerHeading = "",
             footer = {
                 "",
                 "EXAMPLE",
                 "  ./aaron single -t RING -n 20 --k 3 --viewFraction 0.8 --fanOut 2 \\",
                 "                 --graphSeed 42 --simulationSeed 100 -o result.json",
                 "",
                 "METRICS  (fields in the JSON; also the per-trial CSV columns)",
                 "  T_end   rounds until the protocol converges",
                 "  Omega   total message sends across all rounds",
                 "  M       messages per target node            (Omega / (N - 1))",
                 "  alpha   final coverage                      (|informed| / N, in [0, 1])",
                 "  L_0.5   first round reaching ≥50%% coverage  (-1 if never)",
                 "  L_0.9   first round reaching ≥90%% coverage  (-1 if never)",
                 "  L_1.0   first round reaching 100%% coverage  (-1 if never)",
                 "  F_eff   mean messages per informed node     (Omega / |informed|)",
                 "  R_run   reliability flag                    (1 if alpha = 1.0, else 0)",
                 ""
             })
    static class SingleCommand implements Callable<Integer> {

        @ArgGroup(exclusive = false, multiplicity = "1", heading = "%nNETWORK%n")
        NetworkOpts network = new NetworkOpts();

        @ArgGroup(exclusive = false, multiplicity = "1", heading = "%nPROTOCOL%n")
        ProtocolOpts protocol = new ProtocolOpts();

        @ArgGroup(exclusive = false, multiplicity = "1", heading = "%nSEEDS%n")
        SeedOpts seeds = new SeedOpts();

        @ArgGroup(exclusive = false, heading = "%nOUTPUT%n")
        OutputOpts opts = new OutputOpts();

        static class NetworkOpts {
            @Option(names = {"-t", "--topology"}, paramLabel = "<name>", required = true,
                    description = "RANDOM | RING | SCALE_FREE | SMALL_WORLD")
            TopologyForm topology;

            @Option(names = {"-n", "--nodes"}, paramLabel = "<N>", required = true,
                    description = "number of nodes (min depends on k and topology)")
            int nodeCount;

            @Option(names = {"-k", "--k"}, paramLabel = "<k>", defaultValue = "3",
                    description = "base connectivity (default: ${DEFAULT-VALUE})")
            int k;
        }

        static class ProtocolOpts {
            @Option(names = {"--viewFraction"}, paramLabel = "<frac>", required = true,
                    description = "fraction of neighbors visible each round, in (0, 1]")
            double viewFraction;

            @Option(names = {"--fanOut"}, paramLabel = "<int>", required = true,
                    description = "peers contacted per round (>= 1)")
            int fanOut;

            @Option(names = {"--failureProbability"}, paramLabel = "<prob>", defaultValue = "0.0",
                    description = "probability each node is passive Byzantine (default: ${DEFAULT-VALUE})")
            double failureProbability;
        }

        static class SeedOpts {
            @Option(names = {"--graphSeed"}, paramLabel = "<seed>", required = true,
                    description = "seed for topology generation")
            long graphSeed;

            @Option(names = {"--simulationSeed"}, paramLabel = "<seed>", required = true,
                    description = "seed for source node and peer selection")
            long simulationSeed;
        }

        static class OutputOpts {
            @Option(names = {"-o", "--output"}, paramLabel = "<path>",
                    description = "output file (prints to stdout if omitted)")
            String path;

            @Option(names = {"-h", "--help"}, usageHelp = true, description = "show this help")
            boolean helpRequested;
        }

        @Override
        public Integer call() throws Exception {
            try {
                SimulationResult simResult = ExperimentRunner.runSingleTrial(
                    network.topology, network.nodeCount, network.k,
                    protocol.viewFraction, protocol.fanOut,
                    seeds.graphSeed, seeds.simulationSeed,
                    protocol.failureProbability
                );
                TrialMetrics metrics = MetricsCalculator.compute(simResult);
                String json = JsonEventLogWriter.toJson(simResult, metrics);

                if (opts.path != null) {
                    Files.writeString(Path.of(opts.path), json);
                } else {
                    System.out.println(json);
                }
                return 0;
            } catch (IllegalArgumentException | IllegalStateException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    // --- batch ---

    @Command(name = "batch",
             sortOptions = false,
             synopsisHeading = "USAGE%n",
             customSynopsis = {"  aaron batch [options]", ""},
             description = {
                 "custom parameter sweep → CSV (one row per trial)",
                 "Generates G distinct networks, runs S trials on each, and writes one",
                 "CSV row per trial. Every seed derives deterministically from --baseSeed,",
                 "so the same inputs always produce the same output. For the exact thesis",
                 "sweeps, use `rq1` and `rq2` instead. See `single --help` for the metric",
                 "columns in the CSV.",
             },
             optionListHeading = "",
             footerHeading = "%nEXAMPLE%n",
             footer = {
                 "  ./aaron batch -t RING -n 100 --k 3 --viewFraction 0.8 --fanOut 3 \\",
                 "                -G 30 -S 50 --baseSeed 42 -o results.csv",
                 "",
                 "  to replay a specific trial, copy its graphSeed and simulationSeed",
                 "  from the CSV and run with the 'single' command.",
                 ""
             })
    static class BatchCommand implements Callable<Integer> {

        @ArgGroup(exclusive = false, multiplicity = "1", heading = "%nNETWORK%n")
        NetworkOpts network = new NetworkOpts();

        @ArgGroup(exclusive = false, multiplicity = "1", heading = "%nPROTOCOL%n")
        ProtocolOpts protocol = new ProtocolOpts();

        @ArgGroup(exclusive = false, multiplicity = "1", heading = "%nEXPERIMENT%n")
        ExperimentOpts experiment = new ExperimentOpts();

        @ArgGroup(exclusive = false, heading = "%nOUTPUT%n")
        OutputOpts opts = new OutputOpts();

        static class NetworkOpts {
            @Option(names = {"-t", "--topology"}, paramLabel = "<name>", required = true,
                    description = "RANDOM | RING | SCALE_FREE | SMALL_WORLD")
            TopologyForm topology;

            @Option(names = {"-n", "--nodes"}, paramLabel = "<N>", required = true,
                    description = "number of nodes (min depends on k and topology)")
            int nodeCount;

            @Option(names = {"-k", "--k"}, paramLabel = "<k>", defaultValue = "3",
                    description = "base connectivity (default: ${DEFAULT-VALUE})")
            int k;
        }

        static class ProtocolOpts {
            @Option(names = {"--viewFraction"}, paramLabel = "<frac>", required = true,
                    description = "fraction of neighbors visible each round, in (0, 1]")
            double viewFraction;

            @Option(names = {"--fanOut"}, paramLabel = "<int>", required = true,
                    description = "peers contacted per round (>= 1)")
            int fanOut;

            @Option(names = {"--failureProbability"}, paramLabel = "<prob>", defaultValue = "0.0",
                    description = "probability each node is passive Byzantine (default: ${DEFAULT-VALUE})")
            double failureProbability;
        }

        static class ExperimentOpts {
            @Option(names = {"-G", "--graphInstances"}, paramLabel = "<int>", required = true,
                    description = "number of distinct networks to generate")
            int graphInstances;

            @Option(names = {"-S", "--trialsPerGraph"}, paramLabel = "<int>", required = true,
                    description = "simulation trials per network instance")
            int trialsPerGraph;

            @Option(names = {"--baseSeed"}, paramLabel = "<seed>", required = true,
                    description = "root seed (derives all graph and simulation seeds)")
            long baseSeed;
        }

        static class OutputOpts {
            @Option(names = {"-o", "--output"}, paramLabel = "<path>",
                    description = "output file (prints to stdout if omitted)")
            String path;

            @Option(names = {"-h", "--help"}, usageHelp = true, description = "show this help")
            boolean helpRequested;
        }

        @Override
        public Integer call() throws Exception {
            try {
                ExperimentConfig config = new ExperimentConfig(
                    network.topology, network.nodeCount, network.k,
                    protocol.viewFraction, protocol.fanOut
                );
                boolean byzantine = protocol.failureProbability > 0.0;
                List<TrialResult> results = ExperimentRunner.run(
                    config, experiment.graphInstances, experiment.trialsPerGraph,
                    experiment.baseSeed, protocol.failureProbability
                );

                if (opts.path != null) {
                    try (Writer writer = Files.newBufferedWriter(Path.of(opts.path))) {
                        if (byzantine) CsvWriter.writeByzantine(results, writer);
                        else           CsvWriter.write(results, writer);
                    }
                } else {
                    Writer writer = new OutputStreamWriter(System.out);
                    if (byzantine) CsvWriter.writeByzantine(results, writer);
                    else           CsvWriter.write(results, writer);
                    writer.flush();
                }
                return 0;
            } catch (IllegalArgumentException | IllegalStateException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
        }
    }

    // --- rq1 ---

    @Command(name = "rq1",
             sortOptions = false,
             synopsisHeading = "USAGE%n",
             customSynopsis = {"  aaron rq1 [options]", ""},
             description = {
                 "thesis topology sweep → CSV (how network shape affects spread)",
                 "Runs 4 topologies × 3 k-values × 6 network sizes × 3 peer-selection",
                 "settings = 216 configurations, each with G graph instances × S trials.",
                 "Default seeds reproduce the CSV used for the thesis figures byte-for-byte.",
                 "See `single --help` for the metric columns in the CSV.",
             },
             optionListHeading = "%nOPTIONS%n",
             footerHeading = "%nEXAMPLES%n",
             footer = {
                 "  ./aaron rq1 -o data/rq1.csv",
                 "  ./aaron rq1 -o data/rq1.csv --baseSeed 123",
                 "  ./aaron rq1 -o test.csv -G 2 -S 3        (quick smoke test)",
                 ""
             })
    static class RQ1Command implements Callable<Integer> {

        @Option(names = {"--baseSeed"}, paramLabel = "<seed>", defaultValue = "42",
                description = "root seed (default 42 reproduces the thesis CSVs byte-for-byte)")
        long baseSeed;

        @Option(names = {"-G", "--graphInstances"}, paramLabel = "<int>", defaultValue = "30",
                description = "graph instances per configuration (default: ${DEFAULT-VALUE})")
        int graphInstances;

        @Option(names = {"-S", "--trialsPerGraph"}, paramLabel = "<int>", defaultValue = "50",
                description = "trials per graph instance (default: ${DEFAULT-VALUE})")
        int trialsPerGraph;

        @Option(names = {"-o", "--output"}, paramLabel = "<path>", required = true,
                description = "output CSV file path")
        String output;

        @Option(names = {"-h", "--help"}, usageHelp = true, description = "show this help")
        boolean helpRequested;

        @Override
        public Integer call() throws Exception {
            int configCount = ExperimentSuite.rq1Configs().size();
            int totalTrials = configCount * graphInstances * trialsPerGraph;

            System.err.printf("  RQ1 · %d configs × %d graphs × %d trials = %,d total%n",
                configCount, graphInstances, trialsPerGraph, totalTrials);
            System.err.printf("  base seed: %d%n%n", baseSeed);

            long startTime = System.currentTimeMillis();

            List<TrialResult> results = ExperimentSuite.runRQ1(
                baseSeed, graphInstances, trialsPerGraph, System.err);

            Path outPath = Path.of(output);
            if (outPath.getParent() != null) {
                Files.createDirectories(outPath.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(outPath)) {
                CsvWriter.write(results, writer);
            }

            long elapsed = System.currentTimeMillis() - startTime;
            printSummary("RQ1", configCount, results.size(), totalTrials, elapsed, output);
            return 0;
        }
    }

    // --- rq2 ---

    @Command(name = "rq2",
             sortOptions = false,
             synopsisHeading = "USAGE%n",
             customSynopsis = {"  aaron rq2 [options]", ""},
             description = {
                 "thesis peer-selection sweep → CSV (how view and fanOut affect spread)",
                 "Runs 4 topologies × 4 network sizes × 5 viewFractions × 5 fanOuts",
                 "= 400 configurations, each with G graph instances × S trials (k=6 fixed).",
                 "Default seeds reproduce the CSV used for the thesis figures byte-for-byte.",
                 "See `single --help` for the metric columns in the CSV.",
             },
             optionListHeading = "%nOPTIONS%n",
             footerHeading = "%nEXAMPLES%n",
             footer = {
                 "  ./aaron rq2 -o data/rq2.csv",
                 "  ./aaron rq2 -o data/rq2.csv --baseSeed 123",
                 "  ./aaron rq2 -o test.csv -G 2 -S 3        (quick smoke test)",
                 ""
             })
    static class RQ2Command implements Callable<Integer> {

        @Option(names = {"--baseSeed"}, paramLabel = "<seed>", defaultValue = "42",
                description = "root seed (default 42 reproduces the thesis CSVs byte-for-byte)")
        long baseSeed;

        @Option(names = {"-G", "--graphInstances"}, paramLabel = "<int>", defaultValue = "30",
                description = "graph instances per configuration (default: ${DEFAULT-VALUE})")
        int graphInstances;

        @Option(names = {"-S", "--trialsPerGraph"}, paramLabel = "<int>", defaultValue = "50",
                description = "trials per graph instance (default: ${DEFAULT-VALUE})")
        int trialsPerGraph;

        @Option(names = {"-o", "--output"}, paramLabel = "<path>", required = true,
                description = "output CSV file path")
        String output;

        @Option(names = {"-h", "--help"}, usageHelp = true, description = "show this help")
        boolean helpRequested;

        @Override
        public Integer call() throws Exception {
            int configCount = ExperimentSuite.rq2Configs().size();
            int totalTrials = configCount * graphInstances * trialsPerGraph;

            System.err.printf("  RQ2 · %d configs × %d graphs × %d trials = %,d total%n",
                configCount, graphInstances, trialsPerGraph, totalTrials);
            System.err.printf("  base seed: %d%n%n", baseSeed);

            long startTime = System.currentTimeMillis();

            List<TrialResult> results = ExperimentSuite.runRQ2(
                baseSeed, graphInstances, trialsPerGraph, System.err);

            Path outPath = Path.of(output);
            if (outPath.getParent() != null) {
                Files.createDirectories(outPath.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(outPath)) {
                CsvWriter.write(results, writer);
            }

            long elapsed = System.currentTimeMillis() - startTime;
            printSummary("RQ2", configCount, results.size(), totalTrials, elapsed, output);
            return 0;
        }
    }

    // --- rq1b ---

    @Command(name = "rq1b",
             sortOptions = false,
             synopsisHeading = "USAGE%n",
             customSynopsis = {"  aaron rq1b [options]", ""},
             description = {
                 "Byzantine topology sweep → CSV (robustness of RQ1 under passive failures)",
                 "Runs the same 216 configurations as RQ1, but at two failure probabilities",
                 "(0.15 and 0.30). Byzantine nodes remain in the network but stop forwarding.",
                 "Execution is parallelized across configurations.",
                 "See `single --help` for the metric columns in the CSV.",
             },
             optionListHeading = "%nOPTIONS%n",
             footerHeading = "%nEXAMPLES%n",
             footer = {
                 "  ./aaron rq1b -o data/rq1b.csv",
                 "  ./aaron rq1b -o data/rq1b.csv --baseSeed 123",
                 "  ./aaron rq1b -o test.csv -G 2 -S 3        (quick smoke test)",
                 ""
             })
    static class RQ1BCommand implements Callable<Integer> {

        @Option(names = {"--baseSeed"}, paramLabel = "<seed>", defaultValue = "42",
                description = "root seed (default 42)")
        long baseSeed;

        @Option(names = {"-G", "--graphInstances"}, paramLabel = "<int>", defaultValue = "30",
                description = "graph instances per configuration (default: ${DEFAULT-VALUE})")
        int graphInstances;

        @Option(names = {"-S", "--trialsPerGraph"}, paramLabel = "<int>", defaultValue = "50",
                description = "trials per graph instance (default: ${DEFAULT-VALUE})")
        int trialsPerGraph;

        @Option(names = {"-o", "--output"}, paramLabel = "<path>", required = true,
                description = "output CSV file path")
        String output;

        @Option(names = {"-h", "--help"}, usageHelp = true, description = "show this help")
        boolean helpRequested;

        @Override
        public Integer call() throws Exception {
            int configCount = ExperimentSuite.rq1Configs().size();
            int fpCount = ExperimentSuite.BYZANTINE_FAILURE_PROBS.length;
            int totalTrials = configCount * fpCount * graphInstances * trialsPerGraph;

            System.err.printf("  RQ1B · %d configs × %d failure probs × %d graphs × %d trials = %,d total%n",
                configCount, fpCount, graphInstances, trialsPerGraph, totalTrials);
            System.err.printf("  base seed: %d%n%n", baseSeed);

            long startTime = System.currentTimeMillis();

            List<TrialResult> results = ExperimentSuite.runRQ1B(
                baseSeed, graphInstances, trialsPerGraph, System.err);

            Path outPath = Path.of(output);
            if (outPath.getParent() != null) {
                Files.createDirectories(outPath.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(outPath)) {
                CsvWriter.writeByzantine(results, writer);
            }

            long elapsed = System.currentTimeMillis() - startTime;
            printSummary("RQ1B", configCount * fpCount, results.size(), totalTrials, elapsed, output);
            return 0;
        }
    }

    // --- rq2b ---

    @Command(name = "rq2b",
             sortOptions = false,
             synopsisHeading = "USAGE%n",
             customSynopsis = {"  aaron rq2b [options]", ""},
             description = {
                 "Byzantine peer-selection sweep → CSV (robustness of RQ2 under passive failures)",
                 "Runs the same 400 configurations as RQ2, but at two failure probabilities",
                 "(0.15 and 0.30). Byzantine nodes remain in the network but stop forwarding.",
                 "Execution is parallelized across configurations.",
                 "See `single --help` for the metric columns in the CSV.",
             },
             optionListHeading = "%nOPTIONS%n",
             footerHeading = "%nEXAMPLES%n",
             footer = {
                 "  ./aaron rq2b -o data/rq2b.csv",
                 "  ./aaron rq2b -o data/rq2b.csv --baseSeed 123",
                 "  ./aaron rq2b -o test.csv -G 2 -S 3        (quick smoke test)",
                 ""
             })
    static class RQ2BCommand implements Callable<Integer> {

        @Option(names = {"--baseSeed"}, paramLabel = "<seed>", defaultValue = "42",
                description = "root seed (default 42)")
        long baseSeed;

        @Option(names = {"-G", "--graphInstances"}, paramLabel = "<int>", defaultValue = "30",
                description = "graph instances per configuration (default: ${DEFAULT-VALUE})")
        int graphInstances;

        @Option(names = {"-S", "--trialsPerGraph"}, paramLabel = "<int>", defaultValue = "50",
                description = "trials per graph instance (default: ${DEFAULT-VALUE})")
        int trialsPerGraph;

        @Option(names = {"-o", "--output"}, paramLabel = "<path>", required = true,
                description = "output CSV file path")
        String output;

        @Option(names = {"-h", "--help"}, usageHelp = true, description = "show this help")
        boolean helpRequested;

        @Override
        public Integer call() throws Exception {
            int configCount = ExperimentSuite.rq2Configs().size();
            int fpCount = ExperimentSuite.BYZANTINE_FAILURE_PROBS.length;
            int totalTrials = configCount * fpCount * graphInstances * trialsPerGraph;

            System.err.printf("  RQ2B · %d configs × %d failure probs × %d graphs × %d trials = %,d total%n",
                configCount, fpCount, graphInstances, trialsPerGraph, totalTrials);
            System.err.printf("  base seed: %d%n%n", baseSeed);

            long startTime = System.currentTimeMillis();

            List<TrialResult> results = ExperimentSuite.runRQ2B(
                baseSeed, graphInstances, trialsPerGraph, System.err);

            Path outPath = Path.of(output);
            if (outPath.getParent() != null) {
                Files.createDirectories(outPath.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(outPath)) {
                CsvWriter.writeByzantine(results, writer);
            }

            long elapsed = System.currentTimeMillis() - startTime;
            printSummary("RQ2B", configCount * fpCount, results.size(), totalTrials, elapsed, output);
            return 0;
        }
    }

    // --- helpers ---

    private static void printSummary(String label, int configs, int trials, int expected,
                                     long elapsedMs, String outputPath) {
        int skipped = expected - trials;
        System.err.println();
        System.err.printf("  %s complete%n", label);
        System.err.printf("    configurations   %,d%n", configs);
        if (skipped > 0) {
            System.err.printf("    trials           %,d  (%,d skipped: graph not connected)%n",
                trials, skipped);
        } else {
            System.err.printf("    trials           %,d%n", trials);
        }
        System.err.printf("    elapsed          %s%n", formatDuration(elapsedMs));
        System.err.printf("    output           %s%n", outputPath);
        System.err.println();
    }

    private static String formatDuration(long millis) {
        long totalSeconds = millis / 1000;
        if (totalSeconds < 60) return totalSeconds + "s";
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (seconds == 0) return minutes + "m";
        return minutes + "m " + seconds + "s";
    }

    public static void main(String[] args) {
        CommandLine cmd = buildCli();

        // Bare invocation from an interactive terminal -> show the menu.
        // Pipes / redirects / CI keep the original behaviour (print help, exit).
        // Note: as of JDK 22 System.console() is non-null even when stdin is
        // redirected, so we must additionally check isTerminal().
        java.io.Console console = System.console();
        if (args.length == 0 && console != null && console.isTerminal()) {
            InteractiveMenu.run(cmd);
            return;
        }

        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }

    /**
     * Builds a fresh top-level CommandLine with help layout and section order
     * applied. Used both by {@link #main} and by {@link InteractiveMenu} when
     * it needs a clean parser for each command it executes.
     */
    static CommandLine buildCli() {
        CommandLine cmd = new CommandLine(new AaronCli());
        configureHelpLayout(cmd);
        reorderHelpSections(cmd);
        return cmd;
    }

    /**
     * Fixes help-layout defaults: wider usage width so long options fit on one line,
     * and a wider option-label column so entries like {@code --simulationSeed=<seed>}
     * do not split across two lines.
     */
    private static void configureHelpLayout(CommandLine cmd) {
        cmd.setUsageHelpWidth(100);
        cmd.getCommandSpec().usageMessage().longOptionsMaxWidth(32);
        for (CommandLine sub : cmd.getSubcommands().values()) {
            configureHelpLayout(sub);
        }
    }

    /**
     * Shows COMMANDS before OPTIONS in the help layout (picocli default is the opposite).
     * Applies recursively to every subcommand so each command's help is consistent.
     */
    private static void reorderHelpSections(CommandLine cmd) {
        List<String> keys = new java.util.ArrayList<>(cmd.getHelpSectionKeys());
        keys.remove(CommandLine.Model.UsageMessageSpec.SECTION_KEY_COMMAND_LIST);
        keys.remove(CommandLine.Model.UsageMessageSpec.SECTION_KEY_COMMAND_LIST_HEADING);
        int optIdx = keys.indexOf(CommandLine.Model.UsageMessageSpec.SECTION_KEY_OPTION_LIST_HEADING);
        if (optIdx < 0) optIdx = keys.indexOf(CommandLine.Model.UsageMessageSpec.SECTION_KEY_OPTION_LIST);
        if (optIdx < 0) optIdx = keys.size();
        keys.add(optIdx, CommandLine.Model.UsageMessageSpec.SECTION_KEY_COMMAND_LIST);
        keys.add(optIdx, CommandLine.Model.UsageMessageSpec.SECTION_KEY_COMMAND_LIST_HEADING);
        cmd.setHelpSectionKeys(keys);
        for (CommandLine sub : cmd.getSubcommands().values()) {
            reorderHelpSections(sub);
        }
    }
}
