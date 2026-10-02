envelope_version=1
sender_type=plan
sender_id=test-evidence-integrity
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-02T14:01:49Z

# Candidate lesson: an admission counter proves a FILTER MATCHED, not that the input belongs to the named family — and the plan reproduced the defect class it existed to remove

## Signal class

`signal_automated_review_count` — CodeRabbit pr-comment finding `bde6fe`, fixed in-run.

## What happened

The plan's purpose was to remove a defect class: test guards whose predicate is a broad text filter, so a test named for attack family X silently admits inputs that are not X at all, and the assertion it makes is therefore evidence of nothing.

As part of the remediation the plan added six **admission counters** — instrumentation intended to prove the guarded tests were actually seeing input. CodeRabbit pointed out that a counter incremented by a broad text filter proves only that *the broad text filter matched*. It does not prove the admitted input belonged to the named attack family. The counters were themselves an instance of the very defect the plan existed to remove.

The plan's own specification had already written the rule down:

> a matched flag makes the skip VISIBLE, but it does not make a wrong guard right.

The plan then went on to violate its own stated rule in the mechanism it built to enforce it.

## Why it is generalisable

1. **Instrumentation inherits its predicate's semantics.** A counter, flag, or metric derived from a weak predicate is exactly as weak as that predicate; wrapping a broad match in a count converts an unproven claim into a *number*, which reads as evidence and is not. This is the same failure the `plan-marshall` "which zero is this" contracts address, one level up: the count is real, the thing it is taken to count is not what it counts.
2. **Visibility is not validity.** Making a skip observable is a genuine improvement and a genuinely separate axis from making the guard correct. Conflating the two is what let the counters ship as if they closed the finding.
3. **A plan that removes a defect class is the highest-risk place for that class to reappear.** The remediation mechanism is written under time pressure, is not itself the subject of the review lens being applied, and is authored by whoever is most fluent in the weak pattern. Any campaign against a defect class should apply its own acceptance criterion to the code it adds, not only to the code it changes.

## Proposed corrective actions

- When a plan's purpose is to eliminate a predicate shape, add an explicit self-application check to its acceptance criteria: *does any code this plan ADDS use the shape being eliminated?* Run it before submission, in the pre-submission self-review pass, over the plan's own diff.
- Where a guard must decide family membership, require a **family-exact** predicate (an anchored, structure-aware match), and treat any broad text filter as instrumentation-only — never as an admission decision.
- Treat a spec sentence that states a rule as a testable obligation on the plan itself, not only on the target code. This plan's spec contained the exact sentence that would have caught the defect; nothing was checking the plan against it.
