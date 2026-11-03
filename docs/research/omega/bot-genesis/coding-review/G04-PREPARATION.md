# G04 prospective assigned-role preparation

Exact production freeze: `c5f9b06b02fbe8b80f9472a17784fb000812398c` at
`/workspace/genesis-g04-source`. Original g04 fixture/requirements/tests remain
byte-identical to1dfbba2e and manifest4df194f5. No outcome existed during preparation.

Chief explicitly reduced the frozen maximum of two calls to **one actual call**
to respect the existing real profile budget. This changes the experimental budget;
it must not be compared to the earlier two-call A0 as a controlled improvement.
No production profile or quota is edited. Context cap8192 and runtime caps remain.

Reviewer driver calls real `prepareCodingTask`, then process-private
`createCcc1BotAssignment` with builder `bot-forge`, verifier `bot-lint`, reporter
`bot-echo`, using `profileStoreReader` over the actual Kotlin on-disk store.
`runCodingTask` receives that assignment and the existing isolated executor and
default genuine provider. There is no mock profile, provider, patch or grant.

Prepare the exact V2 classes with the frozen source's
`tools/genesis-workspaces/run-host.sh` (includes ChiefBotConversation and
AiContextCompiler when present), then seed20 through actual `HandoffHostChecks seed`.
Each role lookup starts the actual compiled store reader. Classpath/source/input
hashes must be retained. Old V1 compiled profile classes cannot replace V2.

Actual preflight on frozen g04: assertion RED, zero model calls, no grant.
Native inference and Kotlin compilation are **NOT_RUN at this checkpoint**, waiting
for P0's exclusive Gradle window to finish. Rehash approved model/runtime before
running `run-g04.py g04` exactly once. The separate exclusive reservation file
prevents silently replacing a consumed attempt. Failure consumes this case.

Role success must be observed, not inferred from profile names. Atlas/Mnemos may
be registered without executing a stage. If the model yields no valid candidate,
Lint may never execute. Echo is deterministic canonical reporting with zero model
calls and no independent-test claim. Verify the actual report artifact/hash,
current journal/grant and role-specific Docker receipts. New-process technical
recall runs only after a verified successful repair was actually recorded.
No three-role/learning/Fold7 success follows from assignment alone.

## Pre-inference source amendment

Chief moved the final production freeze to `1595389274acb840c4ab544fa699c9eb2a04bd53`
after full Android QA exposed a missing no-argument Activity reflection method.
Only the Activity wrapper changed; Node/workspace/profile sources remain unchanged.
No g04 inference, grant or V2 class build occurred before this amendment. Driver,
launcher and independent audit now require15953892. The earlier c5f preflight
remains retained as setup evidence. Execution still waits for a successful final
Android gate and explicit CPU release; no automatic inference starts.
