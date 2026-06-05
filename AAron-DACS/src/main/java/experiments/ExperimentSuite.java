package experiments;

import aaron.TopologyForm;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Defines and executes the experiment parameter grids for RQ1, RQ2, RQ1B, and RQ2B.
 *
 * <p>RQ1 (topology effect): varies topology, k, and N at three peer-selection settings.
 * 4 topologies x 3 k-values x 6 sizes x 3 settings = 216 configurations.
 * <p>RQ2 (peer-selection effect): sweeps viewFraction x fanOut per topology and N at k=6.
 * 4 topologies x 4 sizes x 5 viewFractions x 5 fanOuts = 400 configurations.
 *
 * <p>RQ1B and RQ2B are Byzantine robustness variants of RQ1 and RQ2: same parameter
 * grids, but each configuration is run at two failure probabilities (0.15 and 0.30).
 * Independent configurations are executed in parallel using a fixed-size thread pool.
 *
 * <p>Each method runs all configurations in a single invocation and returns
 * a flat list of trial results suitable for writing to a single CSV file.
 */
public final class ExperimentSuite {

    public static final int DEFAULT_G = 30;
    public static final int DEFAULT_S = 50;
    public static final long DEFAULT_BASE_SEED = 42L;

    private static final TopologyForm[] TOPOLOGIES = TopologyForm.values();

    private static final int[] RQ1_K_VALUES = {3, 6, 9};
    private static final int[] RQ1_SIZES = {50, 100, 200, 500, 1000, 5000};
    private static final double[][] RQ1_SETTINGS = {
        {1.0, 3},  // Setting A: full visibility, moderate contact
        {0.6, 2},  // Setting B: partial visibility, reduced contact
        {0.2, 1},  // Setting C: minimal visibility, single contact
    };

    private static final int RQ2_K = 6;
    private static final int[] RQ2_SIZES = {100, 500, 1000, 5000};
    private static final double[] RQ2_VIEW_FRACTIONS = {0.2, 0.4, 0.6, 0.8, 1.0};
    private static final int[] RQ2_FAN_OUTS = {1, 2, 3, 4, 5};

    public static final double[] BYZANTINE_FAILURE_PROBS = {0.15, 0.30};

    private ExperimentSuite() {}

    /**
     * Builds the RQ1 configuration list.
     * 4 topologies x 3 k-values x 6 sizes x 3 settings = 216 configurations.
     */
    public static List<ExperimentConfig> rq1Configs() {
        List<ExperimentConfig> configs = new ArrayList<>();
        for (TopologyForm t : TOPOLOGIES) {
            for (int k : RQ1_K_VALUES) {
                for (int n : RQ1_SIZES) {
                    for (double[] setting : RQ1_SETTINGS) {
                        configs.add(new ExperimentConfig(t, n, k, setting[0], (int) setting[1]));
                    }
                }
            }
        }
        return List.copyOf(configs);
    }

    /**
     * Builds the RQ2 configuration list.
     * 4 topologies x 4 sizes x 5 viewFractions x 5 fanOuts = 400 configurations (k=6 fixed).
     */
    public static List<ExperimentConfig> rq2Configs() {
        List<ExperimentConfig> configs = new ArrayList<>();
        for (TopologyForm t : TOPOLOGIES) {
            for (int n : RQ2_SIZES) {
                for (double vf : RQ2_VIEW_FRACTIONS) {
                    for (int fo : RQ2_FAN_OUTS) {
                        configs.add(new ExperimentConfig(t, n, RQ2_K, vf, fo));
                    }
                }
            }
        }
        return List.copyOf(configs);
    }

    /**
     * Runs all RQ1 configurations.
     *
     * @param baseSeed base seed for all trials
     * @param g graph instances per configuration
     * @param s trials per graph instance
     * @param progress stream for progress messages (null to suppress)
     * @return all trial results across all configurations
     */
    public static List<TrialResult> runRQ1(long baseSeed, int g, int s, PrintStream progress) {
        return runAll(rq1Configs(), baseSeed, g, s, progress, "RQ1");
    }

    /**
     * Runs all RQ2 configurations.
     *
     * @param baseSeed base seed for all trials
     * @param g graph instances per configuration
     * @param s trials per graph instance
     * @param progress stream for progress messages (null to suppress)
     * @return all trial results across all configurations
     */
    public static List<TrialResult> runRQ2(long baseSeed, int g, int s, PrintStream progress) {
        return runAll(rq2Configs(), baseSeed, g, s, progress, "RQ2");
    }

    /** Convenience overload with default G and S. */
    public static List<TrialResult> runRQ1(long baseSeed, PrintStream progress) {
        return runRQ1(baseSeed, DEFAULT_G, DEFAULT_S, progress);
    }

    /** Convenience overload with default G and S. */
    public static List<TrialResult> runRQ2(long baseSeed, PrintStream progress) {
        return runRQ2(baseSeed, DEFAULT_G, DEFAULT_S, progress);
    }

    // --- RQ1B: Byzantine robustness variant of RQ1 ---

    /**
     * Runs all RQ1 configurations at each Byzantine failure probability.
     * Execution is parallelized across configurations.
     *
     * @param baseSeed base seed for all trials
     * @param g graph instances per configuration
     * @param s trials per graph instance
     * @param progress stream for progress messages (null to suppress)
     * @return all trial results across all configurations and failure probabilities
     */
    public static List<TrialResult> runRQ1B(long baseSeed, int g, int s, PrintStream progress) {
        return runAllParallel(rq1Configs(), BYZANTINE_FAILURE_PROBS, baseSeed, g, s, progress, "RQ1B");
    }

    /** Convenience overload with default G and S. */
    public static List<TrialResult> runRQ1B(long baseSeed, PrintStream progress) {
        return runRQ1B(baseSeed, DEFAULT_G, DEFAULT_S, progress);
    }

    // --- RQ2B: Byzantine robustness variant of RQ2 ---

    /**
     * Runs all RQ2 configurations at each Byzantine failure probability.
     * Execution is parallelized across configurations.
     *
     * @param baseSeed base seed for all trials
     * @param g graph instances per configuration
     * @param s trials per graph instance
     * @param progress stream for progress messages (null to suppress)
     * @return all trial results across all configurations and failure probabilities
     */
    public static List<TrialResult> runRQ2B(long baseSeed, int g, int s, PrintStream progress) {
        return runAllParallel(rq2Configs(), BYZANTINE_FAILURE_PROBS, baseSeed, g, s, progress, "RQ2B");
    }

    /** Convenience overload with default G and S. */
    public static List<TrialResult> runRQ2B(long baseSeed, PrintStream progress) {
        return runRQ2B(baseSeed, DEFAULT_G, DEFAULT_S, progress);
    }

    // --- sequential runner (baseline RQ1/RQ2) ---

    private static List<TrialResult> runAll(
            List<ExperimentConfig> configs,
            long baseSeed, int g, int s,
            PrintStream progress, String label) {

        List<TrialResult> allResults = new ArrayList<>();
        int total = configs.size();
        int counterWidth = Integer.toString(total).length();

        for (int i = 0; i < total; i++) {
            ExperimentConfig config = configs.get(i);
            if (progress != null) {
                progress.printf("  %s · %" + counterWidth + "d/%d   %-11s  N=%-5d  k=%d  vf=%.1f  fo=%d%n",
                    label, i + 1, total,
                    config.topologyType(), config.nodeCount(), config.k(),
                    config.viewFraction(), config.fanOut());
            }

            try {
                List<TrialResult> results = ExperimentRunner.run(config, g, s, baseSeed);
                allResults.addAll(results);
            } catch (IllegalStateException e) {
                if (progress != null) {
                    progress.printf("  %s · SKIP %" + counterWidth + "d/%d   %-11s  N=%-5d  k=%d   -  %s%n",
                        label, i + 1, total, config.topologyType(),
                        config.nodeCount(), config.k(), e.getMessage());
                }
            }
        }

        return List.copyOf(allResults);
    }

    // --- parallel runner (Byzantine RQ1B/RQ2B) ---

    /**
     * Deterministic comparator for sorting Byzantine trial results.
     * Ensures CSV output is stable regardless of thread scheduling.
     */
    private static final Comparator<TrialResult> DETERMINISTIC_ORDER =
        Comparator.comparing((TrialResult r) -> r.topologyType().name())
            .thenComparingInt(TrialResult::nodeCount)
            .thenComparingInt(TrialResult::k)
            .thenComparingDouble(TrialResult::viewFraction)
            .thenComparingInt(TrialResult::fanOut)
            .thenComparingDouble(TrialResult::failureProbability)
            .thenComparingInt(TrialResult::trial);

    private static List<TrialResult> runAllParallel(
            List<ExperimentConfig> configs,
            double[] failureProbs,
            long baseSeed, int g, int s,
            PrintStream progress, String label) {

        int totalTasks = configs.size() * failureProbs.length;
        int threads = Runtime.getRuntime().availableProcessors();
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        if (progress != null) {
            progress.printf("  %s · %d configs × %d failure probs = %d tasks on %d threads%n",
                label, configs.size(), failureProbs.length, totalTasks, threads);
        }

        List<Future<List<TrialResult>>> futures = new ArrayList<>(totalTasks);
        for (ExperimentConfig config : configs) {
            for (double fp : failureProbs) {
                futures.add(pool.submit(() -> {
                    try {
                        return ExperimentRunner.run(config, g, s, baseSeed, fp);
                    } catch (IllegalStateException e) {
                        if (progress != null) {
                            synchronized (progress) {
                                progress.printf("  %s · SKIP  %-11s  N=%-5d  k=%d  fp=%.2f  -  %s%n",
                                    label, config.topologyType(), config.nodeCount(),
                                    config.k(), fp, e.getMessage());
                            }
                        }
                        return List.of();
                    }
                }));
            }
        }

        List<TrialResult> allResults = new ArrayList<>();
        int completed = 0;
        int counterWidth = Integer.toString(totalTasks).length();

        for (Future<List<TrialResult>> future : futures) {
            try {
                allResults.addAll(future.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Byzantine experiment interrupted", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Byzantine experiment failed", e.getCause());
            }
            completed++;
            if (progress != null && completed % 10 == 0) {
                progress.printf("  %s · %" + counterWidth + "d/%d tasks complete%n",
                    label, completed, totalTasks);
            }
        }

        pool.shutdown();

        allResults.sort(DETERMINISTIC_ORDER);
        return List.copyOf(allResults);
    }
}
