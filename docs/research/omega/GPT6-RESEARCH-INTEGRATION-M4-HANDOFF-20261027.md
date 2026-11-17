# GPT-6 – M4 Forschungsintegration / saubere Übergabe – 27.10.2026

## Auftrag und Geltungsbereich

**Ausschließlich Device AI Continuum `Ullmann27/device-ai`**. Besitzer verlangt, geeignete Open-Source-/Forschungsprojekte (USA und internationale Codeplattformen) zentral aufzuschreiben und danach tatsächlich mit dem bestehenden lokalen Gehirn zu verknüpfen. Keine Vermischung mit anderen Apps/Projekten, kein neuer Gemma-Inferenzkern, keine externe kostenpflichtige Modell-API. **Keine stillschweigende Großintegration ungetesteter Fremdquellen.** Alle Projektcodes und Zweige bleiben für berechtigte Entwickler lesbar.

Arbeitsbasis ist PR #197 HEAD `77e429f6d2f0eebfe697244a467f2b0565e7eb68`; neuer Zweig `chatgpt/continuum-research-integration-memory-m4-20261027`, Draft-PR **#198**. Für den **aktuellen** HEAD stets `git rev-parse HEAD` beziehungsweise PR #198 lesen: diese Übergabedatei kann nicht ihre eigene abschließende Commit-SHA enthalten. Keine Änderungen an `main` oder Vorläufer-PR #197.

## Dokumentierte Entwicklungen

1. Dauerhafter Katalog mit **15 konkreten Upstream-Quell-/Projektverweisen**, offiziellen Repositories, Einordnung in bestehende Continuum-Komponenten, EXP-IDs, Lizenz-/ABI-Gates, Testfällen und Reihenfolge: `docs/research/CONTINUUM-EXTERNAL-CODE-INTEGRATION-20261027.md`.
2. Dauerhafte Eintrittspunkte aktualisiert: `AGENTS.md` (gilt für Astra/Sol und spätere Teamleiter), `README.md` (aktueller Hinweis statt veralteten Stands vom 23.10. als aktuell auszugeben), `docs/CONTINUUM-EXPERIMENTE.json` (maschinenlesbares `externalResearchIntegration`, nur Quellenanker, keine automatische Featurepromotion).
3. **Erster realer source-level Gehirn-Anschluss:** `app/src/main/java/at/heinz/deviceai/cognitive/MemoryRetrievalResearchGate.kt`: rein schreibgeschützter lokaler Benchmark ruft `HybridMemoryRetriever.retrieve` **direkt** auf. Bewertet fünf LongMemEval-V2-inspirierte Gedächtnisfähigkeiten, erwartete Treffer/Abstinenz, Top-1, Trefferquote innerhalb eines Limits, MRR und Laufzeit. Nur ausdrücklich freigegebene PrivacyClass; privat nicht erlaubte Goldbelege führen zu Ablehnung. Kein eigenes Gedächtnis, keine Faktenerzeugung, kein CRE1-Gewichtsupdate, keine Toolausführung. Maximal 64 Probes/2000 Records pro Lauf, 1..32 Treffer.
4. Kotlin-JUnit-Negativtests `MemoryRetrievalResearchGateTest.kt`: bekannte Tatsache, unbekannte Frage/Abstinenz, Falschtreffer, Privacy-Block, optionaler semantischer Scorer, widersprochene alte Belege, ungültige Fälle, keine Weitergabe vertraulicher Gedächtnisinhalte.
5. Node-Quell-/Vertragsprüfungen `scripts/continuum-external-research-contract.test.mjs`: 15 Upstreams, EXP-Verknüpfung, Core-Retriever statt duplizierter Memory-DB, Originalsignierer/Kotlin-/ToolGate-Sperren.
6. Arbeitsaufgaben angelegt: GitHub [#199](https://github.com/Ullmann27/device-ai/issues/199) Gedächtnis, [#200](https://github.com/Ullmann27/device-ai/issues/200) Skills, [#201](https://github.com/Ullmann27/device-ai/issues/201) CCC1-Entwickler, [#202](https://github.com/Ullmann27/device-ai/issues/202) ARM64-Build; Langzeitvermerk direkt bei Experimentanker [#41](https://github.com/Ullmann27/device-ai/issues/41#issuecomment-6453967018).

## Überprüfungen: exakte Wahrheitsabgrenzung

**Tatsächlich über GitHub zurückgelesen:** `AGENTS.md`, `README.md`, Experimentregister und neue Kotlin-/Node-/Forschungsdateien; bisheriger Prüfblock **12/12 strukturelle Quellvertrag-Assertions PASS**. Experimentregister nach Änderung erfolgreich als JSON geparst, **16 alte Experimente unverändert enthalten**, Forschungsquellen R-01..R-15 vorhanden. Originalowner-Builder führt weiterhin Originalzertifikat und P12-Gate. Noch keine importierten Fremd-Bibliotheken, Modelle, Daten oder Binärdateien.

**Nicht ausgeführt:** echter Gradle-`:app:testDebugUnitTest`-Lauf auf neuem M4-HEAD, Kotlin/JUnit-Testklasse auf Android-Testclasspath, tatsächliche Fold7-/Termux-ARM64-Kompilierung, native Gemma-4-E4B-Inferenz, Langzeitbenchmark mit echter Modellleistung, originalsignierte APK/Installation. Status `RESEARCH_SHADOW` / `SOURCE_INTEGRATED_WITH_TESTS_NOT_RUN`, **nicht** `END_TO_END_VERIFIED`. Frühere 1094 Android-JUnit-PASS von Sol gehören zu **einem anderen HEAD** und sind kein M4-Nachweis.

Reproduktionsbefehle im eigenen, bereits authentifizierten, isolierten Checkout (keine GitHub Actions/Render):

```sh
node --test scripts/continuum-external-research-contract.test.mjs
./gradlew :app:testDebugUnitTest --tests 'at.heinz.deviceai.cognitive.MemoryRetrievalResearchGateTest'
./gradlew :app:lintDebug :app:compileDebugKotlin
```

Hinweis: Auf vorbereitetem Fold7/Termux die dokumentierte JDK17/Gradle8.11.1/SDK36/AAPT2-Toolchain und deren bestehenden Flags verwenden; `./gradlew` auf Termux ist nicht der bestätigte native Buildpfad. Es wurde keine CI ausgelöst.

## Nächste Entwicklungsreihenfolge

- **Sofort:** Kotlin-Test und Node-Source-Gate tatsächlich ausführen und bei Fehlern ausschließlich den Forschungszweig reparieren; unabhängigen M4-Kotlin/Android-Compile-Holdout vor produktiver Nutzung.
- **Danach:** in #199 Hybridsuche/Belegverknüpfung auf eingefrorenen DEV/TEST-Probes testen. Nur bei nachgewiesenem Gewinn neue Retrieval-Strategie als separate Opt-in-/Shadow-Schicht. Bestehende `HybridMemoryRetriever`, `MemoryGraph`, ProofCarryingMemory, SWB1/CRE1 bleiben Wahrheit.
- **Danach:** #200 streng deklarative Skill-Metadaten prüfen; Ausführung **nur** im vorhandenen Besitzer-ToolGate. Keine ungeprüften JS-/Python-/Shell-Skills.
- **Danach:** #201 mini-SWE/Aider Ideen im bestehenden CCC1 unabhängigen Verifier; #202 Fold7-native AIDL-AAPT2 und echte Besitzer-APK.
- **Gesperrt:** automatische Modell-/Memory-Promotion, ungeprüfte externe Gewichtedownloads, Core-Replace, `main`-Merge, Branch-Löschung, Secret-Export, Re-Signing oder Deinstallation der installierten Continuum-Haupt-App.

**Rollback:** Den Draft-PR #198 ungemergt lassen; Kernquellen bleiben byte-identisch zum PR #197. Keine temporären Binärartefakte im Git-Repo und keine Veränderung des Besitzergeräts.
