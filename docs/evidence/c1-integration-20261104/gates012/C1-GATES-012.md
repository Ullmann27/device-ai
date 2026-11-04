# C1-GATES-012 — zentrale App-Gates und separate Werkzeug-Abnahme

**Read-only-Quellaudit. Keine Tests/Builds ausgeführt, keine Repositoryänderung, keine alten PASS übernommen.**

Geprüfter Head: `165fc9f58ae9e36295690df60145174fd5dc58e7`. App-Tree: `b2b0cd21327ac1d5c8dd1f0cb35e94e2dcfbee1e`. Scripts-Tree: `ca3460d5dab6c632ceeb84742cd04f80190a07f4`.

Neuere C1-Router-/Activity-Testläufe und der anschließend laufende Lint gehören nicht zu diesem Audit und werden hier nicht als Ergebnis geführt.

## Abgrenzung des Node-Umfangs

Der kanonische `full-node`-Runner wählt nur direkte `*.test.mjs` aus `.github/continuum` (228 Dateien) und `scripts` (96 Dateien), zusammen 324 Dateien. Er durchsucht `tools/` nicht rekursiv. Isolationstests in den beiden kanonischen Wurzeln bleiben enthalten.

Zusätzliche `tools/**`-Reviewtests gehören zu ihrer jeweiligen Entwicklungswerkzeug-/Forschungsabnahme, sofern kein konkreter Pflichtvertrag sie zusätzlich einbindet. Die von C1 gemeldeten 38 FAIL des fremden Zusatzlaufs bleiben FAIL; sie wurden in diesem Audit nicht erneut ausgeführt oder umgedeutet. Ein zentrales App-PASS würde fehlende Hostisolation oder nicht belegte Coding-/Modellfähigkeiten nicht reparieren. Kein unsicherer Ersatz und keine nachträgliche FAIL→SKIP-Umdeutung.

AGENTS verlangt reale lokale oder Render-Gates am exakten Stand, verbietet Actions-Abnahme und vererbte Eltern-PASS. Der Experimentvertrag fordert nicht die vollständige Umsetzung jedes späteren Experiments pro PR; bestehende Sicherheitsgrenzen bleiben verbindlich. Frühere Frontier-/M8-/v37-Launcher haben gezielte `tools/continuum-virtual-fabric/*.test.mjs`-Aufrufe, keine allgemeine rekursive tools-Pflicht.

## Zwingende zentrale Node-Gates

Vorhandene Befehle; `qa_proofs` ist im Launcher ein neues temporäres Verzeichnis außerhalb des Checkouts:

```bash
node --test scripts/continuum-atomic-acceptance.test.mjs scripts/continuum-gguf-preflight.test.mjs scripts/continuum-gguf-preflight.integration.test.mjs scripts/continuum-owner-v38-release.test.mjs scripts/continuum-owner-continuous-release.test.mjs
node scripts/continuum-reliability-qa.mjs critical --out "$qa_proofs/critical"
node scripts/continuum-reliability-qa.mjs full-node --out "$qa_proofs/full-node"
```

Critical enthält `process`, `storage-agent`, `android-source-contracts`, `host-checker`. Die Stufen überlappen; Zahlen nicht addieren. Receipts müssen exakte SHA/Profil, Frische, unveränderte Quelle, bestätigtes Prozessende, tatsächlich ausgeführte Tests und vollständige fehlerfreie JUnit-Belege nachweisen. `continuum-qa-evidence.mjs node RECEIPT SHA PROFILE START` validiert dies.

## Vollständige Android-/UI-Suite

```bash
gradle --no-daemon --rerun-tasks --max-workers=2 --console=plain -Dorg.gradle.jvmargs='-Xmx1600m -XX:MaxMetaspaceSize=512m' -Pkotlin.compiler.execution.strategy=in-process -PdeviceAiMentorPreview=true -PcontinuumUiWidgetTests=true -PcontinuumLocalStandaloneQa=true -PcontinuumStandaloneVersionCode="$CONTINUUM_RELEASE_VERSION_CODE" :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug
```

Kein Klassenfilter. `continuumUiWidgetTests=true` nimmt `app/src/uiTest/java` auf, aktiviert Android-Ressourcen und Offline-Robolectric. `continuum-qa-evidence.mjs android ROOT START` verlangt frische reguläre JUnit-Dateien, vollständige tatsächliche Ausführung und null Fehler/Errors/Platzhalter.

### Elf erforderliche Suites

Jeweils mindestens ein ausgeführter Test und **null Skips**:

```text
at.heinz.deviceai.ToolExecutionStartLatchTest
at.heinz.deviceai.ReadOnlyToolSizeRegressionTest
at.heinz.deviceai.cognitive.LocalPromptFrameTest
at.heinz.deviceai.ToolDurableOutcomeTest
at.heinz.deviceai.localmodel.LocalToolContinuationTest
at.heinz.deviceai.cognitive.ModelNeutralBrainIntegrationTest
at.heinz.deviceai.cognitive.ReadOnlyMultiStepTest
at.heinz.deviceai.learning.interactive.ResearchFeedbackMultiprocessTest
at.heinz.deviceai.cognitive.UnknownOutcomeSafetyTest
at.heinz.deviceai.localmodel.LocalInferenceDeliveryTest
at.heinz.deviceai.cognitive.JournalAvailabilityTest
```

### Fünf zusätzliche Mindestzahlen

| Klasse | Mindestfälle |
|---|---:|
| `at.heinz.deviceai.ShizukuBridgeLifecycleTest` | 14 |
| `at.heinz.deviceai.ShizukuConnectionDialogTest` | 2 |
| `at.heinz.deviceai.ui.ContinuumControlsTest` | 7 |
| `at.heinz.deviceai.cognitive.OwnerChatIntentRouterTest` | 5 |
| `at.heinz.deviceai.cognitive.HybridMemoryRetrieverTest` | 6 |

Auch diese fünf Klassen dürfen keine Skips enthalten. Der Zusatzvalidator zählt Fehler über alle Testfälle. Die 11+5 Kontrollen ersetzen die volle Suite nicht. Der vorhandene allgemeine Validator erlaubt ausgewiesene Skips außerhalb der Pflichtklassen; daraus folgt kein Fähigkeits-PASS.

## Lint

`checkOnly = NewApi`, `abortOnError = true`, `checkReleaseBuilds = false`. Zulässige Aussage: konfigurierter NewApi-Lint; kein vollständiger Android-Lint aller Regeln.

## Unsigned-APK-Gate

```bash
gradle --no-daemon --max-workers=2 --console=plain -Dorg.gradle.jvmargs='-Xmx1600m -XX:MaxMetaspaceSize=512m' -Pkotlin.compiler.execution.strategy=in-process -PdeviceAiMentorPreview=true -PcontinuumLocalRenderUnsigned=true -PcontinuumStandaloneVersionCode="$CONTINUUM_RELEASE_VERSION_CODE" :app:assembleRelease :app:bundleRelease
```

- Expliziter kanonischer QA-Code 39–9999; Name `0.3.(Code−1)`.
- `app-release-unsigned.apk` muss regulär, vorhanden und kein Symlink sein; tatsächlicher SHA256 und Größe über 1 MB.
- `aapt2 dump badging`: `at.heinz.deviceai.continuumlocal` mit exakt erwartetem Code/Name.
- `apksigner verify` darf keinen signierten QA-Artefaktzustand bestätigen; keinerlei Signierdatei/Passwort/Base64-Material im QA-Modus.
- Exakter Head und getrackte Quellen unverändert; sämtliche Gates müssen für den abschließenden QA-PASS grün sein.
- Der vorhandene Launcher sammelt Assembly-Befunde auch nach früheren Gatefehlern, verweigert dann aber den Gesamt-PASS.
- Erfolgsstatus höchstens `UNSIGNED_BUILD_VERIFIED_DEVICE_NOT_RUN`, mit `ownerSigned=false`, `apkPublished=false`, `deviceVerified=false`.

## Umgebung und spätere Freigaben

Der Render-Wrapper ist an reale Dienste, freigegebene SHA/Host und `integration/continuum-all-systems-apk-qa-20261102` gebunden; keine vorgetäuschten Render-Variablen/Branchidentitäten für C1. AGENTS erlaubt echte lokale Äquivalenz der Gate-Scopes und Belege. Script-Pins: Temurin 17.0.20.1+1, Gradle 8.11.1, SDK36, Build Tools35.0.0.

Originalsignaturkontinuität, frischer Geräte-/Kanalcode, bestehender Updatehost/Downloadhash, autorisierter Updateversuch mit Datenerhalt sowie physischer Fold7 mit Inferenz, Gedächtnis, Avatar, Audio und Worker-Lifecycle bleiben gesonderte Gates.

## Gelesene belegrelevante Quellen und exakte Hashes

Die Hashes binden die bereits konsultierten Pfade an den geprüften Commit. Bei ausdrücklich markierten Auszügen wird keine Vollprüfung der gesamten Datei behauptet.

| Quelle und Lesescope | SHA256 | Git-Blob |
|---|---|---|
| [AGENTS.md](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/AGENTS.md) — Full governing instruction file | `deec8d8638ee7837ab6a686644312e072a4421a22173803bf2b95e7048fdf534` | `511396c8e88ef0b9811b791d6c8bccc10f868b26` |
| [scripts/render-continuum-owner-continuous-qa.sh](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/scripts/render-continuum-owner-continuous-qa.sh) — Full central QA launcher | `62f6a7cf0168390d98a7b28c7916bfe96bff7284cf755654daf1b07add850f43` | `9ea8eae2011504b5283a0cf4e62231f0aaa7c39b` |
| [scripts/continuum-qa-evidence.mjs](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/scripts/continuum-qa-evidence.mjs) — Full Node/JUnit evidence validator | `787804965d23df864b0a19baeb2c52c50e06b8f22e584383aaa9a7ff6815128f` | `81227a950b1162e37bc267d95767b6e6a75dbc78` |
| [scripts/continuum-reliability-qa.mjs](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/scripts/continuum-reliability-qa.mjs) — Full critical/full-node selection and source-identity runner | `ed57537bd748481088372e76291afee541c1b52f47261f1f9efd71218404ceca` | `67b0f9ccb2b9e2777e5aa304934572d268d7bd48` |
| [app/build.gradle.kts](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/app/build.gradle.kts) — UI source-set, offline Robolectric and configured lint excerpts | `d375fabbc1633da9c440039e03fed28e3e2e38a14beed0743e64d9dc57f9588c` | `34def8f8ee30401b635945909bd54c223cb61599` |
| [docs/CONTINUUM-EXPERIMENTVERTRAG.md](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/docs/CONTINUUM-EXPERIMENTVERTRAG.md) — Governing experiment, isolation and mobile acceptance clauses | `0513a4b48cd6e38d3da55225ebdcc805f54efa2003519a112fb0106a530586d3` | `1e10c49c019109a3ea1c790490328ecaf2fb6348` |
| [docs/research/omega/ASTRA-NUCLEUS-HANDOFF-20261104.md](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/docs/research/omega/ASTRA-NUCLEUS-HANDOFF-20261104.md) — Current C1 scope, resource/release boundaries and APK acceptance contract | `85f12b4fe0d42b6a46b04832ab1abf4b009522ed494860ba6d6b8d14f86fde58` | `d0e8a058fa2cbb07aba6bf12d473277378a9c226` |
| [docs/research/omega/GRAND-CHALLENGE-HANDOFF-20261102.md](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/docs/research/omega/GRAND-CHALLENGE-HANDOFF-20261102.md) — Matched combined QA and capability-boundary clauses | `07772e2aed6923d12913d32f1b34ac8b68b97d6c4fdec049de4234f37f4d4817` | `ce58fd747df880c19946339c472dbaa96e4d5b0f` |
| [scripts/continuum-frontier-v30-qa.sh](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/scripts/continuum-frontier-v30-qa.sh) — Only matched explicit tools/continuum-virtual-fabric test invocation | `3c603cb39b64c0c5dd57ffb53de7084192d1e956682b63ff62ee8b5c788252e4` | `c385fc3f92a3bdecc3d77874df3ae23288ae2b2e` |
| [scripts/render-continuum-v37-qa.sh](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/scripts/render-continuum-v37-qa.sh) — Only matched explicit tools/continuum-virtual-fabric test invocation | `3d9efd5fb21a57809c4dd3997f29f59f9fd141ce3a317a3b005624c7d1eeb6fa` | `f9cbb3676abb072ddab0544fa6a4d57ceda787eb` |
| [scripts/render-continuum-m8-qa.sh](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/scripts/render-continuum-m8-qa.sh) — Only matched explicit tools/continuum-virtual-fabric test invocation | `0c6a9256ef8c289faecad19dfb9fc0f01e7b5094424b2ac44a0e5488b10fa732` | `68beca3396c81f03989844008cdcf76d34e23adf` |
| [scripts/continuum-local-model-observation.test.mjs](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/scripts/continuum-local-model-observation.test.mjs) — Matched real-isolation capability checks and existing host-only test guards | `6700017faf9c9beff53ca159e5806d2692445790a48157707542a29829569736` | `1e94dce9d810293947f20d10309373680299a9a6` |

Gesichert: 2026-11-04T20:47:42.324491+00:00. Nur die eigenen Belegdateien wurden geschrieben.
