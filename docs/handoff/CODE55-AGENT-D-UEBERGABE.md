# Code55 · Agent D · Freisprechen – Übergabe (12.11.2026)

Feldfehler (Fold7, Code54 0.3.53): Gesprächsmodus „aktiviert/deaktiviert sich selbst, hört nicht mehr zu“,
Toast „Gesprächsmodus pausiert: Der lokale Spracherkennungsdienst meldet einen Fehler …“ (= `Failure.SERVICE_FAILURE`).
Fold7-Gerätetest dieses Stands: **NOT_RUN**. Der genaue native Android-Fehlercode im Feld ist unbekannt.

## Ursachen (Basis d46c286de)
1. `AndroidOnDeviceSpeechInput.kt:91` – der gepoolte („warme“) native Erkenner wird nur bei ERROR_CLIENT/
   ERROR_RECOGNIZER_BUSY verworfen. Bei ERROR_SERVER, ERROR_SERVER_DISCONNECTED, ERROR_TOO_MANY_REQUESTS,
   ERROR_AUDIO und unbekannten Codes (`:186-187` → SERVICE_FAILURE) bleibt die tote Instanz im Pool;
   `acquireWarmRecognizer` (`:39`) gibt sie beim nächsten Start wieder aus → der Neuversuch scheitert sicher.
2. `ContinuousConversationMode.kt:96-100` + `:223` – SERVICE_FAILURE (inkl. unbekannter Codes) galt als
   „recoverable“ mit 3 automatischen Neuversuchen (800/800/1600 ms), der erste davon warm (`recoveryAttempts >= 2`
   für kalt). Jeder Versuch setzt `voiceListening` an/aus (`AgentActivity.kt` startVoiceInput → onState) →
   sichtbares Ein/Aus, danach Pause. Nach dem Antippen dasselbe erneut.
3. `AgentActivity.kt` onConversationListenFailed (PAUSE-Zweig): Erkenner wurde nur bei „recoverable“ freigegeben.
4. Lebenszyklus (nicht geändert): `onPause` → `cancelSpeechInputScope()` → `continuousConversation.stop()` beendet
   den Modus **ohne Hinweis** (z. B. SAF-Dateiauswahl, Activity-Neuaufbau). Für den Besitzer wirkt das wie
   „deaktiviert sich selbst“. Halbduplex (kein Zuhören während Maya/TTS spricht) ist intakt.

## Änderungen
- `AndroidOnDeviceSpeechInput`: jeder nicht-stille Endfehler verwirft den gepoolten Erkenner (Stille
  NO_MATCH/SPEECH_TIMEOUT bleibt warm) – neuer `evictsWarmRecognizer(error)`.
- `ContinuousConversationMode`: `recoveryBudget(failure)` – SERVICE_FAILURE genau **ein** verzögerter Neuversuch
  (1500 ms) mit frischem Erkenner, dann Pause; BUSY/AUDIO/START_FAILED behalten den dokumentierten #641-Plan;
  harte Fehler pausieren sofort mit Grund. Ehrlicher Pausentext („Ein automatischer Versuch …“).
- `AgentActivity`: übergibt Budget/Verzögerung; bei jeder Pause wird der native Erkenner freigegeben.
- Tests: `HandsFreeServiceFailureRecoveryTest` (rein), `AndroidOnDeviceSpeechEvictionUiTest` (Robolectric-Shadow).

## Offen / nächste Schritte
- Nativen Android-Fehlercode im Pausentoast anzeigen (Felddiagnose), z. B. „(Android-Code 11)“.
- onPause: Modus PAUSIERT statt AUS, beim Zurückkehren klarer Hinweis + Ein-Tipp-Fortsetzen; Zustand über
  `onSaveInstanceState` für den Activity-Neuaufbau nach der Dateiauswahl erhalten.
- Fold7: Gesprächsmodus 20 Runden mit Maya und Offline-Stimme, Dateiauswahl dazwischen, Erkennerdienst stoppen.
- CockpitWorkEvent-Vertrag (Zweig `claude/code55-agent-d-work-events-20261112`): **nicht begonnen**.
  Entwurf: versiegelte Klasse mit TaskAccepted/GazeTarget/Click(verified)/Scroll/TypeText/Observation/Verified/
  AskOwner/Stop/Error; gebundener Reducer (feste Kapazität, Ereignisse nach Stop verwerfen, fremde taskId ignorieren).
