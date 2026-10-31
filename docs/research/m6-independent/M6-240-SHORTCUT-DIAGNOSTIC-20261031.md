# M6/G2: Nachweisbarer Signalwort-Leak im eingefrorenen 240er-Fremdholdout

**Datum:** 31.10.2026. **Status:** Retrospektive Analyse des unveränderten Holdouts; KEIN EWM2-Modelltest, keine Änderung der vorregistrierten Regeln. Reproduzierbarer Audit-Code: `scripts/continuum-m6-shortcut-audit.mjs`.

## Kritischer Messbefund

Der bereits eingefrorene Datensatz `docs/research/m6-independent/ewm-v2-foreign-240.jsonl` bleibt exakt erhalten (Git-Blob `622370c291ead9ab11ec8917c992037a2fcf9e17`).

Ein *nach Einsicht in die Testfälle entworfener* trivialer Lexik-Baseline-Klassifikator benötigt ausschließlich das Feld `after`, **nicht** `before`, das unbeteiligte Faktum oder das echte EWM2-Modell:

1. Enthält `after` ein Korrektur-Signalwort (`korrektur|korrigier|falsch|berichtigung|entschuldigung|nicht … sondern`), sage `CORRECTS_EVENT`.
2. Sonst bei `jetzt|inzwischen|mittlerweile|seit|ab sofort`, sage `SUPERSEDES_STATE`.
3. Sonst `COEXISTS`.

**Direkte GitHub-Datenanalyse am 31.10.2026: 240/240 richtig, Accuracy 100 %.** Konfusionsmatrix: jede Klasse 80/80 auf der Diagonalen, null Fehler. Die Regeln sind absichtlich post hoc auf den sichtbaren Korpus abgestimmt und **kein Blindtest eines neuen Verfahrens**. Gerade dieser Umstand belegt jedoch, dass die bisherige Datenverteilung eine schlichte Wortmuster-Lösung erlaubt.

Weitere Befunde: 24 Signal-/Schablonengruppen mit je zehn Fällen; 240 unterschiedliche `before`-Sätze, 231 unterschiedliche `after`-Sätze, nur 31 verschiedene Kontrollfakten. Ein späterer EWM2-`PASS` auf dieser Sammlung bleibt interessant als begrenzter Regressionstest, beweist aber alleine **keine** Übertragung auf neue Semantik, Referenzbindung oder zeitliche Zusammenhänge.

## Test und unabhängige Wiederholung

```bash
node --test scripts/continuum-m6-shortcut-audit.test.mjs
node scripts/continuum-m6-shortcut-audit.mjs
```

Der zweite Aufruf liefert bewusst **Exit 1**, wenn die 100%-Shortcut-Warnung nachgewiesen ist; das ist kein Modell-Fail. Ein unveränderter Datensatz ist für den Aufruf Pflicht (Git-Blob-Prüfung), ansonsten Exit 2. Die fünf implementierten Node-Unit-Tests sind lokal 5/5 grün. Das echte M6-EWM-Modell wurde weiterhin **nicht** auf diesem Holdout gemessen.

## Was für den großen G2-Meilenstein zusätzlich nötig ist

- **Neuer, unabhängiger und vorregistrierter adversarialer Holdout** mit disjunkten Satzfamilien; keine Nachoptimierung auf die Original-240er-Fälle.
- **Signalwort-Balancierung über alle drei Klassen**, sodass `jetzt`, `Korrektur`, `inzwischen` usw. auch in nicht ersetzenden Aussagen, berichteter Rede, zitierten Aussagen, unterschiedlichen Domänen und historischen/futurischen Zusammenhängen vorkommen. Gegenbeispiele mit gleichbleibender Wortoberfläche, aber vertauschten Rollen/Zeitbezügen.
- Goldlabels **aus unabhängig definierten Sachverhaltsrahmen**, nicht aus EWM2-Ausgaben oder aus einem nachträglich an den Algorithmus angepassten Orakel.
- Das simple `after`-Wortmuster als **Pflicht-Negativkontrolle** mitmessen. M5 CRE1, lexikalische Baseline und EWM2 im gleichen Daten-Split prüfen.
- **Vor** Datengenerierung die neuen G2-Abnahmekriterien und die Art der Statistik festlegen (insbesondere echte Recall, positive Precision, Kontrollfakt-Verlust, Konfusionsmatrix, Rollen-/Tempus-Fehler). Der alte 240er-Precision-Vertrag bleibt historisch unverändert.
- Unberührte SEALED-Fragen erst öffnen, wenn auf einem disjunkten VAL-Split die vorher festgelegten Ziele erreicht sind. Keine neue große Erfolgsbehauptung aus dem ursprünglichen, lexikalisch lösbaren Korpus.

**Projektentscheidung:** G2 ist offen. Dieses Dokument verhindert eine unbelegte `100% Intelligenz`-Behauptung, ohne Jules’ Android-Arbeit, die Originaltestdaten oder die eingefrorene Produktionsversion anzutasten.