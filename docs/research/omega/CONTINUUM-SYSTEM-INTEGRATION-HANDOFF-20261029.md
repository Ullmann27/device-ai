# Continuum system integration — working handoff

## Owner scope and fixed design

Integration branch `integration/continuum-brain-system-20261029`, starting at PR241 code `4c697c8f04fbb5b0477ae5e48b3d052cce383d34` on PR238 `a3fdc17958d4e552bd3b74cf611c9fd40480f269`. This is a review candidate, not the installed Fold7 app. EXP-01/02/03/05/07/11/12; existing learning core, EWM2, ToolGate, CCC1 ledger/backlog and worker remain authoritative. No APK, model downloads, private data access, signer, production merge or new worker/memory database.

Verified source snapshots on entry: PR227 `05db7e38d70d886b305a547b5b5b628272c5ddfa`; PR237 `f54327b906a67d4f40862b5dd25a6b72e6015bc7`; PR238 `a3fdc17958d4e552bd3b74cf611c9fd40480f269`; alternative PR239 `30fbef96182ed83ee55a1e6ec732d1c533df4a51`; ARGON PR240 `67888e3323f5f30964202e901034cdf80dddb456`. Recheck before publication. PR239 overlaps and removes stronger PR238 receipt/ledger protections: compare selectively, never merge wholesale. PR236 remains a separate release blocker.

## Implementation and verification plan

1. Preserve PR241 raw evidence and final handoff in its original branch; bring its documentation checkpoint into this branch without changing tested code. Its 371 Node PASS apply to that code only.
2. Reproduce and port only the post-base PR227 unresolved-pronoun fix in `intelligence/ewm/Ewm1Workspace.kt` plus its exact regression; retain later birthday/relationship corrections.
3. Move the three ARGON F1 Kotlin implementations into the main source set, preserving one canonical implementation and published tests. Connect bounded, session-only dialogue diagnostics through `LocalConversationContext`; connect same-turn, exact-authorized evidence/relation and routing diagnostics through `ContinuumCognitiveRuntime`. Never use rewrites, hypotheses, inferred relations or calibration as answer/prompt/permission authority. Reset/eviction, revocation, identity, observer failure, bounds and unchanged-answer tests required.
4. Add the missing date/time-zone anchor to the existing context compiler using its injected clock; test midnight/time-zone behavior and context budget. No timeout increase or extra model call.
5. Preserve F2 editable offline research scripts/results; execute their existing bounded Python tests. Do not activate GPU/cache/storage/performance proposals without Fold7 evidence.
6. Bring F3 pure functions into the existing CCC1 pipeline as a read-only shadow comparison, with no second journal or queue. Reproduce and fix stale-patch credit/capability gaps first. CCC1 `recordSession` and independent verification remain the only repair-learning authority; test malicious/stale reports and observer errors.
7. Run focused real Kotlin/Node tests, then the existing JDK17/Gradle8.11.1/SDK36 standalone-QA full unit/lint/parity gate and CCC1 Node gates on a clean exact commit. Capture raw XML/logs, exitcodes, commands, versions, source/dataset hashes. No device claims from host tests.
8. Commit/push each coherent tested block to this official branch, keep a Draft PR, append final source identities/evidence/results here. Leave unsafe, incompatible or unverified features explicitly SHADOW/OFF with their exact blocker.

Current status: source integration NOT_YET_TESTED. The owner has explicitly requested autonomous implementation and parallel specialist work; no additional design approval is needed for these reversible isolated changes. Agents have disjoint file ownership. Original branches and the old dirty local checkout are retained untouched.
