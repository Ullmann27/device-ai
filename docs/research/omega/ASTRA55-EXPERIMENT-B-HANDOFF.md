# Code55 Experiment B: räumliche Transformationen

SOURCE_INTEGRATED / HOST-VERIFIZIERT. Basis `5f87bdc610944b93d3173a83c5beeea315abeb66`.
Ziel: `work/astra55-cockpit-experiments-20261112` → #799 → #791.
Bestehende EXP-07/11/12; gemeinsame Filament-1.56.0-Szene bleibt erhalten.

Nach offizieller Khronos-/Filament-/Android-Recherche wird genau ein A/B
geprüft: identische lokale Matrizen vor denselben Transform-Aufrufen
überspringen. Gate: mindestens 20 % weniger native Transform-Aufrufe im
eingefrorenen Replay bei identischen lokalen/Weltmatrizen und Projektionen.
Gegenproben: bewegter Elternknoten, Resize, Pitch/Z/Umkehr, veränderlicher
Scratchpuffer, neue Instanz und fehlgeschlagener Upload.

Dateibesitz: Transform-Update-Lane FilamentCockpitStage.kt, kleiner
CockpitTransformUpdates-Helfer, eigene Tests/Runner. Keine Änderung an
Assets, Materialien, Originalringen, Layout, Clock oder Avatarrenderer.
Kandidat deaktivierbar; bei verfehltem Gate nicht übernehmen.
## Anschluss und Entscheidung

Der bestehende Transformblock ist unverändert berechnet; seine neun Aufrufe
gehen durch einen instanzgebundenen Cache. Verglichen werden alle 16
Float-Bitmuster, ohne Epsilon. Jeder erfolgreiche Upload erhält eine eigene
Kopie. Uploadfehler verwerfen auch den vorherigen Cacheeintrag. Nicht endliche
Matrizen werden weitergereicht, aber niemals gespeichert. `cacheTransforms=false`
am internen Stage-Konstruktor stellt den ursprünglichen Schreibpfad her.

Der eigene Harness extrahiert den Transformblock direkt aus Baseline und
Arbeitsquelle. Nur RectF-Datenhülle und TransformManager sind Hostgrenzen.
Aktuelle Produktionsklassen erzeugen die Matrizen; die tatsächlichen beiden
Stage-GLBs liefern die Hierarchie. Alle lokalen und Weltkomponenten werden
pro Frame exakt verglichen. Zusätzlich werden deaktivierter Kandidat,
Elternbewegung, Resize, Seitenmonitor-Sichtbarkeit und Umkehr geprüft.

Quellen und Repro unter `docs/evidence/astra55-experiment-b-scene-20261112/`.
Erster Compilerlauf meldete eine fehlende bestehende Testabhängigkeit
`GlbSkinningRig`; die Quellenliste wurde ergänzt. Der zweite Lauf besteht:
30 Tests PASS, zwölf aktuelle Schlüsselframes 108 → 78 Aufrufe (−27,78 %),
kontinuierliche Fokusfahrt 954 → 642 (−32,70 %). Alle 318.352 geprüften lokalen
und 318.352 Weltkomponenten sind bitgleich; kein Epsilon. Acht Resize-Schritte
bewegen die Kugel-Elternmatrix bei unveränderter lokaler Ringmatrix korrekt.
Der deaktivierte Kandidat reproduziert alle Baseline-Aufrufe in gleicher
Reihenfolge. Entscheidung: Call-Budget-Gate bestanden, Kandidat übernehmen.

Quellcheckpoint: `35122754663f040eb8f4578c38808c4db2e3f81f`; die gemessenen
Dateihashes binden denselben Inhalt, auch wenn der Test vor diesem Commit
begonnen wurde. Der volle kombinierte Androidklassen-Check bleibt beim Root.
Die exakte Stage-Methodenreferenz ist gegen die echte Filament-1.56.0-API
kompiliert. Keine native Filament-Initialisierung im Host-Harness.

Der aktuelle #803-Zulieferer `4a14e0ecaf` ergänzt `frameSubmitted/frameFailed`
vor `update`; diese Methoden gehören nicht diesem Experiment. Beim Zusammenbau
beide Anschlussbereiche erhalten und den realen API-/UI-Gate ausführen.
Rollback: `cacheTransforms=false` beim internen Stage-Konstruktor oder
Anschlusscommit zurücknehmen. Originalassets, Weltformeln, Kamera, Avatar,
Material-/Texturepfad und Clock bleiben unverändert. Gerät NOT_RUN.
Transform-Aufrufbudget ist kein gemessener GPU-/FPS-/Thermikgewinn.
