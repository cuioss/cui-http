envelope_version=1
sender_type=plan
sender_id=plan-10-attack-test-mechanism-correctness
epic=quality-report-remediation
kind=candidate-lesson
created=2026-10-03T21:22:31Z

# Candidate lesson: manage-status mark-step-done --head-at-completion rejects a short SHA

**Source signal**: script failure observed by the finalize orchestrator during this run (reported by the dispatcher; not independently present as a [FAILED] line in the plan work log).
**Component**: plan-marshall:manage-status (mark-step-done) and the phase-6-finalize step docs that build the call.

## What happened

`mark-step-done --head-at-completion <short-sha>` was rejected; only the full 40-character commit SHA is accepted. The call had to be re-issued with the full SHA.

## Candidate rule

Callers must resolve the full SHA (`git rev-parse HEAD`, not `--short`) before passing `--head-at-completion`. Either the docs that construct this call should say "full SHA" explicitly, or the verb could expand an unambiguous short SHA itself.

## Classification hint

Marketplace contract/usability issue (plan-marshall bundle); argparse-rejection class.
