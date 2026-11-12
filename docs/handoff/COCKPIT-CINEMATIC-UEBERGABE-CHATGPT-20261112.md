# Cockpit – filmische Qualität: Übergabe an ChatGPT (12.11.2026)

**Branch:** `claude/cockpit-cinematic-quality-20261112` · **Basis:** PR #804-Stand `422711b6d`
(enthält #801 Fünf-Ringe-Render und #803 Live-Monitor; #799 ist die Integrationslinie).
Kein Merge nach `main`, keine Signierung.

## Tatsächlich implementiert (in der bestehenden Filament-Bühne, kein zweites System)
1. **Studio-Spiegelungen** – `avatar3d/CockpitStudioEnvironment.kt`: prozedurales dunkles Studio
   (Cyan-Softbox vorne links, Violett rechts, warmes Führungslicht oben vorn, kühles Kantenlicht hinter Jarvis),
   **einmalig** in eine vorgefilterte 64-px-Cubemap gebacken (`Texture.generatePrefilterMipmap`) und als
   `IndirectLight.reflections` gesetzt (`FilamentCockpitStage.studioCubemap`). Bisher gab es nur konstantes
   Umgebungslicht → Monitorglas, Rahmen, Ständer und Tisch hatten nichts zu spiegeln. Kein Asset-Download,
   keine Arbeit pro Bild, kein zusätzlicher Renderdurchgang. Schlägt die Cubemap fehl, bleibt das alte Licht.
2. **Monitorlicht** – `CockpitMonitorGlow`: Punktlicht vor dem zentralen Monitor, das Tisch/Tastatur beleuchtet,
   **nur wenn echte Livebilder sichtbar sind** (`pixelsVisible`); lineare Rampe 0,35 s auf / 0,60 s ab;
   Upload nur bei Wertänderung (48 Stufen) → Leerlauf ohne Mehrkosten. Position folgt der echten Monitor-Matrix.
3. Aufräumen in `close()` (Textur, Licht-Entity).

## Tests (HOST)
`:app:testDebugUnitTest --tests 'at.heinz.deviceai.avatar3d.*'` (mit `-PcontinuumUiWidgetTests=true`):
**39 Klassen / 168 Tests / 0 Fehler**, darunter neu `CockpitStudioEnvironmentTest` (4 Fälle: dunkler Raum,
Lichtpositionen/-farben, Cubemap-Konvention + Determinismus, Rampe/Upload-Sparsamkeit).
Der Code kompiliert gegen Filament 1.56.0.

## NICHT belegt / NOT_RUN
- **Kein Renderbild dieses Standes.** Blender fehlte auf dem Host (Download per Sicherheitsprüfung abgelehnt,
  Ubuntu-Paket 4.0 installiert, aber Vorschau-Werkzeug verlangt 4.3+ und wurde nicht ausgeführt).
  `render_host.py` kennt die Studio-Umgebung noch nicht.
- **Filament/GPU/Fold7: NOT_RUN.** Helligkeit der Spiegelungen (Intensität 20 000, Spitzenwert 1,25) und des
  Monitorlichts (max. 12 000 lm) sind **ungeprüfte Startwerte** – auf dem Gerät abstimmen; Überstrahlen möglich.
- Wärme/Bildrate: nicht gemessen.

## Offene Aufgaben (Reihenfolge)
1. Fold7: Spiegelungen/Monitorlicht ansehen, Werte abstimmen (`CockpitStudioEnvironment.PEAK`,
   `CockpitMonitorGlow.MAX_LUMENS`); prüfen, dass App-Pixel (unlit) unverändert scharf bleiben.
2. Host-Vorschau: Studio-Umgebung aus Kotlin exportieren und in `render_host.py` als Weltlicht nutzen,
   dann neue Bilder gegen `docs/evidence/cockpit-original-five-rings-20261112/` rendern.
3. Avatar-Lebendigkeit (nicht begonnen): Sakkaden/Blick, Atmung, Kopf-/Schulterbewegung über
   `AvatarMorphMapper`/`AvatarIdleMotion`/`applyWorkBody` an `CockpitWorkEvent` binden.
4. Materialien in `tools/cockpit-workstation/build_scene.py` (nicht begonnen): Glas mit Clearcoat/geringer Rauheit,
   Metallkanten, damit die neue Cubemap sichtbar wirkt; Seitenmonitore gleiches Glas.
5. Kugel (nicht begonnen): Kernlicht-Puls an Arbeitszustand; Buttons (nicht begonnen): Aktivzustand/Reflexe prüfen.
6. Bloom ist bei sichtbaren Livepixeln aus (Lesbarkeit) – prüfen, ob schwacher Bloom nur außerhalb des Monitors geht.
