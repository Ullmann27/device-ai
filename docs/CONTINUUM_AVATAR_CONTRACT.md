# Continuum Avatar-Vertrag (Vorbereitung, M3) – für die Grafikseite

Status: `HOST_VERIFIED` für Logik und Verdrahtung im Quellcode. Es gibt **keine Grafik und kein 3D-Rendering**.
Bis die Grafikseite einen eigenen `AvatarSink` einsteckt, läuft der Platzhalter `NoOpAvatarSink`.
Der bestehende Hologramm-Kern (`ContinuumHologramView`) bleibt unverändert. Der Avatar bekommt dieselben Signale zusätzlich.

## Einstecken
```kotlin
// vor dem Start von AgentActivity, z. B. in einer Application-Unterklasse eines Avatar-Moduls:
at.heinz.deviceai.avatar.AvatarSinkRegistry.sink = AvatarSink { event -> meinRenderer.post(event) }
```
- `AvatarSink.onAvatarEvent` wird auf **beliebigen Threads** aufgerufen (UI-Thread und TTS-Callback-Thread).
  Die Methode muss schnell zurückkehren und Ereignisse selbst an den Render-Thread übergeben.
- Eine Exception im Sink wird verschluckt. Chat, Werkzeuge und Sprache laufen dann trotzdem weiter.
- `AvatarDirector.attach(sink)` sendet sofort den aktuellen Zustand, damit ein spät gestarteter Renderer nicht im Leerlauf beginnt.
  Zur Laufzeit wird nicht umgeschaltet: Die Activity liest die Registry nur beim Erzeugen.

## Ereignisse (`AvatarEvent`, alle mit `atMillis` = `System.currentTimeMillis()`)
| Ereignis | Bedeutung | Quelle in der App |
|---|---|---|
| `StateChanged(state, expression, gaze)` | Nur bei einer echten Änderung | `refreshHologramState()` (dieselben Flags wie das Hologramm) |
| `SpeechStarted(utteranceId, text)` | Die TTS beginnt den Text, der ohnehin vorgelesen wird | `UtteranceProgressListener.onStart` |
| `VisemeCue(utteranceId, viseme, weight, durationMs)` | Ungefähre Mundform für das gerade gesprochene Wort | `onRangeStart(start, end)` → `GermanVisemeApproximation` |
| `AudioLevel(utteranceId, level 0..1)` | Echte Hüllkurve des synthetisierten Audios, ca. 30 Hz | `voiceSignal.level()` im 33-ms-Frame |
| `SpeechEnded(utteranceId, interrupted)` | Ende, Fehler oder Abbruch | `onDone/onError/onStop` |

Ereignisse einer veralteten oder schon beendeten `utteranceId` werden verworfen.

## Zustände und Vorgaben
Priorität, von oben nach unten: `WAITING_FOR_APPROVAL` > `LISTENING` > `SPEAKING` > `BLOCKED` > `RESEARCHING` > `LEARNING` (busy+lernen) > `THINKING` (busy) > `IDLE`.

| AvatarState | Ausdruck | Blick (`GazeTarget`, x/y in −1..1, 0/0 = Besitzer/Kamera) |
|---|---|---|
| IDLE | NEUTRAL | OWNER (0, 0) |
| LISTENING | ATTENTIVE | OWNER |
| THINKING | FOCUSED | THINKING_AWAY (0,35, 0,25) |
| RESEARCHING | CURIOUS | READING_DOWN (0, −0,4) |
| LEARNING | FOCUSED | READING_DOWN |
| WAITING_FOR_APPROVAL | ATTENTIVE | OWNER |
| SPEAKING | NEUTRAL | OWNER |
| BLOCKED | CONCERNED | OWNER |

Ausdruck und Blick sind **Vorgaben**. Die Grafikseite darf sie animieren und überblenden (Lidschlag, Mikrobewegungen),
aber keinen anderen Zustand erfinden.

## Viseme
Das Viseme-Set umfasst 15 Klassen, benannt wie bei OVRLipSync: `SIL PP FF TH DD KK CH SS NN RR AA E I O U`.
Android-TTS liefert nur **Wortbereiche** und keine Phoneme. `GermanVisemeApproximation` bildet deshalb deutsche Grapheme grob ab
(sch→CH, ch→KK, b/p/m→PP, f/v/w→FF, s/z/ß→SS, n/l→NN, r→RR, Vokale→AA/E/I/O/U). `SIL` steht am Wortanfang, und doppelte Folgen werden zusammengefasst.
Ein Wort bekommt etwa 320 ms, gleichmäßig verteilt, mit mindestens 40 ms pro Visem.
**Empfehlung:** Die Mundöffnung über `AudioLevel` (echtes Signal) steuern und die Form über `VisemeCue` (Näherung). Eine Grafikseite mit eigener
Phonemquelle darf die Cues ignorieren.

## Datenschutz und Grenzen
- Kein Ereignis enthält Werkzeugausgaben, Gedächtnisinhalte oder Modell-Zwischentexte. `SpeechStarted.text` ist nur der Text, der hörbar vorgelesen wird.
- Noch nicht verdrahtet (`SOURCE_ONLY`): `WAITING_FOR_APPROVAL` und `BLOCKED`. Das Signal existiert im Vertrag, aber die Freigabedialoge der Activity
  setzen es noch nicht. Es gibt keine Kopf- oder Augenverfolgung über die Kamera.
- Auf dem Fold7 ist noch nichts geprüft (`DEVICE_REQUIRED`): echte Zeitpunkte von `onRangeStart` je TTS-Engine sowie Thread und Latenz der Callbacks.

Tests: `app/src/test/java/at/heinz/deviceai/avatar/AvatarDirectorTest.kt` (Logik) und
`.github/continuum/android-avatar-wiring.test.mjs` (Verdrahtung in `AgentActivity`).
