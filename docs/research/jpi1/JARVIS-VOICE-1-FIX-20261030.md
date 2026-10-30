# JARVIS VOICE-1 · erster Blocker reproduziert und behoben (30.10.2026)

**Basis (Integrationskandidat):** PR #251 `7a01138466e102bff46d95d262a9da5d23d851fd` → #250 `2e21bdaac8797a83e46e06d1b40244a616946fd6` → #247 `f3a5e3081428d223fc346174cd81493e8e03929a`.
**Zweig:** `opus/jarvis-voice1-fix-20261030`. Kein Merge, kein Force-Push, `main` unberührt.

## Live-Stand (geprüft, nicht abgeschrieben)

| PR | voller HEAD | Basiszweig | Merge-Base mit Basis | Befund |
|---|---|---|---|---|
| #251 | `7a01138466e102bff46d95d262a9da5d23d851fd` | #250 | = #250-HEAD | 7 Commits darauf, sauber gestapelt |
| #250 | `2e21bdaac8797a83e46e06d1b40244a616946fd6` | #247 | = #247-HEAD | VOICE-1 |
| #249 | `d6a666322db225586b03ddc51f5285c10f39dfe3` | #248 | = #248-HEAD `2e33dad9…` | 35 Commits, sauber gestapelt |
| #248 | `2e33dad91a9a393f00996084c5538955bf14ca92` | — | zweigt bei #245 `05e6aa9a` ab | |
| #247 | `f3a5e3081428d223fc346174cd81493e8e03929a` | — | zweigt bei #245 `0f6aa264` ab | |
| #245 | `29c1e1e1bef289cddf1e0c7854f2511923b93c0f` | #244 | | |

Die zwei Linien #247→#250→#251 und #248→#249 sind **parallel**. Sie wurden hier nicht zusammengeführt. Die Ergebnisse unten gelten nur für den jeweils genannten Stand.
Alle Pflichtdokumente sind vorhanden. Widersprüche:
- `JARVIS-APK-QA-ACCEPTANCE` nennt „keine Gradle-CLI“. In diesem Container gibt es `/opt/gradle` 8.14.3, aber kein Android-SDK und keinen Wrapper. Ohne Android Gradle Plugin bleibt ein Gradle-Build unmöglich.
- `JARVIS-VOICE-1-IMPLEMENTATION` beschreibt das automatische Senden als verdrahtet. Es löste aber nie aus (siehe Blocker 1).

## Aufrufpfad (gelesen)

| Glied | Status |
|---|---|
| Mikrofontipp → `startVoiceInput` → `RecognizerIntent` (`startActivityForResult`) | PRODUKTIV VERBUNDEN |
| Langdruck → Sitzungs-Opt-in `jarvisDialogueAutoSend` | PRODUKTIV VERBUNDEN |
| `onActivityResult` → `JarvisVoiceConversationPolicy.evaluate` | PRODUKTIV VERBUNDEN |
| → `runContinuumDialogue(…, allowLocalModel = false)` → `ContinuumCognitiveRuntime`/EWM2-Bridge | **vor dieser Korrektur nie erreicht**, jetzt verbunden |
| → `addAssistantMessage` → `speakBootstrap` (nur bei TON AN und im Vordergrund) | PRODUKTIV VERBUNDEN |
| Gerät: echte ASR, TTS, Fold7 | NICHT AUSGEFÜHRT |
| Ob der Android-Erkenner auf dem Fold7 offline arbeitet | UNBEKANNT |

`allowLocalModel = false` ist unverändert. Die Schutz- und Routingentscheidung wurde nicht angefasst.

## Blocker 1: Der Sprachdialog hat nie automatisch geantwortet

- **Ursache:** Die Bedingung für den automatischen Turn verlangte `foregroundLearningActive`. Dieses Flag wird in `onPause` auf `false` und in `onResume` auf `true` gesetzt.
- **Android-Vertrag** (AOSP `Activity.java`, Javadoc von `onActivityResult`): „An activity can never receive a result in the resumed state … it will be paused and the result will be delivered, followed by onResume.“
- **Folge:** Im Ergebnis-Handler war die Bedingung immer falsch. Jede zugelassene Frage endete als „Nur ein Entwurf“.
- **Reproduktion:** Die neue Wiring-Prüfung läuft gegen die unveränderten #251-Quellen. Ergebnis: Exit 1, 4 von 8 Tests rot, darunter der Lebenszyklus-Test mit `error: 'foregroundLearningActive'` ([Beleg](../../evidence/voice1-20261030/wiring-new-test-on-7a011384-RED.tap)). Der JUnit-Test `resultDeliveredWhilePausedStillStartsTheToolFreeTurn` bildet dieselbe Reihenfolge nach.
- **Änderung, minimal:**
  - `JarvisVoiceDispatchGate.decide` ist ein reines, getestetes Tor.
  - Ohne Vordergrund-Flag gelten: `!busy`, Executor läuft, Aktivität lebt (`!isFinishing && !isDestroyed`) und die Sitzungsbindung stimmt.
  - Die Sprachausgabe bleibt weiterhin an den Vordergrund gebunden.

## Weitere Härtung derselben Kette (je eine Zeile)

- **Sitzungs-/Auftragsbindung:** `voiceRequestSession` merkt sich beim Tipp die Sitzung, das Ergebnis nimmt sie und löscht sie. Ein Ergebnis für eine andere oder unbekannte Sitzung startet keinen Turn (nur Entwurf). Das betrifft zum Beispiel einen neuen Chat im Mehrfenstermodus oder eine neu erzeugte Aktivität.
- **Doppelter Mikrofonstart:** `startVoiceInput` kehrt zurück, solange `voiceListening` gilt.
- **TTS wird nicht zur Eingabe:**
  - Der Tipp stoppt TTS jetzt **immer**. Vorher nur bei `voiceSpeaking`, und dieses Flag wird erst in `onStart` gesetzt. Eine eingereihte, noch nicht gestartete Ausgabe lief also weiter.
  - `speakBootstrap` spricht nicht, solange der Erkenner zuhört. Im Mehrfenstermodus bleibt die Aktivität nämlich resumed.
- **Abbruch:** unverändert über `DeliberationCancellation` und STOP (`activeLocalRequest`). Veraltete Antworten verwirft der vorhandene Abgleich `activeChatCancellation !== cancellation`.
- **Grenze:** Der Dialogpfad hat **keinen eigenen Timeout**. Das ist so geblieben und nicht neu gebaut.

## Blocker 2: Wiring-Prüfung seit #250 rot

`grounded-recall-android-wiring.test.mjs` sucht den Anker `if(LocalFirstPolicy.afterMemory(`. Seit #250 steht dort `if(allowLocalModel && …`, der Ausschnitt wird leer.
Gegenprobe pro Stand: #245 grün, #247 grün, #250 rot, #251 rot. Korrigiert ist nur der Anker, alle inhaltlichen Assertions sind unverändert.

## Prüfungen

| Prüfung | Stand | Ergebnis | Beleg |
|---|---|---|---|
| neue Wiring-Prüfung auf unverändertem #251 | `7a011384` | **FAIL** (erwartet, Reproduktion), Exit 1, 4/8 | `wiring-new-test-on-7a011384-RED.tap` |
| `jarvis-voice-wiring` | Korrektur | PASS 8/8, Exit 0 | `wiring-on-fix.tap` |
| Sprach- und Android-Wiring (speech, android-integration, jarvis-voice) | Korrektur | PASS 33/33, Exit 0 | `voice-related-node-gates-on-fix.tap` |
| alle `.github/continuum/*-wiring` | `7a011384` | 120/121, Exit 1 (Blocker 2) | `all-wiring-on-7a011384.tap` |
| alle `.github/continuum/*-wiring` | Korrektur | **123/123**, Exit 0 | `all-wiring-on-fix.tap` |
| Kotlin-Hostprüfung (`android-kotlin-unit-check.sh`, Hauptquellen inkl. `AgentActivity` gegen `android-all-15`, alle JUnit) | `7a011384` | PASS **1313/1313**, Exit 0; Hauptquellen kompilieren (es gibt also keinen Compile-Blocker) | `kotlin-harness-base.txt` |
| Kotlin-Hostprüfung, dasselbe Skript | Korrektur | PASS **1316/1316** (+3 neue), Exit 0 | `kotlin-harness-fix.txt` |
| Kotlin-Hostprüfung, dasselbe Skript | Endstand `5a1002f8` (inkl. EXP-20-Labortests) | PASS **1319/1319**, Exit 0 | `kotlin-harness-final-5a1002f8.txt` |
| #249 Self-Play inkl. Thermal-Grenzwerttest | `d6a66632`, Node v22.22.0 | PASS 24/24 (20 Kern + 4 Adapter), Exit 0 | `thermal249-d6a66632-evolution-node22.tap` |

**Nicht gelaufen:**
- Gradle `testDebugUnitTest`, `lintDebug`, `assembleDebug`: Es gibt kein Android-SDK, `dl.google.com` ist gesperrt, und es gibt keinen Wrapper.
- APK, Signatur, Installation: kein APK-Hash und keine Signatur, weil nichts gebaut wurde.
- ASR und TTS auf einem Gerät, Fold7.
- `scripts/continuum-fusion-full-regression.sh`: braucht Gradle und SDK.

## Offline-Trennung (nichts davon auf dem Gerät gemessen)

- **ASR:** Android-`RecognizerIntent` mit `EXTRA_PREFER_OFFLINE`. Das ist eine Präferenz, keine Garantie, und der Anbieter ist UNBEKANNT.
- **Inferenz:** Der Sprachpfad läuft ausschließlich über `ContinuumCognitiveRuntime` (lokal, ohne Modell, `allowLocalModel = false`).
- **TTS:** Android-`TextToSpeech`, die Engine ist geräteabhängig.
- **Werkzeuge:** keine im Sprachpfad.

## Thermal-Korrektur #249 (geprüft, nicht geändert)

- **AOSP-Semantik** (`PowerManager.getThermalHeadroom`):
  - Der Wert gibt an, welcher Anteil der Thermalreserve „in use“ ist. 1,0 entspricht `THERMAL_STATUS_SEVERE`, Werte über 1,0 sind möglich.
  - `NaN` heißt „nicht unterstützt“ oder „deutlich öfter als einmal pro Sekunde abgefragt“.
  - `forecastSeconds` liegt zwischen 0 und 60.
  - 0,0 entspricht laut Javadoc „no particular thermal status or temperature“.
- **Die Korrektur stimmt:** Zugelassen wird nur [0; 0,5]. Höhere Werte, `NaN`, ±∞ und negative Werte blockieren.
- **Ungenauigkeit im #249-Dokument:** Dort steht „0 = kühl“. Das ist überzeichnet.
- **Keine Schwellenübertragung:** Der Kotlin-Produktionspfad (`ContinuumOfflineLearningBudget`) nutzt den getrennten Wert `currentThermalStatus` (Ganzzahl 0–6, erlaubt ≤ LIGHT). Die 0,5-Grenze ist eine eigene, nicht kalibrierte Forschungsschwelle.
- **Grenzen:**
  - `canStartShadowSelfPlay` hat keinen Zeitstempel im Messwert, veraltete Werte sind also nicht erkennbar.
  - Kein Android-Erzeuger liefert dorthin echte Werte. Der Status ist NUR TEST/LABOR.
  - Im Kotlin-Budget führt `thermalStatus == null` (API < 29) nicht zum Aufschub. Der Lauf bleibt aber auf 15 s begrenzt, das ist also keine uneingeschränkte Hintergrundarbeit.
- Ein bestandener Policytest ist **keine** Fold7-Thermalabnahme.

## Rückfall

`git revert` der Commits dieses Zweigs. Danach gilt wieder das Verhalten von #251: Der Dialog endet immer als Entwurf, und die Wiring-Prüfung ist rot.

## Genau ein nächster Schritt

Auf dem Fold7 per `scripts/continuum-mobile-builder.mjs` den `deviceAiPreview`-Kandidaten genau dieses Commits bauen. Dann 5 Sprachfragen mit TON AN prüfen:
- Antwort im Chat,
- Antwort gesprochen,
- kein Echo,
- STOP bricht ab.

Dazu den tatsächlichen ASR-Anbieter und den Offline-Modus festhalten.
