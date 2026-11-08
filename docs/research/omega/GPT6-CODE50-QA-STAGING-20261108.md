# Code50 QA Staging — 2026-11-08

Integration base: PR #603 HEAD `0c59dcc7b249d7eb6bfc9fb21d2306f814105ed6`.
Source branch: #628 through `b445b509e79a65f908f95b51d37409cc840ac5d6`.
Incoming planned: non-conflicting quality fixes and tests from #627 at `d6b3f8ce9bb44aab1af667020a24ff9ad5809cc0`.
#629: QA-only, not automatically cherry-picked.

**No main changes, no signing or release.** This branch combines candidate fixes only; Gradle/Node/Fold7 validation remains mandatory.

## Order
1. Carry unique production fixes: ProactiveSideQuestAndCountdownEngine, HollywoodFuiStageOverlay.
2. Carry updated contract tests for true same-session context and Fold7 Shell.
3. Carry Robolectric OwnerAppActionWorkSceneUiTest holdouts.
4. Preserve the richer #628 OwnerAppActionFlowTest instead of overwriting it with #627 one-line-only version.
5. Validate source and run available tests; report exact SHA and outputs.
6. Only then consider integrating into PR #603 (not main).

Risk: PR #628 stale pronoun state after rejected lookup must be cleared; fixed at `b445b509` and keep it in staging. Review all pending threads independently.

STATUS: WIP / UNVERIFIED.
