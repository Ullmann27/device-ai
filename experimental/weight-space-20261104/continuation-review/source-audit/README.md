# Aktuelle Quellen und zusätzliche Bindung der Studienwahl

Auftrag: Fortsetzung von G1-INTEGRATE-WEIGHT-002 im eigenen PR #492.
Geprüfter PR-#508-Stand:
[`9ceffc26a2159c8dc726c1f13d2882119159c130`](https://github.com/Ullmann27/device-ai/commit/9ceffc26a2159c8dc726c1f13d2882119159c130).
Die Prüfung liest festgelegte Textquellen; sie startet keinen Modellprozess.

## 1. Der vorhandene Kapazitätspatch wurde noch nicht übernommen

Alle drei aktuellen Runner sind bytegleich zu den Originalen aus `be4e4960…`.
Damit gelten die zuvor unabhängig reproduzierten Kontrollflussbefunde weiterhin;
ein bloß neuer Evidenzcommit erfordert keine erneute Vollwiederholung derselben
165 Fälle. Die drei registrierten Runner enthalten weder die neue Methode
`post_capacity_evidence()` noch eine alternative Umsetzung dieser Prüfung.

| Runner | Aktuelle Original-SHA-256 |
|---|---|
| v1 | `409f87f5a174804d7290c29972d6b4e2bbb19ad293ac1da027122c3f28d14d4f` |
| v2 / long | `90877b90a7f138a9c469fb0350bd6c28ff56602b7afb61fe7ee05b1e11ab230b` |
| v3 / suffix | `276b2fc27f566fdb3947c9f7fa13e7c9b10cc549354f90b478f16883e9bd4c68` |

Der zuvor geprüfte
[Nachkapazitätspatch](../../recovery-gate-repair/pr508-post-capacity-gate.patch)
bleibt unverändert, SHA-256
`c69cb427e4ba0624720380edef9f7f0ce3e418591c10bd0a35f56590ad67e38f`.
Auch Selector und Registrierungen sind unverändert. Zwischen `be4e…` und
`9ceffc26…` kamen ausschließlich Dokumentations-/Evidenzdateien hinzu bzw.
wurde die Forschungsübergabe ergänzt; keine ausführbaren Quellen änderten sich.

## 2. Die neue veröffentlichte Wahl ist in sich konsistent

Neu vorliegend sind vollständige V3-Entwicklungsbelege sowie die drei Dateien
unter `docs/evidence/lm1-training-recovery-20261104/final-selection/`:

| Artefakt | Prüfergebnis |
|---|---|
| `development-study-choice.json` | Wählt V3 und enthält dessen komplettes Vierarm-Payload |
| `selection-freeze.json` | Ist semantisch exakt gleich `selectedFreezePayload` der Wahl |
| `before-test-verification.json` | Seine Choice- und Freeze-SHA-256 stimmen mit den tatsächlich veröffentlichten Bytes überein |

Die gewählte Registrierung ist weiterhin die ursprüngliche V3-Registrierung
`04392b366746b1e6fca78eb66758e8c99e94b8392dbe013a02d2b416e2184728`,
der angegebene Ausführungscommit weiterhin `be4e4960…`.
Der eingefrorene Stand enthält **kein** oberes `postTrainingCapacity`-Objekt.
S1 und W1 tragen lediglich den alten Auswahlmarker
`REQUIRES_SEPARATE_BENCHMARK`.

Im geprüften Git-Baum sind keine abschließenden `logs/test-*`-Dateien
veröffentlicht. Das ist eine Aussage über diesen veröffentlichten Snapshot,
kein Beweis über andere oder unveröffentlichte Prozesse. Der Handoff enthält
noch die ältere Beschreibung „V3 running“; für den aktuellen Auswahlstand
sind deshalb die tatsächlichen gebundenen Artefakte maßgeblich.

## 3. Weitere Grenze: Der einzelne Testpfad konsumiert die Studienwahl nicht

Das [Suffixprotokoll](https://github.com/Ullmann27/device-ai/blob/9ceffc26a2159c8dc726c1f13d2882119159c130/experimental/lm1-training-recovery-20261104/suffix-study/SUFFIX_PROTOCOL.md)
fordert die Wahl einer gesamten Studie und den Vergleich ihres Freeze-Payloads
durch den Coordinator vor dem einmaligen Test. Die veröffentlichte Wahl und der
veröffentlichte Freeze erfüllen den dokumentierten Vergleich.

Die separat aufrufbaren `Study.test()`-Methoden lesen jedoch weder die
Studienwahl noch `before-test-verification.json`. Ihre Freigabe wird nur aus
dem jeweiligen studieneigenen Freeze rekonstruiert. Auch der bereits vorhandene
Kapazitätspatch bindet für sich allein noch keine studienübergreifende Wahl.
Die aktuelle richtige Coordinatorentscheidung ist somit keine maschinelle
Sperre gegen einen späteren Aufruf mit einer anderen studieneigenen Auswahl.

Diese Lücke wird getrennt vom bestehenden Nachkapazitätsbefund geprüft.
Sie ist **kein beobachteter realer Test der falschen Studie**. Das Protokoll
hatte die Entscheidung bisher dem Coordinator übertragen. Die neue prospektive
Erweiterung soll diese konkrete Entscheidung auch am Prozessstart durchsetzen.

## 4. Implementierte prospektive Erweiterung

Vor Implementierung wurde derselbe Fall unabhängig an echten Methoden und
vollständigen synthetischen Belegen reproduziert. Der nun implementierte Vertrag
für v2/v3 verlangt zusätzlich zum bestehenden Testaufruf drei ausdrücklich
gesetzte Argumente:

```text
--development-choice <development-study-choice.json>
--before-test-verification <before-test-verification.json>
--before-test-verification-sha256 <vorab gebundener SHA-256>
```

Der erwartete Pretest-SHA ist eine vom aufrufenden Coordinator ausdrücklich
gesetzte Referenz. Er bindet den Choice- und Freeze-Hash. Er ist kein
kryptografischer Beweis einer vorherigen Veröffentlichung.

Die neue Methode `study_choice_authorization()` prüft gewählte Studie und
passenden Runner, exakte Payload-Gleichheit, die in der Choice enthaltene
Dev-Rangfolge, gemeinsame Testidentität und konsistente Example-/Token-Schedules.
Die Auswahl muss zugleich `selectedFreezePayload` und dem Freeze-Payload ihres
Eintrags in `candidates` entsprechen. Für eligible Kandidaten bleibt die
vorhandene Kapazitätsbindung Bestandteil des Payloads; ein gültiges FAIL wird
dabei unverändert als FAIL erhalten.

Vor jedem Testarm und vor der Summary werden die Belege erneut geprüft.
Die ursprünglich erhaltene Freigabe wird dabei erneut verglichen; ein Wechsel
der Argumente, Pfade oder Hashreferenz während des Tests autorisiert keinen
neuen Stand. Vor dem ersten Arm werden zusätzlich bereits vorhandene
`test-*.tsv`- oder Test-Receipt-Dateien zurückgewiesen. Zwischen den Armen wird
diese anfängliche Abwesenheitsforderung nicht erneut erhoben.

Fehlende Argumente erhalten keine implizite Altkompatibilität. Das gilt sowohl
am CLI-Einstieg als auch bei einem direkten Aufruf von `Study.test()`. Die
Summary erhält unter `testAuthorization` die tatsächlich verwendete Wahl,
Choice-/Pretest-/Freeze-Hashes, Quell-/Registrierungsidentität und kanonischen
Belegpfade. Die existierende `postTrainingCapacity`-Summary bleibt erhalten.

V1 bekommt diese v2/v3-Pflicht nicht nachträglich auferlegt. Seine vorhandene
Nachkapazitätskorrektur bleibt erhalten. Auswahlregeln, Trainingscode,
Prozessgrenzen und die Zulässigkeit eines vollständig gemessenen Kapazitäts-FAIL
bleiben bestehen. Historische Quittungen oder Registrierungen werden nicht
umgeschrieben; eine tatsächliche spätere Ausführung benötigt eine passende
vorherige Registrierung der geänderten Quellen.

### Tatsächlicher RED→GREEN-Nachweis

Der getrennte Prüfer entwickelte zuerst die Gegenproben; danach wurde die
Implementierung geschrieben. Die unveränderte Studienwahl-Probe trägt SHA-256
`5954cf5cfd6effe29738f9dae5213287b73430b85f9f076d5d6e3481670cd4a9`.
Ihre erwarteten Resultate werden nicht aus dem neuen Validator abgeleitet.

| Vergleichsstand | Dieselben Studienwahlfälle |
|---|---:|
| Aktuelle ungeänderte Originalrunner | 3/31 bestanden, 28 Fehlverhalten reproduziert |
| Vorheriger geprüfter Nachkapazitätspatch `c69cb4…` | 3/31 bestanden, 28 Fehlverhalten reproduziert |
| Neue kombinierte Reparatur | **31/31 bestanden** |

Die positiven Fälle umfassen echte V3-Wahl, V2 bei Gleichstand, V3 bei besserer
Antwort-NLL und vollständig belegtes Kapazitäts-FAIL. Die negativen Fälle
umfassen falschen Runner, fehlende oder veränderte Belege, widersprüchliche
Kandidatenwahl, einen bereits vorhandenen späteren Testarm sowie Änderungen
nach Arm 1 und vor der abschließenden Summary.

Zusätzliche **sechs Registrierungsgegenproben bestehen** an der neuen Reparatur:
für beide Runner blockiert eine alte Registrierung, ebenso eine neue
Registrierung mit alten Trainingsreceipts; eine vollständig neue, konsistente
Bindung erlaubt den Freeze. Diese sechs Prüfungen bestehen bereits am älteren
Kapazitätspatch und belegen den erhaltenen Quellenvertrag. Historische Belege
werden in keinem dieser Fälle vom Studiencode umgeschrieben.

Der unabhängige Prüfbereich enthält
[Studienwahlbericht](../independent-tests/choice-repair-choice-binding.json),
[Registrierungsbericht](../independent-tests/choice-repair-registration.json)
und [separaten Quellvergleich](../independent-tests/independent-source-comparison.json).
Konstruktor, Auswahlhelfer, Registrierungsprüfung, Freeze-, Test- und
Summarymethoden laufen tatsächlich aus den geprüften Quellen. Ausschließlich
`subprocess.Popen` ist an der Modellprozessgrenze abgefangen. Tatsächlich
gestartete Modellprozesse und echte Holdoutzugriffe: **0**.

Die früheren 165 Kapazitätsprüfungen bleiben ihr historischer Nachweis. Die
neuen v2/v3-Testaufrufe benötigen nun ausdrücklich die drei zusätzlichen
Argumente; eine unveränderte alte Positivfixture ohne diese Freigabe soll
gerade nicht mehr autorisieren. Es wird keine erneute unveränderte 165er-
Vollprüfung oder zusätzliche Modellabnahme behauptet.

## 5. Konkrete Artefakte und Quellenbindung

| Ausgangsquelle | Passender Patch |
|---|---|
| Exakte Originalrunner aus `9ceffc26…` | [pr508-capacity-and-choice-gates.patch](pr508-capacity-and-choice-gates.patch) |
| Bereits exakt angewendeter Kapazitätspatch `c69cb4…` | [choice-guard-on-capacity-repair.patch](choice-guard-on-capacity-repair.patch) |

Der kombinierte Patch enthält den bisherigen Kapazitätsvalidator für alle drei
Runner und zusätzlich die neue Studienwahlbindung ausschließlich für v2/v3.
Der inkrementelle Patch enthält nur diese neue v2/v3-Erweiterung. Sie sind an
unterschiedliche Ausgangsbytes gebunden und werden nicht nacheinander auf
dieselbe Originalquelle angewandt.

Kombinierter Patch-SHA-256:
`87d54102113944c0884081b44cbb05eb6d253d56f8df0c84f9c31bc4f7039d05`.
Inkrementeller Patch-SHA-256:
`7ad80b4bb76aaab3daef41bce585b913de4d0422a3679fb6aa1a86ee7c5a663f`.

| Reparierte Quelle | Endgültige SHA-256 |
|---|---|
| v1, exakt unveränderter bisheriger Kapazitätspatch | `b1bed4338bb187ae6d835a46368cc05773ae96143559af368361103b64a7b327` |
| v2 / long | `e1fa9104ec41c926423b8b7d011c05b9e6a298ee794972bc8ff7d0013bfa27b4` |
| v3 / suffix | `67da16654388d322f3735a67f3fbf1d42635ffccf6ac62668ff46b80b47f1942` |

[choice-guard-manifest.json](choice-guard-manifest.json) bindet Original-,
Basisreparatur-, Fragment-, Generator- und Ergebnisbytes. Der AST-Vergleich
gegen den bereits geprüften Kapazitätspatch bestätigt: V1 vollständig identisch;
bei v2/v3 nur neue `study_choice_authorization()`, angepasste `test()`-Methode
und zusätzliche test-spezifische CLI-Argumente. Konstruktor, `verify`, `run`,
Training, Auswahl, Freeze und Kapazitätsvalidator bleiben unverändert.

```sh
python experimental/weight-space-20261104/continuation-review/source-audit/build_choice_guard_patch.py
```

Erwartet: Exit 0 und `SOURCE_BOUND_ARTIFACTS_MATCH`. Der Generator kompiliert
die erzeugten Quellen lediglich; er importiert oder startet keinen Controller.
`--write-artifacts` regeneriert nur seine eigenen neun Artefakte in diesem
Ordner. Der vorherige `recovery-gate-repair/`-Stand bleibt unverändert.

## 6. Integrations- und Aussagegrenzen

Die neuen Dateien sind konkrete Reparaturartefakte im erlaubten eigenen
Forschungsordner. Sie ersetzen keinen aktiven fremden Runner. Eine tatsächliche
Integration in PR #508, ein neuer Testlauf oder eine neue Modellleistung ist
damit nicht behauptet. Historische Quittungen oder die veröffentlichten
`9ceffc26…`-Artefakte wurden nicht angepasst.

Die vom Coordinator gesetzte SHA-Referenz muss vor der tatsächlichen Ausführung
passend festgelegt werden. Der Code liefert keine Signatur über einen
Veröffentlichungszeitpunkt und keinen forensischen Beweis über Prozesse an
anderen Speicherorten. Die Hashprüfungen sind außerdem keine atomare
Dateisperre zwischen Python-Prüfung und späterem JVM-Dateizugriff. Für neue
Ausführungen gelten die ursprünglichen Registrierungs-, Ressourcen- und
Forschungsgrenzen weiter; keine Promotion oder produktive Runtimeänderung.

## Reproduktion der abgeschlossenen Quellenprüfung

Aus dem Repositorywurzelverzeichnis, mit dem gepinnten Commit verfügbar:

```sh
python experimental/weight-space-20261104/continuation-review/source-audit/audit_current_sources.py
```

Erwartet: Exit 0, 14 gepinnte Textquellen und
`CURRENT_FREEZE_CONSISTENT_REPAIR_NOT_INTEGRATED`.
[source-review.json](source-review.json) enthält sämtliche Quellhashes,
Git-Blob-Identitäten, Registrierungskontrollen und die berechneten Choice-/Freeze-
Hashbindungen. Es werden keine Datensatz-, Testfall-, Generator-, Checkpoint-,
JAR- oder Tokenizerbytes gelesen und keine Controller importiert.
