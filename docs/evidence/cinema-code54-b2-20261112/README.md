# Cinema Code54 B2 – Flugkontakt nicht über KPI-Kacheln, EXP_06-Kopplung geprüft (12.11.2026)

**Zweig:** `claude/cinema-code54-b2-20261112`, Basis `1e724dc2b` (Kopf von PR #785, Fable 5.2, auf Code54 `f0120e1b2`).
**Codestand der NACHHER-Bilder und Tests:** `f0b219ae6`.

**Art des Belegs:** HOST. Robolectric API 28, native Software-Canvas auf der JVM, 2D-Ersatzkopf. **Kein Gerät, kein
GPU, kein Filament, kein AGSL, kein Fold7, keine Bildzeiten, keine Wärmemessung** – alles `NOT_RUN`.

## 1. Befund des Besitzers: Decklicht quert die KPI-Kacheln

**Ausgangslage (`1e724dc2b`):** Der Flugkontakt aus PR #785 (Kernschatten, zwei Halbschattenringe und die feine
Akzent-Lichtlinie unter dem fliegenden Glas) wurde nur gegen die geschützten Bereiche (Gesicht, Kugel, Dialog)
geschnitten. Auf dem Innenbildschirm liegt der Fußabdruck während des Flugs über der Statusleiste: Lichtlinie und
Abdunklung lagen auf den KPI-Kacheln (RAM, GPU). Gemessen mit dem neuen Hosttest auf der Basis: Lichtpixel über dem
KPI-Rechteck in **jedem** 16-ms-Bild von `work-280ms` bis `work-504ms` (62 … 678 Pixel je Bild); der Test schlägt dort
erwartungsgemäß fehl.

**Änderung (klein, vorhandene Geometrie):**

- `JarvisWorkWindowHost.plan` gibt dem Fenster zusätzlich `deckLabels`: die beschrifteten Deckzeilen, die bei einem
  Arbeitsauftrag **auf dem Deck bleiben**. Die Regel ist die vorhandene Choreografie, keine neue Liste:
  `keepsFlightContactClear(slot) = slot != AVATAR && !JarvisCinemaEntrance.fliesAwayOnWorkOrder(slot)` –
  ergibt genau Statusband mit KPI-Kacheln (`STATUS`), Auftragszeile (`INPUT`), Dialog, Kopfzeile, Continuum-Kopf und
  Kamerastatus. Die Avatarbühne zählt nicht (Gesicht und Kugel sind ohnehin geschützt), weggeflogene Kacheln, Zeilen,
  Arbeitsboxen und das Dock zählen nicht.
- `JarvisWorkWindow.Plan` / `open(…, deckLabels)` / `place` / Refit tragen diese Rechtecke mit (auch nach Falten/Drehen).
- `JarvisWorkWindow.dispatchDraw`: nur der Aufruf `drawFlightContact` wird mit `clipOutRect` um diese Rechtecke
  gezeichnet. Fußabdruck und Decklicht liegen damit nur auf freiem Deck.

**Unverändert:** stehendes Fenster, Fokustiefe (`drawFocusDepth`), geschützte Bereiche, Glas, Avatar, Kugel, die
Wärmekorrekturen aus #773, eine Uhr; keine neuen Engine- oder Shaderdateien, keine Allokation pro Bild (die
Rechteckliste entsteht beim Planen).

**Bilder (links VORHER `1e724dc2b`, rechts NACHHER `f0b219ae6`, gleicher Harness `CinemaNativeSequenceUiTest`):**

| Datei | Inhalt |
|---|---|
| `01-inner-flug-280ms-ausschnitt.jpg` | Innen, Flug 280 ms, ×2: Lichtlinie an der Oberkante der KPI-Reihe entfällt |
| `02-inner-flug-400ms-ausschnitt.jpg` | Innen, Flug 400 ms, ×2: vorher Linie und Abdunklung über RAM/GPU, nachher Kacheln sauber |
| `03-inner-flug-520ms-ausschnitt.jpg` | Innen, kurz vor der Landung: Restabdunklung auf den Kacheln entfällt |
| `04-inner-flug-400ms-gesamt.jpg` | dasselbe Bild vollständig (Fußabdruck über dem freien Deck bleibt) |
| `05-cover-flug-520ms-ausschnitt.jpg` | Außen 412×915, kurz vor der Landung, Statusband |
| `06-inner-stehend-2100ms-gesamt.jpg` | Kontrolle: stehendes Fenster unverändert |

Bildvergleich aller 26 Phasenbilder (Schwelle Summe RGB > 24): Außen sind alle Phasen **pixelgleich** außer
`work-520ms` (3594 Pixel im Statusband, y 809–869). Innen geändert nur `work-280/400/520ms` (Bereich bis y 729);
alle übrigen Innenphasen unterscheiden sich nur in 326–582 Pixeln der Uhrzeit oben links (x 22–85, y 16–33;
Minutenwechsel zwischen den Läufen, nicht Teil der Änderung). `work-650ms` ist innen pixelgleich bis auf die Uhr.

**Bekannte Grenze (bewusst):** Das Dock (`BOTTOM_NAV`) fliegt beim Auftrag weg und ist bei `work-280ms` noch blass
sichtbar; es ist nicht ausgeschlossen, weil sonst der Fußabdruck über dem danach leeren Dockbereich fehlen würde.
Falls gewünscht: eine Zeile (`BOTTOM_NAV` in `keepsFlightContactClear`). Der Drahtrahmen des Konstrukts selbst
(Overlay) ist nicht Teil des Flugkontakts und wurde nicht verändert.

## 2. EXP_06 – „Fensterlicht und Lesefaden am Avatar“

Geprüft (Code und Tests):

- **Lesefaden:** `JarvisWorkWindowHost.applyCinematics` setzt `fui.attentionLinkEnabled` aus `EXP_06_FACE_LIGHT`;
  der Listener reagiert auf jeden Schlüssel `exp52.*` → live. Jetzt am **echten Host** getestet (AgentActivity,
  Arbeitsszene): Standard AN, Schalter AUS → `attentionLinkEnabled == false` und das Overlay-Bild verliert den Faden,
  wieder AN → exakt dasselbe Bild wie vorher; EXP_11 bleibt unberührt. Ein gespeichertes AUS greift schon beim Öffnen.
- **Fensterlicht im Gesicht:** `ContinuumHologramView` liest `experimentEnabled(EXP_06)` in jedem Bild und übergibt es
  als `windowLightEnabled` an `FilamentAvatarRenderer`; `CinemaWindowLightState.resolve` liefert bei AUS kein Licht,
  auch wenn die Blickbrücke ein Fensterlicht veröffentlicht hat (der frühere Matrixbefund ist damit erledigt). Getestet
  über Einstellung → `resolve` (AUS kein Licht, AN Licht mit Akzent). Filament selbst: `NOT_RUN` (nur GPU).
- **Standard / Persistenz:** Der Speicherschlüssel ist `exp52.EXP_06_FACE_LIGHT` (Enum-Name, nicht die Bezeichnung) –
  die neue Bezeichnung ändert nichts am gespeicherten Wert. Nie berührt → AN (Neuinstallation und bereits migrierte
  Installation ohne Eintrag). Ein bewusstes AUS bleibt nach der einmaligen Code52-Migration AUS.
- **Bezeichnung:** EXP_06 schaltet genau diese zwei Dinge (sonst keine Verwendung im Code). „Fensterlicht und
  Lesefaden am Avatar“ trifft zu; keine Änderung nötig.
- Hinweis: Wer EXP_06 vor Code54 bewusst ausgeschaltet hatte, verliert jetzt auch den Lesefaden – das entspricht der
  neuen Bezeichnung im Schalterdialog.

## 3. Tests (HOST, Robolectric/JVM)

Neu bzw. erweitert:

- `CinemaFlightDeckLabelsUiTest` (neu, 3): echter Host innen 884×760; kein Decklicht im KPI-Rechteck in jedem Bild
  `work-280…650ms` (Glas selbst ausgenommen), Flugkontakt nachweislich aktiv (Spitze > 0,5), Statusband ist Decklabel,
  Avatarbühne nicht; EXP_06 live AUS/AN über den Host; gespeichertes AUS beim Öffnen.
- `HollywoodFuiConnectionsUiTest.flightContactNeverLightsALabelledDeckTile` (Fixture, jedes Bild 280…650 ms).
- `CinemaFlightContactTest.flightContactKeepsTheLabelledDeckRowsThatStayClear` (Slot-Regel).
- `CinemaExperimentRuntimeToggleTest.exp06DefaultPersistenceAndFaceLightGate`.

**Gegenprobe auf der Basis:** Produktionscode von `1e724dc2b` mit dem neuen Hosttest:
`flightContactNeverLightsTheKpiTilesWhileTheWindowFlies` **FEHLGESCHLAGEN** (Lichtpixel 280–504 ms, siehe oben),
die beiden EXP_06-Hosttests grün (die Zuordnung bestand schon, war nur nicht getestet).

**Regressionslauf auf `f0b219ae6`** (`-PcontinuumUiWidgetTests=true :app:testDebugUnitTest`, nur diese Klassen):
**22 Klassen, 126 Tests, 0 Fehler, 0 Errors, 0 übersprungen.**

CinemaFlightDeckLabelsUiTest 3 · CinemaNativeSequenceUiTest 2 · JarvisWorkWindowHostViewportUiTest 2 ·
JarvisWorkWindowLifecycleUiTest 8 · JarvisWorkWindowUiTest 8 · CinemaWindowGazeBridgeTest 10 ·
CinemaWindowLightGateTest 5 · Cinema2DepthLightTest 2 · CinemaConnectionsTest 7 · CinemaExperimentRuntimeToggleTest 7 ·
CinemaExperimentWiringTest 1 · CinemaFlightContactTest 6 · HollywoodFuiCinematicsTest 5 ·
HollywoodFuiConnectionsUiTest 11 · HollywoodFuiConstructTest 9 · HollywoodFuiDepthPlaneTest 2 ·
HollywoodFuiLiveDataTest 3 · HollywoodFuiMaterialRegressionTest 5 · HollywoodFuiWindowUiTest 9 ·
HollywoodFuiWorkActivityTest 5 · JarvisFluidLightTest 10 · JarvisWorkWindowDepthTest 6.

## 4. NOT_RUN

Gesamte Android-Suite, Gradle-Assemble/APK, Filament auf GPU (Fensterlicht im Gesicht real gerendert), AGSL
(API ≥ 33), Fold7 innen/außen, Bildzeiten, Wärme/Energie, TalkBack, echte Modellinferenz, Signierung.
