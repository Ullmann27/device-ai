# Continuum Fold7 Quality & Intelligence Validation Lab

Stand: 2026-11-06. **Unabhängige Prüf-Infrastruktur**, keine Änderung an Produktivcode, Signaturschlüsseln oder Releasewegen.

## Bestand vor der Entwicklung (ausschließlich lesen)

- Ausgangspunkt `main`: `2949893a7c65bdc6a875bdcb28b7a550fd9d7c3a` (30.10.2026). Dessen `app/build.gradle.kts` konfiguriert `at.heinz.deviceai` und v0.3.0. **Dies ist nicht die aktuelle originale Continuum-App**. Darum prüft das Labor immer explizit `at.heinz.deviceai.continuumlocal` und leitet keinerlei Freigabe aus dem alten `main`-Build ab.
- PR #565 (Claude Opus, Snapshot 06.11.2026, `35f6b8517dac35d5f4283b9435856f44d17276b3`): eigener offener Draft; Host-/Robolectric-Prüfungen und visuelle Host-Belege vorhanden. Die PR-Beschreibung weist explizit auf fehlende Fold7-Hardware- und Filament-GPU-Nachweise hin. Dieser Branch wird **nicht beschrieben**.
- `.github/workflows/android.yml` auf `main`: Gradle-Unit-Test und Debug-Build; kein Fold7-Hardwaretest. Das Labor fügt keine kostenpflichtigen CI-Runs hinzu.
- Bestehende Qualität ist in den aktiven Entwicklungsbranches weiter fortgeschritten als `main`; diese Standaufnahme ist kein Vollinventar sämtlicher paralleler Arbeit.

## Host-Voraussetzungen

Benötigt: Python 3.10+ und Standardbibliothek; für echte Messungen Android Platform Tools (`adb`) mit explizit autorisiertem Fold7. Für die **separate** Prüfung einer lokal bereitgestellten APK: Android Build Tools mit `apksigner` und `aapt`. `perfetto` kann später für manuell freigegebene Traces eingesetzt werden; die aktuelle v1 startet Perfetto **nicht** und analysiert keine GPU-Hardwaretraces. Auf dem Entwicklungs-Host bei der Erstprüfung fehlten ADB, Perfetto, apksigner, aapt sowie ein Fold7-Gerät.

Alle Befehle von der Repository-Wurzel aus:

```bash
python3 tools/fold7_quality_lab/fold7.py host
lab_evidence="$(mktemp -d)"
lab_source_sha="$(git rev-parse HEAD)"
python3 tools/fold7_quality_lab/run_unit.py --source-sha "$lab_source_sha" --output "$lab_evidence/host-tests.json"
python3 tools/fold7_quality_lab/report.py --source-sha "$lab_source_sha" --unit-results "$lab_evidence/host-tests.json" --output "$lab_evidence/abnahme.md"
```

Die historische Host-Quittung meldet **31/31 PASS, 0 FAIL, 0 SKIP** (Python 3.13.5, 06.11.2026), aber `source_sha: LOCAL_UNCOMMITTED`. Sie ist **nicht an einen Commit gebunden**; die frühere Zuordnung zu `2949893a…` im Erstbericht ist als `NOT_VERIFIED` korrigiert. Die Zahlen betreffen ausschließlich Softwaretests der unabhängigen Werkzeuge, **keine Android- oder Inferenztests**.

`run_unit.py` ermittelt den tatsächlichen Git-HEAD und kontrolliert vor und nach den Tests den sauberen Checkout sowie die Labor-Python-Dateien gegen die committed Git-Blobs. `--source-sha` ist nur eine Erwartung und kann den beobachteten SHA nicht ersetzen. Ohne Git, bei uncommittierten oder während des Laufs geänderten Quellen bleibt das Ergebnis `NOT_VERIFIED` mit Exitcode 1, auch wenn die Testzahlen erfolgreich sind. Die Ausgaben gehören außerhalb des Checkouts. Ein Bericht attestiert Host-PASS nur bei identischem vollständigem SHA und bestätigter Quellenbindung; ältere Quittungen ohne diese Prüfung bleiben ungebunden. Dies ist lokale Host-Provenienz, keine kryptographisch signierte Ausführungsquittung.

Die Werkzeuge überschreiben keine bestehenden Ergebnisse; neue Dateinamen pro Sitzung verwenden. `local-evidence` nicht hochladen, wenn Geräteinformationen oder Benchmark-Kontrollen enthalten sind.

## Echte Gerätediagnose: ausschließlich nach Einwilligung

1. Gerätebesitzer aktiviert autorisiertes USB-Debugging, verbindet Fold7, liest `adb devices -l` und wählt die **aktuelle, bewusst freigegebene Serienkennung**. Die Serienkennung wird von der Laborsoftware **nicht gespeichert**.
2. Installierte APK in der gewünschten Version verwenden; **niemals** aus diesem Lab eine APK installieren. Innen-/Außendisplay und Schriftgröße manuell am Gerät einstellen (100 %, 150 %, 200 %). Nicht aus Messwerten eine Schriftgröße oder Gerätefaltung erraten.
3. Mit demselben festgelegten Testszenario messen; mit eigener Aktion eine reale Modellanfrage starten, um Vorher/Nachher-Werte zu erfassen. Das Lab startet keine App und verändert keine Telemetrie-Zähler.

```bash
python3 tools/fold7_quality_lab/fold7.py capture \
  --serial 'ADB_DEVICE_ID' --consent-read-only \
  --scenario 'fixed-dialogue-v1' --display inner --font-scale 1.0 \
  --frame-budget-ms 8.333 --samples 12 --interval 5 \
  --workload-confirmed --output ./local-evidence/inner-100-run1.json
```

`--workload-confirmed` ist eine **Selbstauskunft**, keine verifizierte Workload-Kontrolle. Frame-Budget nur nach tatsächlicher Display-Refreshmessung wählen; 8,333 ms ist hier ausschließlich ein **Beispiel** für 120 Hz. Ohne korrektes Refresh-Budget keine belastbare Jank-Aussage.

Das Tool führt lesende Befehle aus: `getprop`, `pm path`, `dumpsys package`, `dumpsys meminfo`, `dumpsys cpuinfo`, `dumpsys battery`, `dumpsys thermalservice`, `dumpsys gfxinfo ... framestats`. Es führt **keine** Installation, keinen App-Neustart, kein Tap, keine Displayumstellung, kein `gfxinfo reset` und keinen Perfetto-Trace aus. `gfxinfo` kann alte Frames enthalten; Differenzen sind deshalb **explorativ**, nicht automatisch kausal. Batterietemperatur ist **nicht** CPU/GPU-Siliziumtemperatur. OS-Version und Herstellerfunktionen können die Verfügbarkeit einzelner Werte beeinflussen; fehlende Metriken bleiben `null`.

## Vergleich von zwei wirklich durchgeführten Geräteläufen

```bash
python3 tools/fold7_quality_lab/fold7.py compare \
  ./local-evidence/baseline.json ./local-evidence/candidate.json \
  --output ./local-evidence/compare.json
```

Vergleich nur für `device_adb_read_only`-Quellen bei gleicher Geräteklasse, SDK, Szenario, Display, Schriftgröße, Framebudget und selbst bestätigtem identischem Workload. Nicht passende oder synthetische Läufe ergeben `NOT_COMPARABLE`. Delta = Kandidat minus Basis; negative Werte sind nur bei bestimmten Metriken eine Verbesserung. Fehlende Werte ergeben `null`; keine erfundenen Prozentgewinne. Ein Vergleich zweier Läufe allein ist **kein statistischer Signifikanznachweis**.

## Original-APK-Integrität (ohne Publisherzugriff)

```bash
python3 tools/fold7_quality_lab/fold7.py verify-apk \
  --apk '/path/to/original-lokal.apk' \
  --expected-cert-sha256 '64-stelliger-verifizierter-HEX-SHA256-Wert'
```

Hier müssen eine **lokale APK** und der **zuvor unabhängig verifizierte** Signaturzertifikats-Fingerabdruck bereitstehen. `apksigner` prüft die APK-Signatur, `aapt` die Paketkennung; fehlt eines oder eine Angabe, bleibt `NOT_VERIFIED`. `pm path` bestätigt nur, dass das Paket installiert ist, **nicht** dessen kryptographische Identität. Die eingesetzte APK wird weder modifiziert noch veröffentlicht; keine Schlüssel werden gelesen oder übertragen.

## Unabhängiger Intelligence-Benchmark

Synthetische, maschinell objektiv auswertbare Aufgaben zu **Kontext/Pronomina, Bedingungslogik, zeitbewusster Gesprächskorrektur, Planung, Wahrheitstreue und Werkzeugauswahl**. Die Fragen verlangen absichtlich strukturierte JSON-Antworten; dies misst **nicht** automatisch freie Ausdrucksqualität oder echte Handlungsausführung.

```bash
python3 tools/fold7_quality_lab/intelligence.py generate \
  --split holdout --count 60 --output ./local-evidence/holdout-secret.json
python3 tools/fold7_quality_lab/intelligence.py export-prompts \
  ./local-evidence/holdout-secret.json --output ./local-evidence/prompts-only.json
```

Nur `prompts-only.json` dem getesteten lokalen Modell geben. Geheimen Kontrollkatalog samt Sollantworten **nicht** in Modellprompt, Produktiv-Repo, PR oder Training übertragen. Das Modell muss jede Antwort unter der zugehörigen `id` als `{"id":"S0001","answer":{"feld":...}}` in einer JSON-Liste liefern. Der Tester speichert tatsächliche Geräteantworten separat; Modell-/App-Quellstand, Datum, Bedienablauf und Inferenzquittung separat kontrollieren.

```bash
python3 tools/fold7_quality_lab/intelligence.py grade \
  ./local-evidence/holdout-secret.json ./local-evidence/model-responses.json \
  --provenance device_model_declared --model-ref 'MODEL_SHA' --app-ref 'APP_SHA' \
  --output ./local-evidence/model-score.json
```

Der Scorer unterscheidet `PASS`, `FAIL`, `NOT_RUN`, Score und Bereich. **Selbst gemeldete Herkunft ist noch keine überprüfte Modellinferenz.** Kein Ausführungssiegel wird von v1 kryptographisch bestätigt. Auswertung zweier vollständig bearbeiteter, **identischer** Kontrollsuiten:

```bash
python3 tools/fold7_quality_lab/intelligence.py compare \
  ./local-evidence/model-old-score.json ./local-evidence/model-new-score.json \
  --output ./local-evidence/model-diff.json
```

Das Generatorprogramm speichert die zufälligen Kontrollfälle, aber nicht den PRNG-Seed. Zur exakten Neugenerierung darf `--seed-file /secure/local/seed.bin` verwendet werden; dessen Inhalt bleibt **ausschließlich lokal**. Kontrollfälle, die ein Modell nachtrainiert oder vorher gesehen hat, sind nicht mehr unabhängig unbekannt. Immer eine frische Holdout-Suite erzeugen und die alte nur zur Regression verwenden.

Ein interner Funktionstest ohne Modellinferenz ist möglich:

```bash
python3 tools/fold7_quality_lab/intelligence.py selftest-responses \
  ./local-evidence/holdout-secret.json --output ./local-evidence/synthetic-answers.json
python3 tools/fold7_quality_lab/intelligence.py grade \
  ./local-evidence/holdout-secret.json ./local-evidence/synthetic-answers.json \
  --provenance synthetic_selftest --model-ref NONE --app-ref NONE \
  --output ./local-evidence/selftest-score.json
```

**100 % bei synthetischen Sollantworten ist kein Modell-Benchmark-Ergebnis.** Es bestätigt nur, dass das Bewertungssystem richtig arbeitet.

## Abnahmebericht und Sperrkriterien

```bash
python3 tools/fold7_quality_lab/report.py \
  --source-sha 'EXACT_SOURCE_SHA' \
  --unit-results ./local-evidence/host-tests.json \
  --telemetry ./local-evidence/inner-100-run1.json \
  --baseline ./local-evidence/inner-100-run0.json \
  --score ./local-evidence/model-score.json \
  --apk-check ./local-evidence/apk-result.json \
  --manual ./local-evidence/manual-checklist.json \
  --output ./local-evidence/abnahme.md
```

Manuelle Liste ist JSON mit optionalen Schlüsseln `restart`, `fold_unfold`, `rotation`, `inner_display`, `cover_display`, `font_100`, `font_150`, `font_200`, `inference`, `long_run`, `thermal_throttle`, `energy_consumption`; Werte: `PASS`, `FAIL`, `NOT_RUN`. Sämtliche Einträge werden als **Selbstauskunft** markiert; ohne Quittungen keine alleinige Freigabe. Fehlen Gerätelauf, Originalsignatur, echte Modellinferenz oder Prüfungen, lautet das Ergebnis `NOT_READY`. Auch bei vollständiger Liste fordert v1 eine **unabhängige Freigabe**; keine automatische Veröffentlichung.

## Noch offen (keine erfundenen Nachweise)

- Echte Fold7-Innen-/Außendisplay- und Falt-/Dreh-Tests einschließlich 100/150/200 %: **NOT_RUN**.
- Native Filament-GPU-/Perfetto-Trace, Frame-Pacing über echten kontrollierten Zeitraum: **NOT_RUN**.
- Reale lokale Inferenz, TTFT/Token-Latenz, thermische Drosselung, Langzeittest und tatsächliche Energie-Messung: **NOT_RUN**.
- Hardwarevariablen wie Refresh-Rate, Power-Rails und CPU/GPU-Frequenz müssen gerätespezifisch validiert werden; die v1 erfindet keine Messbarkeit.
- Mit PR #565 und Gehirnzweigen kann das Labor nach deren Commits auf **fixiertem Quell-SHA** getestet werden. Es schreibt niemals in diese Zweige.
