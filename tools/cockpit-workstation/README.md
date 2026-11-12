# Cockpit – quellgebundene Referenzprüfung

Die Vorschau liest die vorhandenen Produktionsquellen und Originalassets.
Sie ersetzt keinen Android-, Filament- oder Fold7-Test.

## Ablauf

1. Der Kotlin-Compiler kompiliert das tatsächliche `CinematicWorkstationLayout`,
   `CockpitMonitorMotion`, `CockpitStageFrame`, die vorhandenen Painter und die
   Originalavatar-Prüfung. Die bestehenden `wrap`-/`fit`-Methoden sowie die
   Stop-Konstante werden unverändert an der vorhandenen Hostgrenze extrahiert.
2. Der Export erzeugt Fold- und Phone-Layouts sowie Ruhe-, Übergangs- und
   Arbeitspose. Originalkugel, `AvatarPresentation`, Raumhintergrund,
   `CockpitWorkstationChrome` und Glasbuttons verwenden die Produktions-Painter.
   Die Kamerarahmung verwendet das aktuelle `AvatarFraming.pullBack` genau einmal.
3. Blender importiert exakt die gepackte Cockpit-GLB und den Originalavatar.
   Monitormatrix und Kameraprojektion verwenden die exportierten Kotlinwerte.
   Alle vier Ecken der Inhaltsfläche müssen auf höchstens 0,08 Pixel mit
   `CockpitStageFrame.quad()` übereinstimmen. Andernfalls bricht der Lauf ab.
4. Die Montage folgt den tatsächlichen Z-Ebenen für Stage, Monitor und
   Vordergrund. Der Avatar bleibt in der neutralen Originalpose. Optionaler
   Beispielinhalt wird deckend auf die tatsächliche Inhalts-Quad projiziert;
   er wird weder eingefärbt noch unter Glasnebel montiert. Seiteninhalte
   verwenden die Produktionsslots. Der Side-Root folgt der Room-Matrix und
   wird wie im Produkt bei `monitorWidth / width > .70` verborgen.
5. Quell-SHA, einzelne Quelldateihashes, Assethashes, Prüfergebnis und Bildhashes
   werden gespeichert. Verändert sich eine verwendete Quelle während des
   Laufes, wird das Ergebnis verworfen.

## Aufruf

Benötigt werden vorhandene Werkzeuge: JDK 17+, Kotlin-Compilerbibliotheken,
`org.json`, Python mit Pillow/NumPy, Blender 4.3+ und für Video FFmpeg. Das Werkzeug
installiert und lädt nichts herunter; es führt weder Gradle noch die App aus.

```bash
python3 tools/cockpit-workstation/preview.py \
  --repo-root "$COCKPIT_SOURCE" \
  --output "$COCKPIT_OUTPUT" \
  --kotlin-lib-dir "$KOTLIN_LIB_DIR" \
  --json-jar "$JSON_JAR" \
  --android-jar "$ANDROID_JAR" \
  --samples 8 --draft --content example
```

Das Ausgabeverzeichnis muss leer sein. `--repo-root` erlaubt die Prüfung einer
kombinierten Integrationsquelle, ohne deren Produktionsdateien zu verändern.
`--draft` erzeugt zunächst nur Fold-/Phone-Ruheansichten. Für die finale Serie
`--samples 32 --content example --video --video-samples 16` verwenden.
Ohne `--draft` entstehen sechs PNGs mit drei Posen je Layout.
`export_host.sh` ist ein Einstieg in dieselbe vollständige Pipeline; die
frühere gespiegelte Kamera-/Layoutkette wurde ersetzt.

Das optionale MP4 zeigt drei Sekunden mit 60 Frames bei 20 Bildern pro Sekunde.
Der Monitor startet nach 500 Millisekunden, fährt mit `Flight.sample` und der
Produktionsdauer nach vorn und später zurück; Haltephasen ergänzen den Clip.
Die Originalkugel erhält die aus `ContinuumHologramView` extrahierte tatsächliche
Phasenfortschreibung, bei konstantem Zustand READY und ohne Audiosignal.
Es gibt keine erfundene Ringbahn, zusätzliches Easing oder Zeitlupe.
Die drei Sekunden behaupten keine vollständige periodische Ringumlaufzeit.
Identische GLB-Posen mit identischer Samplezahl werden im Video wiederverwendet;
Kugel und Energieflüsse werden trotzdem für jeden Zeitpunkt neu berechnet.
Bei unterschiedlicher Samplezahl enthält das Manifest die verwendeten Werte
je Frame. Standardmäßig verwenden Einzelbilder und Video dieselbe Samplezahl.

## Evidenzgrenzen

**HOST_COMPOSITE:** Originale Geometrie, Produktionslayout, Produktionsbewegung
und Produktionskugel; Blender-Beleuchtung und Java2D-Pixel. Die feste
Blender-Prüfbeleuchtung ist keine photometrisch identische Filament-Szene.
Android-View-Lifecycle, MediaProjection, Sprache, Modell, Geräteframerate und
Wärme werden nicht geprüft. Die Bilder tragen diese Grenze sichtbar.

`--content example` ist ausschließlich ein Hostmodus mit sachlichen
Dokument-/Datei-Beispieldaten und sichtbarem Hinweis
**VORSCHAU · BEISPIELINHALT**. Diese Pixel sind keine App-Aufnahme und gelangen
nicht in den Android-Livefeed. `--content empty` hält die Inhaltsfläche leer.

Das Werkzeug ist ausschließlich eine Entwicklungsprüfung und wird nicht in
die APK aufgenommen. Es gibt keinen zusätzlichen Androidrenderer und keine
neue App-Abhängigkeit. Rollback: Die Preview-Dateien entfernen; die App bleibt
unverändert.

## Integrationsziel

Zulieferung an `work/astra55-cockpit-integration-20261112` / PR #799. Aktive
Architekturverträge EXP-06, EXP-11 und EXP-12 bleiben bestehen. Quellenintegration,
Hostnachweis und Gerätebestätigung werden getrennt dokumentiert.
