envelope_version=1
sender_type=plan
sender_id=redirect-api-documentation
epic=quality-report-remediation
kind=landing
created=2026-09-02T21:17:48Z

## What landed

redirect-api-documentation shipped as #191 (merge_state unknown at the fact level — see
Residue; independently verified on main as squash commit 384a22c) — the redirect API is now
documented, the absolute-URL overclaim is corrected at both sites that asserted it, six
CuiLogger placeholders were fixed, all fourteen ADRs were value-tested with zero removals, and a
real CWE-319 cleartext-credential-forwarding gap was found and fixed mid-run under an operator
scope-widening ruling.

```landing-facts
schema=landing-facts/1
plan_id=redirect-api-documentation
epic=quality-report-remediation
pr=#191
merge_state=unknown
deliverables_total=4
deliverables_done=4
total_tokens=unknown
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:done,pre-submission-self-review:done,finalize-step-simplify:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done,archive-plan:done
step.create-pr.pr_number=191
step.sonar-roundtrip.count_status=confirmed
step.sonar-roundtrip.new_code_issue_count=0
step.sonar-roundtrip.issues_fetched=0
step.sonar-roundtrip.work_performed=true
```

## Residue

Items the epic should track that no step recorded as a fact this run:

- **Producer gap: `branch-cleanup` and `record-metrics` recorded no typed `--fact` this run**,
  unlike prior runs of the same step types in this epic (compare the archived siblings, which
  routinely carry `facts.merge_state` / a token total). `merge_state` and `total_tokens`
  therefore degrade to `unknown` above per the honest-gap rule — this is a producer-gap,
  mechanisable once those two steps' `--fact` wiring fires reliably, not a corpus defect.
  Independently verified against the repository rather than corroborated from narrative: `git
  log` on `main` shows squash commit `384a22c` — "docs: document redirect API and prune the ADR
  corpus (#191)" — and `branch-cleanup`'s own `display_detail` reads "merged via queue 384a22c,
  main pulled, branch + worktree removed". `record-metrics`'s own `display_detail` reads "1h46m
  worked / 2.6M tokens across 6 phases" (7h41m wall per `metrics.md`). Neither is promoted into
  the fenced block above — a narrative string is not the typed fact the schema requires.

- **D1 — redirect API documented** (`doc/client-handlers-readme.adoc` `[#redirect-policy]`
  section, confirmed present at HEAD). Every claim was traced to `RedirectPolicy.java` /
  `HttpHandler.java` at outline time (Q-Gate `f72f7a`) rather than to any prior landing
  narrative, and TASK-007's later fix moved five of those documentation sites in step with the
  code change.

- **D2 — the "All URL validation" absolute-URL overclaim corrected at BOTH sites**, confirmed
  absent from `CLAUDE.md` and `doc/http-security/specification/pipeline-architecture-standards.adoc`
  at HEAD. The Q-Gate caught a stronger-form survival of the same claim at the spec's
  authoritative selection matrix (`pipeline-architecture-standards.adoc:227`) that the original
  task scope had not named (finding `48a973`); fixing only `CLAUDE.md` would have left the
  canonical document untrue. Verification was also switched from a compile proxy to a literal
  `architecture search --content --literal` sweep with a `count:0` criterion.

- **D3 — six `{}` -> `%s` CuiLogger placeholders across the four named `package-info.java`
  files**, spot-verified at HEAD (`pipeline/package-info.java` now reads `log.warn("Security
  violation: %s", ...)`). The Q-Gate's own criterion originally said five while its own
  enumeration summed to six (finding `71f298`); corrected to six. The 19 further brace sites
  across 12 other files are a documented exclusion (finding `cb1816`, narrowed rather than
  fixed), not an oversight.

- **D4 — all fourteen ADRs surveyed against the value test with a written per-record verdict**
  (decision-log hashes `c97eee`..`be2750`): 14 KEEP, 0 removals, so no renumber fired — matching
  the outline's own hypothesis. The removal + renumber machinery was scoped in up front and
  stayed correctly unused.

- **Scope widened twice by operator ruling, both recorded in `decision.log`:**
  (a) `RedirectNotAllowedException.java:37` — a stale Javadoc example calling a nonexistent
  `handler.sendFollowingRedirects`; the real method is `HttpHandler.send(...)`. Found during
  execution, correctly not absorbed there (task constrained "no production source file
  modified"), ruled in by the operator (hash `9f215f`).
  (b) **The significant one — a real CWE-319 gap.** `RedirectPolicy.forwardsCredentials` never
  consulted the target scheme, so an http-origin handler forwarded `Authorization`/`Cookie` over
  cleartext on an http->http same-origin hop (and cross-origin under
  `FORWARD_TO_ALLOWLISTED`). CodeRabbit raised it as `cdb373`; the first triage pass dismissed it
  as unreachable, having proved only the https->http path (`3b03c6`); the operator overruled
  that, established the http->http path IS reachable, and ruled it in scope (`8c3d32`). Fixed in
  TASK-007, commit `8d4e3b0` — confirmed on the branch history — with a cleartext guard, matched
  positive/negative test controls, and five documentation sites moved with the code.

- **Corrections to the spec itself** — the spec told this plan to re-derive rather than trust its
  own counts, and re-deriving found it wrong three times, each independently confirmed in
  `decision.log`:
  - the spec claimed 8 inter-ADR `xref:` links; exactly 7 exist. The alleged `0009->0011` is a
    plain-prose citation in ADR-0009's References section, a third citation category the spec's
    two-bucket checklist never enumerated (`acc90b`).
  - the spec's prescribed proof sweep (`grep -rnoE 'ADR[- ]?[0-9]{4}'`) cannot verify a renumber:
    `doc/adr/README.md` carries zero `ADR-NNNN` tokens and record filenames are bare
    `NNNN-slug.adoc`. A second non-token check was mandated instead.
  - a verification criterion claimed "16 matches across 15 files" — `count:16` is result rows,
    and summing `match_count` gives 24 per the criterion's own component breakdown. The live
    re-derivation at execute time found exactly 23 (`1ebe43`); the discrepancy is reported
    honestly (arithmetic slip in the criterion, not a corpus defect) rather than picking one.

- **Carried forward, not fixed** (finding `22aa41`, accepted): the cleartext guard flipped the
  only end-to-end proof that `FORWARD_TO_ALLOWLISTED` DOES forward cross-origin — the fixture
  `forwardStrategyShouldKeepCredentialsCrossOrigin` was renamed to
  `forwardStrategyShouldDropCredentialsToCleartextAllowlistedHost` and now asserts DROP for its
  (cleartext) target. Nothing now covers cross-origin + allowlisted + https end-to-end: the rule
  is covered at the `RedirectPolicy` unit seam and same-origin-over-TLS is covered E2E, so the
  gap is narrow but real. Closing it needs a second-host TLS fixture. Worth a follow-up plan.

- **Owed ADR pass the spec itself recorded, now doubled.** PLAN-17 settled three ADR-worthy
  decisions (redirect loop placement, same-origin-by-default egress policy, CredentialForwarding
  strategy) with no ADR record, because `adr-propose` carried `lane: off`. That debt was not this
  plan's deliverable and was not absorbed here. This plan's own TASK-007 fix — the cleartext
  credential guard — is now a fourth such decision with no ADR, since `adr-propose` was also
  `lane: off` under this plan's standard posture (`decision.log` `5c142b`/`67c383`).

- **Verification at merge**: `verify -Ppre-commit` green at 7788 tests (`pre-push-quality-gate`
  display_detail); Sonar new-code issues 0, `count_status=confirmed`, scanned SHA == HEAD
  (`sonar-roundtrip` facts, transcribed above); CodeRabbit reviewed the fix commit `8d4e3b0`
  incrementally with no new findings — established from ground truth (`gh api` review objects,
  CodeRabbit's own incremental-review reply, and a SUCCESS check state) after an
  `escalate_ask{reason: re_review_timeout}` resolved on evidence under standing unattended-run
  authorization (`96c64c`), not by picking a prompt option. 0 pending findings of 6 total
  (`bug.jsonl`: 1 fixed; `improvement.jsonl`: 1 accepted; `pr-comment.jsonl`: 4, all
  accepted/taken_into_account) — every finding this plan opened is resolved.
