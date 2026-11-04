# Runde 2: zwei echte lokale Materialvarianten des vorhandenen Kopfes

4. November 2026 · Forschungsbot 3 · ausschließlich `research/round2/avatar/`

## Ergebnis

**V2 ist der bevorzugte Materialkandidat dieser Runde.** Die Irisflächen wirken dunkler und weniger weißlich, die feineren Konturen laufen sichtbar ruhiger über Stirn, Wangen und Mund. Die großen horizontalen Bänder und die darüberliegende grobe Drahtwirkung des Ausgangsassets sind in dieser Variante zurückgenommen. Diese Entscheidung beruht auf den tatsächlich gerenderten Bildern und den nochmals angesehenen Originalreferenzen; es wurde kein Ähnlichkeits-Prozentwert erfunden.

Es ist weiterhin **derselbe vorhandene 3D-Kopf**. Positionsdaten, Normalen, Topologie, UVs, Skin-Gewichte, Rig, Knotenhierarchie und alle 21 Morphziele blieben exakt erhalten. Der Original-GLB wurde nicht geändert. Auch V2 ist keine vollständige Rekonstruktion des Kopfes aus den Referenzbildern: Augenöffnung, Kopfform und Mundinnenraum bleiben Eigenschaften des bisherigen Modells. Die neutralen Bilder sind näher an der gewünschten dunklen holografischen Wirkung, aber nicht als endgültige Referenztreue abgenommen.

### Die beiden unmittelbar prüfbaren Ansichten

- `renders/reference-neutral-comparison.jpg`: Ref-01, Original-GLB, V1 und V2 nebeneinander. 93.205 Byte. Die Referenz hat eine andere Kamera und Beleuchtung; das Bild kennzeichnet diesen Unterschied ausdrücklich.
- `renders/variant-contact.jpg`: Original/V1/V2 jeweils neutral, mit moderatem AA und geschlossenem Blink. Neun tatsächliche GLB-Renderings, 281.266 Byte.

Die PNG-Montagen bleiben ebenfalls vorhanden. Die JPEGs sind kleinere Reviewkopien mit Qualität 85. Sie zeigen dieselben gerenderten Inhalte, lediglich skaliert und verlustbehaftet komprimiert; kein Gesicht wurde nachträglich retuschiert.

## 1. Referenzen und Ausgangspunkt

Die ursprünglichen Frontal-, Dreiviertel- und schrägen Augen-/Kopfansichten `ref-01`, `ref-02` und `ref-03` wurden vor der Materialänderung nochmals tatsächlich angesehen. Ref-01 liefert für diese Runde die klarste Richtung: dunkles Gesicht, zurückhaltende Augenflächen, feine geschwungene Linien und cyan-/violettfarbige Randwirkung. Ref-03 zeigt eine hellere Iriswirkung; die künstlerischen Vorlagen sind deshalb keine numerisch identische Materialvorgabe.

Ausgangspunkt ist unverändert der in Runde 1 gemessene GLB aus PR #491, Commit `e101e393dd6c80803ac9d9ac91fccd9812c4a18d`:

```text
SHA-256: 5f49eface4c828bb7f7564acee751e4d1f232a6e574b5038c6a564de69c9a4a3
Größe:   10.165.644 Byte
```

Die beiden eingebetteten Originaltexturen wurden aus dem GLB gelesen und separat angesehen. Sie enthalten bereits stark sichtbare, weitgehend horizontale Linien. Deshalb wurde die wahrgenommene Grobheit nicht pauschal dem Mesh zugeschrieben. Die wesentliche Änderung dieser Runde sitzt in den tatsächlich eingebetteten Hautatlanten und den Augenmaterialien.

Alle Herkunftsbindungen einschließlich Commit, Repositorypfad, SHA-256 und Referenzdateien stehen in `results/SourceBindings.json`. Bestehende lokale Atlas-/glTF-Skripte wurden zum Quellenabgleich gelesen. Kein Bildgenerator, kein externer Avatar-/Videodienst und kein kostenpflichtiger Dienst wurde verwendet. Referenzpixel sind nicht in die neuen Hauttexturen kopiert worden.

Das dunkle Cockpit, transparente Statuskarten und die KI-/Gehirnkugel unter dem Kinn mit etwa 40–50 % Deckkraft bleiben Gestaltungsvorgaben. Diese Runde baut keine Benutzeroberfläche und stellt keinen angeblichen App-Screenshot her. Die Kugel ist deshalb kein Gegenstand dieser Kopf-Kontaktansicht; es wurde keine bestehende Kugel entfernt.

## 2. Was konkret verändert wurde

Beide Varianten verwenden die vorhandenen UVs und neue lokale, deterministisch erzeugte RGB-PNG-Atlanten mit 2048×2048 Pixeln für Grundfarbe und Emission. Das Material bleibt auf der Haut opak. Die vorhandenen physischen `NeuralThreads` behalten ihre Geometrie und Morphs, erhalten in diesen Kandidaten aber Material-Alpha 0 und keine Emission. Sichtbare Konturen stammen damit aus der Hauttextur, die zusammen mit der bereits vorhandenen Haut verformt wird.

Das ist eine klar begrenzte Materialentscheidung, keine Behauptung einer schon optimierten Laufzeit: Unsichtbare Geometrie wurde nicht entfernt, und Transparenz-/Texturkosten wurden auf dem Fold7 nicht gemessen.

| Parameter | V1: ruhige Konturen | V2: feinere Konturen |
|---|---:|---:|
| Kontur-Feldabstand | 2,8 mm | 2,0 mm |
| Gauß-Breite der Kontur im Feld | 0,20 mm | 0,14 mm |
| Linienverstärkung | 0,60 | 0,62 |
| Emissionsstärke | 1,70 | 2,25 |
| Haut-Rauheit | 0,48 | 0,52 |
| Iris-Metallparameter | 0,25 | 0,65 |
| Iris-Grundfarbe, linear RGB | 0,008 / 0,065 / 0,110 | 0,003 / 0,018 / 0,035 |
| Sklera-Grundfarbe, linear RGB | 0,004 / 0,012 / 0,024 | 0,002 / 0,006 / 0,014 |

Die vollständigen Werte stehen in `scripts/variant_params.json`. „Feldabstand“ bezeichnet den Abstand innerhalb der verwendeten skalaren Konturfunktion. Es ist **keine** Behauptung eines überall exakt gleichen geodätischen Linienabstands auf dem Gesicht. Farbe, Verstärkung und Metallparameter sind bewusste Stilparameter des holografischen Materials.

Die Konturkoordinaten wurden aus dem realen bestehenden UV-Mapping verifiziert. Für diesen GLB gilt `y ≈ -0,3926766647 × v + 0,3926763827`; die größte gemessene Abweichung ist etwa `2,42 × 10⁻⁸ m`. Die U-Koordinate entspricht im gelesenen Float-Datenbestand exakt dem vorhandenen atan2-Wrap um die Y-Achse. Diese Prüfung verhindert, dass die Textur nur zufällig auf einer angenommenen UV-Belegung funktioniert.

Die erzeugten Linien sind eine durchgehende künstlerische Konturfunktion in diesen Koordinaten, mit kontrollierter Biegung im Augen-/Mundbereich. Ein Hilfszylinder dient nur der Texturkoordinate; es entsteht daraus keine neue Kopfgeometrie. Die zusätzliche Randemission wird aus den vorhandenen neutralen Oberflächennormalen abgeleitet und um sechs Texel geglättet. Sie ist fest in der Textur gespeichert und kein blickabhängiger Fresnel-Effekt.

## 3. Sichtbare Qualitätskorrektur während der Runde

Der erste echte Renderdurchgang zeigte unterbrochene und unruhige Linien um Lippen und Augen. Bei der zunächst direkt aus Oberflächenpositionen interpolierten Konturfunktion wirkten sich die problematischen überlappenden/gefalteten Bereiche der vorhandenen UV-Belegung sichtbar aus. Diese Bilder wurden nicht als gelungen präsentiert.

Die Korrektur bestand darin, die Konturlinien analytisch durchgehend in den **verifizierten vorhandenen UV-Koordinaten** zu erzeugen und nur die Randemission separat aus den Normalen zu übernehmen. Im erneuten Rendervergleich sind die vorher sichtbaren Linienabbrüche im Mund-/Wangenbereich beseitigt. Die erste Iteration bleibt mit Erzeugungsscript, Parametern, Manifesten und zwei neutralen Belegbildern unter `iterations/01-position-bake/` nachvollziehbar. Die dortigen Dateien sind Archivbelege; die finalen Herstellskripte liegen unter `scripts/`.

### Visuelle Entscheidung nach der Korrektur

| Ansicht | Befund |
|---|---|
| Original GLB | Helle Augenwirkung, grobe horizontale Bänder und zusätzliche sichtbare Drahtlinien dominieren das Gesicht. |
| V1 | Augen bereits gedämpfter; kontinuierliche geschwungene Linien verbessern die Flächenwirkung. Die hellere Iris bleibt deutlich sichtbar. |
| V2 | Dunklere, zurückhaltendere Iris und feinere Konturen passen von diesen beiden Kandidaten besser zur frontalen Referenz. |
| Moderates AA | Bei beiden Kandidaten ist die tatsächliche vorhandene Mundform sichtbar; die Linien folgen der Haut. Der alte Mundinnenraum bleibt unverändert. |
| Blink | Die vorhandenen Lid-Morphs schließen die Augen. Im Lid-/Brauenbereich bleiben auffällige gebogene Konturbänder sichtbar, die noch gezielt gestaltet werden sollten. |

**V2 wird deshalb als nächste Materialbasis empfohlen, nicht als fertiger Avatar.** Die Augenöffnung des aktuellen Kopfes unterscheidet sich weiterhin von der Referenz. Die Konturen sind geordneter und regelmäßiger als deren organische feine Filamente. Zudem kann die dunklere Iris die Wahrnehmbarkeit kleiner Blickbewegungen beeinflussen; das wurde mit diesen stillstehenden Posen nicht bewertet.

Es wurden keine erfundenen Pixelähnlichkeitswerte, FID-/SSIM-Behauptungen oder „Prozent Originaltreue“ verwendet. Für eine solche Aussage fehlen unter anderem eine kalibrierte gemeinsame Ansicht und ein sinnvoller Abnahmemaßstab.

## 4. Tatsächliche technische Prüfung

`build_variants.py` liest den gepinnten GLB, hängt neue PNG-Daten an und verändert ausschließlich die erforderlichen Material-/Texturreferenzen. Nach dem Schreiben wird jeder Kandidat erneut eingelesen. Für beide Kandidaten wurde verifiziert:

- Der komplette ursprüngliche Binärchunk bleibt bytegenau als Präfix erhalten.
- `accessors`, `meshes`, `nodes`, `skins`, `scenes`, `scene` und vorhandene Animationsangaben sind strukturell identisch.
- Positionen, Normalen, Indizes, Skin-Gewichte, bestehende UVs und sämtliche Morphdeltas behalten dadurch ihre ursprünglichen Bytes.
- Die beiden Meshes mit Morphs behalten jeweils 21 Ziele; Blender importierte jeweils beide mit allen 21 Shape Keys.
- Der Original-GLB besitzt nach Herstellung und Rendern weiterhin seinen ursprünglichen SHA-256.

| Datei | Größe | SHA-256 |
|---|---:|---|
| `variants/v1_soft_flow.glb` | 16.469.888 Byte | `ecead099e3e8312e8211dbfd48228a0af258836476691ce3302a58cf0dc0d6e3` |
| `variants/v2_fine_flow.glb` | 17.400.768 Byte | `60e4107e4577070ef123652501956e6cd41f678c8cd4b65f405ab19d73a07a14` |

Die neuen eingebetteten PNG-Daten umfassen für V1 6.303.980 Byte und für V2 7.234.858 Byte. Die Forschungsdateien behalten absichtlich auch die alten Binärdaten zur lückenlosen Erhaltungsprüfung. **Diese Dateigrößen sind kein Laufzeitspeicherwert und kein fertiges mobiles Exportbudget.** Ein späterer kompakter Export könnte nicht mehr referenzierte Alttexturen entfernen; das ist hier nicht erfolgt.

Das vollständige Messprotokoll enthält `results/variant-manifest.json`. Die Herstellung beider finaler Varianten benötigte auf diesem Host zusammen etwa 10,13 Sekunden. Die frühere falsche Produkt-NOTICE aus Runde 1 wurde weder bearbeitet noch als neue Variantenmetadatei ausgegeben.

## 5. Kontrollierter Hostrender und Livegesprächsgrenze

Die finale Kontaktansicht stammt aus neun tatsächlichen Renderings: drei Assets × drei Posen. Blender 4.3.2, Cycles CPU, zwei Threads, 24 Samples, je 512×576 Pixel, identische orthografische Kamera, identische drei Lichter, Belichtung und Seed. Es gibt keine Bloom-Nachbearbeitung und keine nachträgliche Gesichtsretusche. Gesamtdauer des finalen Renderdurchgangs einschließlich Einrichtung/Import: **83,08 Sekunden**. Die Einzelzeiten und Bildhashes stehen im Render-Manifest.

Die moderate Sprechpose verwendet AA-Gewicht 0,38 und modellierten Kieferinput 0,42. Entsprechend der in Runde 1 geprüften gemeinsamen Öffnung ergibt sich zusätzliches JawOpen `0,42 − 0,38 × 0,8125 = 0,11125` und eine Kieferrotation von 5,04°. Die Blinkpose setzt beide vorhandenen Blinkziele auf 1. Diese konkreten Werte machen den Vergleich reproduzierbar.

Das sind **statische Gesprächsposen**, keine live gesprochenen Silben und keine gemessene Synchronität. Material und UV-Linien sind an das vorhandene Rig/Morphsystem gebunden; der Gesprächs-Director, PCM-Anker, Unterbrechungslogik und Kameraadapter wurden in dieser Runde nicht geändert. Die Hostbilder beweisen weder Filament-Pixelgleichheit noch Bildrate, Energieverbrauch, Wärme, reales TTS-Timing oder Kamera-Blickkontakt auf dem Fold7.

Der nächste konkrete Liveversuch bleibt daher eine kurze Unterhaltung auf dem bestehenden Gerätpfad mit moderatem AA/O/PP, Blink, kleinen Blickänderungen und Unterbrechung. Für die Variante ist dabei zusätzlich sichtbar zu prüfen, ob feine Linien bei Bewegung flimmern, die Iris als Blicksignal erkennbar bleibt und keine störenden Lidkonturen entstehen. Ein gespeichertes Video wird dadurch nicht zum Avatarersatz.

## 6. Reproduzierbare Herstellung

Arbeitsverzeichnis: `/workspace/scratch/9d2785b5ab4e/research/round2/avatar/`.

```bash
python3 scripts/build_variants.py --source /workspace/scratch/9d2785b5ab4e/research/avatar/source/p491/continuum-avatar.glb
BLENDER_USER_CONFIG=/workspace/scratch/9d2785b5ab4e/research/round2/avatar/tmp/blender-config TMPDIR=/workspace/scratch/9d2785b5ab4e/research/round2/avatar/tmp /usr/bin/blender -b -t 2 --python scripts/render_variants.py
python3 scripts/assemble_review.py --reference /workspace/scratch/9d2785b5ab4e/research/avatar/references/ref-01-head-frontal.jpeg
```

`--source` und `--reference` erlauben rekonstruierte Quellpfade auf einem anderen Host. Die Referenz- und Asset-SHAs sind festgehalten. `render_variants.py` unterstützt `--pose neutral|speech|blink|all` und `--samples`, wenn gezielt eine einzelne Ansicht erneut geprüft werden soll. Der Standardlauf erstellt alle neun Bilder. Die Skripte laden nichts aus dem Netz.

| Artefakt | Zweck |
|---|---|
| `scripts/variant_params.json` | Vollständige kontrollierte Material-/Atlasparameter |
| `scripts/build_variants.py` | Lokale GLB-/Atlasherstellung mit Erhaltungsprüfung |
| `scripts/render_variants.py` | Gleicher tatsächlicher Rendereraufbau für alle Assets/Posen |
| `scripts/assemble_review.py` | Unveränderte Renderinhalte mit Beschriftung und JPEG-Kopien |
| `results/SourceBindings.json` | Herkunft, Commits, Pfade und Hashes der gelesenen Inputs |
| `results/variant-manifest.json` | Endgültige Assethashes, Geometrieerhalt, Parameter und Dauer |
| `results/render-manifest-all.json` | Einzelbilder, Posen, Kamera, Renderer und Zeiten |
| `results/review-image-manifest.json` | Maße, Dateigrößen und Hashes der PNG-/JPEG-Montagen |
| `results/artifact-hashes.json` | Hashverzeichnis der finalen Forschungsartefakte |

## 7. Nächste Entscheidung

Für eine weitere lokale Gestaltungsrunde **V2 als Ausgangsmaterial verwenden**, zunächst am neutralen Kopf und bei kleinen Blickbewegungen. Als eng begrenzte nächste Änderung sind die auffälligen Lidkonturen zu beruhigen; danach ist die tatsächliche Augenöffnung/Kopfform gegen die Referenz zu bewerten. Ein neuer beliebiger KI-Kopf würde diese offenen Punkte nicht kontrolliert lösen.

**NOT_RUN:** Android-/APK-Build, native Filament-Ausgabe, Fold7, TTS-/Audio-Abgleich, Laufzeit-FPS oder Speicherbudget, Kamerabetrieb, flüssige Blickanimation, animiertes Aliasverhalten, endgültige Referenzabnahme und Cockpit-/Gehirnkugel-Integration. Kein Produktionsasset, keine Produkt-NOTICE und kein bereits vorhandener Forschungsordner wurden verändert. Es gab keinen Push und keine externe Nachricht.
