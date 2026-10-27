package waitlist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DropPromotionTest extends TestSupport {

    @Test
    void dropPromotesTheHeadOfTheWaitlist() {
        newService(1, "A", "B", "C");
        enrollAll("A", "B", "C");

        assertEquals(new DropResult.Dropped(Optional.of("B"), List.of()), service.drop("A"));
        assertEquals(Set.of("B"), service.snapshot().enrolled());
        assertEquals(List.of("C"), service.snapshot().waitlist());
    }

    @Test
    void successiveDropsPromoteInFifoOrder() {
        newService(1, "A", "B", "C", "D");
        enrollAll("A", "B", "C", "D");

        assertEquals(new DropResult.Dropped(Optional.of("B"), List.of()), service.drop("A"));
        assertEquals(new DropResult.Dropped(Optional.of("C"), List.of()), service.drop("B"));
        assertEquals(new DropResult.Dropped(Optional.of("D"), List.of()), service.drop("C"));
    }

    @Test
    void dropWithAnEmptyWaitlistLeavesTheSeatFree() {
        newService(1, "A", "B");
        service.enroll("A");

        assertEquals(new DropResult.Dropped(Optional.empty(), List.of()), service.drop("A"));
        assertEquals(new EnrollResult.Enrolled(), service.enroll("B"));
    }

    @Test
    void enrolledLearnerCanDropAfterLeavingTheDirectory() {
        newService(1, "A", "B");
        enrollAll("A", "B");
        learners.remove("A");

        assertEquals(new DropResult.Dropped(Optional.of("B"), List.of()), service.drop("A"));
    }
}
