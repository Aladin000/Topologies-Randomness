package cli;

import aaron.NetworkValidator;
import aaron.TopologyForm;
import experiments.ExperimentRunner;
import simulation.SimulationResult;
import simulation.Simulator;

import picocli.CommandLine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AaronCliTest {

    // --- runSingleTrial ---

    @Nested
    class RunSingleTrialTests {

        @Test
        void runSingleTrial_deterministic() {
            SimulationResult r1 = ExperimentRunner.runSingleTrial(
                TopologyForm.RING, 30, Simulator.DEFAULT_K, 0.8, 2, 42L, 100L
            );
            SimulationResult r2 = ExperimentRunner.runSingleTrial(
                TopologyForm.RING, 30, Simulator.DEFAULT_K, 0.8, 2, 42L, 100L
            );

            assertEquals(r1.totalRounds(), r2.totalRounds());
            assertEquals(r1.sourceNode(), r2.sourceNode());
            assertEquals(r1.config(), r2.config());
        }

        @Test
        void runSingleTrial_matchesDirectSimulate_forRing() {
            SimulationResult viaSingle = ExperimentRunner.runSingleTrial(
                TopologyForm.RING, 30, Simulator.DEFAULT_K, 0.8, 2, 42L, 100L
            );
            SimulationResult viaDirect = Simulator.simulate(
                TopologyForm.RING, 30, 0.8, 2, 42L, 100L
            );

            assertEquals(viaDirect.totalRounds(), viaSingle.totalRounds());
            assertEquals(viaDirect.sourceNode(), viaSingle.sourceNode());
        }

        @Test
        void runSingleTrial_connectivityEnforcement_random() {
            SimulationResult result = ExperimentRunner.runSingleTrial(
                TopologyForm.RANDOM, 50, Simulator.DEFAULT_K, 0.8, 2, 42L, 100L
            );
            assertTrue(result.totalRounds() >= 1);
            assertTrue(NetworkValidator.isConnected(result.network()),
                "runSingleTrial must produce a connected graph after connectivity enforcement");
        }

        @Test
        void runSingleTrial_allTopologies() {
            for (TopologyForm topology : TopologyForm.values()) {
                int k = Simulator.DEFAULT_K;
                int minNodes = switch (topology) {
                    case RANDOM, RING, SMALL_WORLD -> 2 * k + 1;
                    case SCALE_FREE -> k + 1;
                };
                SimulationResult result = ExperimentRunner.runSingleTrial(
                    topology, Math.max(minNodes, 20), k, 0.8, 2, 42L, 100L
                );
                assertTrue(result.totalRounds() >= 1);
            }
        }
    }

    // --- CLI parsing ---

    @Nested
    class CliParsingTests {

        @Test
        void helpExitsWithZero() {
            int exitCode = new CommandLine(new AaronCli()).execute("--help");
            assertEquals(0, exitCode);
        }

        @Test
        void singleHelpExitsWithZero() {
            int exitCode = new CommandLine(new AaronCli()).execute("single", "--help");
            assertEquals(0, exitCode);
        }

        @Test
        void batchHelpExitsWithZero() {
            int exitCode = new CommandLine(new AaronCli()).execute("batch", "--help");
            assertEquals(0, exitCode);
        }

        @Test
        void singleMissingRequiredOptions_fails() {
            int exitCode = new CommandLine(new AaronCli()).execute("single");
            assertNotEquals(0, exitCode);
        }

        @Test
        void batchMissingRequiredOptions_fails() {
            int exitCode = new CommandLine(new AaronCli()).execute("batch");
            assertNotEquals(0, exitCode);
        }
    }

    // --- End-to-end with file output ---

    @Nested
    class EndToEndTests {

        @Test
        void singleCommand_writesJsonFile(@TempDir Path tempDir) throws Exception {
            Path outputFile = tempDir.resolve("result.json");

            int exitCode = new CommandLine(new AaronCli()).execute(
                "single",
                "-t", "RING",
                "-n", "20",
                "--viewFraction", "1.0",
                "--fanOut", "2",
                "--graphSeed", "42",
                "--simulationSeed", "100",
                "-o", outputFile.toString()
            );

            assertEquals(0, exitCode);
            assertTrue(Files.exists(outputFile));
            String content = Files.readString(outputFile);
            assertTrue(content.contains("\"configuration\""));
            assertTrue(content.contains("\"network\""));
            assertTrue(content.contains("\"rounds\""));
            assertTrue(content.contains("\"result\""));
            assertTrue(content.contains("\"Ring\""));
        }

        @Test
        void batchCommand_writesCsvFile(@TempDir Path tempDir) throws Exception {
            Path outputFile = tempDir.resolve("results.csv");

            int exitCode = new CommandLine(new AaronCli()).execute(
                "batch",
                "-t", "RING",
                "-n", "20",
                "--viewFraction", "1.0",
                "--fanOut", "2",
                "-G", "2",
                "-S", "3",
                "--baseSeed", "42",
                "-o", outputFile.toString()
            );

            assertEquals(0, exitCode);
            assertTrue(Files.exists(outputFile));
            String content = Files.readString(outputFile);
            String[] lines = content.split("\n");
            assertEquals(7, lines.length, "Header + 6 data rows (2 graphs x 3 trials)");
            assertTrue(lines[0].startsWith("topology,"));
        }

        @Test
        void singleCommand_acceptsAllTopologies(@TempDir Path tempDir) {
            for (TopologyForm topology : TopologyForm.values()) {
                Path outputFile = tempDir.resolve(topology.name() + ".json");

                int exitCode = new CommandLine(new AaronCli()).execute(
                    "single",
                    "-t", topology.name(),
                    "-n", "20",
                    "--viewFraction", "0.8",
                    "--fanOut", "2",
                    "--graphSeed", "42",
                    "--simulationSeed", "100",
                    "-o", outputFile.toString()
                );

                assertEquals(0, exitCode, "Should succeed for " + topology);
                assertTrue(Files.exists(outputFile));
            }
        }
    }
}
