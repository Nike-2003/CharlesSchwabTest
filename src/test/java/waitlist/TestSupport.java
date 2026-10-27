package waitlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import waitlist.EnrollmentService.Snapshot;

/** Base class for single-threaded tests: builds a checked service and verifies the final state. */
abstract class TestSupport {

    /** The learner directory handed to the service; tests change entries to simulate grade changes. */
    protected final Map<String, Learner> learners = new HashMap<>();
    protected Checked service;

    /** Creates a checked service and adds each id as an eligible learner. */
    protected Checked newService(int capacity, String... eligibleIds) {
        for (String id : eligibleIds) {
            learners.put(id, eligible(id));
        }
        service = new Checked(new EnrollmentService(capacity, learners));
        return service;
    }

    protected void enrollAll(String... ids) {
        for (String id : ids) {
            service.enroll(id);
        }
    }

    @AfterEach
    void finalStateIsValid() {
        if (service != null) {
            assertInvariants(service.snapshot());
        }
    }

    /** Runs a call that must not change state, and returns its result. */
    protected <T> T assertNoChange(Supplier<T> call) {
        Snapshot before = service.snapshot();
        T result = call.get();
        assertEquals(before, service.snapshot(), "a rejected or duplicate request changed state");
        return result;
    }

    protected RecordStatus statusOf(String id) {
        return service.getStatus(id).map(EnrollmentRecord::status).orElse(null);
    }

    static Learner eligible(String id) {
        return new Learner(id, Set.of("Math I", "Math II"));
    }

    static Learner learner(String id, String... courses) {
        return new Learner(id, Set.of(courses));
    }

    /** The six invariants from the design, checked against one consistent snapshot. */
    static void assertInvariants(Snapshot s) {
        Set<String> enrolled = s.enrolled();
        List<String> waitlist = s.waitlist();

        assertTrue(s.capacity() >= 1, "1: capacity >= 1");
        assertTrue(enrolled.size() <= s.capacity(), "2: enrolled within capacity");

        assertEquals(waitlist.size(), new HashSet<>(waitlist).size(), "3: no duplicates in waitlist");
        for (String id : waitlist) {
            assertFalse(enrolled.contains(id), "3: " + id + " is both enrolled and waitlisted");
        }

        for (EnrollmentRecord r : s.records().values()) {
            assertEquals(r.status() == RecordStatus.ENROLLED, enrolled.contains(r.learnerId()), "4: enrolled index");
            assertEquals(r.status() == RecordStatus.WAITLISTED, waitlist.contains(r.learnerId()), "4: waitlist index");
        }
        for (String id : enrolled) {
            assertNotNull(s.records().get(id), "4: enrolled id without a record");
        }
        for (String id : waitlist) {
            assertNotNull(s.records().get(id), "4: waitlisted id without a record");
        }

        if (!waitlist.isEmpty()) {
            assertEquals(s.capacity(), enrolled.size(), "5: a non-empty waitlist means the course is full");
        }

        long previousSeq = Long.MIN_VALUE;
        for (String id : waitlist) {
            Long seq = s.records().get(id).waitlistSeq();
            assertNotNull(seq, "6: waitlisted record without a sequence number");
            assertTrue(seq > previousSeq, "6: waitlist out of FIFO order");
            previousSeq = seq;
        }
        for (EnrollmentRecord r : s.records().values()) {
            if (r.status() != RecordStatus.WAITLISTED) {
                assertNull(r.waitlistSeq(), "6: only WAITLISTED records carry a sequence number");
            }
        }
    }

    /** Wraps the service and asserts the invariants after every enroll and drop. */
    static final class Checked {
        private final EnrollmentService inner;

        Checked(EnrollmentService inner) {
            this.inner = inner;
        }

        EnrollResult enroll(String id) {
            EnrollResult result = inner.enroll(id);
            assertInvariants(inner.snapshot());
            return result;
        }

        DropResult drop(String id) {
            DropResult result = inner.drop(id);
            assertInvariants(inner.snapshot());
            return result;
        }

        Optional<EnrollmentRecord> getStatus(String id) {
            return inner.getStatus(id);
        }

        Snapshot snapshot() {
            return inner.snapshot();
        }
    }
}
