# Continuum Avatar – Andock-Leitfaden (einzige Avatar-Schnittstelle)

Gültig ab PR #373 (`continuum/m3-grok-selbstrekonstruktion-20261103`). Laut Koordinationsbeschluss des Besitzers ist **diese** Schnittstelle
(`app/src/main/java/at/heinz/deviceai/avatar/`) die einzige Avatar-Schnittstelle in device-ai. Andere Avatar-Interfaces werden nicht weitergebaut.
Update 03.11.2026 (Operation Ω): Der optionale native 2D-Kopf in `ContinuumHologramView` verwendet diesen Vertrag und den bestehenden Zeichen-/Lifecycle-Loop. Die Originalkugel bleibt Standard; Auswahl unter Systemstatus → AVATAR · KUGEL ODER KOPF. Keine neue TTS, Kamera oder 3D-Assets. Die Registry bleibt standardmäßig `NoOpAvatarSink`; `AgentActivity` hängt ihre aktuelle View direkt über `attach` an und beim Zerstören ab. Status/Tests/Grenzen: [Omega-Avatar-Handoff](research/omega/OMEGA-AVATAR-HANDOFF.md).
Evidenz: Logik, Port und Verdrahtung sind `HOST_VERIFIED` (JUnit und Node-Verdrahtungstest). TTS-Timing auf dem Fold7 ist `DEVICE_REQUIRED`.

## 1. Rollen – wer was tut
| Rolle | Darf | Darf NICHT |
|---|---|---|
| **AgentActivity (Continuum-App)** | Als einzige Stelle `AvatarDirector` füttern: `onRuntime`, `speechStarted/wordRange/audioLevel/speechEnded` | – |
| **Grafik-Bot** | `AvatarSinkRegistry.sink` setzen, `AvatarEvent` konsumieren, optional `AvatarPoseReducer` benutzen | Eigene TTS-Listener, eigene Zustandslogik, Netzwerkzugriffe |
| **Continuum 1 (Agent/Orchestrator)** | Bestehende Flags setzen (`busy`, Freigabedialog über `showStructuredToolConfirmation`, `run.blocker`) | `AvatarEvent` direkt erzeugen |
| **REPERATEUR (CCC1/Reparatur)** | Den Status über die vorhandenen App-Flags spiegeln (z. B. `busy` während eines Laufs, Blocker als `run.blocker`/Ablehnung) | Eigenen Avatar-Zustand, „SUCCEEDED“ vor der Postcondition |
| **Gedächtnis** | Nichts emittieren. Lernphasen laufen schon über `learningVisualActive` → `LEARNING` | Gedächtnisinhalte in Avatar-Ereignisse schreiben |

Wer einen neuen Zustand braucht, erweitert `AvatarRuntimeSignals` und die Prioritätsliste im `AvatarDirector` mit Test.
Ein zweites Enum oder ein paralleles Interface ist nicht erlaubt.

## 2. Einstecken (Grafikseite)
```kotlin
import at.heinz.deviceai.avatar.*

class MeinAvatarRenderer : AvatarSink {
    private val queue = java.util.concurrent.ConcurrentLinkedQueue<AvatarEvent>()
    private val reducer = AvatarPoseReducer()                 // gleiche Mund-/Blick-Abbildung für alle
    override fun onAvatarEvent(event: AvatarEvent) { queue.add(event) }   // schnell zurückkehren!
    fun onFrame() {                                           // Render-Thread, 30–60 fps
        while (true) reducer.apply(queue.poll() ?: break)
        val p = reducer.pose()                                // state, expression, gazeX/Y, jawOpen, viseme, …
        // zeichnen …
    }
}
// vor dem Start von AgentActivity (z. B. Application.onCreate eines Avatar-Moduls):
AvatarSinkRegistry.sink = MeinAvatarRenderer()
```
- **Threading:** `onAvatarEvent` läuft auf dem UI-Thread **und** auf dem TTS-Callback-Thread. Der Director ist synchronisiert, der Sink muss
  Ereignisse selbst in eine Queue legen. `AvatarPoseReducer` ist nicht threadsicher und gehört nur in den Render-Thread.
- Wirft der Sink eine Exception, wird sie verschluckt. Chat, Werkzeuge und Sprache laufen weiter.
- `AvatarDirector.attach(sink)` sendet sofort den aktuellen Zustand und bei laufender TTS die aktuelle SpeechStarted-Identität. Damit kann eine neue Fold-/Home-View die folgenden Audiowerte übernehmen. Der Renderer speichert keinen Sprechtext. Die Activity liest die Registry beim Erzeugen, hängt danach die jeweils aktuelle View an und beim Zerstören den NoOp-Sink.

## 3. Ereignisse und Raten
| Ereignis | Wann / Rate | Quelle |
|---|---|---|
| `StateChanged(state, expression, gaze)` | nur bei einer Änderung | `refreshHologramState()` |
| `SpeechStarted(utteranceId, text)` | einmal pro Äußerung; `text` = der hörbar vorgelesene Text | TTS `onStart` |
| `VisemeCue(utteranceId, viseme, weight, durationMs)` | pro Wort, ca. 320 ms gleichmäßig verteilt, ≥ 40 ms je Visem | TTS `onRangeStart` |
| `AudioLevel(utteranceId, level 0..1, source)` | ca. 30 Hz (33-ms-Frame) | `TtsHologramSignal.level()` |
| `SentenceEnd(utteranceId, boundary)` | beim letzten Wort eines Satzes im gesprochenen Text (`STATEMENT` bei `.`/`!`, `QUESTION` bei `?`); keine Zahl-, Datei- oder Abkürzungspunkte; ohne Text | TTS `onRangeStart` + `SpeechStarted.text` |
| `SpeechEnded(utteranceId, interrupted)` | einmal; auch bei Fehler oder Abbruch | `onDone/onError/onStop` |

`source = PCM_ENVELOPE` ist die gemessene Audiohüllkurve. `WORD_RANGE_FALLBACK` (übernommen aus dem Avatar-Labor) ist eine **grobe**
Wortbewegung (0,10–0,28) für TTS-Engines ohne PCM, maximal 500 ms nach einer Wortgrenze. Sie ist nie als Messung zu werten.
Ereignisse einer veralteten oder beendeten `utteranceId` werden verworfen. Alle Zeitstempel kommen aus `System.currentTimeMillis()`.

## 4. Zustände, Ausdruck, Blick
Priorität, von oben nach unten: `WAITING_FOR_APPROVAL` > `LISTENING` > `SPEAKING` > `BLOCKED` > `RESEARCHING` > `LEARNING` > `THINKING` > `IDLE`.

| AvatarState | Auslöser in der App | Ausdruck | Blick |
|---|---|---|---|
| IDLE | nichts aktiv | NEUTRAL | OWNER (0, 0) |
| LISTENING | Spracheingabe aktiv | ATTENTIVE | OWNER |
| THINKING | `busy` | FOCUSED | THINKING_AWAY (0,35, 0,25) |
| RESEARCHING | Webrecherche aktiv | CURIOUS | READING_DOWN (0, −0,4) |
| LEARNING | busy + Lernlauf | FOCUSED | READING_DOWN |
| WAITING_FOR_APPROVAL | Werkzeug-Freigabedialog offen (`showStructuredToolConfirmation`) | ATTENTIVE | OWNER |
| SPEAKING | TTS spricht | NEUTRAL | OWNER |
| BLOCKED | Orchestrator-Blocker oder Sicherheitsablehnung; endet mit der nächsten Nutzernachricht | CONCERNED | OWNER |

**Koordinaten:** `GazeTarget` liegt jeweils in −1..1, 0/0 = Kamera bzw. Besitzer. **+x = rechts aus Sicht des Betrachters, +y = oben.**
Für 3D empfohlen: x·25° Gier und y·15° Nicken. Ein 2D-Bild hat y nach unten, der Renderer muss y deshalb umkehren.
Ausdruck und Blick sind Vorgaben. Lidschlag, Kopfbewegung, Mikromimik und Idle-Animation gehören dem Renderer.

## 5. Viseme und Mund
Das Viseme-Set umfasst 15 Klassen mit OVRLipSync-Namen: `SIL PP FF TH DD KK CH SS NN RR AA E I O U`.
Android-TTS liefert nur Wortbereiche. `GermanVisemeApproximation` bildet deutsche Grapheme deshalb grob ab. Diphthonge folgen den
`lipsync-de`-Regeln von met4citizen/TalkingHead (MIT, in PR #350 gesichert): au → AA U, ei/ai → AA I, eu/äu → O.
`AvatarPoseReducer.jawOpen` übernimmt die Abbildung aus dem Avatar-Labor: Glättung 0,4, Totzone 0,025, Faktor 1,5.
Die Mundöffnung läuft über `jawOpen`, die Mundform über `viseme`. Native Renderer rufen `pose(System.currentTimeMillis())` auf: zukünftige Cues werden erst am Zeitstempel sichtbar, enden nach ihrer Dauer und sind auf 64 begrenzt. Neue oder unterbrochene Äußerungen löschen vorige Cues; ausbleibende Audioereignisse schließen den Mund nach 250 ms. `clearSpeech()` entfernt beim Pause/Detach den transienten Mundzustand.

## 6. Abgleich mit den Grafik-Assets (`/workspace/jarvis-grafik/avatar/`, nur gelesen)
Vorhanden sind 4 PNG-Standbilder mit 1024×1024, RGB und ohne Alpha (`holo-aaa_v1/v2`, `pixar-3d_v1/v2`) sowie eine Vorlage `vorlage-quantumflow.jpg` mit 1968×2184.
Alle Bilder zeigen einen frontalen, holografisch blau-violetten Kopf.
- **Was jetzt schon passt:** Ein 2D-Overlay-Renderer wie im Avatar-Labor kann Zustände, `jawOpen`, Blick-Offset und Ausdruck sofort benutzen.
- **Lücken (die Grafikseite muss sie liefern):** kein Mesh, kein Rig und keine Blendshapes. Augen und Mund liegen nicht auf eigenen Ebenen, und es gibt keinen Alphakanal.
  Für 2D fehlen außerdem Ankerkoordinaten je Bild (Augen und Mund in normierten Bildkoordinaten).
  Für 3D braucht es GLB/glTF mit 15 Visem-Morph-Targets in OVR-Benennung (alternativ ARKit-52 plus Mapping-Tabelle) und Augen-Bones für den Blick.
  Zielraten der Grafik (FPS, Thermalbudget neben Gemma) sind nicht angegeben.
- Das Format gibt die Grafikseite vor. Der Vertrag braucht dafür keine Änderung, solange sie das 15er-Visem-Set und die Blickkonvention oben übernimmt.

## 7. Was übernommen wurde und was nicht
- Aus dem Avatar-Labor (`experiment/continuum-avatar-lab-offline-20261101`, nicht verändert): Wortbereich-Fallback, Mundabbildung (Totzone, Faktor, Glättung).
  **Nicht übernommen:** die eigene Activity, der Bildimport und der zweite TTS-Listener. Das wäre die doppelte Sprachpipeline, die hier vermieden wird.
- Aus PR #350 (Quantumflow, nicht verändert): die Diphthongregeln aus TalkingHead `lipsync-de` und eine Teilmenge des `AvatarPose`-Plans
  (gaze, jawOpen, speakEnergy). **Nicht übernommen:** WebView/three-vrm/SceneView/MediaPipe-Kamera und der HUD-`AvatarWorkEvent`-Vertrag.
  Dieser gehört zum Statusband und ist nicht Teil des Avatars. Kamera-Tracking wäre nur mit eigener Opt-in-Freigabe möglich.

## 8. Nicht doppelt bauen
Folgendes nicht noch einmal bauen: TTS-Listener, Audiohüllkurve (`TtsHologramSignal`), Zustandsableitung, Visem-Abbildung, eigene Avatar-Enums, ein zweites Avatar-Interface.
Wer etwas davon braucht, konsumiert `AvatarEvent` bzw. `AvatarPose`.

Tests: `app/src/test/java/at/heinz/deviceai/avatar/AvatarDirectorTest.kt` (Director, Viseme, Fallback, PoseReducer) und
`.github/continuum/android-avatar-wiring.test.mjs` (Verdrahtung inklusive Freigabe- und Blocked-Zustand).
