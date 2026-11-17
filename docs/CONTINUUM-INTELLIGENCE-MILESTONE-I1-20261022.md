# Intelligenz-Meilenstein I1 – ehrlicher Belegkern mit Garantie (Opus, 22.10.2026)

Zweig `opus/continuum-csx1-abstention-20261022`, Draft-PR #62, baut auf PR #61 (`21365a4`) auf. Forschungsstand,
keine Promotion, kein Merge nach `main`. SWB1/CDX1, Gerätecheckpoints und produktiver Chat wurden nicht berührt.
Rohdaten: `docs/evidence/csx1-abstention-20261022/milestone-i1-certify.txt`. Verlauf aller Zyklen:
`docs/CONTINUUM-CSX1-ABSTENTION-OPUS-20261022.md`.

**Kurz:** CSX1 hat 4.110 Parameter. Es beantwortet Fragen jetzt mit einem exakten Ausschnitt aus dem Beleg oder
enthält sich. Neu ist eine **statistische Obergrenze für falsche Antworten**, die der Besitzer wählen kann.
Mit der Grenze 2 % gibt der feste Kandidat V8 auf dem eingefrorenen TEST bei **3 von 264 Fragen** eine falsche
Antwort. Ohne Grenze waren es 16. Von den gegebenen Antworten stimmen 96 %. Der Preis: Das Modell enthält sich
öfter (78 statt 99 von 124 beantwortbaren Fragen richtig).

## 1. Recherche: Forschungsprojekte und was davon übernommen wird

| Projekt (Institut) | Kern | Bezug zu Continuum | Entscheidung |
|---|---|---|---|
| Conformal Abstention (Google DeepMind, Abbasi-Yadkori et al., NeurIPS 2024) [1] | Enthaltung mit beweisbarer Obergrenze der Halluzinationsrate | genau unsere offene Frage „wann schweigen?“ | **übernommen (I1)** |
| Learn then Test (Angelopoulos, Bates, Candès, Jordan, Lei 2021) [2] | Schwellenwahl als multiples Testen, Garantie bei endlicher Stichprobe, für jedes Modell | gelernte Gewichte bleiben unverändert, nur die Schwelle wird zertifiziert | **übernommen (I1)** |
| Geometry-Calibrated Conformal Abstention (HKUST Guangzhou, 2026) [3] | Kalibrierung über die Geometrie der internen Darstellung | spätere Verfeinerung mit CSX1-Einbettungen | vermerkt |
| „Why Language Models Hallucinate“ (OpenAI, Kalai et al., 2025) [4] | Bewertungen, die nur die Genauigkeit zählen, belohnen Raten; Fehler sollen härter zählen als Enthaltung | unsere Kennzahl „richtig /124“ belohnte Raten | **übernommen**: fehlerbestrafte Bewertung (score@0,75: richtig +1, falsch −3, Enthaltung 0) |
| Tiny Recursive Model (Samsung SAIL Montréal, 2025) [5] | 7 Mio. Parameter, rekursive Verfeinerung; ARC-AGI-1 45 %, ARC-AGI-2 8 % | Gitterrätsel, kein offenes Textverstehen | nicht jetzt; Idee „tiefe Überwachung“ vermerkt |
| SEAL (MIT, 2025) [6], Nested Learning (Google, NeurIPS 2025) [7] | Modelle passen sich selbst an, kontinuierliches Lernen | passt zum On-Device-Lernen; Vergessen laut den Autoren ungelöst | später, nur hinter eingefrorenen Gates |
| Darwin Gödel Machine (Sakana AI / UBC, 2025) [8], AlphaEvolve (DeepMind) | Agenten verbessern ihren eigenen Code (SWE-bench 20 → 50 %) | relevant für die M4-Programmierkette; die Autoren berichten von Reward Hacking | Lehre: Bewertung nur über eingefrorene, unabhängige Tests (gilt hier bereits) |
| Kleine On-Device-Modelle 2026 (Meta MobileLLM-R1, Microsoft BitNet b1.58, Gemma) [9] | Sprachvorwissen auf dem Telefon | das fehlende Puzzlestück für ungesehene Umformulierungen | Grundlage für Vorschlag I2 |

## 2. Experimente überdacht: Kritik am eigenen Aufbau

1. **Kleine TEST-Menge.** 6 unbelegte Antworten von 140 sind 4,3 %, mit 95-%-Intervall von 2,0 % bis 9,0 % (Wilson).
   Unterschiede wie 99 gegenüber 96 richtigen Antworten liegen in der Unsicherheit. Ab jetzt stehen Intervalle dabei.
2. **Seeds.** Die Seeds 1/2/3 ändern nur die Reihenfolge der Trainingsdaten, die Initialisierung (77) ist fest.
   Drei fast gleiche Läufe überschätzen deshalb die Robustheit.
3. **Bewertung.** Die Genauigkeit allein belohnt Raten (OpenAI 2025). Deshalb wird jetzt auch die fehlerbestrafte
   Bewertung berichtet.
4. **Schwelle.** Die bisherige Schwelle war eine Heuristik auf VALIDATION, ohne Garantie. Ersetzt durch Learn-then-Test.
5. **Ungesehene Formen** messen lexikalischen Transfer („wiegt“ ↔ „wie schwer“). Den kann ohne Sprachvorwissen kein
   Modell leisten; das zeigen Zyklen 2 und 5. Es ist eine Grenze der Eingaben, keine der Architektur. Das ist I2.
6. **Abhängigkeit.** Fälle aus derselben Ziehung teilen sich Entitäten. Deshalb wurde die Garantie zusätzlich über
   200 zufällige Neuaufteilungen ganzer Ziehungen geprüft.

## 3. Meilenstein I1: Abnahmekriterien

| Kriterium | Stand |
|---|---|
| Antwort = exakter Quelltext-Ausschnitt mit Offsets, gelernt | ✓ V8 (Zyklen 1–4) |
| gelernte Enthaltung (keine Antwortregel) | ✓ unbelegte Antworten 61 → 6/140 (argmax) |
| wählbare Fehlergrenze α mit Garantie P(Fehlerrate ≤ α) ≥ 1 − δ bei austauschbaren Fragen | ✓ `ContinuumCertifiedAbstention` |
| Garantie empirisch geprüft (Holdout + 200 Neuaufteilungen) | ✓ Verletzungen 0 % / 5 % / 3 % bei δ = 10 % |
| einmalige Messung auf eingefrorenem TEST (`974e4ba3…`) | ✓ |
| fail-closed: ohne Zertifikat immer Enthaltung; Zertifikat an Checkpoint-SHA gebunden | ✓ Test |
| reproduzierbar, Tests grün | ✓ 16/16 JUnit (kotlinc 2.0.20, JDK 21) |
| keine Promotion, SWB1/CDX1 unberührt | ✓ |

## 4. Verfahren

- **Risiko** pro Frage: R(t) = P(das Modell antwortet mit Konfidenz ≥ t **und** die Antwort ist falsch). Das zählt
  falsche Spannen und unbelegte Antworten.
- **Prüfreihenfolge:** Schwellen von streng nach großzügig, also 1,00, 0,99, … bis 0,00 (0,00 = reines argmax).
- **Test je Schwelle:** exakter Binomial-p-Wert P(Bin(n, α) ≤ Fehler(t)).
- **Abbruch:** beim ersten nicht abgelehnten Test. Gewählt wird die großzügigste noch zertifizierte Schwelle.
- **Gewichte:** bleiben unverändert, kalibriert wird nur die Schwelle.
- **Daten:**
  - Kalibrierung: 10 frische Ziehungen der VALIDATION-Verteilung, n = 1.700. Nie der Auswahl-Seed; TRAIN und TEST
    unberührt.
  - Holdout: 10 weitere Ziehungen, n = 1.700.
  - Garantieprüfung: 40 weitere Ziehungen.
- **Modell:** Kandidat V8, Checkpoint `1786fa9e58d2a1262c461bd2d9563cddf5478260ac62da88f160e868c3c3b7ef`, exakt
  reproduziert.

| α (δ = 0,1) | zertifizierte Schwelle | Kalibrierfehler /1.700 | beantwortet | p | Verletzungen in 200 Neuaufteilungen |
|---|---|---|---|---|---|
| 0,10 | 0,00 (argmax reicht) | 80 | 734 | < 0,0001 | 0/200 |
| 0,05 | 0,40 | 70 | 708 | 0,050 | 10/200 (5 %) |
| 0,02 | 0,64 | 26 | 576 | 0,093 | 6/200 (3 %) |

## 5. Ergebnisse

### Holdout (austauschbar, n = 1.700): Garantie eingehalten

| Strategie | Fehlerrate [95 %] | Präzision | score@0,75 |
|---|---|---|---|
| argmax | 4,6 % [3,7; 5,7] | 0,895 | 0,253 |
| α = 0,05 | 4,2 % [3,3; 5,2] | 0,901 | 0,255 |
| **α = 0,02** | **1,5 % [1,0; 2,2]** | **0,954** | **0,274** |

### Eingefrorener TEST, einmal gemessen (n = 264; Verteilungsverschiebung durch neue Werte und zurückgehaltene Satzformen)

| Strategie | richtig /124 | Fehler /264 [95 %] | unbelegt /140 | falsche Spanne | Präzision | score@0,75 | Fehlerrate ungesehene Formen |
|---|---|---|---|---|---|---|---|
| argmax (Zyklus-4-Kandidat) | 99 | 16 = 6,1 % [3,8; 9,6] | 6 | 10 | 0,861 | 0,193 | 14,7 % |
| α = 0,05 (t = 0,40) | 98 | 12 = 4,5 % [2,6; 7,8] | 6 | 6 | 0,891 | 0,235 | 8,8 % |
| **α = 0,02 (t = 0,64)** | **78** | **3 = 1,1 % [0,4; 3,3]** | **2** | **1** | **0,963** | **0,261** | **1,5 %** |

**Deutung:**
- **Empfehlung:** Für einen persönlichen Assistenten α = 0,02. Es hat die beste fehlerbestrafte Bewertung, 96 %
  Präzision und auf TEST 5-mal weniger Fehler als argmax.
- **Kosten:** 21 richtige Antworten weniger, weil das Modell sich bei diesen Fragen enthält.
- **Verteilungsverschiebung:** Auf TEST hielt die Grenze diesmal auch unter der Verschiebung. **Garantiert ist das
  nicht.** Für neue Arten von Fragen muss neu kalibriert und gemessen werden.

## 6. Was I1 ausdrücklich nicht ist

- **Kein Sprung im Sprachverständnis.** Ungesehene Umformulierungen: weiterhin 12/35 richtig bei argmax. Bei α = 0,02
  beantwortet das Modell nur 3 davon (2 richtig) und enthält sich ehrlich.
- **Keine Mehrwort-Antworten.** Zyklus 5 war ein Negativergebnis.
- **Keine Android-Integration, kein APK, kein Gerät.** Ohne SDK ist das `UNAVAILABLE`; eine Integration braucht
  vorher die Besitzerkontrolle.

## 7. Vorschlag für den nächsten Intelligenz-Meilenstein I2: Sprachvorwissen

- **Ziel:** ungesehene Umformulierungen ≥ 24/35 bei zertifiziertem α = 0,02, ohne mehr unbelegte Antworten.
- **Weg:**
  - eine lokale, frei lizenzierte und read-only eingebundene Wort- oder Satzähnlichkeit als gelernte Zusatzeingabe;
  - Herkunft, Lizenz und Offline-Verfügbarkeit werden vorher geprüft; keine bezahlte API;
  - danach erneut mit I1 zertifizieren und einmal auf TEST messen.

## 8. Reproduktion

```sh
cd app/src
kotlinc main/java/at/heinz/deviceai/learning/span/*.kt test/java/at/heinz/deviceai/learning/span/*.kt \
  -cp junit-4.13.2.jar:hamcrest-core-1.3.jar -jvm-target 17 -include-runtime -d csx1.jar
java -cp csx1.jar:junit-4.13.2.jar:hamcrest-core-1.3.jar org.junit.runner.JUnitCore \
  at.heinz.deviceai.learning.span.ContinuumCertifiedAbstentionTest            # 4 Tests, Teil von 16/16
java -cp csx1.jar at.heinz.deviceai.learning.span.ContinuumSpanAbstentionExperiment certify   # Zertifikat, Holdout, TEST
```

## Quellen

1. Abbasi-Yadkori et al., *Mitigating LLM Hallucinations via Conformal Abstention*, https://arxiv.org/abs/2405.01563
2. Angelopoulos et al., *Learn then Test: Calibrating Predictive Algorithms to Achieve Risk Control*, https://arxiv.org/abs/2110.01052
3. Xu, Chen, Xie, Xiong, *Geometry-Calibrated Conformal Abstention for Language Models* (2026), https://arxiv.org/abs/2604.27914
4. OpenAI, *Why language models hallucinate* (2025), https://openai.com/index/why-language-models-hallucinate/
5. Jolicoeur-Martineau, *Less is More: Recursive Reasoning with Tiny Networks*, https://arxiv.org/abs/2510.04871
6. Zweiger et al., *Self-Adapting Language Models (SEAL)*, https://arxiv.org/abs/2506.10943
7. Google Research, *Nested Learning* (NeurIPS 2025), zusammengefasst in https://labs.adaline.ai/p/the-ai-research-landscape-in-2026
8. Zhang, Hu, Lu, Lange, Clune, *Darwin Gödel Machine*, https://arxiv.org/abs/2505.22954 und https://sakana.ai/dgm/
9. Übersichten zu On-Device-Modellen 2026, z. B. https://www.digitalapplied.com/blog/small-language-models-on-device-agents-2026-guide
