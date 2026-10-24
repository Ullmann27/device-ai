# Operation First Breakthrough – Ergebnis und Übergabe (Opus, 23.10.2026)

**Branch:** `opus/ccc1-e2e-20261023` · **Basis:** PR #97 `integration/continuum-current-review-20261023` @ `81180313`
(unverändert seit Arbeitsbeginn; 0 Commits zurück) · **Draft-PR:** gegen PR #97 · Kein Merge nach `main`, keine APK,
kein Release, keine Besitzerschlüssel berührt.

Statusbegriffe: **bestanden** = hier ausgeführt und grün · **nicht ausgeführt** · **Werkzeug nicht verfügbar** ·
**fehlgeschlagen**.

## Kurzfassung

| Durchbruchsziel | Stand |
|---|---|
| **A. CCC1 Ende zu Ende** | **bestanden außerhalb des Geräts:** echter Reparaturlauf über genau die Termux-Aufrufe der App auf einem Klon des echten Repositorys – echter Fehler gefunden, bestehende Pipeline, unabhängiger zweiter Arbeitsstand, CCC1-Verifier, Patch nur angeboten; zweimal reproduziert (gleicher Patch-Hash). **Nicht ausgeführt:** derselbe Lauf auf dem Fold7/Termux. |
| **B. Android als Ganzes** | CCC1-Brücke in Activity/Empfänger verdrahtet; alle 202 Hauptquellen kompilieren, **817/817 Kotlin-Unit-Tests** + 4 Prüfprogramme grün (außerhalb Gradle). **Nicht ausgeführt:** Gradle/AGP-Build, Lint, Robolectric-UI-Tests, Gerät. |
| **C. Signierte Besitzer-APK > 17** | **Von der Integration gebaut (nicht von mir):** 0.3.17 / Code 18 aus `astra/render-mentor-apk-builder-20261019` @ `285639ba` (enthält diesen Branch bis `c72bd98f`), Render-Signierdienst, Gradle-Tests + Lint grün, gleiches Zertifikat (PR #100, Kommentar vom 23.10. 15:38). **Nicht bestätigt:** Installation und Test auf dem Fold7. |

## CCC1 auf dem Gerät (Nachtrag 23.10. abends)

Die App 0.3.17 schickt `ccc1-*` an den **Termux-Checkout** `~/device-ai`. Laut Issue #41 stand der zuletzt auf einem
alten Commit (`91291cb2`). Nachgestellt: Dort antwortet `ccc1-status` mit Exit 2, leerem stdout und der alten
Usage-Zeile – die App zeigt nur „Keine verlässliche CCC1-Rückmeldung“. **Lösung ohne neue APK:** drei Git-Befehle in
Termux (`docs/CONTINUUM-CCC1-AUF-DEM-GERAET.md`), danach `bash scripts/continuum-teamleader-termux.sh ccc1-check`.
Wörtlich durchgespielt: danach `BEREIT`, und der Kotlin-Code der **installierten** App (`285639ba`) akzeptiert alle
Antworten (Vorprüfung, Einreichen, Schritt, Status, Abbruch). Neu in Termux: `ccc1-check [--full]`, `ccc1-prepare`,
`ccc1-once`, `ccc1-status`, `ccc1-review`. Für die **nächste** APK: Die App erkennt „Checkout zu alt“, „Builder fehlt“
und „Termux startet nicht“ selbst und nennt zu jedem Vorprüfungscode den nächsten Schritt (`0a268a86`).

**Issue #76 (`FAILED_SAVE_CYCLE_IllegalStateException`):** Am Android-15-Framework nachgewiesen, dass `commit()` bei
gesetztem Interrupt-Flag `false` meldet, auch wenn geschrieben wurde. Abgefangen und mit festen Ursachencodes
versehen (`7c6291f9`). Ob genau das die Gerätemeldungen auslöste, ist ohne Gerätelog offen.

**GitHub Actions:** Jeder Lauf endet nach ~2 s ohne Schritte und ohne Logs (HTTP 404) – keine Codeursache;
Konto-/Runner-Einstellungen (Actions-Minuten, Ausgabenlimit) prüfen.

## A. CCC1-Fortschritt (die Kette)

```text
Besitzer (Activity, Bestätigungsdialog)
  -> ContinuumCcc1Bridge.command(...)            feste argv: Skript + Operation + geprüfte Werte, nie Shell-Text
  -> Termux RUN_COMMAND (bestehender Rückkanal, ONE_SHOT PendingIntent, Callback-URI bindet Operation+ID)
  -> scripts/continuum-mobile-builder.mjs ccc1-* zweite Prüfung derselben Grammatik, State außerhalb des Checkouts
  -> .github/continuum/ccc1-cli.mjs              compile | submit | run-once | status | abort (bestehender CLI-Vertrag)
       submit: CCC1-Vorprüfung (isolierter Branch, sauberer Baum, State außerhalb, Node >= 20), idempotent je Auftrags-ID
       run-once: Journal (HMAC-Kette) -> Gate (node-repair:<kette>) -> runMobileRepairJob
            -> bestehende Owner-Repair-Pipeline: isolierter Worktree, Baseline, deterministische Kandidaten,
               zweiter unabhängiger Worktree -> verifyRepairReceipt (3 echte Läufe, Hashes) -> Kritik CONFIRMED
  -> continuum-ccc1-bridge JSON (exit 0 / 2)
  -> ContinuumMobileBuildResultReceiver -> ContinuumCcc1Bridge.evaluate (streng) -> nur geprüfte Zusammenfassung
  -> Entwicklungszentrum zeigt: Patch-Hash, Dateien, Strategie, „NICHT übernommen“; Patch-Text bleibt in Termux
```

**Echter Lauf** (`docs/evidence/ccc1-e2e-20261023/`): eingeschleuste, nur lokale Regression in
`console-policy.mjs` (`startsWith` statt Gleichheit: ein Konto `Ullmann27-similar` hätte Besitzerrechte).
Baseline **792 ausgeführt / 1 rot** → Kandidat **792 / 0** → unabhängiger Arbeitsstand **792 / 0** →
`REPAIR_PATCH_VERIFIED_PENDING_REVIEW`, Patch `d4caa68d…` stellt die exakte Prüfung wieder her. Eigene Gegenprobe:
Patch auf frischem Klon, volle `.github/continuum`-Suite 880 Tests / 0 rot. Checkout unverändert, Worktrees entfernt.
Die Android-Auswertung (`ContinuumCcc1Bridge.evaluate`) ordnet alle echten Antworten richtig ein: der Lauf allein
zählt nicht, erst der Status mit unversehrtem, zur Bestätigung passendem Patch.

**Neu in diesem Branch** (Commits seit `81180313`):

| Commit | Inhalt |
|---|---|
| `89b353d5` | Aufträge an das echte Repository (`origin`) gebunden statt Platzhalter |
| `05a0896c`, `8e091b48` | Rekursiver Suite-Lauf (Fork-Kaskade) gestoppt; Executor- und Ledger-Schutz portiert |
| `eb32f297` | CCC1-Protokoll v2: Reparaturauftrag (`node-repair[:kette]`) über die bestehende Pipeline + `verifyRepairReceipt` |
| `66fd9117` | Brücken-Operationen `ccc1-*`, Besitzer-Abbruch, idempotentes Einreichen, prüfbarer Patch im Status |
| `e6824816` | Android: `ContinuumCcc1Bridge.kt`, Empfänger-Zweig, eigener Abbruch-Slot, Activity-Bereich, JVM-Tests, Vertragsdateien |
| `f6a23243` | Abbruch stoppt die Reparatur vor dem unabhängigen Lauf (`TASK_ABORTED_BY_OWNER`) |
| `0f451fef` | Belege des echten Laufs |
| `687b3a56` → `2ee743a9` | veralteter Fold7-Reflow-Test (Fassung aus PR #98 wörtlich übernommen) |
| `122f5c67` | Sandbox-Fix: unvollständiges awk-Programm in `continuum-isolated-node.sh` |
| `e077b933` | `scripts/android-kotlin-unit-check.sh`: alle Kotlin-Unit-Tests ohne SDK/Gradle |
| `32cc82e1` | Verifier-Test: zeitüberschrittene Läufe bestätigen nie |
| `29fe255e`, `e7136b5b` | Forschung D: CDX1 als Patch-Anomalie-Sensor (vorregistriert, ein Lauf) |

## Pflichtprüfungen (Auftrag §3)

| Fall | Ergebnis | Nachweis |
|---|---|---|
| normaler Erfolg | bestanden (Fixture + echtes Repo) | `ccc1-bridge.test.mjs`, `ccc1-repair.test.mjs`, Beleg `proof/` |
| fehlschlagender Unit-Test | bestanden: echter roter Test ist der Auslöser | Beleg `proof/coding-job-receipt.json` (Baseline 1 rot) |
| nicht verifizierbarer Patch | bestanden: INCONCLUSIVE/REFUTED, nie CONFIRMED | `ccc1-repair.test.mjs` (Verifier-Manipulationen), Kotlin `nothingUnverifiedOrForeignIsAccepted` |
| unbekanntes Worker-Ergebnis | bestanden: INCONCLUSIVE; Kotlin INVALID_RESULT | `ccc1-repair.test.mjs`, `ContinuumCcc1BridgeTest` |
| ungültiger Git-Commit / veraltete Basis | bestanden: `STALE_BASE`, `BLOCKED_STALE_BASE` | `ccc1.test.mjs`, `ccc1-repair.test.mjs` |
| unsauberer Arbeitsbaum | bestanden | Bridge-Test, Beleg `refusals/02-submit-dirty.json` |
| Symlink-Manipulation | bestanden: State per Link in den Checkout abgelehnt, nichts geschrieben | `ccc1-bridge.test.mjs`, `ccc1-path-policy.test.mjs` |
| verbotener State-Pfad | bestanden | Bridge-Test, Beleg `refusals/03-state-inside-checkout.json` |
| main/Produktion | bestanden: `CCC1_BRANCH_NOT_ISOLATED` | Bridge-Test, Beleg `refusals/01-submit-on-main.json` |
| doppelter Auftrag | bestanden: gleiche Auftrags-ID → gleicher Auftrag; offener gleicher Auftrag wird wiederverwendet | Bridge-Test, Beleg `proof/03-…`, `07-…` |
| beschädigtes Journal | bestanden: `CORRUPT`, Zyklus `ERROR`, nichts ausgeführt | Bridge-Test, `ccc1.test.mjs` |
| Worker-Absturz | bestanden: SIGKILL vor Ausführung → Auftrag bleibt offen, kein Ergebnis | Bridge-Test (`CCC1_CRASH_AT`) |
| Zeitüberschreitung | bestanden: abgelaufene Frist → Gate verweigert, Auftrag endet `DEADLINE`; ein abgebrochener oder zeitüberschrittener Lauf ist nie grün | `ccc1.test.mjs`, `ccc1-repair.test.mjs` (Verifier: `timedOut`), `m4-local-gate.test.mjs` |
| Prozess-Neustart | bestanden: Recovery, Replay ohne Neuausführung, unterbrochener Lauf nur mit Besitzerfreigabe | `ccc1.test.mjs`, `ccc1-repair.test.mjs` |
| Benutzerabbruch | bestanden, auch **während** eines echten Laufs: `WRITER_ACTIVE`, Job `CANCELLED`, Auftrag `ABSTAINED` | Beleg `cancel/`, `backlog-repair-worker.test.mjs` |
| unterbrochener Build/Lauf | bestanden: `INTERRUPTED` → `APPROVAL_REQUEST`, nie stiller Erfolg | `ccc1-repair.test.mjs` |
| verlorener Android-Rückkanal | bestanden auf Node-/Kotlin-Ebene: gleiche Auftrags-ID wiederholbar; abgeschnittene/fehlende Antwort = INVALID_RESULT; nach 1 h „ungeklärt“, nie Erfolg. **Nicht ausgeführt:** auf dem Gerät | Bridge-Test, `ContinuumCcc1BridgeTest`, Activity |
| abgeschlossenen Auftrag erneut ausführen | bestanden: `duplicate:true`, `IDLE`, Abbruch `ALREADY_ENDED` | Beleg `proof/07–09` |

## B./D./E. Repariert, aufgeräumt, geprüft

- **Fold7-Reflow-Test** war gegen den neuen Composer-Fix veraltet → strengere Fassung aus PR #98 übernommen.
- **OS-Isolation:** `ldd`-Auswertung in `continuum-isolated-node.sh` war syntaktisch kaputt; die Sandbox lief nie
  (fail-closed). Jetzt laufen alle Isolationstests, inkl. echter Namespace-Sperre für Dateien, Umgebung, Schreiben, Netz.
- **Abbruch** stoppt die Reparatur jetzt vor dem unabhängigen Lauf.

**Testübersicht am Branch-HEAD** (Linux-Container, Node 22, OpenJDK 21, kotlinc 2.x):

| Prüfung | Ergebnis |
|---|---|
| Node `.github/continuum/*.test.mjs` + `scripts/*.test.mjs` | **985 Tests: 979 bestanden, 0 fehlgeschlagen, 6 übersprungen** (brauchen `kotlinc`) |
| dieselben 7 Kotlin-abhängigen Node-Tests mit `kotlinc` im PATH | **7/7 bestanden** |
| Backend `backend/test/*.test.js` | **60/60 bestanden** mit `npm ci` aus dem Lockfile; ohne Abhängigkeiten 7 rot (Umgebung, nicht Code) |
| Kotlin-Unit-Tests (`scripts/android-kotlin-unit-check.sh`) | **822/822 bestanden** (166 Klassen) + 4 Prüfprogramme PASS |
| ausgelieferte APK-Quelle `285639ba` (Node) | **992 Tests: 986 bestanden, 0 fehlgeschlagen**, 6 übersprungen |
| Android-Hauptquellen kompilieren | bestanden (LiteRT-LM nur als Kompilier-Platzhalter, sonst Werkzeug nicht verfügbar) |
| Gradle/AGP, Android-Lint, Robolectric-UI (`src/uiTest`), APK, Fold7 | **nicht ausgeführt** (Werkzeug nicht verfügbar: kein SDK, Google Maven gesperrt) |
| GitHub Actions | nicht ausführbar: jeder Lauf endet nach ~2 s ohne Schritte/Logs (Konto-/Runner-Ebene) |

## C. Weg zur Besitzer-APK (nicht in diesem Branch)

1. PR #98 und Astras zuletzt **gepushten** UI-Stand in den gemeinsamen Integrationsbranch übernehmen
   (`docs/CONTINUUM-PREMIUM-UI-INTEGRATION-GATE-20261023.md`); PR #98 mergt konfliktfrei in diesen Branch.
2. Auf dem eingefrorenen Integrations-SHA: Node-Suite, `scripts/android-kotlin-unit-check.sh`, danach Gradle
   `:app:testDebugUnitTest :app:lintDebug` und Robolectric-UI mit echtem SDK.
3. `app/build.gradle.kts`: versionCode 18 / 0.3.17, Paket `at.heinz.deviceai.mentorpaired`, Zertifikat
   `2b05fa9d…a9cc` – durch Integration/Astra, mit Besitzerschlüssel außerhalb des Repositorys.
4. v0.3.16 als Rollback archivieren und prüfen; In-place-Update auf dem Fold7; dort den CCC1-Ablauf aus der App
   (Vorprüfung → Einreichen → Schritt → Status → Abbruch) und den Rückkanal prüfen.

## F. Offene Punkte, Risiken, nächster Schritt

- **Gerät:** CCC1 aus der App ist auf dem Fold7 noch nicht gelaufen; zuerst den Termux-Checkout aktualisieren
  (`docs/CONTINUUM-CCC1-AUF-DEM-GERAET.md`) und das Ergebnis von `ccc1-check --full` festhalten.
- **Rückkanalgröße:** Die App nimmt höchstens 32 768 Zeichen; der Status ist dafür auf die 10 neuesten Aufträge ohne
  Patch-Text begrenzt. Ob Termux `stdout_original_length` immer mitsendet (sonst gilt die Antwort als abgeschnitten),
  ist auf dem Gerät zu bestätigen – das galt schon für die bestehenden Coding-Aufträge.
- **Abbruch** wirkt seit `c397ff1b` an der nächsten Schrittgrenze (vor jedem Prüfprofil, jedem Kandidaten und dem
  unabhängigen Lauf); gemessen 38,7 s statt 70,6 s bis zum Ende des Schritts. Der gerade laufende Prüfschritt läuft
  zu Ende (bei der Android-Kette ein Gradle-Schritt, also Minuten). Ergebnis zählt nie.
- **Reparaturbreite:** Bewiesen ist der deterministische Weg (hier `REVERT_FILE_TO_PARENT`). Fehler, die keine
  vorhandene Strategie abdeckt, enden ehrlich als `FAILED_REPAIR`/`ABSTAINED`.
- **Forschung (Argon A–E):** **D ausgeführt** (vorregistriert, ein Lauf, `docs/research/CDX1-PATCH-ANOMALY-RESULT-20261023.md`):
  CDX1 erkennt nur ungewöhnliche Bytefolgen (99 %), keine Syntax-, Token-, Abschneide-, Hunk- oder semantischen Fehler;
  ein Byte-4-Gramm leistet dasselbe 6× schneller → kein CDX1-Signal in CCC1. Vorschlag: deterministische Prüfung auf
  Steuer-/unsichtbare Zeichen in Patches. **B ausgeführt** (vorregistriert, TEST einmal,
  `docs/research/LCT1-REASONING-RESULT-20261023.md`): typisierter, quellgebundener Graph-Schlussfolgerer mit
  unabhängigem Prüfer – 0 falsche Antworten in 600 Fällen aus 24 Varianten (Abdeckung 1,0; Prüfer lehnt 1 175/1 175
  Mutationen ab); der Algorithmus der bisherigen `EvidenceBoundedThoughtSearch` gibt 150 falsche Antworten; latente
  Fast-Weights bringen keinen Vorteil (übersehen Widersprüche bzw. nehmen falsche Umwege). **C ausgeführt**
  (`docs/research/WSC1-CONSOLIDATION-RESULT-20261023.md`): nur von V bestätigte Ableitungen werden über ein Tor zu
  Gewohnheiten mit Abhängigkeiten, Gültigkeitsfenster und Quittung – nach Wissensänderungen 0 falsche Antworten in
  1 200 Fragen, zuvor gelöste Fragen rund 13× schneller, Trefferquote 30 % gegen 0,3 % beim gewöhnlichen Cache; ohne
  Abhängigkeiten 129 falsche Antworten. Der Gewinn ist deterministisch, kein Gewicht verändert. Register: `EXP-13`.
  **A ausgeführt** (`docs/research/TTT1-WORKING-MEMORY-RESULT-20261023.md`): Entitätsspeicher 680/680 ohne falschen
  Rückbezug, Fast-Weights 73 falsch → nicht übernommen. **E (GGUF/Spine): Werkzeug nicht verfügbar** (kein Modell,
  keine Laufzeit hier). PR #72 (I1/I2) selektiv übernommen. **Fortsetzung: `docs/CONTINUUM-OPUS-TO-GPT6-HANDOFF-20261023.md`.**
- **Nächster Schritt:** Besitzer: Termux-Checkout aktualisieren und CCC1 aus der App probieren. Integration: die
  Commits ab `c72bd98f` (Termux-Befehle, App-Diagnose, Fix #76) in den nächsten APK-Build übernehmen.

**Rollback:** Branch-Commits sind klein und einzeln revertierbar; vor `89b353d5` liegt unverändert `81180313`
(PR-#97-Basis). Für die App: Activity/Empfänger-Änderungen stecken nur in `e6824816` (eine Revert-Operation).
