# G1: Ergebnis der registrierten 6–10M-Forschung

## Ergebnis und Entscheidung

Die P0-Reparatur ist unabhängig geprüft und in PR #492 übernommen. Erst danach
wurde die getrennte Forschung mit **6.562.304 und 9.712.128 eigenen trainierbaren
LM1-Parametern** registriert und ausgeführt.

Beide großen Initialmodelle bestehen die unveränderten Paritäts-, Integritäts-
und Kapazitätsprüfungen. Die vier anschließenden Trainingsläufe laufen jeweils
vollständig mit 16 Schritten und derselben Folge von 3.255 Trainingstokens durch.
Ihre erzeugten Kandidaten scheitern jedoch an der vollständigen Textgenerierung:
**Alle vier Fortsetzungen liefern am festen Prüf-Prompt sofort EOS und null
neue Tokens.** Beide großen Fortsetzungen verschlechtern zusätzlich die
Entwicklungs- und Holdout-BPB. Diese trainierten Kandidaten sind ausgeschlossen.

Die bisherigen Checkpoints bleiben unverändert. Das kleine Original bleibt nach
der unveränderten Auswahlregel der Qualitätssieger. Die beiden größeren
Initialmodelle bleiben bestandene Kapazitätskandidaten; ein Qualitäts- oder
Instruktionsgewinn ist damit nicht nachgewiesen. Es gab keine Modell-Promotion,
produktiven Trainingsläufe oder Änderung der produktiven Modellruntime.

Die vollständige unabhängige Ergebnisprüfung steht in
[`independent-completed-audit.json`](core-6-10m-evidence/run/independent-completed-audit.json),
der getrennte Abgleich mit den Rohlogs in
[`result-review.json`](core-6-10m-evidence/result-review.json).

## Nachweis der vorgeschriebenen Reihenfolge

| Schritt | Unveränderliche Identität |
|---|---|
| Angeforderte ursprüngliche Basis | `ee5779725690c573d3310c061c6e2e6faf5f0a8b` |
| Unabhängig geprüfter Helfer-PR #497 | `06f071e5850dd405f374bbb58d826f64740d83af` |
| Übernahme der erweiterten P0-Reparatur | Commit `8e2a2370a9392aeff6bb68a8e2b4b0c1caaa68b2` |
| Registrierung der Folgeforschung vor allen echten Modellläufen | Commit `3e571d6390c672ab86f5961d50700f56084ff6ef` |
| Tatsächlich ausgeführter Driver, SHA-256 | `8f2f49bda56ce1f63e86fecf262229acff3d6f5d1ada1d97270b6c1731cda9be` |
| Zusatzplan, SHA-256 | `abbc40f6128637072630b38685a933dab5528f2ec458696798ce5abba01750fd` |
| Tatsächlich verwendete JAR, SHA-256 | `1b6d22ee57977a6e27ec2d0b72e57fab3a35fb8e694905c1270a4ffe87a2f9ba` |

Der Vergleich zwischen ursprünglichem Ablauf, Helferreparatur und integrierter
Reparatur ist in [G1-INTEGRATION.md](G1-INTEGRATION.md) dokumentiert. Der
Helferstand bestand allein 10/36 der unveränderten Gegenproben; die integrierte
P0-Fassung bestand 36/36 plus zehn gezielte Zusatzfälle. Die finale
Profilergänzung bestand anschließend **29 neue und alle 46 bisherigen Fälle,
insgesamt 75/75**, bevor ein neuer Trainingsprozess gestartet wurde.

Die neuen Profile haben feste Ziele und explizite Budgetabschlüsse. Das 6,56M-
Profil endet regulär vor der 9,71M-Stufe. Das zweite Profil endet vor 12.861.952
Parametern, weil die registrierte Obergrenze 10.000.000 beträgt. Diese Abschlüsse
sind geplante Versuchsgrenzen. Es wurde kein Ressourcenfehler erfunden und keine
größere, ungeprüfte Stufe als Trainingsquelle eingesetzt.

Nach beiden abgeschlossenen Fortsetzungen wurde die gemeinsame Auswahl
eingefroren. Ihr SHA-256 lautet:

`7a98a360ba274b1d452237b7a69007691a687593f68092dff5a82a001cb45ac3`

Sie bindet die beiden Child-Auswahlen:

- 6,56M: `84892007baa8a15a0451eb2541e7b36d95d43819417990115047b49eab27ff89`
- 9,71M: `f9395441c7aa3d7fd8301bff53f6df8d09c222584953a9c3bc2f3f45de555096`

Der zusätzliche Freigabebeleg wurde **vor jeder Holdout-Auswertung** geschrieben
und dokumentiert, dass zu diesem Zeitpunkt keine Holdout-Auswertungsdateien
existierten. Beide Abschlussauswertungen enthalten denselben gemeinsamen Hash.
Der unabhängige Review bestätigt auch die zeitliche Reihenfolge aus lokalen
Dateizeiten und Prozessdauern. Die primäre Absicherung ist der geprüfte
Programmablauf mit gebundenen Auswahlhashes, nicht allein diese Dateizeitprüfung.

## Tatsächlich gemessene Kapazität der Initialmodelle

Alle Werte beziehen sich auf diesen Linux-Host mit Java 21. Jede Messung läuft
in einer frischen JVM, mit einem festen 64-Token-Prompt, einem Aufwärmpaar und
drei gemessenen Paaren. Die angegebenen Zeiten sind die Maxima der drei
Messungen. Der Ein-Token-Wert umfasst den gesamten separaten Aufruf; er ist
kein Zeitstempel einer gestreamten ersten Ausgabe.

| Initialmodell | Parameter | Checkpointbytes | Maximaler Ein-Token-Aufruf | Maximaler 32-Token-Aufruf | Maximal beobachteter Prozessspeicher |
|---|---:|---:|---:|---:|---:|
| Original, Kontrollmessung im 6,56M-Profil | 132.160 | 529.297 | 8,259 ms | 15,739 ms | 78,930 MiB |
| Original, Kontrollmessung im 9,71M-Profil | 132.160 | 529.297 | 9,606 ms | 18,731 ms | 82,762 MiB |
| Breite 8 | 6.562.304 | 26.250.085 | **178,254 ms** | **260,890 ms** | **160,508 MiB** |
| Tiefe 3 | 9.712.128 | 38.849.588 | **248,655 ms** | **367,843 ms** | **193,270 MiB** |

Alle drei gemessenen Ein-Token-Aufrufe erzeugen jeweils ein Token, alle drei
32-Token-Aufrufe jeweils 32 Tokens. Die Höchstwerte bleiben unter den
unveränderten Grenzen von 1.500 ms, 5.000 ms und 768 MiB. Das Benchmark-JVM-
Heaplimit beträgt weiterhin 1 GiB, die äußere Prozessfrist 120 Sekunden und
das Checkpointlimit 64 MiB.

Die reinen FP32-Parameterdaten umfassen 528.640 Bytes beim Original,
26.249.216 Bytes beim 6,56M-Modell und 38.848.512 Bytes beim 9,71M-Modell.
Checkpointdateien enthalten zusätzlich Header, Metadaten und Prüfsumme.
Prozessspeicher umfasst weitere Laufzeitstrukturen. Die gemessenen
Inferenzwerte sind kein Nachweis des Trainingsspeicherbedarfs und keine
Android- oder Fold7-Leistungsmessung.

## Fortsetzungstraining und tatsächliche Gewichtsänderungen

| Vergleichsarm | Schritte | Trainingstokens | Tatsächlich veränderte Floatwerte | Protokollierte Trainingsphase | Gesamter JVM-Aufruf |
|---|---:|---:|---:|---:|---:|
| Kleine Kontrolle zum 6,56M-Profil | 16 | 3.255 | 132.159 | 0,811 s | 1,269 s |
| Große 6,56M-Fortsetzung | 16 | 3.255 | **6.562.289** | **22,904 s** | **23,612 s** |
| Kleine Kontrolle zum 9,71M-Profil | 16 | 3.255 | 132.159 | 0,742 s | 1,177 s |
| Große 9,71M-Fortsetzung | 16 | 3.255 | **9.712.110** | **32,453 s** | **33,381 s** |

Die vier Prozesse verwenden dieselben ursprünglichen TRAIN-Daten, denselben
Tokenizer und dieselbe tatsächliche Tokenfolge. Es sind vier Wiederholungen
derselben Folge, nicht vier voneinander verschiedene Datensätze. Die Quelle
enthält 77 ursprüngliche TRAIN-Dokumente; ihre bereinigte Fassung umfasst 2.520
Segmente. Development und Holdout werden nicht in das Training übernommen.

Unverändert bleiben Batchgröße 4, Seed 20261104, Lernrate 0,0001, AdamW-Weight-
Decay 0,01, Warmup 2, Gradient-Clipping 1, zwei Worker, maximal 600 Sekunden
Prozessdauer und 3 GiB JVM-Heap. Die gezählten Floatänderungen wurden unabhängig
direkt aus Eingangs- und Ausgangscheckpoints nachgezählt. Sie umfassen auch
Weight-Decay-Updates und belegen für sich keinen Wissenserwerb oder Qualitätsgewinn.

Nach dem Training liefern **alle vier** Kandidaten in jedem der acht
Kapazitätstrials, einschließlich Aufwärmen, EOS mit null neuen Tokens. Der
Ausschluss beruht damit auf tatsächlich unvollständiger Ausgabe. Eine schnelle
leere Antwort erfüllt die geforderte Arbeitslast nicht. Die beiden erweiterten
Arme sind voneinander unabhängige Vergleiche mit jeweils geprüftem Initialmodell;
der zweite Arm wurde nicht aus dem bereits gescheiterten trainierten ersten
Arm fortgesetzt.

## Qualität auf Entwicklung und neuem Holdout

BPB bedeutet Bits pro ausgewertetem Textbyte; niedriger ist besser. Der neue,
vorab gebundene Testsatz besteht aus **32 synthetischen deutschen Absätzen**.
Die Datei enthält 4.997 Bytes; nach Entfernen der Absatztrenner werden 4.934
Textbytes beziehungsweise 2.544 Tokens bewertet. Sein SHA-256 ist
`a910f25ed4a54742e87c3e75c105472f0c882df2d4026e15a62ee3d48c90bbbd`.

| Kandidat | Entwicklungs-BPB | Neuer Holdout-BPB | Kapazitätsgate | Auswahl |
|---|---:|---:|---|---|
| Original | 2,352541416 | 3,307624861 | Bestanden | **Bevorzugt** |
| Initial 6,56M | 2,352541414 | 3,307624813 | Bestanden | Kapazitätskandidat |
| Initial 9,71M | 2,352541414 | 3,307624813 | Bestanden | Kapazitätskandidat |
| Kleine Kontrolle, in beiden Profilen identisch | 2,352506801 | 3,308117546 | Fehlgeschlagen: EOS/0 Tokens | Ausgeschlossen |
| Trainiert 6,56M | 2,436533181 | 3,369359256 | Fehlgeschlagen: EOS/0 Tokens | Ausgeschlossen |
| Trainiert 9,71M | 2,448411930 | 3,380979223 | Fehlgeschlagen: EOS/0 Tokens | Ausgeschlossen |

Die Unterschiede zwischen Original und großen Initialmodellen liegen im Bereich
numerischer Rundung. Das entspricht ihrer funktionserhaltenden Erzeugung.
Die 6,56M-Fortsetzung verschlechtert Development um **3,570 %** und Holdout um
**1,866 %** gegenüber dem Original. Bei 9,71M sind es **4,075 %** und **2,218 %**.
Die kleinen Kontrollen verbessern Development minimal, verschlechtern den neuen
Holdout und scheitern ebenfalls am Generierungsgate.

Die Auswahl wird ausschließlich aus zulässigen Kandidaten nach Entwicklungs-BPB
getroffen. Innerhalb von 0,00001 BPB gewinnt die kleinere Version. Deshalb bleibt
das Original mit 132.160 Parametern bevorzugt. Die Zielgröße von mindestens sechs
Millionen Parametern bindet den großen Trainingsarm, nicht einen künstlich
erzwungenen Qualitätssieger. Der Holdout verändert diese eingefrorene Auswahl
nicht nachträglich.

## Integrität und Wiederaufnahme

Der unabhängige Prüfer importiert den Driver nicht. Er kontrolliert das LM1C-
Format, interne SHA-256-Prüfsummen, Matrixnamen/-formen, alle endlichen Floatwerte,
Parameterzahlen, inhaltsadressierte Dateinamen, tatsächliche Gewichtsänderungen,
Trainingsherkunft, JVM-Klasse, Heap-/Zeitgrenzen, Profilbindungen, Loghashes und
einzelne Auswertungs-/Kapazitätszeilen. Der Bericht besteht:

- **83 Ausführungsbelege** und ihre Eingaben/Logs geprüft.
- **23 erzeugte Checkpointdateien** in beiden Profilordnern sowie das Original
  geprüft; gleiche Dateien in getrennten Vergleichsarmen zählen dabei getrennt.
- Entwicklungs- und Holdout-BPB aus den Einzelzeilen nachgerechnet.
- Beide Profilentscheidungen und die gemeinsame Auswahl unabhängig rekonstruiert.
- **122 historische Ergebnis-/Eingabedateien** bytegleich mit dem G1-Ausgangsstand.
- Sieben ursprüngliche Runtime-/Werkzeug-/Protokoll-Quellhashes unverändert.

Eine weitere unabhängige Prüfung nimmt die tatsächlich abgeschlossene Studie
über beide `continuation()`-Aufrufe, `study_freeze()` und beide vorhandenen
`final()`-Ergebnisse wieder auf: **5/5 Aufrufe bestanden**, null Prozess-,
Holdout-Lese- oder Schreibversuche nach dem normalen Konstruktor. **205 Dateien**
in beiden Profilen einschließlich Registrierung und gemeinsamem Freeze behalten
identische Pfade und SHA-256-Werte. Details stehen in
[`completed-study-resume.json`](core-6-10m-evidence/completed-study-resume.json).

## Grenzen und erhaltene Forschungsstände

Auch die sechs bereits festgelegten, streng nach exakter Antwort bewerteten
Rechen-/Instruktionsproben bleiben für alle geprüften Kandidaten bei **0/6**.
Die größere Parameterzahl erzeugt hier also keinen belegten praktischen
Instruktionsgewinn.

Der kleine synthetische Holdout ist kein allgemeiner Fähigkeitsbenchmark.
Gleiche Optimierereinstellungen isolieren außerdem keinen kausalen Effekt der
Parameterzahl: Verbreiterung und zusätzliche Schichten ändern die
Optimierungsgeometrie. Dass auch die kleinen Kontrollen die Generierungsanforderung
verfehlen, spricht gegen eine Erklärung allein durch größere Modellgröße.
Es gibt hier keinen belegten allgemeinen Intelligenzgewinn.

Die Python-Prüfung bietet keine atomare JVM-Ladegarantie gegen beliebige
gleichzeitige Dateiaustausche. Auch der vorhandene Abschlusscache rekonstruiert
seine numerischen Holdoutwerte beim einfachen Resume nicht selbst aus allen
Rohbelegen. Für diese tatsächliche Studie wurden die Werte deshalb zusätzlich
durch den unabhängigen Auditor und den getrennten Rawlog-Review geprüft.

Der frühere bestandene 16.011.776-Parameter-Kapazitätsstand bleibt unverändert
erhalten. Der vorliegende Folgeversuch erweitert weder dessen Freigabe noch die
10M-Grenze seiner eigenen Registrierung. Nach den fehlgeschlagenen
Fortsetzungsgates erfolgt keine Anpassung dieses Rezepts anhand des nun bekannten
Holdouts. Eine spätere Optimierungsrunde benötigt einen neuen vorab festgelegten
Plan und einen neuen Testsatz.

Alle Rohbelege dieses Versuchs liegen unter [`core-6-10m-evidence/run/`](core-6-10m-evidence/run/).
Der [Kopiernachweis](core-6-10m-evidence/run-manifest.json) bindet alle 188
archivierten Dateien an die tatsächlich ausgeführten Ergebnisse. Die großen
Checkpointbinärdateien werden separat bereitgestellt; verworfene trainierte
Gewichte sind nicht Bestandteil des bereitgestellten Prüfpakets.
