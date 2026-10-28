package waitlist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MainTest {

    private static List<String> run(int seats, String script) throws IOException {
        return run(seats, false, script);
    }

    private static List<String> run(int seats, boolean samples, String script) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Main cli = new Main(seats, new PrintStream(bytes, true, StandardCharsets.UTF_8));
        if (samples) {
            cli.addSampleLearners();
        }
        cli.run(new StringReader(script));
        return bytes.toString(StandardCharsets.UTF_8).lines().toList();
    }

    @Test
    void sampleLearnersCoverEachPrerequisiteCaseAndNewOnesCanBeAdded() throws IOException {
        List<String> output = run(2, true, """
                enroll ana
                enroll ben
                enroll cara
                enroll dev
                enroll eve
                learner zoe Math I, Math II
                learners
                """);

        assertEquals(List.of(
                "Learner ana has completed [Math I, Math II]",
                "Learner ben has completed [Math I, Math II]",
                "Learner cara has completed [Math I, Math II]",
                "Learner dev has completed [Math I]",
                "Learner eve has completed [Math II]",
                "Enrolled[]",
                "Enrolled[]",
                "Waitlisted[position=1]",
                "PrereqNotMet[missing=[Math II]]",
                "PrereqNotMet[missing=[Math I]]",
                "Learner zoe has completed [Math I, Math II]",
                "Learner ana has completed [Math I, Math II]",
                "Learner ben has completed [Math I, Math II]",
                "Learner cara has completed [Math I, Math II]",
                "Learner dev has completed [Math I]",
                "Learner eve has completed [Math II]",
                "Learner zoe has completed [Math I, Math II]"), output);
    }

    @Test
    void commandsDriveTheServiceAndPrintItsResults() throws IOException {
        List<String> output = run(1, """
                learner ana Math I, Math II
                learner ben Math I, Math II
                learner dev Math I
                enroll ana
                enroll ben
                enroll dev

                show
                drop ana
                status ben
                status zoe
                quit
                enroll ben
                """);

        assertEquals(List.of(
                "Learner ana has completed [Math I, Math II]",
                "Learner ben has completed [Math I, Math II]",
                "Learner dev has completed [Math I]",
                "Enrolled[]",
                "Waitlisted[position=1]",
                "PrereqNotMet[missing=[Math II]]",
                "Seats 1/1 | enrolled [ana] | waitlist [ben]",
                "Dropped[promoted=Optional[ben], skippedIneligible=[]]",
                "ENROLLED",
                "no record"), output); // nothing runs after quit
    }

    @Test
    void badInputGetsAMessageInsteadOfACrash() throws IOException {
        // U+2003 (em space) is whitespace to the id rule but not to the command parser.
        List<String> output = run(1, "learner\nlearner x\u2003 Math I\nbogus\nenroll\nhelp\n");

        assertEquals(Main.LEARNER_USAGE, output.get(0));
        assertEquals("Invalid learner: learner id must be non-empty with no leading or trailing whitespace", output.get(1));
        assertEquals("Unknown command: bogus. Type help.", output.get(2));
        assertEquals(new EnrollResult.InvalidRequest(
                "learner id must be non-empty with no leading or trailing whitespace").toString(), output.get(3));
        assertEquals(Main.HELP.lines().toList(), output.subList(4, output.size()));
    }

    @Test
    void learnerWithoutCoursesPrintsUsageAndKeepsTheExistingCourses() throws IOException {
        List<String> output = run(2, true, "learner dev\nenroll dev\n");

        assertEquals(List.of(Main.LEARNER_USAGE, "PrereqNotMet[missing=[Math II]]"), output.subList(5, 7));
    }

    @Test
    void byteOrderMarkAtTheStartOfPipedInputIsIgnored() throws IOException {
        assertEquals(List.of("Seats 0/1 | enrolled [] | waitlist []"), run(1, "\uFEFFshow\n"));
    }

    @Test
    void seatCountDefaultsToTwoAndAcceptsAnyPositiveNumber() {
        assertEquals(OptionalInt.of(2), Main.parseSeats(new String[0]));
        assertEquals(OptionalInt.of(1), Main.parseSeats(new String[] {"1"}));
        assertEquals(OptionalInt.of(25), Main.parseSeats(new String[] {"25"}));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "2.5", ""})
    void badSeatCountIsRejectedSoMainPrintsUsage(String seats) {
        assertEquals(OptionalInt.empty(), Main.parseSeats(new String[] {seats}));
    }
}
