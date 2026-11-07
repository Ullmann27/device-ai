# Unabhängige Artefaktprüfung: Code 48

**Ergebnis: PASS für unsigned Transportartefakte.**

Quelle `3ff83387af64bf9f99f4440f657f3d27937ca777`, Baum `5206abd91b3740838661aa0e27e6702b3891591b`. Der Build-Receipt weist unveränderte Quelle und erfolgreichen unsigned Build aus. Die Prüfung hat keine Quellen geändert, keinen Gradle-Prozess gestartet und nichts signiert oder installiert.

| Eigenschaft | APK | AAB |
|---|---|---|
| Paket | `at.heinz.deviceai.continuumlocal` | `at.heinz.deviceai.continuumlocal` |
| VersionCode | `48` | `48` |
| VersionName | `0.3.47` | `0.3.47` |
| Debuggable | `False` | `False` |
| minSdk | `26` | `26` |
| targetSdk | `36` | `36` |
| Signatur | UNSIGNED | UNSIGNED |
| ABI | Nur arm64-v8a; 5 ELF64/AArch64-Bibliotheken | Identisch |
| ZIP-CRC | PASS | PASS |
| Doppelte/unsichere Einträge | Keine | Keine |
| ZIP-Ausrichtung | 4 Bytes und 16-KiB-Seiten: PASS | Für AAB nicht anwendbar |
| Quellassets | 26/26 bytegleich | 26/26 bytegleich |
| Zusätzliche Assets | Zwei fest gepinnte öffentliche ONNX-Modelle + Manifest | Identisch |
| Alle Assets | 29 | 29, bytegleich zur APK |

## Dateien

### APK

- Pfad: `/workspace/scratch/3d1967be6193/unsigned-build/app/build/outputs/apk/release/app-release-unsigned.apk`
- Größe: **101,937,943 Bytes** (97.22 MiB)
- SHA256: `7447f2e62871b8fbe0b56345c412665bea9d5abf7f708e4e2f0876dd2b0cf87d`

### AAB

- Pfad: `/workspace/scratch/3d1967be6193/unsigned-build/app/build/outputs/bundle/release/app-release.aab`
- Größe: **71,400,296 Bytes** (68.09 MiB)
- SHA256: `75a7514094a1f83b0176ed8de3c8d2adbfa5a9fb79fd38218ed00f22f90b2751`

## Original-Assets und Modellpins

| Datei | Bytes | SHA256 |
|---|---:|---|
| `avatar3d/continuum-avatar.glb` | 12038416 | `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a` |
| `faceid/face_detection_yunet_2023mar.onnx` | 232589 | `8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4` |
| `faceid/face_recognition_sface_2021dec.onnx` | 38696353 | `0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79` |

Alle ursprünglichen Schrift-, Referenzgrafik-, Projektplan-, Training-Studio-, Forschungs- und Lizenzassets wurden gegen die eingefrorenen Git-Blobs verglichen. Das generierte Modellmanifest stimmt semantisch exakt mit `app/owner-face-models.lock.json` überein. Die beiden ONNX-Dateien sind die dort benannten öffentlichen OpenCV-Zoo-Modelle; keine privaten Modellgewichte wurden gefunden.

## Signatur und Schlüsselprüfung

Apksigner meldet erwartungsgemäß `DOES NOT VERIFY / Missing META-INF/MANIFEST.MF`. Die unabhängige ZIP-Prüfung bestätigt fehlende JAR-Signaturen und fehlenden APK-Signing-Block. Jarsigner meldet für das AAB `jar is unsigned`.

Keine Schlüsselcontainer-Dateien, PEM-Privatschlüsselmarker, unerwarteten Assets oder zusätzlichen Modelldateien gefunden. Die Prüfung untersuchte das vollständige ZIP-Inventar und alle Eintragsinhalte für die angegebenen Marker; sie ist kein Beweis gegen jede denkbare verschlüsselte oder anders kodierte Geheimnisdarstellung.

## Abgrenzung und Belege

- Die Artefakte sind unsigned Transportstände. Eine Owner-Signatur und Installierbarkeit sind damit nicht nachgewiesen.
- Geräteverhalten, Video-Parität, Fold7-Thermik und die gesamte Android-Testabnahme sind nicht Gegenstand dieser Artefaktprüfung.
- Maschinenlesbarer Vollbeleg: `artifact-audit.json`; vollständige Eintrags-Hashes und Tool-Ausgaben liegen daneben.
- Reproduzierbare reine Leseprüfung: `audit.py`.
- APK und AAB enthalten bytegleiche Assets und native Bibliotheken.
