package waitlist;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** A command-line prototype over {@link EnrollmentService}: one command per line, results printed as returned. */
public final class Main {

    static final String HELP = """
            Commands:
              learner <id> <course, course...>   add or replace a learner, e.g. learner zoe Math I, Math II
              learners                           list every learner and their completed courses
              enroll <id>
              drop <id>
              status <id>
              show                               seats, enrolled learners and the waitlist
              help
              quit""";

    private final Map<String, Learner> learners = new HashMap<>();
    private final EnrollmentService service;
    private final PrintStream out;

    Main(int capacity, PrintStream out) {
        this.service = new EnrollmentService(capacity, learners);
        this.out = out;
    }

    static final String LEARNER_USAGE = "Usage: learner <id> <course, course...>, e.g. learner zoe Math I, Math II";

    static final String USAGE ="Usage: java -jar course-waitlist.jar [seats], where seats is a whole number of at least 1 (default 2)";

    public static void main(String[] args) throws IOException {
        OptionalInt seats = parseSeats(args);
        if (seats.isEmpty()) {
            System.err.println(USAGE);
            System.exit(2);
        }
        int capacity = seats.getAsInt();
        Main cli = new Main(capacity, System.out);
        System.out.println("Math III waitlist with " + capacity + " seat(s). Sample learners:");
        cli.addSampleLearners();
        System.out.println("Type help for commands.");
        cli.run(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    }

    /** The seat count from the arguments (2 if none), or empty if it is not a whole number of at least 1. */
    static OptionalInt parseSeats(String[] args) {
        if (args.length == 0) {
            return OptionalInt.of(2);
        }
        try {
            int seats = Integer.parseInt(args[0]);
            return seats >= 1 ? OptionalInt.of(seats) : OptionalInt.empty();
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    /** Learners covering each prerequisite case, so the rules can be tried straight away. */
    void addSampleLearners() {
        for (String learner : List.of(
                "ana Math I, Math II",
                "ben Math I, Math II",
                "cara Math I, Math II",
                "dev Math I",
                "eve Math II")) {
            addLearner(learner);
        }
    }

    void run(Reader input) throws IOException {
        BufferedReader reader = new BufferedReader(input);
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            // Windows PowerShell can start piped input with a byte-order mark, which strip() keeps.
            if (!execute(line.replace("\uFEFF", "").strip())) {
                return;
            }
        }
    }

    /** Runs one command; returns false when the user quits. */
    boolean execute(String line) {
        String[] parts = line.split("\\s+", 2);
        String arg = parts.length > 1 ? parts[1] : "";
        switch (parts[0]) {
            case "" -> { }
            case "learner" -> addLearner(arg);
            case "learners" -> learners.values().stream()
                    .sorted(Comparator.comparing(Learner::id))
                    .forEach(learner -> out.println(describe(learner)));
            case "enroll" -> out.println(service.enroll(arg));
            case "drop" -> out.println(service.drop(arg));
            case "status" -> out.println(service.getStatus(arg).map(r -> r.status().toString()).orElse("no record"));
            case "show" -> show();
            case "help" -> out.println(HELP);
            case "quit", "exit" -> {
                return false;
            }
            default -> out.println("Unknown command: " + parts[0] + ". Type help.");
        }
        return true;
    }

    /** {@code learner <id> <course, course...>}: course names may contain spaces, so they are comma-separated. */
    private void addLearner(String arg) {
        String[] parts = arg.split("\\s+", 2);
        Set<String> courses = parts.length > 1
                ? Arrays.stream(parts[1].split(",")).map(String::strip).filter(c -> !c.isEmpty())
                        .collect(Collectors.toCollection(TreeSet::new))
                : Set.of();
        if (courses.isEmpty()) {
            // At least one course, so a stray "learner dev" can't wipe dev's courses.
            out.println(LEARNER_USAGE);
            return;
        }
        try {
            Learner learner = new Learner(parts[0], courses);
            learners.put(learner.id(), learner);
            out.println(describe(learner));
        } catch (IllegalArgumentException e) {
            out.println("Invalid learner: " + e.getMessage());
        }
    }

    private static String describe(Learner learner) {
        return "Learner " + learner.id() + " has completed " + new TreeSet<>(learner.completedCourses());
    }

    private void show() {
        EnrollmentService.Snapshot s = service.snapshot();
        out.println("Seats " + s.enrolled().size() + "/" + s.capacity()
                + " | enrolled " + new TreeSet<>(s.enrolled()) + " | waitlist " + s.waitlist());
    }
}
