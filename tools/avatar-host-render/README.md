# Avatar-Host-Render (nur Nachweis, kein Produktcode)

Rendert die Original-GLB mit einer echten Mapper-Zeitreihe in Chromium/SwiftShader. Ergebnis ist ein **Host-Rendering**
und muss so beschriftet werden; es ersetzt keine Filament-/Fold7-Abnahme.

1. Zeitreihe erzeugen:
   `./gradlew -PcontinuumUiEvidenceDir=<ordner> :app:testDebugUnitTest --tests 'at.heinz.deviceai.avatar3d.AvatarMotionEvidenceTest'`
   → `<ordner>/avatar-motion-timeline.json`
2. In einen Arbeitsordner (außerhalb des Repos) kopieren: diese drei Dateien, `timeline.json` (= Zeitreihe),
   `continuum-avatar.glb` (aus `app/src/main/assets/avatar3d/`), dann `npm i three@0.170.0`.
3. `node driver.cjs <ausgabeordner> 626 764 '<fenster-json>'` – Fenster `{left,right,halfHeight,cameraCenterY}` aus `AvatarFraming`.

Optionales letztes Argument: Frame-Schrittweite 1..30 (z.B. 3 fuer ca. 10 fps
aus der 30-fps-Zeitreihe). Der Server bindet nur 127.0.0.1.
Ein vorhandener Browser kann mit `CONTINUUM_CHROMIUM=/pfad/zu/chromium`
anstelle des Playwright-Downloads genutzt werden.
`AvatarMotionEvidenceTest` schreibt auch `avatar-work-timeline.json`:
anhaltende Arbeit, gesprochene Unterbrechung, Rueckkehr und STOP.
Auch diese Eingaben sind geskriptete Tests, keine echten Auftragsresultate.

Playwright/Chromium sind im Cloud-Container vorinstalliert; es wird kein Dienst aufgerufen.
