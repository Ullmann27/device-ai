# Cinema APK53 candidate

Owner request: checkpoint all consistent work, close existing repairs and prepare a technically verified unsigned APK candidate. Integration owner: Cinema root, PR #739, branch `integration/continuum-cinema-apk53-20261110`. No main merge, original signing, publication or installation.

## Frozen starting inputs

- Protected Code52 #706: `17491f4d8103f0e55836369333fab07e80e6e9c2`, unchanged.
- Current Cinema #715: `c972f56ccc4caa2f1807bcaf1d4665347c66c629`. Includes the completed avatar, material, result, compact-layout, thermal-guard and render-evidence work from #730–#734.
- Combined support #725: `b0a1635b77f568692ac6ff6b662f52d6b6eb7231`; original parent ancestry retains #720 dialog/ASR, #724 queued results and #726 evidence-bound outcomes.
- Combined WIP checkpoint: `a07b183b67b9c00192bb96b61a0002f74e23372b`, tree `5551010f35d432a9a33790a81f538b0de6011311`.

Upstream green results are not acceptance of this combined candidate. Final checks must run at the exact published source SHA, in a clean checkout, with fresh outputs.

## Candidate contract

Package `at.heinz.deviceai.continuumlocal`; versionCode 53; versionName 0.3.52; ARM64 only. Preserve original GLB/orb and user data. Signed Code52 is a protected draft; installed physical-device version remains NOT_RUN. Large user-installed language models remain runtime data, not a bundled replacement.

Existing `continuumLocalRenderUnsigned` Gradle mode builds `:app:assembleRelease` without signing credentials. It cannot be combined with test/lint tasks. The existing standalone QA mode runs compilation, full unit/UI tests and configured lint without producing an installable alternate app. No signing guard is weakened.

## Required acceptance

- Full source-bound Node and critical regression profiles; backend check/tests.
- Full Android unit/UI suite, including existing JUnit minimum and required cases; no filters for full-suite acceptance.
- Existing `lintDebug` gate (currently NewApi scope).
- Release assembly, ZIP integrity, package/version/ABI readback, original asset checks and SHA-256. Valid signature must be absent.
- Reproducible source-bound PNGs from the existing native capture fixtures, clearly labelled Robolectric Canvas. Original-GLB CPU evidence remains separately labelled.
- Physical Fold7, GPU/Filament, real microphone/voice/lip sync, device frame timing and thermal acceptance: NOT_RUN until measured.

Research #717/#718/#719, rejected #721, model weights #728 and unfinished Android system #735 are not imported or promoted. Existing research defaults are unchanged. No new large research run.

## Signing handoff

The checked-in GitHub signer is still Code51-specific. Do not repurpose it or execute it for53. A protected generic owner/Termux path exists, but any original signing requires fresh explicit owner approval of the exact accepted source and original certificate. This candidate is UNSIGNED_NOT_INSTALLABLE until that separate gate is satisfied.

EXP-00 build compatibility, EXP-07 evidence and EXP-12 session safety apply. Rollback: discard/revert this candidate before promotion; retain all donor branches and evidence.
