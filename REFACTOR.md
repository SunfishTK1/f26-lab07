# REFACTOR.md

One section per milestone. Fill each one in as you go, in order.

Milestone 1 is written in two sittings, the pin before the refactor and the
rest after. A pin written afterwards is worth nothing, and a TA will ask.

Keep it short and specific. Point at methods, call sites, and test names.

---

## Milestone 1: Direct a refactor, characterization first

### The pin (write this section before you direct the refactor)

**The pin.** File and test name, plus one sentence naming the method and the
observable result it pins. Not "recurring bookings work". Green against the
shipped code, and you did not edit or delete an existing test method to get
there.

> `src/test/java/edu/cmu/cs214/scheduling/workflow/BookingWorkflowCharacterizationTest.java`,
> test `recurringSubmitSkipsAWeekThatOnlyTouchesAnExistingBooking`.
>
> It pins that `BookingWorkflow.submit` on a RECURRING request treats a week
> whose slot only *touches* an existing booking (the existing one ends at 9:00,
> the occurrence starts at 9:00) as taken. That week shows up in
> `getSkipped()`. The other weeks are still booked with their original
> occurrence numbers (1 and 3, not renumbered to 1 and 2). The outcome is
> accepted, its message is `"series S-1: 2 booked, 1 skipped"`, and the outbox
> gets one notification per booked occurrence and none for the skipped week.
>
> It's a new class, so no existing test method was edited or deleted. Against
> the shipped code it is green: `Tests run: 36, Failures: 0, Errors: 0, Skipped: 0`.

**Why that one, and does a shipped test already cover it?** Of everything
`BookingWorkflow` does, why is this the behavior worth a test? If something
shipped comes close, say what your pin adds. If nothing does, say how you
checked.

> `submit` checks overlap in three near-copies, and they disagree. REGULAR
> (`BookingWorkflow.java:68-69`) and BLOCKED (`:152-153`) use strict `<`, so
> back-to-back slots are allowed. RECURRING (`:121-122`) uses `<=`, so
> back-to-back weeks are skipped. That contradicts `TimeSlot`'s javadoc
> ("inclusive start and an exclusive end"), and is probably a latent bug. A
> characterization test still pins what the code *does*. Fixing it would be a
> separate, deliberate behavior change, not part of a refactor.
>
> This is the behavior worth pinning because it's the one this refactor is
> most likely to break. Anyone (or any agent) that removes the type switch sees
> three copies of the same overlap loop and folds them into one `overlaps()`
> helper, and the natural helper uses `<`.
>
> **Coverage check.** The closest shipped test is
> `BookingWorkflowTest.regularSubmitAcceptsASlotThatStartsWhenAnotherEnds`,
> which pins the boundary for REGULAR only. My pin adds the RECURRING side, so
> together they pin the asymmetry. I confirmed nothing shipped covers it in two
> ways:
> 1. By grep. All four shipped `BookingRequest.recurring(...)` calls
>    (`BookingWorkflowTest.java:106, 149, 181, 209`) submit into an empty
>    C-200, so no series ever conflicts. No shipped test reads `getSkipped()`,
>    `getOccurrenceIndex()`, or `getMessage()`.
> 2. By mutation. In a scratch copy I changed `<=` to `<` at lines 121-122. All
>    35 shipped tests stayed green, and only the pin failed
>    (`expected: <[2026-10-12T09:00 to 2026-10-12T10:00]> but was: <[]>`).

**What a regeneration would do differently here.** Suppose someone
threw this class away and regenerated it from a one-line description of what a
booking workflow does. Name the decision that would be made a second time, and
say which way it would probably go.

> The decision made a second time is **"does a slot that starts exactly when
> another ends conflict with it?"**, and whether that answer is the same for
> every booking type. A regeneration from "submit, cancel, price and describe
> room bookings" would make that decision once. It would almost certainly go
> half-open everywhere (`a.start < b.end && b.start < a.end`). That matches
> `TimeSlot`'s javadoc and the REGULAR test it would have to keep passing. So a
> regenerated workflow would start *booking* back-to-back weekly occurrences
> that the shipped code skips. Every caller that reads `getSkipped()` or the
> "N booked, M skipped" message would see different results, and only this
> pin would notice.

### The directive

**The refactor and the exact directive.** Name the refactor (one from the menu
in the handout) and paste the directive you gave the agent, including the scope
you set, meaning which files and packages were in bounds, which were not, and
one line on why the boundary sits where it does.

> **Refactor:** Replace Conditional with Polymorphism. The directive below went
> to a separate Claude Code sub-agent, word for word, after the pin commit
> (`6373954`). The only addition was a first line giving the repo path.
>
> ```text
> **Refactor: Replace Conditional with Polymorphism in `BookingWorkflow`.**
>
> `BookingWorkflow.submit`, `cancel`, `priceOf` and `describe` each switch on
> `BookingType`. Remove all four switches.
> - Introduce a package-private interface `BookingTypeHandler` in
>   `edu.cmu.cs214.scheduling.workflow` with one method per switch:
>   `submit(BookingRequest, Room)`,
>   `cancel(Booking, String roomName, boolean adminOverride)`,
>   `price(Booking)` and `describe(Booking, String roomName)`.
> - Add three package-private implementations, `RegularBookingHandler`,
>   `RecurringBookingHandler` and `BlockedBookingHandler`. Each takes the
>   collaborators it needs (store, calculator, hub) in its constructor.
> - `BookingWorkflow` keeps the code that runs before each switch (null
>   checks, room and booking lookups, the early returns and the `roomName`
>   fallback). It builds an `EnumMap<BookingType, BookingTypeHandler>` once in
>   its constructor and delegates to it.
> - The `default:` branches are unreachable, because the enum has exactly
>   three constants and both `BookingRequest` and `Booking` reject a null type.
>   Drop them, and say so in your summary.
>
> **Scope.**
> - In bounds: only `src/main/java/edu/cmu/cs214/scheduling/workflow/`. Edit
>   `BookingWorkflow.java` and add new files in that package.
> - Out of bounds: `domain/`, `notify/`, `pricing/`, `reporting/`, everything
>   under `src/test/`, `pom.xml`, `.github/`, and all `.md` files. Do not edit
>   them.
>
> **This is a behavior-preserving refactor. Hard constraints:**
> 1. `BookingWorkflow`'s public constructor and four public method signatures
>    stay exactly as they are.
> 2. Move each `case` body as is. Do not deduplicate across types. In
>    particular, keep all three overlap checks separate and exactly as
>    written, including RECURRING's `<=` comparisons. That inconsistency is
>    deliberate here and is pinned by a test.
> 3. Every observable output stays byte-for-byte identical: rejection
>    messages, outcome messages, notification recipients, subjects and bodies,
>    notification order, and the order of `store.nextBookingId()` and
>    `store.nextSeriesId()` calls.
> 4. No `switch`, `if` or ternary on `BookingType` may remain anywhere in the
>    `workflow` package.
> 5. Do not edit or add tests. Do not commit.
>
> **When done:** run `mvn -B test` from the repo root (it must report
> `Tests run: 36, Failures: 0`). Report the totals line, every file you
> changed or added, and anything you were tempted to change but didn't.
> ```
>
> **Why the boundary sits there.** Every test (including `ReportServiceTest`
> and `NotificationHubTest`) uses `BookingWorkflow` only through its public
> constructor and four methods. Freezing that API and the tests lets the
> unchanged suite, plus my pin, judge the refactor. `domain/` is shared with
> `reporting/`, and `notify/` and `pricing/` are the subjects of Milestones 2
> and 3, so they stay as shipped.

### The result

**The diff and the suite.** How you are showing the diff to the TA (a commit,
`git diff`, a branch), and the totals line (the shipped count plus your pin,
all green).

> **The diff is commit `6b8f9d2`** ("Replace conditional with polymorphism in
> BookingWorkflow"). It sits directly on top of the pin commit `6373954`.
> To see it, run `git show 6b8f9d2 -- src/` or
> `git diff 6373954 6b8f9d2 -- src/`:
>
> ```text
>  .../scheduling/workflow/BlockedBookingHandler.java |  73 +++++++
>  .../scheduling/workflow/BookingTypeHandler.java    |  26 +++
>  .../cs214/scheduling/workflow/BookingWorkflow.java | 224 ++-------------------
>  .../workflow/RecurringBookingHandler.java          | 123 +++++++++++
>  .../scheduling/workflow/RegularBookingHandler.java |  92 +++++++++
>  5 files changed, 327 insertions(+), 211 deletions(-)
> ```
>
> - `BookingWorkflow` keeps its constructor, the four public methods, and the
>   shared code that ran before each switch. Each method now ends in one line,
>   `handlers.get(type).<op>(...)`, over an `EnumMap` built in the constructor.
> - Each of the 12 former `case` bodies now lives in a package-private
>   handler class, one per `BookingType`.
>
> **Totals** (from `mvn -B test` after the refactor; per class 18 + 1 + 7 + 6 + 4):
>
> ```text
> [INFO] Tests run: 36, Failures: 0, Errors: 0, Skipped: 0
> [INFO] BUILD SUCCESS
> ```
>
> That is the 35 shipped tests plus the pin, all green, and no test file
> changed.

**What did NOT change: behavior and files.** The observable behavior you
checked is still the same, including anything that surprised you while reading.
Which files outside the scope are untouched, and how you verified that rather
than assumed it. If the agent reached outside the directive, say where and what
you did about it.

> **Behavior.**
> - **Every case body moved unchanged.** The agent claimed this, and I checked
>   it separately. I wrote my own script that pulls each of the 12 `case`
>   bodies out of `git show 6373954:.../BookingWorkflow.java` and checks it
>   appears token for token (ignoring whitespace) in the new handler files.
>   All 12 match: `submit`, `cancel`, `priceOf` and `describe` for REGULAR,
>   RECURRING and BLOCKED.
>   - So every rejection and outcome message is the same, along with every
>     notification recipient, subject and body.
>   - The publish order is the same, and so is the `nextSeriesId()` /
>     `nextBookingId()` call order.
> - **The surprise from reading is still there**, and the pin still guards it.
>   RECURRING's `<=` overlap check (now `RecurringBookingHandler.java:61-62`)
>   is unchanged. The REGULAR (`RegularBookingHandler.java:43-44`) and BLOCKED
>   (`BlockedBookingHandler.java:34-35`) checks stay strict `<`. When I
>   repeated the mutation (`<=` to `<`) on the *refactored* code in a scratch
>   copy, the pin went red and nothing else did, so it still guards the moved
>   code.
> - **The shared code before each switch is identical**, as I confirmed by
>   reading the diff:
>   - the null-request check;
>   - the unknown-room rejection;
>   - "missing or already cancelled → `false`" in `cancel`;
>   - the unknown-booking `IllegalArgumentException` in `priceOf`;
>   - the "Unknown booking #" text in `describe`;
>   - the `roomName` fallback.
> - **Other surprises carried over unchanged.** These aren't pinned, but they
>   come through as part of the token-identical move:
>   - RECURRING `cancel` releases the chosen occurrence *and every later one*.
>   - RECURRING `priceOf` prices the whole live series, earlier weeks
>     included.
>   - RECURRING `submit` never checks whether the member is already booked
>     elsewhere (REGULAR does).
>
> **Files.**
> - `git diff --stat 6373954 6b8f9d2 -- src/test pom.xml .github` is empty.
> - `git diff --name-only 6373954 6b8f9d2` lists only the five `workflow/`
>   files above plus `REFACTOR.md`, which I edited (the directive section),
>   not the agent.
> - So `domain/`, `notify/`, `pricing/`, `reporting/` and every test are
>   byte-identical. A grep of `workflow/` finds no `switch` or `case`, and no
>   conditional on `BookingType`.
> - The agent did not reach outside the directive.

**One thing the agent changed that you had to look at twice.** Something you
checked line by line before accepting. If there was nothing, say how carefully
you read the diff.

> **The dropped `default:` branches.** The original returned a fallback for an
> unknown type in each method:
>
> | Method | Original fallback |
> |---|---|
> | `submit` | `"unsupported booking type X"` |
> | `cancel` | `false` |
> | `priceOf` | `0.0` |
> | `describe` | `"Booking #id in room"` |
>
> Now `handlers.get(type)` would return `null` for an unknown type and throw a
> `NullPointerException`. I accepted it after checking it is unreachable
> today:
> - `BookingType` has exactly three constants, all registered in the
>   constructor.
> - `BookingRequest` and `Booking` both reject a null type in their
>   constructors.
>
> So no input can observe the difference. It is still a changed *failure
> mode*: whoever adds a fourth type must register a handler or get an NPE
> instead of a polite rejection.
>
> The other line I read twice is a smaller, structural one.
> `FACILITIES_CONTACT` and `recipientFor` went from `private` to
> package-private so the handlers can static-import them. The workflow and
> its handlers now depend on each other. That is acceptable inside one
> package, and nothing outside `workflow/` can see either.

### The closing explanation

**Refactor or regenerate?** Argue whether regenerating `BookingWorkflow` from scratch
would have been the better call, using the lecture's four questions (test
coverage, code age, spec quality, and reach). Be concrete about this codebase.

> **Refactoring was the better call.** Three of the four questions point that
> way, and the one that doesn't points only weakly.
>
> - **Test coverage: refactor.** The 35 shipped tests are green, but they pin
>   the happy path of each `case` and almost none of its edge decisions:
>   - which weeks a series skips;
>   - occurrence numbering after a skip;
>   - the forward-cascading recurring cancel;
>   - recurring not checking member double-booking;
>   - the touching-boundary rule.
>
>   The mutation experiment proves it: a real behavior change left all 35
>   green. A regeneration remakes every one of those decisions, and the suite
>   would wave most of the differences through. The refactor, by contrast,
>   could be checked completely: 12 bodies, token-identical, plus a green
>   suite.
> - **Code age: weakly regenerate.** The code is young and agent-generated,
>   one commit with no bug-fix history, so a regeneration loses no hard-won
>   fixes. The catch is that it would also lose the quirks above. With no
>   history, nobody can say which of them are intended. Age makes
>   regeneration *cheap*, not *safe*.
> - **Spec quality: refactor.** There is no spec beyond the README's one
>   paragraph and some javadoc, and the javadoc contradicts the code: `TimeSlot`
>   says the end is exclusive, while RECURRING treats it as inclusive. The
>   code is the only complete spec. Regenerating from a description would
>   swap the code's answers for the generator's guesses.
> - **Reach: refactor.** `BookingWorkflow` is the front door. Every store
>   write and every notification goes through it (its own javadoc says so).
>   `ReportService` reports on the bookings it writes, and callers consume
>   its outcome messages, `getSkipped()`, and the exact outbox text
>   (`NotificationHubTest.aConfirmationFromTheWorkflowReachesTheOutbox`
>   asserts a full rendered string). High reach means more callers can see
>   anything a regeneration changes.

**What would flip your answer.** A condition about the artifact, not a feeling.

> I would regenerate if `BookingWorkflow` had a test suite that pins each of
> the 12 type × method behaviors, including the edge decisions listed above,
> such that mutating any one of them turns a test red. Or, as an
> alternative, a written spec that rules on the open questions (touching
> boundary, cascade on cancel, recurring member double-booking). With that,
> a regenerated class could be judged as rigorously as this refactor was.
> Without it, regenerating means accepting changes nobody can see.

---

## Milestone 2: The pattern critique

Read `notify/`. It works and the outbox tests pass.

### The patterns present

List every design pattern you can name in that package. For each one, the class
or classes that carry it.

### The problem each one solves

For each pattern you listed, what would have to be true about the requirements
for that pattern to be the right call? One sentence each, not in terms of
"flexibility".

### Which of those problems exist here

For each pattern, does the problem it solves exist in this codebase? Point at
the code that settles it.

### The simpler structure

**Your proposal.** What replaces `notify/`. Sketch the classes and the one
method that matters.

**What stays the same.** The tested behavior it must still produce, named
precisely enough that a reader can check it against the shipped tests.

**What you would keep, if anything.** If you would keep one interface, say
which and why. "None of it" is a fine answer if you can defend it.

### What would bring each layer back

For at least two of the layers you would remove, what requirement, if it
arrived next sprint, would make that layer the right structure? Be specific
about the requirement, not about the pattern.

**Misuse or anti-pattern?** Say which this is and why the distinction matters.

---

## Milestone 3: The missing pattern

Read `pricing/`. Not coded, one sentence.

**The pattern.** Which one fits `PriceCalculator`, and the problem that makes
it fit. Name the problem.

**Would you apply it today?** Yes or no, one line, with the reason.
