# LM1: eigenes Training mit 6.562.304 Parametern — Abschlussbericht

Stand: 2026-11-04. Forschungsbranch: `research/continuum-lm1-training-recovery-20261104`, [Draft PR #508](https://github.com/Ullmann27/device-ai/pull/508), führende Gewichte-Arbeit [#492](https://github.com/Ullmann27/device-ai/pull/492).

## Ergebnis

**Die Erweiterung auf 6.562.304 Parameter und tatsächliches lokales Training sind ausgeführt. Ein begrenzter Lernfortschritt ist belegt. Das vorab festgelegte Erfolgsziel wurde verfehlt.** Das ausgewählte kleine Modell beantwortet 31 von 48 neuen Intent-Fällen exakt richtig, das ausgewählte große 25. Beide untrainierten Kontrollen erreichen null exakte Antworten. Die größere Variante hat auf diesem Versuch keinen nachgewiesenen Vorteil. Beide trainierten Kandidaten verfehlen außerdem die unveränderte allgemeine Kapazitätsprüfung.

Das konkrete verbleibende Problem ist die Übertragung gelernter Aufgaben auf neue Formulierungen: Beide ausgewählten Modelle erreichen auf den bekannten 128 Trainingsbeispielen 120 richtige Antworten, fallen im getrennten Finaltest aber deutlich zurück. Besonders schwach bleibt das Erkennen von Notizaufträgen. Diese Forschung liefert keine Freigabe für einen allgemeinen Sprachkern oder einen autonomen Android-Mitarbeiter. [Finalaudit](final-independent-review.md), [Finaltest-Zusammenfassung](study-v3/intent-summary.json), [TRAIN-Fit](study-v3/training-fit-summary.json).

## 1. Was tatsächlich ausgeführt wurde

Vier Diagnosearme und drei Intent-Runden mit jeweils zwei Modellgrößen ergeben **zehn echte Trainingsläufe, 2.368 Optimierungsupdates und 31.452 überwachte Zieltoken-Verwendungen**. Wiederholte Zielpositionen zählen mehrfach; dies sind weder 31.452 verschiedene Beispiele noch Wörter. Hinzu kommen numerische Prüfungen an kleinen synthetischen Modellen, die nicht als diese zehn Eigentümermodell-Läufe gezählt werden.

| Teilversuch | Läufe | Updates insgesamt | Überwachte Zielpositionen insgesamt |
|---|---:|---:|---:|
| Vier Diagnosen, jeweils 16 Updates | 4 | 64 | 13.020 |
| Intent V1, jeweils 128 Updates | 2 | 256 | 2.048 |
| Intent V2, jeweils 512 Updates | 2 | 1.024 | 8.192 |
| Intent V3, jeweils 512 Updates | 2 | 1.024 | 8.192 |
| **Summe** | **10** | **2.368** | **31.452** |

Die Starts stammen aus Kopien des vorhandenen eigenen LM1-Checkpoints; es wurden keine fremden vortrainierten Gewichte übernommen. Dies ist eine Erweiterung und weiteres Training des eigenen Bestandsmodells, kein behaupteter vollständiger Neuaufbau aus Zufallsgewichten. Die breite Variante entsteht durch die vorhandene achtfache Verbreiterung. Identitäts-, Paritäts- und initiale Kapazitätsprüfungen gehen den Updates voraus. Die unveränderten Kotlin-Kerne laufen lokal auf CPU; es wurden keine bezahlten Inferenz-APIs, GPU-Dienste oder GitHub-Actions-Trainings eingesetzt. Quellcode und Versuchsdateien sind isoliert; die Anwendung und aktive Modellauswahl bleiben unverändert. [Registrierung V1](../../../experimental/lm1-training-recovery-20261104/registration.json), [V2](../../../experimental/lm1-training-recovery-20261104/long-study/registration.json), [V3](../../../experimental/lm1-training-recovery-20261104/suffix-study/registration.json).

Drei Helfer bearbeiteten getrennt Trainingsdiagnose, Architektur-/Quellenanalyse und Validierung. Der Koordinator kompilierte, führte die begrenzten Prozesse aus, veröffentlichte die Auswahl vor dem Finaltest und integrierte die Belege. Der Finalaudit rechnete alle 192 Arm-Fall-Urteile nach; die 622 dokumentierten Konsistenzprüfungen ergaben keine Abweichung. Das sind Belegprüfungen, keine 622 unabhängigen Modelltests. [Auditdaten](final-case-audit.json).

Vor den Eigentümermodell-Läufen bestanden je Studienversion fünf numerische Prüfungen und sieben Controller-Regressionstests. Geprüft wurden unter anderem Maskenausrichtung einschließlich EOS, der tatsächliche maskierte Update-Verlust, ein Gradient gegen finite Differenzen, die strenge Antwortgrenze und das Verhindern stiller Kontextkürzung. Die Controller-Tests verwerfen fremde Kontrollen, geänderte registrierte Bytes, manipulierte Auswahlidentitäten, verlorenes DEV-Signal und fehlende beziehungsweise ungültige Freezes; der gültige Fall erlaubt genau vier simulierte Finalprozesse. Die Wiederholung derselben Prüfungen über drei Versionen ist keine Verdreifachung unabhängiger Testarten. [Numerische V3-Prüfung](preflight-suffix/mask-checks.log), [Controller-V3-Prüfung](preflight-suffix/controller-checks.log).

## 2. Die getestete Aufgabe

Das Modell soll die Absicht eines deutschen Auftrags als genau eine Ziffer ausgeben und anschließend mit EOS, dem Antwortende, abschließen:

| Ziffer | Erwartete Absicht |
|---|---|
| 0 | Keine Handlung beziehungsweise einen noch nicht ausgeführten Auftrag abbrechen |
| 1 | Eine ausdrücklich genannte Datei öffnen |
| 2 | Informationen zu einem ausdrücklich genannten Thema suchen |
| 3 | Eine ausdrücklich genannte Notiz merken |

Hier werden ausschließlich Modellantworten bewertet. Es werden keine Dateien geöffnet, Suchen ausgeführt oder Notizen gespeichert.

Die Daten wurden vor den Intent-Läufen in 128 TRAIN-, 24 DEV- und 48 TEST-Fälle aufgeteilt. Vollständige Satzvorlagen, Objektwerte und normalisierte Prompts sind zwischen den Teilen getrennt. TRAIN enthält 32 Fälle je Klasse, DEV sechs und TEST zwölf. Der BPE-Tokenizer mit 512 Einträgen bleibt unverändert. Nur Aktionsziffer und abschließendes EOS sind überwachte Zielpositionen; der Auftrag selbst ist bei der Verlustberechnung maskiert. Das gesamte vorhandene Modell wird optimiert, ohne nachgeschalteten Klassifikationskopf.

Die Hauptwertung verlangt eine frei erzeugte richtige Ziffer und EOS. Der Decoder darf das gesamte Vokabular verwenden; es gibt keine erzwungene Vier-Label-Auswahl, keine EOS-Unterdrückung und keine nachträgliche Antwortbereinigung. Eine richtige Ziffer mit zusätzlichen Zeichen oder fehlendem EOS gilt als falsch. [Versuchsvertrag](../../../experimental/lm1-training-recovery-20261104/INTENT_PROTOCOL.md), [Datensatzmanifest](../../../experimental/lm1-training-recovery-20261104/intent-data/manifest.json).

Die Testfälle wurden vom Team vorab erstellt und mit dem Generator veröffentlicht. Ihre Trennung ist eine vorab festgelegte Auswertungstrennung, keine kryptografische Geheimhaltung. Hashprüfungen und Veröffentlichung lesen beziehungsweise übertragen Testdateibytes. Vor der eingefrorenen Auswahl wurden diese Fälle nicht zur Modellauswertung, Checkpoint-Auswahl oder analytischen Einzelfallbewertung verwendet. Die 48 Fälle bestehen aus 16 Vorlagenfamilien mit je drei Objektgruppen; sie sind nicht 48 unabhängige statistische Ziehungen.

## 3. Diagnose und Entwicklung vor dem Finaltest

### Vier kurze Diagnosen

Alle vier Arme verwendeten dieselbe ursprüngliche TRAIN-Reihenfolge mit 3.255 Zielpositionen pro Arm. Geprüft wurden das kleine Modell mit Standardupdates, das breite Modell mit Standardupdates, das breite Modell mit um Faktor acht verkleinertem finalen Normierungsupdate sowie das breite Modell mit um Faktor acht reduzierter globaler Lernrate.

| Arm | Feste erste TRAIN-Minibatch-NLL, Start → Ende | Feste DEV-Bits pro Byte, Start → Ende | Kapazität nach Training |
|---|---|---|---|
| Klein, Standard | 2,628635 → 2,430478 | 2,352541 → 2,352507 | FAIL, sofort EOS |
| Breit, Standard | 2,628635 → 1,917311 | 2,352541 → 2,436533 | FAIL, sofort EOS |
| Breit, Normierungsdelta / 8 | 2,628635 → 1,917971 | 2,352541 → 2,436379 | FAIL, sofort EOS |
| Breit, Lernrate / 8 | 2,628635 → 2,461860 | 2,352541 → 2,351868 | FAIL, sofort EOS |

Kleinere Verlustwerte sind hier besser. Der Normierungseingriff war numerisch wirksam: das Verhältnis von angewendetem zu vorgeschlagenem Delta lag etwa bei 0,125. Er löste das beobachtete Problem trotzdem nicht. Die stark verringerte globale Lernrate verbesserte DEV geringfügig, stellte aber den erforderlichen Generierungsumfang ebenfalls nicht her. Kein Diagnosearm bestand die kombinierte vorab festgelegte Abnahme. [Diagnoseaudit](diagnostic-independent-review.md), [Rohbelege V1](study-v1/).

Das EOS-Verhalten hängt vom Kontext ab. In allen 48 untersuchten Halbpräfix-Beobachtungen war EOS nicht das wahrscheinlichste Token. Am festen Kapazitätsprompt konnte EOS dagegen durch eine veränderte Rangfolge gewinnen, obwohl seine absolute Wahrscheinlichkeit leicht sank. Eine allgemeine EOS-Dominanz in sämtlichen Kontexten ist damit nicht bewiesen.

### Drei Intent-Runden

Alle Intent-Arme starten frisch vom gebundenen Original beziehungsweise seiner neu erzeugten Verbreiterung. Die späteren Runden setzen keinen gescheiterten trainierten Checkpoint fort. Batchgröße ist vier, Spitzenlernrate 0,0001, Warmup vier Updates, anschließend ein Cosinus-Verlauf mit relativer Untergrenze 0,1, AdamW-Gewichtsabnahme 0,01 und Gradient-Clipping 1. Die Prozesse sind zeitlich und im Heap begrenzt.

| Runde | Änderung | Ausgewähltes kleines DEV-Ergebnis | Ausgewähltes breites DEV-Ergebnis | Finaltest |
|---|---|---:|---:|---|
| V1 | 128 Updates, Eingabe endet mit `\nAktion:` | 8/24, Schritt 128 | 10/24, Schritt 128 | Kein ausreichendes DEV-Signal; nicht ausgeführt |
| V2 | 512 Updates, langer Suffix | 13/24, Schritt 256 | 13/24, Schritt 128 | Vorab für V3-Vergleich zurückgestellt |
| V3 | 512 Updates, Eingabe endet nur mit `\n` | 13/24, Schritt 512 | 16/24, Schritt 128 | Nach vorab festgelegter Wahl einmal ausgeführt |

V2 verändert gegenüber V1 auch den zeitlichen Cosinus-Lernratenverlauf, weil dessen Gesamtlänge größer wird; es ist nicht ausschließlich dieselbe 128-Schritt-Folge mit angehängten Updates. V3 verändert gegenüber V2 funktional nur den Eingabesuffix. Modell, Daten, Beispielreihenfolge, Optimierer, Budgets, Snapshots und Auswahlregeln bleiben gleich. Vier Eingabetokens entfallen je Beispiel. [V1-Audit](intent-v1-independent-review.md), [V2-Audit](intent-long-independent-review.md), [V3-Protokoll](../../../experimental/lm1-training-recovery-20261104/suffix-study/SUFFIX_PROTOCOL.md).

Der kurze Suffix wurde durch eine reproduzierbare Untersuchung des ursprünglichen Modells motiviert: Der Median der direkten Zustandsdifferenz-Retention in Schicht 0 lag über dem langen Suffix bei etwa 0,00343 und über dem einzelnen Zeilenumbruch bei etwa 0,1900. Das ist keine Messung des gesamten Netzwerkgradienten. Der geänderte Wrapper verkürzt zugleich den Rechenpfad, entfernt das Wort „Aktion“ und verändert das letzte Eingabetoken. Schon ohne Training verändert er die Antwortverteilung. Ein besseres DEV-Ergebnis beweist daher keine alleinige Ursache in den internen Gates. [Suffixmessung](suffix-path-result.json), [Einordnung](suffix-path-review.md).

### Auswahl vor Betrachtung der Finalergebnisse

Innerhalb eines Arms entscheidet zuerst die Anzahl exakter DEV-Antworten, danach geringere Antwort-NLL und schließlich weniger Updates. Der vor V3 veröffentlichte Vergleich zwischen V2 und V3 verwendet dieselben ersten beiden Kriterien für das breite Modell und behält bei vollständigem Gleichstand V2. Gewählt wird immer der vollständige Vierer-Satz einer Studie.

V3 gewann auf DEV mit 16 statt 13 richtigen Antworten des breiten Arms. Innerhalb V3 hatte der breite Schritt 512 ebenfalls 16/24, aber die schlechtere Antwort-NLL; deshalb wurde Schritt 128 gewählt. Das kleine Modell wurde bei Schritt 512 ausgewählt. Alle vier Finalarme verwenden denselben kurzen Eingabesuffix. [Studienwahl](final-selection/development-study-choice.json), [Freeze](final-selection/selection-freeze.json).

Auswahl, Freeze und sämtliche Entwicklungsbelege wurden im Commit `9ceffc26a2159c8dc726c1f13d2882119159c130` veröffentlicht. Der Koordinator beobachtete die erfolgreiche Branch-Aktualisierung und einen bytegleichen Remote-Abruf vor dem ersten Finalprozess. Die GitHub-Commitmetadaten nennen 19:31:51 UTC, die erste Prozessquittung 19:31:59,698108 UTC. Der Commit ist unsigniert; diese Metadaten und die beobachtete Reihenfolge sind keine unabhängige kryptografische Zeitbeglaubigung. [Publikationsbeleg](final-selection/publication-metadata.json).

## 4. Der einmalige Finaltest

| Modell | Parameter | Ausgewählter Schritt | Exakt richtig | Anteil | Gültiges Ziffer-plus-EOS-Format |
|---|---:|---:|---:|---:|---:|
| S0: eigenes kleines Startmodell | 132.160 | 0 | 0/48 | 0 % | 0/48 |
| W0: frisch verbreiterte Kontrolle | 6.562.304 | 0 | 0/48 | 0 % | 0/48 |
| S1: kleines trainiertes Modell | 132.160 | 512 | **31/48** | **64,58 %** | **48/48** |
| W1: breites trainiertes Modell | 6.562.304 | 128 | **25/48** | **52,08 %** | **48/48** |

Beide trainierten Modelle lernen das Ausgabeformat vollständig auf diesen Fällen. Ihre verbleibenden Fehler betreffen die gewählte Absicht. Die anfänglichen Kontrollen erzeugen jeweils vier Inhaltstokens und enden am Tokenlimit, statt eine gültige Antwort mit EOS zu liefern. Null exakte Antworten bedeuten hier nicht, dass die Startmodelle keinerlei zuvor gelernte Information enthalten.

| Absicht, jeweils zwölf Fälle | S1 richtig | W1 richtig |
|---|---:|---:|
| Keine Handlung / Abbruch | 7/12 | 6/12 |
| Datei öffnen | 12/12 | 9/12 |
| Informationen suchen | 10/12 | 7/12 |
| Notiz merken | 2/12 | 3/12 |

Im gepaarten Vergleich sind 20 Fälle bei beiden richtig, elf nur beim kleinen, fünf nur beim breiten und zwölf bei beiden falsch. Das kleine ausgewählte Modell liegt somit sechs Fälle beziehungsweise 12,50 Prozentpunkte vorn. Die auf DEV beobachtete Reihenfolge kehrt sich um. Weil die gewählten Stände 512 beziehungsweise 128 Updates haben und nur eine Aufgabenfamilie mit einem Seed untersucht wurde, ist dies kein isolierter kausaler Nachweis gegen größere Modelle.

Die primäre vorab festgelegte Abnahme verlangte für W1 mindestens 36/48 richtige Antworten, mindestens 8/12 je Klasse und mindestens acht zusätzliche richtige Antworten gegenüber jeder Startkontrolle. Nur der Abstand zu den Startkontrollen wird erfüllt. Auch die separate Größenvorteilsregel von mindestens vier zusätzlichen richtigen Fällen gegenüber S1 wird verfehlt. S1 würde unter denselben Zahlenanforderungen ebenfalls scheitern. Die Abnahme wird nicht nachträglich zugunsten eines anderen Arms geändert. [Finalaudit mit vollständigen Verwechslungsmatrizen](final-independent-review.md), [192 Einzelurteile](final-case-audit.json), [Rohlogs](study-v3/logs/).

## 5. Bekannte Beispiele versus neue Formulierungen

Nach Abschluss von Auswahl und Finaltest wurden ausschließlich die bereits eingefrorenen S1-/W1-Modelle zusätzlich auf allen 128 bekannten TRAIN-Beispielen bewertet. Es gab dabei keine Updates und keine neue Auswahl.

| Modell | Bekannte TRAIN-Fälle | Neue TEST-Fälle | Notizklasse TRAIN → TEST |
|---|---:|---:|---:|
| S1 | 120/128 = 93,75 % | 31/48 = 64,58 % | 32/32 → 2/12 |
| W1 | 120/128 = 93,75 % | 25/48 = 52,08 % | 32/32 → 3/12 |

Der Befund zeigt eine ausgeprägte Übertragungslücke. Er belegt nicht allein, ob mehr Daten, ein anderes Lernziel, ein veränderter Optimierer oder eine Architekturänderung sie am besten schließt. Besonders die Notizklasse liefert einen konkreten Ansatzpunkt für eine folgende Entwicklungsrunde.

Die Dateien `train-fit-*` sind ausdrücklich TRAIN-Auswertungen. Der gemeinsam verwendete JVM-Scorer bezeichnet Datensätze intern weiterhin als `split=test` und protokolliert `TEST_INPUT`; entscheidend sind der tatsächliche Eingabepfad, der TRAIN-SHA256 und die separate Zusammenfassung. Diese 256 Arm-Fall-Wertungen gehören nicht zu den 192 Urteilen des Finaltests und sind keine unabhängige Bestätigung. [TRAIN-Fit-Zusammenfassung](study-v3/training-fit-summary.json).

## 6. Allgemeine Kapazität bleibt ein Ausschlussgrund

Die initialen Kapazitätsgates bestanden beide frischen Startkandidaten. Nach dem Training wurden genau die ausgewählten Checkpoints mit der unveränderten ursprünglichen Anforderung erneut geprüft.

| Checkpoint | Beobachtete erste Tokens | Vollständige 32-Token-Messläufe | Verhalten | Ergebnis |
|---|---:|---:|---|---|
| S1, Schritt 512 | 0/3 | 0/3 | Sofortiges Antwortende, kein Inhaltstoken | FAIL |
| W1, Schritt 128 | 3/3 | 0/3 | Ein Inhaltstoken, dann Antwortende bei 32-Token-Anforderung | FAIL |

W1 hat damit ein anderes Fehlerbild als S1; es ist falsch, beide als sofortiges EOS zu beschreiben. Eine korrekte einstellige Antwort im Intent-Test ersetzt die verlangte längere Generierung nicht. Dieser Versuch weist keine allgemeine Sprachkerneignung nach. Android-Ausführung wurde nicht getestet. [S1-Kapazitätslog](study-v3/logs/intent-small-capacity-after.tsv), [W1-Kapazitätslog](study-v3/logs/intent-wide-capacity-after.tsv).

## 7. Forschungsgrundlage und Reichweite

- [Net2Net, Chen et al.](https://arxiv.org/abs/1511.05641) motiviert funktionsbewahrende Modellvergrößerung. Die konkrete LM1-Verbreiterung benötigt eigene Identitäts- und Paritätsbelege; das Paper beweist nicht pauschal jede Übertragung auf diese Architektur.
- [Tensor Programs V / μP, Yang et al.](https://arxiv.org/abs/2203.03466) motiviert die Untersuchung breitenabhängiger Optimierung. Die beiden Skalierungsversuche hier sind begrenzte Hypothesenprüfungen, keine vollständige μP-Implementierung.
- [TinyStories, Eldan und Li](https://arxiv.org/abs/2305.07759) zeigt in seinem eigenen Versuchsaufbau die Untersuchung enger Sprachfähigkeiten mit kleinen Modellen. Das motiviert überprüfbare Teilaufgaben. Weder dessen Gewichte noch dessen synthetischer Datensatz oder Erzeugungs-/Bewertungsdienste wurden hier übernommen.

Das Ergebnis sagt etwas über diesen eigenen Modellstand, die registrierten Daten, das Lernziel und den begrenzten Rechenumfang aus. Es beweist weder Superintelligenz noch deren Unmöglichkeit. Mehr Parameter allein haben in diesem kontrollierten Vergleich keinen Gewinn gebracht. Der Nachweis gelernter Fähigkeiten verlangt weiterhin getrennte Bewertung, verlässliche Ausführung und die Einhaltung der jeweiligen Qualitätsgrenzen.

## 8. Quellenidentitäten, Gewichtspaket und Wiederholung

| Gegenstand | Vollständige Identität |
|---|---|
| Unveränderter Kern aus #492 | `30cd3a89810ed14fa84eca95236d3177e016118d` |
| Diagnose und Intent V1 | `def5acf9cbba82ee51a53cc06fecc52e9cd0324d` |
| Intent V2 | `fc2eb797210bee25091caf734aacbfd694a3372e` |
| Intent V3 und vorab festgelegte Studienwahl | `be4e49600fed1db4c697230bb8e42c150dbfbdaf` |
| Vor Finaltest veröffentlichte Auswahl | `9ceffc26a2159c8dc726c1f13d2882119159c130` |
| Finaler Freeze SHA256 | `6c069dd1ab21d792b9985333b14b2d6a0c51b66affbc8a90de0f37092c5a49f4` |
| V3-JAR SHA256 | `8075d799b42f3b4f23f2f154af09685f52074503b0a4103ba336a618a3be1950` |

| Eingefrorenes Modell | SHA256 |
|---|---|
| S0 | `70cb6ed4020042c4ced21bf0c58dab1b83ee2a51655fdbec41279cd961234435` |
| W0 | `af617cf6d223a3e53b44bc3c0578b6924e9c73d8b92f558a981af22990cad04d` |
| S1 | `90b58fb58e048bd4eac967f2b0b131373170201660de7a3d330b393a6c8d820f` |
| W1 | `0f2b5481cf60d6c6021e9e24d1e5ba70d303da274c18933de29c48a47489cf8c` |

Das bereitgestellte Paket `LM1_Training_Research_20261104.zip` enthält diese vier Gewichtsdateien, die drei exakt registrierten Studien-JARs, die gebundene Kotlin-Standardbibliothek, README und SHA256-Manifest. Alle zehn Archivmitglieder wurden gegen ihre Hashes geprüft; die ZIP-Integritätsprüfung war erfolgreich. Quellcode, Datensätze, Tokenizer-Provenienz und vollständige Textbelege stehen im Repository. Die Originaleingaben wurden mit dem beigefügten Hilfsprogramm aus gebundenen Git-Objekten exakt rekonstruiert.

Paketgröße: **51.103.436 Bytes**. SHA256:

`428d62b308e308afd9c971ff4974981e96b9dc8a783d80e3653c226983dcc016`

[Archivbeleg](archive-manifest.json) und [vollständige Wiederholungs- und Rückfallanleitung](REPRODUCE.md). Die Quellenregistrierungen, Prozessquittungen, Rohlogs und unabhängigen Audits bleiben erhalten, einschließlich aller fehlgeschlagenen Gates. Eine Wiederholung des jetzt bekannten Tests bestätigt höchstens Reproduzierbarkeit; sie ist keine neue unabhängige Bestätigung eines später trainierten Modells.

## 9. Rückfall und Übergabe

**Diese Forschungsgewichte nicht als aktiven Modellstand übernehmen.** Originalcheckpoint und bestehende Auswahl bleiben erhalten. Original-SHA256:

`7a0af1e99554f9e73fbe4212d0aa4bf8ec97745efca56998d9b42bac4fad44fd`

Es wurde keine Anwendung integriert, kein Modell promoviert und kein Release erzeugt. Der frühere Helfer-Fix aus #497 ersetzt nicht die inzwischen verschärften integrierten G1-Prüfungen in #492. Die Forschungsrunde ist mit dem festgestellten negativen Abnahmeergebnis abgeschlossen; nach diesem Test wurden keine weiteren Modelle trainiert oder Checkpoints gewählt.

Die nächste begründete Entwicklungsfrage für #492 betrifft abwechslungsreichere, gezielt kontrastierte Notiz- und Abbruchaufträge sowie die Verträglichkeit des Aufgabenlernens mit längerer Generierung. Ein neuer Bestätigungssatz muss vor der nächsten bestätigenden Bewertung festgelegt werden. Erst dann lässt sich beurteilen, ob ein Eingriff die hier festgestellten Lücken wirklich schließt. Dieser Bericht behauptet für diese nächsten Schritte noch kein Ergebnis.
