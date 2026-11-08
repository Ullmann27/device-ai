# Sonnet 5.5 — Code 50 Deck-Footprint-Integration · Handoff 08.11.2026

**STATUS: PARTIAL (Stand nach Merge `8bc695f`).** #634 ist in die QA-Linie #630 gemergt. Node/Backend sind auf dem exakten Merge-SHA geprüft (Abschnitt 10). Das Android-Gate (Kotlin, JUnit/Robolectric, Lint, unsignierter QA-Build) lief **nur diagnostisch** im vorhandenen GitHub-Workflow grün (Run 42478725998: 4032 Tests, 0 Fehler, 20 Skips) — nach Repo-Regel ist das **keine** Abnahme. Offizielle Abnahme auf Render-QA-Host oder lokaler Toolchain, Release-Build, Originalsignatur, Signaturvergleich, Fold7: **`NOT_RUN` / `UNAVAILABLE`**. APK 50: **`NOT_SIGNED / NOT_RELEASED`**; es existiert nur ein unsignierter, nicht installierbarer QA-Build. Kein signierter SHA-256, kein Download-Link.

Ziel bleibt `at.heinz.deviceai.continuumlocal` · `0.3.49` · versionCode `50` · Originalsignatur · Samsung Galaxy Z Fold7. Dieser Stand ändert **keinen** Produktionscode, keine Sicherheitsprüfung, keine Release-Konfiguration.

## 1. Ausgangs-SHAs (per `git ls-remote` unmittelbar vor dem Push geprüft)

| Linie | Branch | SHA |
|---|---|---|
| #603 Code-50-Integration | `codex/continuum-code50-integration-20261107` | `4971dce963daee37df2aceb9e7690392eec75cef` |
| #630 QA-Staging | `chatgpt/code50-qa-staging-20261108` | `d8353c20717217f69aee3ea8353849f22df2892e` (Integration + 60 Commits, 0 zurück) → nach Merge von #634: `8bc695fd0d0fcc10ef0aa88684da8b57acc928f4` |
| #634 Deck-Footprint-Tests | `chatgpt/code50-deck-footprint-tests-20261108` | `1f2276b141133aaf5ae9a54c1b51eab7c31445f8` → `9256f63ab211356ff4ced2a9d8667153f316e1d7` → `c98ce02eb6447235c302e22f91084dfa329c4a2c` (Doku/Evidenz; von GitHub am 08.11. 12:07 UTC automatisch als MERGED markiert) |

## 2. Änderungen dieses Zweigs (nur Tests und Doku)

- `1f2276b` — vier Robolectric-UI-Testdateien an den tatsächlichen `WorkspaceFootprint` angepasst: `JarvisHomeTestRoutes.kt` (Helfer `footprintOfCentre`, `clearedBy`, `keptBy`, `clears`), `ContinuumDocumentSceneUiTest.kt`, `ContinuumReferenceCockpitUiTest.kt`, `ContinuumSystemStatusWorkWindowUiTest.kt`. Behebt die zehn Fehler aus Actions-Run 42473004670 (Ursache: `d8353c2` behält bei `MEDIUM_TERMINAL` die vier Missionsreihen, ältere Tests erwarteten alle 14 Deck-Elemente geleert).
- `9256f63` — `JarvisActionScenesTest.workspaceFootprintsMatchAirbusDeckIntent` pinnt die Zuordnung unabhängig: Lernzentrum = `FULL_MISSION_BAY`; Systemstatus, Gedächtniszentrum, Web und Quellen = `MEDIUM_TERMINAL`. Grund: die UI-Tests leiten die Erwartung aus `JarvisActionScenes` ab, eine Fehlzuordnung wäre sonst unbemerkt grün geblieben (Fund der unabhängigen Review). **WIP / UNVERIFIED beim Commit**; inzwischen im GitHub-Diagnoselauf auf `8bc695f` kompiliert und `JarvisActionScenesTest` 3/3 PASS (keine offizielle Abnahme).
- diese Datei und `evidence/code50-node-sonnet55-20261108.json`.

## 3. Prüfung #634 gegen Produktionscode (statisch + unabhängige Review)

| Footprint | Auslöser (`JarvisActionScenes.footprintFor`) | Deck (14 Views) |
|---|---|---|
| `CONVERSATION_IDLE` | kein Arbeitsfenster | alle 14 sichtbar |
| `MEDIUM_TERMINAL` | STATUS, MEMORY, WEB, APP, FILES, SCREEN, GENERIC | 10 Featurekarten `jarvis-tile:*` weg; 4 Missionsreihen `jarvis-panel:ROW_*` bleiben sichtbar, klickbar, im A11y-Baum |
| `FULL_MISSION_BAY` | PLANNING, LEARNING, DEVELOPMENT | alle 14 weg |

- `ContinuumHomeView.deckHides` setzt `View.INVISIBLE`: kein Hit-Target, kein A11y-Knoten. Der Testhelfer `clears()` entspricht dieser Regel exakt.
- Alle an `assertCentreSurface` übergebenen Titel sind Schlüssel von `JarvisActionScenes.centres`; der Dialog-Fallback kehrt vor den Footprint-Assertions zurück.
- Der Touch-Probe in `openWindowClearsTheDeckAndItReturnsAfterClosing` nutzt nur Ecken geleerter Kacheln, außerhalb des Fensterpanels. Die A11y-Zählung `14 → 4 → 14` folgt `kept().size`.
- Alles nach den früher fehlschlagenden Assertions läuft nach Schließen/Konfigurationswechsel im Idle-Zustand.
- Unabhängige Review (`code-review`-Agent, nur Lesezugriff): **kein Defekt**, eine Low-Lücke (siehe `9256f63`).
- Test D Iterationen 2–3 (760×884, 412×915) und die 1,5×/2,0×-Schriftschleifen liefen seit der Produktionsänderung nie — bis zum GitHub-Diagnoselauf auf `8bc695f`: `ContinuumSystemStatusWorkWindowUiTest` 10/10, `ContinuumReferenceCockpitUiTest` 13/13, `ContinuumDocumentSceneUiTest` 12/12 PASS, keine Skips. Das ist ein Robolectric-Layoutbeweis, **kein** Fold7-Gerätebeweis, und keine offizielle Abnahme.

## 4. Tatsächlich ausgeführte Tests (Host-Sandbox, exakter SHA `1f2276b`; auf dem Merge-SHA `8bc695f` wiederholt, siehe Abschnitt 10)

| Gate | Umgebung | Ergebnis |
|---|---|---|
| Node-Vertragsgate (`.github/continuum/*.test.mjs`, 4 Dateien) | Node 26.12.1, root | 11/11 PASS |
| `scripts/continuum-reliability-qa.mjs critical` | Node 24.22.2, Nicht-root | **PASS 135/135** (process 37, storage-agent 32, android-source-contracts 64, host-checker 2), `sourceUnchanged=true` |
| Backend `npm ci --ignore-scripts`, `check`, `test` | Node 26.12.1, root | 64/64 PASS |
| `full-node` unverändert | Node 26.12.1, root | FAIL 13/2430 (**umgebungsbedingt**, s. u.) |
| `full-node`, CI-Parität | Node 24.22.2, Nicht-root, nur `core.fsmonitor`-Override entfernt | **PASS 2393/2393 ausgeführt, 0 FAIL, 37 SKIP** von 2430 |

**Ursache der 13 Fehler (reproduziert):** 3× `UNSUPPORTED_NODE_LTS` (Node 26 statt LTS), 9× `ISOLATION_UNPRIVILEGED_UID_REQUIRED` (root), 1× `preflight rejects fsmonitor hooks and partial clones` (`Missing expected exception`, `branch-discovery.test.mjs:178`).

**Git-Overlay (1 Fehler):** Die Sandbox setzt `GIT_CONFIG_COUNT=3` mit `safe.bareRepository`, `credential.interactive` und `core.fsmonitor=false`. Git wertet diese Umgebungsvariablen als Scope „command line“ mit höchster Priorität. `git config --get core.fsmonitor` liefert daher `false`, obwohl die Fixture-Repo-Konfiguration `/nonexistent/hook` enthält (`git config --show-origin --get-all` zeigt beide, die „command line“ gewinnt). Die Produktionsprüfung `safeGitConfiguration` liest bewusst die effektive Konfiguration und sieht deshalb korrekt „kein Hook“. Mit entferntem Override (nur dieser eine Eintrag, die zwei anderen bleiben, ausschließlich im Kindprozess des Tests) besteht derselbe Test (`pass 1 / fail 0`), unverändert `fail 1` ohne Entfernung. CI-Runner haben keine solche Überlagerung. **Kein Test, keine Sicherheitsprüfung und keine Produktionsdatei wurde dafür verändert.**

**37 Skips (alle Opt-in/Host-Beweise, keine stillen Auslassungen):** 14× `HOST_ISOLATION_PROOF_NOT_RUN` (`/usr/bin/bwrap` fehlt), 12× opt-in `CONTINUUM_WORKSPACE_HOST_PROOF!=='1'` (`ccc1-bot-workspace-host.test.mjs`), 3× `HOST_RESOURCE_PROOF_NOT_RUN` (kein privater cgroup-Root), 2× Docker-Host-Beweis, 6× `NOT_RUN` (offizielle Kotlin/Shizuku-/JUnit-Jars, `CONTINUUM_FABRIC_JVM_DEPS`, JVM→Node, Harness-SDK, SDK).

Reproduzieren: exakter Klon, Node 24 LTS, Nicht-root: `node scripts/continuum-reliability-qa.mjs critical --out <dir>` und `... full-node --out <dir>`; Backend `npm --prefix backend ci --ignore-scripts --no-audit --no-fund && npm --prefix backend run check && npm --prefix backend test`.

## 5. Nicht ausgeführt (`NOT_RUN` / `UNAVAILABLE`) und Blocker

- **Android-Toolchain:** `dl.google.com` ist aus der Sandbox nicht erreichbar, `maven.google.com`-Artefaktpfade leiten dorthin um. AGP, androidx und Android-SDK sind nicht beziehbar. Kein SDK, kein Render-Zugang (`RENDER_*` leer), Standard-JDK 27. Keine Umgehung (keine Fremd-Mirror, keine Firewall-Manipulation).
- In der **Sandbox nie ausgeführt**: Kotlin-Compile, JUnit, Robolectric, Lint, Release-Build, APK-Identität, Signatur, Fold7. Diagnostisch (keine Abnahme) lief das Gradle-Gate im GitHub-Workflow auf `8bc695f` grün (Abschnitt 10); auf einem freigegebenen Host steht es weiterhin aus.
- **Nur Diagnose, keine Abnahme:** GitHub-Actions-Run 42473004670 auf `d8353c2`: `:app:testDebugUnitTest` 4032 Tests, 10 fehlgeschlagen (genau die von #634 reparierten), 20 übersprungen; Lint- und APK-Schritte wurden nie erreicht. Ein Push auf `chatgpt/code50-qa-staging-20261108` startet den vorhandenen Workflow `continuum-code50-github-qa.yml` erneut; dessen Ergebnis ist nach Repo-Regel nur Diagnose. Folgelauf auf `8bc695f`: Run 42478725998, beide Jobs `success` (Abschnitt 10).
- **Historisch, nicht übertragbar:** Astra-Nachweis auf `5ba76af` (3488 Tests, 3469 PASS, 19 SKIP, Lint 0 Findings; 35 UI-Tests PASS).

## 6. Integrationsmatrix (34 neue `app/src/main`-Kotlin-Dateien gegenüber `codex/continuum-code49-orb-render-performance-20261107`)

Methode: Klassennamen aus der GitHub-Compare-API gegen Referenzen in `app/src/main` (ohne eigene Datei) und in Tests abgeglichen. Statische Referenz, **kein** Laufzeitbeweis.

| Klasse | Dateien | Befund |
|---|---|---|
| **Angeschlossen** | `Airbus2100CockpitOrchestrator`, `HollywoodFuiConstruct`, `HollywoodFuiLiveData`, `HollywoodFuiStageOverlay`, `JarvisLiveTabs`, `JarvisComputePhasePolicy`, `AvatarEssentialMotionPolicy`, `AvatarLiveDiagnostics`, `CriticalDampedSpring`, `ProactiveSideQuestAndCountdownEngine`, `LocalReasoningWorkScenePolicy`, `LocalConversationSituationTracker`, `ConversationSituationModel`, `Ctx3StateTelegram`, `ContinuumGitHubDownloadRedirectPolicy`, `ContinuumGitHubSignedUpdatePolicy`, `ContinuumGitHubReleaseCatalog`, `ContinuumOwnerReleaseSelector` (18) | von Produktionscode aufgerufen |
| **Schatten, nur beobachtend** | `StateTelegramAnswerShadow`, `AnswerClaimVerifier`, `StreamingSpeechShadowTrace`, `ApprovedStreamingSpeechAssembler` (4) | `AgentActivity` protokolliert nur (`VERIFY SHADOW …`, `SPEECH SHADOW … noch NICHT gesprochen`); keine Entscheidung, keine Sprachausgabe |
| **Implementiert, nicht produktiv verdrahtet** | `AdaptiveReasoningStrategyMemory`, `CausalEpistemicLedger`, `CounterfactualWorldOverlay`, `EntrenchmentAwareRetentionPolicy`, `EpistemicArbitrator`, `ExperiencePatternMaturity`, `HypothesisWorkspace`, `KnowledgeGapDetector`, `LightweightJtmsEngine`, `ProvenancePreservingConsolidation`, `EvidenceBackedRelationGraph`, `EntityRoleResolutionEngine` (12) | nur Tests, bzw. nur untereinander referenziert. **Nicht als aktiv melden.** Nicht angeschlossen, weil keine Freigabe und keine Evidenz für Produktivbetrieb vorliegt |
| Angeschlossen, **nicht direkt getestet** | `HollywoodFuiStageOverlay` | keine Testreferenz (Konstrukt und Livedaten haben Tests) |

Weitere geprüfte Verbindungen (statisch gelesen, Tests vorhanden, hier nicht ausgeführt):

- **Sprache:** `AndroidOnDeviceSpeechInput` hält einen warmen On-Device-`SpeechRecognizer` zwischen Runden (`destroy()` beendet nur die Lease). `ERROR_CLIENT` und `ERROR_RECOGNIZER_BUSY` erhalten genau einen 250-ms-Warm-Retry, danach wird der Recognizer verworfen und `BUSY` gemeldet. Satzende nach 3 s Stille. Tests: `AndroidSpeechFailureTest`, `LocalSpeechSessionTest`, `AndroidOnDeviceSpeechRetryUiTest`. **ElevenLabs ist nicht verdrahtet** (keine Referenz in `app/src/main`), keine autorisierte Integration oder Stimmberechtigung nachgewiesen, daher bleibt es so.
- **App-/Werkzeugsteuerung:** `OwnerAppLookupIntent`/`OwnerAppActionFlow`: „Öffne WhatsApp“ = exakter Lookup + ToolGate-Bestätigung; „Ich brauche WhatsApp“ nur bei exaktem installiertem Treffer, sonst Gespräch; „Schreibe WhatsApp“ = Lookup + Rückfrage, kein Versand; „Öffne diese PDF“ gehört nicht in diesen Pfad. Tests: `OwnerAppLookupIntentTest`, `OwnerAppActionFlowTest`, `OwnerAppLookupNaturalRegressionTest`.
- **Performance/Wärme:** `JarvisRenderQuality` (Wärmestufen) → `JarvisComputePhasePolicy` (reduziert nur Last) → `AvatarEssentialMotionPolicy` (Avatar-Schleife bleibt bei LOW, dekorative Animation stoppt); verdrahtet in `ContinuumHologramView` (~826–899). **Keine Messung, keine Leistungsbehauptung.**

## 7. Abstammung (GitHub-Compare-API)

- Gemergte PRs #604, #606–#611, #613, #615–#627: Merge-Commits und PR-Heads sind Vorfahren von oder identisch mit `4971dce` (der Merge-Commit von #627 ist `4971dce`).
- #628 (`b445b509e`) und #633: vollständig in #630 enthalten. #631/#632 in #630 gemergt. #597/#599: in #630 enthalten.
- **Nicht übernommen, divergent:** #629 (`becebaeab`, 1 reine QA-Datei +60), #605, #612, #614 (vermutlich durch #611/#618 überholt, **nicht belegt**), #596, #598, #600, #601, #602 (Code-49-Forschungs-/Schattenmodule). Kein blindes Zusammenführen.

## 8. Release-Gate (laut vorhandenen Übergaben, ohne Render-Zugang nicht live geprüft)

- Build-Konfiguration (`app/build.gradle.kts`): `-PcontinuumStandaloneVersionCode=50` ergibt versionCode 50 und versionName `0.3.49`, applicationId `at.heinz.deviceai.continuumlocal`.
- Das Veröffentlichungsskript `scripts/render-continuum-owner-continuous-release.sh` signiert nur mit **beiden** Variablen `CONTINUUM_RELEASE_FREEZE_LIFTED_BY_OWNER` und `CONTINUUM_RELEASE_APPROVED_SHA` (identischer Commit) sowie dem echten Original-P12. Erwarteter Signer-SHA-256: `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`.
- Render-Dienste: Publisher `srv-dbf1tr2vcj2c73ehpfog` (Zweig `research/continuum-m8-render-signed-release-20261031`, AutoDeploy aus, letzter Deploy `pipeline_minutes_exhausted`); QA `srv-dbnual1srm7s73afh69g` (alter Recall-Zweig, `pipeline_minutes_exhausted`). Letzte signierte Version: Code 48 / 0.3.47.
- **Fehlt für APK 50:** (1) autorisierte Android-Buildumgebung mit Gradle/SDK, (2) Besitzerfreigaben `CONTINUUM_RELEASE_FREEZE_LIFTED_BY_OWNER` und `CONTINUUM_RELEASE_APPROVED_SHA`, (3) Original-P12-Publisher-Host, (4) Prüfung der installierten Version auf dem Fold7 (zuletzt gemeldet 0.3.28/Code 29).

## 9. Rückfallplan und nächster Einstieg

- **Rollback:** reiner Test-/Doku-Zweig. `git revert -m 1 8bc695fd0d0fcc10ef0aa88684da8b57acc928f4` auf `chatgpt/code50-qa-staging-20261108` stellt `d8353c2` inhaltlich wieder her. Kein Force-Push, kein Main-Merge, kein Release.
- **Nächste Schritte in Reihenfolge:** (a) ✔ #634 per Merge-Commit `8bc695f` nach #630 gemergt; (b) ✔ Node/Backend auf `8bc695f` wiederholt (Abschnitt 10); (c) **offene** offizielle Android-Abnahme auf freigegebenem Render-QA-Host oder lokaler SDK-Toolchain **auf demselben SHA** (`:app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` mit `-PdeviceAiMentorPreview=true -PcontinuumUiWidgetTests=true -PcontinuumLocalStandaloneQa=true -PcontinuumStandaloneVersionCode=50`), möglichst mit JDK 21, damit die zwei übersprungenen `LocalModelReloadLifecycleTest`-Fälle laufen; (d) erst danach Release-Build, Signierung über den geschützten Host, SHA-256 und Fold7-Prüfungen.
- **Fold7 bleibt `REAL_DEVICE_NOT_RUN`:** 10 freihändige Sprachrunden, 15-s-Avatar-Live-Beweis, FUI/Wärme/CPU, echte PDF-Szene, 200 % Schrift, Falten/Rotation, signiertes In-place-Update mit Signaturgleichheit.

## 10. Exakter Merge-SHA `8bc695f` — Ergebnisse (08.11.2026)

`8bc695fd0d0fcc10ef0aa88684da8b57acc928f4` ist ein `--no-ff`-Merge von #634 (`c98ce02`) in den Staging-Stand `d8353c2`. Diff gegenüber `d8353c2`: 7 Dateien (+185/−9); `app/src/main` sowie alle `.github`-, `scripts`- und `backend`-Dateien unverändert. Maschinenlesbar: `evidence/code50-merge-8bc695f-sonnet55-20261108.json`. Spätere Doku-Commits auf diesem Zweig ändern keinen Code; die Nachweise gelten für den letzten Code-Commit `8bc695f`.

| Gate | Umgebung | Ergebnis |
|---|---|---|
| `full-node` | Sandbox, Node 24.22.2, Nicht-root, nur `core.fsmonitor`-Override entfernt | **PASS 2393 / FAIL 0 / SKIP 37** von 2430 |
| `critical`, Lauf 1 | wie oben | **FAIL** — `storage-agent` 30/2/0 (siehe unten) |
| `critical`, Lauf 2 (ruhig) | wie oben | **PASS 135/135** (process 37, storage-agent 32, android-source-contracts 64, host-checker 2) |
| Backend `ci`, `check`, `test` | wie oben | **PASS 64/64** |
| GitHub-Run [42478725998](https://github.com/Ullmann27/device-ai/actions/runs/42478725998) | `ubuntu-latest`, Temurin 17, Node 24 — **nur Diagnose** | `node-backend` und `android-host`: `success` |

**`critical` Lauf 1 (FAIL — vorbestehend, lastabhängig, nicht durch den Merge):** Beide Fälle sind die Zwei-Prozess-Tests in `.github/continuum/storage-fabric.test.mjs` mit `FULL_REPLICATION_REQUIRED_METADATA_NOT_COMMITTED` (`storage-fabric-catalog.mjs:103`: eine Knoten-`put`-Anfrage wurde abgelehnt). Die Loopback-Adapter des Fixtures nutzen `timeoutMs: 500` (`storage-fabric.test.mjs:42`). Belege: Der Merge ändert keine Node-Datei; ruhig 8/8 Läufe der Datei grün; unter CPU-Last (4 Busy-Loops auf 2 Kernen) trat im kalten ersten Lauf derselbe Fehlercode mit 2 fehlgeschlagenen Fällen auf (1 von 8 Läufen). Nichts wurde abgeschwächt oder geändert. Mögliche Härtung (Entscheidung beim Besitzer, nicht angewendet): Standard-Timeout 1500 ms im Fixture.

**GitHub-Diagnoselauf auf `8bc695f` — Android (keine Abnahme, Repo-Regel):**

- Gradle-Gate `:app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` (Flags wie in Abschnitt 9): `BUILD SUCCESSFUL in 29m 9s`, 41 Tasks.
- JUnit/Robolectric: **4032 Tests, 0 Fehler, 0 Errors, 20 Skips** (Vorlauf auf `d8353c2`: 10 Fehler, gleiche 20 Skips). `ContinuumDocumentSceneUiTest` 12/12, `ContinuumReferenceCockpitUiTest` 13/13, `ContinuumSystemStatusWorkWindowUiTest` 10/10, `JarvisActionScenesTest` 3/3.
- Lint: „No issues found.“
- Unsignierter QA-Build: `BUILD SUCCESSFUL in 2m 59s`; `aapt`: `at.heinz.deviceai.continuumlocal` · versionCode `50` · versionName `0.3.49`. Artefakt `Code50-UNSIGNED-NOT-INSTALLABLE` (`UNSIGNED_QA_NOT_INSTALLABLE`, `originalSigner=NOT_USED`) — **kein Besitzer-Update und niemals als solches anzubieten.**

**Verbleibende Skips (20, Ursachen in den Quellen geprüft):** 12× `@Ignore("PENDING …")` (Chief-/First-Thinking-Spezifikationen für noch nicht vorhandene Funktionen), 2× `ChiefWorkspaceWiringHostTest` (braucht `CONTINUUM_WORKSPACE_CHIEF_HOST_PROOF=1`), 3× Node-Trainingsvektoren/CRE1-Fixture (CI-geliefert), 2× `LocalModelReloadLifecycleTest` (`assumeTrue(host Java >= 21)`; der Workflow baut mit JDK 17 — **die Tests zu Laden/Entladen des Modells liefen daher nicht**), 1× `fold7InnerMotionClip` (reiner Evidenzclip).

## 11. Parallele Arbeit (nicht berührt)

- #635 (`research/continuum-epistemic-lab-20261108`, `[RESEARCH ONLY]`, 5 Dateien +215) hat den Staging-Zweig als Basis. Nicht gemergt, nicht verändert. Empfehlung (Entscheidung beim Besitzer): erst nach dem Code-50-Release mergen oder auf einen eigenen Forschungszweig umhängen, damit reine Forschungsdokumente nicht in die Release-Linie gelangen.
