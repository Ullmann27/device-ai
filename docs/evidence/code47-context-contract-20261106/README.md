# Local follow-up frame source contract

Base: c3d2cdc5d84786b436b0f2eef7e4f5b8a53a218e. Production is byte-identical. Only the existing integration assertion changes; evidence files document its verification.

The old assertion required inline LocalFollowUpTaskFrame.compose. The real integrated code creates followUpFrame with composeFrame and uses followUpFrame.task. The replacement verifies untouched current text/resolved question, same-session owner evidence only outside bot/planning, actual agentTask dispatch, the model callback task entering the compiler, and deduplication proof bound to the actual promptBody. It operates on codeOnly(local), so comments cannot satisfy the contract. Existing authorization, full-task admission and memory-isolation checks remain.

## Results

- Original file:21 tests,20 PASS,1 expected failure.
- Repaired file:21/21 PASS.
- Exact previously failed android-source-contracts group:64/64 PASS. Command and XML are adjacent.
- Eight independent source mutants:8/8 expected failures at the same intended context-contract assertion. These cover clipped current owner text, removed prior owner evidence, reversed private boundary, ignored frame result, disconnected execution task, disconnected compiler task, proof against the wrong prompt body and a correct-looking comment decoy.

The mutants were written only into scratch overlays. The overlays expose the unmodified repository through read-only use of symlinks, replacing only AgentActivity.kt with a concrete mutated copy. The actual test file ran with each overlay as its working directory. Original production hashes were checked again afterward. No production stubs, compiler or device execution were used.

## Focused commands

```bash
node --test --test-reporter=tap .github/continuum/android-integration-wiring.test.mjs
node --test --test-reporter=tap --test-name-pattern='^cognitive runtime owns the single chat path, privacy boundaries and background diagnostics$' /workspace/scratch/b31a9d731016/wiring-context-contract/.github/continuum/android-integration-wiring.test.mjs
```

The second command runs from the corresponding mutant overlay directory. Adjacent patches and source hashes identify each input; raw TAP outputs show the intended assertion failure, not a syntax/import failure. These are static source checks, not Android runtime coverage.

## Additional context contracts

The later full Node run identified two additional stale checks. The dialogue contract now follows the actual LocalDecisionPromptFrame essential call into LocalPromptFrame; its own requiredTail and requireHistory must remain connected, even if a later optional wrapper call still looks correct. It verifies the centralized four-turn cap, untrusted renderer and explicit partial-output completeness. The grounded-recall contract requires the whole original answer through MEMORY_RECALL rather than an invalidating generic assistant reply. All three changed Node files pass25/25; eight additional negative source controls also fail at the intended assertion. Combined with the first eight this is16/16 negative controls, not a device-runtime result.

## Real persistence UI verification

The old four-case ContinuumChatPersistenceUiTest was executed against the exact existing Gradle testworker classpath and real c3d2cdc Activity. It reproduces both rememberContextTurn NoSuchMethodException errors. Only the test class was then recompiled; the same real Activity/resources/Robolectric runtime passes4/4. The tests use the Activity-installed onTurnRecorded callback through context.record, await actual JSONL persistence, check completeness metadata, verify restore does not add duplicate rows and confirm deliberate invalidation stays cleared across a real Activity restart. No product helper or production stub was reintroduced.

The first standalone test compilation selected the runtime JAR before the friend directory and correctly refused an internal getter. The corrected invocation prepends the genuine debug-class directory as well as declaring it a friend path. The raw initial compiler diagnostic is retained; it was a runner setup issue, not a production error.

Exact77-entry classpath, red/compile/green command arrays, working directory, absolute real resource configuration and baseline class hashes are recorded alongside. Temporary files and test data use the isolated evidence directory. No Gradle task or production compiler was run for this verification. Root owns subsequent combined verification and the separately reported STOP-owner-correction issue.
