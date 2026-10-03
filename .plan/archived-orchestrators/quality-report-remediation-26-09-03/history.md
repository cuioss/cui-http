# History: quality-report-remediation

**Closed 2026-09-03** at `main` = `2571f56`. Opened 2026-08-26.
**22 plans shipped, 1 superseded, 0 dropped, 0 parked.** PRs #153–#194.

`epic.md` and the rest of this tree remain on disk untouched — close freezes, it never deletes.

---

## The vision, as pursued

A seven-pass review of `cui-http` at `7ae6499` produced **117 findings** across six reports
(8 HIGH, 29 MEDIUM, 51 LOW, 29 INFO). The report was a *diagnosis, not a remediation*.

The dominant theme was **the gap between advertised and actual behaviour** — components whose
Javadoc, presets or documentation promised more validation than the code performed. Every finding
was to be resolved one of two honest ways: **close the gap in code**, or **narrow the stated
guarantee** so the contract matches the implementation. *Silently leaving a broad claim over a
narrow implementation was not an accepted outcome.*

**That held.** No finding was silently dropped; every deliberate non-action in this file carries a
rationale. And the epic ended up applying its own rule to itself — the last four plans exist because
this ledger's claims turned out to need the same treatment as the library's.

## Queue outcome

| # | Plan | WS | Outcome | PR |
|---|---|---|---|---|
| 01 | header-and-content-type-enforcement | WS-01 | shipped | #154 |
| 02 | post-decode-character-enforcement | WS-01 | shipped | #159 |
| 03 | security-api-contract-hardening | WS-01 | shipped | #169 |
| 04 | security-javadoc-accuracy | WS-01 | shipped | #180 |
| 05 | forwarded-trust-boundary | WS-02 | shipped | #155 |
| 06 | forwarded-parser-strictness | WS-02 | shipped | #162 |
| 07 | forwarded-diagnostics-and-doc-accuracy | WS-02 | shipped | #168 |
| 08 | client-cache-and-result-correctness | WS-03 | shipped | #166 |
| 09 | client-config-and-retry-hardening | WS-03 | shipped | #172 |
| 10 | client-converter-and-javadoc-accuracy | WS-03 | shipped | #182 |
| 11 | generator-contract-test-semantics | WS-04 | shipped | #164 |
| 12 | generator-implementation-correctness | WS-04 | shipped | #171 |
| 13 | attack-database-and-assertion-quality | WS-04 | shipped | #178 |
| 14 | doc-overclaim-correction | WS-05 | shipped | #153 |
| 15 | doc-inventory-and-navigation | WS-05 | shipped | #161 |
| 16 | benchmark-and-ci-hygiene | WS-06 | shipped | #170 |
| 17 | validated-redirect-following | WS-03 | shipped | #186 |
| 18 | semicolon-dot-segment-traversal | WS-01 | shipped | #185 |
| 19 | adr-number-deduplication | WS-05 | shipped | #184 |
| 20 | test-evidence-integrity | WS-04 | shipped | #189 |
| **21** | guarded-assertion-sweep | WS-04 | **superseded** — merged into PLAN-20 pre-launch; spec retained as a pointer stub | — |
| 22 | redirect-api-documentation | WS-05 | shipped | #191 |
| 23 | cross-origin-credential-forwarding-proof | WS-03 | shipped | #194 |

Every shipped row carries a merged PR and a landing record in `landings/`. Workstreams WS-01…WS-06
all closed; WS-05 was reopened after PLAN-15 and closed again after PLAN-22.

## What the epic actually produced

**Beyond the 117 findings**, four defects were discovered *by the work* and would not have been
found by remediating the report alone:

1. ⛔ **CWE-319 cleartext credential forwarding** (PLAN-22). `RedirectPolicy.forwardsCredentials`
   never consulted the target scheme, so an http-origin handler forwarded `Authorization`/`Cookie`
   over cleartext. **A triage pass dismissed it as unreachable, having proved only the
   `https→http` path; the operator overruled that and was right.**
2. ⛔ **The `..;/` path-parameter traversal bypass** (PLAN-18) — `/..;/..;/etc/passwd` was accepted.
   The known Tomcat/Spring class, with no by-design defence.
3. **Validated redirect following** (PLAN-17) — same-origin by default with an opt-in host
   allowlist, after PLAN-10 implemented and then correctly *reverted* unvalidated following.
4. **The HTTPS test path** (PLAN-20) — restored, and its blocker turned out to have been fixed a day
   *before* the note describing it was written.

## Carried forward as leads — not dropped

### Operator rulings (not work items)

- **Is `failOnSuspiciousPatterns=false` intended?** `/file:///etc/passwd` is accepted under
  `defaults()` because that gate is inert. `file:` and `/etc/` *are* in
  `SUSPICIOUS_PATH_PATTERNS`; the field's own Javadoc says *"suspicious (**non-attack**) patterns"*,
  which argues the default is deliberate. Unresolved.
- **No ADR in this repository has ever been accepted.** All fourteen are `Proposed`; at least three
  (0012, 0013, 0014) record security positions.
- ⛔ **Five ADR-worthy decisions have no record at all** — PLAN-17's redirect loop placement,
  same-origin-by-default egress policy and `CredentialForwarding` strategy; PLAN-22's cleartext
  credential guard. **The cause is structural**: `default:adr-propose` carries `lane: off` under the
  standard posture, so no plan on that posture can produce one.

### Routed cross-repo (transported by the operator, 2026-09-03)

The plan-marshall findings corpus — 11 themes over 74 source records — and the cui-http project
findings were consolidated, transported and removed. Two entries are worth naming here because they
bear on this epic's own evidence:

- **The CodeRabbit `participation_evidence` gap.** It declares only `review_body`/`inline` but
  publishes its verdict as an `issue_comment`, which is *also* in its own `ignore_patterns` — so a
  genuinely reviewed PR classifies `absent`. ⚠️ **This was the real cause of the "bot name mismatch"
  this ledger misdiagnosed** (see below).
- ⛔ **The Sourcery refusal recognizer credits a refusal as participation.** Its budget notice says
  *"used"*; the recognizer keys on `exceeded|reached|hit`. Every other completion-evidence defect
  found here fails toward reporting a real review as *absent* — safe. **This one fails the other
  way**, and it retroactively weakens every Sourcery participation claim in this epic.

### cui-http residue, unowned

- `.plan/marshal.json`:103 carries the author login in the `bot_kind` slot (`c7862d0`, #192).
  Review matters are being handled outside this epic.
- **`scope_creep_check` measured nothing on any plan here.** Its baseline field
  `plan_creation_sha` has **one consumer and no producer** anywhere in the marketplace; exactly one
  archived plan of twelve carries it. So the epic's two largest surface over-realizations (PLAN-04:
  28 files against 11 declared; PLAN-13: four undeclared paths) were caught by hand, not by the guard
  built for them.
- **`pre-submission-self-review` is Java-blind.** The sole surfacer implementor classifies `.java`
  as `other`, and the step's zero-generator fallback returns a clean `done` — indistinguishable from
  a real clean pass.
- PLAN-13's over-realized surface (evidence only); D4 reflection-on-private-method coupling; two
  PATCH/OPTIONS rows; coverage owner.

## Closing rationale, and what this epic got wrong

The queue is exhausted, the inbox is drained (144 archived), the lessons store is empty, and
`cleanup` returned **`restart_verdict: ready`**. Nothing remaining is work: it is three rulings, a
cross-repo routing already done, and residue with no owner.

⚠️ **Three orchestrator errors belong in the permanent record**, because each was caught by
something this epic itself put in place:

1. **The `marshal.json` misdiagnosis.** A landing reported *"the producer matched no author with the
   configured name"*; the orchestrator recorded it as fact, promoted it to a finding, and called the
   fix *"one config value"*. `required_bots` takes a **`bot_kind`**, and `pr-agent` was correct all
   along. Acting on the advice **made `main` worse**. ⛔ **The symptom was verified and the diagnosis
   never was.**
2. **A verification instrument with the blind spot it was warning about.** PLAN-22's spec prescribed
   `grep -rnoE 'ADR[- ]?[0-9]{4}'` to prove a renumber — inside the very block stating that a
   diff-only verification is not verification. `README.md` carries **zero** such tokens, so the sweep
   was blind to the largest reference site. Two further count errors in the same spec.
3. **A seed that under-collected by 3×.** PLAN-20's staged sweep named 12 sites across 8 files; the
   re-derived sweep found **41 across 15**, and an entire guard *form* was invisible to the
   heuristic.

**All three were caught because the specs instructed the plans to re-derive rather than trust.**
That instruction, more than any single deliverable, is what this epic should be remembered for: the
ledger was wrong repeatedly, and the machinery around it was built to assume that.

The corresponding successes are the mirror image — PLAN-13's carve-out held, PLAN-20's
falsification ledger was reported with its own shortfall declared rather than rounded away, PLAN-22's
ADR value test removed **nothing** and said so, and PLAN-23 proved a security control by observing
the suite move `7790/0 → 7788/2` under perturbation.
