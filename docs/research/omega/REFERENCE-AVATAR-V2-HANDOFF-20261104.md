# Continuum Referenzavatar v2 — integrierter, hostgeprüfter Draft

Stand: 4. November 2026. Der Referenzatlas ist auf dem vorhandenen beweglichen 3D-Kopf eingebettet und als tatsächliches GLB im bestehenden Android-Quellpfad angeschlossen. **Quellen und Asset sind hostgeprüft; native Geräteabnahme und Besitzerabnahme der Ähnlichkeit bleiben offen.**

## Auftrag und Ergebnis

Heinz möchte den beigefügten cyan-violetten holografischen Kopf als gerenderten, beweglichen Avatar in Continuum ohne zusätzliche Ausgaben. Der Upload hat SHA-256 `b1575d98f24db11e68c449728c0252652657ad4fd252431e962fd5e7e9288c40` und ist bytegleich mit der Referenz aus #518. Diese Fortsetzung nutzt den kombinierten Anschlussstand #513 in einem isolierten Folge-Draft #521.

Eine erzeugte frontale 2D-Gestaltungsansicht wurde als reproduzierbare Quelle gesichert und auf die echte vorhandene Geometrie gebacken. Das Paket enthält Material/UV-Layout, dieselben Rig-Knoten und 21 Gesichtsmorphs. Es ist eine referenzgeführte Annäherung; die Gesichtsgeometrie wurde nicht aus einem Einzelbild rekonstruiert. Unsichtbare Profilbereiche und im Porträt enthaltene Beleuchtung bleiben gestalterische Grenzen.

Der Anschluss bleibt `AgentActivity` → `AvatarDirector` → `ContinuumHologramView` → `FilamentAvatarRenderer` / `AvatarMorphMapper`. Im App-Produktionscode änderte sich nur der Asset-Hash. Es bleibt bei derselben sichtbaren Avatar-View und demselben Director.

## Ausgangsbasis und Paketidentität

| Gegenstand | Identität |
| --- | --- |
| Repository / Draft | `Ullmann27/device-ai` / #521 |
| Zielzweig | `integration/continuum-apk-connections-wave2-20261104` (#513) |
| Offizieller Basiscommit | `28b228698aef8c52e46c72b71fdd5e1915d6e76c` |
| Vollständiger Basisbaum | `e52acc146e66ae66a95429da2fb0f34242ad1791` |
| Lokaler Quellzwilling desselben Baums | `2d5ce59b786f427eacb394484d81796568bb87b4` |
| Eigener Zweig | `codex/continuum-reference-avatar-v2-20261104` |
| Altes Paketasset | SHA-256 `5f49eface4c828bb7f7564acee751e4d1f232a6e574b5038c6a564de69c9a4a3`, 10.165.644 Bytes |
| Geprüftes neues Paketasset | SHA-256 `70b93bfdfb10c65662f6d37857e4a6a403dfb6ad950a9220acf91cb4145ab145`, 12.552.372 Bytes |
| Paketpfad | `app/src/main/assets/avatar3d/continuum-avatar.glb` |
| Neue `AvatarGlbContract.kt` | SHA-256 `1f416d4ba9c5865550a225e66deb86e893143a7ff5530d06d59772950a05d269` |
| Neue Paket-`NOTICE.json` | SHA-256 `fb07b7b96aaebda7c223357150783ba7fb2fc4f83efad5cc4522191b6b9512f0` |

Lokale und offizielle Commitidentitäten werden getrennt behandelt. Der vollständige lokale Baum wird mit dem erzeugten GitHub-Baum verglichen. Die Veröffentlichung verwendet den bisherigen offiziellen Branch-HEAD als Elterncommit und Lease; fremder Verlauf wird nicht überschrieben. Finaler Remote-Commit und Baum stehen in der PR-Beschreibung.

## Herkunft und reproduzierbare Ableitung

`assets/avatar/reference/continuum-neural-front-20261104.png` ist die mit `image_gen.imagegen` erzeugte **2D-Gestaltungsquelle**, 1086 × 1448 Pixel, RGBA, 1.944.527 Bytes, SHA-256 `0ca742ef92bb9db1bd012df7677594c37b58e77c40e4fc92a946e8e0fb886b1e`. Die Provenienzdatei nennt Werkzeug, Besitzerreferenzhash und Zweck. Diese Pixel werden als tatsächlicher Eingang für die Materialableitung verwendet; sie sind kein angeblicher 3D-Render.

Der Blender-Builder unter `tools/avatar-3d/reference-atlas/` erzeugt einen eingebetteten 2048²-Atlas, SHA-256 `92d5adebe1e794cfbed434ac533f53dda25053177129a399e5eaf0c07c79533b`. UV-Nähte erfordern 3.670 zusätzliche Gesichtsvertex-Duplikate und je 128 Irisvertex-Duplikate. 189 isolierte Dreiecksflächen beheben die zunächst gefundenen UV-Konflikte. Ursprüngliche Positionen, orientierte Dreiecke, Normalen, Morphdeltas, Hautgewichte und Gelenkzuordnungen bleiben nach Rückabbildung erhalten: unabhängige maximale geschützte Skalarabweichung `0.0`.

NOTICE trennt das CC0-Erbe der MakeHuman-Meshassets von der erzeugten Textur. Für deren Pixel wird keine CC0-Lizenz behauptet. Quell-GLB, Bild-, Builder- und Atlas-Hashes sind dokumentiert. Der alte GLB-Eingang ist per `git show` aus dem offiziellen Basiscommit vollständig wiedergewinnbar.

`tools/avatar-3d/reference-atlas/README.md` enthält relative Befehle für Ableitung, Rendern, unabhängige Prüfung, Kotlin-Poseexport und Replay. Die Reproduktion benötigt keine ursprünglichen Workspace-Pfade. Originale Ausführungsbelege behalten ihre damaligen Pfade unverändert.

## Tatsächlich geprüfte Ergebnisse

| Prüfung | Ergebnis |
| --- | --- |
| Neue Herkunftsprüfung vor Austausch | 85/86 JUnit bestanden, genau `AvatarAssetProvenanceTest` rot wegen veralteter NOTICE; 26/26 Motion bestanden |
| Derselbe unveränderte Test nach Austausch | Kotlin-Kompilierung Exit 0, 86/86 JUnit bestanden, 26/26 Motion bestanden |
| Tatsächlicher Kotlin-GLB-Vertrag | PASS auf SHA `70b93…`, 12.552.372 Bytes, alle 21 Zielnamen |
| Unabhängige Geometrie-/Rig-/Morpherhaltung | PASS; Knoten, inverse Bindmatrizen, Winding, Hautgewichte und alle 21 Morphs erhalten |
| Erster UV-Kandidat, eingefrorener finaler Prüfer | FAIL: 608 widersprüchliche Texel bei 2048²; Geometrieerhaltung PASS |
| Akzeptierter zweiter UV-Kandidat, derselbe Prüfer | PASS: 0 widersprüchliche Texel, 0 degenerierte UV-Dreiecke bei 2048² |
| Beschädigte Kontrollfälle | Alle 6 zurückgewiesen: Geometrie, AA-Morph, Kieferpivot, Winding, Hautgewicht, kollabierte UVs |
| Frischer Produktions-Poseexport | Kompilierung und Export Exit 0; Quellen-/Paket-/NOTICE-Hashes gebunden |
| Numerisches Replay aller 108 Posen | PASS auf dem Paketasset; maximale Kontrollmatrixabweichung ca. `3,67e-7`, Gesichtspositionsabweichung ca. `6,44e-8 m`, unter unveränderten `2e-6`-Grenzen |
| Historischer V1-Rigfensterprüfer | Tatsächlicher Kandidat weiterhin FAIL bei `rig_hierarchy_orientation`; ursprünglicher V1-Testkörper PASS |
| Getrennter bestehender Hinge-V2-Vertrag | `PASS_SEPARATE_HINGE_AND_FIXED_REGION_V2`, Exit 0 auf demselben Paketasset |
| Patchform | `git diff --check` bestanden |

Die Herkunftsprüfung bindet tatsächliche Paketbytes, Laufzeithash, NOTICE-Hash und Länge. Sie wurde zwischen ROT und GRÜN nicht abgeschwächt. Ihr SHA-256 bleibt `bb9a0256e64d091c6d448ea1e8dc0f2216ce552812113c703f65bd070a76adfb`. Der abgelehnte erste GLB liegt komprimiert samt Hashbeleg vor. Der UV-Vergleich nutzt denselben finalen Prüfer und dieselbe Abtastung; endliche Rasterabtastung beweist nicht die Abwesenheit jedes Subtexel-Overlaps.

Der separate V2-Pass macht den tatsächlichen Kandidaten nicht nachträglich zu einem V1-Pass. Der diagnostisch transformierte Legacy-Prüfblick ist ausdrücklich nicht das Paketasset. V2 prüft funktionale stilisierte Gelenk-/Fixbereichsgrenzen, keine gemessene menschliche Kieferanatomie.

Die finalen Host-/Poseprüfungen liefen nach dem Paket-/Hash-/NOTICE-Austausch auf einem noch nicht committeten, anschließend eingefrorenen Arbeitsbaum. Der damalige lokale HEAD `460730178b4f0a52648462ddb61a0f8737fc1b0a` enthält diese Änderungen noch nicht. Die Belege binden deshalb tatsächliche Dateien per SHA-256; deren Identität wird bei Veröffentlichung bewahrt. Ein früherer Commit wird nicht als Träger des neuen Pakets ausgegeben.

## Modellbelege

Unter `docs/evidence/reference-avatar-v2-20261104/renders/` liegen echte GLB-Renderings: frontal, dreiviertel, Blinzeln und Sprechdiagnose, mit Modellhash, Kamera, Gewichten und Bildhash. Die statischen Diagnoseposen sind von der quellgebundenen Produktions-Poseprüfung getrennt.

`renders/hero/quarter.png` hat 768 × 1024 Pixel, 32 Cycles-Samples und SHA-256 `faeb8a377179326724404640d295c28bdaa047cbeecf9114c5a9143a51d4fe10`. Der nähere Inspektionsausschnitt ist dokumentiert; dafür wurde keine App-Kamera umgebaut. Beleuchtung und Glow stammen vom Host-Renderer.

Finaler Poseexport: SHA-256 `43b1e4e65c5d52de20c0efee60886a17bcd9b30e070286e0fbd20a3d7e10080f`, 12 Diagnoseeingaben und 96 Idle-Zeitschritte des tatsächlich kompilierten Mappers. Ein Vergleich bestätigt exakte Gleichheit aller 108 Frameeingaben, Morphgewichte, Gelenkwinkel und des Kamerafokus zum früheren Videoexport; nur Quellen-/Assetmetadaten unterscheiden sich. Evidenzindex: `docs/evidence/reference-avatar-v2-20261104/README.md`.

Der zusätzlich ausgelieferte achtsekündige Host-Animationsfilm enthält 96 echte GLB-Frames bei 12 fps, H.264, 384 × 512, ohne Audio. Video-SHA-256: `657e63521b925f7919de28552ea1bedea287545d9a2a65aaec892fb699690f87`, 1.434.559 Bytes. Alle Framehashes, Anzahl und Videoprobe wurden geprüft. Kompakte Belege liegen unter `animation/`; Video und 96 Einzelbilder werden nicht zusätzlich im Repository gespeichert. Der Film nutzt den oben exakt verglichenen früheren Poseexport.

## Offene Abnahme und Umfang

Native Filament-Pixel, Fold7 offen/geschlossen, hörbare Live-TTS-/Lippensynchronität, FPS/Temperatur unter Inferenz und Besitzerabnahme der Referenztreue: **NOT_RUN**. Die belegte Kompilierung ist Kotlin/JVM-Hostkompilierung; ein neuer vollständiger APK-/Android-Build wird nicht behauptet. Host-Poseeingaben sind synthetisch und keine gefilmte Kamera- oder Sprachsitzung.

Der bestehende Kamera-/Blickversatz bleibt erhalten. Die Assetarbeit ergänzt keinen `WARM`-Zustand und keine App-Steuerung für ein Lächeln; der vorhandene Smile-Morph beweist keine Laufzeitverdrahtung. Kugelwahl und Darstellungspräferenzen bleiben bestehen. Offline-Schalter, Kameraeinwilligung, Sprach-/Abbruchregeln und Werkzeugfreigaben werden nicht erweitert.

`AGENTS.md`, `CONTINUUM-EXPERIMENTVERTRAG.md`, `CONTINUUM-EXPERIMENTE.json`, Issue #41 und die #513-Übergabe bleiben maßgeblich. Diese Darstellung markiert keine offenen Android-, Orchestrierungs- oder Gedächtnisexperimente als abgeschlossen.

Rücknahme stellt GLB, Hash und NOTICE gemeinsam aus der Basisrevision wieder her; die #513-Anschluss- und Sprachkorrekturen bleiben erhalten. Keine Besitzerdatenmigration. Der neue Provenienztest würde die historisch veraltete Basis-NOTICE erneut sichtbar machen; ein Rollback darf diesen bekannten Fehler nicht als grüne Abnahme ausgeben.

Kein Main-Merge, keine erzwungene Branchüberschreibung, kein Workflowdispatch, keine kostenpflichtige 3D-API, kein APK-Release und keine Installation. Paketkennung, VersionCode, Signatur, Berechtigungen und Modellgewichte bleiben unverändert.
