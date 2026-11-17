# CDX1 Oracle-Bound-Experiment (Fable 5.1 Genesis, 2026-11-01)

Kontrolliertes Host-Experiment, kein Fold7-Lauf. Es beantwortet die Frage, ob die
historische CDX1-Nullleistung (freie Generierung 0/20 auf dem Fold7) ein
Trainingsbudget- oder ein Architekturproblem ist. **Keine Checkpoints wurden
angefasst** (`checkpointsTouched=false`); alle Decoder sind frische
`TinyContextDecoder(4711)`-Instanzen (7 746 Parameter, CONTEXT 8, CONDITION 16).

- Harness: `tools/continuum-span-research/Cdx1OracleBoundExperiment.kt`
- Rohdaten: `cdx1-oracle-bound.json` (Schema `CDX1_ORACLE_BOUND_V1`)
- Lauf: `java … Cdx1OracleBoundExperiment <json> 60000` (60 000 Updates je Bedingung, ≈1,2 s pro Bedingung auf dem Host)
- Korpus: synthetisch und vom Autor erzeugt (64 Trainingsitems, 13 verschiedene Antworten,
  Nah-Homographen Perg/Berg, Wien/Wein, Graz/Gras; Rollentausch-Paare „A ist in X, B ist in Y“;
  unbeantwortbare Items mit Sollantwort „unbekannt“). Dies ist **kein** Blind-Korpus und ersetzt
  keine Blind-A/B-Evaluation.

## Bedingungen
| Bedingung | Konditionierer (16-dim) | Bedeutung |
|-----------|-------------------------|-----------|
| SWB1_BAG | frischer `TrainableSubwordEncoder(73)` über Beleg+Frage | reale CDX1-Kopplung (SWB1 → CDX1) |
| ORACLE_ANSWER | 16-Bit-Code der Zielantwort | obere Schranke: Decoder *kennt* die richtige Antwort-ID |
| ORACLE_SPELL | Trigramm-Bag der Zielantwort | obere Schranke mit Buchstabierhinweis |
| RULE_CORE | `EvidenceBoundNaturalLocationProbe` (zertifizierter Regelkern) | nicht-neuronaler Vergleichspfad |

## Ergebnisse (exakt korrekte freie Generierung / Enthaltung / unterscheidbare Tausch-Paare)
| Bedingung | Train (47 bea. / 17 unb.) | Test gesehen (29/11) | Test ungesehene Namen (34/6) | Test ungesehene Orte (29/11) | distinct Generierungen | Tausch-Paare |
|-----------|---------------------------|----------------------|------------------------------|------------------------------|------------------------|--------------|
| SWB1_BAG (60k) | 8/47 · 0/17 | 2/29 · 0/11 | 3/34 · 0/6 | 0/29 · 0/11 | **1** (immer „Wels“) | 0/12, 0/6, 0/11, 0/6 |
| ORACLE_ANSWER (60k) | 47/47 · 17/17 | 29/29 · 11/11 | 34/34 · 6/6 | **0/29** · 11/11 | 11–12 | 12/12, 5/6, 11/11, 4/6 |
| ORACLE_SPELL (60k) | 47/47 · 17/17 | 29/29 · 11/11 | 34/34 · 6/6 | **0/29** · 11/11 | 11–12 | 12/12, 5/6, 11/11, 5/6 |
| RULE_CORE (nach Erweiterung) | 47/47 · 17/17 | 29/29 · 11/11 | 34/34 · 6/6 | 29/29 · 11/11 | – | 0 falsche Antworten |
| RULE_CORE (vor Erweiterung, Smoke-Lauf) | 11/47 · 17/17 | 12/29 · 11/11 | 5/34 · 6/6 | 9/29 · 11/11 | – | 0 falsche Antworten |

Verlustwerte: SWB1_BAG 5,55 → 0,37; ORACLE_ANSWER 5,55 → 0,005; ORACLE_SPELL 5,55 → 0,004.

## Befunde
1. **Die SWB1→CDX1-Kopplung ist die Kollapsursache, nicht das Budget.** Mit identischem Decoder,
   identischem Seed und identischem Budget erreicht der Oracle-Konditionierer 47/47, der echte
   SWB1-Bag-Konditionierer erzeugt für *alle* Eingaben dieselbe Zeichenkette (1 distinct) und
   unterscheidet 0 von 35 Rollentausch-Paaren. Der Verlust sinkt trotzdem (0,37), weil der Decoder
   die Trainingsziele über Position/Prefix auswendig lernt – ein klassischer Modus-Kollaps,
   der die frühere Fold7-Beobachtung (identische Ausgaben, 0/20) reproduziert. Das bestätigt
   Argon-Befund 1 (Binding/Superposition-Verlust im 16-dim Bag) experimentell.
2. **Selbst perfekte Konditionierung erzeugt keine ungesehenen Antworten (0/29).** Ohne
   Kopierpfad kann ein 7 746-Parameter-Byte-Decoder nur Antwortstrings emittieren, die er im
   Training gesehen hat („Bad Ischl“ → „WieWs“/„Steyr“). Die Enthaltung „unbekannt“ wird dagegen
   auch bei ungesehenen Orten korrekt gelernt (11/11), weil sie ein gesehener String ist.
   → Freie CDX1-Generierung kann das Qualitätsziel „16/20 belegte Antworten“ auf neuen Orten
   architektonisch nicht erreichen; nötig wäre ein Kopier-/Pointer-Mechanismus.
3. **Negativbefund am Regelkern:** Der zertifizierte Regelkern (37/37 in PR #331) enthielt sich bei
   komma- oder „und“-koordinierten Satzteilen („Anna ist in Perg, Emma ist in Berg.“) vollständig
   (11/47), weil Satzgrenzen nur an `.!?;` erkannt wurden. Das ist sicher (0 falsche Antworten),
   aber genau die Argon-Kollisionsform blieb unbeantwortet. Die Erweiterung in
   `EvidenceBoundNaturalLocationProbe` (Probe und unabhängiger Verifier getrennt angepasst)
   akzeptiert Komma und „und“ nur dann als Satzteil-Grenze, wenn eine vollständige neue Aussage
   folgt; „in Graz und Wien“, Negation, Zeitwörter, gemischte Relationen und Widerrufe enthalten
   sich weiterhin (Tests `coordinatedClausesKeepRoleBinding`, bestehende 31 Span-Tests grün).

## Was das NICHT zeigt
- Keine Aussage über den Fold7 (kein Gerätelauf, kein Checkpoint-Vergleich).
- Der Oracle-Konditionierer kennt die Antwort; er ist eine obere Schranke, kein nutzbares System.
- Der synthetische Korpus ist konsumiert und darf nicht als neue Blind-Evaluation gelten.

## Konsequenz für die Roadmap
CDX1 bleibt ein Forschungsartefakt. Der produktive Antwortpfad für belegte Ortsfragen bleibt
der Regelkern (jetzt mit Koordinationsabdeckung), Gemma bleibt der freie Generator. Ein
späterer CDX2-Kandidat braucht einen expliziten Kopierpfad (Pointer über Belegbytes) und
einen Konditionierer, der Rollenbindung erhält – isoliert entwickelt, ohne CDX1-Checkpoints zu
verändern.
