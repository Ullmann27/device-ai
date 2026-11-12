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
   für Raum, Avatar und Kugel sowie fünf Ringrotationsmatrizen. Die Kugel
   delegiert ihre Tiefenberechnung an `CockpitStageFrame.orbTransform()`.
   `CockpitOrbMotion.sample` liefert die originalen Gier-/Nickwinkel.
   Der Monitor verwendet
   `CockpitStageFrame.monitorTransform()`. Die Kamerarahmung ruft das aktuelle
   `AvatarFraming.pullBack` genau einmal auf.
3. Blender importiert die gepackte Workstation-GLB, die gepackte Kugel-GLB und
   den Originalavatar in **eine Szene mit einer Kamera**. Der Avatar durchläuft
   dieselbe `AvatarMaterialVariant.prepare`-API wie die App. Der Host prüft die
   tatsächlichen fünf Ringknoten gegen die exportierte Anzahl. Die vier
   Mesh-Ecken von `monitor-live-pixels` werden gegen `CockpitStageFrame.quad()`
   projiziert; ab 0,08 Pixel Abweichung bricht der Lauf ab.
4. Raumhintergrund, Header, Badges, Seitenfenster und Glasbuttons kommen aus
   den Produktions-Paintern. Die 3D-Szene enthält keine zusätzlich montierte
   2D-Kugel, Aura oder Kopfmaske. Beispielinhalt liegt als deckende unlit
   Textur direkt auf dem echten Livepixel-Mesh. Seiteninhalte verwenden die
   Produktionsslots; der Side-Root folgt derselben Room-Matrix und wird wie
   im Produkt bei `monitorWidth / width > .70` verborgen.
5. Quell-SHA, einzelne Quelldateihashes, Assethashes, Prüfergebnis und Bildhashes
   werden gespeichert. Verändert sich eine verwendete Quelle während des
   Laufes, wird das Ergebnis verworfen.
6. `CockpitStudioEnvironment.radiance` wird aus der tatsächlichen Kotlin-Quelle
   mitkompiliert. Der Export tastet dieses Feld als lineare 256×128-RGBA-Datei
   ab; Blender nutzt sie als Weltumgebung für glänzende Reflexionsstrahlen.
   Die Lichtformel wird im Vorschaucode nicht nachgebildet. Der Feldhash ist
   in Kotlin-, Blender- und Gesamtmanifest gebunden; die Datei liegt bei den
   Bildern. Die bestehende diffuse Hostaufhellung und der Hostfaktor 0,08
   bleiben erhalten. Cycles-Filterung ist keine Filament-Cubemap-Vorfilterung.

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
ein belegter Lock führt nach 45 Sekunden zu einem sichtbaren Abbruch.
`--draft` erzeugt zunächst nur Fold-/Phone-Ruheansichten. Für die finale Serie
`--samples 32 --content example --video --video-samples 16 --denoise` verwenden,
sofern die lokale Cycles-Version OpenImageDenoise unterstützt. `--denoise`
setzt ausdrücklich `scene.cycles.use_denoising = True` mit dem Backend
`OPENIMAGEDENOISE`. Fehlt dieses Backend, bricht der Render sichtbar ab;
das Werkzeug ersetzt es nicht durch Bildretusche oder einen stillen Filter.
Ohne den Schalter bleibt Denoising aus. Das Manifest verzeichnet den
tatsächlich angeforderten und erfolgreich verwendeten Renderpfad.
Ohne `--draft` entstehen sechs PNGs mit drei Posen je Layout.
`export_host.sh` ist ein Einstieg in dieselbe vollständige Pipeline; die
frühere gespiegelte Kamera-/Layoutkette wurde ersetzt.

Das optionale MP4 zeigt drei Sekunden mit 60 Frames bei 20 Bildern pro Sekunde.
Der Monitor startet nach 500 Millisekunden, fährt mit `Flight.sample` und der
Produktionsdauer nach vorn und später zurück; Haltephasen ergänzen den Clip.
Die Kugel-GLB erhält für jeden Frame fünf tatsächliche Matrizen aus
`CockpitWorldTransforms.ringTransform(identity, index, phase, true)`.
Blender multipliziert die exportierte Matrix mit der jeweiligen ursprünglichen
Knotenmatrix; die Gier-/Nickformeln werden im Host nicht erneut implementiert.
Die Phasen-Updatezeile, Startphase, READY-Energie und Delta-Begrenzung werden
unverändert aus `ContinuumHologramView` gelesen und als kleiner Kotlin-Hostadapter
kompiliert. Dieser spielt eine ausdrücklich synthetische Zeitreihe mit
konstanter READY-Energie ab. Sekunden werden nicht als Phase ausgegeben.
Es gibt keine zusätzliche Ringbahn, kein zusätzliches Easing und keine Zeitlupe.
Die drei Sekunden behaupten keine vollständige periodische Ringumlaufzeit.
Jeder Videoframe rendert die gemeinsame Szene mit der aktuellen Ringstellung.
Bei unterschiedlicher Samplezahl enthält das Manifest die verwendeten Werte
je Frame. Standardmäßig verwenden Einzelbilder und Video dieselbe Samplezahl.

## Evidenzgrenzen

**HOST_UNIFIED_3D_COMPOSITE:** Gepackte Geometrie, Produktionslayout,
Produktionsmatrizen und originale Fünfringbewegung; Blender-Beleuchtung und
Java2D-Chrome. Die feste Blender-Prüfbeleuchtung verwendet die Farben und
Positionen der nativen Lichtquellen, ihre Intensitäten sind keine
photometrisch identische Filament-Szene. Der Avatar bleibt in seiner neutralen
Originalpose; Kopf-, Augen- und Lippenanimationen werden hier nicht abgespielt.
Optionales Denoising betrifft ausschließlich den 3D-Render; die Chrome aus
den Produktions-Paintern wird anschließend unverändert montiert. Die Dokumenttextur wird
unverändert in die Szene eingespeist und nicht separat retuschiert.
Android-View-Lifecycle, MediaProjection, Sprache, Modell, Geräteframerate und
Wärme werden nicht geprüft. Die Bilder tragen diese Grenze sichtbar.

Die Abbildung der Weltumgebung folgt Blenders equirektangulärer Projektion:
[Cycles 4.3.2 projection.h](https://github.com/blender/blender/blob/v4.3.2/intern/cycles/kernel/camera/projection.h).
Die gemeinsame Achsenkonversion ist `(x,y,z) → (x,-z,y)` von Kotlin nach Blender.
Das Feld enthält die echte neue Studio-Radiance, aber keine Messung ihrer
Filament-Belichtung, des Monitorlichts oder von Bloom auf dem Gerät.

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
