package waitlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class InvalidRequestTest extends TestSupport {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", " A", "A ", "\tA"})
    void malformedIdsAreRejectedWithoutChangingState(String id) {
        newService(1, "A", "B");
        enrollAll("A", "B");

        assertInstanceOf(EnrollResult.InvalidRequest.class, assertNoChange(() -> service.enroll(id)));
        assertInstanceOf(DropResult.InvalidRequest.class, assertNoChange(() -> service.drop(id)));
    }

    @Test
    void unknownLearnerIsRejected() {
        newService(1, "A");
        service.enroll("A");

        assertEquals(new EnrollResult.UnknownLearner(), assertNoChange(() -> service.enroll("Z")));
        assertEquals(new DropResult.UnknownLearner(), assertNoChange(() -> service.drop("Z")));
    }

    @Test
    void directoryEntryUnderTheWrongKeyCountsAsMissing() {
        newService(1);
        learners.put("A", eligible("B"));

        assertEquals(new EnrollResult.UnknownLearner(), assertNoChange(() -> service.enroll("A")));
    }

    @Test
    void dropByALearnerWhoNeverEnrolledReturnsNotEnrolled() {
        newService(1, "A");
        assertEquals(new DropResult.NotEnrolled(), assertNoChange(() -> service.drop("A")));
    }

    @Test
    void dropByAWaitlistedLearnerReturnsNotEnrolledAndKeepsTheirPlace() {
        newService(1, "A", "B");
        enrollAll("A", "B");
        assertEquals(new DropResult.NotEnrolled(), assertNoChange(() -> service.drop("B")));
    }

    @Test
    void secondDropReturnsAlreadyDropped() {
        newService(1, "A", "B");
        enrollAll("A", "B");
        service.drop("A");

        assertEquals(new DropResult.AlreadyDropped(), assertNoChange(() -> service.drop("A")));
    }

    @Test
    void invalidConfigurationThrows() {
        assertThrows(IllegalArgumentException.class, () -> new EnrollmentService(0, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new EnrollmentService(-1, Map.of()));
        assertThrows(NullPointerException.class, () -> new EnrollmentService(1, null));

        assertThrows(NullPointerException.class, () -> new Learner(null, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new Learner("", Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new Learner(" A", Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new Learner("A", Set.of(" ")));
        assertThrows(NullPointerException.class, () -> new Learner("A", null));
    }

    @Test
    void learnerKeepsItsOwnCopyOfCompletedCourses() {
        Set<String> courses = new HashSet<>(Set.of("Math I"));
        Learner learner = new Learner("A", courses);
        courses.add("Math II");

        assertEquals(Set.of("Math I"), learner.completedCourses());
    }
}
