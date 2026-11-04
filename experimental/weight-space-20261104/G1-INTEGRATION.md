# G1-INTEGRATE-WEIGHT-002: geprüfte Trainingsfreigabe

## Entscheidung

Der P0-Befund aus Helfer-PR #497 ist bestätigt. Dessen veröffentlichte Reparatur
in `06f071e5850dd405f374bbb58d826f64740d83af` reicht allein nicht für die Abnahme:
Sie sperrt einige unmittelbar erkennbare Fehlerzustände, vertraut aber weiterhin
gespeicherten Freigabefeldern. Die Übernahme in PR #492 ergänzt deshalb eine
Prüfung der tatsächlich vorhandenen, an Eingaben und Programm gebundenen Belege.
Der Helferbranch wird nicht vollständig übernommen.

Die hier abgenommene Datei `run_experiment.py` hat SHA-256
`9a68ddf27158692044738aed96892ad6476a520b7e30ab6651adfff2c4f38447`.
Dieser Hash bezeichnet die P0-Integrationsfassung vor einer folgenden,
gesondert registrierten Forschungsrunde.

## Quellstände und unabhängiger Vergleich

| Stand | Commit | SHA-256 der Python-Orchestrierung |
|---|---|---|
| Vom Auftrag bezeichnete Basis | `ee5779725690c573d3310c061c6e2e6faf5f0a8b` | `609215b0f2671e2407c8b81867255bd1ce60680626c081fd91b0a12d14f62deb` |
| Integrationsbeginn von PR #492 | `88d7b05072bdec6250990efaa9914a1e6744c29b` | `6b14f23ef9b595008099b16d81f1121783fea0bb67e7f3d942e44eed6405e425` |
| Veröffentlichte Helferreparatur #497 | `06f071e5850dd405f374bbb58d826f64740d83af` | `6bd9c00c635f100fdedc05e60a1d720565e43ba7e6722bdc7cf7188f4a87487d` |
| Geprüfte Integration | Diese Änderung | `9a68ddf27158692044738aed96892ad6476a520b7e30ab6651adfff2c4f38447` |

Die unabhängige synthetische Matrix enthält 33 unzulässige Zustände und drei
erlaubte Kontrollfälle. Dieselben Fallnamen und Entscheidungen werden gegen
die unveränderten Quelldateien geprüft. Die Belege enthalten echte
Kommando-, Protokoll-, Eingabe- und Ergebnisfelder; nur die drei festen
Inhaltskonstanten für Modell, Tokenizer und Holdout werden an die synthetischen
Dateien gebunden. Entscheidungsfunktionen werden nicht ersetzt.

| Stand | Bestandene Fälle | Unzulässige Zustände bis zur Trainingsgrenze | Ungeprüft akzeptierte fehlerhafte Wiederaufnahmen |
|---|---:|---:|---:|
| Angeforderte Basis `ee577972` | 7/36 | 27 | 2 |
| Integrationsbeginn #492 | 7/36 | 27 | 2 |
| Helferreparatur #497 | 10/36 | 24 | 2 |
| Geprüfte Integration | **36/36** | **0** | **0** |

Sechs zusätzliche, gezielt aus der unabhängigen Quellprüfung abgeleitete Fälle
prüfen den direkten Einstieg in `final()` mit und ohne vorhandenen Abschluss
sowie fehlende oder veränderte Erzeugungsbelege. Auch diese bestehen mit
**6/6**. Die frühere Zwischenfassung bestand diese sechs Fälle nicht. Die Tests
fangen Trainingsversuche und jeden externen Prozess ab; die positiven Fälle
belegen Erreichbarkeit der erlaubten Trainingsgrenze, kein wirkliches Training.

Eine dritte, getrennte Suite führt den tatsächlichen zentralen `call()`-Pfad aus
und ersetzt nur den Prozessstart. **4/4 Fälle bestehen:** erlaubter direkter
Aufruf, gesperrter direkter Aufruf nach Baseline-Fehler, derselbe Fehler trotz
Trainingscache und ein nach der Autorisierung ausgetauschter Eingabecheckpoint.
Beim letzten Fall verändert ein Python-Trace ausschließlich die synthetischen
Checkpointbytes nach dem tatsächlichen Rücksprung aus der Autorisierung. Die
Freigabeentscheidung selbst bleibt unverändert. Der frühere Zwischenstand
bestand dabei 3/4 Fälle; die endgültige Fassung erkennt den Austausch vor dem
Prozessstart. Zusammen ergeben sich **46 bestandene gezielte Prüffälle**.

Die vollständigen Fallentscheidungen, Quellen- und Harnesshashes liegen unter
[`g1-evidence/`](g1-evidence/). Die Tests beanspruchen keine mathematisch
vollständige Fehlerfreiheit; sie reproduzieren den ursprünglichen Befund und
prüfen zusätzlich die konkret festgestellten Umgehungswege.

## Ursprünglicher und korrigierter Ablauf

| Stelle | Ursprüngliches Verhalten | Verhalten nach der Reparatur |
|---|---|---|
| Baseline fehlgeschlagen | Ein späterer Fortsetzungsaufruf konnte trotzdem zum kleinen Training gelangen. | `BASELINE_GATE` und fehlende Startfreigabe brechen vor jedem Training ab. |
| Gespeicherte Leiter | `lastPassed` und gespeicherte Freigabefelder wurden weitgehend übernommen. | Kanonische Baseline, Kalibrierung, vollständige Reihenfolge und tatsächliche Kapazitätsbelege werden erneut gelesen und geprüft. |
| Erweiterte Zielversion | Ein unzulässiges größeres Ziel konnte erst nach dem kleinen Training auffallen. | Beide Eingangsmodelle müssen vor dem ersten Training zulässig sein. |
| Direkter Trainingsaufruf | Eine Prüfung nur im normalen Phasenablauf erfasste nicht jeden Einstieg. | Der zentrale Aufrufpfad autorisiert Modus, Label, Quelle, Daten, Ausgabeort und Zeitlimit. |
| Erzeugungsnachweis | Vorhandene Checkpoints und Paritätswerte allein erklärten ihre Entstehung nicht vollständig. | Elternmodell, Verbreiterungsfaktor beziehungsweise zusätzliche Schichten, Seed, Rauschen und Ergebnischeckpoint müssen zum gespeicherten Aufruf passen. |
| Wiederaufnahme | Ein vorhandener eingefrorener Stand konnte einen frühen Rücksprung bewirken. | Alle Startbelege, Trainingsausgaben, Entwicklungswerte, Zulässigkeitsentscheidungen und die Auswahlregel werden vorher rekonstruiert. |
| Direkter Abschluss | `final()` konnte unabhängig vom normalen Fortsetzungspfad erreicht werden. | Die gleiche Vorprüfung und eingefrorene Auswahl werden vor Rücksprung oder Holdout-Zugriff geprüft. |

Fehlende Belege lösen keine Ersatzmessung aus. Ein bereits dokumentiertes
Scheitern wird nicht durch einen frischen Modellprozess stillschweigend ersetzt.
Ein später gescheiterter Leiterschritt darf die letzte nachweislich bestandene
Stufe erhalten; unvollständige oder widersprüchliche Leitern dürfen das nicht.

Historische Nachweise ohne die später eingeführten Python-Hashfelder bleiben
nur bei vollständiger ursprünglicher Befehls- und JVM-Identitätsbindung gültig.
Teilweise ergänzte oder fremde Bindungen werden zurückgewiesen. Die Prüfung
verlangt dadurch keine nachträgliche Umschreibung der ursprünglichen Messdaten.

## Checkpoints und tatsächliche Wiederaufnahme

Eine unabhängige Prüfung des Hauptagenten hat die tatsächlich abgeschlossenen
Phasen `continuation()` und `final()` mit gesperrten Prozessstarts, Schreibzugriffen
und Holdout-Lesezugriffen wiederaufgenommen. Beide Aufrufe bestehen. **122 Dateien
bleiben bytegleich:** 119 bisherige Ergebnisdateien und die drei ursprünglichen
Modell-/Tokenizer-/SWB1-Eingaben. Zusätzlich stimmen sieben Quellhashes und die
separat erfassten größeren Checkpoints mit dem Zustand vor Beginn überein.

Eine getrennte Prüfung des Reparaturagenten umfasst außerdem die unmittelbare
Vorprüfung und Rekonstruktion der eingefrorenen Auswahl. Der unabhängige
Quellprüfer hat die drei zusätzlichen Befunde anschließend als geschlossen
bestätigt. Die Zuständigkeiten und tatsächlichen Ausführungen sind in den
Evidenzdateien getrennt bezeichnet.

Die eingefrorene historische Auswahl bleibt bei SHA-256
`8c433e76c28ceafa5e5149c9b0469d37eb4e217f1887868bce2a2ed1330dc6f9`.
Das kleine Original bleibt die bevorzugte Qualitätsauswahl. Beide historischen
Fortsetzungen bleiben wegen unvollständiger Generierung unzulässig. Kein
Checkpoint wird durch die Gate-Reparatur geändert oder neu trainiert.

## Parameterzahl und Speicher

Diese Werte stammen aus den vorhandenen historischen Checkpoints und Messungen;
sie sind keine neuen Messläufe der Integrationsprüfung.

| Bestehende Version | Trainierbare Parameter | FP32-Parameterbytes | Checkpointbytes | Bisher gemessener maximaler Prozessspeicher |
|---|---:|---:|---:|---:|
| Original | 132.160 | 528.640 | 529.297 | 80,789 MiB |
| Breite 8 | 6.562.304 | 26.249.216 | 26.250.085 | 152,570 MiB |
| Tiefe 3 | 9.712.128 | 38.848.512 | 38.849.588 | 197,750 MiB |
| Tiefe 5 | 16.011.776 | 64.047.104 | 64.048.511 | 257,215 MiB |

FP32-Parameterbytes sind `4 × Parameterzahl`. Prozessspeicher umfasst weitere
Laufzeitstrukturen; Trainingsspeicher ist damit nicht gleichgesetzt. Für Tiefe 5
verwendet das Gate den höchsten vorhandenen Messpunkt von **263.388 KiB**. Der
historische Schlusswert von 262.548 KiB bleibt im Rohlog unverändert. Die
konservativere Auswertung ändert die damalige Zulässigkeitsentscheidung nicht.

## Unveränderte Anforderungen

- Höchstens 1.500 ms für den separaten Ein-Token-Aufruf und 5.000 ms für den
  Aufruf mit 32 tatsächlich erzeugten Tokens.
- Höchstens 768 MiB maximal beobachteter Prozessspeicher; frische JVM mit
  höchstens 1 GiB Heap für Kapazitätsmessungen und 120 Sekunden Prozessfrist.
- Ein Aufwärmpaar und drei vollständige Messpaare am gebundenen 64-Token-Prompt.
  Frühes EOS im Aufwärmen war bereits erlaubt; unvollständige gemessene
  Generierungen erfüllen die Anforderung weiterhin nicht.
- Maximal 64 MiB pro Checkpoint; unveränderte numerische Paritätsgrenzen.
- Fortsetzung mit 16 Schritten, Batch 4, Lernrate 0,0001, Weight Decay 0,01,
  zwei Warmup-Schritten, Gradient-Clipping 1 und zwei Trainingsworkern;
  höchstens 3 GiB JVM-Heap und 600 Sekunden Prozessfrist.
- Identische Datensatz-, Tokenizer- und Tokenablaufhashes für kleinen und
  erweiterten Kontrolllauf.
- Auswahl nach minimalen Entwicklungs-BPB unter zulässigen Kandidaten;
  innerhalb von 0,00001 BPB hat die kleinere Version Vorrang.

Produktive Modelldateien, produktive Laufzeit und das ursprüngliche
`PROTOCOL.md` bleiben unverändert. Die Integration führt weder Modell-Promotion
noch produktives Training aus.

## Grenzen und anschließende Forschung

Die Python-Prüfung bindet die autorisierten Identitäten vor dem Aufruf, prüft sie
unmittelbar vor dem Prozessstart und nach dessen Abschluss erneut. Der
unveränderte JVM-Lader erhält keinen extern vorgegebenen erwarteten Hash.
Atomare Sicherheit gegen beliebige gleichzeitige Dateiaustausche zwischen diesen
Zeitpunkten wird daher nicht behauptet.

Bei einem bereits abgeschlossenen `final.json` sind Auswahlhash und
Kandidatenidentitäten geprüft. Gespeicherte Holdout-Messwerte werden auf diesem
reinen Wiederaufnahmepfad nicht nochmals aus sämtlichen Auswertungsbelegen
abgeleitet. Das ist eine getrennte Einschränkung der Berichtsprüfung; sie
autorisiert kein Training und ändert keine historischen Werte.

Erst nach dieser Übernahme darf die nächste Forschungsrunde starten. Für
6–10 Millionen Parameter sind die bestehenden Größen 6.562.304 und 9.712.128
geeignet. Sie benötigen einen ausdrücklich registrierten Budget-Stopp,
getrennte Ergebnisverzeichnisse und einen neuen Holdout. Eine künstlich als
Fehler gekennzeichnete Leiter oder ein direkter Trainingsaufruf außerhalb der
geprüften Freigabe ist dafür nicht vorgesehen. Die bisherigen Ergebnisse
bleiben bei einem fehlgeschlagenen neuen Gate erhalten.
