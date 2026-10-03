# Landing Analysis: PLAN-06 — Forwarded Parser Strictness

epic: quality-report-remediation
workstream: WS-02
pr: #162 — https://github.com/cuioss/cui-http/pull/162 (MERGED → `7d43646`)

## Ground-Truth Corroboration

| Claim | Verdict | Evidence |
|---|---|---|
| PR #162 merged → `7d43646` | **corroborated** | MERGED; on `origin/main`. 13/13 CI checks. |
| 6/6 deliverables covering all 7 spec deliverables | **corroborated** | The spec's 7 map onto 6 outline deliverables; FW-9's remainder rides deliverable 4. |
| FW-9's remainder handled, not re-done | **corroborated — the amended spec worked** | The spec was rewritten after PLAN-05 closed FW-9's delimiter half early, directing *verify the remainder*. Deliverable 4 covers exactly the multi-colon host half. No duplicate work. |
| `HTTP-125` added AND `doc/LogMessages.adoc` updated in the same change | **corroborated** | Deliverable 6 updated the guide and `LogMessages.adoc` to HTTP-120..125. ⛔ **This is the second time the ledger's adjacency note held**: the report verified `LogMessages.adoc` matches the code exactly, and both plans adding a log record have kept it so. |
| No new ADR | **corroborated, and correct** | `adr-propose` returned none — ADR-0002 already covers the fail-closed principle. **This plan did not add to the duplicate-ADR problem.** |
| 5583 tests green, 0 Sonar new-code | **corroborated as reported** | Quality gate ran fully. |
| Landing completeness | **contradicted — `complete: false`** | All 8 required keys missing. **Fifth incomplete landing of six drained.** |

## Deliverable Fidelity vs Spec

| Spec deliverable | Verdict |
|---|---|
| 1. RFC 7239 aborts on malformed elements (FW-4) | **shipped-as-specified** — a malformed directive now rejects the whole `Forwarded` header, logging `HTTP-125`. |
| 2. Validating compact constructor on `ResolvedForwarding` (FW-5) | **shipped-as-specified** |
| 3. Reject trailing garbage after a bracketed IPv6 literal (FW-7) | **shipped-as-specified** — `BRACKET_PORT_SUFFIX` permits only `:digits` after `]`. |
| 4. Reject IPv4 octets with leading zeros (FW-8) | **shipped-as-specified, with far larger effect than the finding claimed** — see below. |
| 5. Tighten the host guard (FW-9) | **remainder shipped** — multi-colon host closed here; delimiter half was PLAN-05's. |
| 6. Digit-only `parsePort` (FW-10) | **shipped-as-specified** |
| 7. Regression tests | **shipped-as-specified** — cross-cutting suite through the public resolver. |

## Metrics and Anomalies

- ⛔ **A claim in the source report's "Verified correct / done well" section was FALSE, and fixing a
  LOW finding closed it.** The report states (`source/forwarded-review.adoc:148`): *"No DNS
  resolution: `IpAddresses.parse` regex-guards to numeric literals before `InetAddress.getByName`,
  so hostnames can never be resolved into trusted addresses."* The old pattern was
  `\d{1,3}(\.\d{1,3}){3}` — it guarded the **shape** but not the **range**. `999.999.999.999`
  matches that shape, is not a valid IPv4 literal, and `InetAddress.getByName` therefore treats it as
  a **hostname and performs a real blocking DNS lookup** — attacker-triggerable through
  `X-Forwarded-For`, measured by the executor at 13–54 ms round trips. The FW-8 fix
  (`IPV4_OCTET = (0|25[0-5]|2[0-4]\d|1\d\d|[1-9]\d?)`) range-bounds every octet and closes it.
  **This is the epic's first demonstrated case of a report POSITIVE verification being wrong.**
- **Three of the four unspecified findings share one shape the spec never named:** the host-header
  path was laxer than the for-chain path. Also fixed: `resolvePort` ignoring a malformed header (a
  gap inside deliverable 1's own guarantee), `[]`/`[not-an-ip]` accepted as hosts, and a Sonar BLOCKER.
- ⛔ **Coverage was never measured.** `verify:coverage` does not resolve in this project's
  architecture and recorded `skipped` every phase. Tests ran (5583), but **the plan's 80% coverage
  bar is unverified** — and CLAUDE.md mandates a minimum 80% with 100% on critical paths. This is a
  process gap affecting every plan in the epic, not just this one.
- **Self-corrected mid-run error, disclosed:** a "fix" for wildcard imports that openrewrite owns was
  committed on an unchecked premise, reverted by the next build, then properly reverted
  (`9c87f17` → `928d696`). Squash-merge means `main` never carried it.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` 162; `landing`; `plan_marshall_plan_id`
- [x] 4 candidate-lessons promoted (`2026-08-27-12-008..011`)
- [x] Watch opened — the report's positive verifications are not more reliable than its findings
- [x] Open Defect opened — coverage is unmeasurable in this project

## Follow-Ups

- ⛔ **Re-read the report's "Verified correct / done well" sections with suspicion.** This landing
  proves at least one is wrong. Those sections are load-bearing for plans that scoped AWAY from an
  area because the report said it was sound — in particular PLAN-07's FW-14 zone-ID work and
  PLAN-16's benchmark-methodology claims both rest on such statements.
- **Coverage cannot currently be verified for any plan.** `verify:coverage` does not resolve. Every
  landing in this epic that claimed to meet the 80% bar did so unverified. No staged spec owns the
  build's coverage configuration — PLAN-16 owns the poms and is the natural home if it is fixed.
