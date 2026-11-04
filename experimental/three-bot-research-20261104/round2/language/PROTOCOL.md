# Runde 2: deutsche Besitzzuordnung nach Zustandsänderungen

Status: prospektiv festgelegte Studie `lm1-state-binding-v1`, RESEARCH_SHADOW.
Dieses Protokoll, Datengenerator, Datenhashes und Harness werden vor dem ersten
Trainingsschritt vom Koordinator im Forschungs-PR #509 gebunden. Es erfolgt keine
nachträgliche Änderung von Hyperparametern, Testfällen oder Erfolgsbedingungen.

## Frage und enge Aussagegrenze

Kann der vorhandene eigene 132.160-Parameter-minGRU nach lokalem Training auf
synthetischen deutschen Beispielen den aktuellen Besitzer eines von zwei
Objekten nach null, einer oder zwei Übergaben besser bestimmen?
Die Personen heißen symbolisch A, B, C und D. Es gibt vier bekannte Objekte:
Ball, Ring, Stift, Schlüssel. Der korrekte Zustand wird von einem unabhängigen
Parser/Interpreter aus dem ausgegebenen deutschen Text bestimmt. Dies ist eine
kleine Überschreib-/Abfrageaufgabe, keine allgemeine Simulation oder Logik.

Dieser Versuch ergänzt die Absichtserkennung in #508. Er verändert weder
EOS-Verhalten, Modellgröße, Normierung noch Loss-Maskierung. Die echte originale
LM1-Runtime, der Trainer und der Tokenizer bleiben unverändert auf Commit
30cd3a89810ed14fa84eca95236d3177e016118d gepinnt.

## Daten, Split und Wahrheitsprüfung

Generatorseed: 2026110402. Die erste und zweite Objektzuweisung verwenden zwei
verschiedene Personen. Die weiteren zwei Personen stehen als Empfänger bereit.
Alle 24 Personenpermutationen sind prinzipiell zulässig; Auswahl erfolgt nur
über SHA-256 des Falls und des vorab festgelegten Generatorseeds, nicht durch ein Modell.

| Familie | Ereignisse | Antwortquelle |
| --- | ---: | --- |
| copy | 0 | ursprünglicher Besitzer des erfragten Objekts |
| move_query | 1 | Empfänger des erfragten Objekts |
| move_other | 1 | ursprünglicher Besitzer; nur das andere Objekt wechselt |
| twice_query | 2 | zweiter Empfänger desselben erfragten Objekts |
| query_then_other | 2 | erster Empfänger; letzte Übergabe betrifft Ablenkobjekt |
| other_then_query | 2 | zweiter, für die Frage relevanter Übergabeempfänger |

Die kritischere Teilmenge besteht fest aus move_other und query_then_other.
Die Heuristik „letzte genannte Person“ ist dort falsch. Die zusätzliche
Kontrolle „ursprünglicher Besitzer“ ist bei Übergaben des Frageobjekts falsch.
Beide Heuristiken werden ohne Training exakt mitgemessen.

| Split | Objektpaar-Kombinationen | Fälle | Balance |
| --- | --- | ---: | --- |
| TRAIN | Ball/Ring, Ring/Stift, Stift/Schlüssel, Schlüssel/Ball | 512 | 128 je Antwortbuchstabe |
| DEV | Ball/Stift | 48 | 2 je Familie und Antwort |
| TEST | Ring/Schlüssel | 96 | 4 je Familie und Antwort |

Train-Familiengrößen: copy 64, move_query 96, move_other 96, twice_query 64,
query_then_other 96, other_then_query 96. In jeder Familie gleiche Anzahl A/B/C/D.
Jeder Objektname ist im Training enthalten; DEV/TEST halten vollständige
Zwei-Objekt-Kombinationen zurück. Satzgrammatik und Ereignisfamilien werden
bewusst geteilt. Das ist Kompositionsgeneralisation innerhalb einer festen
Grammatik, kein Test unbekannter deutscher Formulierungen.

Der Generator berechnet Labels aus seiner strukturierten Ereignisbeschreibung.
oracle.py erhält ausschließlich den deutschen Prompt und prüft die Semantik
über einen getrennten Regex-Parser und Zustandsinterpreter. Der behauptete
Absender muss das Objekt tatsächlich besitzen. Doppelte initiale Objekte,
unbekannte Objekte, fehlerhafte Übergaben und Zusatztext werden abgewiesen.
Positive Fälle sowie absichtlich falsche Antworten/ungültige Ereignisse prüfen
den Verifier, bevor Daten erzeugt werden. Jeder erzeugte Fall muss bestehen.
Keine fremden Holdouts oder früheren Modellantworten werden eingelesen.

TEST wird beim Erzeugen semantisch geprüft und gehasht, danach vor der finalen
Freezephase von keinem Modell-/Analyseprozess für Auswahl oder Tuning gelesen.
Die Versiegelung ist eine protokollierte Zugriffstrennung, keine Verschlüsselung.
Keine vollständigen Prompts oder Kombinationen überlappen zwischen Splits.

## Modelle, Varianten und Rechenbudget

Originalcheckpoint:
7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd.
Tokenizer:
94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f.

- Baseline: unveränderter Originalcheckpoint, keine neuen Gradientenupdates.
- mixed: 256 Schritte, Batch 4, dieselben 512 TRAIN-Beispiele jeweils zweimal,
  über alle 1.024 Beispiele mit dem jeweiligen Seed gemischt.
- curriculum: dieselben 1.024 Beispiele desselben Seeds, stabil nach Zahl der
  Ereignisse (0, dann 1, dann 2) sortiert. Innerhalb derselben Schwierigkeit
  bleibt die Reihenfolge der mixed-Liste erhalten. Gleiche Datenmultimenge,
  gleiche Zahl der Inhalts- und EOS-Ziele, gleiche Zahl der Optimierungsschritte.
- Scheduleseeds: 202611041 und 202611042. Jeder Arm startet unabhängig vom
  unveränderten Original. Keine Fortsetzung eines vorherigen Kandidaten.

Vier reale Trainingsläufe, höchstens 1.024 Optimierungsschritte insgesamt.
Originaler LmTrainer: Lernrate 0,001, Weight Decay 0,01, Warmup 16,
Cosine-Schedule über 256 Schritte, Gradient-Clipping 1, zwei Threads.
Vollständige Sequenz BOS + tokenisierter Prompt/Antworttext + EOS; kein
Abschneiden, keine Änderung des Tokenizers, keine Maskierung der Promptverluste.
EOS wird als natürlicher Abschluss gelernt und bei Inferenz nie blockiert.
Die Antwort ist genau ein Buchstabe aus A/B/C/D, ohne Punkt; im Trainingsstring
folgt nach „Antwort:“ ein Leerzeichen und der richtige Buchstabe.

JVM-Heap 1.024 MiB, ActiveProcessorCount 2. Maximal 270 s je Trainingsprozess,
180 s je Evaluationsprozess, 60 s je Kapazitätsprüfung. Gesamter Runner maximal
1.500 s. Keine Downloads, keine APIs, keine GPU, keine Android-Ausführung.
Abbruch bei nicht-finiten Gewichten/Verlusten, Kontextüberschreitung, Zeitlimit,
Identitätsänderung, fehlenden oder widersprüchlichen Pflichtbelegen. Kein
automatisches Nachtrainieren oder Budgetverlängern nach Ergebnissichtung.

## Reihenfolge und unveränderte Kapazitätsbefunde

1. Dateien/Daten/Quellen/Compiler im Vorregistrierungsmanifest hashen und
   Vorregistrierungscommit des Koordinators festhalten.
2. Originale Baseline mit unverändertem LmCapacityBenchmark prüfen; nur gültiger
   PASS erlaubt den Beginn der separat autorisierten begrenzten Trainingsarme.
3. Alle vier Arme vollständig vom Original aus trainieren, jeweils neuen
   Checkpoint schreiben und neu dekodieren. Schritte, echte Zielzahlen, Loss,
   Gewichtsänderungen, Schedule und Checkpointhash protokollieren.
4. Jeden fertigen Kandidaten mit unverändertem LmCapacityBenchmark prüfen.
   Gültiges FAIL bleibt erhalten und bedeutet keine Promotion/Fortsetzung.
   Fehlende/unlesbare/inkonsistente Belege verhindern Freeze und TEST.
5. Alle Checkpoints samt PASS/FAIL-Kapazitätsbelegen und Identitäten einfrieren.
   Keine Auswahl des besten Kandidaten. Baseline, alle festgelegten Arme und
   Seeds werden danach einmalig auf DEV und TEST rein lesend ausgewertet.
6. Auch ein Kapazitäts-FAIL darf im separat definierten Forschungstest nur
   lesend diagnostiziert werden; ein gutes Taskresultat macht ihn nicht zu
   einem zulässigen Produktionsmodell. Kein Training nach diesem Freeze.

## Zwei strikt getrennte Auswertungen

**Primär: vollständige freie Antwort.** Original LmRuntime.generate,
temperature 0, topK 1, maximal 12 neue Tokens, 30.000 ms, kein EOS-Filter.
Genau richtig nur wenn result.text.trim() exakt dem einen richtigen Buchstaben
entspricht. Zusätzlich STOP, Tokenzahl, Sofort-EOS und Promptkürzung berichten.
Ein korrekter Buchstabe mit Zusatztext zählt als falsch. Promptkürzung ist ein
Versuchsfehler. Keine Antwort aus erlaubten Labels nachträglich hineinfiltern.

**Sekundär: bedingte Auswahl aus vier bekannten Antworten.** Für jede mögliche
Fortsetzung „ A“, „ B“, „ C“, „ D“ wird nur die Summe der NLL ihrer Antworttokens
unter dem unveränderten Modell gemessen. Prompttargets werden zur Auswertung
maskiert, EOS wird in diesem Auswahlsignal nicht mitgezählt. Zusätzlich wird
die Wahl mit EOS im Kandidatenscore separat erfasst. Das beeinflusst weder
Training noch freie Inferenz. Tokenisierungs-Präfixstabilität muss für alle vier
Kandidaten gelten; sonst Abbruch. Tokenlängen werden veröffentlicht.
Diese beschränkte Antwortwahl ist keine freie Sprachgenerierung.

Metriken: exakte Fallzahlen und Quoten insgesamt, je Familie, auf kritischer
Teilmenge und je Antwortklasse; mittlere NLL richtiger Antwort; Konfusionsmatrix;
gepaarte Gewinne/Verluste gegenüber Baseline; Heuristikquoten. Zwei Seeds werden
nicht als zwei unabhängige Testmengen zusammengezählt. Keine beste-Seed-Auswahl.

Vorab definierter positiver enger Befund für mixed (primärer Trainingsarm):
auf TEST bei beiden Seeds mindestens 10 Prozentpunkte mehr freie exakte
Antworten als Baseline UND mindestens 50 % frei richtige Antworten auf der
kritischen Teilmenge. Zusätzlich muss die Gesamtquote die beiden deterministischen
Heuristiken übertreffen. Fehlt das, kein Nachweis frei gelernter Besitzzuordnung.
Für bedingte Auswahl gelten dieselben Zahlen separat; ein Erfolg dort wird nur
als „verbesserte Antwortwahl im festen Vokabular“ bezeichnet. Curriculum ist ein
vorab festgelegter sekundärer Vergleich, keine nachträgliche Rettungsvariante.
Die endliche synthetische Menge erlaubt keine Behauptung allgemeiner Intelligenz,
flüssiger Sprache, allgemeiner Zustandssimulation oder Fold7-Tauglichkeit.

## Parallele Projektstände

Live geprüft: #492 HEAD f9a594641f4f2101e84d46baa8dd87e85b5364cd ergänzt gegenüber
30cd3a... nur 21 Dateien unter followup-eos-review; keine Änderung an den hier
benutzten Runtime-/Trainer-/Tokenizerdateien. #508 HEAD
7131c1a50a9c697e481a6debe259758dbf422eef behandelt vier deutsche Werkzeugabsichten
und EOS-/Trainingsdiagnostik. Dessen Daten/Holdouts/Runner werden nicht übernommen.
Diese Studie prüft stattdessen inhaltliche Objekt-Person-Zustandsbindung.
