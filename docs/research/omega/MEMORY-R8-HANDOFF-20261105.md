# MEMORY R8 — Verneinungs-Evidenz und sichere Top-k-Grenzen

**Stand:** 05.11.2026. **Status:** `PRODUCTION_PATCH_PUBLISHED / SOURCE_GATES_GREEN / KOTLIN_AND_ANDROID_NOT_RUN`. Keine Sub-Bots oder parallelen Builds.

## Quellen, Zuständigkeit und Freigabegrenzen

C1 [#519](https://github.com/Ullmann27/device-ai/pull/519) wurde aktuell bei `13c37f55789bf67b07beeb37cc1a5035526e623e` mit zentralem `docs/research/omega/ASTRA-NUCLEUS-HANDOFF-20261104.md` gelesen. C1 hat die beiden abgegrenzten #522-Compiler-/Mapper-Fixes bereits in seinem eigenen gemeinsamen Kandidaten geprüft und eingebaut; R6 [#532] und R7 [#533] sind dagegen eigene, **nicht** übernommene Fachzweige. Das jüngste R7-Handoff und JSON wurden vor der Arbeit gelesen.

Dieser R8-Zweig ist **auf dem R7-Commit `a1e0ceca7fbbbc378e508f16868bcac2f676a222` gestapelt**; er darf nicht direkt auf den veralteten C1-Head gemergt werden. C1 allein integriert die gemeinsam benutzte Activity, Factory, Sprache und Worker. Keine Änderungen an diesen Pfaden, am Datenschutzcompiler, am Mapper, an Gewichten, Besitzerdateien, Berechtigungen, Offline-, Paket-, Signatur- oder Update-Konfiguration.

## Reproduzierbare Fehlerhypothesen und Testfreeze

Einzige geänderte Produktionsdatei: `app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryRetriever.kt`.

1. `GermanText.STOP` entfernte **`nicht`** aus *Dokumenten und Abfragen*, sodass die ausdrücklich verneinte Erinnerung bei identischem Resttext über BM25 sowie 3–5-Gramm **nicht als Unterschied repräsentiert** werden konnte. Die neue JUnit-Methode prüft sowohl die Suchreihenfolge als auch das unverändert weitergegebene unbestätigte `confirmed=false`. Ein alleinstehendes `nicht` muss ebenfalls suchbar bleiben.
2. `Bm25GermanRetriever.search` verwendete `take(k)` ohne die Grenze `k<0` abzuweisen; `CharNgramHashRetriever.scoredParts` hatte dieselbe Grenze. Eine nichtpositive angeforderte Trefferzahl darf kein Exceptionpfad sein. `search` der N-Gramm-Variante verwendet genau den `scoredParts`-Pfad.

**Vor dem Produktionsfix eingefroren:** `MemoryRetrieverPolarityAndLimitR8Test.kt`, Commit `22d58502c367be4cce6709a762df9d102c421697`, Blob `a2c62141b9b1c2d9a4557e983c1433a1ac28438c`, fünf Android/JUnit-Methoden, später unverändert.

**Vorherige Quellenprüfung:** Commit `22d5850` / Produktionsblob `a0849ac148164fb94e7f0725182ce831546519d4`: drei konkrete **statische** Sollprädikate `0 PASS / 3 FAIL`. Das ist kein ausgeführter Android-/JVM-Test, sondern eine tatsächliche Prüfung des veröffentlichten Produktionsquelltextes; JSON unter `docs/evidence/memory-r8-negation-20261105/source-red.json`.

## Produktionsänderung, genaue Grenze und Prüfergebnisse

Produktionsfix `48bd147f30a68cd7c6c902cd9a7d42cb0b1c29ad`, Ausgangsblob `a0849ac...` → neuer Blob `b0200e5246130d056ce02eb570f69dbcfa641ae5`. **Genau drei Quelledits**: `nicht` aus der STOP-Liste entfernt, frühe `k<=0`-Rückgabe in BM25-`search` und frühe `k<=0`-Rückgabe in N-Gramm-`scoredParts`. Die R6-Snapshotbindung, beide R7-atomaren Indexgenerationen, Scoringformeln, TemporalCue und Retrieval-ID-/Bestätigungsübertragung bleiben textuell unverändert. Eine neue Suchqualität wird daraus **nicht** behauptet.

**Quellenprüfung nach Fix:** 7/7 statische Sollprädikate PASS (darunter zwei R6-Snapshots, zwei R7-Volatile-Generationen, unveränderte Test-SHA und ursprüngliche Scoreformeln); `source-green.json`. Der effektive Produktions-Git-Diff wurde über den GitHub-Commit erneut gelesen. Keine Laufzeitmessungen.

**Kotlin-Hosttest vorbereitet, aber hier nicht ausgeführt:**

```bash
bash tools/memory-r8-negation/run.sh
```

Das Skript prüft zwingend den Quellenblob `b0200e5...` (sonst Exit76), kompiliert die **vollständige** produktive Retrieverdatei und führt die elf gefrorenen R6-/R7-/R8-Projekt-JUnit-Methoden mit dem klar abgegrenzten bisherigen Host-JUnit-/EventEmitting-Stub aus. Dies ist keine Android-/Gradle-Abnahme; auch dieses Hostkommando bleibt in dieser Sitzung **NOT_RUN**. Nötige Abhängigkeiten sind im gestapelten R7-Branch enthalten.

**Volles Android-JUnit, Gradle/NewApi, offizieller 1.000-Dokumente-/44-Fragen-Holdout, reale Modellinferenz und Fold7:** **NOT_RUN**. Insbesondere kann das bewusste Erhalten von Verneinungen Rangfolgen verschieben: vor Annahme muss C1 den bestehenden Holdout erneut messen und bei Regression verwerfen oder gezielt weiter untersuchen. R6 und R7 zuvor seriell unabhängig einbauen und testen.

## Rücknahme und nächste Schritte

Nur die drei Quelledits aus `48bd147` zurücknehmen; gefrorene Tests, R6/R7, negative Belege und übrige C1-Arbeit erhalten. C1: nach serieller R6/R7-Aufnahme erst den R8-Fokus samt zwölf? **Nein: elf** R6/R7/R8-Hostmethoden, dann die fünf echten Projekt-JUnit-Methoden mit den vorhandenen Temporal-/Retrieval-/Holdout-Suites ausführen, gemeinsame Node-/Backend-/Android-/UI-/NewApi-Gates und danach Gerät. Keine APK-Signierung, Veröffentlichung, Installation, Actions, Bezahlrunner oder Main-Merge.
