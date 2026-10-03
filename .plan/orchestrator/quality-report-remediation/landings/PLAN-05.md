# Landing Analysis: PLAN-05 — Cookie Validation Completeness

epic: quality-report-remediation
workstream: WS-02-contenttype-cookie-collection
pr: #231 (https://github.com/cuioss/cui-http/pull/231) — merged 2026-09-09T15:06:21Z as `ca74911`

> Corroborated: `gh pr view 231` (MERGED), `git show --stat ca74911` (10 files), the
> `HTTPBody.java` hunk read directly, `corpus surfaces` for the declared set.

## Deliverable Fidelity vs Spec

All 5 shipped, 6 commits, 3h59m wall, 3,682,061 tokens.

| Deliverable | Verdict | Evidence |
|---|---|---|
| 1. Case-insensitive prefixes, incl. `__Http-` / `__HostHttp-` | shipped-as-specified | `b489b80` — two `String.startsWith` branches replaced by one `SecurityPrefix` enum under an **ASCII-only** case fold |
| 2. Cookie name and value content validated; CR/LF rejected | shipped-as-specified | `96ff4a1` — both run through `CharacterValidationStage` |
| 3. Attribute resolution last-wins per RFC 6265 §5.3 | shipped-as-specified | `cbac689` |
| 4. One attribute-splitting implementation, one trim key rule | shipped-as-specified | `ceb369b` — shared `splitAttributes`/`attributeKey` with the §5.2 trim rule |
| 5. Cookie-content pipeline absence stated as a decision | shipped-as-specified (documentation half only, per an operator ruling settled at refine) | `2d2e1f2` — no `COOKIE_NAME`/`COOKIE_VALUE` pipeline implemented |

**Verify-first clause checked and settled**: neither `functional-requirements.adoc` (HTTP-14) nor
`specification.adoc` promises those pipelines, so deliverable 5's documentation-only resolution was
the correct outcome rather than a narrowing.

✅ **`String.regionMatches(true, …)` was deliberately rejected** for the case fold because it folds
over all of Unicode, so `__ſecure-` would read as prefixed although no user agent treats it so.
That is precisely the reasoning ADR-0017's both-preset discipline is meant to produce, applied
without being asked.

## Two genuinely exploitable holes closed — not style divergences

- `__Host-a=1; Secure; Path=/; Path=/admin` **passed** `validateHostPrefix`, because the gate read
  the *first* `Path=/` while the browser scopes the cookie to `/admin`.
- `__Host-x=1; Secure; Path=/; Domain =evil.com` **passed**, because `getDomain()` returned empty
  on the whitespace-padded key while a browser applies the `Domain` — **defeating exactly what the
  `__Host-` prefix encodes**. The `AttributeParser` comment calling that skip "strict RFC
  compliance" misread §5.2, which mandates trimming.

Both are fail-open holes in a security prefix whose entire purpose is to be un-spoofable. This is
the highest-severity pair the epic has closed since PLAN-01.

## ⛔ A test was pinning the fail-open behaviour as correct

`CookieTest.shouldHandleAttributesWithSpaces` asserted the deliverable-4 bypass, with the comment
**"This is actually correct per RFC 6265"**. Rewritten, not deleted.

⛔ **That comment is why the defect survived earlier review**, and it is the most durable form of
the sibling-consistency class this epic already tracks: a wrong claim written into a test *as its
own justification*. Every subsequent reader — human or gate — met an assertion that looked
deliberate and cited a spec. Neither a regression question nor a consistency question catches
this; only reading the cited clause does.

## ⛔ Surface delta — and the plan's own "no under-declaration" claim is wrong by one

The landing message asserts *"Realized footprint is exactly the 10 declared files — declared and
realized agree, with no under-declaration."* **That is not correct.**

`corpus surfaces` resolves PLAN-05's declaration to **7 entries** (four files, one directory, two
test files). Nine of the ten realized files fall inside them. The tenth does not:

| File | Status |
|---|---|
| `cui-http-core/src/main/java/de/cuioss/http/security/data/HTTPBody.java` | ⛔ **UNDECLARED** — inside PLAN-04's declared surface (shipped, so no conflict) |

The edit is Javadoc-only — it documents that a repeated `charset` resolves last-wins, inherited
from the shared `AttributeParser` — so it is a legitimate consequence of deliverable 3 and
harmless in itself. **What matters is the claim, not the file.** A plan asserting zero
under-declaration while carrying one is worse for the epic's measurement than a plan that simply
under-declares, because the assertion invites the orchestrator to skip the check. It was checked.

✅ Set against that: **one Javadoc-only file out of ten is the epic's second-best declaration**,
after PLAN-03's one adjacent test. Two of the last three plans have declared close to honestly.

## ⛔ CORRECTION — the epic's oldest standing defect is narrower than recorded, and this landing refutes half of it

The orchestrator's brief to this plan asserted that `pre-submission-self-review` **resolves no
implementor for Java** in this repo. **That was wrong, and it was wrong in the brief this
orchestrator wrote.**

Observed here: the step **did** resolve an implementor — `ext-self-review-plan-marshall` — ran
**full-surface over all 10 files**, and **found a real ADR-0017 defect** which was then fixed.

The epic has carried "no `ext-self-review-{domain}` implementor resolves for Java; every plan ships
without a structural self-review" since the PLAN-01 landing, and re-asserted it as recently as the
PLAN-04 addendum ("fourth corroboration"). ⛔ **The corroborations were of the `clean, no check
matched` OUTCOME, never of the absence of an implementor** — and those are different facts. This
landing separates them: an implementor exists and can find real defects, so the earlier
`no check matched` results need a different explanation than "the dimension is absent".

The defect is amended in `epic.md` rather than deleted: something made the step inert on earlier
plans and not on this one, and that difference is now the open question. Recording the refutation
without the residue would swap one over-claim for another.

## Metrics and Anomalies

- **Tokens**: 3,682,061 — the epic's *cheapest* plan since PLAN-14, against 962 insertions.
- **Duration**: 3h59m wall — by far the shortest of the epic (previous best 12h34m).
- **Finalize**: 16/16 steps done, **no loop-back re-runs**, `simplify` 0 edits / 0 findings on a
  single pass. The verdict-currency cascade that cost PLAN-04 ~400k tokens did not fire here.
- `landing-facts` **complete and correct**: `total_tokens` and `total_wall_seconds` match the
  reported phase totals, and it carries `merge_commit` and `base_at_merge` as optional keys.
- **`scope_cross_check: undetermined`** (`required_coverage_unknown`) on the push freshness gate —
  it permitted on a worktree-sha match, not on a coverage verdict. An unobserved signal, recorded.

## Routing and Merge Behavior

- ⛔ **`main` carries a REQUIRED merge queue, so the configured squash merge was refused** —
  *"an immediate merge would close the PR unmerged"* — and routing went through `pr merge-queue`.
  **Sibling plans should expect the same**; this is repo configuration, not a plan defect.
- **The CI arm counts review bots as build checks.** The first `ci-complete` poll returned
  `ci_final_status: timeout` naming CodeRabbit as the sole failing check **while all 22 build
  checks were already SUCCESS**. Recording that as `ci-verify-timeout` would have been a false
  failure on a green build; a re-poll settled it. → promoted as a lesson.
- **A finalize contract gap forced a manual commit** — `pre-submission-self-review` made source
  edits the dispatcher did not commit, and the operator committed them by hand as `89d143a`.
  ✅ **RESOLVED UPSTREAM — ignore.** Verified against installed plan-marshall 0.1.1635: the step
  now routes findings through `manage-findings qgate add` with `--loop-back-target 6-finalize`, so
  the fix is an ordinary committed change in the re-entered loop and no edits can be stranded. The
  lesson filed for it was removed (tombstone `2026-09-09-16-001`).
- **No ADR allocated** — the `standard` posture composed `adr-propose` out. High-water stays
  **0022**. ⛔ This is now the **third distinct reason** a plan produced no ADR (PLAN-03: `lane:
  off`; PLAN-06: `skipped` yet an ADR shipped from execute; PLAN-05: composed out by posture).
  No step outcome is evidence about whether an ADR exists — only `doc/adr/` is.

## Reconciliation Actions

- [x] row `status` → `shipped`; `pr` `#231`; `landing` `landings/PLAN-05.md`;
      `plan_marshall_plan_id` `cookie-validation-completeness`
- [x] `## Expected Surface` corrected with the undeclared `HTTPBody.java`
- [x] **Epic defect AMENDED, not deleted** — the self-review implementor claim is refuted; the
      inert-outcome residue is retained as the open question
- [x] **Lesson removed, not kept** — the `mutates_source` contract gap is fixed upstream
- [x] Handoffs folded into PLAN-10 and PLAN-13
- [x] Five candidate-lessons dispositioned: two promoted, two folded, one promoted then REMOVED
      as obsolete on the operator's word (the `mutates_source` gap is fixed upstream)
- [x] epic.md reconciled; both generated blocks regenerated

## Follow-Ups

- **WS-05 PLAN-10** — three stale passages in `CookieChaosAttackTest.java` at lines **60-66,
  240-244 and 300-303**. ⛔ The staged spec cited 241-244 and 301-303; the Javadoc blocks start one
  line earlier. All three are prose; the assertions exercise only the `Cookie` record's data
  behaviour and reference no factory message, so nothing there broke.
- **WS-06 PLAN-13** — two more falsifications:
  `doc/http-security/specification/pipeline-architecture-standards.adoc` and any prose describing
  cookie-prefix case-sensitivity or first-match attribute resolution; and
  `doc/http-security/functional-requirements.adoc`, which still carries a "not yet implemented" hit
  for the cookie pipelines that now contradicts the production refusal message.
- **Owed architecture hint, not written** (post-merge writes are unpushable):
  `architecture enrich --module cui-http` — `java:S4144` on a dedicated RFC-clause regression test
  in the `de.cuioss.http.security.data` tests is suppressed at the call site, not collapsed.
- ✅ **The first lesson in this epic that belongs in THIS store.** Candidate-lesson `-005` (the test
  comment pinning fail-open behaviour) is a `cui-http` repository-domain finding, not a
  plan-marshall one — so it files without `--allow-foreign-store`, unlike the 28 parked ones.
