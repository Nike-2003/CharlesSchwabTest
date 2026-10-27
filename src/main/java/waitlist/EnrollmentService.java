package waitlist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Enrollment, drop and FIFO waitlist promotion for the Math III offering.
 *
 * <p>Thread-safe: every public method runs under one private lock, so each call, including the
 * promotion inside a drop, is atomic. The lock covers this service's state, not the caller's
 * learner directory: if other threads update learners while calls run, pass a
 * {@code ConcurrentHashMap} and replace whole entries.
 */
public final class EnrollmentService {

    private static final String INVALID_ID = "learner id must be non-empty with no leading or trailing whitespace";

    private final ReentrantLock lock = new ReentrantLock();
    private final int capacity;
    private final Map<String, Learner> learners;                         // caller's directory, read only
    private final Set<String> enrolled = new HashSet<>();
    private final List<String> waitlist = new ArrayList<>();             // FIFO, head = index 0
    private final Map<String, EnrollmentRecord> records = new HashMap<>(); // at most one per learner
    private long nextSeq = 0;

    public EnrollmentService(int capacity, Map<String, Learner> learners) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        this.capacity = capacity;
        this.learners = Objects.requireNonNull(learners, "learners");
    }

    public EnrollResult enroll(String learnerId) {
        if (!Learner.isValidId(learnerId)) {
            return new EnrollResult.InvalidRequest(INVALID_ID);
        }
        lock.lock();
        try {
            RecordStatus status = statusOf(learnerId);
            if (status == RecordStatus.ENROLLED) {
                return new EnrollResult.AlreadyEnrolled();
            }
            if (status == RecordStatus.WAITLISTED) {
                return new EnrollResult.AlreadyWaitlisted(waitlist.indexOf(learnerId) + 1);
            }
            if (status == RecordStatus.DROPPED) {
                return new EnrollResult.AlreadyDropped();
            }
            // No record, or REMOVED_INELIGIBLE: handled as a first request.
            Learner learner = lookup(learnerId);
            if (learner == null) {
                return new EnrollResult.UnknownLearner();
            }
            List<String> missing = Eligibility.missingPrereqs(learner);
            if (!missing.isEmpty()) {
                return new EnrollResult.PrereqNotMet(missing);
            }
            // All checks passed; state changes start here.
            if (enrolled.size() < capacity) {
                enrolled.add(learnerId);
                setRecord(learnerId, RecordStatus.ENROLLED, null);
                return new EnrollResult.Enrolled();
            }
            waitlist.add(learnerId);
            setRecord(learnerId, RecordStatus.WAITLISTED, nextSeq++);
            return new EnrollResult.Waitlisted(waitlist.size());
        } finally {
            lock.unlock();
        }
    }

    public DropResult drop(String learnerId) {
        if (!Learner.isValidId(learnerId)) {
            return new DropResult.InvalidRequest(INVALID_ID);
        }
        lock.lock();
        try {
            RecordStatus status = statusOf(learnerId);
            if (status == RecordStatus.DROPPED) {
                return new DropResult.AlreadyDropped();
            }
            if (status == null && lookup(learnerId) == null) {
                return new DropResult.UnknownLearner();
            }
            if (status != RecordStatus.ENROLLED) {
                return new DropResult.NotEnrolled();
            }
            enrolled.remove(learnerId);
            setRecord(learnerId, RecordStatus.DROPPED, null);
            return promoteNext();
        } finally {
            lock.unlock();
        }
    }

    public Optional<EnrollmentRecord> getStatus(String learnerId) {
        lock.lock();
        try {
            return Optional.ofNullable(records.get(learnerId));
        } finally {
            lock.unlock();
        }
    }

    public Snapshot snapshot() {
        lock.lock();
        try {
            return new Snapshot(capacity, Set.copyOf(enrolled), List.copyOf(waitlist), Map.copyOf(records), nextSeq);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Fills the seat a drop just freed. Skip and remove: an ineligible head becomes REMOVED_INELIGIBLE
     * and the next head is checked, until someone is promoted or the waitlist is empty.
     */
    private DropResult.Dropped promoteNext() {
        List<String> skipped = new ArrayList<>();
        while (!waitlist.isEmpty()) {
            String head = waitlist.remove(0);
            Learner learner = lookup(head);
            if (learner != null && Eligibility.missingPrereqs(learner).isEmpty()) {
                enrolled.add(head);
                setRecord(head, RecordStatus.ENROLLED, null);
                return new DropResult.Dropped(Optional.of(head), skipped);
            }
            setRecord(head, RecordStatus.REMOVED_INELIGIBLE, null);
            skipped.add(head);
        }
        return new DropResult.Dropped(Optional.empty(), skipped);
    }

    private RecordStatus statusOf(String learnerId) {
        EnrollmentRecord record = records.get(learnerId);
        return record == null ? null : record.status();
    }

    /** The learner's directory entry, or null if it is missing or its learner's id differs from the key. */
    private Learner lookup(String learnerId) {
        Learner learner = learners.get(learnerId);
        return learner != null && learner.id().equals(learnerId) ? learner : null;
    }

    private void setRecord(String learnerId, RecordStatus status, Long waitlistSeq) {
        records.put(learnerId, new EnrollmentRecord(learnerId, status, waitlistSeq));
    }

    /** An immutable, consistent copy of the offering's state, taken under the lock. */
    public record Snapshot(int capacity, Set<String> enrolled, List<String> waitlist,
                           Map<String, EnrollmentRecord> records, long nextSeq) {
    }
}
