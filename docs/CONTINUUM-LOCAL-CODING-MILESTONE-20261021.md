# Lokaler Coding-Meilenstein – geprüfter Wiederaufnahmepunkt

Basis: PR #48, `integration/continuum-astra-20261021`, `dfd876c23e2e88faa2ff7ee2f7f75138b416929a`.
Entwicklungsbranch: `astra/continuum-local-coding-milestone-20261021`; Draft-PR #49. EXP-00/02/05/07/08/11/12. Restquote nicht zugänglich; keine Prozentwerte erfunden.

## Implementiert

Der bestehende kanonische Owner-REPAIR-Worker kann einen **explizit aktivierten lokalen Kandidatenlieferanten** verwenden. Derselbe Kritiker, Syntaxprüfer, feste Testprofile und ein zweiter frischer Worktree entscheiden über den Patch. Erst danach darf das kanonische Backlog DONE speichern. Kein zweiter Agent, kein anderes Backlog, keine kostenpflichtige API und keine Modellgewichtsänderung.

Der vorhandene authentifizierte llama.cpp-Client läuft über einen festen Node-Unterprozess und ausschließlich `127.0.0.1:8089`. Explizite Positivliste mit höchstens zwei Quelldateien zu je 1400 Zeichen, begrenztes JSON, Zeitlimit und keine Shell aus Modelltext. Schlüssel bleiben in der lokalen Umgebung. Standardbetrieb bleibt deterministisch. Nicht erreichbarer Server lässt den deterministischen Pfad verfügbar; der Bericht behauptet keine unabhängig nachgewiesene Modellausführung.

**Sicherheitsgrenze:** Ein Git-Worktree ist keine OS-Sandbox. Das unabhängige Review fand deshalb vor Abschluss eine zu breite Ausführungsgrenze. Automatisch testbare Modellpatches sind nun ausschließlich komplette, kleine `.mjs`/`.js`-Module mit genau einer exportierten Zahlenfunktion, unverändertem Namen und Parametern. Der Kritiker prüft alte und neue Datei mit einer begrenzten Ausdrucksgrammatik: Zahlen, Parameter, Arithmetik, Vergleiche, Logik und Bedingungen. Keine Imports, Globals, Calls, Properties, Zuweisungen, Updates, Schleifen oder zusätzlichen Anweisungen. Dieselbe Prüfung gilt im Publisher und für Rezept-Replay. Replay braucht dieselbe explizite Aktivierung und Dateipositivliste. Modellpatches behalten konservative Rangklasse und Besitzerreview. Freie Kotlin-/JS-Modellpatches bleiben außerhalb automatischer Ausführung; bestehende deterministische Kotlin-Reparaturen bleiben verfügbar.

## Tatsächlich ausgeführt

- Drei Integrationstests zuerst rot, danach grün: korrekter Kandidat, überangepasster Konstantenpatch mit Gegenbeispielen, unabhängiger Backlog-Abschluss.
- Frühe gezielte Suite 44/44 grün. Anschließend zusätzliche Sicherheitsfälle: echte Worker-Ausführung verweigert Datei-Schreibcode und Test-Monkeypatching vor dem Kandidatentest; eine externe Sentinel-Datei bleibt bytegleich. Ausdrucksgrammatik, Scope, Replay und Timeout werden separat geprüft.
- Echter HTTP→Node-CLI→Patch→Git-Worktree→Node-Test-Durchlauf mit **kontrollierter Protokollantwort**. Kein llama.cpp/GGUF-Lauf und kein Modellleistungsnachweis. Die vier Testfälle eines bekannten Mini-Reparaturproblems sind Sicherheits-/Integrationstests, keine versiegelte Lernstudie und kein Neuartigkeitsbeleg.
- Android: 706/706 Tests, null Skips, Kotlin-Kompilierung, bestehendes NewApi-Lint-Gate und Debug-APK-Build erfolgreich. Node↔Kotlin-Backlog-Roundtrip, SWB1/SWB2-Parität und CRE1-Inferenz-/SGD-Parität anschließend unabhängig durch die bestehenden Verifikationsskripte bestätigt.
- Ein erster Android-Lauf scheiterte an wiederverwendeten, vom vorigen Worktree veränderten Paritätsdaten. Frische getrennte Fixtures beseitigten diesen Ausführungsfehler; keine Produktprüfung wurde abgeschaltet.
- Gesamte Node-Suite läuft nach den Sicherheitsänderungen erneut. Ein zuvor gefundener veralteter Quelltexttest erwartete die Android-Rückmeldung ohne den bereits in PR #48 integrierten Commit-/Paketnachweis. Assertion an die tatsächliche strukturierte Rückmeldung angepasst; keine Sicherheitsbedingung entfernt.
- Backend: 60/60 und Syntaxprüfung erfolgreich.

## APK- und Gerätestatus

Lokales Prüfarbeitsartefakt: Paket `at.heinz.deviceai.mentorpaired`, 0.3.10/Code 13, v2-Signatur gültig, genau ein **lokaler Android-Debug-Signer**. Dieser ist nicht der bestehende Installationssigner. Kein Installationsdownload und keine Installation freigegeben. Aktuelle Node-/Termux-Änderungen sind außerdem kein automatisch aktualisierter Android-App-Build.

Kein Samsung/ADB und keine vorhandene GGUF-Datei in diesem Arbeitsbereich; keine Behauptung einer Geräteabnahme oder verbesserten neuronalen Qualität. SWB1/CDX1/NEURON-Gewichte, Checkpoints und Besitzerdateien wurden nicht geöffnet oder geändert. Die bestehende 20%-/Temperatur-/Speicherplanung und Termux-Build-/Rückkanalimplementierung aus PR #48 bleiben erhalten.

## Kontrollierter nächster Einstieg

1. Tatsächlichen GitHub-HEAD von PR #49 und AGENTS.md prüfen; jüngsten Ergebnisabschnitt unten lesen. Kein blindes Checkout eines historischen SHA, keine Main-Promotion.
2. Der Besitzer startet einen bereits installierten und geprüften lokalen llama.cpp-Server. Kein automatischer Modell-/Tooldownload. Ein leistungsfähiges Modell muss erst anhand realer Samsung-Messungen ausgewählt werden.
3. Einen REPAIR-Auftrag im vorhandenen Backlog ausdrücklich annehmen und an den tatsächlichen lokalen Git-HEAD binden. Einen kleinen erlaubten Zahlenfunktionsfehler verwenden. Der Worker erfindet keine Aufgaben oder Besitzerfreigaben.
4. Nach sauberem Checkout mit bereits lokal gesetztem Schlüssel:

```sh
node .github/continuum/developer-agent.mjs owner-repair \
  --file /ABSOLUTER_PRIVATER_STATE/backlog.json \
  --out /ABSOLUTER_PRIVATER_STATE/reports \
  --id OWNER_AKZEPTIERTE_TASK_ID --minutes 5 \
  --local-brain --local-paths lib/example.mjs
```

5. Gespeicherten Bericht, Patch-SHA und unabhängige Testevidenz prüfen. DONE bedeutet geprüfter Vorschlag, keine Merge-/Installationsfreigabe. Modellantworten werden keine bestätigten Besitzerfakten.
6. Danach aktuellen App→Termux→App BUILD-Rückruf mit Commit, Paket, Signer und APK-SHA auf Samsung abnehmen. Für größere Modellpatches zuerst einen tatsächlich eingeschränkten Ausführer mit belegten Datei-/Netzwerk-/Prozessgrenzen entwickeln; Kritiker allein reicht dafür nicht.

Rollback: lokalen Kandidatenmodus weglassen oder ausschließlich die Commits dieses Entwicklungsbranches zurücknehmen. Persistente Telefonstände bleiben außerhalb dieser Arbeit. Neue Forschung erst mit getrennten Baselines, frischen Holdouts und unabhängiger Gegenprüfung; kein Intelligenzgewinn aus Updatezahlen ableiten.
