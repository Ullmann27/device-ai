# Android System Intelligence — integration and acceptance

Canonical feature: [PR #735](https://github.com/Ullmann27/device-ai/pull/735),
`feature/android-system-intelligence-20261110`; target Cinema #715,
`cinema/fluid-light-stage-20261110`, verified base
`c1fdbaeab95d8f808715c180fbc77762e459a59c`.
Integration owner: this PR's root integrator. EXP-08/EXP-11.
This is an additional bounded prototype, not a complete phone optimizer or accepted
Fold7 release. No main merge, APK, signing, installation or publication.

## Connected behavior

`Continuum, analysiere mein Handy` reaches the local recognizer, existing ToolGate,
DeviceToolExecutor and Android APIs. Also accessible in Systemstatus → Diagnose &
Bereinigung. No model/network call is needed. The observation reads storage totals,
Android memory pressure, own PSS, battery/temperature/charging, supported thermal
status and own process CPU across an actual bounded 250-ms window. CPU means a
percentage of one core and may exceed 100%; it is not total-device CPU. Missing
values remain unavailable. RAM occupancy alone is not a fault. One observation
cannot identify the heat source or prove acceleration.

Seven typed tools reuse the registry, gate, receipt journal and executor:
diagnosis, capabilities, selected-file analysis, cache preview, explicit report
retention, approved cleanup and approved restoration. New tools also require the
exact locally authorized call ID; a model cannot mint its own approval. Pause/STOP
invalidate confirmation/result generations. Partial cleanup is not success. Prior
undo survives rejected cleanup and failed ownership-index restoration.

## Actual access comparison

| Function | A: ordinary Android | B: authorized Shizuku/ADB | C: root |
| --- | --- | --- | --- |
| Device, visible packages/version, storage, battery | Connected without Shizuku | Not required | Not required |
| Own PSS/CPU, thermal/pressure indicators | Connected; unknown explicit | Existing collector supports permitted extra observations | Not required |
| Foreign app storage / energy attribution | Not implemented here; never inferred | No blanket claim from dumpsys availability | Disabled |
| Sizes/content duplicates | Selected local documents; read grants checked | Shell path is not a SAF grant | Disabled |
| Cleanup | Owned numeric diagnostic reports only; exact approval | No foreign-app clearing added | Disabled |
| App launch / one-shot screen reading | Existing verified paths reused | Not required | Disabled |
| Free navigation, typing, messaging | Existing candidates remain unconnected | No autonomous input loop enabled | Disabled |
| Shell identity | Not applicable | Server/service UID and SDK verified; ordinary actions require UID 2000 | UID 0 detected, action mode off |

Full caller audit, priorities and official policy sources:
[access audit](ANDROID-SYSTEM-ACCESS-AUDIT-20261110.md).
No second permission authority, device engine, scheduler or hidden screen monitor.
Existing package and manifest permissions are unchanged.

## Storage and meaningful limits

The system picker selects at most 64 local documents; no persistent grant is taken.
The adapter accepts audited system-installed Android local document providers and
rechecks grants. Unknown/cloud providers fail closed. Largest files and verified
duplicate groups appear only in local UI. Tool outputs/receipts contain counts,
not selected names or URIs. Size/age is not evidence of non-use. No personal file
is deleted. SHA-256, EOF, unchanged metadata and bounded work establish duplicates;
oversized/incomplete reads stay unverified. A blocked provider cannot spawn an
unlimited number of replacements. See [storage contract](ANDROID-SYSTEM-STORAGE-20261110.md).

Cleanup does not guess that temporary suffixes, model caches, render data, APKs,
memories, backups or unknown files are disposable. Existing render caches were
in memory; native-model-cache ownership was not safe to infer. Only actual numeric
diagnostic snapshots explicitly saved by the owner are eligible, max 32 records.
Empty caches have zero savings; no artificial cleaning workload is generated.
This small cleanup is a safety prototype, not a large-space or speedup promise.
The preview binds ID/hash/size/inode/expiry; secure relative deletion is required.
Unsupported filesystems refuse deletion. Undo lasts ten minutes in the live
process. Unexpected entries are preserved for review. Failed ledger restoration
retains bytes and identity for retry, without overwriting replacement files.

## Measurements and Cinema

Before/after snapshots use SystemPerformanceEvaluator. Unknown build/warm/background
conditions produce NOT_COMPARABLE even if byte counts differ. Median/nearest-rank
p95 use actual supplied samples; failed, missing, duplicated or cancelled benchmark
samples cannot become fast zeroes. Regressions remain visible. A gain is exploratory,
not causal proof. The existing Fold7 lab has a read-only collector binding APK/OS
hashes, battery/charging/temperature/thermal conditions, PSS and fresh gfxinfo frames.
App-start, UI-action and model-task timings remain NOT_INSTRUMENTED there. No app
JSON import is added. Reproduction: [performance contract](ANDROID-SYSTEM-PERFORMANCE-20261110.md).

The new panel uses existing Cinema STATUS scenes, tool events, original avatar and
orb. Its native Canvas surface projects a phone schematic and snapshot fields;
unmeasured fields stay explicit. This is a 2.5D data surface, not a new Filament
engine. Native Robolectric renders use synthetic numeric fixtures and the existing
2D avatar fallback; they establish no Fold7 graphics, GPU or model performance.

## Privacy and review decisions

The cloud conversation envelope no longer silently includes device model/maker,
SDK/build or Shizuku readiness. Cloud-dispatched tools are limited to existing
web_search/web_read, under existing internet consent. This intentionally also
blocks older device tools in that cloud path: there is no separate informed
system-data-transfer consent. Local device paths remain available. Allowing old
device tools through by default would violate the requested no-hidden-transfer
boundary. An eventual explicit scoped transfer approval must reuse the same gate.

Review checks cover actual charging versus plugged power, late provider completion,
stale confirmations, partial results, exact hash budgets and retained undo on index
failure. STOP tests are aligned with the existing Cinema contract from baseline
`468a479`: speech/work stop immediately; camera/window return animates. Pause,
focus loss and reduced motion still reset immediately. Production animation is
unchanged. The red integration result is retained rather than overwritten.

## Verification checkpoint

Android main and complete unit-test source compilation passed at local
`27039079f509add2faca22400b1ab4bb3a320f9f`, published tree-equivalent
`b3e59e05155d16e3eb151d145ac59b6c67f834fd`.
Initial integrated native run: 152 tests, 148 passed, four failed: two obsolete
STOP expectations and two new render fixtures missing Cinema-shell setup.
Corrections are committed; the next run must validate them. Scoped Node wiring:
26/26 PASS. Python lab: 60/60 PASS, mocked device I/O. Storage follow-up: 40/40
PASS after five reproduced failures. Source-bound evidence:
`docs/evidence/android-system-{access,storage,performance,intelligence}-20261110`.

| Target | Actual status |
| --- | --- |
| Kotlin policy/storage/statistics | Host tests, synthetic inputs |
| Android API/wiring/bridge lifecycle | Robolectric API28, no real Shizuku |
| Diagnosis visual | Native Canvas fixture; corrected capture pending |
| Physical Fold7 / Android17 / actual grants | NOT_RUN; adb lists no connected device |
| Physical thermal/latency/storage improvement | NOT_RUN; no improvement claimed |
| Root, personal-file deletion, messages/payments | NOT_RUN; not enabled |
| APK/signing/install/release | NOT_RUN; not authorized here |

## Research, contributors, next gates

- #721 HEAD `340bfb0972db1bbc76ef4fb7d0afeb00fdf45bce`: REJECT_PROMOTION,
  six context cases remain. Not imported; OMEGA context stays off.
- #727 HEAD `ff27033e36e2e6dfa552c570a0bea0f8f57fc386`: scoped host fixes,
  full Android compilation unresolved. Not imported/recommended for production.
- Support PRs #736 access, #737 measurements, #738 storage stay open. Completed
  sources are integrated without overwriting their branches.

Next: finish exact-source host/render checks, then separately authorized Fold7
validation of real Shizuku reconnect/revocation, local providers, secure-delete
support/undo, lifecycle, cover/inner display and controlled A-B-B-A observations.
Optional Usage Access statistics and narrow verified-node actions remain later
work. General autonomous Accessibility control is not claimed Play-compliant;
see official sources in the access audit. Root actions stay unimplemented/off.
Rollback: revert this feature on its development branch. No memory, weights,
signer, package, active research default or system setting is migrated.
PORTIERT: yes; VERIFIZIERT: scoped host, corrective run pending;
GERÄTEBESTÄTIGT: no. Branches remain open.
