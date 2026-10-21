# Wiederaufnahme: Forschungsfehler und Android-Worker, 21.10.2026

Branch: `astra/continuum-research-verification-20261021`.
Ausgangs-HEAD: `06a41709ac1eba32b3081e0aee63e450fce3b3dc` des Experimentbranches.
`main` unverändert. Die zwischenzeitlichen Quellenreparaturen bis 0.3.9 werden übernommen und geprüft, nicht erneut entwickelt. Der ältere lokale Sol-Arbeitsstand ist separat erhalten.

## Meilenstein 0 – tatsächlicher Stand

AGENTS.md, Issue #41, Experimentvertrag/-register und Astra-Resume-/Limitprotokoll gelesen. Betroffen: EXP-00/01/02/04/06/08/11/12. Keine neue Agentenarchitektur, keine Modellpromotion, keine Installation. ChatGPT-Restquote nicht zugänglich; keine Prozentangabe ableitbar.

Eine echte, begrenzte HTTPS-Abfrage der vorhandenen deutschen Wikipedia-API für `Asilomar Conference on Beneficial AI` lieferte in dieser Sitzung:

```json
{"batchcomplete":"","query":{"pages":{"-1":{"ns":0,"title":"Asilomar Conference on Beneficial AI","missing":""}}}}
```

Damit ist ein fehlender Artikel **jetzt** reproduziert. Der historische 08:21-Abbruch bleibt ohne damaligen Stacktrace nicht eindeutig zuordenbar. Der alte Fetch-Code warf bei fehlendem Auszug `IllegalStateException`; die inzwischen vorhandene Reparatur klassifiziert diesen Fall als SOURCE_UNAVAILABLE. Weitere HTTP-/JSON-/Transportfehler brauchen echte Verhaltenstests und feste Diagnosecodes.

Aktuell laufen unveränderte Baseline-Prüfungen: vollständige Node-/Script-Suite sowie Android Unit/Compile/Lint/Debug-APK (lokaler Debug-Signer, kein Download-/Installationskandidat). Ergebnisse werden nach Abschluss ergänzt.

Nächster kleiner Schritt: tatsächlichen Fetch über eine injizierbare HTTPS-Verbindung prüfen: fehlender Artikel, ungültige Antwort, 429/503, Timeout, Abbruch, Bytegrenze. Produktions-Host/Consent/Quellen- und Holdout-Grenzen erhalten. Danach die 20%-Teamleiterplanung und eine eng begrenzte Lücke im vorhandenen Coding-Worker prüfen.

Rollback: nur Änderungen dieses Prüfbranches zurücknehmen; persistente Telefonzustände werden hier nicht geöffnet oder verändert. Kein Push auf main oder den parallel bearbeiteten Experimentbranch.

## Meilenstein 1 – Fetch-Fehlergrenzen repariert

Ausgangsstand tatsächlich geprüft: Node 749/749, Backend 60/60, Android 676 Tests (673 bestanden, drei fixtureabhängig übersprungen), Kotlin-Kompilierung, bestehendes NewApi-Lint-Gate und Debug-APK erfolgreich. Keine Samsung-Abnahme.

Reproduktion mit echter Fetch-Implementierung und kontrollierter HTTPS-Verbindung: neun JVM-Fälle, davon zunächst fünf rot. Insbesondere gab `discover()` bei API-Fehler-JSON eine scheinbar gültige leere Liste zurück. Jetzt werden fehlende Artikel/kurze Auszüge, HTTP-Status, API-Fehler, ungültiges JSON/Antwortschema/Content-Type, Größenlimit, Transport und Timeout getrennt ausgewiesen. Fehlermeldungen enthalten keine Antworttexte oder URLs. Eine ungeeignete Quelle bleibt NO_CHANGE und gibt die nächste Quelle frei. Fehler/Abbruch behalten den Titel für einen späteren Versuch; unbekannte Phasen dürfen ihn nicht mehr als erledigt markieren. Separate Archiv-/Index-/Training-/Evidenzfehler behalten ihre Phase. Optionale Linkentdeckung darf eine gültige Quelle weiterhin nicht verwerfen.

Nach Korrektur: Fetch-JVM 9/9 grün; Gradle `:app:testDebugUnitTest --tests '*ContinuumPublicStudy*'` erfolgreich; 17 Node-Vertragsprüfungen grün. Zusätzlich hinzugefügter Diagnose-Test wird im nächsten gebündelten Android-Lauf geprüft. JSON-Abhängigkeit ist ausschließlich `testImplementation`; APK verwendet weiter Androids JSON-API. Kein Produktionsgewicht, Archivschema oder Checkpointformat verändert.

Belege extern im Arbeitsverzeichnis `/workspace/evidence/research-20261021/`: `fetch-red.log`, `fetch-green.log`, `fetch-green-gradle.log`, `research-wiring.log`, Baseline-Logs. Persistierte Live-Antwort als Testfixture im Repo. Erstes nächstes Ziel: 20%-Planung des Teamleiters, während laufender Forschung wiederholte Temperatur-/Speicher-/Akkuprüfung; danach vorhandenen Termux-Builder auf falsche Artefaktfreigabe prüfen.

## Meilenstein 2 – Akku, laufende Jobs und Teamleiter

Die Teamleiter-Registrierung verlangte noch Androids herstellerabhängiges `batteryNotLow`. Ein Verhaltenstest wurde gezielt rot und ist nach Umstellung grün: 20% ohne Ladekabel sind zulässig, 19% und unbekannte/ungültige Messwerte bleiben gesperrt. Bestehende abweichende Teamleiterregistrierung wird einmal migriert; identische Registrierung bleibt bestehen. Gemeinsame aktuelle Beobachtung für Forschung, Offlinejob und Teamleiter; Prozentberechnung mit Long statt möglichem Int-Überlauf.

Forschung prüft jetzt zusätzlich Temperatur und freien Speicher vor dem Start sowie alle 1,5 Sekunden innerhalb des bestehenden 15-Sekunden-Budgets. Ein einmal gestoppter Durchlauf bleibt gestoppt. Eine spätere lokale Übungsphase darf den echten Quellenabschluss nicht durch ihren eigenen Ressourcenstatus überschreiben. Forschungs-/Offlinejob verweigern einen neuen Start, solange ihr voriger Thread noch lebt; dessen Abbruchflag wird nicht wieder freigegeben. Keine Endlosschleife, keine neue Hintergrundberechtigung und keine garantierte Ausführungsfrequenz.

Gezielter Gradle-Lauf erfolgreich: öffentliche Quellen/Ergebnisdiagnose, Ressourcenbudget, Teamleiterplanung/-status und Android-Buildrückmeldungen. Vollständige Suite einschließlich vorher fehlender Node↔Kotlin-Referenzdateien folgt. Offener Gerätenachweis: 19/20%-Grenze ohne Kabel, Temperaturdeferral, Stop/Restart unter Android 16.

## Meilenstein 3 – vorhandener Coding-/Buildworker

Zwei neue Node-Negativtests belegten vor der Reparatur eine falsche Freigabe: Eine gültige Signatur genügte auch bei falscher Paketkennung bzw. fehlender Paketprüfung für `EXPERIMENTAL_APK_BUILT_NOT_DEVICE_TESTED`. Der vorhandene Termux-Builder prüft nun mit seinem bereits benötigten AAPT2 dieselben privat kopierten APK-Bytes: `at.heinz.deviceai.preview` und gültiger positiver Android-Versioncode. Bei Fehlern kein freigegebenes Artefakt und keine Download-Kopie. Ergebnis `FAILED_APK_IDENTITY` wird über den vorhandenen Android-Rückkanal als historischer Fehler erklärt. Berichtsschema bleibt kompatibel; Paketname/Version werden additiv gespeichert.

Builder-Node-Suite 36/36 grün (Tool-Ausgaben simuliert, keine Behauptung eines ARM64-Gerätelaufs). Android-Rückkanaltest im gezielten Gradle-Lauf grün. Gemeinsame Build-Lease, Git-Prüfung vor/nach dem Build, Signaturprüfung, bestehende Reparaturpipeline und Besitzerfreigabe bleiben bestehen. Kein fremder Agent, keine neue Aufgabenverwaltung, keine Installation. Nächster echter Fortschritt ist ein bekannter Mini-Reparaturauftrag über das kanonische Backlog mit unabhängiger Testevidenz und anschließendem Besitzerreview auf dem Samsung.
