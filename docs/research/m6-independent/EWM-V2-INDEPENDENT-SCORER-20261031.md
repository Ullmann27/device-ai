# M6 – unabhängige Auswertung des EWM-v2-Holdouts (31.10.2026)

**Status: Auswerter lokal 15/15 getestet. Tatsächlicher EWM-Lauf auf den 240 eingefrorenen Fällen: NOT_RUN.**

Bestehende Holdoutdaten, vorregistrierte Kriterien und EWM-Produktivquellen bleiben unverändert. Dieser Nachtrag liefert ausschließlich einen strengen Auswerter für spätere externe Laufbelege. Kein Android-/Gradle-Eingriff; Jules arbeitet getrennt an APK und Update-Button.

## Unabhängiger Laufvertrag

Ein unabhängiger EWM-Runner führt den vorher eingefrorenen Kandidaten genau einmal auf 240 Testpaaren aus. Soll-Labels dürfen nicht als Eingabe an den Kandidaten fließen. Die JSONL-Datei enthält für jede ID M6X-001 bis M6X-240: id, candidate_sha (volle SHA-1-Commit-ID), expected_label, predicted_label, reasons, before_current, after_current und unrelated_fact_kept.

Die drei Zustandsflags müssen aus einer **unabhängigen Inspektion des tatsächlichen EWM-Ersetzungsgraphen** stammen. Modellselbstaussagen ohne Zustandsnachweis sind kein zulässiges Testergebnis. Das erwartete Label wird erst beim Auswerten zugeordnet, nicht beim Inferenzaufruf.

## Aufrufe

    node scripts/continuum-ewm-v2-holdout-integrity.mjs
    node --test scripts/continuum-ewm-v2-holdout-score.test.mjs
    node scripts/continuum-ewm-v2-holdout-score.mjs --receipts ./m6-real-receipts.jsonl --candidate-sha VOLLSTAENDIGE_40_ZEICHEN_COMMIT_SHA

Der Auswerter prüft den unveränderten Git-Blob 622370c291ead9ab11ec8917c992037a2fcf9e17, exakt 240 Fälle und alle IDs, dass Erwartungswerte aus den eingefrorenen Daten kommen, sowie konsistente Commit-SHA, Erklärungstext und drei Wahrheitswerte pro Zeile.

## Ergebnisstatus und Exitcodes

- **2 / NOT_ACCEPTED**: fehlerhafte, doppelte, unvollständige, umgelabelte oder gemischte Belege; keine Metrik-Ausnahme.
- **1 / FAIL**: echte formal vollständige Vorhersagen, aber Gesamt-Precision positiver Ersetzung < 0,95; undefinierte Precision wegen null positiver Vorhersagen; oder mindestens ein verlorener unbeteiligter Kontrollfakt.
- **0 / PASS**: vollständige Daten, positive Ersetzungs-Precision >= 0,95, null verlorene Kontrollfakten. Bericht enthält Konfusionsmatrix, klassenweise Precision/Recall/F1, Makro-F1 und positive Precision/Recall.

**Grenze:** Die 240 Fälle stammen aus Varianten von nur 24 Basiskonstruktionen. Ein PASS allein beweist weder breite Generalisierung noch eine funktionierende Fold7-APK. Vor einem echten unabhängigen Lauf ist die Modellerfolgsquote unbekannt.

**Änderungssicherheit:** Ergänzung direkt in bestehender Draft-PR #258, kein neuer PR, kein direkter Main-Push, keine Berührung von Jules-Dateien.

## Echter Kotlin-Graphlauf (neu)

Für diesen Stand wurde zusätzlich **kein neuer PR** eröffnet. Die neue Datei `app/src/test/java/at/heinz/deviceai/intelligence/ewm/Ewm2Foreign240ReceiptTest.kt` ruft auf dem bestehenden EWM2-Produktionscode `Ewm1Memory` auf. Die verbundene Ersetzungskante `after -> before` kommt direkt aus dem originalen Graphen; das Testprogramm enthält **keinen eigenen Ersatz für `replaces()`**.

Der Test erkennt einen expliziten korrigierten Vergangenheitsfall anhand der **Produktionsmerkmale** `tense` und `correction`. Nur bei vorhandener Ersetzungskante kann das Ergebnis `CORRECTS_EVENT` bzw. `SUPERSEDES_STATE` sein. Fehlende Kanten ergeben `COEXISTS`. Die Klassifikation verwendet das Gold-Label nicht; das Gold-Label wird erst nach der Modellentscheidung für den externen Auswerter beigefügt.

Vor der Ausführung werden die folgenden **originalen Git-Blob-Hashes** verifiziert:

| Datei | Eingefrorener Git-Blob |
|---|---|
| Ewm1EvidenceWorkspace.kt | `cb19581af34ad177add22a89638988a74a9c7aad` |
| Ewm1Lexicon.kt | `8c1e6acd4a7ee2cf632b115550eb7c7d5c801df6` |
| Ewm1Workspace.kt | `b338a755a161a620405bd3581bf45fb74b1c43fc` |
| EWM-v2 Fremdholdout | `622370c291ead9ab11ec8917c992037a2fcf9e17` |

Der Prüflauf ist damit auf den **eingefrorenen Kandidaten-Commit** `3ba1ba0bba28e441d2b85ae098371901d3db7407` und die nachweislich unveränderten produktiven Klassen festgelegt.

### Einmalige Ausführung

```bash
bash scripts/continuum-m6-foreign-eval.sh
```

Voraussetzungen: Git-Checkout dieses bestehenden PR-Branches, Git, Node.js, Android SDK und eine funktionsfähige Android-Gradle-Umgebung. Das Skript prüft die Holdout-Integrität, löscht nur die **alte erzeugte** Receipt-Datei unter `app/build/reports/ewm-m6/`, führt gezielt `Ewm2Foreign240ReceiptTest` aus und bewertet erst anschließend die erzeugten Belege. Es verwendet keine NVIDIA-/Cloudflare-API und ändert keinen Produktionscode. Die Prüfdaten verbleiben unverändert im Repository.

Die **testseitige Klassifikationsabbildung** (Ersetzungskante plus Vergangenheitsmerkmal) ist als Auswertung des internen EWM-Graphen zu verstehen; sie ersetzt weder die Antwortqualität der Gesamt-App noch die unabhängige Validierung von tatsächlichen Nutzerfragen.

### Abnahmezustand

- Quellcode und echte 240-Fälle-Ausführungshilfe: **COMMITTED**.
- 15 deterministische Node-Auswertertests: **lokal berichtet als bestanden**.
- Kotlin-Gradle-Lauf: **NOT_RUN** in dieser Ausführungsumgebung, da hier kein zugänglicher vollständiger Android-Gradle-Build vorhanden ist.
- Echte Precision/Recall/F1 auf 240 Fällen: **UNKNOWN**, nicht 100 %, nicht geschätzt.
- Fold7/APK-Release: weiterhin **Jules**, getrennte Arbeit.

Die reine Existenz eines JUnit-Tests ist **kein** Nachweis für einen erfolgreichen Android-Gradle-Test oder einen EWM-Messwert.
