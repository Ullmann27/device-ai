# Begrenzter Modelllade-Review – 2026-11-11

**Ergebnis: Review OK; kein neuer Befund im geprüften Model-Load-Diff.**

- Prüfer: `/root/logic`.
- Vergleich: `9a6de6c4547feb62c9822520596e786a852ed83b` → `1ba7b9458fd47184cedaed18e94e9aae1bd76c39` (#773).
- Fortschreibung: `8decff576dc05ed3cdb0b8ded680b6098a1d8537`; die neun Quellen des geprüften JUnit-Belegs sowie Gemma-Vertrag und Handoff wurden unabhängig byte-identisch bestätigt.
- Scope: `LocalModelCatalog.kt`, `LiteRtLmLocalModelRuntime.kt`, neue Load-Tests, ergänzter `gemma-reopen-lifecycle.test.mjs`-Vertrag und `THERMAL-INFERENCE-HANDOFF-20261111.md`. Keine Freigabe weiterer Thermik-/Inference-Änderungen.

## Feststellungen

1. `sourceForLoad()` bleibt `@Synchronized` und ruft `get(id, version)` mit dem unveränderten Standard `verifyBytes = true` auf. Jeder Load berechnet weiterhin SHA-256 über die vollständige Modelldatei. Nur die zweite unmittelbar folgende vollständige Prüfung entfällt.
2. Validierung von ID/Version und die Ableitung des Dateinamens aus dem gehashten Katalogschlüssel bleiben erhalten. Verifikation und Pfadauflösung erfolgen unter demselben Katalogmonitor. Kein neuer frei vorgebbarer Dateipfad.
3. Der native Fähigkeitsvergleich vor `Engine.initialize()` bleibt erhalten. Außer dem Austausch der beiden Katalogaufrufe enthält der Runtime-Diff keine Änderung. `loadedModel`, Ressourcenbesitz, Unload, Cancellation, CPU-Threads und Tokenbudgets bleiben unverändert.
4. Die fünf neuen Load-Tests prüfen vollständige Hashanzahl, erneute Prüfung bei späteren Loads, gleich lange Manipulationen, fehlende/verkürzte Dateien und falsche Identität. Der ergänzte Gemma-Vertrag prüft auch manipulierte Varianten mit umgangener Verifikation.
5. Alle neun Quellhashes des gespeicherten Nachweises stimmen mit beiden oben genannten Kandidaten überein. Das vorhandene JUnit-Protokoll meldet 35/35 Tests; diese wurden bei diesem Review nicht erneut ausgeführt.

## Prüfgrenzen

Die Rückgabe eines `File`-Pfads verhindert kein externes Austauschen zwischen Verifikation und späterem nativen Öffnen. Dieses Rennen bestand bereits vor dem Diff; hier wird kein neuer Schutz dafür bestätigt. Der Katalogmonitor koordiniert Zugriffe auf dieselbe Kataloginstanz, keine externen Dateischreiber.

Kein eigener Gradle-Lauf, keine Codeänderungen, keine native Modellinitialisierung, keine Fold7-/Temperatur-/Energie-/Latenzmessung. Die Reduktion der Hasharbeit belegt keine Verbesserung der bereits laufenden Inferenz.

## Belege

- Exakte SHA-256-Werte und Kandidatenvergleich: `model-load-source-hashes.json` im selben Verzeichnis.
- Geprüfter Git-Beleg: `docs/evidence/thermal-inference-20261111/hash-green/inputs.json` und `junit.log`.
- Produktionsstellen: `LocalModelCatalog.kt` (`get`, `sourceForLoad`, private Pfadauflösung), `LiteRtLmLocalModelRuntime.kt` (`load`, `cancel`, `unload`, `finishInference`, `closeResources`).
