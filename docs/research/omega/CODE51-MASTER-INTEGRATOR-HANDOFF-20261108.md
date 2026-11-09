# Code 51 master integrator

Branch: `copilot/code51-master-integrator-20261108`.
Base: #658, `f153758dfd19d1c12f6fe63b6c4c4bd1da95e4cb`.
Remote audit: 2026-11-08. Draft PR: [#663](https://github.com/Ullmann27/device-ai/pull/663).
Frozen source under regression: `b3e288f79d3bce6da23dbf5a498d50811a246358`.
WIP until the acceptance gates below are satisfied.
No main merge, signing, APK publication, device installation or research promotion.

## Source audit

| PR | Audited remote HEAD at freeze | Decision |
|---|---|---|
| #646 | `f3f90736f4addb027dcc4f42a1f3cd3300713d3b` | Already an ancestor; repair three open speech reviews in place |
| #650 | `82590f532d0b070823d82e6007f53e5e825cf70f` | Already an ancestor; preserve bounded tools and result evidence |
| #653 | `cb11db2f967110680da92db8c0b07f01f3d2b47d` | Already an ancestor, including H1-H3 and #651; do not merge again |
| #658 | `f153758dfd19d1c12f6fe63b6c4c4bd1da95e4cb` | Canonical base: navigation, research lifecycle and whole-text memory admission |
| #659 | `76b26e227e898f375820b872d674859016e6bad2` | Integrated through reviewed `839017298f78d825d82118f359c5d56874bfae57`; later avatar/orb lighting change deferred, not covered by this freeze |
| #661 | `58c545aaefefb233371c92948ad1a9a002c7c004` | Measurement tooling only; repair hue label and metric documentation before new evidence |
| #655 | `c90f020b8db929ae9a53e2cd95c39419497281b4` | H4-A excluded; independently test H4-B1 normalization with prefix-collision negatives |
| #660 | `26b0ebc5a4b194e837f706784b6810052f592a86` | Research/evaluation only; no new model capability |
| #662 | `6b299c33efc2e334661db4c92710753f802ac8a7` | Initial `350ceaf2` was docs-only; real text-team implementation now exists at `18d8a429`, followed by three route-label test corrections. Separate checkpoint below; not imported |

`git merge-base` confirms #659/#661 branch from #653, #655 from #649,
#660 from Code50 #630, and #662 directly from #658. The H4-B1 implementation
commit `3ab39fcd41b0d0015197bdbab6e22b76a1e424da` changes two production
files; `SituationReturnMorphologyTest.kt` originates in earlier commit
`790e91adf` (not in that implementation commit). No whole research-branch merge.

Relevant contracts: EXP-05 local model, EXP-06 provenance/context, EXP-11
existing delegation, EXP-12 lifecycle, EXP-16 discourse research. Preserve
ToolGate, owner consent, private bot contexts and the single rendering budget.
Later remote observations (#659 `6d392e3efc38feaf12debb91dc1b6149ee5e43aa`,
#662 `5a0fd0a8e1bc2cd098693a62e766f3be19d23429`) are recorded separately in the
inventory. They were not silently imported into or certified by this freeze.

Full per-PR changed-file/status inventories, exact merge bases, ancestry and the
candidate's actual changed files are in
[`source-inventory.json`](evidence/code51-master-integrator-20261108/source-inventory.json).
These describe source changes, not runtime activation.

## Functional integration matrix

Paths below are under `app/src/main/java/at/heinz/deviceai/` unless stated
otherwise; the inventory contains complete paths and full SHAs.

| Function / origin | Actual production path and changed surfaces | Tests, overlap, decision |
|---|---|---|
| Speech #646 `f3f90736` | `AgentActivity` -> `ContinuousConversationMode` -> Android ASR/TTS and `LiteRtLmLocalModelRuntime`; successful unload clears thread diagnostics | Recovery schedule/pause tests and Node lifecycle contract. Repair in `c8d5d0b7`; failed native close still retains ownership. No new audio subsystem |
| Internal navigation #654 `c61bf171`, reconciled #658 | `sendCurrentInput` -> `OwnerInternalNavigationIntent` -> existing work-area route; `OwnerAppLookupIntent` excludes internal routes | Navigation intent/integration tests cover file names, compound actions, negation, ASR and existing route keys. Preserve single recognizer; no duplicate SOL/Fable router |
| PDF/files #650 `82590f53` | `OwnerDocumentIntent` -> `openOrReuseDocument` -> existing picker/read/draft lane before navigation; grants remain Android owner-selected URIs | Document intake/context and ToolGate tests; real Android picker/read/write NOT_RUN. Preserve, no invented opened/saved file |
| Research #656 `1bbdab1a`, #658 `d1fb060d` | `OwnerResearchCommand` -> `ApprovedWebToolGate` -> `WebToolRequest`/`DeepResearchEngine`; foreground/session/consent guards around publication | Single-entry, approval integration, transport and stale-dialog tests; OFF/ASK/ALWAYS_ON, STOP and revoked results covered with controlled providers. Preserve canonical path, no live-network result claim |
| Facts/instructions #657 `b1117df1`, repaired #658 `b557652e` | `BootstrapBrain` checks complete input before `OwnerFactMetaSeparator`; existing memory/neural admission remains authoritative | Whole-input sensitive/length negatives and neural teaching tests. Preserve repair; never revert only whole-input admission while keeping separation |
| Memory/context #647-649, #658 | `AgentActivity.runLocal` -> `AiContextCompiler` -> existing `HybridMemoryRetriever`; same-session situation is navigation, never a fact | Context, provenance, privacy, withdrawal, contradiction and session tests. No second memory DB; H2/H3 research diagnostics/reuse are not general intelligence or automatic learning |
| H4-B1 #655 `3ab39fcd` | Shared normalization in `ConversationSituationModel`/`LocalConversationSituationTracker`; already called via `LocalConversationContext.situationHint` | Original candidate rejected by 3 independent negatives; constrained repair in `001ece73`, 20 focused passes. H4-A and benchmark claims excluded |
| Tool truthfulness #650 `82590f53` | `runLocal` -> `BoundedAgentOrchestrator` -> existing `createToolGate`/receipt store -> outcome guard | Denied/missing tools, partial effects, STOP, approvals and replay tests. No success based solely on model prose or form-only evaluator |
| Graphics #651 ancestor, #659 through `83901729` | `JarvisWorkWindowHost` -> shell motion observer -> `JarvisWorkWindow` -> overlay `present`; shared budget and protected geometry | Pure material/cinematic tests; runner import and close timing corrected in widget tests (Android execution blocked). No independent render loop; original GLB/orb/seven worlds retained |
| Reference measurement #661 `58c545aa` | Developer preview/measurement scripts only; no app/runtime authority | Seven synthetic metric checks and two negative image controls. Hue label/measurement definition fixed; one-sided gate is not visual equivalence or Fold7 proof |
| Existing bots #658 | Existing 20 profiles, private conversation/task intake, `runLocal`, bounded ToolGate and CCC1 draft path remain | Existing Chief/store/handoff/workspace tests run with combined suite. Mere `ChiefDelegationCoordinator`/`ChiefWorkerPool` availability is not owner-selected persistent team activation |
| New team #662 `18d8a429`, tests `6b299c33` | Separate proposed roster -> `ChiefBotTeam` -> existing coordinator/pool/`ChiefJobs` -> shared local generation; serial ANALYZE then REVIEW, zero tools | Three upstream review findings repaired (handoff, rejected-result terminal state, explicit history deletion). Full frozen upstream regression still pending when inspected; defer from release candidate |
| Genesis #660 `26b0ebc5` | Research tasks/evaluators, no newly trained runtime skill | Leave research-only. No whole-branch merge, trained-capability or intelligence-gain claim |

## Initial environment and current Android blocker

At entry all new integration work was `NOT_RUN`. The earlier 3578 JVM / 202 Node report
belongs to `d1fb060d95a4207191a665b91f90a208ad941cae`, not this candidate.

Commands actually attempted on the unchanged base:

```sh
bash scripts/android-kotlin-unit-check.sh <output>
./gradlew --no-daemon --console=plain \
  -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true \
  -PcontinuumStandaloneVersionCode=51 -PcontinuumOwnerArm64=true \
  :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug
```

Initially the host runner was `BLOCKED` (Kotlin and dependency jars absent).
Gradle wrapper 8.11.1 downloaded from its configured official source;
Gradle failed before app tasks on installed JDK 27 (`What went wrong: 27`).
JDK21 was then restored from Ubuntu packages, Kotlin2.2.21 from its official
JetBrains release (SHA-256 verified), and pinned host dependencies from Maven
Central (publisher digests verified). Focused host tests now run.

Actual Gradle retry on JDK21, repeated on frozen `b3e288f7`, remains `BLOCKED`:
AGP `com.android.application:8.10.0` cannot resolve; `dl.google.com` DNS is
unavailable. The command above additionally used `-PcontinuumUiWidgetTests=true`.
No app Gradle task started. SDK/adb/emulator remain unavailable.
Both existing authorized Render QA `/build.json` endpoints
(`continuum-m8-android-qa-20261031.onrender.com`,
`continuum-unified-research-apk-20261102.onrender.com`) fail DNS; no Render
management credential is configured. No service reconfiguration attempted.
Android compile/unit/lint/UI: `BLOCKED`; APK, original signing, installation,
Fold7/Android17/ARM64 verification: `NOT_RUN`. No untrusted artifact mirror or
GitHub-Actions/signing workaround.

## Resume and rollback

The bounded repairs, graphics integration, H4-B1 gate and host regressions are
complete below. Resume with actual Android Gradle/widget/device acceptance on
an authorized working SDK host, then a separately pinned combined #662 and
late-graphics checkpoint. The Code50-only GitHub Actions/signing exception does
not apply. Re-read upstream heads before new work; do not extend this freeze
by inheriting later branches' test claims.

Commit each independent repair/integration separately and verify its remote
SHA. Roll back with a new revert commit of the affected checkpoint, never
reset/force-push another team's branch or discard existing bot/memory data.
Persistent owner-selected chief/two-specialist execution is not imported in
this candidate and is not claimed as device-verified.

## Speech review repair

The existing runtime clears `computeThreads` together with `loadedModel` only
after a successful native close; failed closes still retain owned resources.
ASR retries now match the documented post-increment 800/800/1600 ms schedule.
The Activity uses the existing conversation policy for failure-specific pause
text. `resetRecognizer()` destroys the pooled recognizer, so the message says
the next start creates it anew, not that a restart already succeeded.

RED on base implementation: 32 focused JUnit tests, 1 failure (second retry
1600 instead of 800 ms); Node lifecycle regression detects missing diagnostic
reset. GREEN and exact source SHA are recorded at the next acceptance checkpoint.
No audio, native inference or device behavior is proved by these host tests.

Verified speech source: `c8d5d0b7de6348525e5bc844051088b79f13ab77`
(`SOURCE_INTEGRATED`, `HOST_TESTED`): 33 focused JUnit and 19 Node checks.

## Graphics and measurement integration

Imported #659's three commits through `452591edd31215fecea7a3110036157a7c1ed438`
onto #658 without replaying #651. The canonical `JarvisWorkWindowHost` connects
the native window's existing shell clock to `HollywoodFuiStageOverlay.present`.
`JarvisShellView` applies the shared motion/thermal budget and cadence.
The overlay sits below native content and clips protected avatar/input bounds.
Original avatar, orb, seven world profiles and event sources are unchanged.
`JarvisCinematicSettings` provides a reversible presentation-only fallback.

Fixed the missing `RobolectricTestRunner` import. Updated two old close-lifecycle
assertions to the intentional first-supplied-frame start and full 420 ms close;
they still require deferred hierarchy cleanup and exactly-once callbacks.
UI execution remains `NOT_RUN`, not passed by the pure painter tests.

From #661 only the measurement tool/script and corrected documentation are
imported, not its old images/JSON or obsolete #653 preview stack.
The complete V2 preview shares `paintConstruct` with measurement; all **40/40**
PNG files are byte-identical before/after extraction. Synthetic checks freeze
the actual HSV-value threshold and 180-degree control. JSON now says
`hue-rotated-180`; documentation matches edge snapping, denominator floors and
weighted RGB (not physical linear-light luminance).

Graphics integration commit:
[`72f2a690a96a7597a91a3038ee567d1cae9d677a`](https://github.com/Ullmann27/device-ai/commit/72f2a690a96a7597a91a3038ee567d1cae9d677a).
After the isolated upstream follow-up, all 19 graphics tests were rerun as part
of both the full suite and the 44-test focused selection on
[`b3e288f79d3bce6da23dbf5a498d50811a246358`](https://github.com/Ullmann27/device-ai/commit/b3e288f79d3bce6da23dbf5a498d50811a246358).
The measurement was also regenerated on clean `b3e288f7`; checked-in
`graphics-provenance.txt` records the exact source hashes.

Focused graphics: **19 JUnit PASS**, **7 metric checks PASS**. Fresh one-sided
reference gate PASS with both negative controls failing as intended. The
fold-settled sample has rimL 0.2871, interiorL 0.0455, rimContrast 6.3126 and
litRimShare 0.6790. This is **not reference equivalence**: interior remains
too dark and contrast/cyan share too high versus the design reference.
Viewed fold 760/2400 ms and phone low-budget frames: separate cyan/violet rims,
subtle diagonal reflection, no persistent source beam; native content is only
a placeholder. Java2D shear is not Android Camera, Filament or Fold7 evidence.

Commands:

```sh
bash scripts/hollywood-fui-cinematics-preview.sh <external-output>
bash scripts/hollywood-fui-reference-fidelity.sh <external-output> code51-v2
```

JDK21/Kotlin2.2.21; use the actual production UI sources plus
`HollywoodFuiConstructTest`, `HollywoodFuiCinematicsTest`,
`HollywoodFuiMaterialRegressionTest` with JUnit4.13.2. The complete-source
runner and SHA-bound evidence are recorded below. Rollback graphics independently of
speech; the runtime switch disables film layers without changing tool grants.

## H4-B1 independent gate

The exact two-file candidate from `3ab39fcd41b0d0015197bdbab6e22b76a1e424da`
plus upstream morphology tests was tested independently. New negatives produced
**3 failures / 19 tests**: Arbeit bound Arbeitsamt, long compounds collapsed
into the same ten-character topic, and oversized words collided after truncation.
The original candidate therefore is **not accepted unchanged**.

The constrained integration keeps shared umlaut folding and complete words up
to 80 characters (overlength words are omitted, never truncated into another
topic). Only `e/en/er/es/n/s` suffixes on stems of at least six characters may
match; arbitrary longer compounds cannot. Positive compound/ASR/plural,
ambiguity, explicit-return and reset cases plus the new bidirectional collision
negatives pass: **20/20 focused JUnit**. This is a conservative heuristic, not
general German semantic understanding; upstream H4 scores do not apply.

Productive path already exists:
`AgentActivity.runLocal -> LocalConversationContext.situationHint ->
LocalConversationSituationTracker -> ConversationSituationModel`, frozen once
per model turn and appended only as navigation. Bot/coding contexts are excluded.
`AiContextCompiler -> HybridMemoryRetriever` and owner-fact/privacy gates are
unchanged. Added actual `LocalConversationContext` tests verify the hint does
not alter owner evidence and disappears on clear/exit/session/topic/correction.
Those broader tests passed in the complete host runner.
H4-A, research scorers/certificates and Genesis remain excluded.

Update: constrained H4 integration
[`001ece73d8d78970b222473a644fe93ad1999299`](https://github.com/Ullmann27/device-ai/commit/001ece73d8d78970b222473a644fe93ad1999299)
is now covered on frozen `b3e288f7` by the complete 3600-test run and a fresh
44-test selection: 19 graphics, 20 situation/morphology and 5 real context
invalidation tests. The earlier pending context status is resolved.

## Separate Bot Universe checkpoint

The entry observation (documentation only at `350ceaf2`) is historical, not a
claim about today's branch. Inspected implementation:
`18d8a42935f966a80f4599dc58e720fbe0223db7`; latest inspected test-only follow-up:
`6b299c33efc2e334661db4c92710753f802ac8a7`.
Coordination comments were posted on #662 and #659; their branches were not edited.

The proposal reuses `ChiefBotProfileStore`, `ChiefDelegationCoordinator`,
`ChiefWorkerPool`, `ChiefJobs` and the existing roster/model lane. It adds a
bounded checksummed task file rather than replacing private profiles/dialogues.
Owner selection binds chief, two distinct specialists, profile versions, team
revision, request and model hash. Restart marks active tasks INTERRUPTED; there
is no automatic replay. History deletion is explicit and revokes old drafts.
Optional approved instructions use the existing skill library and do not grant
tools. UI labels model output as unverified. Coding still opens a CCC1 draft,
not an executing coding agent or an independent verifier.

Decision: **defer implementation from this frozen candidate**, retain as the
next separate integration checkpoint. Upstream's full frozen main/test/Node
validation was still pending (including newly corrected route-label tests);
its reported focused fixtures do not prove Android lifecycle, real shared-model
two-stage execution, restart or selected-bot coding. No substitute architecture
was created. Next gate: pin completed upstream evidence, run combined host and
widget regressions, then exercise the real native model lane/STOP on authorized
Android QA. Existing 20 profiles, private contexts and owner data remain intact.

Independent check performed here: compile the five changed Chief core files
and three new/updated test files from exact `6b299c33` against the completely
compiled `b3e288f7` main/stub module, then run `ChiefBotTeamTest`,
`ChiefBotTeamSkillsTest`, `ChiefEngineeringPracticeCurriculumTest` as `nobody`.
**33/33 PASS**, persisted in `bot-overlay-junit.txt`. This is a bounded core
overlay check, not full #662 Activity/widget/native-model acceptance.

## Frozen acceptance and reproducibility

Tested source:
[`b3e288f79d3bce6da23dbf5a498d50811a246358`](https://github.com/Ullmann27/device-ai/commit/b3e288f79d3bce6da23dbf5a498d50811a246358).
Subsequent commits only add documentation/evidence.
`tested-source-trees.json` records unchanged app, scripts, tools, backend,
assets, GitHub contracts and root build configuration identities.

| Gate | Exact result on the frozen source |
|---|---|
| `SOURCE_INTEGRATED` | Speech repairs, bounded #659 graphics, repaired #661 tooling and constrained H4-B1; independent commits below |
| `HOST_TESTED` Kotlin | Complete main/test source compilation; **637 JUnit classes / 3600 tests PASS**; four executable Checks PASS, one no-main helper not executable |
| `HOST_TESTED` focused | **44/44 PASS** on compiled frozen source; not additional unique tests beyond the full 3600 |
| `HOST_TESTED` Node | **2503 discovered: 2480 PASS, 0 FAIL, 0 cancelled, 23 skipped**. Supplements below subsequently execute seven of those skipped cases; **16 remain NOT_RUN** |
| `HOST_TESTED` backend | **64/64 PASS**, Node24.22.2, locked dependencies |
| `HOST_TESTED` graphics | 7 metric assertions, four probe samples and two rejecting controls; one-sided envelope only |
| `HOST_TESTED` original asset | Original GLB SHA-256 `63b42c7786edadd86de22bc2afbae8638e1c61b46da887838cdaa19a34b439ca`; 62 structural assertions, Khronos 0 errors/0 warnings |
| `ANDROID_BUILD_VERIFIED` | **BLOCKED**, actual compile/unit/UI/lint invocation stops at AGP resolution |
| `DEVICE_VERIFIED` | **NOT_RUN**, no Fold7/Android17/ARM64, native model/audio/thermal/FPS or physical restart evidence |
| Signing / APK / release | **NOT_RUN**, no owner approval or authorized working channel for Code51; no main merge |

Primary logs and comparison images:
[`evidence/code51-master-integrator-20261108/`](evidence/code51-master-integrator-20261108/).
No model weights, owner dialogues, credentials or signing material are included.

The three real-Kotlin oracle suites subsequently pass **11/11**, including six
previously skipped RED/GREEN/compiler/independent-output cases. Real JVM export
from the frozen compiled graph into the existing Node adapter passes **9/9**,
including one previously skipped cross-language byte-identity case. Do not add
all 20 to the full-sweep pass count: 13 repeat already-passed tests. Combined
coverage is 2487 passing canonical Node cases and 16 still unexecuted, plus
the separate 64 backend cases.

The 16 remaining cases need separately provisioned Docker/CCC1 workspaces,
approved Harness SDK, private cgroup resources or the older standalone
shell/cognitive subset compiler harness. No unavailable native/model/device
capability is inferred from their skip. The broad exploratory `tools/*` sweep
also hit specialized external-review prerequisites and historical fixed
workspace paths; these remain outside canonical acceptance, with failures
retained in `node-exploratory-attempts.json`.

### Host prerequisites and commands

Use an unprivileged user with a writable checkout/output directory and UTF-8
locale. The actual run used an independent local clone of `b3e288f7`, not
mutable sources or the root user's read-only-permission bypass. Toolchain:
Ubuntu OpenJDK21.0.12.1, official Kotlin2.2.21, official Node24.22.2.
Kotlin archive SHA-256:
`a623871f1cd9c938946948b70ef9170879f0758043885bbd30c32f024e511714`.
Node `node-v24.22.2-linux-x64.tar.xz` SHA-256:
`48cc0e21e048790e54c0cf5749c2725005313d652651c40b7a21feb9697aaf75`.
Official origins: JetBrains/kotlin release `v2.2.21`, `nodejs.org/dist/v24.22.2`,
and `repo.maven.apache.org/maven2`. Publisher checksums were verified.

Set `DEPS` to official Maven artifacts with the names expected by
`scripts/android-kotlin-compile-check.sh`: android-all15-robolectric-13954326,
OkHttp4.12.0, Okio3.6.0, Shizuku api/aidl/shared/provider13.1.5,
Filament/gltfio1.56.0, OpenCV4.13.0, JUnit4.13.2, Hamcrest1.3.
For AARs extract their `classes.jar`; no native model implementation is supplied.
[`host-dependency-sha256.txt`](evidence/code51-master-integrator-20261108/host-dependency-sha256.txt)
pins every jar actually used. Put the installed Kotlin/JDK/Node bins on PATH.
The existing LiteRT compile-only stand-in throws rather than claiming inference.

Actual runner commands (paths parameterized; run from the exact clean checkout):

```sh
export LC_ALL=C.UTF-8 JAVA_OPTS=-Xmx3g
export CONTINUUM_ANDROID_DEPS="$DEPS"
export CONTINUUM_JUNIT_JARS="$DEPS/junit.jar:$DEPS/hamcrest.jar"
bash scripts/android-kotlin-unit-check.sh "$OUT/jvm"

env -u GIT_CONFIG_COUNT -u NODE_TEST_CONTEXT node --test --test-concurrency=2 \
  .github/continuum/*.test.mjs scripts/*.test.mjs \
  tools/continuum-virtual-fabric/*.test.mjs \
  tools/continuum-cognitive-pilots/verified-reuse-pilot.test.mjs

npm --prefix backend ci --ignore-scripts --no-audit --no-fund
npm --prefix backend run check
node --test --test-concurrency=2 backend/test/*.test.js

bash scripts/hollywood-fui-cinematics-preview.sh "$OUT/frames"
bash scripts/hollywood-fui-reference-fidelity.sh "$OUT/metrics" code51-b3e288f
npm --prefix tools/avatar-3d ci --ignore-scripts --no-audit --no-fund
npm --prefix tools/avatar-3d run validate

./gradlew --no-daemon --console=plain \
  -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true \
  -PcontinuumStandaloneVersionCode=51 -PcontinuumOwnerArm64=true \
  -PcontinuumUiWidgetTests=true \
  :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug
```

Supplemental commands use the same frozen checkout, with the restored
`kotlinc` on PATH and `JAVA_OPTS=-Xmx1g`:

```sh
env -u GIT_CONFIG_COUNT -u NODE_TEST_CONTEXT node --test --test-concurrency=1 \
  .github/continuum/kotlin-delegate-acceptance.test.mjs \
  .github/continuum/kotlin-delegate-synthesizer.test.mjs \
  .github/continuum/owner-kotlin-preview.test.mjs

java -cp "$OUT/jvm/test:$OUT/jvm/main:$OUT/jvm/stubclasses:$DEPS/*:$KOTLIN_HOME/lib/kotlin-stdlib.jar:app/src/test/resources:app/src/main/resources" \
  at.heinz.deviceai.cognitive.VirtualModelFabricFixtureMain > "$OUT/registry-snapshot.json"
CONTINUUM_FABRIC_SNAPSHOT_FILE="$OUT/registry-snapshot.json" \
  node --test .github/continuum/compute-fabric-model-adapter.test.mjs
```

The export uses the already compiled complete graph, not the older subset
compile script. It proves synthetic JVM/Node contract compatibility only, not
shared model weights, live remote compute or a new training capability.

The Node command removes only inherited per-process test/config injection:
`GIT_CONFIG_COUNT` forced `core.fsmonitor=false` and masked the synthetic
unsafe-config fixture. The same failure reproduced on unchanged #658 (16/17),
then the clean fixture environment passed 17/17; system Git configuration and
production guards were not changed. Node26 is rejected by the host-admission
contract; Node22 and early Node24.11.0 lack the actual JUnit file identities
required by CCC1. The current official Node24.22.2 was selected from
`nodejs.org/dist/index.json`, checksum-verified, and passed all 62 targeted
runtime/host-admission checks before the final sweep.
Ubuntu's bubblewrap0.9.0 was restored after the missing-executable probe; an
actual unprivileged user/network namespace plus read-only root bind returned
exit 0. No system namespace policy or isolation guard was weakened.
Exploratory failures are retained in `node-exploratory-attempts.json`, not
relabelled as green. No test assertion was weakened.

Fresh focused JVM rerun after the complete runner:

```sh
CP="$OUT/jvm/test:$OUT/jvm/main:$OUT/jvm/stubclasses:$DEPS/*:$KOTLIN_HOME/lib/kotlin-stdlib.jar:src/test/resources:src/main/resources"
cd app
java -cp "$CP" org.junit.runner.JUnitCore \
  at.heinz.deviceai.ui.HollywoodFuiConstructTest \
  at.heinz.deviceai.ui.HollywoodFuiCinematicsTest \
  at.heinz.deviceai.ui.HollywoodFuiMaterialRegressionTest \
  at.heinz.deviceai.cognitive.ConversationSituationModelTest \
  at.heinz.deviceai.cognitive.LocalConversationSituationTrackerTest \
  at.heinz.deviceai.cognitive.SituationReturnMorphologyTest \
  at.heinz.deviceai.cognitive.LocalConversationContextInvalidationTest
```

Use absolute `OUT`, `DEPS`, `KOTLIN_HOME` paths. The original GLB validator
writes generated reports under `docs/avatar-live/geometry-work`; its two
timestamp/path-only changes were removed, preserving the original evidence.

### Visible comparison and limitations

Rendered baseline #658 with its own archived preview/assets (36 frames) and
the candidate with its unchanged frozen preview/assets (40 frames). Retained
nine PNGs with SHA-256 provenance, including matching 760/2400 ms Fold frames,
phone low budget, reduced motion and the candidate's new disabled-effects
fallback. These are Java2D stand-ins, not screenshots of Android.

At 760 ms the candidate has subdued grid lines under a native-content
placeholder, localized cyan/blue/violet depth rims and a faint diagonal
reflection; the old preview is dominated by a uniform grid and permanent
source beam. At 2400 ms the candidate is quieter and preserves an uncluttered
content area, but remains very dark with strong cyan edges. The old preview's
drawn source orb is absent from the new *preview*: the actual app's existing
avatar and energy orb were not removed. Java2D cannot prove native content
occlusion, Android Camera perspective, animation smoothness or frame rate.
The measured darkness/contrast discrepancy remains an explicit quality gap.

### Isolated rollback checkpoints

All commits were pushed and their remote SHAs read back. Revert with new commits
on the integration branch, never force-push or reset another developer's work.

| Change | Exact checkpoint | Rollback boundary |
|---|---|---|
| Audit | [`a01e9a3ae98a096b6919118289cba4c6b02ef544`](https://github.com/Ullmann27/device-ai/commit/a01e9a3ae98a096b6919118289cba4c6b02ef544) | Documentation only |
| Speech repairs | [`c8d5d0b7de6348525e5bc844051088b79f13ab77`](https://github.com/Ullmann27/device-ai/commit/c8d5d0b7de6348525e5bc844051088b79f13ab77) | Revert speech files/tests together only if regressions justify it |
| Graphics/reference tooling | [`72f2a690a96a7597a91a3038ee567d1cae9d677a`](https://github.com/Ullmann27/device-ai/commit/72f2a690a96a7597a91a3038ee567d1cae9d677a) | Disable cinematic mode first; for source rollback revert graphics follow-up before this checkpoint |
| H4-B1 constrained repair | [`001ece73d8d78970b222473a644fe93ad1999299`](https://github.com/Ullmann27/device-ai/commit/001ece73d8d78970b222473a644fe93ad1999299) | Revert both cognitive files and coupled tests together; leaves canonical #658 context/memory intact |
| Graphics follow-up | [`b3e288f79d3bce6da23dbf5a498d50811a246358`](https://github.com/Ullmann27/device-ai/commit/b3e288f79d3bce6da23dbf5a498d50811a246358) | Independently revert disabled-frame/zero-bar/truthful-label cleanup if required |

No persistent owner-data schema changed in these integrations. Do not delete
profiles, dialogue, memory, receipts or APK data as a rollback technique.
## APK51 continuation — 09.11.2026 (new freeze, not covered by the old acceptance)

The owner requested completion of APK51 including Bot Universe and an original-signed,
data-preserving update over Code50. After renewed local AGP/Google Maven and both Render
host failures, the owner explicitly approved the GitHub Code51 QA/signing channel.
This channel decision is **not** final approval of an as-yet-unfrozen signing SHA.
The required `Ullmann27` environment reviewer remains mandatory; no deployment approval,
signing secret access, main merge, installation or publication has occurred.

- `SOURCE_INTEGRATED`: #662 `5a0fd0a8e1bc2cd098693a62e766f3be19d23429`
  merged as `16688ed015bf757c636a59126867b3ca76cc1bd0`.
  Existing Chief coordinator/pool/store, owner-selected chief/two specialists, two bounded
  serial model calls, zero tools, private-context separation, durable interrupted/no-replay
  state and explicit missing-model/coding boundaries are retained.
- `SOURCE_INTEGRATED`: only #659 production/tests from `76b26e22`, `6d392e3e`,
  `34b2aa1f498f08779596b36331e6e786983b3e12`, isolated as
  `f34842a074057ef876f55fdf851d200f790d03e0`. Existing cyan fill responds to real orb
  energy; gaze follows native projected pane centers; work admitted during speech waits
  without stealing speech gaze. Original GLB, energy orb, shared clock/budget and earlier
  Code51 repairs survive. Upstream's contradictory compile evidence was not imported.
- Added byte-for-byte regression over existing owner runtime files, 20 profiles, private
  notes and dialogue history through team initialization, execution, reopen and history
  clearing. This is host storage evidence, **not** proof of an actual installed update.
- `HOST_TESTED`: 14 release-contract/preflight/JUnit-evidence tests pass on Node24.22.2:
  `node --test .github/continuum/code50-github-release-contract.test.mjs
  .github/continuum/code50-github-signer-preflight.test.mjs
  scripts/continuum-code51-junit-check.test.mjs`.
  Full combined Kotlin/Node regression and Android acceptance are pending on this new source.
- Reuse existing registered `continuum-code50-github-qa.yml` and
  `continuum-code50-sign-release.yml` paths on the #663 branch only, with Code51 guards,
  artifacts and receipts. Their old filenames do not select Code50. The published Code50
  branch is unchanged. No new workflow registration or main modification is needed.
- Android gate: JDK21, SDK36/build-tools35, real compile + all unit/widget tests + lint,
  no nonblocking lifecycle job. Expected 4,197 tests, zero failures/errors and exactly the
  18 named pre-existing skips from original Code50 evidence, excluding its two JDK17-only
  lifecycle skips. Missing/duplicate cases, changed skip identities and absent critical
  new suites fail closed. Counts derive from source test annotations and must be confirmed
  against actual Gradle XML, not treated as an observed pass.
- Unsigned transport is original package, versionCode51/versionName0.3.50, ARM64 only.
  QA independently downloads and verifies published Code50 APK SHA
  `38b39da66a96d8be08b84937dd6412868fa279eb477a3ff5700d9195611a2778`,
  package/version and original certificate
  `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`.
  The signer requires the same source, QA run, receipts, narrow environment branch policy,
  original certificate and explicit owner approval, then draft/public readback.
- `ANDROID_BUILD_VERIFIED`: pending. `DEVICE_VERIFIED`: **NOT_RUN**.
  Original signing/publication: **BLOCKED** until new exact-SHA green QA and owner approval.
  Existing environment branch policy still names only the Code50 branch; no protection was
  weakened. Never uninstall or clear data to install this update. No 60FPS, real model
  quality, native graphics or data-loss-free device-upgrade claim is made.

Rollback remains isolated: revert the new graphics integration independently of the bot
merge; revert the bot merge with `-m 1` if necessary. Do not remove existing profile,
conversation, runtime or model files. Do not attempt an Android versionCode downgrade.
