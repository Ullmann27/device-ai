# Code 51 master integrator

Branch: `copilot/code51-master-integrator-20261108`.
Base: #658, `f153758dfd19d1c12f6fe63b6c4c4bd1da95e4cb`.
Remote audit: 2026-11-08. Draft PR: #663. WIP.
No main merge, signing, APK publication, device installation or research promotion.

## Source audit

| PR | Actual remote HEAD | Decision |
|---|---|---|
| #646 | `f3f90736f4addb027dcc4f42a1f3cd3300713d3b` | Already an ancestor; repair three open speech reviews in place |
| #650 | `82590f532d0b070823d82e6007f53e5e825cf70f` | Already an ancestor; preserve bounded tools and result evidence |
| #653 | `cb11db2f967110680da92db8c0b07f01f3d2b47d` | Already an ancestor, including H1-H3 and #651; do not merge again |
| #658 | `f153758dfd19d1c12f6fe63b6c4c4bd1da95e4cb` | Canonical base: navigation, research lifecycle and whole-text memory admission |
| #659 | `452591edd31215fecea7a3110036157a7c1ed438` | Evaluate bounded graphics delta; fix missing Robolectric runner import |
| #661 | `58c545aaefefb233371c92948ad1a9a002c7c004` | Measurement tooling only; repair hue label and metric documentation before new evidence |
| #655 | `c90f020b8db929ae9a53e2cd95c39419497281b4` | H4-A excluded; independently test H4-B1 normalization with prefix-collision negatives |
| #660 | `26b0ebc5a4b194e837f706784b6810052f592a86` | Research/evaluation only; no new model capability |
| #662 | `350ceaf2aea4ccde68645645ef6c92bac729c2f2` | One documentation file, no implementation; defer team integration |

`git merge-base` confirms #659/#661 branch from #653, #655 from #649,
#660 from Code50 #630, and #662 directly from #658. The H4-B1 implementation
commit `3ab39fcd41b0d0015197bdbab6e22b76a1e424da` changes two production
files; `SituationReturnMorphologyTest.kt` originates in earlier commit
`790e91adf` (not in that implementation commit). No whole research-branch merge.

Relevant contracts: EXP-05 local model, EXP-06 provenance/context, EXP-11
existing delegation, EXP-12 lifecycle, EXP-16 discourse research. Preserve
ToolGate, owner consent, private bot contexts and the single rendering budget.

## Initial acceptance

All new integration work: `NOT_RUN`. The earlier 3578 JVM / 202 Node report
belongs to `d1fb060d95a4207191a665b91f90a208ad941cae`, not this candidate.

Commands actually attempted on the unchanged base:

```sh
bash scripts/android-kotlin-unit-check.sh <output>
./gradlew --no-daemon --console=plain \
  -PdeviceAiMentorPreview=true -PcontinuumLocalStandaloneQa=true \
  -PcontinuumStandaloneVersionCode=51 -PcontinuumOwnerArm64=true \
  :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug
```

Host runner: `BLOCKED` / dependencies `UNAVAILABLE` (Kotlin and dependency jars
absent). Gradle wrapper 8.11.1 downloaded from its configured official source;
Gradle failed before app tasks on installed JDK 27 (`What went wrong: 27`).
Restore a supported JDK and official pinned dependencies, then retry.
SDK/adb: `UNAVAILABLE` at entry. Android build, UI tests, APK hash and
Fold7/Android17/ARM64 verification: `NOT_RUN`.

## Resume and rollback

First repair #646, then bounded #659/#661 integration, then optional H4-B1
only if negatives pass. Run full host suites and actual Gradle gates.
Investigate the existing authorized Render QA host if local SDK is blocked;
the Code50-only GitHub Actions/signing exception does not apply.

Commit each independent repair/integration separately and verify its remote
SHA. Roll back with a new revert commit of the affected checkpoint, never
reset/force-push another team's branch or discard existing bot/memory data.
Persistent owner-selected chief/two-specialist execution is not implemented
by #662 and is not claimed here.

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
