# Code60 full QA repair — 2026-11-16

Status: WIP / UNVERIFIED. Owner: Astra, this repair branch.
Integration target: `integrate/continuum-command03-android-20261115`, PR #851.
Starting source: `3d5efd9ec4b1a8a5c9c12bb39835be8aef9a9b64`.
Failed real QA: https://github.com/Ullmann27/device-ai/actions/runs/43864138460 (run #41).

## Observed evidence

- unsigned-arm64: PASS. Full lint: PASS.
- renderer-preflight: 33 cases, one source-contract assertion failure at CockpitMonitorPresentationTest.kt:92.
- full-android: extracted JUnit contains 5914 cases, 134 failures and 2 skips. Both omitted cases match the existing `ChiefWorkspaceWiringHostTest` skip ledger; no new skip is accepted.
- node-backend critical contracts: process 37 PASS; storage-agent 32 PASS; Android source contracts 64 PASS / 2 FAIL; host-checker 2 PASS.
- native-runtime: PDF and isolated Filament probes passed. Existing native-home hologram probe could not find a visible legacy home. App/routine UI-hierarchy checks failed after camera decline.
- The native camera-declined PNG actually renders the Command03 HTML design. Its hierarchy contains only the WebView: an empty hierarchy is not evidence of an empty screenshot.
- Most failed Activity fixtures explicitly assume the classic native cockpit but did not select it after the default changed to original HTML. Each fixture still requires individual review; this is not permission to discard any assertion.

## Repair scope and acceptance

Preserve the original HTML default, approved asset bytes, package, signing certificate, owner gates and full QA requirements. Select the classic mode explicitly in classic fixtures; retain separate real-default HTML coverage. Correct stale source assertions against the actual call path. Extend native QA to exercise the real HTML stage and preserve classic regression coverage, without counting mocked DOM/host checks as device proof.

First repair checkpoint: 27 classic Activity test classes now explicitly select the existing classic presentation. Three stale source assertions follow the current More-menu offset, bounded conversation presence and authored orb rest phase. All nine Android source-contract files pass all 66 cases locally at `e423f6ec3f972e743996b410735bb9b4f61d33fa`. The subsequent test-helper correction preserves the new original-design menu row and routes existing entries with the actual +1 offset. Android execution remains UNVERIFIED. No application/default/asset change. Full/context QA and original signing remain blocked. A code change requires a new source SHA and fresh real runs; the failed run ID is not a signing input.

## Evidence available

GitHub run artifacts: Code60-Android-full-QA (14226165344), Code60-native-runtime-QA (14225625699), Code60-Node-backend-QA (14224698200), Code60-Renderer-regressions (14224858552). Original unsigned APK artifact: 14225260831.

## Current execution boundary

Three local Android compilation attempts and an additional info-level diagnostic failed before completing. Gradle processes terminated or their daemon disappeared. No compiler diagnostic or completed test execution was obtained; the exact termination cause is unproven. Lower memory settings and removal of this task's disposable release-library intermediates did not resolve it. Stop repeating the same compilation without new evidence. The local checkout was sparse; `backend` has now been materialized and all 66 source contracts reran successfully. An earlier 62/63 result was a missing local backend file, not an application defect. Full Node QA and backend checks are not claimed.

## Concrete remaining work

1. Run the modified classic fixtures and check all remaining failures. Explicit mode selection does not itself prove a repair.
2. Reconcile existing native-workstation tests with the actual shared composer and conversation rail. Run #41 also fails nine `ContinuumReferenceCockpitUiTest` cases even though that fixture already selects classic mode. `JarvisLiveTabsUiTest` still expects no navigation rail; both `JarvisWorkWindowHostViewportUiTest` cases retain old geometry assumptions. These are not covered by the HTML-mode fixture fix.
3. Repair native coverage for both presentations: the classic hologram/routine/app probes need explicit classic selection, and the real default HTML must retain its own actual emulator lifecycle/input/navigation evidence. Merely forcing classic globally would leave the shipped default untested and is not an acceptable repair.
4. Investigate HTML accessibility capture: the real screenshot renders the design, but the captured hierarchy contains only a WebView. The screenshot alone proves neither accessible controls nor successful lifecycle/interaction.
5. Obtain new full QA and separate context-QA success on the same final integration SHA before original signing. This draft is not integrated into PR #851 and the release candidate stays at `3d5efd9e`.

The complete run-41 failure inventory, hash-verified artifact identities, source-contract log, failed compilation receipts and actual emulator screenshot are in [`code60-run41/`](code60-run41/). The PNG is an observation from a failed run, not a fabricated preview or an acceptance image. No app merge to main.
