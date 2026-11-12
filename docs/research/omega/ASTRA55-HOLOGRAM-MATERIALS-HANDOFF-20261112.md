# Astra55 – Cockpit-Materialien, 12.11.2026

Status: **PORTIERT / Geometrie und API-Kompilierung geprüft / GERÄTEBESTÄTIGT: NEIN**.
Basis: `9ffd3b193`, Ziel: vorhandene Code55-Cockpit-Integration.
Arbeitszweig: `work/astra55-hologram-materials-20261112`.

## Änderung

- Der bestehende Cockpit-GLB verwendet wiederverwendete, echte Bevel-Geometrie für Metall und Rauchglas. Die ursprünglichen Szenenanker `cockpit-room` und `cockpit-monitor` bleiben erhalten.
- Seitliche Glasfinnen, getrennte Glaslagen und äußere Prismenkanten geben Architektur und Monitorrahmen sichtbare Tiefe. Die Farbteilung bleibt Cyan links und Violett rechts.
- Konstantes sphärisch-harmonisches Umgebungslicht ergänzt das vorhandene gerichtete Filament-Licht. Die Ressource wird mit der Szene freigegeben. Keine zusätzliche Engine, Texturwarteschlange, Animation oder Bildquelle.
- Native Monitorpixel, deren Seitenverhältnis und Projektion werden nicht verändert. Die sichtbaren neuen Monitor-Glasstreifen und Lichtfänger liegen außerhalb der Inhaltsöffnung. Originalavatar und Originalkugel bleiben unverändert.
- Bloom und bestehende Cinematic-Pfade bleiben erhalten. GPU-Zeit und Wärme sind nicht gemessen.

## Prüfungen

- `python tools/cockpit-workstation/build_scene.py`: identische Bytes bei Wiederholung.
- GLB-Header, interne Puffer, eindeutige Knoten, beide Szenenanker, endliche Einträge, Einheitsnormalen, Indexgrenzen und nach außen gerichtete Dreiecke: **PASS**.
- Asset-Pin und 32.768-Byte-Grenze: **PASS**. Ergebnis: 14.712 Bytes, 83 Knoten, 81 Renderables, neun geteilte Meshes, 2.508 instanzierte Dreiecke.
- SHA-256: `e3328ea08314e3f6527782ba00d7715005104f00edf47cf85ede82295d4fb094`.
- `git diff --check`: **PASS**.
- API-Signaturen für `IndirectLight.Builder.irradiance(int,float[])`, `Scene.setIndirectLight` und `Engine.destroyIndirectLight` im vorhandenen Filament-1.56.0-Artefakt geprüft.
- `FilamentCockpitStage.kt` und `CockpitStageFrame.kt` auf `4d61bf8575`: **Kotlin-Kompilierung PASS**, Exit 0, keine Diagnosen. Kotlin-Compiler 2.4.0, JVM-Target 17, echtes Android-36-`android.jar`, Filament/gltfio 1.56.0. Keine Stub-Klassen, kein vollständiger App-Build. Integrationstests beim Koordinator.
- Host-Renderbilder: separater Render-Agent; dessen Manifest und tatsächliche Bilder maßgeblich.
- Android-/Filament-GPU und Fold7: **NOT_RUN**.

## Integration

`FilamentCockpitStage.kt` enthält ausschließlich Änderungen an Lichtbesitz/-freigabe und Asset-Pin; `render()` und seine Transformationen sind unverändert. Der Motion-Patch kann an diesen Stellen getrennt übernommen werden. `build_scene.py`, GLB und Pin müssen zusammen übernommen werden.

Der öffentlich gelesene Stand `b9de06b79` enthält das Layering-/Button-Teilstück, jedoch noch nicht die Filament-Cockpit-Dateien aus `9ffd3b193`. Den tatsächlichen 3D-Pfad beim Zusammenführen bewahren.

Veröffentlichung durch den Koordinator; dieser Agent hat keinen Push ausgeführt.

Rücknahme: diesen Materialpatch gemeinsam zurücknehmen; ursprünglichen GLB-Pin wiederherstellen. Keine Rücknahme anderer Cinema-, Bewegungs- oder Bildquellenänderungen.
