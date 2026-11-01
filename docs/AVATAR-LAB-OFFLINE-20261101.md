# Device AI Continuum – lokales Avatar-Labor (Experiment)

## Stand
Privater Android-Bildavatar als kostenfreier, opt-in Test. Isolierter Screen, keine
Änderung der bisherigen Kugel, keine Cloud-Kosten, keine neuen Berechtigungen
und keine Veränderung der Haupt-Gesprächs-, Gedächtnis- oder Modellsysteme.

## Aufruf
Continuum → Mehr → Systemstatus → AVATAR-LAB · OFFLINE-SPRECHTEST.
Über Bild wählen ein Bild der hellen oder holografischen Figur auswählen.
Das Bild muss einmal auf dem Android-Gerät gespeichert sein. Gespeichert wird
nur die lokale Android-Dokument-URI, kein Upload. Mit Sprechen testen und mit Stopp abbrechen.
Blau / Hell verändert die Position der Augen-/Mundsteuerung, nicht das Foto.

## Tatsächlich animiert
- Originalbild mit kleiner seitlicher Bewegung (2D, keine 3D-Drehung).
- Augenlichter und dezente Blink-/Lidbewegung.
- Audio-amplitudenabhängige Mundöffnung durch vorhandenes TtsHologramSignal.
- Falls TTS nur Wortgrenzen, aber keine PCM-Daten liefert: begrenzte Wortbewegung.
- Stopp und Ressourcenschonung bei Pausierung / Unterbrechung.

## Noch nicht vorhanden
- Kein Mesh/GLB, kein echtes 3D, keine frei drehbare Kopfgeometrie.
- Kein Face-Rig und keine präzise Phonem- oder Viseme-Synchronisation.
- Kein Live-Anschluss jeder generierten Chatantwort an das Avatar-Labor.
- Kein fertiger signierter APK-Release oder bestandener Fold7-Gerätetest.

## Weitere Schritte
Kostenfreies GLB aus eigener oder zulässig lizenzierter Quelle importieren,
Blendshapes für Mund und Augen, Kiefersteuerung und präzise Viseme prüfen.
Eine separate Auswahl in der App kann Kugel und Avatar nebeneinander anbieten,
wobei das ursprüngliche Kugel-Markenzeichen stets erhalten bleibt.
Vor produktiver Integration: Gradle/JUnit/Lint, realer Fold7-Test,
anschließend eigene Freigabe für die bestehende Owner-Signierungsroute.

## Prüfung
node --test scripts/continuum-avatar-lab-contract.test.mjs
Der Test prüft nur statische Integrationsverträge und ersetzt keinen Android-Build.
