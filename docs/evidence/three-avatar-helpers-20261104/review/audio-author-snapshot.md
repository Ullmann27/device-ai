# Avatar-Audio: Implementierungshandoff

Stand: 2026-11-04. Helfer: `helfer_pruefung_animation`.

## Stand und Übergabe

- Worktree: `/workspace/scratch/b6ee64d418cf/helper-animation`
- Branch: `codex/three-helpers-animation-20261104`
- Ausgangspunkt: `a65cfcac`, Produktionsquellen aus `d352bab6855db2a44def0bbf60fd93202b4e3066`.
- Finaler lokaler Commit: `693f2c0a5721fc2761669cabd578ea0b96f8f837`.
- Nur `AvatarPoseReducer` in `app/src/main/java/at/heinz/deviceai/avatar/AvatarContract.kt` und die neue Datei `app/src/test/java/at/heinz/deviceai/avatar/AvatarSpeechOrderingRegressionTest.kt` geändert.
- Keine öffentlichen Contracts, Events, Clock-APIs, Abhängigkeiten oder anderen Arbeitsbereiche verändert. Keine Remote-Mutation durch diesen Helfer; Integration und Remote-Sicherung erfolgen durch Root.

## Fehler und endgültige Korrektur

Root reproduzierte an den unveränderten Produktionsquellen: SpeechStarted100, Audio(.9,200), Audio(.1,250), danach Replay Audio(.9,200) verändert die Energie von ungefähr `.256` auf `.5136`. Das alte Sample wurde erneut geglättet und setzte den Audiozeitstempel zurück.

Der finale Reducer verwirft Audio vor jeder Zustands- und Default-Zeitänderung, wenn die Äußerung nicht aktiv ist, der Pegel nicht endlich ist oder der Sample-Zeitstempel kleiner oder gleich der letzten Audiomarke ist. Ein verworfenes Sample verändert weder Energie noch Ablaufgrenze oder den Default-Zeitpunkt von `pose()`.

Erkennt `pose(atMillis)` einen Audiozeitstempel in der Zukunft, setzt der Reducer die Energie auf null und die Audiomarke direkt auf den expliziten Renderzeitpunkt. Hat dieses Sample auch die Default-Pose-Zeit gesetzt, wird diese auf den Renderzeitpunkt korrigiert. Eine zwischenzeitlich eingeführte bestätigte Audiomarke wurde nach Gegenprüfung vollständig entfernt: Sie konnte selbst durch einen Default-Aufruf mit Zukunftszeit vergiftet werden. Der finale Fix benötigt keine zusätzliche Zustandsvariable und keine Warteschlange.

**Bewusster Tradeoff:** Nach einem ungültigen Zukunftssample werden wartende Audioereignisse bis einschließlich des Recovery-Renderzeitpunkts verworfen. Erst ein Sample mit neuerem Zeitstempel öffnet den Mund wieder. Das verhindert Replay auch dann, wenn mehrere Callbacks vor dem ersten Rendern eintreffen, und nimmt dabei ein kurzes Schweigen bis zum nächsten frischen Sample in Kauf. Die erste neue Energie wird aus null geglättet; ein hohes ungültiges Zukunftssample wirkt nicht nach.

`clearSpeech()` setzt die bestehende Audiomarke und Energie zurück. Ende, Pause und Wechsel der Äußerung behalten ihre vorhandenen Grenzen.

## Originale lokale Commits in Reihenfolge

| Commit | Inhalt |
|---|---|
| `c678233cb7b77fb94cdd95717c9c847001b153a4` | Erste sieben Regressionstests, noch ohne Produktionskorrektur. |
| `22329b5fd372160d32f07a784d8a3d4973d98e5a` | Achter Test: Zukunftssample darf Energie, Reihenfolge und Default-Pose nicht vergiften. |
| `e8accee0e7ad2ad1a72bdaf3aa549248cb51f5a1` | Erster Replay-/Future-Fix; noch mit später verworfener bestätigter Audiomarke. |
| `8f4f9305b211c696ca7504ba7f0de36fa87c7e28` | Neunter Test: gültiges Sample und Zukunftssample vor dem ersten Rendern. |
| `fdc41fbb5adc643173254074a6e3d0bef88c6456` | Zwischenkorrektur des Batchfalls durch Anheben der Recovery-Grenze. |
| `862f02af52fb8b35d906356b626e958144644da2` | Zehnter Test: explizite Renderzeit muss nach einem Default-Aufruf wieder Erholung erlauben. |
| `693f2c0a5721fc2761669cabd578ea0b96f8f837` | Finaler vereinfachender Fix: Zusatzmarke entfernen, explizite Renderzeit als Recovery-Grenze verwenden. |

Die früheren Produktionscommits sind Zwischenstände und nicht separat als fertige Korrektur zu übernehmen. Der finale Stand enthält beide Reviewkorrekturen.

## Zehn Regressionstests

1. `olderAudioCannotResmoothTheCurrentEnvelope`: verspätetes Audio verändert weder Energie noch sichtbare Pose.
2. `olderAudioCannotMoveTheLatestSamplesExpiry`: die Ablaufgrenze bleibt am jüngsten gültigen Sample; 250 ms noch aktiv, 251 ms geschlossen.
3. `repeatedTimestampIsIdempotent`: identische oder anderswertige Wiederholungen desselben Zeitstempels werden nicht erneut geglättet.
4. `rejectedAudioCannotRewindOrAdvanceTheDefaultPoseClock`: alte, doppelte, nicht endliche oder fremde Samples verändern die Default-Pose nicht.
5. `replacementSpeechStartsWithItsOwnAudioOrdering`: eine neue Äußerung beginnt mit eigener Audioreihenfolge; fremde alte Callbacks bleiben wirkungslos.
6. `rejectedFutureAudioCannotPoisonEnergyOrderingOrTheDefaultPoseClock`: Zukunftsenergie wird verworfen; alte Samples bleiben gesperrt, frische Energie und aktuelle Viseme erholen sich.
7. `futureAudioBetweenFramesCannotReopenFromAlreadyDeliveredSamples`: Audio200 und AudioMAX vor dem ersten Frame; nach pose201 bleiben 199/200/201 gesperrt, Audio202 wird frisch geglättet.
8. `aDefaultPoseCannotKeepFutureAudioAheadOfTheExplicitRenderClock`: AudioMAX und pose() können die anschließende Erholung durch pose201 und Audio202 nicht dauerhaft blockieren.
9. `endedSpeechCannotRestartFromQueuedAudioOrVisemes`: nach Ende bleiben verspätete Audio-/Visemeereignisse wirkungslos; neue Äußerung funktioniert.
10. `pauseClearsAudioOrderingWithoutAdmittingLateCallbacks`: clearSpeech/Pause schließt den Mund und lässt nur eine neue Äußerung wieder Audio liefern.

## Prüfung und Grenzen

- `git diff --check`: PASS am finalen lokalen Stand; Worktree beim letzten Commit sauber.
- Unabhängige schmale Gegenprüfung durch `helfer_integration`: PASS für `693f2c0`; beide gemeldeten Reviewfälle sind durch eigene Tests erfasst. Kein weiterer Critical-/Important-Befund in dieser Gegenprüfung.
- Der Helfer hat keine eigenen konkurrierenden Gradle-Läufe gestartet. Root führt den gesamten Audio-RED gegen die unveränderten d352-Produktionsquellen aus und danach die gemeinsame GREEN-Prüfung. Diese Ergebnisse sind zum Handoffzeitpunkt **ausstehend** und dürfen hier nicht als bestanden übernommen werden.
- Bereits beobachteter früher Root-RED mit den ersten sieben Tests: 4 Fehler, 3 erfolgreich, 0 übersprungen, 0 Errors. Das belegt nicht die vollständige Zehner-Suite oder den finalen Fix.
- Native GPU-, Android-Geräte- und tatsächliche Audioausgabe-Abnahme: **NOT_RUN**.
- Zukunft wird erst durch den Abgleich mit einem expliziten `pose(atMillis)` erkannt. Ein Default-Aufruf verwendet den letzten Ereigniszeitpunkt und liefert allein keine unabhängige Zeitwahrheit. Audioereignisse und explizite Renderzeit müssen derselben bestehenden Zeitdomäne entstammen.
- Keine allgemeine Absicherung gegen rückwärts springende Systemuhren und keine neue Thread-Sicherheitsgarantie: Die bestehende externe Serialisierung/Synchronisierung des Reducers bleibt erforderlich.

## Nächster Schritt und Rückfall

Root übernimmt die tatsächlichen RED-/GREEN-Ergebnisse, Integrations-SHAs und Remote-Nachweise in den gemeinsamen Handoff. Bei Rücknahme nur diesen isolierten Reducer-/Test-Diff gezielt revertieren; keine fremden Änderungen oder Branches zurücksetzen. Es gibt keine Datenmigration, Konfigurationsänderung oder Signaturänderung.
