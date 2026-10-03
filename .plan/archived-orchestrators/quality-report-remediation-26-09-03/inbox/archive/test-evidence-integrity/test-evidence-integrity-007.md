envelope_version=1
sender_type=plan
sender_id=test-evidence-integrity
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-02T14:02:34Z

# Candidate lesson: a dispatched leaf cannot honestly wait out a 3600s rate window inside a 300s budget — the wait belongs to the orchestrator, not the dispatch

## Signal class

`signal_script_failure_clusters_count` — script-failure cluster 3 of 3.

## Failing invocation

```text
plan-marshall:automatic-review
  -> rate_window_await_incomplete
```

## What happened

The `automatic-review` step needed to wait on a review-bot **rate window of up to 3600 seconds**. It runs as a dispatched leaf under a per-agent timeout budget measured in minutes. A dispatch cannot honestly poll an hour-long window inside its own budget, so it returned **non-terminally** with `rate_window_await_incomplete`.

The pipeline then **stalled**: the step had not failed, had not completed, and had produced no continuation. It only progressed because the orchestrator noticed and drove it manually.

## Why it is generalisable

1. **A wait whose duration exceeds the waiter's budget is architecturally misplaced.** The leaf is the wrong tier to own it: leaves are bounded, and the marketplace already states that long, lossy waits belong to the main-context orchestrator (`await-long-running`), where the kill is *detected on the wake path* rather than pretended away.
2. **A non-terminal return is a stall by construction.** Every dispatched step must return a terminal disposition — success, failure, or an explicit, machine-routable *hand-back* that names what the orchestrator must do next. `rate_window_await_incomplete` is currently the third thing: it describes the situation but routes nowhere, so the only thing that moves the pipeline is a human/orchestrator noticing silence.
3. **This generalises to every externally-rate-limited gate**, not just review bots: merge queues, CI quotas, provider back-off. Any of them can exceed a dispatch budget, and each will stall the same way unless the wait is hoisted.

## Proposed corrective actions

- **Hoist the wait.** The leaf should observe the rate window, and if the required wait exceeds its remaining budget, return immediately with a structured hand-back naming the wake condition (`resume_after` instant, the claim id, and the step to re-fire). The orchestrator owns the actual wait via the sanctioned `await-long-running` seam.
- **Make the non-terminal return routable.** Give `rate_window_await_incomplete` an explicit orchestrator-side continuation contract, so that receiving it re-dispatches on wake rather than leaving the pipeline quiescent. A disposition with no continuation is a stall with extra words.
- **Assert the budget relationship up front**: any step that may wait on an external window should compare the window's worst-case duration against its own dispatch budget at entry and refuse-with-handback rather than begin a wait it cannot finish.
