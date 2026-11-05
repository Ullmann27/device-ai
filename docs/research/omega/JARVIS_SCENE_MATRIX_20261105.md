# JARVIS-Szenenmatrix (Stand 05.11.2026, Branch `claude/continuum-jarvis-cockpit-z7bo60`, PR #565)

Grundsatz: Jede Animation hängt an einem echten App-Ereignis. Der universelle Adapter `ui/JarvisActionScenes.kt`
ordnet echte Aktionen einer wiederverwendbaren Szene des vorhandenen `JarvisWorkWindow` zu (Art, Akzent, Ursprung).
Er gewährt keine Werkzeuge und trägt keine Daten. Belegstufen: **impl.** = Code vorhanden, **Host-Test** =
Robolectric/JVM grün, **Gerät** = auf dem Fold7 bestätigt (bisher bei keinem Punkt).

| Ablauf | Reale Daten | Berechtigung | Adapter / Ereignis | Animation | Stand |
|---|---|---|---|---|---|
| START-Cockpit, Originalkopf, Originalkugel | GLB `c8b88385…`, `ContinuumHologramRenderer` (transparent) | keine | `ContinuumHologramView`, `AvatarDirector` | Atmen, Blinzeln, Blick, Kugelringe | impl., Host-Test; Filament nur auf Gerät |
| Systemstatus-Fenster | `systemCenterStatus()`, Live-Hardware alle 5 s | keine | Kachel → `navigateDashboardTarget` → `showCenter` → `JarvisWorkWindowHost` | Fenster fliegt aus der Kachel mit Lichtbahn, Avatar blickt hin, Schließen auf derselben Bahn | impl., Host-Test |
| Gedächtnis, Logik, Web, Lernen, Entwicklung | jeweilige Center-Statusabfragen | keine (Web: Internetfreigabe) | wie oben, `JarvisActionScenes.forCentre` | gleiches Fenster aus der **berührten** Kachel; ohne Kachel aus einem Punkt neben dem Gesicht | impl., Host-Test (`everyCentreTileUnfolds…`) |
| Werkzeuge der `ToolRegistry` (26) | echte Runner-Ergebnisse | je Werkzeug (`Risk`, Freigabe) | `ToolGate` → `cockpitTriggers.toolStarted/Finished`, `ContinuumTaskEffects` | Prozess-Zeitleiste, Task-Effekte; Szenenart je Werkzeug zugeordnet (`toolKind`) | Zuordnung Host-Test; **eigenes Arbeitsfenster je Werkzeuglauf fehlt** |
| Zuhören → Denken → Sprechen → Ruhe | Spracherkennung, TTS-Fortschritt (`onRangeStart`) | Mikrofon | `AvatarDirector`, `ApprovedSpeechChunkQueue` | Lippen nach Wortbereichen, Mund zu nach Ende/STOP, lange Antworten vollständig | impl., Host-Test; Audio-Synchronität nur auf Gerät prüfbar |
| Kamera / Blick folgt dem Besitzer | Kamerabeobachtung nach Zustimmung | Kamera, ausdrückliche Zustimmung | `toggleConversationCamera`, Observed-Gaze im Director | Augen führen, Kopf folgt geglättet | impl., Host-Test (`AvatarObservedGazeTest`); Gerät offen |
| Gmail / E-Mail | **keine Quelle**: App meldet „Google / Gmail … noch nicht verbunden“ | OAuth/Kontozugriff fehlt | `AccountCardFlyIn` vorgesehen, ohne Quelle | – | **nicht umsetzbar ohne echte Anbindung** |
| PDF / Dokument | **keine allgemeine PDF-Aufnahme** (`DocumentQuestion`: Quelle fehlt) | Dokumentanbieter (SAF) | – | – | offen |
| Coding-Bots Kora / Forge / Lint | Bot-Profile in `ChiefBotProfileStore` | – | `workerStarted/Reported` | Zeitleiste | Profile vorhanden; echte RED→GREEN-Kette offen |
| Kalender, Karte, Download, Sicherheit | keine Quelle in der App | jeweils eigene | – | – | offen |
| Thermik/Akku | `PowerManager.currentThermalStatus`, Energiesparmodus | keine | `JarvisRenderQuality.resolve` (LOW/MEDIUM/HIGH) | weniger Bilder/Partikel bei Wärme, Inferenz, Energiesparen | impl.; Wirkung auf dem Fold7 nicht gemessen |

## Nächste Schritte (in dieser Reihenfolge)
1. Werkzeuglauf als Arbeitsfenster: `toolStarted` → `forTool` → kompaktes Fenster neben dem Gesicht mit Werkzeugname
   und echtem Status; Ende nur nach `toolFinished` (Fehlschlag bernstein, Erfolg ohne künstliches Grün).
2. App-Identität beim Öffnen: Label und Symbol aus `PackageManager` des tatsächlich aufgelösten Pakets.
3. Dokument: SAF-Auswahl → `PdfRenderer` → echte Seiten als Fensterinhalt; ohne Datei eine klare Meldung.
4. Fold7: Filament-Kopf, Bildrate, Wärme, Lippen zu echter TTS, Kamera-Blick; erst danach gelten die Szenen als abgenommen.
