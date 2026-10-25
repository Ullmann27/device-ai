# Continuum M6 – Recovery handoff and owner RUN binding audit
Date: 2026-10-25
Source: Ullmann27/device-ai

## Recovery of Sol 6.1 M6 work

At the time of this review, the latest visible coding chain on GitHub is draft PR #125 -> #127 -> #128 -> #129, ending at commit `9f14139b9b4f7b1c02de2c5bc2a568f75c30714c`. Repository branch and PR listings do not show a published new M6 branch. The two SHAs displayed in Sol's uploaded screenshot (`53dfe75a72618789123dd1119fcb501d4b34cca3` for implementation and `4f01eab30756c47e5272efd5102211b6414fdd90` for an experimental fixture) cannot currently be retrieved as commits via this GitHub connection. **Do not assume they were pushed.**

The screenshots report M6 research into recovery, FEATURE acceptance with a green baseline, context feedback, local model runtime, Android patch-proposal mode and analyzer regression. They also report an Android host QA result of 1,013 passing tests, 3 skipped, Lint without reported findings, and a debug APK built only for host QA. Those new M6 source files, logs, hashes, test artifacts and the result of the two-call feature trial are **not independently available on GitHub in this snapshot**. A failed remote execution message is visible; its cause remains unverified. Do not merge, recreate or advertise these changes as independently verified without the original new branch and evidence.

## Security finding in the last *published* coding path

A prepared Android RUN passes both a task ID and SHA-256. Before this PR, `scripts/continuum-typed-coding-bridge.mjs` forwarded only the ID into `runCodingTask`, comparing the task hash only *after* running CCC1. A mismatched owner hash could therefore cause an otherwise valid stored coding task to be run before detecting the mismatch. The orchestrator also did not explicitly recheck that the checked-out tree stayed clean at RUN time.

## Changes in this branch

- `.github/continuum/coding-orchestrator.mjs`: optional `expectedTaskFingerprint` parameter; fail closed on mismatching or malformed hash immediately after reading the persistent grant, prior to any model/tool execution or CCC1 transition. Validate grant task ID equals requested ID and stored CCC1 ID has exact `t-[a-f0-9]{16}` format. Refuse dirty worktrees before RUN. Older non-Android callers can omit the new parameter without API breakage.
- `scripts/continuum-typed-coding-bridge.mjs`: pass the exact Android-approved hash as `expectedTaskFingerprint`. Preserve the new rejection code `CODING_TASK_BINDING_MISMATCH` as a fixed, redacted failure response.
- `scripts/continuum-typed-coding-bridge.test.mjs`: regression coverage for early mismatch, corrupted stored grant, dirty checkout and SHA forwarding. No more files modified.

## Validation

- V8 parsing of the ESM bodies of all three modified files passed after removing top-level imports/exports for a **syntax-only** check.
- The **actual new `runCodingTask` function body** was evaluated in a sandboxed JavaScript runner with controlled dependency mocks. Cases: mismatched hash, malformed hash, invalid stored CCC1 ID and dirty checkout, **4/4 expected rejections**, **zero** journal invocations and **zero** model invocations. This is not an end-to-end Node/Android test.
- The source was read back from the GitHub branch after each commit. No connected CI, full repository Node suite, complete Android Gradle suite or physical Fold7 test was executed on this new commit. This PR must remain a Draft until those checks pass on an authorized complete checkout with model/OS evidence.

## Next high-value integration

1. Recover Sol's M6 commit and exact origin branch from the original cloud workspace if available; do not invent or attempt to reconstruct proprietary unpushed files. Verify SHA and cherry-pick onto an isolated branch only after comparing interfaces, especially `coding-orchestrator.mjs` and CCC1 grant schema.
2. Run baseline and targeted Node regression suite on a complete checkout; check impact on preexisting CCC1 tasks. Review legacy caller compatibility and ensure the owner's signing/release gates have not changed.
3. Integrate M6 durable replay and proposal-only mode only from actual source, with negative crash/tamper cases and truthful distinction between unexecuted proposal vs independently verified patch.
4. Reproduce a real local model FEATURE trial with its recorded model weights/hash, fixture SHA, two-call limit, full test output and no synthetic success labels.
5. Keep `main`, the single signed `at.heinz.deviceai.mentorpaired` app, productive model weights and owner certificate unchanged until an explicitly authorized new owner release.
