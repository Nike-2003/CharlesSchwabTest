package waitlist;

/**
 * A learner's single record in the offering. It is replaced whole on every status change.
 *
 * @param waitlistSeq the learner's place in arrival order; non-null only while {@code WAITLISTED}
 */
public record EnrollmentRecord(String learnerId, RecordStatus status, Long waitlistSeq) {
}
