# FABLE 5.1 · Code 51 · Hollywood‑FUI Konstrukt v2 – Handoff (2026‑11‑08)

Branch `fable/code51-fui-cinematic-construct-20261108` · Basis `473f7972` (Code50‑Source, PR #630) · Ziel‑PR gegen
`chatgpt/code50-qa-staging-20261108` · Aufträge #642 / #643 / #614 / #638 · Status **KOTLIN_UNIT_TESTS_CHECKED, nicht
DEVICE_VERIFIED**.

## 1 · Werkzeug‑ und Referenz‑Audit (ehrlich)

| Werkzeug | Verfügbar | Einsatz |
|---|---|---|
| JDK 21 + kotlinc 2.4 + Jar‑Satz `/opt/continuum-deps` | ja | Kompilieren von main/test, 3.49x JUnit‑Tests (`scripts/android-kotlin-unit-check.sh`) |
| Java2D‑Painter‑Vorschau (`Java2DContinuumPainter`) | ja | Produktiver Zeichenpfad des Konstrukts rendern → `docs/evidence/fable51-fui-cinematics-20261108/` |
| Python PIL/numpy | ja (nachinstalliert) | Kontaktbögen, Zoom‑Ausschnitte, Frame‑Vergleich |
| GLB‑Inspektion (Python, glTF‑JSON‑Chunk) | ja | Original‑Avatar geprüft: 25 Meshes, 21 Visem‑Morphs (Face, NeuralThreads), 8 PBR‑Materialien mit Emissive, 1 Skin – **unverändert** |
| Blender, ffmpeg, Filament‑CLI (matc/cmgen/gltf_viewer), Android SDK/Gradle/adb/Emulator, Robolectric‑Capture (`tools/capture`) | **nein** | nicht nutzbar in dieser Umgebung → keine APK, keine Gerätescreens, keine Framezeiten von mir |
| Referenzbilder `docs/design-screenshots/ref5-fold7-offen.png`, `jarvis-master-20261104.jpg`, `phone-einflug-250ms.png`, `ref4-*`, `ref2-*` | ja | SOLL |
| Referenzvideos (`docs/evidence/**/*.mp4`) | Datei ja, **Decoder nein** | nur Keyframe‑JPG (`claude-jarvis-visual-20261106/keyframes-app-szene.jpg`) ausgewertet |
| Drei Fold7‑Screenshots des Besitzers (19:19, Code50/0.3.49, Innendisplay) | ja, im Chat gesehen | IST‑Quelle der Matrix in Abschnitt 4 |

## 2 · Was im Code tatsächlich geändert wurde

### 2.1 Eine Bewegungs‑Gesetzgebung (`ui/HollywoodFuiConstruct.kt`, ersetzt v1)
Fünf Phasen statt drei: **Lichtanker 0–140 ms → Eckklammern –320 → Scanraster –560 → Glasschichten 480–760 → Daten
640–900 ms**; Schließ‑Choreografie 420 ms (Daten 0–160, Glas 80–300, Rahmen 160–420, jeweils easeInOut);
`tiltDegrees` (14° Einflug, beruhigt sich bis GLASS_END, kippt beim Schließen wieder), `pulse` (Kernenergie am Anker,
#643 B), `settled`, `closeData/closeGlass/closeFrame`. Monoton, deterministisch, reduzierte Bewegung → sofort fertig.

### 2.2 Neue plattformneutrale Zeichenschicht (`ui/HollywoodFuiCinematics.kt`)
Reine Painter‑Geometrie + Zeichnung: `Box`, `Budget.from(JarvisRenderQuality.Budget)` (Motes/Bloom/Frame‑Cadence aus
**dem einen** Cockpit‑Budget, kein zweites Qualitätsmodell), `glassLayer()` (3 gestaffelte Glasscheiben mit Parallaxe,
Cyan→Violett‑Fresnel‑Rand, Glint), `motes()` (deterministische Staubpartikel, seed‑stabil), `anchorBeam()` (Lichtbahn
vom Avatar‑Kern zur nächsten Fensterkante), `Painterly.drawAnchor / drawGlassLayers / drawGridAndScan / drawBrackets /
drawMotesAndEnergy` (atmender Energierand nur bei echter Arbeit).

### 2.3 Overlay (`ui/HollywoodFuiStageOverlay.kt`)
`open(bounds, scene, anchorX, anchorY)`, `setRenderBudget(Budget)`, `liveFrameDelayMs()`; ganzes Konstrukt in
Perspektive via `android.graphics.Camera.rotateY(frame.tiltDegrees · side)`; Raster/Scan/Klammern laufen jetzt über
den Painter (alte Canvas‑Duplikate gelöscht → Vorschau = App‑Pfad); Daten blenden mit `closeData`, Rahmen mit
`closeFrame`; Live‑Cadence folgt dem Budget (16 ms HIGH … 50/80 ms warm/low; SUSPENDED/LOW: keine Live‑Frames,
keine Motes, kein Scan); dritter Haptik‑Tick am Anker.

### 2.4 Fenster + Host
`JarvisMotion.Depth.entranceTilt(openness, side, animatorScale)`; `JarvisWorkWindow.applyFrame()` setzt
`cameraDistance` und `rotationY` (Glas schwingt von der Herkunftsseite ein, liegt bei Öffnung 1 flach, 0 bei
reduzierter Bewegung). `JarvisWorkWindowHost.show()` übergibt den **echten Ursprung** (`plan.origin`‑Mitte) als Anker
und abonniert `home.hologram.observePresentationBudget` → `fui.setRenderBudget`, Abo endet mit dem Fenster.

### 2.5 Aus den Fold7‑Screenshots des Besitzers (IST 19:19) direkt behoben
* Fußzeile „1 tool call(s) require operator review“ (englischer Debug‑Text) → `ContinuumUiCopy.pendingToolReview(n)`:
  „1 Werkzeugaufruf wartet auf deine Freigabe · Ergebnis unbekannt, kein automatischer Neuversuch“.
* Panel „Aktive Agenten“ zeigte „—   offline“ → ehrliche Zeile „Agenten‑Pool · nicht gestartet“ (keine erfundenen
  Agenten, Tone NEUTRAL).

### 2.6 Werkzeuge
`scripts/hollywood-fui-cinematics-preview.sh` + `tools/continuum-jvm-preview/.../HollywoodFuiCinematicsPreview.kt`
rendern 36 Frames (Fold/Phone, Öffnen/Schließen/Budgets/reduzierte Bewegung).

## 3 · Tests (RED → GREEN)

Neu/erweitert: `HollywoodFuiConstructTest` (Phasenkonstanten, Monotonie, Tilt, Pulse, Schließ‑Reihenfolge, reduzierte
Bewegung, Scan‑Fenster, Szenen‑Profile), `HollywoodFuiCinematicsTest` (Budget‑Ableitung aller Stufen, Glasschichten,
Motes deterministisch/im Rahmen, Anker‑Strahl), `JarvisMotionTest.JarvisMotionEntranceTiltTest`,
`Argon4CockpitAndBudgetRegressionTest.fuiMaterializationFollowsTheFivePhaseConstructLaw` (Vertrag von 3 auf 5 Phasen
angehoben), `ContinuumUiCopyTest.pendingToolReviewFooterSpeaksGermanAndCountsHonestly`,
`ContinuumCockpitPresentationTest.panelsNameTheMissingAgentPoolInsteadOfADashAndOffline`.

Kommando (einziger lauffähiger Pfad hier, kein Gradle):
```
export JAVA_HOME=/opt/jdk21 PATH=/opt/kotlinc-dl/kotlinc/bin:/opt/jdk21/bin:$PATH \
  CONTINUUM_ANDROID_DEPS=/opt/continuum-deps \
  CONTINUUM_JUNIT_JARS=/opt/continuum-deps/junit-4.13.2.jar:/opt/continuum-deps/hamcrest-core-1.3.jar \
  JAVA_OPTS="-Xmx4g -XX:+UseSerialGC" LC_ALL=C.UTF-8
scripts/android-kotlin-unit-check.sh /tmp/fui-check2
scripts/hollywood-fui-cinematics-preview.sh /tmp/fui-preview
```
Ergebnis 2026‑11‑08: 620 Testklassen, **3492 Tests, 2 Failures** (beide vorbestehend/umgebungsbedingt, s. u.), 5 Checks PASS; Vorschau 36 Frames gerendert. Erwartete, **vorbestehende** Umgebungsfehler: `ChiefBotStorageBoundaryTest`
(Sandbox läuft als root), `OwnerAppActionFlowTest` (JDK ≥ 19 `\b`‑Regex, auf JDK 17/Android‑ICU grün).

## 4 · IST/SOLL‑Matrix (SOLL = ref5/master; IST = drei Fold7‑Screenshots 19:19 + Keyframes 20261106)

| Kriterium | IST (Fold7 19:19) | SOLL (ref5 / master) | Dieser Branch | Offen |
|---|---|---|---|---|
| Position Kopf/Kugel/Eingabe | Kopf links, Kugel rechts auf Podest, Eingabe unten – **deckungsgleich** mit ref5 | ebenso | – | – |
| Farbe/Palette | Navy/Schwarz, Cyan/Blau/Violett/Magenta, Neon‑Rahmen – trifft ref5 | ebenso | Konstrukt nutzt dieselben `JarvisDesign.Colors` | – |
| Tiefe Arbeitsfenster | Keyframes 20261106: flache Glasplatte, kein Anker, keine Staffelung | gestaffelte Glasscheiben, Lichtanker, Perspektive | 3 Glasschichten + Parallaxe, Anker‑Strahl vom Kern, 14° Einflug‑Neigung (Overlay **und** Panel) | Vorzeichen von `rotationY` auf Gerät prüfen |
| Bewegung | 3 Phasen 450 ms, feste 33 ms Cadence | filmisch, Kern pulsiert bei Auftrag (#643 B), Fenster fliegt ein (C), beruhigt sich (E) | 5 Phasen 900 ms, Pulse, Schließ‑Choreografie 420 ms, Cadence aus Budget | Jank/Framezeiten nur auf Fold7 messbar |
| Glaseffekt | dunkle Scheibe, wenig Rand | Fresnel‑Rand Cyan→Violett, Glint, Sheen | umgesetzt (Iteration 2 nach Frameprüfung: Alpha/Offset/Rand verstärkt) | Echtwirkung auf OLED |
| Lesbarkeit | Profiltexte `MONOSPACE` 10 dp; Fußzeile Englisch | saubere moderne Typo | Fußzeile Deutsch; Agenten‑Leerzustand benannt | Typografie‑Durchgang (Agent 2) nicht begonnen |
| Leerzustände | „—  offline“, „—“ bei Kontext‑Aktualisierungen, „Keine laufenden Prozesse“ | belegte Zustände, sonst ehrlich benannt | Agenten‑Pool benannt | Kontext‑/Denken‑/Assistenten‑„—“ bleiben ehrlich „nicht gemessen“ |
| Performance/Thermik | Fold7 32,4 °C bei 15 % App‑CPU (Screenshot 3) | 60 FPS‑Ziel, thermisch sicher | Motes 24→8→0, Bloom aus, Cadence 16→50→80 ms, Live‑Frames nur bei Arbeit | **keine** Framezeit/Temperatur‑Messung von mir |
| Graue Android‑Dialoge | `AlertDialog` für Tool‑Review, OwnerFace, Shizuku | Cockpit‑FUI | nicht angefasst | Agent 2 |
| Navigation doppelt (Rail + Kacheln) | Rail links 3 Icons + 10 Kacheln | eine logische Struktur | nicht angefasst | Agent 3 / Consumer‑Modus |
| Sieben Arbeitswelten | Profile im Overlay vorhanden, Test 02 (#638) offen | sichtbar, an echte Ereignisse gebunden | Profile unverändert über `HollywoodFuiLiveData` | Fold7‑Test aller 7 Szenen |

## 5 · Offene Gerätechecks (nur Besitzer, Fold7)

1. Einflug‑Neigung: schwingt das Fenster von der Avatar‑Seite ein (sonst `side` negieren in `JarvisWorkWindow.entranceSide()`/Overlay)?
2. Anker‑Strahl startet am Kopf/Kern (Ursprung = `plan.origin`), nicht am Fensterzentrum.
3. Framezeiten (Perfetto/`adb shell dumpsys gfxinfo`), CPU/GPU, Akku‑Temperatur während 2 min arbeitendem Fenster; Cadence muss bei Thermik 2 sichtbar langsamer werden, bei 3 stehen.
4. STOP/Barge‑in, Rotation, Fold open/close, Hintergrund → Konstrukt schließt sauber (`onSizeChanged` schließt sofort – unverändert).
5. Alle 7 Arbeitswelten mit echtem Auftrag (Dokument, App, Coding, Agenten, Recherche, Zeitlinie, Security).
6. Lesbarkeit der neuen Fußzeile auf Außendisplay.

## 6 · Nicht behauptet
Keine APK, kein Gerätescreenshot, keine FPS‑Zahl, kein Filament‑/Shader‑Eingriff am Original‑Avatar, keine Änderung
an Signatur/Release. Vorschaubilder sind Java2D‑Belege des Zeichengesetzes, nicht das fertige Cockpit.
