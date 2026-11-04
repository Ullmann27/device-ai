# Unabhängiger Finalaudit: LM1-Intent-Studie V3

Stand: 2026-11-04. Geprüfter Finaltest: `training-recovery-study-v3`.
Quellcommit V3: `be4e49600fed1db4c697230bb8e42c150dbfbdaf`.
Veröffentlichter Auswahl-/Freeze-Commit: `9ceffc26a2159c8dc726c1f13d2882119159c130`.

## Urteil

**Die Ergebnisbelege sind konsistent. Beide trainierten Modelle zeigen auf den
vorab getrennten Testfällen messbaren Fortschritt gegenüber ihren untrainierten
Kontrollen. Die registrierten Erfolgskriterien sind trotzdem nicht erfüllt.
Die größere ausgewählte Variante ist auf diesem Test schwächer als die kleine;
beide verfehlen außerdem die unveränderte allgemeine Kapazitätsprüfung.**

Der Audit hat alle **192 Arm-Fall-Urteile gegen dieselben 48 Test-IDs und
Original-Labels** nachgerechnet. Es gibt keine fehlenden oder doppelten Fälle,
keine Labelabweichung und keine abweichende Richtig/Falsch-Wertung. Alle
Klassen-, Format-, Stop- und Summenangaben stimmen mit den Rohdaten überein.
Die maschinenlesbare Ergänzung enthält jedes Einzelurteil, alle vier Matrizen,
gepaarte Vergleiche, Dateihashes und 622 konkrete Konsistenzprüfungen; keine
dieser Prüfungen ist fehlgeschlagen. Diese Zahl bezeichnet Belegprüfungen,
nicht zusätzliche Modelläufe oder zusätzliche unabhängige Testfälle. [A, T]

| Arm | Parameter | Ausgewählter Schritt | Final richtig | Anteil | Korrektes Ziffer-plus-EOS-Format |
|---|---:|---:|---:|---:|---:|
| S0: kleines Original | 132.160 | 0 | 0/48 | 0 % | 0/48 |
| W0: untrainiert verbreitert | 6.562.304 | 0 | 0/48 | 0 % | 0/48 |
| S1: klein trainiert | 132.160 | 512 | **31/48** | **64,58 %** | 48/48 |
| W1: breit trainiert | 6.562.304 | 128 | **25/48** | **52,08 %** | 48/48 |

Die korrekten Antworten auf bisher nicht zum Training oder zur Dev-Auswahl
verwendeten Sätzen sind ein positiver, eng begrenzter Lernbefund. Sie ersetzen
nicht die vorab vereinbarten Mindestwerte und rechtfertigen keine Aussage über
Allgemeinintelligenz, offene Werkzeugwahl oder Produktiveignung. [P, T]

## 1. Prüfverfahren und unveränderte Abnahme

Die freigegebene `test.sealed.tsv` wurde als Wahrheitstabelle für IDs und Labels
eingelesen: 48 verschiedene IDs, vier Klassen mit jeweils zwölf Fällen.
Ihr SHA256 stimmt mit der vor dem Training registrierten Identität, der
Studienwahl, dem Freeze und allen vier `TEST_INPUT`-Datensätzen überein:

`cb75fb0be812116d8bbde95fbe5f82cf8c175d09ba0c49c51090c6cbb3d18a6a`

Für jedes `CASE` wurde die Ausgabe aus Base64 dekodiert. Ein Erfolg verlangt
genau die richtige einzelne Aktionsziffer, genau ein neues Inhaltstoken und
Stop-Grund `EOS`. Zusätzliche Zeichen, fehlendes EOS und Tokenlimit-Abbruch
bleiben Fehler. Es gab keine nachträgliche Antwortbereinigung. Die in den
Quellbelegen festgelegte Generierung bleibt uneingeschränkt über das Vokabular;
eine erzwungene Auswahl aus vier Labels ist nicht die Hauptmessung. [P, H, T]

| Vorab festgelegte Bedingung für W1 | Beobachtet | Erfüllt? |
|---|---|---|
| Mindestens 36/48 exakt richtig | 25/48 | Nein |
| Mindestens 8/12 in jeder Klasse | 6, 9, 7, 3 | Nein; Klassen 0, 2 und 3 verfehlen die Grenze |
| Mindestens acht zusätzliche richtige Fälle gegenüber S0 | +25 | Ja |
| Mindestens acht zusätzliche richtige Fälle gegenüber W0 | +25 | Ja |
| Gültige Identitäts- und Ausführungsbelege | Keine festgestellte Abweichung | Ja im beschriebenen Prüfumfang |

Damit ist `limitedBenchmarkLearningCriterionPassed=false` korrekt.
Die zusätzliche Größenvorteilsregel verlangt mindestens vier richtige Fälle
mehr als S1; W1 erzielt stattdessen sechs weniger. Auch
`widerSelectedCheckpointHasFourCaseAdvantage=false` ist korrekt. [P, S, A]

S1 wird aufgrund des nun bekannten Testergebnisses nicht nachträglich zum
primären Erfolgsmodell erklärt. Unter denselben numerischen Mindestwerten
läge auch S1 mit 31/48 sowie 7/12 in Klasse 0 und 2/12 in Klasse 3 darunter.
Der vorher festgelegte Ausgang dieses Versuchs bleibt negativ.

## 2. Auswahl V3 gegenüber V2: ausschließlich nach Dev

Die Auswahl wurde aus den gebundenen Trainingslogs unabhängig rekonstruiert.
Innerhalb jedes Arms gilt: mehr exakte Dev-Antworten, danach geringere
Antwort-NLL, danach weniger Schritte. Zugelassen sind die trainierten
Snapshots 128, 256 und 512. Beide Studien erfüllen ihr ursprüngliches
Wide-Dev-Gate von mindestens 12/24 und mindestens vier zusätzlichen richtigen
Fällen gegenüber der jeweiligen W0-Kontrolle. [C, D2, D3]

| Studie | Arm | Gewählter Schritt | Dev richtig | Antwort-NLL |
|---|---|---:|---:|---:|
| V2: langer Suffix | S1 | 256 | 13/24 | 1,353089 |
| V2: langer Suffix | W1 | 128 | 13/24 | 1,713391 |
| V3: kurzer Suffix | S1 | 512 | 13/24 | 1,635476 |
| V3: kurzer Suffix | W1 | 128 | 16/24 | 1,652808 |

Die vorher festgelegte Studienwahl vergleicht W1: V3 gewinnt bereits auf dem
ersten Kriterium mit 16 statt 13 richtigen Dev-Fällen. Die NLL- und
V2-Gleichstandsregeln mussten die Studienwahl nicht entscheiden.
Innerhalb V3 erreicht W1 auch bei Schritt 512 genau 16/24, jedoch mit der
schlechteren Antwort-NLL 1,967192; deshalb ist Schritt 128 korrekt gewählt.
Die jeweils vollständigen Vierergruppen wurden aus ihren eigenen Logs
rekonstruiert. Das veröffentlichte Payload übernimmt ausschließlich die
V3-Arme, einschließlich des kleinen Arms und beider untrainierten Kontrollen.
Es gibt keinen Mischsatz aus unterschiedlichen Eingabeformaten. [C, D2, D3, F]

Alle vier finalen Prozesse verwenden `LmIntentSuffixStudy` und damit dasselbe
V3-Eingabeformat `Auftrag: <prompt>\n`. Das gemeinsame Beispiel-Schedule ist
innerhalb und zwischen V2/V3 identisch; die Token-Schedules stimmen innerhalb
der jeweiligen Studie überein und unterscheiden sich erwartungsgemäß zwischen
den beiden Eingabeformaten. Beide Größen jeder Studie haben 512 vollständig
protokollierte Updates und 4.096 überwachte Zielpositionen. [A, D2, D3]

Die ausgewählten Checkpoints haben unterschiedliche Trainingsumfänge:
S1 liegt bei 512 Updates, W1 bei 128. Bei Batchgröße vier entsprechen diese
Snapshots 2.048 beziehungsweise 512 Beispielverwendungen. Beide Arme hatten
zwar denselben maximalen Suchumfang, ihre ausgewählten Trainingsstände aber
nicht dieselbe Updatezahl. Der Finalvergleich ist deshalb der Vergleich
dieser regelkonform ausgewählten Modelle; er isoliert keinen alleinigen
kausalen Effekt der Parameterzahl. [D3, F]

V2 wurde nicht zusätzlich auf dem Finaltest bewertet. Ein abschließender
Testvergleich der beiden Suffixvarianten liegt daher nicht vor. Die günstigere
V3-Dev-Leistung beweist auch nicht, dass ein bestimmter innerer Gate-Mechanismus
die Ursache ist. [P, C]

## 3. Vollständige Verwechslungsmatrizen

Zeilen: tatsächliche Klasse aus der Testdatei. Spalten: erzeugte Aktionsziffer,
sofern das vollständige Format korrekt ist; andernfalls `ungültig`.
Klassen: 0 keine Handlung/Abbruch, 1 Datei öffnen, 2 Informationen suchen,
3 Notiz merken. [T, A]

### S0 — untrainiertes kleines Original

| Soll | Ausgabe 0 | Ausgabe 1 | Ausgabe 2 | Ausgabe 3 | Ungültig |
|---|---:|---:|---:|---:|---:|
| 0 | 0 | 0 | 0 | 0 | 12 |
| 1 | 0 | 0 | 0 | 0 | 12 |
| 2 | 0 | 0 | 0 | 0 | 12 |
| 3 | 0 | 0 | 0 | 0 | 12 |

### W0 — untrainierte breite Kontrolle

| Soll | Ausgabe 0 | Ausgabe 1 | Ausgabe 2 | Ausgabe 3 | Ungültig |
|---|---:|---:|---:|---:|---:|
| 0 | 0 | 0 | 0 | 0 | 12 |
| 1 | 0 | 0 | 0 | 0 | 12 |
| 2 | 0 | 0 | 0 | 0 | 12 |
| 3 | 0 | 0 | 0 | 0 | 12 |

### S1 — kleines Modell, Schritt 512

| Soll | Ausgabe 0 | Ausgabe 1 | Ausgabe 2 | Ausgabe 3 | Ungültig |
|---|---:|---:|---:|---:|---:|
| 0 | **7** | 4 | 1 | 0 | 0 |
| 1 | 0 | **12** | 0 | 0 | 0 |
| 2 | 0 | 2 | **10** | 0 | 0 |
| 3 | 4 | 3 | 3 | **2** | 0 |

### W1 — breites Modell, Schritt 128

| Soll | Ausgabe 0 | Ausgabe 1 | Ausgabe 2 | Ausgabe 3 | Ungültig |
|---|---:|---:|---:|---:|---:|
| 0 | **6** | 0 | 2 | 4 | 0 |
| 1 | 3 | **9** | 0 | 0 | 0 |
| 2 | 0 | 0 | **7** | 5 | 0 |
| 3 | 3 | 0 | 6 | **3** | 0 |

Die Notizklasse bleibt bei beiden besonders schwach: S1 erkennt 2/12, W1
3/12. Auch die Abbruch-/Keine-Handlung-Klasse ist unzuverlässig: S1 verfehlt
fünf, W1 sechs der zwölf Fälle. Die Ausgabeform ist dabei jeweils korrekt;
die Fehler bestehen in der gewählten Absicht. In dieser Forschungsstudie
werden nur Ziffern bewertet und keine entsprechenden Werkzeuge ausgeführt.

## 4. Gepaarte Gewinne und Verluste

Jede Zeile vergleicht dieselben 48 Fälle; die Richtung des Nettowerts ist
immer „erstgenannter Arm minus zweitgenannter Arm“. [A]

| Vergleich | Beide richtig | Nur erster richtig | Nur zweiter richtig | Beide falsch | Nettogewinn des ersten |
|---|---:|---:|---:|---:|---:|
| W1 gegen S1 | 20 | 5 | 11 | 12 | **−6** |
| S1 gegen S0 | 0 | 31 | 0 | 17 | +31 |
| W1 gegen W0 | 0 | 25 | 0 | 23 | +25 |
| W1 gegen S0 | 0 | 25 | 0 | 23 | +25 |
| S1 gegen W0 | 0 | 31 | 0 | 17 | +31 |
| S0 gegen W0 | 0 | 0 | 0 | 48 | 0 |

S1 liegt somit auf diesem Test um sechs Fälle beziehungsweise 12,50
Prozentpunkte vor W1. Die beim Dev-Satz beobachtete Reihenfolge kehrt sich
auf dem Finaltest um: dort hatte W1 16/24 und S1 13/24. Die Auswahl wird
deshalb nicht rückwirkend verändert.

Der Datensatz besteht aus 16 Satzvorlagenfamilien mit je drei Objektgruppen.
Die 48 Fälle werden nicht als 48 unabhängige statistische Ziehungen und die
192 Arm-Fall-Wertungen nicht als 192 verschiedene Testbeispiele ausgegeben.
Es wird keine universelle Signifikanz- oder Allgemeinheitsbehauptung aus den
beobachteten Anteilen abgeleitet. [P, A]

## 5. Ausgabeformat und Wahrscheinlichkeitsdiagnostik

S0 und W0 liefern auf allen 48 IDs dieselben jeweiligen Ausgaben, Tokenzahlen
und Stop-Gründe. Jeder ihrer Fälle läuft nach vier Inhaltstokens ins Tokenlimit;
kein Fall erfüllt die Ziffer-plus-EOS-Vorgabe. S1 und W1 liefern dagegen in
jedem Fall genau eine Aktionsziffer und enden anschließend mit EOS. Es gibt
bei beiden trainierten Armen weder vorzeitiges noch fehlendes EOS auf diesen
Intent-Prompts. [T, A]

| Arm | Antwort-NLL | EOS-NLL nach richtiger Antwort | Mittlere p(EOS) vor der Antwort | Vorzeitiges EOS | Fehlendes EOS |
|---|---:|---:|---:|---:|---:|
| S0 | 4,332935 | 8,031457 | 0,000181208 | 0/48 | 48/48 |
| W0 | 4,332935 | 8,031456 | 0,000181208 | 0/48 | 48/48 |
| S1 | 0,977714 | 0,005597 | 0,000181586 | 0/48 | 0/48 |
| W1 | 1,064273 | 0,017162 | 0,000029706 | 0/48 | 0/48 |

Alle Mittelwerte wurden erneut aus den 48 Einzelfallwerten jedes Arms
berechnet und stimmen mit den `SCORE`-Zeilen überein. Teacher-forced NLL
ist Zusatzdiagnostik; die Hauptwertung beruht weiterhin ausschließlich
auf der frei erzeugten Antwort und ihrem EOS-Abschluss.

## 6. Kapazitätsbelege und allgemeine Eignung

Beide frischen V3-Startkandidaten bestanden das ursprüngliche Kapazitätsgate
vor dem ersten Update. Die ursprünglichen Gate-Logs sind über ihre Hashes an
die Trainingslogs gebunden. Die nachträglichen Prüfungen beziehen sich auf
genau die später eingefrorenen S1-/W1-Hashes. [D3, K]

| Geprüfter V3-Checkpoint | Ersttoken-Beobachtungen | Vollständige 32-Token-Durchläufe | Verhalten | Ergebnis |
|---|---:|---:|---|---|
| S1, Schritt 512 | 0/3 | 0/3 | Sofort EOS, null Inhaltstokens | FAIL |
| W1, Schritt 128 | 3/3 | 0/3 | Genau ein Inhaltstoken; bei 32-Token-Anforderung danach EOS | FAIL |

Bei S1 enden alle acht Warmup-/Messaufrufe sofort mit EOS. Bei W1 enden die
vier Ein-Token-Aufrufe nach einem Token am angeforderten Tokenlimit; die vier
32-Token-Aufrufe enden nach einem Inhaltstoken mit EOS. Die Fehlergründe sind
bei S1 `NO_FIRST_TOKEN_OBSERVATION,INCOMPLETE_32_TOKEN_WORKLOAD`, bei W1 nur
`INCOMPLETE_32_TOKEN_WORKLOAD`. Beide Prozesse enden mit Exitcode 1 ohne Timeout.
Ein korrektes Ziffer-plus-EOS-Format im Intent-Test ersetzt diese fehlende
32-Token-Arbeit nicht. Die Belege enthalten keinen Grund für eine Promotion
zum allgemeinen Sprachkern. [K]

## 7. Quellen-, Auswahl- und Publikationsbindung

Die vollständigen Freeze-Payloads beider Studien wurden aus ihren Trainings-
und Kapazitätsbelegen nachgebildet und stimmen mit der veröffentlichten
Studienwahl überein. Aktiver Freeze, veröffentlichte lokale Freeze-Kopie und
`selectedFreezePayload` sind identisch. `before-test-verification.json` bindet
die tatsächlichen Hashes von Wahl und Freeze und dokumentiert, dass die
Finaltestlogs zu diesem Zeitpunkt noch nicht vorhanden waren. [C, F, B, A]

| Beleg | SHA256 beziehungsweise Commit |
|---|---|
| V3-Quellcommit | `be4e49600fed1db4c697230bb8e42c150dbfbdaf` |
| Veröffentlichter Freeze-Commit | `9ceffc26a2159c8dc726c1f13d2882119159c130` |
| V3-Registrierung | `04392b366746b1e6fca78eb66758e8c99e94b8392dbe013a02d2b416e2184728` |
| V3-JAR | `8075d799b42f3b4f23f2f154af09685f52074503b0a4103ba336a618a3be1950` |
| Studienwahl | `1325d06208d8850e6ce3aa5969d68f37cdaa3e26c46c50f66743ddd3dc442cac` |
| Finaler Freeze | `6c069dd1ab21d792b9985333b14b2d6a0c51b66affbc8a90de0f37092c5a49f4` |

Die V2-/V3-Registrierungen, sämtliche darin gebundenen lokalen Quellen und
Daten sowie die tatsächlich genannten JAR-Dateien wurden gehasht: keine
Abweichung. Alle geprüften Trainings-, Kapazitäts- und Testquittungen stimmen
mit ihren Log-Hashes, Quellcommits, Registrierungen und JARs überein. Die vier
Finalprozesse endeten erfolgreich ohne Timeout innerhalb ihrer Prozessfrist;
jeder hat genau einen `TEST_INPUT`, 48 `CASE`-Datensätze und einen `SCORE`.
Die Eingabe-Identitäten entsprechen jeweils dem eingefrorenen Modell und
dem freigegebenen Testdatensatz. [A, T]

| Arm | Eingefrorener und durch TEST_INPUT bestätigter Checkpoint-SHA256 |
|---|---|
| S0 | `70cb6ed4020042c4ced21bf0c58dab1b83ee2a51655fdbec41279cd961234435` |
| W0 | `af617cf6d223a3e53b44bc3c0578b6924e9c73d8b92f558a981af22990cad04d` |
| S1 | `90b58fb58e048bd4eac967f2b0b131373170201660de7a3d330b393a6c8d820f` |
| W1 | `0f2b5481cf60d6c6021e9e24d1e5ba70d303da274c18933de29c48a47489cf8c` |

Der lokale GitHub-Metadatenexport nennt für den Freeze-Commit den
Commit-Zeitpunkt **19:31:51 UTC**. Der erste Finalprozess startet laut seiner
Quittung um **19:31:59,698108 UTC**, rund 8,698 Sekunden später. Root hat laut
dem mitgelieferten Ablaufbeleg vor diesem Start die erfolgreiche Ref-Aktualisierung
und einen bytegleichen Remote-Abruf des Freezes beobachtet. Der Commit ist
nicht signiert. Diese Metadaten und die beobachtete Ausführungsreihenfolge
werden ausdrücklich nicht als unabhängige kryptografische Zeitbeglaubigung
bezeichnet. Dieser Auditor hat den lokalen Export, die Kopien und die
Zeitangaben abgeglichen; er hat den Remote-Vorgang nicht erneut selbst
ausgeführt. [G, B, T]

## 8. Grenzen dieses Audits und Ende der Bestätigungsrunde

Der Audit führte keine Modelle, Trainings oder neuen Auswertungen aus. Die
Gewichtsdateien und der externe Standardbibliotheks-JAR wurden nicht nochmals
gelesen. Modellidentitäten wurden zwischen den gebundenen Trainings-,
Kapazitäts-, Freeze- und `TEST_INPUT`-Belegen abgeglichen; die ausführbaren
Studien-JARs und registrierten Quellen wurden direkt nachgehasht.

Im geprüften Belegsatz existiert genau ein aufgezeichneter Finaltestaufruf je
Arm. V1 und V2 enthalten keine Finaltestlogs. Dieser Befund ist keine
forensische Ausschlussgarantie für unprotokollierte Prozesse außerhalb der
geprüften Verzeichnisse. Die gesonderten nachträglichen `train-fit-*`-Logs
wurden ausdrücklich nicht als Finaltestfälle gelesen oder gezählt und ändern
keinen Schwellenwert, Checkpoint oder Auswahlentscheid dieses Audits.

**Die Bestätigungsrunde endet mit dem festgestellten negativen Ausgang nach
Vertrag.** Weder ein anderes Wrapper-Ergebnis noch ein späterer Checkpoint
darf nach Betrachtung dieses Tests als neue unabhängig bestätigte Auswahl
ausgegeben werden. Weitere Entwicklungsarbeit kann neue Hypothesen prüfen,
braucht für eine neue unabhängige Bestätigung aber einen neu festgelegten
Bestätigungssatz. Die Originalmodelle bleiben der Rückfallstand; aus diesen
Ergebnissen folgt keine Produktivfreigabe. [P]

## Quellen und maschinenlesbare Ergänzung

- **[A]** [final-case-audit.json](final-case-audit.json): alle 192 Einzelurteile, Matrizen, gepaarte Fall-IDs, rekonstruierte Payloads, Prüfresultate und Dateihashes.
- **[P]** [Suffix-Protokoll V3](../../../experimental/lm1-training-recovery-20261104/suffix-study/SUFFIX_PROTOCOL.md) und [ursprünglicher Intent-Versuchsvertrag](../../../experimental/lm1-training-recovery-20261104/INTENT_PROTOCOL.md).
- **[H]** [Registrierter Suffix-Harness](../../../experimental/lm1-training-recovery-20261104/suffix-study/LmIntentSuffixStudy.kt).
- **[C]** [Veröffentlichte Dev-Studienwahl](final-selection/development-study-choice.json).
- **[F]** [Veröffentlichte lokale Freeze-Kopie](final-selection/selection-freeze.json).
- **[B]** [Lokale Prüfung vor Teststart](final-selection/before-test-verification.json).
- **[G]** [Publikationsmetadaten und Root-Ablaufbeleg](final-selection/publication-metadata.json).
- **[D2]** V2-Trainingslogs: [Small](study-v2/logs/intent-small.tsv), [Wide](study-v2/logs/intent-wide.tsv); zugehörige Quittungen sind im JSON-Katalog gehasht.
- **[D3]** V3-Trainingslogs: [Small](study-v3/logs/intent-small.tsv), [Wide](study-v3/logs/intent-wide.tsv); zugehörige Quittungen und initiale Kapazitätsgates sind im JSON-Katalog gehasht.
- **[K]** V3-Kapazitätslogs nach Training: [Small](study-v3/logs/intent-small-capacity-after.tsv), [Wide](study-v3/logs/intent-wide-capacity-after.tsv).
- **[T]** Freigegebene [Testdatei](../../../experimental/lm1-training-recovery-20261104/intent-data/test.sealed.tsv); Finaltestlogs [S0](study-v3/logs/test-S0.tsv), [W0](study-v3/logs/test-W0.tsv), [S1](study-v3/logs/test-S1.tsv), [W1](study-v3/logs/test-W1.tsv). Quittungen und Log-Hashes sind vollständig im JSON-Katalog enthalten.
- **[S]** [Controller-Zusammenfassung](study-v3/intent-summary.json), deren Werte unabhängig nachgerechnet wurden.
