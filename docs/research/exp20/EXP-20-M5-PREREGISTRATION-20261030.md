# CONTINUUM EXP-20 M5 / WP1 – Vorregistrierung (30.10.2026)

**Status:** PRE-REGISTERED, NO QUALITY RESULTS. Forschung ausschließlich in `Ullmann27/device-ai`, auf Draft-PR #256; keine Änderung an `app/src/main`, keine Fold7-Abnahme.

## Ausgangslage und Hypothese

P2 wurde nach der eingefrorenen Regel mit unverändertem Router A beendet. P3 zeigte bei neuen Synonymen keinen funktionierenden semantischen Encoder; DG-SDR fiel durch. P4 zeigte einen erheblichen Medoid-/Datenkonstruktionsartefakt beim zweistufigen Hopfield-Abruf. Der explorative Kandidat ist der **bereits vorhandene** `HybridMemoryRetriever` mit `Cre1InferenceEncoder.score`; 79/120 gegen 60/120 im verbrauchten P3/P4-Satz ist **keine unabhängige Bestätigung**.

**H-M5:** Mit gleichen Erinnerungen, gleichem trainierungsfreiem CRE1-Checkpoint und gleichen Querys ist `HYBRID_CRE1` auf einem neuen, fremd verfassten versiegelten Datensatz in striktem Top-1 gegenüber *jedem* `HYBRID_LEX`, `JACCARD` und `QUERY_BLIND_MEDOID` überlegen, bei akzeptabler Latenz. Andernfalls **INCONCLUSIVE / REJECTED**, keine Integration. Die P2-A0/EMA-Arme sind **separate Kontext-Baselines** für WP4, keine passenden Episoden-Retrieval-Referenzen für WP1–3.

## WP1 – Datenkonstruktion vor jedem Vergleich

Ein neuer Generator unter `app/src/test/java/at/heinz/deviceai/research/exp20/m5/` erzeugt **nur DEV und VAL**; der echte **SEALED-Holdout wird durch einen anderen Autor unabhängig geliefert**. Keine Wiederverwendung von P2/P3/P4-Sätzen, Synonymlisten oder Gold-Daten. Format: `group\tsplit\ttype\trole\tcandidate_id\ttext`, pro Gruppe genau eine QUERY, eine TRUE, sieben CANDIDATE-Zeilen, insgesamt acht Kandidaten. Anfrage und Wahrheit sind ausschließlich durch Sprache/Slots bestimmt, nie durch Speicherreihenfolge, Record-ID oder Lücken in der Metadatenstruktur.

**Anti-Medoid-Konstruktion:** Acht Kandidaten bilden ein balanciertes 2×2×2-Faktoriell (zwei Personen × zwei Tage × zwei Orte), gleiche Tätigkeit. Damit besitzen sowohl wahre als auch falsche Kandidaten mehrere Ein-Slot-Nachbarn; das richtige Element rotiert **gleichmäßig über alle acht Faktoriell-Positionen**, und die Reihenfolge wird unabhängig davon permutiert. Kein Zentrum ist aufgrund der Konstruktionsregel garantiert wahr. Pro Typ und Split mindestens 64 Gruppen mit achtmal jeder Zielposition; Splits per stabiler Gruppen-ID festgelegt. Typ L verwendet wörtliche Slot-Wörter, S verwendet neue, nicht mit P3 geteilte Umschreibungen für Tag und Ort. Die Daten enthalten keine personenbezogenen echten Nutzerdaten.

**Datengültigkeits-Gate vor SEALED:** Frage-blinder Medoid-Scorer `score_j = Σ_i ⟨embedding(memory_i), embedding(memory_j)⟩` (gleiches CRE1-Modell und keine Query) und deterministische positionsbasierte Negativkontrolle dürfen auf VAL nicht mehr als `1/8 + 0,05 = 17,5 %` strikte Top-1 erreichen. Gleichstände = Fehler. Die Daten, Modell-/Checkpoint-SHA, Generator-SHA und Gruppeneinteilung werden VOR Prüfung versiegelt. Fällt das Gate, Dataset = INVALID; Fehler dokumentieren, überarbeitete Generation separat neu registrieren, **SEALED nicht öffnen**. DEV/VAL sind interne synthetische Kontrollen, kein unabhängiger Erfolgsbeleg.

## WP3 – spätere Bestätigungsprüfung (nicht von WP1 vorwegzunehmen)

Fixe Arme: `JACCARD`, `HYBRID_LEX`, `HYBRID_CRE1`, `CRE1`, `QUERY_BLIND_MEDOID`. `HOPFIELD2_B4` nur Artefaktkontrolle. Freeze der Kandidaten, Formeln und Checkpoints VOR Öffnung. Primärmetrik: strikte Top-1 je unabhängiger Gruppe, L/S getrennt und gesamt. Paarige McNemar-Tests, Holm-Korrektur für die drei primären Vergleiche, gruppenbasiertes Bootstrap mit 10.000 Ziehungen, vorab fixierte `MDE = 5 Prozentpunkte` und `α = 0,05`. SUPERIOR nur bei korrigiertem p < 0,05, positivem 95-%-KI-Unterrand und Effekt >= 5 Prozentpunkte gegenüber **allen drei** Vergleichsarmen. Fehlende Evidenz ist kein Erfolg. Zusätzlich Near-Miss-Verwechslungen, falsche Enthaltungen, p50/p95/p99 und Speicher messen. Ein versiegelter Test wird **einmal** geöffnet, mit unveränderlicher Quittung/Hash und reinem Replay ohne Neufit.

**STOP-Regeln:** Keine Semantikbehauptung bei fehlender Synonym-Vorbedingung, kein Tuning auf versiegelten Daten, keine neue Produktivbrücke, keine automatische Berechtigungsausweitung. Kein `main`-Merge, kein Force-Push. Historische P2–P4-Daten bleiben unangetastet. Android- und Fold7-Werte niemals aus Host-Läufen ableiten.

## Nachweise

- `docs/research/omega/GPT6-HANDOFF-FROM-OPUS-EXP20-20261030.md`
- `docs/research/omega/OPUS-CONTINUATION-BRIEF-M5-20261030.md`
- `docs/research/exp20/EXP-20-P4-RESULT-20261030.md`

**Erst dieser Vorregistrierungs-Commit, dann Generator/Code/VAL-Prüfung; kein SEALED-Lauf in WP1.**
