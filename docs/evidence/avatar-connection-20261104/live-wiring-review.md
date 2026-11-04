# Avatar-Anschluss: abgeschlossene lesende Quellprüfung

Datum: 2026-11-04  
Prüfer: `avatar_live_wiring`  
Geprüfter Zielstand: `e101e393dd6c80803ac9d9ac91fccd9812c4a18d` aus PR #491

Dieses Protokoll hält die bereits abgeschlossene Quellprüfung fest. Bei seiner
Erstellung wurde keine erneute Prüfung oder Testausführung vorgenommen.

## Ergebnis

Die drei Produktionskorrekturen aus Helfer-PR #495 sind auf dem geprüften
#491-Stand bereits vorhanden. Die folgenden Git-Blobs entsprechen den finalen
Audio-, Mundblend- und Dispatch-Korrekturen:

| Datei unter `app/src/main/java/at/heinz/deviceai/` | Git-Blob auf e101e393 |
| --- | --- |
| `avatar/AvatarContract.kt` | `d9a0f6b1d72d2b9107d620526e311e41007a573d` |
| `avatar3d/AvatarMorphMapper.kt` | `546832d4c4b7248fb865154879744eeb0be9ab05` |
| `cockpit/CockpitContracts.kt` | `602ab96b32e0886f0bad4d511d8cdbc9ef07822c` |

Auch die drei zugehörigen Regressionstestdateien sind im Zielstand vorhanden.
Ein erneuter Import derselben Produktionsdiffs ist deshalb nicht erforderlich.
Im eng geprüften Sprach-/Avatar-Aufrufpfad wurde keine neue Produktionslücke
gefunden, die eine zweite Verdrahtung rechtfertigt.

## Tatsächlicher Aufrufpfad

Alle nachstehenden Zeilennummern beziehen sich auf den festen Commit e101e393.

1. **Android-TTS-Callbacks in der Activity.**
   `AgentActivity.kt:6552–6600` installiert den `UtteranceProgressListener`.
   `onStart()` ruft `avatarDirector.speechStarted(...)` auf (6558);
   `onRangeStart()` ruft `avatarDirector.wordRange(...)` auf (6580).
   `onBeginSynthesis()` und `onAudioAvailable()` liefern Format und PCM an den
   bestehenden `TtsHologramSignal`. Der vorhandene `voiceFrame` liest diese
   Hüllkurve, versorgt die Orb-Darstellung und ruft
   `avatarDirector.audioLevel(...)` auf (244–250).

2. **Director an die eine sichtbare Oberfläche gebunden.**
   `buildConversationPane()` verwendet die vorhandene `ContinuumHologramView`
   weiter beziehungsweise erzeugt sie beim ersten Aufbau und bindet sie direkt
   mit `avatarDirector.attach(it)` (991–997). Der anschließende Cockpitadapter
   erhält genau dieselbe Oberfläche: `JarvisHoloAvatar(home.hologram)` (1015).

3. **Ereignisse im vorhandenen Reducer.**
   `ContinuumHologramView.kt:180–189` verarbeitet Ereignisse mit
   `synchronized(avatarPose) { avatarPose.apply(event) }`. Der Audio-Guard und
   die Erholung nach Zukunftszeitstempeln sind damit im tatsächlichen
   View-Aufrufpfad vorhanden. Beim Zeichnen wird die Pose mit der expliziten
   Renderzeit abgefragt: `avatarPose.pose(System.currentTimeMillis())` (581).

4. **Optionaler nativer 3D-Pfad.**
   Die View übergibt die Pose an `FilamentAvatarRenderer.render(...)` (585).
   Dort ruft Zeile 152 `mapper.update(pose, seconds, motion)` auf; Zeile 165
   übergibt dessen Gewichte über `setMorphWeights(...)` an Filament. Danach
   folgen Kopf-, Augen- und Kieferrotationen. Die korrigierte Mundkompensation
   ist somit in diesem Renderer angeschlossen. Das ist ein Quellnachweis,
   kein Nachweis tatsächlich gerenderter GPU-Pixel.

## Home, Chat und Lebenszyklus

`AgentActivity.syncConversationAvatar()` (1998–2022) verschiebt dieselbe
Oberfläche zwischen Home und Chat. Nach dem Reparent bindet es den bestehenden
Director erneut an diese Oberfläche (2013). `AvatarDirector.attach()` liefert
den aktuellen Zustand und gegebenenfalls `SpeechStarted` der weiterhin aktiven
Äußerung erneut, nachdem das Detach vorübergehende Mundgeometrie gelöscht hat.

`stopVoiceAnimation()` (6542–6549) meldet `speechEnded`, setzt die aktive
Äußerung und Hüllkurve zurück und entfernt den geposteten Sprach-Frame.
`onPause()` deaktiviert die Oberfläche und beendet die Sprachanimation
(669–676). `onDestroy()` meldet den Slot ab (704) und bindet den Director an
`NoOpAvatarSink` (716).

Die View setzt bei Inaktivität, Pause beziehungsweise Detach die vorübergehende
Sprach-/Blickgeometrie zurück (`setForegroundActive`, ab 394;
`onDetachedFromWindow`, ab 442). Die bestehende externe Synchronisierung und
Zeitdomäne bleiben erhalten.

## Warum kein zusätzliches Slot-Fanout einzubauen ist

Der Director wird anfänglich mit `JarvisAvatarSlot.fanOut` konstruiert
(`AgentActivity.kt:255–256`). Der spätere UI-Aufbau ersetzt diesen Sink bewusst
durch die direkte Bindung an die tatsächliche Oberfläche.

Eine zusätzlich parallel zum Direktpfad angeschlossene Fanout-Route würde im
Home über `JarvisHoloAvatar.onAvatarEvent()` nochmals dieselbe Oberfläche
erreichen. Das wäre eine neu eingeführte doppelte Zustellung; die vorhandene
Verdrahtung zeigt diese Doppelzustellung nicht.

Fanout als alleiniger Ersatz ist ebenfalls nicht gleichwertig:
`ContinuumHomeView.applyAvatarSlot()` (223–236) dockt den Adapter nur, solange
`hologram.parent === home` gilt. Beim Abkoppeln setzt
`JarvisHoloAvatar.detach()` seine eigene `hologram`-Referenz auf null, während
die echte Oberfläche im Chat weiterbesteht. Ein alleiniger Slotpfad könnte
dann Sprachereignisse verlieren.

Der vorhandene `ContinuumAdaptiveAvatarNavigationUiTest` fordert nach
wiederholten Home↔Chat-Wechseln ausdrücklich `AvatarDirector.sink === head`.
Diese direkte Bindung sollte erhalten bleiben. Der Dispatch-Fix bleibt eine
geprüfte Komponentenkorrektur für Nutzer des Slot-Fanouts; er ist kein Beleg,
dass die normale Unterhaltung über dieses Fanout läuft.

## Ergänzter Testbeitrag

Der vorhandene
`JarvisLiveAvatarWiringUiTest.actualDirectorSpeechReachesTheVisibleHead()`
prüft bereits Director→sichtbarer Reducer→Mundöffnung→Sprachende, ruft den
Director jedoch direkt auf. Dafür wurde ein zusätzlicher, ausschließlich
testseitiger Beitrag erstellt:

- Branch: `codex/avatar-tts-connection-tests-20261104`.
- Basis: `f88c919` (Produktionsdateien identisch zu e101e393).
- Ursprünglicher lokaler Commit:
  `c1ecd199544c29e3432a6262f2b598d56da5db7d`.
- Neue Datei:
  `app/src/uiTest/java/at/heinz/deviceai/AvatarTtsLiveConnectionUiTest.kt`.
- Drei Fälle: tatsächlicher installierter Listener plus geplante PCM-Frames,
  Range und Ende; fremde beziehungsweise abgeschlossene Äußerungen; tatsächliches
  Home→Chat-Reparent mit anschließender Activity-Pause und verspäteten Callbacks.

Der erste PCM-Nachweis erfolgt ohne Range-Callback: Visem SIL, offene
Mundgeometrie und Sprechenergie über .35 müssen aus dem vorhandenen
`voiceFrame`-Pfad kommen. Erst danach wird die Range-Verbindung geprüft.
Die positive Frame-Verarbeitung läuft über die tatsächlich gepostete
Handler-Schleife. Ein manueller Frame-Aufruf wird nur im Pause-Negativfall
verwendet. Keine Director- oder View-Ereignisse werden ersatzweise direkt
eingespeist.

Robolectric 4.14.1 wurde aus `app/build.gradle.kts` verifiziert. Die verwendeten
Getter `ShadowTextToSpeech.getOnInitListener()` und
`getUtteranceProgressListener()` wurden in der offiziellen getaggten Quelle
gelesen:

https://github.com/robolectric/robolectric/blob/robolectric-4.14.1/shadows/framework/src/main/java/org/robolectric/shadows/ShadowTextToSpeech.java

Quell-Blob: `54cd10b0a7c0c71c0981c82eb7624fd7b57874d5`.

Die reale Activity installiert ihren Listener über den echten Init-Callback
der von ihr erzeugten TextToSpeech-Instanz. Lediglich der bereits angeforderte
Äußerungskontext, die Callback-IDs und die PCM-Bytes sind synthetische
Testeingaben. Die Erteilung einer Besitzerfreigabe oder die gesamte reale
TTS-Servicewarteschlange wird dadurch nicht geprüft.

## Prüfstatus und Grenzen

- Produktionsquellen bei diesem Helfer unverändert.
- `git diff --cached --check` vor dem Commit: PASS.
- Unabhängige statische Gegenprüfung durch `avatar_patch_review` für Test-Blob
  `4e3d8c77b252c68872d7d4b7037e7c7f3ea8a77c`: PASS, keine Critical-/Important-Befunde.
- Dieser Helfer hat weder Gradle noch einen eigenen Testlauf gestartet.
  Kompilierung und tatsächliche GREEN-/Mutations-Ergebnisse müssen aus Roots
  separaten, quellengebundenen Ausführungsprotokollen stammen. Dieses Dokument
  behauptet hierfür keinen Erfolg.
- Funktionale Host-Prüfung mit LEGACY-Grafik; keine hörbare TTS-Ausgabe,
  Filament-Pixel, native GPU-, Fold7-, Referenzbild- oder APK-Abnahme.
- Keine allgemeine Absicherung gegen Systemzeitrücksprünge und keine neue
  Hintergrundthread-Registrierungsgarantie.

Vorgesehener enger Testfilter:

```sh
-PcontinuumUiWidgetTests=true :app:testDebugUnitTest \
  --tests at.heinz.deviceai.AvatarTtsLiveConnectionUiTest
```

Die vorgeschlagene unabhängige Negativkontrolle entfernt ausschließlich
`avatarDirector.audioLevel(it, level)` aus `voiceFrame` in einem gesonderten
Mutationsstand. Der PCM-Test muss dadurch scheitern. Die Ausführung dieser
Kontrolle liegt ebenfalls bei Root und wird hier nicht als erfolgt dargestellt.
