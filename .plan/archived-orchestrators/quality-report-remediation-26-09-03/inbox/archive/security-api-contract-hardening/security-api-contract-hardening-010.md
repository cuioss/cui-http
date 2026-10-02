envelope_version=1
sender_type=plan
sender_id=security-api-contract-hardening
epic=quality-report-remediation
kind=candidate-lesson
created=2026-08-29T15:52:02Z

# Candidate lesson: the automated-review scripted path degraded to manual filing, and the degradation is only visible in prose

**Signal source**: `signal_automated_review_count` — the `plan-marshall:automatic-review`
step's degraded execution, recorded across two work-log WARNINGs and the `detail` text of
both pr-comment findings it produced.

## What happened

Three separate degradations on one step, all attributed to gh CLI contention from a
concurrent session:

1. `2026-08-29T13:18:47Z` (`5e0aa9`, WARNING) — `pr wait-for-comments` returned
   `status:error`; the step proceeded best-effort to the completion-aware poll.
2. `2026-08-29T13:22:20Z` (`2ab67f`, WARNING) — the completion-aware poll's `bot_completion`
   returned `status:unconfigured` for `coderabbit`, `pr-agent`, and `sourcery`; polling
   stopped best-effort and the step proceeded to the producer stage.
3. Both resulting findings (`75ae6a`, `8fcf23`) carry, in their `detail` prose: "Filed
   manually: the scripted `fetch_findings` path was blocked by a 60s `gh auth status`
   timeout."

The step nonetheless completed `outcome=done` at 15:33:25Z, and the outcome was materially
good — both review findings were filed, remediated in `3313a97`, confirmed by the bot, and
resolved. But every one of those findings was transcribed by hand.

## Candidate rule

The step reported `done` while three of its automated stages had failed and its finding
production had fallen back to manual transcription. The fallback worked here because an
operator was present and attentive; the failure mode it masks is a run where the same
degradation occurs and NO findings are filed, which is indistinguishable at the step level
from a clean review that found nothing.

Candidate rule: a review step that could not complete its scripted fetch MUST record that
fact as structured state — a distinguishable outcome, or a fact on the step record — not
only as free prose inside the `detail` of findings that happen to exist. **"Zero findings
because the bots were clean" and "zero findings because the fetch never ran" are the same
observable today**, and the second is the dangerous one. This is precisely the
which-kind-of-zero-is-this discriminator that `inbox list`, `list-stalled`, and
`corpus enumerate` all enforce on their own zeros; the automated-review step does not
enforce it on its finding count.

Secondary, narrower observation: `bot_completion` returning `status:unconfigured` for all
three bots was treated as a transient-contention symptom. `unconfigured` and `unreachable`
are different claims — the first says the bot is not set up, the second says it could not be
read — and collapsing them means a genuinely unconfigured bot roster looks like transient
contention and vice versa.

## Recurrence question for the orchestrator

Whether gh CLI contention from concurrent sessions is systemic across this epic's plans (in
which case the serialization of review steps is the fix) or was a one-off collision here.
This plan saw it three times in one step, which is suggestive but not conclusive.

## Provenance

- Plan: `security-api-contract-hardening`
- Work-log entries: `5e0aa9` (13:18:47Z, WARNING), `2ab67f` (13:22:20Z, WARNING)
- Findings whose `detail` records the manual fallback: `75ae6a`, `8fcf23`
- Step `plan-marshall:automatic-review` completed `outcome=done` at 2026-08-29T15:33:25Z
- PR #169
