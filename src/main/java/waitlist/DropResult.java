package waitlist;

import java.util.List;
import java.util.Optional;

/** Every outcome of {@link EnrollmentService#drop}. Only {@code Dropped} changes state. */
public sealed interface DropResult {

    /**
     * @param promoted          the waitlisted learner who took the freed seat, if any
     * @param skippedIneligible learners removed from the head of the waitlist as ineligible, in order
     */
    record Dropped(Optional<String> promoted, List<String> skippedIneligible) implements DropResult {
        public Dropped {
            skippedIneligible = List.copyOf(skippedIneligible);
        }
    }

    record NotEnrolled() implements DropResult {
    }

    record AlreadyDropped() implements DropResult {
    }

    record UnknownLearner() implements DropResult {
    }

    record InvalidRequest(String reason) implements DropResult {
    }
}
