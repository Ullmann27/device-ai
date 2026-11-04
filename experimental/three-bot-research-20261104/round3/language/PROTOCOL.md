# Vorregistrierung Runde 3: minimale Besitzbindung und Lernzielgewichtung

**4. November 2026 · RESEARCH_SHADOW · vor Training einzufrieren und zu veröffentlichen.**

## 1. Frage und begrenzte Aussage

Runde 2 änderte das Ausgabeformat des vorhandenen 132.160-Parameter-LM1, zeigte aber keine belastbare Zuordnung eines Besitzers; selbst die Teilfamilie ohne Übergabe blieb nahe einer konstanten Antwort. Diese neue Studie prüft deshalb genau eine unveränderte Objekt-Person-Bindung. Ein Erfolg wäre lediglich ein Beleg für die Übertragung eines gelesenen Besitzerbuchstabens bei zwei festen Satzformen auf neue Objektwörter. Er wäre kein Beleg für mehrere gleichzeitig gespeicherte Zustände, Übergaben, allgemeines Sprachverständnis oder ein internes Weltmodell.

Die letzte genannte Person ist hier immer die richtige Antwort. Diese deterministische Regel erreicht definitionsgemäß 100 Prozent und ist als Oberkontrolle auszuweisen. Wir fordern nicht, diese perfekte Regel zu schlagen. Der Versuch isoliert das notwendige kleine Kopierminimum, das beim komplexeren Vorgänger noch nicht gelernt wurde.

Hypothese: Bei gleichem Ausgangsmodell, denselben Beispielen, denselben Updates und derselben Reihenfolge lernt der native Kern diese Zuordnung mit auf Antwort und EOS beschränktem Verlust besser als mit vollständigem Textverlust. Diese Gewichtung verändert die Zahl der überwachten Ziele und deren Normalisierung absichtlich; es wird kein identisches Targetbudget behauptet.

## 2. Anschluss an vorhandene Arbeit

Gelesen wurden #508 bei `9ceffc26a2159c8dc726c1f13d2882119159c130`, #492 bei `f2421698c15195505b0632bf40ff596f0ee0e87f` und der Gradientenaudit aus #514 bei `bfed526727041b2581f22b5d613dcb5cd8c188b3`. #508 nutzt bereits Antwort+EOS-Training auf einer Intentaufgabe und untersucht Suffixvarianten. Diese Studie verwendet keine seiner Daten, keine Intentklassifikation, keine Vergrößerung und keine Suffixsuche. Sie vergleicht das Lernziel auf einer getrennten Ein-Bindungs-Aufgabe direkt bei gleicher kleiner Modellgröße. #514 prüft ausgewählte native Ableitungen; maskierte Targets waren ausdrücklich nicht Teil seiner Aussage. Die hier benötigte Masken-/Normierungsprüfung wird separat durchgeführt. Quellenkopien und aktuelle Pfade stehen in `source-check.json`.

Der Originalkern bleibt auf `30cd3a89810ed14fa84eca95236d3177e016118d` gebunden. Der verglichene neue #492-Stand enthält ausschließlich 80 zusätzliche Forschungs-/Gatebelegdateien, keine Änderung der verwendeten Produkt-Runtime.

## 3. Neue Daten und Wahrheit

Zwei feste Templates:

1. `Der Gegenstand {Objekt} gehört Person {A–D}.` gefolgt von Zeilenumbruch und `Besitzer:`.
2. `Person {A–D} besitzt den Gegenstand {Objekt}.` gefolgt von derselben Frageendung.

Die Antwort wird als Leerzeichen und genau ein Besitzerbuchstabe angehängt. Alle Stories enthalten genau eine Person und genau eine Bindung. Satzformen, Labels und Frageendung bleiben zwischen den Splits gleich; die Objektwörter sind disjunkt. Das ist keine Längengeneralisierung oder unbekannte Grammatik.

| Split | Objektwörter | Fälle | Antwortklassen |
|---|---:|---:|---|
| TRAIN | 16 | 128 | je 32 A/B/C/D, beide Templates gleich häufig |
| DEV | 8 | 64 | je 16 A/B/C/D, beide Templates gleich häufig |
| TEST | 8 | 64 | je 16 A/B/C/D, beide Templates gleich häufig |

Die vollständigen Listen stehen vor Training in `generate_data.py` und `data/manifest.json`. Keine alte Testmenge wird importiert. Der neue, unabhängige `oracle.py` liest den tatsächlich erzeugten Text, ermittelt Besitzer und Objekt und prüft die Labels; vier positive und acht negative Kontrollen müssen bestehen. Sämtliche neuen Fälle werden vor Training semantisch geprüft und gehasht. Die Testversiegelung bedeutet keine Modellauswertung vor dem vollständigen Freeze, nicht Verschlüsselung oder Verbot der Daten-Hashkontrolle.

## 4. Vier gepaarte Arme

Der Ausgangscheckpoint ist ausschließlich `7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd`, der Originaltokenizer `94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f`. Kein Kandidat aus Runde 1, Runde 2 oder #508 wird fortgesetzt.

| Faktor | Festlegung |
|---|---|
| Objectives | `full_text` und `answer_eos` |
| Reihenfolge-Seeds | 202611043 und 202611044 |
| Updates / Batchgröße | 256 / 4 |
| Beispielverwendungen | alle 128 Trainingsfälle genau achtmal, insgesamt 1.024 |
| Native Optimierung | originaler `LmAdamW`, Lernrate 0,001, Weight Decay 0,01, Clip 1 |
| Lernratenfolge | Originalform: 16 Warmup-Schritte, dann Cosine mit Gesamtbudget 256 und Endfaktor 0,1 |
| Adapter | gleiche einzelne Ausführungsschleife, native `Tape`, `LmModel` und `LmAdamW`; ein Trainingsthread |
| JVM-Grenzen | 1.024 MiB Heap, höchstens zwei gemeldete Prozessoren |
| Harte Prozessgrenzen | Training 90 s je Arm, Kapazität 60 s, einzelne Auswertung 90 s, Gesamtlauf 600 s |

Pro Seed ist die Beispielsfolge für beide Objectives exakt gleich. Alle vier Arme haben dieselbe Multimenge. Jeder Arm startet frisch aus dem Original. Eine automatische Wiederholung oder Budgeterhöhung bei Timeout ist ausgeschlossen.

`full_text` überwacht wie der native Originaltrainer sämtliche Prompt-, Antwort- und EOS-Ziele. `answer_eos` setzt allein Prompttargets auf `-1` und überwacht Antworttoken plus EOS; die Prompttokens bleiben in den tatsächlichen Eingaben und ihrem rekurrenten Forward erhalten. Jeder Minibatch wird durch seine tatsächliche Zahl aktiver Targets normalisiert. Es gibt kein Maskieren von EOS bei Generierung, keine erlaubte Antwortliste für die freie Ausgabe und keine Veränderung von Modellarchitektur oder Produktquellen.

## 5. Mathematische Vorprüfung ohne Optimizerupdate

Vor Registrierung müssen Kompilierung und folgende Leserechnungen bestehen. Sie führen keine Optimizerupdates durch und verändern keine Originalgewichte:

- Tokenizergrenzen für sämtliche 128 TRAIN-Prompts und A–D sind stabil; jede Antwort besteht aus genau einem Token; vollständige Sequenzen und maximal zwölf neue Tokens passen in Kontext 256.
- Direkte Softmax-Referenz auf einer kleinen Logitmatrix: übersprungene Targetzeilen haben exakt Nullgradient, aktive Zeilen erhalten `(p-one_hot)/aktiveTargets`. Ein absichtlich falscher Volltext-Normalisierer wird erkannt.
- Volltext-Adapterverlust und sämtliche Parametergradienten werden mit separat aus nativen Einzelsequenzen berechneten und nach Zielzahl gewichteten Verlusten/Gradienten verglichen. Feste Grenze für Maximalfehler: `2e-5`; Verlust gegen native `LmModel.nll` mit `2e-5`.
- Für vollständige und maskierte Targets eines synthetischen nativen minGRU werden ausgewählte Koordinaten aller Tensoren gegen zentrale Differenzen eines unabhängigen Double-Forward geprüft. Schritte `1e-5` und `5e-6`, Entscheidung bei `5e-6`, Toleranz `3e-5 + 0.003 * abs(Referenz)`; alle Werte beider Schritte werden erhalten.
- Zusätzlich werden im tatsächlichen Originalmodell an einem festen TRAIN-Beispiel vorab bestimmte Einbettungs-, Normalisierungs- und rekurrente Koordinaten für das maskierte Lernziel mit derselben Double-Referenz geprüft. Die Double-Rechnung liest nur Kopien der Floatgewichte; keine Gewichtsdatei oder nativer Parameter wird dafür überschrieben.
- Checkpointdatei und sämtliche nativen Parametertensoren müssen vor/nach den Prüfungen bytegleich bleiben.

Der Double-Forward wird nachvollziehbar aus dem vorhandenen isolierten #514-Oracle abgeleitet und hier um explizite Eingaben, Targets und aktive-Target-Normalisierung erweitert. Er benutzt weder `Tape` noch native Aktivierungs-/Softmaxkerne. Die Referenz ist eine mathematische Double-Näherung, kein Beweis jeder Float-Rundungsableitung. Die ausgewählten Koordinaten sind keine vollständige Gradientabdeckung.

Scheitert eine Vorprüfung, wird kein Trainingslauf gestartet. Quelländerungen oder mathematische Korrekturen dürfen nur vor der endgültigen Registrierung erfolgen; Fehlbelege bleiben erhalten. Toleranzen werden nicht nach Ergebnissen erweitert.

## 6. Reihenfolge, Gates und Freeze

1. Alle Daten, Programme, Bibliotheksidentitäten, Vorprüfungen und dieses Protokoll werden gehasht und auf dem Forschungsbranch veröffentlicht.
2. Erst nach bestätigtem prospektivem Commit beginnt der Runner. Die unveränderte originale Baseline-Kapazitätsprüfung muss PASS ergeben.
3. Vier vollständige Trainingsarme in fester Reihenfolge: je Seed `full_text`, danach `answer_eos`. Jeder gespeicherte Checkpoint wird erneut geladen und geprüft.
4. Nach jedem Arm folgt die unveränderte eigene Kapazitätsprüfung. Ein gültiges FAIL bleibt erhalten und verhindert jede Promotion oder Fortsetzung dieses Kandidaten. Es ist gemäß dieser Forschungsvorregistrierung nur eine anschließende lesende Diagnose erlaubt. Fehlende, veränderte oder unvollständige Pflichtbelege blockieren Freeze und Endauswertung.
5. Sämtliche fünf Modellidentitäten und alle Kapazitätsbelege werden zusammen überprüft und eingefroren. Es gibt keine Auswahl des besten Kandidaten.
6. Erst danach erfolgen einmalig DEV und TEST für das Original sowie alle vier Kandidaten. Vor jedem Auswertungslauf werden alle eingefrorenen Modelle und Belege erneut geprüft. Danach gibt es kein Training und keine Veränderung der registrierten Bedingungen.

Die originale Kapazitätsprüfung verlangt weiterhin ihren vollständigen 32-Token-Arbeitsumfang. Ein korrekt beendeter Ein-Buchstaben-Task ersetzt diesen Arbeitsumfang nicht. Gate- und Aufgabenergebnis werden getrennt berichtet.

## 7. Messung und vorab festgelegte Entscheidungen

**Primärmessung:** Originale freie Generierung, Temperature 0, Top-k 1, höchstens zwölf neue Tokens. Ein Treffer verlangt zugleich: getrimmter vollständiger Text entspricht genau dem richtigen Buchstaben, genau ein Inhaltstoken wurde erzeugt und der normale Stopgrund ist EOS. Zusatztext, Tokenlimit, Timeout oder direktes EOS ohne Buchstaben sind kein Treffer. TIME oder CANCELLED machen den betreffenden Lauf unvollständig, nicht bloß fachlich falsch.

**Sekundär:** reine Antwort-NLL für A–D und daraus die bedingte Wahl; Antwort+EOS-NLL separat. Dies ist ein Ranking eines bekannten Antwortalphabets und wird nicht mit freier Generierung gleichgesetzt. Berichtet werden Rohfälle, Antwortverteilung, je Template, je Antwortklasse und gepaarte Gewinne/Verluste.

**Enger Kopierfähigkeitsbefund:** Beide `answer_eos`-Seeds müssen auf TEST mindestens 90 Prozent freie strikte Treffer und mindestens 75 Prozent in jeder der vier Antwortklassen erreichen. Bei 64 Testfällen heißt dies mindestens 58/64 und jeweils mindestens 12/16 je Klasse. Zusätzlich muss jeder Seed gegenüber der unveränderten Baseline mindestens 25 Prozentpunkte gewinnen. Das Kriterium wird auch deskriptiv für die Volltextarme ausgewiesen, aber kein Seed nachträglich ausgewählt.

**Separater Objective-Vorteil:** Jeder `answer_eos`-Seed muss mindestens zehn Prozentpunkte mehr freie strikte Treffer als der gepaarte `full_text`-Seed erzielen. Ein erfülltes Kopierkriterium ohne diesen Unterschied belegt keinen Vorteil der Loss-Maskierung. Umgekehrt reicht ein Unterschied allein nicht für brauchbare Kopierfähigkeit.

DEV ist eine zusätzliche festgehaltene Diagnose und wird erst nach Freeze ausgewertet; es steuert keine Auswahl, Lernrate oder Stoppentscheidung. Alle Schwellen sind deskriptive Kriterien dieses kleinen Piloten, keine populationsweite statistische Garantie. Die zwei Seeds ändern die Reihenfolge, nicht die initialen Gewichte.

## 8. Ressourcen und Übergabe

Keine Fremdgewichte, keine kostenpflichtige API, kein Produkttraining, keine S01-/16M-Wiederholung, keine Appänderung oder Gerätebehauptung. Sämtliche eigenen Dateien entstehen ausschließlich im neuen Bereich `research/round3/language`. Registrierte Dateien bleiben danach unverändert. Bericht, Rohbelege, alle vier Forschungscheckpoints sowie ein vollständiges Manifest werden an Root zur Veröffentlichung übergeben. Ein unabhängiger Belegaudit bindet Modellnamen, Arm-/Seed-Paare, alle zehn Receipts, Fallmengen und Entscheidungskardinalitäten ausdrücklich, damit die bekannte `all([])`-Lücke aus Runde 2 hier nicht wiederkehrt.
