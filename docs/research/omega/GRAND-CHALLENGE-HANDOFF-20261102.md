# Grand Challenge — consolidation checkpoint, 2026-11-02

Repository Ullmann27/device-ai; branch research/continuum-grand-challenge-20261102; Draft PR #349. Integration commit f56949d8f0b05984ed517a010c4aefd121f89529 joins PR348 4d07bdf8c12a5cc3d93e6a29a2c23c8a03027460 and Fusion privacy commit 66b648b523073fe866e51df5528b4639efef0359. Later documentation/QA commits are discoverable with `git log`; always fetch and verify actual remote HEAD. No other branch reset, force push, main merge or owner release.

## Verified ancestry and actual interfaces

PR344 cee7d2c974ae9e01ec41edfc03ee41b2b582c7bb → PR345 cb700e03e4d6ed2ab5e8cd4283692e44280d9d62 → PR346 a69e88937361b01cd7ebb78baa588e4fbf330fcd → PR347 15ccf8b1cb9ae386e7e9e32cb42aba05376ab64f → PR348 4d07bdf8c12a5cc3d93e6a29a2c23c8a03027460. Fusion privacy finish 66b648b5 was absent from the latter three; this branch explicitly brings it back. Existing changes were inspected; no blind old release import. GitLab !299 is only partially triaged in docs/evidence/dialog-vii-20261102/REPAIR-AUDIT.md; authenticated MR/source equivalence remains UNVERIFIED.

| Capability | Actual caller/connection | Status and remaining gate |
|---|---|---|
| German app discovery | AgentActivity.sendCurrentInput → OwnerAppLookupIntent → existing ToolGate find_app → DeviceToolExecutor → AndroidInstalledAppLookup/NativeAppLookup | Source integrated; visible launcher apps only; ambiguity refuses auto-launch |
| Native app launch | ToolGate CONFIRM → DeviceToolExecutorRunner → AndroidDeviceAppActions → VerifiedForegroundLaunch → ScreenObservationBus | Requires literal package and fresh observed foreground; device NOT_RUN |
| Screen interpretation | Owner-enabled ContinuumScreenObservationService → BoundedScreenReader → read_app_screen CONFIRM → JarvisUnifiedEvidenceContract → ScreenObservationPrompt → fresh Gemma inference | Bounded/redacted, source/revision/hash/expiry, grant rechecked; incomplete screen refused; no taps/swipes/visual fallback |
| Screen privacy | BoundedAgentOrchestrator sees read_app_screen → clears checkpoint, skips persistent ExperienceStore/reputation | New RED→GREEN exclusion; ordinary verified device experiences retained |
| Voice | Explicit mic → AndroidOnDeviceSpeechInput → composer; existing TTS → JarvisVoiceProfile | On-device recognizer required; offline installed German voice; code fences/URLs redacted, lifecycle cancel/barge-in; no measured accent/latency |
| Context/brain | LocalConversationContext → revision-aware evidence/reflex/EWM2/SWB1 → existing local Gemma | Inherited corrections; unsuccessful owner turns provisional; no second memory |
| Learning | Existing bounded agent experience gates; CRE1/CDX2 isolated candidates | Screen data excluded; no new strategy learner/loop guard; no weight promotion |
| CCC1 | Existing checkout/source-bound worker → isolated repair → independent test → review receipt | Real disposable host fixture passed; Android/Termux NOT_RUN |

No structural deletion or competing runtime was necessary. Dialog VII already removed the duplicated teamleader source check; legacy backlog hashes deliberately preserved. Real Dialog V–VII Android compilation had not been established by their limited pure-source tests: the combined gate must decide acceptance.

## Evidence already saved

Fusion exact source through 66b648b5: full host 1590 JUnit /292 classes +4 Checks PASS (compile-only LiteRT stand-in; not real Gradle); focused31 PASS; privacy RED1/2 failing → GREEN8/8 incl ordinary learning; backend64 PASS. Broad Node635/639: three historical owner0324 approval failures plus detached-process startup timeout under parallel load; unchanged process suite rerun12/12 PASS. Preserve both outputs in docs/evidence/fusion-iv-20261102/finish. Do not call the full broad suite green.

CCC1 real disposable repair at source cb700e03: candidate dbfe30a8849f6dad700c8ee6881f20f2395b9533; baseline3 failures, candidate4/4, independent4/4, main blocked, patch da5c8bbf0e3a012d3cab4d8877c73f9c21ad6beec04df3411f169c2dd52a5a43. Device NOT_RUN. Existing experiment archive remains intact.

Combined branch host and canonical Node gates started after integration. First host invocation failed before compilation because javac was absent from PATH; retried with existing JDK21. Final outcomes belong in committed QA results and PR349, never substitute parent results. Existing Render pipeline is reused, with this exact branch permitted and all five Dialog suites plus seven Fusion suites required in architecture proof. No new pipeline.

## Open-source and research

Read FUSION-IV-UPSTREAM-AUDIT.md and third_party/tokenarc-open-jarvis/NOTICE.md. TokenArc 04873998933830ce582018fe4664ebce5ec64d2f MIT: narrowly adapted native launcher/traversal API shapes; saved API discovery, not measured engineering hours. Bounded ownership/cancel/postcondition checks are local corrections. OpenJarvis 45504acbb49459993d60e19d5b37c85fbc0c35ed Apache2 and Dix dff115ca4f30b74240fc6199b55b4770474e7ac8 MIT are audited references only. Fifteen byte-exact source/license snapshots verified; scoped Git whitespace exemptions preserve original artifacts, not application formatting or tests. No external runtime/dependency/model imported. PocketClaw, Sherpa, UI-TARS, OpenHands, browser-use, llama.cpp/whisper remain NOT_AUDITED. No AGPL/BSL code imported.

Synapse III full retraining regression remains rejected; frozen-base two-weight CDX2 adapter remains SHADOW with synthetic E/consumed C results, not general intelligence. Old corpora/weights unchanged. Do not repeat those experiments or claim new research from consolidation.

## Remaining risks and next priorities

1. Read combined exact-source host/Render failures and fix real compilation/test defects before adding action types. Parent Render cee PASS is not this branch's proof.
2. Run owner-approved Fold7 matrix: missing/revoked accessibility, wrong/stale window, launch confirmation, STOP, session change, mic permission pending during pause, on-device recognition unavailable, offline TTS, code confidentiality, screen injection and no persisted screen experience. DEVICE_NOT_RUN now.
3. Extend tested app discovery to verified launch via existing planner; keep exact consent/observed postcondition. No generic taps, typing, swipe, screenshot or visual model is implemented here.
4. Audit pinned PocketClaw/Sherpa/UI-TARS only when a missing capability is explicit; inspect artifacts/model licenses/ABI/memory before import.
5. Existing BoundedAgent lacks measured repeat-progress guard. OpenJarvis warning count issue is static, not a passed runtime reproduction. Keep bounds and no second worker.
6. Establish authorized hardware measurements before claiming RAM, energy, voice quality or unknown multi-step gains. GitLab MR299 full comparison still requires its actual source access.

Protected: main, owner knowledge/weights, signer/publisher/package and installed APK. No release/installation authorization. Source integration, host tests, Android build and device performance are distinct gates.

## Copyable continuation order

Continue Ullmann27/device-ai PR349, research/continuum-grand-challenge-20261102. Fetch actual remote HEAD; read AGENTS.md, this handoff, upstream audit, Dialog VII audit and committed QA outcomes. Preserve both parents f56949d8 and privacy66b648b5. First resolve exact-source Android/Render gate and check all required JUnit classes; never reuse parent PASS. Then run the explicit Fold7 negative/positive matrix. Only after those gates, implement one independently tested missing safe action through existing ToolGate/observer. Do not redo CDX2 training, add another DB/agent/queue, change frozen tests/weights/signing, merge main or publish an APK. Commit/push each reviewed change and update this handoff with exact SHA and actual evidence.

## Follow-up structural regression repair

Two source assertions still expected old bridge access and old context-render signatures. RED reproduced both. GREEN: four assertions/tests pass, three explicitly optional native integrations skipped. Assertions now require real bridgeReady dispatch ordering, bounded Internet mode, owner veto, active research grant, session identity, screen exclusion, and successful response vs provisional unanswered input. No runtime requirement removed. Exact logs (whitespace normalized only) in docs/evidence/grand-challenge-20261102/structural-*.txt.

The expanded `.github/continuum/*test.mjs` run is broader than the Fusion 639-test scripts suite; it exposes additional failures (including historical speech, memory, approval and source-string expectations). These are NOT automatically classified as baseline. Preserve raw output and triage separately; do not claim the combined branch fully accepted. Render service srv-dbk47a7lk1mc73aq51u0 uses existing QA script; first run dep-dbk47aflk1mc73aq52g0 tests 87994e71b0ce80878d3d9774e0e032a4829a0e84. Follow-up runs must explicitly match later HEAD. No published owner binary.
