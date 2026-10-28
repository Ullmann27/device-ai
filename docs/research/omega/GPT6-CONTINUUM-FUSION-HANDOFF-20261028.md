# GPT-6 Continuum Fusion – GitHub-Handoff (28.10.2026)

> **Aktueller Abschlussstand:** siehe „SOL-Abschlussprüfung“ am Ende. Die ursprünglichen `NOT_RUN`-Angaben und Fortsetzungsvorschläge unten dokumentieren den damaligen Stand und werden durch diesen Nachlauf ersetzt.

## GitHub-Identität und unveränderte Sicherheitsgrenzen

Repository: `Ullmann27/device-ai` (privat).
Arbeitsbranch: `chatgpt/continuum-fusion-memory-vertical-20261028`.
Draft-PR: [#203](https://github.com/Ullmann27/device-ai/pull/203), **Base = PR #198-Branch** `chatgpt/continuum-research-integration-memory-m4-20261027`.
Verifizierter Ausgangs-HEAD #198: `34acc378ac77e915b8d322bf02e46af77288bcf0`; darunter PR #197 `77e429f6d2f0eebfe697244a467f2b0565e7eb68`, PR #196 `126309f9a7069c7e7b5bea3e2082462cab4f9f9c`. Alle drei waren bei Einstieg open/Draft/unmerged. Der **aktuelle** #203 HEAD ist beim Weiterarbeiten erneut aus GitHub auszulesen; dieses Dokument kann seine eigene Commit-SHA nicht kennen.

Betrifft EXP-04/06/07 und Issue #199; #200 (Skills), #201 (CCC1), #202 (ARM64) bleiben offen. `main`, die alten PR-Branches, produktive Modelle, Gedächtnis-Ledger, Besitzer-ToolGate, Signatur und Android-App wurden **nicht** geschrieben, gemergt, gelöscht, signiert oder installiert.

## Tatsächliche Codeänderungen auf #203

1. **NEU** `app/src/main/java/at/heinz/deviceai/cognitive/AuthorizedConversationMemoryShadow.kt`: read-only Observer mit `AuthorizedHybridShadowReport` und festen Statuscodes. Nur 1–3 bereits SWB1-autorisierte `ContinuumMemoryHit`-Objekte (oder saubere Abstinenz), max. 4096 Zeichen/Beleg, exakte ID-Reihenfolge, zwei existierende Belegarten, vollständige CRE1-ID-/Lexikal-Score-Identität. Aufruf des **vorhandenen** `HybridMemoryRetriever.retrieve()` mit ausschließlich temporären `MemoryRecord`-Objekten, `DEVICE_LOCAL_ONLY`, `UNVERIFIED`, ohne Quell-URL oder erfundene Zeitstempel. Zwei Rankings (lexikalisch / optional bereits berechnete CRE1-Scores) nur als Aggregat. Keine Änderung an Antwort, Prompt, Toolrechten oder Memory-Store.
2. **GEÄNDERT** `app/src/main/java/at/heinz/deviceai/learning/ContinuumCognitiveRuntime.kt`: innerhalb der existierenden `reply()`-Kette werden die ursprünglichen autorisierten CRE1-Callback-Kandidaten erfasst und vor der Shadow-Evaluation **als vollständige Kotlin-Objekte** gegen die aus demselben Snapshot rekonstruierten `ranked`-Belege geprüft. Bei Differenz `REJECTED_EVIDENCE_IDENTITY`; bei Observer-Ausnahme `EVALUATION_FAILED`. `ContinuumCognitiveTurn.hybridShadow` ist nur ein neues aggregiertes Diagnosefeld mit rückwärtskompatiblem Default. Das bestehende `Cre1PromotionGate.rerank`, `ContinuumLearningCore.replyWithEvidenceInConversation` und die produktive Antwortauswahl bleiben unverändert.
3. **NEU** `app/src/test/java/at/heinz/deviceai/cognitive/AuthorizedConversationMemoryShadowTest.kt`: sechs Kotlin-JUnit-Tests für lexikalisch/CRE1 abweichendes Ranking ohne Antwortmutation; Abstinenz; fehlende, zusätzliche, vertauschte, doppelte, unbekannte, übergroße und NaN-Belege; gefälschte/partielle CRE1-Scores; keine Inhalte/IDs/URLs im Diagnosebericht; synthetische Verbindung zu `MemoryRetrievalResearchGate` und `MemoryResearchShadowComparator` mit fünf getrennten Holdout-Fähigkeiten und ohne automatische Promotion.
4. **GEÄNDERT** `app/src/test/java/at/heinz/deviceai/learning/ContinuumCognitiveRuntimeTest.kt`: echter SWB1-Dialogaufruf im Testcode mit `confirmOwnerFact`, unverändertem Modellhash, überprüfter Hit-ID-Kette, anschließend `revokeOwnerFact` und erneuter notwendiger Abstinenz. Zusätzlich werden die Fallbacks für unbekannte Fragen und beschädigte CRE1-Checkpoints überprüft. **Testcode eingecheckt, nicht ausgeführt.**
5. **NEU** `docs/research/omega/CONTINUUM-FUSION-FIVE-CORE-ARCHITECTURE-20261028.md`: Zuordnung aller 15 vorhandenen Forschungsregister-Quellen zu den fünf autoritativen Continuum-Kernen mit konkreten Eintrittspunkten, Status, Risiko, Grenzen und nächstem Gate.
6. **GEÄNDERT** `docs/research/CONTINUUM-EXTERNAL-CODE-INTEGRATION-20261027.md`: PR-#203-Verknüpfung ohne Verlust der 15 Quellen.

## Gepushte Code-Commits (chronologisch)

- [`5f9f1c6`](https://github.com/Ullmann27/device-ai/commit/5f9f1c6ec9f2793ba7bf8eebc189f142d346bdfa): erster sichtbarer WIP-Checkpoint.
- [`12a4dc0`](https://github.com/Ullmann27/device-ai/commit/12a4dc0dc9298132eeae2d0241696778de622a4a): Observer-Quellcode.
- [`5278be4`](https://github.com/Ullmann27/device-ai/commit/5278be465fa9b4518ddecb23021069a9d462aa68): echter Runtime-Aufruf.
- [`cbdcc9d`](https://github.com/Ullmann27/device-ai/commit/cbdcc9dcae4a01b780a35b8b2507cdb6e3955a87): negative Kotlin-JUnit-Fälle.
- [`0b0bdeb`](https://github.com/Ullmann27/device-ai/commit/0b0bdeb01dbc13aa3a1b7bddccabd988924553f7): SWB1-Revocation-Integrationstest.
- [`6e55085`](https://github.com/Ullmann27/device-ai/commit/6e55085e9264c5997b223bba3eee53966026b56d): Research-Gate/Holdout-Test.
- [`4320025`](https://github.com/Ullmann27/device-ai/commit/43200251fe2e59d02a627d0bf0c4f5c81726b532): fünf-Kern-Architektur.
- [`94c8eab`](https://github.com/Ullmann27/device-ai/commit/94c8eab6621c7707fdfb3be4e7520dd03476a313): vollständiger In-Turn-Kandidatenidentitätsvergleich.
- [`c550927`](https://github.com/Ullmann27/device-ai/commit/c550927d56f4eb81c4ae90682bbfa050cac052b2): Forschungsregister-Hinweis.
- [`8e62fac`](https://github.com/Ullmann27/device-ai/commit/8e62facd33d0c6e73af9e85b44b2a49a3c7e30ca): zusätzliche Runtime-Abstinenz-/CRE1-Korruptionsassertionen.

## Ehrliche Tests und Messgrenzen

**Wirklich ausgeführt:** Rücklesen der vier neuen/geänderten Kotlin-Dateien vom aktuellen GitHub-Branch; **26/26** gezielte JavaScript-Quell-/Integrationsinvarianten **PASS**, einschließlich vier einfacher balancierter-Klammer-/String-/Kommentar-Scans. Dies sind ausschließlich **Source-Checks**, weder Kotlin-Compiler noch JUnit. Der vorherige M4-Stubs-`kotlinc`-Erfolg gehört zu #198 und ist **kein** Test dieser neuen Dateien.

**NOT_RUN / UNAVAILABLE:** echter Kotlin-Compiler für den neuen Branch, JUnit-Laufzeit, vollständige Android Gradle/SDK36/JDK17-Suite, Android Lint, native Fold7/Termux ARM64-Ausführung, echte Gemma-/CRE1-Modellmessung, APK, Owner-P12-Signierung, Installation, Laufzeit-/RAM-/Thermik-/Energievergleich, unabhängiger Retrieval-Qualitätsgewinn. GitHub Actions, Render und kostenpflichtige APIs wurden nicht gestartet.

**Nächster verifizierbarer Lauf im authentifizierten vollständigen Checkout:**

```sh
git rev-parse HEAD
./gradlew :app:testDebugUnitTest --tests 'at.heinz.deviceai.cognitive.AuthorizedConversationMemoryShadowTest' --tests 'at.heinz.deviceai.learning.ContinuumCognitiveRuntimeTest' --tests 'at.heinz.deviceai.cognitive.MemoryRetrievalResearchGateTest' --tests 'at.heinz.deviceai.cognitive.MemoryResearchShadowComparatorTest'
./gradlew :app:compileDebugKotlin :app:lintDebug
node --test scripts/continuum-external-research-contract.test.mjs
```

Nach bestandenem gezielten Lauf: vollständige `:app:testDebugUnitTest`-Suite und reproduzierbare, getrennte DEV-/frozen-HOLDOUT-Regressionen gegen unveränderte SWB1-/CRE1-Baseline. Die neue Shadow-Sicht **kann noch keine Verbesserung bei bisher nicht gefundenen Belegen beweisen**, weil sie absichtlich nur autorisierte vorhandene Treffer erhält. Eine echte Produktiv-Retrieval-Änderung benötigt unabhängigen Qualitätsnachweis und separate Besitzerfreigabe.

**Forschungsstand:** Ein separater abgeschlossener Deep-Research-Bericht wurde diesem Implementierungslauf nicht als auslesbares Artefakt übergeben. Die Zuordnung basiert auf dem konkreten Besitzerauftrag und dem bereits im privaten Repository dokumentierten 15-Quellen-Register. Keine externe Upstream-Commit-/Lizenzprüfung wurde für eine Codeübernahme behauptet; es wurde kein Fremdcode übernommen.

## Risiken und Fortsetzung

- **Risikogrenze:** `ContinuumMemoryHit` besitzt keine kanonischen Zeitstempel/ValidationState. Der Adapter setzt sie deshalb bewusst nicht auf `VERIFIED` oder „jetzt“. Für echte Zeit-/Widerspruchsqualität braucht es später autorisierte Provenienzschnittstellen, nicht erfundene Metadaten.
- **Fehlende CRE1- oder Rekonstruktionsidentität:** Diagnose fällt zurück auf `LEXICAL_ONLY`, `REJECTED_CRE1_SCORES` oder `REJECTED_EVIDENCE_IDENTITY`, ohne die ursprüngliche Antwort umzuschreiben.
- **Laufzeitkosten:** zwei lokale Rankings über maximal drei Belege, noch nicht auf Gerät gemessen; bei zu hoher Latenz zuerst diesen Observer aus dem isolierten PR zurücknehmen.
- **Nächster Schritt:** echte Kotlin-JUnit-Fehler reproduzieren und minimal reparieren; erst nach tatsächlichem Grün Issue #200 (deklariertes Skill-Manifest über bestehendes ToolGate), danach #201 (CCC1-Repro/Verifier), #202 (ARM64 SDK36/Gradle8.11.1/JDK17). Keine automatische Promotion.
- **Rollback:** PR #203 Draft lassen oder gezielten Revert im neuen Branch; #198/#197/#196 und `main` nicht verändern, kein Force-Push, kein Löschen, keine Besitzer-P12 auslesen.

**Status:** `SOURCE_INTEGRATED / RESEARCH_SHADOW` für den produktiven Aufrufanschluss, `NOT_RUN` für Kotlin-JUnit/Android, `BLOCKED` für Original-P12-/Fold7-Abnahme. **Keine belegte Verbesserung der Antwortqualität**.

## SOL-Abschlussprüfung – 28.10.2026

**Geprüfter Quellcommit:** [`63beedbb04ec35fc1ad55af60ba53562dae79cd4`](https://github.com/Ullmann27/device-ai/commit/63beedbb04ec35fc1ad55af60ba53562dae79cd4), bei Einstieg identisch mit GitHub-HEAD von [Draft-PR #203](https://github.com/Ullmann27/device-ai/pull/203); Basis #198 unverändert `34acc378ac77e915b8d322bf02e46af77288bcf0`. Sauberer isolierter Worktree `/workspace/device-ai-fusion`. Keine anderen lokalen Worktrees verändert. Der abschließende SHA steht im PR; der finale Dokumentationscommit ist zusätzlich mit `git log -1 --format=%H -- docs/research/omega/GPT6-CONTINUUM-FUSION-HANDOFF-20261028.md` eindeutig bestimmbar. Programm- und Testquellen bleiben bytegleich zum geprüften Commit.

**Vorhandene Toolchain, unverändert:** Linux x86_64; Temurin JDK17.0.21+9; Gradle8.11.1; Projekt-Kotlin-Plugin2.4.0 (Gradles eingebettetes Kotlin2.0.20); AGP8.10.0; SDK API36 Revision2; Build Tools35.0.0 / aapt2 2.19-11948202; Node24.19.0. Keine Installation und kein Versionswechsel. Projektwarnungen sind in den Rohlogs erhalten.

| Tatsächlicher Lauf | Status / Exitcode |
|---|---|
| Zuerst angeforderter `./gradlew`-Aufruf mit den vier Testfiltern | FAIL / 127: Wrapper fehlt im Repository. Umgebungs-/Startfehler, kein Kotlin-/JUnit-Befund. Originalausgabe erhalten. |
| Derselbe gezielte Lauf mit vorhandenem Gradle und realen Projektklassen | PASS / 0; BUILD SUCCESSFUL in 2m6s; 34 Tests, 0 Failures, 0 Errors, 0 SKIP. |
| `AuthorizedConversationMemoryShadowTest` | PASS 6/6. |
| `ContinuumCognitiveRuntimeTest` | PASS 14/14. |
| `MemoryRetrievalResearchGateTest` | PASS 9/9. |
| `MemoryResearchShadowComparatorTest` | PASS 5/5. |
| `:app:compileDebugKotlin :app:lintDebug` | PASS / 0; BUILD SUCCESSFUL in 1m33s. Kotlin hier UP-TO-DATE nach frischer Kompilierung im gezielten Lauf. Lint unverändert auf `NewApi` begrenzt, keine vollständige Lint-Abdeckung. |
| Node-Vertrag vor Korrektur | FAIL / 1; 4 PASS, 1 FAIL: im Katalog fehlte der lokale Builder-Verweis. |
| Node-Vertrag nach Korrektur | PASS / 0; 5/5, 0 SKIP. Tests unverändert. |

**Tatsächlich verwendete Befehle:** zunächst `git rev-parse HEAD` und der oben dokumentierte vollständige `./gradlew`-Aufruf; anschließend mit der vorhandenen Toolchain:

```bash
export JAVA_HOME=/workspace/continuum-tools/jdk17
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME=/workspace/continuum-tools/android-sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME=/workspace/continuum-tools/gradle-cache
gradle_bin=/workspace/continuum-tools/gradle/gradle-8.11.1/bin/gradle
gradle_args=(--offline --no-daemon --console=plain -PdeviceAiMentorPreview=true -Pandroid.aapt2FromMavenOverride=/workspace/continuum-tools/android-sdk/build-tools/35.0.0/aapt2)
"$gradle_bin" "${gradle_args[@]}" :app:testDebugUnitTest --tests 'at.heinz.deviceai.cognitive.AuthorizedConversationMemoryShadowTest' --tests 'at.heinz.deviceai.learning.ContinuumCognitiveRuntimeTest' --tests 'at.heinz.deviceai.cognitive.MemoryRetrievalResearchGateTest' --tests 'at.heinz.deviceai.cognitive.MemoryResearchShadowComparatorTest'
"$gradle_bin" "${gradle_args[@]}" :app:compileDebugKotlin :app:lintDebug
node --test scripts/continuum-external-research-contract.test.mjs
# Letzteren Befehl nach der Dokumentationskorrektur identisch wiederholt.
```

**Tatsächlich geänderte Dateien:** `docs/research/CONTINUUM-EXTERNAL-CODE-INTEGRATION-20261027.md` (einziger reparierter Fehler: Link auf den bestehenden `scripts/continuum-local-owner-apk.sh` im APK-Absatz); diese bestehende Handoff-Datei; Rohbelege unter [`docs/evidence/continuum-fusion-final-20261028/`](../../evidence/continuum-fusion-final-20261028/). Keine Änderungen an Kotlin, Tests, Gradle oder Schutzbedingungen. Das Hashmanifest listet jede Belegdatei; beide ursprünglichen FAIL-Ausgaben bleiben erhalten.

**Schutzprüfung:** Die echten JUnit-Läufe prüfen exakte Belegidentität, gefälschte/partielle CRE1-Scores, Abstinenz, Widerruf, private Diagnoseinhalte und unveränderten Modellhash. Der korrupte CRE1-Snapshot lässt die bestätigte normale Antwort zu und fällt auf `LEXICAL_ONLY` zurück. Der Ausnahmefang des neuen Hybrid-Observers (`runCatching` → `EVALUATION_FAILED`) wurde im Quellcode geprüft; eine gezielt injizierte Observer-Ausnahme ist **NOT_RUN**, kein behaupteter Laufzeitnachweis dieses speziellen Fehlerpfads. Der bestehende CRE1-Unit-Test erzeugt ausschließlich einen synthetischen Snapshot in seinem temporären Testverzeichnis; keine produktiven Gewichte verwendet oder verändert.

**NOT_RUN / offene Nachweise:** volle Unit-Regressionssuite; injizierter Observer-Ausnahmefall; Android-Instrumentation/Gerät/Fold7/Termux ARM64 (hier nicht zugänglich); Gemma-Inferenz, APK, Original-P12-Signierung, Installation, Laufzeit-/RAM-/Thermik-/Energie-/Qualitätsvergleich. Die Linux-Pfade oben sind kein Termux-Rezept. Keine Stubs, keine neue Toolchain, kein Modell-Download, kein Provider-/Trainingsexperiment und kein kostenpflichtiger Dienst. Die anfänglichen 26 Source-Checks bleiben Source-Checks; 34 Unit-Tests beweisen keinen Geräteerfolg oder verbesserten Recall bisher übersehener Erinnerungen. SWB1/CRE1/CDX1/Gemma, Besitzerbelege, ToolGate, Signierschlüssel, installierte APK und `main` bleiben unberührt; keine Integrationen oder Merges.

**Genau ein nächster ausführbarer Schritt:** mit der oben gesetzten vorhandenen Linux-Umgebung die vollständige bestehende Unit-Suite ausführen: `"$gradle_bin" "${gradle_args[@]}" :app:testDebugUnitTest`. Keine neue Forschung beginnen. PR #203 bleibt Draft; alle Entwicklungszweige und Verläufe bleiben erhalten.

**Aktueller Status:** `SOURCE_INTEGRATED / RESEARCH_SHADOW`; gezielte echte Kotlin-/JUnit-Prüfung **34/34 PASS**, Compile/Lint **PASS**, Node-Vertrag **5/5 PASS**. Geräte- und Qualitätsgewinn **NICHT NACHGEWIESEN**.

## Nachlauf: vorhandenen Fusion-Pfad bis zur Haupt-App-Diagnose schließen

Besitzerauftrag nach dem Abschluss: vorhandene Gehirnverbindungen mit knappem Restbudget fertigstellen. EXP-04/06/07; Vertrag und aktuelles Issue #41 gelesen. Ausgangs-HEAD `a6fe9466c1970755257a62a8f116cae0fdad383a`, sauber und identisch mit Remote; keine weiteren Integrationen.

Die Haupt-App nutzt bereits `CognitiveDecisionBridge.respond()` → `ContinuumCognitiveRuntime.reply()` → autorisierten SWB1-/CRE1-Zug → `AuthorizedConversationMemoryShadow.observe()`. `AgentActivity.runContinuumDialogue()` zeigt `cognitive.trace` über die vorhandene Toolaktivität an. Der neue Shadow-Bericht fehlte bisher in genau dieser Diagnoseanzeige. `ContinuumCognitiveRuntime.kt` ergänzt ausschließlich Status und autorisierte Anzahl in den bestehenden Trace, keine Inhalte/IDs, keine zusätzliche Retrieval- oder Modellanfrage, keine Antwort- oder Berechtigungsänderung.

`CognitiveDecisionBridgeTest.kt` erhält einen echten Projektkern-Test für denselben App-Brückenaufruf: bestätigter Treffer, identische Antwort und Belegkette, begrenzte Diagnose, unveränderter Modellhash, danach Widerruf und notwendige Abstinenz ohne Wiederbelebung des Belegs. Kein neuer Adapter oder Inferenzprovider.

**Gesicherter Anschlusscommit:** [`f86847cff0a8145d5e7f9623fcdbea43d5d692e5`](https://github.com/Ullmann27/device-ai/commit/f86847cff0a8145d5e7f9623fcdbea43d5d692e5). Der folgende Belegcommit korrigiert ausschließlich die neue Testannahme und ergänzt diese Übergabe samt Rohbelegen; keine weitere Produktionsänderung. Die SHA-256-Werte beider tatsächlich geprüften Kotlin-Dateien stehen in den Belegen.

**Tatsächliche Läufe, unveränderte vorhandene Linux-Toolchain wie oben:**

- Erster Lauf: **FAIL / Exit 1**, 43 Tests, 42 PASS, 1 FAIL. Die neue Testannahme „2 alte Dialogzeilen nach Widerruf“ war falsch: `ContinuumLearningCore.revokeOwnerFact()` ruft absichtlich `dialogue.clear()` auf. Der korrigierte Test fordert nun ausdrücklich eine Zeile vor Widerruf, leeren Verlauf direkt nach Widerruf und genau eine neue abstinente Zeile ohne Memory-IDs danach. Keine produktive Schutzbedingung und keine bestehende Assertion abgeschwächt. Lint wurde in diesem ersten abgebrochenen Lauf nicht erreicht.
- Identischer Wiederholungslauf nach dieser Testkorrektur: **PASS / Exit 0**, BUILD SUCCESSFUL in 1m45s. **43/43 echte Kotlin-JUnit-Tests**, 0 Failures/Errors/SKIP: bisherige vier Klassen 34/34 plus `CognitiveDecisionBridgeTest` 9/9. Kotlin-/Test-Kompilierung und `:app:lintDebug` PASS; Lint bleibt ausschließlich `NewApi`.
- `node --test scripts/continuum-external-research-contract.test.mjs`: **PASS / Exit 0**, 5/5, 0 SKIP.

Exakter Gradle-Befehl: dieselbe oben dokumentierte Umgebung und `"$gradle_bin" "${gradle_args[@]}" :app:testDebugUnitTest --tests 'at.heinz.deviceai.cognitive.AuthorizedConversationMemoryShadowTest' --tests 'at.heinz.deviceai.learning.ContinuumCognitiveRuntimeTest' --tests 'at.heinz.deviceai.cognitive.MemoryRetrievalResearchGateTest' --tests 'at.heinz.deviceai.cognitive.MemoryResearchShadowComparatorTest' --tests 'at.heinz.deviceai.cognitive.CognitiveDecisionBridgeTest' :app:lintDebug`.

**Rohbelege:** [`docs/evidence/continuum-fusion-connect-20261028/`](../../evidence/continuum-fusion-connect-20261028/), mit ursprünglichem FAIL-Log und fünf FAIL-Lauf-XMLs, finalem PASS-Log und fünf PASS-Lauf-XMLs, Node-Ausgabe, Lint-Berichten, Quellhashes, Zusammenfassung und Hashmanifest. Tatsächlich geändert: `ContinuumCognitiveRuntime.kt` (vier Zeilen Diagnoseanschluss), `CognitiveDecisionBridgeTest.kt` (ein neuer Integrationstest), dieser bestehende Handoff sowie diese Belege. Der Codeanschluss und alle Belege bleiben auf demselben offenen PR #203; endgültigen HEAD im PR bzw. `git rev-parse HEAD` ablesen.

**Grenzen:** Der native Fold7-/Gerätenachweis, eine gezielt injizierte Hybrid-Observer-Ausnahme und bessere Retrieval-Qualität bleiben NOT_RUN/NOT_PROVEN. Keine zusätzlichen Forschungsintegrationen oder Aktivierungen; alle bisherigen Freigaben, ToolGate-Regeln, produktiven Gewichte, Besitzererinnerungen und Signierschlüssel unverändert. Keine APK, Installation, Cloud-Builds oder Main-Merges. **Einziger nächster Schritt bleibt die oben dokumentierte vollständige bestehende Unit-Suite.**

## Weiterer Besitzerauftrag: reife Forschung lesend erreichbar machen

Ausgangs-HEAD `4ebd588c395681de19b34df430ba387a915281fd`, sauber und remote bestätigt. EXP-07 mit bestehenden EXP-04/06-Verträgen. Kontextcompiler, ChatReasoningRouter, ReadOnlyToolPlan, ExperienceCompiler-Review und die Gemma-/SWB1-/CRE1-Aufrufwege sind bereits in der Haupt-App angeschlossen und werden nicht dupliziert oder ungeprüft umgeschaltet.

Kleinster zusätzlicher Anschluss: Das vorhandene Lernzentrum erhält unter den experimentellen Aktionen **„GEDÄCHTNIS · SYNTHETISCHER SELBSTTEST (NUR LESEN)“**. Es ruft auf dem vorhandenen Learning-I/O-Executor `MemoryResearchShadowComparator.syntheticSelfCheck()` → `compare()` → `MemoryRetrievalResearchGate.evaluate()` → bestehenden `HybridMemoryRetriever` auf. Die beiden bisher nur in JUnit erreichbaren Research-Gates werden damit ausdrücklich auf Besitzeraktion lokal nutzbar.

Der neue kleine Einstieg verwendet ausschließlich vier fest eingebaute öffentliche synthetische Records, eine separate Entwicklungsfrage und fünf feste Kontrollfragen einschließlich notwendiger Abstinenz. Die Sichtbarkeit dieser Fixtures ist beabsichtigt: **kein unabhängiger Qualitäts-Holdout, kein Trainingsmaterial und kein realer Recall-Benchmark**. Ein Null-Scorer prüft nur die bestehende Vergleichs-/Nichtverschlechterungslogik; er behauptet keinen lernenden semantischen Kandidaten. Das UI leitet BESTANDEN/FEHLGESCHLAGEN aus tatsächlich ausgeführten Ergebnissen ab und benennt diese Grenze. Keine Besitzer-Memory-Lese-/Schreibzugriffe, externe Anfrage, Modellladung, Toolausführung, Gewichtänderung oder automatische Promotion.

**Geprüfter und gepushter Quellcommit:** [`47a741cada431197e1dc5aba8d686783b8422d8d`](https://github.com/Ullmann27/device-ai/commit/47a741cada431197e1dc5aba8d686783b8422d8d). Der Abschlusscommit ergänzt nur diesen Handoff und Belege. Keine parallele Remote-Änderung am Forschungszweig beobachtet; alle anderen Zweige unverändert.

**Tatsächlich ausgeführt:** vorhandene unveränderte Linux-JDK17-/Gradle8.11.1-/SDK36-Toolchain und dieselben oben dokumentierten Offline-/MentorPreview-/AAPT2-Flags. `:app:testDebugUnitTest` mit den bisherigen fünf Testfiltern plus `at.heinz.deviceai.cognitive.ContinuumIntelligencePhase2Test`, `at.heinz.deviceai.cognitive.ExperienceEvidenceRegressionTest` und `at.heinz.deviceai.cognitive.ReadOnlyToolPlanTest`, anschließend `:app:lintDebug`: **PASS / Exit 0, BUILD SUCCESSFUL in 2m12s, 61/61 echte Kotlin-JUnit-Tests, 0 Failures/Errors/SKIP**. Der neue Test prüft den appseitig verwendeten synthetischen Einstieg, fünf Fähigkeiten, Abstinenz und notwendiges `eligibleForOwnerReview=false`. Die erweiterten Tests prüfen auch die bereits angeschlossenen Skill-/Experience- und schreibgeschützten Toolplan-Grenzen. Frische Kotlin-/Testkompilierung und unverändert auf `NewApi` beschränktes Lint PASS.

`node --test scripts/continuum-external-research-contract.test.mjs scripts/continuum-experiment-integration.test.mjs`: **PASS / Exit 0, 8/8, 0 SKIP**; diese Node-Prüfungen bleiben ausschließlich Quellverträge.

**Geänderte Dateien:** `AgentActivity.kt` (eine Aktion und lesender Dialog im vorhandenen Lernzentrum), `MemoryResearchShadowComparator.kt` (begrenzter synthetischer Einstieg in vorhandene Gates), `MemoryResearchShadowComparatorTest.kt` (ein neuer echter Unit-Test), dieser bestehende Handoff und [`docs/evidence/continuum-fusion-research-ui-20261028/`](../../evidence/continuum-fusion-research-ui-20261028/) mit acht originalen JUnit-XMLs, Gradle-/Node-Rohlogs, Lint-Berichten, exaktem Wiederholungsbefehl, Zusammenfassung, Quellhashes und Hashmanifest.

**Grenzen / nächster Schritt:** Fold7-/UI-Ausführung, volle Regression, injizierter Hybrid-Observer-Ausnahmefall und Qualitätsgewinn bleiben NOT_RUN/NOT_PROVEN. Der Selbsttest ist kein unabhängig unbekannter Qualitäts-Holdout; die feste Fixture ist nur eine öffentlich einsehbare Modulprüfung. Keine produktive Aktivierung von CRE1/CDX1 oder eines neuen Retrieval-Rankings, keine zusätzlichen Berechtigungen, Gewichte, Besitzererinnerungen, Schlüssel, APK oder Main-Änderungen. **Einziger nächster ausführbarer Schritt bleibt die vollständige bestehende `:app:testDebugUnitTest`-Suite mit der oben dokumentierten Toolchain.**

## Knappes Abschlussbudget: gleicher Gedächtnisabruf für interne Denkschritte

Ausgangs-HEAD `37826a7e5ce31c73f96a42e4f3d8817adf1adee7`, sauber und remote bestätigt. EXP-05/06/11, vorhandener Vertrag unverändert. Wichtigste verbleibende kleine Verbindung: `runLocal()` übergibt seinen bereits vorhandenen `LocalGroundedRecall` jetzt auch an `ChatReasoningRouter.advise()`. Bisher stand dieser aktuelle Abruf nur im finalen Modellprompt; Kandidaten-/Kritik-/Verifier-/Judge-Prompts bekamen lediglich den separat kompilierten Kontext.

Der bestehende Router fügt den identischen JSON-Datenblock nach dem vorhandenen Governance-Kontext ein. Kein neuer Abruf, Modelladapter, Tool oder Schreibpfad. Passt der vollständige Kontext samt vollständigem Beleg nicht in das unveränderte Kontextbudget, wird die optionale Zusatzanalyse vor jedem Modellaufruf übersprungen; der ursprüngliche endgültige Antwortpfad mit dem vorhandenen Abruf bleibt erhalten. Aufrufe ohne Abruf behalten ihr bisheriges Verhalten.

**Geprüfter und gepushter Quellcommit:** [`46222914dcff3551c1be99c833ca245cf1de1628`](https://github.com/Ullmann27/device-ai/commit/46222914dcff3551c1be99c833ca245cf1de1628). Der abschließende Belegcommit verändert nur diese Dokumentation und Rohbelege. Änderungen: `AgentActivity.kt` (derselbe Abruf als Router-Argument), `ChatReasoningRouter.kt` (vollständiger Kontext-/Belegtransport oder Abstinenz), `ChatReasoningRouterTest.kt` (zwei neue Unit-Tests), dieser Handoff und die folgenden Belege.

**Tatsächliche Verifikation:** unveränderte vorhandene Linux-JDK17-/Gradle8.11.1-/SDK36-Toolchain; oben dokumentierte Offline-/MentorPreview-/AAPT2-Flags; `:app:testDebugUnitTest` mit den bestehenden fünf Fusion-Testfiltern plus `ChatReasoningRouterTest` und `LocalGroundedRecallTest`, danach `:app:lintDebug`: **PASS / Exit 0, BUILD SUCCESSFUL in 2m21s, 56/56 echte Kotlin-JUnit-Tests, 0 Failures/Errors/SKIP**, Kotlin-/Testkompilierung und unverändert auf `NewApi` begrenztes Lint PASS. Die zwei neuen Tests prüfen identischen Abruf in allen internen Prompts sowie null Modellaufrufe bei zu großen Daten. Es sind Transport-/Budgettests mit einem ausdrücklich synthetischen Adapter, keine tatsächliche Modellinferenz.

`node --test scripts/continuum-external-research-contract.test.mjs scripts/continuum-experiment-integration.test.mjs scripts/continuum-local-context.test.mjs`: **PASS / Exit 0, 16/16, 0 SKIP**. Diese Quell-/Transport-/Budgetprüfungen sind kein Inferenznachweis.

**Rohbelege:** [`docs/evidence/continuum-fusion-reasoning-memory-20261028/`](../../evidence/continuum-fusion-reasoning-memory-20261028/), sieben originale JUnit-XMLs, Gradle-/Node-Ausgaben, Lint-Berichte, exakter Befehl, Quellhashes, Zusammenfassung und Hashmanifest. Der endgültige Beleg-HEAD ist in PR #203 bzw. mit `git rev-parse HEAD` ablesbar. Arbeitszweig bleibt offen; keine parallelen Änderungen beobachtet.

**Grenze:** Bei großen kombinierten Kontexten entfällt die optionale Zusatzanalyse nun auch dann, wenn sie zuvor ohne den vollständigen Abruf gestartet wäre. Der normale Modellprompt und der deterministische Rückfall behalten den Abruf. Kein tatsächlicher Gemma-/Fold7-Lauf oder Qualitätsgewinn nachgewiesen; volle Regression bleibt NOT_RUN. Keine APK, Gewichte, Besitzerbelege, Freigaben, Signierschlüssel, Main-Merges oder anderen Zweige geändert. **Einziger nächster ausführbarer Schritt:** die vollständige bestehende `:app:testDebugUnitTest`-Suite mit derselben dokumentierten Toolchain.
