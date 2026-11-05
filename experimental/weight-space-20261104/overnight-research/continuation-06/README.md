# Fortsetzung 06 — authentische Eingaben wiederhergestellt und verifiziert (05.11.2026)

G1-INTEGRATE-WEIGHT-002, eigener Draft-PR #492. Kein Modellstart, keine JVM-Ausführung, kein Finaltest, kein Training. Eigen-Head vor dieser Fortsetzung: `f8b2ac35e144dc95cba4941904c26f6b91c48c39`; der erneut geprüfte #508-Head ist `59891cfad6dd015f47c187cc688ea94b0026ad7d` (Draft). Lease in `STATE.json` vor dem Lauf nicht aktiv.

## Tatsächlich ausgeführte Wiederherstellung

Die ursprünglich notierten Checkout- und Archivpfade waren **nicht** vorhanden. Der lokale Container unterstützte jedoch jetzt `git 2.47.3`, `Python 3.13.5` und `OpenJDK 21.0.12.1`. Das anonyme `git ls-remote` scheiterte wegen fehlender DNS-Auflösung; ein vollständiger originaler Checkout wurde nicht wiederhergestellt. Mit dem autorisierten File-Library-Werkzeug konnten die zwei vorhandenen privaten ZIP-Archive stattdessen in einen privaten Arbeitsbereich materialisiert werden.

**Wichtig:** Die von der Besitzeranweisung vorgegebenen SHA-256 der beiden *gesamten* ZIPs wurden tatsächlich mit `sha256sum` über die Materialisierungen verglichen und **beide stimmen exakt überein**. Aus jedem unveränderten ZIP wurde das Manifest gelesen; dann wurden alle registrierten Nutzdaten einzeln über SHA-256 und Bytelänge geprüft: **Diagnose 3/3 JARs PASS, Modell-/Runtime-Archiv 9/9 PASS (darunter S0/S1/W0/W1 und die Runtime-JARs)**. ZIP-CRC-Prüfung `testzip()`: bei beiden Archiven kein defekter Eintrag. Die vier Checkpoint-Metadaten stimmen zusätzlich zwischen Modellkarte und Dateiregister überein. Die registrierte v3-Runtime- und Kotlin-Stdlib-Abhängigkeit des Diagnosearchivs stimmen mit den tatsächlich verifizierten Einträgen des Modellarchivs überein (**2/2**).

Die vollständigen privaten ZIP-Identifikatoren, Wiederherstellungspfade und Archiv-/Checkpoint-Hashes wurden **bewusst nicht in Git-Dateien kopiert**; sie sind mit dem Besitzerauftrag und den ZIP-internen Manifesten prüfbar. Die ZIPs und alle originalen Checkpoints wurden **nicht verändert**.

## Isolierte verifizierte Negativkontrollen

`verify_archive.py` ist ein schreibgeschützter Archiv-Prüfer ohne Modellimport, JAR-Start oder Datei-Extraktion. Er verlangt den extern vorgegebenen gesamten Archiv-Hash und prüft danach manifesteindeutig alle Einträge, SHA-256/Größe, Extras, Pfade, Duplikate, Symlinks und die Modellkarten-Konsistenz.

`test_verify_archive.py` ist die quelltextidentische, lokal tatsächlich unter **Python 3.13.5** ausgeführte **13/13 PASS**-Suite: eine synthetische Positivkontrolle und **zwölf** synthetische Ablehnungsfälle, darunter manipulierte Checkpoint-Bytes, falsche Gesamtarchiv-SHA, fehlender Checkpoint, extraneous Entry, falsche Größe, doppelter ZIP-Member, doppelter JSON-Key, falsche Modellkarte, Symlink und Pfad-Traversal. Die lokal getesteten Git-Blobs sind `cfcc5bcd892675b076cf42feadf04c5b13dccd7a` (Prüfer) und `a39e162a83c35cbeb682c9aeb99063a605368260` (Tests). Diese Git-Objekte wurden vor Commit-Erstellung separat über den GitHub-Connector erzeugt; beide Git-SHAs entsprachen exakt `git hash-object` der ausgeführten lokalen Dateien.

Wiederholung mit **eigenen** authentisch registrierten Eingaben, ohne Hashpublikation in Logs:

```bash
python3 -B -m unittest -v test_verify_archive.py
python3 -B verify_archive.py <private-zip-path> --expected-sha256 <trusted-64-hex-from-private-registration>
```

## Neuer Provenienzhinweis und Grenzen

Im authentischen Modellarchiv stehen `knownTrainingFit.scope=POST_SELECTION_TRAIN_FIT_ONLY` und `dataset=TRAIN`, während die darin abgelegten Scoreobjekte `split=test` tragen. Das ist eine **Benennungs-/Kontextambiguität** des Recordformats, kein Beweis, dass der historische verbrauchte Finaltest erneut genutzt wurde. Die Originalbelege ordnen 120/128 ausdrücklich dem TRAIN-Fit zu; ohne Originalcontrolleranalyse keine Umdeutung. Die Qualitätsfelder melden weiterhin `generalCapacityGatePassed=false` und `limitedBenchmarkLearningCriterionPassed=false`. Diese eingelesenen Felder sind **keine neue Qualitätsmessung**.

Die neuen ZIP-/Manifest-/Synthetic-Verifikationen **ersetzen nicht** vollständige dynamische originale #508-Controller-Regressionen, insbesondere nicht die Wiederaufnahme-Pfade, manipulierte Baseline/Receipt oder das Capacity-/Candidate-Wahlgate. Diese bleiben **NOT_RUN**. Der vorhandene kombinierte Patch ist nach der zuvor belegten Quellenprüfung 16/16 passend, wurde in dieser Runde nicht erneut appliziert und ist **nicht in PR #508 integriert**. Keine neue freigegebene Lernrate, EOS-Ursache oder Retentionsbehauptung; `M5_UNCHANGED`, `M6_PARITY_FAIL_RESTORED`, alte Qualitätsabnahme `FAIL`, Finaltest verbraucht.

Nächste legitime Quelle-Gate-Aufgabe: Originalcontroller und unveränderte Datensatz-/Initial-Dev-Receipts explizit zuordnen; nur synthetische ungültige Baselines, Checkpoint-Manipulation und unberechtigte Wiederaufnahme mit modellfreiem Stub ausführen, **nicht** echte Modellprozesse starten. Die separate neue Train/Dev/Final-Registrierung und externe Finaltest-Verbrauchssperre müssen vor einer späteren Messstudie unabhängig freigegeben werden.
