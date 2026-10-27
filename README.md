# Course waitlist: Math III

Enrollment, dropping and FIFO waitlist promotion for one course offering, Math III. No starter project was
supplied, so this follows the brief's fallback: a small in-memory Java library. There is no `main` method;
the tests are how it runs.

## Requirements

- JDK 17 or later
- Maven 3.9 or later

## Run the tests

```sh
mvn test                          # all tests; the two concurrency tests repeat 50 times each
mvn test -Dtest=ConcurrencyTest   # a single test class
mvn verify                        # tests plus a coverage report at target/site/jacoco/index.html
```

All 132 test runs pass: 32 behaviour tests plus 2 concurrency tests × 50 repetitions.

## Usage

```java
Map<String, Learner> learners = new HashMap<>();
learners.put("ana", new Learner("ana", Set.of("Math I", "Math II")));
learners.put("ben", new Learner("ben", Set.of("Math I", "Math II")));

EnrollmentService mathIII = new EnrollmentService(1, learners);
mathIII.enroll("ana");   // Enrolled[]
mathIII.enroll("ben");   // Waitlisted[position=1]
mathIII.drop("ana");     // Dropped[promoted=Optional[ben], skippedIneligible=[]]
mathIII.drop("ana");     // AlreadyDropped[]
```

## Layout

```
src/main/java/waitlist/
  Learner.java            immutable learner: id + completed courses
  Eligibility.java        the fixed rule: Math I and Math II
  EnrollmentService.java  enroll / drop / getStatus / snapshot, under one lock
  EnrollResult.java       sealed result types for enroll
  DropResult.java         sealed result types for drop
  RecordStatus.java, EnrollmentRecord.java
src/test/java/waitlist/
  TestSupport.java        Checked wrapper: asserts the invariants after every call
  EnrollTest, DropPromotionTest, EligibilityTest, InvalidRequestTest, ConcurrencyTest
docs/
  DESIGN.md               design specification and assumptions
  LIMITATIONS.md          limitations and follow-up risks
  AI_USAGE.md             AI prompts, decisions and verification evidence
```
