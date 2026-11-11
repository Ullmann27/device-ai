# APK53 — local QA and unsigned package verified

Frozen runtime source: `a980a19ed3ab44323009ae27eb299d322ff11679`  
Git tree: `a9d3da18e774ccc1d3c7cd61c493ad65928c4d2e`  
Source [#739](https://github.com/Ullmann27/device-ai/pull/739) is published.
Evidence is maintained separately on `qa/apk53-a980-evidence-20261111`;
the runtime source remains unchanged. **Not signed or installable.**

| Exact-source check | Result | Counting / scope |
|---|---|---|
| Android compile | PASS | Local Android toolchain |
| Android full suite | **4464 PASS / 17 SKIP / 0 FAIL**, 4481 total, 783 XML suites | JVM/Robolectric; unfiltered |
| Focused camera tests | 11/11 PASS | Verified subset of full suite; do not add |
| Lint | PASS, 0 issues | **NewApi only**, not full lint coverage |
| ARM64 release assembly | PASS | Unsigned package |
| Full Node | 2462 PASS / 31 SKIP / 0 FAIL, 2493 total | Node 24.22.1 |
| Critical Node subset | 136/136 PASS | Verified subset; do not add |
| Backend syntax / tests | PASS / 64/64 PASS | Host checks |
| Targeted Kotlin source-safety | 12/12 PASS | Host checks |

All five Android phases and seven host gates exited 0 with exact-source
receipts. Skips are not passes. [SKIPS.md](SKIPS.md) enumerates **17 Android
and 31 Node skips**. All five skipped Android test classes are byte-identical
to Code52; XML records no reason. Required Cinema classes passed with no skips.
No cross-suite grand total or addition of older donor results is made.

## Verified unsigned APK

- Version **0.3.52 / code 53**, package `at.heinz.deviceai.continuumlocal`.
- **102588096 bytes**; SHA-256 `f138b92a99ea9aab67103490f0a249fd14adbc84c3c5346cd2169f36a40f1921`.
- ARM64 only, **5 native libraries**, min SDK **26**, target SDK **36**; not debuggable.
- ZIP CRC and **16 KiB zipalign PASS**; **34 packaged assets match source**.
- No JAR signature or APK signing block; expected missing-signature result.
  Status: **UNSIGNED_NOT_INSTALLABLE**.

The first validator attempt **FAILED** (`Unexpected minSdk`) because its
badging-key parser was wrong. The corrected validator **PASSED on identical
APK bytes/source**. Both receipts and validator hashes remain in `QA.json`.
The retained 25-case selfcheck belongs to the earlier validator, not the
corrected validator; it is not substituted for actual APK verification.

## Images and original avatar

**Six selected native PNGs** are hash-bound to a980a19: inner idle,
work 280/650/1180 ms, returned, and cover work 1180 ms. Provenance is
Robolectric API28 native Canvas, scripted lifecycle, existing **2D fallback**;
no live model, physical Fold7 or Filament GPU claim. Exact filenames/hashes
are in `QA.json` under `nativeImageEvidence`.

Separate **46 Java2D host files** remain: 40 construct frames plus 6 fidelity
files (4 candidate probes, 1 negative-control image, 1 contact sheet). The
host fidelity gate passed; two negative controls failed as expected. These
are not 46 independent device acceptance tests or performance measurements.

Original avatar SHA-256
`c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`,
**21 morphs**, byte-identical to protected Code52. Asset identity is not
hardware rendering acceptance.

## Incorporated sources

| Donor | Pinned source | Verification |
|---|---|---|
| [#715](https://github.com/Ullmann27/device-ai/pull/715) | `c972f56ccc4caa2f1807bcaf1d4665347c66c629` | Ancestor verified |
| [#725](https://github.com/Ullmann27/device-ai/pull/725) | `b0a1635b77f568692ac6ff6b662f52d6b6eb7231` | Ancestor verified |
| [#741](https://github.com/Ullmann27/device-ai/pull/741) | `e3ed6e984acb75b46c8646203cdf4c9b3f052623` | Ancestor verified |
| [#742](https://github.com/Ullmann27/device-ai/pull/742) | `0160da525f5de5a3763f67cb9f71e5c28db4543a` | Ancestor verified |
| [#743](https://github.com/Ullmann27/device-ai/pull/743) | `e739f45bc87d90058c10ea6ca5d2f1d81c2930ce` | Scoped files byte-identical |
| [#744](https://github.com/Ullmann27/device-ai/pull/744) | `59ee7593c012c6f54501e2f86a1db5b5b06fa258` | Scoped files byte-identical |
| [#745](https://github.com/Ullmann27/device-ai/pull/745) | `bbcab4d0a603c8f801c394cd7e0b70c05682cc01` | Scoped files byte-identical |

The scoped file comparisons for #743–745 are explicit in [QA.json](QA.json);
their PR state is not treated as integration proof. Additional frozen fixes:
`3908ec1` dialog references/order/ASR; `0e8f6c7` reading priority,
nonexecution feedback and controlled STOP; `7322436` accepted-but-not-started
feedback; `f495fd6` lazy-host test access; `e730f96` camera-return timing
from actual window closure. The final Android results below come from this frozen source, not older donor runs.


## Remaining acceptance and signing boundary

| Check / action | Status |
|---|---|
| Source-bound GitHub Actions QA | **IN_PROGRESS** — [run 42934541308](https://github.com/Ullmann27/device-ai/actions/runs/42934541308), workflow edff51d7; source a980a19 |
| Gitar / CodeRabbit review checks | SUCCESS / SKIPPED (draft); one known thread resolved |
| Code53 protected-signer binding and exact-source owner approval | **PENDING** |
| Original signing, release publication, installation | **NOT_RUN** |
| Fold7, hardware Filament/AGSL, live audio/model, device thermal/performance | **NOT_RUN** |

Review-check success is not build/CI acceptance. The protected signer remains
bound to **Code52**, QA branch `code52/grok-release-candidate-0351-20261110`,
workflow `.github/workflows/continuum-code52-cinematic-qa.yml`, event `push`,
and job `validate-source-and-build`. Code53 needs explicit source/QA/artifact
binding and owner approval while retaining the original certificate and
reviewer/branch guards. No signer modification or secret access is claimed.

[QA.json](QA.json) preserves source/intake bindings and compact receipt
hashes. The receipts describe completed local commands. Copying their results into
this evidence branch is not another test run. Source code remains frozen;
only evidence, its validator and a separate unsigned CI workflow are updated.
No fixture payloads, raw logs or secrets are copied.

## PR inventory snapshot

The supplied `apk53-pr-inventory.json` contains 15 PRs. #741 and #742
auto-closed as merged through earlier parent ancestry; the other 13 remain
open. Donor branches are retained and no main merge occurred, as reported
by the integrator. `QA.json` retains the concise number/state/branch/head
mapping and hashes the supplied snapshot; PR closure is not source proof.


## Evidence-runner review fix

The reproduction helper no longer writes a literal `None` proxy host/port when
no proxy is configured. Existing Gradle options are retained; empty HTTPS
configuration falls back to HTTP, default ports are explicit, and credentials
are not emitted. Eight targeted checks pass (six failed before the repair).
This changes the evidence runner only; the app and the running CI's build
commands are unchanged. The stale documentation-preparation block was removed.

## Original GLB pose replay

Eight selected poses from the passing a980 `AvatarMotionEvidenceTest` were
replayed on the unchanged original GLB with the existing repository Blender
renderer. All21 morph weights and head/neck/eye/jaw values match the exported
timeline. Canonical glTF skinning/control comparisons passed. See
[original-avatar/receipt.json](original-avatar/receipt.json) and the pinned
[reproduction command](original-avatar/replay-original-avatar.sh).

**Blender4.3.2 Cycles CPU,512×640,16samples,one thread.** Studio lighting/glow
is separate from Android/Filament. Scripted camera/TTS-word events are not live
perception or audio. No original asset or app source was changed. A Chromium
attempt failed at the host socket permission boundary; no browser-render pass
is claimed. The existing CPU renderer supplied the actual eight PNGs.
