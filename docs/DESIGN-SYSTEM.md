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

## Form-Faktoren und Slots
- `PHONE_PORTRAIT` (ref4, < 600 dp): TOP_BAR, AVATAR (Kopf + Brain-Kugel), TILES (3 Spalten, 2 bei großer Schrift), STATUS, BOTTOM_NAV.
- `FOLD_OPEN` (ref5, ≥ 600 dp, Seitenverhältnis < 1.35): NAV_RAIL (56 dp, nur Icons), COCKPIT_PRIMARY (Dialog links), AVATAR (Mitte), COCKPIT_SECONDARY (Kern-Module rechts), INPUT, STATUS (Leiste unten).
- `TABLET_LANDSCAPE` (ref2): NAV_RAIL (168 dp mit Labels), AVATAR links, COCKPIT_PRIMARY / COCKPIT_SECONDARY rechts gestapelt, INPUT unter dem Avatar, STATUS rechts unten.

## Wer nutzt was
- **Jarvis Avatar**: zeichnet nur in `Slot.AVATAR`; Effekte über `JarvisNeon.halo/ring/line`.
- **Jarvis Kern**: füllt `COCKPIT_*`, `TILES`, `STATUS`; Karten über `JarvisNeon.glassCard`, Text über `JarvisDesign.Type`.
- Farben nur über `JarvisDesign.Colors`, nie Hex-Werte direkt. Statuswerte nur aus echten Daten.
- `Glow.OFF` für Energiesparmodus / reduzierte Bewegung.

## Bewegung (`ui/JarvisMotion.kt`)
Pflicht-Abläufe laut Auftrag: Einfliegen bei Befehlen, Gmail-Konto-Karte, PDF-Karte, Ablauf-Schritte live.
**Alle Zeiten/Kurven sind GESCHÄTZT**, weil die Videos (Google Drive „Quantum Backup/Videos“, letzte 4) noch nicht ausgewertet sind. Anpassen nur über `Durations` und `Presets`.

- `FlyIn(direction, distanceDp, fromScale, fromAlpha, durationMs, edgeFlashMs, easing)`: Einflug-Spezifikation.
- `frame(spec, elapsedMs, animatorScale)` → `Frame(dx, dy, scale, alpha, glow, done)`. Der Rand-Glow steigt im Flug an, leuchtet nach der Landung auf und klingt dann ab.
- `staggered(spec, count, elapsedMs)`, `staggerStart`, `groupDurationMs`: gestaffeltes Einfliegen mehrerer Karten.
- `drawCard(painter, frame, l, t, r, b) { inhalt }`: zeichnet eine `JarvisNeon.glassCard` im Frame-Zustand.
- Presets: `command` (von unten, 48 dp, 520 ms), `account` (Gmail, von rechts, 120 dp, 640 ms), `document` (PDF, aus der Tiefe, Skalierung 0.6, 700 ms), `step` (von links, 420 ms). Staffelung 70 ms, Rand-Aufleuchten 360 ms.
- Easing: `linear`, `easeOutCubic`, `easeInOutCubic`, `easeOutBack` (etwa 5 % Überschwingen).
- Reduzierte Bewegung: `animatorScale` = `Settings.Global.ANIMATOR_DURATION_SCALE` übergeben. Bei 0 kommt sofort der Endzustand `REST`. Andere Werte strecken die Zeiten.
- Treiber: Der Aufrufer (View/`ValueAnimator`/`Choreographer`) liefert `elapsedMs` und invalidiert. Die API hält keinen eigenen Zustand.
