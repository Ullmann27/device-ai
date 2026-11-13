# Code56 Kamerafahrt – Host-Vorschau (KEIN Filament-, KEIN Gerätebeleg)

Erzeugt mit `tools/cockpit-workstation/flight_preview.py` (Blender 4.0.2, Cycles CPU, 480×270, 24 Samples) aus dem
**ausgelieferten** `app/src/main/assets/cinema3d/cockpit-workstation.glb`. Kamera und Monitordrehung spiegeln
`CockpitCameraFlight.kt` (gleiche Konstanten). Avatar, Originalkugel und Live-Pixel sind nicht Teil dieses Assets und
fehlen daher. Evidenzklasse: `HOST_BLENDER_CYCLES_ILLUSTRATION`.

| Datei | Fortschritt | Inhalt |
|---|---|---|
| `host-flight-0-rear.jpg` | 0,00 | Monitor um −100° gedreht, Kamera 60° vorn-seitlich → Rückseite |
| `host-flight-1-rear-side.jpg` | 0,30 | Rückseite/Seite |
| `host-flight-2-side.jpg` | 0,50 | Seite, Ständer, Seitenmonitore, Tastatur |
| `host-flight-3-front-approach.jpg` | 0,75 | schräg vorn |
| `host-flight-4-rest-front.jpg` | 1,00 | exakte Ruhekamera |
| `host-flight-REJECTED-first-orbit-through-walls.jpg` | – | verworfener erster Entwurf: Orbit hinter den Monitor läuft durch die massiven Bunkerwände |

Erkenntnisse aus der Vorschau: (1) Der Bunker liegt direkt hinter dem Monitor und ist kaum breiter (±0,85 vs. ±0,51
Monitorbreiten); die Wände sind Volumen → die Kamera bleibt vor der Raumöffnung, der Monitor dreht sich.
(2) Die Rückseite des Monitors ist im Asset eine strukturlose dunkle Platte – Lüftungsschlitze, Anschlüsse und
VESA-Halter fehlen und müssen als echte Meshes ergänzt werden.
