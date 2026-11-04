# Fortsetzung mit drei Helfern: Studienwahl vor dem Finaltest durchsetzen

Auftrag: `G1-INTEGRATE-WEIGHT-002`, Besitzerfortsetzung „Weiter“ am 04.11.2026.
Status: **REPAIR_AND_EVIDENCE_INTEGRITY_PASS — MODEL_QUALITY_AND_CAPACITY_FAIL**.

Eigener PR: [#492](https://github.com/Ullmann27/device-ai/pull/492).
Ausgangspunkt: `f2421698c15195505b0632bf40ff596f0ee0e87f`.
Vollständig geprüfter Quellen-/Entwicklungsbelegstand aus PR #508:
`9ceffc26a2159c8dc726c1f13d2882119159c130`.
Abschließender Ergebnisnachtrag: `59891cfad6dd015f47c187cc688ea94b0026ad7d`.
Die Reparatur wurde vor dieser Finalnachprüfung in
`e6aa65a81f593107aab07a93694a09685cacb92c` veröffentlicht und danach nicht geändert.

## Ergebnis und konkrete Reparatur

Die separat aufrufbaren v2/v3-Testpfade konsumierten die studienübergreifende
Auswahl und den Vorabprüfbeleg nicht. Ein lokal gültiger Freeze der anderen
Studie konnte deshalb die Prozessstartgrenze erreichen. Das betrifft auch den
bisherigen, unabhängig geprüften Nachkapazitätspatch allein.

Die tatsächlich veröffentlichten Auswahl-, Freeze- und Vorabprüfdateien sind
am geprüften Commit untereinander konsistent: V3 ist korrekt gewählt. Die
Gegenprobe belegt eine fehlende Durchsetzung und keinen beobachteten echten
Test der falschen Studie.

Die Ergänzung verlangt für v2/v3 bei `test` zusätzlich `--development-choice`,
`--before-test-verification` und `--before-test-verification-sha256`. Sie bindet
die gewählte Version, den exakten Freeze, Quellen, Registrierung und die
unveränderte Dev-Rangfolge. Die Freigabe wird vor jedem Testarm und vor der
Summary erneut geprüft. Bereits vorhandene Testlogs oder -receipts sperren
vor dem ersten Arm.

| Geprüfter Stand | Identische 31 Wahlbindungsfälle | Registrierung |
|---|---:|---:|
| Unveränderte Originalrunner | 3 PASS, 28 RED | Nicht nochmals geprüft |
| Bisheriger Kapazitätspatch `c69cb427…` | 3 PASS, 28 RED | 6/6 PASS beim unabhängigen Helfer |
| Neuer kombinierter Patch | **31/31 PASS** | **6/6 PASS** |

Root hat Original, alten Patch und neue Reparatur mit exakt derselben
31er-Probe selbst wiederholt; zusätzlich die sechs Registrierungsfälle der
neuen Reparatur. Ergebnis: **37/37 neue Fälle bestehen**. Nur die
Prozessstartgrenze wird abgefangen; die eigentlichen Selector-, Study-,
Freeze-, Test- und Summarymethoden laufen aus den hashgebundenen Quellen.

Die Registrierungskontrollen weisen für beide neuen Runner alte
Registrierungen sowie neue Registrierungen mit alten Trainingsreceipts
zurück. Eine vollständig neue, konsistente synthetische Bindung besteht.
Historische Belege werden nicht nachträglich umgeschrieben.

## Zusätzliche eigene Verifikation

Root hat außerdem:

- Quellenreview und V3-Rohbelegaudit wiederholt; die Berichte sind bytegleich
  mit den jeweiligen Helferberichten;
- die drei Originalfixtures direkt gegen Git-Blob-Identitäten und SHA-256
  des geprüften Commits verglichen;
- beide Patchvarianten in getrennten frischen temporären Gitbäumen angewendet;
  alle drei Ergebnisdateien entsprechen exakt den geprüften Fixtures;
- Generator und unabhängigen AST-/Textvergleich erneut ausgeführt;
- für beide neuen Runner die CLI-Ablehnung fehlender Freigabeargumente geprüft.

V1 bleibt zum vorherigen Kapazitätspatch vollständig bytegleich. Bei v2/v3
ändern sich nur `test()`, die CLI und die zusätzliche Methode
`study_choice_authorization()`. Alle anderen Study-Methoden bleiben
AST-identisch; der vollständige Kapazitätsvalidator bleibt textgleich.
Qualitätsregeln, Parameterzahlen und Ressourcenbudgets werden nicht verändert.

Die früheren 165 Kapazitätsfälle bleiben der historische Nachweis ihres
Quellstands. Sie wurden nicht erneut ausgeführt oder mit den neuen 37 Fällen
zu einer neuen Gesamtabnahme zusammengezählt. Alte v2/v3-Positivfixtures ohne
die neuen Pflichtargumente sollen jetzt nicht autorisieren.

Nachweise: [Root-Verifikation](root-verification/verification.json),
[unabhängige Tests](independent-tests/README.md),
[Quellen- und Reparaturbericht](source-audit/README.md).

## Neue V3-Entwicklungsbelege

Die Nachrechnung umfasst 60 gepinnte Textquellen, vier Receipt-/Log-Paare,
1.024 protokollierte Updates, 192 Dev-Einzelfälle, vier vollständige
Kapazitätsläufe mit 32 Rohaufrufen und 32 Online-Epochenmittel.

| V3-Befund | Small | Wide |
|---|---:|---:|
| Eigene trainierbare Parameter | 132.160 | 6.562.304 |
| Dev-Treffer bei Schritt 0 / 128 / 256 / 512 | 0 / 11 / 12 / 13 von 24 | 0 / 16 / 14 / 16 von 24 |
| Gewählter Schritt | 512 | 128 |
| Gewählter Dev-Stand | 13/24 | 16/24 |
| Überwachte Ziele im gewählten Trainingspräfix | 4.096 | 1.024 |
| Vollständiger Trainingsprozess | 12,816 s | 583,046 s |
| Reine Float32-Parameterbytes | 528.640 | 26.249.216 |
| Berichtete ausgewählte Checkpointbytes | 529.673 | 26.250.249 |
| VMHWM im Nachkapazitätsprozess | 67,64 MiB | 167,64 MiB |
| Vollständige 32-Token-Ausgaben danach | **0/3** | **0/3** |
| Kapazitätskriterium | **FAIL** | **FAIL** |

Wide128 gewinnt den Dev-Gleichstand mit Wide512 durch die niedrigere
Antwort-NLL, 1,652808383 gegenüber 1,967191765. V3 gewinnt die Studienwahl
mit 16 gegenüber 13 Wide-Dev-Treffern von V2. Die gemeinsame Vierarmauswahl
und das veröffentlichte Freeze-Payload sind konsistent.

Die drei zusätzlichen Wide-Treffer betreffen den zur Auswahl benutzten
Dev-Satz. Die gewählten Trainingspräfixe sind verschieden; daraus folgt weder
eine isolierte Parameterwirkung noch eine unabhängige Generalisierungsbestätigung.
Small erzeugt in allen acht Nachkapazitätsaufrufen null Tokens, Wide jeweils
ein Token und dann EOS. Die volle 32-Token-Anforderung scheitert weiterhin.
Der Freeze enthält weiterhin nur den alten Kapazitätsmarker und keine
gebundenen `postTrainingCapacity`-Belege.

Der erste Rohbelegaudit war ausschließlich textbasiert und bestätigte noch
keine tatsächlichen Binärbytes. Danach konnte Root das zugehörige Archiv
direkt prüfen; dieser zusätzliche Nachweis steht getrennt weiter unten.
Der ursprüngliche [Ergebnisbericht](results/README.md) bleibt unverändert.

## Späterer Branchstand: getrennte Aktualitätskontrolle

Während der Schlussprüfung rückte PR #508 auf
`59891cfad6dd015f47c187cc688ea94b0026ad7d` vor. Die drei Runner, der Selector
und die drei Registrierungen haben weiterhin dieselben sieben Git-Blob-IDs.
Die hier reparierten Durchsetzungslücken sind damit noch nicht in den aktiven
Runnern korrigiert.

Der neuere Baum enthält zusätzliche Dateien mit finalen Testergebnissen.
Die ursprünglichen Berichte bleiben an `9ceffc26…` gebunden; ihre Aussage
„keine veröffentlichten Finaltestlogs“ darf nicht auf den späteren Baum
übertragen werden. Die erste Aktualitätskontrolle liest ausschließlich
Git-Identitäten und geänderte Pfadnamen. Die anschließend abgeschlossene
Prüfung der neuen Ergebnisbelege ist separat dokumentiert; Reparaturcode
und unabhängige Tests standen vorher fest.

Nachweis: [Quellenaktualität](root-verification/upstream-source-freshness.json).

## Abschließende Nachrechnung der veröffentlichten Finaltests

Dieselben drei Helfer prüften den festgelegten neuen Commit. Root hat alle
drei Auditoren danach selbst ausgeführt; jede gespeicherte JSON-Ausgabe ist
bytegleich mit dem jeweiligen Helferbericht. Die Reparatur und ihre 37 neuen
Gegenproben wurden durch die später gelesenen Finalergebnisse nicht verändert.

| Vorher eingefrorener Arm | Parameter | Schritt | Final richtig | Klassen 0/1/2/3, jeweils von zwölf |
|---|---:|---:|---:|---|
| S0 | 132.160 | 0 | 0/48 | 0 / 0 / 0 / 0 |
| W0 | 6.562.304 | 0 | 0/48 | 0 / 0 / 0 / 0 |
| S1 | 132.160 | 512 | **31/48** | **7 / 12 / 10 / 2** |
| W1 | 6.562.304 | 128 | **25/48** | **6 / 9 / 7 / 3** |

Alle 192 Arm-Fall-Urteile wurden aus veröffentlichtem Antworttext, Tokenzahl
und Stopgrund neu berechnet. S1 und W1 erzeugen jeweils in 48/48 Fällen das
geforderte Ziffer-plus-EOS-Format. Die verbleibenden Fehler sind falsche Klassen.
Gepaarter Vergleich: 20 beide richtig, elf nur Small, fünf nur Wide, zwölf beide
falsch. Die Auswahlpräfixe unterscheiden sich; dieser deskriptive Unterschied
isoliert keinen kausalen Parameterzahleffekt und ist keine nachträgliche
Modellauswahl.

W1 verfehlt die ursprünglichen **36/48**, die Mindestgrenze **8/12 in jeder
Klasse** sowie den zusätzlichen Größenvorteil von **vier Fällen gegenüber
S1**. Beide Kontrollen werden zwar um 25 Treffer übertroffen, doch die
verfehlten Pflichtschwellen bleiben verfehlt. Die schon geprüfte vollständige
32-Token-Kapazität scheitert bei beiden ausgewählten Checkpoints weiterhin.

Die nachfolgenden Auswertungen bekannter TRAIN-Beispiele ergeben je Arm
120/128. Diese 256 separat nachgerechneten Urteile verwenden dieselben
eingefrorenen Gewichte und enthalten **keine Optimiererupdates**. Der Scorer
nennt seinen Auswertungszweig intern `test`; TRAIN-Dateipfad und TRAIN-Hash
trennen diese Belege eindeutig von den 48 finalen Testfällen.

Der reine Quellen-/Publikationsaudit besteht 514 Konsistenzkontrollen:
65 tatsächliche Quellhashprüfungen über 29 unterschiedliche Pfade, die
historischen Ausführungscommits, sieben unveränderte Kontroll-/Registrierungs-
dateien und sechs Log-/Receiptbindungen. Der unabhängig implementierte
Finalchecker besteht 105 Belegkontrollen. Diese Zahlen sind keine zusätzlich
ausgeführten Modelltests. Die Zeitbelege passen zusammen, liefern aber keine
unabhängige signierte Zeit- oder Prozessattestation.

Nachweise: [Finaler Ergebnisbericht](results/final-evidence-59891/README.md),
[unabhängige Finalnachrechnung](independent-tests/final-evidence-59891/README.md),
[Publikationsprüfung](source-audit/final-publication-59891/README.md).

## Tatsächliche Binärintegrität zusätzlich geprüft

Root hat das reale Archiv `LM1_Training_Research_20261104.zip` gelesen:
**51.103.436 Bytes**, SHA-256
`428d62b308e308afd9c971ff4974981e96b9dc8a783d80e3653c226983dcc016`.
Alle zehn Mitglieder stimmen mit dem internen Manifest überein. Die vier
Modellhashes stimmen mit dem zuvor veröffentlichten Freeze überein; die vier
JAR-Hashes stimmen mit den jeweiligen Registrierungen überein.

An den tatsächlichen Checkpoints wurden Header, interne SHA-256-Prüfsumme,
vollständige Tensorreihenfolge, Dimensionen, Dateiende und Endlichkeit aller
Float32-Parameter geprüft. Die Parameterzahlen sind aus den Tensorlayouts
neu gezählt: **132.160 und 6.562.304**, mit **528.640 und 26.249.216 Bytes**
reinen Parameterdaten. Die eingebetteten Adaptionsschritte, Trainingsidentität
und Schedule entsprechen dem Freeze und der Registrierung.

Die Unterschiede zwischen den jeweiligen Ausgangs- und trainierten Dateien
betreffen tatsächliche Parameterbytes: 132.160 beziehungsweise 6.562.303
Float32-Slots haben verschiedene Bitmuster. Das ist kein alleiniger
Metadatenwechsel. Der Wert zählt Bitmuster, nicht die Zahl unabhängig
nachgewiesener Optimiererschritte.

Der separat vorhandene Tokenizer ist ebenfalls tatsächlich gehasht und
strukturell geprüft: 2.040 Bytes, 254 gültige BPE-Merges, 512 Vokabeleinträge,
passende Identität in allen Checkpoints. Er ist **nicht im ZIP enthalten**.
Es wurde weder aus dem Archiv extrahiert noch ein Modell oder JAR gestartet.
Der unabhängige Helfer prüfte zusätzlich den neuen binären Leser als Quelltext
gegen die gepinnten Kotlinformate; er beansprucht keine eigene Binärausführung.

Nachweise: [Binärintegritätsbericht](root-verification/BINARY_INTEGRITY.md),
[maschinenlesbare Binärprüfung](root-verification/binary-integrity.json),
[abschließende Gegenbindung](root-verification/final-verification.json).

## Abnahme dieser Fortsetzung

**Reparatur und Beleg-/Artefaktintegrität bestehen. Modellqualität und
vollständige Ausgabekapazität sind verfehlt.** Aus dieser Bestätigungsrunde
folgen keine weitere Parametersteigerung, kein zusätzliches Training und
keine Modell-Promotion. Die bestehenden Checkpoints, Registrierungen,
Quittungen und die vorherige Auswahl bleiben erhalten.

Die veröffentlichten Finalantworten wurden jetzt zur Abnahme gelesen;
dieser Holdout ist verbraucht. Die eigentlichen Datensatzdateien und deren
Generator wurden nicht geöffnet. Ein späterer unabhängiger Bestätigungsversuch
benötigt einen vorher festgelegten neuen Bestätigungssatz. Die Resultate
werden nicht zur nachträglichen Änderung der Kriterien verwendet.

## Artefakte und Integration

| Ausgangsstand | Anwendbares Artefakt |
|---|---|
| Exakte Originalrunner | [Kombinierter Patch](source-audit/pr508-capacity-and-choice-gates.patch) |
| Exakter bisheriger Kapazitätspatch `c69cb427…` | [Inkrementelle Ergänzung](source-audit/choice-guard-on-capacity-repair.patch) |

Die Varianten sind Alternativen für unterschiedliche Ausgangsbytes.
Kombinierter Patch: `87d54102113944c0884081b44cbb05eb6d253d56f8df0c84f9c31bc4f7039d05`.
Inkrementeller Patch: `7ad80b4bb76aaab3daef41bce585b913de4d0422a3679fb6aa1a86ee7c5a663f`.

Diese Artefakte sind im eigenen erlaubten Forschungsordner implementiert und
geprüft. Die aktiven fremden Runner liegen außerhalb des für diesen Auftrag
erlaubten Schreibbereichs. Eine tatsächliche Integration dort und eine neue
Ausführung mit geänderter Quelle benötigen eine passende prospektive
Registrierung. Historische Quittungen werden nicht migriert.

## Arbeitsteilung und Grenzen

Genau drei Helfer: `current_gate_source` für Quellen und Reparatur,
`independent_current_gate` für unabhängige Gegenproben,
`current_training_evidence` für veröffentlichte Rohbelege.
Root prüft Verträge und Ergebnisse zusätzlich und veröffentlicht im eigenen PR.

Alle Änderungen bleiben unter
`experimental/weight-space-20261104/continuation-review/`. Modellprozesse: 0.
Produktive Runtime, historische Forschung und Checkpoints bleiben unverändert.
Keine Promotion, kein Main-Merge und kein APK-Release.

Die erwartete Pretest-SHA bindet vom Aufrufer festgelegte Bytes. Sie beweist
keinen früheren Publikationszeitpunkt und ist keine atomare Dateisperre oder
globale Ausführungssperre gegen unabhängige Prozesse. Der konkrete geprüfte
Vertrag ist die registrierte Study-/CLI-Testgrenze.

Fehlgeschlagene Qualitäts- oder Kapazitätskriterien bleiben sichtbar und
führen zu keiner Modellübernahme. Der nächste größere Trainingskandidat
wird nicht aus diesen Fehlständen als erfolgreich freigegeben.
