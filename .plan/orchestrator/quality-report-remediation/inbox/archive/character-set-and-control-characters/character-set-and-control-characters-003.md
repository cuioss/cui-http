envelope_version=1
sender_type=plan
sender_id=character-set-and-control-characters
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-08T05:19:57Z

component=plan-marshall:automatic-review
category=anti-pattern

# Re-read WHAT a review bot's refusal says on every retry — waiting only helps for one of its refusal classes

The run spent roughly 90 minutes across three waits against a rate-limited review bot and
achieved nothing. The waits were not too short; they were aimed at the wrong refusal.

The bot's refusal had silently CHANGED CLASS between attempts:

- Class 1 (time-bounded): a quota / rate window. Waiting is the correct remedy — the
  window expires and the same request succeeds unchanged.
- Class 2 (unconditional): "does not re-review already reviewed commits". No amount of
  waiting changes this. The request will be declined identically forever, because the
  decline is a function of the target, not of the clock.

The wait loop was entered on a class-1 refusal and kept re-firing on the assumption that
the class still held. It did not. Every subsequent wait was pure latency.

## Rule

A retry loop against an external reviewer MUST re-read the refusal text on EACH iteration
and re-derive its class, rather than caching the class from the attempt that opened the
loop. Concretely:

- Classify each refusal as time-bounded (quota, rate limit, "try again later") or
  unconditional (already reviewed, unsupported target, out of scope, not configured).
- Wait only on a time-bounded refusal.
- Exit the wait loop immediately on an unconditional refusal and switch to a remedy that
  changes the REQUEST rather than the timing.

## The remedy that worked

For "does not re-review already reviewed commits", the working remedy was to give the
incremental reviewer an UNREVIEWED TARGET — a fresh PR. That is the general shape: an
unconditional refusal is answered by changing the input, not by changing the schedule.

## Cost of getting this wrong

The failure is silent and expensive. Every wait returns "still refused", which looks
identical to a quota window that has not yet expired, so the loop is self-confirming.
Nothing surfaces the class change except the refusal text itself, which is exactly the
thing a cached classification stops reading.
