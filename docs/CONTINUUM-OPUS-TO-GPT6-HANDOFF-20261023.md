# Continuum – verbindliche Übergabe Opus → GPT-6 (23.10.2026, spät)

> **Neuer Stand (Nacht zum 24.10.):** Heinz hat den GPT-6-Auftrag an Opus übertragen. Aktueller Einstieg:
> `docs/CONTINUUM-OPUS-STAND-20261023-NACHT.md` (Zweige, Kandidat 0.3.18, Reparaturen, offene Besitzerentscheidung).

**Für dich, GPT-6:** Diese Datei ist vollständig. Du brauchst weder meine Sitzung noch temporäre Dateien. Lies sie
einmal ganz, dann arbeite autonom nach Abschnitt J. Alles hier ist gepusht; jede Zahl ist mit Datei und Commit belegt.

- **Branch:** `opus/ccc1-e2e-20261023` (Spitze per `git ls-remote origin refs/heads/opus/ccc1-e2e-20261023`)
- **Draft-PR:** [#99](https://github.com/Ullmann27/device-ai/pull/99), Basis `integration/continuum-current-review-20261023`
  @ `81180313` (PR #97). Kein Merge nach `main`, kein Release, keine Installation ohne Besitzer.
- **Ältere ausführliche Übergabe (Details, Belegtabellen):** `docs/CONTINUUM-OPUS-FIRST-BREAKTHROUGH-20261023.md`

## 0. Erste 15 Minuten (genau so)

```bash
git fetch origin opus/ccc1-e2e-20261023
git switch -c gpt6/<thema> FETCH_HEAD          # eigener Zweig; meinen nie force-pushen oder umschreiben
env -u NODE_TEST_CONTEXT node --test .github/continuum/*.test.mjs scripts/*.test.mjs      # Soll: 986 / 980 / 0 / 6 übersprungen
(cd backend && npm ci && node --test test/*.test.js)                                       # Soll: 60 / 60
CONTINUUM_ANDROID_DEPS=<jars> CONTINUUM_JUNIT_JARS=<junit:hamcrest> scripts/android-kotlin-unit-check.sh   # Soll: OK (855 tests), 4 Checks
```

Die 6 übersprungenen Node-Tests brauchen `kotlinc` im `PATH` (mit `kotlinc`: bestanden). Die Jar-Liste für den
Kotlin-Check steht im Kopf von `scripts/android-kotlin-unit-check.sh` (android-all-15, okhttp, okio, shizuku-*).
Weicht eine Zahl ab: zuerst die Ursache finden, nichts Neues bauen.

## A. Was heute erreicht wurde

1. **CCC1 Ende zu Ende** (außerhalb des Geräts bewiesen): Besitzerauftrag → Termux-Brücke (`ccc1-*` im Builder-Skript) →
   Journal/Gate → bestehende Reparatur-Pipeline → unabhängiger zweiter Arbeitsstand → CCC1-Verifier → Patch nur
   angeboten. Echter Lauf auf Klon des Repos: Baseline 792/1 rot → Kandidat 792/0 → unabhängig 792/0
   (`docs/evidence/ccc1-e2e-20261023/`). 18 Pflicht-Negativfälle mit Test oder Beleg.
2. **CCC1 auf dem Fold7 lauffähig gemacht:** Ursache für „Keine verlässliche CCC1-Rückmeldung“ = zu alter
   Termux-Checkout. Anleitung `docs/CONTINUUM-CCC1-AUF-DEM-GERAET.md`; Termux-Befehle
   `continuum-teamleader-termux.sh ccc1-check [--full] | ccc1-prepare | ccc1-once | ccc1-status | ccc1-review` (`a4331044`);
   App erklärt jede Ablehnung selbst (`0a268a86`).
3. **Abbruch an der Schrittgrenze** (`c397ff1b`): Prüfpunkt vor jedem Prüfprofil, Kandidaten und unabhängigen Lauf;
   echter Lauf 38,7 s statt 70,6 s (`docs/evidence/ccc1-e2e-20261023/cancel-step-boundary/`).
4. **Issue #76** (`7c6291f9`): `commit()` meldet bei gesetztem Interrupt-Flag `false`, obwohl geschrieben wurde
   (am Android-15-Framework-Bytecode nachgewiesen). Abgefangen ohne Doppelzählung; übrige Ursachen mit festen Codes
   (`STORAGE_FULL`, `NOT_WRITABLE`, `COMMIT_FALSE`, `STOPPED`). **Gerätebestätigung offen → Issue bleibt offen.**
5. **Aufräumen:** PR #72 (I1 zertifizierte Enthaltung, I2 bedingtes Risiko, neuerer Span-Kern) selektiv übernommen,
   Verträglichkeit dreifach bewiesen (`docs/CONTINUUM-I1-I2-ADOPTION-20261023.md`); OS-Isolation (`ldd`-Auswertung)
   repariert; Fold7-Reflow-Test aus PR #98 aktualisiert.
6. **Forschung A–D** (alle vorregistriert, TEST je einmal, nur Test-Quellset): siehe C.

## B. Commits seit dem APK-Stand (0.3.17 enthält diesen Branch bis `c72bd98f`)

| Commit | Inhalt | Wohin |
|---|---|---|
| `a4331044` | Termux-Befehle `ccc1-*` | Termux-Checkout (nicht APK) |
| `0a268a86` | App-Diagnose für CCC1-Ablehnungen | **nächste APK** |
| `b11c8a93`, `c602ddae` | Geräte-Anleitung, Übergabe | Doku |
| `7c6291f9` | Fix Issue #76 | **nächste APK** |
| `c397ff1b`, `8a128c07` | Abbruch an der Schrittgrenze + Beleg | Termux-Checkout (Node-Worker) |
| `2f4bf587` … `6c7d600e` | Forschung B (LCT1), C (WSC1) | Test-Quellset + Doku |
| `e9714f70`, `d7cc9f09` | Übernahme PR #72 (Span-Kern, I1, I2) + Doku | Hauptquellen ohne Aufrufer (APK-neutral) |
| `2c946759`, `abcede88` + Folgecommit | Forschung A (TTT1) | Test-Quellset + Doku |

Probe-Merges (`git merge-tree`, kein Zweig verändert): **konfliktfrei** gegen `integration/continuum-current-review-20261023`
@ `81180313` **und** gegen den Signierzweig `astra/render-mentor-apk-builder-20261019` @ `285639ba`.

## C. Messwerte der Experimente (`docs/research/*-RESULT-20261023.md`, Register `EXP-13`)

| Exp. | Frage | Ergebnis | Entscheidung |
|---|---|---|---|
| D CDX1 | Patch-Anomalie-Sensor | erkennt nur ungewöhnliche Bytes; Byte-4-Gramm gleich gut, 6× schneller | kein CDX1-Signal in CCC1 |
| B LCT1 | nachvollziehbares Mehr-Hop-Schließen | **0 falsch** in 600 Fällen/24 Varianten, Abdeckung 1,0; Prüfer lehnt 1 175/1 175 Mutationen ab; alter EBTS-Algorithmus 150 falsch | L+V = Forschungskern; Fast-Weights ohne Vorteil |
| C WSC1 | geprüfte Konsolidierung | 0 falsch in 1 200 Fragen nach Wissensänderungen; zuvor gelöste Fragen **~13× schneller** (44 vs 571 µs); Treffer 30 % vs 0,3 % gewöhnlicher Cache; ohne Abhängigkeiten 129 falsch | WSC1-Muster statt neuronaler Konsolidierung |
| A TTT1 | Arbeitsgedächtnis | Entitätsspeicher **680/680**, 0 falsch; Kontextfenster 98 falsch; +Fast-Weights 73 falsch | Speicher = Baseline; Fast-Weights nicht übernehmen |

Gemeinsame Lehre: Der messbare Gewinn kam jedes Mal aus **expliziter, prüfbarer Struktur** (IDs, Quellen, Zeit,
Abhängigkeiten, Gleichstand → Rückfrage), nie aus 16-D-Fast-Weights.

## D. Was ist produktiv, integriert, experimentell?

- **In APK 0.3.17 (vom Besitzer eingerichtet):** CCC1-Brücke (Kotlin) bis `c72bd98f`, Activity/Empfänger-Verdrahtung.
- **Im Branch, nicht in der APK:** App-Diagnose `0a268a86`, Fix #76 `7c6291f9`, Span-Kern/I1/I2 (ohne Aufrufer).
- **Wirkt über den Termux-Checkout, nicht die APK:** `ccc1-*`-Befehle, Abbruch an der Schrittgrenze.
- **Rein experimentell (Test-Quellset, nie in die Laufzeit ohne neues Tor):** LCT1, WSC1, TTT1, CDX1-Experiment D.

## E. Tests (zuletzt am Branch ausgeführt; Linux-Container, Node 22, JDK 21)

Node 986/980/0/6 · Kotlin 855/855 + 4 Checks (`scripts/android-kotlin-unit-check.sh`) · Backend 60/60 · I1/I2-Belege
bytegleich reproduziert. **Nicht ausgeführt** (kein SDK): Gradle/AGP, Lint, Robolectric-UI (`src/uiTest`), APK, Gerät.
GitHub Actions: jeder Lauf endet nach ~2 s ohne Schritte (Konto-/Runner-Ebene, Issue #54) – keine Codeursache.

## F. Offene Fehler und Risiken

1. CCC1 aus der App lief **noch nie auf dem Fold7**; zuerst Termux-Checkout aktualisieren (Anleitung oben).
2. Issue #76: Fix nicht in 0.3.17; ob genau dieser Mechanismus die Gerätemeldungen auslöste, zeigt erst ein Gerätelog.
3. Abbruch: ein gerade laufender Prüfschritt läuft zu Ende (bei Gradle Minuten); sein Prozess wird nicht beendet.
4. Rückkanal ≤ 32 768 Zeichen; ob Termux `stdout_original_length` immer mitsendet, ist auf dem Gerät zu bestätigen.
5. Reparaturbreite: bewiesen ist der deterministische Weg (`REVERT_FILE_TO_PARENT`); sonst ehrlich `FAILED_REPAIR`.
6. I1/I2: ein `Certificate` ist von Hand konstruierbar (kein attestiertes Kalibrierergebnis).
7. Forschungsdaten sind synthetisch und vom selben Autor wie die Systeme.

## G. Was in der Besitzer-APK fehlt

`0a268a86` (CCC1-Diagnose) und `7c6291f9` (Fix #76). Optional `e9714f70` (APK-neutral). Alles andere wirkt über den
Termux-Checkout oder ist Forschung.

## H. Sichere Integrationsreihenfolge (keine Cherry-Picks, Astras UI nie überschreiben)

1. PR #99 als Ganzes in `integration/continuum-current-review-20261023` (Probe-Merge konfliktfrei). Vorher prüfen, ob
   die Basis inzwischen weitergewandert ist; dann erneut `git merge-tree --write-tree` laufen lassen.
2. Astras Premium-UI (PR #98 und ihr zuletzt **gepushter** Stand) laut `docs/CONTINUUM-PREMIUM-UI-INTEGRATION-GATE-20261023.md`.
3. Auf dem eingefrorenen Integrations-SHA: Node-Suite, Kotlin-Check, Backend; dann mit echtem SDK
   `./gradlew :app:testDebugUnitTest :app:lintDebug` und Robolectric-UI.
4. In den Signierzweig `astra/render-mentor-apk-builder-20261019` (Probe-Merge konfliktfrei) bzw. den dort üblichen Weg.

## I. Nächster Build und Verifikation (signierter Update-Kandidat)

1. Installierte Version auf dem Fold7 bestätigen (Einstellungen → Apps → Continuum → Version; oder
   `adb shell dumpsys package at.heinz.deviceai.mentorpaired | grep version`). Ausgangsstand laut Besitzer: 0.3.17 / 18.
2. `app/build.gradle.kts`: `versionCode` > installiert (bei 18 → **19**, `versionName` 0.3.18), Paket unverändert
   `at.heinz.deviceai.mentorpaired`.
3. Signieren nur über den bestehenden Render-Signierdienst; Schlüssel nie ins Repo, nie ausgeben.
4. Prüfen: `apksigner verify --print-certs` → Zertifikat-SHA-256 `2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc`;
   `aapt dump badging` → Paket und `versionCode`; SHA-256 der APK notieren; 0.3.17-APK als Rollback aufbewahren.
5. In-place-Update (keine Deinstallation, Lerngewichte und private Daten bleiben). Auf dem Gerät prüfen: CCC1-Ablauf
   (Vorprüfung → Einreichen → Schritt → Status → Abbruch), Rückkanal, Forschungszyklus ohne `FAILED_SAVE_CYCLE`.
6. Veröffentlichung, Installation und jeder `main`-Merge nur mit Besitzerfreigabe.

## J. Dein Einstieg – wie ich weitergemacht hätte (du hast Handlungsspielraum)

**Reihenfolge, die ich gewählt hätte** (du darfst sie mit Begründung ändern, wenn Belege es nahelegen):

1. **Gerät zuerst:** Den Besitzer durch `docs/CONTINUUM-CCC1-AUF-DEM-GERAET.md` führen und das Ergebnis von
   `ccc1-check --full` festhalten. Ein echter Gerätelauf ist mehr wert als jeder weitere Host-Test.
2. **Nächste APK** mit `0a268a86` + `7c6291f9` (Abschnitte H/I). Danach #76 und die CCC1-Diagnose auf dem Gerät prüfen;
   Issue #76 erst nach Gerätebestätigung schließen.
3. **CCC1 stärker machen** – innerhalb der bestehenden Pipeline, keine zweite:
   - Abbruch auch **während** eines Prüfschritts: ein kleiner Wächterprozess zwischen `runProfile` und dem Kind, der
     die Prozessgruppe beendet, sobald der Backlog-Auftrag `ABORTED` ist. Achtung: Zeitüberschreitung/Signal-Erkennung
     (`timedOut`) und JUnit-Ausgabe dürfen sich nicht ändern; mit Mutationstest absichern.
   - Mehr Reparaturstrategien nur mit unabhängigem Lauf und Beleg; erfolglose Strategien je Fehlerbild meiden
     (`repair-ledger` gibt es schon).
   - WSC1-Muster auf Reparaturerfahrung übertragen: geprüfte Patches mit Abhängigkeiten (betroffene Dateien,
     Basis-Commit) wiederverwenden, sonst neu prüfen.
4. **Intelligenz, nach Belegen:** LCT1 (L+V) im **Forschungspfad** `ContinuumCortexResearchPipeline` an Stelle von
   `EvidenceBoundedThoughtSearch` anbinden; Siegelprüfung beim Laden zwischenspeichern (~40 µs → wenige µs); echtes
   Besitzersiegel statt `ownerReviewed`-Flag. TTT1-Entitätsspeicher als Rückbezugs-Auflösung hinter Flag im Chat
   erproben (vorher Sprachanalyse für Fürwort/Prädikat messen – die fehlt noch). I2-Zertifikate vor jede Span-Antwort.
5. **E (GGUF/Spine):** hier nicht messbar (kein Modell, keine Laufzeit). Kleinster nächster Schritt: Kontextpackung
   (`continuum-reviewed-context-shadow.mjs`) offline auf einem festen deutschen Fragenkorpus messen – enthält das
   gepackte Fenster die belegende Stelle? – und erst dann ein Modell auf dem Gerät.

**Worauf du achten musst:**

- Vorregistrieren **vor** dem ersten Lauf, TEST genau einmal, Code vorher committen; Präzisierungen nur vor dem Lauf und
  als eigener Commit. Mutationstests für jeden neuen Test.
- Nie Besitzergewichte/Checkpoints ändern; keine Forschung in die Laufzeit ohne Tor; Astra besitzt `ui/**`,
  `AgentActivity.kt`, `ContinuumHologramView.kt`, Manifest, `app/build.gradle.kts` (Versionsänderung mit Integration
  abstimmen).
- Gerätenachweis ≠ Host-Test; „nicht ausgeführt“ ist eine erlaubte, ehrliche Antwort.
- Kotlin-Tests ohne Gradle: `-module-name app -Xfriend-paths`; Node-Tests mit `env -u NODE_TEST_CONTEXT`.

**Ideen, die ich noch hatte (offen, ungeprüft):**

- Prüfer V mit Index, aber als zweite unabhängige Implementierung gegen die Rohdaten verifiziert (gleiche Urteile,
  schneller).
- Gewohnheiten (WSC1) für Enthaltungen: auch „nicht beantwortbar“ mit Abhängigkeiten zwischenspeichern.
- Besitzer-Notiz (nicht als Auftrag eingegangen): EXP-SYNAPSE-ZERO (HRR in R¹⁶, Sinkhorn, Zwei-Hop mit
  Doppel-Offset), adaptive Dämpfung γ = 0,85 in `ContinuumHolographicEquilibriumCore.kt`, Prüfung „bei `updates == 0`
  keine Antwort“ und „`answered.size` statt `outcomes.size`“. Die Datei liegt nicht in meinem Zweig; vermutlich auf
  `experiment/continuum-synapse-zero-20261022` @ `2b58ed17`. Wenn du das aufgreifst: zuerst den Zweig lesen, dann mit
  derselben Disziplin (Vorregistrierung, Holdout, Gegenprobe) – der Unterschied zwischen P(antwortet ∧ falsch) und
  P(falsch | antwortet) ist genau I1 gegen I2 (`ContinuumSelectiveRiskCertification.kt`).

**Rücksetzweg:** Jeder Commit ist einzeln revertierbar; vor `89b353d5` liegt unverändert `81180313`. Für die
PR-#72-Übernahme: `e9714f70` und `d7cc9f09` revertieren.
