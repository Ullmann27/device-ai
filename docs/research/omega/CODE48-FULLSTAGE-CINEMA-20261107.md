# Code48 full-stage cinema continuation

Status: SOURCE_CONNECTED / FOCUSED_HOST_PASS / FULL_GATE_PENDING.
Base: PR590 at d73fc46b5a14ea5fc3b82a3bd3b96cacebe7d4de.

## Concrete remaining reference gap

The baseline recovered document flight was connected to genuine document/recall
events, but its canvas was restricted to the avatar slot. The existing original
ContinuumHologramRenderer already provides the energy core, lightning and orbit
layers. A larger finite composition from document flight to the centred original
orb and back to the actual cockpit is now connected through the actual Home,
HologramView and sequence callback.

## Authorized implementation boundary

Reuse the actual original renderer, frame state and clock. Keep current head,
orb assets, palette, data, model and ToolGate. Preserve visible navigation/STOP
and foreground work-card access. End immediately on STOP, foreground/visibility
loss, detach, reduced motion or disallowed thermal policy; do not replay stale
events or prolong an active episode on repeated events. No demo button, fake
telemetry, second renderer, animation thread, cloud service or device install.

## Implemented and focused verification

The scene has a fixed 4,800 ms lifetime. Positive document/recall events use the
large scene when its actual Home host is visible and ready; the existing small
inner-slot fallback retains its 3,000 ms limit. Repeated events do not extend
the scene. The same original orb renderer and live frame draw at the larger
position, then return to the existing local geometry. Work windows and actual
visible controls stay in front. Canvas state and temporary frame energy are
restored with finally. No original assets, appearance or model data changed.

A delivered recall failed the outside-avatar pixel assertion on the baseline.
The focused corrected run passed 9/9 checks, including finite state and existing
recall/STOP controls. An independent 5/5 run through the actual AgentActivity
and cockpit bus covered original-orb geometry/return, event admission, repeated
events, queued-before-STOP, CHAT reparenting, reduced motion, actual window/control
taps and complete hdpi control bounds. These are host overlays, not a full APK.

The integration owner inspected native Activity frames at 1,100, 2,200, 3,520
and 5,060 ms. Bright paper/folder flight, the large original orb and return are
visibly present. The 24-frame export samples every 220 ms; it establishes scene
appearance, not device frame rate. Private reference media is not published.

The d73 baseline Android gate was superseded before completion once this
requested scene became ready. Its compiled source and four completed non-Android
gates are preserved, but no Android PASS is claimed for that interrupted run.

## Reproducible gates and review repair

The combined source `3ff83387af64bf9f99f4440f657f3d27937ca777`
(tree `5206abd91b3740838661aa0e27e6702b3891591b`) completed these gates:

| Gate | Exact command | Result on 3ff |
| --- | --- | --- |
| Node full | `node scripts/continuum-reliability-qa.mjs full-node --out <evidence>/full-node` | 2,398 passed, 31 skipped, 0 failed |
| Critical | `node scripts/continuum-reliability-qa.mjs critical --out <evidence>/critical` | 135 passed, no skips |
| Backend | `npm ci --ignore-scripts --no-audit --no-fund`, `npm run check`, `npm test` in `backend` | Commands exit 0; 64 tests passed |
| Release/preflight | Six explicit Node test files in the receipt | 23 passed, no skips |
| Unsigned release | Gradle base below plus `-PcontinuumLocalRenderUnsigned=true -PcontinuumOwnerArm64=true :app:assembleRelease :app:bundleRelease` | PASS; independent APK/AAB integrity, package, assets and unsigned checks PASS |
| Complete Android | Gradle base below plus `--rerun-tasks -PcontinuumUiWidgetTests=true -PcontinuumLocalStandaloneQa=true :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` | No completed PASS; first daemon disappeared, isolated retry deliberately interrupted for review repair |

Gradle base: `gradle --no-daemon --max-workers=2 --console=plain
-Dorg.gradle.jvmargs="-Xmx1600m -XX:MaxMetaspaceSize=512m"
-Pkotlin.compiler.execution.strategy=in-process -PdeviceAiMentorPreview=true
-PcontinuumStandaloneVersionCode=48`. Use an isolated Gradle home and run Android
and unsigned Gradle tasks sequentially. The first concurrent unsigned attempt
hit a shared journal-cache lock. The exact cause of the earlier disappearing
Android daemon is not proven; no completed suite is inferred from compilation.

The 3ff unsigned artifacts are intermediate. Review
[discussion_r4500300174](https://github.com/Ullmann27/device-ai/pull/591#discussion_r4500300174)
identified transient notice cards below the full-stage compositor. The actual
Home shell also returned before positioning that card. A focused native
regression and bounded visibility repair must pass before a new final freeze.
All previous failures and the deliberate interruption remain preserved as
separate receipts, never converted into PASS.

The actual CalculationShown bus probe did not create a legacy card: the shell
presenter deliberately returns before that legacy fallback. Document/account
events already have a separate sequence presentation. The repair is therefore
restricted to the existing public Home notice API and view hierarchy; it does
not enable another event route or claim a newly connected calculation feature.
The native regression uses real MEMORY bus delivery plus that public API, with
normal Activity measure/layout. The first bus-probe result is retained as a
diagnostic scope correction, not presented as proof of a live hidden card.

## Notice API repair: focused native PASS

The bounded repair changes only `ContinuumHomeView` production code. The
existing notice view now uses Z `.75 * density`, above the full-stage scene
and shell artwork and below real controls. Its measured/layout rectangle
comes from the current shell avatar slot with the dimensions available in
that pass; the old layout and rail offset remain unchanged in legacy mode.
The common layout block runs before the shell branch returns.

The final unchanged hdpi test first fails on original 3ff because the notice
has no layout, and then fails on corrected layout without foreground Z
because the scene overdraws actual opaque text. The corrected production
passes 1/1 with both compilers exiting 0. It exercises the real Activity,
MEMORY bus, public notice API, entrance translation/alpha, native text pixels
against the same root hierarchy and a real dismissal touch while the memory
scene remains active. The earlier isolated-card raster comparison and the
first CalculationShown probe are retained as unsuccessful diagnostic attempts;
they are not counted as final GREEN or a connected calculation feature.

Evidence: [notice repair](../../evidence/code48-notice-api-repair-20261107/).
Final Home SHA256: `79bd15a4713fb56b4d076d8dacb756839ae7f4f8421cc82b4a8208a294528e6a`.
Final test SHA256: `408be078c3d3a13f65b73834d62e8de12edba839a107fde2fee8c111916c365b`.
No presenter route, original asset, model, permission or signing contract changed.
The complete source gates and new unsigned build still follow the new freeze.

## Next entry point and rollback

Start with `ui/ContinuumHomeView.kt` (`showTransientCard`, `onLayout`,
`memoryFullStage`) and `FullStageMemoryReviewUiTest` in `app/src/uiTest` for the
notice repair. The scene lifecycle is in `ui/video3/Video3MemoryScene.kt`;
`ContinuumHologramView.drawMemoryScene` reuses the original renderer and the
`JarvisSequenceView` caller admits actual memory events. Focused evidence is
under `docs/evidence/code48-fullstage-20261107/` and
`docs/evidence/code48-fullstage-independent-review-20261107/`. Final combined
receipts and the superseded attempt history are added after the new freeze.

Rollback the bounded full-stage scene/host change while preserving PR590's
connected file tunnel, training cancellation correction and STOP protections.
Do not reset a shared branch or change the original renderer, assets, models,
package, signer, release catalog or installed data as a rollback shortcut.

## Final acceptance still required

Full-source Node/backend/Android/lint and the unsigned Code48 build must be rerun
on the final integrated source; the d73fc46 baseline is not final-source proof.
Host canvas evidence does not establish Filament GPU, Fold7 performance or exact
pixel parity with the original movie. Signing/publication remains subject to
the exact-SHA owner release freeze. No such action is authorized by this note.

Contracts: EXP-02/07 regression and independent evidence, EXP-11 event routing,
EXP-12 lifecycle. Rollback: revert only this bounded scene/host connection.
