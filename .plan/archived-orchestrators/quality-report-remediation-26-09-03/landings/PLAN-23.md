# Landing Analysis: PLAN-23 — Cross-Origin Credential-Forwarding Proof

epic: quality-report-remediation
workstream: WS-03
pr: 194 (merged via queue as `ff12f34`; #193 replaced)

> Landing record for one shipped plan. Every claim below was corroborated against ground truth —
> including a claim **this orchestrator propagated and which turns out to be false**, and which
> caused a defect now live on `main`.

## ⛔ Correction first: I was wrong about `marshal.json`, and my advice made `main` worse

**`required_bots` takes a `bot_kind`, not an `author_login`. `pr-agent` was the correct value all
along.** Verified in the registry: `automatic-review/SKILL.md` repeatedly gates on
*"`{bot_kind}` is present in `required_bots ∪ optional_bots`"* and names `pr-agent` as that kind
throughout (*"only `pr-agent` can reach this set"*, *"`pr-agent` has no push trigger at all"*).
`cuioss-review-bot` is the **author login** the registry resolves *from* the kind.

`.plan/marshal.json`:103 at HEAD now reads `"required_bots": "coderabbit,cuioss-review-bot"` — the
login in the kind slot — landed by `c7862d0` (#192). **This is a live defect on `main`, and it is
worse than what it replaced**: the previous value was correct.

**The chain of error is mine, and it is worth stating in full:**

1. PLAN-20's landing reported *"the producer matched no author with the configured name"*.
2. I recorded that as fact and characterised it as a **configuration** defect.
3. I promoted it into `pm-findings.md` **§2.7** as a recurrence — *"a WORSE variant: the bot IS
   installed and DID review"*.
4. I put it in the resume anchor as **"cheaply actionable — ONE config value"**.
5. It was actioned in another context, and broke a field that had been right.

⛔ **I verified the symptom and never verified the diagnosis.** I had `marshal.json`:103 open and
confirmed its *value*; I never asked what the field *means*. The verify-first rule says a pasted
claim is a lead, not a fact — I applied it to the value and not to the interpretation, which is
exactly the gap `pm-findings.md` §10.8 names: *a proof over one thing is not a proof over the claim*.

**The real defect was finding 2 all along** (below): a plan-marshall registry gap, not a cui-http
config value. `pm-findings.md` §2.7 needs correcting and `marshal.json` needs reverting.

## Corroboration Summary

`inbox landing-check`: **`complete: true`, `missing_keys[0]`** — a full facts block, back to the
best shape in the epic.

| Claim | Verdict | Evidence |
|---|---|---|
| PR #194 merged as `ff12f34` | **corroborated** | `git log origin/main` head |
| ⛔ **Zero production code changed** | **corroborated** | `git diff --stat c7862d0..ff12f34` — **one file**, `HttpHandlerHttpsIntegrationTest.java` +125/−6. The spec's hardest constraint held |
| No second server was needed | **corroborated** | `RedirectDispatcher.java`:120 `PATH_ALLOWLISTED`, :173 `ALLOWLISTED_HOST = "127.0.0.1"`, :275 the 302 route — a cross-origin hop via the loopback alias, scheme and port preserved |
| The falsified line is real | **corroborated** | `HttpHandler.java`:789 `boolean forwardCredentials = redirectPolicy.forwardsCredentials(currentUri, target);` feeding the `headerFilter` that drops `Authorization`/`Cookie` at :790–793 |
| `required_bots` takes a `bot_kind` | **corroborated** | see above |
| CodeRabbit declares no `issue_comment` evidence | **corroborated** | `coderabbit.md`:41–43 — `participation_evidence: [review_body, inline]`; its verdict-bearing comment is additionally in `ignore_patterns` |
| Sourcery's "used" phrasing matches no recognizer | **corroborated** | `sourcery.md`:119 says the structural recogniser is blind to phrasing that is *"a comparison, not an 'exceeded / reached / hit' statement"*; `refusal_patterns` lists *"reached your weekly rate limit of"*, not "used" |

## The proof, and why it is a real proof

**Falsification: neutralising `HttpHandler.java`:789 moved the suite 7790/0 → 7788/2**, failing on
exactly the credential pair with `expected: <present> but was: <absent>`; restored clean. ⛔ **Two
tests, not more** — the perturbation is sensitive to precisely the claimed behaviour and nothing else
silently depended on it. That number is the evidence, exactly as `pm-findings.md` §4.1 prescribes.

⚠️ **The `STRIP_ON_CROSS_ORIGIN` control passing throughout independently confirms the hop is
genuinely cross-origin** — without it, a same-origin hop would satisfy the positive assertion for the
wrong reason. That control is what makes the proof a proof rather than a coincidence.

**My spec's main risk was refuted at outline, which is where I put it.** I wrote that a second-host
TLS fixture was needed and that its feasibility *"is the plan's main risk and belongs at outline, not
at execute"*. The outline found no second server is required — the existing `PATH_ALLOWLISTED` route
already yields a cross-origin hop; the only blocker was the certificate's single `localhost` DNS SAN.
Placing the question at outline is what let it be answered cheaply instead of built around.

## Deliverable Fidelity vs Spec

The spec staged four; the plan shipped **two**, having collapsed them once the second-server premise
fell. All four are covered: loopback-alias cert + positive wire assertion + STRIP control (spec 1–3),
and three-pass falsification (spec 4).

| Deliverable | Verdict |
|---|---|
| 1. Loopback-alias cert, positive wire assertion, STRIP control | shipped — asserts **what the server received**, not a policy verdict, as the spec required |
| 2. Three-pass falsification against neutralized `HttpHandler`:789 | shipped, with counts recorded |

**The cleartext guard was not relaxed.** The spec's hardest verify-first clause — *"a test that only
passes after weakening that guard has re-opened CWE-319"* — held: no production file is in the diff.

## Three defects needing an owner, none fixable inside this plan's write boundary

1. ⛔ **`marshal.json`:103 is wrong on `main`** — see the correction above. **Revert to `pr-agent`.**
2. ⛔ **CodeRabbit registry gap — the real root cause.** It declares only `review_body` / `inline` as
   `participation_evidence`, but posts its verdict as an **`issue_comment`** — which is *also* in its
   `ignore_patterns`. A genuinely-reviewed PR therefore classifies **`absent`**, which is what forced
   the loop-back and the force-done escape hatch. **Reproduced on both #193 and #194.** ⚠️ This is the
   defect §2.7 should have named; it is a plan-marshall registry surface.
3. ⛔ **Sourcery refusal recognizer fails in the UNSAFE direction.** Its per-account budget notice
   uses *"used"*, while the recognizer keys on `exceeded|reached|hit`, so **a hard refusal was
   credited as participation.** ⚠️ **This retroactively weakens every Sourcery participation claim in
   this epic** — including the `sourcery-ai COMMENTED` rows I read off `ci pr reviews` when closing
   the Sourcery budget defect. It strengthens the standing conclusion that *"all review bots green"*
   was never true for any PR here.

## Metrics and Anomalies

- 7,324 s worked / 1,154,965 tokens — **the cheapest plan of the epic** and the tightest surface.
- 7,790 tests green. Sonar 0 new-code, `count_status=confirmed`. `automatic-review` fired twice
  (one `loop_back`, then done) — caused by defect 2, not by a finding.
- **Two measurement gaps, recorded not papered over:** `scope_creep_check` could not run (no
  `plan_creation_sha` — **fifth** consecutive plan), and the self-review surfacer is Java-blind, so
  its clean verdict **rested on no evidence**.
- ⚠️ **The plan reported three of its own errors unprompted** — a self-review dispatched without its
  required `candidates` field, the wrongly-propagated author-login rename, and misreading a CodeRabbit
  summarize comment as "no review". That candour is what made defect 1 findable.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` = `194`; `landing` = `landings/PLAN-23.md`
- [x] ⛔ **`pm-findings.md` §2.7 CORRECTED** — its "name mismatch" diagnosis was wrong; the real
      mechanism is the `issue_comment` evidence gap.
- [x] **New Open Defect** — `marshal.json`:103 wrong on `main`, needs a revert.
- [x] **New Open Defect** — the Sourcery unsafe-direction recognizer.
- [x] Finding `22aa41` **CLOSED** — the E2E proof is restored and falsified.
- [x] resume_anchor updated; both generated blocks regenerated

## Follow-Ups

1. **Revert `marshal.json`:103 to `pr-agent`** — one line, and it undoes a regression I recommended.
2. **Both bot-registry defects (2 and 3) are plan-marshall surfaces** needing cross-repo routing, and
   defect 3 is the more dangerous: it fails toward *crediting* an absent review.
3. **The owed-ADR debt stands at five.** This plan settled no architectural decision and correctly
   added none.
