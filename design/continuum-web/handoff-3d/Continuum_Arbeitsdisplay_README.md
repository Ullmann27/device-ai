# Continuum – editierbares 3D-Arbeitsdisplay

Ein eigenständiger Displaykörper für die räumliche Continuum-Szene. Das Modell besitzt eine getrennte Metallfassung, eine optische Frontscheibe, eine zurückgesetzte Inhaltsfläche, echte Materialstärke und eine ausgearbeitete Rückseite.

**Dies ist ein exportiertes 3D-Asset.** Die vorhandene Website verwendet für ihre funktionierende Arbeitsfläche weiterhin HTML, CSS und SVG. Das neue GLB ersetzt diese Oberfläche noch nicht zur Laufzeit und ist nicht als bereits integriertes Android-Modell ausgegeben. Kopf und Energiekugel wurden für diesen Arbeitsschritt weder geladen noch verändert.

## Dateien

| Datei | Zweck |
| --- | --- |
| `Continuum_Arbeitsdisplay.blend` | Editierbare Blender-Quelle mit getrennten Objekten, Texten, Kurven, Materialien, Bevel-Modifikatoren und Renderstudio |
| `Continuum_Arbeitsdisplay.glb` | Portables Display-Asset, ohne Studioboden, Kamera und Studiolichter |
| `build_continuum_workdisplay.py` | Reproduzierbarer Aufbau und Export |
| `fonts/` | Zwei lokal verwendete Schriftdateien für die reproduzierbare Beschriftung; in der Blender-Datei ebenfalls gepackt |
| `Continuum_Arbeitsdisplay_Vorne.png` | Tatsächlich gerenderte Dreiviertelansicht der Vorderseite |
| `Continuum_Arbeitsdisplay_Hinten.png` | Tatsächlich gerenderte Dreiviertelansicht der Rückseite |
| `Continuum_Arbeitsdisplay_Struktur.json` | Objekt-, Material- und Datenstruktur |
| `Continuum_Arbeitsdisplay_GLB_Pruefung.json` | Ergebnis der ausgeführten GLB-Strukturprüfung |

## Aufbau und Maße

Die geprüften GLB-Abmessungen betragen **1,20 m Breite × 0,74 m Höhe × 0,04725 m Tiefe**. Die Ecken sind geometrisch gerundet. Die Fassung besitzt zusätzliche kleine Bevels für Lichtreflexe.

Die Blender-Datei enthält fünf getrennte Baugruppen:

1. `01_Metallfassung_und_Materialstaerke`: Hauptfassung, polierte optische Lippe, gebürstete Frontfassung und separate Seitenwand.
2. `02_Optisches_Rauchglas`: dünne, blau getönte Frontscheibe mit Principled-Transmission.
3. `03_Rueckplatte_und_Bahnen`: strukturelle Graphitplatte, sechs technische Lichtbahnen, Befestigungspunkte, vertiefte Details und rückseitige Beschriftung.
4. `04_Inhalt_Sechs_Phasen_ENTWURFSWERTE`: Inhaltsuntergrund, Achsen, Phasenbalken und Beschriftungen. In Blender bleiben Texte und Kurven editierbar.
5. `05_Cyan_Lichtkanten`: voneinander getrennte äußere, innere und rückseitige Lichtkanten.

Die Oberflächen verwenden 14 benannte Materialien auf Basis von Principled BSDF. Metallanteil und Rauheit sind exportiert; die Glasfläche verwendet Alpha-Blending und Transmission. Das GLB enthält 64 Nodes, 63 Meshes und 103.508 Dreiecke. Dazu gehört die für den GLB-Export in Geometrie umgewandelte Beschriftung. Die originale Blender-Datei behält ihre editierbaren Textobjekte.

Koordinaten: In Blender ist X horizontal und Z vertikal; die Vorderseite zeigt nach −Y. Das GLB wurde mit Y als Hochachse exportiert. Der gemeinsame Ursprung liegt in der Mitte des Displays.

## Die sechs Phasen

Diese Zahlen entsprechen den Entwurfswerten der Vorschau. Sie beschreiben die geplante Choreografie und sind keine gemessenen KI- oder Gerätezeiten.

| Phase | Beginn | Ende |
| --- | ---: | ---: |
| Aufmerksamkeit | 0 ms | 120 ms |
| Raum schaffen | 90 ms | 260 ms |
| Licht wird Form | 180 ms | 480 ms |
| Einflug | 360 ms | 760 ms |
| Inhalt | 620 ms | 900 ms |
| Arbeit | 900 ms | 950 ms |

Jeder Phasenbalken trägt seine Anfangs- und Endzeit außerdem als Objektmetadaten. Die Inhaltsbaugruppe lässt sich für andere Dokumente ausblenden oder austauschen.

## In Blender verwenden

`Continuum_Arbeitsdisplay.blend` öffnen. Die fünf Baugruppen befinden sich unter `CONTINUUM_Arbeitsdisplay_ASSET`. Die Sammlung `STUDIO_NUR_RENDER_NICHT_GLB` enthält ausschließlich Kamera, Boden und Licht für die Vorschauaufnahmen.

Für eine bestehende Blender-Szene kann die Asset-Sammlung aus der `.blend`-Datei angehängt werden. Alternativ das GLB über den glTF-Import laden. Das `.blend` ist die geeignete Quelle für Änderungen an Schrift, Bevels, Kurven und Materialparametern; das GLB dient dem Austausch.

## In Spline importieren

`Continuum_Arbeitsdisplay.glb` in den Spline-Viewport ziehen oder über **Open / Import** auswählen. Die offizielle Spline-Dokumentation führt GLB ausdrücklich als unterstütztes Importformat auf. Der Import des konkreten Assets in eine angemeldete Spline-Sitzung wurde in diesem Arbeitsschritt nicht ausgeführt.

Nach dem Import die Frontseite und die benannten Materialflächen prüfen. Die Blender-Studiobeleuchtung ist absichtlich kein Bestandteil des GLB; in der Zielszene wird deshalb eigenes Licht benötigt. Die erzeugte Principled-/glTF-Materialstruktur ist enthalten, ein identischer Renderlook in einer anderen Engine wird nicht behauptet.

Quellen für den Importweg:

- [Spline: How to Import Content](https://docs.spline.design/importing-content/how-to-import-content)
- [Spline: PBR Materials](https://docs.spline.design/materials-shading/basics/pbr-materials)

## Erneut erzeugen

Getestet mit **Blender 4.3.2**. Den Python-Code und den benachbarten Ordner `fonts` zusammen verwenden:

```bash
blender -b --python build_continuum_workdisplay.py -- --output-dir ./export
```

Nur Modell und Austauschdatei, ohne neue Vorschaurender:

```bash
blender -b --python build_continuum_workdisplay.py -- --output-dir ./export --skip-renders
```

Die beiden gelieferten Ansichten wurden mit **Cycles auf der CPU, 32 Samples und 960 × 600 Pixeln** gerendert und anschließend angesehen. Die lokale Blender-Build enthält keinen OpenImageDenoise-Support; deshalb ist Denoising ausgeschaltet. Das sichtbare Sample-Rauschen der Bilder ist kein Bestandteil der Geometrie oder der Materialien.

## Ausgeführte Prüfung

Die GLB-2.0-Datei wurde eingelesen. Header, eingebetteter Binärpuffer, BufferView-Grenzen, Mesh-/Materialreferenzen, Positionsgrenzen, sechs Phasenwerte und Glas-Transmission wurden geprüft. Das Asset benötigt keine externen Dateien. Studioobjekte sind im GLB nicht enthalten. Die Vorder- und Rückseite wurden in den beiden tatsächlich erzeugten PNG-Dateien visuell kontrolliert.

Eine Android-Laufzeitprüfung oder ein Spline-Import wird mit dieser Prüfung nicht behauptet.
