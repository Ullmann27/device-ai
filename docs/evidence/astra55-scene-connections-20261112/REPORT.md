# Anschluss vorhandener Cockpit-Bausteine

## Quelle und Ziel

- Basis: `d7a59c6f6855dab3884e44725d6a2c3a55634806`.
- Vorhandene Bausteine: `df43ec3580b7939462619505a1e196cf0373879e`.
- Neuere Hauptlinien-Deckenstreben: `84acc08cb452a40265f585270b0b3bb84444c792`.
- Erster Anschlusscommit: `88418eaeac40174325abf82494e8c91a41426f73`.
- Ziel: gemeinsame Integration des Hauptteams über #799/#801.
- Status: `LOCAL_ONLY / NOT_PUSHED`; kein Main-Merge, keine APK oder Installation.

Receipts nennen den jeweils beobachteten Eltern-HEAD und die tatsächlich
geprüften Quelldateihashes. Ein Eltern-HEAD allein ist kein Nachweis für die
darauf noch uncommittierten Änderungen; hierfür gelten die einzelnen Hashes.

## Differenzieller Anschluss

| Bestehender Baustein | Produktionsanschluss | Erhaltener Hauptpfad |
|---|---|---|
| Fokusfahrt, kontinuierliche Umkehr | `ui/CockpitMonitorMotion.kt`, `ui/ContinuumHomeView.kt` | Bestehende Feed-Anbindung und Monitoransicht |
| Pitch und Z-Tiefe | `avatar3d/CockpitStageFrame.kt`, `avatar3d/FilamentCockpitStage.kt` | Gemeinsame native Quad-/3D-Matrix |
| Verdeckte/inaktive Fokusanimation | `ui/ContinuumHomeView.kt` | Vorhandene Foreground-/Detach-Hooks |
| Native Texte vor der Szene | `ui/ContinuumHomeView.kt` | Bestehender Foreground-Painter; nur Layerwert |
| Materialien, Glas, Raumkanten, Standfuß | `tools/cockpit-workstation/build_scene.py`, `cinema3d/cockpit-workstation.glb` | Deckenstreben aus `84acc08cb4` |
| Indirektes Licht samt Freigabe | `avatar3d/FilamentCockpitStage.kt` | Bestehender Filament-Renderer |
| Große Schrift und Tischkontakt | `ui/CinematicWorkstationLayout.kt` | Kopfrahmen `.70`, `AVATAR_SCALE=.76`, Schutzbox und skalierte Originalkugel |
| Vorhandene Hostprüfung | `tools/cockpit-workstation/{preview.py,render_host.py,WorkstationHostExport.kt}` | Export verwendet den tatsächlichen `pullBack`-Pfad |

Java-/Kotlinpfade in der Tabelle sind relativ zu
`app/src/main/java/at/heinz/deviceai/`, der GLB-Pfad zu `app/src/main/assets/`.
`VirtualComputerMonitorView.kt` und `ContinuumHologramView.kt` sind bytegleich
zur Basis. `sourceViewport`, Klickabsicherung und das originale Avatarasset
bleiben erhalten. Die ursprüngliche Holographic-Kopfverkleinerung auf `.60`
und deren breitere Schutzbox werden nicht über die bereits verkleinerte
Hauptlinien-Rahmung gelegt. Die übernommenen Sichtbarkeitstests bestehen mit
deren unveränderten `.70`-/`.76`-Parametern.

## Tatsächliche Prüfungen

| Prüfung | Ergebnis | Grenze |
|---|---|---|
| 14 StageFrame-/Motion-Tests | PASS | Host-Mathematik |
| 4 bestehende Layouttests | PASS | Keine Android-Bedienung |
| 6 übernommene Visibility-Tests | PASS | Original-GLB-Metadaten und Produktionslayout; kein GPU-Bild |
| Direkte Kotlin-2.0.20-Kompilation | PASS | Android 36, Filament/gltfio 1.56.0; Produktionsgeometrie und Hostexport |
| Home-/Monitor-/UI-Test-API-Kompilation mit Kotlin 2.4.0 | PASS | Bestehende Appklassen als Abhängigkeiten; kein vollständiger Appbuild |
| Kombiniertes GLB zweimal reproduziert | PASS | Beide Läufe bitgleich zum gepackten Asset und Runtimepin |
| Originalavatar-Hash | PASS | Unverändert `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a` |
| Python-Syntax der übernommenen Werkzeuge | PASS | Kein Renderlauf |
| Android-UI-/Lifecycle-Ausführung | NOT_RUN | Zwei Klassen übernommen und kompiliert |
| Filament-GPU / Fold7 / Live-Capture / Klickabnahme | NOT_RUN | Benötigt gemeinsamen App-/Gerätestand |

Geprüfte Sichtbarkeitsbreiten: 240, 320, 360, 412, 600, 640, 700, 720, 884 und
1280 dp; Höhen 500/760/960 dp, Schriftfaktoren 1/1,5/2. Die bestehenden sechs
Visibility-Methoden prüfen Gesicht und Kameradrift, Standfuß-Clipping,
Tischkontakt, getrennte Seitenfelder bei großer Schrift, normale Inhaltshöhe
und die originale geschützte Gesichtsregion. Ihre Endpunkte kommen jetzt aus
dem tatsächlichen `CockpitMonitorMotion.pose`-Pfad. Die `Mehr`-Prüfung aus dem
bestehenden Layouttest bleibt eine Routen-/Layoutprüfung; eine erfolgreiche
Android-Bedienung wird daraus nicht abgeleitet.

Der zusätzliche erste Android-API-Versuch mit Kotlin 2.0.20 ist unter
`android-api/` als fehlgeschlagen dokumentiert: Die vorhandenen Appklassen und
LiteRTLM enthalten Kotlin-2.4-Metadaten. Der passende bereits vorhandene
Kotlin-2.4-Compiler lief anschließend erfolgreich (`android-api-2.4/`). Keine
Metadatenprüfung wurde unterdrückt. Der Digest der Appklassen blieb während
dieser erfolgreichen Prüfung unverändert.

## Reproduktion ohne Gradle

JDK 17, vorhandene Compilerbibliotheken, Android-36-JAR, Filament-/gltfio-1.56.0-
Klassen, `org.json:20240303`, JUnit 4.13.2 und Hamcrest 1.3 verwenden.
Die Compiler-/Abhängigkeitshashes stehen in den Receipts.

```bash
python3 docs/evidence/astra55-scene-connections-20261112/verify_host.py \
  --root "$SCENE_ROOT" --kotlin-lib-dir "$KOTLIN_LIB_DIR" \
  --android-jar "$ANDROID_JAR" --filament-jar "$FILAMENT_JAR" \
  --gltfio-jar "$GLTFIO_JAR" --json-jar "$JSON_JAR" \
  --output "$SCENE_EVIDENCE"

python3 docs/evidence/astra55-scene-connections-20261112/verify_android_api.py \
  --root "$SCENE_ROOT" --kotlin-lib-dir "$KOTLIN_LIB_DIR" \
  --gradle-cache "$SCENE_GRADLE_CACHE" --android-jar "$ANDROID_JAR" \
  --baseline-classes "$SCENE_BASELINE_CLASSES" --output "$SCENE_API_EVIDENCE"
```

Beide Compiler sind auf 512 MiB begrenzt. Der erste Prüfer verwendet die
vorhandene Hostgrenze: Nur `wrap`/`fit` und die Stop-Konstante werden aus den
bestehenden Android-gebundenen Klassen extrahiert. Die Layout-, Framing-,
Motion-, Matrix-, Filament- und Painterklassen werden direkt kompiliert.
Der zweite Prüfer kompiliert die geänderten Androidklassen und die übernommenen
UI-Tests direkt. Kein Prüfer führt Gradle, die App oder einen Bildrender aus.

## Kombination und nächste Abnahme

Der erste Materialstand `1d1ff43d5f` hatte die sechs `arch-cap`-Kappen aus dem
Holographic-Zweig wieder eingefügt. Der tatsächliche Hauptstand `84acc08cb4`
enthält diese Kappen nicht. Dieser übersehene Unterschied wird durch Entfernen
der einen Generatorzeile korrigiert; sonstige Materialien und Geometrien
bleiben gleich. Die historischen Belege unter `material/` beziehen sich auf
den vorherigen Pin `f7a2931d...` und ersetzen keinen Nachweis der Reparatur.

Das korrigierte gepackte GLB hat 14.084 Byte, 77 Knoten, 75 Renderobjekte und
9 Materialien. SHA-256:
`8dfe8e9ae86969c53f5a4c60474cae2cb72f23c517bca6a9134edd8d4c98db9c`.
`canopy-repair/combination.json` belegt die genau sechs entfernten Kappen,
unveränderte übrige Knoteneigenschaften, Materialien und Binärgeometrie sowie
die erhaltenen Hauptlinien-Deckenstreben. `canopy-repair/receipt.json` enthält
die fokussierte Wiederholung der bestehenden 24 Tests, API-Kompilation und
beiden isolierten Generatorläufe. Der Aufruf bleibt unverändert; lediglich das
Ausgabeverzeichnis wird auf `canopy-repair` gesetzt.

Bei Zusammenführung mit `84acc08cb4` müssen Generator, kombiniertes GLB und
dieser neue Pin zusammen übernommen werden. Der Hauptintegrator führt danach
den vorhandenen vollständigen App-/UI-Prüfpfad auf dem gemeinsamen SHA aus.
Erst ein tatsächlicher Gerätelauf kann `GERÄTEBESTÄTIGT` ersetzen.
