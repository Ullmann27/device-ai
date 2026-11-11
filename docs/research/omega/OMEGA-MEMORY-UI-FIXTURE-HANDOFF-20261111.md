# Gedächtnis: Activity-Testhelfer, 11.11.2026

## Quelle und Verantwortung

- Basis: `5b935b4feaf960bb6973dd4d630f02744e41913f`, Tree `246ff9ffbbb3d17e59147806c90bb0b47a7743ef`.
- Kandidat: `work/omega-memory-ui-20261111`; Root integriert nach `integrate/omega-apk54-20261111` / PR #769.
- Ausschließlich `OwnerObjectStateConversationUiTest.kt` plus dieser Bericht und Rohbeleg. Keine Produktionsänderung, kein verändertes Wartebudget und keine abgeschwächte fachliche Assertion.

## Belegter Fehler

Root-Lauf `0fb80769-cffc-481d-99db-4c0a7ae6a4ba` liefert vier abgeschlossene Fehler in `at.heinz.deviceai.OwnerObjectStateConversationUiTest`. Die unveränderten Ergebnisdatensätze stehen in [baseline-failures.json](../../evidence/omega-memory-ui-20261111/baseline-failures.json).

Alle vier scheitern im jeweils ersten `modelTurn`: Der sofort ausgewertete Stringparameter für `until` ruft `visibleReply` auf. Dieses verlangt mit `last { !it.user }` bereits einen Assistant-Eintrag, obwohl das Testmodell seine erste Antwort noch nicht geliefert hat. `NoSuchElementException` entsteht dadurch vor Eintritt in die Warteprüfung auf `runtime.running`.

Die etwa zehn Sekunden zweier Fehler sind kein Beleg für einen Modell-Timeout: Auch die anschließende Bereinigung besitzt eine eigene Zehn-Sekunden-Wartephase. Welcher asynchrone Zustand dort vorlag, belegt dieses Journal nicht.

## Enger Fix

`until` erzeugt zusätzliche Fehlerdetails erst, wenn seine unveränderte Wartebedingung nicht erfüllt wurde. Nur die diagnostische Antwortabfrage fängt einen fehlenden Eintrag ab und nennt dessen Ausnahmeklasse. `visibleReply` selbst und alle fachlichen Rückrufprüfungen bleiben strikt.

`submit` berührt den bestehenden sichtbaren Chat-Einstieg nur, wenn `activeWorkArea` noch nicht `CHAT` ist. Anschließend prüft es sowohl `CHAT` als auch `conversationActive == true` und sendet weiterhin über den echten `sendButton`. Der Produktionsvertrag `navigateWorkArea` / `focusChatComposer` selektiert Chat ausdrücklich; es handelt sich nicht um einen belegten Produkt-Toggle. Die zusätzliche Zustandsprüfung verhindert, dass ein laufender Dialog nochmals eine möglicherweise ausgeblendete Home-Kachel bedienen muss.

## Abnahme bleibt offen

Kandidat: **Android NOT_RUN**. Während Roots laufender Prüfung wurde kein weiterer Test- oder Gradle-Prozess gestartet. `git diff --check` ist lediglich eine Patch-Formprüfung.

Root muss dieselben vier Fälle unverändert im gemeinsamen Prüfstand ausführen:

- `fiveStepCorrectionUsesActualOfflineEntryAndOwnerPerspective`
- `actualNewSessionClearsThePreviousOwnerObjectState`
- `ordinaryRelatedTaskReachesModelWithBoundedCurrentState`
- `historicalChangeRequestsReachModelWithRetainedHistory`

Klasse: `at.heinz.deviceai.OwnerObjectStateConversationUiTest`. Weiterhin gelten die vorhandenen UI-Quellfilter und `-PcontinuumUiWidgetTests=true`. Es gibt keine neue Testklasse, keine deaktivierte Prüfung und keinen Geräte-, Installations- oder Release-Nachweis. Weitere Fehler nach dem reparierten Testeinstieg müssen anhand des nächsten realen Laufs getrennt untersucht werden.
