# Jarvis Design-System (device-ai)

Baut auf dem Continuum-Theme aus PR #477 auf (`ContinuumTheme`, `ContinuumSurfaces`, `ContinuumFonts`,
`ContinuumPainter`). Keine Duplikate: neue Dateien sind eine semantische Fassade plus Effekte plus Shell-Layout.
UI-Stack: Android-Views + plattformneutraler `ContinuumPainter` (kein Compose).

## Dateien
| Datei | Inhalt |
|---|---|
| `ui/JarvisDesign.kt` | `Colors` (Rollen: background, surface, cyan, violet, ok/warn/error, text*, neonEdge, brand), `Type` (brand, title, label, body, caption, figure, nav), `Metrics` (Radien, Abstände, Rail-/Nav-/Input-Höhen), `Glow` (OFF/SUBTLE/NORMAL/STRONG) |
| `ui/JarvisNeon.kt` | `glassCard` (Glas-Karte mit leuchtendem Cyan→Violett-Rand, `active`), `line` (Lichtbahn), `ring` (Orbit/Sockel), `halo` (Lichtpool hinter Kopf/Gehirn-Kugel), `text` (Neon-Schrift) |
| `ui/JarvisShellLayout.kt` | `formFactor(w,h)` und `create(w,h,fontScale)` → `Shell` mit Slot-Boxen in dp |

## Form-Faktoren und Slots (Stand nach Prüfbericht Jarvis Sicherheit)
- `PHONE_PORTRAIT` (ref4, < 600 dp): TOP_BAR, AVATAR (Kopf und Brain-Kugel), DIALOG (Sprechblase, liegt im AVATAR-Bereich, siehe `overlays`), INPUT („Message Continuum“), TILES 2 × 4, STATUS, BOTTOM_NAV.
- `FOLD_OPEN` (ref5): NAV_RAIL beschriftet, 124 dp. Das ist Heinz' Entscheidung vom 4.11.; mit `foldRail = Rail.ICONS` optional nur Icons (56 dp). Darauf folgen DIALOG links, AVATAR mittig, COCKPIT_SECONDARY (Module) rechts, TILES 4 × 2 (2 × 4 bei Schrift über 1,3), eine Zeile mit ROW_AGENTS, ROW_TASKS, ROW_MEMORY und ROW_MODEL sowie STATUS über die volle Breite.
- `TABLET_LANDSCAPE` (ref2): NAV_RAIL mit Labels (168 dp), DIALOG links, AVATAR **mittig und dominant**, COCKPIT_PRIMARY und COCKPIT_SECONDARY rechts gestapelt, INPUT unter dem Kopf, STATUS rechts unten.
- `Shell.tileBoxes()` liefert die einzelnen Kacheln.

## Farben (VIDEO-SPEC.md, weichen bewusst von ContinuumTheme #477 ab)
| Rolle | Jarvis | #477 ContinuumTheme |
|---|---|---|
| Hintergrund | Verlauf #050914 → #0A1128 | #000A1D |
| Cyan | #00FFFF | #3EC8FF |
| Violett | #8A2BE2 | #925CFF |
| Glas | rgba(10,15,26,0.6) mit 1 px Leuchtrand | deckendes Navy |

`ContinuumTheme.kt` bleibt unverändert.

## Glow
Glow ist eine weiche Lichtschicht mit Abfall (drei additive Striche). Es gibt keine versetzten Kopien mehr, deshalb wirkt große Schrift nicht doppelt.

## Renderer, View, Vorschau
- `JarvisShellRenderer.draw(painter, shell, content, elapsedMs, animatorScale)` zeichnet Hintergrund, Avatar-Bühne mit Idle-Schweben und alle Karten mit Muster-B-Einflug (Mitte zuerst, links und rechts). `content: Map<Slot, SlotContent>` ersetzt die Platzhalter. Platzhalter zeigen nur Beschriftungen und keine Werte.
- `JarvisShellView(context)` ist eine Android-View. Sie berechnet das Layout aus der eigenen Größe, liest `ANIMATOR_DURATION_SCALE` und bietet `content`, `playEntrance()`, `shell` (Geometrie in dp) und `foldRail`.
- `scripts/jarvis-design-preview.sh` erzeugt die JVM-Vorschau über Java2D (`tools/continuum-jvm-preview/Java2DContinuumPainter`). Die PNGs liegen unter `docs/design-screenshots/`, neben den Kopien von ref2, ref4 und ref5. Das ist eine **Annäherung mit Platzhaltern**, kein Gerät und kein Emulator.

## Einbau bei Jarvis Kern (Vorschlag)
Branch `design/jarvis-cockpit-home-20261104`: Das Cockpit ist `ContinuumHomeView` (Kind-Views pro `ContinuumHomeLayout.Slot`, Hintergrund über `AndroidContinuumPainter` in `onDraw`). Minimaler Einbau ist ein Shell-Hintergrund mit Einflug bei Befehlen:
```kotlin
// ContinuumHomeView
private val jarvisShell = JarvisShellView(context).also { addView(it, 0) }   // unterste Ebene
// onLayout: jarvisShell.layout(0, 0, r - l, b - t)
// bei Befehl (CockpitAnimationTriggers → Einflug-Ereignis):
jarvisShell.playEntrance()
// eigene Inhalte statt Platzhaltern:
jarvisShell.content = mapOf(JarvisShellLayout.Slot.STATUS to JarvisShellRenderer.SlotContent { p, box -> /* echte Werte */ })
// Kind-Views auf dieselben Slots legen: jarvisShell.shell?.get(Slot.TILES)?.let { /* dp * density */ }
```

## Wer nutzt was
- **Jarvis Avatar**: zeichnet nur in `Slot.AVATAR`; Effekte über `JarvisNeon.halo/ring/line`.
- **Jarvis Kern**: füllt `COCKPIT_*`, `TILES`, `STATUS`; Karten über `JarvisNeon.glassCard`, Text über `JarvisDesign.Type`.
- Farben nur über `JarvisDesign.Colors`, nie Hex-Werte direkt. Statuswerte nur aus echten Daten.
- `Glow.OFF` für Energiesparmodus / reduzierte Bewegung.

## Bewegung (`ui/JarvisMotion.kt`)
Pflicht-Abläufe laut Auftrag: Einfliegen bei Befehlen, Gmail-Konto-Karte, PDF-Karte, Ablauf-Schritte live.
Die Werte folgen `/workspace/jarvis-design-referenz/VIDEO-SPEC.md`.

Belegt:
- Panels (B): von links und rechts, Skalierung 80 → 100 %, 0,6 s cubic ease-out, Mitte zuerst, Staffelung 80 ms (Spec-Bereich 0,05–0,1 s).
- Szenenwechsel Z (A): 0,8 s exponential ease-in. Die alte UI wächst über 300 % und blendet aus, die neue wächst von 0 auf 100 %.
- Laser (D): 0,3 s.
- Gmail-Umschlag (F): 0,5 s aus der Tiefe, danach 0,3 s Klappe.
- Pillen (G): von den Rändern, ohne Fade, starkes Abbremsen (easeOutQuint).
- Widgets: Skalierung 50 → 100 %.
- Idle (C): 4 s Sinus, ±2,5 dp.
- Blinzeln: 0,15 s.

GESCHÄTZT:
- Einflug-Distanzen (64, 160 und 96 dp).
- Dauer der Pillen (450 ms) und der Widgets (600 ms).
- Rand-Aufleuchten (300 ms).
- PDF-Dokument und Ablauf-Schritt.

- `FlyIn(direction, distanceDp, fromScale, fromAlpha, durationMs, edgeFlashMs, easing)`: Einflug-Spezifikation.
- `frame(spec, elapsedMs, animatorScale)` → `Frame(dx, dy, scale, alpha, glow, done)`. Der Rand-Glow steigt im Flug an, leuchtet nach der Landung auf und klingt dann ab.
- `staggered(spec, count, elapsedMs)`, `staggerStart`, `groupDurationMs`: gestaffeltes Einfliegen mehrerer Karten.
- `drawCard(painter, frame, l, t, r, b) { inhalt }`: zeichnet eine `JarvisNeon.glassCard` im Frame-Zustand.
- Presets: `panelLeft`/`panelRight`, `pill(dir)`, `widget(dir)`, `envelope`, `document`, `step`. Hilfen: `centerFirstRanks`, `panelSpecFor`, `sceneZ`, `laser`, `envelopeFlap`, `idleOffset`.
- Easing: `linear`, `easeOutCubic`, `easeInOutCubic`, `easeOutQuint`, `easeInExpo`, `easeInOutSine`, `easeOutBack`.
- Bekannte Grenze: Die Transparenz wirkt auf den Kartenrand, nicht auf fremden Inhalt, weil der Painter keinen Ebenen-Alphawert hat.
- Reduzierte Bewegung: `animatorScale` = `Settings.Global.ANIMATOR_DURATION_SCALE` übergeben. Bei 0 kommt sofort der Endzustand `REST`. Andere Werte strecken die Zeiten.
- Treiber: Der Aufrufer (View/`ValueAnimator`/`Choreographer`) liefert `elapsedMs` und invalidiert. Die API hält keinen eigenen Zustand.

## Review 1 (Kreativdirektor, 4.11.)
- **Glas:**
  - Panels #0A1128 @50 %, 1 px Cyan-Rand @40 %, Radius 14 dp.
  - `JarvisGlassRenderer` (nur Android) ist das `GlassBackend` von `JarvisShellRenderer`:
    - **API 31+ (Hardware-Canvas):** Backdrop-Blur über `RenderEffect.createBlurEffect` (24 dp, geschätzt) auf einer RenderNode-Kopie des Hintergrunds, je Panel per Clip.
    - **API 33+:** Hintergrund als **ein** AGSL-`RuntimeShader` (Verlauf, unscharfes Cyan-Raster, Scanlines, Violett-Schleier) plus Bokeh; Rand-Glow als AGSL-SDF-Shader.
    - **minSdk 26–30 und Software-Canvas:** portabler Fallback (halbtransparentes Glas, additive Rand-Striche).
  - Die JVM-Vorschau kompiliert ohne Android-Klassen und zeigt **nur den Fallback**, also keinen Blur und kein AGSL.
- **Hintergrund:** Verlauf #050814 → #0A1128, Raster 48 dp, Bokeh-Staub (deterministisch).
- **Frame-Zeit:** Mit `JarvisShellView.frameLogging = true` (nur Debug) loggt die View alle 120 Frames unter dem Log-Tag `JarvisFrames` den Mittelwert und das Maximum des Frame-Abstands sowie die onDraw-Dauer. **Die Messung auf dem Fold7 steht noch aus.**
- **Fold7 offen:** 3 Spalten 20/55/25 – beschriftete Navigation, Avatar mit Dialog und Kacheln 4 × 2, rechts System-Status, Agenten, Ablauf, Gedächtnis und Modell. Darunter eine Statusleiste über die volle Breite.
- **Kacheln:**
  - `JarvisTile(target, label, subtitle, icon, displayText?)` ist ein **reines Darstellungsmodell** und keine Datenschnittstelle.
  - `JarvisTiles.reference` enthält die 8 Kacheln aus ref5. Navigation: `JarvisTiles.railNav` (Leiste aus ref5) und `bottomNav` (ref4).
  - In schmalen Kacheln steht das Icon über dem Label.
  - Die Vorschau zeigt nur Labels und Untertitel, keine Werte.
