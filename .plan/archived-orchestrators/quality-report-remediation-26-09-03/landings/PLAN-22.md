# Landing Analysis: PLAN-22 — Redirect API Documentation and ADR Value Test

epic: quality-report-remediation
workstream: WS-05
pr: 191 (squash-merged as `384a22c`; fix commit `8d4e3b0`)

> Landing record for one shipped plan. Every claim below was corroborated against ground truth
> before it was recorded — including the claims this landing makes **about the orchestrator's own
> spec**, which were wrong three times.

## Corroboration Summary

`inbox landing-check`: **`complete: false`, 2 missing keys** — `merge_state` and `total_tokens`, both
written as `unknown`. ⚠️ **This is the checker working correctly over an honest report, not a
regression.** The emit-landing agent found that `branch-cleanup` and `record-metrics` recorded no
typed `--fact` this run and **refused to back-fill the values from the narrative it had in front of
it**, documenting the producer gap instead. Per the payload spec, `unknown` reads as missing at every
key — so the *right* behaviour is correctly scored incomplete. **A fact reconstructed from prose is
not a recorded fact**, and the plan was right to say so. Big improvement on the previous landing's
all-8-missing.

| Claim | Verdict | Evidence |
|---|---|---|
| PR #191 merged as `384a22c` | **corroborated** | `git log origin/main` head |
| ⛔ **The CWE-319 fix is real and correctly scoped** | **corroborated at HEAD** | `RedirectPolicy.java`:226–231 — `forwardsCredentials` now returns `false` outright when the target scheme is `http`, **before** the same-origin / allowlist decision. The `FORWARD_TO_ALLOWLISTED` Javadoc (:352–356) states the cleartext strip applies under that strategy too, and that it **changes no refusal verdict** |
| Absolute-URL overclaim gone from **both** sites | **corroborated** | `CLAUDE.md`:41 now reads *"URL **path component** validation … not a full absolute URL; extract the path and pass that"*; `pipeline-architecture-standards.adoc` also corrected |
| Six `{}` → `%s` placeholders | **corroborated** | four `package-info.java` files in the diff |
| 14 ADRs surveyed, **zero removals**, no renumber | **corroborated** | no file under `doc/adr/` appears in the diff at all |
| 7,788 tests green; Sonar 0 new-code, `count_status=confirmed` | accepted as reported | |

## ⛔ The spec was wrong three times, and the spec's own instruction caught all three

The spec told the plan to **re-derive rather than trust** its counts. That instruction is the only
reason these were caught — and every one is the orchestrator's error, not the plan's.

| # | What the spec claimed | Ground truth | Why it was wrong |
|---|---|---|---|
| 1 | **8** inter-ADR `xref:` links | **7** — verified: `grep -oh 'xref:00…' \| wc -l` = 7 | The alleged `0009→0011` is a **plain-prose citation** in ADR-0009's References section, not an `xref:`. ⛔ The orchestrator's own sweep had produced *citations* and *xrefs* as two different greps and then **folded one count into the other** — a third citation category its two-bucket checklist never enumerated |
| 2 | Verify the renumber with `grep -rnoE 'ADR[- ]?[0-9]{4}'` | ⛔ **That sweep is structurally blind to the largest reference site** — verified: `doc/adr/README.md` carries **zero** `ADR-NNNN` tokens; its links are `[Title](NNNN-slug.adoc)`, with the number only inside the filename | A prescribed verification instrument that would have reported README clean whether or not its 14 links were updated. A second, non-token check was mandated instead |
| 3 | "37 sites across 15 files" | The table's own components sum to **50** (14+14+7+14+1) | An arithmetic slip in the orchestrator's summary line, which then propagated into a Q-Gate criterion as "16 matches"; live re-derivation found 23. Reported honestly as a slip rather than by picking a number |

⚠️ **Error 2 is the sharp one, and it is self-inflicted in the worst possible place.** It sits inside
the very constraint block that told the plan *"a renumber verified only by reading the diff is not
verified"* — while prescribing a verification instrument with exactly that class of blind spot. This
is `pm-findings.md` **§10.2** (*a sweep's form vocabulary bounds its coverage claim, invisibly*)
committed by the orchestrator, one turn after recording it.

## Deliverable Fidelity vs Spec

| Deliverable | Verdict | Evidence |
|---|---|---|
| 1. Document the redirect API | shipped-as-specified | `doc/client-handlers-readme.adoc` +88, `[#redirect-policy]`. Every claim traced to `RedirectPolicy.java` / `HttpHandler.java` at outline time (Q-Gate `f72f7a`) rather than to PLAN-17's narrative — the verify-first clause honoured |
| 2. Correct the absolute-URL overclaim | shipped-**widened, correctly** | ⛔ The Q-Gate found a **stronger-form survival of the same claim** at `pipeline-architecture-standards.adoc`:227 — the spec's own **authoritative selection matrix** — which the task scope had not named (finding `48a973`). **Fixing only `CLAUDE.md` would have left the canonical document untrue.** Verification switched from a compile proxy to a literal content sweep with a `count:0` criterion |
| 3. Six `{}` → `%s` | shipped-as-specified | The Q-Gate caught its own criterion saying *five* while its enumeration summed to *six* (`71f298`); corrected. 19 further brace sites across 12 other files are a **documented exclusion** (`cb1816`), narrowed rather than silently skipped |
| 4. ADR value test | shipped as a **verified no-op** | 14 per-record written verdicts (`c97eee`..`be2750`): **14 KEEP, 0 removals**, so no renumber fired |

**Deliverable 4 matched the orchestrator's stated hypothesis exactly** — *"remove nothing, or at most
ADR-0005"* — and the removal-plus-renumber machinery was scoped in up front and **stayed correctly
unused**. ⚠️ That is the outcome the spec explicitly licensed (*"minimize is not a quota"*); a pass
that removed something to justify itself would have been the failure.

## ⛔ The run's real story: an overruled security dismissal

CodeRabbit raised **CWE-319** (`cdb373`): `RedirectPolicy.forwardsCredentials` never consulted the
**target scheme**, so an http-origin handler forwarded `Authorization` / `Cookie` over **cleartext**
on an `http→http` same-origin hop — and cross-origin under `FORWARD_TO_ALLOWLISTED`.

**The first triage pass dismissed it as unreachable, having proved only the `https→http` path**
(`3b03c6`). ⛔ **The operator overruled that**, established that the `http→http` path *is* reachable,
and ruled it in scope (`8c3d32`). Fixed in `8d4e3b0` with a cleartext guard, matched positive/negative
test controls, and five documentation sites moved with the code.

⚠️ **This is `pm-findings.md` §6.3 in reverse, and it is the more valuable direction.** That finding
records *an operator-confirmed decision is not review-proof — a security finding must reopen it*. Here
a **triage dismissal** was not review-proof either: the dismissal proved one path unreachable and
generalised to all paths. **A negative result over one path is not a negative result over the class** —
the same under-derived-completeness shape this epic keeps meeting, this time in a security triage.

**The plan changed production code, and that was correct.** The spec said *"a documentation correction
that requires a code change has found a real defect: report it in the landing and re-scope rather than
absorbing it."* The plan did exactly that, twice — the `RedirectNotAllowedException` stale Javadoc
example (`9f215f`) and the CWE-319 fix (`8c3d32`), each re-scoped by an explicit operator ruling
recorded in `decision.log`, neither absorbed silently.

## Metrics and Anomalies

- 1h46m worked / 7h41m wall / 2.6M tokens; 7,788 tests.
- **Zero pending findings of six total** — every finding this plan opened is resolved.
- **No rate-limit stall.** CodeRabbit reviewed the fix commit incrementally; an
  `escalate_ask{reason: re_review_timeout}` was resolved **on evidence** (`gh api` review objects, the
  bot's own incremental-review reply, a SUCCESS check state) under standing unattended-run
  authorization — **not by picking a prompt option** (`96c64c`).

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` = `191`; `landing` = `landings/PLAN-22.md`
- [x] **New Open Defect** — the owed-ADR debt, now **five** records.
- [x] **New Open Defect** — finding `22aa41`, the flipped E2E control.
- [x] **Recorded against the orchestrator** — three spec errors, in `pm-findings.md` §10.2's terms.
- [x] resume_anchor updated; both generated blocks regenerated

## Follow-Ups

1. ⛔ **Finding `22aa41` — the cleartext guard flipped the only end-to-end proof that
   `FORWARD_TO_ALLOWLISTED` DOES forward cross-origin.** The fixture
   `forwardStrategyShouldKeepCredentialsCrossOrigin` was renamed to
   `…ShouldDropCredentialsToCleartextAllowlistedHost` and now asserts DROP for its cleartext target.
   **Nothing now covers cross-origin + allowlisted + https end-to-end.** The rule holds at the
   `RedirectPolicy` unit seam and same-origin-over-TLS is covered E2E, so the gap is narrow but real.
   ⚠️ Closing it **needs a second-host TLS fixture** — the same class of test-infrastructure work
   PLAN-20 did. Accepted, not fixed. **Worth a follow-up plan.**
2. ⛔ **The owed-ADR debt is now FIVE unrecorded decisions.** PLAN-17 left three (redirect loop
   placement, same-origin-by-default, `CredentialForwarding`); PLAN-22 adds **the cleartext
   credential guard** — a security decision with matched test controls and no ADR. **Cause is
   structural, not per-plan:** `adr-propose` carries `lane: off` under the standard execution posture,
   so no plan on that posture can produce one. Reported in both landings, absorbed by neither.
3. **A producer gap, cleanly reported:** `branch-cleanup` and `record-metrics` recorded no typed
   `--fact` this run, unlike their archived siblings in this epic. Mechanisable once that wiring fires
   reliably; not a corpus defect.
