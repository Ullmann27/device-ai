# Quellenbasis der Cognition-Runde

Geprüft am 4. November 2026. Primärquellen dienen als Inspiration für begrenzte Experimente auf den vorhandenen Continuum-Pfaden. Keine fremden Gewichte, Datensätze oder Quellmodule werden dadurch übernommen.

## Frontier-Referenzen

- [OpenAI: GPT-6 Astra](https://openai.com/index/gpt-6-astra/) beschreibt Vortraining, Reinforcement Learning, Alignment und gemessene Fähigkeiten. Die gelesene Veröffentlichung enthält keinen nachbaubaren vollständigen Modellbauplan. Aus ihren Resultaten folgt kein Continuum-Leistungswert.
- [Anthropic: Transparenzbericht, Fable 5.1 und Mythos 5.1](https://www.anthropic.com/transparency?component=claude-fable-5-and-claude-mythos-5-system-card) beschreibt beide als dasselbe zugrunde liegende Modell mit unterschiedlichen Schutzmechanismen. Vortraining und Nachtraining werden allgemein beschrieben. Dies ist kein offener vollständiger Architektur-/Gewichts-/Trainingsdatensatz.

Eine identische interne Architektur der genannten Anbieter ist mit diesen Quellen nicht reproduzierbar. Die Arbeit bleibt eine überprüfbare Verbesserung von Continuum; Superintelligenz, Bewusstsein und Frontier-Parität sind weder daraus ableitbar noch in dieser Runde gemessen.

## Offene Methoden und abgeleitete Prüfziele

| Primärquelle | Tatsächlicher methodischer Inhalt | Ableitung für diese Runde |
| --- | --- | --- |
| [LongMemEval, Wu et al., ICLR 2025, arXiv 2410.10813v2](https://arxiv.org/abs/2410.10813v2) | Bewertet Abruf, sitzungsübergreifende und zeitliche Schlussfolgerungen, Wissensänderungen und begründete Nichtantworten; trennt Indexierung, Retrieval und Lesen. | Quellenidentität, zeitliche Gültigkeit und Änderungen getrennt prüfen. Gezielte Continuum-Tests sind keine Ausführung des vollständigen LongMemEval. |
| [Reflexion, Shinn et al., arXiv 2303.11366v4](https://arxiv.org/abs/2303.11366v4) | Nutzt sprachliche Rückmeldungen und episodisches Gedächtnis für spätere Versuche, ohne dafür Modellgewichte aktualisieren zu müssen. | Bestehende Erfahrungs- und Rückmeldungspfade auf gültige Belege und Wiederholungen prüfen. Keine Reflexion-Replikation oder Qualitätssteigerung des lokalen Sprachmodells daraus behaupten. |
| [Self-RAG, Asai et al., arXiv 2310.11511](https://arxiv.org/abs/2310.11511) | Trainiert einen Ansatz für bedarfsgerechten Abruf und Bewertung mit speziellen Reflexionstokens. | Relevanz und Belegqualität getrennt vom bloßen Vorhandensein einer Quelle prüfen. Continuum erhält dadurch keine trainierten Self-RAG-Tokens. |

Die dritte Tabellenspalte ist unsere technische Ableitung. Sie ist keine Aussage der Autoren über diesen Code. Gemeldete Modellbenchmarks werden nicht auf das Android-Gerät, den lokalen Gemma-Kern oder die vorhandenen Forschungsranker übertragen.

## Versuchsdisziplin

- Zuerst Fehler an der eingefrorenen C1-Basis reproduzieren; Erwartung und Gegenfälle vor dem Fix festhalten.
- Dieselbe unveränderte Regression nach dem Fix ausführen, außerdem bestehende benachbarte Tests.
- Quelltext, Befehle, Toolversionen und Rohresultate mit Git-Baum und Dateihashes verbinden.
- Für Qualitätsforschung Entwicklung und zurückgehaltene Fälle trennen. Bereits betrachtete Fälle sind keine ungesehenen Holdouts.
- Selbst erzeugte Texte und wiederholte Rückmeldungen dürfen ihre Evidenzklasse nicht durch Wiederholung erhöhen.
- Fehlversuche, Ressourcenabbruch und Nichtausführung getrennt von PASS führen. Einen roten Versuch nicht durch weichere Assertions grün erklären.

Diese Regeln konkretisieren den vorhandenen Experimentvertrag. Es entstehen weder ein zweites Gedächtnis noch ein neuer Scheduler. Die vollständige Geräte- und Modellabnahme bleibt gesondert erforderlich.
