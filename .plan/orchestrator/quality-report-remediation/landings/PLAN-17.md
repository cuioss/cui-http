# Landing Analysis: PLAN-17 — Post-landing residue

epic: quality-report-remediation
workstream: WS-01
pr: #265 (`d25f55a`, merged via merge queue on operator instruction, 2026-10-05)

> Executed by a plain Claude Code session (no inbox message, no landing-facts block). Analyzed in
> paste mode from the session's report, corroborated against GitHub and the diff: PR open → merged
> as `d25f55a`, `mergeable/clean`, no failing check, 33 files (+167 / −128), review comments only
> from CodeRabbit (answered and resolved).

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|-------------|---------|----------|
| 1. Decoded >255 in URL pipelines | **escalated → kept by design** | Lead reproduced: `%e5%98%8a%e5%98%8d` and `%e4%b8%ad` accepted at default by the parameter-value, parameter-name and path pipelines. Intended per ADR-0011 (Accepted): the >255 rule is a wire-form rule; `DecodingStage` owns decoded safety (NUL, combining marks, C0/C1). Operator chose option 1 at the stop gate. Tests now pin acceptance and that the decoded value stays U+560A U+560D (never LF/CR). No ADR, no release note. |
| 2. Scheme rejection documented | shipped | `URLPathValidationPipeline` Javadoc: scheme handling is the caller's job |
| 3. Javadoc / comment drift | shipped | `CharacterValidationStage` (headers ASCII-only, bodies 160–255 at default; >255 rule = wire form), adapter package-info (no SQLi/XSS claim), forwarded comments now say "contests" |
| 4. Test residue | shipped, one spec error | 23 stale lines removed; `IDNAttackDatabase` literals escaped; `URLPathValidationPipelineTest` set-membership asserts → exact failure type. The raw NUL was in `HTTPHeaderInjectionGenerator.java`, not `HttpHeaderInjectionAttackGenerator.java` as the spec said — fixed in the right file |
| `src/main` executable change | none | every changed `src/main` `.java` line in `git diff -U0` is a comment line |

## Metrics and Anomalies

- No plan-marshall metrics (plain session).
- First `verify -Ppre-commit` failed one untouched `DecodingStageTest` case on stale Eclipse-compiled
  classes in `target/`; `clean verify -Ppre-commit` passed (12,562 tests). Environment, not code.

## Routing and Merge Behavior

- Review: one CodeRabbit comment (forwarded-header wording: an invalid value competing with the
  other de-facto family is settled by `deFactoPrecedence`, not dropped) — fixed and resolved.
- CI/merge: merge queue, operator-instructed merge.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr`, `landing` stamped (`plan_marshall_plan_id` n/a — plain session)
- [x] Open Defects resolved: CRLF homograph (refuted by design, ADR-0011), scheme rejection
  (documented as caller's job), Javadoc drift (adapter prose, `CharacterValidationStage`,
  forwarded comments), test-side residue, 23 phrase files
- [x] Remaining open: release notes must name the removed generators
