# Continuum M6 · Unabhängiger EWM-v2-Ersetzungs-Holdout (30.10.2026)

## Auftrag und Grenzen
- **Prüfgegenstand:** unveränderter Kandidat aus `claude/ewm-owner-subject-fix-kxrxy2`, vorheriger Status: 47/47 neue/Bestandstests und 788/788 Hosttests durch Opus berichtet, **Entwicklungsorakel ist kein Holdout**.
- Dieser PR enthält nur unabhängige Testdaten und den **vorab registrierten** Auswertungsvertrag. Kein Patch am EWM-Produktivcode, keine zweite Memory-DB, keine Änderung an OwnerGate oder ToolGate.
- **Datensatz:** 240 unabhängige, fiktive Paare in `ewm-v2-foreign-240.jsonl`, 80 `SUPERSEDES_STATE`, 80 `CORRECTS_EVENT`, 80 `COEXISTS`. Systematische 8×10-Varianten pro Klasse (kein Anspruch auf 240 rein von Hand unabhängig formulierte Sprachstile). Enthält Orts-, Preis-, Ereignis-, Zeit-, Drittpersonen- und Negativkontrollen.
- **Primärziel:** Precision der positiven Ersetzung ≥ 0,95; zusätzlich Recall und Confusion Matrix berichten (kein verdecktes Nachjustieren). `SUPERSEDES_STATE` und `CORRECTS_EVENT` gelten positiv; `COEXISTS` negativ.
- **Harte Sicherheitsinvariante:** Ein unbeteiligter Kontrollfakt darf nicht verschwinden, von einer Ersetzungskante getroffen oder als `OUTDATED` markiert werden: 0 Vorfälle aus 240.
- **Unvermeidliche Grenze:** In dieser Runde ist keine unabhängige EWM-JVM-Ausführung vorhanden; alle Werte bleiben `NOT_RUN`. Ein 1,0-Ergebnis aus Opus' Entwicklungsorakel darf **nicht** als Beweis für fremde Formulierungen wiederverwendet werden.

## Vorregistrierter Ablauf (keine nachträgliche Änderung)
1. Kandidaten-SHA per Git commit ermitteln und unverändert einfrieren, die 240 Fälle als Dateiblob-SHA dokumentieren; vor diesem Punkt keine Ergebnisse erzeugen.
2. Harness verwendet dieselbe Klassifikation wie der Produktions-Ersetzungsgraph auf der eingefrorenen EWM-Linie. Keine Heuristik im Prüfer, keine Sonderfälle für Testfälle.
3. Erst danach Dataset **einmal** durchlaufen und `predicted_label, expected_label, reasons, before_current, after_current, unrelated_fact_kept` als unveränderliche JSONL-Belege protokollieren.
4. `TP/(TP+FP)` für Gesamtersetzung und je Subtyp separat, Recall, Makro-F1, Confusion Matrix; bei 0 vorhergesagten positiven Fällen Precision nicht künstlich auf 1 setzen, sondern `UNDEFINED` und Gate `FAIL`.
5. Keine Optimierung auf diesen Holdout. Nach einem FAIL neue Entwicklungs-/Trainingsdaten und ein späterer neuer fremder, unberührter Holdout nötig.

## R7: offener Semantikvertrag
Die Abfolge „Anna wohnte in Wien → Anna wohnt inzwischen in Graz“ ist nach bisheriger Opus-Definition bewusst nicht automatisch ersetzt. Deshalb werden historische Aussagen **nicht** als eindeutig supersedierend gelabelt. Die Entscheidung über R7 bleibt dem Besitzer vorbehalten. Vergangene Besuche oder zukünftige Absichten müssen aktuelle Wohnfakten nicht verdrängen.

## Nachweis und Rollback
- Nur Forschungsholdout. Android-APK `NOT_RUN`, Fold7 `NOT_RUN`, Gerätelast `NOT_RUN`.
- Kein Auto-Merge nach main; bei Fehler Draft-PR offen lassen oder ohne Merge schließen. JSONL ist Testmaterial, enthält keinerlei persönliche Nutzerfakten.
