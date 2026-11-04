# Eigene lokale Sprachmodelle: EOS-Diagnose und funktionserhaltendes Wachstum

**Datum:** 4. November 2026. **Status:** `RESEARCH_SHADOW`, real ausgeführter CPU-/JVM-Versuch. **Projekt:** `Ullmann27/device-ai`, Forschungsbot 2 von drei. **Keine Modellfreigabe, keine Änderung der App, des Trainingsgates oder produktiver Gewichte.**

## 1. Ergebnis und Entscheidung

Die kleine historische Trainingskontrolle wurde mit den originalen LM1-Klassen, dem Originalcheckpoint, dem Originaltokenizer und den 77 ursprünglichen TRAIN-Dokumenten tatsächlich erneut ausgeführt. Bei Seed `20261104` wurden wie im bestehenden Bericht **16 Optimierungsschritte und 3.255 Trainingsziele** verarbeitet. Der feste Kapazitätsprompt erzeugt anschließend tatsächlich **0 Tokens mit EOS**. Offene Satzpräfixe werden dagegen weiter fortgesetzt. Der Befund ist deshalb präziser als die pauschale Formulierung „das Modell ist vollständig in EOS kollabiert“: **Für die kleine Kontrolle ist ein promptabhängiger Wechsel der höchsten Tokenwahrscheinlichkeit an einer Satzgrenze nachgewiesen.** Ein allgemeiner Sofortabbruch bei allen Eingaben ist durch die beobachteten Gegenbeispiele widerlegt. Die großen trainierten 6,56M-/9,71M-Kandidaten wurden in diesem Versuch nicht erneut untersucht. [E1, E2, Q1]

Das ändert die Abnahmeentscheidung nicht: Der trainierte Originalarm erfüllt den geforderten 32-Token-Workload nicht. Der Koordinator hat anschließend das **unveränderte** `LmCapacityBenchmark` unabhängig auf dem erzeugten Checkpoint erneut ausgeführt: Exitcode 1, `pass=false`, `NO_FIRST_TOKEN_OBSERVATION,INCOMPLETE_32_TOKEN_WORKLOAD`, bei drei gemessenen Wiederholungen jeweils 0 Tokens. Es gibt keinen Anlass, dieses Gate rückwirkend abzuschwächen. [E3]

Auch die Baseline ist kein brauchbarer allgemeiner Sprachassistent: Ihre tatsächlichen Fortsetzungen enthalten wiederholte Quellenformulierungen, Pfade, Hashplatzhalter und Datumsfragmente. Eine diagnostische Variante ohne EOS-Trainingsziele liefert am Kapazitätsprompt wieder 32 Tokens, verbessert damit aber **nicht nachweislich** die Sprache. Diese Variante ist ausdrücklich **kein Reparatur- oder Produktionsvorschlag**. EOS wurde bei der Generierung in keinem Arm blockiert. [E1]

Ein zweiter positiver, eng begrenzter Befund ist nachgewiesen: Die vorhandenen Wachstumsoperationen können auf dem echten Checkpoint die Funktion praktisch erhalten. Zweifache Breite mit und ohne paarweises Rauschen blieb über sechs vorab bestimmte Prompts innerhalb der festgelegten numerischen Toleranzen; eine zusätzliche, zunächst neutrale Schicht lieferte exakt gleiche gemessene Logits. **Das schafft zusätzliche trainierbare Kapazität, kein zusätzliches Wissen.** [E1, Q2]

**Empfohlene nächste Entscheidung:** Zuerst Datenziel, Ausgabeformat und sprachliche Abnahme auf dem kleinen Modell verbessern und messen. Eine 16M-Konfiguration erst anschließend als kontrollierten Kapazitätsvergleich prüfen. Die reine Parameterzählung in PR #501 ist kein Beleg für ein trainiertes oder sprachlich brauchbares 16M-Modell. [Q1, Q3]

## 2. Eingefrorener Stand und Identitäten

Der Code stammt aus PR #492, eingefrorener Commit `30cd3a89810ed14fa84eca95236d3177e016118d`. Das frühere separate Gate-Reparatur-PR #497 steht auf `06f071e5850dd405f374bbb58d826f64740d83af`; #492 enthält bereits weitergehende integrierte Absicherungen. Eine weitere Gate-Reparatur wurde hier nicht erfunden. Der Koordinator hat dessen 75 Prüfungen unabhängig erneut bestanden. Diese Aussage über die Gate-Suite stammt aus dem Koordinator-Nachweis, nicht aus einer zusätzlichen Ausführung dieses Forschungsbots. [Q1, Q4]

| Bestandteil | Herkunft / Identität |
| --- | --- |
| Originalcheckpoint | Git-Datencommit `d2304a7658e1256b160c8012e1bac28268ff64a5`; 529.297 Bytes |
| Checkpoint-SHA-256 | `7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd` |
| Originaltokenizer | 2.040 Bytes; 512 Token; BOS 510, EOS 511 |
| Tokenizer-SHA-256 | `94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f` |
| Architektur | minGRU, Dimension 64, 2 Schichten, Kontext 256, **132.160 Parameter** |
| TRAIN-Dokumente | 77 Dokumente aus Git-Corpuscommit `1d92db03b87558c0b330dc584fd1f4c025a93c82` |
| TRAIN-Pfadliste | SHA-256 `bfbb448a46eade19bf947a69e9f35ba5f190b621c460708103ae08e0b13b5e9d` |
| Bereinigter TRAIN-Corpus | 2.520 Segmente, 429.290 UTF-8-Bytes |
| Bereinigter Corpus-SHA-256 | `27e67455e23dcc410855b2fd0f0292f9a00556d72d62aefb018e9eab4be034c9` |
| Diagnoseprompts-SHA-256 | `35fe4c8ac8de447a05b386ffba4c7dd2798a65d86ad2e2489224882352650947` |
| Rohresultat-SHA-256 | `564c6b0c6ccfbe74a865503deb9884096d8415421dfcecf0794ef18a3fabe7a8` |

Alle acht verwendeten Original-Kotlin-Dateien wurden gegen das bereitgestellte `SOURCE_MANIFEST.json` geprüft, unverändert in das Versuchsausgabeverzeichnis kopiert und gemeinsam mit dem neuen Harness kompiliert. Alle 77 Dokumenthashes, die identische Manifestliste, die Pfadidentität und die bestehende TRAIN-Zuordnung wurden vor dem Lauf erneut geprüft. Nach dem Lauf wurden sämtliche Eingabe- und Quellhashes nochmals geprüft. Details stehen im maschinenlesbaren Rohresultat. [E1]

## 3. Quellengebundene Diagnose vor dem Versuch

### Das Eingabeprompt enthält kein automatisch angehängtes EOS

`LmBpeTokenizer.encode(text)` liefert Texttokens. `LmRuntime.generate` fügt BOS hinzu, hängt jedoch kein EOS an das Prompt an. Bei der Auswahl wird BOS blockiert; EOS bleibt regulär auswählbar und beendet die Ausgabe. Das wurde zusätzlich in jeder gemessenen Probe kontrolliert: kein BOS/EOS unter den kodierten Texttokens, keine Promptkürzung, Übereinstimmung zwischen EOS als Argmax und dem tatsächlichen Sofortabbruch. Eine Ursache „EOS versehentlich ans Prompt angehängt“ wird von diesem Code und den Messungen nicht gestützt. [E1, Q5]

### TRAIN lernt Fortsetzung und Segmentende, kein allgemeines Befolgen von Anweisungen

Das bestehende `LmWeightExperiment.train` verwendet `BOS + erste maximal 63 Texttokens`; EOS wird nur angefügt, wenn das ganze Segment höchstens 63 Tokens besitzt. `LmTrainer.step` optimiert alle folgenden Tokens dieser Sequenz. Die 77 Dokumente enthalten unter anderem Repository-Dokumentation, Projektvorgaben und README-Dateien. Dass das Modell bei neuen Alltagspräfixen auf Pfade, Quellen und Statusformulierungen zurückfällt, passt zu dieser Datenverteilung. Diese Passung ist eine Interpretation der beobachteten Ausgaben, kein kausaler Beweis für jede einzelne Fehlfortsetzung. [E1, Q6]

Der Kapazitätsprompt besteht aus **zwei abgeschlossenen Sätzen**:

> Ein kurzer Bericht beschreibt den Ablauf einer Prüfung. Zuerst wird die Aufgabe gelesen, danach wird das Ergebnis geprüft.

Er eignet sich als festgelegter technischer Workload. Ohne weitere Prüfung folgt aus EOS an diesem Prompt jedoch kein allgemeiner Verlust der Fortsetzungsfähigkeit. [Q7]

### Die bestehende BPB-Metrik lässt EOS aus

`LmEval.segmentBits` setzt den letzten EOS-Zielwert auf `-1` und zählt ausschließlich Inhaltstokens. Eine nahezu unveränderte Inhalts-BPB kann deshalb gleichzeitig mit verändertem Abschlussverhalten auftreten. Für die Diagnose wurden zusätzlich `p(EOS)`, EOS-Rang, Logitabstand zum stärksten Nicht-EOS-Token, tatsächliche Ausgabetokens und Abschlussgrund erfasst. `endTargetNll` wird ebenfalls protokolliert; bei offenen Präfixen dient es nur zur Diagnose und ist kein sinnvolles Güteziel für einen erwünschten Abschluss. [E1, Q8]

## 4. Versuchsaufbau und Begrenzungen

Der Versuch verwendet die **echten Kotlin-Klassen**, keine NumPy-Nachbildung und keinen simulierten Trainer. Laufumgebung: Linux x86_64, OpenJDK 21.0.13, Kotlin-Compiler 2.0.20, JVM-Heapgrenze 1.024 MiB, zwei Trainingsthreads. Es gab keine Modell-Downloads, keine externe Trainings-API und keinen GPU-Lauf. Die gesamte Ausführung dauerte **37,238 Sekunden**: 14,035 s Kompilierung, 0,516 s Baseline-Kapazitätsprüfung, 22,631 s Inferenz, Wachstum und neun kleine Trainingsläufe. Zeiten gelten nur für diesen Host. [E1]

Vor jedem Training wurde im Runner zuerst die unveränderte Kapazitätsprüfung des **Originalcheckpoints** verlangt. Sie bestand mit drei vollständigen gemessenen 32-Token-Ausgaben. Größte gemessene Ein-Token-Aufrufzeit: 10,206 ms; größte 32-Token-Aufrufzeit: 28,012 ms; beobachteter Prozess-Höchstwert: 84.356 KiB. Der Runner würde bei fehlendem positiven Baselinebefund abbrechen. Kein Arm beginnt mit einem durchgefallenen trainierten Kandidaten; jeder lädt erneut denselben Originalcheckpoint. Das ist ein begrenzter Diagnoseversuch und keine Ersatzimplementierung des vollständigen Fortsetzungstreibers mit dessen übrigen Gates. [E1]

### Vorab festgelegte Prompts und Datentrennung

Die Datei `probes.tsv` wurde vor der Ausführung angelegt und ihr Hash im Resultat festgehalten. Sie enthält 18 Prompts:

| Gruppe | Umfang | Zweck |
| --- | --- | --- |
| Legacy | 4 | Original-Kapazitätsprompt, ohne Schlusspunkt sowie zwei offene Endungen |
| Development | 6 | Drei neue Satzfamilien, jeweils vollständiger Satz und offenes Präfix: Garten, Fenster, Lampe |
| Test | 6 | Drei andere neue Satzfamilien, ebenfalls vollständig/offen: Schublade, Fluss, Zug |
| Formatdiagnose | 2 | Neue Frage mit bzw. ohne `Frage: … Antwort:`-Markierung |

Development und Test sind nach **Satzfamilien** getrennt. Alle Varianten, Seeds, Lernraten und 16 Schritte standen vor der Messung fest. Es wurde kein Modell anhand der Testtexte ausgewählt oder nach deren Ergebnissen nachtrainiert. Die ursprünglichen VALIDATION-/TEST-Dokumente und der historische neue Holdout wurden nicht zum Training verwendet und in diesem Versuch nicht eingelesen. Der Runner prüft die dokumentbasierte originale TRAIN-Zuordnung. Für die neuen Prompts wurde zusätzlich ein Vorkommen des ganzen Prompttexts in den Trainingssegmenten ausgeschlossen. Gemeinsame Wörter oder alltägliche Satzteile sind damit selbstverständlich nicht ausgeschlossen. Das ist ein kleiner mechanistischer Diagnosebestand, kein repräsentativer Sprachbenchmark. [E1]

### Trainingsbedingungen

Alle Arme: 16 Schritte, Batchgröße 4, AdamW, Lernrate 0,0001, Weight Decay 0,01, Warmup 2, Gradient-Clipping 1, zwei Threads. Seeds `20261104`, `20261105`, `20261106` ändern ausschließlich die Auswahl/Reihenfolge von 64 Segmenten; die Startgewichte bleiben gleich. Es handelt sich **nicht** um drei unabhängige Modellinitialisierungen. [E1]

| Arm | Einzige beabsichtigte Änderung |
| --- | --- |
| `original_eos` | Unverändertes historisches Sequenzrezept |
| `paragraph_end_eos` | EOS zusätzlich nur am tatsächlichen Ende des ursprünglichen Absatzes; prüft die Hypothese künstlicher Segmentenden |
| `no_eos_target_ablation` | Keine EOS-Zielbeispiele in dieser kurzen Fortsetzung; nur diagnostischer Vergleich, keine EOS-Unterdrückung bei Inferenz |

Wichtiges Negativergebnis: In den drei gewählten Schedules gab es **kein** kurzes Segment, dessen EOS durch die zusätzliche Absatzbedingung entfernt worden wäre. `original_eos` und `paragraph_end_eos` besitzen deshalb dieselben Tokenschedules, sämtliche gleichen Verluste und **bitidentische Parameterbytes**. Der Analyzer prüft das nach Entfernung der unterschiedlichen Metadaten. Es gibt neun ausgeführte Trainingsläufe, aber nur **zwei unterschiedliche wirksame Trainingsbedingungen**. Der Absatzarm ist hier eine Nullkontrolle, kein Beleg für eine erfolgreiche Reparatur. [E2]

Die Ablation entfernt 16–26 EOS-Zielpositionen, lässt alle Inhaltstokens und ihre Reihenfolge identisch und verändert dadurch auch den Nenner des mittleren Trainingsverlusts um 0,45–0,80 %. Das verhindert eine völlig reine kausale Zuordnung ausschließlich zum EOS-Gradienten. Eine künftige feinere Loss-Maskierungsstudie müsste den Nenner gesondert kontrollieren. Dafür wurde hier weder der originale Trainer verändert noch ein zusätzlicher Lauf nachgeschoben. [E2]

## 5. Tatsächliche Trainingsmengen

„Tokens“ bezeichnet hier die Zahl der **optimierten nächsten Tokenziele**, einschließlich EOS, soweit vorhanden. 64 Segmente sind keine volle Epoche über die 2.520 Segmente. Die neun kurzen Läufe ergeben zusammen 30.339 optimierte Zielpositionen; wiederholte Inhalte zählen dabei mehrfach. Ein höherer Gesamtzähler darf nicht als Menge unabhängiger neuer Lerninformation ausgegeben werden. [E1]

| Seed | Arm | Schritte | Zielpositionen | Davon EOS | Trainingszeit |
| --- | --- | ---: | ---: | ---: | ---: |
| 20261104 | Original | 16 | 3.255 | 26 | 1,459 s |
| 20261104 | Absatzende-Nullkontrolle | 16 | 3.255 | 26 | 1,234 s |
| 20261104 | Ohne EOS-Ziele | 16 | 3.229 | 0 | 1,421 s |
| 20261105 | Original | 16 | 3.345 | 21 | 1,677 s |
| 20261105 | Absatzende-Nullkontrolle | 16 | 3.345 | 21 | 1,394 s |
| 20261105 | Ohne EOS-Ziele | 16 | 3.324 | 0 | 1,774 s |
| 20261106 | Original | 16 | 3.534 | 16 | 1,698 s |
| 20261106 | Absatzende-Nullkontrolle | 16 | 3.534 | 16 | 1,614 s |
| 20261106 | Ohne EOS-Ziele | 16 | 3.518 | 0 | 1,711 s |

Jeder erzeugte Checkpoint wurde serialisiert, mit Prüfsumme wieder geladen und für die Messung verwendet. Gewichtänderungen sind belegt; wegen Weight Decay sind bloße Zahlen geänderter Floats kein Nachweis erlernter Fähigkeiten. Beim historischen Seed verändert der Originalarm 132.159 von 132.160 Parametern, entsprechend dem früher berichteten kleinen Kontrolllauf. [E1, Q1]

## 6. EOS ist hier eine knappe Rangentscheidung

Für den vollständigen Kapazitätsprompt ergibt sich:

| Zustand | p(EOS) | EOS-Rang ohne BOS | EOS-Logit minus bester anderer Logit | Ausgabetokens |
| --- | ---: | ---: | ---: | ---: |
| Originale Baseline | 0,219758 | 2 | −0,082702 | 32 |
| Originalarm, Seed 04 | 0,226541 | 1 | +0,097137 | 0 |
| Originalarm, Seed 05 | 0,206406 | 1 | +0,030682 | 0 |
| Originalarm, Seed 06 | 0,201666 | 1 | +0,017122 | 0 |
| EOS-Zielablation, Seed 04 | 0,202164 | 2 | −0,003048 | 32 |
| EOS-Zielablation, Seed 05 | 0,187400 | 2 | −0,040649 | 32 |
| EOS-Zielablation, Seed 06 | 0,193865 | 2 | −0,013498 | 32 |

Bei Seed 05 und 06 sinkt p(EOS) sogar gegenüber der Baseline, obwohl EOS zum wahrscheinlichsten Token wird. Die sofortige Beendigung wird damit **nicht** durch eine überall stark ansteigende EOS-Wahrscheinlichkeit erklärt. Greedy-Decoding entscheidet nach dem Rang; ein kleiner Wechsel gegenüber dem konkurrierenden Token reicht aus. [E1]

Beim historischen Seed 04 führt die Entfernung ausschließlich des abschließenden Punktes dazu, dass der trainierte Originalarm zunächst genau einen Punkt erzeugt und dann beendet. Entfernt man dagegen das letzte Satzwort und endet mit „… danach wird das Ergebnis“, folgen 32 Tokens. Auch hier wurde kein EOS blockiert. [E1]

### Neue offene und abgeschlossene Texte

| Zustand | Sofort-EOS: 3 abgeschlossene Development-Sätze | Sofort-EOS: 3 abgeschlossene Testsätze | Sofort-EOS: 6 offene Präfixe |
| --- | ---: | ---: | ---: |
| Baseline | 3/3 | 0/3 | 0/6 |
| Originalarm, Seed 04 | 3/3 | 3/3 | 0/6 |
| Originalarm, Seed 05 | 3/3 | 2/3 | 0/6 |
| Originalarm, Seed 06 | 3/3 | 2/3 | 0/6 |
| EOS-Zielablation, Seed 04 | 3/3 | 2/3 | 0/6 |
| EOS-Zielablation, Seed 05 | 3/3 | 0/3 | 0/6 |
| EOS-Zielablation, Seed 06 | 3/3 | 1/3 | 0/6 |

Bereits die Baseline beendet drei neue abgeschlossene Sätze sofort. Über alle drei trainierten Originalarme gibt es dagegen **keinen Sofortabbruch bei den 18 geprüften offenen Präfixfällen**. Manche Fortsetzungen enden später regulär, andere erreichen das Tokenlimit; „nicht sofort EOS“ bedeutet deshalb weder 32 Tokens in jedem Fall noch sprachliche Richtigkeit. [E1, E2]

Ein typisches echtes Baselineergebnis nach „Im stillen Garten steht ein kleiner“ lautet:

> Quellen.\n- Ein Quellen.\n- Ein Quellen.\n- Ein Quellen.

Auch das einfache Frage-/Antwortpräfix erzeugt keine verifizierte richtige Antwort, sondern ähnliche Quellenformulierungen. Das Modell benötigt einen besseren sprachlichen und aufgabenbezogenen Lernbestand; längere Ausgabe alleine würde diese Schwäche verdecken. [E1]

### Inhaltsverlust und Abschlussverhalten sind verschiedene Messgrößen

Auf den drei abgeschlossenen neuen Testsätzen liegt die Inhalts-BPB bei 3,611250 für die Baseline. Die drei Originalarme ergeben 3,614114, 3,594700 und 3,583558. Wegen des winzigen Diagnosebestands, gleicher Startgewichte und fehlender repräsentativer externer Abnahme ist daraus kein allgemeiner Qualitätsgewinn ableitbar. Gleichzeitig zeigen die Abschlusszahlen eine deutliche Änderung, die Inhalts-BPB definitionsgemäß nicht vollständig abbildet. Ein zukünftiges Abnahmeset sollte Inhaltsvorhersage, berechtigten Abschluss, unerwünschten Sofortabbruch bei offenen Aufgaben und überprüfbare Aufgabenrichtigkeit getrennt messen. Dies ist eine vorgeschlagene **zusätzliche** Diagnostik; das bestehende Gate bleibt erhalten. [E1, E2, Q8]

## 7. Numerisch geprüftes Modellwachstum

Es wurden die vorhandenen Funktionen `widenMinGru` und `deepenMinGru` direkt auf die echten Baselinegewichte angewendet. Keine dieser gewachsenen Varianten wurde in diesem Versuch trainiert. Verglichen wurden die schrittweisen Logits des echten Runtimepfads, die normalisierten nächsten Tokenwahrscheinlichkeiten und die deterministischen 32-Token-Anfragen auf den ersten sechs vorab festgelegten Prompts. Das umfasst vollständige und offene Eingaben. [E1, Q2]

Vorab im Harness gesetzte Grenzen: maximale absolute Logitabweichung ≤ 0,001; maximale absolute Wahrscheinlichkeitsabweichung ≤ 0,00001; identischer Ausgabetext, Tokenzähler und Abschlussgrund auf allen sechs Prompts.

| Operation | Parameter danach | Max. Logitabweichung | Max. Wahrscheinlichkeitsabweichung | Gleiche Generierungen |
| --- | ---: | ---: | ---: | ---: |
| Breite ×2, ohne Rauschen | 460.928 | 0,000006676 | 0,000001013 | 6/6 |
| Breite ×2, paarweises Rauschen, Std. 0,001 | 460.928 | 0,000008583 | 0,000000715 | 6/6 |
| Eine zusätzliche neutrale Schicht | 181.824 | 0 | 0 | 6/6 |

Die Breitenoperation teilt eingehende Beiträge entsprechend der Replikation auf und berücksichtigt die gebundene Ein-/Ausgabeeinbettung über den finalen Normgewinn. Die Vertiefung setzt die neuen Residualausgänge zunächst auf null. Das passt zum Prinzip funktionserhaltender Netzerweiterung. Der Versuch bestätigt die konkrete Implementierung für diese Varianten und Prompts; er ist kein Beweis über alle Eingaben, Wachstum ×8 oder spätere Trainingsstabilität. Vor allem bleibt die anfangs schlechte Sprache dadurch ebenfalls erhalten. [E1, Q2, P2]

## 8. Bedeutung für das eigene 16M-Modell

Die beiden großen Fortsetzungen in #492 sind echte Trainingsläufe mit 6.562.304 beziehungsweise 9.712.128 Parametern und jeweils 16 Schritten. Der dortige Bericht dokumentiert eine schlechtere Development- und neue Holdout-BPB sowie das nicht bestandene Kapazitätsgate. Diese Negativbefunde werden durch den neuen kleinen Mechanismustest nicht aufgehoben. Die hier geprüfte Frage ist enger: Was bedeutet der Sofortabbruch der kleinen Kontrolle bei einem vollständig abgeschlossenen Prompt? [Q1]

Der Helfer #501 zählt eine Konfiguration mit Dimension 512 und fünf Schichten auf 16.011.776 Parameter. Sein Titel und Bericht bezeichnen die Checkpoints ausdrücklich als unbestätigt. Die Zahl ist somit ein Konfigurationsbefund, kein Trainingsnachweis. [Q3]

Eine zweckmäßige Weiterentwicklung folgt deshalb drei getrennten Fragen:

1. **Kann der kleine Kern die gewünschte Sprachaufgabe aus den eigenen Daten überhaupt lernen?** Zuerst klare deutsche Satzfortsetzung und einfache, prüfbare Frage-/Antwortformate. Trainings-, Entwicklungs- und Testfamilien getrennt halten. Dokumente mit Pfaden und Statusvorgaben bilden kein ausgewogenes Alltagsdialog-Curriculum.
2. **Bleibt Lernen bei mehr Kapazität stabil?** Danach kleiner und gewachsener Kern mit identischen Inhaltsdaten, überprüften Abbruchmarkern und vergleichbarem Budget. Logitparität vor dem ersten Update prüfen; nach Training wieder inhaltliche Qualität, Terminierung und Kapazität prüfen. Größer ist kein automatischer Vorteil, insbesondere wenn ein winziges Fortsetzungsbudget und fast unveränderte Daten bestehen bleiben.
3. **Passt die reale Umsetzung auf das Fold7?** Erst separat, am konkreten Gerät, Ladezeit, Speicher, thermische Entwicklung, längere Ausführung und Android-Lifecycle messen. Weder die vorliegenden Hostzeiten noch eine 64-MiB-Dateigrenze sind ein Hardwarebeweis für das Telefon.

Ein eigener lokaler Datenweg ist möglich, ohne in diesem Projekt eine bezahlte Modell-API vorauszusetzen: eigene redigierte Texte, zulässig nutzbare externe Sprachdaten mit dokumentierter Herkunft sowie lokal erzeugte Aufgaben mit unabhängig berechenbaren Lösungen. Beispielsweise können Zahl-/Einheiten-/Formatierungsaufgaben durch deterministische Prüfer ausgewertet werden; sie ersetzen aber kein breites Sprachmaterial. Modellgenerierte Antworten sollten erst nach externer Prüfung in künftige Trainingsbestände gelangen, und Testdaten dürfen dabei nicht zurückfließen. Dies sind Entwicklungsvorschläge, keine bereits ausgeführten Daten- oder Trainingspipelines.

## 9. Primärliteratur und konkrete Übertragungsgrenzen

**TinyStories — Eldan und Li.** Das Paper zeigt, dass stark vereinfachte, zusammenhängende Geschichten einen geeigneteren Lernraum für sehr kleine Sprachmodelle schaffen können. Seine Daten wurden mithilfe von GPT-3.5 und GPT-4 erzeugt. Daraus folgt eine nützliche Datendesign-Hypothese für Continuum: ein enger, sauberer und sprachlich kohärenter deutscher Lernbestand könnte aussagekräftiger sein als mehr Parameter auf heterogener Projektdokumentation. Das Paper belegt keine cloudfreie Erstellung seiner eigenen Daten und keine allgemeine Assistenzleistung unseres Modells. Es wurde hier kein TinyStories-Datensatz heruntergeladen oder trainiert. [P1]

**Net2Net — Chen, Goodfellow und Shlens.** Funktionserhaltende Umformungen können vorhandene Modellinformation in breitere oder tiefere Netze übertragen. Das unterstützt die Zielsetzung der vorhandenen Surgery-Operationen. Die konkrete minGRU-, Normierungs- und gebundene Embedding-Implementierung muss trotzdem numerisch geprüft werden; genau dafür liefert Abschnitt 7 einen realen begrenzten Nachweis. Das Erhalten einer Funktion ist keine Verbesserung dieser Funktion. [P2]

**SmolLM2 — Ben Allal und Mitautoren.** Das Paper dokumentiert ein datenorientiertes, mehrstufiges Trainingsverfahren mit bewusst abgestimmter Mischung und spezialisierten Beständen. Das behandelte Modell hat 1,7 Milliarden Parameter und wurde auf ungefähr 11 Billionen Tokens trainiert. Diese Größenordnung ist kein Rezept für das Fold7 und kein Beleg, dass 16M Parameter dieselbe Leistung bieten. Übertragbar als Forschungsprinzip sind kontrollierte Datenmischungen und getrennte kleine Ablationen vor einer größeren Investition. [P3]

Die Quellen wurden am 4. November 2026 auf den offiziellen Paperseiten eingesehen. TinyStories und Net2Net sind bewusst ältere grundlegende Arbeiten, SmolLM2 ist die jüngere datenorientierte Vergleichsquelle; es wird keine vollständige Übersicht des neuesten Forschungsstands behauptet.

## 10. Reproduktion und Dateien

Aus dem Verzeichnis `research/language`:

```bash
python3 run_experiment.py --output run-repeat
python3 analyze_results.py --run run-repeat
```

`--output` muss ein neues Unterverzeichnis von `research/language` sein. Quellen, Originaldaten und Compilerpfad können mit `--source`, `--inputs` und `--kotlin-lib` angegeben werden. Standardmäßig werden die vom Koordinator materialisierten benachbarten Quellen genutzt. Der Runner verweigert einen falschen Commit, falsche Dateien, andere Dokumentreihenfolge, Holdout-Dokumente im TRAIN-Manifest und vorhandene Ausgabeverzeichnisse. Er enthält keine Download- oder Netzwerkfunktion. Die vollständigen tatsächlich ausgeführten Java-Befehle, Zeitgrenzen und Exitcodes liegen in `run-20261104/commands.json`. Die vier ursprünglichen Quelldateien für Trainer, Tensoren, Tokenizer und Runtime bleiben unverändert. [E1]

| Datei | Inhalt |
| --- | --- |
| `MechanismExperiment.kt` | Neuer begrenzter Diagnose-Harness, drei feste Seeds, feste Trainingsbudgets und Wachstumstoleranzen |
| `run_experiment.py` | Hash-/Datenprüfung, unveränderte Baseline-Kapazitätsprüfung, Kompilierung und Prozesszeitgrenzen |
| `probes.tsv` | Vor dem Lauf festgelegte Diagnoseprompts mit Gruppen |
| `analyze_results.py` | Rein lesende Ergebnisanalyse plus Parametervergleich der Nullkontrolle |
| `run-20261104/results.json` | Vollständige Metadaten, Hashes, Befehle und 195 Ergebnisereignisse |
| `run-20261104/analysis.json` | Gruppierte Kennzahlen, Tokenschedule-Kontrollen und bitgleiche Nullkontrollen |
| `run-20261104/mechanism.stdout` | Vollständiger ursprünglicher JSONL-Output der JVM |
| `run-20261104/baseline-capacity.stdout` | Unveränderte originale Baselineprüfung |
| `run-20261104/frozen-source/` | Acht bytegleiche Original-Kotlin-Dateien |
| `run-20261104/*.lm1` | Neun isolierte Diagnosecheckpoints; keine Freigabekandidaten |
| `run-20261104/schedule-*.txt` | Tatsächlich trainierte Tokenfolgen je Arm und Seed |

Es wurde kein 16M-Modell trainiert, kein Android-Build durchgeführt und kein Fold7-Zustand gelesen. Die Messung belegt native Hostinferenz, kleine echte lokale Gradientenupdates, promptabhängiges Abschlussverhalten und begrenzte Wachstumsparität. Sie belegt keinen autonomen Coding-Agenten, keine allgemeine Intelligenz, keine Hardware-Abnahme und keine erfolgreiche Produktionseinführung.

## Quellen und Evidenz

### Eigene ausgeführte Evidenz

- **[E1]** [Vollständige Rohresultate](run-20261104/results.json), [originaler JVM-Output](run-20261104/mechanism.stdout), [Befehle](run-20261104/commands.json), [Baseline-Kapazitätsnachweis](run-20261104/baseline-capacity.stdout).
- **[E2]** [Maschinenlesbare Auswertung](run-20261104/analysis.json), erzeugt mit [analyze_results.py](analyze_results.py); Parameterhashes der drei bitgleichen Original-/Absatzpaare enthalten.
- **[E3]** Unabhängiger Koordinator-Nachweis: [root-verification.json](../verification/language-root-recheck/root-verification.json), [trained-original-capacity.stdout](../verification/language-root-recheck/trained-original-capacity.stdout).

### Projektquellen

- **[Q1]** [PR #492, historischer Trainings- und Qualitätsbefund, fester Commit](https://github.com/Ullmann27/device-ai/blob/30cd3a89810ed14fa84eca95236d3177e016118d/experimental/weight-space-20261104/CORE_6_10M_RESULTS.md).
- **[Q2]** [LmWeightSurgery.kt, fester Commit](https://github.com/Ullmann27/device-ai/blob/30cd3a89810ed14fa84eca95236d3177e016118d/experimental/weight-space-20261104/LmWeightSurgery.kt).
- **[Q3]** [PR #501: analytischer Parameterzähler, Checkpoints unbestätigt](https://github.com/Ullmann27/device-ai/pull/501), live geprüfter Head `db2c3ba998f480c1213dfbca1aa793899b1d46cd`.
- **[Q4]** [Integrierte Gate-Dokumentation in #492](https://github.com/Ullmann27/device-ai/blob/30cd3a89810ed14fa84eca95236d3177e016118d/experimental/weight-space-20261104/G1-INTEGRATION.md); [separate ältere Reparatur #497](https://github.com/Ullmann27/device-ai/pull/497).
- **[Q5]** [LmRuntime.kt](https://github.com/Ullmann27/device-ai/blob/30cd3a89810ed14fa84eca95236d3177e016118d/app/src/main/java/at/heinz/deviceai/learning/lm/LmRuntime.kt), [LmBpeTokenizer.kt](https://github.com/Ullmann27/device-ai/blob/30cd3a89810ed14fa84eca95236d3177e016118d/app/src/main/java/at/heinz/deviceai/learning/lm/LmBpeTokenizer.kt).
- **[Q6]** [Historisches Trainingsrezept](https://github.com/Ullmann27/device-ai/blob/30cd3a89810ed14fa84eca95236d3177e016118d/experimental/weight-space-20261104/LmWeightExperiment.kt), [TRAIN-Quellmanifest](https://github.com/Ullmann27/device-ai/blob/30cd3a89810ed14fa84eca95236d3177e016118d/experimental/weight-space-20261104/train-source-manifest.json), [LmCorpus.kt](https://github.com/Ullmann27/device-ai/blob/30cd3a89810ed14fa84eca95236d3177e016118d/app/src/main/java/at/heinz/deviceai/learning/lm/LmCorpus.kt).
- **[Q7]** [Unverändertes LmCapacityBenchmark.kt](https://github.com/Ullmann27/device-ai/blob/30cd3a89810ed14fa84eca95236d3177e016118d/experimental/weight-space-20261104/LmCapacityBenchmark.kt).
- **[Q8]** [LmModel.kt mit LmTrainer und LmEval](https://github.com/Ullmann27/device-ai/blob/30cd3a89810ed14fa84eca95236d3177e016118d/app/src/main/java/at/heinz/deviceai/learning/lm/LmModel.kt).

### Primärliteratur

- **[P1]** Ronen Eldan, Yuanzhi Li: *TinyStories: How Small Can Language Models Be and Still Speak Coherent English?* 2023. [Offizielle arXiv-Seite](https://arxiv.org/abs/2305.07759).
- **[P2]** Tianqi Chen, Ian Goodfellow, Jonathon Shlens: *Net2Net: Accelerating Learning via Knowledge Transfer.* 2015/2016. [Offizielle arXiv-Seite](https://arxiv.org/abs/1511.05641).
- **[P3]** Loubna Ben Allal und Mitautoren: *SmolLM2: When Smol Goes Big — Data-Centric Training of a Small Language Model.* 2025. [Offizielle arXiv-Seite](https://arxiv.org/abs/2502.02737).
