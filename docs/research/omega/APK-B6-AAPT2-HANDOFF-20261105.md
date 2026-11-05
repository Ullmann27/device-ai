# APK-B6 — AAPT2 host preflight 2026-11-05

Status: ACTIVE / source: PR #544 `6da09218472dc51b691bbe7996323fc84adb1dc8`. Independent support branch, not C1's main candidate. Owner: Continuum Helfer 6 – APK-Bau. EXP-00/09. Source paths reserved: `scripts/continuum-release-host-preflight.mjs` and its existing host regression test only. No changes to `AgentActivity`, Avatar, Orb, Kotlin app sources, signing, main, update channel, model, owner files or other worktrees.

Hypothesis: the base's `inspectHostEnvironment` admits an arbitrary existing file as native AAPT2, even if non-executable or for a wrong ABI, and reports `hostReadyToAttemptQa: true` when simulated other tools are present. Scope: reject broken AAPT2 by bounded read-only `aapt2 version` call; do not build, sign or fetch. Preserve existing positive tests by using an explicit process stub rather than a plain-text file. Excerpt tests first; actual imported regression and full project gates only if full checkout/tools are available. Early host tests do NOT establish native ABI or a real Android build.

Coordination: C1 #519, interim #543, current #544, QA Orb #550, support #547 and Original-APK role read; no other team owns these two exact preflight paths in #544. #515 independently contains a newer *file-shape* check on its own source, not a tested version execution/ABI probe. Any intake must reconcile with that branch instead of overwriting its prior safeguards.

Owner source: `at.heinz.deviceai.continuumlocal` / original signer `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23` / existing Render channel only. Staging, signing, deployment and installation are not in scope. A fresh combined full Android/Node/backend/lint acceptance is required; Fold7 separate.
