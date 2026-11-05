# Fortsetzung 03 — unabhängige RED→GREEN-Prüfung des lokalen Cross-Study-Guards

Geprüfter Ausgangs-HEAD #492: `07516a8bcebeeaf9af6a4453d0182cdd88debebd`. Fremder PR #508: `59891cfad6dd015f47c187cc688ea94b0026ad7d` (Draft). Originalcheckout unter `/workspace/scratch/0549bca61ccf/device-ai-weight-audit` fehlt, Python 3.13.5 ist vorhanden. Git-Klon fehlgeschlagen (DNS); GitHub-Quellen mit autorisiertem Connector gelesen.

**Sechs nachgewiesene neue Quellen-Gegenfälle:** Der vorhandene lokale `continuation-02/pretest_exclusivity.py` akzeptierte bei synthetischen Pfaden sechs unzulässige Inventare: partielle Test-TSV, partielle Receipt, unbekannte Testartefaktendung, partielle Summary, Symlink-Vorfahren einer Ausgabewurzel und Symlink-Vorfahren des gepinnten Inventars. Testquelle und ursprünglicher Guard wurden anhand exakter Git-Blob-SHAs `aa85775476cbb70e9d3e5852db860575f76cc932` und `5b068596a0b2301f373fe89421189654db15c963` geprüft. Dies ist kein Beleg, dass diese Szenarien in alten echten Studien eingetreten sind.

**Neuer isolierter Guard:** `pretest_exclusivity_hardened.py` blockiert sichtbare `test-`/`.test-`-Artefakte unabhängig von Erweiterungen, partielle Ergebnis-/Startmarker sowie Symlink-Komponenten in Inventar und Ausgabepfaden. Kein Editieren des Originalguards, der Originalrunner oder des Patches von #508. Das Ergebnis bleibt **nur** `LOCALLY_UNCONSUMED_AT_INSPECTION`, keine externe Finaltest-Freigabe.

**Ausgeführt lokal mit Python 3.13.5** im Verzeichnis `continuation-03`:
`python3 -B -m unittest -v test_pretest_exclusivity_hardened.py` → **19/19 PASS**, davon sechs RED→GREEN-Negativkontrollen, eine saubere Positivkontrolle und zwölf unveränderte ursprüngliche Tests gegen den Nachfolger. SHA-256: Guard `209e41267f0b6d483b6b5b72b150872a15298a57b2f7c0f4377cc37dad144ede`; Tests `6b07b72d7f4b27ef4c2f12babc9608fcf0185e6a0f31b66e4423df2175d12421`.

**Grenzen:** Lokale Prüfung ist kein atomarer/extern attestierter Finalverbrauch, verhindert keine TOCTOU-Rennen oder Weglassen nicht registrierter Output-Verzeichnisse. Dynamische vollständige #508-Runner-Gegenprüfungen mit schlechtem Baseline-Gate, manipulierten echten Checkpoints und falschen Resume-Receipts: **NOT_RUN**. Alte Qualität **FAIL**, alter Finaltest **verbraucht**. **M5_UNCHANGED**, M6_PARITY_FAIL_RESTORED, null Modellstarts/Optimizerupdates. Kein Patch in #508 integriert.

Nächster Schritt: authentische Originaleingaben/Checkout vor jeder Nutzung prüfen, dann echte source-pinned, ausschließlich synthetische Runner-RED→GREEN-Proben ohne Modellstart ergänzen. Prospektive unabhängige Train-/Dev-/Final-Daten, unveränderte Ressourcen/Qualität und externe exklusive Finalverbrauchssperre bleiben obligatorisch.
