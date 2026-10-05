# History: cui-http Quality Report Remediation

epic: quality-report-remediation
closed: 2026-10-05
created: 2026-09-05

> Frozen record of the closed epic. Written by `close` from the rendered ledger blocks below;
> `epic.md`, `finding-ledger.md`, `landings/`, `plans/` and `workstreams/` stay on disk as the
> full audit record.

## Vision as pursued

An independent quality review of `cui-http` produced 126 findings (0 Critical, 5 High, 47 Medium,
59 Low, 15 Info). The epic set out to drive every finding to a landed deliverable or a recorded
refutation, across seven workstreams that must not be edited concurrently. `finding-ledger.md`
maps each finding id to its owning plan; it was not re-audited row by row at close.

## Final state (rendered at close)

### START HERE (`resume-summary` → `summary`)

**Resume anchor**: ALL PLANS TERMINAL as of 2026-10-05: 15 shipped (last: PLAN-17, #265, d25f55a), 2 superseded; inbox empty. Only open item: the next release notes must name PathTraversalURLGenerator and DoubleEncodingAttackGenerator as removed from the generators test artifact (PLAN-10, #256). NEXT ACTION: close the epic (/plan-orchestrator close), carrying the release-notes item into history.md. Standing: main has a REQUIRED merge queue; PR #224 still OPEN; lessons backlog 40+ (/plan-orchestrator lessons); plan-marshall findings were routed to plan-marshall truthful-signals (020 messages).
**Phase**: orchestrating
**Inbox (derived)**: 0 queued, 116 archived
**Queue** (staged, in order):
- (empty)
- PLAN-01 (WS-01-security-validation-core) — plan=decoding-normalisation-hardening — PR #210 — landing=landings/PLAN-01.md — status: shipped
- PLAN-02 (WS-01-security-validation-core) — plan=character-set-and-control-characters — PR #217 — landing=landings/PLAN-02.md — status: shipped
- PLAN-03 (WS-01-security-validation-core) — plan=exception-sanitisation-and-config-hygiene — PR #222 — landing=landings/PLAN-03.md — status: shipped
- PLAN-15 (WS-01-security-validation-core) — plan=configuration-surface-hygiene — PR #229 — landing=landings/PLAN-15.md — status: shipped
- PLAN-04 (WS-02-contenttype-cookie-collection) — plan=contenttype-pipeline-enforcement — PR #227 — landing=landings/PLAN-04.md — status: shipped
- PLAN-05 (WS-02-contenttype-cookie-collection) — plan=cookie-validation-completeness — PR #231 — landing=landings/PLAN-05.md — status: shipped
- PLAN-06 (WS-03-forwarded-trust-model) — plan=plan-06-forwarded-trust-model — PR #214 — landing=landings/PLAN-06.md — status: shipped
- PLAN-07 (WS-03-forwarded-trust-model) — plan=plan-07-forwarded-parsing-and-validation — PR #237 — landing=landings/PLAN-07.md — status: shipped
- PLAN-08 (WS-04-http-client) — plan=etag-cache-principal-isolation — PR #240 — landing=landings/PLAN-08.md — status: shipped
- PLAN-09 (WS-04-http-client) — status: superseded
- PLAN-10 (WS-05-test-framework-quality) — plan=plan-10-attack-test-mechanism-correctness — PR #256 — landing=landings/PLAN-10.md — status: shipped
- PLAN-11 (WS-05-test-framework-quality) — status: superseded
- PLAN-12 (WS-06-documentation-set) — plan=plan-12-javadoc-samples-and-api-prose — PR #260 — landing=landings/PLAN-12.md — status: shipped
- PLAN-13 (WS-06-documentation-set) — plan=plan-13-asciidoc-specs-requirements-adrs — PR #262 — landing=landings/PLAN-13.md — status: shipped
- PLAN-14 (WS-07-build-ci-benchmarking) — plan=build-artifacts-and-ci-safety — PR #208 — landing=landings/PLAN-14.md — status: shipped
- PLAN-16 (WS-01-security-validation-core) — plan=parameter-value-linebreak-carve-out — PR #239 — landing=landings/PLAN-16.md — status: shipped
- PLAN-17 (WS-01-security-validation-core) — PR #265 — landing=landings/PLAN-17.md — status: shipped

### Ordered Queue (`resume-summary` → `ordered_queue`)

| # | Plan | Workstream | Status | Surface (expected) |
|---|------|------------|--------|--------------------|
| — | (empty) | — | — | — |

## Queue outcome

- **Shipped (15):** PLAN-01 #210, PLAN-02 #217, PLAN-03 #222, PLAN-04 #227, PLAN-05 #231,
  PLAN-06 #214, PLAN-07 #237, PLAN-08 #240, PLAN-10 #256, PLAN-12 #260, PLAN-13 #262,
  PLAN-14 #208, PLAN-15 #229, PLAN-16 #239, PLAN-17 #265.
- **Closed unshipped — superseded (2):** PLAN-09 (merged into PLAN-08), PLAN-11 (merged into
  PLAN-10), both at the 2026-09-15 cleanup.
- **Parked (0).**

All seven workstreams closed. PLAN-16 came from GitHub issue #236 rather than the report;
PLAN-17 swept the residue the last three landings left unowned.

## Decision record (highlights)

The authoritative record is `logs/decision.log`; the curated view is `epic.md` § Decisions.

- 2026-09-15 cleanup: PLAN-09 → PLAN-08 and PLAN-11 → PLAN-10 merged (component-first grouping).
- 2026-10-02/03: ledger relocated into the tracked store (`.plan/orchestrator/`, shared ledger
  worktree, landed via PRs).
- 2026-10-03 cleanup: PLAN-10/12/13 re-grounded at `c10ffa9` (52 verdicts, 5 refutations re-scoped).
- 2026-10-03 onward: on plugin 0.1.1842 the disjointness gate counts terminal rows as candidates,
  so PLAN-10, PLAN-12+13 and PLAN-17 were each emitted under a recorded operator override.
- 2026-10-05: all plan-marshall findings from landings route to plan-marshall epic
  `truthful-signals` (20 messages, sender `cui-http-quality-report-remediation`).
- 2026-10-05: decoded code points >255 in URL pipelines kept by design (ADR-0011); the CRLF
  lookalike lead is refuted, not a pipeline defect.

## Carried forward (leads, not silently dropped)

- **Release notes (owed):** the next release must name `PathTraversalURLGenerator` and
  `DoubleEncodingAttackGenerator` as removed from the published `generators` test artifact
  (PLAN-10, #256).
- **PR #224** (`skill_domains` `file_globs` gap) was still OPEN at close.
- **Lessons backlog** of 40+ pending lessons in this repo awaits `/plan-orchestrator lessons`.
- **Older Open Defects and Watches in `epic.md` were not re-audited at close.** Those not marked
  RESOLVED stay as leads in the frozen `epic.md`, among them: PLAN-15's un-ADR'd removal of eleven
  `public static` constants; two PLAN-14 review findings accepted as out of scope; the PLAN-14
  deploy guard and the benchmark egress allowlist without an executed test; and several
  plan-marshall tooling defects (gate behaviour, `landing-facts` emitter, scope-creep check,
  module-tests arm) now tracked on the plan-marshall side.

## Closing rationale

Every queue row is terminal and the inbox is empty. The only owed item is a release-note entry,
which belongs to the release itself, not to a plan.
