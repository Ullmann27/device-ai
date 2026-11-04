# V3-Ergebnisse und veröffentlichte Auswahl vor dem Finaltest

Geprüfter Stand von PR #508:
[`9ceffc26a2159c8dc726c1f13d2882119159c130`](https://github.com/Ullmann27/device-ai/commit/9ceffc26a2159c8dc726c1f13d2882119159c130).
Direkter Vorgänger: `e3c5aeadd6336f843bd625d56431208d3a03b5ad`.
Der Commit fügt ausschließlich 16 Evidenzdateien hinzu. Die bisherigen
be4e-/e3c5-Reviews bleiben unverändert.

**Ergebnis:** Die neuen V3-Rohbelege, die Auswahl zwischen V2/V3 und die
veröffentlichten Choice-/Freeze-Hashes sind intern konsistent. V3 wird nach
der vorab festgelegten Entwicklungsregel korrekt gewählt. Das große Modell
erreicht **16/24** Entwicklungsfälle, das kleine **13/24**. Beide scheitern
weiterhin an der vollständigen 32-Token-Kapazitätsanforderung. Der veröffentlichte
Freeze verwendet noch das alte Format ohne gebundene Nachkapazitätsbelege.
Finaltestresultate sind in diesem Git-Baum nicht veröffentlicht. [A] [V3] [F]

## 1. Tatsächliche unabhängige Prüfung

[`audit_published_v3.py`](audit_published_v3.py) hat mit Exit 0 abgeschlossen:

```text
PUBLISHED_V3_AND_LEGACY_FREEZE_CONSISTENT_CAPACITY_FAIL_FINAL_TEST_NOT_PUBLISHED
```

Die Nachrechnung liest **60 gepinnte Textquellen**. Sie prüft:

- vier äußere Receipt-/Log-Paare einschließlich Quell-, Registrierungs-,
  JAR-, Befehls-, Budget- und Loghashbindung;
- sämtliche **1.024 Updates** über zwei Arme und jeweils 4.096 überwachte
  Antwort-/EOS-Ziele;
- **192 veröffentlichte Dev-Einzelfälle**, freie Ziffer-plus-EOS-Ausgabe,
  Klassen- und Gesamtsummen, Antwort-/EOS-NLL sowie die Float32-EOS-Mittelwerte;
- vier vollständige Kapazitätsläufe mit insgesamt **32 Rohaufrufen**,
  ihren Speicherbeobachtungen, Zeitmaxima, Tokenzählern und FAIL-Gründen;
- die innerhalb jedes Modells gewählte Schrittzahl sowie die übergreifende
  Wahl zwischen V2 und V3;
- die gespeicherte Vierarm-Auswahl, ihren identischen Freeze und beide
  Hashes in `before-test-verification.json`.

Zusätzlich werden 32 Online-Epochenmittel aus den V3-Trainingszeilen neu
berechnet. Die schon geprüften V2-Evidenzdateien sind am neuen Commit
bytegleich; ihre frühere Nachrechnung wird unter exakter Hashbindung wiederverwendet.
Alle sechs bisherigen Bericht-/Skriptdateien werden am Anfang und Ende
unverändert nachgehasht. [A]

Es wurden **keine Modelle ausgeführt, keine Gewichte geladen und keine
Trainings-, Entwicklungs- oder versiegelten Testdatendateien geöffnet**.
Auch der Datengenerator bleibt ungelesen. Wiederverwendet werden nur die
gehashten Textparser und Bewertungsrechnungen des bisherigen Auditors.
Der einzige Unterprozess des neuen Auditprogramms ist das schreibgeschützte
Lesen mit Git. Die Prüfung ist eine unabhängige Auswertung veröffentlichter
Belege, keine Replikation der Modellläufe.

## 2. Trainingsablauf und Entwicklungsergebnis

Beide Modelle starten laut Quell- und Laufbelegen neu vom registrierten
Original. Die drei initialen Paritätsproben und die ursprünglichen
Kapazitätsgates sind vor dem ersten Update als bestanden protokolliert.
Die Trainingsprozesse enden jeweils mit Exit 0, ohne Timeout und ohne
gemeldete Änderung registrierter Bytes. [A] [V3]

| Größe | Parameter | Dev Schritt 0 | Dev Schritt 128 | Dev Schritt 256 | Dev Schritt 512 | Auswahl |
|---|---:|---:|---:|---:|---:|---|
| Small | 132.160 | 0/24 | 11/24 | 12/24 | **13/24** | Schritt 512 |
| Wide | 6.562.304 | 0/24 | **16/24** | 14/24 | 16/24 | Schritt 128 |

Bei Wide haben Schritt 128 und 512 gleich viele richtige Antworten. Die
Antwort-NLL ist bei Schritt 128 niedriger: **1,652808383** gegenüber
**1,967191765**. Deshalb gewinnt nach der registrierten Regel Schritt 128.
Bei Small gewinnt Schritt 512 durch die höhere Trefferzahl, obwohl die
Antwort-NLL gegenüber früheren Schritten steigt. Die Auswahl-TSVs, die
`SELECTION`-Zeilen, die Checkpointzeilen und die Zusammenfassung stimmen
jeweils exakt überein. [A]

Am ausgewählten Stand sind die vier Klassen bei Small mit **1, 6, 6, 0**
von jeweils sechs Fällen richtig, bei Wide mit **2, 4, 6, 4**. Der gepaarte
Vergleich auf denselben 24 Dev-IDs ergibt elf Fälle, die beide richtig lösen,
zwei nur bei Small, fünf nur bei Wide und sechs bei beiden falsch.
Das ist ein beobachteter Nettovorteil von drei Entwicklungsfällen für Wide.
Die ausgewählten Trainingspräfixe sind jedoch verschieden: Small512 umfasst
4.096 überwachte Ziele, Wide128 1.024. Diese ausgewählten Dev-Werte sind kein
unabhängiger Finaltest und keine allgemeine Fähigkeitsbestätigung. [A]

Die neu berechneten mittleren Online-Verluste der ersten und letzten Epoche
liegen bei Small bei **2,793759 → 0,149366**, bei Wide bei
**1,399288 → 0,109759**. Das sind Mittel aus wechselnden Minibatches während
laufender Gewichtsänderungen. Sie sind keine feste Checkpointauswertung;
insbesondere wird keine monotone Konvergenz oder Generalisierung daraus
behauptet. V3-Epochenmittel waren in der bei diesem Commit unveränderten
`intent-epoch-losses.json` noch nicht veröffentlicht; die hier genannten
V3-Werte stammen aus der eigenen Nachrechnung der Rohlogs. [A]

## 3. Was sich gegenüber V2 geändert hat

Der vollständige Quellvergleich der beiden Harness-Dateien findet genau
Klassenname, Experimentname und die registrierte Promptänderung:

```text
V2: Auftrag: <prompt>\nAktion:
V3: Auftrag: <prompt>\n
```

Antwortmaske, Architektur, Originalgewichte, Tokenizer, Optimierer,
512-Update-Schedule, Seeds, Batchgröße und freie Generierung bleiben im Code
gleich. Beide V3-Größen melden denselben Tokensequenzhash. Der Beispielplan
stimmt mit V2 überein; der Tokensequenzhash unterscheidet sich wie registriert
wegen der kürzeren Eingabe. Die tatsächlichen Datendateien wurden hierfür
nicht gelesen oder neu tokenisiert. [A] [P]

Wide steigt am jeweils ausgewählten Entwicklungsstand von V2 **13/24** auf
V3 **16/24**; Small bleibt bei 13/24, wird aber in V3 später gewählt. Der
Versuch betrifft den vollständigen geänderten Prompt: kürzerer Rekurrenzweg,
Entfernung des Wortes und anderes letztes Eingabetoken. Er isoliert keinen
einzelnen Forget-Gate-Mechanismus. Die vorherige Suffixpfad-Hypothese wird
dadurch nicht nachträglich als kausal bewiesen dargestellt. [A] [P]

## 4. Kapazität, Parameter und Ressourcen

Die Kapazitätsprüfung bleibt unverändert: derselbe Prompt mit 64 Inhaltstokens,
ein Warmup-Paar, drei gemessene Paare mit jeweils einem beziehungsweise 32
angeforderten Tokens; höchstens 1.500/5.000 Millisekunden, 768 MiB beobachteter
Prozessspeicher, 120 Sekunden Prozessfrist und 1 GiB Heap.
Der Audit berechnet die Maxima selbst aus den Rohzeilen und prüft, dass Ladezeit
plus alle acht Aufrufzeiten innerhalb der protokollierten Prozessdauer liegen.
[A] [B]

| V3-Messung | Small | Wide |
|---|---:|---:|
| Parameter | 132.160 | 6.562.304 |
| Reine Float32-Parameterbytes | 528.640 | 26.249.216 |
| Ausgewählter Checkpoint: berichtete Dateigröße | 529.673 Byte | 26.250.249 Byte |
| Vollständiger Trainingsprozess einschließlich Prüfungen | 12,816 s | 583,046 s |
| Letzter protokollierter Trainingszeitpunkt | 11,274 s | 560,725 s |
| VMHWM im Kapazitätslauf vor Training | 79,49 MiB | 176,95 MiB |
| VMHWM im Kapazitätslauf nach Training | 67,64 MiB | 167,64 MiB |
| Ersttoken-Beobachtungen nach Training | 0/3 | 3/3 |
| Vollständige 32-Token-Ausgaben nach Training | **0/3** | **0/3** |
| Nachtrainings-Kapazitätsentscheidung | **FAIL** | **FAIL** |

Small erzeugt in sämtlichen acht Nachkapazitätsaufrufen sofort EOS und null
Inhaltstokens. Wide erzeugt in sämtlichen acht Aufrufen ein Inhaltstoken und
anschließend EOS. Insbesondere produziert auch jede Anforderung von 32 Tokens
nur ein Token. Die für Wide berichteten maximalen Aufrufzeiten von
182,574 Millisekunden für den Ersttokenaufruf und 171,792 Millisekunden für die
32-Token-Anforderung sind daher keine erfolgreiche 32-Token-Leistungsabnahme.
[A] [V3]

Die 583,046 Sekunden des Wide-Prozesses liegen innerhalb seines registrierten
900-Sekunden-Rahmens. V2 hatte 653,790 Sekunden benötigt. Diese beobachtete
Differenz ist ohne zusätzliche Kontrolle der Hostlast kein isolierter
Geschwindigkeitsnachweis des kürzeren Prompts. Die registrierten
Trainingsgrenzen bleiben für V2/V3 gleich: 3 GiB Heap und 900 Sekunden außen,
840 Sekunden innerhalb der Trainingsschleife. Die Speicherwerte in der Tabelle
gehören zu frischen Kapazitäts-JVMs, nicht zum Spitzenspeicher des Trainings.
[A] [P]

Checkpointpfade, Größen, Parameterzahlen, gehashte Logketten und berichtete
Decoderprüfungen stimmen überein. Die echten binären Checkpoints, JARs und
Tokenizer wurden in diesem Review nicht neu gehasht. Eine konsistente Quittung
ist zudem keine kryptografisch signierte Prozess- oder Hostattestierung.

## 5. Die Studienwahl stimmt; die stärkere Nachweisbindung fehlt im Freeze

Die veröffentlichte Regel wählt nach Wide-Dev-Treffern, danach Antwort-NLL und
bei vollständigem Gleichstand V2. V3 gewinnt mit 16 gegenüber 13 Treffern.
Die gespeicherte Auswahl enthält jeweils die vollständigen vier Arme derselben
Version; ein Small-Kontrollmodell aus V2 wird nicht in V3 hineingemischt.
Der endgültige Freeze ist exakt gleich dem gewählten V3-Payload. [A] [F]

Der Audit rechnet folgende veröffentlichte Hashes unabhängig nach:

| Datei | SHA-256 |
|---|---|
| `development-study-choice.json` | `1325d06208d8850e6ce3aa5969d68f37cdaa3e26c46c50f66743ddd3dc442cac` |
| `selection-freeze.json` | `6c069dd1ab21d792b9985333b14b2d6a0c51b66affbc8a90de0f37092c5a49f4` |

`before-test-verification.json` nennt genau diese Hashes, V3, den
Ausführungscommit `be4e…`, identischen Choice/Freeze und fehlende Testlogs.
Der Evidenzcommit ist mit **4. November 2026, 20:31:51 Uhr Wiener Zeit**
datiert. Im geprüften Baum fehlen tatsächlich veröffentlichte finale Testlogs.
Diese Git-Beobachtung beweist keine Abwesenheit anderer, unveröffentlichter
Prozesse. Eine später tatsächlich erfolgte Finaltestauswertung müsste separat
belegt und als verbrauchter Holdout behandelt werden. [A] [F]

**Der gespeicherte Freeze enthält weiterhin kein gebundenes
`postTrainingCapacity`-Objekt.** Seine S1-/W1-Auswahlen tragen lediglich den
alten Hinweis `REQUIRES_SEPARATE_BENCHMARK`. Die Kapazitätsrohbelege sind zwar
jetzt vorhanden und wurden hier unabhängig ausgewertet. Ihre notwendige
Bindung und erneute Prüfung durch den tatsächlichen Testpfad folgt aber nicht
aus dem bloßen Hashabgleich der alten Choice-/Freeze-Dateien. Die separate
Gate-Reparatur und ihre Übernahme werden von den beiden anderen Helfern
beurteilt; dieser Ergebnisreview autorisiert keinen Modell- oder Testprozess.
[A] [F]

Der bisherige Handoff-Text bezeichnet V3 noch als laufend und nennt keinen
finalen Freeze. Die neu veröffentlichten Rohdaten und Auswahlquittungen sind
für diese Punkte der neuere Beleg. Der Handoff wird hier nicht umgeschrieben.

## 6. Abnahmeentscheidung

V3 hat einen vollständigen, nachvollziehbar gewählten Entwicklungskandidaten.
Der begrenzte Entwicklungsscore steigt auf 16/24; der ursprüngliche
Kapazitätsvertrag bleibt verletzt. Es gibt am geprüften Stand keinen
veröffentlichten Finaltestnachweis und keine allgemeine Modellfreigabe.

Das registrierte Protokoll bezeichnet V3 als letzte Entwicklungsvariante
dieser Runde. Die nächste offene Grenze ist die korrekt gebundene
Testfreigabe und anschließend ein nachvollziehbar veröffentlichter einmaliger
Finaltest, soweit der verantwortliche Studienzweig diese Voraussetzungen
erfüllt. Aus späteren Testresultaten darf dieselbe Runde nicht erneut
optimiert werden; weitere Entwicklung benötigt eine neue unabhängige
Bestätigungsmenge. Dieser Review wählt keine weitere Wrapper-, Parameter-
oder Lernratenvariante und verändert keine historischen Quellen, Quittungen
oder Checkpoints. [P]

## Reproduktion und Quellen

Mit vorhandenem gepinntem Git-Commit, vom Repositoryroot:

```sh
python experimental/weight-space-20261104/continuation-review/results/audit_published_v3.py --repo . --output /tmp/pr508-v3-review.json
```

Erwartet werden Exit 0 und der oben genannte Status. Die JSON-Ausgabe ist
deterministisch. Sie enthält alle Quellhashes, Git-Blob-IDs, Bytelängen,
neu berechneten Werte und expliziten Aussagegrenzen.

- **[A]** [Unabhängige vollständige Nachrechnung und Quelleninventar](published-v3-results.json).
- **[V3]** [Veröffentlichte V3-Zusammenfassung und Rohbelege](https://github.com/Ullmann27/device-ai/tree/9ceffc26a2159c8dc726c1f13d2882119159c130/docs/evidence/lm1-training-recovery-20261104/study-v3).
- **[F]** [Veröffentlichte Studienwahl, Freeze und Vorprüfquittung](https://github.com/Ullmann27/device-ai/tree/9ceffc26a2159c8dc726c1f13d2882119159c130/docs/evidence/lm1-training-recovery-20261104/final-selection).
- **[P]** [Prospektiv registriertes Suffix-/Auswahlprotokoll](https://github.com/Ullmann27/device-ai/blob/be4e49600fed1db4c697230bb8e42c150dbfbdaf/experimental/lm1-training-recovery-20261104/suffix-study/SUFFIX_PROTOCOL.md).
- **[B]** [Unveränderter ursprünglicher Kapazitätsvertrag](https://github.com/Ullmann27/device-ai/blob/9ceffc26a2159c8dc726c1f13d2882119159c130/experimental/weight-space-20261104/LmCapacityBenchmark.kt).
