# Runde 2: Ausgabeformat gelernt, Besitzzuordnung nicht nachgewiesen

**Projekt:** Device AI Continuum / Ullmann27/device-ai. **Datum:** 4. November 2026.
**Status:** `RESEARCH_SHADOW`; abgeschlossen, keine Modellwahl und keine Promotion.

## 1. Ergebnis und Entscheidung

Der vorhandene eigene **132.160-Parameter-LM1** wurde tatsächlich auf einem neuen, unabhängig überprüften deutschen Besitzdatensatz weitertrainiert. Vier vorab festgelegte Läufe verarbeiteten jeweils **256 Optimierungsschritte und 59.274 Tokenziele**. Alle Modelle und Pflichtbelege wurden anschließend eingefroren, bevor die zurückgehaltenen Fälle ausgewertet wurden. Der gesamte registrierte Lauf dauerte **92,635 Sekunden** auf dem CPU-Host. [E1–E4]

**Der positive Befund betrifft das Ausgabeformat:** Auf allen 144 neuen DEV-/TEST-Prompts pro trainiertem Modell wurde genau ein Antwortbuchstabe ausgegeben und anschließend regulär mit EOS beendet. Die Baseline gab dagegen jedes Mal dieselbe unpassende Quellenformulierung aus und erreichte das Limit von zwölf Tokens. Es wurde kein EOS blockiert und keine Antwort nachträglich aus einer erlaubten Liste in die freie Ausgabe eingesetzt. [E3, E4]

**Der inhaltliche Befund ist negativ:** Drei trainierte Modelle nennen für jeden Testfall denselben Buchstaben. Das vierte wechselt nur zwischen C und D und erreicht 27 von 96 richtigen Antworten. Bei den entscheidenden Ablenkfällen bleiben alle vier bei **8 von 32**. Die vorher festgelegten Erfolgskriterien für Besitzzuordnung werden weder in freier Generierung noch in bedingter Auswahl erreicht. Ein 0→25-%-Anstieg freier Treffer ist hier ausdrücklich **kein Nachweis von Sprachverständnis oder brauchbarer Zustandsverfolgung**: Die Ausgaben sind jetzt formal passend, aber weitgehend fallunabhängig. [E4]

| Modell | Freie vollständige Antwort, TEST | Bedingte Wahl A/B/C/D | Bedingte Wahl einschließlich EOS-Score | Kritische Fälle: frei / bedingt | Original-Kapazitätsgate |
| --- | ---: | ---: | ---: | ---: | --- |
| Original-Baseline | 0/96 = 0 % | 24/96 = 25 % | 24/96 | 0/32 / 8/32 | PASS |
| mixed, Seed 202611041 | 24/96 = 25 % | 24/96 = 25 % | 24/96 | 8/32 / 8/32 | FAIL: 18 statt 32 Tokens |
| curriculum, Seed 202611041 | 24/96 = 25 % | 24/96 = 25 % | 24/96 | 8/32 / 8/32 | PASS |
| mixed, Seed 202611042 | 27/96 = 28,125 % | 27/96 = 28,125 % | 25/96 | 8/32 / 8/32 | FAIL: 18 statt 32 Tokens |
| curriculum, Seed 202611042 | 24/96 = 25 % | 24/96 = 25 % | 24/96 | 8/32 / 8/32 | FAIL: 31 statt 32 Tokens |

Die einfache Kontrolle „nimm die letzte genannte Person“ erzielt auf demselben Test **59/96 = 61,458 %**, die Kontrolle „nimm den ursprünglichen Besitzer“ **32/96 = 33,333 %**. Beide schlagen alle trainierten Modelle insgesamt. Die erste Heuristik ist auf allen 32 kritischen Ablenkfällen falsch; genau deshalb wurden diese Fälle vorab aufgenommen. [E4]

**Entscheidung:** Keiner der vier Kandidaten wird als gelernte Besitzzuordnung oder als allgemeiner Sprachfortschritt freigegeben. Der eine Kapazitäts-PASS liefert genauso wenig Inhaltsnachweis wie die drei Kapazitäts-FAILs. Eine 16M-Vergrößerung ist durch diesen Befund nicht begründet. Es gibt keine nachgeschobene Trainingsrunde und keine nachträgliche Lockerung der Kriterien.

## 2. Prospektiver Nachweis und unveränderte Methode

Das vollständige Protokoll, der Generator, der unabhängige Verifier, alle Daten, beide Runner, das Kotlin-Harness, das kompilierte JAR und die Vorprüfungsbelege wurden **vor dem ersten Trainingsschritt** vom Koordinator in [Commit 93b43fa2a80109c64c30335149227ccfcebe0f0a](https://github.com/Ullmann27/device-ai/commit/93b43fa2a80109c64c30335149227ccfcebe0f0a) auf dem Forschungszweig zu Draft PR #509 veröffentlicht. Der Lauf verlangt diesen Commit ausdrücklich als Parameter. [E1]

| Beleg | SHA-256 |
| --- | --- |
| Endgültige Vorregistrierung `registration.json` | `b0a70dec25b1b3c8b3aef567f2e03ac4667b886555e6bb3fff7b8fa8426eedcc` |
| Protokoll | `b2b002a0af0ab90598fafaae05471a900eb3094b52aba14a479597abb659bba2` |
| Kotlin-Harness | `e893118937d7056fd7aaf444b366ec33322d3704a9477cf729c931d749aa1eb4` |
| Datenmanifest | `8869d8b5d46987b8220fbf9ac2c272844317e35ec2783b876d92a3059df9fe26` |
| Freeze nach sämtlichen Trainings-/Kapazitätsläufen | `d507835c6703308cc52ded7525fc98f4e90601418f2f6bef4a629b81ffc99ca2` |
| Vollständiger Laufabschluss `completed.json` | `76b86e965d4e188a229434fcbb1d5fc4a56728be77c8cbbc7f4e8e493d4487be` |
| Auswertung `analysis.json` | `17e58f4b0861049b743aa960e3560afe5d002b0ff2e0a552c768eebadc60d89d` |

Eine technische Härtung erfolgte ausdrücklich **noch vor** dieser endgültigen Veröffentlichung: Der Runner prüft alle fünf Checkpoints und sämtliche Nachkapazitätsbelege gemeinsam unmittelbar vor dem Freeze und erneut vor jeder Auswertung. Fehlende oder veränderte Pflichtbelege stoppen den Vorgang. Daten, Protokoll, Hyperparameter und kompilierter Harness blieben dabei unverändert. Das Amendment ist in der Registrierung dokumentiert. Nach der Veröffentlichung wurde keine registrierte Datei geändert. [E1–E3]

Die Runtime bleibt auf dem bereits verifizierten Commit `30cd3a89810ed14fa84eca95236d3177e016118d` gepinnt. Der Live-Vergleich zu #492 HEAD `f9a594641f4f2101e84d46baa8dd87e85b5364cd` zeigte ausschließlich 21 zusätzliche Analyse-/Auditdateien unter `followup-eos-review`; die verwendete Runtime, der Tokenizer und der Trainer wurden dort nicht geändert. #508 am gelesenen Stand `7131c1a50a9c697e481a6debe259758dbf422eef` untersucht Werkzeugabsichten und EOS-/Trainingsdiagnostik. Seine Daten, Holdouts und sein Runner wurden nicht übernommen. Unsere Besitz-/Objektbindung ist der ergänzende Inhaltstest. [Q1, Q2]

## 3. Der neue Datensatz und sein unabhängiger Wahrheitsprüfer

Jede Geschichte nennt zwei Objekte und deren ursprüngliche Besitzer. Die Personen heißen symbolisch A, B, C und D. Es folgen null, eine oder zwei Übergaben, danach die Frage nach dem jetzigen Besitzer eines bestimmten Objekts. Die Antwort ist genau ein Buchstabe. Dies prüft eine eng definierte deutsche Grammatik mit einfachen Zuweisungen und Überschreibungen, keine allgemeine Weltmodellierung. [E1, E2]

Beispiel für die Logik des Aufgabenraums:

> A hält den Ball. B hält den Ring. A gibt den Ball an C. B gibt den Ring an D. Wer hält den Ball? Antwort:

Hier ist C richtig. D ist die zuletzt genannte Person, besitzt aber das andere Objekt. Dieser Unterschied ist der zentrale Schutz gegen eine bloße Letzte-Erwähnung-Heuristik.

### Vorab festgelegte Ereignisfamilien

| Familie | Veränderungen | Erforderliche Information |
| --- | ---: | --- |
| `copy` | 0 | ursprünglichen Besitzer des Frageobjekts wiedergeben |
| `move_query` | 1 | Übergabe des Frageobjekts beachten |
| `move_other` | 1 | Übergabe des anderen Objekts ignorieren |
| `twice_query` | 2 | zweimal überschriebenen Besitzer des Frageobjekts bestimmen |
| `query_then_other` | 2 | relevante erste Übergabe behalten, letzte Ablenkübergabe ignorieren |
| `other_then_query` | 2 | relevante zweite Übergabe erkennen |

`move_other` und `query_then_other` bilden die vorher definierte kritische Teilmenge. Der Generator erzwingt dort eine andere richtige Antwort als die zuletzt genannte Person. Die Sender jeder Übergabe müssen das betreffende Objekt tatsächlich besitzen. [E2]

### Kompositionssplit

| Split | Objektpaare | Fälle | Antwortverteilung |
| --- | --- | ---: | --- |
| TRAIN | Ball/Ring, Ring/Stift, Stift/Schlüssel, Schlüssel/Ball | 512 | je 128 A/B/C/D |
| DEV | Ball/Stift | 48 | je 12 A/B/C/D |
| TEST | Ring/Schlüssel | 96 | je 24 A/B/C/D |

Alle einzelnen Objektwörter sind aus TRAIN bekannt. Die vollständigen Zwei-Objekt-Kombinationen für DEV und TEST wurden zurückgehalten. Pro TEST-Familie gibt es 16 Fälle, davon vier je richtiger Antwort. Im Training wurden die Größen und Klassen ebenfalls vorab festgelegt. **Grammatik und Ereignistypen sind zwischen den Splits absichtlich gleich.** Es wird somit nur die begrenzte Generalisierung auf neue Kombinationen bekannter Objekte geprüft, keine unbekannte deutsche Formulierung und keine längere Ereigniskette. [E1, E2]

Der Generator verwendet Seed `2026110402` und eine deterministische SHA-256-Reihung. Er berechnet die Antwort aus einer strukturierten Ereignisfolge. `oracle.py` liest dagegen ausschließlich den tatsächlich ausgegebenen deutschen Prompt, zerlegt ihn mit einem getrennten Parser und spielt die Zustandsänderungen erneut durch. Falsche Antwort, falscher Besitzer als Absender, doppelte initiale Objekte, unbekanntes Frageobjekt, unzulässige Selbstübergabe und Zusatztext werden zurückgewiesen. **Vier positive und sieben gezielte negative Verifierkontrollen bestanden.** Danach musste jeder erzeugte Fall den unabhängigen Verifier bestehen. [E2]

TEST wurde bei der Erzeugung semantisch geprüft und gehasht, danach aber bis zum vollständigen Modellfreeze nicht vom Evaluationsprozess gelesen. Die Versiegelung war eine dokumentierte Zugriffstrennung, keine Verschlüsselung. Der Runner konnte Hashes kontrollieren, ohne Testresultate auszuwerten. Es gab keine Auswahl oder Hyperparameteranpassung anhand der Endtests. Die frühere Runde, der ursprüngliche Projektdaten-Holdout und die Testdaten aus #508 wurden hierfür nicht eingelesen. [E1–E3]

## 4. Echte Trainingsbedingungen und Vergleichbarkeit

Der ursprüngliche eigene Checkpoint hat SHA-256
`7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd`.
Der unveränderte 512-Token-BPE-Tokenizer hat SHA-256
`94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f`.
Das geladene Modell ist minGRU mit Dimension 64, zwei Schichten, MLP-Breite 256, Kontext 256 und **132.160 real gezählten Parametern**. [E1, E3]

Es wurden die originalen Kotlin-Klassen verwendet, einschließlich `LmTrainer`, `LmAdamW`, `LmModel`, `LmRuntime` und `LmBpeTokenizer`. Es gab keine Portierung, keine simulierten Gradienten, keine Fremdgewichte und keine externen KI-Aufrufe während des Versuchs.

| Einstellung | Vorab festgelegter Wert |
| --- | --- |
| Scheduleseeds | 202611041 und 202611042 |
| Start je Arm | erneutes Laden desselben unveränderten Originalcheckpoints |
| Schritte / Batch | 256 / 4 |
| Datenmenge | alle 512 Beispiele genau zweimal, insgesamt 1.024 Sequenzen |
| Sequenz | BOS + vollständiger Prompt/Antworttext + EOS |
| Lernrate | 0,001 |
| Optimierer | originaler AdamW, Weight Decay 0,01 |
| Schedule / Warmup | originaler Cosine-Schedule, 16 Schritte Warmup |
| Gradient-Clipping | 1 |
| Trainingsthreads / JVM-Prozessoren | 2 / 2 |
| JVM-Heapgrenze | 1.024 MiB |

`mixed` mischt alle 1.024 Sequenzen mit dem jeweiligen Seed. `curriculum` nimmt **genau dieselbe Multimenge** und sortiert sie stabil nach null, einer und zwei Zustandsänderungen. Innerhalb jeder Schwierigkeitsstufe bleibt die gemischte Reihenfolge erhalten. Es ist ausschließlich ein Vergleich der Reihenfolge, kein Vergleich anderer Datenmengen oder längerer Trainingsbudgets. Die Seeds verändern ebenfalls die Reihenfolge, nicht die Startgewichte. [E1, E3]

Alle vier Tokenmultimengen besitzen denselben SHA-256:
`ddf0e7c47cfbcf210821fe6c2b53f2e15ad1663f023a13d67ad870a954ccc8f0`.

| Arm | Schritte | Tokenziele | Davon Antwort / EOS | Geänderte Floats | Reine Trainingszeit |
| --- | ---: | ---: | ---: | ---: | ---: |
| mixed / 202611041 | 256 | 59.274 | 1.024 / 1.024 | 132.160 | 13,619 s |
| curriculum / 202611041 | 256 | 59.274 | 1.024 / 1.024 | 132.160 | 10,292 s |
| mixed / 202611042 | 256 | 59.274 | 1.024 / 1.024 | 132.160 | 11,272 s |
| curriculum / 202611042 | 256 | 59.274 | 1.024 / 1.024 | 132.160 | 14,883 s |

Insgesamt wurden 237.096 Zielpositionen über die vier unabhängigen Läufe optimiert. Das sind wiederholte Verwendungen derselben 512 Geschichten, keine entsprechend große Menge neuer unabhängiger Beispiele. Gewichtänderungen einschließlich Weight Decay beweisen allein keine erworbene Fähigkeit. Jeder neue Checkpoint wurde gespeichert, mit Prüfsumme wieder dekodiert und auf finite Werte geprüft. [E3]

## 5. Präzise Trennung von Generierung und Antwortwahl

### Freie vollständige Antwort

`LmRuntime.generate` lief mit Temperature 0, Top-k 1, maximal zwölf neuen Tokens und normal auswählbarem EOS. Eine Antwort gilt nur dann als korrekt, wenn der komplette Text nach Entfernen äußerer Leerzeichen exakt dem richtigen einen Buchstaben entspricht. Zusätzliche Wörter oder Satzzeichen wären falsch. Kein Prompt wurde gekürzt. [E1, E3]

### Bedingte Wahl

Getrennt davon wurden die vier Fortsetzungen ` A`, ` B`, ` C` und ` D` unter dem unveränderten Modell bewertet. Primär für diese sekundäre Metrik zählt ausschließlich die NLL der Antworttokens, zusätzlich wurde die Variante mit EOS in der Bewertung erfasst. Das ist ein begrenztes Ranking bekannter Antworten, keine freie Sprache.

Vor dem Training wurde für **512 Prompts × vier Antwortkandidaten** geprüft, dass der BPE-Tokenizer den Prompt nicht durch Anhängen einer Antwort verändert. Alle vier Antworten sind jeweils exakt **ein Token**. Damit tritt kein Unterschied durch Antwortlänge auf. Die tatsächlichen Sequenzen haben 35–79 Zielpositionen und passen vollständig in den Kontext. Diese Vorprüfung verhinderte eine häufige Fehlerquelle, bei der Prompt-/Antwortgrenzen durch Tokenisierung verschoben würden. [E1]

### Vorregistriertes Erfolgskriterium

Der primäre Trainingsarm ist `mixed`. Auf dem zurückgehaltenen TEST hätte er bei **beiden** Seeds mindestens zehn Prozentpunkte mehr richtige freie Antworten als die Baseline, mindestens 50 % richtige Antworten auf der kritischen Teilmenge und eine höhere Gesamtquote als beide Heuristiken erreichen müssen. Dasselbe Kriterium wurde gesondert für bedingte Wahl definiert. `curriculum` ist ein vorher festgelegter sekundärer Vergleich. Es wurde kein bester Seed ausgewählt. **Beide Hauptkriterien sind FAIL.** [E1, E4]

## 6. Was sich in den Ausgaben wirklich geändert hat

Die folgenden Verteilungen beziehen sich auf sämtliche 96 Testfälle:

| Modell | Freie Ausgaben nach Entfernen äußeren Leerraums | Abschluss |
| --- | --- | --- |
| Baseline | 96-mal `** Dieser Quellen.\n-` | 96-mal Tokenlimit |
| mixed / 202611041 | 96-mal B | 96-mal EOS nach einem Token |
| curriculum / 202611041 | 96-mal B | 96-mal EOS nach einem Token |
| mixed / 202611042 | 59-mal C, 37-mal D | 96-mal EOS nach einem Token |
| curriculum / 202611042 | 96-mal C | 96-mal EOS nach einem Token |

Bei gleich häufigen richtigen Antworten erzielt jede konstante Antwort zwangsläufig 24/96. Genau dies erklärt die drei 25-%-Resultate; sie sind kein Hinweis auf korrektes Lesen der Geschichte. Die bedingte Wahl der Baseline nennt ebenfalls jedes Mal denselben Buchstaben, hier D. Die 24 korrekten Fälle des auf B gewechselten Modells stehen im bedingten Vergleich **24 verlorenen Fällen** gegenüber: Es wurden andere Fälle richtig, insgesamt aber nicht mehr. [E3, E4]

Der kleine Anstieg auf 27/96 beim zweiten mixed-Seed liegt vollständig in `move_query`: sieben statt vier korrekte Fälle von 16. In jeder anderen Familie, einschließlich `copy` ohne Zustandsänderung, sind es weiterhin vier von 16. Somit ist schon die einfachere Wiederholung einer anfänglichen Objekt-Person-Zuordnung nicht robust gelernt. Die Daten stützen keine Erklärung, nach der erst zwei Veränderungen zu schwierig wären. [E4]

Auf DEV erreichen alle vier trainierten Modelle ebenfalls nur **12/48 = 25 %** in freier und bedingter Messung. Es gibt keinen verborgen gehaltenen positiven Entwicklungsbefund, nach dem eine günstigere Modellvariante hätte gewählt werden können. [E4]

### Warum sinkender Verlust hier nicht als Inhaltsleistung genügt

Die mittlere NLL des richtigen Antworttokens auf TEST sinkt von **4,275312** bei der Baseline auf **1,409650 / 1,400828 / 1,402154 / 1,398519** in der Reihenfolge der vier trainierten Arme. Das ist ein realer Wahrscheinlichkeitsgewinn für das Zielalphabet. Die Werte liegen nahe `ln(4) ≈ 1,386294`, während die Trefferraten nahe der Quote einer konstanten Antwort bleiben. Zusammen mit den tatsächlichen Ausgaben zeigt das eine bessere Format-/Alphabetanpassung, keinen zuverlässigen Zustandsbezug. [E4]

Der originale Trainer optimiert alle Tokens des ganzen Texts. **96,545 % der Zielpositionen gehören zum Prompt**, nur 1,728 % zum eigentlichen Antwortbuchstaben und weitere 1,728 % zu EOS. Deshalb kann ein insgesamt niedriger Sequenzverlust entstehen, obwohl gerade die entscheidende Antwort falsch bleibt. Dies ist eine nachvollziehbare mögliche Erklärung, aber keine in diesem Versuch isoliert bewiesene Ursache. Die Studie verglich weder eine andere Loss-Gewichtung noch eine andere Architektur. [E1, E3]

## 7. Kapazitätsbelege und Forschungsgrenzen

Vor Beginn bestand das Originalmodell das unveränderte `LmCapacityBenchmark`: drei gemessene vollständige 32-Token-Ausgaben; maximale Ein-Token-Aufrufzeit 10,120 ms und maximale 32-Token-Aufrufzeit 40,157 ms. Beobachteter Prozesshöchstwert dieser separaten Inferenz-JVM: 73.008 KiB. [E3]

Nach dem Training lief dieselbe unveränderte Prüfung für jeden Kandidaten in einer frischen JVM. Die gemessenen 32-Token-Anfragen ergaben:

| Modell | Drei Wiederholungen | Befund |
| --- | --- | --- |
| mixed / 202611041 | 18 / 18 / 18 Tokens | gültiges FAIL, `INCOMPLETE_32_TOKEN_WORKLOAD` |
| curriculum / 202611041 | 32 / 32 / 32 Tokens | PASS |
| mixed / 202611042 | 18 / 18 / 18 Tokens | gültiges FAIL, `INCOMPLETE_32_TOKEN_WORKLOAD` |
| curriculum / 202611042 | 31 / 31 / 31 Tokens | gültiges FAIL, `INCOMPLETE_32_TOKEN_WORKLOAD` |

Es gab hier keinen fehlenden ersten Token und keinen Anlass, 31 nachträglich wie 32 zu bewerten. Ein gültiger Kapazitäts-FAIL wurde gemäß dem vorregistrierten getrennten Forschungsprotokoll nur noch lesend auf der Fachaufgabe diagnostiziert. Er erlaubte kein weiteres Training und keine Promotion. Alle folgenden Trainingsarme starteten jeweils unabhängig beim zuvor bestandenen Original. Fehlende/unlesbare Kapazitätsbelege hätten den Freeze und die Endtests blockiert. [E1, E3]

Die Zeit-/Speicherangaben stammen ausschließlich von diesem Linux-/OpenJDK-21-Host. Der Prozessspeicher des Kapazitätsbenchmarks ist keine Messung des Trainings-Spitzenverbrauchs. Es wurde keine APK gebaut, kein Fold7 geprüft und keine Android-Hintergrundfähigkeit nachgewiesen. Auch der einzelne Kapazitäts-PASS bedeutet keine sprachliche Produktreife.

## 8. Reproduzierbarkeit und Qualität der Auswertung

Der tatsächlich ausgeführte Befehl lautete im Studienverzeichnis:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 run_study.py run --registration-commit 93b43fa2a80109c64c30335149227ccfcebe0f0a
PYTHONDONTWRITEBYTECODE=1 python3 analyze_study.py
```

Für eine reine Reproduktion mit denselben registrierten Dateien ist ein neuer Ausgabeordner erforderlich:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 run_study.py run --registration-commit 93b43fa2a80109c64c30335149227ccfcebe0f0a --output run-reproduction
PYTHONDONTWRITEBYTECODE=1 python3 analyze_study.py --run run-reproduction
```

Die ursprünglichen `prepare`-/Generatorbefehle, Compilerargumente, Zeitgrenzen, Exitcodes und Hashes sind in der Registrierung und in den einzelnen Receipts erhalten. Vor jedem Trainings- und Auswertungsschritt wurden die registrierten Dateien und originalen Runtimequellen erneut auf Identität geprüft. Checkpoints, Kapazitätsreceipts und Logs wurden vor Freeze und jeder Endauswertung erneut gemeinsam geprüft. [E1, E3]

Eine unabhängige Quellprüfung des Autonomieagenten identifizierte eine Grenze des **generischen** Analyzers: Seine abschließende `all(...)`-Aggregation bindet das Vorhandensein beider mixed-Arm-/Seed-Metadaten nicht selbst nochmals explizit. Manipulierte Metadaten könnten dadurch theoretisch eine leere Menge als wahr aggregieren. Der eingefrorene Analyzer wurde nach Trainingsbeginn nicht geändert. Im vorliegenden normalen Lauf sind alle fünf Modelle, die vier echten Trainingsarme und beide mixed-Seeds tatsächlich vorhanden, und die gedruckten primären Entscheidungen sind beide **false**. Die genauen Einzelzahlen, Paarvergleiche, Modell-/Seed-Bindungen und Rohfälle müssen daher als Evidenz herangezogen werden; ein nacktes Boolesches Erfolgsflag allein ist kein hinreichender Nachweis.

Der Koordinator hat die tatsächlichen Belege anschließend separat und erfolgreich geprüft: fünf Modelle, zehn eindeutig gebundene Evaluationsreceipts, sämtliche Startheader, Checkpoint- und Datensatzhashes, 720 Fälle und acht TEST-Entscheidungen. Für jede der beiden entscheidenden Metriken sind genau die zwei vorgesehenen mixed-Seeds vorhanden. Beide Erfolgskriterien wurden unabhängig als **false** nachgerechnet. Der [abgeschlossene unabhängige Audit](../verification/language-independent-audit.json) ist PASS. Damit ist die konkrete Ergebnisaussage zusätzlich abgesichert; die allgemeine Analyzer-Grenze bleibt für eine spätere technische Härtung ausdrücklich dokumentiert. [E5]

## 9. Was die nächste Forschung begründen würde

Der Versuch zeigt, dass echte lokale Updates die Ausgabe wirksam verändern können. Unter dem festgelegten Budget entsteht aber kein belastbarer Inhaltstransfer auf neue Objektkombinationen. Das ist eine Begrenzung dieses Trainingsrezepts und dieser Messung; es beweist keine prinzipielle Unfähigkeit von minGRU und keinen grundsätzlichen Mindestparameterumfang.

Ein nächster Versuch müsste eine **neue konkrete Hypothese** prüfen. Der vorhandene Negativbefund bereits in `copy` legt nahe, zuerst die Wiedergewinnung einer einzigen Objekt-Person-Bindung zu untersuchen, bevor mehr Ereignisse oder mehr Parameter eingesetzt werden. Die starke Dominanz der Promptziele und die Frage, ob die benötigte Objektinformation am Antwortzeitpunkt noch im internen Zustand verfügbar ist, sind zwei getrennte mögliche Ursachen. Sie sollten nicht gleichzeitig verändert werden. Antwortmaskierungs-/Intentexperimente des parallelen #508 sollten dabei berücksichtigt werden, um dieselbe Arbeit nicht zu wiederholen.

Der jetzige Endtest ist nach dieser Auswertung verbraucht. Er darf künftig als offen bekannter Regressionstest dienen, aber nicht als unverbrauchter Beleg einer nachträglich daran optimierten Methode. Eine neue Hypothese benötigt vorab neu gebundene Daten und getrennte Endtests. Die vier gespeicherten Checkpoints bleiben ausdrücklich Forschungsartefakte.

## 10. Einordnung anhand von Primärquellen

Li, Guo und Andreas untersuchen in *“(How) Do Language Models Track State?”* das Zusammensetzen von Permutationen durch Transformer-Sprachmodelle und unterscheiden dabei gelernte Mechanismen von einfacheren ausgenutzten Merkmalen. Das unterstützt die methodische Notwendigkeit von Kontrollen gegen Heuristiken. Ihre Architektur, Aufgaben und Nachweise sind aber nicht identisch mit unserem kleinen minGRU-Versuch; deren positive Resultate können nicht auf unsere Gewichte übertragen werden. [P1]

Petty, Merrill und Sabharwal unterscheiden im am **2. November 2026** veröffentlichten Preprint eine Hierarchie von unveränderlichen Zuweisungen über veränderliche und selbstreferenzielle Updates bis zu allgemeiner Zustandsverfolgung. Unsere Beispiele gehören inhaltlich in die einfache Zuweisungs-/Überschreib-/Abfrageklasse mit höchstens zwei Änderungen. Sie prüfen weder allgemeine Simulation noch lange Folgen oder Längengeneralisierung. Auch ein positives Ergebnis wäre entsprechend eng zu benennen gewesen. Der aktuelle Befund ist ohnehin negativ. [P2]

Beide offiziellen Primärseiten wurden für diese Einordnung eingesehen; es wurden weder deren Modelle noch Datensätze heruntergeladen.

## 11. Erzeugte Checkpoints und Hauptdateien

| Forschungscheckpoint | Bytes | SHA-256 |
| --- | ---: | --- |
| `mixed-202611041.lm1` | 529.642 | `52a5bcae7b66c24613e7ac81adaa8d690842b646021ed4acfa453bba83f21176` |
| `curriculum-202611041.lm1` | 529.647 | `bfbf3729676aa4d4a40470e6830942c3a8813b79a4114bcebefb6bc6a4ae850c` |
| `mixed-202611042.lm1` | 529.642 | `57c8ae8b8ad0b81d40a4db00ee7bd4d6484c66c1673bd6e32b12abf03ad48516` |
| `curriculum-202611042.lm1` | 529.647 | `f24680d71ab7bb53cce4bd5d50234c1d0d3b7b37238b610e79c89ae623e11e2e` |

Alle Dateien sind im Abschlussmanifest mit Größe und SHA-256 aufgeführt. Die vier Gewichte sind nicht promovierte, nicht für den Produktivbetrieb empfohlene Forschungsartefakte.

### Evidenz

- **[E1]** [PROTOCOL.md](PROTOCOL.md), [registration.json](registration.json), [Vorprüfungsoutput](build/preflight.stdout); Veröffentlichung vor Training: [Commit 93b43fa2…](https://github.com/Ullmann27/device-ai/commit/93b43fa2a80109c64c30335149227ccfcebe0f0a).
- **[E2]** [generate_data.py](generate_data.py), [unabhängiger Verifier oracle.py](oracle.py), [Datenmanifest](data/manifest.json).
- **[E3]** [completed.json](run-v1/completed.json), [freeze.json](run-v1/freeze.json), sämtliche `run-v1/*-train.stdout`, `*-capacity.stdout`, `*-dev.stdout`, `*-test.stdout` und zugehörige Receipts; [StateLearningStudy.kt](StateLearningStudy.kt), [run_study.py](run_study.py).
- **[E4]** [analysis.json](run-v1/analysis.json), erzeugt mit dem unverändert registrierten [analyze_study.py](analyze_study.py); enthält Einzelgruppen, Konfusionsmatrizen, gepaarte Gewinne/Verluste und vorab definierte Entscheidungen.
- **[E5]** [Unabhängiger abgeschlossener Root-Audit](../verification/language-independent-audit.json), ausgeführt mit [verify_language_results.py](../verification/verify_language_results.py); konkrete Evidenzbindungen und negative Hauptentscheidungen separat nachgeprüft.

### Projektquellen

- **[Q1]** [Live-Quellabgleich](source-check.json); [PR #492](https://github.com/Ullmann27/device-ai/pull/492), gepinnte [LM1-Runtime/Trainerquellen](https://github.com/Ullmann27/device-ai/tree/30cd3a89810ed14fa84eca95236d3177e016118d/app/src/main/java/at/heinz/deviceai/learning/lm).
- **[Q2]** [Intent-Protokoll #508 am gelesenen Commit](https://github.com/Ullmann27/device-ai/blob/7131c1a50a9c697e481a6debe259758dbf422eef/experimental/lm1-training-recovery-20261104/INTENT_PROTOCOL.md).

### Primärliteratur

- **[P1]** Belinda Z. Li, Zifan Carl Guo, Jacob Andreas: *(How) Do Language Models Track State?* ICML 2025, PMLR 267:34429–34452. [Offizielle PMLR-Seite](https://proceedings.mlr.press/v267/li25r.html).
- **[P2]** Jackson Petty, William Merrill, Ashish Sabharwal: *A Fine-Grained Hierarchy and Benchmark for State Tracking in Language Models.* arXiv:2611.03094v1, eingereicht 2.11.2026. [Offizielle arXiv-Seite](https://arxiv.org/abs/2611.03094).
