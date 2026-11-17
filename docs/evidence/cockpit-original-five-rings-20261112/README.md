# Originalkugel mit fünf Ringen – Renderbeleg

**HOST_UNIFIED_3D_COMPOSITE.** Sechs Bilder und ein tatsächlich gerenderter
Animationsclip aus einer gemeinsamen 3D-Szene. Kein Android-/Filament-/Fold7-
Gerätebeleg. Monitorinhalt ausschließlich **VORSCHAU · BEISPIELINHALT**.

## Quellenbindung

| Gegenstand | Tatsächlich verwendeter Stand |
| --- | --- |
| Render-Checkout-HEAD | `62c8602b1437c6b9f6637b2e101f603452089705` |
| Produktbasis, App und Assets | `62c8602b1437c6b9f6637b2e101f603452089705` |
| Ausgeführter Werkzeug-Checkout | `c1d4046236f732433a5887639ca02aa3726bd2c7` |
| Integrierter Stand mit identischen fünf Werkzeugdateien | `38d5b32e6f976f020f21008e2dbbf10580876203` |

Der Werkzeugstand enthält zusätzlich den Guard für sichtbare Blenderfehler.
Alle fünf ausgeführten Werkzeugdateien wurden einzeln gegen den integrierten
Stand verglichen: gleiche SHA-256-Hashes. 68 verwendete Quell-/Assetdateien
blieben während des Laufs unverändert; der Render-Checkout war sauber.
Die tatsächlichen Einzelhashes stehen in [preview-manifest.json](preview-manifest.json)
und getrennt nach Produkt-/Werkzeugstand in [verification.json](verification.json).

## Bilder und Clip

| Pose | Fold, 884 × 830 | Phone, 411 × 1030 |
| --- | --- | --- |
| Ruhe | [fold-idle.png](fold-idle.png) | [phone-idle.png](phone-idle.png) |
| Übergang | [fold-transition.png](fold-transition.png) | [phone-transition.png](phone-transition.png) |
| Arbeit | [fold-working.png](fold-working.png) | [phone-working.png](phone-working.png) |

[Animationsclip](fold-monitor-motion.mp4): 884 × 830 Pixel, **3,000 Sekunden,
60 Frames, 20 fps**. Die Maße enthalten jeweils die sichtbare Hostkennzeichnung.

Der Monitor fährt ab 0,500 Sekunden mit der produktiven `Flight.sample`-Kurve
420 Millisekunden vor, hält die Arbeitslage und fährt ab 2,080 Sekunden mit
derselben Produktionsdauer zurück. Alle fünf Ringe erhalten je Frame die
Kotlin-Matrizen aus `CockpitWorldTransforms.ringTransform`. Die originale
Gier-/Nickbewegung stammt aus `CockpitOrbMotion.sample`; der Host kopiert
keine Bewegungsformel.

Die Phasenuhr verwendet die tatsächlich extrahierte View-Updatezeile,
Startphase 0,4, READY-Energie 0,18 und die native Delta-Begrenzung auf
0,1 Sekunden. Die synthetische Zeitreihe wird mit 20 Hz abgetastet;
letzter Abtastzeitpunkt 2,950 Sekunden, Phase 1,6083206.
Die drei Sekunden sind kein vollständiger periodischer Ringumlauf.

## Renderqualität und tatsächliche Prüfung

Blender 4.3.2, Cycles CPU, zwei Threads, Seed 731:
**32 Samples für alle sechs Einzelbilder, 16 Samples für alle 60 Videoframes.**
**Denoising ist aus.** Der vorgelagerte Versuch mit OpenImageDenoise scheiterte
vor dem Assetimport mit `enum "OPENIMAGEDENOISE" not found in ()`.
Das Backend war in dieser Umgebung nicht verfügbar. Der endgültige Lauf
verwendete ausdrücklich keinen Denoising-Schalter und keine Bildretusche.

| Prüfung | Ergebnis |
| --- | --- |
| Produktions-Kotlinexport und gemeinsame Szene | PASS |
| Tatsächliche fünf Ringknoten und fünf Matrizen je Frame | PASS |
| 66 Projektionen des echten Livepixel-Meshs gegen Kotlin | maximal 0,0003716 Pixel Abweichung |
| Elf im Previewmanifest verzeichnete Ausgabedateihashes | PASS |
| 68 Quell-/Assethashes und fünf Werkzeughashes | PASS |
| Vollständige MP4-Decodierung | PASS, 60 unterschiedliche Frames |
| MP4-Dauer / Bildrate / Auflösung | 3,000 s / 20 fps / 884 × 830 |
| Gemeinsame Prüfsperre | verwendet |
| Filament / Fold7 / Geräteframerate / Wärme | NOT_RUN / NOT_RUN / NOT_MEASURED / NOT_MEASURED |

Fold- und Phone-Ruhekompositionen sowie ein aus dem fertigen MP4 decodierter
mittlerer Frame wurden tatsächlich angesehen. Gesicht und Schultern bleiben
deckend vor der Kugel, Monitorinhalt lesbar, Tisch und Tastatur sichtbar.
**Beobachteter Randbefund:** Einzelne äußere Plexus-Zierlinien des Originalassets
reichen bis an die obere Stagekante. Dafür wurde keine nachträgliche
Layoutänderung oder Bildkorrektur vorgenommen.

## Assets und Grenzen

| Asset | SHA-256 |
| --- | --- |
| Workstation | `03b6364e2ff396fd9207b129e765ce5d4a93975113315cc3896844e53fd8ad30` |
| Original-Fünfring-Kugel | `fdfe54ce9aa2ed6af5ec6866c1078a4a34ece511e66b29cd1b3672f83229079c` |
| Originalavatar und Laufzeitvariante bei Opazität 1 | `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a` |

Alle drei GLBs teilen Kamera und Tiefenraum. Header, Hintergrund, Badges und
Seitenfenster stammen aus den Produktions-Paintern. Der Dokumentinhalt liegt
als ungetönte, unlit Beispieltextur auf dem tatsächlichen Livepixel-Mesh.
Die Blender-Beleuchtung ist eine Hostannäherung; Java2D übernimmt die
Schriftrasterung. Der Avatar bleibt in neutraler Originalpose. App-Capture,
Android-Lifecycle, Sprache sowie Lippen-/Augenbewegung wurden hier nicht
ausgeführt. Die Rohbilder jedes Renderframes sind im
[Blender-Manifest](blender-manifest.json) gehasht; persistiert sind die sechs
Gesamtbilder, der Clip und die Export-/Prüfdateien.

## Reproduktion

Die [Werkzeuganleitung](../../../tools/cockpit-workstation/README.md) beschreibt
die vorhandenen Compiler- und Renderabhängigkeiten. Tatsächliche Optionen:

```bash
python3 "$COCKPIT_TOOLS/tools/cockpit-workstation/preview.py" \
  --repo-root "$COCKPIT_SOURCE" --output "$COCKPIT_OUTPUT" \
  --kotlin-lib-dir "$KOTLIN_LIB_DIR" --json-jar "$JSON_JAR" \
  --android-jar "$ANDROID_JAR" --samples 32 --video --video-samples 16 \
  --content example --qa-lock "$COCKPIT_QA_LOCK"
```

Quellen-/Werkzeugstände wie oben; leeres Ausgabeverzeichnis verwenden.
Dieser Beleg ersetzt die historischen Vier-Ring-Zwischenbilder für die
genannte Produktbasis. Er behauptet keinen späteren, ungeprüften Appstand.
