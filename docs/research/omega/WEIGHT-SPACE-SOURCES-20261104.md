# Gewichtsänderung und Modellerweiterung: Quellen und Aussagegrenzen

Stand: 04.11.2026. Diese Notiz ordnet die geprüften Quellen und die eigenen
Ausgangsmodelle ein. Ergebnisse der laufenden Kapazitätsstufen gehören in den
abschließenden Versuchsbericht; hier wird kein größerer Kandidat als erfolgreich
oder freigegeben ausgewiesen.

## 1. Zugeordnetes Video

- Originaldatei: `f6144be7e261f6fe4e704a1f4014ed76.mp4`.
- Originalablage: `Google Drive / Continuum_backupDatein / Jarvis Video`.
- [Drive-Quelle](https://drive.google.com/file/d/1HHhP_gtOx8LVbs6ouGFPiJ6_BJAw9Zhk/view).
- Dauer der lokal geprüften Datei: 113,233 Sekunden.
- SHA-256: `95d5afb2d854610dcebec56f898c3e50e59e7fd5570a583bbaf62d0ba50b0372`.

Der Videoabgleich beruht auf gesichteten Bildmontagen und per OCR erfassten
Untertiteln. Eine vollständige Audio-Transkription liegt nicht vor. Die fachlichen
Angaben unten wurden zusätzlich anhand der verlinkten Primärquellen geprüft.
Videodatei und vollständiger Untertiteltext sind nicht Bestandteil dieser Notiz.

## 2. Was die ursprüngliche Gewichtsarbeit berichtet

Die Veröffentlichung behandelt **Qwen3.5-0.8B**. Sie adressiert ausgewählte
MLP-Ausgabespalten in den Schichten 15 und 19: 131.072 Zielpositionen, davon
130.188 tatsächlich geänderte BF16-Werte, entsprechend ungefähr 0,016 % der
Modellparameter. Die Autoren berichten für ihre Auswertung MATH-500 von 7,9 %
auf 48,1 % und IFEval von 21 % auf 62,3 %. Das sind Ergebnisse des dort geprüften
Modells und Versuchsaufbaus; in diesem Arbeitslauf wurden diese Benchmarks nicht
reproduziert. Quellen: [Projektbeitrag](https://rikiyat.github.io/blog/weight-space-programming/)
und [veröffentlichte Modellkarte](https://huggingface.co/RikiyaT/Qwen3.5-0.8B-weight-space-programming).

Die Änderungen erfolgen ohne Gradienten-Training. Aufgabenbeispiele und
Rückmeldungen aus Auswertungen fließen jedoch in die Suche ein; daraus folgt
keine datenfreie Optimierung. Die dokumentierten Regeln schließen Änderungen
der abschließenden Normalisierung und konstante Skalierungen ganzer Tensoren
aus. Quelle: [Originalprotokoll der Mathematikversuche](https://github.com/RikiyaT/lm_understand-logs/blob/main/exp8_math/README.md).

## 3. Einordnung der eigenen Experimente

Die eigene Skalierung von `l0.n1`, `l1.n1` oder `nf` ist ausdrücklich eine
**gesonderte Kalibrierungsdiagnostik**. Sie repliziert die ursprüngliche
Spaltenänderung nicht und erfüllt deren obige Eingriffsbeschränkungen nicht.
Für LM1 wurde keine entsprechende, ursächlich nachgewiesene Richtung zur
Unterdrückung von Denkschleifen identifiziert. Die fremden Benchmarkgewinne
lassen sich deshalb nicht auf LM1 übertragen.

[Net2Net](https://research.google/pubs/net2net-accelerating-learning-via-knowledge-transfer/)
ist die allgemeine Referenz für die Übertragung einer gelernten Funktion in ein
größeres Netz. Die konkrete minGRU-Abbildung wurde am vorhandenen LM1-Code
umgesetzt und gesondert getestet: Kanalreplikation mit angepassten dichten
Gewichten und abschließendem Normalisierungsfaktor; zusätzliche Residualschichten
mit anfangs verschwindenden Ausgangspfaden. Die anfängliche Erhaltung der
Ausgaben belegt die Übertragung der Funktion. Sie belegt keinen Wissens- oder
Qualitätszuwachs durch die bloße zusätzliche Parameterzahl.

Versuchsgrenzen und Auswahlregeln stehen in
[`PROTOCOL.md`](../../../experimental/weight-space-20261104/PROTOCOL.md).
Die endgültigen Größen-, Laufzeit-, Fortsetzungs- und Qualitätsresultate sind im
abschließenden Versuchsbericht getrennt auszuweisen.

## 4. Tatsächlich geprüfte eigene SWB1-Sicherung

Prüfquelle dieses Arbeitslaufs:
`/workspace/scratch/0549bca61ccf/evidence/swb1-baseline.json`.

| Merkmal | Festgestellter Wert |
|---|---|
| Format | SWB1 |
| Parameter | 4.113 |
| Gespeicherter Update-Zähler | 335.150 |
| SHA-256 der ursprünglichen Sicherung | `5ba8ba7e22efc19a099148d224ab000dcd21953bb74b5d6504f49621fe56b2b6` |
| Sämtliche Parameter endlich | Ja |
| Dekodieren und erneutes Kodieren bytegenau | Ja |
| Original unverändert | Ja |

Der Update-Zähler dokumentiert den gespeicherten Zustand; er ist keine
Erfolgsquote und keine unabhängig rekonstruierte Trainingshistorie. SWB1 dient
hier als kleiner Intentions- und Ähnlichkeitsencoder. Die verwendeten
Testfälle sind vorhandene, selbst verfasste Regressionsfälle
(`EXISTING_AUTHORED_REGRESSION_FIXTURES_NOT_FRESH_HOLDOUT`), kein neuer
unabhängiger Holdout. Auch der Datensatzname `ANDROID_VALIDATION` bezeichnet
in diesem Lauf lediglich eine auf dem Host ausgeführte Teilmenge. Er belegt
keinen Test auf einem Android-Gerät.

## 5. Tatsächlicher Ausgangspunkt der LM1-Erweiterung

Der geladene eigene Sprachkern ist ein bereits trainiertes **LM1-minGRU-Modell
mit 132.160 Parametern**: Vokabular 512, Breite 64, zwei Schichten und MLP-Breite
256. Die geprüften Identitäten sind im Versuchsprotokoll festgehalten:

- LM1-Checkpoint: `7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd`.
- Gebundener Tokenizer: `94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f`.
- Herkunft im bestehenden Datenzweig: Commit `d2304a7658e1256b160c8012e1bac28268ff64a5`.

Dieses Modell ist weder Qwen noch Gemma. Die Erweiterung betrifft seine eigenen
LM1-Matrizen. Aussagen über Sprachqualität, Rechenfähigkeit oder flüssige
Ausführung benötigen die jeweiligen Messungen; aus einer Parameterzahl oder
einer bestandenen Übertragungsprüfung allein ergeben sie sich nicht. Messungen
auf dem Host bleiben von einer späteren Android-Geräteprüfung und einer
produktiven Modellfreigabe getrennt.
