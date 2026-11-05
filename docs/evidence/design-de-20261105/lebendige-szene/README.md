# Lebendige 59509-Szene (05.11.2026, WIP — nicht abgenommen)

Art der Belege: **Java2D-Host-Vorschau** mit Produktionszeichencode (Energieflüsse, Kopf-Aura, Gehirnkugel, Cockpit)
und Host-Komposit des gepinnten GLB `c8b88385…` (three.js/SwiftShader, gleiche verankerte Orthorahmung wie
`FilamentAvatarRenderer`: Anker 0,70, Ausschnitt 0,92, Azimut −22°; Posen aus echten Morph-Zielen). Kein Filament,
kein Emulator, kein Fold7, leerer Datenzustand.

- `cockpit-animation-host-6s.mp4`: 72 Bilder, 12 fps, 1280×854. Zeitachse = dieselbe Sekundenzahl, die
  `ContinuumHologramView` als `avatarSeconds` verwendet. Blinzeln bei 1,4 s und 4,9 s, Sprechlaute 2,4–4,4 s.
- Farbmessung gegen 59509 (gleiche Bereiche): Helligkeit 0,346 (Ref. 0,351), Farbigkeit 0,241 (0,258),
  Neonanteil 9,6 % (12,0 %). Ausgang dieses Tages: 0,253 / 0,177 / 6,1 %.
- Zeichenkosten pro Bild (Host-Zählung, `JarvisLivingSceneTest`, 5/5 PASSED auf `c49248dcbffb4d687ccb5a780bef3f20c21af6f2`,
  UI-Paket 183/183): Energieflüsse ≤ 64, Kopf-Aura ≤ 80 Primitive, zeitunabhängig. GPU-Bildzeiten auf dem Fold7: NOT_RUN.

## Zähne ausgeblendet (Besitzerentscheidung)

`FilamentAvatarRenderer` entfernt die 16 Knoten `ToothUpper0..7`/`ToothLower0..7` nach dem Laden aus der Szene. Das GLB
(`c8b88385…`), NOTICE, Pin, Rig und die 21 Morph-Ziele bleiben bytegleich. `mund-vorher-nachher-ohne-zaehne.png` zeigt
dieselbe AA-Pose als Host-Rendering. Offen: heller Keil der inneren Lippenfläche am linken Mundwinkel.
