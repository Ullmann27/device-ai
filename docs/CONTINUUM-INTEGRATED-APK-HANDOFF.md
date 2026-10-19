# Device AI Continuum – integrierter Android-Preview-Stand (19.10.2026)

## Reproduzierbarer Quellstand

Dieser Entwicklungszweig basiert auf dem vollständigen SOL-6.1-Code inklusive
der beiden anschließenden TTS-Reparaturcommits aus
`handoff/sol61-continuum-20261019` (letzte geprüfte Basis:
`54b93d2b58b6b5d62b19e503f310825fa272ebde`).
Sols Android-Layout, Hologramm, Voice-Signale, Besitzerwiderruf,
Webfreigabe, kognitive Pipeline und UI-Instrumentierung werden **nicht**
durch die ältere unabhängige Astra-UI aus PR #35 ersetzt.

Zusätzlich übernommen:

- Sichere, kostenlose öffentliche Web-Metadatenrecherche und eine
  authentifizierte, read-only Recherche-Wissens-API
- Persistenter lokaler Node-Forschungsworker mit begrenzten Zyklen,
  Quellennachweisen und getrenntem, ausdrücklich optionalem CRE1-Shadow-Training
- Reproduzierbare CRE1-Recherchekandidaten (keine ungeprüfte Promotion),
  mathematische Prepooling-/Gram-Tests, schreibgeschützter Android-P-256-Prüfer
- Signatur- und DNA-Integritätsprüfung im Android-Teamleader; kein Write-to-main

## App und Server sind getrennte Laufzeiten

Der **Android-APK-Build** enthält die Kotlin-Android-Funktionen.
Die in `backend/` und `.github/continuum/` geschriebenen lokalen
Worker laufen nicht plötzlich in Android: Sie benötigen einen separaten
eingeschalteten Node-Host und eine bewusst eingerichtete, authentifizierte
Verbindung für den Zugriff auf dessen Ergebnisse.
Ein importiertes LiteRT-LM-Modell auf dem Samsung wird durch diesen
Source-Merge ebenfalls nicht automatisch bereitgestellt.

## APK-Build und Abnahme

Im Checkout mit JDK 17, Android SDK 36, Gradle 8.11.1 und Node 22:

```bash
bash scripts/build-continuum-integrated-preview.sh
```

Die vorhandene Workflow-Datei
`.github/workflows/continuum-integrated-preview.yml` führt dieselben
Qualitätskontrollen aus und erzeugt, nur bei grünem Build:

```text
DeviceAI-Continuum-Integrated-Preview/
  DeviceAI-Continuum-Preview.apk
  SHA256SUMS.txt
  COMMIT.txt
  android-unit-results/
```

Anwendungspaket: `at.heinz.deviceai.preview`. Dieser QA-Build
installiert sich getrennt von einem vorhandenen produktiven Device AI.

Die Workflow-Läufe dieses Branches scheiterten am 19.10.2026 mit
`steps=[]` **bevor ein Build-Befehl ausgeführt wurde**. GitHub hatte
zuvor Runner-/Account-Blockaden. Es existiert deshalb **noch kein**
verifizierter APK-Download **für diesen Integrations-Commit**.
Sols früherer, auf seinem Cloud-Workspace gebauter APK ist davon
zu unterscheiden. Sein Release-Upload war HTTP 401; die neueste
lokale `quickfix-frame`-APK kann nur über seinen Codex-Artefaktlink
oder einen echten Dateiupload in diesen Chat geprüft werden.

Erforderliche Geräteabnahme: Signatur, Package-ID, APK-Hash,
Samsung-Installation, LiteRT-LM-Echtmodell, Voice-/TTS-PCM,
Sicherheitswiderruf, Start-/Chat-Kern bei 150%-Schrift,
Bildschirmausschnitte in Fold-/Portrait-Modus.
Keine ungeprüfte automatische Veröffentlichung auf `main`.
