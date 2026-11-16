# Code60 full QA repair — 2026-11-16

Status: WIP / UNVERIFIED. Owner: Astra, this repair branch.
Integration target: `integrate/continuum-command03-android-20261115`, PR #851.
Starting source: `3d5efd9ec4b1a8a5c9c12bb39835be8aef9a9b64`.
Failed real QA: https://github.com/Ullmann27/device-ai/actions/runs/43864138460 (run #41).

## Observed evidence

- unsigned-arm64: PASS. Full lint: PASS.
- renderer-preflight: 33 cases, one source-contract assertion failure at CockpitMonitorPresentationTest.kt:92.
- full-android: extracted JUnit contains 5914 cases, 134 failures and 2 skips. Counts require comparison with the existing accepted skip ledger; no skipped case is silently accepted.
- node-backend critical contracts: process 37 PASS; storage-agent 32 PASS; Android source contracts 64 PASS / 2 FAIL; host-checker 2 PASS.
- native-runtime: PDF and isolated Filament probes passed. Existing native-home hologram probe could not find a visible legacy home. App/routine UI-hierarchy checks failed after camera decline.
- The native camera-declined PNG actually renders the Command03 HTML design. Its hierarchy contains only the WebView: an empty hierarchy is not evidence of an empty screenshot.
- Most failed Activity fixtures explicitly assume the classic native cockpit but did not select it after the default changed to original HTML. Each fixture still requires individual review; this is not permission to discard any assertion.

## Repair scope and acceptance

Preserve the original HTML default, approved asset bytes, package, signing certificate, owner gates and full QA requirements. Select the classic mode explicitly in classic fixtures; retain separate real-default HTML coverage. Correct stale source assertions against the actual call path. Extend native QA to exercise the real HTML stage and preserve classic regression coverage, without counting mocked DOM/host checks as device proof.

First repair checkpoint: 27 classic Activity test classes now explicitly select the existing classic presentation. Three stale source assertions follow the current More-menu offset, bounded conversation presence and authored orb rest phase. The two Node contract files pass all 11 cases locally; Android execution is UNVERIFIED at this checkpoint. No application/default/asset change. Full/context QA and original signing remain blocked. A code change requires a new source SHA and fresh real runs; the failed run ID is not a signing input.

## Evidence available

GitHub run artifacts: Code60-Android-full-QA (14226165344), Code60-native-runtime-QA (14225625699), Code60-Node-backend-QA (14224698200), Code60-Renderer-regressions (14224858552). Original unsigned APK artifact: 14225260831.

Next: classify failure reports; repair fixtures/contracts and native HTML checks; execute actual targeted and full checks; integrate validated changes into PR #851. No app merge to main.
