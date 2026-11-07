# Ω evaluation comparison implementation plan

Goal: compare the existing frozen M3 offline scorer's reports without letting aggregate gains hide individual regressions. This extends the current evaluator; it does not run a model or authenticate a device result.

Spec: ASTRA-OMEGA-HANDOFF-20261103.md and current owner master request, EXP-05/07/12.

- [ ] Add Node tests for report parity/improvement, per-case regression despite aggregate gain, harmful results, changed/missing/duplicate/unknown cases and altered expected labels. Observe RED before implementation.
- [ ] Implement scripts/continuum-offline-eval-compare.mjs with strict 81-case fixture hash and exact report coverage/metadata checks. Ignore untrusted summary comments. Return per-category counts and case changes, report SHA256 digests and explicit HOST_DETERMINISTIC_REPORTS_ONLY boundary. CLI nonzero for rejection/regression/harm.
- [ ] Freeze docs/research/omega/evaluation-v1.json with all 15 requested dimensions, their existing test surfaces and missing direct-Gemma/Fold7 evidence.
- [ ] Run focused Node tests; independent Quality agent reviews final commit and reruns. Capture existing baseline/final M3 output when SDK permits. A hash identifies bytes, not a trusted independent grader.

Review focus: a report is not an authenticated execution receipt; absence of device reports remains NOT_RUN. M3 is a known development set, not a fresh blind holdout. No tests or expected answers may be changed for a better score. No owner information enters the synthetic fixture. Preserve current evaluator semantics; do not reimplement its judge.
