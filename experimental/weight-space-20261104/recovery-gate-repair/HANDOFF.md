# Drei Helfer: Reparatur der Nachkapazitäts-Abhängigkeit

## Auftrag und Stand

Besitzerfortsetzung vom 04.11.2026: „Weiter mit 3 bots“.
Status: **REPAIR_ARTIFACT_VERIFIED / V2_RAW_EVIDENCE_REVIEW_COMPLETE**.
Der frühe WIP-Checkpoint wurde separat als `fd91457efbb2b3d05ad962b25302ccc61e44f0f3`
veröffentlicht; der folgende Stand ergänzt die tatsächlich abgeschlossenen Prüfungen.

Eigener Branch: `research/continuum-weight-space-audit-20261104`, PR #492.
Ausgangscommit: `f9a594641f4f2101e84d46baa8dd87e85b5364cd`.
Beim Einstieg frisch geprüfter fremder PR #508: zunächst
`7131c1a50a9c697e481a6debe259758dbf422eef`, anschließend
`be4e49600fed1db4c697230bb8e42c150dbfbdaf`.

Die vorhandene [unabhängige EOS-/Gate-Prüfung](../followup-eos-review/HANDOFF.md)
bleibt unverändert. Diese Runde soll die dort reproduzierte fehlende
Nachkapazitäts-Abhängigkeit konkret reparieren und die neuen veröffentlichten
Trainingsbefunde unabhängig einordnen. Betroffen: EXP-05, EXP-07 und EXP-12.

## Tatsächliche Arbeitsteilung

| Helfer | Aufgabe | Eigener Schreibbereich |
|---|---|---|
| `post_capacity_repair` | Minimaler Patch für die tatsächlichen v1-, Long- und Suffix-Runner, soweit betroffen; Originalbytes, reparierte Fixtures und Patchbindung erhalten | Dieser Ordner außer den nachfolgenden Teilbereichen und dieser Übergabe |
| `capacity_gate_validation` | Unabhängiger RED→GREEN-Nachweis an echten Study-Methoden mit vollständigen synthetischen Benchmarkbelegen | `independent-tests/` |
| `fresh_training_results` | Veröffentlichte Diagnose-/128-/512-Schritt-Ergebnisse und ihre Belege prüfen; Ausführung, Summary und neue Registrierung unterscheiden | `result-review/` |

Root prüft die Ergebnisse zusätzlich, hält die Dateibereiche auseinander und
veröffentlicht konkrete reproduzierbare Zwischenstände im bestehenden PR #492.

## Korrekturvertrag

Der Nachtrainings-Kapazitätsbeleg ist eine Pflichtabhängigkeit von Auswahl-Freeze
und Test. Er muss zu ausgewähltem Checkpoint, Tokenizer, Registrierung, Quell- und
Programmidentität, dem begrenzten Benchmarkbefehl und seinen Rohlogs passen.
Fehlende, beschädigte, vertauschte oder nach Freeze veränderte Belege stoppen
vor dem Testprozess. Die unveränderten Ein-/32-Token-Arbeitslasten und Ressourcen-
grenzen bleiben prüfbar.

Ein **gültig belegtes FAIL** bleibt entsprechend dem fremden Intent-Protokoll
mit einer begrenzten Taskmetrik zulässig. Es muss zusammen mit dem Ergebnis
sichtbar erhalten bleiben und ist keine allgemeine Modellfreigabe. Fehlende
Evidenz ist kein solches FAIL. Die Korrektur darf diese Unterscheidung nicht
durch einen pauschalen PASS-Zwang ersetzen.

Die unabhängigen Tests führen die echten Methoden aus; ausschließlich die
Prozessgrenze wird abgefangen. Die erwarteten Resultate werden aus den
Protokollanforderungen und vollständigen synthetischen Rohmessungen abgeleitet,
nicht vom neuen Validator selbst berechnet.

## Umfang und Quellenwahrheit

Alle eigenen Änderungen bleiben unter
`experimental/weight-space-20261104/recovery-gate-repair/`. Der aktive fremde
PR #508 wird nicht überschrieben. Die Lieferung ist ein funktionierender,
quellengebundener Reparaturpatch mit ausführbar geprüften Fixtures, keine
Behauptung einer bereits erfolgten In-Place-Übernahme in dessen Forschungszweig.

Für eine spätere Übernahme müssen die tatsächlich geänderten Runner und ihre
Registrierung vor weiterer Ausführung neu gebunden werden. Historische
Registrierungen und Quittungen werden nicht umgeschrieben und nicht mit einer
nachträglich behaupteten neuen ursprünglichen Quellidentität versehen.

Keine neuen Modell-/Trainingsprozesse, keine erneute Holdoutauswertung, keine
Änderung der vorhandenen Checkpoints, produktiven Runtime, G1-Gates oder
eingefrorenen Auswahl. Keine Promotion, kein Main-Merge und kein APK-Release.
Die neuen Ergebnisreviews lesen nur bereits veröffentlichte Messbelege.

## Abnahme und Rückfall

Vor Veröffentlichung als geprüfter Patch: ursprüngliche Quellen reproduzierbar
RED; korrigierte Quellen auf denselben relevanten Gegenproben GREEN; zulässige
PASS-/FAIL-Fälle erhalten; Dateiveränderungen nach Freeze abgewiesen; exakter
Patch auf die gebundenen Originale anwendbar. Root wiederholt die entscheidende
Prüfung unabhängig. Fehlende Modell-/Geräteabnahme wird nicht ersetzt.

Rückfall: kein Kandidat und kein fremder Runner wird übernommen, wenn eine
erforderliche Prüfung fehlschlägt. Der bisherige Forschungsstand bleibt bestehen.
Der nächste Einstiegspunkt ist dieser versionierte Ordner und der jeweils
tatsächlich geprüfte fremde Quellcommit, nicht ein veralteter PR-Beschreibungstext.

## Tatsächlich abgeschlossener Reparaturnachweis

Der [Reparaturpatch](pr508-post-capacity-gate.patch) ist implementiert und exakt
an die drei Originalquellen gebunden. Der unabhängige Testbot und anschließend
Root haben die echten Kontrollmethoden jeweils selbst ausgeführt:

| Einstieg | Originale: erfüllte Erwartungen | Reparierte Quellen: erfüllte Erwartungen |
|---|---:|---:|
| Intent v1 | 10/52 | 52/52 |
| Intent long | 10/52 | 52/52 |
| Intent suffix | 10/52 | 52/52 |
| Unveränderte v2/v3-Studienwahl mit diesen Controllern | 3/9 | 9/9 |

Die negativen Originalresultate sind die beabsichtigte Fehlerreproduktion.
Die Reparatur erfüllt insgesamt **165/165** geprüfte Erwartungen. Darunter:
gültige PASS-/EOS-FAIL-/Latenz-FAIL-/RSS-FAIL-Fälle, fehlende oder beschädigte
Nachweise, unplausible Zeitbudgets, beide trainierten Arme, korrekte Wiederaufnahme,
Dev-Gleichstandsregel, sichtbare FAIL-Ergebnisse und Mutation nach Freeze bzw.
zwischen Testarmen. Die älteren 50-Fall-Berichte sind als überholt gekennzeichnet.

Root hat zusätzlich die Git-Blob- und SHA-256-Bindungen verglichen, den Patch auf
frische temporäre Kopien exakt angewendet und die entstandenen Bytes mit den
getesteten Fixtures verglichen. Der AST-Vergleich bestätigt: bestehende Trainings-,
Optimierer-, Auswahl- und Prozessausführungsmethoden sind unverändert. Neu ist
die Nachbelegvalidierung; angepasst sind nur `freeze_evidence()` und `test()`.

Die [Root-Prüfung der Runner](root-verification/verification.json) und die
[Root-Prüfung der Studienwahl](root-verification/selector-verification.json)
enthalten Quellenhashes, Befehle und Ergebnisse. Ausschließlich die Prozessgrenze
ist in den Tests abgefangen; Modellleistung und Android-Eignung sind damit nicht
geprüft. Tatsächlich gestartete Modellprozesse und reale Holdoutzugriffe: **0**.

## Neuer veröffentlichter Ergebnisstand während der Prüfung

Der [unabhängige be4e-Ergebnisreview](result-review/README.md) wurde mit 64
Textquellen, zwölf Receipt-/Log-Paaren und 192 veröffentlichten Dev-Einzelfällen
abgeschlossen. Root hat seine vollständige JSON-Ausgabe bytegleich reproduziert.
Seine Aussage über damals fehlende V2-Rohbelege gilt genau für diesen Snapshot.

Während des Abschlusses veröffentlichte #508 den direkten Folgecommit
`e3c5aeadd6336f843bd625d56431208d3a03b5ad` mit den fehlenden V2-Rohbelegen.
Der [erneute Quellvergleich](latest-source-check.json) bestätigt alle drei
Runner bytegleich zum geprüften Reparaturstand. Der dritte Helfer hat diese
neuen V2-Belege in einem getrennten, ausdrücklich auf `e3c5…` gebundenen
[Nachtrag](result-review/V2_ADDENDUM.md) geprüft. Historischer Bericht,
Quittungen und Registrierungen bleiben unverändert.

Der Nachtrag rechnet 38 Textquellen, vier Receipt-/Log-Paare, alle 1.024 Updates
über beide Arme, 192 veröffentlichte Dev-Einzelfälle und 32 Online-Epochenmittel
nach. Die vorherige Summary-only-Lücke ist am neueren Commit aufgelöst. Root
hat auch diese vollständige JSON-Ausgabe bytegleich reproduziert und zusätzlich
die Zeit- und Speichermaxima aller vier V2-Kapazitätslogs aus den Rohzeilen
neu berechnet: [V2-Root-Nachweis](root-verification/v2-verification.json).

| V2-Befund | Small | Wide |
|---|---:|---:|
| Parameter | 132.160 | 6.562.304 |
| Ausgewählter Trainingsschritt | 256 | 128 |
| Frei generierte Dev-Antworten richtig | 13/24 | 13/24 |
| Ersttoken-Beobachtungen nach Training | 0/3 | 3/3 |
| Volle 32-Token-Ausgaben nach Training | 0/3 | 0/3 |

Wide erzeugt am Kapazitätsprompt jetzt ein Token und danach EOS; das ist
weiterhin ein Kapazitäts-FAIL. Im gepaarten Dev-Vergleich sind elf Fälle bei
beiden richtig, zwei nur bei Small, zwei nur bei Wide und neun bei beiden
falsch. Ein Nettovorteil der größeren Parameterzahl ist dort nicht belegt.
Die ausgewählten Checkpoints enthalten außerdem unterschiedliche Trainingspräfixe.

Der vollständige Wide-V2-Prozess benötigt 653,790 Sekunden. Das liegt innerhalb
seines neu registrierten 900-Sekunden-Rahmens, aber über den früheren 600 Sekunden.
Kapazitätsschwellen und Qualitätsschwellen bleiben unverändert. Gleiche
Schrittnummern in V1 und V2 bedeuten wegen des geänderten Cosine-Nenners
`STEPS - 4` keinen identischen bisherigen Lernratenverlauf.

## Abschlussentscheidung und verbleibende Grenze

Die geprüfte Reparatur wurde auf dem eigenen PR #492 als
`86f51e38622bf46a72d95a5f76683e7d9637cb81` veröffentlicht. Die aktuellen
Abschlussdateien ergänzen diesen Stand um den verifizierten V2-Nachtrag.
Der Patch selbst bleibt exakt auf die geprüften Originalbytes gebunden.

Eine tatsächliche Änderung der fremden Runnerpfade und ihre neue prospektive
Registrierung stehen weiterhin aus. Die aktuell erlaubten Dateien schließen
diese fremden Pfade nicht ein. Der bereitgestellte Patch ist konkret anwendbar;
eine bereits erfolgte Übernahme in PR #508 wird nicht behauptet.

Kein fehlgeschlagener Trainingskandidat wird übernommen. Die bestehenden
G1-Schutzmechanismen, Checkpoints und frühere Forschungsstände bleiben erhalten.
Die Ergebnisreviews prüfen veröffentlichte Text- und Hashketten; die tatsächlichen
Modell-, JAR- und Tokenizerbytes wurden in dieser Runde nicht erneut geladen oder
binär auf Integrität geprüft. Am abschließend geprüften Commit `e3c5…` fehlen
weiterhin veröffentlichte V3-Studienresultate, Studienwahl-/Freeze-Quittungen und
Finaltestbelege. Der Abschluss behauptet weder deren Ausführung noch eine
allgemeine Qualitätsverbesserung, Android-Abnahme oder Modell-Promotion.
