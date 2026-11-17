# Continuum: Referenzkopf ohne externe Generierungskosten

## Auftrag und Status

Besitzerauftrag vom 04.11.2026: den beigefügten cyan-violetten holografischen Kopf als Avatar gestalten, tatsächlich rendern und an die vorhandene App anschließen. Ergänzende Grenze: kein Geld für externe Dienste. Status dieses Start-Checkpoints: **WIP / UNVERIFIED**.

## Feste Ausgangsbasis

- Repository: Ullmann27/device-ai
- Basis-PR: #512, integration/continuum-followup-connectors-20261104-822e
- Basis-SHA: f9267a3743a87a77bf6421011c014b2ca2d6b0d6
- Eigener Branch: codex/continuum-reference-avatar-20261104
- Vorhandener Avatar: app/src/main/assets/avatar3d/continuum-avatar.glb
- Lokal bestätigter Ausgangs-SHA256: 5f49eface4c828bb7f7564acee751e4d1f232a6e574b5038c6a564de69c9a4a3
- Größe der Ausgangsdatei: 10165644 Bytes

## Gewählter Umsetzungspfad

Meshy CLI 0.8.2 ist verfügbar, aber nicht angemeldet. Kein Meshy-Generierungsauftrag wurde eingereicht. Keine kostenpflichtigen Dienste, Guthabenkäufe oder Cloud-Modelle werden für diesen Auftrag aktiviert.

Der vorhandene animierbare MakeHuman-abgeleitete Kopf wird lokal mit Blender 4.3.2 gerendert. Die neue gestalterische Ableitung nutzt den aktuellen GLB und bewahrt vorhandene Geometrie, Gelenke und 21 Gesichtsmorphs. Die Nutzerreferenz bleibt eine separate visuelle Vorgabe; exakte Gesichtsidentität ist damit noch nicht bewiesen. Ein bloßes Bild wird nicht als 3D-Modell ausgegeben.

## Arbeitspakete

1. Tatsächliche Vorschau des unveränderten GLB und materialbasierte Ableitung anhand der Vorlage.
2. Vergleich von Geometrie-/Skin-/Morphdaten sowie echter Kotlin-GLB-Validierung und vorhandenen Avatar-JVM-Tests.
3. Nur nach visueller und struktureller Prüfung: Paketasset, Runtime-Hash und NOTICE konsistent aktualisieren.
4. Reproduzierbare lokale Renderbefehle, Quellhashes, Befunde und verbleibende Grenzen nachführen.

## Aktuelle Abnahmegrenze

Erste lokale Baseline-Vorschau aus dem echten GLB erzeugt. Neue Ableitung, Kotlin-Tests und Quellenanschluss laufen noch. Android-Gesamtbuild, Filament-Pixel, Fold7, Sprachausgabe und Installation **NOT_RUN**. Die bekannte unabhängige Python-Rig-Pivot-Prüfung wird separat berichtet; keine Gate-Grenzen werden gelockert.

## Sicherheits- und Rückfallgrenzen

Ein AvatarSlot, ein Director und der bestehende Filament-/Posepfad bleiben maßgeblich. Kugelwahl, Offline-Steuerung, Gehirn/Modellgewichte, Paketkennung, VersionCode und Signatur werden nicht verändert. Keine Actions, kein Main-Merge, keine APK-Veröffentlichung oder Installation. Bei nicht bestandener Kandidatenprüfung bleibt das aktuelle Paketasset unverändert. Ein Rückfall stellt Asset, Hash und NOTICE gemeinsam aus der Basisrevision wieder her.

## Nächster Einstieg

Dieser Handoff wird beim nächsten überprüften Schritt durch konkrete Ergebnisdateien, Befehle und Hashes ergänzt. Parallele aktive #495/#509-Zweige werden nicht überschrieben.
