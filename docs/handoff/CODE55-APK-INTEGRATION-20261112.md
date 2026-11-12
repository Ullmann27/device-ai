# Code55 APK integration — 12 November 2026

## Canonical continuation

- Owner request: integrate Fable's latest cockpit work, repair remaining branches, build the next owner APK.
- Integration owner: Astra; branch `integrate/code55-apk-fable-20261112`.
- Parent: PR #799 `585d660a1c8ab8ca31822daae1714f67eec9d107`.
- Fable PR #808 `7c1fd7baf06e6753f6285d7b13baf4f0585bb916` merged locally at `2e0668165caf29d9ef000a40d870866c1f466107`.
- Target: Code55 / 0.3.54 / `at.heinz.deviceai.continuumlocal` / arm64-v8a.
- Existing experimental contracts EXP-05, EXP-06, EXP-07, EXP-11 and EXP-12 remain unchanged.

## Work in progress

1. Correct monitor light placement under full monitor rotation and preserve cinematic bloom with live pixels.
2. Port #793 voice readiness/recovery; explicit PAUSED state must remain paused across foreground changes.
3. Port #796 missing-evidence validation controls without promoting research or weakening gates.
4. Port only missing #802 window/time/sentinel guards; retain newer #803/#804 scroll and rendering contracts.
5. Run combined Android checks, source-bound host visuals and the ARM64 release build.

Already contained in #799: #801, #803, #804-807, #787 and #788. An open PR is not proof of missing code.
The historical catalog contains additional research candidates; this pass does not claim a complete project-history audit.

## Current evidence

Status: SOURCE_INTEGRATED / WIP. New combined tests and APK build: NOT_RUN.
Existing parent test results do not verify this candidate. Fold7/GPU/thermal measurements: NOT_RUN.
No main merge or device installation.

## Build environment

Reuse Gradle 8.11.1, JDK17, Android SDK36/build-tools35 and the existing dependency cache.
Heavy work is serialized through `/workspace/scratch/01387b612451/agent-gradle.lock`.
Only redundant, byte-identical downloaded dependencies and reproducible generated caches were removed to free disk.
Original source, APKs, signing material and test evidence were retained.

## Resume and rollback

Read this document, the three repair handoffs and the current remote HEAD. Inspect actual results before making claims.
Rollback the new repair commits or return to parent #799; do not reset another active branch.
