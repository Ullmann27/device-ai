# M6 EWM2 – echter JVM-Hostlauf ohne GitHub Actions

**Status 31.10.2026:** Der prüfbare Run-Pfad ist im bestehenden Draft-PR #258 umgesetzt. Ein Lauf auf dem echten EWM-Produktionsalgorithmus mit allen 240 eingefrorenen Fällen ist **NOCH NICHT AUSGEFÜHRT**. Weder EWM-Precision noch Fold7-Funktion gelten dadurch als nachgewiesen.

## Ausführungsumgebung

Ein vollständiges Checkout des Branches `chatgpt/continuum-m6-independent-holdout-20261030`, Node.js 22+, JDK und `kotlinc` im PATH. Keine Android-SDK-, Cloudflare-, NVIDIA- oder GitHub-Actions-Berechtigung erforderlich.

## Einmaliger unabhängiger Lauf

    node scripts/continuum-ewm-v2-holdout-integrity.mjs
    node --test scripts/continuum-ewm-v2-holdout-score.test.mjs scripts/continuum-ewm-v2-run-jvm.test.mjs
    node scripts/continuum-ewm-v2-run-jvm.mjs --receipts /tmp/continuum-m6-actual-240.jsonl

Das Skript prüft vor jeder Kompilierung den eingefrorenen Git-Blob des 240-Paar-Holdouts sowie die beiden unveränderten EWM-Produktionsquellen. Kandidat: vollständiger SHA `3ba1ba0bba28e441d2b85ae098371901d3db7407`. Die JVM lädt genau `Ewm1Lexicon.kt`, `Ewm1EvidenceWorkspace.kt` und `M6ForeignEwmProbe.kt` mit `kotlinc` und verarbeitet 240 Fälle, ohne die erwarteten Labels zu sehen.

Jede Testeingabe enthält lediglich die ID und die drei Sätze (vorher, danach, unbeteiligter Kontrollfakt). Der Produktionsgraph meldet: ob `after` `before` ersetzt, den Zustand aller drei Fakten und die tatsächlichen Kanten. Node ergänzt die eingefrorenen Soll-Labels **erst nach dem JVM-Lauf** und erstellt die vorregistrierten Metriken. Die JSONL-Ausgabe wird mit Dateierstellungsmodus `wx` und Berechtigungen 0600 geschrieben; eine bestehende Datei wird nicht überschrieben.

## Wichtige fachliche Grenze

`Ewm1Memory.supersedes` produziert ausschließlich eine **binäre Ersetzungskante**. Es unterscheidet in seiner Ergebnis-API nicht zwischen den positiven Untertypen `SUPERSEDES_STATE` und `CORRECTS_EVENT`. Deshalb codiert der Probe jede beobachtete positive Kante explizit als `SUPERSEDES_STATE`, **ohne das erwartete Label zur Untertyp-Klassifikation zu verwenden**. Der binäre Precision/Recall-Gate-Test bleibt aussagefähig; dreiklassige Confusion-Matrix und Untertyp-F1 sind nur diagnostisch und **kein** Nachweis der Ereignis-/Zustands-Unterscheidung. `positive_subtype` ist in den Belegen entsprechend `NOT_EMITTED_BY_PRODUCT`.

## Fehlerschranken

- Abweichender Git-Blob des Holdouts oder einer der beiden EWM-Kotlin-Dateien: Lauf bricht **vor** der Ausführung ab.
- Fehler in `kotlinc`, JVM, Anzahl, Format oder Reihenfolge der Rückgaben: `NOT_ACCEPTED`, keine behauptete Modellleistung.
- Positiver Precision-Wert <0,95 oder auch nur ein verlorener unbeteiligter Kontrollfakt: `FAIL`.
- Bei echtem `PASS`: nur eingegrenzte binäre EWM-Supersession auf genau diesem Holdout; keine allgemeine Sprachintelligenz behaupten.
- Kein einzelner Test darf auf Basis der Gold-Antworten nachträglich den Produktionsalgorithmus verändern. Kein Auto-Merge, kein Produktionsbranch-Schreibzugriff.

## Überprüfter Entwicklungsstand

- Kotlin-Probe lokal gegen eine ausdrücklich **künstliche** `Ewm1Memory`-Stub-Klasse auf JVM kompiliert; künstlicher 240-Zeilen-Ein-/Ausgabe-Smoketest erfolgreich. **Das ist keine echte EWM-Auswertung.**
- JVM-Node-Startskript lokal mit `node --check` auf Syntax geprüft.
- Zehn neue Node-Tests prüfen vorab Label-Isolation, Unicode, Kanteninterpretation, Zuordnung, Vollständigkeit und Fehlformate. **GitHub-CI ist aktuell gestört; kein Hosted-CI-PASS behauptet.**
- APK-/Update-Arbeit verbleibt bei Jules. Die neuen Dateien betreffen ausschließlich den M6-Forschungszweig.

**Nächste echte Abnahme:** Das unabhängige Skript auf einem vollständigen Checkout mit eingefrorenen Produktionsdateien ausführen, 240-Receipt-Datei samt Hash und Ergebnis dokumentieren. Ohne diese Ausgabe bleibt `M6 ACTUAL = NOT_RUN`.