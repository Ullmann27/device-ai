# One real read-only cognitive action

Goal: reuse the existing main-chat BoundedAgentOrchestrator for a natural storage-check request, confirmed-memory retrieval, a typed plan, ToolGate, actual bounded local measurement, receipt-backed evaluation, episodic persistence and response. No additional brain, queue, app or permission system.

Architecture choice: deterministic BootstrapBrain -> small ReadOnlyToolPlan AgentModel/AgentEvaluator adapter -> existing BoundedAgentOrchestrator. The generative chat already uses this orchestrator. For get_storage_info use Java File filesystem statistics on the app's own data volume, on Android and host, without privileged shell or scanning personal files. Other device tools retain their existing runner and privileges; their string-only exit contract does not establish positive learning evidence.

Alternatives: calling the old bootstrap gate directly leaves the two orchestration paths disconnected; creating another supervisor duplicates state/permissions. Both are rejected. Model generation is unnecessary for this fixed read-only measurement; do not report a deterministic plan as neural inference.

Tasks (execute inline, preserve exact test outputs):

- [ ] Write native regressions demonstrating that rejected/replayed tool results can otherwise become SUCCESS with a permissive evaluator. Compile the actual source graph; observe RED; constrain positive episodic outcomes and retain actual call/output hashes in existing Experience.sourceIds; observe GREEN.
- [ ] Add ReadOnlyToolPlan and DeviceStorageProbe with real-filesystem E2E and negative tests (permission denial, duplicate receipt, malformed plan, missing volume, cancellation, forged prose). Keep existing registry/ToolGate as authority. Verify actual receipt hashes and persistence with existing signed ContinuumRuntimeStore after reopening.
- [ ] Route the existing Android bootstrap read-only branch through this adapter and existing orchestrator. Exact user task, session/cancel checks, existing memory/experience stores and checkpoint flow. Only local storage's typed measurement may establish a positive episode; no owner-fact or weight mutation. App-volume storage needs no Shizuku connection.
- [ ] Add an opt-in native Kotlin regression hook in the existing Node inventory so the same worker can periodically run this integration when its compiler is configured. Missing compiler is NOT_RUN, not a fake successful JVM test. Retest changed modules, preserve full-suite release/infrastructure blockers, publish actual code/evidence and continuation in PR179.

Review focus: fresh output versus replay; failed backend text versus actual measurement; remembered/private text never authorizes tools; cancellation/session changes never store an unverified success; filesystem measurement is a dated app-volume observation, not timeless owner knowledge. Native JVM tests are not Android UI/device/Keystore/Gradle proof. Full SDK/phone validation remains required. Neural final set is already consumed; no additional training or SWB2 promotion.

Progress/rulings append here. Base:558b10b5 (PR179). Previous complete native evidence is already pushed in PR178/179. This scope implements the first real observation, not a claim of the entire autonomous model-generated coding/CCC1 loop.

Task1 COMPLETE: actual selected Kotlin graph16JUnit PASS after RED2failures(SUCCESS from denied/replayed tools), then RED1failure(false model reputation update). Guard positive episodes/reputation against missing or failed fresh tool outcomes; retain actual call/output SHA references. Exact source-blob manifests and RED/GREEN logs in docs/evidence/jarvis-vertical-20261027/outcome-safety/. No UI/tool adapter change yet.
