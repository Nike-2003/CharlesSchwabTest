package waitlist;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;

/** Starts many calls at the same moment and checks that counts, positions and FIFO order stay exact. */
class ConcurrencyTest {

    private static final int WAIT_SECONDS = 10;

    @RepeatedTest(50)
    @Timeout(30)
    void concurrentEnrollsFillEverySeatAndPositionExactlyOnce() throws Exception {
        List<String> ids = ids("L", 200);
        EnrollmentService service = new EnrollmentService(10, directory(ids));

        List<EnrollResult> results = runAtOnce(ids.stream()
                .<Callable<EnrollResult>>map(id -> () -> service.enroll(id))
                .toList());

        assertEquals(10, results.stream().filter(EnrollResult.Enrolled.class::isInstance).count());
        List<Integer> positions = results.stream()
                .filter(EnrollResult.Waitlisted.class::isInstance)
                .map(r -> ((EnrollResult.Waitlisted) r).position())
                .sorted()
                .toList();
        assertEquals(IntStream.rangeClosed(1, 190).boxed().toList(), positions);
        TestSupport.assertInvariants(service.snapshot());
    }

    @RepeatedTest(50)
    @Timeout(30)
    void dropsRacingNewEnrollsPromoteTheWaitlistInOrder() throws Exception {
        List<String> seated = ids("S", 10);
        List<String> waiting = ids("W", 40);
        List<String> newcomers = ids("N", 20);
        Map<String, Learner> learners = directory(seated);
        learners.putAll(directory(waiting));
        learners.putAll(directory(newcomers));
        EnrollmentService service = new EnrollmentService(10, learners);
        seated.forEach(service::enroll);
        waiting.forEach(service::enroll);

        List<Callable<Object>> tasks = new ArrayList<>();
        seated.forEach(id -> tasks.add(() -> service.drop(id)));
        newcomers.forEach(id -> tasks.add(() -> service.enroll(id)));
        List<Object> results = runAtOnce(tasks);

        Set<String> promoted = new HashSet<>();
        for (Object result : results.subList(0, 10)) {
            DropResult.Dropped dropped = assertInstanceOf(DropResult.Dropped.class, result);
            assertEquals(List.of(), dropped.skippedIneligible());
            promoted.add(dropped.promoted().orElseThrow());
        }
        results.subList(10, 30).forEach(result -> assertInstanceOf(EnrollResult.Waitlisted.class, result));

        EnrollmentService.Snapshot s = service.snapshot();
        Set<String> firstTenWaiting = Set.copyOf(waiting.subList(0, 10));
        assertEquals(firstTenWaiting, promoted);
        assertEquals(firstTenWaiting, s.enrolled());
        assertEquals(waiting.subList(10, 40), s.waitlist().subList(0, 30));
        assertEquals(Set.copyOf(newcomers), Set.copyOf(s.waitlist().subList(30, 50)));
        TestSupport.assertInvariants(s);
    }

    /** Runs each task on its own thread, all released together; a worker exception fails the test. */
    private static <T> List<T> runAtOnce(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(WAIT_SECONDS, SECONDS)) {
                        throw new IllegalStateException("start gate timed out");
                    }
                    return task.call();
                }));
            }
            assertTrue(ready.await(WAIT_SECONDS, SECONDS), "workers did not all start");
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(WAIT_SECONDS, SECONDS)); // rethrows anything a worker threw
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static List<String> ids(String prefix, int count) {
        return IntStream.range(0, count).mapToObj(i -> prefix + i).toList();
    }

    private static Map<String, Learner> directory(List<String> ids) {
        Map<String, Learner> learners = new HashMap<>();
        ids.forEach(id -> learners.put(id, TestSupport.eligible(id)));
        return learners;
    }
}
