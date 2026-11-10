# Continuum Code 52 – Grafik- und Animationskontrolle (10.11.2026)

**Sicherheitsstatus:** Nur isolierter Integrationszweig `chatgpt/code52-cinema-final-graphics-audit-20261110`; kein `main`-Merge, keine Originalsignierung, keine Veröffentlichung, keine neue App, keine Nutzerdateien/Avatar-GLB ersetzt. Quelle ist der zunächst vollständig QA-grüne und **unsignierte** Cinema2030-Stand `0df55d7c8d5cae8a53bf1a43e26651f902881e78` (#681). Das folgende zusätzliche Patchset benötigt eine **neue** exakte Gesamtabnahme; vorige 4254 JUnit-Tests/0 Fail, 18 Skip und green Node/Backend gelten ausschließlich für diesen alten SHA.

## Nutzer-Kanon
- Ein und derselbe **originale** Continuum-Kopf mit 21 Morphs, keine Ersatzfigur; originale Kernkugel mit Ringen, Kernlicht, Partikeln und echten Zuständen.
- Sichtbare räumliche Fenster: 5-Phasen-Öffnung/Schließung, tiefe Glasflächen, Lichtlinien, echte sieben FUI-Arbeitswelten, bewegliche Kamera und Kopf-/Augenkoordination.
- Cyan, Blau, Violett, Magenta; dunkle Flächen, räumliche Glanzeffekte und Neon-Kacheln. Keine zusätzliche statische linke Taskleiste.
- Cinematische Inszenierung nicht mit fiktiven Arbeitserfolgen, Agenten, PDF-Seiten, Zugriffsrechten oder Geräteverifikation verwechseln.
- Vor Überhitzung geschützt: gemeinsame Frame-Uhr, Energiesparmodus, Android-Animationsskalierung, reduzierte Bewegung und thermisches Renderbudget; nativer Text, Hitboxes und Screenreader nicht von einem Shader filtern oder verschieben.

## Getrennte Quelle/Einbau/Kontrolle (exakter Ursprung und nachgewiesener aktueller Callpath)

| PR | Befund | Entscheidung und Runtime-Anker |
|---|---|---|
| #477/#479/#481/#489 | Motion-Library der alten Jarvis-Design-Serie ist nicht verwaist. `DampedSpring.kt` und `Easings.kt` byte-identisch zu #489, `JarvisMotion` delegiert an `MotionPresets`. | **ALREADY_PRESENT**; keine konkurrierende Animationsbibliothek oder zweite Uhr. |
| #535/#539/#563 | Originalkugel/Neon und transparente Referenz-Kopf-Komposition sind im aktuellen System erhalten; aktueller `ContinuumBrainOrbRenderer` zeichnet die echte Hypothesen-Markierung und GPU-Globe separat in `ContinuumHologramRenderer`. | **SOURCE_CONNECTED**, keine Ersatzkugel kopieren; echte Fold7-Bildabnahme offen. |
| #540/#551/#565 | `AvatarIdleMotion.kt` und `AvatarFraming.kt` sind **byte-identisch** zum Head von #565; minimum-jerk Blink und clipping-sichere Kopfprojektion vorhanden. | **ALREADY_PRESENT**; ursprüngliche GLB-Skinning-Geometrie unverändert lassen. |
| #541/#549 | `JarvisTileEnergy.kt` ist **byte-identisch** zu #549. `JarvisShellView` setzt `ambientMotionEnabled`, prüft Fokus/Energiesparstatus/Animationsmaßstab/Thermik. `JarvisShellRenderer` gibt reale `timeMs` an `JarvisTiles.drawSurface`, das `JarvisTileEnergy.sample` aufruft. | **SOURCE_CONNECTED** bis zu sichtbaren Kacheln; 13-s-Kantenfahrt, keine Zusatzclock. Fold7-FPS/Look offen. |
| #550/#542 | Geprüfter aktueller Kugelcode neutralisierte NaN und ±∞, **nicht** `Float.MAX_VALUE`; Zwischenrechnung konnte trotz endlichem Eingang nicht-endlich werden. | **REGRESSION REPAIRED** im neuen Zweig: wieder `phase in -1_000_000f..1_000_000f` und Grenzwertfälle in `ContinuumOrbNonFiniteSignalsTest`. |
| #558 | Hypothesenmarker als 12 violette, getrennte Arc-Segmente bereits im Produktiv-Renderer und über `ContinuumBrainSceneView` an `effects.hypothesisVisible(now)` gebunden; der historische JUnit-Test fehlte jedoch. | **CODE ALREADY_PRESENT, TEST RESTORED**: `ContinuumOrbHypothesisMarkerTest.kt`. Keine Hypothese als bestätigte Erinnerung ausgeben. |
| #594 | Orb-Renderoptimierung `ContinuumBrainOrbRenderer.kt` **byte-identisch** mit #594, Scratch-Puffer gegen Frame-Allokationsspitzen vorhanden. | **ALREADY_PRESENT**; keine ältere, ineffizientere Vollkopie. |
| #597/#599 | Dunkles Referenz-Cockpit, mehrere lebende Tabs sowie deterministischer 15-s-Avatar-Livetest wurden in spätere Quelllinie weiterentwickelt. | **SOURCE_CONNECTED**, echte TextureView/Filament-/Fold7-Tests weiterhin nicht durchgeführt. |
| #614/#618/#651 | `HollywoodFuiConstruct`, `HollywoodFuiCinematics` und `HollywoodFuiStageOverlay` erfüllen dasselbe fünfphasige Bewegungsprinzip im aktuellen Werkfenster. | **SOURCE_CONNECTED / NEWER SUPERSET**, nicht zurück zum älteren Code überschreiben. |
| #617 | WorkWindow-Camera-Profile und `CriticalDampedSpring` steuern den ursprünglichen Avatar-Durchlauf. | **SOURCE_CONNECTED**; kein zweiter Head-Motion-Renderer. |
| #621/#623 | Reale CockpitAnimationEvent- und ProcessStep-Quellen steuern Code-/App-/Dokument-/Security-/Chrono-/Evidenz-/Bot-Arbeitswelten; keine nicht belegten Seiten oder Worker vortäuschen. | **SOURCE_CONNECTED**, vollständige sieben native Szenen am Fold7 offen. |
| #659 | `JarvisWindowGaze` existiert, `AvatarSurfaceRadiance` ist in den originalen Filament-Gesichtsmaterialpfad über `applySurfaceRadiance` eingebunden, `SpatialWindowLight` in `applyCoreLight`. | **SOURCE_CONNECTED / MODIFIED**; exakt kopierte ältere Fenster-Engine würde aktuelle Cinema-Verbesserungen verlieren. |
| #661 | Java2D-Painter, `HollywoodFuiReferenceFidelity.kt`, Referenzbilder, Referenz- und Preview-Skripte existieren; der Compiler-Eingang wurde zwischenzeitlich angepasst. | **TOOLING_PRESENT, CI NOW REQUIRED**; Preview/Negativkontrollen nun im neuen QA-Workflow, nicht als Filament-Geräteerfolg etikettieren. |
| #666 | Avatar-Satzgesten-Policy und aktives Arbeits-Raster sind im neueren Kanon vorhanden, das Innenraster darf die Rahmenhelligkeit nicht steuern. | **SOURCE_CONNECTED / MODIFIED**. |
| #677 | Räumliche FUI, Materiallicht, Step-Receipt, Work-Pulse, Sensorik und 15 Experimentverträge. | **ANCESTOR OF #681**, neuere Änderungen nicht erneut cherry-picken. |
| #678 | GazeBlinkPolicy und realer Mapper/Filament-Gaze-Pfad. | **MERGED INTO #677, INHERITED**. |
| #679 | Hardware-AGSL-Rand nur auf Geometrie, niemals auf nativen Text oder Touchziele. | **MERGED INTO #677, INHERITED**. |
| #680 | Elf tatsächlich schaltbare Effekte, vier nicht unabhängige/gesperrte Experimente; Glas-Z-Slices, A/B-Rim-Test und Orb-Fenster-Pulse. | **ANCESTOR OF #681**; vorhandene Schalter erhalten. |
| #681 | Pixeldichte-Regression: AGSL-Stroke innerhalb falscher dp-Canvas-Transformation. | **SOURCE-FIX VERIFIED / FULL CI PASS** auf Ausgangs-SHA `0df55d7c8d5cae8a53bf1a43e26651f902881e78`. |
| #485/#591 und ältere umfangreiche Design-/Dateiflugzweige | Einige ihrer Features wurden in spätere Code-50/51-/52-Pfade umgebaut; nicht alle Teile sind byte-identisch. | **FOLLOW-UP SOURCE REVIEW** nur für nicht belegte eigenständige Fähigkeiten; keine große Fremd-Diff-Vollübernahme auf einen getesteten Code52-Stand. |

## Neue tatsächliche Änderungen dieses Kontrollgangs

1. **Kugelphase** gegen große endliche Werte absichern (wiederhergestellter #550-Guard), mit exakten **Float.MAX_VALUE / -Float.MAX_VALUE / 1_000_000** Regressionen.
2. **Hypothesen-UI-Regression** aus #558 wiederherstellen; der Renderer zeichnet seine violetten Segmente bereits.
3. `.github/continuum/code52-graphics-branch-intake.test.mjs` verifiziert Verbindungen zwischen alten und neuen Quellzweigen: Kachelzeit → sichtbare Zeichenfunktion; Motion-Presets → aktiver Motion-Pfad; Avatar → originaler Filament-Licht- und Morph-Pfad; Cine-Fenster → AGSL und geschützte native Overlay-Geometrie.
4. Erweiterte **SHA-gebundene CI**: vollständige Node-/Backend-/Android-JVM/UI-/Lint-/ARM64-unsigned-Tests **plus Java2D-Referenzmessung und 36 Frames** mit Provenienz, Referenzpanel-Negativkontrollen und Debug-Artefakt. Keine GPU-/Fold7-Wahrheitsbehauptung.
5. Cinema-2030-Opt-in bleibt **standardmäßig AUS**. 11 getrennte Experimente sind im Code verdrahtet; #03, #07, #10, #15 wurden nicht als neu unabhängig aktivierbare Effekte umetikettiert. Grundlegende vorhandene Blick-/Satz-/Budgettechnik ist trotzdem vorhanden, aber keine freigegebene neue Szene.

## Gate vor dem nächsten Code-52-Signieren

- **Ein** finaler HEAD wird unveränderlich geprüft; keine Kombination von GitHub-Testergebnissen verschiedener SHAs.
- Phase- und Hypothesen-JUnit müssen auf dem finalen HEAD bestanden sein, Referenzchecks und alle anderen Gating-Stufen ebenfalls.
- Reines Java2D liefert nur eine Host-Vorschau. Der echte Fold7-Check muss Licht/Kontrast, 3D-Material, originalen Kopf, sichtbaren Szenenflug, 7 Arbeitswelten, 10-Satz-Hands-Free, Temperatur, FPS, reduzierte Bewegung und sichere Update-Installation abdecken.
- **Originalsignierung ist NICHT Bestandteil dieses Branches**; der separate Signierer #682 darf nicht für den älteren #681-SHA als Ersatz einer neuen QA missbraucht werden. Beim neuen HEAD müssen SHA, QA-ID und Artefaktbezeichnung separat neu freigegeben werden.
- `main`, signierter Code-51-Release, Originalsignatur, Original-GLB, App-ID und vorhandene Nutzerdaten bleiben unverändert.

Dies ist ein **priorisierter Grafik-Quell-Audit** auf Basis der vorhandenen 627 PR/727-Branch-Snapshots und aktueller gezielter GitHub-PR-Abfragen; kein Anspruch, jeden historischen Branch live auf einem Fold7 ausgeführt zu haben. Ein späterer Fingerprintvergleich der tatsächlich installierten App und Referenzvideos ist für eine Aussage „nichts mehr fehlt“ notwendig.
