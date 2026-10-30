# Continuum M13 – Belegabnahme und Wiederanlauf

Stand: 30.10.2026. PR #249 (auf PR #248 und PR #245).

## Ist-Stand

Der M13-Miner existiert unter experimental/repair-transfer-lab/run-m13.mjs. Opus hatte einen abgekoppelten Lauf gemeldet. Ein abgeschlossener M13-Ergebnisordner ist im geprüften GitHub-Branch opus/continuum-m9-m16-20261030 jedoch nicht veröffentlicht. Ob der frühere Prozess fortbestand, ist unbekannt. **M13 ist damit nicht als abgeschlossen nachgewiesen.**

## Neues unabhängiges Abnahme-Werkzeug

Die Dateien experimental/repair-transfer-lab/m13-evidence-contract.mjs und m13-evidence-cli.mjs prüfen **nur aufgezeichnete Resultate**, ohne irgendeinen Testprozess oder Reparaturworker zu starten.

Das Gate verlangt die vier M13-Dateien summary.json, hypotheses.json, train-rows.json und dev-rows.json, den außerhalb der Resultate bestätigten Quell-Commit sowie das bekannte Siegel. Es prüft Fallzahlen, Hypothesenstatus, Modultrennung zwischen TRAIN und DEV und berechnet bei CONFIRMED eine einseitige Binomialwahrscheinlichkeit aus den aufgezeichneten Resultaten nach. Kandidatenzeilen werden zur effizienten Prüfung einmal aggregiert statt für jede Hypothese vollständig durchsucht.

Die CLI öffnet die vier Dateien mit O_NOFOLLOW und O_NONBLOCK auf POSIX, prüft reguläre Dateien und verweigert FIFO, Symlinks, leere sowie über 16 MiB große Daten. Das schützt vor den bekannten Hänge- und Symlink-Fällen.

**Statistische Grenze:** Mehrere Kandidaten innerhalb derselben Aufgabe oder desselben Moduls sind korreliert. Der von M13 verwendete Binomialwert behandelt solche Zeilen jedoch wie unabhängige Versuche. Das Gate überprüft lediglich die aufgezeichnete Berechnung, nicht diese Unabhängigkeitsannahme. Ein belastbarer Signifikanznachweis erfordert unabhängige Aufgaben-/Modul-Holdouts und eine geeignete statistische Auswertung.

**Wichtig:** Der Ergebnisstatus CONSISTENT_WITH_RECORDED_M13 bedeutet nur Widerspruchsfreiheit der aufgezeichneten Belege. Es ist kein kryptografischer Herkunftsbeweis und keine unabhängige Wiederholung der Originaltests.

## Wiederaufnahme auf einem tatsächlich angeschlossenen lokalen Host

Zunächst einen sauberen Checkout des tatsächlich ausgeführten M13-Commits sicherstellen. Den vorhandenen run-m13.mjs ausschließlich in einen neuen absoluten Outputordner außerhalb des Repositories laufen lassen. Nur wenn alle vier JSON-Dateien vollständig vorhanden sind, anschließend prüfen:

    node --test experimental/repair-transfer-lab/m13-evidence-contract.test.mjs experimental/repair-transfer-lab/m13-evidence-cli.test.mjs

    node experimental/repair-transfer-lab/m13-evidence-cli.mjs /ABS/OUTPUT-ORDNER <EXAKTER-AUSSERHALB-DER-ERGEBNISDATEI-GEPRUEFTER-COMMIT> 7a160a0ec6d73ae65c3ac7ade7475e355c2d18a497c1fbd6ad5bae970adcf211

Die Quell-Commit-SHA muss dem tatsächlichen Runner-Worktree entsprechen. Bei fehlender Evidenz, Inkonsistenz oder Fehler bleibt der Status UNVERIFIED. Nicht in die CCC1-Rangfolge oder das Lerngedächtnis einspeisen. Erst nach gesichertem Rohprotokoll und unabhängiger Forschungsprüfung Befunde als belastbar einstufen.

## Lokale Tests und Hash-Nachweise

Am 30.10.2026 wurden beide Testsuiten unter Node.js v22.16.0 ausgeführt: **11 PASS, 0 FAIL, 0 SKIP**. Die vier lokal geprüften Dateien wurden mit Git-Blob-Hashes gegen die gepushten GitHub-Dateien abgeglichen:

- m13-evidence-contract.mjs: d5a7d558b20371566d4a546f2cbf4a9e6492a6e6
- m13-evidence-contract.test.mjs: dba220cccb87e64e1c97510aab50100ee9153a52
- m13-evidence-cli.mjs: 3788c036eb26e73bd0027040355cd859bf4000db
- m13-evidence-cli.test.mjs: 0063cfde783123b22948152ce787a3bdf71e6551

Noch **nicht ausgeführt**: das vollständige M13-Forschungsexperiment, alle CCC1-/Worker-Regressionen auf dem neuen Branch, Gradle, APK, native Gemma und Fold7.

Keine Änderung an main, Besitzerfakten, Schlüsseln, Modellgewichten, ToolGate oder Produktiv-Reparaturpipeline.
