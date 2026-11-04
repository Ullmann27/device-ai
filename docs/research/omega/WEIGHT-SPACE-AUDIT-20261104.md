# Weight-space audit — 2026-11-04

Status: WIP / UNVERIFIED / RESEARCH_SHADOW.

## Scope and authorization
Owner requested analysis of a supplied video about direct model-weight edits, followed by inspection, testing and possible improvement of Continuum's own weights. This branch records the bounded EXP-04 / EXP-05 / EXP-07 / EXP-12 investigation. No runtime promotion is authorized by a benchmark result alone.

## Exact starting point
- Base branch: fix/continuum-device-experience-resume-20261104 (PR #478).
- Base source SHA: d8fd7f7cc5aa721b2c4a975c527c5123e216a944, freshly read through the GitHub connector on 2026-11-04.
- PR #483 was observed separately at 578bdfd12f896cc990bf1a043bc1b248ba3adb6d; this investigation does not integrate its ongoing UI/runtime work.

## Planned verification
1. Identify the original scientific source, exact edited model/tensors and limits of the video claim.
2. Restore the available owner-provided SWB1 snapshot with the existing strict codec; record format, checksum, dimensions and original hash without publishing the private snapshot.
3. Measure an unchanged baseline using the existing evaluators. Existing published fixtures are regression tests, not newly blind holdouts.
4. Attempt only a small, justified offline candidate edit after a diagnosed failure; freeze candidate selection before any independent holdout is opened. Preserve original bytes.
5. Record changed-weight count, before/after hashes, regressions, raw local test logs, rejected candidates and compatibility. Do not claim LLM or device improvement from SWB1 synthetic probes.

## Current evidence
- A stored non-native SWB1 backup of 32,960 bytes was fetched and its complete-file SHA256 calculated locally.
- No inference, candidate edit or model-quality improvement has been completed at this checkpoint.
- Large language model weight availability and the video claim are still under investigation.

## Boundaries and rollback
No main merge, force-push, branch deletion, GitHub Actions, paid compute/API, model promotion, release, signing, installation or S01 bypass. Preserve owner facts, ToolGate, CCC1, original package/signature and update channel. Private video, backup bytes and any personal examples remain outside the repository. All candidate work is off the active application path; rollback is to discard the candidate and retain the original snapshot.

## Resume
Read this handoff, the experiment contract and current source SHA, then the forthcoming evidence report and inspected existing SWB1 implementation. Until actual results are recorded here, all quality and device outcomes remain NOT_RUN.
