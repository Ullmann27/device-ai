# Design, Avatar und deutsche Oberfläche — Übergabe 05.11.2026

Status: GERMAN_UI_HOST_VERIFIED / REFERENCE_LAYOUT_PARTIAL / AVATAR_SURFACE_FINDINGS_OPEN / DEVICE_NOT_RUN.

Auftrag: Heinz' Hauptauftrag „Design, Grafik und lebendiger KI-Avatar“ und Zusatzauftrag „Qualität des gesamten
Erlebnisses“ (05.11.2026). Verbindliche Gestaltungsvorlagen: 59509 (Querformat, Fold7 offen/Tablet) und die am
05.11. nachgereichte Hochformatvorlage für das zugeklappte Fold7 bzw. ein normales Handy.

## Quellstand

| Gegenstand | Wert |
|---|---|
| Basis | #526 `codex/continuum-avatar-alive-20261104` @ `5a18f74f18d9b7d927f8c622052369496153829f` (enthält #521) |
| Arbeitszweig | `ccr-4f80075a-ycl67y` |
| Geprüfte Kandidaten | `d497d1b00dab5ea6d337cd784d7b440e0a1ca904`, `3a21a453309b9c645bad80a8ca72917585fbb78f`, `c49248dcbffb4d687ccb5a780bef3f20c21af6f2` (Details und Quellstand je Lauf: `docs/evidence/design-de-20261105/host-tests/host-unit-baseline-vs-candidate.json`) |
| Avatar-GLB | unverändert `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`, 12.038.416 Bytes |
| Neuere Köpfe geprüft | #519 `13c37f5`, #527 `19039e5`, #491 `e101e39` — keine dieser Quellen enthält die Kacheltexte des Fold7-Screenshots |

## 1. Referenzen — was tatsächlich geprüft wurde

| Referenz | Zugriff | Befund |
|---|---|---|
| 59509 (1536×1024) | Vom Besitzer im Auftrag mitgegeben | Gestaltungsziel Querformat; Vergleich mit Host-Vorschau unten |
| Hochformatvorlage Handy/Fold7 zu (866×2076) | Vom Besitzer am 05.11. mitgegeben | Ziel Kompaktlayout; deutliche Abweichung des aktuellen Kompaktlayouts (Lücke L2) |
| Fold7-Screenshot des installierten Stands (1968×2184) | Mitgegeben | Zeigt deutsche Kacheluntertitel („Vorhandene KI-Mitarbeiter“, „Gerät und Laufzeitdaten“), aber englische Kacheltitel und eingebranntes „Continuum Brain“. Diese Texte stehen in **keinem** der geprüften veröffentlichten Zweige (#526, #527, #519, #491, sieben weitere Design-/Integrationszweige) — die installierte Quelle ist nicht auffindbar |
| „Kopfkorrektur v3“ (neun Blender-Ansichten) | Bild mitgegeben | Zeigt einen dunklen Körper im Nacken. Das veröffentlichte GLB `c8b88385…` hat diesen Körper **nicht** (eigene Renderings unten). v3-Datei nicht im Repository |
| Fünf Drive-Clips | **BLOCKIERT** für diese Sitzung | Drive-Download über den Connector bricht ab („message too large“, 6–15 MB); kein Transkript verfügbar. Ich habe die Clips nicht selbst gesehen. Grundlage bleibt die 160-Bilder-Matrix aus #526 (`AVATAR-VIDEO-REFERENCE-MATRIX-20261104.md`), deren Zeitmarken ich nicht unabhängig bestätigt habe |

## 2. Belegte Lückenliste (Ist-Zustand)

| Nr. | Bereich | Befund mit Quelle | Priorität |
|---|---|---|---|
| L1 | Sprache der Oberfläche | Referenz-Cockpit, Seitenleiste, Kacheln, Modul- und Modellkarte, Gedächtnisaktivität waren englisch (`JarvisTiles.kt`, `JarvisCockpitGridContent.kt`, `JarvisMemoryActivityPanel.kt`, `LocalModelStatusPresentation.kt`, `JarvisCockpitPanels.kt`, `CognitiveWorkPresentation.kt`) | **in diesem Stand behoben** |
| L2 | Kompaktlayout (Fold7 zu / Handy) | `JarvisShellLayout.reflow` stapelt Kopf 418 dp, Dialog, Continuum-Kopf, Kugel, Eingabe, 10 Kacheln, 4 Karten → 1.887 dp Gesamthöhe (zwei Bildschirme). Vorlage: eine Bildschirmhöhe mit schmaler Seitenleiste, Kopf mit Kugel auf der Brust, JARVIS-Live-Kennung, Dialogleiste, vier runde Knöpfe, wischbaren Kachel- und Kartenreihen mit Seitenpunkten, Statusleiste | hoch, nächster Schritt |
| L3 | Kopfoberfläche | Eigene Renderings des gepinnten GLB: Projektionsnaht über der Schädeldecke (weißlich verschmiertes Band), dunkle Rückseite mit senkrechter Spiegelnaht, harter Übergang an der Ohrlinie. Geometrie, Rig und 21 Morphziele unauffällig | hoch |
| L4 | Kopfgröße im Cockpit | In 59509 füllt der Kopf etwa drei Viertel der HEAD-Fläche; das Host-Komposit mit Schulterbüste wirkt kleiner. Die Kameraeinstellung liegt in `FilamentAvatarRenderer` und ist nur auf dem Gerät abschließend beurteilbar | mittel |
| L5 | Lebendigkeit | `AvatarIdleMotion`, `AvatarDirector`, `AvatarMorphMapper`, `TtsHologramSignal` vorhanden (Blinzeln, Blick, PCM-Stille, Wortbereichs-Viseme). Hörbare Synchronität, natürliche Blinzelkurven auf Filament und Kamerablick auf dem Fold7 nicht geprüft | hoch, gerätegebunden |
| L6 | Kamerablick | `AndroidAvatarCameraInput` nutzt Camera2 `STATISTICS_FACES` lokal, nur im Vordergrund, ohne Speicherung. Gerätetest Außen-/Innendisplay offen | mittel, gerätegebunden |
| L7 | Word/PDF | Kein DOCX- oder PDF-Erzeuger im App-Quellcode (`PdfDocument`, `wordprocessingml`: 0 Treffer). Q05–Q07, Q10, Q14 können derzeit nicht bestehen | hoch, Fachstrang |
| L8 | Startsequenz Tiefe/Verkleinerung, Ganzkörperfigur, Dokumenttunnel | laut #526-Matrix offen; unverändert | mittel |
| L9 | Eingebrannte Grafiktexte | Kugelbeschriftung jetzt „Continuum / Gehirn“ als echter Text. Die installierte Fold7-Version zeigt ein Rasterbild mit „Continuum Brain“ — Quelle unbekannt (siehe Referenztabelle) | geprüft im veröffentlichten Stand |

## 3. Änderungen in diesem Stand

Durchgängige deutsche Begriffe nach Auftrag, ohne Änderung der Ziel-IDs, Reihenfolge, Routen oder Trefferflächen:

| Ziel | vorher | jetzt |
|---|---|---|
| home | Home | Start |
| chat | Chat | Gespräch |
| memory | Memory | Gedächtnis |
| logic | Logic | Denken und Planen (Kurzform im 108-dp-Knopf: „Denken“) |
| research | Research | Forschung |
| agents | Agents | Assistenten (Kurzform „Helfer“, nur wenn zu schmal) |
| tasks | Tasks | Aufgaben |
| web | Web | Internet |
| learning | Learning | Lernen |
| device | Device Control | Gerätesteuerung (Seitenleiste zweizeilig „Geräte-/steuerung“, Kurzform „Geräte“) |
| system | System Status | Systemstatus |

Außerdem deutsch: Kacheluntertitel, Kopfzeile („Deine KI-Steuerzentrale“, „Deine persönliche KI / auf deinem Gerät“,
„JARVIS-Modus“, „Lokal · Internet gesperrt“), Continuum-Kopf („Ein Gedächtnis für alle deine Geräte“,
„Reden · Denken · Forschen · Handeln · Lernen · Wachsen“), „Kamerawahrnehmung“, „Dein KI-Begleiter auf dem Gerät“,
Modulliste, „Aktueller Ablauf“, „Gedächtnis-Aktivität“ mit deutschen Zeilen, „Modell und Gerät“ mit deutschen Zeilen
und Wärmestufen, Arbeitsmodule in `CognitiveWorkPresentation`, Kugel „Continuum / Gehirn“, Datum immer deutsch
(`Locale.GERMANY`, z. B. „Do., 1. Jan.“), Barrierefreiheitstexte „Device AI Continuum: Start“, „JARVIS-Modus: Darstellung“.

Lesbarkeit: Längere deutsche Begriffe verkleinern ihre eigene Schrift bis höchstens 82 % (Untertitel 88 %), bevor
„…“ gekürzt wird (`JarvisShellRenderer.shrinkToFit`). Zusammengesetzte Wörter in der Seitenleiste werden an der
Wortfuge getrennt statt abgeschnitten (`JarvisTiles.railLines`).

Werkzeuge: `tools/host-jvm-harness` (Kompilierung und Unit-Suite ohne SDK), `tools/avatar-3d/web-review`
(Mehransichten des gepinnten GLB mit three.js in Chromium), `GermanCockpitPreview.kt` (Cockpit in drei Fenstergrößen
mit Produktionszeichencode).

## 4. Tatsächlich ausgeführte Prüfungen

| Prüfung | Ergebnis | Art |
|---|---|---|
| Kompilierung aller 436 Haupt-Quelldateien | Exit 0 | Host-JVM mit android-all, Stubs (siehe Harness-README) |
| Vollständige Unit-Suite, Basis #526 unverändert | 2.639 Tests, 17 übersprungen, 9 Fehler | Host-JVM |
| Vollständige Unit-Suite, dieser Stand | 2.639 Tests, 17 übersprungen, **dieselben** 9 Fehler | Host-JVM |
| Angepasste Erwartungen | 7 Testdateien: nur Label-Texte auf die neuen deutschen Begriffe; Reihenfolge, Ziele, Werte unverändert geprüft | — |
| Cockpit-Vorschau 960×640, 411×960, 832×750 dp | erstellt, `docs/evidence/design-de-20261105/previews/` | Java2D-Produktionszeichnung + **Host-Komposit** des GLB-Kopfs; kein Filament, kein Gerät, leerer Datenzustand |
| GLB-Mehransichten (11) | erstellt, `docs/evidence/design-de-20261105/glb-renders/` | three.js r170 / SwiftShader; Beleuchtung weicht von Filament ab |

Die 9 Fehler beider Läufe sind Umgebungsgrenzen und keine AGP-Ergebnisse: LiteRT-Stub (2), fehlende Android-Laufzeit (2),
root ignoriert Schreibschutz (1), Standardzeichensatz (1), Benchmark-Fixturepfad (3).

**NICHT AUSGEFÜHRT:** AGP-Unit-Suite, Robolectric-UI-Suite (`src/uiTest`), Lint, APK-Bau, Gerät (kein ADB-Gerät,
kein Android-SDK erreichbar). Alles Gerätebezogene (Filament-Pixel, Ton, Lippensynchronität, Kamerablick, Fold-Wechsel,
Leistung) bleibt offen.

## 5. Nächste Schritte (in dieser Reihenfolge)

1. **L2 Kompaktlayout nach Hochformatvorlage** in `JarvisShellLayout.reflow` (COMPACT) samt Trefferflächen in
   `ContinuumHomeView`; wischbare Reihen brauchen eine Gestenbehandlung im View und eine Robolectric-Prüfung.
2. **L3 Kopfoberfläche**: Nur Texel außerhalb der frontalen Projektion neu backen (Schädeldecke, Hinterkopf, Ohrlinie),
   Gesichtstexel bytegleich lassen; GLB, NOTICE und `AvatarGlbContract`-Hash gemeinsam aktualisieren; Vorher/Nachher
   in denselben 11 Ansichten.
3. **L7 Word/PDF** an den zuständigen Fachstrang (C1) übergeben; grafischer Anschluss (Dokumenteingang, Verarbeitung,
   bestätigtes Ergebnis) bleibt bei mir.
4. Gerätenachweise L4–L6 sobald ein Fold7 per ADB erreichbar ist.

## 6. Rücknahme und Grenzen

Rücknahme: diesen Zweig aus der Integration auslassen oder die Label-Commits zurücknehmen; keine Daten-, Paket-,
Signatur-, Berechtigungs- oder Modelländerung. Kein Main-Merge, keine Installation, kein Release, kein bezahlter Dienst.
Private Originalvideos und die vollständigen Besitzervorlagen sind nicht im Repository.

## 7. Design-Nachbesserung „Kopf frei, lebendige 59509-Szene“ (WIP, nicht abgenommen)

| Commit | Inhalt |
|---|---|
| `3a21a45` | Sprachfeld links unten (Ref.-x 256–540), freie Kopfzone 548–800; kräftigere Glas-/Neonflächen und Hintergrund |
| `aaffac0` | Echte Alphamaske (DST_IN, nur TextureView-Kind) statt rechteckiger Büstenkanten |
| `c49248d` | Verankerte Nahaufnahme im `FilamentAvatarRenderer` (asymmetrische Ortho-Projektion, Anker 0,70, Ausschnitt 0,92), Kopfbühne hinter dem Sprachfeld; animierte Energieflüsse (`JarvisEnergyFlows`) und Kopf-Aura auf der Bilduhr der Hologramm-Ansicht; Gehirnkugel mit Magenta/Violett/Cyan-Ringen, pulsierendem Kern, zweiter Schale, Lichtsäulen, beleuchtetem Sockel |

Messung (Host-Vorschau gegen 59509, gleiche Bereiche): Helligkeit 0,253 → 0,346 (Ref. 0,351), Farbigkeit 0,177 → 0,241
(0,258), Neonanteil 6,1 % → 9,6 % (12,0 %). Kopf Scheitel–Kinn ≈ 47 % → ≈ 65 % der Hero-Höhe (Ref. ≈ 80 %, dort
überlappt die Karte den Hals; hier bleibt der ganze Kopf frei).

**Größter verbleibender Unterschied — Avatar-Material:** A zeigt ein leuchtendes Linienhologramm mit sehr feinen
hellen Strukturen und kräftigem Randlicht; das gepinnte GLB trägt einen dunkleren gebackenen Atlas. Nächster Schritt:
Atlas außerhalb der bestätigten Gesichtszüge neu backen (feine Linien, Rückseite/Schädeldecke ohne Naht, Lücke L3)
und Fresnel-Randlicht im Filament-Material; GLB, NOTICE und `AvatarGlbContract`-Pin gemeinsam ändern. Das braucht
Filament-Pixel auf dem Gerät als Abnahme.

Weitere offene Punkte: Lichtimpulse entlang der Menüränder und Übergänge zwischen Arbeitsflächen (gemeinsamer Takt
statt Shell-Dauerneuzeichnung), Kompaktlayout nach Hochformatvorlage, Fold7-Bildzeiten/Speicher/Temperatur.
Android-Emulator und Gerät: in dieser Umgebung nicht verfügbar (kein SDK, `dl.google.com` gesperrt).

### Prüfbefehle, Status und Rücknahme zu Abschnitt 7

Befehl (Host-JVM, kein AGP): `cd tools/host-jvm-harness && gradle --no-daemon test -Prepo=<repo> -Plibs=<libs> -PmainList=<Hauptquellen> -PtestList=<Testquellen>`; nur UI-Paket: `-PtestList` = `app/src/test/java/at/heinz/deviceai/ui/*.kt`.

| Quellstand | Lauf | Ergebnis |
|---|---|---|
| `3a21a453309b9c645bad80a8ca72917585fbb78f` | volle Suite | 2.640 Tests, 9 Fehler = Basis-Umgebungsfehler, 17 übersprungen: **PASSED gegenüber Basis** |
| `3a21a453…` | UI-Paket inkl. `theDialogueNeverCoversTheHeadInAnyReferenceWindow` | 178/178 **PASSED** |
| `c49248dcbffb4d687ccb5a780bef3f20c21af6f2` | volle Suite | 2.649 Tests, dieselben 9 Umgebungsfehler: **PASSED gegenüber Basis** |
| `c49248dc…` / `927d0c1` | UI-Paket inkl. `JarvisLivingSceneTest` (5 Fälle) | 183/183 **PASSED** |
| alle | AGP-Unit, Robolectric-UI, Lint, APK, Gerät | **NOT_RUN** |

Kopfgröße: Scheitel bis Kinn ≈ 65 % der Hero-Höhe (gleicher Wert in `JarvisCockpitGrid`-KDoc).

Rücknahme der Geometrie ohne Revert: `JarvisCockpitGrid.HEAD_LEFT = 250f`, `DIALOG_RIGHT = 640f`, `HEAD_ANCHOR_X = .5f`,
`HEAD_CLOSE_UP = 1f`; dann sitzt die Kopfbühne wieder im alten HEAD-Bereich und `Shell.headStage` deckt sich mit ihm. Die
Szenenebenen lassen sich einzeln über `JarvisEnergyFlows.draw(enabled = false)` und `JarvisHeadAura.draw(enabled = false)`
abschalten; das Ausblenden der Zähne über `FilamentAvatarRenderer.HIDDEN_NODE_PREFIX`.

Laufzeitkosten: Die Szenenebenen verwenden Fill-Instanzen erneut (`JarvisSceneFills`). Shader entstehen nur bei
Geometrieänderung oder bei einem 1/128-Farbschritt (≈ 0,2 s). Die Kopfmaske hält ihre drei Gradienten pro Kopfrahmen;
Kurvenpunkte schreiben in ein wiederverwendetes Array.

## 8. Sitzungsende 05.11.2026 — Stand für C1

Letzter Code-Commit: `c195d73` (Kugelkern-Verstärkung, Test `realPulseAndProcessingBrightenTheOrbCore`). Host-UI-Paket 184/184 PASSED;
volle Suite zuletzt auf `c49248dc…` ohne neue Fehler gegenüber der Basis. Alle sechs gitar-Review-Befunde auf #535 behoben und
geschlossen. Zähne werden nicht gerendert (`927d0c1`), GLB `c8b88385…` bytegleich.

Nächste Schritte in dieser Reihenfolge:
1. Gerätenachweis, sobald `dl.google.com` freigegeben oder ein Fold7 per ADB erreichbar ist: Filament-Kopf mit Rahmung und Maske,
   Bildzeiten, Ton und Lippenbewegung.
2. Avatar-Material: Linienhologramm, Randlicht, Atlas-Naht an Schädeldecke und Rückseite, heller Keil an der inneren Lippe.
3. Gehirnkugel größer, mit feineren Windungen; Leuchtimpulse an den Menürändern.
4. Kompaktlayout nach Hochformatvorlage; Bewegungsmodell (Minimum-Jerk, Blinzelstatistik, Sakkaden); Sprachauswahl DE/EN.
