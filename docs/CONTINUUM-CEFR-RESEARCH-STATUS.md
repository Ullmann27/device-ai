# CEFR und kognitive Android-Brücke: Forschungs- und Übergabestand

Stand: 18. Oktober 2026. Entwicklungsbasis: PR #28, Ausgangs-HEAD `c1249ae2e5efaaf9fe4fc55b0c02d0c5a474a41e`.

## Was tatsächlich implementiert ist

**CEFR – isolierter Entwickler-Lernkern:**
- `.github/continuum/cefr-repair-ranker.mjs` implementiert lexikografische Risiko-Stufen, ein unverändertes Produktionsranking samt separatem CRE1-Schattenscore, verpflichtende Holdout-Daten mit mindestens vier Fällen, expliziten positiven Kandidatenindizes und getrennter Trainings-Query.
- `trainCefrCandidateFromVerifiedReport()` verlangt zunächst `validateVerifiedReport(report, patchText)` **und** einen vom vertrauenswürdigen lokalen Worker zur Laufzeit ausgeführten `verifyIndependent(report, patchText)`-Callback. Er muss einen erfolgreichen zweiten Testbeleg für exakt die Profile aus `CHAINS[report.chain]`, den Basis-Commit, die Kette und den Patch-Hash liefern. Ohne Callback oder bei ungültigen Nachweisen findet **keine Promotion** statt.
- Trainiert wird ausschließlich auf `RelationalEncoder.restore(baselineBytes)`, niemals auf der bestehenden aktiven Instanz. Nicht bestandene Holdouts liefern die Ausgangsbytes unverändert zurück.
- `.github/continuum/cefr-dev-checkpoint.mjs` verwaltet den eigenen `cre1-dev-repair-v1.cre1`-Checkpoint **außerhalb** des Git-Checkouts, getrennt von Androids `cre1-research-v1.cre1`. Ein Lock, SHA-Vergleich, atomare Umbenennung, Symlink-Abweisung und Integritätsprüfung schützen das lokale Store-Verfahren.
- `repair-critic.mjs` schützt die `cefr-*`-Kontrollmodule vor automatisierten Codeänderungen.
- Testmodule: `cefr-repair-ranker.test.mjs`, `cefr-dev-checkpoint.test.mjs` und erweiterter `cegis-critic-safety.test.mjs`.

**Android – kognitive Roh-Beleg-Brücke:**
- `ContinuumCognitiveTurn.evidenceHits` enthält jetzt die bereits im einen `replyWithEvidence()`-Aufruf ausgewählten autorisierten `ContinuumMemoryHit`-Objekte.
- `cognitive/CognitiveDecisionBridge.kt` verwendet diesen unveränderlichen Turn und führt **keinen zweiten Dialog-/Gedächtnisaufruf** aus. Es erzeugt `ReasoningPassRequest(taskId=...)`, verwirft jeden Tool-Vorschlagsversuch im Denk-Kanal und fällt bei Offline/Timeout/fehlerhaften Zahlen oder fehlenden Originalankern auf `turn.answer` zurück.
- `CognitiveDecisionBridgeTest.kt` enthält fünf Regressionen zu Belegtext, doppelten Dialogturns, falschen Zahlen und Orten, Werkzeugversuchen, Offline-Fallback und fehlenden eigenen Fakten.

## Sicherheits- und Erkenntnisgrenzen

1. `validateVerifiedReport()` **prüft nur einen übergebenen Bericht und Patch**; es führt selbst keine unabhängigen Tests aus. Der vertrauenswürdige Node-Worker muss den Patch in einem **zweiten Worktree** tatsächlich prüfen und erst dann einen Callback ausführen. Ein willkürlich von fremden Inputs gelieferter Callback ist **keine** Beweiskette. Der bisherige `backlog-repair-worker.mjs` besitzt bereits `independent-check`, wird aber von CEFR bislang **nicht** aufgerufen.
2. `report.outcome === 'VERIFIED'` allein ist keine Lernfreigabe. Ebenso wenig zählen abgewiesene Kritiker-Entscheidungen oder bloße Syntaxfehler als ausgeführte negative Testbeispiele. CEFR verwendet momentan nur `FAILED_TESTS` derselben Diagnose mit vorhandenen Testergebnissen als Hard Negatives.
3. Der Holdout-Prüfkorpus muss vor dem Training mit unabhängig geprüften positiven Labels erstellt, versioniert und eingefroren werden. Die enthaltenen vier Testfall-Figuren dienen der Vertragsprüfung und **beweisen keinen signifikanten Lerneffekt** auf unbekannten Reparaturen.
4. `RelationalEncoder.save()` enthält Gewichte und Steps, **nicht den Adam-Momentzustand**. Kandidaten-Training über Checkpoints beginnt deshalb mit frischem Optimierer; es verändert keinen vorherigen Optimierer, beweist aber keine Optimizer-Kontinuität zwischen Sessions.
5. `CognitiveDecisionBridge.preservesEvidenceAnchors()` prüft nur einen konservativen **lexikalischen** Schutz: Zahlen, Negationspräsenz und großgeschriebene Begriffe des ersten Rohbelegs. Dies ist kein allgemeiner semantischer Wahrheitsbeweis. Das Modul ist noch **nicht an `AgentActivity.kt` angeschlossen**.
6. Eine SHA-256-Prüfsumme beweist bytegleiche Inhalte, **nicht**, dass Tests tatsächlich ausgeführt wurden, wer sie erstellt hat oder dass generierter Text wahr ist.

## Teststand

Die neue JavaScript-Quell- und Testsyntax wurde anhand der aktuellen GitHub-Dateien geprüft. **Die komplette CEFR-Node-Testsuite, Gradle-/Kotlin-Tests und Android-APK-Abnahme wurden in dieser Umgebung noch nicht ausgeführt und dürfen nicht als grün gemeldet werden.** Der letzte GitHub-Actions-Lauf konnte die komplette Prüfkette nicht als grün belegen.

Gezielter lokaler Abnahmebefehl im vollständig verfügbaren Repository:

```bash
node --test .github/continuum/cefr-repair-ranker.test.mjs \
  .github/continuum/cefr-dev-checkpoint.test.mjs \
  .github/continuum/cegis-critic-safety.test.mjs
node --test .github/continuum/*.test.mjs
gradle :app:testDebugUnitTest --console=plain -Pkotlin.incremental=false
node .github/continuum/m4-local-gate.mjs --root . --android
```

Die letzten beiden Befehle brauchen den vollständig eingerichteten lokalen Android-Buildrechner.

## Nächste drei realen Meilensteine

1. **CEFR-Isolation beweisen:** Die neuen Node-Tests und eine größere Regression ausführen; Sicherheitsnegativfälle zu ungültiger unabhängiger Verifikation, Holdout-Leakage und symlinkbasierten Checkpoint-Angriffen grün halten.
2. **Lernen wirklich anbinden:** Einen unabhängigen Reparatur-Benchmark mit ausgeführtem zweiten Worktree nachweisen, daraus ein unverfälschbares Trainingsereignis herstellen und CEFR zuerst ausschließlich in der Schattenbeobachtung auswerten. Benchmark: echte unbekannte Aufgaben, Vergleich gegen das bestehende deterministische Ranking.
3. **Android-Brücke abnehmen und aktiv schalten:** Kotlin-Unit-Tests, relevante Agent-Runtime-Tests, vollständigen APK-Build sowie einen echten auf dem Gerät geladenen LiteRT-LM-Checkpoint verifizieren. Erst danach eine getrennte, sicherheitstechnisch kontrollierte Chat-Routing-Integration durchführen.

**CEGIS-Originalcode von Sol** (`d0ff6e97857f40ea584af67cdcc65accd89fe852`) blieb bei der letzten GitHub-Abfrage weiterhin nicht erreichbar; die Implementierung wird nicht fingiert.

`main` und PR #19 bleiben unberührt. PR #28 bleibt Draft bis zur realen Integration und Abnahme.
