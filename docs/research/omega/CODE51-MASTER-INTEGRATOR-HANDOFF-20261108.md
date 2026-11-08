# Code 51 master integrator

Branch: `copilot/code51-master-integrator-20261108`.
Base: #658, `f153758dfd19d1c12f6fe63b6c4c4bd1da95e4cb`.
Remote audit: 2026-11-08. Draft PR: [#663](https://github.com/Ullmann27/device-ai/pull/663).
Frozen source under regression: `b3e288f79d3bce6da23dbf5a498d50811a246358`.
WIP until the acceptance gates below are satisfied.
No main merge, signing, APK publication, device installation or research promotion.

## Source audit

| PR | Actual remote HEAD | Decision |
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

First repair #646, then bounded #659/#661 integration, then optional H4-B1
only if negatives pass. Run full host suites and actual Gradle gates.
Investigate the existing authorized Render QA host if local SDK is blocked;
the Code50-only GitHub Actions/signing exception does not apply.

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
`HollywoodFuiMaterialRegressionTest` with JUnit4.13.2. Final complete-source
runner and SHA-bound evidence follow. Rollback graphics independently of
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
Those broader tests await the complete host runner.
H4-A, research scorers/certificates and Genesis remain excluded.

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
