# Code 51 master integrator

Branch: `copilot/code51-master-integrator-20261108`.
Base: #658, `f153758dfd19d1c12f6fe63b6c4c4bd1da95e4cb`.
Remote audit: 2026-11-08. WIP; this initial checkpoint changes documentation only.
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
