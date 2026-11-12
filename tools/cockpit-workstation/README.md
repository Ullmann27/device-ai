# Cockpit – gemeinsame 3D-Szene

Die Vorschau liest die vorhandenen Produktionsquellen und Originalassets.
Sie ersetzt keinen Android-, Filament- oder Fold7-Test.

## Ablauf

1. Der Kotlin-Compiler kompiliert das tatsächliche `CinematicWorkstationLayout`,
   `CockpitMonitorMotion`, `CockpitStageFrame`, die vorhandenen Painter und die
   Originalavatar-Prüfung. Die bestehenden `wrap`-/`fit`-Methoden sowie die
   Stop-Konstante werden unverändert an der vorhandenen Hostgrenze extrahiert.
2. Der Export erzeugt Fold- und Phone-Layouts sowie Ruhe-, Übergangs- und
   Arbeitspose. `CockpitWorldTransforms` liefert die tatsächlichen Matrizen
   für Raum, Avatar und Kugel sowie die Ringwinkel. Der Monitor verwendet
   `CockpitStageFrame.monitorTransform()`. Die Kamerarahmung ruft das aktuelle
   `AvatarFraming.pullBack` genau einmal auf.
3. Blender importiert die gepackte Workstation-GLB, die gepackte Kugel-GLB und
   den Originalavatar in **eine Szene mit einer Kamera**. Der Avatar durchläuft
   dieselbe `AvatarMaterialVariant.prepare`-API wie die App. Der Host prüft die
   tatsächlichen vier Mesh-Ecken von `monitor-live-pixels` gegen
   `CockpitStageFrame.quad()`; ab 0,08 Pixel Abweichung bricht der Lauf ab.
4. Raumhintergrund, Header, Badges, Seitenfenster und Glasbuttons kommen aus
   den Produktions-Paintern. Die 3D-Szene enthält keine zusätzlich montierte
   2D-Kugel, Aura oder Kopfmaske. Beispielinhalt liegt als deckende unlit
   Textur direkt auf dem echten Livepixel-Mesh. Seiteninhalte verwenden die
   Produktionsslots; der Side-Root folgt derselben Room-Matrix und wird wie
   im Produkt bei `monitorWidth / width > .70` verborgen.
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
  --samples 8 --draft --content example --qa-lock "$COCKPIT_QA_LOCK"
```

Das Ausgabeverzeichnis muss leer sein. `--repo-root` erlaubt die Prüfung einer
kombinierten Integrationsquelle, ohne deren Produktionsdateien zu verändern.
`--qa-lock` serialisiert Blender über die vereinbarte gemeinsame Prüfsperre;
ein belegter Lock führt nach 60 Sekunden zu einem sichtbaren Abbruch.
`--draft` erzeugt zunächst nur Fold-/Phone-Ruheansichten. Für die finale Serie
`--samples 32 --content example --video --video-samples 16` verwenden.
Ohne `--draft` entstehen sechs PNGs mit drei Posen je Layout.
`export_host.sh` ist ein Einstieg in dieselbe vollständige Pipeline; die
frühere gespiegelte Kamera-/Layoutkette wurde ersetzt.

Das optionale MP4 zeigt drei Sekunden mit 60 Frames bei 20 Bildern pro Sekunde.
Der Monitor startet nach 500 Millisekunden, fährt mit `Flight.sample` und der
Produktionsdauer nach vorn und später zurück; Haltephasen ergänzen den Clip.
Die Kugel-GLB erhält für jeden Frame die tatsächlichen Winkel aus
`CockpitWorldTransforms.ringAngleDegrees`. Es gibt keine erfundene Ringbahn,
zusätzliches Easing oder Zeitlupe.
Die drei Sekunden behaupten keine vollständige periodische Ringumlaufzeit.
Jeder Videoframe rendert die gemeinsame Szene mit der aktuellen Ringstellung.
Bei unterschiedlicher Samplezahl enthält das Manifest die verwendeten Werte
je Frame. Standardmäßig verwenden Einzelbilder und Video dieselbe Samplezahl.

## Evidenzgrenzen

**HOST_UNIFIED_3D_COMPOSITE:** Gepackte Geometrie, Produktionslayout,
Produktionsmatrizen und Produktions-Ringwinkel; Blender-Beleuchtung und
Java2D-Chrome. Die feste Blender-Prüfbeleuchtung verwendet die Farben und
Positionen der nativen Lichtquellen, ihre Intensitäten sind keine
photometrisch identische Filament-Szene. Der Avatar bleibt in seiner neutralen
Originalpose; Kopf-, Augen- und Lippenanimationen werden hier nicht abgespielt.
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
