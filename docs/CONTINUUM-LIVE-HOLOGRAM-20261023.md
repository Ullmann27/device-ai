# READY-Hologramm: begrenzter Bewegungssprint

Branch `astra/continuum-live-hologram-20261023`, Basis des bestätigten Code17-APK:
`6706da14f6d713bf34799a5cac5b64cf0d4e638e`. PR-Ziel: derselbe signierte
Integrationszweig `astra/render-mentor-apk-builder-20261019` (zusätzlicher
HEAD `9fb4361` enthält ausschließlich den Releasebericht). Keine APK gebaut/installiert.

## Befund und Änderung

READY zeichnet bereits kontinuierlich mit zeitabhängiger Phase. `setState`
setzt diese nicht zurück; Sichtbarkeit, Animator-Einstellung und Stromsparmodus
stoppen die Schleife. Der vorhandene `ContinuumHologramPlexus` rotiert bereits
kohärent in 3D. Das bleibt erhalten, einschließlich seiner festen Topologie.
Die äußeren Ringe waren dagegen 2D-Ellipsen mit lediglich zwei Grad Wackeln;
Front/Back waren feste Winkelhälften, keine bewegte räumliche Projektion.

`ContinuumOrbitalRing` projiziert jetzt fünf echte Kreisbahnen mit separater
Achsenbewegung und Perspektive. Segmente werden genau an z=0 getrennt und vor/
hinter der Glaskugel gezeichnet. Feste 72 Segmente pro Ring, wiederverwendete
Puffer, vier Lichtschichten als native Canvas.drawLines-Batches. Keine
Vollbild-Blur-Pässe oder neue Assets. Sehr kleines Schweben (±2,4 % Radius)
bewegt nur das Hologramm; Sockel, Markenlinie, Layout und Touch-Zonen bleiben
stehen. Kontaktlicht reagiert auf die Höhe, Strahlen bleiben am Sockel verankert.
Das View verwendet ein wiederverwendetes Frame-Objekt statt einer Allokation
pro Bild. Echte Zustands-/PCM-Anschlüsse und sämtliche Bewegungssperren bleiben.

Experimentbezug: ausschließlich Darstellung der bestehenden EXP-04/05/11,
Identitäts-/Rollbackgrenzen EXP-12 bleiben erhalten. Kein Lernfortschritt,
CPU-Wert, Audiopegel, Gewichtsformat oder KI-Modul wird geändert.

## Prüfung und Wiederaufnahme

- Gezielte JVM-Suite: Plexus, neue Orbit-Geometrie, bestehendes Layout,
  WaveChart-Marker, Dashboard-Mathematik, echte Java2D-Screen-Renderings.
- Neue Tests: deterministische Projektion, zwei geschlossene Tiefenhälften,
  wiederverwendete Puffer, beschränkte Segmentzahl, sichtbare READY-Bewegung
  nach vier Sekunden, unveränderte Nachbarschaften und null synthetisches Audio.
- Node UI/Fold/Navigation/Lernwellen-Verträge werden weiter geprüft. Die
  bisherige Assertion auf eine Frame-Neuallokation prüft nun wiederverwendeten
  Frame plus alle echten Phasen-/Energie-/Audio-Zuweisungen.
- Android: `gradle -PdeviceAiFeaturePreview=true :app:compileDebugKotlin
  :app:testDebugUnitTest --tests 'at.heinz.deviceai.ui.*'
  --tests 'at.heinz.deviceai.HologramAudioTest'
  --tests 'at.heinz.deviceai.TtsHologramSignalTest' :app:lintDebug`.
- Reale Rendersequenz: `ContinuumMotionPreview` im vorhandenen JVM-Preview;
  25 READY-Bilder je Phone/Fold, 0–8 Sekunden, Abtastung 3 Hz. Dies sind
  Produktions-Rendererbilder auf Java2D, **keine gemessenen Geräte-FPS**.

Offen: neue Bewegung am Samsung, Background/Resume, erneuter Appstart,
Fold-Wechsel, reduzierte Bewegung/Stromsparmodus und Framezeit-/GPU-Abnahme.
Die vom Besitzer bestätigte Fold-Abnahme gilt für Code17, nicht automatisch
für diese Änderung. Spätere kontrollierte gemeinsame APK-Integration benötigt
denselben ursprünglichen Signierer, Paket `at.heinz.deviceai.mentorpaired`
und Versionscode >17. Kein autonomer Merge oder Installationsschritt.

Rollback: diesen UI-Commit revertieren; keine Migration, keine Lernstände betroffen.
Fortsetzung: zuerst `git status --short` und `git rev-parse HEAD`, anschließend
obigen gezielten Gradle-Befehl. Tests/Sequenzbelege unter
`docs/evidence/live-hologram-20261023/` beachten; offene Gerätenachweise nicht
als erledigt deklarieren. Nutzungskontingent hier nicht verifiziert auslesbar.
