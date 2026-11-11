# Thermal repair — 11 November 2026

## Scope and integration ownership

The owner requested exclusive investigation and repair of excessive heat during
local model loading and questions, with exactly three helpers. This change removes
proven redundant work while preserving model quality, context, features, configured
throughput and visible rendering. Existing Android thermal protection stays intact.

- Owner of integration: thermal coordinator, PR #773.
- Canonical target: `integrate/omega-apk54-20261111`, PR #769.
- Starting baseline: `f88d7d1936b9c0d53c176a0af31108fb863199e6`.
- Canonical target with integrated repair: `c36258a853bbc6dacd8fbfa0dbae64f0f95a0d14`.
- Android-tested published source: `308bccfd7932f0519a6cede78a20b88f36e839b1`.
- Complete `app/src` tree in tested and canonical source: `9299568f4aa98a563347adb5e377878a7a00a658`.
- Thermal branch: `fix/thermal-root-20261111`.
- Installed Code53 source reported in #670:
  `050a57cb019dcc1b7fe6cfa2fb5c11176b048e05`.
- Issue #569 remains the physical thermal acceptance issue.

Helpers: inference #774, rendering #775, background #777. Each has source-bound
red/green evidence and its own handoff. The coordinator reviewed the Activity call
paths, combined all three repairs and checked their interaction. No fourth helper
or cascading delegation was used.

## Accepted repairs

### 1. Verify model bytes once per load

`LiteRtLmLocalModelRuntime.load()` previously called `catalog.get()` followed by
`catalog.modelFile(record)`, which called `get()` again. Both traversed the complete
file for SHA-256 verification. The new synchronized `sourceForLoad()` returns the
record and the already verified path under the same catalog monitor.

Every load still hashes every byte, validates identity and length, and performs
the unchanged native capability inspection. There is no verification cache across
loads. A test provider delegates to real SHA-256 and counts actual processed bytes:
two full scans become one; two loads change from four scans to two. A 3,659,530,240-byte
model therefore loses one duplicate 3,659,530,240-byte scan per load. This does not
remove computation from later queries while the model remains loaded.

### 2. Reuse unchanged status evaluations

The five-second dashboard collector calls `ContinuumLearningCore.status()` through
new core instances. Previously each status decoded unchanged SWB1 weights and ran
eight intent plus six semantic evaluation examples again. A single bounded,
synchronized cache now binds the immutable measurements to exact checkpoint bytes
and both evaluation corpora. Experiences, statistics and the last audit remain fresh.

Changed or corrupt weights cannot reuse a previous good result. Recovery, missing
weights, cross-directory statistics and concurrent initialization were checked
independently. The cache still reads the checkpoint bytes; it does not trust file
mtime or length. In the synthetic 1,000-call repetition, evaluation objects fall
from 1,000 to one with the same result checksum. Timings are noisy host observations,
not a measured Fold7 energy or temperature improvement.

### 3. Remove repeated work in the visible light effects

`JarvisEnergyFlows` recalculated identical drifting control points for every point
and pulse of each line. The control points are now computed once per flow and frame,
using the same arithmetic. Actual instrumented `Math.sin` calls in this helper fall
from 1,526 to 70 per drawing invocation. The full recorded drawing-command stream
is bit-identical across 72 scenarios.

`JarvisHeadAura.drawLightStreams` reuses its two static paths and gradient while the
exact floating-point viewport size stays unchanged. Fractional size changes and
fold/resize returns recreate correct geometry. The cache has one bounded entry.
No color, opacity, path, pulse, resolution or frame-rate setting was reduced.

## Verification

| Evidence | Actual result |
|---|---|
| Inference regression before fix | 5 cases: 3 pass, 2 fail |
| Inference focused production compile and neighbors | 35/35 pass |
| Background regression before fix | 5 cases: 3 pass, 2 fail |
| Background core/encoder/reuse tests | 27/27 pass |
| Independent background corruption/recovery/cache review | 5/5 pass |
| Renderer regression before fix | 10 cases: 9 pass, 1 fail |
| Renderer focused tests after fix | 10/10 pass |
| Combined JVM production compile and 16 test classes | 94/94 pass |
| Combined production drawing probe | 72 scenarios; maximum 70 sin calls; baseline digest matches |
| Six neighboring Node source-contract files | 34/34 pass |
| Independent final five-file production review | no blocking findings |
| Android production and all test-source compilation | PASS |
| Focused Android `testDebugUnitTest` | 182/182 pass; 31 classes, zero failures/errors/skips |
| Configured NewApi `lintDebug` | separate run executing; offline dependency failure retained |
| Native Gemma/Fold7 temperature, energy, latency and visual acceptance | NOT_RUN |

The old Node load contract expected direct `catalog.get()` in the runtime. Its
failure was reproduced, then updated to follow the actual verified resolver.
Six negative mutants are rejected, including disabled byte verification and a
reintroduced duplicate scan. No assertion or security gate was removed.

The first Android run compiled production successfully but rejected the new test
observer's JDK-only string-version `Provider` constructor. Its version argument is
now the Android-compatible double `1.0`. This is a one-line test helper correction,
with no assertion or production change. The original failed log is preserved as
`android-initial.log`. The regular Android recheck passed all 182 selected tests.
Its subsequent lint phase stopped because `lint-gradle:31.10.0` was absent from the
offline cache. The official Google Maven POM is reachable through normal inherited
proxy settings. A separate online lint run is executing under the same build lock.
Raw XML and the complete recheck log are preserved; the combined command exited
with code 1 for the lint dependency failure, not for failed tests.

Combined JVM source identity was `b1d7e7df90e56e0b4db2c131041fb9e6a7c16174`, tree
`903bca2e718caabe2846bfa76493493cedf10dc3`. The subsequent target merge changed none
of those compiled source/test/probe bytes. The dependency cache is explicitly
identified; focused JVM results are not a complete Android/native build result.

Raw logs, source hashes, runners and combined summary are in
`docs/evidence/thermal-root-20261111/`. Component red/green evidence remains in the
three sibling `thermal-{inference,render,background}-20261111` directories.
Verbatim raw logs retain their original trailing blank lines.

## Audited paths without a proven additional defect

The inspected model host is process-wide and admits one generation while ready.
No second native engine or infinite Kotlin busy loop was established. Used native
conversation reuse would change prompt isolation and was not introduced.
Filament has no additional autonomous frame loop in the inspected path.

Advisory reasoning can perform additional bounded model work. A possible later
prompt-budget rejection does not prove that path is reachable with the earlier
context-size guard. Moving it would also affect consent/history freshness. No
speculative routing change was made. PDF surface invalidation and a possible
training cancellation lifecycle race remain unproven candidates, not claimed
thermal causes or repaired behavior.

The main contributor to severe heat during an already-loaded query remains
unmeasured. CPU/GPU attribution and a native backend change require real Fold7
measurements. The old CPU attribution comment is not evidence.

## Acceptance boundary and next executable step

This is a repair of proven unnecessary work, not a claim that the complete physical
heat problem is resolved. No watts, degrees, battery-life percentage, tokens/second
or Fold7 latency improvement has been measured. Model weights, backend, threads,
context/token settings and thermal safeguards are unchanged by the thermal delta.

The Android run uses the existing local JDK21/SDK/Gradle runner and the shared
`/tmp/continuum-omega-gradle.lock`. The wrapper's missing offline distribution was
an environment failure, recovered by using the existing installed Gradle 8.11.1.
One initial competing launch was stopped before compilation; the replacement waited
for the shared lock and then started. No GitHub Actions or paid runner was invoked.

For physical acceptance, use the existing read-only
`tools/fold7_quality_lab/fold7.py` and `system_capture.py` on an authorized Fold7
connection. No ADB channel is available here. Compare baseline and candidate on
the same original model and fixed prompts, matched starting battery temperature,
ambient conditions, brightness, fold position and charging state. Separate cold
loading, warm repeated queries, idle animation and STOP/background transitions.
Record the real model timings, thermal status, battery temperature and frame times;
the battery sensor is not a CPU/GPU temperature measurement. Do not stress an
already hot device. A signed/installable update and its device measurements remain
separate from this source repair; no installation or device PASS is claimed.

Rollback: revert the five-file thermal production delta together with its source
contract update. No user-data or model migration is required. Preserve the raw
regression evidence and keep Android thermal guards enabled.

## Canonical equivalence after Android execution

PR #773 was merged into #769 while the thermal tests ran. Canonical `c36258a8`
also contains the identical Android-compatible test constructor. All `app/src`
bytes (production and tests) match the tested public source `308bccfd`; the source
subtree SHA is recorded above and in `android-tests.json`. No new production or
test change is included in the evidence follow-up. The focused 182-case result
does not replace the wider integrator suite or physical Fold7 acceptance.
