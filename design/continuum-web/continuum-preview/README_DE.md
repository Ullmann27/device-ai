# Device AI Continuum

Diese Fassung verbindet das vorhandene Originalmodell und die Originalkugel aus `Ullmann27/device-ai` mit dem gewünschten Raster: Chats links, Werkzeuge oben, Arbeitsraum in der Mitte, Diagramme und Quellen rechts.

## Öffnen

Die bereitgestellte private Webadresse ist der einfachste Einstieg am Smartphone. Die gesonderte Datei `Continuum_Vorschau.html` enthält sämtliche notwendigen Darstellungsdateien und lässt sich in einem Browser öffnen.

Für diese bearbeitbare Projektmappe: Node.js 20 oder neuer verwenden, einen Terminal im entpackten Ordner öffnen und `npm start` ausführen. Es werden keine npm-Pakete für den Betrieb benötigt. Die angezeigte lokale Adresse im Browser öffnen. In einem Webeditor kann `npm run dev` verwendet werden.

## Ausprobieren

1. „Argon4-Demo öffnen“ startet den räumlichen Aufbau der Arbeitsfläche.
2. Rechts eine der sechs Phasen wählen. Diagramm, Erklärung und Quelle ändern sich gemeinsam.
3. Eine Quelle öffnen: Die zugehörige Passage wird fokussiert.
4. Links zwischen Chats wechseln oder einen neuen lokalen Verlauf beginnen.
5. Oben Dateien, Recherche, Agenten, Gedächtnis, Android-Computer und Einstellungen erkunden.
6. Die sieben Szenenschritte oder die Abspieltaste zeigen den gesamten visuellen Ablauf.
7. Auf einem schmalen Bildschirm öffnen „Chats & Projekte“ und „Diagramme“ die Seitenbereiche.

## Bestand erhalten

Der Originalkopf liegt unverändert als `assets/continuum-avatar.glb` vor. Die Datei wird beim Laden auf ihre bekannte Größe und, wenn die Browserfunktion verfügbar ist, auf SHA-256 geprüft. Der Kopf hat sein vorhandenes Rig und seine vorhandenen Morph-Ziele. Ohne WebGL wird eine in Blender aus genau diesem Modell gerenderte Grafik gezeigt.

`orb-original.js` überträgt den vorhandenen Kotlin-Kugelrenderer in die Webdarstellung. Die Kugel wurde nicht durch ein neues Modell ersetzt. `scene.js` steuert die Atmosphäre und Energieverbindungen; `app.js` steuert die Bedienung.

## Grenzen dieser Fassung

Dies ist eine interaktive Gestaltung und ein wiederverwendbarer Darstellungsadapter. Die enthaltenen Beispielaufträge verbinden sich nicht mit dem lokalen Android-KI-Modell, dem Mikrofon oder Android-Werkzeugen. Das Dokument ist ausdrücklich eine Demonstration. Lippenbewegungen werden nur von tatsächlichen Viseme-Ereignissen ausgelöst; der Sprechzustand allein erfindet keine Mundbewegung.

Die ursprüngliche Android-App und ihre Signatur werden durch das Öffnen dieser Gestaltung nicht verändert. Ein Gerätetest auf dem konkreten Fold7 bleibt gesondert erforderlich.
