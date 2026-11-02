# Continuum Dialog VI: 9-gate implementation handoff (2026-11-02)

Parent: PR #346, immutable base a69e88937361b01cd7ebb78baa588e4fbf330fcd.
Current working branch: research/continuum-dialog-vi-nine-gates-20261102; PR #347 (Draft, reviewable). No main merge.

## Three substantive rounds
Round A (M1-M3): conservative German app intent parser + read-only ToolGate integration into chat + bounded native result validation, registry constraints and cancellation-safe UI lifecycle. App startup stays on separate consent-required launch_app path.
Round B (M4-M6): code/URL redaction for offline TTS, voice interruption when the owner submits a new request, cancellation of active and pending microphone scopes on pause.
Round C (M7-M9): unconfirmed owner text survives revoked web authority, stale model answer anchors cleared on consent/screen revocation; duplicate failed callbacks do not displace independent user history; exact-source pure Kotlin host regression runner plus JVM tests added.

## Real proof
- Verified GitHub production blob IDs against local JVM source bytes: NativeAppLookup cf10ecfa022f18394ba7a509d7a6bb9dedf347a6, JarvisVoiceProfile c39ef9a9776895fef542e0e7ac03b1e6b071412a, OwnerAppLookupIntent 52fe61624ae1eec84475bacac53818b7cfaad03d.
- OpenJDK 21, kotlinc-jvm 1.9.0: compilation of these three pure Kotlin sources and a local 43-assertion standalone contract harness completed; 43/43 PASS. The committed ContractChecks.kt is structurally comparable but not byte-identical to the executed local harness.
- Standalone parsing harness from exact OwnerAppLookupIntent source: 19/19 PASS.
- New project JUnit classes added but NOT_RUN in Android Gradle. AgentActivity, ToolRegistry, lifecycle and read-only adapter integration untested as complete Android project.
- New-HEAD Android Gradle, LiteRT, Lint, Render, Fold7 hardware, spoken quality and autonomous Gemma tool selection: NOT_RUN. Never confuse pure Kotlin JVM success with device QA.

## Highest priority next owner-gated actions
1. Pin exact current PR #347 HEAD, verify diff and ancestry of PR #346/#345/#344. Do not overwrite their branches.
2. In full Android QA environment run complete Gradle compilation + JVM/JUnit + LiteRT + lint + unsigned build on that SHA. Fix any issues on this same research branch with new evidence.
3. Execute live Fold7 opt-in tests for German offline ASR/TTS and permission revocation, user text to safe find_app ToolGate, explicit separate launch_app confirmation, window postconditions and cancelled lookup callback handling.
4. Test F08 with late negation/unknown referent and a deliberately failed local Gemma turn; ensure failed owner statements never become confirmed SWB1/EWM2 memories.
5. Only then propose a reviewed stacked merge/release with the existing owner signature and version code higher than the actually installed build; do not produce or install an owner APK without approval.

## Safety
GitHub private repository preserved; no Main write/merge, force push, model weights, user data, credentials, owner signing, or direct installed APK mutation. Existing CCC1 isolated repair ownership maintained. No unrestricted Android tapping/swiping or unsupervised sending/installation enabled.

QA and detailed milestone mapping: docs/evidence/dialog-vi-20261102/NINE-MILESTONE-QA.md.
