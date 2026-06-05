package metrics;

/**
 * Per-trial performance metrics computed from a simulation result.
 *
 * @param tEnd rounds until termination (T_end)
 * @param omega total message send events across all rounds (Omega)
 * @param messageComplexity messages per target node: Omega / (N - 1). Denominator is always N - 1. (M)
 * @param alpha final coverage: |I(T_end)| / N
 * @param latency50 first round at end of which >= 50% are informed (L_0.5); -1 if not reached
 * @param latency90 first round at end of which >= 90% are informed (L_0.9); -1 if not reached
 * @param latency100 first round at end of which 100% are informed (L_1.0); -1 if not reached
 * @param effectualFanout mean messages sent per informed node: Omega / |I(T_end)| (F_eff)
 * @param reliability 1 if alpha = 1.0 (all nodes informed), 0 otherwise (R_run)
 */
public record TrialMetrics(
    int tEnd,
    int omega,
    double messageComplexity,
    double alpha,
    int latency50,
    int latency90,
    int latency100,
    double effectualFanout,
    int reliability
) {}
