# Continuum JARVIS – Belege 05.11.2026 (Claude, Branch `claude/continuum-jarvis-cockpit-z7bo60`)

Alle Bilder: **Host-Aufnahmen der echten AgentActivity** (Robolectric NATIVE Canvas, `ContinuumVisualEvidenceUiTest`).
Im Kopfbereich zeichnet der Host den 2D-Vorschaukopf, **nicht** den Filament-3D-Kopf. Kein Fold7-Gerät, keine GPU-Pixel.
TTS-Rückrufe und Besitzerposition sind über den echten `AvatarDirector` geskriptet, nicht gemessen.

| Datei | Inhalt |
|---|---|
| `compare-fold7-inner-884x760-1-start.jpg` | START-Cockpit Fold7 innen, vorher (`c4cd3b58`) / nachher |
| `compare-fold7-inner-884x760-5-tile-pressed.jpg` | Kachel „Gedächtnis“ gedrückt (echtes Touch-Ereignis) |
| `compare-reference-1536x1024-1-start.jpg` | 1536×1024-Referenzkomposition |
| `compare-fold7-cover-phone-412x915-1-start.jpg` | Fold7 außen / Smartphone |
| `after-fold7-inner-884x760-4-speaking.jpg`, `after-fold7-cover-phone-412x915-4-speaking.jpg` | Sprechen über Director-Wortbereiche |
| `after-fold7-inner-884x760-6-window-systemstatus.jpg` | Geöffnetes Systemstatus-Fenster (echte Daten des Hosts) |
| `after-fold7-inner-884x760-font130-1-start.jpg` | Fold7 innen, Schrift 130 % |
| `avatar-motion-timeline.json` | 540 Frames (30 fps): Director → Reducer → Mapper, Ruhe/Zuhören/Denken/Sprechen/STOP; echte Kopf-Fenster je Bühne |

## Gemessene Werte (Host)
- Neonanteil (Pixel mit Helligkeit > 0,55 und Sättigung > 0,45), gleiche Szene: Fold7 innen 17,47 % → 18,20 %;
  gedrückte Kachel 17,35 % → 18,09 %; 1536×1024 12,85 % → 13,39 %; Fold7 außen 18,20 % → 18,39 %. Mittlere Helligkeit unverändert (± 0,005).
- Kopf-Fenster (`AvatarFraming`, echte GLB): Fold7 innen 884×760 → Kopfmaßstab 0,875 bei Anker 0,60 (vorher 1,0 bei 0,70 mit
  12–40 dp Abschnitt rechts); 840×649 → 0,805 bei 0,70; 1536×1024 → unverändert 1,0 bei 0,70.
- Hals/Kiefer: Kantendehnung p99 im Band 0,12–0,27 m, schlimmste Pose: 129 % (nur Head) → 42 % (Neck 65 %).

## Tests (Host, root-Container)
- Gezielt: 77/77 grün; Freisprech-Reviewfall RED (`expected:<START> but was:<CHAT>`) → GREEN.
- Volle Suite auf dem Endstand: 3.290 Tests, 5 fehlgeschlagen, 17 übersprungen. Davon 3 Umgebungsfehler, auf `c4cd3b58`
  identisch reproduziert (root umgeht Schreibschutz; Shell-Locale nicht UTF-8), und 2 Fehlschläge des neuen Aufnahme-Helfers
  (Kachel außerhalb des sichtbaren Bereichs) – korrigiert, Nachlauf `ContinuumVisualEvidenceUiTest` 4/4 + `AvatarMotionEvidenceTest` 1/1 grün.

Reproduktion: `./gradlew -PcontinuumUiWidgetTests=true -PcontinuumUiEvidenceDir=<ordner> :app:testDebugUnitTest --tests 'at.heinz.deviceai.ContinuumVisualEvidenceUiTest' --tests 'at.heinz.deviceai.avatar3d.AvatarMotionEvidenceTest'`
