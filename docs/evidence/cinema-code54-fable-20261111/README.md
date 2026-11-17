# Cinema Code54 – Flugkontakt des Arbeitsfensters und Schalter für den Lesefaden (11.11.2026)

**Zweig:** `claude/cinema-code54-fable-20261111`, Basis `f0120e1b2` (`integrate/omega-apk54-20261111`).
**Codestand der NACHHER-Bilder und Tests:** `f1229b9d2` (der Commit ist als WIP betitelt, weil er vor dem ersten
Hostlauf gepusht wurde; der Code ist seither unverändert und wurde mit den unten genannten Läufen geprüft).

**Art des Belegs:** HOST. Robolectric API 28, native Canvas (Software) auf der JVM, 2D-Ersatzkopf. **Kein Gerät, kein
GPU, kein Filament, kein AGSL, kein Fold7, keine Bildzeiten, keine Wärmemessung** – alles `NOT_RUN`.

## 1. Flugkontakt (sichtbare Verbesserung)

**Ausgangslage im Code:** Zwischen Abheben (140 ms) und Landung (760 ms) ist das native Glas unsichtbar
(`panel.alpha = frame.data`, erst ab 640 ms), die Glasschichten des Konstrukts entstehen erst ab 480 ms. In dieser Zeit
flog nur ein dünner Drahtrahmen ohne Körper und ohne Bezug zum Deck über die hellen Decklinien. Zusätzlich standen die
Kontaktschatten-Ringe der Fokustiefe (`drawFocusDepth`) schon am Ziel, während das Glas noch unterwegs war.

**Änderung (Produktionspfad `JarvisWorkWindow.dispatchDraw`):**

- `HollywoodFuiCinematics.flightContact(frame, openness)` – reine Regel aus dem vorhandenen Konstruktbild:
  `brackets · closeFrame · (1 − smoothstep(openness))`. 0 vor dem Abheben, 0 nach der Landung, kehrt beim Wegfalten
  kurz zurück. Keine eigene Uhr, kein zusätzlicher Frame-Wunsch; reduzierte Bewegung fliegt nie.
- `Painterly.drawFlightContact` zeichnet den Fußabdruck des fliegenden Glases auf dem Deck (gerader Weg ohne
  Bogenhub): Kernschatten, zwei Halbschattenringe und das eigene Licht des Hologramms als feine Akzentlinie unter der
  Unterkante. Mit `EXP_04` hebt der Bogen das Glas sichtbar vom Fußabdruck ab und setzt es darauf ab.
- `drawFocusDepth(…, contact)` – die drei vorhandenen Kontaktringe stehen jetzt unter dem Glas, wo es in diesem Bild
  ist. Bei gelandetem Fenster ist `contact == window`, das Bild ist unverändert.

Kosten: höchstens 1 Füllung, 2 Konturen, 2 Linien pro Bild und nur während des Flugs; keine Allokation von Shadern
(vorhandene `Fill.Solid`). Bildrate, Partikelbudget, Avatar, Kugel und die Wärmekorrekturen aus #773 sind nicht berührt.

**Bilder (links VORHER `f0120e1b2`, rechts NACHHER `f1229b9d2`, gleiche Sequenz `CinemaNativeSequenceUiTest`):**

| Datei | Inhalt |
|---|---|
| `01-inner-flug-400ms-ausschnitt.jpg` | Innen 884×760, Flug, Ausschnitt ×2: Deck unter dem Rahmen abgedunkelt, Lichtlinie am Boden |
| `02-inner-flug-400ms-gesamt.jpg` | dasselbe Bild vollständig |
| `03-inner-flug-520ms-ausschnitt.jpg` | kurz vor der Landung: Kontakt ist fast erloschen |
| `04-cover-flug-400ms.jpg` | Außen 412×915, Flug: Glas hebt sich vom Fußabdruck ab |
| `05-cover-flug-520ms.jpg` | Außen, kurz vor der Landung |
| `06-inner-stehend-2100ms-gesamt.jpg` | stehendes Fenster: unverändert |

Bildvergleich aller 26 Phasenbilder (Schwelle Summe RGB > 24): Außen sind `idle`, `work-140ms`, `work-1180ms`,
`work-2100ms`, `result-*`, `return-650ms`, `returned`, `aborted-return` **pixelgleich** (0 geänderte Pixel); geändert
sind nur `work-280/400/520/650ms`. Innen dasselbe, bis auf 146–178 Pixel der Uhrzeit oben links (Minutenwechsel
zwischen den beiden Läufen, nicht Teil der Änderung).

Bekannte Eigenschaft: Auf dem Innenbildschirm liegt die Lichtlinie während des Flugs (≈ 300 ms) kurz über den
KPI-Kacheln, die das landende Fenster danach ohnehin bedeckt. Die Phasenmarken des Harness sind Zeiten seit dem
Auftragsstart, nicht exakt die Konstruktzeit.

## 2. Lesefaden an EXP_06 gebunden (Besitzerkontrolle)

Stand der Basis (geprüft): EXP_06 (Fensterlicht) und EXP_11 (Band, Quittungspaket) sind bereits durch `3f9c5eba3`
vollständig geschaltet. Offen war nur noch Matrixpunkt 3: der Lesefaden Gesicht→Fenster hatte keinen Schalter.

- `HollywoodFuiStageOverlay.attentionLinkEnabled` schaltet den Faden im Zeichenpfad (`onDraw`), live.
- `JarvisWorkWindowHost.applyCinematics` setzt ihn aus `EXP_06_FACE_LIGHT` (Licht zwischen Fenster und Gesicht);
  die Bezeichnung lautet jetzt „Fensterlicht und Lesefaden am Avatar“. Kein neuer Schalter, kein Bus, keine Uhr.
- EXP_11 bleibt davon unabhängig (`orbLinkSwitchDoesNotHideIndependentReadingAttention` weiter grün).

Einschränkung: Getestet ist der Schalter am Overlay; die eine Zeile Einstellung→Overlay im Host ist nur durch
Codelesen belegt (wie bei den übrigen Host-Zuordnungen in der Matrix).

## 3. Harness

`CinemaNativeSequenceUiTest` nimmt zusätzlich `work-400ms` und `work-520ms` auf (Flugmitte war vorher nicht erfasst).
Für VORHER wurden die Produktionsquellen (`app/src/main`) der Basis mit genau diesem Harness aufgenommen.

## 4. Tests (HOST, Robolectric/JVM)

Lauf auf dem Codestand `f1229b9d2`, `-PcontinuumUiWidgetTests=true :app:testDebugUnitTest`:
**20 Klassen, 115 Tests, 0 Fehler, 0 Errors, 0 übersprungen.**

CinemaNativeSequenceUiTest 2 · JarvisWorkWindowHostViewportUiTest 2 · JarvisWorkWindowLifecycleUiTest 8 ·
JarvisWorkWindowUiTest 8 · JarvisWorkWindowDepthTest 6 · CinemaWindowGazeBridgeTest 10 · Cinema2DepthLightTest 2 ·
CinemaConnectionsTest 7 · CinemaExperimentRuntimeToggleTest 6 · CinemaExperimentWiringTest 1 ·
**CinemaFlightContactTest 5 (neu)** · HollywoodFuiCinematicsTest 5 · **HollywoodFuiConnectionsUiTest 10 (3 neu)** ·
HollywoodFuiConstructTest 9 · HollywoodFuiDepthPlaneTest 2 · HollywoodFuiLiveDataTest 3 ·
HollywoodFuiMaterialRegressionTest 5 · HollywoodFuiWindowUiTest 9 · HollywoodFuiWorkActivityTest 5 ·
JarvisFluidLightTest 10.

Das Muster `*CinematicSettings*` trifft keine Testklasse (es gibt keine dieses Namens).

## 5. NOT_RUN

Gesamte Android-Suite, Gradle-Assemble/APK, Filament auf GPU, AGSL (API ≥ 33), Fold7 innen/außen, Bildzeiten,
Wärme/Energie, TalkBack, echte Modellinferenz, Signierung.
