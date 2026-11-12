# Code55: Studio-HOST-Prüfung · 12.11.2026

## Ergebnis

Sechs Bilder erfolgreich aus Produktionsquellen und Originalassets erzeugt und
visuell geprüft: Fold und Phone, jeweils Ruhe, Übergang und Arbeitszustand.
Der Lauf meldet `sourceStableDuringRun=true`; sämtliche Ausgabehashes stimmen.
Die größte Projektionsabweichung der echten Monitor-Mesh-Ecken gegenüber
`CockpitStageFrame.quad()` beträgt **0.000368126 Pixel**
bei einer Abbruchgrenze von 0,08 Pixel.

- Quellcommit: `3599fd36aad78436a03718a53a89d42dbe0d2254`.
- Bytegleicher veröffentlichter Checkpoint: `8259efa93eefd0701b0f86dfbd6414eca6575886`;
  beide Git-Trees sind `5ea94f5d0559190b6dea2f2e190d3692d6ca46f3`.
- Renderer: Blender 4.3.2 Cycles CPU; 2 CPU-Threads; 32 Samples.
- Denoising: aus. Der erste, ausdrücklich mit OIDN angeforderte Lauf brach vor
  dem ersten Bild sichtbar ab: `OPENIMAGEDENOISE` war im installierten Blender
  kein verfügbares Enum. Der erfolgreiche Lauf wurde ausdrücklich ohne
  `--denoise` neu gestartet; keine Bildretusche oder nachträgliche Glättung.
- Originalavatar, Workstation, Fünfringkugel und Produktmaterialien unverändert.
- Kein Gradle- oder Geräteversuch durch diese Hostprüfung.

## Produktionsbindung der neuen Reflexionen

`preview.py` kompiliert die tatsächliche Produktionsdatei
`app/src/main/java/at/heinz/deviceai/avatar3d/CockpitStudioEnvironment.kt`.
`WorkstationHostExport.kt` ruft deren `radiance` für die Richtungen einer
256×128-Umgebung auf. Keine Kopie der Lichtformel im Host.

Die Datei `studio-environment.rgba32f` enthält lineare RGBA-Werte, little-endian,
Zeilen von unten nach oben. SHA-256:
`5fc436abb7424a98ca46a67867861f6a51ccbe307f740b914118016341f3ae78`.

Blender liest das hashgeprüfte Feld als lineare equirektanguläre Weltumgebung
für glänzende Reflexionsstrahlen. Die bestehende diffuse Hostaufhellung und
Stärke 0,08 bleiben erhalten. Die Achsenkonversion ist identisch zur bestehenden
Geometriekette: Kotlin `(x,y,z)` → Blender `(x,-z,y)`.
Die Projektionszuordnung folgt [Cycles 4.3.2 projection.h](https://github.com/blender/blender/blob/v4.3.2/intern/cycles/kernel/camera/projection.h).
Das ist keine photometrische Gleichsetzung von Cycles und Filament; insbesondere
wird Filaments native Cubemap-Vorfilterung hier nicht ausgeführt.

Die vier Änderungen betreffen ausschließlich das vorhandene Hostwerkzeug:
`preview.py`, `WorkstationHostExport.kt`, `render_host.py`, `README.md`
unter `tools/cockpit-workstation/`. Python-Syntaxprüfung und `git diff --check`
bestanden; Kotlin-Compile, Export und Blenderlauf erfolgreich.

## Sichtprüfung

Alle sechs fertig komponierten PNGs wurden einzeln geöffnet:

- Avatar und Kugel stehen konsistent in der Szene. Die Originalgeometrie bleibt
  erkennbar; cyan/violette Kanten und Reflexe sind sichtbar.
- Der Monitor hält seinen Beispielinhalt in allen drei Posen innerhalb des
  realen Pixelmeshs; kein sichtbares Ablösen von Textur und Rahmen.
- Fold: Beide seitlichen Dokument-/Entwurfsflächen bleiben lesbar und getrennt
  vom Hauptmonitor. Phone: Seitliche Monitore sind gemäß Produktionsregel verborgen.
- Eingabezeile und acht Navigationstasten bleiben innerhalb der Bildfläche.
- Beispielinhalt und fehlender Gerätebeleg sind sichtbar gekennzeichnet.
- Leichte Körnung an 3D-Kanten gehört zu diesem Cycles-Lauf ohne Denoising;
  daraus wird keine Aussage über Android-Bildqualität abgeleitet.

| PNG | Größte Projektionsabweichung, Pixel |
|---|---:|
| fold-idle.png | 0.000338100 |
| fold-transition.png | 0.000368126 |
| fold-working.png | 0.000340759 |
| phone-idle.png | 0.000118773 |
| phone-transition.png | 0.000118280 |
| phone-working.png | 0.000119281 |

## Dateien und Reproduktion

Ausgabeordner: `/workspace/scratch/ca86708b9801/evidence/code55-cockpit-studio-20261112`.

`preview-manifest.json` enthält Quell- und Werkzeughashes, Ausgabehashes,
Assetpins und den Status des Quellbaums. `scene-export.json` enthält die aus
Kotlin exportierten Matrizen. `blender-manifest.json` enthält die Messwerte
aller sechs Ansichten; das Radiancefeld liegt unverändert daneben.
Fold-PNGs: 884×830; Phone-PNGs: 411×1030, jeweils einschließlich Evidenzfußzeile.

Erfolgreicher Befehl, aus dem Repository ausgeführt:

```bash
python3 tools/cockpit-workstation/preview.py \
  --repo-root /workspace/scratch/ca86708b9801/device-ai \
  --output /workspace/scratch/ca86708b9801/evidence/code55-cockpit-studio-20261112 \
  --kotlin-lib-dir /tmp/code55-cockpit-kotlin-lib \
  --json-jar /workspace/scratch/c9f74e2967e3/gradle-home/caches/modules-2/files-2.1/org.json/json/20240303/ebb88e8fb5122b7506d5cf1d69f1ccdb790d22a/json-20240303.jar \
  --android-jar /tmp/astra55-android-sdk/platforms/android-36/android.jar \
  --samples 32 --content example --qa-lock /tmp/code55-cockpit-preview.lock
```

Die Compilerbibliotheken stammen aus dem vorhandenen Kotlin-2.4.0-Cache;
kein Download und keine Installation. Für einen neuen Lauf ein leeres
Ausgabeverzeichnis wählen. Der gemeinsame Renderlock wurde verwendet.

## Aussagegrenze

`HOST_UNIFIED_3D_COMPOSITE`. Produktionslayout, tatsächliche Transformationsmatrizen,
Produktions-Radiancefeld und Originalassets sind gebunden. Das sind Standbilder
an drei Animationspositionen, kein Nachweis der Laufzeit-Flüssigkeit.
Avatar-Mimik, Augen-/Lippenbewegungen, natives Monitorlicht, Bloom, Android-Lifecycle,
Gemini, Livefeed, Framerate und Wärme wurden nicht am Gerät ausgeführt.
Filament und Fold7 bleiben in den Manifesten ausdrücklich `NOT_RUN`.
