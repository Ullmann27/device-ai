# Cockpit – reproduzierbare Hostbilder

Die Vorschau liest die vorhandenen Produktionsquellen und Originalassets.
Sie ersetzt keinen Android-, Filament- oder Fold7-Test.

## Ablauf

1. Der Kotlin-Compiler kompiliert das tatsächliche `CinematicWorkstationLayout`,
   `CockpitMonitorMotion`, `CockpitStageFrame`, die vorhandenen Painter und die
   Originalavatar-Prüfung. Die bestehenden `wrap`-/`fit`-Methoden sowie die
   Stop-Konstante werden unverändert an der vorhandenen Hostgrenze extrahiert.
2. Der Export erzeugt Fold- und Phone-Layouts sowie Ruhe-, Übergangs- und
   Arbeitspose. Die Originalkugel und die vorhandenen Glasbuttons kommen aus
   demselben Kotlin-Painter wie in der App, hier über Java2D.
3. Blender importiert exakt die gepackte Cockpit-GLB und den Originalavatar.
   Monitormatrix und Kameraprojektion verwenden die exportierten Kotlinwerte.
   Alle vier Ecken der Inhaltsfläche müssen auf höchstens 0,08 Pixel mit
   `CockpitStageFrame.quad()` übereinstimmen. Andernfalls bricht der Lauf ab.
4. Die Montage verwendet die Produktionsrahmung und Alphamaske des Avatars.
   Der Avatar bleibt in der neutralen Originalpose. Der Monitor enthält kein
   Livebild; die sichtbaren Texte kennzeichnen den synthetischen Prüflauf.
5. Quell-SHA, einzelne Quelldateihashes, Assethashes, Prüfergebnis und Bildhashes
   werden gespeichert. Verändert sich eine verwendete Quelle während des
   Laufes, wird das Ergebnis verworfen.

## Aufruf

Benötigt werden vorhandene Werkzeuge: JDK 17+, Kotlin-Compilerbibliotheken,
`org.json`, Python mit Pillow, Blender 4.3+ und für Video FFmpeg. Das Werkzeug
installiert und lädt nichts herunter; es führt weder Gradle noch die App aus.

```bash
python3 tools/cockpit-workstation/preview.py \
  --repo-root "$COCKPIT_SOURCE" \
  --output "$COCKPIT_OUTPUT" \
  --kotlin-lib-dir "$KOTLIN_LIB_DIR" \
  --json-jar "$JSON_JAR" \
  --android-jar "$ANDROID_JAR" \
  --samples 12 --video
```

Das Ausgabeverzeichnis muss leer sein. `--repo-root` erlaubt die Prüfung einer
kombinierten Integrationsquelle, ohne deren Produktionsdateien zu verändern.
Sechs PNGs zeigen die drei Posen je Layout. Das optionale MP4 zeigt 49 durch
`Flight.sample` bestimmte Zeitpunkte: eine volle Hin- und Rückfahrt. Die
Abspielrate ergibt sich aus der Produktionsdauer; es gibt kein zusätzliches
Easing und keine Zeitlupe. Das letzte Bild steht einen Frame lang.

## Evidenzgrenzen

**HOST_COMPOSITE:** Originale Geometrie, Produktionslayout, Produktionsbewegung
und Produktionskugel; Blender-Beleuchtung und Java2D-Pixel. Die feste
Blender-Prüfbeleuchtung ist keine photometrisch identische Filament-Szene.
Android-View-Lifecycle, MediaProjection, Sprache, Modell, Geräteframerate und
Wärme werden nicht geprüft. Die Bilder tragen diese Grenze sichtbar.

Das Werkzeug ist ausschließlich eine Entwicklungsprüfung und wird nicht in
die APK aufgenommen. Es gibt keinen zusätzlichen Androidrenderer und keine
neue App-Abhängigkeit. Rollback: Die Preview-Dateien entfernen; die App bleibt
unverändert.

## Integrationsziel

Zulieferung an `work/astra55-cockpit-integration-20261112` / PR #799. Aktive
Architekturverträge EXP-06, EXP-11 und EXP-12 bleiben bestehen. Quellenintegration,
Hostnachweis und Gerätebestätigung werden getrennt dokumentiert.
