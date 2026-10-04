# Landing Analysis: PLAN-10 — Attack-test mechanism correctness

epic: quality-report-remediation
workstream: WS-05
pr: #256 (`f81a554`, merged via merge queue)

> Drained from inbox message `plan-10-attack-test-mechanism-correctness-010.md` on 2026-10-04.
> `inbox landing-check`: `complete: true`, no missing keys. Corroborated: `ci pr view 256` reports
> `state: merged`, merge commit `f81a554`; `git diff f81a554^ f81a554` lists 98 files.

## Deliverable Fidelity vs Spec

The landing reports 12/12 deliverables done (`deliverables_total=12`, `deliverables_done=12`).
Per-deliverable fidelity was not re-derived file by file; the diff is consistent with the spec's
scope (97 of 98 changed files under `cui-http-core/src/test/`, **no production file changed**, as
the spec required).

| Check | Verdict | Evidence |
|-------|---------|----------|
| No production code changed | held | `git diff --name-only f81a554^ f81a554` — nothing under `src/main` |
| No `doc/` edit (spec Expected Surface: "does not edit any file under `doc/`", owned by PLAN-13) | **violated** | `doc/http-security/specification/testing.adoc` changed |
| Generator removal | added-unplanned (breaking) | `PathTraversalURLGenerator` and `DoubleEncodingAttackGenerator` removed from the published `generators` test artifact |

## Surface delta

`inbox landing-check` with declared (9 entries) vs realized (98 files): `expansion_detected`,
`added_count: 4`, `missing_count: 0`, base `origin/main` = `f81a554` (not stale).

- `cui-http-core/src/test/java/de/cuioss/http/security/pipeline/PipelineFactoryTest.java`
- `cui-http-core/src/test/java/de/cuioss/http/security/pipeline/URLParameterValidationPipelineTest.java`
- `cui-http-core/src/test/java/de/cuioss/http/security/pipeline/URLPathValidationPipelineTest.java`
- `doc/http-security/specification/testing.adoc`

Twelfth under-declaration occurrence. No collision: nothing else was running.

## Metrics and Anomalies

- Tokens: 8,769,480 total
- Duration: 67,986 s wall (~18.9 h)
- Anomalies: finalize loop ran 5 rounds against a ceiling of 3 (iterations 4 and 5
  operator-authorised); `ci-verify` record left at `loop_back` although later CI rounds and
  post-merge CI were green; pre-push module-tests arm DEGRADED (no default-scope canonical);
  `archive-plan: pending`.

## Routing and Merge Behavior

- Review: CodeRabbit quota refusal stalled `automatic-review` ~1.5 h before participation
  completed; Sonar first-round failure `text:S6389` (raw bidi chars in
  `CookieChaosAttackTest.java:100`) fixed in `484282b`; Sonar new-code issues at merge: 0.
- CI/merge: merge queue; no rebase conflict.

## Follow-ups reported by the plan (leads, not verified here)

- Production: `URLParameterValidationPipeline` reportedly accepts the low-byte CRLF homograph
  `%e5%98%8a%e5%98%8d` with line breaks disallowed; no preset rejects
  http/https/ftp/gopher/ldap/custom schemes on the URL path pipeline.
- Test-side: set-membership assertion in `URLPathValidationPipelineTest`; raw NUL in the
  `HttpHeaderInjectionAttackGenerator` Javadoc; raw characters in `IDNAttackDatabase`; truncated
  PR Intent section.

## Reconciliation Actions

- [x] row `status` → `shipped`
- [x] row `pr` stamped `#256`
- [x] row `landing` stamped `landings/PLAN-10.md`
- [x] row `plan_marshall_plan_id` stamped `plan-10-attack-test-mechanism-correctness`
- [x] epic.md: Open Defects for the two production leads, the `testing.adoc` boundary
  violation (PLAN-13 must re-read it), and the generator-artifact breaking change
- [x] 9 candidate-lesson messages promoted to the lessons corpus
- [x] resume anchor updated
