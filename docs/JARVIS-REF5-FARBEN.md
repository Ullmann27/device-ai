# Jarvis-Farben und Typo aus ref5 (gemessen)

Quelle: `/workspace/jarvis-design-referenz/ref5-fold7-offen.png`, **1448 x 1086 px**, RGB (Original nicht im Git).
Messung: `python3 tools/measure_ref5_colors.py [pfad]`. Koordinaten = Box `(x0, y0, x1, y1)` in Original-Pixeln.

Methoden: `median` (flache Fläche), `dark` (Median der dunkelsten 30 %, Fläche hinter Text/Icons),
`peak` (Median der hellsten 10 %, dünne Striche), `violet` (nur violette Pixel, gesättigtste 10 %).

## Farbtabelle

| Rolle | Wert | Methode | Box |
|---|---|---|---|
| `bg.top` | `#000C16` | median | `(268, 270, 278, 290)` |
| `bg.mid` | `#010D18` | median | `(268, 470, 278, 490)` |
| `bg.low` | `#010C17` | median | `(268, 590, 278, 610)` |
| `bg.violetAmbient` | `#0D2250` | median | `(268, 875, 278, 885)` |
| `glass.fill` | `#011A2A` | dark | `(585, 830, 660, 880)` |
| `glass.rim` | `#329EC0` | peak | `(330, 748, 520, 756)` |
| `cyan.icon` | `#78F6FB` | peak | `(110, 210, 140, 235)` |
| `cyan.text` | `#2FF9FE` | peak | `(310, 395, 440, 410)` |
| `violet.icon` | `#916DC9` | violet | `(320, 668, 350, 700)` |
| `violet.hair` | `#914CEF` | violet | `(540, 180, 640, 240)` |
| `violet.pillRim` | `#9E5BD5` | violet | `(880, 105, 915, 140)` |
| `text.primary` | `#FFFFFF` | peak | `(375, 577, 415, 595)` |
| `text.secondary` | `#D0E1EA` | peak | `(380, 603, 480, 625)` |
| `tile.fill` | `#051B30` | dark | `(440, 590, 500, 640)` |
| `tile.rim` | `#17687F` | peak | `(360, 566, 440, 572)` |
| `tile.icon` | `#C9FFFF` | peak | `(315, 580, 350, 610)` |
| `button.fill.active` | `#012C3C` | dark | `(180, 212, 255, 235)` |
| `button.rim.active` | `#32D6ED` | peak | `(120, 198, 250, 204)` |
| `button.fill` | `#041521` | dark | `(180, 273, 255, 295)` |
| `pill.fill` | `#07204D` | dark | `(830, 115, 870, 132)` |
| `pill.rim` | `#0175AF` | peak | `(775, 108, 870, 113)` |
| `status.ok` | `#01FC7B` | peak | `(955, 115, 970, 132)` |
| `status.okCheck` | `#86E9CD` | peak | `(1295, 245, 1315, 262)` |
| `glow.core` | `#ABF8FF` | peak | `(92, 205, 100, 240)` |
| `glow.halo` | `#012539` | median | `(82, 205, 90, 240)` |

## Ehrliche Grenzen

- ref5 ist ein KI-gerendertes, JPEG-artig komprimiertes Bild. Text, 1-px-Ränder und Icons sind antialiased:
  `peak`-Werte sind die hellsten Kerne, die echte Strichfarbe liegt dazwischen (Rand wirkt im Bild dunkler, z. B. `tile.rim`).
- `glass.fill`/`tile.fill` sind Mischwerte über dem Hintergrund. Im Code als Farbe @80 % hinterlegt, so berechnet,
  dass sie über `bg.top` genau den Messwert ergeben (Alpha 80 % ist eine Festlegung, nicht messbar).
- Hintergrund: Zwischen den Panels ist kein vertikaler Verlauf messbar (#000C16 → #010C17). Unten wirkt violettes
  Umgebungslicht (`bg.violetAmbient` #0D2250).
- Warn-/Fehlerfarben kommen in ref5 nicht vor, sie bleiben bei ContinuumTheme.

## Typo-Skala (Fold7 offen)

Umrechnung: Fold7 offen 2184 px bei 420 dpi = 832 dp, ref5-Bildschirm 1296 px breit → **1 px = 0,642 dp**.
Schriftgröße = Versalhöhe / 0,711 (Roboto). Achtung: ref5 ist breiter als das echte Fold7 (Seitenverhältnis 1,47 statt 1,11).

| Token | Beispiel | Versalhöhe px | sp |
|---|---|---|---|
| `brand` (Montserrat 600) | Device AI Continuum | 18 | 16 |
| `title` (Montserrat 500) | Continuum (Kopf) | 23 inkl. Glow | 20 |
| `body` | Good afternoon. | 15 | 13 |
| `heading` | Active Agents (4) | 14 inkl. Klammer | 11 |
| `tileLabel` | Chat | 12 | 11 |
| `nav` | Home | 12 | 11 |
| `pill` | JARVIS Mode | 11 | 10 |
| `subtitle` | Natural, multi-turn | 10, Zeilenabstand 20 px = 13 dp | 9 |
| `caption` | Android AI Control Center | 10 | 9 |
| `status` | System Status: Optimal | 9 | 8 |

Schrift: ref5 nutzt eine humanistische Sans (Segoe-/Noto-ähnlich). Gebündelt sind nur Roboto und Montserrat
(ContinuumFonts); Roboto ist am nächsten, Abweichung: etwas schmalere Laufweite, andere `a`/`g`-Formen.

## Komponenten (px → dp)

| Stil | Maße ref5 | dp | Radius | Rand | Füllung / Rand |
|---|---|---|---|---|---|
| Kachel | 258 x 79, Icon 39, Einzug 25, Icon→Text 26 | 166 x 51, Icon 24, 16, 16 | 12 px → 8 | 1 | `surfaceLow` / `rim`, Glow SUBTLE |
| Button (Nav) | 168 x 50, Abstand 9 | 108 x 32, Abstand 6 | 12 px → 8 | 1 | `navSurface` / `rim` |
| Button aktiv | wie oben, Glow-Hof ca. 8 px | Glow NORMAL | 8 | 1 | `navSurfaceActive` / `navRimActive`, Kern `glowCore` |
| Pille | 151 x 35, Icon 14 | Höhe 22, Icon 9 | rund | 1 | `pillSurface` / `cyanDeep` (Varianten `violetRim`, `ok`) |
