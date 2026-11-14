# Code58 complete QA and APK handoff

## Integrated starting point

PR #841 was fast-forwarded into the canonical Code58 branch
`fix/code57-fold7-downloads-research-20261114` at
`f7445b3e73a91682bde40302fc34813e1fef5cf4`. GitHub confirmed the target ref and
reported #841 as merged. That exact source passed 367 Android/Robolectric cases,
all 42 required regression methods and the ARM64 build in run `43568928590`.
This was a selected regression matrix, not the complete release QA.

## Current release candidate preparation

Branch: `release/code58-full-qa-20261114`.

The existing registered workflow path
`.github/workflows/continuum-code57-voice-full-qa.yml` is retained and adapted
on this branch for Code58 (`versionCode 58`, `versionName 0.3.57`). Historical
Code57 source remains unchanged in its own branch. All actions are pinned to
observed commit SHAs. The four original jobs and their safety checks remain:

1. `unsigned-arm64`: exact source, original package, ARM64-only, version,
   16-KiB alignment, unsigned state, APK digest and BUILD-INFO.
2. `renderer-preflight`: Android compilation and renderer regressions,
   explicitly including the six monitor-presentation cases.
3. `full-android`: unfiltered Android/UI JUnit, unchanged strict declared-skip
   policy, 43 required Code58 regression methods and Android lint.
4. `node-backend`: critical and complete Node QA, backend checks and tests.

The additional test `stretchedContinuationAssemblesPromptAndClearsSession`
preserves the useful integration coverage from PR #840 commit
`201054e78fde9fd75c226de13f8d82d0d7adadb4` as a separate method. It exercises
context, history, prompt firewall, final assembly and cleared-session rejection.
The previously verified byte-budget, response-ending and incomplete-history
tests remain intact. No Android production source is changed at this checkpoint.

## Verification at commit creation

- Workflow YAML, embedded Python and shell syntax: PASS.
- Four original job names, exact branch/version conditions, read-only workflow
  permissions, full-suite invocation and strict skip policy: preserved.
- Additional test source and unchanged earlier assertions: independently checked.
- Complete Android/Node/lint/build execution: PENDING on this new source SHA.
- Fold7 hardware: NOT_RUN.

Record the created remote SHA and actual run ID/results in the accompanying PR.
Keep the source frozen after successful QA; do not add a documentation-only
commit and reuse the previous source's result for signing.

## First complete-run diagnosis

Run `43571580048` on `0c747194a1e93801216e693cc0d6501c306d1e33`
built and verified the unsigned ARM64 APK and passed renderer preflight.
The complete Node suite reported 2,471 passing, three failing and 37 skipped
cases. All three failures were obsolete source assertions in
`continuum-hologram-context-wiring.test.mjs` and
`continuum-owner-mainapp-quality.test.mjs`: they still required the old
`Runnable` scheduler instead of the integrated `Choreographer.FrameCallback`.

The corrected source contracts retain single-callback ownership, frame
admission, timestamp propagation and lifecycle cancellation. They additionally
check re-scheduling outside frame admission and reset ordering on pause/detach.
Both test files reproduced the three failures locally before correction and
then passed all 11 cases with zero skips on Node `v24.22.1`.
Backend execution was not reached after the complete Node failure.
A new complete run on the corrected source is required; the failed
run cannot authorize signing. Its Node artifact ZIP SHA-256 is
`57926faa8934bb594101476a27ba4e640e733d4dc2ec4d99924e05b0428a618c`.

The dedicated Code58 QA concurrency group now cancels superseded runs when a
corrected candidate is pushed. The already-failed first run is superseded
without waiting for its remaining long Android job. All four jobs must still
pass on the new exact source; interrupted Android results are not acceptance
evidence. Protected signing concurrency and owner review are unchanged.

## Existing protected signing path

The established signing branch is `chatgpt/code52-owner-signing-20261109`.
Its inspected head was `8e0d8168086e711273b331e5296a96b82033adeb`, with the
Code57 signer integrated by PR #835. The existing `continuum-original-signing`
environment requires owner review. Prepare any Code58 adaptation separately
from the application source and preserve its original certificate, environment,
reviewer, exact-source/artifact verification and draft-only output conditions.

Only the final successful four-job Code58 run can supply the new exact source
SHA, run ID and artifacts. The older Code57 approval and run are not a Code58
approval. No signing secrets are needed for preparing or testing that patch.

Canonical production fallback remains the verified `f7445b3` checkpoint.
Do not reset another team's branch or bypass a failed test or owner gate.
