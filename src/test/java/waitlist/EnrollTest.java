package waitlist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EnrollTest extends TestSupport {

    @Test
    void enrollsWhenASeatIsFreeAndPrerequisitesAreMet() {
        newService(2, "A");
        assertEquals(new EnrollResult.Enrolled(), service.enroll("A"));
        assertEquals(Set.of("A"), service.snapshot().enrolled());
    }

    @Test
    void waitlistsInRequestOrderWhenFull() {
        newService(1, "A", "B", "C");
        service.enroll("A");
        assertEquals(new EnrollResult.Waitlisted(1), service.enroll("B"));
        assertEquals(new EnrollResult.Waitlisted(2), service.enroll("C"));
        assertEquals(List.of("B", "C"), service.snapshot().waitlist());
    }

    @Test
    void repeatRequestFromAWaitlistedLearnerChangesNothing() {
        newService(1, "A", "B", "C");
        enrollAll("A", "B", "C");
        assertEquals(new EnrollResult.AlreadyWaitlisted(2), assertNoChange(() -> service.enroll("C")));
    }

    @Test
    void repeatRequestReportsTheNewPositionAfterAPromotion() {
        newService(1, "A", "B", "C");
        enrollAll("A", "B", "C");
        service.drop("A");
        assertEquals(new EnrollResult.AlreadyWaitlisted(1), service.enroll("C"));
    }

    @Test
    void repeatRequestFromAnEnrolledLearnerChangesNothing() {
        newService(1, "A");
        service.enroll("A");
        assertEquals(new EnrollResult.AlreadyEnrolled(), assertNoChange(() -> service.enroll("A")));
    }

    @Test
    void learnerWhoDroppedCannotEnrollAgain() {
        newService(2, "A");
        service.enroll("A");
        service.drop("A");
        assertEquals(new EnrollResult.AlreadyDropped(), assertNoChange(() -> service.enroll("A")));
    }

    @Test
    void getStatusReportsTheCurrentRecord() {
        newService(1, "A", "B");
        learners.put("X", learner("X", "Math I"));
        enrollAll("A", "B", "X"); // X is rejected for a missing prerequisite

        assertEquals(Optional.of(new EnrollmentRecord("A", RecordStatus.ENROLLED, null)), service.getStatus("A"));
        assertEquals(RecordStatus.WAITLISTED, statusOf("B"));
        assertEquals(Optional.empty(), service.getStatus("X"));
        assertEquals(Optional.empty(), service.getStatus("nobody"));
        assertEquals(Optional.empty(), service.getStatus(null));

        service.drop("A");
        assertEquals(RecordStatus.DROPPED, statusOf("A"));
        assertEquals(RecordStatus.ENROLLED, statusOf("B"));
    }
}
