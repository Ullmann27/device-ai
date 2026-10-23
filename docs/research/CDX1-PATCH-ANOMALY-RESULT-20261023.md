# Experiment D – CDX1 als Patch-Anomalie-Sensor: Ergebnis (23.10.2026)

Vorregistrierung: `CDX1-PATCH-ANOMALY-PREREG-20261023.md` (Commit `29fe255e`, vor dem Lauf). Rohbericht:
`cdx1-patch-anomaly-report-20261023.json`. Ein Messlauf, TEST einmal ausgewertet.

## Ergebnis in einem Satz

**Kein Zusatznutzen nach der vorregistrierten Regel:** CDX1 erkennt ungewöhnliche Bytefolgen, aber keine Syntax-,
Token-, Abschneide-, Hunk- oder semantischen Fehler, und ein triviales Byte-4-Gramm leistet dasselbe sechsmal
schneller. CDX1 kommt **nicht** in CCC1-Quittungen; Parser-/Apply-Prüfung und echte Tests bleiben maßgeblich.

## Daten

`scripts/cdx1-patch-anomaly-dataset.mjs --until 81180313 --max-commits 1000` (Seed 20261023): 682 Commits, 464
gültige Hunks (TRAIN 288 / CAL 80 / TEST 96, Aufteilung nach Dateipfad), 2 896 Fragmente, 524 TRAIN-Texte
(199 659 Bytes). Datensatz-SHA-256 `61efb1240f67839565b7fea4aee65fb23fd36f8488d02426a8f5f3d4c29940c7` (nicht
eingecheckt; mit demselben Befehl reproduzierbar). Abweichung vom ersten Versuch: Der Klon war flach (50 Commits,
nur 38 gültige Hunks); vor jeder Auswertung wurde die volle Historie geholt. Auf dem kleinen Satz wurde nichts gemessen.

## Parser-Basislinie (TEST, `git apply --check` + `node --check`)

| Gruppe | erkannt |
|---|---|
| INCOMPLETE_HUNK | 1,00 |
| SYNTAX | 0,92 |
| TRUNCATED | 0,92 |
| ODD_BYTES | 0,70 |
| MISSING_TOKEN | 0,52 |
| SEMANTIC (n = 22) | 0,00 |
| VALID (Fehlalarm) | 0,00 |

## CDX1 und 4-Gramm (Primärwert `S_max`, Schwelle 95.-Perzentil auf CAL-VALID)

| | CDX1 | Byte-4-Gramm |
|---|---|---|
| Fehlalarmquote TEST-VALID | 0,083 | 0,042 |
| ODD_BYTES Treffer / AUROC | 0,99 / 0,998 | 0,99 / 0,997 |
| SYNTAX, MISSING_TOKEN, TRUNCATED, INCOMPLETE_HUNK – AUROC | 0,48–0,53 (Zufall) | 0,48–0,53 (Zufall) |
| SEMANTIC Treffer / AUROC | 0,14 / 0,55 | 0,00 / 0,55 |
| **vom Parser unerkannt (n = 113)** Treffer / AUROC | **0,336 / 0,661** | 0,283 / 0,649 |
| Parser **oder** Modell, ODD_BYTES | 1,00 | 1,00 |
| Zeit je Fragment (Median / p95, JVM x86_64) | 2,49 / 3,81 ms | 0,44 / 0,75 ms |
| Größe | 7 746 Parameter (62 KB) | 66 011 Tabelleneinträge |

Entscheidungsregel: Treffer ≥ 0,20 ✓, ≥ Fehlalarm + 0,10 ✓, AUROC ≥ 4-Gramm + 0,05 ✗ (0,661 gegen 0,649).
Die 33,6 % auf der unerkannten Teilmenge stammen fast ganz aus ODD_BYTES (29 von 113 Fragmenten); semantische Fehler
erkennt keines der Modelle. Mittelwert-Score (`S_mean`) ist schwächer (unerkannt: AUROC 0,586).
Gepaarter Vergleich mit der vorhandenen `Cdx1PatchShadowStudy` (gleicher Hunk, gültig gegen defekt; Anteil „defekt
überraschender“): ODD_BYTES 1,00, SYNTAX 0,69, MISSING_TOKEN 0,51, INCOMPLETE_HUNK 0,51, TRUNCATED 0,38, SEMANTIC 0,27.

## Folgerungen

1. CDX1 bleibt beratende Forschung; keine Aufnahme als Signal in CCC1.
2. Der einzige echte Fund – unsichtbare Steuer- und Sonderzeichen – braucht kein Lernmodell: eine deterministische
   Prüfung auf Steuer-/unsichtbare Zeichen in Patches (Schutz vor „Trojan Source“) wäre billiger und überprüfbar.
   Als nächster kleiner Schritt vorgeschlagen, nicht umgesetzt.
3. Gegen semantisch falsche, syntaktisch gültige Patches helfen nur Tests – die CCC1-Reparaturkette verlangt sie bereits.

## Grenzen

Nur JavaScript-Hunks dieses Repositorys; synthetische Defekte (keine echten fehlerhaften Patches); Zeiten auf
x86_64-JVM, keine Android-/Akku-Aussage; die beobachtete Fehlalarmquote (8,3 %) liegt über den nominellen 5 %
(kleines CAL-Set, 80 gültige Hunks).

**Nachvollziehen:** Datensatz wie oben erzeugen, dann `Cdx1PatchAnomalyExperiment <dataset.jsonl> <report.json> <sha256>`
mit dem Klassenpfad aus `scripts/android-kotlin-unit-check.sh` (Hauptquellen + Testklassen) ausführen.
