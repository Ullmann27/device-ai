# M4: echte, unabhängig geprüfte Host-SDK-Artefakte

Code: `c787c0986a2dd944e41cfa08bf4ceba226148a07`, Basis `b842d471183fcf181f392d522a0d0a8f5936a649`.
Status: **HOST_SDK_VERIFIED_NOT_DEVICE_TESTED**. Keine Installation, keine neue Besitzer-APK, keine Gewichtsänderung.

| Projekt | APK | Bytes | SHA-256 |
|---|---|---:|---|
| Notizen | [separate APK](m4-notes/state/Continuum-M4-Notes-196f40c756cd67c8.apk) | 902441 | `196f40c756cd67c85486bb7fd43e25769d86b6911cb7edeb19cb041163e35d22` |
| Zähler | [separate APK](m4-counter/state/Continuum-M4-Counter-da3a998c09fe65a4.apk) | 901641 | `da3a998c09fe65a4d07adc8e68433c4cc44627d07d5f1e5e5fb3a329422be573` |

Pakete `at.heinz.continuum.m4notes` / `at.heinz.continuum.m4counter`, Version 0.1.0/code1, minSdk26/target36.
Beide deklarieren keine Berechtigungen. Frisch erzeugter Android-Debug-Signierer:
`bc6c439abe04c0338fc7219cc90b1b0234750e086539606d7a20aa348af9a78e`.
APK-Signaturschema v2 wurde unabhängig bestätigt. Dies ist weder der Besitzer-Schlüssel noch ein Releaseversprechen.
Der private Debug-Schlüssel ist nicht Bestandteil dieses Bundles. Ein Neubau mit anderem Debug-Schlüssel hat einen anderen APK-Hash.

## Inhalt und Prüfung

`summary.json` und `toolchain.json` enthalten Identität, wirkliche Testzahlen und SHA-256 der verwendeten
offiziellen Toolarchive. Pro Projekt: originale unveränderte `receipt.json`, alle zehn geprüften Quelldateien
plus Quellenmanifest unter `source/`, APK und gehashte Gradle-/JUnit-/Lint-/Signer-/aapt2-Belege unter `state/`.
`independent/` enthält die **separat nach dem Export ausgeführten** Signer-/Paketprüfungen.
`tests/` enthält tatsächliche rote Baselines, grüne Endläufe, die 178 JUnit-XML-Reports und Haupt-App-Lint.
Keine `.gradle`-Caches, privaten Schlüssel oder Besitzerdateien wurden kopiert.

Mit JDK17, Node22+ und nativen SDK-Werkzeugen `apksigner` und `aapt2` im PATH, vom Repository aus:

```sh
node docs/evidence/sol-m4-20261023/verify-bundle.mjs
sha256sum -c docs/evidence/sol-m4-20261023/files.sha256
```

Der Audit rebasiert ausschließlich Transportpfade einer In-Memory-Kopie des originalen Receipts,
prüft mit dem bestehenden CCC1-Profil Quellen/Tests/Lint/Toolbelege/APK-Bytes und führt Signer und aapt2
nochmals auf derselben privaten Prüfkopie aus. Er baut und installiert nichts. Ohne SDK-Tools schlägt er fehl.
Gespeicherte Belege allein sind keine kryptographisch attestierten Laufnachweise; Git-Vertrauen und der
erneute echte Toolaufruf bleiben relevant. `--record` war nur für die erstmalige, exklusive Belegsicherung bestimmt.

## Wiederholung der tatsächlichen Tests

JDK17, Gradle8.11.1, SDK36/build-tools35+36, Kotlin2.4 und die offiziellen Gradle-Abhängigkeiten müssen
installiert bzw. im Cache vorhanden sein. Download-Checksummen stehen in `toolchain.json`.
Die erste Tool-/Dependency-Einrichtung verwendete Internet; die finalen beiden Projektbuilds liefen offline.
In dieser Sitzung war `JAVA_HOME=/workspace/toolchains/jdk-17.0.21+9`, `ANDROID_HOME=/workspace/toolchains/android-sdk`
und `GRADLE_USER_HOME=/workspace/toolchains/gradle-cache`. In einer neuen Umgebung die eigenen Pfade setzen.

```sh
env -u NODE_TEST_CONTEXT node --test --test-reporter=tap --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs
npm --prefix backend ci --ignore-scripts --no-audit --no-fund
env -u NODE_TEST_CONTEXT node --test --test-reporter=tap --test-concurrency=2 backend/test/*.test.js

node .github/continuum/contrastive-parity.mjs prepare /tmp/continuum-swb-parity
node .github/continuum/cre-parity.mjs prepare /tmp/continuum-cre-parity
export CONTINUUM_CONTRASTIVE_PARITY_DIR=/tmp/continuum-swb-parity
export CONTINUUM_CRE1_PARITY_DIR=/tmp/continuum-cre-parity
gradle --offline --no-daemon --max-workers=2 --console=plain -PcontinuumUiWidgetTests=true -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest :app:lintDebug
node .github/continuum/contrastive-parity.mjs verify /tmp/continuum-swb-parity
node .github/continuum/cre-parity.mjs verify /tmp/continuum-cre-parity
```

Ergebnis: **1074/1074 Node**, **60/60 Backend**, **873/873 Android-JUnit**, jeweils 0 Fehler/Skips.
Die Android-Zahl enthält acht Robolectric-Widgettests. Vier zusätzliche `*Checks`-Programme liefen auf dem
echten Gradle-Testclasspath, ohne Android-/LiteRT-Kompilierstubs; Ausgaben in `tests/kotlin-real-sdk-checks.log`.
Die dort bereits vorhandene Residual-Span-DEV-Demo ist kein neuer blinder Sprachlern-Nachweis.
Haupt-App-Lint prüft laut vorhandenem Gradleprofil `NewApi`; das ist kein vollständiges Sicherheitsaudit.
Die separaten Projekt-Lints meldeten je 0 Fehler, 2 Warnungen (`DataExtractionRules`, `MissingApplicationIcon`).

## Neue Projekte reproduzieren

Die CLI nutzt standardmäßig den echten Termux-Homepfad und `termux-arm64`. Auf einem Host muss `--host-sdk`
bewusst gewählt werden. Für einen eigenen, leeren Prüfbereich ohne Änderung von HOME ist auch die bestehende API nutzbar:

```sh
export CONTINUUM_M4_AUDIT_HOME="$(mktemp -d)"
node --input-type=module <<'JS'
import {createOwnedApp,buildOwnedApp,readOwnedAppStatus} from './scripts/continuum-owned-app.mjs';
const home=process.env.CONTINUUM_M4_AUDIT_HOME;
for (const recipeId of ['m4-notes','m4-counter']) {
  console.log(createOwnedApp({home,recipeId}));
  console.log(await buildOwnedApp({home,recipeId,toolchainProfile:'host-sdk'}));
  console.log(readOwnedAppStatus({home,recipeId}));
}
JS
```

Die finalen Wiederholungsläufe verwendeten bereits erzeugte, erneut geprüfte Quellen und `--rerun-tasks`.
Ihre Build-/Prüfzeiten inklusive Doctor betrugen 30019 ms und 27679 ms auf diesem warmen Linux-x64-Host.
Das sind keine Smartphone-Latenzen oder Telefon-RAM-/Akkumessungen. Beide APK-Hashes waren identisch zu
den vorherigen Host-Trial-Builds mit demselben Debug-Schlüssel und Cache; keine plattformübergreifende Bitreproduzierbarkeit behauptet.

## Noch offen

Aktuelle Android-App → Termux → erzeugtes Projekt → echter ARM64-Build → App-Rückmeldung auf dem Fold7,
native Prozesssicht/Abbruch, verlorener Callback, Falten/Rotation, Speicherung/Wiederstart und thermische Last.
Die UI-Tests verwenden Robolectric und simulierte Termux-IPC, kein Telefon.
Die historische Besitzer-Releaseprüfung bleibt unverändert: 14/22 gepinnte Dateien passen, acht bewusst
geänderte Dateien blockieren die alte Attestierung. Vor einer Besitzer-APK müssen neue Dateien/Hashes
geprüft und ausdrücklich freigegeben, Rollback und bestehender Signierdienst erneut verifiziert werden.
