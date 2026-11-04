# Review integration/continuum-grok1-369-372-20261103 @b49fb847 – S4/B5 + Race-Fix (Prüfer, read-only)

## (1) S4/B5-Integration (9ec6f3f8) – BESTÄTIGT
- `ExternalContentActionGuard.kt` und `ExternalContentInjectionGuardTest.kt` sind **byte-identisch** zu meinem Fix `0c2d5a16` (`git diff --quiet` = 0). Aufrufstelle `BoundedAgent.kt:367` übergibt `proposal.arguments` wie bei mir.
- Entscheidung nach Herkunft (Ziel-Argumente müssen aus dem Owner-Auftrag stammen) + Aktionsrisiko (High-Impact: DESTRUCTIVE, install/uninstall, send/post/share/upload, kaufen/zahlen); keine Verb-Wortliste mehr.
- `move_file` (CONFIRM, reversibel) → normale Owner-Freigabe; `FirstThinkingRunE2ETest` schritt4a (Nein: nicht ausgeführt, REJECTED) und 4b (Ja: genau 1 Ausführung nach Freigabe) grün.
- Injiziertes delete aus Werkzeugausgabe: 0 Ausführungen, 0 Rückfragen (`injectedDeleteInAToolResultNeverReachesExecution` grün); send an fremden Empfänger / purchase ohne Owner-Ziel werden verweigert (Unit-Test).
- `FirstThinkingRunE2ETest.kt`: keine „S4 zu streng“-Ignores mehr; die 6 übersprungenen sind PENDING-Platzhalter fremder Bots; `OwnerStatementModality`-Assertion (#371) wieder aktiv. Keine Garantie verloren.

## (2) Race-Fix ChiefWorkerPool (6299cbcf) – BESTÄTIGT (mit 2 Hinweisen)
- `ChiefWorkerPool.kt:93–100`: `onReport` wird vor `slots.remove` ausgeführt, `remove` im `finally` → `awaitIdle()` kann nicht mehr zurückkehren, während ein Bericht unterwegs ist. Reihenfolge korrekt.
- Doppelzustellung ausgeschlossen: `reported.compareAndSet` (Z. 94). Abbruch/Timeout/Exception/Shutdown-Interrupt enden je in genau einem `report()` (Z. 101–121).
- Kein Deadlock: `submit` ist `@Synchronized` (Z. 59), die Zustellung hält keinen Lock; Callback-Exceptions schluckt `runCatching`.
- Cap-Patch enthalten: `ToolOutputCap.cap` in Z. 98 (Bericht) und Z. 130 (WorkerScope.runner).
- **Hinweis Mittel – Verhaltensänderung:** Während der Zustellung zählt der Slot noch. Ein Callback, der dieselbe ID neu einreicht, wird abgelehnt (lokal gemessen: `accepted=false`); bei vollem Pool auch neue IDs. `awaitIdle()` aus dem eigenen Callback kann nie true liefern (gemessen: `false` nach Timeout). Routinen dürfen sich also nicht synchron aus dem Callback neu einplanen.
- **Hinweis Niedrig (vorbestehend):** `report()` wird nur für `Exception` erreicht. Ein `Error` (z. B. OOM/StackOverflow) aus `factory`/`future.get` gibt den Slot nie frei und liefert keinen Bericht. Empfehlung: äußeres `try { … } catch (t: Throwable) { report(FAILED, …) }`.
- Blockierender Callback belegt einen `runners`-Thread (fest `maxConcurrent`) und den Slot – wie vorher, jetzt zusätzlich die Kapazität.

## Tests (Box, JDK 17, Worktree @b49fb847)
- Gezielt: `./gradlew testDebugUnitTest --tests '*FirstThinkingRunE2ETest' --tests '*ExternalContentInjectionGuardTest' --tests '*BoundedAgent*' --tests '*ChiefWorkerPool*'` → FirstThinkingRunE2E 15 (9 grün, 6 PENDING-skip), InjectionGuard 4/4, BoundedAgent 6/6 + OutcomeEvidence 2/2, ChiefWorkerPoolTest 3/3, lokaler Stresstest 3/3 (20 Runden × 20 Jobs mit Exceptions/Cancels: genau 1 Bericht je Job, awaitIdle true). Stresstest nicht gepusht; Quelle: `docs/reviews/evidence/ChiefWorkerPoolStressReviewTest.kt.txt`.
- Voll (ohne Stresstest): `./gradlew testDebugUnitTest` → **1955 Tests, 0 Fehler, 17 übersprungen**.
