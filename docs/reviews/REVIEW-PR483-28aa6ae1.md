# Review PR #483 – integration/jarvis-on-chatgpt-20261104 @28aa6ae1 (Merge 68cf13e5 = #478 d8fd7f7c + #375 b49fb847) – Prüfer, read-only

## 1) Guard-Merge – BESTÄTIGT (mit 1 Hinweis Mittel)
- `cognitive/ExternalContentActionGuard.kt` byte-identisch zu `0c2d5a16`. Aufruf `BoundedAgent.kt:367` übergibt `proposal.arguments`; danach immer `toolGate.execute` (Z. 386). Kein neuer Bypass-Pfad: einzige weitere `runner.execute`-Stellen sind `AgentToolLayer.kt:350` (ToolGate-intern) und `AgentActivity.kt:7010` (Web-Recherche mit einmaliger Owner-Internetfreigabe, vorbestehend).
- ChatGPT-Tests umgeschrieben (`ExternalContentInjectionGuardTest.kt:89–116`): alle 6 übrigen Negations-/Teilwort-Fälle (send/delete/purchase/uninstall/install/„unterteilte“) bleiben verweigert, jetzt mit fremdem Ziel-Argument.
- **(a) „Nicht verschieben, nur anzeigen“ + move_file:** Absicht bei ChatGPT war eine verneinte Anweisung → Verschieben sollte ohne Rückfrage verweigert werden (Negations-Regex in d8fd7f7c-Guard Z. 28/52). Unter B5 ist move_file reversibel → geht zur Owner-Freigabe. Lokaler Test (nicht gepusht, Quelle `docs/reviews/evidence/NegatedMoveReviewTest.kt.txt`): Nein → **0 Ausführungen**, 1 Rückfrage; Ja → 1 Ausführung, 1 Rückfrage. Garantie „keine Auto-Ausführung“ hält. **Hinweis Mittel:** Gegenüber ChatGPT schwächer – Jarvis fragt trotz „nicht verschieben“ nach, und ein Klick auf Ja führt es aus. Empfehlung: eigener Test + optional „verneinte reversible Aktion → keine Rückfrage“ (dann aber strukturiert, nicht per Wortliste).
- **(b) Frage-Fall** („Wie schaffe ich Platz…?“): von move_file auf delete_file umgestellt. Für delete stärker (verweigert ohne Rückfrage wegen fremdem Ziel, unabhängig vom Wortlaut). Für move **nicht äquivalent**: eine Frage + Modell-Verschiebung geht jetzt zur Owner-Freigabe statt Verweigerung (gleicher Hinweis wie a).

## 2) Cap 3900 + Pool – BESTÄTIGT
- ChatGPTs Pool hat eine einzige `report(slot, …)`-Funktion (`chief/ChiefWorkerPool.kt:156–170`); alle Pfade (DONE Z. 191, TIMED_OUT 197, CANCELLED 151/176/182/200/205/209, BLOCKED/FAILED 208–210) laufen dort durch → `ToolOutputCap.cap(message)` Z. 165 überall. Zusätzlich `WorkerScope.runner` Z. 220.
- `ToolOutputCap` = meine Version (escaped-Gewicht, Surrogat-sicher, Marker „[gekürzt …]“), ToolOutputCapTest 4/4.
- Race-Eigenschaft von 6299cbcf anders, aber erfüllt: `awaitIdle()` wartet auf `unfinishedAssignments` (Z. 130–132), das erst im `finally` **nach** der Zustellung sinkt (Z. 167–168). Doppelzustellung ausgeschlossen (`reported.compareAndSet` unter `synchronized(slot)`, Z. 157–158). Slot wird vor dem Callback entfernt (Z. 164) → Re-Submit derselben ID aus dem Callback möglich (besser als 6299cbcf). Reject bei `RejectedExecutionException` zählt zurück (Z. 95–97).
- Hinweis Niedrig (vorbestehend): `Error` (OOM o. ä.) außerhalb `catch (e: Exception)` → kein Bericht, Zähler bleibt → `awaitIdle` false bis Timeout.

## Tests (Box, JDK 17, Worktree @28aa6ae1)
- Gezielt: InjectionGuard 6/6, BoundedAgent 6/6 + OutcomeEvidence 2/2, FirstThinkingRunE2E 15 (9 grün, 6 PENDING-skip), ChiefWorkerPoolTest 9/9, ChiefWorkerPoolShutdownTest 5/5, ToolOutputCap 4/4, lokal NegatedMoveReviewTest 2/2.
- Voll `./gradlew testDebugUnitTest` (ohne lokalen Test): **2218 Tests, 0 Fehler, 19 übersprungen**.
- `./gradlew lintDebug`: **FAILED, 1 Error** `AndroidOnDeviceSpeechInput.kt:31` NewApi (`createOnDeviceSpeechRecognizer` API 31, minSdk 26) – aus ChatGPT-Basis d8fd7f7c, vor Integration beheben (`Build.VERSION.SDK_INT >= 31`-Guard).
