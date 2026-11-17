# Continuum – Forschungs-Lern-Gate (Shadow, keine automatische Promotion)

Der kostenfreie Quellenworker aus PR #32 speichert **bibliografische Beobachtungen** mit
`WEB_OBSERVATION / authority=NONE`. Das sind noch **keine Trainingsbelege**:
ein DOI, ein Titel oder ein `[1]`-Verweis beweist keine Textaussage.

`.github/continuum/research-learning-cycle.mjs` ergänzt den vorhandenen CRE1
um ein isoliertes, budgetiertes Forschungsexperiment. Jeder von einem geprüften
Quellenabruf übergebene Datensatz muss die tatsächlichen Originaltextbytes
(`sourceText`), ihren SHA-256 (`sourceSha256`), einen **exakt enthaltenen**
Extrakt (`claim`), Forschungsfrage, öffentliche HTTPS-Herkunft und die
ausdrückliche Trainingsrechte-Prüfung (`trainingPermitted: true`) enthalten.
Diese boolesche Kennzeichnung ersetzt keine unabhängige Lizenz- oder
Herkunftsprüfung. Das Modul ruft Quellen nicht selbst ab.

Die Aufteilung in 75 % Training und 25 % Audit erfolgt anhand der letzten
Hexziffer des **Quelldokumenthashs**; mehrere Aussagen derselben Quelle
dürfen nicht zwischen Training und Audit verstreut werden. Drei unabhängige
Quellen je Split sind das Minimum. Die ursprüngliche CRE1-Einbettungsmatrix
wird nicht verändert. Lediglich die beiden vorhandenen 32×32-Projektionsmatrizen
werden als **Projektionstraining, nicht als LoRA oder QLoRA**, experimentell
angepasst. Kandidaten werden bei Regression von Audit-Top-1, MRR oder
Audit-Verlust verworfen; die produktiven Android-SWB1-Gewichte bleiben unangetastet.

Bei positivem Shadow-Benchmark kann ein Forschungsmodell mit einem **separat
verwalteten ECDSA-P-256-Schlüssel** signiert werden. Manifest und Audit sind
bytegenau über SHA-256 gebunden. `ContinuumSignedCre1Package.kt` überprüft
auf Android Signatur, gepinnten Public Key, Modell- und Audit-Hashes, P-256-Kurve
und das unabhängig geöffnete CRE1-Modell. **Das ist kein neuer produktiver
Modellimport:** Der bestehende `ContinuumPublicModelPackage`-Importer erwartet
SWB1 und wird absichtlich nicht auf CRE1 umgebogen. Ein Signaturnachweis ist
auch kein Beleg für tatsächlichen Lernfortschritt.

Fokussierte Prüfbefehle bei einem vollständigen Repository-Checkout:

```bash
node --check .github/continuum/research-learning-cycle.mjs
node --test .github/continuum/research-learning-cycle.test.mjs
gradle :app:testDebugUnitTest --tests at.heinz.deviceai.learning.ContinuumSignedCre1PackageTest
```

Ein grüner Shadow-Test ist **nicht** gleichbedeutend mit erfolgreicher
Geräteinstallation, lokalen LiteRT-LM-Modellgewichten oder einem immer laufenden
PC-Worker. Der PC/NAS muss separat eingeschaltet und mit
persistenter Datenhaltung gestartet werden. Vor jeder produktiven
Modellpromotion fehlen weiterhin ein unabhängig verifizierter Quellenabruf,
ein gepinnter Geräteschlüssel, vollständige Holdouts und echte
Modellqualitätsmessungen.

## PR #34: Themennahe Negativbeispiele und echter Shadow-Dauerworker

Die Pre-Pooling-Optimierung verwendet die einmalig berechneten
`model.pool(features(text))`-Vektoren **tatsächlich im Gradientenpfad**;
es wird nicht mehr pro Epoche erneut tokenisiert. Ein Gram-Isometrie-Term
und der bestehende Projektionsanker begrenzen ungewollte Veränderungen
der beiden 32×32-Linsen. Die Rank-1-Anisotropie-Korrektur ist optional
(`anisotropyBeta=0` als Standard) und benötigt vor der Freigabe
einen unabhängigen gemessenen Nutzen. Aussagen wie „100× schneller“
oder „40 % genauere Antworten“ sind **nicht gemessen**.

Für schwierige Negativkandidaten bevorzugt der Trainer andere Aussagen
aus dem gleichen Originaldokument, **sofern die Frage den gemeinten Satz
lexikalisch eindeutig von seinem Geschwister unterscheidet**. Identische,
unspezifische Dokumentfragen werden sonst zu widersprüchlichen
Trainingslabels und dürfen nicht als sichere Sibling-Negatives gelten.
Cross-Document-Negatives bleiben dokumentenweise im jeweiligen Split.

Das zurückgehaltene Audit erhält zusätzlich Zahlen-/Negations-Canaries.
Canaries werden **nie** für Gradienten verwendet. Ein einziges falsch
rangiertes Canary verhindert die Übernahme des Kandidaten in den
isolierten CRE1-Forschungsspeicher. Das ersetzt nicht die unabhängig
qualifizierte `Cre1PromotionGate`-Prüfung mit mindestens 200 Stichproben,
50 Negativfällen und nachgewiesenem Hybrid-Vorteil. Eine
`cre1-promotion.properties` wird nicht automatisch erzeugt.

Der Backend-Worker kann über folgende explizite Einstellung zusätzlich
nach **erfolgreichen** Quellenzyklen den Shadow-Worker ausführen:

```bash
CONTINUUM_SHADOW_LEARNING_ENABLED=true npm --prefix backend run worker
```

Er nimmt keine bibliografischen Titel und keine erfundenen Textbelege als
Training. Er wartet auf eine lokal kuratierte Datei
`<Verzeichnis von CONTINUUM_STATE_PATH>/cre1-research/approved-extractive-spans.json`
mit überprüften `query`, `claim`, `sourceText`, `sourceSha256`, `url`,
`trainingPermitted`. Die tatsächliche Herausgeber-/Lizenzprüfung erfolgt
vorgelagert; allein `trainingPermitted:true` bestätigt **keine** Lizenz.
Ohne verifizierte Originaldokumente lautet der Status
`AWAITING_VERIFIED_ORIGINAL_DOCUMENTS` und es findet kein Training statt.

Veröffentlichte Forschungskandidaten liegen ausschließlich unter
`cre1-research/cre1-research-v1.cre1`, mit synchronisierten atomaren
Writes, `.lkg`-Backup und einem ausführlichen Shadow-Audit. Ein
konkurrierender Prozess oder ungeklärtes Lock wird nicht automatisch
übernommen. Android-SWB1, `main`, persönliche Fakten und die produktive
CRE1-Promotion werden nicht verändert.

### Gezielt zu prüfende Kommandos

```bash
node --test .github/continuum/cre1-blacklab-optimizer.test.mjs \
  .github/continuum/research-shadow-worker.test.mjs \
  .github/continuum/research-learning-cycle.test.mjs
node --test .github/continuum/*.test.mjs
npm --prefix backend test
gradle :app:testDebugUnitTest
```

Die Implementierung ersetzt **keinen Live-Nachweis** tatsächlicher
Research-Volltextbeschaffung, trainierter Gewichte, Android-Promotion
oder eines 24/7 eingeschalteten Rechners.
