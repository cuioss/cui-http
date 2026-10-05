envelope_version=1
sender_type=plan
sender_id=decoding-normalisation-hardening
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-06T07:09:09Z

component=plan-marshall:phase-6-finalize
category=improvement
bundle=plan-marshall

# `pre-submission-self-review` records `done` when no surfacer exists, hiding a whole missing review dimension

## What happened

In plan `decoding-normalisation-hardening` (epic `quality-report-remediation`) the finalize step
`default:pre-submission-self-review` completed in about 15 seconds and recorded:

```text
status.metadata.phase_steps["6-finalize"]["pre-submission-self-review"]
  outcome: done
  display_detail: "self-review not run: no surfacer implementor resolved"
  head_at_completion: 39e026f0b1b2ddb67c92e22e95ea9d7d7384bdad
```

The paired decision-log line is explicit:

```text
(plan-marshall:phase-6-finalize:pre-submission-self-review) Zero-generator fallback — no
ext-self-review-{domain} implementor resolves for domains java,java-cui,general-dev,documentation;
no surfacer ran and no analysis was performed
```

Only `pm-plugin-development:ext-self-review-plan-marshall` ships an implementor of the
`ext-self-review-{domain}` extension point. For a Java project no implementor resolves, so the
structural self-review dimension is simply absent — and it is absent while reporting `done`.

## Why it matters here

This plan's changes were security-critical (decode/normalise verdict symmetry in
`DecodingStage` and `NormalizationStage`). With no self-review surfacer, CodeRabbit was the ONLY
structural review the change received — and CodeRabbit's round-2 review caught a genuine
reintroduction of the exact asymmetry the plan existed to remove (see the sibling
candidate-lesson on that defect). A structural self-review pass over symmetric-pair
functions and contract sources is precisely the class of surfacer that would have flagged
"the check reads the RETURNED value, whose selection is config-dependent" before push.

The reporting shape is the compounding problem: `outcome: done` is what the
`phase_steps_complete` handshake and the phase-breakdown renderer both consume. The
`display_detail` string carries the truth, but nothing routes on prose.

## Rule

An extension-point consumer that resolves ZERO implementors must not report the same terminal
outcome as one that ran. Two concrete asks:

1. `pre-submission-self-review` should record `outcome: skipped` (or `done` with a structured
   `--fact self_review_ran=false surfacer_count=0`) when the zero-generator fallback fires, so
   an audit can COUNT the runs that had no structural review instead of parsing
   `display_detail`.
2. The absent Java-domain implementor is the underlying gap: a `pm-dev-java:ext-self-review-java`
   surfacer (symmetric-pair methods, config-gated branch pairs, contract/Javadoc-vs-behaviour
   sources, regex constants) would give Java plans the dimension `plan-marshall` plans already have.

## Impact

Every non-plan-marshall-domain plan in this epic — and every Java project using the workflow —
silently ships without the pre-submission structural review while its step board shows green.
