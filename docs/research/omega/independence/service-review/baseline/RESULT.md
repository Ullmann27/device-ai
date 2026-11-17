# Unmodified scheduler baseline

Environment: Linux x86_64 host, Node process tests; not Android or local model execution.
Baseline production files: `527d43f93f6b1837bd8da38cfefcfc84a84fd464`.
Review checkout at execution: `e8c5a264`; the review-only observer and criteria do
not change these scheduler files.

Command: `node --test .github/continuum/local-teamleader-loop.test.mjs`

Result: **PASS — 22 tests, 0 failed, 0 skipped**, exit 0. Raw output:
`local-teamleader-loop.tap`; exit file: `local-teamleader-loop.exit`.

This includes a real SIGKILL/restart test: an interrupted durable cycle consumes
the original attempt without replay. Concurrent scheduler exclusion and source
changes during wait/work are also covered. These are baseline regression checks,
not independent proof of the new standalone service or new neural ability.

Relevant preserved contracts: EXP-01 bounded worker, EXP-02 canonical repair,
EXP-05 approved local runtime, EXP-07 independent counterexamples, EXP-08 durable
job identity, EXP-11 single orchestrator, EXP-12 continuity. The current public
Issue #41 and repository experiment contract were read; Android-first remains
the product target and this acceptance slice is explicitly host-scoped.
