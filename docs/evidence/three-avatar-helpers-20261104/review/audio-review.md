# Avatar-Audio-Reducer: abschließende Gegenprüfung

**Datum:** 2026-11-04
**Reviewer:** Helfer Integration (`/root/helfer_integration`)
**Ergebnis:** **PASS nach Behebung zweier Important-Findings**
**Abschließend geprüfter ursprünglicher Commit:** `693f2c0a5721fc2761669cabd578ea0b96f8f837`

## Gegenstand und Nachweisgrenze

Die Prüfung betrifft ausschließlich die zeitliche Verarbeitung von Audioereignissen im bestehenden `AvatarPoseReducer` in `app/src/main/java/at/heinz/deviceai/avatar/AvatarContract.kt`. Geprüft wurden die tatsächlichen Produktionsdiffs und zugehörigen Regressionstests, einschließlich der Wechselwirkung mit Standard-Pose-Zeit, Visem-Cues, Sprachunterbrechungen und den vorhandenen Gaze-Watermarks.

Dies ist eine lesende Quell- und Gegenfallprüfung. Der Reviewer hat während dieser Prüfung weder Repository-Quellcode geändert noch zusätzliche Tests ausgeführt. Die tatsächlichen RED/GREEN-Läufe und die gemeinsame Integration übernimmt Root; ihre Ergebnisse müssen durch die gesonderten Testprotokolle belegt werden. Dieser Review behauptet keinen Gradle-, Geräte-, GPU- oder Audioausgabe-Erfolg.

Die nachstehenden vollständigen SHAs bezeichnen die ursprünglichen lokalen Entwicklungscommits. Eine spätere Übernahme oder Veröffentlichung kann andere Commit-SHAs besitzen und ist separat zu dokumentieren.

## Verlauf der überprüften Änderungen

| Bestandteil | Ursprünglicher Commit |
| --- | --- |
| Sieben ursprüngliche Audio-Reihenfolgetests | `c678233cb7b77fb94cdd95717c9c847001b153a4` |
| Zusätzlicher Future-Audio-Test / Parent der ersten Produktionskorrektur | `22329b5fd372160d32f07a784d8a3d4973d98e5a` |
| Erste Produktionskorrektur | `e8accee0e7ad2ad1a72bdaf3aa549248cb51f5a1` |
| Regression für mehrere Samples vor dem ersten Rendern | `8f4f9305b211c696ca7504ba7f0de36fa87c7e28` |
| Erste Nachbesserung für diesen Batch-Fall | `fdc41fbb5adc643173254074a6e3d0bef88c6456` |
| Regression für Standard-Pose vor expliziter Renderzeit | `862f02af52fb8b35d906356b626e958144644da2` |
| Abschließende vereinfachte Produktionskorrektur | `693f2c0a5721fc2761669cabd578ea0b96f8f837` |

Die Regressionen liegen in `app/src/test/java/at/heinz/deviceai/avatar/AvatarSpeechOrderingRegressionTest.kt`; der abschließende Entwicklungsstand enthält dort zehn Tests.

## Important-Finding 1: Replay nach mehreren ungezeichneten Samples

**Betroffener Stand:** `e8accee0e7ad2ad1a72bdaf3aa549248cb51f5a1`.

Die erste Korrektur setzte nach dem Erkennen eines zukünftigen Samples die Audiozeit auf `newestConfirmedAudioMillis` zurück. Diese Marke wurde erst durch einen Pose-Aufruf bestätigt. Treffen mehrere Audioereignisse vor dem ersten Rendern ein, war sie noch `Long.MIN_VALUE`.

Konkreter Gegenfall innerhalb derselben aktiven Äußerung:

1. `SpeechStarted` zum Zeitpunkt `100`.
2. `AudioLevel(level = .9f, atMillis = 200)`.
3. Ohne zwischenzeitliches `pose()` folgt `AudioLevel(level = 1f, atMillis = Long.MAX_VALUE)`.
4. `pose(201)` erkennt den Zukunftswert und löscht die Energie, setzt die Reihenfolgegrenze jedoch auf `Long.MIN_VALUE` zurück.
5. Ein erneut zugestelltes Sample bei `199` oder `200` wird wieder angenommen und erzeugt erneut `.36f` Energie.

Das verletzte die verlangte Ablehnung alter beziehungsweise identischer Audioereignisse. Der vorhandene Future-Test bestätigte Sample `200` vorher mit `pose(200)` und erfasste diesen Fall deshalb nicht.

**Behebung:** Der ergänzte Test `futureAudioBetweenFramesCannotReopenFromAlreadyDeliveredSamples` prüft nach `pose(201)` die Ablehnung der Zeitstempel `199`, `200` und `201`, anschließend die Annahme eines frischen Samples bei `202`. Die abschließende Korrektur verwendet den tatsächlichen Renderzeitpunkt als neue Grenze. Der Gegenfall ist im finalen Quellstand geschlossen.

## Important-Finding 2: Standard-Pose bestätigt eine blockierende Zukunftsmarke

**Noch betroffener Zwischenstand:** `fdc41fbb5adc643173254074a6e3d0bef88c6456`.

Die erste Nachbesserung setzte die Grenze auf `maxOf(newestConfirmedAudioMillis, atMillis)`. Dadurch konnte eine zuvor vom Standard-Pose-Aufruf bestätigte Zukunftsmarke erhalten bleiben.

Konkreter Gegenfall:

1. Ein gültiges Sample bei `200` wird mit `pose(200)` verarbeitet.
2. Ein Sample bei `Long.MAX_VALUE` trifft ein.
3. `pose()` ohne Zeitargument verwendet `lastEventMillis` und bestätigt dadurch intern `Long.MAX_VALUE`.
4. Das anschließende explizite `pose(201)` löscht zwar die Energie, behält durch `maxOf` aber die Reihenfolgegrenze `Long.MAX_VALUE`.
5. Ein gültiges neues Sample bei `202` bleibt verworfen; eine Wiederaufnahme innerhalb derselben Äußerung ist damit blockiert.

**Behebung:** Der ergänzte Test `aDefaultPoseCannotKeepFutureAudioAheadOfTheExplicitRenderClock` erfasst genau diesen Wechsel vom Standardaufruf zur expliziten Renderzeit. Commit `693f2c0a5721fc2761669cabd578ea0b96f8f837` entfernt das zusätzliche Bestätigungsfeld vollständig. Bei einem erkannten Zukunftswert wird `lastAudioMillis` unmittelbar auf das explizite `atMillis` gesetzt. Auch dieser Gegenfall ist im finalen Quellstand geschlossen.

## Bewertung des abschließenden Produktionsdiffs

- Audioereignisse für eine andere oder bereits beendete Äußerung, nicht endliche Pegel sowie ältere oder gleiche Audiozeitstempel werden vor jeder Änderung von Energie oder `lastEventMillis` verworfen.
- Erkennt `pose(atMillis)` ein zukünftiges Audiosample, wird dessen gesamte Glättungsenergie gelöscht. Die neue Reihenfolgegrenze ist der Renderzeitpunkt. Nach `pose(201)` bleiben alle Samples bis einschließlich `201` gesperrt; Sample `202` ist wieder zulässig.
- Das erste frische Sample mit Pegel `.1f` erzeugt nach der Korrektur `.04f` Energie aus dem vorhandenen Glättungsfaktor `.4f`; es enthält keinen Anteil des verworfenen Zukunftswerts.
- Ende, Pause und Wechsel der Äußerung verwenden weiterhin `clearSpeech()`. Dadurch werden Energie, Audiozeit und Visemzustand zurückgesetzt; verspätete Ereignisse der alten Äußerung bleiben abgewiesen.
- Visem-Auswahl, Visem-Warteschlange und Gaze-Watermarks bleiben unverändert. Die Korrektur führt keine weitere Uhr, Warteschlange oder öffentliche API ein.

Die bewusste konservative Wirkung nach Future-Recovery ist, auch verspätet eintreffende, noch nicht gezeichnete Samples bis einschließlich des Wiederherstellungszeitpunkts zu verwerfen. Für die visuelle Audiohüllkurve verhindert dies die Wiederbelebung alter Energie; frische spätere Samples werden wieder verarbeitet.

## Verbleibende Grenze der Zeitdomäne

Der bestehende Produktionspfad verwendet dieselbe Systemzeitdomäne: `AvatarDirector` nutzt standardmäßig `System.currentTimeMillis`; `ContinuumHologramView` übergibt beim Rendern ebenfalls `System.currentTimeMillis` an `pose(...)`.

Diese Uhr ist nicht garantiert monoton. Die hier geprüfte Ablehnung alter Samples und Wiederaufnahme nach einem Zukunftswert gilt innerhalb des unveränderten Zeitmodells und bei nicht rückläufigem legitimen Renderzeitverlauf. Eine allgemeine Absicherung gegen Systemzeitrücksprünge oder einen Wechsel der Zeitdomäne wurde weder implementiert noch nachgewiesen. Insbesondere darf der lokale Darstellungsfilter nicht als umfassende Replay-Abwehr über solche Zeitwechsel hinweg beschrieben werden.

Eine Änderung der Uhr oder des öffentlichen Vertrags liegt außerhalb dieses schmalen Fixes. Im geprüften finalen Diff bestehen nach Schließung der beiden genannten Gegenfälle **keine weiteren Critical- oder Important-Findings**. Die Ausführungsabnahme bleibt an die separat protokollierten RED/GREEN-Ergebnisse gebunden.
