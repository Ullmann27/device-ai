# Avatar-Host-Render (nur Nachweis, kein Produktcode)

Rendert die Original-GLB mit einer echten Mapper-Zeitreihe in Chromium/SwiftShader. Ergebnis ist ein **Host-Rendering**
und muss so beschriftet werden; es ersetzt keine Filament-/Fold7-Abnahme.

1. Zeitreihe erzeugen:
   `./gradlew -PcontinuumUiEvidenceDir=<ordner> :app:testDebugUnitTest --tests 'at.heinz.deviceai.avatar3d.AvatarMotionEvidenceTest'`
   → `<ordner>/avatar-motion-timeline.json`
2. In einen Arbeitsordner (außerhalb des Repos) kopieren: diese drei Dateien, `timeline.json` (= Zeitreihe),
   `continuum-avatar.glb` (aus `app/src/main/assets/avatar3d/`), dann `npm i three@0.170.0`.
3. `node driver.cjs <ausgabeordner> 626 764 '<fenster-json>'` – Fenster `{left,right,halfHeight,cameraCenterY}` aus `AvatarFraming`.

Playwright/Chromium sind im Cloud-Container vorinstalliert; es wird kein Dienst aufgerufen.
