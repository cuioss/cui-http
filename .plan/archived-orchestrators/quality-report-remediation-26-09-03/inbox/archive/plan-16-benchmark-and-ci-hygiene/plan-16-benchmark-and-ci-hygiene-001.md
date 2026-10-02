envelope_version=1
sender_type=plan
sender_id=plan-16-benchmark-and-ci-hygiene
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T16:05:41Z

# Candidate lesson: a completeness sweep verified only via `architecture search --content` is structurally blind to inventory-excluded trees

## Observed signal

Recurred **twice in one plan**, from two independent detectors:

1. A Q-Gate pass found that a rename/sweep task had reported clean after
   `architecture search --content` returned zero hits, while stale references
   survived in `.github/dependency-review.yml` and `.github/scorecards.yml`.
   `.github/**` is a dotfile tree the architecture inventory does not walk, so
   the search could not have found them. Only a `Grep` fallback surfaced them.
2. CodeRabbit later found `.plan/marshal.json` still mapping a build-profile key
   to a profile the same sweep had renamed away — same root cause, a different
   inventory-excluded tracked file.

## Why it is durable

The defect is not "we forgot two files". It is that the sweep's *verification
instrument* has a known, documented scope boundary, and the sweep treated a
zero result from that instrument as proof of completeness over the whole repo.
Because the instrument's scope is silent about what it skipped, a clean result
is indistinguishable from a complete one — so the same sweep class will keep
reporting green with stale references left behind.

## Proposed durable directive

For any completeness-class sweep (rename, key migration, deprecation removal,
reference retarget) whose green verdict rests on `architecture search --content`:

- Treat the inventory result as **necessary, not sufficient**.
- Add an explicit second pass over the trees the inventory does not walk:
  - dotfile trees at the repo root (`.github/**`, `.claude/**`, `.plan/**`, and
    similar), including tracked config/descriptor files inside them;
  - anything a `.gitignore` rule excludes but that is still consulted at build
    or CI time.
- Report the two passes separately, so "the inventory pass found nothing" is
  never published as "the repository contains nothing".

The directive is about **which trees a completeness sweep must additionally
cover**, not about the two specific files that happened to be missed here.

## Suggested classification (orchestrator judgement)

Likely `anti-pattern`, cross-cutting rather than owned by one bundle. Two
independent recurrences in a single plan argue for codifying it rather than
noting it.
