envelope_version=1
sender_type=plan
sender_id=plan-07-forwarded-parsing-and-validation
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-15T19:15:34Z

# Candidate lesson: a doc-update deliverable enumerated the wrong sites AND gave a rationale that was false at HEAD

**Source**: Q-Gate finding `ae55fd` (3-outline, type `triage`, severity `warning`, resolution `taken_into_account`)
**Plan**: plan-07-forwarded-parsing-and-validation
**Component under edit**: `cui-http-core/src/main/java/de/cuioss/http/forwarded/ForwardedHeaderResolver.java`

## Observation

Deliverable 3 said to update the Javadoc on `parseHostPort`, `statesPort`,
`reconcileStatedByPortToken` and `RfcHost#statesPort`, "each of which currently uses `h:bogus` as the
worked example", because "the host half now resolves empty too and the paragraphs must say so rather
than continuing to imply the host survives".

Re-anchored against HEAD by a literal sweep for `:bogus` (2 files, 303 scanned, complete coverage),
**both halves were wrong**:

- **Enumeration**: `parseHostPort`'s Javadoc carries no such example, so one of the four named sites
  was misattributed; two real sites (class Javadoc 145-146, `resolveHost` 348 / `resolvePort` 636)
  were omitted.
- **Rationale**: every one of those paragraphs is a *port-field* account. None states or implies the
  host survives, and the class Javadoc at 139-140 already says a present-but-invalid host contests.

Following the deliverable as written would have **weakened accurate prose** in six places while
missing the one site (`parseHostPort`'s own literal-only contract at 517-549) that genuinely needed
updating — for a different reason than the one given.

## Why this is lesson-bearing

Two compounding failure classes worth separating:

1. **Unverified site enumeration.** A "these N sites all say X" claim is a closure claim over a set.
   It must be re-derived from a complete-coverage sweep before it is written, not recalled.
2. **Rationale that outlived its premise.** The stated reason ("the host survives and the prose
   implies otherwise") was never true of those paragraphs. A doc deliverable whose rationale is false
   is worse than one with no rationale: it directs edits that make correct text incorrect.

The corrective that closed the finding is a reusable shape: name exactly the site(s) to edit, mark
the surveyed-but-untouched sites **DO-NOT-EDIT with the reason**, and state the actual behaviour
change as the rationale. The DO-NOT-EDIT list is what makes the enumeration falsifiable.

A third-order note: the same stale claim was mirrored in the outline's Risks section and had to be
corrected in the same revision — a single fact restated at two sites in one document drifted at one
of them.

## Suggested disposition (orchestrator judges)

Candidate `plan-marshall:phase-3-outline` rule (doc-update deliverables carry an explicit
edit/DO-NOT-EDIT partition derived from a complete-coverage sweep) and/or a Q-Gate detector for
site-enumeration closure claims.
