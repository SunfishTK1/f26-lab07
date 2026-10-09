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

**What did NOT change: behavior and files.** The observable behavior you
checked is still the same, including anything that surprised you while reading.
Which files outside the scope are untouched, and how you verified that rather
than assumed it. If the agent reached outside the directive, say where and what
you did about it.

**One thing the agent changed that you had to look at twice.** Something you
checked line by line before accepting. If there was nothing, say how carefully
you read the diff.

### The closing explanation

**Refactor or regenerate?** Argue whether regenerating `BookingWorkflow` from scratch
would have been the better call, using the lecture's four questions (test
coverage, code age, spec quality, and reach). Be concrete about this codebase.

**What would flip your answer.** A condition about the artifact, not a feeling.

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
