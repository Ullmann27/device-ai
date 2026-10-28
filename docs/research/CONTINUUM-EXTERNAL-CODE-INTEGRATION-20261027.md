# CONTINUUM – internationale Open-Source-/Forschungsintegration (27.10.2026)

**Verbindlicher Einstieg für alle berechtigten Entwicklungsagenten.** Diese Akte ist der Integrations-Backlog zu EXP-02 (Reparatur), EXP-04/06 (Gedächtnis/Provenienz), EXP-05 (lokale Inferenz), EXP-07 (Benchmarks), EXP-09 (ARM64-Build) und EXP-11 (Spezialisten). Primärsystem bleibt **Device AI Continuum**, nicht ein zweites Produkt. Besitzerwunsch: Alle hier genannten geeigneten Bausteine langfristig prüfen und nach erfolgreicher Freigabe **in die bestehende lokale Continuum-Haupt-App und ihr Gehirn einbinden**. Das bedeutet **nicht**, fremde Software ungeprüft oder ohne Lizenzprüfung sofort produktiv zu schalten.

**Wahrheitsstatus am 27.10.2026:** Die Quellen sind externe Referenzen, keine automatisch integrierten Bibliotheken. Ein Projekt-README belegt weder Fold7-/Android-17-Kompatibilität noch eine auf unserem Gerät erfolgreiche Inferenz, signierte APK oder bessere Erinnerungsqualität. Jede konkrete Übernahme braucht einen eigenen, isolierten Integrationszweig, Lizenz- und Releaseprüfung, Regressionen, Besitzerschutz, reale Fold7-Nachweise und Rückfallplan. Niemand darf Kopien fremder Quelltexte ohne NOTICE/Lizenzhinweise in unser Repository drücken. Kein Download externer Gewichte ohne Zustimmung.

## Bestandsabgleich – was wir bereits haben

- **Gehirn:** `CognitiveDecisionBridge.kt` → `ContinuumCognitiveRuntime.kt` / SWB1 → freigegebene Belege; existierender Native-LiteRT-LM/Gemma-4-Pfad. Rein modellgenerierte Aussagen dürfen keine bestätigten Besitzerfakten überschreiben.
- **Gedächtnis:** `cognitive/HybridMemoryRetriever.kt` hat bereits BM25-artige Lexik, optionales Semantik-Scoring, Entitäts-/Zeit-/Provenienz-/Vertrauensbewertung und Datenschutzfilter. `MemoryEcology.kt` hat bereits `MemoryGraph`, PrivacyClass und ValidationState. `ProofCarryingMemory.kt` enthält signierbare Mutationsevidenz. Eine zweite konkurrierende Memory-DB wäre ein Rückschritt.
- **Werkzeuge:** vorhandenes ToolGate/Owner Approval, Shizuku- und Termux-Brücken. Skills sind nur Daten/Pläne; Modelltext allein darf keine neue Android-Berechtigung erzeugen.
- **Autonomie:** existierender CCC1-/CEGIS-Reparaturprozess und ein lokaler Teamleiter mit Zeit-, Akku- und Temperaturgrenzen. Keine zweite Worker-Schleife, keine Fremd-Shellbefehle aus Modelltext.
- **APK:** PR #197 führt lokale Haupt-App-QA ohne Original-P12 ein. Der vorhandene Owner-Buildweg ist [`scripts/continuum-local-owner-apk.sh`](../../scripts/continuum-local-owner-apk.sh). **Nur die Original-P12** signiert ein echtes Update für `at.heinz.deviceai.mentorpaired`. Termux-Fold7-Reallauf und Original-P12 bleiben offen.

## Umsetzungsregister – Quellcode, Nutzen, verbindlicher Eintrittspunkt

| ID | Externes Projekt / überprüfbarer Code | Konkreter Continuum-Anschluss | Kategorie / Status | Integrationstest |
|---|---|---|---|---|
| R-01 | [Google AI Edge Gallery – Skills](https://github.com/google-ai-edge/gallery/blob/main/skills/README.md) | *Manifestformat als Inspiration*, festgelegte Fähigkeiten an bestehenden ToolGate, keine beliebigen JavaScript-WebViews | EXP-05/11 · AUDIT_REQUIRED | Parser-/ToolGate-Negativtest: unbestätigtes Skill darf nichts ausführen |
| R-02 | [Jandal AI](https://github.com/NickMonrad/kernel-ai-assistant) | Android-Kotlin-Skill-/Voice-/Embedding-Verfahren vergleichen, **nicht** Gemma-Engine duplizieren | EXP-05/06 · AUDIT_REQUIRED | Gleicher Package-/Owner-/LiteRT-Pfad ohne neuen Inferenzkern |
| R-03 | [LocalChat](https://github.com/NeuroMarshal/LocalChat) | Hybride BM25/Embedding-Ranking-Evaluation, PDF-Seitenplaner, JVM-Harness; nur gezielte Adapter | EXP-04/06/07 · AUDIT_REQUIRED | Bestehenden Retriever gegen gleiche lokale Holdouts evaluieren |
| R-04 | [Mem0](https://github.com/mem0ai/mem0) | Gedächtnisverdichtung/Entität/Zeit **als Shadow-Kandidaten** über vorhandenen Ledger | EXP-04/06 · AUDIT_REQUIRED | Revoke/Contradiction/Privacy vor jeder Ausgabe erhalten |
| R-05 | [A-MEM](https://github.com/agiresearch/A-mem) | Vorschläge für `MemoryGraph`-Beziehungen; neue Kanten niemals ungeprüft als Wahrheit | EXP-06 · AUDIT_REQUIRED | Graphrelationen belegen, Besitzerfakten nicht automatisch ändern |
| R-06 | [sqlite-vec](https://github.com/asg017/sqlite-vec) | **optionaler** lokaler Embedding-Index, kein zweiter Faktenspeicher | EXP-06 · ABI_LICENSE_CHECK | Fold7-ABI/JNI und SQLite-Extension-Ladeverhalten prüfen |
| R-07 | [LongMemEval-V2](https://github.com/xiaowu0162/LongMemEval-V2) | Fähigkeiten: Zustand, Verlauf, Arbeitsabläufe, Stolperfallen, Prämissen; lokale synthetische Fälle und getrennte Holdouts | EXP-07 · FIRST_SHADOW_SLICE | `MemoryRetrievalResearchGate.kt` wertet **bestehenden** `HybridMemoryRetriever` aus; Android-/JVM-Test erforderlich |
| R-08 | [mini-SWE-agent](https://github.com/SWE-agent/mini-swe-agent) | Kleine Schritte, feste Toolverträge, transparente Patchabläufe im **bestehenden CCC1** | EXP-02/11 · AUDIT_REQUIRED | Neuer Test erst nach unabhängiger Reparaturprüfung und Besitzerreview |
| R-09 | [Aider](https://github.com/Aider-AI/aider) | Repository-Karte/kleine Änderungen, keine zweite lokale Python/Cloud-Agentenpflicht | EXP-02/11 · AUDIT_REQUIRED | Begrenzter Scope, unabhängiger Git- und Testbeleg |
| R-10 | [OpenHands](https://github.com/OpenHands/OpenHands) | Sandbox-/Interaktionsmuster **als Referenz**; mögliche PC-Option, nie Pflicht | EXP-02/11 · ARCHITECTURE_ONLY | Keine Modellzugriffsfalle und kein neuer produktiver Agent |
| R-11 | [Termux Android Build Tools](https://github.com/termux/android-build-tools) | Native ARM64 `aidl`, `aapt2`, `zipalign` gegen bestehendes SDK36/Gradle8.11.1 prüfen | EXP-00/09 · HIGH_PRIORITY | Echter Fold7-Unit/Lint/APK-Build, Signer/Package/SHA getrennt |
| R-12 | [Android SDK Tools ARM64](https://github.com/amirghm/android-sdk-tools-arm64) | Alternative gepinnte ARM64-Buildtoolquellen **nur nach ABI/Versions-Prüfung**; dort SDK37, bei uns SDK36 | EXP-00/09 · INCOMPATIBLE_UNTIL_PROVEN | Kein unkontrolliertes SDK37-Upgrade oder fremdes Binärpaket |
| R-13 | [LongMemEval](https://github.com/xiaowu0162/LongMemEval) | Kleine lokale Fähigkeitstests: Faktenupdate, Abstinenz, Zeit, Mehrsitzungsbezüge | EXP-04/07 · FIRST_SHADOW_SLICE | Gleicher Datensatz, Vorher-Nachher-Score mit eingefrorenem TEST |
| R-14 | [Google LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM) | Eigentümerfreigabe vor Toolausführung, dokumentierte Modell-/Runtime-Schnittstelle prüfen | EXP-05/11 · AUDIT_REQUIRED | Reale Gemma-E4B-Läufe und negatives Tool-Proposal-Gate |
| R-15 | [US-Forschungsdiskussion r/LocalLLaMA](https://www.reddit.com/r/LocalLLaMA/) · [r/termux](https://www.reddit.com/r/termux/) · [Google AI Forum](https://discuss.ai.google.dev/) | Fehlerbilder, ARM64-Erfahrungen und Referenzen; Forumsaussagen **keine** technische Autorität | RESEARCH_INPUT | Commit/Quellbeleg und reproduzierbarer lokaler Fehlerbeweis |

### Weitere Codequellen dauerhaft überwachen

[Hugging Face](https://huggingface.co) (Modelle/Datasets/Spaces), [GitLab](https://gitlab.com/explore), [Codeberg](https://codeberg.org/explore/repos), [SourceHut](https://sourcehut.org), [arXiv](https://arxiv.org), [OpenReview](https://openreview.net). Import erfolgt nur nach echter Quell- und Lizenzprüfung. Ein Host-Wechsel ist kein Ersatz für Entwicklerkompetenz oder Testbelege.

## Lizenz-/Kompatibilitätsregel

Google Gallery, LocalChat, Jandal und Termux android-build-tools dokumentieren Apache-2.0; A-MEM und mini-SWE-agent MIT; sqlite-vec Dual-Lizenz MIT/Apache-2.0. **Diese Angaben sind Hinweise zu konkreten Upstream-Ständen**, kein Ersatz für Prüfung der exakten Commit-SHA, THIRD_PARTY_NOTICES, eingeschlossenen Modellgewichte, Transitivabhängigkeiten und Redistributionspflichten. Bei den übrigen Kandidaten wird die Lizenz vor Codeimport neu gelesen und dokumentiert. **In dieser PR wurden keine Fremdquellen kopiert, keine externen Pakete/Modellgewichte installiert und keine neue kostenpflichtige KI-API eingeführt.** Nur eigenständiger Kotlin-Code wurde geschrieben.

## Reihenfolge der verbindlichen Meilensteine

1. **M4a / EXP-00:** Fold7-Haupt-App-Komplettbuild ohne CI-Minuten tatsächlich prüfen; Signatur nur mit Original-P12. Unabhängig davon keine Modell-/Gedächtnispromotion.
2. **M6.1 / EXP-07:** lokal prüfbare, synthetische SWB1-/Hybrid-Memory-Benchmarks plus Abstinenz/Privacy. Erstes **RESEARCH_SHADOW**-Modul: `MemoryRetrievalResearchGate.kt`, reine Kennzahlen, keine Speicheränderung.
3. **M6.2 / EXP-06:** vorhandene `MemoryGraph`-Beziehungen und Retriever auf gezielte Hybrid-Ranking-Experimente erweitern, separat gegen eingefrorene TEST-Fälle vergleichen. Kein Import einer zweiten Memory-DB.
4. **M7 / EXP-02:** mini-SWE/Aider-Strategien nur im bestehenden CCC1-Repair-Journal und unabhängigen Verifier testen; kein neuer Scheduler oder Produktionspatch.
5. **M8 / EXP-11:** Skills als explizit zugelassene Aufrufpläne auf dem bestehenden ToolGate; verdächtige Manifest-/Tool-Kombination ablehnen; keine fremden JS-Skripte ausführen.
6. **M9 / EXP-09:** native ARM64 AIDL/AAPT2-Rezepte erst am Fold7 verifizieren, keine ungeprüfte Installation durch fremde Curl-Pipe-Shell-Skripte.

## Abnahmestufen

Jeder Eintrag wird erst nach nachvollziehbaren Commits und Tests von `AUDIT_REQUIRED` nach `RESEARCH_SHADOW`, `SOURCE_INTEGRATED`, `DEVICE_TOOLCHAIN_VERIFIED`, `REAL_APK_BUILT` und `END_TO_END_VERIFIED` hochgestuft. Diese Zustände dürfen nicht aus einem README, einem syntaktisch geprüften Patch oder einem vorigen Git-HEAD abgeleitet werden. **Alle Beiträge müssen quelloffen im offiziellen privaten Repository für berechtigte Entwickler erhalten bleiben. Keine Force-Pushes, Branchlöschungen, Quellcode-Obfuskation oder Merge nach `main` ohne gesonderte Besitzerfreigabe.**

## Handoff

Integrationsbasis: PR #197 `77e429f6d2f0eebfe697244a467f2b0565e7eb68`. M4-Arbeitszweig: `chatgpt/continuum-research-integration-memory-m4-20261027`. Beim Fortsetzen immer frischen HEAD prüfen. Falls der Kotlin/Gradle-Lauf nicht tatsächlich durchgeführt wurde, `NOT_RUN` berichten; die vorhandenen 1.094 JUnit-Passbelege gelten ausschließlich Sols älterem HEAD. Rollback: M4-Draft-PR unangetastet lassen oder im Testcheckout auf seinen vorherigen Commit wechseln, niemals produktive Daten/Branches zurücksetzen.

## Weiterführung 28.10.2026 – echter Gesprächspfad, weiterhin Shadow

[Draft-PR #203](https://github.com/Ullmann27/device-ai/pull/203) auf #198 führt den neuen `AuthorizedConversationMemoryShadow.kt` **nach** der autoritativen SWB1-/CRE1-Auswahl in `learning/ContinuumCognitiveRuntime.reply()` aus. Die Diagnose bekommt ausschließlich die für denselben Zug ausgewählten Belege und bereits berechnete CRE1-Scores; der Runtime-Adapter prüft zusätzlich die vollständige `ContinuumMemoryHit`-Objektidentität gegen die originalen Kandidaten des CRE1-Callbacks. Der bestehende `HybridMemoryRetriever` wertet diese Belege nur temporär als `DEVICE_LOCAL_ONLY` und `UNVERIFIED` aus. Es werden nur Zähler/Boolesche Indikatoren zurückgegeben, **keine neuen Belege in Prompt, Antwort, Log oder Memory-Store**.

Kotlin-Unit-/Runtime-Negativtests und die Verbindung zu `MemoryRetrievalResearchGate`/`MemoryResearchShadowComparator` liegen im PR. **JUnit/Android/Device: NOT_RUN** in dieser Sitzung; ausgeführte JS-Quellstrukturkontrollen sind kein Ersatz. Daher kein belegter Qualitätsgewinn und keine Aktivierung einer neuen produktiven Retrieval-Reihenfolge. Vollständige fünf-Kern-Zuordnung: `docs/research/omega/CONTINUUM-FUSION-FIVE-CORE-ARCHITECTURE-20261028.md`. Alle 15 externen Referenzen unten bleiben erhalten, ohne Quellcodeübernahme.
