# Kommando 03 – visuelle Quelltreue und Android-Zwischenstand

**Status: WIP – QUELLDATEN ORIGINAL, EINGEBETTETE BÜHNE ZUR SICHTPRÜFUNG; KEIN INSTALLIERBARER RELEASE.**

Der Besitzer hat am 15.11.2026 die Android-Aufnahme aus Code 60 ausdrücklich als abweichend von der HTML-Fassung zurückgewiesen. 112 erfolgreiche Hosttests sagen nichts über visuelle 1:1-Übereinstimmung, GPU-Flüssigkeit oder Live-Werkzeugprojektion aus. Der Signier-/Releaseweg ist dafür **blockiert**.

## Maßgebliche Gestaltung

- PR #850, Quellen-Commit `0876aa0accdf8154e06e53da42aa5c9492892066`.
- HTML-Struktur, alle CSS- und JS-Module, Schriften, Originalkopf-GLB, Kugelgrafik und Lizenzen wurden **per identischer Git-Blob-SHA** nach `app/src/main/assets/continuum-command03/` übernommen.
- Die ganze Datei- und Prüfsummenliste liegt in `docs/research/omega/COMMAND03-HTML-VISUAL-PARITY-LOCK.json`.
- `node tools/command03-native-qa/check-visual-source.mjs` prüft den Blobvertrag auf dem Checkout. Das ist ein **Quelltest**, kein Screenshotvergleich.

## Vorbereitete Android-Bühne

`ContinuumCommand03OriginalStage` lädt die HTML-Quelle unter einem begrenzten lokalen HTTPS-Asset-Ursprung. Externe Netzaufrufe, `file://`, die universelle Dateifreigabe und ein `JavascriptInterface` sind deaktiviert. Eine Android-spezifische, von den Originaldateien getrennte `native-bridge.js` fängt erlaubte Navigation und Texteingaben vor dem Demo-Handler ab. Ein Klick öffnet die bestehenden Besitzer-gesteuerten Android-Routen; er führt nie den im HTML gespeicherten Suchlauf als echte Recherche aus.

Über **Mehr → KOMMANDO 03 · HTML-ORIGINAL (DESIGNTEST)** lässt sich diese originale Oberfläche im bestehenden Activity-Kontext als WebView sehen. Beim Wechsel zu echten Werkzeugen kehrt die App zur nativen Bedienung zurück. **Damit ist die vollständige 1:1-Verbindung noch nicht erreicht.** Diese bewusste Trennung verhindert vorzeitige Veröffentlichung einer fremden/nicht real angebundenen Vorschau.

## Offene, verbindliche P0-Arbeit

1. Original-Webbühne zur dauerhaft angezeigten Hauptoberfläche entwickeln, ohne zweite Chatdatenbank und ohne native API-/Tool-Gates auszuhebeln.
2. Echte Native-Events und Zustände in das HTML-Workspace-Fenster projizieren: generierte Antworten, sichtbare PDF-Dokumente, erteilte Recherchefreigaben, Agentenstatus und Computer-Livebild; die Simulationen dürfen nur als klar gekennzeichnete Demos bestehen.
3. Wachsende Chatlisten aus dem existierenden Store anzeigen; keinen lokalen Beispielverlauf als echten Besitzerverlauf präsentieren.
4. WebGL-Avatar und ursprüngliche Kugel gegen das Original auf Android rendern, Speicher-/Thermik-Budget prüfen. Keine doppelte 3D-Engine im Vordergrund.
5. Android-Screenshots und Animationsaufnahmen bei ~392 und ~904 dp **pixelnah gegen die HTML-Referenz** prüfen; Header, Navigationsleisten, Lichtraum, Graphen, Arbeitsfenster, Farben, Proportionen und Bewegung.
6. Fokussierte Tests, volle Android- und Kontext-QA, echter Fold7-Test mit Tastatur, Rotation, GPU, Performance und Sprache. Erst nach Design- und Funktionsfreigabe Quelle einfrieren, signieren und veröffentlichen.

Die früher gebaute unsignierte Code60-Datei `744b1e15…33bc0` ist **nicht freigegeben**. Kein Merge nach main und keine Änderung am Originalsignaturvertrag.

## Konfliktbewertung

Eine reine Neuzeichnung in Kotlin/Canvas hat das Design massiv verändert. Die Webdateien als **eine gemeinsame Darstellungsquelle** zu verwenden, ist die verlässlichere Richtung. Daraus folgt aber noch keine Garantie für GPU-Leistung oder sichere, vollständige native Werkzeugverbindung. Diese Grenzen müssen durch echte Tests geschlossen werden.
