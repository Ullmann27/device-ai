# Continuum Ω — Autonomous safety integration handoff, 02.11.2026

## Scope and source identity
- Official repository: `Ullmann27/device-ai`. Owner asked for autonomous, conflict-free work on the architecture after Sol PR#358, not for production deployment.
- Independent Draft-PR #359, `research/continuum-execution-atomicity-20261102`, branch forked **exactly** from PR358 commit `699c85e24ace808203993b7fafbef6cb1c9f5e02`. At release of this handoff, **check fresh HEAD again**.
- Other agents/branches deliberately untouched: CodeRabbit PR354 Android UI/click; own GGUF PR356; Sol PR357/358; `main`, APK, signing, productive model weights and owner files.
- Original important independent counterexample: `docs/evidence/sovereign-brain-iv-20261102/independent-baseline-review/BaselineProbe.kt`, which delays a trusted eligibility callback, then cancels/expires before backend invocation, plus a legitimate 8,000-character tool result whose label overflows the model-output limit.

## Implemented components
1. `ToolExecutionStartLatch.kt` provides per-invocation atomic PENDING→COMMITTED or PENDING→REVOKED transition. This is a **logical dispatch decision**, not evidence of completed OS side effects. `AgentToolLayer.kt` `ToolGate` registers and retires these latches, serializes owner cancellation and dispatch commitment via `startCommitLock`, revokes pending calls on timeout/cancel, and **explicitly says UNKNOWN** in reason text if a dispatched call terminates without independently observed result. No automatic retries. The existing durable `ToolReceiptState` values are unchanged; an unknown side-effect outcome still requires operator review. A physical backend may be preempted between commit and execution, so no impossible 'rollback' guarantee is claimed.
2. `ReadOnlyToolPlan.kt` treats a full valid raw tool output plus a label that would exceed `AgentModelOutput.response<=8000` as **unverified/blocked** rather than throwing an uncaught `IllegalArgumentException` or promoting an experience.
3. `LocalPromptFrame.kt` ensures the mandatory local prompt body, including the exact owner request and selected whole evidence, survives intact. Optional history is either included whole or omitted as a whole; advisory never steals mandatory space. `AgentActivity.runLocal` uses it, stops repeating the raw task when compiler context already includes `USER REQUEST`, and reports a precise clarification instead of a model failure when required context does not fit. Existing compiler privacy/revocation checks, screen-frame checks, owner ToolGate and callback orchestration are preserved.
4. New source JUnit regressions: `ToolExecutionStartLatchTest.kt` (commit/revoke, 128 controlled races, delayed-clock timeout/cancel, formatted 8000-char output, short normal output), `LocalPromptFrameTest.kt` (whole corrections, optional history omission, mandatory overbudget fail-closed, Unicode, advisory). **These full module JUnit tests have not yet been executed against the actual repository build.**

## Actual local host verification / honesty
Evidence: `docs/evidence/continuum-atomic-execution-20261102/standalone-host-proof.json`.
- The **exact blob** of new `ToolExecutionStartLatch.kt`, git blob SHA1 `810e8c6c76d43dfd21fc101f86beb35b006a4ce6`, compiled locally on Linux/x64 JDK21/Kotlin1.9 and passed a 1000-iteration concurrent commit/revoke check plus pre-dispatch exclusion checks.
- The **exact blob** of new `LocalPromptFrame.kt`, SHA1 `323252e6c51c4014aa601117b0c80b232392e4bc`, compiled locally using **manually reconstructed Router/PromptBudget stand-ins**; mandatory prompt, Unicode correction, advisory exclusion and whole-history omission checks PASS.
- NO full `ToolGate` or `AgentActivity` build, no real Gradle/Kotlin2.4/JUnit/lint/full Node, no ARM64/Gemma/Fold7 QA, no owner app, no new device verification. No comparison with Sol's historical 1648/1938 results is implied for these new source changes.
- Internal effect on Product: only `SOURCE_INTEGRATED` on an unmerged Draft research branch, not on installed owner APK.

## Review- und Testfortsetzung (02.11.2026, PR359)

- Unabhängiger Gitar-Review meldet am 02.11.2026 **Approved / 1 closed / 0 open findings** für den Worker-Cancellation-Randfall. Ursprünglich wurde ein im `FutureTask` verpackter `CancellationException` fälschlich als `FAILED` verbucht. Änderung in `AgentToolLayer.kt` entpackt den Fehler und meldet `CANCELLED` (oder `UNKNOWN`, falls ein Dispatch bereits feststand). Neuer JUnit-Regressionstest und eigenständiger JDK-`FutureTask`-Hostkontrolllauf (2 PASS) vorhanden. Dies ist **keine** Android-Release-Zertifizierung.
- Echter GitHub-Quelltext `ReadOnlyToolPlan.kt`, Blob-SHA1 `ce3df482e8750f6b570dc1166daefe56e3563369`, wurde bytegleich in ein Kotlin1.9/JDK21-Host-Harness übernommen und kompiliert. Mit kontrollierten Projektinterface-Stubs: **7.990** vollständig erlaubte und bestätigte Ausgabegrößen sowie **210** überschreitende Textgrößen und **3** große Unicode-Ausgaben korrekt blockiert; eine fehlgeschlagene Beobachtung verworfen; **8.204 Fälle insgesamt**. Auch das alte Ergebnis + Beschriftung über 8.000 Zeichen verursacht keinen `AgentModelOutput`-Konstruktorfehler. Der Test verwendet allerdings Stubs für ToolRegistry/Evaluator-Datenklassen und ist keine originale BoundedAgent-Gradle-Ausführung.
- `LocalPromptFrame.kt` konservativ um eine zusätzliche **UTF-8-Bytebudget-Grenze** erweitert: Mehrbyte-Zeichen/Emojis dürfen das Promptbudget nicht umgehen. Geschichte und Modell-Advisory sind optional und werden im Zweifel ganz verworfen; der Besitzerauftrag und seine vollständigen Belege werden dagegen entweder ganz eingebunden oder mit explizitem Klärungshinweis abgewiesen. Exact-source Git-Blob `1d7f168f302c6521cc65ca5e6a8a15bf04e9f57c`, Linux/Kotlin1.9 mit Router/Budget-Stubs: alter und neuer UTF-8-Prompt-Harness PASS. Ein echtes LiteRT-Kontextfenster/Tokencount ist damit **nicht** nachgewiesen.
- Neue JUnit-Regressionsquellen: `ToolExecutionStartLatchTest.kt` (wrapped cancellation, 128 Raceversuche, Timeout/Widerruf/UNKNOWN, Ausgabegrenzen), `LocalPromptFrameTest.kt` (Spätkorrekturen und UTF-8-Mehrbyte-Grenzen). Gegen produktiven Android-/Gradle-Quellbaum weiterhin **NOT_RUN**.
- Der vorherige CodeRabbit-`full review`-Aufruf wurde aufgrund einer in der Zwischenzeit verschobenen HEAD-SHA abgewiesen. Nach finalem Dokumentationscommit muss er für die dann aktuelle HEAD **erneut** gestartet und separat als ausstehend oder erfolgreich belegt werden.

## Next independent verification commands in a real complete checkout
```bash
git rev-parse HEAD
git status --short
./gradlew :app:testDebugUnitTest --tests 'at.heinz.deviceai.ToolExecutionStartLatchTest' --tests 'at.heinz.deviceai.ReadOnlyToolSizeRegressionTest' --tests 'at.heinz.deviceai.cognitive.LocalPromptFrameTest'
./gradlew :app:compileDebugKotlin :app:lintDebug
node scripts/continuum-reliability-qa.mjs critical --out /tmp/continuum-atomic-critical
node scripts/continuum-reliability-qa.mjs full-node --out /tmp/continuum-atomic-node
```
Above commands are **required future actions, NOT executed in this continuation**. Adjust Gradle paths only after checking actual project configuration; failing commands must retain raw stderr/exit.

## Important adversarial tests to add/re-run
- Independent original `BaselineProbe.kt` on actual new `ToolGate` source and all cancellation/timeout edge cases; callbacks that swallow interrupts, blocked injected clock, overlapping cancel/commit, long backend whose side effects may already have started, replay after process restart.
- Inspect **durable receipt** distinctions: commitment before timeout is not equivalent to successful execution; `CANCELLED/TIMED_OUT` status may still mean physically unknown. No retries for side-effecting tools, especially before enabling PR354 click. A separate durable `DISPATCH_COMMITTED/UNKNOWN` transition may be necessary before real UI actions.
- Test real activity long German/CJK/emoji requests and maximum contradictory memory groups; verify `LocalPromptFrame` cannot bypass privacy or screen-frame freshness.
- Ensure 8000-char output overflow is blocked and never yields a successful experience or skill candidate. Compare original instance and exact source SHA under 2.4 Kotlin on existing Render Android QA host or trusted local SDK host; no GitHub Actions.
- Obtain a new independent CodeRabbit/Gitar review of this **actual branch** and resolve all actionable findings before integration.

## Follow-on program
1. **M1** separately finish PR354 user-approved native click + `UNKNOWN_AFTER_DISPATCH` contract and Fold7 owner's verified Settings→Battery mission.
2. **M2** integrate Sol S1–S3 cognition from PR357/358 and source of this patch only after full branch DAG and event-callback compatibility tests.
3. **M3** connect our PR356 alternate GGUF backend through one provider-neutral brain contract; prove real second-model offline task on Fold7, retaining owner memory and ToolGate.
4. **M4** CCC1 local coding workbench, independently checked unknown-bug RED→GREEN then owner-reviewed patch, never autonomous main merge.
5. **M5** realistic voice/3D globe/JARVIS runtime and verified generalization research; no imaginary neural or device claims.

Rollback: do not integrate PR359; no production state was written. If integration fails, revert only this branch's 3 runtime changes and the two new helper files, not Sol/CodeRabbit or owner assets.
