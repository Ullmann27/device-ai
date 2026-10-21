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
