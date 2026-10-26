# Focused dialogue routing review — 2026-10-26

**Scope:** production `LocalFirstPolicy.kt` only, with new JUnit and reproducible host-only compiler smoke. No Android APK, model inference, new memory, tool grant or weights.

## Trigger and real RED
PR151 changed the route so that if a confirmed recall exists, the presence of unquoted OR quoted token `analysiere`, `warum`, `plane`, `vergleiche` etc. could select local model synthesis rather than direct owner recall. E.g.
`Was habe ich über „analysiere“ gespeichert?` on the old `SYNTHESIS.containsMatchIn(request)` path returned `LOCAL_MODEL`; desired `LOCAL_CORE`.

The baseline RED was reproduced using an exact copy of the new production Kotlin class with just the old routing expression restored; `kotlinc` built it and the Kotlin runtime check failed with `FALSE_SYNTHESIS ... => LOCAL_MODEL` (exit 1).

## Repair
- Keeps actual user input unchanged; only quote/lexical-definition spans are excluded from *intent detection*, never from memory lookup or model text.
- Explicit *unquoted* synthesis such as `Bitte analysiere …`, `Warum …` continues routing to the local model when ready.
- Direct quoted recall and one-word dictionary definitions stay with confirmed memory.
- In the existing branch, the `LocalFirstPolicy.kt` Git blob is `123d1d795df408db23a6d2cf1b3d8d17743de94b`. The same exact Git blob was reconstituted locally and compiled using installed `kotlinc 1.9.0`, JVM21 and JVM17 target. `LocalModelState` for the focused compilation was copied from the exact enum declaration of the actual runtime, not a generated replacement policy.
- **19/19** focused host Kotlin route cases green. Red/green includes direct and quoted recall, German word definitions, genuine synthesis, model-unavailable fallback, model setup routing, UI state, full long-input preservation and scoped follow-up.
- The interim trial of a Unicode-regex refinement produced an actual Kotlin `illegal escape: '\\p'` compiler error and was **corrected before this final tested blob**. This negative result was not hidden.

Reproduction from a full authorized checkout (JDK and Kotlin compiler on PATH):
```sh
bash scripts/continuum-dialogue-routing-smoke.sh
```

Expected on a supported JVM host: `CONTINUUM_ROUTING_SMOKE_PASS: 19 focused JVM cases`; the test script compiles the original production Kotlin class and extracts the existing runtime's exact enum declaration without requiring external model/native Android SDK. The host checks above used an equivalent 19-case probe plus the same production file byte hash.

## Tests and limitations
- New production Kotlin code: **COMPILED / HOST_SMOKE_GREEN** at blob `123d1d...`; this is not an Android build.
- Additional Android JUnit cases in `LocalFirstPolicyTest.kt`: **COMMITTED / NOT_RUN_ANDROID_GRADLE** (Android SDK/Gradle unavailable in this assistant's runtime).
- SOL PR156 `df4b395f` started Android SDK and independent grounding work on its own branch, but no final success is inferred here.
- Fresh independent60 grounding evaluator, full Node suite, backend, Android Gradle, lint, Fold7, real Gemma and DeepSeek inference: **NOT_RUN** for this new branch.
- `main`, PR151, PR152–156, signing key, existing app, private data and owner weights unchanged.
- No model authority/tool privileges changed.

## Review and continuation
Before cherry-picking, execute the new JUnit tests plus full existing `LocalFirstPolicyTest` suite and the current Android SDK compile on one pinned integration SHA. Check behavioral interactions with direct owner recall and ToolGate/privacy/cancellation. Then perform the frozen new PR153 grounding evaluation without exposing its labels to training. The CDX and DeepSeek research remains separate. Roll back by reverting the independent source/test commits in PR157 only.
