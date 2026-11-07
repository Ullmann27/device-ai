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

## Nachtrag: zweiter unabhängiger Gitar-Befund und frühe UTF-8-Grenze

- Gitar meldete nach Einführung des konservativen UTF-8-Promptlimits einen zweiten realen Randfall: `LocalChatPromptBudget.acceptsCompleteTask` prüfte nur UTF-16-Zeichen, sodass ein großer CJK-/Emoji-Auftrag zunächst zugelassen und erst nach der kognitiven Vorbereitung abgewiesen wurde.
- Produktquelltext `LocalChatPromptBudget.kt` wurde deshalb um **denselben konservativen UTF-8-Byte-Check** ergänzt; Eingaben über 7.900 Zeichen **oder** UTF-8-Bytes erhalten früh `CLARIFICATION` und erreichen nicht erst den Modellaufruf. Eigener JUnit-Grenztest für ASCII/CJK/Emoji ergänzt.
- Exakte Quelle des Früh-Gates Git-Blob `1db85d0b57d35c5ee1e5c5f69d92a62bd918f009`; gemeinsam mit exakter neuer `LocalPromptFrame.kt` auf Linux/JDK21/Kotlin1.9 mit kontrollierten Klassifizierer-/Router-Stubs kompiliert. Grenzfälle **PASS**; der tatsächliche Android-Lauf, reale Modelltokenlänge und vollständige Gradle-Prüfung sind weiterhin **NOT_RUN**.
- Das zweite Gitar-Review hat den Befund als `Approved with suggestions` angemahnt; die Abhilfe ist committet. Eine neue Bestätigung durch unabhängige Nachprüfung ist separat abzuwarten; **nicht** aus einem Commit selbst ableiten, dass der Review geschlossen ist.
- Der bisherige CodeRabbit-Aufruf war zwischenzeitlich wegen bewegter HEAD-SHA ungültig geworden. Ein neuer Review ist erst auf der stabilen finalen SHA belastbar.
- Die isolierte Quellkompilierung mit Stubs beweist nur den geprüften Ausschnitt. Es gibt **keinen** vollständigen Android-Quellbaum im lokalen Host, und Render bietet aktuell keine unverändert auf PR359 zeigende QA-Instanz. Keine bestehenden QA-Dienste für fremde Branches umkonfiguriert, keine neue Dienstinstanz erstellt.

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

## Nächster Integrationsmeilenstein: quellgebundener Android-Gesamttest (neue lokale QA)

**Code auf diesem Forschungsbranch:** `scripts/continuum-atomic-acceptance.sh` und `scripts/continuum-atomic-acceptance.test.mjs`. Kein GitHub-Actions-Workflow, kein weiteres ToolGate, kein Cloud-LLM und keine Änderung an Besitzer-APK/-Signierung.

Das Skript ist ein manueller, offline-fähiger **Abnahmevertrag** für einen echten lokalen Android-SDK-Host oder den von Besitzern bereits betriebenen QA-Rechner. `--expected-head` muss die volle 40-stellige Checkout-SHA sein; `--out` muss ein neuer absoluter Pfad außerhalb des Git-Quellbaums sein. Es verweigert einen Dirty-Worktree, einen anderen Commit, `main`/Release-Branches oder einen überschriebenen Ergebnisordner.

Es ruft **mit echten Quellen** `gradlew --offline --no-daemon --rerun-tasks :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` auf und verwertet die echte bestehende `readJUnitDirectory`-Logik. Ein altes JUnit-XML zählt ausdrücklich nicht: Startzeit, Aktualität der XML-Daten und drei notwendige Klassentests werden kontrolliert:

- `at.heinz.deviceai.ToolExecutionStartLatchTest`
- `at.heinz.deviceai.ReadOnlyToolSizeRegressionTest`
- `at.heinz.deviceai.cognitive.LocalPromptFrameTest`

Danach laufen die **bestehenden** `continuum-reliability-qa.mjs` Profile `critical` und `full-node`. Ihre Reports müssen `PASS`, `sourceUnchanged=true` und genau die erwartete Git-SHA enthalten. Jeder fehlende Beleg, unerwartete Abbruch und jede negative Prüfung führen zu `FAIL`. Die maschinenlesbare `acceptance.json` enthält die sechs Prüfgrenzen; **fehlt die Quittung wegen eines Schreibfehlers, darf der Shell-Prozess nicht mit Exit 0 enden**. Alle Befehle laufen ohne produktive Signierung; kein APK-Build oder -Install.

**Abnahmebefehle (noch NICHT ausgeführt im vollständigen PR-Checkout):**
```bash
node --test scripts/continuum-atomic-acceptance.test.mjs
bash -n scripts/continuum-atomic-acceptance.sh
git status --short
bash scripts/continuum-atomic-acceptance.sh \
  --expected-head "$(git rev-parse HEAD)" \
  --out "/tmp/continuum-atomic-acceptance-$(git rev-parse --short=12 HEAD)"
```

Die Skript-Negativtests bauen ein **synthetisches eigenes Wegwerf-Git-Repository** mit künstlicher `gradlew`, JUnit-Ausgabe und künstlichen Node-Prüfreports. Sie erzwingen Abweisung bei falschem Commit, geschütztem Branch, verändertem Checkout, fehlgeschlagenem Gradle, leerem, altem oder unvollständigem JUnit, anderem Commit in Node-Evidenz sowie bei fehlgeschlagenem Receipt-Write. Ein erfolgreicher synthetischer Positivfall darf **nicht** als echter Android-Test ausgegeben werden.

**Belegstand:** Die ursprüngliche kleine synthetische QA-Harness-Version wurde zuvor mit Node22 / 1 Hosttest PASS geprüft (siehe `docs/evidence/continuum-atomic-execution-20261102/acceptance-harness-results.json`). Seitdem wurde der Abnahmevertrag um Build-Neulauf, frisch generierte JUnit-Belege, notwendige Testklassen, verifizierte Node-SHAs und Schreibfehler-Behandlung erweitert. **Die letzte erweiterte Version ist auf GitHub gepusht, aber bislang nicht erneut in einem vollständigen autorisierten Checkout ausgeführt: `SOURCE_INTEGRATED / QA_NOT_RUN`.** Auch die synthetischen Fixture-Tests für diese letzte Revision benötigen erneute Ausführung. Keine irreführende Übernahme älterer PASS-Quittungen.

**Sicherheits- und Rollbackgrenze:** Dieser zusätzliche Abnahmevertrag ist nur ein Prüfprogramm; er schaltet keine ToolGate-Aktionen frei und ändert die tatsächlich installierte Android-App nicht. Zum Zurücksetzen den Draft-PR #359 nicht integrieren. Bei unvollständiger Android-SDK/Gradle-Umgebung `UNAVAILABLE` und Rohlogs erhalten, nicht als PASS melden.

