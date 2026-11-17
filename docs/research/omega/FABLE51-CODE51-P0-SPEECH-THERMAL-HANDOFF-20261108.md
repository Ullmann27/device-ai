# FABLE 5.1 · Code51 P0 Handoff — Sprache/Mikrofon‑Dauerabbruch + Fold7‑Wärme — 2026‑11‑08

Status‑Wörter nach `docs/CONTINUUM-EXPERIMENTVERTRAG.md`: `KOTLIN_UNIT_TESTS_CHECKED` (JVM, ohne Gradle/SDK),
**nicht** `APK_BUILT`, **nicht** `DEVICE_VERIFIED`. Die Fold7‑Abnahme bleibt beim Besitzer.

## Abstammung

- Basis: Code50‑Quellstand `473f79727e4b52509cc7d1af71e59b80ab9b6be5`
  (= Tag `continuum-local-code50-473f79727e4b52509cc7d1af71e59b80ab9b6be5`, Branch `chatgpt/code50-qa-staging-20261108`, PR #630).
- Arbeitsbranch: `fable/code51-p0-speech-recovery-20261108` (isoliert; kein `main`, kein Release, keine Signatur).
- Owner‑Kanon: #642; Befunde: #641 (Mikrofon belegt), #566 (TTS), #569 (Thermal‑Guard).
- Kein zweiter Eventbus, keine zweite Memory‑DB, keine zweite Netzpipeline: alle Änderungen hängen an den bestehenden
  Klassen `AndroidOnDeviceSpeechInput`, `LocalSpeechSession`, `ContinuousConversationMode`, `LiteRtLmLocalModelRuntime`,
  `LocalModelHost`, `JarvisRenderQuality`, `BoundedAgent`, `ContinuumUiCopy`.

## Befund 1 · „Gesprächsmodus pausiert: Der lokale Spracherkenner ist gerade belegt“ (Dauerschleife)

**Aufrufpfad (ist):** TTS endet → `onConversationSpeechFinished` → 450 ms → `startVoiceInput` →
`AndroidOnDeviceSpeechInput.create` nutzt den **warmen** `SpeechRecognizer` → Android liefert `ERROR_CLIENT`/`ERROR_RECOGNIZER_BUSY`
→ genau **ein** Warm‑Retry nach 250 ms → zweiter Fehler → `destroyWarmRecognizer()` + `Failure.BUSY` →
`LocalSpeechSession.fail` → `onError(String)` → `AgentActivity.onConversationListenFailed` vergleicht **Nachrichtentexte** auf
Stille, alles andere → `ContinuousConversationMode.listenFailed(silence=false)` → **sofort PAUSE**. Der Tipp des Besitzers →
`ownerTap` → `startVoiceInput` → neuer Recognizer, derselbe Dienst noch belegt → wieder BUSY → wieder PAUSE. Kein Versuch
dazwischen wartet länger als 250 ms; der Dienst bekommt nie Zeit, die alte Sitzung freizugeben.

**Fix (soll):**

| Schicht | Datei | Änderung |
|---|---|---|
| Reine Policy | `cognitive/SpeechRecoveryPolicy.kt` (neu) | Eskalation warm 250 ms → warm 700 ms → **kalt** (Recognizer zerstören + neu erzeugen) 1 200 ms → Aufgeben. Harte Grenzen: 3 Versuche, ≤ 4 s Wartezeit je Turn. |
| Reiner Turn‑Treiber | `cognitive/SpeechTurnRecovery.kt` (neu) | Plant die Wiederholung, schluckt doppelte Fehler‑Callbacks, meldet nach erschöpftem Budget genau einmal; `close()` bricht jede anstehende Wiederholung ab (Ergebnis, Cancel, Timeout, Destroy). |
| Android‑Adapter | `AndroidOnDeviceSpeechInput.kt` | nutzt `SpeechTurnRecovery`; kalter Neustart innerhalb des Turns; neue `resetRecognizer()`; `start(...)` liefert zusätzlich strukturierte `onFailure(Failure)`. |
| Session | `cognitive/LocalSpeechSession.kt` | optionaler `onFailure`‑Callback neben dem Besitzertext (keine String‑Vergleiche mehr). |
| Zustandsautomat | `cognitive/ContinuousConversationMode.kt` | `listenFailed(..., recoverable)`: bis zu `MAX_RECOVERY_RETRIES = 3` automatische Wiederholungen (800/800/1 600 ms), ab dem 2. Versuch `coldRecognizerRecommended`; danach PAUSE **mit** Kalt‑Empfehlung für den nächsten Tipp. `isRecoverable`/`isSilence` klassifizieren strukturiert. Budget wird bei erkannter Sprache zurückgesetzt. |
| Activity | `AgentActivity.kt` | `onConversationListenFailed(turn, Failure)`; Retry‑Delay aus dem Automaten; bei PAUSE nach Busy `resetRecognizer()`; `applyColdRecognizerIfRecommended()` vor jedem Hands‑Free‑Start; Tipp räumt anstehende `conversationListen` auf. |

Half‑Duplex während TTS bleibt (`LocalSpeechInputPolicy.maySpeak`). Nichts läuft unbegrenzt: Adapter ≤ 3 Neustarts/Turn,
Automat ≤ 3 Turn‑Wiederholungen, dann Pause mit ehrlicher Begründung („mehrfach belegt; neu gestartet“).

**RED → GREEN:**
- RED: `ContinuousConversationRecoveryTest`, `SpeechRecoveryPolicyTest`, `SpeechTurnRecoveryTest` gegen den unveränderten
  Code50‑Stand → 35 Kompilierfehler (fehlende Recovery‑API), siehe Abschnitt Nachweise.
- GREEN: siehe Abschnitt Nachweise.

## Befund 2 · „Das lokale Sprachmodell ist gerade nicht geladen“ nach erfolgreichem Gemma‑Direkttest

**Ursache:** `ContinuumUiCopy.memoryFallbackAnswer(…, modelReady = state == READY && loadedModel != null)`. Direkt nach dem
Direkttest steht die Laufzeit noch auf `GENERATING` (Nachlauf/Abbruch) oder `LOADING` → Hinweis behauptet „nicht geladen“.
**Fix:** neue Überladung mit `LocalModelState`; Text nennt den echten Zustand (geladen‑aber‑beschäftigt / lädt / entlädt /
Fehler / nicht geladen). Test `MemoryFallbackModelNotReadyTest.aBusyOrLoadingModelIsNotReportedAsMissing`.

## Befund 3 · „Unvollständige Antwort zurückgehalten: Die lokale Qualitätsprüfung wurde nicht bestanden“

**Ursache:** `BoundedAgent` meldet bei unvollständiger Generierung **nur** das Prüfergebnis, nicht den Abbruchgrund
(Ausgabelimit / Stillstand / Abbruch z. B. durch den Thermal‑Guard). **Fix:** Grund + Prüfnotiz im Text; Teiltext wird
weiterhin nur ausgegeben, wenn er die Prüfung besteht (keine Halluzination aus Fragmenten). Kein Auto‑Resume (Besitzerregel #569).

## Befund 4 · Wärme: „erreicht sehr schnell über 35 Grad und bricht ab“

Ehrlich vorweg: Android liefert Apps **keine Gradzahl**, nur Thermalstufen 0–6 (`PowerManager.currentThermalStatus`).
Der bestehende Guard (#569) stoppt ab Stufe 3 (SEVERE). Die 35 °C sind die Oberflächen‑/Akkutemperatur des Besitzers; die
Code‑Logik kann nur die Stufe sehen. Die Abhilfe muss daher die **Wärmeerzeugung** senken:

1. **Inferenz‑Threads begrenzen** (`localmodel/LocalModelComputeBudget.kt` neu, `LiteRtLmLocalModelRuntime`, `LocalModelHost`):
   LiteRT‑LM 0.18 `Backend.CPU(threadCount)` wurde bisher mit Default = **alle Kerne** aufgerufen (Fold7: 8). Jetzt beim Laden:
   ausgewogen = Kerne/2 (max. 4) → Fold7 **4 Threads**; Kühlmodus = Kerne/4 (max. 2) → **2 Threads**; bereits warm
   (Stufe ≥ 2) beim Laden → höchstens 2. Verifiziert gegen LiteRT‑LM‑Quelle `kotlin/java/com/google/ai/edge/litertlm/Config.kt`
   (`Backend.CPU(threadCount: Int? = null)`). Wirkt erst beim nächsten Laden (Engine‑Konfiguration).
2. **Kühlmodus‑Schalter** im Modell‑Center (`AgentActivity.showModelCenter`, Abschnitt „WÄRME · RECHENBUDGET“): zeigt geplante
   und tatsächlich geladene Threadzahl, aktuelle Android‑Thermalstufe, erklärt Stufe‑3‑Stopp. Einstellung in SharedPreferences
   `continuum.compute/cooling_mode`.
3. **Render‑Budget während Inferenz auf warmem Gerät** (`ui/JarvisRenderQuality.kt`): Inferenz + Stufe 1 → 50 ms/16 Partikel;
   Inferenz + Stufe 2 → 80 ms/12 Partikel, Kopf bleibt animiert. Test `inferenceOnAWarmDeviceGetsTheSmallestAnimatedBudget`.

**Nicht behauptet:** Tokens/s‑ oder Temperaturmessungen. Erwartung (Hypothese, auf dem Fold7 zu messen): 4 statt 8 Threads
senkt die Leistungsaufnahme deutlich, Decode‑Geschwindigkeit sinkt nur mäßig (Effizienzkerne tragen wenig bei). Messplan unten.

## Nachweise (Kommandos, exakt)

Umgebung: Codespace ohne Android‑SDK (dl.google.com nicht auflösbar), JDK 21.0.13 (Temurin, manuell), kotlinc 2.4.0
(GitHub‑Release), Jars von Maven Central nach `/opt/continuum-deps` (android-all‑15, okhttp, okio, shizuku, filament, gltfio, opencv).

```bash
export JAVA_HOME=/opt/jdk21 PATH=/opt/kotlinc-dl/kotlinc/bin:/opt/jdk21/bin:$PATH \
  CONTINUUM_ANDROID_DEPS=/opt/continuum-deps \
  CONTINUUM_JUNIT_JARS=/opt/continuum-deps/junit-4.13.2.jar:/opt/continuum-deps/hamcrest-core-1.3.jar \
  JAVA_OPTS="-Xmx4g -XX:+UseSerialGC"
scripts/android-kotlin-unit-check.sh /tmp/unitcheck
```

Hinweis: ohne `JAVA_OPTS=-Xmx4g` stirbt kotlinc bei ~2 000 Dateien mit Exit 137 (OOM, 512 MB Default).

| Lauf | Ergebnis |
|---|---|
| Baseline (473f7972 + nur neue Tests) | `KOTLIN_COMPILE_CHECKED` für main; Test‑Set **RED**: 35 Fehler in `ContinuousConversationRecoveryTest` (fehlende API) |
| Nach Fix | siehe `RESULT_AFTER_FIX` unten |

RESULT_AFTER_FIX (`/tmp/unitcheck-p0`, JDK 21.0.13, kotlinc 2.4.0): main **kompiliert** (`KOTLIN_COMPILE_CHECKED`, 133 Fehler nur in den
LiteRT‑LM‑Dateien = erwartet ohne Google Maven), 622 Testklassen + 5 Checks, **JUnit: 3 508 Tests, 3 Fehler** — alle drei vorbestehend und
umgebungsbedingt, keiner in den P0‑Dateien:

| Test | Grund | Nachweis |
|---|---|---|
| `ShellExecutionTest.successfulGermanOutputRoundTripsThroughStrictVersionedWire` | Sandbox‑Locale `POSIX` (native.encoding ASCII) | **PASS** nach `LC_ALL=C.UTF-8` |
| `ChiefBotStorageBoundaryTest.failedFilesystemWriteRetainsRegistryAndSafeDiagnostic` | Sandbox läuft als `root`, read‑only‑Verzeichnis ist schreibbar | erwarteter Fehlschlag als root |
| `OwnerAppActionFlowTest.compoundRequestsStayWholeAndReferencesNeedActualCandidates` | JDK ≥ 19: `\b` ist ASCII‑only (ohne `UNICODE_CHARACTER_CLASS`), `\böffnen\b` matcht nicht; Android‑ICU und CI‑JDK 17 sind Unicode‑aware | Mini‑Repro `Pattern.compile("(?iu)\\b(öffne|öffnen)\\b").matcher("Signal öffnen? Und").find()` → `false` auf JDK 21 |

Neue/erweiterte P0‑Tests, alle **PASS**: `SpeechRecoveryPolicyTest`, `SpeechTurnRecoveryTest`, `ContinuousConversationRecoveryTest`
(bounded Busy‑Retries, Tipp nach Busy‑Pause, 20 Hands‑Free‑Turns mit injiziertem BUSY, nicht‑behebbare Fehler → Pause, Stille‑Klassifikation,
STOP während Recovery, Barge‑in + Busy), `LocalModelComputeBudgetTest`, `JarvisRenderQualityTest`, `MemoryFallbackModelNotReadyTest`.

Nicht ausgeführt (UNAVAILABLE in dieser Umgebung): Gradle‑Build, Android‑Lint, Robolectric `src/uiTest`, APK, Gerät.

## Offene Geräteprüfungen (Besitzer, Fold7)

1. Gesprächsmodus an, 10–20 Turns mit Antworten > 20 s: Mikrofon muss nach jeder Antwort wieder zuhören; eine Pause darf nur nach
   3 automatischen Wiederholungen kommen und ein Tipp muss sie beenden (kein App‑Daten‑Löschen).
2. Barge‑in während TTS (Tipp), „Stopp“, Fokuswechsel/Fold‑Öffnen während des Zuhörens, Modell entladen/neu laden im Gesprächsmodus.
3. Wärme: Modell entladen → Kühlmodus an → laden; Modell‑Center zeigt „geladen mit 2 Threads“. Dann dieselbe lange Frage im
   Vergleich zu „ausgewogen“ (4 Threads): Zeit bis Stufe 3 und Sekunden/Antwort notieren (`LocalInferenceProfile`‑Anzeige).
4. Direkttest → sofort allgemeine Frage: Hinweis darf nicht mehr „nicht geladen“ sagen, sondern „arbeitet gerade noch“.

## Nicht angefasst

Originalsignatur, Release, `main`, fremde KI‑Branches, Experimente‑Registry (keine neue Hypothese‑ID; Messung ist Owner‑Gerätetest).
