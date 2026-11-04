# Drei Reparatur-Bots — geprüfter Abschlussstand

Stand: 04.11.2026, Europe/Vienna. Auftrag: vorhandene Continuum-Komponenten prüfen und belegte Fehler reparieren.

## Gesicherte Quelle

- Gemeinsamer Draft: [PR #505](https://github.com/Ullmann27/device-ai/pull/505).
- Ausgangspunkt: PR #491 bei `e101e393dd6c80803ac9d9ac91fccd9812c4a18d`.
- Veröffentlichter Quellcheckpoint: [`edd41aeb25c4a0a39247344c27e9f395ebe8a5d2`](https://github.com/Ullmann27/device-ai/commit/edd41aeb25c4a0a39247344c27e9f395ebe8a5d2).
- Identischer lokal geprüfter Gesamtbaum: `fbcc38ed2bb570f929be4f9f4f94affafe8b424b`; lokaler Integrationscommit `f6ddd0f62afe9c09af7b06f96abdd257c6fab25b`.
- Nach diesem Quellcheckpoint kommen ausschließlich Dokumentation und Prüfbelege hinzu. App-, Skript- und Tool-Unterbäume werden vor Abschluss auf unveränderte Identität geprüft.

Drei Unteragenten wurden tatsächlich gestartet. Root koordinierte getrennte Arbeitskopien, prüfte die Änderungen, führte eigene Tests aus und sicherte die Ergebnisse. Es wurden keine permanenten Android-Bots oder fremden Chats gestartet.

## Drei begrenzte Reparaturen

| Bereich | Bestätigter Fehler und Änderung | Tatsächliche Prüfung |
| --- | --- | --- |
| App-/Cockpit-Ereignisse | Boot, Navigation und globale Statusmeldungen konnten den nächsten Worker ausblenden. Verspätete Meldungen konnten erledigte/verdrängte Karten erneut öffnen. Nur gerenderte Auftragsereignisse übernehmen jetzt den Kontext; maximal 24 zuletzt abgeschlossene/verdrängte IDs bleiben gesperrt. | Identischer finaler Hostbestand: 20 Fälle, vorher 9 Fehler, nachher 20 PASS. Anschließend unabhängiger Botlauf und Rootlauf jeweils 20 PASS. |
| Build-Vorprüfung | Im gewöhnlichen QA-Modus fehlte die frühe Versionsprüfung; übergroße Zahlen konnten die arithmetische Prüfung im Besitzer-Modus passieren. Beide Modi prüfen jetzt kanonische Dezimalwerte 39–9999 vor Vorbereitung/Downloads. | Neue Regression: vorher 12 Fehler unter 32 Fällen. Danach 70 PASS einschließlich 38 bestehender Prüfungen; Root wiederholte RED und GREEN sowie den 70er-Lauf auf dem kombinierten Stand. |
| Vorhandener M2.9-Suchbaustein | „Letzte Woche“/„letzten Monat“ verwendeten pauschale Abstände. Große Tageszahlen wurden zu „gestern“ oder warfen Ausnahmen. Kalendergrenzen sind nun zeitzonenbezogen, nicht darstellbare Tageszahlen ergeben kein Fenster. Ein unabhängiger Mitternachts-DST-Befund wurde zusätzlich korrigiert. | Final 17 PASS: 15 neue Grenz-/Fehlerfälle und 2 unveränderte Corpus-/Trust-Tests. Unabhängig erneut 17 PASS; Root zusätzlich 17 PASS unter JDK 17. |

Geänderte Produktionsdateien sind ausschließlich `ui/JarvisSequenceView.kt`, `learning/ContinuumMemoryRetriever.kt` und `scripts/render-continuum-owner-continuous-qa.sh`. Weitere Änderungen sind Tests, klar ausgewiesene Host-Testhilfen und Belege.

## Wichtige offene Qualitätsgrenze

Die Kalenderkorrektur ist keine pauschale Verbesserung der Suchqualität. Im unveränderten Corpus mit 1.000 synthetischen Erinnerungen und 44 Fragen sinkt beim Char-N-Gramm-Retriever mit Zeitgewicht die Trefferquote unter den ersten drei Ergebnissen von 43/44 auf 42/44 (gerundet 0,977 → 0,955).

Die Frage „Wohin ging ich letzten Monat mit Thomas?“ erwartet einen Theater-Treffer vom 05.10.2026. Dieser bleibt im richtigen Kalenderfenster und behält seinen Score, fällt aber durch nun zeitlich korrekt berücksichtigte Oktober-Konkurrenten von Rang 3 auf 5. Corpus und Schwellen wurden nicht geändert. Die vorhandene Mindestschwelle 0,80 bleibt erfüllt; der Rangverlust bleibt ein offener Qualitätsbefund.

Im geprüften Hauptquellbaum ist kein aktiver Hauptchat-Aufruf dieses M2.9-Retrievers nachgewiesen. Dieser Auftrag aktiviert ihn nicht. Der Fix bleibt ein isolierter Draft-Kandidat und darf nicht als verbesserte Modellintelligenz oder bereits geänderte Handy-Antwort ausgegeben werden.

## Prüfgrenzen

- Vollständiger Gradle-/Robolectric-Lauf: **UNAVAILABLE**. Der tatsächliche Versuch scheiterte vor Testausführung am fehlenden Android-Gradle-Plugin `com.android.application:8.10.0` im Offline-Cache.
- Elf neue Android-UI-Testfälle: **NOT_RUN**.
- UI-Hostcheck: vollständige originale View-/Effekt-/Bus-Quellen, aber explizite Android-/Painter-Stand-ins; keine echte Android-Queue, Grafik oder Lifecycle-Abnahme.
- Memory-Hostcheck: vollständige originale Retriever-Quelle; der nicht geprüfte Event-Dekorator ist ein fail-fast Compile-Stand-in.
- Physisches Fold7, lokale Modellinferenz, APK, Signierung, Installation und produktive Veröffentlichung: **NOT_RUN**.
- Kein Main-Merge. Paket, Versionswert, Originalsignatur, bestehender Updatekanal, Internetfreigabe, Besitzerwissen und Modellgewichte wurden nicht geändert.

Die örtliche Testquote ist keine Gesamtfreigabe der App. Wiederholungen und enthaltene bestehende Tests werden nicht als zusätzliche neue Tests gezählt.

## Belege und unabhängige Prüfung

- [App-Bot: Ursache, Tests und Grenzen](THREE-REPAIR-BOTS-APP-20261104.md).
- [Build-Bot: Vorprüfung und RED/GREEN](THREE-BOTS-BUILD-REPAIR-HANDOFF-20261104.md).
- [Memory-Bot: vollständige Historie, unabhängiger DST-Fund und Ranking-Nachteil](../../evidence/temporal-cue-calendar-20261104/RESULT.md).
- [Koordinator: tatsächliche Nachprüfungen und Eingabeidentität](../../evidence/three-repair-bots-20261104/coordinator/receipt.json).

Bot 3 prüfte Bot 1 unabhängig; Bot 1 prüfte anschließend Bot 2 und dessen Belegzuordnung. Root prüfte den Build-Fix unabhängig und testete alle drei Bereiche selbst. Ein zunächst möglicher falsch positiver UI-Test durch vom Bus geschluckte Fixturefehler wurde durch Fehlererfassung außerhalb des Busses beseitigt.

## Nächste Abnahme und Rücknahme

Zuerst die fehlende vollständige Android-Toolchain bereitstellen und die neuen Kalender- sowie UI-Tests mit den bestehenden Anschlussregressionen ausführen. Anschließend echte Auftragsereignisse, Darstellung und Updates auf dem Fold7 prüfen. Vor einer produktiven Aktivierung des M2.9-Suchbausteins bleibt dessen Rangverlust zu bearbeiten und unabhängig neu zu bewerten.

Jede der drei Produktionskorrekturen ist separat rücknehmbar. Es gibt keine Datenmigration oder Formatänderung. Andere offene PRs und fremde Arbeitskopien wurden nicht überschrieben.
