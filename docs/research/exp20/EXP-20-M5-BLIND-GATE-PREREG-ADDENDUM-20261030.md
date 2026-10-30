# EXP-20 M5 – vorab registrierte Ergänzung: Tie-/Positional-Verzerrung (30.10.2026)

**Zeitpunkt:** Ergänzung nach Implementierung der CRE1-abfrageblinden Gegenprobe, jedoch **vor jeder Auswertung mit dem echten CRE1-Modell oder einem Checkpoint** und vor jeder SEALED-Öffnung. Der bisherige VAL-Blind-Test mit Jaccard ist kein CRE1-Test. Die historischen P2–P4-Ergebnisse bleiben unverändert.

## Anlass

Die ursprüngliche M5-Vorregistrierung erklärt **Gleichstand = Fehler** für strikte Top-1-Abrufe. Diese Bedingung alleine reicht bei einer *abfrageblinden Negativkontrolle* jedoch nicht aus: Eine ungeeignete oder degenerierte Repräsentation könnte massenhaft Gleichstände erzeugen und dann wegen `strict_top1=0` fälschlich als biasfrei gelten. Das wäre dieselbe Klasse methodischer Fehler, die P4 wegen des Medoid-Artefakts aufgezeigt hat.

## Zusätzliche, vorab festgelegte Gültigkeitsbedingung

Neben der bisher verbindlichen strikten Blind-Top-1-Quote auf **VAL** muss auch die deterministisch aufgelöste Blind-Top-1-Quote unter dem bereits fixierten CRE1-Modell bei maximal `1/8 + 0,05 = 17,5 %` liegen. Gleichstände werden ausschließlich in dieser zweiten Kontrolle nach aufsteigendem Kandidatenslot aufgelöst. Wegen der schon fixierten exakt balancierten Positionen bedeutet ein vollständig entartetes, konstant punktendes Modell hier genau 12,5 % – kein künstlicher Sieg.

**PASS** nur, wenn gleichzeitig:
- `strictHits/128 <= 0.175`;
- `forcedHits/128 <= 0.175`;
- der fixierte M5-DEV/VAL-SHA-256 sowie der vorab benannte CRE1-Checkpoint-SHA-256 übereinstimmen.

Bei `strictHits=22` und `forcedHits=23` gilt **INVALID**, nicht PASS. Anzahl der Mehrfachmaxima (`maxTies`) vollständig berichten. Zusätzlich dürfen nachträglich keine künstlichen Gewinner über Checkpoint- oder Schwellenwechsel erzeugt werden.

**Wichtig:** Diese Ergänzung präzisiert die **Daten-Gültigkeitskontrolle** und ist kein neuer Qualitätsvergleich zwischen Retrieval-Armen. Der SEALED-Holdout bleibt fremd verfasst, ungeöffnet und darf nicht als Ergebnis eines internen Datengenerators gelten. Die bisherige `EXP-20-M5-PREREGISTRATION-20261030.md` bleibt als historische Erstregistrierung bestehen; diese Ergänzung ist ab jetzt im weiteren M5-Gate zusätzlich bindend.

**Implementierungsbeleg:** `app/src/test/java/at/heinz/deviceai/research/exp20/m5/M5Cre1BlindGate.kt`. **Status bei Veröffentlichung:** `MODEL_NOT_RUN`, `SEALED_UNOPENED`, `FOLD7_NOT_RUN`.
