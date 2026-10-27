# Limitations and follow-up risks

- **In memory, single process.** State is lost when the object is discarded. Correctness relies on one
  in-process lock; several processes or a database would need a transaction or row lock.
- **One lock serializes every call.** Fine for one course; a throughput limit at scale.
- **The learner directory belongs to the caller.** Concurrent updates are safe only with a `ConcurrentHashMap`
  and whole-entry replacement. A directory entry whose key differs from its learner's id is not rejected; it is
  treated as missing.
- **Concurrency tests show only the races they exercise.** With the lock removed from `enroll` and `drop`, the
  enroll race failed in 39 of 50 repetitions and the drop race in 31 of 50 (see AI_USAGE.md). A passing run is
  evidence, not proof, that no race exists.
- **A drop is final**, even if made by mistake.
- **A learner removed as ineligible loses their queue position.** If their grade is corrected later, they
  rejoin at the back.
- **An ineligible learner further back keeps a place** until they reach the head, so a reported position can
  be pessimistic.
- **A waitlisted learner cannot leave the waitlist**, and will be promoted when they reach the head.
- **Enrolled learners are never re-checked** if they lose eligibility.
- **Records are never cleaned up**; `DROPPED` and `REMOVED_INELIGIBLE` records stay for the life of the service.
- **No promotion notifications.**
- **One offering, Math III**, with its prerequisites fixed in code; capacity is fixed at construction.
