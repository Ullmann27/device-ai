# Continuum Zwischenstand – Originalkopf v2 auf C1-R3-Orb-QA-Basis (05.11.2026)

**Status: SOURCE_STAGED / APK_NOT_BUILT / ANDROID_NOT_RUN.**

## Quellen und exakt abgestimmte Dateien

Träger: #527 `19039e5b822310cd5ab1ce5cc5cb854822a1bcd3` + #539 `766a1953fe1b72a890448e38178448d50140fa40` + #542 `3357c6ac9ad347f93b4aa80690ce8a00df5e2a1c`.
Kopfquelle: #535 `4bbf2863dc3c7debe74ac81f3d16521926c8ae0a` (direkter Nachfolger von #526).
Vorherige C1-Pin des Modells: `70b93bfdfb10c65662f6d37857e4a6a403dfb6ad950a9220acf91cb4145ab145` (12.552.372 Bytes).
Gewünschtes aktuelles Besitzer-Gesicht: `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a` (12.038.416 Bytes).
Git-Blob des verifizierten #535-GLB: `3498c003a8bf186a8abb47048e2d36be4810b88c`. Alle fünf Dateien sind bytegleich aus genau diesem Quellstand übernommen.

**Untrennbar übertragene Fünfergruppe:**

- `app/src/main/assets/avatar3d/continuum-avatar.glb` — tatsächliches Bild-/Rig-Asset.
- `app/src/main/assets/avatar3d/NOTICE.json` — Provenienz und SHA-256/Bytezahl.
- `app/src/main/java/at/heinz/deviceai/avatar3d/AvatarGlbContract.kt` — Laufzeit-Pin, ansonsten identische 17.493-Byte-Quelle mit einziger geänderter PIN-Zeile.
- `app/src/main/java/at/heinz/deviceai/avatar3d/AvatarMorphMapper.kt` — Berücksichtigung des Kameraazimuts des Dreiviertelprofils, Augenbewegung/Mimik.
- `app/src/main/java/at/heinz/deviceai/avatar3d/FilamentAvatarRenderer.kt` — korrektes asymmetrisches 3D-Framing, optisches Ausblenden der Zähne ohne Veränderung des GLB, bestehende Render-Pipeline weiterverwenden.

## Fachliche Einschränkungen

`AvatarAssetProvenanceTest` im #527-C1-R3-Baum prüft bereits originale GLB-Bytes gegen Laufzeitpin und NOTICE. Er bleibt unverändert. Keine zweite 3D-Runtime, keine Änderung an Benutzerpaket, Signiermaterial, Besitzerdateien, Offline-Gate, `main` oder dem Original-Downloadkanal.

**Jetzt nicht behaupten:** C1-/R3-Androidtests mit dem älteren Modell beweisen die neue asset-spezifische Kombination nicht. Vor APK-Release müssen reale Gradle-`testDebugUnitTest`, Filament-Version/API, Lint, saubere Asset-Provenienz, APK-Bau, Signatur und Paketversorgungs-Readback des *exakten* integrierten Heads passieren. Native Fold7-/Audio-/Blickabnahme ist zusätzlich separat, wenn Hardware erreichbar.

**Rücknahme:** diesen isolierten neuen Branch/PR aus der C1-Integration auslassen. Historische PRs #527/#535/#539/#542 bleiben bytegleich.
