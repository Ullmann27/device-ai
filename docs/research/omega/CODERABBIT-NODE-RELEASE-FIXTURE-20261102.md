<!-- coderabbit-ai-generated:v1 -->

# Continuum — drei Node-Release-Fixtures repariert (2026-11-02)

## Ursache und Reparatur

Die drei fehlgeschlagenen Fälle in `scripts/continuum-0324-live-source-gate.test.mjs` setzten voraus, dass der aktuelle Checkout dem historischen 0.3.24-Release-Kandidaten und dessen Branch entspricht. Im Forschungsbranch wichen tatsächliche Dateien vom eingefrorenen Inventar ab; das unveränderte Gate blockierte korrekt. Die beiden Shell-Tests scheiterten vorher am Branch-Gate, bevor die zu prüfenden Freigabefehler erreicht wurden.

Die Tests erzeugen jetzt einen temporären, unabhängigen Git-Bestand aus dem festgelegten Commit `5270202e0699764b0eac5d4dd3e6a587b982ae05`: alle 1095 Inventardateien, unverändertes Manifest und die tatsächlichen Builderdateien. Für die Szenarien wird ein isolierter synthetischer Builder-Commit auf dem erwarteten Branch erstellt. Die echten Gate- und Shell-Skripte werden aufgerufen; die Umgebung übernimmt keine `CONTINUUM_*`-Variablen und erhält ausschließlich synthetische Testwerte. Die Shell-Fälle haben zusätzlich eine absichtlich ungültige UI-SHA als Stoppschranke vor späteren Build- oder Signierschritten. Der Bestand wird nach dem Lauf gelöscht.

Der Checkout muss den gefrorenen Commit und seine Git-Objekte lokal enthalten. Ein flacher oder partieller Checkout muss ihn zuvor abrufen, beispielsweise `git fetch --no-tags origin 5270202e0699764b0eac5d4dd3e6a587b982ae05`; fehlende Objekte werden als Fehler gemeldet. Kein Netzwerkzugriff wird bewusst vom Test verlangt.

## Ergebnisse

- Vorher gezielt: **4 PASS / 3 FAIL**, Exit1. Unverändertes Gate meldete geänderte Quelle oder falschen Branch.
- Nachher gezielt: **10 PASS / 0 FAIL / 0 SKIP**, Exit0. Positive synthetische Freigabe und negative Tests für fehlende/falsche Freigaben, geänderte/fehlende/verlinkte/nicht attestierte Dateien, geändertes Manifest und falschen Branch.
- Vollständiges Node-Profil nach finaler Umgebungsisolierung: **1938 PASS / 0 FAIL / 13 SKIP** von1951 Fällen, Exit0; Quellstand während des Laufs unverändert, Prozessbereinigung bestätigt. Ein früherer Gesamtlauf vor der zusätzlichen Umgebungsisolierung zeigte denselben Ergebnisstand.
- Originalbefehle, Exit-Codes, Testnamen, SHA256 und Roh-JUnit: `docs/evidence/continuum-release-fixture-20261102/results.json` und `full-node/report.json`.

Die produktiven Release-Gates, Freigabewerte, das Inventar und APK-Signierskript wurden nicht verändert. **13 SKIP** bleiben nicht ausgeführte Tests. Kein APK-, Fold7- oder Modelltest; keine Freigabe und kein Merge.
