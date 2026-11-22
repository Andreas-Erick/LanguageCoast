# ADR-0003: Schedule with FSRS-5 using its published default parameters

- **Status:** Accepted
- **Date:** 2026-10-09
- **Commit:** `f8d6578`

## Context

The app decides when each card comes back. The scheduler
has to run offline on both platforms, give the same answer
for the same history, and survive bad data from imports
and old backups.

## Options considered

1. **SM-2**, the classic Anki algorithm. Small and well
   known, but its ease factor drifts down ("ease hell"),
   and it ignores how long ago a card was really seen.
2. **FSRS-5 with the default weights.** A memory model
   (stability and difficulty) that fits real review data
   better. Its defaults come from a large public dataset.
3. **FSRS with weights fitted to each user.** Best in
   theory, but it needs hundreds of reviews per user and an
   optimizer on the phone.

## Decision

Option 2, in pure Kotlin in
[`data/SpacedRepetition.kt`](../../shared/src/commonMain/kotlin/com/andreaserick/languagecoast/data/SpacedRepetition.kt).
The time and time zone are passed in, never read inside.

- Target retention is 90%, so the interval equals the
  stability.
- Intervals are capped at 36,500 days, and kept in the
  order Hard ≤ Good ≤ Easy, as Anki does.
- The scheduling fields live on the flashcard row, so
  undoing a delete keeps them.

## Consequences

- Deterministic and testable. `SpacedRepetitionTest` pins
  the published formulas. `PropertyFuzzAndBoundaryTest`
  feeds NaN, infinity, negative values and 250 seeded
  random histories, and checks the results stay in range.
- Imported Anki cards carry no FSRS state, so they start
  as new cards.
- No per-user fitting. Fitting needs a log of every review,
  and the app keeps only each card's current state. Adding
  it later means a new table and a Room migration, and only
  reviews from then on would count.
