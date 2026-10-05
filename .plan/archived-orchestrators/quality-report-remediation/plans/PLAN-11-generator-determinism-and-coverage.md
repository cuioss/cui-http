# PLAN-11 — RETIRED (merged into PLAN-10)

epic: quality-report-remediation
workstream: WS-05

> ↪ **Merged into `plans/PLAN-10-attack-test-mechanism-correctness.md` on 2026-09-15 at `d385aa3`
> (cleanup pass, operator-directed redistribution).** PLAN-11 and PLAN-10 were already declared
> strictly sequential ("Sequenced PLAN-10 first; PLAN-11 re-reads both at its own outline") and
> both overlapped at `cui-http-core/src/test/java/de/cuioss/http/security/database/` plus two
> named generator files. Neither touches production code; both work the same
> `security/tests` + `security/generators` + `security/database` test tree. The component-first,
> task-second grouping rule makes one WS-05 plan the correct unit rather than two dependency-linked
> PRs. All seven of this plan's former deliverables (fixed-list iteration determinism, valid-
> generator widening, overlap/misplaced-consumer fixes, `SupportedValidationTypeGenerator`
> coverage, a real `AllGeneratorsIntegrationTest`, contract tests for the eleven bare generators,
> and generator usage in the suites lacking it) are now deliverables 6-12 of
> `PLAN-10-attack-test-mechanism-correctness.md`. Every claim this plan's Claim Labels section
> carried is preserved verbatim in the merged spec's Claim Labels section under "From the former
> PLAN-11"; none was dropped.

This file is retained as the audit record of the retirement — it is not deleted, per the epic's
duplication/redistribution apply-policy (a retired spec is the record of why it was retired). The
epic `status.json` row for `PLAN-11` is transitioned to `parked` with this pointer as its reason.
Do not launch this spec; launch `PLAN-10-attack-test-mechanism-correctness.md` instead.
