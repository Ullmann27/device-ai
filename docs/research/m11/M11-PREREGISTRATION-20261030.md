# M11 · Gesprächsintelligenz und Langzeitgedächtnis – Ausgangsmessung und Vorregistrierung

**Stand:** vor jeder Änderung. Benchmark: `research/m11/M11ConversationMemoryBench.kt`. Gleiche Aufgaben, kein Modell, keine Besitzerdaten.
**HOLDOUT-Siegel:** `be20962699362578fc50043b49b0c48371b606ee69474755d1d1872186e2234e` (andere Namen, Orte und Zwischenfragen). Er läuft genau einmal nach den Änderungen.

## Ausgangsmessung DEV (je 6 Fälle pro Kategorie)

| Teil | Kategorie | Produktivpfad | Forschungskomponente |
|---|---|---|---|
| A Gespräch | mehrere Personen, lang | 0/6 (Bezug nicht mehr im Kontext) | F1-Kern 5/6 |
| A | Rückkehr nach 12 Zwischenfragen | 0/6 | **0/6** („er“ → maskulines Ding aus Zwischenfragen) |
| A | späte Korrektur | 6/6 (noch im Kontext) | **0/6** (korrigierte Person wird nicht aktuell) |
| A | falscher Bezug („er“ ohne männliche Person) | n/a | **0/6** (bindet an „Witz“ statt Rückfrage) |
| A | Zeitbezug nach Zwischenfragen („an dem Tag“) | 0/6 | **0/6** |
| B Gedächtnis | überholt (bis/seit) | lexikalisch 6/6 | **EWM2 0/6** (zitiert den alten Fakt) |
| B | korrigiert („nicht …, sondern …“) | 0/6 | **0/6** |
| B | widersprüchlich | 0/6 | **0/6** (keine Rückfrage) |
| B | Zwei-Schritt (umgekehrte Verwandtschaft) | 0/6 | **0/6** |
| B | Ablenker | 6/6 | 6/6 |
| B | unbeantwortbar | 0/6 | **6/6** (enthält sich) |

## Geplante Änderungen (nur an Forschungskomponenten im SHADOW-Pfad, nicht am produktiven Gedächtnis)

1. F1-Kern: „er/sie“ bevorzugt Personen, wenn eine passende Person existiert. Ohne passende Person und mit personentypischer Frage folgt eine Rückfrage.
2. F1-Kern: Die in einer Korrektur genannte Person wird zum jüngsten Bezug.
3. F1-Kern: Ausdrücke wie „an dem Tag“ greifen den letzten Zeitbezug der Sitzung wieder auf.
4. **Kein Eingriff in das eingefrorene EWM2.** Überholte, korrigierte und widersprüchliche Fakten werden in einer getrennten Zeitlinienprüfung über den zitierten Fakten erkannt, zunächst als Shadow-Messung.

**Erfolgskriterium (HOLDOUT):** mehr richtige Fälle als vor der Änderung, und **keine Kategorie fällt um mehr als 1 Fall zurück**. Bestehende F1-Tests (21) und der ARGON-F1-Benchmark müssen grün bleiben.
