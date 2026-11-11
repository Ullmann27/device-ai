# App-Namensrückfrage: STOP-Fixturekorrektur – 11.11.2026

**Status: belegter RED-Befund, Testkorrektur vorbereitet; erneuter Android-Lauf NOT_RUN.**

- Basis: `5b935b4feaf960bb6973dd4d630f02744e41913f`.
- Branch: `work/omega-app-cancel-fixture-20261111`.
- Integration: `/root`, Ziel `integrate/omega-apk54-20261111` / PR #769.
- Der laufende gemeinsame Android-Lauf und der eingefrorene APK-Quellstand bleiben unverändert.

## Belegter Fehler im Test

Der tatsächliche gemeinsame Lauf meldet für
`OwnerAppActionWorkSceneUiTest.stopAndIndependentNavigationInvalidateAppNameQuestions`
an Zeile 210: `expected:<0> but was:<1>`.

Der Test speichert `appLookupGeneration` vor STOP und beginnt seine Schleife mit
`cancel = true`. `AgentActivity.cancelLocalRequest()` löscht bei Zeile 11980 die
offene Namensrückfrage und erhöht im normalen Owner-Pfad bei Zeile 12040 absichtlich
`appLookupGeneration`, damit verspätete Rückmeldungen ungültig werden. Eine neue
App-Suche erhöht denselben Zähler in `runExplicitAppLookup()` bei Zeile 5966.
Die bisherige Assertion unterscheidet diese beiden Ursachen nicht.

Damit ist ein falscher Messbeginn im Test belegt. Der Fehler `0 → 1` beweist keine
unerlaubte neue App-Suche. Keine Produktionsreparatur ist daraus abgeleitet.

## Strengere Prüfung

Nur der betroffene UI-Test wird geändert:

1. Nach STOP ausdrücklich genau `vorher + 1` verlangen.
2. Nach unabhängiger Navigation einen unveränderten Zähler verlangen.
3. Erst nach dieser Invalidierung den Referenzstand für die folgende Antwort nehmen.
4. Nach „Signal“ keinerlei weiteren Lookup verlangen: Generation unverändert,
   `activeOwnerAppFlow == null`, `appLookupActive == false`, null Launch-Aufrufe
   und kein angezeigter Bestätigungsdialog.

Die bisherigen Launch-/Dialogassertionen bleiben erhalten. Keine Behauptung, der
geänderte Test sei bereits grün; die erneute Ausführung erfolgt erst im kombinierten
Android-Lauf des Integrators. Keine Assertions gestrichen, kein Scope verkürzt.

## Belege und Grenzen

`docs/evidence/omega-app-cancel-fixture-20261111/red.ndjson` enthält den unveränderten
Fehlerdatensatz aus dem noch laufenden Originaljournal. `provenance.json` bindet
diesen Datensatz, Run-ID und die Quellen an SHA256. Das Gesamtjournal wird nicht
umgeschrieben und nicht als bereits abgeschlossen behauptet.

Produktionsdateien sind bytegleich zur Basis. Kein eigener Test-/Gradle-Lauf,
keine APK-/Signier-/Installationsaktion. Die MCP-Fixturearbeit bleibt pausiert.

Nächster Schritt: unabhängiger enger Quellenreview durch `/root/extensions`, dann
Integration durch `/root` und vollständige vorgesehene Android-Prüfmenge. Bei einem
weiteren Befund Ursache getrennt prüfen; keine pauschale Entschärfung des Tests.
