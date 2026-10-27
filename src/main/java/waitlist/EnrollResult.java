package waitlist;

import java.util.List;

/**
 * Every outcome of {@link EnrollmentService#enroll}. Only {@code Enrolled} and {@code Waitlisted} change state.
 * {@code AlreadyDropped}, {@code UnknownLearner} and {@code InvalidRequest} also exist in {@link DropResult},
 * so code always refers to them by qualified name.
 */
public sealed interface EnrollResult {

    record Enrolled() implements EnrollResult {
    }

    /** @param position 1-based place in the waitlist */
    record Waitlisted(int position) implements EnrollResult {
    }

    record AlreadyEnrolled() implements EnrollResult {
    }

    /** @param position current 1-based place in the waitlist */
    record AlreadyWaitlisted(int position) implements EnrollResult {
    }

    record AlreadyDropped() implements EnrollResult {
    }

    record PrereqNotMet(List<String> missing) implements EnrollResult {
    }

    record UnknownLearner() implements EnrollResult {
    }

    record InvalidRequest(String reason) implements EnrollResult {
    }
}
