package waitlist;

import java.util.Objects;
import java.util.Set;

/** An immutable learner: an id and the names of the courses they have completed. */
public record Learner(String id, Set<String> completedCourses) {

    public Learner {
        Objects.requireNonNull(id, "id");
        if (!isValidId(id)) {
            throw new IllegalArgumentException("learner id must be non-empty with no leading or trailing whitespace");
        }
        completedCourses = Set.copyOf(completedCourses); // immutable copy; rejects a null set or element
        for (String course : completedCourses) {
            if (course.isBlank()) {
                throw new IllegalArgumentException("course names must not be blank");
            }
        }
    }

    /** The one id rule, shared by learners and requests, so every learner that can be built can be reached. */
    static boolean isValidId(String id) {
        return id != null && !id.isEmpty() && id.equals(id.strip());
    }
}
