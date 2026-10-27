package waitlist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EligibilityTest extends TestSupport {

    @Test
    void ruleNeedsBothMathIAndMathIIMatchedExactly() {
        assertEquals(List.of(), Eligibility.missingPrereqs(learner("A", "Math I", "Math II")));
        assertEquals(List.of("Math II"), Eligibility.missingPrereqs(learner("A", "Math I")));
        assertEquals(List.of("Math I"), Eligibility.missingPrereqs(learner("A", "Math II")));
        assertEquals(List.of("Math I", "Math II"), Eligibility.missingPrereqs(learner("A")));
        assertEquals(List.of(), Eligibility.missingPrereqs(learner("A", "Math I", "Math II", "Physics")));
        assertEquals(List.of("Math II"), Eligibility.missingPrereqs(learner("A", "Math I", "MATH II")));
    }

    @Test
    void ineligibleRequestCreatesNoRecordAndIsRejectedAgainOnRetry() {
        newService(1, "A");
        service.enroll("A"); // full, so B would be waitlisted if eligible
        learners.put("B", learner("B", "Math I"));

        EnrollResult expected = new EnrollResult.PrereqNotMet(List.of("Math II"));
        assertEquals(expected, assertNoChange(() -> service.enroll("B")));
        assertEquals(expected, assertNoChange(() -> service.enroll("B")));
        assertEquals(Optional.empty(), service.getStatus("B"));
    }

    @Test
    void ineligibleHeadIsSkippedAndRemoved() {
        newService(1, "A", "B", "C");
        enrollAll("A", "B", "C");
        learners.put("B", learner("B", "Math I")); // B's Math II grade is withdrawn while waiting

        assertEquals(new DropResult.Dropped(Optional.of("C"), List.of("B")), service.drop("A"));
        assertEquals(RecordStatus.REMOVED_INELIGIBLE, statusOf("B"));
        assertEquals(RecordStatus.ENROLLED, statusOf("C"));
    }

    @Test
    void seatStaysOpenWhenEveryoneWaitingIsIneligible() {
        newService(1, "A", "B", "C");
        enrollAll("A", "B", "C");
        learners.put("B", learner("B"));
        learners.put("C", learner("C", "Math II"));

        assertEquals(new DropResult.Dropped(Optional.empty(), List.of("B", "C")), service.drop("A"));
        assertEquals(Set.of(), service.snapshot().enrolled());
        assertEquals(List.of(), service.snapshot().waitlist());
    }

    @Test
    void learnerMissingFromTheDirectoryIsRemovedAtTheHead() {
        newService(1, "A", "B", "C");
        enrollAll("A", "B", "C");
        learners.remove("B");

        assertEquals(new DropResult.Dropped(Optional.of("C"), List.of("B")), service.drop("A"));
    }

    @Test
    void onlyTheHeadIsChecked() {
        newService(1, "A", "B", "C");
        enrollAll("A", "B", "C");
        learners.put("C", learner("C", "Math I")); // C is behind the head

        assertEquals(new DropResult.Dropped(Optional.of("B"), List.of()), service.drop("A"));
        assertEquals(RecordStatus.WAITLISTED, statusOf("C"));
    }

    @Test
    void removedLearnerWhoIsStillIneligibleIsRejectedAgain() {
        newService(1, "A", "B", "C");
        enrollAll("A", "B", "C");
        learners.put("B", learner("B", "Math I"));
        service.drop("A");

        assertEquals(new EnrollResult.PrereqNotMet(List.of("Math II")), assertNoChange(() -> service.enroll("B")));
        assertEquals(RecordStatus.REMOVED_INELIGIBLE, statusOf("B"));
    }

    @Test
    void removedLearnerWhoBecomesEligibleRejoinsAtTheBack() {
        newService(1, "A", "B", "C", "D");
        enrollAll("A", "B", "C", "D");
        learners.put("B", learner("B", "Math I"));
        service.drop("A"); // B removed, C promoted, D waiting
        learners.put("B", eligible("B")); // B's grade is restored

        assertEquals(new EnrollResult.Waitlisted(2), service.enroll("B"));
        assertEquals(List.of("D", "B"), service.snapshot().waitlist());
    }
}
