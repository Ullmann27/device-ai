# Nachbesserung „Kopf frei, kräftigere Farben“ (05.11.2026)

Host-Vorschau mit Produktionszeichencode (Java2D) und Komposit eines three.js-Renderings des gepinnten GLB
`c8b88385…` mit der orthografischen Büstenrahmung von `AvatarGlbContract.Focus` (Azimut −22°). Leerer Datenzustand,
kein Filament, kein Gerät.

Farbmessung gegen 59509 in gleichen Bereichen (`colorstats`, HSV): Helligkeit gesamt 0,253 → 0,339 (Ref. 0,351),
Farbigkeit S·V 0,177 → 0,238 (Ref. 0,258), Neonanteil 6,1 % → 9,1 % (Ref. 12,0 %).

Geometrie: Sprachfeld 256–540, freie Kopfbühne 548–800 (Referenzbreite 1536); Überdeckung Kopf/Sprachfeld = 0 in allen
geprüften Referenzfenstern (Test `theDialogueNeverCoversTheHeadInAnyReferenceWindow`).

Quellstand: `3a21a453309b9c645bad80a8ca72917585fbb78f`. Host-UI-Paket 178/178 PASSED, darunter
`theDialogueNeverCoversTheHeadInAnyReferenceWindow`; volle Suite ohne neue Fehler gegenüber der Basis (siehe
`host-tests/host-unit-baseline-vs-candidate.json`). Android/Gerät: NOT_RUN. Rücknahme: `HEAD_LEFT = 250f`,
`DIALOG_RIGHT = 640f`.
