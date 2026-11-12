# Code55: gemeinsame Filament-Szene

Stand: Runtime-Agent `work/astra55-unified-scene-20261112`, zuletzt `efc82cd6a2`. Integration, Tests, unsignierter Android-Build und Veröffentlichung erfolgen durch den Hauptagenten. Diese Datei behauptet keine Geräteabnahme.

## Implementierter Anschluss

Der vorhandene `FilamentAvatarRenderer` besitzt weiterhin Engine, Renderer, Scene, View, Perspektivkamera und Swapchain. `FilamentCockpitStage` fügt die tatsächlichen Arbeitsplatz- und Kugel-GLBs dieser Scene hinzu. Es erzeugt keine eigene Scene, Kamera, View, Surface oder Swapchain. Das frühere `CockpitStageView` mit eigener TextureView wurde entfernt. Die vorhandene Avatar-TextureView zeigt im Cockpit die komplette gemeinsame Szene, mit Deckkraft 1 und ohne Kopfmaske über der Welt.

Die originale Avatar-Datei bleibt bytegleich:

- `avatar3d/continuum-avatar.glb`
- SHA256 `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`

`AvatarMaterialVariant` erstellt ausschließlich eine Ladevariante der Materialbeschreibung. Nicht maskierte Avatar-Materialien erhalten BLEND; ihre ursprünglichen Farbwerte bleiben erhalten. Alle Geometriedaten, Binärblöcke, Texturen, Gelenke, Morphs und sonstigen JSON-Felder bleiben erhalten. Die aktuelle Besitzersteuerung wird mit 84 Prozent Avatar-Deckkraft umgesetzt. Ein eigener Transform-Elternknoten positioniert den Avatar in der Welt; vorhandene Rig-Knoten und ihre Ruhetransformationen werden dabei nicht ersetzt. Blick, Kiefer, Viseme und die vorhandene begrenzte Körperbewegung laufen weiter über denselben AvatarDirector/Mapper.

Die Kugel wird aus der vorhandenen Originalparametrik als echte Geometrie übernommen. Es wird keine fremde Avatar- oder Kugelidentität geladen. Ihre vier vorhandenen Orbitgruppen verwenden den bestehenden Präsentationstakt. Gegenüber dem vorherigen Layout ist ihre Box rund 26 Prozent größer. Das Logo bleibt Bestandteil der Kugel hinter dem Avatar.

## Gemeinsame Geometrie

Eine Welteinheit entspricht der kürzeren Szenenkante. X zeigt nach rechts, Y nach oben, Z zur Kamera. Der Avatar liegt um Z=0, das Kugelzentrum bei Z=-0,44 und der Monitor in Ruhe bei Z=+0,22. Die Kamera blickt vom positiven Z zur Szenenmitte und verwendet eine Perspektivprojektion.

`CockpitStageFrame.monitorTransform()` ist die gemeinsame Matrix für den Filament-Monitor und seine geometrische Projektion. Die Ruheposition wird perspektivisch auf die Layoutrechtecke eingepasst. Arbeitsbeginn verschiebt den Monitor tatsächlich auf der Z-Achse; die Layoutbreite wird dafür nicht vergrößert. Die vorhandene 420-ms-Flugkurve aus PR801 erhält bei einer Richtungsänderung Position und Geschwindigkeit. Nur passende reale Arbeitsereignisse der gebundenen Sitzung lösen diese Fahrt aus. Stoppen, Fehler, Rückfrage, Pause und Abschluss führen zurück.

Die View-Bufferkante ist auf 1280 Pixel begrenzt und behält das Seitenverhältnis. Androids Surface-Größenwechsel stellt diese Grenze erneut her. Das ist eine Renderbudgetentscheidung, keine Behauptung über native Fold7-Auflösung oder gemessene thermische Belastung.

## Tatsächliche App-Pixel

`monitor-live-pixels` ist eine echte Mesh-Fläche mit UV0. Der Filament-1.56-Materialcode verwendet `samplerExternal videoTexture`; sein Material ist opaque und unlit. Die davorliegende Glasabdeckung eines zweiten Bildlayers entfällt. Glas und Metall liegen am Rahmen bzw. Arbeitsplatz.

`CockpitLiveTexture` übernimmt die unveränderlichen HardwareBuffer des vorhandenen LiveMonitorFeed über `Stream.Builder()` im dokumentierten ACQUIRED-Modus und `Stream.setAcquiredImage`. Es gibt keine Bildschirm-PNGs, Base64-Pixel, Canvas-App-Bitmaps oder nachgezeichneten WhatsApp-Oberflächen in diesem Pfad. Der kleine native Status-View zeichnet ausschließlich eine Statuszeile und liefert Zugänglichkeit/Lifecycle-Signale.

Die bestehende Holder-Lease wird nach dem Duplizieren des nativen HardwareBuffer-Handles geschlossen. Das Duplikat stammt aus der öffentlichen Parcelable-Schnittstelle; die Pixel werden nicht kopiert. Filaments Releasecallback schließt dieses eigene Handle. Höchstens zwei solche GPU-Referenzen sind ausstehend. Diese Grenze kommt zum vorhandenen Producer/Holder-Budget hinzu; sie ist keine Aussage, dass das Gesamtsystem nur zwei Buffer besitzt. Beim Quellenwechsel und Schließen werden Stream/Textur freigegeben, die GPU abgeglichen und verbleibende eigene Referenzen idempotent geschlossen.

Vor und nach dem Übernehmen sowie vor der Präsentation gelten dieselben Bindungen: Task, Capture-Sitzung, Zielquelle, aktuelle Sequenz und Frische. Ein anderer, verspäteter oder angehaltener Feed darf keine alten Pixel als Live präsentieren. Die native Fläche wird bei einem ausgelassenen gemeinsamen GPU-Render ausgeblendet, bis wieder ein erfolgreicher Frame vorliegt. Ein Klickring setzt zusätzlich die exakt dargestellte Frame-Sequenz und ein bekanntes `sourceViewport` voraus. Die Zielkoordinate aus dem vollständigen App-Fenster wird auf den tatsächlich ausgeschnittenen Bereich und dessen Letterboxing umgerechnet. Außerhalb dieses Ausschnitts gibt es keinen präzisen Ring.

## Farben und Licht

Das Pixelmaterial dekodiert sRGB explizit in lineare Werte. Die gemeinsame View verwendet neutrales lineares Tonemapping. Der tatsächlich kompilierte unlit-Shader multipliziert die App-Pixel nicht mit der Kamerabelichtung. Nicht-sRGB-Buffer werden in diesem Importpfad erklärbar abgelehnt; der Produktions-Crop muss deshalb den vom Hauptagenten normalisierten sRGB-Ausgang liefern.

Die vorhandene Hauptbeleuchtung und zwei Punktlichter bleiben erhalten. Die geprüfte SH-Aufhellung aus PR801 ist in derselben Scene angeschlossen: ein Band, Koeffizienten 0,90/0,98/1,10, Intensität 20.000. Kontrollierter HDR-Bloom ist ohne reales App-Bild aktiv. Sobald gültige App-Pixel sichtbar sind, wird Bloom für die gemeinsame View ausgeschaltet. Emissionsgeometrie, Kugel, Ringe und Licht bleiben erhalten. Es wird ausdrücklich keine isolierte Bloom-Maske bei gleichzeitigem Live-Bild behauptet.

## Prüfungen und praktische Grenzen

Neue Hosttests prüfen:

- Avatar-Ladevariante: Originaldatei und BIN unverändert; alle JSON-Bereiche außer den vorgesehenen Materialmodi gleich.
- GPU-Pixelbindung: alte Sequenz, fremde Sitzung, anderer Task, fehlende Bindung und nicht aktive Zustände werden abgewiesen.
- Monitor: tatsächliche Z-Bewegung, kontinuierliche Umkehr und kein Abschneiden während der Fahrt auf normalem Telefon bzw. geöffnetem Fold-Layout.

Die Ausführung und Ergebnisse dokumentiert der Hauptagent am integrierten Stand. Der Asset-Agent erstellt neue Hostbilder aus den tatsächlichen GLBs und den gemeinsamen Matrizen. Diese Bilder sind räumliche Hostbelege, keine Android-Filament-Screenshots und keine Bestätigung einer laufenden privaten App.

Auf einem realen Android-Gerät bleiben zu bestätigen: sichtbarer HardwareBuffer-Import samt UV-Ausrichtung, genaue Farben, Rotation/Resize und Rückkehr aus Hintergrund, Capture-Widerruf/geschützte Inhalte, kontinuierliche Speicherfreigabe sowie Bildrate und Temperatur. Die gemeinsame Perspektive kann außerdem eine praktische Feinabstimmung der Avatarbeleuchtung erfordern; ein Hostbild beweist nicht das exakte photometrische Ergebnis von Filament auf dem Fold7.

Die vorhandene separate `FilamentDocumentStage` für spezielle PDF-Karten wurde in diesem Schritt nicht neu aufgebaut. Der gemeinsame virtuelle Computer zeigt dagegen echte PDF-/Word-App-Pixel innerhalb seines Monitors. Eine vollständige Migration aller früheren Dokumentkarten in den gemeinsamen 3D-Dateiraum wird hier nicht behauptet.

## Nachgeprüfte SDK-Verträge

- Filament 1.56 `Stream.java`: ACQUIRED als Builder-Standard, HardwareBuffer-Übernahme vor beginFrame, eigener Releasecallback: https://github.com/google/filament/blob/v1.56.0/android/filament-android/src/main/java/com/google/android/filament/Stream.java
- Android 14 `HardwareBuffer.java`: öffentliche Parcelable-Schnittstelle und eigener nativer Handle beim Lesen: https://github.com/aosp-mirror/platform_frameworks_base/blob/android-14.0.0_r1/core/java/android/hardware/HardwareBuffer.java
- Tatsächliches SDK-JAR 1.56: Material-, Transform-, Renderable-, ColorGrading- und Stream-Signaturen wurden zusätzlich mit javap geprüft.
- Der Asset-Agent hat das Material mit offiziellem matc 1.56.0 für Mobile OpenGL/Vulkan kompiliert und das erzeugte GLSL auf Belichtungsverhalten geprüft.
