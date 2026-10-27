# Course waitlist: Math III

Enrollment, dropping and FIFO waitlist promotion for one course offering, Math III. No starter project was
supplied, so this follows the brief's fallback: a small in-memory Java service with a command-line prototype
for trying it by hand.

## Requirements

- JDK 17 or later (check with `java -version`). That's all: the Maven Wrapper (`mvnw`) downloads Maven 3.9.9
  on first use, so Maven doesn't need to be installed. If `JAVA_HOME` isn't set, the wrapper uses `java` from
  the `PATH`.

## Run the tests

macOS / Linux:

```sh
./mvnw test                          # all tests; the two concurrency tests repeat 50 times each
./mvnw test -Dtest=ConcurrencyTest   # a single test class
./mvnw verify                        # tests plus a coverage report at target/site/jacoco/index.html
```

Windows (PowerShell; keep the quotes around `-D` arguments):

```powershell
.\mvnw.cmd test
.\mvnw.cmd test "-Dtest=ConcurrencyTest"
.\mvnw.cmd verify
```

All 143 test runs pass: 43 tests plus 2 concurrency tests × 50 repetitions. An installed Maven 3.9+ works too:
use `mvn` in place of the wrapper.

## Run the prototype

```sh
./mvnw package                               # Windows: .\mvnw.cmd package. Runs the tests, then builds the jar
java -jar target/course-waitlist-1.0.0.jar   # 2 seats by default; add a number to change it, e.g. ... .jar 1
```

A seat count that isn't a whole number of at least 1 (`abc`, `0`) prints a usage line and exits with status 2.

It starts with five sample learners, one for each prerequisite case:

| Learner | Completed | Eligible for Math III |
| --- | --- | --- |
| ana, ben, cara | Math I, Math II | yes |
| dev | Math I | no, missing Math II |
| eve | Math II | no, missing Math I |

Type one command per line; `help` lists them. This session tries each rule with the default 2 seats:

```
enroll ana                    ->  Enrolled[]
enroll ben                    ->  Enrolled[]
enroll cara                   ->  Waitlisted[position=1]
enroll cara                   ->  AlreadyWaitlisted[position=1]
enroll dev                    ->  PrereqNotMet[missing=[Math II]]
enroll zoe                    ->  UnknownLearner[]
show                          ->  Seats 2/2 | enrolled [ana, ben] | waitlist [cara]
learner cara Math I           ->  Learner cara has completed [Math I]      (cara loses Math II while waiting)
drop ana                      ->  Dropped[promoted=Optional.empty, skippedIneligible=[cara]]
drop ana                      ->  AlreadyDropped[]
enroll ana                    ->  AlreadyDropped[]
status cara                   ->  REMOVED_INELIGIBLE
learner zoe Math I, Math II   ->  Learner zoe has completed [Math I, Math II]
enroll zoe                    ->  Enrolled[]
quit
```

`learner <id> <course, course...>` adds a learner or replaces an existing learner's courses. Course names
contain spaces, so they are comma-separated. At least one course is required, so a stray `learner dev` prints
a usage line instead of wiping dev's courses. `learners` lists everyone in the directory.

## Using the service from code

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
mvnw, mvnw.cmd, .mvn/     Maven Wrapper: runs the build with only a JDK installed
AI-prompts.md             AI prompt and use record: prompts, responses, decisions and verification
src/main/java/waitlist/
  Learner.java            immutable learner: id + completed courses
  Eligibility.java        the fixed rule: Math I and Math II
  EnrollmentService.java  enroll / drop / getStatus / snapshot, under one lock
  EnrollResult.java       sealed result types for enroll
  DropResult.java         sealed result types for drop
  RecordStatus.java, EnrollmentRecord.java
  Main.java               command-line prototype: one command per line
src/test/java/waitlist/
  TestSupport.java        Checked wrapper: asserts the invariants after every call
  EnrollTest, DropPromotionTest, EligibilityTest, InvalidRequestTest, ConcurrencyTest, MainTest
docs/
  DESIGN.md               design specification and assumptions
  LIMITATIONS.md          limitations and follow-up risks
```
