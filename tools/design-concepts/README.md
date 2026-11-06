# Designkonzept-Generator

`designkonzept.html?p=1..5` setzt eine Phase zusammen; `node shoot-design.cjs <ordner> 1,2,3,4,5 2` nimmt sie mit Playwright
(Gerätefaktor 2) als PNG auf. Erwartet im selben Ordner: `heads/h0000.png … h0004.png` (fünf Posen der Original-GLB aus
`tools/avatar-host-render`), `orb/orb-{ready,thinking,research,speaking}.png` (Original-`ContinuumHologramRenderer`,
transparenter Hintergrund) und `fonts/` (Kopie aus `app/src/main/assets/fonts`). Ergebnisse sind Designkonzepte und müssen so
gekennzeichnet bleiben.
