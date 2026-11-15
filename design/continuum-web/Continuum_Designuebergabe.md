# Device AI Continuum – Designübergabe

Stand: 15. November 2026 · Präsenz 02. Maßgeblich ist die weiterentwickelte Webgestaltung mit den Originalen aus GitHub. Öffnen: private Webvorschau, gesonderte Offline-HTML oder `npm start`; bestätigter Bereitstellungslink und Zugriffsstatus stehen in der Auslieferungsnachricht.

## 1. Aufbau und gestalterischer Zusammenhang

Das aktuelle Raster ordnet **Chats und Projekte links, Plugins oben, Avatar mit Linkleiste und Arbeitsraum in der Mitte sowie Diagramme und Quellen rechts** an. Der Originalavatar steht jetzt exakt mittig vor der größeren Originalkugel. Deren Geometrie und Ringorientierung bleiben auf Wunsch fest; weiche Lichtantworten verbinden sie mit der Szene. Links und rechts dieser zentralen Einheit stehen ein holografisches Phaseninstrument und ein räumlich gestaffeltes Quellennetz. Dateien, Recherche, Agenten, Gedächtnis, Android-Computer und Einstellungen verwenden dieselbe Material- und Lichtwelt. Auf schmalen Displays werden die Seitenbereiche über „Chats & Projekte“ beziehungsweise „Diagramme“ geöffnet; Eingabe und aktive Arbeitsfläche bleiben erreichbar.

Obsidian, Nachtblau, Graphit und blaues Rauchglas bilden den Raum. Cyan führt Interaktion und Energie; Eisblau zeichnet Kanten, Violett schafft Tiefe, Amber markiert Aufmerksamkeit. Manrope trägt Inhalte und Bedienung, Space Grotesk die technischen Überschriften. Helle Energiequellen, zurückhaltende Flächen und scharfe Schrift sichern Orientierung.

Displays besitzen getrennte Fassung, Glas, Inhaltsfläche und Rückkörper. Der Blender-Bestand enthält ein eigenständiges Arbeitsdisplay mit sichtbarer Stärke und Vorder-/Rückansicht. Dieses neue Arbeitsobjekt ersetzt weder Avatar noch Kugel. Texte und Trefferflächen bleiben stabil; Licht, Reflexionen und Umgebung bewegen sich weiter. Containerabhängige Layouts ordnen auf schmalen Flächen den Avatar über den beiden Instrumenten an. Die Hologramme übernehmen ihre Werte aus derselben Phasendatenquelle wie das Dokument; jede Auswahl synchronisiert alle Ansichten.

## 2. Verbindliche Originale

Quelle: [Ullmann27/device-ai](https://github.com/Ullmann27/device-ai), Code55-Commit `1b90611388cc2b1978ddfe32064a25dd3a08cee1`.

**Avatar:** `app/src/main/assets/avatar3d/continuum-avatar.glb`, 12.038.416 Bytes, SHA-256:

```text
c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a
```

Die Vorschau verwendet genau dieses unveränderte Modell. Es besitzt das bestehende Hals-/Kopf-Rig und 21 Morphziele. Der Webadapter berücksichtigt Augen-vor-Kopf, den ursprünglichen Halsanteil von 65 % und dieselben Morphziele in Gesicht und NeuralThreads. Ohne WebGL erscheint das in Blender aus diesem Modell gerenderte PNG. PNG und WebGL werden nach demselben proportionalen Kameraausschnitt eingepasst; Kopf und Schultern werden nicht gestreckt. Ein neutraleres Hauptlicht hebt das Gesicht vor dem Halo hervor. Berührte Diagrammpunkte steuern den Blick auf die tatsächliche Auswahlposition. Das zuvor verwendete andere Sheet-Gesicht ist kein aktueller Avatarbestand.

**Kugel:** Der Originalbestand ist Kotlin-/Canvas-Code, keine Kugel-GLB. `orb-original.js` überträgt `ContinuumHologramRenderer`, `ContinuumPainter`, Ringgeometrie, Plexus und Zustandsuhr. Die übernommenen Quelldateien, Commit- und Dateihashes stehen in `interop/orb-port-provenance.json`. Android- und Browser-Rasterisierung werden nicht als pixelidentisch bezeichnet.

## 3. Bewegung und Bedienung

Sieben Gestaltungsmomente führen durch Bereitschaft, Zuhören, Displayentstehung, Dokumentarbeit, Diagrammvergrößerung, Erklärung und Ergebnis/Rückkehr. Raum, Lichtschleier, Partikel und Avatar laufen über Zustandswechsel hinweg weiter. Der Originalkern bleibt räumlich und geometrisch fest hinter der Büste; sein zwischengespeichertes Bild vermeidet unnötige Neuberechnung. Der Avatar zeigt zurückhaltende Präsenz; seine vorhandene Büste ist keine vollständige Körper-, Haar- oder Stoffsimulation.

Die Displaychoreografie überlappt Aufmerksamkeit, Raumbildung, Kontur, Einflug und Inhalt innerhalb ungefähr 950 ms. Das ist ein Gestaltungsziel, keine gemessene KI-Laufzeit. Ein echtes Werkzeugergebnis muss später aus der tatsächlichen Verarbeitung stammen.

`ContinuumAvatar.setSpeechFrame()` nimmt ausschließlich tatsächliche Viseme-Ereignisse an. Ohne solche Ereignisse bleibt der Mund neutral, auch wenn die Oberfläche „Sprechen“ darstellt. Vorhandene Mundöffnung wird kompensiert; P/B/M schließen die Lippen, veraltete Ereignisse laufen aus.

## 4. Dateien, Nachweise und Arbeitsstand

| Bestand | Verwendung |
|---|---|
| `continuum-preview/` | Bedienbare HTML/CSS/JS-Gestaltung mit Originalassets |
| `interop/` | Lesbare Adapterquellen, gepinnter Build, Herkunft und Vertragsprüfungen |
| `handoff-3d/` | Echte Blender-Dateien, Display-GLB, Originalavatar-PNG, Skripte und Diagnosen |
| `Continuum_Praesenz_02.mp4` | Aktualisierte Canvas-Raumstudie mit zentralem Kopf, fester Kugel und beiden echten Diagrammen |

Der Film kennzeichnet den Kopf ausdrücklich als ruhiges Blenderbild; er zeigt die neue Anordnung und die Bewegung von Raum, Licht und Diagrammverbindungen. Er ersetzt keinen Live-Gesichtsanimationstest. Die erste Raumstudie mit seitlicher Anordnung dokumentiert den vorherigen Stand und ist kein Nachweis der aktuellen Anordnung.

Codeübergabe: [Draft-PR 850](https://github.com/Ullmann27/device-ai/pull/850), Branch `design/continuum-fui-web-20261115`, Basis `claude/code55-cinematic-evolution-20261112`. Ein Draft-PR ist kein Merge und kein neuer APK-Stand.

## 5. Figma und ehrliche Grenzen

Die [native Figma-Datei](https://www.figma.com/design/rN6yiKiRwzeOPxnqEuaN7O) enthält den früheren Entwurf. Wegen des erreichten Aufrufquotums wurden ihre ursprünglichen Frames noch nicht auf das neue Raster und den Originalkopf umgestellt. Ein gesonderter Codeimport aktualisiert diese nativen Frames nicht automatisch; sein bestätigter Stand gehört zur Auslieferung.

Argon4 ist eine gekennzeichnete Demonstration; eine Original-PDF lag nicht vor. Lokale KI, Android-Werkzeuge, Mikrofon und echte TTS sind hier nicht verbunden. Blender-Rendering, Modellvertrag und Canvas-Nachweise wurden geprüft; GPU-Leistung, Android-Filament-Gleichheit und Fold-Framerate wurden nicht gemessen.

### Abschluss des zusätzlichen Figma-Codeimports

Der separate Codeupload wurde mit HTTP 400 abgewiesen. Es wurde kein neuer Code-Layer importiert. Die bestehende Figma-Datei bleibt ein früherer Designstand; das aktuelle Raster und die Originale sind in der Webfassung und im Projektpaket enthalten.
