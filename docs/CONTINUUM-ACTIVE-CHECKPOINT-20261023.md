# Device AI Continuum – Aktueller Integrationsstand und Arbeitsindex
Stand: 23. Oktober 2026. **Dies ist ein quellseitiger Integrationskandidat, keine installierbare, abgenommene APK.**
Dieser Index gilt nur für den Branch `integration/continuum-current-review-20261023` und dessen jeweils tatsächlichen `git rev-parse HEAD`. Alte Dokumente beschreiben historische Snapshots und ersetzen diesen Index nicht.

## Einstieg für jede neue KI-Entwicklungssitzung

1. `AGENTS.md`, `docs/CONTINUUM-EXPERIMENTVERTRAG.md`, `docs/CONTINUUM-EXPERIMENTE.json` und [Issue #41](https://github.com/Ullmann27/device-ai/issues/41) lesen. Nur **Device AI Continuum**, kein anderes App-Projekt.
2. Unbedingt tatsächlichen `git rev-parse HEAD`, Arbeitsbaum (`git status --short`), offene Draft-PRs und alle laufenden/gesperrten Arbeitspfade prüfen. Keine angeblich „neueste“ Funktion allein aus PR-Titel oder Chat ableiten.
3. Dieses Dokument lesen. `docs/CONTINUUM-INTEGRATION-GATES-20261023.md`, `docs/CONTINUUM-THREE-PASS-AUDIT-CCC1-SHELL-20261023.md` und `docs/CONTINUUM-OPUS-ASTRA-HANDOFF-20261023.md` zu ihren jeweiligen tatsächlichen Ausgangs-SHAs beachten.
4. `main`, Besitzerschlüssel, installierte APK, persönliche Fakten/Modellgewichte und Opus' aktiven CCC1-Branch nicht verändern. Änderungen: kleiner isolierter Commit und Draft-PR, unabhängige Prüfung, keine stillschweigende Promotion.

## Kanonische, nicht gemergte Entwicklungslinie

```text
owner-signierte APK 0.3.16 / versionCode 17
   |
   +-- PR #91 UI/Hologramm --------+
   +-- PR #92 Opus LM1 + CCC1 -----+--> PR #94 = gemeinsame Quellintegration (Draft)
   +-- PR #93 GGUF-Kontext --------+
                                        |
                                        v
                                  PR #95 = Cognitive Spine/CSX3-Adapter,
                                  Cortex Shadow, CDX1 Shadow,
                                  Kontext-Trichter, CCC1 Shell-Preflight
                                        |
                                        +-- PR #96 Fold7-Reflow-Fix (Draft,
                                        |   historischer Seitenzweig)
                                        |
                                        +--> DIESER BRANCH = PR #95 + PR #96
                                             UI-Code ohne Überschreiben der
                                             neueren Forschungsdateien
                                             (eigener Draft, keine APK)
```

**Verbindlich:** Die Branches sind nicht alle gemergt; `main` ist älter. Der jeweils neue Integrations-HEAD ist ein REVIEW-SHA und muss vor Ausführung erneut fixiert werden. Über das Ergebnis einer Git-Merge- oder Release-Aktion entscheidet weiterhin der Eigentümer nach echten Gates.

### Was im aktuellen Quellbaum vorhanden ist

| Bereich | Vorhandene Einstiegspunkte | Tatsächliche Grenze |
|---|---|---|
| Native Android-Oberfläche | `AgentActivity.kt`, `ui/ContinuumHomeView.kt`, `ui/ContinuumControls.kt`, Hologramm-/Dashboard-Views | neue Fold7-Eingabereparatur quellseitig übernommen; SDK-/Realgeräteprüfung offen |
| Modelle | `LocalModelHost`, `LocalModelRuntime`, `learning/TinyContextDecoder.kt`, `learning/lm/LmRuntime.kt` | CDX1/LM1 Forschungsmodelle, kein nachgewiesener Ersatz des Hauptchats |
| Quellen/Antworten | `ContinuumMemoryDialogue.kt`, `learning/span/ContinuumResidualSpanCore.kt`, `intelligence/ContinuumCognitiveSpine.kt`, `ResidualSpanResearchAdapter.kt` | Lexikon-/Literalspan-Prüfung ist keine unabhängige Tatsachenzertifizierung |
| Denken/Lernen | `intelligence/maxultra/ContinuumCortexShadow.kt`, `ContinuumCortexResearchPipeline.kt`, `learning/span/ContinuumCounterfactualCurriculum.kt` | experimentell; keine automatische Besitzergewichts-Promotion |
| CCC1 | `.github/continuum/ccc1-cli.mjs`, `ccc1-worker.mjs`, `ccc1-verifier.mjs`, `local-teamleader-loop.mjs`, `ccc1-path-policy.mjs` | CCC1 Node-Worker quellseitig; Android Ende-zu-Ende noch offen |
| Lokaler Shell-Gate | `.github/continuum/ccc1-local-preflight.mjs`, `ccc1-local-preflight.test.mjs` | NUR lesende Vorprüfung; kein Workerstart, kein OS-Sandbox-Beweis |
| Android↔Termux | `ContinuumMobileBuilderActivity.kt`, `ContinuumMobileCodingJob.kt`, `ContinuumMobileBuildResultReceiver.kt`, `ToolExecutionReceiptStore.kt`, `ShizukuBridge.kt` | bestehenden Rückkanal wiederverwenden; echter aktueller CCC1-Zyklus nicht belegt |
| APK-Auslieferung | `app/build.gradle.kts`, `scripts/render-mentor-owner-update-v17.sh` | aktuell weiterhin versionCode 17 bei Owner-Signed-Update; keine neue 0.3.17-APK |

## Historische PRs: erhalten, NICHT ungeprüft überschreiben oder löschen

Die folgende Bewertung beruht auf GitHub-Commit-Abstammung und Quellbaum-Vergleichen am 23.10.2026, nicht auf einer Vollanalyse jeder historischen Codezeile.

| PR | Prüfung gegen den kombinierten Stand | Entscheidung |
|---|---|---|
| #52, #78, #79, #81, #84 | Git-Vorfahren des aktuellen Review-Stands (`behind=0` bei Commit-Vergleich) | Quellhistorie ist enthalten; keinen zweiten Merge |
| #70 QF2+ | `dialogue-retrieval.mjs` und `ContinuumMemoryDialogue.kt` sind zum PR-70-HEAD **blob-identisch** | Bereits übernommen; keine erneute Kopie |
| #72 I2 selektive Risikozertifizierung | eigenständiger Entwicklungszweig; 34 dort vorhandene Dateien fehlen im aktuellen Baum, einschließlich `ContinuumSelectiveRiskCertification.kt` und separaten Beleg-/Testdateien | Wichtiges, **noch nicht übernommenes Forschungsmodul**. [PR #72](https://github.com/Ullmann27/device-ai/pull/72) erhalten; erst API-/Holdout-/Checkpoint-Kompatibilität nachweisen, dann selektiv übernehmen |
| #75 und #82 | zentrale CCC1-/NFC1-Codepfade sind weitgehend vorhanden, aber zwölf alte Quellen-/Isolations-Belegdokumente fehlen im aktuellen Baum | Belege im [PR #82](https://github.com/Ullmann27/device-ai/pull/82) erhalten; Test- und Experimentprovenienz vor ggf. dokumentiertem Transfer prüfen |
| #83 Fold-Rail | keine fehlenden Dateipfade, aber aktuelle Dateiinhalte von älterem Spezial-Patch abweichend | aktuelle, weiterentwickelte Layoutimplementierung prüfen; keinen alten Vollfile-Overwrite |
| #90 visuelle QA | vier dort vorhandene Doku-/Testdateien fehlen, etwa `scripts/test_continuum_emulator_screenshots.py` | [PR #90](https://github.com/Ullmann27/device-ai/pull/90) als expliziten QA-Kandidaten für Astra erhalten; Tests vor Integration gegen *aktuelle* Skripte ausführen |
| #96 Fold7-Eingabe | `AgentActivity.kt`, `ComposerReflowDraft.kt` und `ComposerReflowDraftTest.kt` sind hier **byte-identisch** von PR #96 übernommen | ab jetzt aktuellen Integrationszweig als vollständigeren Review-Kandidaten verwenden; PR #96 bleibt als Ursprung und Rollback nachvollziehbar |

Andere ältere PRs bleiben bis zu einer **separaten Code-/Belegentscheidung** erhalten und werden nicht als automatisch geprüft oder veraltet eingestuft. Divergenz allein heißt nicht, dass ein Feature fehlt: sie kann auch aus nachfolgenden Anpassungen stammen.

## Die aktuelle CCC1-Sperre (was Opus wirklich fertigstellen muss)

Korrekte CLI-Aufrufe: `compile`, `submit`, `status`, `run-once`, `loop`; es gibt keinen Kommandovertrag `cycle --state-dir --trigger`. Ein gemeinsam autorisiertes **Owner→Android→Termux→CCC1→verifizierte Receipts→Android-Review** ist das offene E2E-Gate. Die bereits vorhandenen Lease-/Journal-/Verifier-/Backlog-Module dürfen nicht dupliziert oder umgangen werden.

1. Vorbedingung: sauberer Git-Branch, Besitzerfreigabe, Quell-SHA, State-Verzeichnis außerhalb des Projekts; lokale Preflight-Prüfung (noch keine Ausführungserlaubnis).
2. Besitzerbestätigter konkreter Zielauftrag über den EXISTIERENDEN Termux-Jobtransport.
3. Realer Exitcode/Journalstatus/Verifier-Belege inklusive Abbruch, Kill, Wiederaufnahme und misslungener Tests.
4. Geprüften Patch ausschließlich zur Kontrolle bereitstellen; **kein Main-Merge, kein automatisches Besitzer-App-Update**.
5. Der nächste gemeinsame Android-/Node-/Lint-/APK-Test muss denselben Integrations-SHA verwenden.

## Testevidenz und Release-Sperre

Belegte **isolierte** Tests aus den Arbeitssitzungen: 2× Kotlin-Kortex-Programme PASS; Kotlin Composer-Sonderfälle PASS; CCC1-Preflight 4/4 PASS und CCC1 State-Policy 2/2 PASS. Vollständiger Quellbuild samt Android-SDK-/JUnit-/Lint-/Fold7-Abnahme wurde NICHT ausgeführt. GitHub CI-Status auf PR #95/#96: keine bestandenen Checks gemeldet. GitHub Actions auf dem älteren `main` scheitern teilweise ohne auslesbare Job-Step-Daten; kein Testbefund für PR #94+.

Neue Owner-APK **nicht bauen oder verteilen**, bevor der Besitzer-Update-Branch unter isolierter Prüfung korrekt auf `versionCode >17` gestellt ist, die **identische** Paketidentität `at.heinz.deviceai.mentorpaired` und das bestehende Signaturzertifikat nachgewiesen sind, vollständige Prüfungen bestehen und das alte Update-Rollback archiviert wurde.

## Übergabe

**Opus:** nur CCC1-E2E, Worker-Journal/Verifier, Termux-Berechtigung/Lease, echter Shell-Status. `docs/CONTINUUM-OPUS-ASTRA-HANDOFF-20261023.md`.

**Astra:** aktuelle UI/IME-Fold-Reflow-Tests, sichtbares neues Hologramm/Navigation, vollständige Android-Regression. PR #90 als optionalen, nicht blind kopierten QA-Test ergänzen.

**Integrationsverantwortung:** alle Arbeit im **aktuellen** Review-Branch bündeln, alte gesicherte Zweige vorerst unangetastet lassen; Release erst mit nachprüfbar grünen Voll-Gates.
