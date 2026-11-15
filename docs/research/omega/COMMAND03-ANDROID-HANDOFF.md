# Kommando 03 – native Android integration

Status: WIP / UNVERIFIED

## Source and responsibility

- Integration owner: current Continuum Android implementation task.
- Current base: Code 59, `79c16e57aeb7e0cf4a851e921d7441f7f6384fdf`, published 0.3.58.
- Integration target: `fix/code59-context-complete-20261114` (PR #848).
- Work branch: `integrate/continuum-command03-android-20261115`.
- Approved visual source: PR #850, `0876aa0accdf8154e06e53da42aa5c9492892066` (Kommando 03).

## Authorized scope

Adapt the approved layout to the existing Android application for Fold, ordinary phones and resizable windows. Preserve the original avatar and native orb, existing model, speech, memory, document, research, agents and device tool paths. Use actual native state and data. Do not ship the web research simulation or invented CPU/RAM samples. Keep controls reachable with larger text and keyboard/inset changes. Preserve current package, persistence and permission gates.

Use the current AgentActivity -> ContinuumHomeView -> CinematicWorkstationLayout/Filament paths; no second app, activity launcher or model runtime. Relevant existing contracts: EXP-00/08/11 transport and orchestration compatibility; no new research promotion.

## Verification and integration

Native geometry and interaction tests, existing widget tests, Kotlin compile and source-bound APK build are required. Web-preview checks are not Android evidence. Original asset hashes must remain unchanged. No main merge or installation. The work branch targets the existing integration line and will be kept recoverable with exact commits and test evidence.

## Current checkpoint

Repository, current branch/ref, published release, original assets, instructions and current UI call paths inspected. No native implementation or tests claimed at this initial checkpoint.
