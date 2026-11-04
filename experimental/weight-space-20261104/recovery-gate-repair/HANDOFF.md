# Drei Helfer: Reparatur der Nachkapazitäts-Abhängigkeit

## Auftrag und Stand

Besitzerfortsetzung vom 04.11.2026: „Weiter mit 3 bots“.
Status dieses frühen Checkpoints: **WIP / PATCH_IN_PROGRESS**.

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
