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
- Asset-Pin und 32.768-Byte-Grenze: **PASS**. Ergebnis: 14.728 Bytes, 83 Knoten, 81 Renderables, neun geteilte Meshes, 2.508 instanzierte Dreiecke.
- SHA-256 nach Kontaktkorrektur: `3223168a58e5fa51c48bb56ba36aabbf0cdb936025a01d96d4b14e5e0fa023aa`.
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

## Sichtbare Ausschnittkorrektur

Die ersten quellgebundenen Hostbilder zeigten verdeckten Mund/Kinn und den außerhalb der Bühne liegenden Monitorfuß. Gegenproben auf der Ausgangsgeometrie reproduzierten drei Fehler bei vier Tests: Gesicht, Fuß und bekannte Großschriftüberlappung. Der Standardschrift-Viewport blieb bereits korrekt.

Der produktive `AvatarGlbContract` liefert für das Originalasset eine konservative tiefste Gesichtsposition von `0.16316418269020994 m`. Mit dem produktiven `AvatarFraming` liegt diese bei maximal `80,452737 %` der Kopfregion; zusätzlich berücksichtigt die Prüfung `6,5 px` Kameradrift. Die neue Geometrie hält die originale Pose einschließlich Drift über beiden Fokus-Endstellungen des Monitors.

- **Vom Hauptteam übernommen:** `cb1c647e06`, lokal `ec590e2f58`: breitere Original-Bust-Region (`.82` der Raumbreite breit / `.98` auf dem Telefon, maximal `1.65 × Szenenhöhe`), neues `protectedFaceBounds` und bestehender WorkWindow-Schutz. Keine eigene Neuentwicklung dieser Teile.
- **Vom Support übernommen:** Mindesthöhe breiter Szenen `310 × Schriftfaktor`, ohne Änderung der Standardschrift-Mindesthöhe.
- **Eigene Korrektur darauf:** Kopfregion `.60` statt `.70` der Szene, Monitorbeginn `.54` der Szene, fünf Prozent Bodenreserve. Gesichtsschutz deckt die gemessene Pose und Drift ab und bleibt horizontal innerhalb der Kopfregion. Kein anderer Ausschnitt des Originalmodells, keine neue Scroll-Mindesthöhe bei Standardschrift.
- Der Stand endet bei `-.588` statt `-.721` Monitorhöhen unter der lokalen Mitte. Generator, GLB und Pin wurden gemeinsam geändert.
- Fold-Profil `884 × 760`: gleiche Szenenhöhe und Monitorbreite, Monitorhöhe `196,8 dp`; Kopfregion 14,3 Prozent niedriger als zuvor. Lesbarkeit und Materialwirkung sind anschließend im tatsächlichen Hostbild zu beurteilen.

`CinematicWorkstationVisibilityTest`: **5/5 PASS** nach Übernahme des Hauptteam-Deltas. Originale Avatarbytes, vollständige produktive Framing-/Layout-/Projektionsklassen und tatsächlicher GLB-Fuß werden geprüft; kein nachgebautes Layout. Die Hostkompilierung verwendet den bestehenden Preview-Adapter für benachbarte Zeichen-/Konstantenabhängigkeiten. Es ist kein vollständiger Android-App-Lauf. Belege unter `docs/evidence/astra55-materials-20261112/`.

Die Bewegungszwischenbilder und visuelle Endabnahme folgen auf dem gemeinsamen Render-Freeze des Koordinators. Kein eigener Push.

### Kontaktkorrektur nach dem echten Render

Der nachfolgende Hostrender zeigte, dass der bereits im Bühnenausschnitt befindliche Fuß noch unter der Tischplatte lag. Auch die zusätzlich eingezogene Glasauflage lag zu hoch. Daher keine weitere Veränderung von Kopf, Layout oder Monitorfläche:

- Tischkörper, seine Position und Bühnenhöhe bleiben unverändert.
- Glasauflage liegt nun dünn direkt auf der Platte; obere Fläche `-.4605 × Szenenhöhe`.
- Fußzentrum `-.5195 × Monitorhöhe`, Dicke `.012`; kurzer Stand verbindet seine Oberkante exakt mit dem Monitor. Bestehendes Bevel-Material macht den flachen Fuß lesbarer.
- Tasten und hintere Lichtlinie liegen auf derselben Glasfläche.
- `footContactsGlassAtBothFocusEndpoints` prüft tatsächliche GLB-Geometrie bei Ruhe und Fokus, maximal ein Pixel Kontaktabweichung. Zusammen **6/6 Tests PASS**. Der Fuß ist bewusst klein; keine zusätzliche Fläche wurde dem App-Bild entzogen.
- Echter Blender-16-Sample-Hostrender erstellt und angesehen: `docs/evidence/astra55-materials-20261112/foot-contact-host.png`, Assetbindung im begleitenden Manifest. Die Projektion verwendet den eingefrorenen Kotlin-Frame des Koordinators. Kein Filament- oder Gerätebeleg.
