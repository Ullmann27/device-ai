<!-- coderabbit-ai-generated:v1 -->

# Continuum brain integration — checkpoint

Dependent on PR353 at `86032c31a36547888536446c765a719cd6ee5c5c`; isolated branch `coderabbit/continuum-brain-integration-20261102`. M1 remains in PR353, not main. This branch follows the owner's master directive and preserves signing, package, ToolGate, owner knowledge and weights.

## IMPLEMENTED / TESTED / INTEGRATED

`AgentActivity.sendCurrentInput` already calls `LocalConversationContext.inspectRequest`, resets `ContinuumBrainBridge` and the scoped SWB1 dialogue for explicit corrections, and `runLocal` later renders that same ephemeral context. The missing edge was its typed research digest: turns cleared, but old research and freshness metadata survived. The correction now invalidates that channel too. No persistent memory changes.

Red/green: the native host script with JDK17/Kotlin2.1.21 and3GiB compiler heap ran1621 JUnit cases and4 Checks programs. Before:1620PASS/1FAIL (new correction regression). After:1621PASS/0FAIL and all4 Checks pass. The new positive control keeps research for ordinary source follow-ups and context reports; corrected or ambiguous scope removes old research, URL, model turn, freshness metadata and owner evidence. These are deterministic context-quality observations, not a measured Gemma intelligence or throughput gain. The repo-declared Kotlin2.4.0 check is in progress.

A small fixed-scope QA command reuses `runOwnedCommand` and `parseJUnit` rather than creating a new worker or scheduler. `node scripts/continuum-reliability-qa.mjs --list`; `node scripts/continuum-reliability-qa.mjs critical --out /tmp/continuum-qa-NEW`; `full-node` selects all repository Node suites. The directory must be new and outside the checkout. It records PASS/FAIL/SKIP/NOT_RUN, P0/P1 severity, source/content identities, actual exit/signal/cleanup, original JUnit evidence and diagnostics, and generates JSON and marked Markdown. Empty, failed, truncated, placeholder-only or unconfirmed runs cannot pass. All-skipped selected checks block success. Tests3PASS; real CLI gates still pending at this checkpoint.

No new upstream implementation was imported. No CodeRabbit/Autofix integration or independent review approval is claimed. Gradle/lint/APK/Fold7 and settings-to-battery E2E remain NOT_RUN at this checkpoint. Full architecture matrix and final evidence follow before this draft is offered for integration.
