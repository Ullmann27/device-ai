# EXP-20 M5 – Übergabespezifikation für einen unabhängig verfassten SEALED-Holdout

**Stand:** 30.10.2026. Dies ist ein **Auftrags-/Formularvertrag, kein fertiger Holdout**. Der Verfasser darf die M5-DEV/VAL-Daten und P2/P3/P4-Auswertungsdaten nicht als Textvorlagen verwenden. Das eigene Testmaterial bleibt von der Entwicklungs-KI und ihrem CRE1-Tuning getrennt, bis Kandidaten und Prüfregeln eingefroren sind.

## Aufgabe an einen unabhängigen Verfasser

Erstelle neue, deutschsprachige 9-Zeilen-Gruppen für Episodenabruf. Ein Satz fragt nach einer Erinnerung; acht gleich plausible Episoden konkurrieren. Mindestens je 64 Gruppen für **L** (wörtliche Slot-Begriffe) und **S** (gleiche Bedeutung mit paraphrasiertem Ort/Zeit/Auftrag). Pro Gruppe: exakt eine richtige Episode, sieben falsche; Near Misses sollen sich nur in einem relevanten Detail unterscheiden. Neue Namen, Orte, Tage, Aufgaben und **eigene Satzschablonen**; keine Wiederholung bereits verwendeter P2–P4-Formulierungen. Wahrheit muss aus der Frage bestimmbar sein, nicht aus der Kandidatenreihenfolge oder einer zentralen Stellung unter den Erinnerungen. Verwende gegen die wahre Erinnerung **und** gegen falsche Erinnerungen strukturähnliche Gegenbeispiele. Erzeuge reale Mehrdeutigkeit und eindeutige Fälle getrennt; Gold muss eindeutig und von einer zweiten Instanz überprüfbar sein.

## Gewünschter TSV-Transport

Header: `group\tsplit\ttype\trole\tcandidate_id\ttext`

- `split` = `SEALED` (nicht `DEV` oder `VAL`); die unabhängige Import-/Auswertungskomponente ist **noch nicht implementiert**, `M5Data.read` liest bewusst nur DEV/VAL. Keine bestehende Forschungsklasse zur Umgehung des Contracts modifizieren.
- `type` = `L` oder `S`.
- `role` = `QUERY` (eine Zeile), `TRUE` (eine Zeile), sonst `CANDIDATE` (sieben Zeilen).
- `candidate_id` = `-` für QUERY, sonst `C0` bis `C7` genau einmal; diese IDs/Gold-Rollen dürfen den Scorern nicht übergeben werden.
- `text` = UTF-8, ohne Tabs oder Zeilenumbrüche. Jede Gruppe hat genau neun Zeilen.
- Stabile Gruppen-IDs, keine personenbezogenen echten Nutzerdaten.

## Versiegelung und unabhängige Autorenprüfung

Der Verfasser führt einen abfrageblinden Rankingtest aus. Alle Kandidatenpositionen sind balanciert; keine Positional-/Medoid-Heuristik darf systematisch richtig liegen. Inhaltliche Gleichstände zählen im strikten Top-1 als Fehler. Gesamtdatei und Gold-Schlüssel werden mit SHA-256 festgehalten. Der autorisierte Reviewer liefert **nur SHA, Gruppenzahl, Zählungen pro L/S und Autorenerklärung** vor dem Kandidaten-Freeze; den eigentlichen SEALED-Inhalt erhält die Experiment-KI erst bei der einmaligen Öffnung. Nicht versuchen, die Antwortdatei vor dem Freeze anhand der vorhandenen CRE1-Fehler zu optimieren.

## Experiment-Gates

`HYBRID_CRE1` muss nach gleicher eingefrorener Modell-/Score-Version auf dem bisher **nicht** eingesehenen SEALED-Holdout mit exaktem gepaarten McNemar, Holm über drei primäre Vergleiche, 10.000 Cluster-Bootstraps und Mindestdifferenz 5 Prozentpunkte gegenüber `HYBRID_LEX`, `JACCARD` und `QUERY_BLIND_MEDOID` bestehen. Vorher muss das CRE1-blind-Datensatz-Gültigkeits-Gate auf M5-VAL bestanden sein. Ein Holdout-Lauf darf nicht nachträglich getunt oder als neuer unabhängiger Lauf wiederholt werden. Fehlende Vorbedingungen bedeuten `NOT_RUN`, nicht `PASS`.

**Referenzen:** M5-Vorregistrierung; `M5Data.kt` (synthetische DEV/VAL-Schablone, nicht der externe Holdout); `M5-WP1-HOST-AND-LEAKAGE-AUDIT.md`; Opus-Übergabe an GPT-6. Keine Änderungen an Produktivcode, `main`, OwnerGate oder ToolGate.
