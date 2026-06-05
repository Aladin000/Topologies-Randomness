package cli;

import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Interactive entry point shown when the user runs {@code aaron} with no
 * arguments from a terminal. It is both:
 *
 * <ol>
 *   <li>A main page listing the four subcommands.</li>
 *   <li>A runner: the user can either pick a number to enter a guided form
 *       or type (or paste) a full command that is forwarded verbatim to
 *       picocli.</li>
 * </ol>
 *
 * Invoked only when stdin is a TTY (see {@link AaronCli#main}), so scripts
 * and CI keep the classic behaviour of printing the help page and exiting.
 */
final class InteractiveMenu {

    // ------------------------------------------------------------------ layout

    private static final String[] HOME = {
        "",
        "  AAron  \u25CF\u2500\u25CF\u2500\u25CF  1.0.0 \u00B7 gossip-epidemic protocol simulator",
        "",
        "  1   single    one trial \u2192 JSON",
        "  2   batch     parameter sweep \u2192 CSV",
        "  3   rq1       reproduce RQ1 grid",
        "  4   rq2       reproduce RQ2 grid",
        "  5   rq1b      reproduce RQ1B grid (Byzantine)",
        "  6   rq2b      reproduce RQ2B grid (Byzantine)",
        "  ?   help      show full CLI help",
        "  q   quit",
        "",
        "  pick a number for a guided run \u00B7 or type a command (e.g. rq1 -o data/rq1.csv)",
        ""
    };

    // ------------------------------------------------------------------ forms

    /** One field in a guided form: visible label, CLI flag, default, help text. */
    record Field(String label, String flag, String defaultValue, String hint) {}

    private static final List<Field> SINGLE = List.of(
        new Field("topology",           "-t",                    "RING",        "RANDOM | RING | SCALE_FREE | SMALL_WORLD"),
        new Field("nodes",              "-n",                    "100",         "number of nodes (min depends on k and topology)"),
        new Field("k",                  "--k",                   "3",           "base connectivity (integer >= 1)"),
        new Field("viewFraction",       "--viewFraction",        "1.0",         "fraction of neighbors visible each round, in (0, 1]"),
        new Field("fanOut",             "--fanOut",              "3",           "peers contacted per round (>= 1)"),
        new Field("failureProbability", "--failureProbability",  "0.0",         "probability each node is passive Byzantine, in [0, 1]"),
        new Field("graphSeed",          "--graphSeed",           "42",          "seed for topology generation"),
        new Field("simulationSeed",     "--simulationSeed",      "7",           "seed for source node and peer selection"),
        new Field("output",             "-o",                    "result.json", "output JSON file")
    );

    private static final List<Field> BATCH = List.of(
        new Field("topology",           "-t",                    "RING",        "RANDOM | RING | SCALE_FREE | SMALL_WORLD"),
        new Field("nodes",              "-n",                    "100",         "number of nodes"),
        new Field("k",                  "--k",                   "3",           "base connectivity"),
        new Field("viewFraction",       "--viewFraction",        "1.0",         "fraction of neighbors visible each round"),
        new Field("fanOut",             "--fanOut",              "3",           "peers contacted per round"),
        new Field("failureProbability", "--failureProbability",  "0.0",         "probability each node is passive Byzantine, in [0, 1]"),
        new Field("graphs",             "-G",                    "30",          "number of distinct networks (G)"),
        new Field("trials",             "-S",                    "50",          "simulation trials per network (S)"),
        new Field("baseSeed",           "--baseSeed",            "42",          "root seed (derives every graph and simulation seed)"),
        new Field("output",             "-o",                    "batch.csv",   "output CSV file")
    );

    private static final List<Field> RQ1 = List.of(
        new Field("output",         "-o",               "data/rq1.csv","output CSV file"),
        new Field("baseSeed",       "--baseSeed",       "42",          "root seed (42 reproduces the thesis CSVs byte-for-byte)"),
        new Field("graphs",         "-G",               "30",          "graph instances per configuration"),
        new Field("trials",         "-S",               "50",          "simulation trials per graph")
    );

    private static final List<Field> RQ2 = List.of(
        new Field("output",         "-o",               "data/rq2.csv","output CSV file"),
        new Field("baseSeed",       "--baseSeed",       "42",          "root seed (42 reproduces the thesis CSVs byte-for-byte)"),
        new Field("graphs",         "-G",               "30",          "graph instances per configuration"),
        new Field("trials",         "-S",               "50",          "simulation trials per graph")
    );

    private static final List<Field> RQ1B = List.of(
        new Field("output",         "-o",               "data/rq1b.csv","output CSV file"),
        new Field("baseSeed",       "--baseSeed",       "42",           "root seed"),
        new Field("graphs",         "-G",               "30",           "graph instances per configuration"),
        new Field("trials",         "-S",               "50",           "simulation trials per graph")
    );

    private static final List<Field> RQ2B = List.of(
        new Field("output",         "-o",               "data/rq2b.csv","output CSV file"),
        new Field("baseSeed",       "--baseSeed",       "42",           "root seed"),
        new Field("graphs",         "-G",               "30",           "graph instances per configuration"),
        new Field("trials",         "-S",               "50",           "simulation trials per graph")
    );

    /** Maps menu choices (both number and word form) to canonical subcommand name. */
    private static final Map<String, String> SUBCOMMAND = new java.util.LinkedHashMap<>();
    static {
        SUBCOMMAND.put("1", "single"); SUBCOMMAND.put("single", "single");
        SUBCOMMAND.put("2", "batch");  SUBCOMMAND.put("batch",  "batch");
        SUBCOMMAND.put("3", "rq1");    SUBCOMMAND.put("rq1",    "rq1");
        SUBCOMMAND.put("4", "rq2");    SUBCOMMAND.put("rq2",    "rq2");
        SUBCOMMAND.put("5", "rq1b");   SUBCOMMAND.put("rq1b",   "rq1b");
        SUBCOMMAND.put("6", "rq2b");   SUBCOMMAND.put("rq2b",   "rq2b");
    }

    private static final Map<String, List<Field>> FORMS = Map.of(
        "single", SINGLE,
        "batch",  BATCH,
        "rq1",    RQ1,
        "rq2",    RQ2,
        "rq1b",   RQ1B,
        "rq2b",   RQ2B
    );

    private static final Map<String, String> DESCRIPTIONS = Map.of(
        "single", "one simulation trial \u2192 JSON",
        "batch",  "custom parameter sweep \u2192 CSV",
        "rq1",    "thesis topology sweep \u2192 CSV",
        "rq2",    "thesis peer-selection sweep \u2192 CSV",
        "rq1b",   "Byzantine topology sweep \u2192 CSV (parallel)",
        "rq2b",   "Byzantine peer-selection sweep \u2192 CSV (parallel)"
    );

    private InteractiveMenu() {}

    // ------------------------------------------------------------- entry point

    static void run(CommandLine root) {
        PrintStream out = System.out;
        BufferedReader in = new BufferedReader(
            new InputStreamReader(System.in, StandardCharsets.UTF_8));

        while (true) {
            for (String line : HOME) out.println(line);
            out.print("  >  ");
            out.flush();

            String raw = readCommandLine(in);
            if (raw == null) {                  // Ctrl-D / stream closed
                out.println();
                return;
            }
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (isQuit(line)) return;

            String[] argv = tokenize(line);
            if (argv.length == 0) continue;
            argv = stripLeadingLauncher(argv);
            if (argv.length == 0) continue;

            String head = argv[0].toLowerCase();

            // "?" or "help" on its own -> full CLI help
            if (argv.length == 1 && (head.equals("?") || head.equals("help"))) {
                out.println();
                root.usage(out);
                pause(in, out);
                continue;
            }

            // "? single" or "help rq1" -> that subcommand's help
            if (argv.length >= 2 && (head.equals("?") || head.equals("help"))) {
                String sub = SUBCOMMAND.get(argv[1].toLowerCase());
                if (sub != null) {
                    CommandLine target = root.getSubcommands().get(sub);
                    if (target != null) {
                        out.println();
                        target.usage(out);
                        pause(in, out);
                        continue;
                    }
                }
            }

            // Bare "1" / "single" / "2" / "batch" / ... -> guided form
            if (argv.length == 1 && SUBCOMMAND.containsKey(head)) {
                runGuided(SUBCOMMAND.get(head), in, out);
                continue;
            }

            // Otherwise: free-form command; normalise a leading digit ("1 -t ..."
            // becomes "single -t ...") and let picocli parse the rest.
            if (SUBCOMMAND.containsKey(head)) argv[0] = SUBCOMMAND.get(head);

            runFreeform(argv, in, out);
        }
    }

    // ----------------------------------------------------------- guided runner

    private static void runGuided(String sub, BufferedReader in, PrintStream out) {
        List<Field> fields = FORMS.get(sub);
        if (fields == null) return;

        int labelW = fields.stream().mapToInt(f -> f.label().length()).max().orElse(0);
        int defaultW = fields.stream().mapToInt(f -> f.defaultValue().length()).max().orElse(0) + 2;

        out.println();
        out.println("  " + sub + "  \u00B7  " + DESCRIPTIONS.get(sub));
        out.println("  enter = accept default  \u00B7  ? = explain field  \u00B7  q = cancel");
        out.println();

        List<String> values = new ArrayList<>();
        for (Field f : fields) {
            String value = promptField(f, labelW, defaultW, in, out);
            if (value == null) return;         // cancelled or EOF
            values.add(value);
        }

        List<String> argv = new ArrayList<>();
        argv.add(sub);
        for (int i = 0; i < fields.size(); i++) {
            argv.add(fields.get(i).flag());
            argv.add(values.get(i));
        }

        out.println();
        out.println("  " + formatCommand(argv));
        out.println();
        out.print("  run?  [Y]  >  ");
        out.flush();

        String confirm = readLine(in);
        if (confirm == null) { out.println(); return; }
        String c = confirm.trim().toLowerCase();
        if (!(c.isEmpty() || c.equals("y") || c.equals("yes"))) {
            out.println("  cancelled");
            pause(in, out);
            return;
        }

        out.println();
        execute(argv.toArray(new String[0]), out);
        pause(in, out);
    }

    /**
     * Prompts once for a field, looping on "?" (show hint). Returns the user's
     * value (or the default if empty), or {@code null} if the user cancelled
     * with "q" or closed stdin.
     */
    private static String promptField(Field f, int labelW, int defaultW,
                                      BufferedReader in, PrintStream out) {
        while (true) {
            String prompt = "  " + pad(f.label(), labelW)
                          + "  " + pad("[" + f.defaultValue() + "]", defaultW)
                          + "  >  ";
            out.print(prompt);
            out.flush();

            String raw = readLine(in);
            if (raw == null) { out.println(); return null; }
            String v = raw.trim();
            if (v.equalsIgnoreCase("q")) {
                out.println("  cancelled");
                return null;
            }
            if (v.equals("?")) {
                out.println("    " + f.hint());
                continue;
            }
            return v.isEmpty() ? f.defaultValue() : v;
        }
    }

    // --------------------------------------------------------- free-form runner

    private static void runFreeform(String[] argv, BufferedReader in, PrintStream out) {
        out.println();
        execute(argv, out);
        pause(in, out);
    }

    /**
     * Builds a fresh CommandLine and runs picocli on the given argv. Using a
     * fresh instance per call avoids residual state on the subcommand beans
     * from previous parses.
     */
    private static void execute(String[] argv, PrintStream out) {
        CommandLine fresh = AaronCli.buildCli();
        int rc;
        try {
            rc = fresh.execute(argv);
        } catch (RuntimeException e) {
            out.println("  error: " + e.getMessage());
            return;
        }
        boolean helpOrVersion = Arrays.stream(argv).anyMatch(a ->
            a.equals("-h") || a.equals("--help") || a.equals("-V") || a.equals("--version"));
        if (!helpOrVersion) {
            out.println();
            out.println(rc == 0 ? "  done" : "  exit code " + rc);
        }
    }

    // ------------------------------------------------------------------ input

    /**
     * Reads one logical command line, joining physical lines that end in a
     * backslash (standard shell continuation). This lets users paste
     * multi-line command examples straight into the prompt.
     */
    static String readCommandLine(BufferedReader in) {
        String first = readLine(in);
        if (first == null) return null;
        StringBuilder sb = new StringBuilder(first);
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\\') {
            sb.setLength(sb.length() - 1);
            String next = readLine(in);
            if (next == null) break;
            sb.append(' ').append(next.stripLeading());
        }
        return sb.toString();
    }

    /**
     * Splits a command-line string into tokens, respecting single and double
     * quoted strings. Handles paths with spaces (e.g. {@code -o "my run.csv"}).
     */
    static String[] tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == quote) { quote = 0; continue; }
                cur.append(c);
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (Character.isWhitespace(c)) {
                if (cur.length() > 0) { tokens.add(cur.toString()); cur.setLength(0); }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) tokens.add(cur.toString());
        return tokens.toArray(new String[0]);
    }

    private static String[] stripLeadingLauncher(String[] argv) {
        if (argv.length > 0 && (argv[0].equals("./aaron") || argv[0].equalsIgnoreCase("aaron"))) {
            return Arrays.copyOfRange(argv, 1, argv.length);
        }
        return argv;
    }

    private static String readLine(BufferedReader in) {
        try { return in.readLine(); } catch (IOException e) { return null; }
    }

    // --------------------------------------------------------------- utilities

    private static boolean isQuit(String s) {
        String l = s.toLowerCase();
        return l.equals("q") || l.equals("quit") || l.equals("exit");
    }

    private static String pad(String s, int width) {
        if (s.length() >= width) return s;
        return s + " ".repeat(width - s.length());
    }

    /**
     * Renders an argv list as a single-line command, wrapping at ~76 columns
     * with backslash continuation so long invocations stay readable.
     */
    private static String formatCommand(List<String> argv) {
        StringBuilder sb = new StringBuilder("command:  ").append(argv.get(0));
        int lineLen = sb.length();
        String indent = " ".repeat(12);
        for (int i = 1; i < argv.size(); i++) {
            String part = argv.get(i);
            int peek = part.length() + (i + 1 < argv.size() ? 1 + argv.get(i + 1).length() : 0);
            if (lineLen + 1 + peek > 76) {
                sb.append(" \\\n  ").append(indent);
                lineLen = 2 + indent.length();
            } else {
                sb.append(' ');
                lineLen++;
            }
            sb.append(part);
            lineLen += part.length();
        }
        return sb.toString();
    }

    private static void pause(BufferedReader in, PrintStream out) {
        out.println();
        out.print("  press enter for menu  ");
        out.flush();
        readLine(in);
        out.println();
    }
}
