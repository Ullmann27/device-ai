# Fortsetzung mit drei Helfern: tatsächliche Studienwahl vor dem Finaltest

Auftrag: `G1-INTEGRATE-WEIGHT-002`, Besitzerfortsetzung „Weiter“ am 04.11.2026.
Status des frühen Checkpoints: **WIP / NEW_CHOICE_BINDING_REVIEW_IN_PROGRESS**.

Eigener PR #492 startet diese Runde bei
`f2421698c15195505b0632bf40ff596f0ee0e87f`.
Frisch gelesener und als Gitobjekt verfügbarer PR-#508-Stand:
`9ceffc26a2159c8dc726c1f13d2882119159c130`.

## Was seit dem letzten Abschluss neu ist

Der neue Fremdcommit ergänzt vollständige V3-Entwicklungsbelege und die drei
Coordinatorartefakte `development-study-choice.json`, `selection-freeze.json`
und `before-test-verification.json`. Der gepinnte Baum enthält noch keine
veröffentlichten Finaltestlogs. Der Handoff-Text beschreibt V3 dagegen teilweise
noch als laufend; für diese Prüfung gelten die tatsächlichen Quellen und Dateien.

Die drei Runner und der Studienwahlhelfer sind bytegleich zum zuletzt geprüften
Stand. Der bereits unabhängig verifizierte Nachkapazitäts-Patch wurde dort nicht
integriert. Seine 165 Prüfungen und die bisherigen Ergebnisreviews bleiben
unverändert unter `../recovery-gate-repair/`; identische Quellen werden nicht
ohne neue Fragestellung nochmals getestet.

Die jetzt veröffentlichte Studienwahl erfordert einen zusätzlichen konkreten
Vergleich: Der getrennt aufrufbare Testpfad liest weder diese Wahl noch den
Pretest-Beleg. Die neue Gegenprobe untersucht, ob ein lokal gültiger Freeze
der nicht gewählten Studie trotzdem einen Teststart erreicht. Das ist zunächst
ein Prüfauftrag zur maschinellen Durchsetzung des Coordinatorvertrags und kein
Beleg, dass der tatsächliche veröffentlichte Lauf falsch gestartet wurde.

## Arbeitsteilung

| Helfer | Auftrag | Ausschließlicher Schreibbereich |
|---|---|---|
| `current_gate_source` | Frische Quellen prüfen; nach unabhängig reproduziertem RED die kleinste prospektive Ergänzung der Wahlbindung implementieren | `source-audit/` |
| `independent_current_gate` | Neue Teststart-/Wiederaufnahmegrenzen an echten Methoden prüfen; Original, vorhandenen Patch und neue Reparatur vergleichen | `independent-tests/` |
| `current_training_evidence` | Veröffentlichte V3-Rohbelege, Studienwahl und Freeze unabhängig nachrechnen | `results/` |

Root prüft die neuen Verträge und Resultate zusätzlich, hält die Dateihoheit
getrennt und veröffentlicht konkrete prüfbare Zwischenstände im eigenen PR.
Es werden genau drei Helfer eingesetzt; keine weiteren Unteragenten.

## Geplanter enger Reparaturvertrag

Erst nach tatsächlicher Fehlerreproduktion: Die v2/v3-Testpfade müssen eine
passende Studienwahl, den passenden Pretest-Beleg und den exakten Freeze
verlangen. Ein ausdrücklich vorab erwarteter Hash kann die unveränderlichen
Belegbytes binden. Fehlende Angaben dürfen keinen stillen Rückfall auf den
ungeschützten Testpfad erlauben. Die Kontrolle muss vor Modellprozessen und
bei Belegänderungen während des Vierarmtests greifen.

Die unveränderte Dev-Auswahlregel, vollständige Kapazitätsnachweise und die
zulässige sichtbare Capacity-FAIL-Semantik bleiben bestehen. V1 erhält keine
unbegründeten Pflichten aus dem späteren v2/v3-Vergleich. Der Patch wird als
neues, quellengebundenes Artefakt bereitgestellt; der frühere Reparaturstand
wird nicht überschrieben. Historische Quellen, Registrierungen und Quittungen
werden nicht nachträglich umetikettiert.

## Grenzen und Rückfall

Alle eigenen Änderungen dieser Runde bleiben unter
`experimental/weight-space-20261104/continuation-review/`. Kein Schreiben in
die aktiven fremden Runner, keine produktive Runtimeänderung, keine Promotion,
kein Main-Merge und kein APK-Release. Keine neuen Modell-/Trainingsprozesse und
keine realen Datensatz- oder versiegelten Testdateien werden für diese Prüfung
geöffnet. Veröffentlichte Messbelege werden rein lesend nachgerechnet.

Die Kontrolle einer Hashreferenz ist kein kryptografischer Nachweis einer
früheren Veröffentlichung und keine globale Ausführungssperre gegen beliebige
Dateikopien oder fremde Prozesse. Die konkrete geprüfte Grenze wird benannt.

Falls ein erforderlicher Nachweis fehlschlägt, wird keine neue Reparatur als
bestanden erklärt und kein Trainingskandidat übernommen. Vorherige Forschungs-
und Checkpointstände bleiben erhalten. Der abschließende Bericht muss den
tatsächlich geprüften Quellcommit und die noch offene fremde Integration nennen.
