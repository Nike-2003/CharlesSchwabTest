package waitlist;

import java.util.List;

/** The fixed prerequisite rule for Math III: both Math I and Math II, matched exactly. */
public final class Eligibility {

    public static final List<String> REQUIRED = List.of("Math I", "Math II");

    private Eligibility() {
    }

    /** Returns the required courses the learner has not completed, in a fixed order; empty means eligible. */
    public static List<String> missingPrereqs(Learner learner) {
        return REQUIRED.stream().filter(course -> !learner.completedCourses().contains(course)).toList();
    }
}
