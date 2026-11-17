# Code55 – verbindliche 3D-Fortsetzung

## Auftrag des Besitzers

Die am 12.11.2026 ausdrücklich beauftragte Fortsetzung arbeitet auf den bestehenden Änderungen weiter: **Originalkugel hinten, Original-Jarvis in der Mitte, echter 3D-Computer vorne**. Die drei Komponenten werden gemeinsam räumlich gerendert. Echte Android-Bilder dürfen Texturen sein; ihre Perspektive und Eingabekoordinaten müssen aus derselben Monitorgeometrie folgen.

Der nächste Meilenstein umfasst eine implementierte, geprüfte und auf GitHub gesicherte gemeinsame Szene. Die bereits vorhandenen Reparaturen für Gedächtnis, PDF, Sprache, Android-Bedienung, Freigaben und Stop bleiben erhalten. Zielgerät ist das vom Besitzer benannte Galaxy Z Fold7 mit Android 17; eine Geräteprüfung wird nicht durch Hostprüfungen ersetzt.

## Eingefrorene Ausgangsstände

| Quelle | Stand | Verwendung |
|---|---|---|
| #799 vor Zusammenführung der Renderflächen | `e165573e1cbacd27748372ed38f899fc4393ec30` | Tree exakt wie lokales `a99eac98cf`; bestehende Reparaturen und Hostbelege gesichert |
| #800 | `b9de06b794668f9f85874710b9f78ad454d0dbaf` | Ursprünglicher Grafikcheckpoint, bereits in #799 enthalten |
| #801 | `dfe8f08f646360c0290ef420b9e6b16cc90904a1` | Bevel-/Glasgeometrie, Monitorbewegung und gemeinsame Projektionsformeln gezielt übernehmen |
| #802 | `28aa599bb0e6c77ad8b594d7717003d243a312e8` | Gegenproben und fehlende Reparaturen gegen aktuellen Stand neu prüfen |
| Code54 | `9713854bfb` | Historische Grafikbasis; vorhandene Canvas-Belege sind keine Filament-Aufnahmen |

## Tatsächliche Umsetzung

Die bisher getrennten Kugel-, Avatar-, Cockpit- und Monitorflächen werden durch eine gemeinsame Filament-Scene, View, Perspektivkamera und Swapchain ersetzt. Es bleibt dieselbe Engine. Die alte Canvas-App-Bildfläche wird nicht als fertiger 3D-Pfad ausgegeben.

Die tatsächlich vorhandene Filament-1.56.0-API unterstützt einen ACQUIRED-Stream mit HardwareBuffer-Übergabe und Freigabecallback. Dieser Weg wird für die reale Monitorfläche verwendet. Farbraum, Seitenverhältnis, Quellausschnitt, Aufnahmesitzung und Ressourcenbesitz bleiben ausdrücklich gebunden.

Das Originalavatar-Asset bleibt bytegleich. Die Laufzeit darf seine Materialkonfiguration für Transparenz anpassen, ohne Geometrie, Rig, Morph Targets oder Animationen zu ersetzen. Eine Transparenz des ganzen Android-Views würde auch die App-Pixel verfälschen und ist daher ungeeignet.

Blender ist tatsächlich vorhanden. Reproduzierbare Modellierungsskripte, exportierte GLB-Dateien, Material-/Shaderquellen und überprüfte Hashes begleiten die Umsetzung. Die Kugel wird aus echter Ring-/Kern-/Partikelgeometrie in der bisherigen Formsprache aufgebaut. Eine erzeugte Konzeptgrafik ersetzt keine dieser Komponenten.

Die Monitorfahrt wird an tatsächliche Auftragszustände gebunden. Dieselbe räumliche Transformationskette bestimmt Rahmen, App-Textur und Klickmarkierung. Keine animierte Erfolgsmeldung darf eine fehlende Android-Nachprüfung ersetzen.

## Bloom und reale Bildfarben

Die Umsetzung prüft eine kontrollierte gemeinsame Policy: sparsames HDR-Bloom ohne präsentierte App-Textur, keine ungeprüfte Bloom-Verfärbung realer Monitorpixel. Geometrisches Leuchten und gemeinsames Licht können während der Live-Anzeige erhalten bleiben. Eine solche Policy ist keine behauptete selektive Bloom-Isolation innerhalb einer Live-Textur.

## Zuständigkeiten

- Gemeinsamer Renderpfad: `cockpit_scene`, eigener Worktree `wt-astra-unified-scene`.
- Blender-/GLB-/Materialpaket und Renderbelege: `live_computer`, eigener isolierter Worktree.
- #802-Gegenproben, echter Scrollnachweis und Aufnahme-Ausgangsbindung: `workflow_motion`, eigener isolierter Worktree.
- Integration, Geräte-/Testgrenzen, Gesamtsuite, Build und GitHub-Sicherung: Root auf #799.

Keine parallelen schweren Gradle-/Blender-Läufe; gemeinsame Dateisperre. Keine aktiven Arbeitsbäume anderer Teams überschreiben. Originalsignierung und `main` bleiben außerhalb dieses Integrationsmeilensteins.

## Nachweise und bisherige Grenzen

Vor der neuen gemeinsamen Szene bestand ein abgeschlossener Fokuslauf 270 von 278 Prüfungen; acht veraltete Navigationserwartungen wurden danach korrigiert. Vollsuite und neue Szene müssen erneut auf ihrem eigenen Quellstand geprüft werden.

Die Umgebung hatte einen fast vollen 4,9-GB-`/tmp`-Speicher. Ein Vollversuch scheiterte beim Laden nativer Testbibliotheken mit `No space left on device`. Die eigenen Java-/Test-Temporärdateien wurden auf den freien Arbeitsdatenträger verlegt. Dieser abgebrochene Lauf gilt nicht als bestandene Suite und seine Umgebungsfehler sind keine nachgewiesenen App-Fehler.

Vorher-/Nachher-Belege nennen Backend, Kamera, Maße, Quelle und skriptierte Zustände. Historische Code54-Bilder unter `docs/evidence/code55-cinema55-baseline-20261112/` stammen aus dem echten Android-Canvas-Pfad ohne Filament. Neue Blender-/Hostansichten dürfen nicht rückwirkend als alte Android-GPU-Szene ausgegeben werden. Ein echtes WhatsApp-Arbeitsergebnis, Fold7-Bildrate und Wärme benötigen weiterhin den Gerätelauf.
