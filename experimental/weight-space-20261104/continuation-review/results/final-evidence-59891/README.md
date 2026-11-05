# Getrennter Finalbeleg-Nachtrag zum unveränderten 9ce-Audit

## Ergebnis und Abnahmegrenze

**Die veröffentlichten Final- und Train-fit-Belege sind intern konsistent. Das vorab festgelegte Lernkriterium und die unveränderte Kapazitätsanforderung sind verfehlt. Ein Vorteil des größeren ausgewählten Modells ist nicht nachgewiesen.**

Dieser Nachtrag prüft ausschließlich Commit **`59891cfad6dd015f47c187cc688ea94b0026ad7d`** aus PR #508. Dessen unmittelbarer Vorgänger ist der vor dem finalen Test veröffentlichte Freeze **`9ceffc26a2159c8dc726c1f13d2882119159c130`**. Ausgeführt wurde nach den gebundenen Prozessbelegen der Quellstand **`be4e49600fed1db4c697230bb8e42c150dbfbdaf`**. Diese drei Identitäten bezeichnen unterschiedliche Rollen: Abschlussbelege, Auswahlfreeze und gemeldeter Ausführungsquellstand.

Der eigenständige Python-Auditor berechnet **192 finale Arm-Fall-Urteile** und separat **256 Urteile auf bekannten Trainingsbeispielen** aus den veröffentlichten `CASE`-Zeilen. Er prüft sechs Prozessreceipts, 59 unveränderte bereits auditierte Quell-/Belegdateien und 29 registrierte Quelldateien gegen den gemeldeten Ausführungscommit. Insgesamt bindet das Ergebnis 76 gelesene Textdateien mit SHA-256, Bytezahl und Git-Blob-ID. Die 6.485 erfolgreichen internen Assertions sind Konsistenzprüfungen innerhalb dieses Auditors; sie sind keine zusätzlich ausgeführten Modelltests.

**Der Holdout ist verbraucht.** Veröffentlichte finale Modellantworten wurden ausdrücklich für diese Abnahme gelesen. Der Auditor öffnet weder die tatsächlichen Intent-Datensatzdateien noch deren Generator, Modellcheckpoints, JARs oder Tokenizerbinärdateien. Er startet keine Modell- oder Trainingsprozesse, ändert keine Auswahlregel und leitet keine Optimierung aus finalen Fehlerfällen ab. Der unveränderte frühere Bericht über den damals ungeöffneten Holdout bleibt historisch korrekt; dieser Nachtrag dokumentiert den späteren Zustand.

Quelle für die neue Evidenz ist der [festgelegte Abschlusscommit](https://github.com/Ullmann27/device-ai/commit/59891cfad6dd015f47c187cc688ea94b0026ad7d). Alle Einzelberechnungen, Prozessbindungen und gelesenen Dateihashes stehen in `final-results.json` dieses Verzeichnisses.

## Vier finale Testarme

| Arm | Eigene trainierbare Parameter, laut gebundenen Logs | Eingefrorener Schritt | Exakte Antwort + EOS | Gültiges Ziffer-EOS-Format | Antwort-NLL | EOS-NLL |
|---|---:|---:|---:|---:|---:|---:|
| S0, Small vor Training | 132.160 | 0 | 0/48 · 0 % | 0/48 | 4,332935464 | 8,031456577 |
| W0, Wide vor Training | 6.562.304 | 0 | 0/48 · 0 % | 0/48 | 4,332935254 | 8,031456387 |
| S1, ausgewähltes Small | 132.160 | 512 | **31/48 · 64,58 %** | 48/48 | **0,977714334** | **0,005596800** |
| W1, ausgewähltes Wide | 6.562.304 | 128 | **25/48 · 52,08 %** | 48/48 | 1,064273129 | 0,017161896 |

Die frei erzeugte Antwort wird je Fall aus `textBase64` als UTF-8 rekonstruiert. Ein Erfolg verlangt gleichzeitig die exakt passende einzelne Ziffer, genau ein neu erzeugtes Inhaltstoken und den Stopgrund `EOS`. Es gibt kein Trimmen, keine nachträgliche Antwortkorrektur und keinen Ersatz dieser Generierungswertung durch teacher-forced NLL. Der gebundene Kotlin-Scorer erzeugt mit `maxNewTokens=4` und prüft, dass weder Zeit-/Kontextabbruch noch Promptkürzung vorliegt. NLL und EOS-NLL werden zusätzlich als Mittel der veröffentlichten Einzelfallverluste nachgerechnet; sie werden hier nicht erneut aus einem geladenen Modell berechnet.

S0 und W0 erzeugen auf allen 48 Fällen jeweils vier Inhaltstoken und enden mit `MAX_TOKENS`. Ihre Ausgabezeichenketten stimmen auf jedem Fall überein. Bei S1 und W1 ist das geforderte Ausgabeformat auf allen Fällen vorhanden; die verbleibenden Fehler sind falsche Klassen. Die ursprünglichen untrainierten Kontrollen tragen jeweils 192 generierte Inhaltstoken bei, die trainierten Arme jeweils 48.

| Erwartete Klasse | S0 | W0 | S1 | W1 |
|---|---:|---:|---:|---:|
| 0 – Abbruch/keine Aktion | 0/12 | 0/12 | 7/12 | 6/12 |
| 1 – Datei öffnen | 0/12 | 0/12 | 12/12 | 9/12 |
| 2 – Suchen | 0/12 | 0/12 | 10/12 | 7/12 |
| 3 – Notiz | 0/12 | 0/12 | 2/12 | 3/12 |

Beim gepaarten Vergleich S1/W1 sind **20 Fälle in beiden Armen richtig, elf nur in S1, fünf nur in W1 und zwölf in beiden falsch**. Small liegt damit sechs Fälle beziehungsweise 12,5 Prozentpunkte vor Wide. Die Checkpoints haben unterschiedliche ausgewählte Trainingsschritte; dieser Vergleich isoliert keinen kausalen Effekt der Parameterzahl. Die Ergebnisse begründen auch keine nachträgliche Auswahl von Small anhand des Finaltests.

Belege: [Finalsummary](https://github.com/Ullmann27/device-ai/blob/59891cfad6dd015f47c187cc688ea94b0026ad7d/docs/evidence/lm1-training-recovery-20261104/study-v3/intent-summary.json), die dortigen sechs separat hashgebundenen Log-/Receipt-Paare im JSON-Nachtrag und der [veröffentlichte Fallaudit](https://github.com/Ullmann27/device-ai/blob/59891cfad6dd015f47c187cc688ea94b0026ad7d/docs/evidence/lm1-training-recovery-20261104/final-case-audit.json). Der eigene Auditor beginnt bei den Rohlogs und vergleicht erst anschließend mit diesen Zusammenfassungen.

## Vorab festgelegte Schwellen bleiben unverändert

| Kriterium für W1 | Vorgabe | Beobachtung | Ergebnis |
|---|---|---|---|
| Gesamtgenauigkeit | mindestens 36/48 | 25/48 | Verfehlt, elf richtige Fälle fehlen |
| Jede Klasse | mindestens 8/12 | 6/12, 9/12, 7/12, 3/12 | Drei Klassen verfehlen die Schwelle |
| Zuwachs gegenüber S0 | mindestens acht richtige Fälle | +25 | Erfüllt |
| Zuwachs gegenüber W0 | mindestens acht richtige Fälle | +25 | Erfüllt |
| Zusätzlicher Vorteil des großen ausgewählten Checkpoints | mindestens vier mehr als S1 | sechs weniger als S1 | Verfehlt |

Der veröffentlichte Protokolltext verlangt außerdem gültige Eingabe-, Quell- und Gewichtsidentitäten sowie kein Testtuning. Dieser Textaudit prüft die publizierten Bindungen und den unveränderten Freeze. Er ersetzt keine unabhängige Ausführungsattestation. Bereits die nachgerechneten numerischen Pflichtschwellen reichen aus, um den begrenzten Lernnachweis abzulehnen. Die gewonnene Formatfähigkeit und der Zuwachs gegenüber den Kontrollen werden vollständig berichtet, ersetzen jedoch keine der verfehlten Schwellen.

Der [unveränderte Suffix-Studienplan](https://github.com/Ullmann27/device-ai/blob/59891cfad6dd015f47c187cc688ea94b0026ad7d/experimental/lm1-training-recovery-20261104/suffix-study/SUFFIX_PROTOCOL.md) und das [Intent-Protokoll](https://github.com/Ullmann27/device-ai/blob/59891cfad6dd015f47c187cc688ea94b0026ad7d/experimental/lm1-training-recovery-20261104/INTENT_PROTOCOL.md) legen diese Kriterien fest. Letzteres beschreibt 16 Vorlagenfamilien mit je drei Objektgruppen. Daher wird aus den 48 Fällen kein Signifikanztest mit 48 unabhängigen Ziehungen und keine allgemeine Intelligenzbehauptung abgeleitet. Die tatsächliche Vorlagentrennung wurde hier ohne Datensatzzugriff nicht erneut geprüft.

## Die spätere Train-fit-Auswertung ist getrennte Evidenz

| Arm, weiterhin identischer Freeze | Bekannte TRAIN-Beispiele | Klassen 0/1/2/3, jeweils von 32 | Antwort-NLL | EOS-NLL | Gültiges Format |
|---|---:|---|---:|---:|---:|
| S1, Schritt 512 | 120/128 · 93,75 % | 24 / 32 / 32 / 32 | 0,272752181 | 0,005499089 | 128/128 |
| W1, Schritt 128 | 120/128 · 93,75 % | 24 / 32 / 32 / 32 | 0,230342127 | 0,017134486 | 128/128 |

Diese beiden Prozesse rufen denselben **Auswertungsmodus `test`** mit der Datei **`train.tsv`** und deren registriertem Hash auf. Daher heißen die internen Zeilen weiterhin `TEST_INPUT` und `split=test`; ihre tatsächliche logische Zugehörigkeit ist durch Prozessargument, Eingabehash und separate Summary eindeutig **TRAIN**. Der Auditor kontrolliert diese Bindungen und erwartet je Arm 128 eindeutige `train-…`-IDs statt 48 `test-…`-IDs.

Die 256 zusätzlichen Fallurteile sind eine deskriptive Auswertung eingefrorener Gewichte auf bereits bekannten Beispielen. Sie sind **keine neuen Optimiererschritte**, keine zusätzlichen unabhängigen Finaltestfälle und kein Anlass zur Änderung des ausgewählten Checkpoints. Beide Prozesse beginnen laut Receipts erst nach Abschluss aller vier finalen Testprozesse. Der registrierte Studienplan hatte diese Nachauswertung bereits vorab beschrieben.

Die Trainingsgenauigkeit liegt bei Small rund 29,17 und bei Wide rund 41,67 Prozentpunkte über der finalen Testgenauigkeit. Bei Klasse 3 stehen jeweils 32/32 bekannte Trainingsbeispiele lediglich 2/12 beziehungsweise 3/12 finalen Testbeispielen gegenüber. Das beschreibt den beobachteten Transferabstand auf diesen festen Daten; es diagnostiziert hier weder eine eindeutige Ursache noch rechtfertigt es ein neues Experiment aus dem verbrauchten Holdout.

Beleg: [separate Training-fit-Summary](https://github.com/Ullmann27/device-ai/blob/59891cfad6dd015f47c187cc688ea94b0026ad7d/docs/evidence/lm1-training-recovery-20261104/study-v3/training-fit-summary.json), ihre zwei Rohlogs und Receipts sowie der [hashgebundene reine Auswertungszweig](https://github.com/Ullmann27/device-ai/blob/59891cfad6dd015f47c187cc688ea94b0026ad7d/experimental/lm1-training-recovery-20261104/suffix-study/LmIntentSuffixStudy.kt).

## Freeze-, Quell-, Prozess- und Eingabebindung

| Bindung | SHA-256 beziehungsweise Git-Identität |
|---|---|
| Auswahlfreeze, vor Finaltest veröffentlicht | `6c069dd1ab21d792b9985333b14b2d6a0c51b66affbc8a90de0f37092c5a49f4` |
| V3-Registrierung | `04392b366746b1e6fca78eb66758e8c99e94b8392dbe013a02d2b416e2184728` |
| Kotlin-Suffix-Scorerquelle | `2ca026da5ecebb79f9fcbb0d49d39cc6c1485b0d31b19fe10118cb4ff711ef89` |
| Gemeldetes Experiment-JAR | `8075d799b42f3b4f23f2f154af09685f52074503b0a4103ba336a618a3be1950` |
| Registriertes Kotlin-stdlib-JAR | `fb169596659a518357c4b2c16f43dc75ab1c4980565ed4b4a317a050e5e39006` |
| Registrierter Tokenizer | `94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f` |
| Registrierter Finaltest | `cb75fb0be812116d8bbde95fbe5f82cf8c175d09ba0c49c51090c6cbb3d18a6a` |
| Registriertes TRAIN | `a474d13be70162fb5c8452df43777f06681fec91ba1d4498ea10d453180a718b` |

| Arm | Eingefrorener Checkpoint-SHA-256 |
|---|---|
| S0 | `70cb6ed4020042c4ced21bf0c58dab1b83ee2a51655fdbec41279cd961234435` |
| W0 | `af617cf6d223a3e53b44bc3c0578b6924e9c73d8b92f558a981af22990cad04d` |
| S1 | `90b58fb58e048bd4eac967f2b0b131373170201660de7a3d330b393a6c8d820f` |
| W1 | `0f2b5481cf60d6c6021e9e24d1e5ba70d303da274c18933de29c48a47489cf8c` |

Die vollständigen Befehlsargumente, Prozesszeiten und Loghashes jedes Receipts sind im JSON erhalten. Alle sechs verlangen denselben Klassenpfad, den Auswertungsmodus, den jeweiligen eingefrorenen Checkpoint, denselben Tokenizer und genau den zur logischen Datengruppe gehörenden Pfad. Alle melden Exitcode 0, keinen Timeout und unveränderte registrierte Bytes. Die Prozessfrist bleibt 900 Sekunden bei `-Xms64m -Xmx3g`.

Die neue Evidenzänderung berührt ausschließlich Dokumentationspfade. Alle zuvor auditierten Trainings-, Auswahl-, Kapazitäts- und Quellbelege bleiben bytegleich. Der aktualisierte allgemeine Handofftext wird ausdrücklich nicht als unveränderter Baselinebeleg behandelt. Auswahl und Freeze entsprechen weiterhin exakt dem früher unabhängig nachgerechneten Dev-Ergebnis: V3, S1 Schritt 512 und W1 Schritt 128.

Die [Publikationsmetadaten](https://github.com/Ullmann27/device-ai/blob/59891cfad6dd015f47c187cc688ea94b0026ad7d/docs/evidence/lm1-training-recovery-20261104/final-selection/publication-metadata.json) passen zu den unveränderlichen Git-Commit-/Baum-/Elternobjekten. Der Freeze-Commit trägt `2026-11-04T19:31:51Z`; der erste Testreceipt beginnt `19:31:59.698108Z`, also 8,698108 Sekunden später. Alle sechs gemeldeten Prozessfenster sind nacheinander angeordnet. Der letzte Finaltest endet rechnerisch `19:32:55.883050Z`; die erste Train-fit-Auswertung beginnt `19:34:13.178923Z`.

**Diese Zeitfolge ist eine konsistente Folge unsignierter Commitmetadaten und Prozessreceipts.** Der im publizierten Metadokument berichtete erfolgreiche Referenzupdate und der anschließende entfernte Bytevergleich vor Testbeginn werden nicht zu einer unabhängigen kryptografischen Zeitbeglaubigung umgedeutet. Ebenso sind in diesem Textaudit übereinstimmende Checkpoint-/JAR-Hashes eine Bindungsprüfung und keine eigene binäre Reattestierung der damaligen Ausführung. Eine gegebenenfalls parallel ausgeführte Binärprüfung des koordinierenden Agents ist separat zu belegen.

## Parameter und Ressourcen bleiben unverändert zu bewerten

Die Parameterzahl von Wide beträgt rund das 49,65-Fache von Small. Reiner Float32-Parameterspeicher ist rechnerisch 528.640 Bytes für Small und 26.249.216 Bytes für Wide, also rund 0,504 beziehungsweise 25,033 MiB. Diese Werte enthalten weder Optimiererzustände noch Aktivierungen oder JVM-Speicher. Der Auditor gleicht sie mit den früher auditierten geladenen Modellinventaren ab; neue Binärdateien werden dafür nicht geöffnet.

| Unverändert ausgewählter Checkpoint | Gemeldete Checkpointbytes | Vorher auditierter Peak VmHWM | Erste-Token-Beobachtungen | Vollständige 32-Token-Beobachtungen | Kapazitätsgate |
|---|---:|---:|---:|---:|---|
| S1 | 529.673 | 69.268 KiB | 0/3 | 0/3 | Fehlgeschlagen |
| W1 | 26.250.249 | 171.660 KiB | 3/3 | 0/3 | Fehlgeschlagen |

Die ursprünglichen Grenzen bleiben erstes Token höchstens 1.500 ms, vollständige 32 Inhaltstoken höchstens 5.000 ms und Peak VmHWM höchstens 786.432 KiB. Beim Kapazitätsprompt erzeugte S1 in den früheren Logs kein Inhaltstoken vor EOS und W1 lediglich eines. Die kurzen gemeldeten Laufzeiten dieser abgebrochenen Arbeitslasten belegen keine flüssige vollständige 32-Token-Generierung. Gültige Ziffer-EOS-Ausgaben im Intenttest heben diesen Kapazitätsfehlschlag nicht auf.

Die Rohlogs dieser Kapazitätsbefunde wurden im 9ce-Audit bereits unabhängig nachgerechnet und werden hier erneut auf ihre unveränderten Bytehashes geprüft. Es gab im Nachtrag keinen neuen Ressourcenbenchmark und keine Androidprüfung. Der historische Freeze enthält weiterhin `REQUIRES_SEPARATE_BENCHMARK` und keine spätere reparierte Bindung an den Kapazitätsnachweis. Der Textnachtrag genehmigt diesen historischen Controller nicht rückwirkend.

## Kleine Unterschiede im veröffentlichten Fallaudit

Der fremde veröffentlichte Fallaudit mittelt die gedruckten Dezimalwerte von `firstEosProbability`. Der Kotlin-Scorer hatte zuvor Float32-Werte in Double akkumuliert. Der eigene Auditor rekonstruiert deshalb zuerst jeden Float32-Wert und bildet anschließend das Double-Mittel. Damit stimmen alle sechs originalen SCORE-Mittel innerhalb `rel_tol=1e-12`, `abs_tol=1e-14` überein. Die maximale Differenz zum fremden Dezimalmittel beträgt bei den vier Finalarmen rund **4,214 × 10⁻¹³**. Sie ändert kein Urteil und keine Schwelle. Beide Rechenwerte und die exakten Differenzen sind im JSON dokumentiert.

Das W0-Testlog beginnt mit einer JVM-Warnung, dass `/tmp/hsperfdata_agent/26` von einem anderen Prozess gesperrt sei. Der Auditor erlaubt genau diese gebundene erste Warnzeile, bewahrt sie im JSON und erwartet danach unverändert einen Eingabebeleg, 48 CASE-Zeilen und einen SCORE. Beliebige weitere Fremd- oder Trainingszeilen würden die Prüfung abbrechen. Die Warnung verhindert laut Receipt weder vollständige Ausgabe noch Exitcode 0; sie wird nicht aus dem Loghash entfernt.

Der eigene Auditor vergleicht die 192 veröffentlichten Fallentscheidungen des Fremdaudits mit den Rohlogs. Dessen zusätzliche Prompt-Hashes und Aussagen über reale Datensatzzeilen werden hier ohne Datensatzzugriff nicht unabhängig bestätigt. Dies unterscheidet das Lesen veröffentlichter Modellantworten ausdrücklich vom Öffnen der tatsächlichen versiegelten Testdatei.

## Reproduktion und unveränderte Vorgängerdateien

Vom Repositorywurzelverzeichnis aus:

```sh
python experimental/weight-space-20261104/continuation-review/results/final-evidence-59891/audit_final_evidence.py | cmp - experimental/weight-space-20261104/continuation-review/results/final-evidence-59891/final-results.json
```

Die Ausgabe ist deterministisch und enthält keinen wechselnden Ausführungszeitstempel. Der Auditor verwendet ausschließlich Python-Standardbibliothek und Git-Objektlesezugriffe. Er importiert und startet keinen Repositorycontroller und keine Modellklasse. Die benötigten festgelegten Git-Objekte und die drei unveränderten Vorgängerdateien müssen verfügbar sein; bei einem Teilklon kann Git fehlende erlaubte Textblobs über seine bestehende Konfiguration nachladen.

| Datei im bisherigen `continuation-review/results/` | Unveränderter SHA-256 |
|---|---|
| `audit_published_v3.py` | `31af55ca4ce3c4d282a8269c9f9b0d287f92a295ce9493a0441903d633c63c1e` |
| `published-v3-results.json` | `51342b48ac0288370df43f8b763873b878cbcfbdb702129eb246005ee4e7e5ac` |
| `README.md` | `eea810b52a83cb3a5d659b030a327f57c607799c014ccede8765fbda569d1ca1` |

| Neue Datei in diesem Nachtragsverzeichnis | SHA-256 |
|---|---|
| `audit_final_evidence.py` | `1ac0fdd94315f7d538f9f23933192b1c1a881b75b67bed0a6e8951883be1fbc1` |
| `final-results.json` | `9375ab0276d3508160ac1ebb64a879dba6a779ef2fbdb48cce35bff4dccb1dd1` |

Der Nachtrag enthält keine Modell-Promotion, kein produktives Training, kein zusätzliches Forschungsgewichtupdate und keine Änderung der bereits eingefrorenen Reparatur. Seine Abnahme lautet: **Belegkonsistenz bestanden; vorgeschriebene Qualität und vollständige Kapazität verfehlt; Forschungs- und Produktionsfreigabe daraus nicht erteilt.**
