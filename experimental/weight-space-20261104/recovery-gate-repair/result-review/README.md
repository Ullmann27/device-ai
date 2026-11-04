# Unabhängiger Quellen- und Ergebnisabgleich von PR #508

Prüfdatum: 04.11.2026. Gepinnter Veröffentlichungsstand:
[`be4e49600fed1db4c697230bb8e42c150dbfbdaf`](https://github.com/Ullmann27/device-ai/commit/be4e49600fed1db4c697230bb8e42c150dbfbdaf).
Dieser Bericht gehört zu PR #492 / G1-INTEGRATE-WEIGHT-002. Er verändert weder
einen fremden Studienbranch noch eine Modellwahl.

**Ergebnis:** Die veröffentlichten Rohbelege für die vier Diagnosearme und
die beiden 128-Schritt-Intent-Läufe sind untereinander konsistent. Kein
Diagnosearm erfüllt den registrierten Fortsetzungserfolg. Intent V1 lernt
auf den Entwicklungsfällen das freie Ziffer-plus-EOS-Format, verfehlt aber
seine Entwicklungsschwelle. Die neu veröffentlichte V2-Zusammenfassung meldet
einen besseren Entwicklungsstand; ihre Rohlogs und Prozessbelege fehlen im
geprüften Git-Baum. V3 ist dort registriert, aber ohne veröffentlichte
Studienresultate. Ein unabhängiger Testnachweis oder eine Modellfreigabe folgt
aus keinem dieser veröffentlichten Stände. [D] [I1] [I2] [P3]

## 1. Was diese Prüfung tatsächlich ausgeführt hat

[`audit_published_results.py`](audit_published_results.py) liest ausschließlich
Textblobs des oben gepinnten Git-Commits. Es importiert keinen Studiencontroller,
startet kein JVM-/Modellprogramm und öffnet weder Checkpoint-/JAR-/Tokenizerbytes
noch Trainings-, Entwicklungs- oder Testdatendateien der Intent-Studie. Auch
der Datengenerator, der die Testvorlagen enthält, bleibt ungelesen.

Die neue Nachrechnung verarbeitet **64 Textquellen**, prüft zwölf äußere
Prozess-Receipt/Log-Paare und rekonstruiert die Bewertung aller **192 bereits
veröffentlichten Dev-Einzelfälle** von Intent V1. Sie prüft außerdem Schrittfolgen,
Zieltokenbudgets, Auswahlregeln, Checkpoint-Identitätsketten und die unveränderte
Kapazitätsarbeitslast. Für alle gelesenen Quellen enthält
[`published-results.json`](published-results.json) SHA-256, Git-Blob-ID und
Bytelänge. Das Resultat lautet:

```text
PUBLISHED_V1_EVIDENCE_CONSISTENT_V2_RAW_EVIDENCE_MISSING
```

Das ist eine unabhängige **Auswertung vorhandener Belege**, keine unabhängige
Replikation der Modellläufe. Die gespeicherten Laufquittungen binden V1 an
Quellcommit `def5acf9cbba82ee51a53cc06fecc52e9cd0324d`, Registrierung
`540f646e6963bcc9902a362f77d963fd39aa76513837125b86085768af8b8992` und JAR
`901b94e4c4d9b54c5ba422a5fa3b00bd9f28f187b68ab0b79f7f9b8fc6b62911`.
Die registrierten, gelesenen V1-Quellen stimmen mit ihren angegebenen SHA-256
überein; das ausführbare JAR wurde nicht erneut gehasht. [A]

## 2. Vier Diagnosearme: echte Updates, vier negative Gesamtentscheidungen

Jeder Arm protokolliert dieselben 16 lückenlosen Updates, Batchgröße vier,
3.255 Zieltoken und identische Beispiel- sowie Tokensequenz-Hashes. Darunter
sind 3.229 Inhaltsziele und 26 EOS-Ziele. Die vier Vorher-Kapazitätsprüfungen
bestehen vollständig und stehen im jeweiligen Log vor dem ersten Update.
Alle vier Trainingsprozesse enden mit Exit 0, ohne Timeout und innerhalb
der registrierten 600 Sekunden. [A] [D]

BPB bezeichnet die Inhalts-Bits pro Byte auf denselben Entwicklungsdaten;
kleiner ist besser. Der registrierte Gesamterfolg verlangt zusätzlich die
vollständige, unveränderte Kapazitätsprüfung.

| Arm | Parameter | BPB vor Training | BPB nach Training | Änderung | Volle 32-Token-Ausgaben nach Training | Gesamterfolg |
| --- | ---: | ---: | ---: | ---: | --- | --- |
| Small, Standard | 132.160 | 2,352541416 | 2,352506801 | −0,000034615 | 0/3 | Nein |
| Wide, Standard | 6.562.304 | 2,352541414 | 2,436533181 | +0,083991767 | 0/3 | Nein |
| Wide, finales Norm-Update / 8 | 6.562.304 | 2,352541414 | 2,436379136 | +0,083837722 | 0/3 | Nein |
| Wide, gesamte Lernrate / 8 | 6.562.304 | 2,352541414 | 2,351867623 | −0,000673792 | 0/3 | Nein |

Die stets gleiche erste Trainings-Minibatch wird in allen Armen besser
vorhergesagt. Ihre NLL sinkt von ungefähr 2,628635 auf 2,430478 / 1,917311 /
1,917971 / 2,461860 in dieser Reihenfolge. Diese Beobachtung betrifft
bereits trainierte Beispiele. Bei den breiten Standard- und Normarmen wird
die Entwicklungs-BPB gleichzeitig schlechter. Die wechselnden laufenden
Minibatch-Verluste werden deshalb nicht als feste Generalisierungskurve
verwendet. [A] [D]

Die isolierte Normskalierung wurde den Rohwerten zufolge tatsächlich
angewandt: `nfAppliedUpdateL2 / nfProposedUpdateL2` liegt für ihre 16 Schritte
zwischen 0,1247946171 und 0,1250460876. Diese konkrete Intervention behebt
den gemessenen Fehler nicht. Der globale Lernratenarm erzielt eine kleine
Entwicklungsverbesserung, scheitert aber ebenfalls an der Generierungslast.
Er verändert außerdem alle Updates einschließlich des AdamW-Decay-Schritts;
sein Ergebnis isoliert keine einzelne interne Ursache. [A] [P1]

### EOS: belegter Rangwechsel am Kapazitätsprompt

Nach dem Training liefern alle vier Diagnosearme in ihren jeweils acht
Kapazitätsaufrufen einschließlich Warmup sofort EOS und null neue Inhaltstoken.
Der unveränderte Grund lautet
`NO_FIRST_TOKEN_OBSERVATION,INCOMPLETE_32_TOKEN_WORKLOAD`.

Die Kapazitäts-Margin `logit(EOS) − bester anderer Ausgabelogit` wechselt
bei allen vier Armen von etwa −0,08270 ins Positive. Beim breiten Standardarm
steigt sie auf +0,5466442, während die EOS-Wahrscheinlichkeit sogar leicht
von 0,21975711 auf 0,21847048 fällt. EOS kann also gewinnen, obwohl seine
absolute Wahrscheinlichkeit nicht steigt. [A] [D]

Die drei nichtterminalen Prefixe ergeben zusammen über vier Arme und vier
Messpunkte 48 Beobachtungen. Ihre EOS-Margins bleiben sämtlich negativ;
das Maximum beträgt −6,374255. Die größte EOS-Wahrscheinlichkeit beträgt
0,00021617622, entsprechend ungefähr 0,02162 %. Daraus folgt weder ein
globaler EOS-Kollaps an beliebigen Prompts noch eine bereits bestimmte
kausale Reparatur. Es ist eine begrenzte Kontextdiagnose. [A] [D]

## 3. Intent V1: Format gelernt, Entwicklungsgate verfehlt

Small und Wide erhalten je 128 Updates mit Batchgröße vier und zwei
überwachten Zielen pro Beispiel: die Aktionsziffer und das unmittelbar
anschließende EOS. Damit sind es **1.024 überwachte Zielpositionen je Arm**.
Beispiel- und Tokensequenz-Hashes stimmen zwischen den Größen überein.
Diese Zielpositionen sind nicht mit den 3.255 Inhalts-/EOS-Zielen der
anderen Diagnoseaufgabe oder mit allen verarbeiteten Eingabetoken
gleichzusetzen. [A] [H1]

Der Quellabgleich bestätigt freie Generierung: `LmRuntime.generate` wählt
mit den Standardparametern greedy aus dem normalen Vokabular, nur BOS
wird ausgeschlossen. Der Intent-Harness setzt höchstens vier neue Token
und akzeptiert genau eine erzeugte Ziffer mit anschließendem tatsächlichem
EOS. Er fügt EOS nicht nachträglich an, verbietet es nicht und ersetzt
die Ausgabe nicht durch einen Klassen-Argmax. Die andere, teacher-forced
Antwort-/EOS-NLL wird als separate Metrik protokolliert. [H1] [RT]

| Größe | Schritt 0 | Schritt 32 | Schritt 64 | Ausgewählt: Schritt 128 | Klassenrichtig am ausgewählten Stand |
| --- | ---: | ---: | ---: | ---: | --- |
| Small | 0/24 | 6/24 | 6/24 | **8/24** | 0, 4, 4, 0 von je 6 |
| Wide | 0/24 | 6/24 | 8/24 | **10/24** | 2, 6, 2, 0 von je 6 |

Alle 192 `CASE`-Datensätze wurden neu aus dekodierter Ausgabe, Tokenzahl
und Stop-Grund bewertet; sämtliche Flags und Summen stimmen. Ab Schritt
32 besitzen beide Modelle bei allen 24 Dev-Fällen das korrekte
Ziffer-plus-EOS-Format. Die frühen 6/24 können jedoch durch eine konstante
Klasse entstehen: Small gibt bei Schritt 32 durchgehend `1` und bei Schritt
64 durchgehend `2` aus; Wide bei Schritt 32 durchgehend `0`.
Die Notizklasse erreicht am gewählten Schritt bei beiden Größen 0/6. [A] [I1]

Die Auswahlregel — mehr Dev-Treffer, dann niedrigere Antwort-NLL, dann
weniger Schritte — ergibt für beide Größen Schritt 128. Auswahl-TSV,
Checkpointzeile, Zusammenfassung und nachträglicher Kapazitätsbericht nennen
jeweils denselben ausgewählten Checkpointhash. Wide verbessert sich um zehn
gegenüber W0, verfehlt aber die ebenfalls erforderlichen **12/24**. Der
Abbruch `NO_DEVELOPMENT_SIGNAL` mit `testEvaluated=false` ist korrekt. [A]

Beide ausgewählten Modelle scheitern anschließend an der ursprünglichen
Kapazitätsprüfung: null von drei Ersttoken-Beobachtungen und null von drei
vollen 32-Token-Ausgaben, jeweils sofortiges EOS auch in den Warmups.
Der Fortschritt auf anders aufgebauten Intent-Prompts hebt diese Regression
nicht auf. [A] [I1]

## 4. Parameter, Speicher und unveränderte Anforderungen

Die Parameterzahlen bleiben innerhalb jeder Größe unverändert. Die
Checkpoint-Metadaten werden neu gespeichert; die folgenden Größen sind
die im Modellladeprotokoll berichteten Dateigrößen, nicht neu gelesene
Checkpointbytes.

| Intent V1 | Small | Wide |
| --- | ---: | ---: |
| Parameter | 132.160 | 6.562.304 |
| Reine Float32-Parameterbytes | 528.640 | 26.249.216 |
| Initialer Checkpoint, berichtete Bytes | 529.664 | 26.250.240 |
| Ausgewählter Checkpoint, berichtete Bytes | 529.666 | 26.250.242 |
| Vorher-Kapazität: beobachteter VMHWM, KiB | 79.300 | 185.336 |
| Nachher-Kapazität: beobachteter VMHWM, KiB | 68.556 | 193.976 |
| Trainingsprozess einschließlich Prüfungen/Dev-Auswertung | 7,361 s | 200,075 s |

Die unabhängige Nachrechnung prüft die ursprünglichen Grenzen in jedem
Kapazitätslog: Ersttokenaufruf höchstens 1.500 ms, vollständiger
32-Token-Aufruf höchstens 5.000 ms, beobachteter VMHWM höchstens
786.432 KiB. Jeweils drei Messwiederholungen und dieselben 64 Prompttoken
bleiben erhalten. Jeder Kapazitätsprozess erhält höchstens 1 GiB Heap
und 120 Sekunden; V1-Trainingsprozesse höchstens 3 GiB und 600 Sekunden.
Die berichteten Fehlschläge betreffen die fehlende Generierungsarbeit.
Ihre kurze Abbruchzeit oder niedrige Speichernutzung ist kein erfolgreiches
32-Token-Leistungsresultat. [A]

Die Hashketten sind im veröffentlichten Text konsistent. Eine unabhängige
binäre Integritätsprüfung der tatsächlichen Checkpoints, des JARs und
Tokenizers wurde in dieser rein lesenden Ergebnisprüfung nicht vorgenommen.
Berichtete `fullLayoutLoaded=true` und `finiteWeights=true` sind
Laufbelege, keine von diesem Reviewer selbst ausgeführten Decoderprüfungen.

## 5. Intent V2: bessere veröffentlichte Dev-Zahlen, fehlende Rohbelege

Im gepinnten Git-Baum liegt unter `study-v2/` **ausschließlich**
`intent-summary.json`. Sie meldet:

| Größe | Dev bei Schritt 128 | Dev bei Schritt 256 | Dev bei Schritt 512 | Auswahl | Berichtete Kapazität nach Auswahl |
| --- | ---: | ---: | ---: | --- | --- |
| Small | 12/24 | **13/24** | 12/24 | Schritt 256 | 0/3 Ersttoken, 0/3 volle 32 Token |
| Wide | **13/24** | 11/24 | 11/24 | Schritt 128 | 3/3 Ersttoken, 0/3 volle 32 Token |

Die Auswahl passt intern zur vorgesehenen Sortierung. Die Zusammenfassung
nennt `testEvaluated=false` und
`ELIGIBLE_DEVELOPMENT_CANDIDATE_TEST_DEFERRED_FOR_REGISTERED_SUFFIX_COMPARISON`.
Das sind veröffentlichte Summary-Angaben. Ohne `TRAIN_STEP`-/`CASE`-Rohlogs,
Prozess-Receipts, Vorher-/Nachher-Kapazitätslogs und Auswahl-TSVs kann diese
Prüfung die behauptete Ausführung, Hashbindungen und Einzelfallscores nicht
wie für V1 unabhängig nachrechnen. [I2]

Die neue Registrierung bindet 512 Updates, insgesamt 4.096 überwachte
Antwort-/EOS-Ziele pro Arm und einen von 600 auf **900 Sekunden erhöhten
Trainingsprozessrahmen**; die interne Schleifengrenze steigt von 540 auf
840 Sekunden. Die Kapazitätsanforderungen und -grenzen werden dabei nicht
gelockert. Dieser ausdrücklich neue Trainingsbudgetrahmen darf nicht als
unveränderter Gesamtressourcenverbrauch dargestellt werden. [P2]

Der vollständige Quellvergleich V1→V2 enthält exakt fünf Änderungen:
Klassenname, 128→512 Updates, Experimentname, interne Zeitgrenze und
Messpunkte 32/64/128→128/256/512. Die übrige Trainingslogik bleibt gleich.
**Die Cosine-Lernrate hängt jedoch von `STEPS - 4` ab.** Dadurch unterscheiden
sich bereits die frühen Updates; zweimal „Schritt 128“ bedeutet nicht
denselben Optimierungsverlauf. Zudem stammen die ausgewählten Small-/Wide-
Stände aus verschiedenen Schrittzahlen. Die gemeldeten 13/24 belegen damit
keinen isolierten kausalen Vorteil von mehr Parametern oder ausschließlich
zusätzlichen Updates. [A] [P2]

## 6. V3-Registrierung und Auswahl vor dem Holdout

Der Stand `be4e…` registriert eine abschließende Suffixvariante: Aus
`Auftrag: <prompt>\nAktion:` wird `Auftrag: <prompt>\n`. Der vollständige
Quellvergleich V2→V3 findet neben Klassen-/Experimentnamen ausschließlich
diese Promptänderung. Antwortmaske, freie Generierung, Architektur,
512-Update-Schedule und Auswahlcode sind unverändert. Vorgesehen bleiben
4.096 überwachte Antwort-/EOS-Ziele; vier Eingabetoken pro Beispiel
fallen weg. Gleiche überwachte Ziele bedeuten daher nicht dieselbe gesamte
Eingabetokenzahl oder identischen Rechenaufwand. [A] [P3]

Die protokollierte Regel für die spätere Wahl zwischen V2 und V3 ist:
höhere Dev-Trefferzahl des gewählten Wide-Modells, danach niedrigere
Antwort-NLL, bei Gleichstand V2. Es wird der vollständige Vierarmvergleich
derselben Version übernommen. Beide Registrierungen nennen denselben
Finaltest-Hash; dessen Datei wurde nicht gelesen. Wiederholte Dev-Auswahl
bleibt Entwicklung und ersetzt keine unabhängige Bestätigung. [P3]

Bei diesem Commit sind weder V3-Studienlogs noch eine veröffentlichte
Studienwahl-/Freeze-Quittung oder finale Testlogs vorhanden. Eine
Protokollregistrierung allein beweist keine spätere Einhaltung dieser
Reihenfolge. Die vorgeschriebene Veröffentlichung der vollständigen Wahl
und Freeze-Bindung vor dem einmaligen Test muss anhand dieser noch
fehlenden Artefakte geprüft werden. Die Absenz in diesem Git-Baum ist
kein forensischer Beweis über andere, unveröffentlichte Prozesse.

Der neue `choose_development_study.py` prüft bereits Nach-Kapazitätsreceipts
im Auswahlpfad. Das ersetzt nicht die eigenständige Absicherung der
separat aufrufbaren Freeze-/Testcontroller. Die konkrete Reparatur und ihre
unabhängige Gegenprobe liegen bewusst außerhalb dieses Ergebnisreviews
bei den beiden anderen Helfern. [P3]

Die zusätzliche veröffentlichte Suffixpfad-Berechnung betrifft laut ihrer
Quelle nur die direkte layer-0-Zustandsretention des Originalmodells.
Sie ist kein gemessener vollständiger Netzwerkgradient und kein kausaler
Nachweis für die Dev-Fehler. Sie begründet eine prüfbare Hypothese; dieser
Reviewer hat dafür keine Gewichte geöffnet oder erneut gerechnet. [SP]

## 7. Entscheidung und nächster begrenzter Forschungsschritt

Die bisherigen negativen Kandidaten werden aus diesem Review nicht
übernommen. Qualitätsschwellen und die volle 32-Token-Arbeitslast bleiben
unverändert. Die dokumentierten Ergebnisse rechtfertigen echte kleine
Optimierungsläufe und einen begrenzten Formatfortschritt, jedoch weder
allgemeine Sprach-/Werkzeugkompetenz noch eine Modell-Promotion.

Der nächste konkrete Schritt ist **Evidenz vervollständigen und den
separaten Nach-Kapazitäts-Gate-Nachweis abschließen**: V2-Rohlogs,
Prozessreceipts, Auswahl- und Kapazitätsbelege veröffentlichen und an die
registrierten Quellen binden. Die bereits registrierte V3-Variante wird
erst anhand ihrer tatsächlich veröffentlichten Ergebnisse bewertet.
Vor einem einmaligen Finaltest müssen die reparierten getrennten
Freeze-/Testpfade unabhängig bestehen sowie Studienwahl und vollständige
Kapazitätsbelege veröffentlicht gebunden sein. Es wird hier weder eine
weitere Parameter-/Lernratensuche gewählt noch nach Holdout-Ergebnissen
nachjustiert. Wenn der Nachweis scheitert, bleibt der bisherige
Forschungs- und Modellstand erhalten.

## Reproduktion und Quellen

Aus dem Repositoryroot, mit vorhandenem gepinntem Git-Commit:

```sh
python experimental/weight-space-20261104/recovery-gate-repair/result-review/audit_published_results.py --repo . --output /tmp/pr508-published-review.json
```

Erwartet: Exit 0 und oben genannter Status. Die Ausgabe ist deterministisch;
es werden keine Modellprozesse gestartet. Der Bericht bindet ausschließlich
den genannten Snapshot; spätere #508-Commits sind separat zu prüfen.

- **[A]** [Maschinelle unabhängige Nachrechnung und vollständiges Quelleninventar](published-results.json).
- **[D]** [Vier Diagnosearme: veröffentlichte Zusammenfassung und Rohlogverzeichnis](https://github.com/Ullmann27/device-ai/tree/be4e49600fed1db4c697230bb8e42c150dbfbdaf/docs/evidence/lm1-training-recovery-20261104/study-v1).
- **[I1]** [Intent V1: veröffentlichte Zusammenfassung](https://github.com/Ullmann27/device-ai/blob/be4e49600fed1db4c697230bb8e42c150dbfbdaf/docs/evidence/lm1-training-recovery-20261104/study-v1/intent-summary.json); die eigenständig ausgewerteten Rohbelege stehen im selben Studienverzeichnis unter `logs/`.
- **[I2]** [Intent V2: einzige veröffentlichte Studienergebnisdatei am geprüften Commit](https://github.com/Ullmann27/device-ai/blob/be4e49600fed1db4c697230bb8e42c150dbfbdaf/docs/evidence/lm1-training-recovery-20261104/study-v2/intent-summary.json).
- **[P1]** [Registriertes Diagnoseprotokoll](https://github.com/Ullmann27/device-ai/blob/be4e49600fed1db4c697230bb8e42c150dbfbdaf/experimental/lm1-training-recovery-20261104/DIAGNOSTIC_PROTOCOL.md).
- **[P2]** [Registriertes 512-Schritt-Protokoll](https://github.com/Ullmann27/device-ai/blob/be4e49600fed1db4c697230bb8e42c150dbfbdaf/experimental/lm1-training-recovery-20261104/long-study/INTENT_LONG_PROTOCOL.md).
- **[P3]** [Registriertes Suffix-/Auswahlprotokoll](https://github.com/Ullmann27/device-ai/blob/be4e49600fed1db4c697230bb8e42c150dbfbdaf/experimental/lm1-training-recovery-20261104/suffix-study/SUFFIX_PROTOCOL.md) und [Studienwahlcode](https://github.com/Ullmann27/device-ai/blob/be4e49600fed1db4c697230bb8e42c150dbfbdaf/experimental/lm1-training-recovery-20261104/suffix-study/choose_development_study.py).
- **[H1]** [Geprüfter Intent-Harness mit Antwortmaske und tatsächlicher freier Generierung](https://github.com/Ullmann27/device-ai/blob/be4e49600fed1db4c697230bb8e42c150dbfbdaf/experimental/lm1-training-recovery-20261104/LmIntentStudy.kt).
- **[RT]** [Unveränderte Generierungsruntime](https://github.com/Ullmann27/device-ai/blob/be4e49600fed1db4c697230bb8e42c150dbfbdaf/app/src/main/java/at/heinz/deviceai/learning/lm/LmRuntime.kt).
- **[SP]** [Begrenzter Suffixpfad-Befund, hier nur quellenkritisch gelesen](https://github.com/Ullmann27/device-ai/blob/be4e49600fed1db4c697230bb8e42c150dbfbdaf/docs/evidence/lm1-training-recovery-20261104/suffix-path-review.md).
