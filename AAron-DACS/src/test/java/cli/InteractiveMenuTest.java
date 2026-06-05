package cli;

import picocli.CommandLine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class InteractiveMenuTest {

    private InputStream originalIn;
    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream capturedOut;
    private ByteArrayOutputStream capturedErr;

    @BeforeEach
    void setUp() {
        originalIn = System.in;
        originalOut = System.out;
        originalErr = System.err;
        capturedOut = new ByteArrayOutputStream();
        capturedErr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(capturedErr, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() {
        System.setIn(originalIn);
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    private String runMenuWith(String input) {
        System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
        CommandLine cmd = AaronCli.buildCli();
        InteractiveMenu.run(cmd);
        return capturedOut.toString(StandardCharsets.UTF_8)
             + capturedErr.toString(StandardCharsets.UTF_8);
    }

    // exits

    @Test
    void quitReturnsImmediately() {
        String out = runMenuWith("q\n");
        assertTrue(out.contains("AAron"), "banner should be shown at least once");
    }

    @Test
    void quitAcceptsExitKeyword() {
        String out = runMenuWith("exit\n");
        assertTrue(out.contains("AAron"));
    }

    @Test
    void quitAcceptsQuitKeyword() {
        String out = runMenuWith("quit\n");
        assertTrue(out.contains("AAron"));
    }

    @Test
    void eofExitsCleanly() {
        String out = runMenuWith("");
        assertTrue(out.contains("AAron"));
    }

    @Test
    void emptyLinesAreIgnored() {
        String out = runMenuWith("\n\n\nq\n");
        assertTrue(out.contains("AAron"));
    }

    // help view

    @Test
    void questionMarkShowsFullHelp() {
        String out = runMenuWith("?\n\nq\n");
        assertTrue(out.contains("USAGE"));
        assertTrue(out.contains("COMMANDS"));
        assertTrue(out.contains("single"));
        assertTrue(out.contains("batch"));
    }

    @Test
    void helpKeywordShowsFullHelp() {
        String out = runMenuWith("help\n\nq\n");
        assertTrue(out.contains("USAGE"));
        assertTrue(out.contains("COMMANDS"));
    }

    @Test
    void helpForSpecificSubcommand() {
        String out = runMenuWith("help single\n\nq\n");
        assertTrue(out.contains("aaron single [options]"),
            "'help single' should render the single command's usage");
    }

    @Test
    void questionMarkForSpecificSubcommand() {
        String out = runMenuWith("? rq1\n\nq\n");
        assertTrue(out.contains("aaron rq1 [options]"));
    }

    // guided mode

    @Test
    void numericChoice1EntersGuidedSingle() {
        // "1" opens guided form; first prompt is topology; "q" cancels.
        String out = runMenuWith("1\nq\n\nq\n");
        assertTrue(out.contains("single  \u00B7  one simulation trial"),
            "guided header should identify the subcommand");
        assertTrue(out.contains("topology"));
        assertTrue(out.contains("cancelled"));
    }

    @Test
    void wordChoiceSingleEntersGuided() {
        String out = runMenuWith("single\nq\n\nq\n");
        assertTrue(out.contains("single  \u00B7  one simulation trial"));
    }

    @Test
    void numericChoice3EntersGuidedRq1() {
        String out = runMenuWith("3\nq\n\nq\n");
        assertTrue(out.contains("rq1  \u00B7  thesis topology sweep"));
    }

    @Test
    void choiceIsCaseInsensitive() {
        String out = runMenuWith("SINGLE\nq\n\nq\n");
        assertTrue(out.contains("single  \u00B7  one simulation trial"));
    }

    @Test
    void guidedQuestionMarkShowsFieldHint() {
        // First prompt for "single" is topology. Typing "?" should print its
        // hint line and re-prompt on the same field.
        String out = runMenuWith("1\n?\nq\n\nq\n");
        assertTrue(out.contains("RANDOM | RING | SCALE_FREE | SMALL_WORLD"),
            "'?' at a prompt should reveal the field's hint");
    }

    @Test
    void guidedPrintsAssembledCommandBeforeConfirm() {
        // Accept all defaults for rq1 (only 4 fields), then cancel the confirm.
        String out = runMenuWith("3\n\n\n\n\nn\n\nq\n");
        assertTrue(out.contains("command:"),
            "the assembled command should be printed before confirmation");
        assertTrue(out.contains("rq1"));
        assertTrue(out.contains("-o"));
    }

    @Test
    void guidedCancelAtConfirmAborts() {
        // All defaults, then "n" at confirm -> cancelled.
        String out = runMenuWith("3\n\n\n\n\nn\n\nq\n");
        assertTrue(out.contains("cancelled"));
    }

    // free-form run

    @Test
    void freeformSingleHelpForwardsToPicocli() {
        String out = runMenuWith("single --help\n\nq\n");
        assertTrue(out.contains("aaron single [options]"),
            "'single --help' should forward to picocli and show its usage");
    }

    @Test
    void freeformUnknownCommandShowsPicocliError() {
        String out = runMenuWith("zzz\n\nq\n");
        boolean rejected = out.contains("Unmatched") || out.contains("Unknown")
                        || out.contains("exit code");
        assertTrue(rejected, "picocli should reject 'zzz' and the menu should continue");
        assertTrue(countOccurrences(out, "AAron") >= 2,
            "banner should be shown again after the failed command");
    }

    @Test
    void leadingLauncherPrefixIsStripped() {
        // "./aaron single --help" should behave the same as "single --help".
        String out = runMenuWith("./aaron single --help\n\nq\n");
        assertTrue(out.contains("aaron single [options]"));
    }

    // paste / multiline

    @Test
    void backslashContinuationMergesLines() {
        // Pasting a multi-line example with trailing \ should produce a single
        // logical command. We verify it via --help so no simulation actually runs.
        String input =
            "single --help \\\n" +
            "       -t RING\n" +
            "\nq\n";
        String out = runMenuWith(input);
        assertTrue(out.contains("aaron single [options]"),
            "backslash continuation must merge pasted lines into one command");
    }

    @Test
    void tokenizerPreservesQuotedPaths() {
        String[] tokens = InteractiveMenu.tokenize("single -o \"my run.json\"");
        assertArrayEquals(new String[] {"single", "-o", "my run.json"}, tokens);
    }

    @Test
    void readerJoinsBackslashContinuedLines() throws Exception {
        String text = "a b c \\\n   d e\n";
        java.io.BufferedReader r = new java.io.BufferedReader(new java.io.StringReader(text));
        String combined = InteractiveMenu.readCommandLine(r);
        assertEquals("a b c  d e", combined);
    }

    // layout

    @Test
    void bannerIsRenderedBeforeFirstPrompt() {
        String out = runMenuWith("q\n");
        int bannerIdx = out.indexOf("AAron");
        int promptIdx = out.indexOf(">");
        assertTrue(bannerIdx >= 0);
        assertTrue(promptIdx > bannerIdx, "banner must precede the first prompt");
    }

    @Test
    void bannerAdvertisesBothInteractionModes() {
        String out = runMenuWith("q\n");
        assertTrue(out.contains("guided"));
        assertTrue(out.contains("type a command"));
    }

    // helpers

    private static int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
