# Continuum Referenzavatar v2 — isolierter Anschlussstand

Stand: 4. November 2026. **WIP / UNVERIFIED — Start-Checkpoint.**

## Besitzerauftrag und gewählter Umfang

Heinz möchte den erneut beigefügten cyan-violetten holografischen Kopf als tatsächlich gerenderten, beweglichen Avatar in der vorhandenen Continuum-App, ohne zusätzliche Ausgaben. Der aktuelle Upload ist bytegleich mit der Referenz aus #518: SHA256 `b1575d98f24db11e68c449728c0252652657ad4fd252431e962fd5e7e9288c40`.

Diese Fortsetzung prüft und übernimmt vorhandene Arbeit aus #518 auf dem inzwischen kombinierten Anschlussstand #513. Sie erzeugt keinen zweiten Avatar-Controller, keine zweite sichtbare Avatar-View und keinen weiteren Worker. Lokale Modell-/Renderarbeit darf die Darstellung verbessern; eine Materialableitung allein beweist keine Rekonstruktion der Gesichtsgeometrie.

## Exakte Ausgangsbasis

| Gegenstand | Identität |
| --- | --- |
| Offizielles Repository | `Ullmann27/device-ai` |
| Ziel-PR / Zielzweig | #513 / `integration/continuum-apk-connections-wave2-20261104` |
| Frisch geprüfter GitHub-Basiscommit | `28b228698aef8c52e46c72b71fdd5e1915d6e76c` |
| Vollständiger Basisbaum | `e52acc146e66ae66a95429da2fb0f34242ad1791` |
| App-Unterbaum | `471dccb8844a9aba34de5fa33b7f7ffc619ee163` |
| Lokaler Quellzwilling desselben Baums | `2d5ce59b786f427eacb394484d81796568bb87b4` |
| Eigener Zweig | `codex/continuum-reference-avatar-v2-20261104` |
| Vorheriger Referenz-PR | #518, Startcommit `4167961234452c8845416ec99b241bd501b7f038` auf #512 |
| Unverändertes Paketasset | `app/src/main/assets/avatar3d/continuum-avatar.glb` |
| Paketasset-SHA256 | `5f49eface4c828bb7f7564acee751e4d1f232a6e574b5038c6a564de69c9a4a3` |
| Paketasset-Größe | `10165644` Bytes |

Der neue offizielle Zweig beginnt beim genannten GitHub-Commit. Der lokale Checkout verwendet dessen nachgewiesen identischen vollständigen Baum; lokale und publizierte Commitidentitäten werden getrennt protokolliert. Kein gemeinsam genutzter Quellbaum und kein bestehender fremder Zweig wird zurückgesetzt oder überschrieben.

## Bestehender Anschluss bleibt maßgeblich

`AgentActivity` → `AvatarDirector` → `ContinuumHologramView` → `FilamentAvatarRenderer` / `AvatarMorphMapper`.

Der vorhandene Kotlin-GLB-Vertrag prüft eingebettete Ressourcen, den festgelegten Paketasset-Hash, Größenlimits, Rig und 21 benannte Gesichtsmorphs. Ein freigegebener Kandidat benötigt eine zusammenhängende Änderung von GLB, `AvatarGlbContract.ASSET_SHA256` und `assets/avatar3d/NOTICE.json`. Die aktuelle NOTICE nennt noch eine ältere Assetrevision; diese Inkonsistenz wird beim tatsächlichen Austausch anhand der echten Bytes bereinigt.

Die Kugelwahl und vorhandene ausdrückliche Darstellungspräferenzen bleiben bestehen. Der Offline-Schalter, Kameraeinwilligung, Sprach-/Abbruchregeln und kanonische Werkzeugfreigaben werden nicht erweitert.

## Prüfstand dieses Checkpoints

- Remote-HEAD von #513 und identischen lokalen Basisbaum geprüft; sauberer isolierter Checkout erstellt.
- Bestehende #513-Evidenz gelesen: 352 ausgewählte Android-/JVM-Hosttests und 70 QA-/Update-Vertragstests. Diese historischen Ergebnisse sind an ihre dokumentierten Quellen gebunden und sind **kein neuer Lauf dieses Folgeauftrags**.
- Noch kein neues Asset in die App übernommen. Neue Kandidaten-, Kotlin-/Gradle-, Render- und Negativprüfungen: **NOT_RUN**.
- Bekannter unabhängiger GLB-Befund `rig_hierarchy_orientation` bleibt offen. Eine Materialableitung darf ihn weder als behoben ausgeben noch die Prüfgrenzen lockern.
- Native Filament-Pixel, Fold7 offen/geschlossen, hörbare Sprache/Lippensynchronität, Laufzeit unter Inferenz und Besitzerabnahme der Referenztreue: **NOT_RUN**.

## Gesicherte zweidimensionale Gestaltungsquelle

`assets/avatar/reference/continuum-neural-front-20261104.png` bewahrt die mit `image_gen.imagegen` anhand der Besitzervorlage erzeugte frontale Gestaltungsansicht als tatsächlichen Eingang für die geplante UV-Atlasableitung. Das PNG hat 1086 × 1448 Pixel, RGBA, 1.944.527 Bytes und SHA256 `0ca742ef92bb9db1bd012df7677594c37b58e77c40e4fc92a946e8e0fb886b1e`. PNG-Integrität und bytegleiche Übernahme wurden geprüft.

Die benachbarte Datei `continuum-neural-front-20261104.provenance.json` dokumentiert Erzeugungsart, Besitzerreferenzhash und Verwendungszweck. Dies ist **erzeugte 2D-Gestaltung**, kein Render eines bereits gebauten 3D-Modells und keine Android-/Fold7-Abnahme. Die exakten Pixel sind als reproduzierbarer Ableitungseingang gesichert; eine identische erneute Bildgenerierung wird nicht vorausgesetzt. Das Runtime-Asset bleibt in diesem Checkpoint unverändert.

## Reproduzierter Herkunftsfehler vor dem Assetaustausch

Die neue vorhandene-Bytes-Prüfung `AvatarAssetProvenanceTest.packagedAssetHashAndLengthMatchRuntimeAndNotice` bindet das tatsächliche GLB, den Runtime-Hash und die Angaben in NOTICE aneinander. Auf dem unveränderten alten Asset/NOTICE wurde der Fehler tatsächlich reproduziert: Kotlin-Kompilierung erfolgreich, 86 JUnit-Fälle ausgeführt, 85 bestanden und genau dieser neue Test fehlgeschlagen. NOTICE deklariert `6b199c…`, tatsächliche Datei und Runtime verlangen `5f49ef…`. Die ergänzenden 26 Motion-Prüfungen bestanden weiterhin.

Status: **EXPECTED_RED_STALE_NOTICE**. Der Test wird für den späteren geprüften Asset-/Hash-/NOTICE-Austausch unverändert beibehalten. Originale Befehle, Protokolle, Quellenhashes und alte NOTICE liegen unter `docs/evidence/reference-avatar-v2-20261104/provenance-red/`. Der Testlauf prüft Kotlin/JVM-Hostverhalten; er ist keine Geräte- oder Pixelabnahme. Noch keine Runtime-Datei wurde zur Erzeugung eines grünen Ergebnisses verändert.

## Nächster konkreter Schritt

1. Den von der Gestaltung gelieferten Kandidaten einfrieren: tatsächliche Dateigröße, SHA256, Geometrie-/Rig-/Morphherkunft und abweichende Materialien erfassen.
2. Den echten Kotlin-Vertrag sowie den unabhängigen bestehenden Geometrieprüfer auf genau diesen Bytes ausführen; Ergebnisse getrennt protokollieren. Bewusst manipulierte Kontrollfälle dürfen nicht angenommen werden.
3. Tatsächliche Modellvorschauen mit neutraler Pose, Blinzeln und Sprachpose prüfen. Jede Vorschau muss auf Modellhash und Renderparameter zurückführbar sein.
4. Erst den abgestimmten Kandidaten im eigenen Zweig anschließen und die betroffenen bestehenden Tests erneut ausführen. Den vorhandenen Ein-Avatar-Pfad erhalten.
5. Quellen, Belege und verbleibende Lücken gemeinsam in diesem Draft sichern. Keine APK-/Geräteerfolge aus Host-Rendering ableiten.

## Kompatibilität und Rücknahme

Die Projektverträge aus `AGENTS.md`, `CONTINUUM-EXPERIMENTVERTRAG.md`, `CONTINUUM-EXPERIMENTE.json`, Issue #41 und der aktuellen #513-Übergabe wurden gelesen. EXP-00/EXP-09 (Android-Bau-/Toolchainpfad), EXP-11 (kanonische Orchestrierung) und EXP-12 (Identitäts-/Gedächtniskontinuität) werden durch diese Darstellungsarbeit nicht als abgeschlossen markiert oder umgebaut.

Rücknahme eines später übernommenen Kandidaten stellt GLB, Hash und NOTICE gemeinsam aus der Basisrevision wieder her. Die bestehenden #513-Sprach-/Anschlusskorrekturen bleiben erhalten. Es gibt keine Besitzerdatenmigration.

Kein Main-Merge, kein erzwungenes Branch-Update, keine Actions-Ausführung, kein kostenpflichtiger Dienst, kein APK-Release/keine Installation und keine Änderung von Paketkennung, VersionCode, Signatur oder Modellgewichten sind Bestandteil dieses Auftrags.
