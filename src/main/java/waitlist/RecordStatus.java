package waitlist;

/** Where a learner stands in the Math III offering. {@code DROPPED} is final. */
public enum RecordStatus {
    ENROLLED,
    WAITLISTED,
    DROPPED,
    REMOVED_INELIGIBLE
}
