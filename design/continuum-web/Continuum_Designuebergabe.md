# Device AI Continuum – Kommando 03

## Ergebnis

Die bestehende Webgestaltung wurde auf Heinz' neue Anweisung überarbeitet. Originalavatar und Originalkugel bleiben erhalten. Die Mitte ist deutlich größer, der bedeutungslose Buchstabe R am Titel ist entfernt. Präzise Kanten, geradere Fassungen, ruhige technische Beschriftungen und gezielte Amber-Akzente geben der Oberfläche einen strengeren Kommandocharakter. Manrope und Space Grotesk bleiben die Schriftfamilien.

Die private Vorschau: https://device-ai-continuum-heinz.electemu.chatgpt.site
Formate ausprobieren: https://device-ai-continuum-heinz.electemu.chatgpt.site/ausprobieren.html
GitHub-Entwurf: https://github.com/Ullmann27/device-ai/pull/850

## Neue Bedienmöglichkeiten

- Recherche erleben: projizierter Computer mit fünf nachvollziehbaren Schritten, Suchanfrage, Quellenkarten, Zusammenhang und Ergebnis.
- Pause, Fortsetzen, direktes Anwählen eines Schritts, drei Geschwindigkeiten und Neustart.
- Drei gespeicherte, verlinkte Fachquellen. Der eingegebene Auftrag ändert die Darstellung, nicht die gespeicherte Recherche. Die Oberfläche nennt diese Grenze ausdrücklich.
- Ergebnis als Markdown herunterladen.
- Eigene TXT-, Markdown-, CSV- und JSON-Dateien bis 2 MB lokal lesen. Tatsächliche Wort-, Zeilen- und Absatzanzahl; Abschnittsbalken verknüpfen Diagramm und Textstelle. Keine externe Übertragung und keine KI-Zusammenfassung.
- Seitliche CPU/RAM-Lastdemo, eindeutig als Simulation bezeichnet. Wechsel zur Browsermessung: rAF-Frameabstände, lange Aufgaben soweit unterstützt und JS-Heap-Schätzung soweit verfügbar. Kein vorgetäuschter Zugriff auf die gesamte Geräte-CPU oder den Geräte-RAM.
- Separate Formatvorschau mit demselben Index in einem 1440/904/392 CSS-Pixel breiten Rahmen; reale Innenviewport-Abfragen bleiben wirksam. Die breite Ansicht passt sich der verfügbaren Fläche an.

## Fenster und laufende Szene

Ein gemeinsamer Controller übernimmt die Arbeitsfläche. Einflug 950 ms, Rückflug 650 ms; Unterbrechungen übernehmen Pose und Bewegungsrichtung. Die Rückplatte und Materialseiten haben eigene Tiefen. Eine FLIP-Hülle gleicht Inhalts- und Größenwechsel aus. Die eigentliche Animation verwendet Transform und Deckkraft. Die Geometrie wird an Änderungsgrenzen gemessen, nicht fortlaufend je Bild.

Der Hintergrund berechnet ruhige Beleuchtungs- und Architekturanteile vor. Drei bewegte Tiefenebenen bleiben aktiv. Renderpuffer reagieren auf anhaltenden Zeitdruck; die Bewegung wird dabei nicht pausiert. Beim Avatar werden Pose und Morphzuordnung wiederverwendet, unveränderte Puffergrößen nicht erneut angelegt und Shader vor dem Einblenden vorbereitet. Der Blick folgt auch der gewählten Recherchequelle.

Die Kugelgeometrie und Ringorientierung bleiben wie gewünscht fest. Lichtintensität, Avatar und Umgebung reagieren weiterhin.

## Originalbestand

Quelle: Ullmann27/device-ai, Code55 `1b90611388cc2b1978ddfe32064a25dd3a08cee1`.
Originalavatar: 12.038.416 Bytes, SHA-256 `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`.
Die Kugel basiert auf den tatsächlichen Kotlin-Originalquellen in `interop/originals/`. Modell, Texturen, Rig und Identität bleiben erhalten. Der Display-GLB und bearbeitbare Blender-Dateien liegen weiterhin im Gesamtpaket.

## Prüfung und Grenzen

63 gezielte DOM-/Ablaufprüfungen: 21 App, 10 Präsenzinstrumente, 13 Fensterbewegung, 10 Telemetrie, 9 Computer/Dateien. Hinzu kommen Originalmodell-/Rigvertrag und die native Renderbudgetprüfung.

Tatsächlich im Browser geprüft: neue Anordnung, Titel ohne R, Recherche öffnen, Quellen auswählen, echte Textdatei lesen (44 Wörter, 8 Zeilen, 4 Absätze), Abschnitt per Diagramm anwählen, Fenster schließen und anschließend einen neuen Auftrag öffnen. Ein Statusselektor-Konflikt wurde dabei gefunden, behoben und mit einer vollständigen Timeline-Prüfung abgesichert.

Im Prüfbrowser wurde das Original-PNG als Avatar-Fallback angezeigt. Seine 3D-GPU-Ausgabe wurde dort nicht bestätigt. Bei der späteren Formatprüfung traten Browser-Protokollzeitüberschreitungen auf; Fold/Schmal wurden daher nicht vollständig visuell abgenommen. Kein Fold-FPS-Benchmark. Die neue Screenshotdatei ist ein tatsächlicher Browser-Screenshot; die alte Raumstudie 02 dokumentiert ausschließlich den früheren Stand.

## Übernahme in die App

Der aktuelle Quellstand liegt in `design/continuum-web/` im bestehenden Entwurfs-PR. Die Webmodule sind vollständig verbunden. Die Android-Übernahme folgt erst nach Heinz' Designfreigabe. Dabei bleiben originale Assets, konkrete Abmessungen, Typografie, Datenbindungen und die Zustands-/Abbruchsemantik maßgeblich. Der Rechercheablauf der Vorschau ist noch keine Verbindung zum Android-KI-Dienst.

## Quellen der eingebetteten Recherche

Geprüft am 15.11.2026:

- MDN, Web Animations API: https://developer.mozilla.org/en-US/docs/Web/API/Web_Animations_API/Using_the_Web_Animations_API
- web.dev, effiziente CSS-Animationen: https://web.dev/articles/animations-guide
- MDN, PerformanceObserver: https://developer.mozilla.org/en-US/docs/Web/API/PerformanceObserver
