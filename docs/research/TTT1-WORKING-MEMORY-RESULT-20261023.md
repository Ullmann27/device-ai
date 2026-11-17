# Experiment A – TTT1: adaptives Arbeitsgedächtnis – Ergebnis (23.10.2026)

Vorregistrierung `TTT1-WORKING-MEMORY-PREREG-20261023.md` (`2c946759`, vor jedem Lauf). Code eingefroren in `abcede88`
(`app/src/test/java/at/heinz/deviceai/intelligence/maxultra/Ttt1*.kt`), TEST **einmal** ausgewertet.
Bericht `ttt1-working-memory-report-20261023.json`, Konsole `ttt1-working-memory-test-stdout-20261023.txt`.
JVM, kein Gerät. Nur Test-Quellset.

## Ergebnis TEST (360 Dialoge, 680 bewertete Züge; richtig / falsch / unnötige Rückfrage)

| Variante | K (Kontext, 3 Züge) | S (Entitätsspeicher) | S+FW (Speicher + Fast-Weights) |
|---|---|---|---|
| `CONTINUE`, `SWITCH`, `RETURN`, `NEW_SESSION` | alle richtig | alle richtig | alle richtig |
| `LONG_DISTANCE` | 120/0/**40** | 160/0/0 | 160/0/0 |
| `TYPE_CONSTRAINT` | 0/**40**/0 | 40/0/0 | 40/0/0 |
| `AMBIGUOUS` (Gold: Rückfrage) | 40/**40**/0 | 80/0/0 | 40/**40**/0 |
| `TYPE_DESCRIPTION` | 22/**18**/0 | 40/0/0 | 40/0/0 |
| `PATTERN` | 40/0/0 | 40/0/0 | 7/**33**/0 |
| **Summe** | 542 / 98 / 40 | **680 / 0 / 0** | 607 / 73 / 0 |
| Zeit je Zug (Median / p95) | 0,8 / 3,8 µs | 0,3 / 0,7 µs | 3,3 / 11,1 µs |
| Zustand je Sitzung (Spitze, gezählt) | 48 B | 84 B | 2 516 B |

S+FW mit Seeds 1–5: 70–75 falsche Rückbezüge. Mathematik (Unit-Tests): Fast-Weight-Schritt = exakter
Gradientenschritt (finite Differenzen < 1e-7), Normgrenze 2,5 hält, NaN wird abgelehnt, Neustart setzt alles zurück.

## Entscheidung (Vorregistrierung)

S+FW hat **mehr** falsche Rückbezüge als S (73 gegen 0) → Fast-Weights kommen in **keinen** produktiven Pfad. Sie
raten bei echter Mehrdeutigkeit statt nachzufragen und stellen ein gelerntes Wechselmuster über den jüngsten passenden
Bezug. Der explizite Entitätsspeicher (Geschlecht, Typvorgabe des Prädikats, Gleichstand → Rückfrage, Neustart leert
alles) ist die Baseline. Der begrenzte Kontext scheitert an weiten Rückbezügen, Typvorgaben und Mehrdeutigkeit.

## Grenzen

Dialoge und Gold vom selben Autor; Züge kommen bereits analysiert an (Erwähnungen, Fürwort-Geschlecht,
Prädikat-Typ): gemessen ist das Gedächtnis, nicht das Sprachverstehen. Das deutsche „sie“ (Plural) und das
Dativ-„ihm“ (maskulin oder neutral) sind ausgeklammert.
