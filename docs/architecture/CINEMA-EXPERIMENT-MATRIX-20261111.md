# Cinema-Experimentmatrix – die 15 Schalter aus `JarvisCinematicSettings` (11.11.2026)

**Quellstand:** Zweig `claude/omega-agent-a-cinema-20261111`, Basis `6c538e2ef` (enthält #761, #757/`be33c2c97`, #747).
**Methode:** Aus Code und Tests abgeleitet, nicht geschätzt. Für jeden Schalter: wo `experimentEnabled(…, EXP_xx)`
gelesen wird, was der Wert im Produktionspfad schaltet, welche eingecheckten Hostbilder es gibt, welche Tests den
Mechanismus bzw. das An/Aus des Schalters prüfen und ob ein Fold7-Gerätebeleg im Repo dokumentiert ist.

## Legende

- **IMPLEMENTIERT** – `JA`, wenn ein Produktionsaufruf den Schalter liest und damit einen echten Zeichen-/Logikpfad
  ändert (Datei:Zeile). Alle 15 sind `wired = true` (`CinemaExperimentWiringTest`).
- **SICHTBAR (HOST, im Repo)** – nur eingecheckte Hostbilder. Wichtig: Robolectric API28 zeichnet den 2D-Ersatzkopf,
  **kein Filament**, führt **kein AGSL** (`RuntimeShader`, API 33) aus und hat **keinen Bewegungssensor**. Alle
  Hostsequenzen laufen mit den Code52-Voreinstellungen (alle 15 EIN); **kein** eingechecktes Bildpaar zeigt einen
  Schalter AUS gegen EIN. „Im Gesamtbild“ heißt daher: der Pfad ist beim Aufnehmen aktiv gewesen, seine Wirkung ist im
  Bild aber nicht isoliert nachgewiesen.
- **FUNKTIONSFÄHIG (Tests)** – Testklassen, die den Mechanismus prüfen; getrennt davon, ob das **An/Aus des
  Schalters selbst** getestet ist. Alle genannten Klassen liefen in diesem Zweig: **115 Tests, 0 Fehler, 0 Errors,
  0 übersprungen** (19 Klassen, siehe `docs/evidence/cinema2-connections-20261111/junit-summary.json`, Lauf
  `matrix-run`). Das ist Host-JVM/Robolectric, kein Gerätenachweis.
- **FOLD7-GEPRÜFT** – nur mit im Repo dokumentiertem Gerätebeleg. Gesucht in `docs/` (Fold7/Gerätetest/PASS):
  für **keinen** Schalter existiert einer. Die Cinema-Dokumente führen Fold7 durchgehend als `NOT_RUN`
  (z. B. `docs/integration/CINEMA52_OWNER_RELEASE_CONTRACT_20261110.md` §3,
  `docs/research/omega/CINEMA-PERFORMANCE-VERIFICATION-20261110.md:77/137`,
  `docs/evidence/cinema2-depth-light-20261111/README.md`).

Eingecheckte Hostbilder mit Cinema-Bezug: `docs/evidence/cinema2-depth-light-20261111/*.jpg` (9, Robolectric,
Quellstand `907553f8`), `docs/evidence/cinema-native-sequence-20261110/*.png` (4, Robolectric, `9fd5d1`),
`docs/evidence/cinema-stop-return-20261110/*.png` (2), `docs/evidence/fable51-fui-cinematics-20261108/*.png`
(4, Java2D-Stand-in des Konstrukts), `docs/evidence/pdf-scene-20261111/visual-samples/*.png` (2). Die
Software-Skia-Kantenshaderbilder aus `cinema-render-coverage-20261110/coverage.json` sind dort nur referenziert,
**nicht eingecheckt**.

## Matrix

| # | Schalter (Bezeichnung) | IMPLEMENTIERT | SICHTBAR (HOST, im Repo) | FUNKTIONSFÄHIG (Tests) | FOLD7-GEPRÜFT |
|---|---|---|---|---|---|
| 01 | `EXP_01_Z_GLASS` – 3D-Glasschichten | JA. `JarvisWorkWindowHost.kt:276-277` → `fui.zSliceEnabled` → `HollywoodFuiStageOverlay.kt:313/351` verschiebt Rückglas (−24 dp) und Frontklammern (+20 dp) nach `HollywoodFuiConstruct.layerShiftDp` während der Neigung. Zusätzlich `JarvisShellView.kt:130-134` startet `SpatialPresenceAndLightBridge` (Game-Rotation-Parallaxe → `fui.setParallax`), nur mit Arbeitsfläche ≠ Leerlauf und animierendem Budget. | Teilweise: Wirkung nur bei Neigung ≠ 0 (Flug/Schließen); Flugbilder `cinema2-depth-light/02,03` im Gesamtbild, nicht isoliert. Sensorparallaxe auf dem Host **nicht darstellbar**. | Mechanismus: `HollywoodFuiDepthPlaneTest` (2). Schalter-An/Aus: **kein Test**. `SpatialPresenceAndLightBridge`: **kein Test**. | NOT_RUN |
| 02 | `EXP_02_RIM` – Dauerhafter Hologrammrahmen | JA. `JarvisWorkWindowHost.kt:274-275` → `fui.rimIsolationEnabled` → `HollywoodFuiStageOverlay.kt:328-331`: EIN = Rahmen behält volle Rasterstärke, nur das Innenraster dimmt unter Text; AUS = Rahmen dimmt mit. | Im Gesamtbild (stehendes Fenster `cinema2-depth-light/04,05`), nicht isoliert. | Mechanismus: `HollywoodFuiWorkActivityTest` (5, u. a. `settledFramePreservesBrightRimWhileRasterProtectsText`). Schalter-An/Aus: **kein Test**. | NOT_RUN |
| 03 | `EXP_03_DEPTH_GLASS` – Geschützte Tiefenflächen | JA. `JarvisWorkWindowHost.kt:280-281` → `window.protectedDepthSurfacesEnabled` → `JarvisWorkWindow.kt:848` zeichnet eine zurückgesetzte Platte (`JarvisProtectedDepthSurfaces.recess/plateAlpha`) auf jedem Tiefenblatt. Nur sichtbar, wenn das Fenster Tiefenblätter hat (`depthLayers > 0`). Hinweis: das „Tiefenglas“ aus `cinema2-depth-light` (`glassVacancy`) gehört **nicht** zu EXP_03. | Kein isolierter Hostbeleg. | `JarvisProtectedDepthSurfacesTest` (4); Schalter-An/Aus: `CinemaExperimentRuntimeToggleTest.exp03OnOffChangesWhetherAProtectedPlateIsRecessed` (Einstellung + `recess`, zeichnet das Fenster nicht). | NOT_RUN |
| 04 | `EXP_04_FOLDER_FLIGHT` – Fensterflug aus Kacheln | JA. `JarvisWorkWindowHost.kt:266-269,278` → `fui.cinematicArcEnabled` (`HollywoodFuiStageOverlay.kt:295`) und `window.cinematicArcEnabled` (`JarvisWorkWindow.kt:650`) → Bogenhub `arcLiftDp` = −28 dp·sin(π·Öffnung). | Flugbilder `cinema2-depth-light/02,03`, `cinema-native-sequence` (280/650 ms) im Gesamtbild; Bogen nicht isoliert. | Mechanismus über die ungeschaltete Projektion: `JarvisCinemaEntranceTest.projectedWindowFollowsTheArcFromARealOriginAndStaysAClosedFolder` (1 von 29). Schalter-An/Aus am Fenster: **kein Test**. Offen: `JarvisCinemaEntrance.projectedWindow` nutzt `arcLiftDp(…, !reducedMotion)` unabhängig von EXP_04. | NOT_RUN |
| 05 | `EXP_05_WINGS` – Räumliche Cockpit-Flügel | JA. `JarvisShellView.kt:466-473` → `JarvisShellRenderer.kt:221` `PerimeterWingChoreographer.depth` für Kacheln, die für ein Arbeitsfenster räumen. | Im Gesamtbild der Arbeitsbilder (geräumtes Deck), nicht isoliert. | `PerimeterWingChoreographerTest` (2: nur räumende Kacheln; Wärme/reduzierte Bewegung → flach). Schalter-An/Aus: **kein Test**. | NOT_RUN |
| 06 | `EXP_06_FACE_LIGHT` – Fensterlicht auf Avatar | JA. `ContinuumHologramView.kt:1149-1151` → `windowOpen` → `FilamentAvatarRenderer.kt:267` → `SpatialWindowLight.tint` (Kernlicht des Filament-Kopfs). **Befund:** `open = windowOpen \|\| flyIn.open`; `CinemaWindowGazeBridge.publishLight` setzt `CinemaWindowLightState` ohne EXP_06-Prüfung → EXP_06 AUS entfernt das Fensterlicht während projizierter Bewegung **nicht vollständig**. | **Nein** – nur Filament; Host zeichnet den 2D-Ersatzkopf. | `SpatialWindowLightTest` (8), `CinemaWindowGazeBridgeTest` (10). Schalter-An/Aus: **kein Test**. | NOT_RUN |
| 07 | `EXP_07_GAZE` – Augen-Hals-Kopplung | JA. `ContinuumHologramView.kt:1154-1156` → `eyeNeckCouplingEnabled` (EIN, wenn Cinema nicht gewählt **oder** EXP_07 EIN) → `AvatarMorphMapper`. | **Nein** (Filament). | Schalter-An/Aus am Mapper: `CinemaAvatarExperimentTest.exp07OnOffChangesNeckShareWithoutMovingTheEyesAlone`; Master-AUS: `CinemaExperimentRuntimeToggleTest.cinemaMasterOffDisablesTheNewExperiments`. | NOT_RUN |
| 08 | `EXP_08_PRE_SPEECH` – Vorbereitende Lippenbewegung | JA. `AgentActivity.kt:301-303` → `TtsHologramSignal.prePhonatoryLevel` (80 ms Vorlauf auf tatsächlich gepuffertem PCM) → `avatarDirector.audioLevel`. | **Nein** (braucht echte TTS-PCM und Filament). | `TtsHologramSignalTest` (11, u. a. `prePhonatoryJawOnlyTracksActuallyBufferedFutureSpeech`, `lookAheadIsEightyMillisecondsOnBufferedPcm`, `guessedOrUnbufferedFutureSpeechDoesNotMoveTheJaw`). Schalter-An/Aus: **kein Test**. | NOT_RUN |
| 09 | `EXP_09_FOCUS_BLINK` – Blickwechsel-Lidschlag | JA. `ContinuumHologramView.kt:1152-1153` → `gazeBlinkEnabled` → `AvatarMorphMapper.update` / `GazeBlinkPolicy`. | **Nein** (Filament). | `CinemaAvatarExperimentTest.exp09GazeBlinkStaysOffDuringAnOpenSyllable`, `GazeBlinkPolicyTest` (2). Schalter-An/Aus nur indirekt über den Mapper-Parameter. | NOT_RUN |
| 10 | `EXP_10_SENTENCE` – Natürliche Satzgesten | JA. `ContinuumHologramView.kt:1157-1159` → `sentenceGesturesEnabled` → `AvatarMorphMapper`. | **Nein** (Filament). | Schalter-An/Aus am Mapper: `CinemaAvatarExperimentTest.exp10OnOffAppliesOrSuppressesTheExistingSentenceNod`, `sentenceGestureGateRequiresRealProgressAndAnEnabledSwitch`. | NOT_RUN |
| 11 | `EXP_11_ORB_LINK` – Kugel-Fenster-Energiebrücke | JA. `ContinuumHologramView.kt:1093-1098` → `JarvisWindowOrbPulse.energy` ab dem ersten Fensterbild → `coreEnergy` der **Originalkugel** (2D-Pfad `:1185`, Filament `:1146`). **Achtung:** das #747-Energieband Kugel→Fenster (`HollywoodFuiStageOverlay` `workingSample` + `setEnergySource`) und das neue Quittungspaket hängen **nicht** an EXP_11, nur am Master-Schalter. | Kugel im 2D-Pfad hostzeichenbar; kein isolierter Beleg. Das #747-Band ist in den `cinema2-depth-light`-Arbeitsbildern im Gesamtbild. | `JarvisWindowOrbPulseTest` (2). Schalter-An/Aus: **kein Test**. #747-Band: `HollywoodFuiWindowUiTest.sustainedEnergyUsesOrbBoundsAndStopsWithActualWork`, `JarvisFluidLightTest` (10). | NOT_RUN |
| 12 | `EXP_12_RECEIPTS` – Bestätigte Arbeitsschritt-Impulse | JA. `JarvisWorkWindowHost.kt:270-271` → `fui.workPulseEnabled`. Auslöser nur `AgentActivity.settleToolScene` (`:3858-3859`: `SUCCEEDED`, nicht unsicher, nicht wiederholt) → `verifiedStep`. Wirkung: Randimpuls (`HollywoodFuiStageOverlay.kt:345-349`) und **neu (be33c2c97)** Quittungspaket auf dem Band zurück zur Kugel (`:272-280`, `JarvisFluidLight.drawTransfer(…, receipt)`). | Kein eingechecktes Bild (die Hostsequenzen lösen keinen `verifiedStep` aus). Pixelprüfung nur im Test. | **Neu in diesem Zweig:** `CinemaConnectionsTest` (4 Paketfälle), `HollywoodFuiConnectionsUiTest.receiptPacketOnlyAfterAVerifiedStepAndOnlyOnTheBand` und `…noBandNoReducedMotionNoExperimentNoPacket` (inkl. EXP_12 AUS → keine Quittung). Vorher gab es **keinen** Test für `verifiedStep`. | NOT_RUN |
| 13 | `EXP_13_LUXURY` – Edles Rauchglas und gerichtete Farben | JA. `JarvisShellView.kt:464-473` → `JarvisShellRenderer` `luxuryDepth` → `JarvisTiles.drawSurface` → `LuxuryHoloButtonRenderer` (Licht von der Kugel). | Im Gesamtbild aller Cockpit-Hostbilder (Kacheln), nicht isoliert. | `LuxuryHoloButtonRendererTest` (7). Schalter-An/Aus: **kein Test**. | NOT_RUN |
| 14 | `EXP_14_GLASS_SHADER` – Hardware-Glaskanten | JA, nur API ≥ 33 und mit Bloom: `JarvisWorkWindowHost.kt:272-273` → `fui.prismRimEnabled` → `HollywoodFuiStageOverlay.kt:363-365` `AgslHologramEdgePainter`. | **Nein** – Robolectric API28 führt keinen `RuntimeShader` aus; Software-Skia-Proben nicht eingecheckt. | `AgslHologramEdgeCoordinatesTest` (2, nur UV-Abbildung). Shaderausführung und Schalter-An/Aus: **kein Test**. | NOT_RUN |
| 15 | `EXP_15_FRAME_BUDGET` – Thermisch adaptive Animation | JA. `ContinuumHologramView.kt:946-952` → `JarvisRenderQuality.adaptiveAnimation(…, exp15)` → ein Cockpit-Budget für Shell, Overlay (`setRenderBudget`) und Kugel. | Kein Bildeffekt im engeren Sinn (Budget). | Schalter-An/Aus: `CinemaExperimentRuntimeToggleTest.exp15OnOffChangesTheResolvedAnimationBudget`, `alwaysOnCinemaStillHonorsThermalPowerSavingAndReducedMotion`; `JarvisRenderQualityAdaptiveTest` (5), `JarvisThermalVisualContractTest` (3). Gerätemessprotokoll vorhanden, aber **NOT_RUN** (`CINEMA-PERFORMANCE-VERIFICATION-20261110.md:77`). | NOT_RUN |

### Zusammenfassung

- IMPLEMENTIERT: **15/15**.
- SICHTBAR mit isoliertem An/Aus-Hostbeleg: **0/15**. Im Gesamtbild hostzeichenbar: 01 (teilweise), 02, 04, 05, 11, 13.
  Auf dem Host grundsätzlich nicht darstellbar: 06, 07, 08, 09, 10 (Filament/TTS), 14 (AGSL API 33), Sensoranteil von 01.
- Schalter-An/Aus durch Test belegt: **03, 07, 10, 12, 15** (09 indirekt). Nur Mechanismus getestet: 01, 02, 04, 05, 06,
  08, 11, 13, 14. Einschränkung: Die An/Aus-Tests für 07, 10 und 12 schalten den Parameter, den der Host aus dem Schalter
  setzt (Mapper-Flag bzw. `workPulseEnabled`); die Verdrahtung Einstellung→Parameter im Host ist nur für 03 und 15 über
  `JarvisCinematicSettings` mitgeprüft, für die übrigen nur durch Codelesen (Zeilen oben).
- FOLD7-GEPRÜFT: **0/15**, alle `NOT_RUN`.

## Cinema-2.0-Verbindungen ohne eigenen Schalter

| Effekt | Gesteuert durch | Tests |
|---|---|---|
| #747 Energieband Kugel→Fenster während echter Arbeit | Master `JarvisCinematicSettings.KEY` (`cinematicEffectsEnabled`), Budget, reduzierte Bewegung; **nicht** EXP_11 | `HollywoodFuiWindowUiTest.sustainedEnergyUsesOrbBoundsAndStopsWithActualWork`, `JarvisFluidLightTest` |
| be33c2c97 Lesefaden Gesicht→Fenster | Master, `setWorkActive`, Budget, reduzierte Bewegung; **kein** EXP-Schalter | `CinemaConnectionsTest` (3 Fadenfälle), `HollywoodFuiConnectionsUiTest` (3 Fadenfälle) |
| Fokus-Tiefe / Tiefenglas (Cinema 2.0, `cinema2-depth-light`) | Master (`JarvisWorkWindow.cinematicEffectsEnabled`); **nicht** EXP_03 | `Cinema2DepthLightTest` (2) |

## Offene Punkte (nicht in diesem Zweig behoben)

1. EXP_06 AUS lässt das von `CinemaWindowGazeBridge` veröffentlichte Fensterlicht (`CinemaWindowLightState`) weiter
   wirken (`FilamentAvatarRenderer.kt:267`). Entscheidung nötig: Brücke ebenfalls an EXP_06 binden oder Bezeichnung ändern.
2. EXP_11 heißt „Kugel-Fenster-Energiebrücke“, schaltet aber nur den Kugelimpuls; Band und Quittungspaket laufen unabhängig.
3. Lesefaden hat keinen eigenen Schalter; falls der Besitzer ihn einzeln abschalten können soll, an einen bestehenden
   Schalter binden (kein neuer Bus, keine zweite Uhr).
4. Schalter-An/Aus-Tests fehlen für 01, 02, 04, 05, 06, 08, 11, 13, 14; isolierte An/Aus-Hostbilder fehlen für alle.
5. Gesamte Android-Suite, Filament auf GPU, AGSL API 33, Fold7 innen/außen, Bildzeiten, Wärme: **NOT_RUN**.
