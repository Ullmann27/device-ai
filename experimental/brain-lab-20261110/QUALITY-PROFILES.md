# Shared scenes, platform-specific measured presentation budgets

Documentation only. Android source checked against Cinema
`56934505b674c8ed55c3a0ff3aa10aa914368743`; `JarvisRenderQuality.kt` Git blob
`0564e698800e8d4edb2b3151d00b584aec69cd5c` is identical in this lab checkout.
These are configured delays/count limits, not measured FPS, battery savings or
frame deadlines. This document changes no app defaults.

## Existing Android policy

`ContinuumHologramView.renderBudget()` applies, in order:
`JarvisRenderQuality.adaptiveAnimation` → `JarvisComputePhasePolicy.apply` →
`AvatarEssentialMotionPolicy.frameDelayMillis`. The shell obtains that same
budget through `ContinuumHomeView.motionBudgetProvider`; work-window decoration
subscribes via `JarvisWorkWindowHost`. Do not create a second quality engine.

The following table describes **resolve() before phase shaping**, in priority
order. Thermal values are the existing Android numeric inputs; a missing value
is treated as zero for tier selection, but does not count as explicitly cool
for the inference-lighting exception.

| Conditions, after earlier rows | Tier | Delay ms | Particles / orbit segments | Decorative animation |
|---|---|---:|---:|---|
| Not foreground/visible according to caller | SUSPENDED | 0 | 0 / 40 | Off |
| Power saving, reduced motion, or thermal >=3 | LOW | 80 | 12 / 40 | Off |
| Inference active, thermal >=2 | MEDIUM | 80 | 12 / 40 | On |
| Inference active, thermal >=1 | MEDIUM | 50 | 16 / 48 | On |
| Thermal >=2 | MEDIUM | 50 | 16 / 48 | On |
| Inference active or thermal >=1 | MEDIUM | 33 | 32 / 64 | On |
| Otherwise | HIGH | 16 | 70 / 96 | On |

Additional existing restrictions, never an Android capability upgrade:

- EXP15, when enabled for Cinema and animation is allowed, limits particles to
  12 and segments to 40 during inference/warmth, with delay at least 50 ms
  (66 ms for thermal >=2). It preserves any stricter base delay and disables
  the explicitly-cool lighting exception.
- Phase shaping caps IDLE at 40/64 with >=33 ms; LISTENING at 48/72;
  THINKING at 16/48 with >=42 ms; RESEARCH/LEARNING at 12/40 with >=50 ms.
  SPEAKING retains the base; diagnostic proof can bypass only phase shaping.
- Essential avatar motion is separate from decoration: foreground, enabled
  animations and system animators remain required. Visible speech in LOW may
  use a 50 ms delay with zero particles. This is not blanket permission to
  bypass accessibility, hidden-window or foreground restrictions.
- `FilamentAvatarRenderer.setQuality` uses the same animated model and changes
  bloom buffers (64/128 resolution, 3/4 levels). It does not select another head,
  rig, expression system, memory or reasoning engine.

Source anchors: [base policy](../../app/src/main/java/at/heinz/deviceai/ui/JarvisRenderQuality.kt),
[phase policy](../../app/src/main/java/at/heinz/deviceai/ui/JarvisComputePhasePolicy.kt),
[essential motion](../../app/src/main/java/at/heinz/deviceai/ui/AvatarEssentialMotionPolicy.kt),
[actual caller](../../app/src/main/java/at/heinz/deviceai/ContinuumHologramView.kt).
Existing `JarvisRenderQualityTest`, `JarvisRenderQualityAdaptiveTest`,
`JarvisComputePhasePolicyTest` and `AvatarEssentialMotionPolicyTest` define
regressions; this documentation-only checkpoint does not claim a fresh test run.

## Android / desktop contract

| Surface | Current state | Shared contract | Permitted presentation difference |
|---|---|---|---|
| Android/Fold7 app | Existing source path; this lab has no device acceptance | Existing AvatarEvent/AvatarDirector identities, CockpitAnimationEvent, JarvisActionScenes and JarvisShellRenderer.CinemaFrame with one scene time | Actual viewport, visibility, power/accessibility/thermal/inference signals drive the existing budget |
| Desktop host tests and captures | Existing diagnostic tooling, not a desktop product | Replays may inspect the same events, scene values and source-pinned assets | Host renderer differences must be labelled; they are not Android GPU or audio evidence |
| Future desktop application | **NOT_IMPLEMENTED** in this work | Preserve the same event ordering, turn/utterance IDs, cancellation, scenes, camera intent and authoritative permission/evidence boundaries | A thin capability/viewport adapter may supply measured platform inputs; larger budgets require actual latency/RAM/power evidence, not a device-name assumption |

Do not fork a PC brain, avatar engine, event bus or scene state machine. A
future adapter must map absent thermal/power/visibility capabilities explicitly,
not claim cool hardware by inventing readings. Unknown desktop performance has
no promised particle count, FPS, memory budget or model speedup.

Before desktop promotion, replay the same frozen event trace on both adapters:
identical turn/utterance/scene transitions and stop/revocation outcomes are
required; only documented presentation sampling/budgets may differ. Android
must retain its current hard stops. No desktop implementation or cross-platform
parity test has run here. The rejected compute-admission and event-accumulator
experiments remain rejected regardless of platform.
