# Code47 hidden-decision conflict repair

## Problem and minimum correction

Source baseline: `f26d1063b25c65c22b8d411789d1733c92e3ad72`.
Its production/test source is unchanged in Code47 documentation checkpoint `6bd0e172b5615854e021e43aa0ed04a1978eb733`.

The optional prompt digest filtered confidential or overlong active decisions before deciding whether several relevant decisions conflict. A newer conflicting statement could disappear from that set, leaving an older incompatible statement as apparently unambiguous context.

Two production lines now keep all relevant active decisions in the conflict set, then apply the existing display-safety filter to the single eligible candidate. No confidential or overlong content is emitted. Existing revocation timing, exact deduplication, journal selection, prompt authority boundaries and byte limit remain unchanged.

Two new JUnit regressions exercise actual `OwnerStatement -> WorkDecisionRecall.items -> LocalDecisionPromptDigest`, including positive single-decision controls.

## Executed evidence

| Check | Baseline | Corrected digest |
|---|---:|---:|
| Real SDK-class Java reproducer | 2/2 failed; stale Monday plan emitted | 2/2 passed; stale plan suppressed |
| Root: same 19 JUnit tests and test-class bytes | 17 passed, exactly 2 new regressions failed | 19 passed, 0 failed |
| Independent patch/receipt review | Confirmed ordering defect | No blocker |
| Whitespace validation | — | git diff --check passed |

Root changed only classpath precedence between the original SDK-produced digest and the newly compiled corrected digest. It ran the identical new test classes both times. No substitute production classes, mock item mapper, model inference or signing key was used.

Compilation: installed Kotlin2.4.0/JDK17, 256-MiB maximum compiler heap; only the corrected digest and its test were newly compiled. The existing real Android build produced their unchanged production dependencies. `compile-command.json` and `junit-command.json` preserve exact local invocations; adjust local toolchain/build paths when reproducing elsewhere.

## Scope and state

Targeted fix verification is GREEN. Full Android/Node/backend/APK validation of the **final integrated Code47 source remains required**. The separate full SDK run on f26d is an unmodified baseline run and must never be relabeled as a run of this patch. No Fold7/LiteRT/audio/GPU or original-signed APK result is claimed.

The author receipt intentionally preserves its historical LOCAL_PATCH_VERIFIED_NOT_PUSHED status; the enclosing GitHub commit supplies subsequent publication provenance. Revert only the two source/test changes to roll this repair back; no data/schema migration exists.

## Subsequent verified checkpoints

Code46 is delivered: original publisher LIVE at 2026-11-06T19:00:55Z, independent verification of actual APK bytes and original signature passed at 19:05:55Z. See `code46-release/`.

The separate unchanged Code47 f26d full SDK run ended with 3577 total /3556 passed /1 failed /20 skipped. Its one failure is the outdated time-anchor source contract; lint was not reached. See `code47-baseline/`. This is not final integrated Code47 verification.
