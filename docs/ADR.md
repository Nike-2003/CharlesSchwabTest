# Architecture decision records

Each record states one decision, why it was made, what else was considered, and what it costs. The design
itself is in [DESIGN.md](DESIGN.md); known gaps are in [LIMITATIONS.md](LIMITATIONS.md). All records are
accepted as of 28 Oct 2026.

## ADR-001: An in-memory Java service with a thin command-line prototype

**Context.** No starter project was supplied. The brief allows "a small service or command-line prototype with
an in-memory store", and the evaluator has to be able to run it on their own machine.

**Decision.** A single Java 17 class, `EnrollmentService`, holds all state in memory. `Main` is a thin CLI on
top that has no rules of its own. The build is Maven with the Maven Wrapper, so a JDK is the only requirement.

**Alternatives.**
- TypeScript: our first draft. We switched to Java because it's the language we know best, and records and
  sealed interfaces fit the result types well.
- An HTTP API or a database: out of scope for one offering and more to run and review.
- A library with no CLI: the brief asks for runnable code, and a CLI lets the rules be tried by hand.

**Consequences.**
- State is lost when the process ends.
- A Java object can be shared across threads, so concurrency has to be handled (ADR-004) even with no starter.

## ADR-002: Skip and remove an ineligible learner at the head of the waitlist

**Context.** The brief asks us to "define whether an ineligible learner is skipped or remains waiting", and to
say what happens when one reaches the front. Only eligible learners join the queue, so this happens when a
learner's completed courses change while they wait.

**Decision.** Eligibility is checked when a learner asks to enroll, and again only when they reach the head as a
seat opens. An ineligible head becomes `REMOVED_INELIGIBLE` and leaves the queue, and the next head is checked,
until someone is promoted or the queue is empty.

**Alternatives.**
- Skip and retain: the retained learner keeps blocking or being re-checked, and a seat can sit open while
  people are waiting, which breaks the "non-empty waitlist means full" invariant.
- Check eligibility at request time only: doesn't answer the brief's question.
- Re-check the whole queue on every change: checks people early and costs a full scan per drop.

**Consequences.**
- A removed learner loses their place. If their grade is corrected, they can ask again and join at the back.
- An ineligible learner further back keeps a place until they reach the head, so positions can be pessimistic.

## ADR-003: "A learner may enroll only once" means a seat is held at most once

**Context.** The sentence can mean "no duplicate enrollment at the same time" or "never enroll again after
dropping". The brief doesn't say which.

**Decision.** We read it as "hold a seat at most once". `DROPPED` is final, and enrolling again returns
`EnrollResult.AlreadyDropped`. A learner removed as ineligible never held a seat, so they may ask again.

**Alternatives.** Allowing re-enrollment after a drop. That's the looser reading, but it makes the sentence
redundant with AC 2's duplicate rule.

**Consequences.** A mistaken drop can't be undone. This is stated as an interpretation in DESIGN.md and as a
limitation in LIMITATIONS.md.

## ADR-004: One private lock around every public method, with promotion inside the drop

**Context.** Enroll, drop and promotion must not interleave. Otherwise two learners could take one seat, or a
newcomer could take a freed seat ahead of the queue.

**Decision.** One private, non-fair `ReentrantLock` wraps the whole body of `enroll`, `drop`, `getStatus` and
`snapshot`. A drop frees the seat and fills it from the queue under the same lock, so no other call sees the
seat free. FIFO is defined as the order in which calls acquire the lock.

**Alternatives.**
- `synchronized` on the instance: outside code could lock the same object and block the service.
- A fair lock: costs throughput and still doesn't guarantee arrival order.
- Concurrent collections or finer-grained locks: the drop and its promotion still have to be one atomic step,
  so they'd add complexity for no gain at this size.

**Consequences.**
- Every call is serialised, which is fine for one offering but a throughput limit at scale.
- The lock doesn't cover the caller's learner directory, so DESIGN.md states a threading contract (ADR-006).
- `ConcurrencyTest` checks this. With the lock removed, the enroll race failed in 39 of 50 repetitions and the
  drop race in 31 of 50.

## ADR-005: Requests return sealed result records and never throw

**Context.** AC 5 asks that invalid requests and duplicate drops "return clear results without corrupting"
state.

**Decision.** `enroll` returns an `EnrollResult` and `drop` returns a `DropResult`. Both are sealed interfaces
with one record per outcome, such as `Waitlisted(position)` or `Dropped(promoted, skippedIneligible)`. Every
check runs before the first change to state. Only bad configuration throws: a capacity below 1, a null directory,
or an invalid `Learner`.

**Alternatives.**
- Exceptions for rejected requests: a duplicate or unknown request is an expected outcome, not an error.
- A status enum or boolean: can't carry the position, the missing courses or who was promoted.

**Consequences.**
- `AlreadyDropped`, `UnknownLearner` and `InvalidRequest` exist in both interfaces, so code always uses the
  qualified name, for example `DropResult.AlreadyDropped`.
- Java 17 can't check a `switch` over sealed types for completeness. The CLI prints each record's `toString`.

## ADR-006: The learner directory is the caller's map, checked at lookup

**Context.** Learners come from outside the service, and tests need to change a learner's courses between
calls to simulate a corrected grade.

**Decision.** The constructor takes a `Map<String, Learner>`, keeps the reference and only reads it. An entry
counts only if it's non-null and its key equals the learner's id. This is checked on every lookup, not once in
the constructor, because entries can be replaced later.

**Alternatives.**
- A `LearnerDirectory` interface, or copying the map behind an update method on the service: tighter, but it
  adds API the brief doesn't ask for.
- Validating entries in the constructor: misses entries that are replaced afterwards.

**Consequences.**
- A bad entry fails quietly: the learner looks unknown. If it happens while they wait, they're removed when
  they reach the head.
- Thread safety needs the caller's help. If other threads update learners, the caller passes a
  `ConcurrentHashMap` and replaces whole entries.
