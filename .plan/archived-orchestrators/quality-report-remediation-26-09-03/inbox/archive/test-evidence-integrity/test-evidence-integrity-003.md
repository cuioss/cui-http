envelope_version=1
sender_type=plan
sender_id=test-evidence-integrity
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-02T14:01:55Z

# Candidate lesson: a sweep's heuristic could not see one guard FORM, so it passed green over six genuine sites — coverage must be graded against the form population, not the hit count

## Signal class

Q-Gate finding `adea8c` (phase `5-execute`, resolution `fixed`) — recorded as a SCOPE EXPANSION.

## What happened

The plan ran a sweep for guarded assertions. Its heuristic recognised the guard forms it was written for, but **structurally could not see early-return filter guards** — the `if (...) { return; }` / `return; // Skip` shape. Six genuine sites in `HttpRequestSmugglingAttackTest` were therefore invisible to it.

The sweep reported clean over those six sites. They were found only because a *separate*, differently-shaped search (`return; // Skip`) was run afterwards. Had that second search not been run, the plan would have shipped with a green sweep and six unremediated instances of exactly the defect it was chartered to remove.

## Why it is generalisable

1. **A sweep's coverage claim is bounded by its heuristic's form vocabulary, and that bound is invisible in its output.** The sweep reported hits; it did not report *which syntactic forms it was capable of matching*. A consumer reading "N sites found, all fixed" cannot tell that from "N sites found out of an unknown population".
2. **This is the under-derived-completeness failure.** The honest artifact of a form-based sweep is a two-part report: the enumerated form population it searched for, and the hits per form. A form with zero hits is then a stated zero ("searched for early-return guards, found none"), which is falsifiable — as opposed to a form that was never searched for, which is silent and indistinguishable from a clean result.
3. **The remedy that actually worked was a second, differently-shaped search.** That is evidence the fix is not "improve the regex" but "enumerate the forms first, then search each one, then report per-form".

## Proposed corrective actions

- Any coverage-class sweep must **declare its form population up front** and report per-form counts, including explicit zeros. A form absent from the declaration is a declared blind spot, not a clean result.
- For guard/skip detection specifically, the form population must at minimum include: conditional-assertion guards, `assumeTrue`-style guards, early-return filter guards (`if (...) return;`), `continue`-based loop filters, and comment-marked skips (`// Skip`, `// ignore`).
- Grade a sweep to the floor: if any declared form could not be searched mechanically, the sweep's thoroughness verdict is capped, and that cap must ride the report rather than being absorbed into a green result.
