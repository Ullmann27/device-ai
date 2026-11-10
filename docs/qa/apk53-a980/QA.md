# APK53 QA draft — host gates passed, Android pending

Frozen source: `a980a19ed3ab44323009ae27eb299d322ff11679`  
Git tree: `a9d3da18e774ccc1d3c7cd61c493ad65928c4d2e`  
Source and integration [#739](https://github.com/Ullmann27/device-ai/pull/739)
are already published. This WIP evidence snapshot is on
`qa/apk53-a980-evidence-20261111`; the tested integration source stays frozen.

| Existing exact-source check | Observed result | Boundary |
|---|---:|---|
| Full Node | 2462/2493 passed; 31 skipped; 0 failed | Node 24.22.1, Linux x64; skipped is not passed |
| Critical subset | 136/136 passed | All 136 identities occur in full Node; **do not add** |
| Backend syntax | Exit 0 | Syntax gate |
| Backend tests | 64/64 passed | Host tests |
| Targeted Kotlin source-safety | 12/12 passed | Host gate, not Android compilation |
| Reference fidelity | Host gate PASS; 4 probes, 2 expected negative-control failures | Java2D metric only |
| Construct preview | 40 PNGs generated | Java2D stand-in |

All seven host commands exited 0; their start/end source is unchanged and the
recorded ending worktree status is clean. Counts are extracted from report
`checks` and log summaries. No grand total is calculated; prior donor totals
(such as 110 or 240) are not added.

**46 existing host PNG files** = 40 construct frames + 6 fidelity files
(4 candidate frames, 1 negative-control image, 1 contact sheet). They are
Java2D stand-ins, not native Android/Filament/Fold7 acceptance or measured
device FPS, GPU, energy or thermal performance.

Original avatar remains byte-identical to Code52 `17491f4d8103f0e55836369333fab07e80e6e9c2`:
SHA-256 `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`, **21 morphs**. Asset identity is not runtime proof.

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
from actual window closure. Older native test results are not promoted to
this final Android source.

## Still pending

| Gate | Status |
|---|---|
| Android compile, focused tests, full tests, lint | **PENDING** |
| ARM64 unsigned APK / code 53 | **PENDING** — no artifact hash or size yet |
| Original signing, publication, installation | **NOT_RUN** |
| Physical Fold7, hardware AGSL/Filament, live microphone, Gemma | **NOT_RUN** |

The queued pipeline is configured for this exact SHA. No Android pipeline
result is imported into this draft. The root must replace pending statuses
only from completed source-bound receipts; source changes invalidate this
draft's applicability to the changed code.

[QA.json](QA.json) contains compact counts, source/blob bindings and input
receipt SHA-256 values. [SKIPS.md](SKIPS.md) lists all 31 skipped technical
test names and deduplicated reasons, including the explicit Docker proof
flag and missing SDK/JVM/kernel/isolation prerequisites. No raw logs, case-hash
arrays, source comments or fixture/user payloads were copied. Preparation ran no tests,
builds or renders and made no repository edits or pushes.

## Live PR snapshot

The supplied `apk53-pr-inventory.json` contains 15 PRs. #741 and #742
auto-closed as merged through earlier parent ancestry; the other 13 remain
open. Donor branches are retained and no main merge occurred, as reported
by the integrator. `QA.json` retains the concise number/state/branch/head
mapping and hashes the supplied snapshot; PR closure is not source proof.
