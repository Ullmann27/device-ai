# Neural continuity implementation plan

Goal: connect existing GitHub research, real encoder learning, console memory and offline Android.
Architecture: SWB1 interoperable encoder, bounded replay, multitask validation, separate audit, owner-selected import.
Spec: ../specs/2026-10-14-neural-continuity.md
Execution: native, under the owner's explicit autonomous implementation instruction.

- [ ] Add Node tests for gradient learning, corruption, replay, promotion/rollback, restart and dialogue continuity; run red.
- [ ] Implement portable-subword.mjs, neural-curriculum.mjs, neural-core.mjs and neural-dialogue.mjs; integrate into zero-cost-core and progress-watcher.
- [ ] Add Kotlin import/replay protection tests and cross-runtime fixture checks; implement import and guard both tasks in ContinuumLearningCore; expose document picker in AgentActivity.
- [ ] Run Node suites, Kotlin learning tests, publish an atomic tree onto current PR head, check full Android CI.
- [ ] Pin the default-branch scheduled workflow to verified source, preserve state history and inspect a real training cycle.

Review focus: corrupt state must not reset memory; test strings must not enter training/replay; rejected candidates cannot alter active bytes; private memory cannot enter exports; scheduled jobs and owner turns must share a state lock.
