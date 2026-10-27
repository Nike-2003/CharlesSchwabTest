# AI usage record

## Summary

I used Claude (Anthropic) as an engineering assistant for:
- design review
- plan revision
- an independent re-derivation of the plan
- implementation and its verification

AI drafted most of the plan text, the code, the tests and these documents. I made the policy decisions: language, prerequisite rule, which review findings to apply and which to reject. I verified the result with the test suite, a coverage report and deliberate break-it-once checks (end of this file).

Prompts are quoted as I sent them, with local paths removed.

| # | Activity | Decision |
| --- | --- | --- |
| 1 | First architecture plan (TypeScript) | Edited |
| 2 | Second design (Java, Math III) | Used as input |
| 3 | Compare both plans against the brief | Accepted, then edited |
| 4 | Merge into plan v2 | Accepted |
| 5 | Language choice | **Rejected** the AI's recommendation |
| 6 | Simplify the prerequisite rule | Accepted |
| 7 | Re-derive the plan with a fresh agent | Two findings accepted |
| 8 | Independent review of v2 | Most accepted, two rejected, one wrong |
| 9 | Implementation | Accepted after verification |

---

### 1. First architecture plan (TypeScript)

- **Prompt:** TODO: add the prompt and tool used for the first draft.
- **Response:** a TypeScript/Vitest in-memory service with skip-and-remove, result types and an invariant checker.
- **Decision:** Edited. A compliance review in the same conversation led me to:
  - reject a waitlist-withdrawal path;
  - reject a mutex and concurrency suite;
  - correct the record-reuse wording.
- **Informed:** the base design (plan v1).

### 2. Second design (Java, Math III)

- **Source:** TODO: say where this design came from. If an AI tool produced it, record that prompt here.
- **Used for:** comparison input to entry 3.

### 3. Comparing the two plans against the brief

- **Prompt:**
  > Compare these two architectures for the same problem. the problem statement is and question is given in the third pdf. Evaluate carefully, with respect tot he plans stickign to what the assignment has asked and not overdoing or missing anything in sticking to the scop mentioned in the assignement and if htey have made any similar interpretations or different list them if yes.
- **Response (summary):**
  - Plan A followed the brief more closely, but allowing re-enrollment after a drop conflicts with "A learner may enroll only once".
  - Plan B avoided the skip-or-retain question by fixing eligibility at request time.
  - Plan B lacked the AI record, run commands and limitations deliverables.
  - Plan B's concurrency suite went beyond what the brief requires.
- **Decision:** Accepted. I asked for plan A to be the base, with fixes taken from plan B.
- **Verification:** I checked each cited sentence against the brief.

### 4. Merging into plan v2

- **Prompt:**
  > can you use A as a base and then make changes where needed from B as you have mentioned,
- **Response:** plan v2, which changed:
  - a drop is final;
  - removed-ineligible learners may request again;
  - an explicit task decomposition;
  - constructor validation;
  - invariants checked after every call;
  - record-first checks;
  - break-it-once checks;
  - a lifecycle diagram;
  - a rubric check.
- **Decision:** Accepted.
- **Informed:** DESIGN.md and the test matrix.

### 5. Language choice

- **Prompts:**
  > is it better to use java instrad of ts

  > i have better understanding of java lets swtich to that.

  > if no major change then use java 17 only
- **Response:**
  - The AI recommended staying with TypeScript unless the role used Java or I was faster in it.
  - Once I chose Java, it said the switch reopens concurrency, because a Java object can be shared across threads. It then added a private lock and a concurrency test.
  - It confirmed Java 17 needs no design change, because records and sealed interfaces are available.
- **Decision:** **Rejected** the TypeScript recommendation, because I know Java better. Accepted the lock and Java 17.
- **Verification:** `ConcurrencyTest` and the lock-removal check below.

### 6. Simplifying the prerequisite rule

- **Prompt:**
  > for the plan you made, lets make the courses checking like plan B, with math i,ii, and iii only to keep it simple
- **Response:** the course is Math III only, with "Math I" and "Math II" as constants in `Eligibility`. The configurable `Offering` record was removed.
- **Decision:** Accepted.
- **Verification:** `EligibilityTest.ruleNeedsBothMathIAndMathIIMatchedExactly`.

### 7. Re-deriving the plan with a fresh agent

- **Prompt:**
  > Can you launch and agent and run this flow with it and see if prompts and expected responsea re working well to acheive end result
- **Response:** an agent with no prior context rebuilt the plan from the brief and reviewed it. Two findings applied to my plan too:
  - `UnknownLearner` and `InvalidRequest` exist in both result interfaces, so importing them by short name would not compile;
  - returning `NotEnrolled` for a second drop is an AC 5 risk.
- **Decision:** Accepted both, applied in entry 8: the qualified-name rule and `DropResult.AlreadyDropped`.

### 8. Independent review of v2

- **Prompt to the reviewer:** TODO: add the prompt given to the other reviewer.
- **My follow-up prompts:**
  > this is what another agent reviwed and said. What needs to eb fixed and what are fine>

  > Yes apply the fixes.
- **Accepted:**
  - concurrency tests call `Future.get()` on every task, and use latch and test timeouts;
  - the weak drop race is replaced by drops running alongside enrolls;
  - `Learner` uses the same id rule as requests;
  - a directory entry whose key doesn't match its learner's id is treated as missing, checked at lookup;
  - `DropResult.AlreadyDropped`;
  - "enroll only once" worded as an interpretation;
  - the threading contract is stated;
  - five more tests;
  - lock-removal results recorded as they come out.
- **Rejected:**
  - copying the directory behind an update method, because it adds API the brief doesn't ask for;
  - changing `Optional` and `ArrayList`, which are fine at this scale.
- **Wrong:** the reviewer reported the lifecycle diagram as missing. A text copy of the plan had dropped it.
- **Verification:** the tests and break-it-once rows below.

### 9. Implementation

- **Prompt:**
  > Ok now in <repo root> is our repo root. Create a github repo for it and generate a Starter boilerplate code according to the plan. Implement all features mentioned in the plan. Refer to the assesment attached for any doubts. Try to be precise and optimal, don't overdo anything stick to the plan and assesment and maintain code quality and test coverage. Implementation needs to be as simple as possible and effective and optimal.
- **Response:** the AI wrote the code, tests and docs as planned. Coverage showed `promoteNext` re-checking the capacity on a branch that can never run, because a drop always frees exactly one seat. The AI removed that check.
- **Decision:** Accepted after the verification below.
- **My review:** TODO: record your own read-through of `EnrollmentService` and the tests, and anything you changed.
- **Verification:**
  - `mvn verify`: 132 test runs, 0 failures. That is 32 behaviour tests plus 2 concurrency tests × 50 repetitions.
  - JaCoCo: 100% of lines, branches and instructions in `src/main`.

## Break-it-once checks

Method:
- Each check plants one deliberate bug in a copy of the project and runs the suite, without `ConcurrencyTest` except for the last row.
- Every check failed at least one test, which shows the tests can catch the bug.
- The original code passes all tests.

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
