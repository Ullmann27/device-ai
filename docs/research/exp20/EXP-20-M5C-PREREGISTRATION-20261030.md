# EXP-20 M5c · Vorregistrierung: Grenzen von CRE1 und drei getrennte Generalisierungsarten

**Status:** Vorregistrierung und Grenzanalyse. Noch **kein** M5c-Datensatz, kein Training, keine Auswertung. Die M5-Abnahmebedingungen bleiben unverändert: L ≥ 80 % und S ≥ 90 % auf unabhängigen Daten, fremder SEALED nur einmal. M5 bleibt offen.

## 1. Grenzanalyse des vorhandenen CRE1 (Code + Messung)
CRE1 (`Cre1InferenceEncoder`) bildet pro Token Merkmale: Wort-Unigramm `w:` und Zeichen-3- bis -5-Gramme *innerhalb* des Wortes, gehasht auf 16 384 Zeilen. Diese werden L1-gewichtet gemittelt, mit einer 32×32-Projektion multipliziert (eigene Projektion für Anfrage und für Erinnerung) und L2-normiert. Score = Kosinus. Daraus folgen drei Grenzen:

| | Grenze | Begründung | Messung ([Beleg](../../evidence/exp20-m5c-20261030/m5c-limit-analysis.txt)) |
|---|---|---|---|
| **L1** | **Keine Rollenbindung.** Gleiche Tokens in anderer Reihenfolge ergeben dieselbe Repräsentation. | Die Merkmale hängen nur von der Token-Multimenge ab. | 80 Rollentausch-Paare („Anna wohnt bei Ben.“ ↔ „Ben wohnt bei Anna.“): Embeddings bis auf Rundung gleich (max. \|Δ\| = 2,2·10⁻¹⁶). Das gilt **auch für das M5b-trainierte Modell**. |
| **L2** | **Keine Konjunktion innerhalb der Anfrage.** | Score = ⟨W_q·p_q, W_m·p_m⟩ / Normen ist eine Bilinearform zweier Mittelwerte. Jedes Anfragemerkmal wirkt nur additiv; „nach“ × „Montag“ → Dienstag ist nur über eine zufällig passende Geometrie darstellbar, nicht als Regel. | analytisch |
| **L3** | **Keine lexikalische Übertragung.** | Im Training erhalten nur die Zeilen gesehener Merkmale einen Gradienten. Die gemeinsamen Projektionen können ungesehene Merkmalsvektoren nicht sinnvoll zuordnen, weil diese zufällig initialisiert und mit der Bedeutung unkorreliert sind. | M5b-VAL-S, nachträgliche Diagnose: Anteil ungesehenen Merkmalsgewichts bei Tag fern 0,555 gegenüber nah 0,165, bei Ort fern 0,592 gegenüber nah 0,314. Passt zu S fern/fern 3/16 auf Zufallsniveau. |

**Folgerung (vor jedem M5c-Lauf festgelegt):**
- Mehr kontrastives Training von CRE1 (Arm A) kann L1 **beweisbar nicht** beheben.
- L3 kann es nur beheben, wenn das Curriculum die Wörter der Prüfung enthält. Dann wären sie nicht mehr unbekannt.
- **Unbekannte Formulierungen** im Sinne von M5 verlangen **Vorwissen**: ein vortrainiertes Modell (Arm D) oder Regeln mit allgemeinem Sprachwissen (Arm C).

## 2. Drei getrennte Generalisierungsarten (getrennte Spuren, getrennte Urteile)

| Spur | Frage | Beispiel | Vorhersage vor dem Lauf |
|---|---|---|---|
| **G-lex** (M5-relevant) | Neue Wörter, gleiche Bedeutung | „gleich zu Wochenbeginn“ = Montag | A ≈ Zufall; B ≈ Zufall; C nur mit allgemeinem Kalenderwissen; D möglich |
| **G-comp** | Bekannte Bausteine, neue Kombination | TRAIN „einen Tag vor dem Mittwoch“, „zwei Tage nach dem Montag“; VAL „einen Tag nach dem Donnerstag“ | A begrenzt (L2); B und C möglich |
| **G-role** | Wer tut was mit wem | „Anna wohnt bei Ben.“ gegen „Ben wohnt bei Anna.“ auf „Bei wem wohnt Anna?“ | jeder Bag-Encoder (Jaccard, CRE1, A) **= Zufall** (L1); B und C möglich |

## 3. Arme

| Arm | Inhalt | Bedingung |
|---|---|---|
| A | CRE1 kontrastiv mit strukturellen harten Negativbeispielen (Rollentausch, Zeit- oder Ortsvertauschung bei gleichem Wortlaut), nur TRAIN | Startpunkt: Parity-Checkpoint `2290194d…0db6` |
| B | kompositionelle Repräsentation: Merkmale gebunden an Rollen (Agens, Ziel, Ort, Zeit, Negation), Rollen aus Satzbau und Kasus (der/den, bei/für) | Rollenzuordnung deterministisch, Einbettung gelernt nur auf TRAIN |
| C | deterministische Kontrollarchitektur: Regeln für Kalenderarithmetik, Präpositionsrollen, Kasus | Regeln nur aus TRAIN und allgemeinen Sprachregeln, eingefroren vor VAL |
| D | vortrainierter lokaler Satz-Encoder | **nur nach Besitzerentscheidung** (Abschnitt 6); keine Gewichte im Repository ohne Freigabe |
| Referenzen | Jaccard, HYBRID_LEX, CRE1 (Parity), HybridMemoryRetriever + CRE1, abfrageblinder Medoid, Gleichstand-Zufallsrate | unverändert aus M5/M5b |

## 4. Daten (für jede Spur neu erzeugt, nichts aus M5/M5b wiederverwendet)
- **TRAIN, DEV und VAL** haben getrennte Paraphrasenfamilien, getrennte Personen-, Orts- und Namenspools und getrennte Hard-Negative-Schablonen. Keine Person-Ort-Kombination wiederholt sich. Die Position der Zielerinnerung ist balanciert.
- **Vor VAL** stehen fest und werden mit SHA-256 committet:
  - TRAIN- und DEV-Generator, Normalisierer bzw. Regeln (C), Trainingsskript mit Hyperparametern (A, B).
- **VAL** wird nach dem Freeze geschrieben und genau einmal ausgewertet. Fern/fern ist das zentrale Generalisierungsmaß.
- **Datengate:** abfrageblinder Medoid ≤ 17,5 % strikte Top-1-Trefferquote; Gleichstand-Zufallsrate wird mitberichtet.
- Der M5b-VAL ist geöffnet und wird **nicht** als neuer unabhängiger Test verwendet.

## 5. Berichtete Größen und Urteil
**Größen je Arm:**
- L- und S-Genauigkeit.
- Die Kombinationen nah/nah, nah/fern, fern/nah, fern/fern.
- Entitätsverwechslungen, falsche Zeitbezüge, unbegründete Korrekturen, nötige Enthaltungen, fehlerhafte Gedächtnisersetzungen.
- Exakter McNemar auf Clustern mit Holm-Korrektur über die registrierten Vergleiche.
- Cluster-Bootstrap mit 10 000 Ziehungen, Seed 5.
- Kleinster relevanter Effekt (MDE) 5 Prozentpunkte. Urteile: SUPERIOR, INFERIOR, EQUIVALENT oder INCONCLUSIVE.

**Spur-Schwellen, je Spur fern/fern:**
- G-role ≥ 90 % strikt.
- G-comp ≥ 80 %.
- G-lex = M5-Schwelle (S ≥ 90 %).

**Weitere Regeln:**
- Ein kleinerer Trainingsverlust ist **kein** Beleg.
- Ein Arm, der G-role oder G-comp besteht, aber G-lex nicht, erfüllt M5 **nicht**.

## 6. Arm D: Vorschlag für die Besitzerentscheidung (nicht integriert)
- **Prüfpunkte vor jeder Nutzung:**
  - Lizenz, tatsächliche Offline-Ausführung, ARM64, Modellgröße und RAM nach Quantisierung, CPU/NPU auf dem Fold7.
  - p50/p95-Latenz pro Satz auf dem Gerät, Datenschutz (keine Netzwerkaufrufe), Integrationskosten (Laufzeit, APK-Größe).
- **Kandidatenklassen**, nach Größe aufsteigend; die Angaben stammen aus den Modellkarten der Hersteller und sind **in dieser Sitzung nicht verifiziert**:
  1. **Statische, destillierte Embeddings** (z. B. Model2Vec-Varianten): Nachschlagen plus Mittelwert, sehr schnell. Lösen L3 durch Vorwissen, nicht L1.
  2. **Kleine mehrsprachige Satz-Encoder** (etwa 100 M Parameter, z. B. multilingual-MiniLM- oder E5-small-Klasse): lösen L1 und L3 teilweise. Nach INT8-Quantisierung etwa 100–150 MB.
  3. **Geräteorientierte Embedding-Modelle** (etwa 300 M Parameter, z. B. EmbeddingGemma-Klasse): stärker, eigene Lizenzbedingungen.
- **Versuch erst nach Freigabe:**
  - Ein Kandidat wird als **reiner Host-Forschungsarm** auf einem neuen G-lex-VAL gemessen, gegen dieselben Referenzen und mit derselben Statistik.
  - Erst danach wird über die Integration als `semanticScorer` im vorhandenen `HybridMemoryRetriever`-Schatten entschieden. Es entsteht kein zweiter Retriever.

## 7. Reihenfolge und Abbruch
1. **G-role** zuerst. Sie ist am billigsten und klärt sofort, ob B oder C die Bindung leisten; A ist nach L1 vorab als unterlegen registriert.
2. **G-comp.**
3. **G-lex**, nur mit C (allgemeines Wissen) und, nach Freigabe, D.

**Abbruchregeln:**
- Besteht in einer Spur kein Arm, wird das Ergebnis als negativ dokumentiert, und es gibt kein Nachtuning auf VAL.
- Keine neuen neuronalen Bausteine ohne gemessenen Vorteil gegenüber C.
