# Code49 – Reference home audit and input repair

## Scope and evidence boundary

Owner supplied six target concepts and an actual Android home screenshot. This audit concerns the actual home screen, not only chat. Target graphics are concept references, not evidence that the app already renders or animates that way. No private screenshot, transcript or device telemetry is published here.

Base: `88dfd45139471463618673a1f827ee062e9bf6d5`, PR #594. Isolated repair: PR #598, branch `codex/continuum-code49-reference-input-20261107`. Current owner APK remains Code48; no signing, publishing, main merge or installation in this patch. Existing QA run 42370446205 belongs to the base SHA and does NOT validate this repair.

## Actual screen versus approved target

| Area | Actual screenshot / verified source | Target and next acceptance |
|---|---|---|
| Overall composition | Head, sphere and cyan/violet language are present. Dense rectangular navigation, ten short horizontal action buttons and four large diagnostic panels dominate the lower area. | Preserve head/sphere/colours; create the spacious, layered visual hierarchy of the supplied home reference. Not a new unrelated neon theme. |
| Primary input | No broad central input strip is visible. `JarvisShellLayout.reference()` has no Slot.INPUT, while the reflow layout, painter and native conversation hit target already support it. | Restore a separate reachable input surface below the hero. First isolated fix in this PR. This remains a conversation-entry action, not a new inline editor. |
| Tiles | `JarvisCockpitGrid` still cites the older 1536x1024 reference; extra height goes to hero and information row. Tile content is drawn horizontally with icon consuming label width. Several labels are shortened in the screenshot. | Taller, spatially separated reference-style cards; icons above readable full labels and descriptions where appropriate. Adapt columns before shrinking text. Not fixed here. |
| Navigation/text | Rail entries and long labels are visibly clipped; thick glow competes with small text. | Word wrapping and useful minimum text sizes, high-contrast native-resolution text. Do not solve clipping by shrinking the whole screen. Not fixed here. |
| Materials/depth | Current screenshot reads as a flat neon dashboard with strong rounded outlines. References use more distinct glass layers, thin edges, perspective planes and restrained highlights. | Test real materials, lighting, geometry and depth order. Merely increasing bloom is not evidence of better sharpness. Not fixed here. |
| Avatar/sphere | Both are visible. A still image does not prove which renderer is active, continuous motion, correct lip sync or reference-level detail. | Verify the actual packaged asset, active renderer, render resolution, anti-aliasing, effects and playback on device. No renderer failure inferred from this still. |
| Motion | User reports missing idle/action effects. Static image cannot establish FPS, stoppage or event delivery. | Continuous low-cost foreground ambience separated from real action scenes. Native capture: idle, PDF entry, analysis, draft, return, STOP. No fake jobs or fabricated status. Not fixed here. |
| Performance | User reports long answers and heating. Screenshot is not a sustained thermal or latency measurement. | Separate model latency, first visible response, frame pacing and thermal measurements. Preserve reduced-motion, background and high-temperature safeguards. |

## Reproduced defect

Three unmodified pure Kotlin files were fetched at the exact base SHA and locally verified against their Git blob hashes before testing:

- `JarvisShellLayout.kt`: `456f40bb61b45f36ac30932292a46c91217edcf1`
- `JarvisCockpitGrid.kt`: `b0be0c5a2340f16143b05c462ca88ddfd1b932a0`
- `JarvisWindowProfile.kt`: `e875023c009128c865581fd3e8c3a18669e18c77`

`ReferenceInputRegression.kt` uses the actual layout classes, without Android or mock replacements. Eight sizes, three font scales and normal/compact presentation give 48 configurations. These are layout fixtures, NOT 48 physical devices.

RED, original source:

```text
checks=448 failures=16
16 reference-composition configurations: MISSING_INPUT
process exit=1
```

GREEN, repaired source:

```text
checks=528 failures=0
process exit=0
```

The 80 additional assertions become reachable after the 16 missing inputs exist; they are not 80 new full-app tests. Checks cover input presence, minimum touch height, separation from avatar/dialogue/tiles, ten destinations, viewport preservation, deterministic remeasurement, finite bounds and agreement between grid/painter and native-hit geometry.

## Implemented change

Commit `b31aa9a3335d5bae59408528a4357098789f1ba8` changes only `JarvisShellLayout.kt`:

- Runs `withReferenceInput()` after normal or compact reference composition.
- Reserves 52 dp times font scaling, with at least an 8 dp gap.
- Moves tiles, diagnostic row and status downward together, preserving their existing sizes.
- Keeps head, sphere, dialogue, labels, data sources and routes unchanged.
- Moves the matching grid bounds and extends the rail consistently.
- Preserves viewport height; uses the existing vertical scrolling layout instead of compressing the avatar or touch targets.
- Adds no timer, GPU effect, network call, model invocation, entitlement or signing access.

Tradeoff: the reference page becomes taller by the new input plus gap. This is a bounded missing-slot repair, NOT the final one-screen reference composition. The later hierarchy/card pass must re-balance the scene deliberately rather than keep appending panels.

Remote readback blob SHA: `75f1fc1a22da3a2a328e86adfd8c313a46e71ee8`, identical to locally compiled repaired source.

Added regular JUnit regression class: `app/src/test/java/at/heinz/deviceai/ui/JarvisReferenceInputTest.kt`. Its Gradle/JUnit execution is NOT_RUN in this session. The standalone Kotlin runner was executed successfully; do not conflate them.

## Reproduce standalone host check

From repository root with Kotlin compiler and Java available:

```bash
work=$(mktemp -d)
kotlinc \
  app/src/main/java/at/heinz/deviceai/ui/JarvisShellLayout.kt \
  app/src/main/java/at/heinz/deviceai/ui/JarvisCockpitGrid.kt \
  app/src/main/java/at/heinz/deviceai/ui/JarvisWindowProfile.kt \
  scripts/qa/ReferenceInputRegression.kt \
  -include-runtime -d "$work/reference-input.jar" &&
java -jar "$work/reference-input.jar"
```

## Ordered remaining work

1. Native UI regression for this input: actual drawn surface, matching tap target, keyboard return, compact toggle, rotation/fold, font scaling and scroll. Full Android/JUnit/lint required before integration.
2. Reference hierarchy and tile typography: eliminate clipped primary labels, allocate readable vertical card content, subordinate diagnostics without deleting access. Preserve all ten existing destinations and genuine state.
3. Sharpness/material pass: inspect real asset/viewport resolution and text rasterization before changing bloom or shaders. No claim that an AI-generated concept is an app screenshot.
4. Foreground ambience and genuine PDF work sequence with STOP, background and thermal suspension; capture real frames/video.
5. Device evidence for frame pacing, answer latency and heating. No physical Fold7 access in this session; NOT_RUN.

Release remains blocked on full integration and applicable device acceptance. No new owner APK in this work. Revert the single production-file commit to undo the input change; no owner data migration is involved.
