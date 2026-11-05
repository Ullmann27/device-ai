# MEMORY R7 — atomare Generationen für M2.9-Retriever

Stand 05.11.2026. **Fachspezialist: JVM-HOST-GREEN / Projekt-Android NOT_RUN / C1-INTEGRATION PENDING.** Keine weiteren Bots oder Hintergrundprozesse erzeugt.

## Quelle, Zuständigkeit und Testfreeze

Start direkt ab R6/#532 `5073cdc0ee96687d3b17cfefa8710174c8514b96`; Ausgangsproduktionsblob `b2d6ac9b078557f8c74fe0be9dc0032a15e52323`. C1/#519 zuletzt gelesen `13c37f55789bf67b07beeb37cc1a5035526e623e`, zentrales Board `docs/research/omega/ASTRA-NUCLEUS-HANDOFF-20261104.md`. Der R6-Commit ist **noch nicht** in C1 integriert. C1/#519 ist einziger Einbauer gemeinsamer Activity-, Factory-, Sprach- und Worker-Einstiege. Andere PRs #522, #529, #531 und ihre Eigentümer bleiben unangetastet.

Eigener Branch `fix/continuum-memory-index-atomic-r7-20261105`. Zwei Android-JUnit-Regressionsmethoden wurden zuerst auf der ungeänderten R6-Produktionsquelle mit Commit `bddbe018efe52ddb0f71341768c39e52428dbc51` eingefroren. Erst danach wurde die *eine* Produktionsdatei `app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt` in `8905679e5e396e8ee560d8ce11d7d804365b31ba` geändert, Blob `a0849ac148164fb94e7f0725182ce831546519d4`. Eingefrorene Android-Testquelle Blob `76c793321a6476dbaa9e28eaa10248f65d526fdb`.

## Fehlergruppe und zwei unabhängige Reparaturen

**BM25:** `index()` veröffentlichte zunächst die neue Dokumentliste, löschte danach die globalen Postings und änderte `lengths`, `avg` und `vocab` nacheinander. Ein paralleler `search()` konnte deshalb eine neue `id`/Bestätigungsangabe mit alten Scores verknüpfen, partielle Postings lesen oder bei gleichzeitiger Mutation scheitern.

**3–5-Gramm:** `index()` tauschte `docs`, `idf` und `vectors` nacheinander aus. `scoredParts()` konnte das neue Dokument mit einem Score aus der alten Generation darstellen. Das spätere R6-Callerlisten-Snapshot schützte nicht vor diesem internen Race.

Beide Varianten bauen jetzt jeweils eine vollständige interne `IndexState`-Generation in lokalen, nicht nachträglich veränderten Arrays/Maps auf und veröffentlichen sie mit genau einer `@Volatile`-Referenzzuweisung. `search()` und `scoredParts()` lesen pro Aufruf genau eine Generation. `approxIndexBytes()` stammt ebenfalls aus einem abgeschlossenen Indexzustand. Das erhält R6s geklonte Caller-Liste, die API, Scoreformel, TemporalCue und `confirmed` unverändert. Schreibreihenfolge **zweier paralleler index()-Aufrufe** und das Kopieren einer währenddessen vom Caller mutierten Liste sind damit nicht gesondert garantiert.

## Reale Tests und reproduzierbare Ausführung

Das vollständig aus GitHub gelesene Kotlin-Produktionsfile wurde als lokaler Git-Blob geprüft: R6 `b2d6ac9...`, R7 `a0849ac...`; kein umgeschriebener Retriever-Methodenrumpf. Die importierten R7- und R6-Android-Testmethoden wurden auf JRE21/kotlinc-jvm1.9 kompiliert, mit **einem Host-Stellvertreter ausschließlich für EventEmittingRetriever und der JUnit4-API**, nicht mit Android/Robolectric.

- Vor Fix: **4/6 PASS, 2 FAIL, Exit1**; in einer Ausführung 1.405 fehlerhafte/partielle BM25- und 10.221 N-Gramm-Beobachtungen.
- Nach Fix: **6/6 PASS, Exit0**, einschließlich vier unveränderter R6-Regressionen.
- Zusätzliche getrennte Parität: **144/144 Hit-/Score-Vergleiche identisch** bei 48 Abfragen gegen R6, zwei 40-Dokumente-Korpora, inkl. Zeitcue, plus identische Indexgrößen; Kotlin/JVM Exit0.

Originale Ausgaben `docs/evidence/memory-index-atomic-r7-20261105/{host-red,host-green,parity}.txt`. Reproduktion auf einem Git-Checkout dieses Zweigs mit installiertem Kotlin/JDK:

```bash
bash tools/memory-index-atomic-r7/run.sh
git show 5073cdc0ee96687d3b17cfefa8710174c8514b96:app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt > /tmp/memory-r6-source.kt
bash tools/memory-index-atomic-r7/run.sh /tmp/memory-r6-source.kt b2d6ac9b078557f8c74fe0be9dc0032a15e52323
bash tools/memory-index-atomic-r7/run_parity.sh
```

Grenze: Die JVM-Tests haben echte original gefrorene Methoden, aber nicht das vollständige Android-Gradle-Projekt ausgeführt. Unter diesem Lauf ist keine SDK-/Robolectric-/NewApi- oder echte M2.9-1.000-Dokumente-/44-Fragen-Abnahme erfolgt. Die 42/44 Recall@3 ist historische C1-Evidenz; kein neues Retrievalqualitätsversprechen. Die Parität misst keine Android-Performance.

## Übergabe an C1, Rollback und Sicherheitsgrenzen

C1 sollte R6 **zuerst unabhängig** auf seinem unveränderten gemeinsamen Retriever-Basisblob prüfen, danach R7 punktuell in denselben Produktpfad übernehmen. Vier R6- und zwei R7-Android-JUnit-Fälle sowie alle bestehenden Temporal-/Retriever-/1.000-Dokumente-Verträge laufen auf dem *exakt identifizierten finalen gemeinsamen* Source-Tree unter den beiden bestehenden Gradlelocks. Bei FAIL keine Annahme, negative Evidenz behalten. Vor Main/Release weiter vollständiges Node/Backend/Android/UI/NewApi und Fold7 erforderlich.

Rollback nur diesen einzelnen Retriever-Produktionshunk, R6/Testfreeze und Rohlogs erhalten. Kein Main-Merge, GitHub Actions, bezahlter Dienst, APK, Installation, Signatur-/Versionswechsel oder Modellaustausch. Ownerdaten und Offline-/ToolGate-/CCC1-Grenzen unverändert.
