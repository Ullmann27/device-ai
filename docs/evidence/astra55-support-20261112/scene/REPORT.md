# Szene55: unabhängige Support-Prüfung

Geprüfter Quellcommit: `080d7f4df9a7b355b1edbe5f876a35bc0d834338` auf
`work/astra55-scene-20261112`. Kanonisches Integrationsziel bleibt Code55 / PR #791.
Keine Produktionsdatei, Git-Referenz oder fremde Arbeitskopie verändert. Der
Reparaturkandidat liegt ausschließlich als `repair.patch` vor. Keine Veröffentlichung.

## Status und belastbare Ergebnisse

| Gegenstand | PORTIERT | HOST-VERIFIZIERT | GERÄTEBESTÄTIGT |
|---|---|---|---|
| Acht Dockziele, bestehende Originalkugel, überlappende Kopfgeometrie | im geprüften Szene-Commit | ausgewählte Geometrie-/Navigationswerte geprüft | NOT_RUN |
| Einzeilige Reparatur für große Schrift | Kandidatenpatch, nicht integriert | 28/28 Geometrie-/Navigationsprüfungen | NOT_RUN |
| Neue Filament-Monitorbausteine | laufender uncommittierter Aufbau beim letzten Lesen | NOT_RUN | NOT_RUN |
| Transparenz und Bildqualität der vollständigen Szene | Quellprüfung | kein neuer Rendernachweis | NOT_RUN |

Die Prüfung verwendet die vollständigen, unveränderten Klassen
`JarvisShellLayout`, `JarvisCockpitGrid`, `JarvisWindowProfile`,
`CinematicWorkstationLayout` und `ContinuumWorkAreaNavigationSpec` aus dem genannten
Commit. Die benötigten Docklisten und Geometriemethoden aus `JarvisTiles` und
`CockpitGlassButtons` werden unverändert ausgeschnitten. Ausschließlich das
Icon-Enum wird durch die verwendeten Namen ersetzt; Zeichenmethoden werden nicht
kompiliert. Das ist ein deterministischer Layouttest, kein vollständiger UI-Test.
Die genauen Eingänge stehen jeweils in `source-hashes.json`.

## 1. Reproduzierter Fehler: große Schrift

Pfad: `app/src/main/java/at/heinz/deviceai/ui/CinematicWorkstationLayout.kt`,
`create`, insbesondere die Mindesthöhe `sceneH` und die Seitenfelder.

Bei **884 × 760 dp, Schrift 200 %** wird die Szenenhöhe auf nur 310 dp begrenzt,
während die Seitenfelder mit der Schrift wachsen:

- `COCKPIT_SECONDARY` hat negative Höhe und wird durch `filterValues` entfernt.
- `DIALOG` liegt vertikal bei 132–344 dp, `WORK_DOCUMENT` bei 273,3–402 dp.
  Die identischen seitlichen Bereiche überlappen um **70,7 dp**.
- Bei Schrift 150 % bleiben auf mehreren breiten Fenstern weniger als 48 dp
  für das klickbare Sekundärfeld; bei 600 dp entsteht schon dort Überlappung.

Der Kandidat ändert ausschließlich die Mindesthöhe breiter Szenen von
`310f` auf `310f * font`. Standardgröße und Telefongeometrie bleiben dabei gleich;
große Schrift erhält bei Bedarf vertikal scrollbaren Inhalt. Keine neue Oberfläche.

**Nachweis:** `baseline-matrix/result.json`: Kompilierung exit 0, Prüflauf exit 1,
**28 Prüfungen, 12 Fehler**. `candidate-matrix/result.json`: Kompilierung und Lauf
exit 0, **28 Prüfungen, 0 Fehler**. Die Matrix umfasst 320, 360, 600, 640, 700,
720 und 884 dp bei Schrift 100/150/200 %. Geprüft werden positive und enthaltene
Boxen, kollisionsfreie Bedienelemente außerhalb des gemeinsamen Hintergrunds,
48-dp-Dockzellen und lesbare Höhe des breiten Sekundärfelds. Zusätzlich werden
412-dp-Telefon, 884-dp-Breitansicht und Navigationslisten geprüft.

## 2. „Mehr“: Direktzugriff fehlt, Ziele bleiben vorhanden

`JarvisShellLayout.headerActionBoxes` bevorzugt `grid.areas[ICONS]`.
`JarvisCockpitGrid.create` reserviert erst ab 704 dp drei 48-dp-Felder.
Die echte Layoutfunktion liefert daher bei **allen 104 ganzzahligen Breiten von
600 bis 703 dp** nur Benachrichtigungen und Einstellungen. Bei 599 und 704 dp
ist „Mehr“ vorhanden. Dies ist ein Sprung im direkten Headerzugriff.

**Kein belegter vollständiger Verlust der ausgelagerten Funktionen:**

| Entferntes Dockziel | Eintrag unter Mehr | bestehender Zielpfad |
|---|---|---|
| `logic` | Denken und Planen | `JarvisMenuNavigator` → `showLogicCenter` |
| `agents` | Mein KI-Team | `ACTION_AGENTS` → `showBotRoster` |
| `system` | Systemstatus | `SYSTEM_STATUS` → `showSystemCenter` |

`ContinuumWorkAreaNavigationSpec.moreEntries` enthält alle drei Ziele;
dies wurde mit dem echten Kotlin-Datenmodell geprüft. `JarvisMenu.routes` übernimmt
die unverändert vorhandenen Ziele aus `JarvisShellContent.routes`.

Der Ersatzweg ist quellseitig durchgehend vorhanden:
`ContinuumHomeView.measureShellActions` bindet `STATUS` an `system`;
`AgentActivity.showSystemCenter` enthält **MEHR · ARBEITSBEREICHE**;
`showMoreWorkAreas` bindet die Einträge an `navigateDashboardTarget`.
Das Statusfeld bleibt in der 600–703-dp-Matrix vorhanden. Eine tatsächliche
Touchfolge dieses neuen Standes wurde noch nicht ausgeführt.

Der Kandidatenpatch verändert diese Navigation nicht. Falls direkter Headerzugriff
überall gewünscht ist, müssen Headerbreite und Marken-/Privatsphärebereiche zusammen
umgeordnet werden. Ein isolierter 144-dp-Zwang könnte andere Headerinhalte überdecken.

## 3. Bestehender 2D-Ersatzpfad: Kugel vor dem Kopf

Pfad: `app/src/main/java/at/heinz/deviceai/ContinuumHologramView.kt`, `onDraw`.
Bei `!native3dDrawn` wird zuerst `avatarRenderer.draw` aufgerufen und erst danach
die nun überlappende Originalkugel gezeichnet. Nur `nativeTexture` erhält die neue
Deckkraft `.62f`. Der Ersatzpfad und die Initialisierungsphase erhalten dadurch
eine andere Ebenenfolge und keine entsprechende Kopftransparenz.

Minimaler Ansatz für den bestehenden Implementierer: Kugel im ausdrücklich
geschichteten Modus vor beiden Kopfpfaden zeichnen; Transparenz im Ersatzpfad
ebenfalls gezielt auf den Kopf anwenden. Den bestehenden Kugelrenderer und dessen
Zustands-/Sprachkopplung behalten. **Kein Fallback-Reparaturpatch hier**, weil diese
Android-Zeichenkette in der vorliegenden Prüfung nicht ausführbar validiert wurde.

## 4. Monitoranschluss: laufende Verdrahtung

Im geprüften Commit bedient `FilamentDocumentStage` das vorhandene Dokument-Mesh
über `Bitmap → baseColorMap`; Engine, Renderer und Loader stammen aus
`FilamentAvatarRenderer`. Das ist noch kein Nachweis aktueller App-Pixel.

Während der Prüfung entstanden im selben Arbeitsbaum `FilamentCockpitStage`,
`CockpitStageFrame`, `CockpitStageView`, `VirtualComputerMonitorView` und
`CockpitMonitorPainter`. Der beobachtete Entwurf verwendet weiter die Avatar-Engine;
die App-Pixel sollen über ein HardwareBuffer-Bitmap und eine dazu passende
Monitorprojektion erscheinen. Die native Rahmendarstellung und App-Bilddarstellung
sind damit getrennte Ebenen. Diese Zwischenstufe wird nicht als fehlgeschlagene
Fertigimplementierung bewertet. Ihr Zusammenspiel, Freigabeentzug, Sitzungswechsel,
Frische, Seitenverhältnis und Rotation benötigen die anschließende Abnahme.

Originalavatar: tatsächlich ermittelter GLB-SHA-256
`c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`;
Runtime-Pin unverändert. Originalasset und Kugelrenderer sind im geprüften
Commitvergleich nicht verändert. Hashbeleg: `asset-check.json`.

## 5. Vorhandene Tests und Renderbelege

- `CinemaButtonMaterialUiTest` sucht weiterhin `jarvis-bottom:system` und erwartet
  `bottom:system`. Das Ziel ist im neuen Acht-Dock entfernt: Der Test muss auf
  ein echtes verbleibendes Dockziel portiert werden und seine DOWN/CANCEL/UP-,
  Pause-/Resume- und Ursprungsprüfungen behalten. Kein grünes Ergebnis behauptet.
- `LuxuryHoloButtonRendererTest` verwendet denselben entfernten Dockschlüssel;
  seine Materialannahmen passen außerdem zum bisherigen Renderer. Die bestehenden
  Schutz-/Druckprüfungen auf den jetzt verwendeten Glasbuttonpfad übertragen.
- `JarvisShellContentTest.hitTestRoutesAllFeatureCardsWithoutAFixedRailAcrossWindowSizes`
  erwartet elf `railNav`-Ziele gegenüber nun acht Dockzielen. Den neuen Vertrag
  acht direkt plus drei unter Mehr prüfen, die Erreichbarkeitsprüfung nicht streichen.
- `JarvisShellLayoutTest` ruft vielfach `create` auf und trifft damit die neue
  `forContent(..., workstation = true)`-Verzweigung nicht. Die obigen Fälle sind
  als Ergänzung im tatsächlichen Workstation-Aufrufpfad sinnvoll.
- Danach vorhandene `JarvisShellStageUiTest`, `ContinuumReferenceCockpitUiTest`,
  `DocumentStageFrameRefreshUiTest`, `DocumentStagePageGeometryUiTest` und
  `AvatarAssetProvenanceTest` über den gemeinsam gesperrten Hostlauf verwenden.

Die vorhandenen Bilder unter `docs/evidence/code55-cinema55-baseline-20261112/`
und `docs/evidence/code55-after785-20261112/` betreffen ältere Commits und ausdrücklich
Robolectric/API28 mit 2D-Kopf. `tools/avatar-host-render` rendert die Original-GLB
in Chromium/SwiftShader und kann einen zusätzlichen Hostnachweis liefern.
Keiner dieser Wege bestätigt Android-Filament, die neue vollständige Szene,
echte Live-App-Pixel oder Fold7-Bildrate/Wärme. Keine neuen Bilder erzeugt.

## Reproduktion

Mit vorhandener Java-Laufzeit und dem bestehenden Kotlin-Compiler-Verzeichnis:

```sh
python3 run_probe.py --repo /path/to/device-ai \
  --revision 080d7f4df9a7b355b1edbe5f876a35bc0d834338 \
  --kotlin-lib /path/to/gradle-8.11.1/lib --output-dir /new/empty/baseline
python3 run_probe.py --repo /path/to/device-ai \
  --revision 080d7f4df9a7b355b1edbe5f876a35bc0d834338 \
  --kotlin-lib /path/to/gradle-8.11.1/lib --output-dir /new/empty/candidate \
  --patch repair.patch
```

Kein Gradle, keine Installation, kein Netzwerk. Der Compiler hat maximal 512 MiB,
die Probe 128 MiB Heap. Patchanwendung erfolgt ausschließlich in einer temporären,
aus dem exakten Commit gelesenen Kopie. `baseline/` enthält den vorausgehenden
kleineren Sieben-Prüfungen-Lauf; maßgeblich sind die beiden Matrixverzeichnisse.
