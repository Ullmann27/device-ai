# Interne Bot-Fachübungen: tatsächliche Entwicklerprüfungen

Stand: 04.11.2026. Draft-PR [#516](https://github.com/Ullmann27/device-ai/pull/516).

**Echte Bot-Modellversuche: 0. Gewichtsänderungen: 0. Geräteabnahme: nicht durchgeführt.**
Diese lokalen Gradle-Läufe verwenden tatsächliche Android-/Testquellen und ausdrücklich vorgegebene Entwickler-Antwortfixtures. Sie sind keine erfolgreich absolvierten Fachübungen durch das lokale Modell.

| Lauf | Geprüfter Quellstand | Befund | Frische Tests |
|---|---|---|---:|
| run-01 | b1701e7e3bf52047efff05ae33f0ba602dcc3d4b | PASS, 135,626 s | 70 |
| run-02 | 90cc7373b2c3bac1d7b9f77e582958e359d00b20 | Gradle-Prozessverlust, 134,048 s | 0 |
| run-03 | 90cc7373b2c3bac1d7b9f77e582958e359d00b20 | Gradle-Prozessverlust, 171,402 s | 0 |
| run-04 | 55ac3e9315de24bfc4d1ad589e565cf5b2622ff6 | Gradle-Prozessverlust, 92,070 s | 0 |

Run-01 enthält 39 neue und 31 vorhandene Tests. Die spätere V2-Kompatibilitätskorrektur ist dadurch noch nicht abgenommen. Run-02 bis run-04 endeten während `compileDebugKotlin` mit `Gradle build daemon disappeared unexpectedly`, Exit 1 und ohne Timeout. Die Logs belegen weder einen Kotlin-Compilerfehler noch OOM als Ursache. Alle sieben alten XML-Ergebnisse wurden verworfen; sie wurden deshalb nicht in diese fehlgeschlagenen Läufe kopiert. Run-04 reduzierte Gradle auf einen Worker und 1.536 MiB Java-Heap. Der vollständige Android-Build des letzten Quellstands bleibt **BLOCKED / UNVERIFIED**.

## Archiv und Aussagegrenzen

Jedes Verzeichnis enthält den unveränderten `result.json`, die tatsächliche `command.json`, `java-version.txt`, den Gradle-Rohlog sowie alle erzeugten Quell-Hashmanifeste und frischen Klassen-XMLs. `source-before.json` und `source-after.json` erfassen die 975 Dateien in Android-Hauptquellen, Testquellen und Runner sowie die Build-Konfiguration. Innerhalb jedes einzelnen Laufs sind diese Manifeste identisch. Der bei run-03 im Git-Status vermerkte Handoff-Text gehört nicht zu den ausgeführten Quellen.

`manifest.json` hasht jede archivierte Datei außer sich selbst. Die vollständigen Rohlogs enthalten auch vorhandene Projektwarnungen; es wird kein warnungsfreier Build behauptet. Es wurde keine APK assembliert, signiert oder installiert.

Reproduktion und Speicherkontrakt: [Handoff](../../research/omega/INTERNAL-BOT-TRAINING-HANDOFF-20261104.md). Der dortige Runner benötigt eine bereits bereitgestellte lokale Toolchain. Er führt keine Downloads aus, verlangt frische XML-Berichte und zählt alte oder fehlende Ergebnisse nicht als Erfolg.
