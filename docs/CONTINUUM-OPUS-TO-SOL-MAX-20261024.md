# CONTINUUM – Übergabe Opus 5.5 → Sol 6.1 Max (24.10.2026)

Verbindliche Übergabe der Express-Mission. Alles hier Genannte liegt auf GitHub; es gibt keine lokale
Sitzungsdatei, keinen privaten Patch und keinen versteckten Schlüssel, die Sol fehlen würden.
Nichts wurde nach `main` geschrieben, nichts signiert, keine Besitzerfreigabe und kein Fingerabdruck verändert.

## 1. Zweige und HEADs (per `git ls-remote origin` am 24.10.2026 geprüft)

| Zweig | HEAD | Rolle |
|---|---|---|
| **`opus/continuum-sol-handoff-20261024`** | **dieser Dokument-Commit** (Eltern: `4f6cc98f`) | **Hier weiterarbeiten.** = PR #111 + 4 Opus-Commits |
| `chatgpt/continuum-master-integration-v19-20261024` (PR #111) | `ec4fa6db4e57d40d25a6e8ed1c794b4d96c046c2` | Ausgangsbasis, unverändert |
| PR #110 / #109 (M5) / #108 (M4) | `f382aeda` / `144ab574` / `e4fd67c3` | vollständig in HEAD enthalten (Vorfahren) |
| `astra/render-mentor-apk-builder-20261019` | `285639ba` | Signierzweig, in HEAD enthalten |
| `opus/continuum-next-20261023` | `ef3832ec` | in HEAD bis auf `ef3832ec`; dessen Inhalt ist als `4f6cc98f` portiert |
| `opus/continuum-0.3.18-candidate-20261023` | `421a96b7` | Inhalt von `663412b8`, `be469760`, `21fc3f7d` blobgleich in HEAD; zwei Wiring-Tests aus `18b6a0d6` liegen in HEAD in neuerer Fassung; nur das alte Kandidaten-Dokument fehlt (durch dieses Dokument ersetzt) |
| `main` | `190f5eab` | unberührt |

Maßgeblich ist immer der echte Remote-Stand: `git ls-remote origin refs/heads/opus/continuum-sol-handoff-20261024`.

## 2. Umgesetzte Änderungen (je ein Commit, je gepusht)

1. **`01cf6f80` fix(m4/m5)** – `ContinuumMobileBuilderActivity.kt`: Die M5-Übergabe (`m5_project` + `m5_plan_sha`)
   wurde bei jeder Neuerzeugung der Activity (Fold, Drehung, Prozess-Neustart mit SavedState) erneut angewandt und
   setzte eine spätere manuelle Projektwahl des Besitzers still auf das alte M5-Projekt zurück. Jetzt einmalig nur bei
   `savedInstanceState == null`, gespeichert wie eine manuelle Wahl (`selected_m4_project_v1`).
   Zusätzlich repariert: `.github/continuum/android-integration-wiring.test.mjs` war seit `5a2df39f` ein
   SyntaxError (kaputte IIFE) und lief gar nicht; veraltete `openM5ProjectPlan`-Erwartung an den echten Code angepasst;
   neuer Wiring-Test für die Einmal-Übergabe. Neuer Robolectric-Test
   `ContinuumOwnedBuilderUiTest.anM5HandoffSelectsItsProjectOnceAndARecreationKeepsTheOwnersLaterSwitch` (nicht
   ausgeführt: SDK_UNAVAILABLE).
2. **`0e51aff0` feat(release)** – `scripts/continuum-v19-pin-audit.mjs` liefert jetzt einen vollständigen
   maschinenlesbaren Freigabebericht: Rolle je Abweichung (`TEST_ONLY` / `WIRING_OR_UI` / `SECURITY_REVIEW`), alle
   Commits seit dem Pin, attestierte Basis, **nicht attestierte APK-Quellen** außerhalb der 22 Pins und die
   Release-Gate-Befehle mit `SDK_AVAILABLE|SDK_UNAVAILABLE`. Exitcodes unverändert, Pins unverändert. Tests 7/7.
   Bericht: `docs/evidence/v19-release-audit-20261024/report-at-4f6cc98f.json` (und `report-at-01cf6f80.json`).
3. **`4f6cc98f` research(ctx2)** – `Ctx1DialogueContext.kt` (nur Test-Quellset, nicht in der APK): Ein Zug, dessen
   einzige Erwähnungen verneint sind („Nicht Max.“), warf `NoSuchElementException` (`wanted.first()`), in beiden
   Varianten. Guard portiert, Sols Negationsfix unverändert. Regressionstest
   `aTurnWhoseOnlyMentionsAreNegatedNoLongerCrashesEitherVariant` schlägt auf `ec4fa6db` mit genau dieser Exception
   fehl und besteht mit dem Fix. CTX3-TEST3 hatte CRASH 0, seine Ergebnisse bleiben gültig.
4. Dieses Dokument + Bericht am aktuellen Stand.

## 3. Geprüfte Kette (P1): Chat → M5 → Plan → Freigabe → M4 → Termux → CCC1 → APK-Ergebnis

`AgentActivity.openM5ProjectPlan(initialDraft)` (Chat-Route „Projektplan“, Entwurf begrenzt auf 1..400 Zeichen ohne
Steuerzeichen) → `ContinuumM5LabActivity` + `ContinuumCodingConversation` (Anforderungsdialog) →
`ContinuumOwnedProjectPlan` / `assets/continuum-owned-project-plans-v1.json` (kanonischer Plan, `planSha256`) →
Besitzerfreigabe (einmalig) → `ContinuumMobileBuilderActivity` (Extras `m5_project`, `m5_plan_sha`) → bestehender
M4-Builder `scripts/continuum-owned-app.mjs` über Termux → CCC1-Prüfung → `ContinuumMobileBuildResultReceiver` /
`ContinuumMobileResultPolicy`.

- Unabhängig geprüft: Sols Fix `41b6296f` (bestätigter M5-Auftrag darf nach Activity-Neustart nicht erneut freigegeben
  werden) ist korrekt: `acceptedTurns.clear()` vor dem Builder-Start; Wiederherstellung spielt Züge in ein frisches
  Gespräch ein und übernimmt keine Freigabe.
- Gefunden und behoben: die stale Builder-Übergabe (Commit 1). Kein zweiter Builder, keine zweite Reparaturpipeline.
- **Nicht** geprüft: dieselbe Kette auf dem Fold7 mit Termux und echtem APK-Ergebnis an diesem HEAD (kein Gerät, kein SDK).

## 4. Ausgeführte Tests (echte Ergebnisse, nichts geschönt)

| Befehl (Release-Gate-Nummer laut Bericht) | Stand | Ergebnis |
|---|---|---|
| 1 `node --test --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs` | `0e51aff0` | **1097/1097 pass**, 0 fail, 0 skipped |
| 2 `node --test --test-concurrency=2 backend/test/*.test.js` (nach `npm ci --ignore-scripts` in `backend/`) | `4f6cc98f` | **60/60 pass** |
| 3 Mentor-/Groq-Tests (4 Dateien) | `4f6cc98f` | **26/26 pass** |
| 4 Workshop-/Academy-/Atlas-/Hyper-Lab-Tests (6 Dateien) | `4f6cc98f` | **34/34 pass** |
| 5 `node --test` (Standard-Discovery) | `4f6cc98f` | **250/250 pass** |
| `scripts/android-kotlin-unit-check.sh` (kotlinc + JUnit außerhalb Gradle) | `0e51aff0` | **183 Klassen, 4 Check-Programme, JUnit OK (907 Tests)** |
| Ctx1/Ctx2/Ctx3-Testklassen mit Fix, Harness-Klassenpfad | `4f6cc98f` | **28/28 pass** |
| Wiederholung Gate 1 + Kotlin-Harness am exakten Stand `4f6cc98f` | `4f6cc98f` | siehe Abschnitt 4a |
| 6 `gradle --no-daemon --max-workers=2` (Unit, Robolectric `uiTest`, lint, assembleDebug) | – | **SDK_UNAVAILABLE** (dl.google.com gesperrt) |

Alle Node-Läufe mit `env -u NODE_TEST_CONTEXT -u CONTINUUM_REPAIR_DEPTH`, ohne Signierumgebung.
Bestandene Tests sind **keine** bestätigte Android-Gerätefunktion.

### 4a. Wiederholung am exakten Stand `4f6cc98f`

Wird nach Abschluss des Laufs hier eingetragen (eigener Commit). Fehlt der Eintrag, gilt der Lauf als **nicht geprüft**.

## 5. Release-Blocker: 11 von 22 v19-Fingerabdrücken weichen ab + 10 nicht attestierte APK-Quellen

Quelle: `report-at-4f6cc98f.json`, Status `OWNER_REVIEW_REQUIRED`, attestierte Basis `2ef71365`. Diff je Datei:
`git diff <pin-blob> <aktueller-blob>`.

| Rolle | Datei | Pin → jetzt | Commits seit Pin | Bewertung |
|---|---|---|---|---|
| TEST_ONLY | `app/src/test/.../ContinuumOwnedAppContractTest.kt` | `2736d947` → `a4f1b385` | c787c098 | harmlos (Test) |
| TEST_ONLY | `app/src/test/.../ContinuumWorkerChatRouterTest.kt` | `697e678a` → `9a6f7a4c` | 16a055b4, 239c9cdd, b09a8148 | harmlos (Test) |
| TEST_ONLY | `scripts/continuum-owned-app.test.mjs` | `e287909e` → `65cec140` | c787c098, 6ff6d8f0 | harmlos (Test) |
| WIRING_OR_UI | `AgentActivity.kt` (Astra) | `c151ade5` → `5634095f` | d2fd0f7c, d3f5f3ee, 6138cbe6, 63ea9189 | notwendig (M5-Chat-Route) |
| WIRING_OR_UI | `ContinuumMobileBuilderActivity.kt` | `acfebe1f` → `ea350fb2` | 01cf6f80, f8b85774, c787c098 | notwendig (M5→M4, Opus-Fix) |
| SECURITY_REVIEW | `ContinuumMobileBuildPlan.kt` | `3cb8e5d5` → `6ebec7ba` | c787c098 | Builder-Operationen |
| SECURITY_REVIEW | `ContinuumMobileResultPolicy.kt` | `e8c153af` → `79f36503` | c787c098 | Ergebnisannahme |
| SECURITY_REVIEW | `ContinuumMobileBuildResultReceiver.kt` | `384ab70a` → `402fc6f7` | c787c098 | Rückkanal (Broadcast) |
| SECURITY_REVIEW | `ContinuumWorkerChatRouter.kt` | `479849bd` → `a900cdc7` | 5e81011b, 7b17bdd8, 1a890eb6 | Steuerzeichen-/Befehlsgrenze |
| SECURITY_REVIEW | `scripts/continuum-owned-app.mjs` | `83692c7a` → `c7356064` | c787c098, 6ff6d8f0, 3f9ce263, ba6aa26b | Termux-Builder |
| SECURITY_REVIEW | `BootstrapBrain.kt` | `5ddd43cb` → `e0981a9a` | c787c098 | **Sicherheitsfix** („installier“ wird nie Gerätewerkzeug), blobgleich mit Kandidat `be469760` |

Nicht im alten Vertrag, aber in der APK geändert (`unattestedApkSourceChanges`):
`M AndroidManifest.xml` (Astra; neue Activity – Export-Status prüfen), `A assets/continuum-owned-project-plans-v1.json`,
`A ContinuumCodingConversation.kt`, `A ContinuumM5LabActivity.kt`, `A ContinuumOwnedProjectPlan.kt`,
`M learning/ContinuumCognitiveRuntime.kt`, `M learning/ContinuumLearningCore.kt`,
`M learning/ContinuumPublicStudyCycleResult.kt` (#76-Fix), `A learning/context/Ctx3Context.kt`,
`A learning/context/Ctx3Shadow.kt`.

Regel: Keine Pin-Zeile wird ohne ausdrückliche Besitzerfreigabe auf neue Werte gesetzt. Die automatische Klassifizierung
hat Opus das Bearbeiten der Pin-Liste verweigert („Security Weaken“); das wurde nicht umgangen.

## 6. Signierstatus

Nicht signiert, keine APK gebaut. Paket `at.heinz.deviceai.mentorpaired`, Besitzerzertifikat SHA-256
`2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc`, Signierung ausschließlich über die bestehende
private Render-Infrastruktur (Zweig `astra/render-mentor-apk-builder-20261019` @ `285639ba`,
`CONTINUUM_ASTRA_UI_SHA=811803138c86a5c1a7a2a2b9cd196580a574a4a1`). Kein Schlüssel und kein Geheimnis im Repository.
Ziel 0.3.18 / versionCode 19 bleibt **blockiert**, bis (a) Heinz die 11 Abweichungen und die 10 neuen Quellen
freigibt, (b) Gate 1–6 inklusive Gradle/Robolectric/lint in Render grün sind.

## 7. Offene Fehler und Risiken (mit Reproduktion)

1. **CTX3 verwirft fast alle Erinnerungsfragen** (bleibt OFF/SHADOW; TEST3 = FAIL, CORRECT 13 statt 111).
   Diagnoselauf (nur lesend, nichts geändert) mit Fakten `Nuri ist eine Katze. / Nuri schläft im Korb. /
   Basko schläft im Flur. / Nuri hat ein blaues Halsband.`:
   Register = nur `[Nuri]`, Quellen von Nuri = `f1,f4` (der Schlafplatz-Fakt `f2` fehlt);
   „Wem gehört er?“, „Welche Farbe hat das Halsband?“, „An welchem Projekt arbeitet sie?“ → `Abstain(NO_CONFIRMED_ENTITY)`;
   „Wo schlafen Basko und Nuri?“ → `Clarify(MIXED_UNKNOWN_REFERENT)`. Ursachen in `Ctx3Context.kt`:
   (a) Zeile 51–54: jedes nicht abgedeckte großgeschriebene Wort (Satzanfang, deutsches Hauptwort) gilt als
   unbekannter Name, noch vor der Pronomenauflösung; (b) Zeile 95–97: Faktgrammatik kennt nur
   `ist|wohnt|lebt|hat|arbeitet|frisst|startet`; (c) eine Entität erhält nur die Fakten, die ein Muster lasen, ein
   eingegrenzter Query schließt also die antwortende Quelle aus. Welcher Anteil der 109 MISSED auf (a)/(b)/(c) fällt,
   ist **nicht** gemessen (der TEST3-Bericht speichert keine Gründe; „ABSTAIN“ dort heißt nur „nichts zitiert“).
   Jede Korrektur braucht eine neue Vorregistrierung mit frischem TEST; TEST3 ist verbraucht.
2. **CTX2-TEST2 nicht abgeschlossen:** Der einzige Lauf auf `opus/continuum-next-20261023` brach mit derselben
   `NoSuchElementException` ab (danach `ef3832ec`). Achtung: Das B2 in HEAD enthält Sols späteren Negationsfix und ist
   damit **nicht** das vorregistrierte B2. Entweder TEST2 einmalig auf `ef3832ec` (eingefrorenes B2 + Guard) als
   erklärte Protokollabweichung „Wiederholung nach Absturz“ laufen lassen oder TEST2 als verbraucht erklären.
   Kein Ergebnis darf als Erfolg gelten, bevor das dokumentiert ist.
3. Robolectric-Test aus Commit 1 und alle Gradle-Prüfungen: **SDK_UNAVAILABLE**, erst in Render ausführbar.
4. Issue #76 bleibt offen bis zur Gerätebestätigung.
5. `AgentActivity.kt`, `AndroidManifest.xml`, `ui/**`, `app/build.gradle.kts` gehören Astra: Änderungen nur abgestimmt.

## 8. Nächste konkrete Schritte

1. HEAD prüfen, Abschnitt 4a lesen; falls leer: Gate 1 und Kotlin-Harness selbst laufen lassen.
2. Zwei unabhängige Kontrolldurchgänge über die vier Opus-Commits (Code + Tests + Mutation der Fix-Zeilen).
3. Besitzer-Prüfpaket aus `report-at-<HEAD>.json` erstellen (Diff je SECURITY_REVIEW-Datei, Manifest-Exportstatus),
   Heinz entscheiden lassen; erst danach Pins per freigegebenem Commit.
4. Render-Gate 1–6 inkl. Gradle/Robolectric/lint/assembleDebug; bei Grün und Freigabe signieren, APK-Hash und
   Zertifikat unabhängig prüfen, HTTPS-Downloadlink.
5. CTX2-TEST2 nach Abschnitt 7.2 regeln; CTX3 nur mit neuer Vorregistrierung weiterentwickeln.

## 9. Fortsetzungs-Masterprompt für Sol 6.1 Max (vollständig kopierbar)

```text
CONTINUUM – SOL 6.1 MAX: FORTSETZUNG NACH OPUS 5.5 (Übergabe vom 24.10.2026)

Arbeite ausschließlich im Repository https://github.com/Ullmann27/device-ai. Stelle keine Rückfragen und plane nicht
neu: Der Auftrag ist vollständig. Prüfe vor jeder Änderung den echten Remote-HEAD (git ls-remote).

START: Zweig opus/continuum-sol-handoff-20261024 (enthält PR #111 @ ec4fa6db, PR #108/#109/#110, den Signierzweig
285639ba und vier Opus-Commits 01cf6f80, 0e51aff0, 4f6cc98f sowie die Übergabe). Lies zuerst AGENTS.md,
docs/CONTINUUM-EXPERIMENTVERTRAG.md, docs/CONTINUUM-EXPERIMENTE.json, docs/CONTINUUM-OPUS-TO-SOL-MAX-20261024.md,
docs/CONTINUUM-SOL-M5-TO-GPT6-HANDOFF-20261024.md und docs/research/CTX3-INDEPENDENT-RESULT-20261024.md.
Arbeite auf einem neuen, klar benannten Fortsetzungszweig von diesem HEAD (z. B. sol/continuum-max-20261024).

ABSOLUTES GESETZ – CODE NIEMALS VERSPERREN: Nach jedem abgeschlossenen Arbeitsschritt ist der gesamte Code auf GitHub
gepusht. Keine Force-Pushes, keine Rebases geteilter Zweige, keine destruktiven Resets, keine Änderungen an main oder
Production, kein automatischer Main-Merge, keine verschlossenen Worktrees, keine privaten Patches, keine liegen
gelassenen Sperrdateien, keine Geheimnisse oder Signierschlüssel im Repository, keine Sitzungsdatei, ohne die ein
Nachfolger nicht weiterarbeiten kann. Andere Entwickler nie aussperren, ihre laufenden Änderungen nie zerstören, alte
Zweige nicht löschen. Keine kostenpflichtigen externen KI-APIs als Betriebsabhängigkeit.

1. KONTROLLE: Prüfe den gesamten Code dieses HEADs. Validiere jede Opus-Reparatur unabhängig in mindestens zwei
   getrennten Kontrolldurchgängen (Code lesen, Test gegen den alten Stand reproduzieren, Fix-Zeile mutieren und
   prüfen, dass der Test sie erkennt):
   - 01cf6f80: M5-Übergabe im Builder nur bei savedInstanceState == null, Wiring-Test-Reparatur;
   - 0e51aff0: maschinenlesbarer v19-Freigabebericht (scripts/continuum-v19-pin-audit.mjs);
   - 4f6cc98f: Guard gegen NoSuchElementException in Ctx1DialogueContext (nur Test-Quellset).
2. KETTE: Hauptchat → M5-Anforderungsdialog → kanonischer Projektplan → einmalige Besitzerfreigabe → bestehender
   M4-Builder → Termux → CCC1-Prüfung → APK-Ergebnis. Prüfe Zustandsübergänge, Datenübergabe, Prozessabbrüche,
   Activity-Neuerzeugung, alte Dialoge, wiederholte Intents und Einmaligkeit. Jeden nachweisbaren Fehler mit
   gezieltem Regressionstest beheben. Keine Funktion entfernen, kein zweiter Builder, keine zweite Reparaturpipeline.
3. ZWEIGE VERBINDEN: Führe alle brauchbaren Zweige konfliktbewusst per Merge-Commit zusammen (Inhalt vor SHA prüfen:
   git cherry, Blob-Vergleich). Astra besitzt AgentActivity.kt, ContinuumHologramView.kt, app/src/main/java/at/heinz/
   deviceai/ui/**, AndroidManifest.xml, app/build.gradle.kts und die Android-Release-/Render-Skripte: dort nur
   abgestimmte, minimale Änderungen.
4. INTELLIGENZ: SWB1, CDX1, CRE1, I1/I2 und CTX3 gehen nicht ungeprüft in den produktiven Antwortkern. CTX3 bleibt
   OFF bzw. SHADOW (TEST3 = FAIL). Produktive Gewichte von SWB1/CDX1 und Besitzergewichte nie verändern. Neue
   Forschung nur vorregistriert mit TRAIN/DEV/TEST, versiegeltem SHA-256 und genau einem TEST-Lauf; ältere Ergebnisse
   nie überschreiben; nie einen zweiten NFC2-Finaltest. CTX2-TEST2: entweder einmalig auf ef3832ec als erklärte
   Protokollabweichung „Wiederholung nach Absturz“ oder als verbraucht erklären – nicht mit dem veränderten B2 aus HEAD.
5. TESTS: Führe alle Release-Gate-Befehle vollständig aus (Liste in
   docs/evidence/v19-release-audit-20261024/report-at-*.json, Feld releaseGate.commands): Node-Suiten, Backend
   (npm ci --ignore-scripts), Mentor-, Workshop-Tests, node --test, Kotlin-Harness und Gradle (Unit, Robolectric
   uiTest, lint, assembleDebug). Behebe jeden reproduzierten Fehler. GitHub Actions nur über vorhandene Logs
   untersuchen, keine wiederholten identischen Fehlstarts. Teststatus nie verfälschen; ohne echtes Android-SDK
   ausdrücklich „SDK_UNAVAILABLE“ protokollieren. Bestandene Tests nie mit Gerätefunktion verwechseln; ein simuliertes
   Receipt oder ein Mock-Erfolg genügt nicht. Melde nie „fertig“, solange ein relevanter Prozess läuft oder sein
   Ergebnis ungeprüft ist; eine Unterbrechung ist nie ein Erfolg.
6. FREIGABE: Die 11 abweichenden v19-Fingerabdrücke und die 10 nicht attestierten APK-Quellen als Prüfpaket für Heinz
   aufbereiten (Diff je Datei, Rolle, Grund, Manifest-Exportstatus). Ändere keine Besitzerfreigabe automatisch,
   ersetze keine Signierschlüssel und setze die alten Fingerabdrücke niemals blind auf neue Werte. Pins nur per
   Commit nach ausdrücklicher Freigabe durch Heinz.
7. APK: Nur wenn alle Release-Gates grün sind und alle Besitzerfreigaben vorliegen, die bestehende private
   Render-Signierinfrastruktur unverändert nutzen: Owner-APK 0.3.18 / versionCode 19, Paket
   at.heinz.deviceai.mentorpaired, bestehendes Besitzerzertifikat SHA-256
   2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc. Nie eine andere App-ID oder einen anderen
   Schlüssel verwenden. Privaten Schlüssel nie exportieren oder offenlegen. Keine Deinstallation, keine Überschreibung
   der privaten Daten (Update-Installation über die bestehende App). Nach dem Signieren APK-SHA-256 und
   Signaturzertifikat (apksigner verify --print-certs) unabhängig nachweisen und einen direkten HTTPS-Downloadlink
   angeben, der in Chrome funktioniert. Sonst bleibt die Freigabe blockiert und jedes Hindernis wird dokumentiert.
8. ZIEL: eine stabile App mit erhaltenen Daten, Premium-Hologramm-Design und wirklich verbundener KI, Gedächtnis,
   Entwicklung, Lernen und Reparatur. Issue #76 bleibt offen bis zur Gerätebestätigung.
9. ABSCHLUSS: Bevor das Budget knapp wird, neue Entwicklung stoppen, Git-Stand sichern und ein Übergabedokument
   docs/CONTINUUM-SOL-MAX-HANDOFF-<Datum>.md anlegen (Zweige/HEAD-SHAs, Änderungen, Testbefehle mit echten
   Ergebnissen, offene Fehler mit Reproduktion, Signierstatus, nächste Schritte, kopierbarer Fortsetzungsprompt).
   Schlussbericht auf Deutsch, nur Wesentliches: was programmiert wurde, welche Tests tatsächlich bestanden,
   was neu funktioniert, welcher Zweig/Commit gilt, Downloadlink nur mit Hash- und Zertifikatsnachweis.
```
