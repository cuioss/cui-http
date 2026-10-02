envelope_version=1
sender_type=plan
sender_id=character-set-and-control-characters
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-08T05:20:02Z

component=plan-marshall:plan-marshall
category=improvement

# A wait implemented as elapsed time in a background sleep is not resumable — persist a target timestamp instead

A background `sleep` was used as the wait timer for a long poll. The host killed it TWICE
under system memory pressure. Each kill silently reset the elapsed time: the wait restarted
from zero with no signal that anything had been lost, so the total wall time grew without
the wait ever completing.

The defect is that ELAPSED TIME lives only in the killed process. When the process dies,
the accumulated progress dies with it, and nothing on disk records that the wait had been
running at all.

## Remedy that worked

Persist the wait's TARGET INSTANT (an absolute UTC timestamp) to disk before the wait
begins, then have each wake compute `remaining = target - now`. This makes the wait
resumable across an arbitrary number of kills:

- A kill costs only the sleep segment that was in flight, never the accumulated progress.
- A resumed wait computes the correct remaining interval with no coordination.
- A wait that has already expired is detected on the first wake after a kill instead of
  restarting a full interval.
- The target timestamp is itself the evidence that the wait was entered, so a kill is
  DETECTABLE rather than invisible.

## Rule

Any wait longer than a single tool call must record its deadline, not its elapsed time.
Absolute-deadline arithmetic is the resumable form; elapsed-time accumulation in a
process's own memory is not, because the process is exactly the thing that gets killed.

## Relation to the existing known-lossy wait seam

The orchestrator-tier long-running wait is already documented as a known-lossy primitive
whose harness kill is DETECTED on the wake path rather than prevented. This lesson is the
complementary half: detecting the kill is not sufficient if the wait's own progress was
stored where the kill destroys it. A persisted deadline makes the detected kill RECOVERABLE
instead of merely observable.
