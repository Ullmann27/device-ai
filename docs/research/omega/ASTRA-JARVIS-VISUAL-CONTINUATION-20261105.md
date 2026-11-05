# JARVIS Visual – Fortsetzung 05.11.2026

## Einstieg und verbindliche Identitäten

Repository `Ullmann27/device-ai`, Branch
`claude/continuum-jarvis-cockpit-z7bo60`, Draft-PR #565 bleibt offen.
Produktcommit dieser Fortsetzung:
`7c3523cde9dae302a52d69818867a9abe8cf0fb7`.
Die exakten Endergebnisse stehen im
[Belegindex](../../evidence/astra-jarvis-visual-20261105/README.md).
Ein späterer reiner Belegcommit verändert diesen Produktstand nicht.

Der **einzige vorhandene Originalkopf** bleibt: GLB, Rig, Hals, Materialien,
cyanblaue Augen und 21 benannte Morphziele. GLB-SHA-256:
`c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`.
Die separate Originalkugel verwendet `ContinuumHologramRenderer` mit
`transparentBackdrop = true`, eigenen Ringphasen, Kern und Partikeln sowie
bestehenden `orbPulseLevel`-/`recallBrightness`-Signalen. Dieser Anschluss war
bereits auf der Ausgangsbasis korrekt. Er wird nicht als hier neu erfundener
oder neu ersetzter Renderer ausgegeben. `ContinuumBrainOrbRenderer` wird im
Cockpit nicht instanziiert.

## Tatsächlich verbundener erster Ablauf

Systemstatus-Kachel → Lichtspur und Glasfläche → bestehende echte Statusabfrage
→ tatsächliche Werte oder Lesefehler → Inhalts-Scrollen → Schließen → START.
Die Activity, der Avatar und die Originalkugel bleiben dabei bestehen. Der
Fensterblick verwendet einen eigenen Director-Intent. Öffnen/Schließen nutzt
`JarvisMotion` und die bereits vorhandenen Shell-Draws. Kein weiterer Timer.

Die Fläche schützt Kopf, Kugel und Dialogkarte. Endkoordinaten rasten innerhalb
nativer Pixel ein. Inhalts-Scrollen lässt sie offen; tatsächliches Scrollen des
Cockpit-Hintergrunds schließt sie sofort, damit die Schutzkoordinaten nicht
veralten. Navigation, Pause, Größenwechsel, Zurück und STOP geben sie frei.
Eine während Draw abgeschlossene Animation entfernt Kinder erst danach;
Generation und Objektidentität schützen ein inzwischen erneut geöffnetes Fenster.

Bei extrem kurzem Viewport ohne sichere 200 × 160 dp bleibt der bestehende
native Systemstatus-Dialog erreichbar. Das ist ausdrücklich kein erfolgreich
abgenommener holografischer Übergang. Telefongeometrie bei großer Schrift
reserviert zusätzlich 2 dp für Rundung bei realen Dichten.

## Avatar, Sprache und Zustimmung

Eine revisionsgebundene Intent-Verarbeitung vereinigt vorhandene Legacy-Adapter
und die neue Fensteranbindung. Kamera-`GazeObserved` bleibt ein tatsächliches
Beobachtungssignal. Fensterblicke laufen endlich, Augen führen den Kopf an;
Schließen und echte Sprach-/Unterbrechungszustände beenden sie. Denken erhält
begrenzte Bewegungen. Ein kurzes Nicken hängt an der letzten ausdrücklichen
positiven Freigabe, nicht an automatisch erfolgreichen Hintergrundcallbacks.

STOP beendet TTS, Mundanimation und Arbeitsfläche vor bestehenden frühen
Rückgaben. Späte Sprachereignisse dürfen den Mund nicht erneut öffnen. Die
bestehenden Handsfree-/TTS-Anschlüsse bleiben zuständig; keine neue Stimme,
kein erfundener Audioerfolg und keine Freisprechaktivierung ohne Besitzereinstellung.
Reduzierte Bewegung, Fokus und Lifecycle bleiben wirksam.

Die geometrische Framing-Korrektur berücksichtigt die geprüften Kopfposen und
Head-/Neck-/Rest-Mischungen des Originalmodells. Das schützt Krone und Kinn in
Host-Geometrietests. Die physische Fold7-/Filament-Abnahme bleibt offen.

## Genau drei Fachbots und C1

1. `avatar_motion`: Originalkopf, Director/Mapper, Mimik, TTS-Vertrag,
   neun Original-GLB-Replays und Bindung der acht Abhängigkeiten an den Endstand.
2. `neon_flow`: Originalkugel prüfen, Glasfläche, Lichtspur, Übergänge,
   Draw-sichere Freigabe, native Pixel und semantische Ref5-Messung.
3. `original_orb`: unabhängige Integration/QA, echte Activity-Ereignisse,
   Gegenproben, Roh-XML-Zählungen, Bild-/Pixel- und Identitätsprüfung.

Die Integrationsleitung führt gemeinsame Änderungen und Gradle-Läufe zusammen.
Keine zusätzlichen Bots. Parallel eingetroffene Branchänderungen bis `a5723ac9`
wurden erhalten. Kein breiter Import aus fremden PRs.

`AgentActivity`: 45 hinzugefügte und 16 entfernte Zeilen gegenüber `a5723ac9`.
`JarvisCockpitGrid`: byteidentisch zur Ausgangsbasis `b294fc91`.
[C1-Handoff](../../evidence/astra-jarvis-visual-20261105/C1-HANDOFF.md) mit
separatem minimalem Kontextpatch; C1 #519 wurde hier nicht verändert.

## Prüf- und Bildquellen

Ausgangsbasis: `b294fc9139f1a458fccf815bb931eb61b2f7b0e8`.
Zuerst gelesen: `CHATGPT6_CONTINUATION_JARVIS_VISUAL_20261105.md` und
`docs/evidence/claude-jarvis-visual-20261105/README.md`.
Originalkugel-Spender #563: `17ee574abc021b8f1cf1edf93e96ef484ac0ec4d`;
Randfluss-Spender #549: `ea315130b990f4a1610d8efbc3f68f32d34162da`.
Die abweichende Original-Benennung in #539 legitimiert keinen Brain-Renderer.

JDK17 / SDK36 / Gradle8.11.1 / Kotlin2.4.0; eigener Gradle-Cache, vorhandener
Build-Lock und Offline-Abhängigkeiten. Der breite Lauf auf `9468eb87` bleibt
mit **274 PASS / 1 FAIL / 0 SKIP** archiviert. Sein später Hintergrundscrollfehler
wurde gezielt reproduziert und in `7c3523cd` korrigiert. Der abschließende Lauf
prüft betroffene Fenster/Shell/TTS/Handsfree/Freigabe, alle vier Bildprofile und
den vorhandenen NewApi-Lint-Gate. Ergebnis: **39 PASS / 0 FAIL / 0 SKIP**,
Exit 0, 0 NewApi-Issues; App-Quelle während des Laufs unverändert.
Zahlen nur aus tatsächlichen Receipts; überlappende Läufe nicht
addieren. Kompilierfehler und vorzeitige Fixture-Fehler bleiben sichtbar.

- Native Robolectric-Canvas: tatsächliche Activity und Originalkugel, aber
  **bestehender 2D-Ersatzkopf**, keine Filament-Pixel.
- Blender-CPU: Original-GLB mit geprüfter Mapper-Zeitreihe, neun Posen;
  geskriptete Eingaben, kein echtes PCM und keine Android-GPU-Ausgabe.
- Physischer Fold7, echte TTS/Kamera, GPU- und Display-Frametiming: `NOT_RUN`.

Ref5 wird unverändert und ohne Strecken gemessen. Eigene native semantische
App-ROIs benutzen `tools/measure_ref5_colors.py`. Vier Rollen bleiben gegenüber
Baseline unverändert; App-Rand `#31C5FD`, Referenz `#17687F`. Lokales
Rand-/Füllungsverhältnis 9,231 gegenüber 2,756. Keine Behauptung exakter Farbtreue;
nichtmonotones Lichtprofil erlaubt keine belastbare 1/e-Glowbreite.

## Offene Besitzerabnahme und Ausbau

[Fold7-Prüffolge](../../evidence/astra-jarvis-visual-20261105/FOLD7-OWNER-ACCEPTANCE.md):
auf-/zugeklappt, große Schrift, Originalkopf/Kinn/Krone, tatsächlich hörbare
Sprache und STOP, Fensterphasen, Unterbrechung, Pause/Fokus/Rotation und
reduzierte Bewegung. Übergangszeiten sind Entwurfswerte; erst Gerätevideo und
Frame-Messung können Flüssigkeit und Audio-Reaktionszeit bestätigen.

Gmail-, PDF- und Coding-Zielszenen sind durch Systemstatus nicht fertiggestellt.
Für jede weitere Fläche zuerst den vorhandenen echten Adapter, Berechtigungen,
Daten und Ereignisse verbinden. Fehlende Zugriffe verständlich zeigen; keine
Nachrichten, Dokumentfunde, Rechtsquellen oder bestandenen Tests simulieren.
Speichern/Senden/Installieren/Veröffentlichen bleibt an die vorgesehenen realen
Freigaben gebunden. Kein automatischer Vollzug allein durch eine Geste.

Kein Main-Merge, kein APK-Release oder Installieren, keine Paket-/Versions-/
Signaturänderung, keine zweite App und kein zweiter Avatar. Keine manuellen
Actions-Auslösungen. Modell-/Memory-Experimente bleiben außerhalb dieses Deltas.
