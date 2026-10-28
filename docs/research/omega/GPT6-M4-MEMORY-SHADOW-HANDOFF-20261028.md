# CONTINUUM M4 – Gedächtnis-Shadow-Weiterarbeit (28.10.2026)

## Projekt / Zweig / Status

Nur `Ullmann27/device-ai`. Fortsetzung von Draft-PR #198 (gestapelt auf #197 → #196). Arbeitszweig: `chatgpt/continuum-research-integration-memory-m4-20261027`. PR bleibt Draft, `main` unangetastet. Betroffene Experimente: EXP-04, EXP-06, EXP-07; Folgeauftrag #199.

Der Stand nach der Umsetzung ist **RESEARCH_SHADOW / SOURCE_ONLY; Android Gradle und Fold7 NOT_RUN**. Die vollständige aktuelle HEAD-SHA muss aus GitHub/PR #198 frisch abgefragt werden, da dieses Dokument seine eigene Commit-SHA nicht enthalten kann.

## Tatsächlich implementiert

1. `MemoryRetrievalResearchGate.kt`: doppelte `MemoryRecord.id` in einem Benchmark-Korpus werden nun vor dem Ranking verworfen. Identitätsmehrdeutigkeit darf nicht als Treffer oder Evidenz gelten.
2. `MemoryRetrievalResearchGateTest.kt`: reproduzierbarer Negativfall mit gleicher ID und widersprüchlichem Inhalt.
3. `MemoryResearchShadowComparator.kt`: zwei Entwicklungs-/Holdout-Splits, dieselbe bestehende `HybridMemoryRetriever`-Benchmarkbasis, zwei semantische Bewertungen (Baseline ohne Scorer, Kandidat mit lokalem Scorer). Jeder der fünf `MemoryResearchAbility`-Werte muss im eingefrorenen Holdout vertreten sein. Doppelte Probe-IDs und gleiche normalisierte DEV-/TEST-Fragen werden verworfen. Alle erwarteten Gold-Memories müssen vorhanden, privacy-zugelassen und `VERIFIED` sein und dürfen nicht auf `MODEL_GENERATED`, `INFERENCE` oder `UNVERIFIED` beruhen.
4. Der Vergleich prüft **jede Probe einzeln** auf Nichtverschlechterung von Top-1/Abstinenz, Hit@K und Reciprocal Rank. Erst eine positive Verbesserung auf DEV **und** Holdout ohne Einzelregression führt zu `eligibleForOwnerReview=true`. Das ist **nur ein Vorschlag zur Prüfung**, keine Aktivierung, keine echte Nutzerzustimmung und kein Qualitätsnachweis auf Gerät.
5. `MemoryResearchShadowComparatorTest.kt`: fünf Kategorien, semantischer positiver Fall, Überretrieval/Abstinenz-Rückschritt, Datensatzüberschneidung, private/widersprochene Goldbelege, unvollständiger Holdout und doppelte IDs.

**Unverändert:** SWB1/CRE1/CDX1, Gemma4/LiteRT-LM, `HybridMemoryRetriever.kt`, MemoryStore/Graph/ProofCarryingMemory, Owner-ToolGate, Signer, Version/Package, CCC1-Reparaturpipeline. Keine externe Runtime, kein Fremdcode, keine Cloud-API oder Modellgewichte übernommen. Der Callback muss ausschließlich vertrauenswürdiger lokaler Code sein: Ein beliebiger Scorer bekäme Query und Memory und könnte sonst Daten exportieren. Das API allein erzwingt diese Vertrauensannahme nicht.

## Wirklich ausgeführte Kontrollen

- GitHub-Connector: neue/geänderte vier Kotlin-Dateien aus PR-Zweig zurückgelesen; 17/17 **strukturorientierte** JS/Quellchecks PASS. Dies sind keine ausgeführten Kotlin-JUnit-Fälle.
- Isolierter Kotlin-Syntax-/Typcheck mit `kotlinc-jvm 1.9.0`, `java 21.0.12` und passenden minimalen Ersatztypen für die im privaten Checkout fehlenden Continuum-Domänenklassen: `kotlinc` **Exit 0** für beide tatsächlichen, byteidentischen Produktionsdateien **`MemoryRetrievalResearchGate.kt`** (`git hash-object` = `bc4aa5dbb38417cd7bb885a930ef4f644dde2f8e`) und **`MemoryResearchShadowComparator.kt`** (`git hash-object` = `960bc7fe349af4743e212703bcf4284ffd565902`). Beide Git-Blob-Hashes entsprechen den auf dem PR-Zweig zurückgelesenen GitHub-Datei-SHAs. Warnungen betreffen nur unbenutzte Parameter der temporären Retriever-Ersatzimplementierung. **Der echte Retriever, die vollständige JUnit-Klasse und Android-Gradle wurden damit nicht ausgeführt.**
- **Nicht ausgeführt / nicht beansprucht:** echtes Android-Gradle-Compile auf vollständigem Source, JUnit-Runtime auf dem Android-Testclasspath, Gesamtsuite/Lint, verlässlicher echter Fold7-ARM64-Build, Modellinferenz, unabhängiger Gold-Holdout nach Candidate-Tuning, APK, Eigentümersignatur oder Installation. JDK 21 im temporären Syntaxcheck ist nicht die vorgesehene JDK17-Android-Buildkette.

## Nächste zwingende Abnahme

Im authentifizierten und isolierten vollständigen Continuum-Checkout auf JDK17/SDK36/Gradle8.11.1 zuerst exakt den aktuellen PR-HEAD auslesen. Danach:

```sh
./gradlew :app:testDebugUnitTest --tests 'at.heinz.deviceai.cognitive.MemoryRetrievalResearchGateTest' --tests 'at.heinz.deviceai.cognitive.MemoryResearchShadowComparatorTest'
./gradlew :app:compileDebugKotlin :app:lintDebug
node --test scripts/continuum-external-research-contract.test.mjs
```

Auf dem Fold7 nur die bereits dokumentierte native Termux-AAPT2/AIDL-Toolchain mit den tatsächlichen bestehenden Flags verwenden; `./gradlew` allein behauptet keine ARM64-Kompatibilität. Defekte nur im isolierten Forschungszweig korrigieren. Danach zusätzliche unabhängige Negations-/ID-Verwechslungs-/Revocation-Probes auf eingefrorenen Daten durchführen, tatsächliche Messwerte und Datensatzversionen sichern, erst dann ggf. einen **nichtproduktiven** semantischen Kandidaten vergleichen. Eine wirkliche produktive Aktivierung erfordert separate Owner-Genehmigung und neue Build-/Gerätebelege.

**Rollback:** PR #198 offen und als Draft belassen, Änderungen jederzeit durch Review/gezielten Revert im Forschungszweig entfernen. Niemals Main-Reset, Force-Push, Branch-Löschen, automatische Memory-Promotion, private Schlüsselweitergabe oder APK-Installation.
