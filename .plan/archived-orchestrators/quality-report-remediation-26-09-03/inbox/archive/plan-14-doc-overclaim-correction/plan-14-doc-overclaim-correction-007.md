envelope_version=1
sender_type=plan
sender_id=plan-14-doc-overclaim-correction
epic=quality-report-remediation
kind=landing
created=2026-08-26T19:32:29Z

## What landed

plan-14-doc-overclaim-correction shipped as #153 (merged) — six documentation
overclaims corrected across four AsciiDoc files, plus two in-run additions.

```landing-facts
schema=landing-facts/1
plan_id=plan-14-doc-overclaim-correction
epic=quality-report-remediation
pr=#153
merge_state=merged
deliverables_total=4
deliverables_done=4
total_tokens=1663592
total_wall_seconds=21787
steps=finalize-step-sync-baseline:done,pre-push-quality-gate:skipped,pre-submission-self-review:done,finalize-step-simplify:done,push:done,create-pr:done,ci-verify:done,automatic-review:done,sonar-roundtrip:done,branch-cleanup:done,lessons-capture:done,finalize-step-preference-emitter:done,record-metrics:done,finalize-step-print-phase-breakdown:done,emit-landing:done,archive-plan:done
step.branch-cleanup.merge_mechanism=merge_queue
step.create-pr.pr_number=153
step.finalize-step-sync-baseline.action=rebased
```

## Residue

Items the epic should track that no step recorded as a fact:

- **DOC-4 was NOT applied by this plan, by design.** The spec deferred it to PLAN-07
  (WS-02 owns `doc/forwarded-header-resolution.adoc`). This plan never opened that file.
  DOC-4 remains outstanding until PLAN-07 lands it.

- **DOC-6 resolved by repointing, not by supplementing `cve-analysis.adoc`.** The spec
  left both routes open. SEC-3 now links CVE-2021-41773/42013 to `ApacheCVEAttackDatabase`
  and marks CVE-2020-5410/CVE-2019-0232 as representative traversal classes with no
  per-CVE encoding. Those two CVEs are still substantiated by no artifact in the
  repository — the doc now says so honestly rather than implying coverage, but the
  underlying coverage gap is real and unclosed.

- **The plan introduced, then caught, one instance of the defect class it exists to
  remove.** Correcting SEC-3's body left its traceability row still pointing at
  `cve-analysis.adoc` — a self-contradiction. CodeRabbit found it (cbe21d); fixed in
  0bce2bc. The pre-submission self-review independently caught the same shape at the
  `owasp-best-practices.adoc` section header (4b65f8). Two instances, one class: a claim
  stored as a prose/row pair, edited on one side only. Emitted as candidate-lesson -001.

- **The quality gate did NOT run on this plan.** `build-decision` returned
  `not_necessary` (footprint touches no build_map glob), so zero bundles derived and no
  arm executed. Recorded `skipped`, not `done`. Nothing was compiled, linted, or tested
  locally; remote CI was the only gate.

- **Two repo-infrastructure defects were found and fixed in-run, outside this plan's
  declared four-.adoc surface**, on operator instruction: `marshal.json` listed
  `pr-agent` in `required_bots` while the PR-Agent caller workflow had never been added
  to `cui-http`, making the participation guard unsatisfiable. Fixed by installing
  `.github/workflows/pr-agent.yml` (c0a6221), copied from `cui-jwt` and pinned to the
  same org SHA. PR #153 therefore carries a CI change alongside the docs change.

- **CI wedged for 75+ minutes on two unrecoverable GitHub runs** (a push run reporting
  `completed/failure` while its jobs stayed `queued`; a `pull_request` run in
  `startup_failure`), neither rerunnable nor cancellable. Not caused by this change —
  sibling branches passed on the same workflow pin an hour later. Cleared by an
  operator-approved amend+force-push producing a byte-identical tree under a new SHA.

- **The `documentation` skill domain declares no `skills_by_profile.implementation`.**
  Accepted out-of-scope (bb0ea9); tasks were not skill-starved because persona
  augmentation supplied all six skills. Emitted as candidate-lesson -006.
