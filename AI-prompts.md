# AI prompt and use record

| Step | AI's part | My decision | Informed |
| --- | --- | --- | --- |
| [Prompt 1](#prompt-1-the-brief-and-my-design-decisions): plan and review | a full plan, then 26 numbered defects and risks | input to prompt 2 | prompt 2 |
| [Prompt 2](#prompt-2-what-to-do-with-the-review): apply the review | applied my 7 changes, plus wording, documentation and test items | accepted most; rejected two | DESIGN.md, LIMITATIONS.md, the test plan |
| [Implementation](#implementation) | wrote the code, tests and documents | accepted after review; 4 changes | `src/`, `docs/`, README.md |

The checks behind all of this are under [Verification](#verification).

## Prompt 1: the brief and my design decisions

```text
The problem statement brief is attached as a PDF.

There's no starter project. We will use a small in-memory Java 17 service, built with Maven and tested with JUnit, with JaCoCo for coverage. I'll include the Maven Wrapper so whoever evaluates it only needs a JDK on their machine. No HTTP layer, frontend or database.

I've already made most of the design calls, listed below. Please plan around them, and if any of them clashes with the brief, tell me instead of quietly working around it.

Course and prerequisites:
We proceed with some assumptions and data setup. There's one course, Math III. To take it a learner needs both "Math I" and "Math II". The names are exact and case-sensitive, and they're hard-coded constants, not configuration. Eligibility is checked when someone asks to enroll and again when they reach the front of the waitlist, because completed courses can change while they wait (a corrected grade, for example).

Learners:
The service gets a learner directory, a Map<String, Learner>, in its constructor and only ever reads it. Learner is an immutable record with an id and a set of completed courses. Tests simulate a grade change by swapping a learner's entry between calls. A well-formed id that isn't in the directory, and that the service has no record of, gets UnknownLearner.

Rules:
- Eligible and a seat is free: Enrolled. Eligible but the course is full: Waitlisted, with a 1-based position.
- Not eligible when they ask: PrereqNotMet with the missing courses, and no record is created.
- If the learner at the front of the queue isn't eligible when a seat opens, they're skipped and removed (REMOVED_INELIGIBLE) and we check the next one, until someone gets the seat or the queue is empty. Nobody further back gets checked early.
- "A learner may enroll only once": once you've held a seat and dropped it, you can't come back (AlreadyDropped). Someone removed as ineligible never held a seat, so they can ask again and go to the back of the queue.
- Duplicate requests return AlreadyEnrolled or AlreadyWaitlisted(position) and don't change anything.
- Only enrolled learners can drop. There's no leaving the waitlist: dropping a waitlisted learner gives NotEnrolled, and so does dropping twice.
- Promotion happens inside the drop that frees the seat.

Errors:
Requests never throw. Null, empty or padded ids get InvalidRequest. Only bad construction throws: a capacity below 1, or a blank learner id or course name. Every check runs before anything changes, so a rejected request leaves no trace.

Running it:
The brief asks for runnable code, so there's a command-line prototype (a Main class) on top of the service. It reads one command per line (learner, learners, enroll, drop, status, show, help, quit) and prints the result. It has no business rules of its own. Lets start with five sample learners so the evaluator can try everything straight away: ana, ben and cara have both courses, dev only has Math I and eve only has Math II. "learner <id> <courses>" adds a learner or replaces their courses, which is also how you'd make someone on the waitlist ineligible. "mvnw package" builds a runnable jar, and the number of seats is an optional argument that defaults to 2.

Implementation:
I want to have classes named Learner, Eligibility (the two course constants and missingPrereqs(learner)), EnrollmentService, RecordStatus (ENROLLED, WAITLISTED, DROPPED, REMOVED_INELIGIBLE), EnrollmentRecord (learner id, status, and a waitlist sequence number that's only set while waiting) and Main. Results are records inside two sealed interfaces, EnrollResult and DropResult. State is a set of enrolled ids, a FIFO list for the waitlist and one EnrollmentRecord per learner, replaced whole on every change. I want to have immutable Snapshot for tests, and getStatus(id) returns the learner's record. One private ReentrantLock wraps every public service method, and FIFO means the order in which calls get the lock.

I want two concurrency tests, even though the brief only asks for one when the starter is concurrent: 200 learners enrolling into 10 seats at once, and one seat holder being dropped from 50 threads at once. Both start from a CountDownLatch and repeat 50 times. A small test wrapper checks the invariants after every call: capacity, no duplicates, statuses match the lists, a non-empty waitlist means the course is full, and FIFO order.

Based on the above, I want the following:
1. The plan. Please cover scope; stack, layout and a task breakdown table; assumptions; the data model with a learner-lifecycle state diagram (Mermaid is fine); the API; algorithms in pseudocode; invariants; concurrency and persistence; a test matrix mapped to AC 1-6, plus one test that scripts a short command-line session; an AI-usage record plan with "break-it-once" checks (one deliberate bug per rule, and the test that should catch it); what goes in DESIGN.md, README.md (including how to run the prototype and the tests with only a JDK) and LIMITATIONS.md; a time split within 7-8 hours; and a compliance checklist against the brief. Keep it proportionate: nothing the brief doesn't ask for, roughly 30 focused tests, and break-it-once checks for the key rules rather than every line.
2. After the plan, a separate numbered list of defects and risks, the way a strict assessor would see them. I want to check whether the concurrency tests could pass when they shouldn't, whether the directory can hold entries the service can't use, whether every invalid or duplicate request gets a clear result (AC 5), how the shared directory squares with the thread-safety claim, and test gaps. Suggest a fix for each but don't apply anything yet. I'll come back with what I want changed.

```

## Response 1 (summary)

The agent checked my design calls against the brief and found no clashes. Before the plan it made four points:

- **The front-of-queue re-check is required, not optional.** My draft of prompt 1 said the brief doesn't
  mention it. The agent pointed out that the brief asks what happens "when an ineligible learner reaches the
  front of the queue", and only eligible learners can join the queue. So a change while waiting is the only way
  to get there, and that test is the main evidence for AC 4. I agreed and took that remark out of prompt 1.
- **"A learner may enroll only once" is an interpretation.** List it as an assumption with a reason, and say in
  LIMITATIONS.md that a mistaken drop is final.
- **Some calls go beyond the brief.** The CLI, the `learner` command and both concurrency tests are extras,
  because there's no starter project. It kept them and asked for a one-line reason for each in DESIGN.md.
- **The rubric file wasn't attached,** so its compliance checklist maps to the brief only.

**Part 1, the plan:**
- scope, and the stack, layout and a 12-task breakdown;
- 13 assumptions;
- the data model with a Mermaid lifecycle diagram, and the API with a result table;
- enroll and drop in pseudocode, and 8 invariants;
- concurrency: one lock, FIFO defined as lock order, the directory contract, and a paragraph on mapping to a
  database;
- a test matrix of 28 scenario tests, 2 concurrency tests and a scripted CLI session, mapped to AC 1–6;

**Part 2, 26 defects and risks (D1–D26).** 
- **Concurrency tests that could pass when they shouldn't:**
  - worker exceptions are lost without `Future.get()` (D1, High);
  - nothing shows the tests can fail, so the lock-removal check must record real failure counts (D2, High);
  - checking invariants after every call serialises the workers (D3);
  - the drop test never runs enrolls at the same time, so queue-jumping is untested (D4).
- **Directory entries the service can't use:**
  - a key that doesn't match its learner's id (D6, High);
  - null values (D7);
  - padded ids that `Learner` accepts but requests reject (D8);
  - a learner who leaves the directory while they still have a record (D10).
- **Thread safety:** the lock doesn't cover writes to the caller's map (D11, High). It also offered a
  `putLearner` method on the service as an alternative.
- **AC 5:** a second drop, a drop by a waitlisted learner and a drop by someone with no record all return the
  same `NotEnrolled` (D12). Its fix was a `NotEnrolled` that carries the current status.
- **Test gaps:** policy edge cases such as a removed learner asking again, or everyone on the waitlist being
  ineligible (D17), and a "no change" check after every rejected request (D16).
- **Build and CLI:**
  - Java 17 has no pattern `switch` (D19);
  - `Set.copyOf` gives a different output order on each run (D20);
  - the wrapper needs an executable bit and LF line endings (D21);
  - CLI parsing (D22).

The rest covered:
- `getStatus` for unknown ids (D13);
- the order of the checks (D14);
- detail in the results (D15);
- a two-way status check (D18);
- FIFO with a non-fair lock (D23);
- the missing rubric (D24);
- scope creep (D25);
- record history (D26).

## Prompt 2: what to do with the review

```text
I want to fix the below ones:

1. Concurrency tests. Have the workers return results through Callables, and call Future.get() with a timeout on every one from the main thread, so an exception in a worker fails the test instead of vanishing. Use a ready latch and a start latch, and put a @Timeout on each test. Also replace the "one seat holder dropped from 50 threads" case, because its race window is too small. Start with 10 learners enrolled and 40 waiting, then have the 10 seat holders drop while 20 new eligible learners enroll at the same moment. Every drop should promote the next waiting learner, the first 10 waiting learners should end up enrolled, and the waitlist should be the other 30 in their original order followed by the 20 newcomers in any order. Check the invariants once at the end, on the main thread.

2. Learner ids and the directory. Learner should use the same id rule as the service (non-empty, no leading or trailing whitespace), and a null id should throw NullPointerException. Keep the plain Map, but if an entry's key doesn't match its learner's id, or the value is null, treat that learner as missing: UnknownLearner on a request, ineligible at the head of the queue. Do that check at lookup, not in the constructor, since entries can be replaced later.

3. Drop results. A second drop should get its own result, DropResult.AlreadyDropped. AC 5 asks for clear results and NotEnrolled hides what actually happened. A drop for an id with no record and no directory entry should return DropResult.UnknownLearner. That means AlreadyDropped, UnknownLearner and InvalidRequest will exist in both result interfaces, so note in DESIGN.md that code always uses the qualified names.

4. "Enroll only once". Write it in DESIGN.md as our interpretation rather than a requirement: we read it as "hold a seat at most once", and we wont allow re-enrolling after a drop.

5. Threading contract. Lock protects the service's own state, not the caller's map. If other threads update learners, the caller should pass a ConcurrentHashMap and replace whole entries.

6. Tests. Make sure the matrix covers a removed learner who asks again while still ineligible (PrereqNotMet, nothing changes), a learner who disappears from the directory while waiting, AlreadyWaitlisted reporting the new position after a promotion, a mismatched directory key, and getStatus.

7. Break-it-once. For the lock-removal checks, record the actual failure count out of the 50 repetitions, even if it's zero. Don't promise that both tests will fail.

For the rest of your list, go ahead with anything that's wording, documentation or a missing test, but otherwise leave the design as it is.

 These two suggestions from you I don't want to implement:

- The directory abstraction. I'm not replacing the Map with an interface, copying it, or adding an update method to the service. It would tighten things up, but it adds API the brief doesn't ask for, and the lookup check plus a documented contract cover it.
- Restructuring the data model or the test wrapper, for example a plain status map instead of the per-learner record, or a separate model inside the wrapper. They're reasonable ideas, but what we have is enough for the scope, they can be explored for future scope.

Send back the full updated plan, then a short list of what changed and which findings you left out.
```

## Response 2 (summary)

The agent applied all seven changes and kept the rest of its design. It ended with four short lists.

**What changed:**
- **Concurrency tests:**
  - workers are Callables, and the main thread calls `Future.get` with a timeout on each;
  - both tests use a ready latch and a start latch, and each has a `@Timeout`;
  - the invariants are checked once at the end;
  - the second test is now mine: 10 drops and 20 enrolls at the same moment.
- **Ids and the directory:** one id rule shared by `Learner` and the service, and a null id throws
  `NullPointerException`. A mismatched or null directory entry counts as missing at lookup.
- **Drop results:** `DropResult.AlreadyDropped` and `DropResult.UnknownLearner`, with the qualified-name note in
  DESIGN.md.
- **Wording:** "enroll only once" is worded as an interpretation, and the threading contract is stated plainly.
  Tests and the CLI use a plain `HashMap`, with a note on why that's safe.
- **New tests:**
  - a changed position after a promotion;
  - a learner who leaves the directory while waiting;
  - a bad directory entry;
  - `getStatus` in every state.
- **Lock-removal checks** record actual failure counts out of 50, zeros included.

**Kept from its first plan without a ruling from me:**
- `getStatus` returning `Optional`;
- `InvalidRequest(reason)`;
- a `Dropped` result that names who was promoted and who was removed;
- `Set.copyOf` in `Learner`;
- a stable output order;
- `release=17` with no preview features.

**Left out:**
- validating the directory in the constructor (D6, D7), because I chose the check at lookup;
- rejecting spaces inside ids (D8), noted in LIMITATIONS.md instead;
- the `putLearner` method (D11), which I rejected;
- `NotEnrolled` carrying the status (D12), replaced by `DropResult.AlreadyDropped`;
- its own version of the second concurrency test (D4), replaced by mine.

**A note for this record:** only one idea in my two rejections came from its review: the update method, which
was the `putLearner` alternative in D11. The directory interface, copying the map, the plain status map and the
separate model inside the wrapper came up in earlier review rounds, not in this run. So my second rejection
turned down ideas from those earlier rounds, and this record credits them there.

## Implementation

With the plan settled, I had the AI build it in this repo.

**Prompt:**

> Ok now in &lt;repo root&gt; is our repo root. Create a github repo for it and generate a Starter boilerplate code according to the plan. Implement all features mentioned in the plan. Refer to the assesment attached for any doubts. Try to be precise and optimal, don't overdo anything stick to the plan and assesment and maintain code quality and test coverage. Implementation needs to be as simple as possible and optimal.

Follow-up requests, summarised: add the command-line prototype and its sample learners, add the Maven Wrapper
so only a JDK is needed, and the fixes in the table below.

**Response:** the AI wrote the code in `src/main`, the tests in `src/test`, and README.md, DESIGN.md and
LIMITATIONS.md, following the plan.

**Changes after review:**

| Change | Why | Decision |
| --- | --- | --- |
| Removed a capacity re-check in `promoteNext` | Coverage showed it could never run: a drop always frees exactly one seat. | AI's suggestion, accepted |
| A bad seat count (`abc`, `0`) prints a usage line and exits with status 2 | It printed a Java stack trace. A fresh-agent run of the planning prompts had flagged this case. | Edited |
| Input is read as UTF-8, and a leading byte-order mark is ignored | Piping commands from Windows PowerShell made the first command "unknown". | Edited |
| `learner <id>` needs at least one course | I typed `learner dev` expecting a lookup, and it silently wiped dev's courses. `learners` already lists everyone. | Edited |

**My review:** TODO: what I read and checked myself, and anything I changed.

## Verification

- **Tests:** `./mvnw test` passes 143 test runs: 43 tests plus 2 concurrency tests × 50 repetitions. A `Checked`
  wrapper asserts the six invariants after every service call.
- **A machine without Maven:** `./mvnw test` (Git Bash) and `.\mvnw.cmd test` (PowerShell) each downloaded
  Maven 3.9.9 and passed every test.
- **Coverage (JaCoCo):** 100% of lines and branches in the service classes. `Main` is at 58 of 69 lines; the 11
  untested lines are `main()`, which connects `System.in` and `System.out`. Running the app exercises them.
- **Manual run:** the README's example session, run against the jar, printed every line as shown. Piping
  commands from Windows PowerShell and from Git Bash both work.
- **Planning prompts re-run:** a fresh agent given only the brief and prompts 1 and 2 reached the same design
  decisions as this repo: skip-and-remove at the head, "enroll only once" as an interpretation, the drop
  results, the directory check at lookup, one lock and the same two concurrency tests. It differed only in
  details the prompts leave open, such as result records carrying the learner id.

### Break-it-once checks

Each check planted one bug in a copy of the service and ran every test class except `ConcurrencyTest`; the
last row ran only `ConcurrencyTest`. They ran on the first version of the code, before the command-line
prototype existed; the service hasn't changed since. Every check failed at least one test, and the unchanged
code passes them all.

| Bug planted | Rule it breaks | Tests that failed |
| --- | --- | --- |
| Promote the head without re-checking eligibility | Re-check at the head | 4, including `EligibilityTest.ineligibleHeadIsSkippedAndRemoved` |
| Promote from the tail (`remove(size - 1)`) | FIFO | 9, including `DropPromotionTest.successiveDropsPromoteInFifoOrder` |
| Remove the `DROPPED` check in `enroll` | A drop is final | `EnrollTest.learnerWhoDroppedCannotEnrollAgain` |
| Remove the `DROPPED` check in `drop` | A duplicate drop gets a clear result | `InvalidRequestTest.secondDropReturnsAlreadyDropped` |
| Ignore the key/id check in `lookup` | A mismatched entry counts as missing | `InvalidRequestTest.directoryEntryUnderTheWrongKeyCountsAsMissing` |
| Write `DROPPED` before the `NotEnrolled` check | Checks run before any change | 2 `InvalidRequestTest` cases, caught by the no-change assertion and invariant 4 |
| Remove the capacity check in `enroll` | Capacity limit | 21, mostly through invariant 2 in `Checked` |
| Remove the lock from `enroll` and `drop` | Atomic calls | enroll race failed in **39 of 50** repetitions; drop race failed in **31 of 50** |
