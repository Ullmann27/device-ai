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
