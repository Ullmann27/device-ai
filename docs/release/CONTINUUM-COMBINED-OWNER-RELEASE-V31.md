# Continuum Lokal — gemeinsame nächste APK, nicht wieder nur eine Teilversion
Stand: 31.10.2026 · Branch: `release/continuum-local-complete-v31-20261031`

## Release-Vorrang und Eigentümervorgabe

**Primärauftrag:** Eine echte, funktionierende Android-APK mit sämtlichen zum festgelegten Release-Cutoff bereits integrierten und sicher testbaren Verbesserungen bauen. Keine neue Forschung beginnen, bis dieser Release-Stand aufgebaut und als Gesamtsystem unabhängig geprüft wurde. **Nicht** die ältere M8-Einzelzweig-APK als „gesamte neueste Continuum-Version“ bezeichnen.

Der bisher öffentlich veröffentlichte Code `0.3.29 / versionCode 30` basiert auf `66c1ef88ad149a93776c4fc4b4dbd4b43b7c3ffc` (separater PR #286). Er ist ein **echter älterer Teilrelease**, nicht die Zusammenführung dieser zusätzlichen Forschungszweige. Das nächste kombinierte Ziel lautet `0.3.30 / versionCode 31`, `at.heinz.deviceai.continuumlocal`, **unverändertes Original-Zertifikat** SHA-256 `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23` und derselbe in der installierten App angeheftete Host `continuum-owner-v23-release-preflight.onrender.com`.

## Herkunft und Status auf genau diesem Quellzweig

| Ursprung | Im gemeinsamen Quellzweig | In tatsächlicher APK nutzbar? |
|---|---|---|
| #265 M7/M8 | Altbestand: EWM-v2, LocalModelRuntime, M8 Loop, gVisor/MCP-Adapter, Update-Button | Baseline-Funktionen; End-to-End-M8-Reparatur ungeprüft |
| #280–#283 via #284 | Memory-Kontext-Gate, verifizierte Skill-Review, Compute-Lease-Schranken, Jarvis-Intent-Intake | Android-Code; manche Pfade nur opt-in/PROPOSED |
| #285 | Agent-Fallback und Tool-Evidenz, lokaler M8-Modelladapter | Bestehenden Chatpfad erhalten, neue LLM-Autoreparatur nicht behaupten |
| #287 | 3.000 logische Experten, NeMo Dry-Run, CPU-Reuse, Forschungsdokumentation | **Node-Experimente im Repository**, nicht 3.000 reale Android/Cloud-Rechner |
| #287 | `ContinuumComputeReuseResearch.kt` + neun JUnit-Tests | Kotlin-Adapter in APK-Quellbaum, derzeit unverdrahtet und Default-Deny |
| #267 | Update-Schnellzugriff + CDX1 eingefrorener Legacy-Konditionierer + Test | Direkte Android-Änderung, Gesamt-Gradle-Abnahme ausstehend |
| #262 | JARVIS Mission Control (2.5D, echte Quittungen) + AndroidManifest + Test + Originaltranskripte | Direkte Android-Änderung, echte Gerätedarstellung ausstehend |
| #263 | Sicherheits- und MCP-Faktencheck des zusätzlichen Originalvideos | Forschung/Dokumentation, keine fiktive Cloud-Anbindung |
| #278 | Strikte Manifestfelder-/Schema-/JSON-Typen + zwei JUnit-Tests | Direkte Update-Sicherheitsverbesserung |
| #279 | Unabhängiger HTTPS-Readback und Byte-/SHA-256-Abgleich + Node-Tests | Release-Prüfwerkzeug, nicht im Android-APK |
| #268 (Teil) | `TemporalNeuralShadow` und fünf neue JUnit-Tests zu Kontraktion, thermischem Reset, Widerruf | Android-kompilierbares Forschungsmodul; **nicht** als eigenes LLM oder KI-Antwortquelle aktiviert |
| #286 | Original-P12-Signer, QA und offizielle Manifest-Auslieferung, für **v31** angepasst | Release-Werkzeug; noch nicht auf v31 ausgeführt |

**Ausdrücklich nicht als installierte Funktion ausgeben:** Cloud-GPU-Knoten, reale NeMo-Trainingsdienste, autonomer 24/7-Coding-Agent, tausend gleichzeitig laufende Modelle, selbstverändernde/selbstsignierende App, biologisches Bewusstsein. Sie benötigen reale Dienste, Hardware, Berechtigungen oder unabhängige Abnahmen. Eine signierte APK verleiht solche Fähigkeiten nicht allein durch Kopieren der Forschungsquellen.

## Eine verbindliche Gate-Kette — vor allen weiteren Feature-PRs

1. **Quellumfang:** `node --test scripts/continuum-v31-feature-gate.test.mjs` muss bestätigen, dass die benannten Komponenten im **identischen** ausgecheckten Commit vorhanden sind.
2. **Node:** vorhandene M8-Vertragstests, komplette `tools/continuum-virtual-fabric/*.test.mjs`, JARVIS-Mission-Control- und Update-Readback-Tests, originalsignierte Release-Vertragstests.
3. **Kotlin/Android:** JUnit aller App-Module inklusive neuen Kotlin-Brain-/Temporal-/CDX1-/JSON-Tests, `lintDebug`, Release-Kompilierung und `lintRelease`. QA muss dieselbe kombinierte Quell-SHA verwenden.
4. **APK:** `-PcontinuumLocalStandalone=true -PcontinuumStandaloneVersionCode=31` ausschließlich mit Original-P12 signieren und **per apksigner** korrekten Fingerabdruck, Paketidentität, Code 31, SHA-256 und Dateilänge prüfen. Keine Geheimnisse in GitHub/Chat/Logs.
5. **Publisher:** Den **bereits gepinnten** Render-Dienst erst nach erfolgter Source-Abnahme auf **diesen** freigegebenen Branch/Commit bringen; seinen bestehenden v30-Download bis zur erfolgreichen neuen v31-Veröffentlichung nicht zerstören. Das Render-Tool kann derzeit weder Branch noch Build-Command direkt ändern; ohne kontrollierte Dienstumstellung läuft automatisch weiter der ältere PR-#286-Zweig.
6. **Öffentliches Readback:** `node scripts/continuum-owner-render-readback.mjs` muss die **tatsächlich ausgelieferte** v31 APK-Bytezahl und Hash gegen `latest.json` prüfen. Ein „Render live“ ohne Readback genügt nicht.
7. **Fold7:** In der vorhandenen `Continuum Lokal` App `Updates prüfen und laden` auslösen; installierter VersionCode muss kleiner als 31 sein; Android PackageInstaller mit Besitzerbestätigung, **ohne Deinstallieren, Datenverlust oder Modellgewichts-Reset**. Danach echte Chat-, Memory-, Voice-, Update-, Skill-, Coding-, 2.5D-UI- und Thermiktests.
8. **Erst dann:** Erfolgsmeldung „Neueste APK fertig und funktionierend“. Alle gefundenen Fehler als gezielte Nachfolge-Fixes auf **demselben** Integrationszweig beheben, nicht wieder auf konkurrierenden alten Releasezweigen.

## Wahrheitsstatus am Zeitpunkt des Eincheckens

- Alle aufgeführten Quelländerungen **eingecheckt**, aber der gemeinsame v31-HEAD wurde **noch nicht** in einer vollständigen Original-Node-/Gradle-Suite ausgeführt.
- **Kein** neuer v31-Signer-/Render-Deploy, kein öffentliches `versionCode 31`-Manifest und kein bestätigter Fold7-Updatevorgang.
- Der existierende v30-Download bleibt der letzte öffentlich geprüfte reale Release.
- Fokus bei sämtlichen weiteren Sol/Astra/Opus/ChatGPT-Runden: **Release v31**. Forschung im Hintergrund nicht auf Kosten des APK-Meilensteins weiter verzweigen.

**Reproduzierbare Einstiegsbefehle:**
```bash
git fetch origin
git switch release/continuum-local-complete-v31-20261031
node --test scripts/continuum-v31-feature-gate.test.mjs
node --test tools/continuum-virtual-fabric/*.test.mjs
node --test scripts/continuum-jarvis-mission-control-contract.test.mjs scripts/continuum-owner-render-readback.test.mjs
# Danach: QA-Skript, Full Gradle/JUnit und signierter Release auf gleichem SHA
bash scripts/render-continuum-m8-qa.sh
```
