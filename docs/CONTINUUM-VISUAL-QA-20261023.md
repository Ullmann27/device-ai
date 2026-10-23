# Continuum: reproduzierbare Referenzmessung

Basis: `93cd4c08bc961e07ea0dc2834e5b9b4d38a41195` (PR #81), isolierter
Zweig `astra/continuum-visual-engineering-20261023`. Experimentbezug:
EXP-02/07 (negative Regressionstests), EXP-06 (Quellprovenienz).
Status: lokal geprüfter QA-Helfer; kein APK- oder Gerätenachweis.
`AGENTS.md`, Experimentvertrag/-register und Issue #41 wurden gelesen.

## Feste Vergleichsbedingungen

Die beiden Referenz-PNGs in `docs/design_targets/continuum-2026-10-22/`
sind nur Messquellen. Referenzpixel werden nicht als App-Texturen verwendet.
Die eingefrorenen Zielregionen `continuum-2026-10-22-v1` stammen aus der
Referenzkalibrierung; sie folgen keinen späteren Änderungen am Renderer.
Der JSON-Bericht speichert ihre Koordinaten und prüft sie bei Wiederverwendung.
Nicht vollständig im Renderbild liegende Regionen heißen `OUTSIDE_RENDER`;
sie erhalten keinen unvollständigen Vergleichswert.

Referenzgröße Telefon: 941×1672, Fold: 1448×1086. Die festen Ursprünge
Telefon `(116,112)` und Fold `(80,40)` wurden anhand der Referenzbilder und
Modellkartenanker geprüft: Die linke Modellkante liegt damit bei ca. 139
bzw. 215 px, die obere bei ca. 770 bzw. 488 px. Das passt zur Zielkomposition;
Fotoperspektive und Rasterung lassen kleine Restabweichungen. Diese Ursprünge
bleiben für Baseline und Kandidaten gleich.

Frisch gerenderte Baseline vom 23.10.2026: Telefon 713×1490, Fold 1294×999,
READY-Zustand, feste Phase 0.4. Die Bilder liegen lokal unter
`/workspace/evidence/visual-20261023/baseline/`.

```bash
python3 scripts/continuum-design-diff.py baseline/phone.png \
  docs/design_targets/continuum-2026-10-22/reference-phone.png baseline/phone-diff \
  --density 1.7306 --scale 1 --offset-x 116 --offset-y 112
python3 scripts/continuum-design-diff.py baseline/fold.png \
  docs/design_targets/continuum-2026-10-22/reference-fold.png baseline/fold-diff \
  --density 1.54 --scale 1 --wide --offset-x 80 --offset-y 40
python3 scripts/continuum-design-diff.py candidate/phone.png \
  docs/design_targets/continuum-2026-10-22/reference-phone.png candidate/phone-diff \
  --density 1.7306 --scale 1 --alignment baseline/phone-diff/report.json
python3 scripts/continuum-design-diff.py candidate/fold.png \
  docs/design_targets/continuum-2026-10-22/reference-fold.png candidate/fold-diff \
  --density 1.54 --scale 1 --wide --alignment baseline/fold-diff/report.json
```

Die Pfade `baseline/` und `candidate/` sind entsprechend dem Artefaktordner
anzupassen. `--alignment` übernimmt ausschließlich die Baselinekoordinaten
und verlangt identische Bildgeometrie, Zuschnitt, Skalierung, Referenzhash,
Skripthash, Zielregionen und Metrikdefinition. Die Kandidatenpixel dürfen sich
ändern. SHA-256 von Eingabebild, Referenz, Skript und gegebenenfalls
Snapshot-Metadaten/Ausrichtungsbericht stehen im Bericht; Toolversionen ebenso.
Nach einer Skriptänderung muss auch die Baseline neu gemessen werden.

## Behobene Messfehler und Grenzen

- Die Feinsuche beginnt mit einem neuen Bestwert: ein heruntergerechneter
  NCC-Wert darf keinen Vollauflösungswert überstimmen. Ohne feste Ausrichtung
  bleibt die Suche verfügbar, ist aber ausdrücklich `auto-exploratory` und
  abhängig von den Kandidatenpixeln. Zwei solche Läufe belegen keine faire
  Verbesserung bei unveränderter Geometrie.
- Übergröße, ungültige Offsets oder inkompatible Metadaten brechen mit Exitcode
  ungleich null ab. Kein stilles Abschneiden und keine schwarzen Auffüllpixel.
- `--metrics` akzeptiert das tatsächliche Snapshot-Schema v1 und prüft
  `windowPx` gegen die PNG-Größe, positive endliche Dichte/Skalierung,
  boolesches `wideLayout` und vier nichtnegative ganzzahlige Insets.
  `null`-Skalierung wird nicht mehr still durch 1 ersetzt.
- Der Snapshot zeichnet `decorView`; `AgentActivity` setzt alle vier Insets
  als Padding. Deshalb werden standardmäßig alle vier Systemleistenabstände
  entfernt, auf beiden Formaten. `--insets none` ist eine explizite, im Bericht
  gespeicherte Alternative. Bereits zugeschnittene Bilder mit den alten
  Fenstermetadaten werden abgewiesen. Die Metadaten enthalten keinen sicheren
  Nachweis für geschlossene Tastatur oder Startansicht: diese Zustände müssen
  vor dem Snapshot visuell geprüft werden.
- Die bisherigen Positionsargumente und `--density/--scale/--wide` bleiben
  verwendbar; `--metrics` bleibt verfügbar, nun mit strenger Validierung.
  Ein hochkant gewachsenes Fold-Layout passt nicht automatisch in die feste
  Querformatreferenz. Die Messung verweigert ein übergroßes Bild; ein separater
  Geräte-/Responsive-Nachweis ist dafür nötig.

## Mehrere unabhängige Beobachtungen

MAE und NCC bleiben erhalten. Zusätzlich werden je Zielregion und insgesamt
bei festen Schwellen berichtet:

- `brightMaskIoU` und beide `BrightCoverage`-Werte: Überlappung und Anteil der
  Pixel mit Pillow-Luminanz ≥64. Das ist eine Helligkeitsmaske, keine semantische
  Hologrammsegmentierung; Schrift/Labels können in der HERO-Region enthalten sein.
- `luminanceHistogramWasserstein`: Abstand der Helligkeitsverteilungen in
  0–255-Stufen; `luminanceProfileL1`: mittlerer Abstand der Zeilen-/Spaltenprofile,
  normiert auf 0–1. Die Profile reagieren auf räumliche Verschiebung auch bei
  identischem Histogramm.
- `edgeDistancePx`: symmetrischer mittlerer euklidischer Abstand zwischen
  Kantenpixeln (FIND_EDGES ≥24, künstlicher Bildrand ausgeschlossen), mittels
  SciPy. SciPy war lokal verfügbar. Ohne SciPy ist der Wert `null` und das
  Backend `UNAVAILABLE`; bei nur einer leeren Kantenmaske ist er ebenfalls
  `null`, mit separat berichteter Belegung. Zwei leere Helligkeitsmasken ergeben
  `brightMaskIoU=null`, keinen vorgetäuschten perfekten Silhouettenvergleich.

Keine Einzelmetrik ist ein Qualitätsurteil: Helligkeitsmaske, Verteilung,
Kantendistanz und sichtbare Überlagerung müssen gemeinsam beurteilt werden.
Ein niedrigerer MAE allein rechtfertigt keine Verbesserungsaussage.

| HERO, feste Baseline | Telefon | Fold |
|---|---:|---:|
| MAE | 39.6 | 44.2 |
| NCC | 0.075603 | 0.055252 |
| Helligkeitsmasken-IoU | 0.419274 | 0.417768 |
| Render / Ziel: helle Fläche | 0.292874 / 0.310256 | 0.279718 / 0.321733 |
| Histogramm-Wasserstein | 3.673051 | 8.850305 |
| Profilabstand | 0.037903 | 0.044150 |
| Kantenabstand, px | 2.866076 | 3.003955 |

Die Gesamt-NCCs lauten 0.157401/0.142084. Die früher dokumentierten
0.335/0.344 waren durch den Auflösungsfehler keine vergleichbaren Gesamtwerte.
Alle Regionen der jetzigen Referenzproportionen wurden vollständig gemessen.

## Prüfung, Fortsetzung, Rollback

`python3 -m unittest discover -s scripts -p 'test_continuum_design_diff.py' -v`
prüft bekannte Translationen einschließlich Randpositionen, den ursprünglich
reproduzierten NCC-Fehler, feste Ausrichtung, veränderte Kandidatenpixel,
falsche Referenz/Geometrie, ungültige Metadaten/Größen und die Reaktion der
Zusatzmetriken auf Verschiebung/Helligkeitsänderung. Der Negativlauf wurde vor
der Implementierung beobachtet; anschließend bestanden 11 Tests. Beide echten
Baseline-Läufe wurden mit `--alignment` wiederholt: identische Metriken und
byteidentische Overlay-, Differenz-, Vergleichs- und Zielregionen-PNGs.

Nächster Schritt: Kandidaten mit demselben eingefrorenen Skript und den
Baseline-Berichten messen; erst danach Android-/Samsung-Rasterung prüfen.
Rollback: ausschließlich diesen QA-Helfer, seinen Test und dieses Dokument
zurücknehmen; keine App-Daten, Gewichte oder Rendererzustände betroffen.
