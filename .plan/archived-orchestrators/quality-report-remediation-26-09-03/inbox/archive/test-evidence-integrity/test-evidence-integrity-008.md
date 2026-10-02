envelope_version=1
sender_type=plan
sender_id=test-evidence-integrity
epic=quality-report-remediation
kind=candidate-lesson
created=2026-09-02T14:02:40Z

# Candidate lesson: the `broad` scope band was driven by file count inside ONE Maven module — path count measures breadth, not cross-module reach

## Signal class

Q-Gate finding `a33d09` (phase `2-refine`, resolution `taken_into_account`) — scope-realism.

## What happened

The plan's scope estimate resolved to the `broad` band. The band was produced by the **count of distinct file paths** named in the request. Every one of those paths lived inside a **single Maven module** (`cui-http-core`), and specifically inside its test tree.

So the routing signal said "broad, cross-cutting" when the truthful description was "many files, one module, one concern". The finding was accepted as `taken_into_account` rather than acted on, and the band-derivation logic is unchanged.

## Why it is generalisable

1. **Path count and module reach are different measurements, and only one of them is what "broad" is used to mean downstream.** Consumers of the band (lane routing, execution-profile projection, review depth) treat `broad` / `multi_module` as a proxy for *architectural* reach — how many independently-owned surfaces the change touches. A count of files cannot express that, and in a monorepo with one large module it systematically over-reports.
2. **The error direction is safe but not free.** Over-reporting widens the lane, which is the correct default when scale is unknown — but it is paid on every plan whose work is genuinely concentrated in one module's test tree, in extra discovery and heavier finalize posture. A cheaply available module-attribution query would refine it without giving up the safe default.
3. **The information needed is already available.** Module attribution is a structured query (`architecture which-module --path P`), so the band could be derived from the count of DISTINCT owning modules alongside the path count, rather than from the path count alone.

## Proposed corrective actions

- Derive the scope band from **two** measurements rather than one: distinct path count (breadth within a surface) and **distinct owning-module count** (architectural reach). Reserve the `multi_module` / `broad` bands for a genuine plural module count; let a high path count inside one module resolve to a `single_module` band that carries the path count as evidence.
- Keep the fail-wide default: when module attribution is unavailable or a fan-out marker is present, continue to widen. The change is to stop widening when attribution IS available and says "one module".
- Report the provenance of the band either way — which measurement drove it — so a reader can see "broad because 40 paths in 1 module" and disagree with the band without reverse-engineering the rule.
