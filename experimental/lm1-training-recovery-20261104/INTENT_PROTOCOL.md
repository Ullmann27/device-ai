# LM1: begrenzter Lernversuch zur deutschen Absichtserkennung

Stand: 2026-11-04. Version: `lm1-intent-study-v1`.

## 1. Behauptung und Grenzen

Der Versuch prüft, ob ausschließlich selbst erzeugte LM1-Gewichte aus neuen,
selbst verfassten Beispielen eine eng begrenzte deutsche Zuordnung lernen.
Er ist ein eigener Forschungsversuch. Auch ein Erfolg macht das Modell weder
zum allgemeinen Sprachkern noch zu einem freigegebenen Werkzeugagenten.
Es werden keine Dateien geöffnet, Recherchen ausgeführt oder Notizen gespeichert.
Produktivtraining, Promotion, Installation und Release sind nicht Teil dieses Versuchs.

| Zielziffer | Eindeutige Absicht |
|---|---|
| `0` | Keine Handlung ausführen; einen noch nicht ausgeführten Auftrag abbrechen |
| `1` | Eine ausdrücklich bezeichnete Datei öffnen oder zur Ansicht aufrufen |
| `2` | Informationen zu einem ausdrücklich genannten Thema suchen |
| `3` | Eine ausdrücklich genannte Information als Notiz merken |

Explizite Korrekturen ersetzen die davor stehende, noch nicht ausgeführte
Anweisung. Ein verneinter Nebenauftrag hebt einen anderen ausdrücklich
erteilten Auftrag nicht auf. Nicht eindeutig auf diese vier Fälle abbildbare
Aufträge gehören nicht zum bewerteten Aufgabenraum.

Der Test untersucht neue Satzvorlagen und neue Gegenstände innerhalb derselben
vier Absichten. Mehrdeutige Aufträge, mehrere gleichzeitig gültige Aktionen,
andere Sprachen, Rechtschreibstörungen, offene Werkzeugwahl und unbekannte
Absichten sind nicht abgedeckt. Insbesondere ist `0` kein nachgewiesener
OOD- oder Sicherheitsdetektor. Für diese Bereiche wird keine Erfolgsrate behauptet.

## 2. Frische Daten und Zugriffstrennung

`make_intent_data.py` enthält eigene deutsche Formulierungen. Es liest weder
vorhandene Trainingskorpora noch Modellgewichte oder einen früheren Holdout.
Die beiden früheren Auswahlquellen — alte Dev-Ergebnisse und der bereits
offengelegte alte Holdout — dürfen keine Kandidatenauswahl oder Abnahme dieses
Versuchs bestimmen. Der unveränderte eigene Ausgangscheckpoint bleibt zulässig.

| Datei | Beispiele | Pro Klasse | Vollständige Vorlagen pro Klasse | Objektgruppen |
|---|---:|---:|---:|---:|
| `train.tsv` | 128 | 32 | 8 | 4 |
| `dev.tsv` | 24 | 6 | 3 | 2 |
| `test.sealed.tsv` | 48 | 12 | 4 | 3 |

Alle Dateien verwenden UTF-8, LF und exakt die Kopfzeile `id\tlabel\tprompt`.
Prompts enthalten keine Tabs oder Zeilenumbrüche. Normalisierte vollständige
Prompts, vollständige Satzvorlagen und Datei-/Themen-/Notizwerte sind zwischen
Splits disjunkt. Alle Objektgruppen kommen innerhalb ihres Splits bei allen
Klassen vor. Klassen, Negationsphänomene und Korrektursemantik werden bewusst
geteilt; gleiche Bedeutungen sind keine identischen Trainingsbeispiele.

Der Generator prüft Größen, Klassenbalance, Dubletten und Split-Trennung.
Er ordnet Zeilen deterministisch über SHA-256 und Seed `20261104`; die
Reihenfolge hängt nicht vom globalen Zufallszustand ab. Das Manifest enthält
Generator- und Dateihashes, Familiennamen, Größen und Klassenhäufigkeiten,
aber keine ausgeschriebenen Testfälle. Es ist zugleich Abschlussmarke der
Erzeugung. Ein unvollständiges Verzeichnis ohne Manifest ist unzulässig.

Die Satzsemantik wird anhand der vorab geschriebenen Vorlagen geprüft; ein
Dublettencheck allein beweist keine semantisch richtigen Labels.
Die Versiegelung ist eine Arbeitsregel, keine kryptografische Geheimhaltung:
Testvorlagen stehen im Generator, werden aber nach Start des Versuchs nicht
an Modellergebnisse angepasst. Die erzeugte Testdatei wird bis zur endgültigen
Auswahl weder als Text geöffnet noch zur Modellentwicklung eingelesen.

Vor jedem Training:

1. Generator, Protokoll und Harness-Code einfrieren.
2. Mit dem festen Seed in ein neues Verzeichnis erzeugen; keine bestehende Datei überschreiben.
3. Alle drei TSVs samt Manifest in einem Git-Commit binden und Commit-SHA sowie Dateihashes protokollieren.
4. Danach nur `train.tsv` und `dev.tsv` für Lernen und Auswahl lesen.

Zulässige Vorprüfung ohne Datendateien:

```sh
python experimental/lm1-training-recovery-20261104/make_intent_data.py --check
```

Erzeugung für den anschließend zu bindenden Versuch:

```sh
python experimental/lm1-training-recovery-20261104/make_intent_data.py --output experimental/lm1-training-recovery-20261104/intent-data --seed 20261104
```

## 3. Vier getrennte Arme

| Arm | Start | Lernen auf neuen Intent-Beispielen |
|---|---|---|
| S0 | Unverändertes eigenes kleines Original | Nein |
| W0 | Faktor-8-Verbreiterung dieses Originals; 6.562.304 Parameter | Nein |
| S1 | Separate Kopie von S0 | Ja |
| W1 | Separate Kopie von W0 | Ja |

W0 muss die bestehende Transformations-/Paritätsprüfung bestehen. Rauschen und
Verbreiterungsseed werden unverändert aus der festgeschriebenen Operation
übernommen und protokolliert. Der Parameterumfang wird aus dem geladenen Modell
gezählt und nicht allein aus einem Dateinamen übernommen. Gewichte, Tokenizer,
Konfiguration, Quellcode, Trainingsfolge und Auswertung erhalten SHA-256-Belege.
Keine fremden vortrainierten Gewichte oder externen KI-Dienste sind zulässig.

S1 und W1 erhalten dieselbe Reihenfolge vollständiger Trainingsbeispiele und
denselben Token-Schedule-Hash. Nach Fehler oder Zeitüberschreitung ist ein
angefangener Trainingsarm kein erfolgreicher Kandidat. Originale bleiben
unverändert; jeder Snapshot erhält einen neuen Pfad und einen eigenen Hash.

## 4. Feste Trainingsregel

| Einstellung | Wert |
|---|---|
| Update-Schritte | 128 |
| Batchgröße | 4 vollständige Beispiele |
| Lernrate | `0.0001` in beiden trainierten Armen |
| Seed | `20261104` |
| Optimierer | Vorhandener `LmAdamW`; zusätzliche Parameter vor Start im Laufmanifest fixieren |
| Checkpoints/Dev-Messungen | Schritte 32, 64 und 128; zusätzlich Ausgangszustände S0/W0 |
| Prozessfrist | Höchstens 600 Sekunden je Trainingsarm |
| JVM-Heap | Höchstens `-Xmx3g` |
| Ausführung | Lokale CPU; Threadzahl im Laufmanifest fixieren, kein externer Trainingsdienst |

Der Modellinput ist exakt `Auftrag: ` + Prompt + `\nAktion:`.
Der Tokenizer bleibt eingefroren. Ziel ist exakt eine Zielziffer und anschließend
das echte EOS-Spezialtoken. Jede der vier separat tokenisierten Ziffern muss
genau ein Token ergeben. Keine Antworten oder EOS-Zeichen als Klartext im Prompt.

Root implementiert den isolierten Harness mit Antwort-Loss. Bei einer
Präfixlänge P einschließlich BOS und einer Antwortziffer A=1 gilt:
`ids = prefix + answer`; die beiden Zielpositionen sind `P-1` für die
Antwortziffer und `P` für EOS. Alle früheren Targets sind `-1` und tragen
nicht zum Loss bei. Der Normalisierer zählt genau zwei gültige Ziele je
Beispiel, also acht je vollständigem Viererbatch. `-1` darf niemals als
Eingabetoken in `gather` gelangen. Das EOS-Ziel darf nicht abgeschnitten werden.

Vor dem ersten Update werden alle Train-/Dev-Folgen gegen die echte
Kontextgrenze geprüft. Testfolgen werden erst nach der Auswahlfestschreibung
beim freigegebenen Testlauf geprüft. Überlange oder fehlerhafte Fälle werden
nicht still gekürzt oder ausgelassen: Die jeweilige Phase scheitert vor ihrer
Ausführung. Diese Prüfung rechtfertigt kein vorzeitiges Öffnen des Tests. Alle Schritte,
Zieltokens, wall-clock-Zeiten, Finite-Prüfungen und Checkpointhashes werden
protokolliert. Die CPU-Zeit ist ein Budget, keine versprochene Laufzeit.

## 5. Kapazitätsprüfung bleibt eigenständig

Vor Trainingsbeginn müssen die unveränderte ursprüngliche Kapazitätsprüfung
und ihre Identitätsbelege für den jeweiligen Startkandidaten bestehen.
`BASELINE_GATE`, fehlender gültiger `lastPassed`, Timeout oder ein anderer
Fehler blockieren das Training. Der neue Intent-Harness umgeht G1 nicht.

Die ursprüngliche Prüfung wird nach dem Training für die ausgewählten
Kandidaten erneut ausgeführt. Ihre ursprünglichen Anforderungen — einschließlich
vollständiger 32-Token-Generationen — werden nicht gelockert. Ein nachträgliches
FAIL bedeutet keine allgemeine Eignung und keine Promotion. Ein guter
Intent-Score kann trotzdem als begrenztes Aufgabenergebnis berichtet werden,
muss dann aber zusammen mit diesem FAIL erscheinen.

Korrektes Digit+EOS im Intent-Versuch ist ausdrücklich kein Bestehen des alten
32-Token-Gates. Die beiden Anforderungen betreffen unterschiedliche Prüfungen.

## 6. Bewertung ohne erzwungene Klassenauswahl

Die Hauptmessung generiert frei über das gesamte Vokabular: Greedy/Temperatur
null, keine Whitelist für `0` bis `3`, keine EOS-Sperre, keine Min-Length und
keine nachträgliche Reparatur der Antwort. Das feste Limit beträgt vier neue
Tokens. Nur genau `[richtiges Zifferntoken, EOS]` ist ein exakter Erfolg.
Falsche Ziffern, ein zusätzliches Leerzeichen, andere Tokens, früheres EOS,
fehlendes EOS, Limitabbruch oder eine andere Stop-Ursache zählen als Fehler.

Für jeden Arm werden mindestens getrennt ausgewiesen:

- Exact-Digit+EOS: richtige Fälle / alle Fälle, zusätzlich je Klasse.
- Vier-mal-vier-Verwechslungsmatrix plus eigene Kategorie für ungültige Ausgaben.
- Antwort-NLL nur am ersten Zieltoken, nicht mit der EOS-NLL vermischt.
- EOS-NLL am echten Ende nach der richtigen Antwort sowie p(EOS) vor der Antwort.
- Anteil vorzeitiger EOS, Anteil fehlender EOS und tatsächlich erzeugte Tokenzahl.
- Unveränderte Kapazitätsresultate sowie Laufzeit und Speicherbelege.

Teacher-forced NLL und gegebenenfalls auf vier Labels beschränkte Accuracy
sind Zusatzdiagnostik. Sie ersetzen niemals die freie Hauptmessung.
Auch längere Ausgaben oder fallender Trainingsloss allein belegen kein Lernen.

## 7. Dev-Auswahl und endgültiges Einfrieren

Für S1 und W1 wird separat unter den vollständig abgeschlossenen und korrekt
gebundenen Snapshots 32/64/128 gewählt: zuerst mehr exakte Dev-Erfolge,
dann geringere Antwort-NLL, bei vollständigem Gleichstand weniger Schritte.
Die Reihenfolge wird nicht nach Betrachtung von Dev-Kurven geändert.
Die separate ursprüngliche Kapazitätsprüfung wird vollständig mitberichtet;
ein nicht allgemein geeigneter Intent-Spezialist erhält dadurch keine Freigabe.

Ein erster Entwicklungshinweis liegt nur vor, wenn W1 auf Dev mindestens
12/24 exakte Erfolge und mindestens vier zusätzliche Erfolge gegenüber W0
erreicht. Fehlt dieser Hinweis, lautet das Ergebnis
`NO_DEVELOPMENT_SIGNAL`: keine Öffnung des Tests und kein Nachjustieren anhand
seiner Ergebnisse. Ein neuer vorab dokumentierter Versuch darf weiter nur
Train/Dev nutzen, solange der Test ungeöffnet bleibt.

Vor der einmaligen Testauswertung wird eine neue Auswahlquittung geschrieben:
alle vier Arm-Hashes, gewählte Schritte, Dev-Metriken, Regel, vollständige
Trainingskonfiguration, Token-Schedule-Hash, Daten-Commit, TSV-Hashes und
Harness-Hash. Erst nach dieser ausdrücklichen Auswahlfestschreibung werden
die vier eingefrorenen Arme einmal auf `test.sealed.tsv` ausgewertet.
Ein Lauf mit noch nicht festgeschriebener oder nachträglich veränderter Auswahl
darf keinen Test lesen. Ein bereits ausgewerteter Test wird nie wieder zur
unabhängigen Bestätigung nach einer Trainingsänderung erklärt.

## 8. Vorab festgelegte Akzeptanz und Interpretation

**Begrenztes Lernen auf unbekannten Aufträgen ist bestätigt**, wenn W1 alle
folgenden Bedingungen erfüllt:

1. Mindestens **36/48** freie Exact-Digit+EOS-Erfolge auf Test.
2. In jeder Klasse mindestens **8/12** exakte Erfolge.
3. Mindestens **acht zusätzliche richtige Fälle** gegenüber jeweils S0 und W0.
4. Alle Eingabe-, Quell- und Gewichtsidentitäten bleiben gültig; kein Testtuning.

Für jeden gepaarten Vergleich werden die Rohzahlen ausgewiesen: nur W1
richtig, nur die Kontrolle richtig, beide richtig und beide falsch. Der Test
enthält 16 Vorlagenfamilien mit jeweils drei Objektgruppen. Diese 48 Fälle
werden deshalb nicht als 48 unabhängige Stichproben für einen Signifikanztest
oder ein allgemeines Konfidenzintervall behandelt. Der Befund beschreibt den
festgelegten vorlagendisjunkten Benchmark; er erlaubt keine statistische
Hochrechnung auf beliebige deutsche Aufträge. Jeder Fall zählt genau einmal;
zusätzliche Decoding-Seeds erzeugen keine zusätzlichen Testfälle.

**Ein zusätzlicher Vorteil des größeren trainierten Modells** wird separat
bewertet: W1 muss S1 um mindestens **vier richtige Fälle** übertreffen und im
gepaarten Rohvergleich einen entsprechenden Nettogewinn zeigen. Das ist die
vorab festgelegte praktische Schwelle dieses Benchmarks, keine universelle
Überlegenheitsbehauptung. Unterschiedliche ausgewählte Trainingsschritte oder Tokenbudgets werden ausdrücklich genannt;
dann ist das ein Vergleich der ausgewählten Trainingsstände und kein isolierter
kausaler Nachweis, dass allein zusätzliche Parameter den Unterschied erzeugen.
Bei gleichen oder besseren S1-Ergebnissen wird kein Nutzen der Größe behauptet.

Alle Zahlen und Fehlerfälle werden berichtet, auch wenn eine Schwelle verfehlt
wird. Erfolg einer Teilmetrik wird nicht nachträglich zur Hauptmetrik erklärt.
Selbst bei erfüllten Lernschwellen bleibt ein fehlgeschlagenes ursprüngliches
Kapazitätsgate sichtbar und blockiert allgemeine Eignung oder Promotion.
Es gibt keinen behaupteten Gewinn an Allgemeinintelligenz, autonomer
Werkzeugkompetenz oder Sicherheitszuverlässigkeit.
