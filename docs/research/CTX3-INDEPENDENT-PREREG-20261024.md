# CTX3 / M5 – unabhängige Nachprüfung nach geöffnetem TEST2

Vor der einmaligen finalen Ausführung festgelegt. Keine neuen Modellgewichte und keine privaten Daten.

## Hypothese und Systeme
CTX3s explizite Kandidatenmengen und Quellenbindung reduzieren falsche Entitätsbelege gegenüber B0,
ohne richtige bekannte Antworten zu verlieren. B0=unveränderter Gedächtnisdialog; B1=CTX1 default unverändert;
B2=generalized=true mit separat dokumentiertem Negationsfix; CTX3=neuer optionaler Shadow-Gate plus dieselbe
statische Retrievalfunktion. B0/B1/B2 führen vorhandene Dialoghistorie; CTX3 läuft wie der echte Shadow ohne
zusätzliche Historydatei. Unterschiede sind Teil des Systems, kein isolierter Beweis einer einzelnen Regel.

## Trennung
DEV: eigene Unit-/Negativtests, Reviewer-Gegenbeispiele und bereits geöffneter TEST2; keine Gewichtsoptimierung.
CTX1/CTX2 Originalberichte bleiben unverändert. TEST2 wird ausdrücklich nicht frisch blind genannt.
Neuer TEST3 wurde von einem frischen unabhängigen Autor ohne Kenntnis der Implementierung/alten Korpusvorlagen
verfasst: 24 Dialoge,198 Züge,42923 Bytes; sechs Folgen mindestens zehn Züge, alle mindestens fünf.
SHA256: e5681030efe4f83a471cc2e91dae7bf3ed577f889b0fc0aabd48cf39bfd39077.
Der Implementierer hat bis zum Freeze weder Inhalte noch Ergebnisse gelesen. Datei ist unverschlüsselt im Repo,
keine Zugangsblockade; Schutz ist Disziplin. Keine weitere Kandidatenanpassung nach erster finaler Ausführung.

## Metriken und vorab festgelegte Entscheidung
Erste Quellenzuordnung richtig/falsch, ausgelassene bekannte Information, nötige/unnötige Rückfrage,
Enthaltung und CRASH je Turn; zusätzliche Quellen außerhalb Gold separat. Quellen-ID-Präzision wird zusätzlich
in den Unit-/Withdraw-Tests geprüft. Alle Baseline-Abstürze werden gezählt, keine Fälle entfernt.
Latenz Median/p95 inkl. jeweiligem Retrieval, maximale explizite Zustandseinträge/Historydatei;
beobachteter Heap-Peak ist ganze JVM, keine präzise RAM-Allokation pro Arm und kein Telefonbenchmark.

CTX3 ist nur Kandidat für weitere aktive Forschung, wenn WRONG < B0, CORRECT >= B0,
keine CRASH, zusätzliche falsche Quellen <= B0, keine unberechtigten Quellen und Median <=5ms/p95<=20ms.
Sonst Hypothese in dieser Form verwerfen; Shadow darf nur ausdrücklich als nicht erfolgreicher Forschungskandidat
abschaltbar/off bleiben. Keine produktive Antwortübernahme aufgrund dieses synthetischen Vergleichs allein.
Freeze-Codecommit und SHA256 der Implementierungs-/Runnerdateien werden vor dem Lauf im Begleitmanifest gesichert.
