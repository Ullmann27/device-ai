# Unabhängiger Nachweis der v2/v3-Wahlbindung

## Ergebnis

Die neue prospektive Korrektur besteht **31/31 Wahlbindungsfälle und 6/6
Registrierungsfälle**, insgesamt **37/37**. Die unveränderte 31er-Probe findet
sowohl im aktuellen Original als auch im vorherigen Kapazitätspatch dieselbe
fehlende maschinelle Wahlbindung: drei positive Kontrollen bestehen, 28
Erwartungen werden verfehlt. Diese Zahlen beschreiben Fälle derselben
Integrationslücke und keine 28 verschiedenen Fehlerursachen.

| Quellstand | Wahlbindung: gleiche 31 Fälle | Prospektive Registrierung: sechs Fälle |
|---|---:|---:|
| Originale aus `9ceffc26a2159c8dc726c1f13d2882119159c130` | 3 PASS, 28 RED | Nicht erneut geprüft |
| Zuvor verifizierter Kapazitätspatch `c69cb427…` | 3 PASS, 28 RED | 6/6 PASS |
| Neue kombinierte Korrektur `87d54102…` | **31/31 PASS** | **6/6 PASS** |

Die früheren 165 Kapazitätsfälle wurden nicht erneut ausgeführt. Ihr
Prüfgegenstand bleibt historisch erhalten. Die neue Prüfung behandelt die
zusätzliche v2/v3-Studienwahl, den vorher gepinnten Coordinatorbeleg und deren
tatsächliche Durchsetzung im Testaufruf.

## Was im veröffentlichten Stand wirklich vorliegt

Der geprüfte [PR-#508-Commit](https://github.com/Ullmann27/device-ai/commit/9ceffc26a2159c8dc726c1f13d2882119159c130)
ergänzt veröffentlichte Studienwahl-, Freeze- und Vorabprüfdateien. Ihre
Rohhashes stimmen untereinander, die gewählte Studie ist v3 und das gewählte
Freeze-Payload ist exakt gleich dem veröffentlichten Freeze. Die drei Dateien
sind daher keine Belege einer falschen historischen Auswahl.

Der SHA-256 von `before-test-verification.json` ist:

`b12fdea44b7a3531be407f542d66c104406e7d51038956a048403b57d18136c6`

Die separat aufrufbaren Originalmethoden `Study.test()` lesen jedoch weder
die Studienwahl noch diese Vorabprüfung. Der Protokolltext delegiert den
Vergleich bislang an den Coordinator. Ein lokal gültiger Freeze der anderen
Studie kann deshalb die Testprozessgrenze erreichen, obwohl der tatsächliche
Selector eine andere Studie gewählt hat. Auch nachträgliche Änderungen der
Wahl bleiben auf diesem Pfad unbeachtet.

Im geprüften Gitbaum befinden sich keine veröffentlichten finalen Testlogs.
Das ist keine Aussage über unveröffentlichte Prozesse. Die Gegenproben zeigen
einen möglichen unzureichend abgesicherten Ablauf; sie behaupten keinen
tatsächlich falsch durchgeführten Modelltest. Der Text- und AST-Abgleich steht
in [published-binding-review.json](published-binding-review.json).

## Echte Aufrufe und unabhängige Erwartungen

`probe_choice_binding.py` führt den echten, unveränderten
`choose_development_study.main()` sowie Konstruktor, `verify`, `selection`,
`freeze`, `freeze_evidence`, `run` und `test` aus den jeweils hashgebundenen
Controllern aus. Keine dieser Produktionsmethoden wird ersetzt. Nur
`subprocess.Popen` wird abgefangen. Pfadkonstanten und CLI-Argumente zeigen
auf vollständig synthetische, temporäre Dateien im eigenen Testbereich.

Die bestehenden Fixture-Bausteine werden aus der früheren unabhängigen Suite
an ihre exakten SHA-256 gebunden und unverändert verwendet. Sie liefern
vollständige acht Kapazitätsaufrufe und je Arm 512 synthetische Schrittzeilen.
Die Zahlen sind Testdaten, keine ausgeführten Trainingsupdates. Die
Checkpoint-, JAR-, Tokenizer- und Datensatzbytes sind nicht ausführbare
Identitätssentinels. Kein echtes Modell wird geladen und keine echte
Holdoutdatei oder ihr Generator gelesen.

Positivkontrollen bestätigen die Auswahl von v3, v2 bei vollständigem
Gleichstand und v3 bei gleicher Trefferzahl, aber niedrigerer Antwort-NLL.
Die Summary-Kontrollen führen den echten Schreibpfad aus und erhalten einen
gültigen Kapazitäts-FAIL sichtbar. Die dort simulierten 40/48 Testtreffer
sind ausschließlich ein künstlicher Kontrollwert und kein Modellergebnis.

Die Gegenfälle prüfen:

- fehlende Choice-/Vorabprüfargumente oder einen fehlenden beziehungsweise
  ungültigen erwarteten Vorabprüfhash;
- entfernte, unlesbare oder nach dem Pinnen veränderte Belegdateien;
- falsche Quellen-, Choice- oder Freeze-Identität und falsche boolesche
  Freigabebehauptungen;
- einen falschen Runner sowie eine intern widersprüchliche Auswahl, auch
  wenn deren Dateien vollständig neu gehasht wurden;
- unveränderte Rangfolge einschließlich NLL-Vergleich und v2-Gleichstandsregel;
- bereits vorhandene Testlogs oder Testreceipts eines späteren Arms;
- Belegänderung nach Arm 1 sowie nach Arm 4 vor dem Summary-Schreiben.

Bei einer Änderung nach Arm 1 startet mit der Reparatur kein Arm 2. Bei
einer Änderung nach Arm 4 entsteht keine Ergebnis-Summary. Vorhandene
Trainingsreceipts, Checkpoints und Freeze-Dateien bleiben vom getesteten
Code unverändert. Ein gezählter `processBoundaryCalls` bedeutet ausschließlich,
dass die abgefangene Startgrenze erreicht wurde. Tatsächlich gestartete
Modellprozesse: **0**.

## Prospektive Registrierung und Wiederaufnahme

`probe_prospective_registration.py` prüft für long und suffix jeweils drei
reale Kontrollpfade:

1. Neue Runnerbytes unter der alten synthetischen Registrierung werden im
   echten Konstruktor mit `SOURCE_OR_DATA_CHANGED` zurückgewiesen.
2. Eine neue Registrierung mit den alten synthetischen Trainingsreceipts
   wird beim Freeze mit `INVALID_TRAINING_RECEIPT` zurückgewiesen.
3. Vollständig neu und konsistent gebundene synthetische Registrierung,
   Quellen und Receipts erlauben den Freeze einschließlich Kapazitätsbelegen.

Das ist keine Migration historischer Forschung. Die echte Übernahme der
neuen Runner benötigt eine passende prospektive Bindung. Alte Quittungen
werden nicht umgeschrieben oder durch neue Metadaten nachträglich autorisiert.
Ein teilweise vorhandener Testlauf wird ebenfalls nicht automatisch erneut
ausgeführt; der neue Teststart verweigert vorherige Testartefakte vor Arm 1.

## Unveränderte Methoden und Quellen

Der unabhängige, rein lesende AST-Abgleich in
[independent-source-comparison.json](independent-source-comparison.json)
bestätigt:

- v1 ist zum bereits geprüften Kapazitätspatch vollständig bytegleich.
- In long und suffix kommt nur `study_choice_authorization()` hinzu;
  die bestehende Methode `test()` und die CLI erhalten die neue Bindung.
- Alle übrigen `Study`-Methoden sind AST-identisch. Insbesondere Training,
  Auswahl, Registrierungskontrolle und die Kapazitätsprüfung bleiben erhalten.
- Die vollständige Methode `post_capacity_evidence()` ist sogar textgleich
  zum vorherigen Kapazitätspatch.

| Artefakt | SHA-256 |
|---|---|
| Unveränderte 31er-Probe | `5954cf5cfd6effe29738f9dae5213287b73430b85f9f076d5d6e3481670cd4a9` |
| Registrierungsprobe | `03102af2856607b88d607c0c84caa6647b3fba445485242c625e4a6937ad183b` |
| Neuer kombinierter Patch | `87d54102113944c0884081b44cbb05eb6d253d56f8df0c84f9c31bc4f7039d05` |
| Korrigierter long-Runner | `e1fa9104ec41c926423b8b7d011c05b9e6a298ee794972bc8ff7d0013bfa27b4` |
| Korrigierter suffix-Runner | `67da16654388d322f3735a67f3fbf1d42635ffccf6ac62668ff46b80b47f1942` |

Die Quellen- und Blob-Identitäten der Originale stehen zusätzlich in
[source-inventory.json](source-inventory.json). Der aktualisierte Patch liegt
im benachbarten `source-audit/`; die aktiven fremden Runner bleiben unverändert.

## Grenzen

Der vorab erwartete Beleg-SHA ist eine explizite Referenz des Aufrufers. Er
bindet lokale Bytes, beweist jedoch nicht kryptografisch einen früheren
Publikationszeitpunkt. Die neue Summary benennt diese Grenze selbst. Auch
eine atomare Dateisperre zwischen letzter Hashprüfung und JVM-Dateizugriff
wird hier nicht behauptet.

Diese Prüfung belegt Kontrollfluss, Quellenbindung und Zurückweisung
unzulässiger Wiederverwendung. Sie ist keine neue Modellqualität,
Generalisierungsbestätigung, Android-Abnahme oder Modell-Promotion.

## Reproduktion

Aus dem Repositorywurzelverzeichnis reproduzieren diese Aufrufe die zwei
unterschiedlichen RED-Quellstände mit derselben Probe. Erwarteter Exitcode: 1.

```sh
python -B experimental/weight-space-20261104/continuation-review/independent-tests/probe_choice_binding.py --phase original --report /tmp/original-choice-binding.json
python -B experimental/weight-space-20261104/continuation-review/independent-tests/probe_choice_binding.py --phase capacity-repair --report /tmp/capacity-repair-choice-binding.json
```

Die korrigierte Quelle wird mit exakt den geprüften Hashes aufgerufen.
Erwarteter Exitcode: 0 mit 31/31.

```sh
python -B experimental/weight-space-20261104/continuation-review/independent-tests/probe_choice_binding.py \
  --phase choice-repair \
  --long-runner experimental/weight-space-20261104/continuation-review/source-audit/fixtures/long/run_registered_study.py.txt \
  --long-sha256 e1fa9104ec41c926423b8b7d011c05b9e6a298ee794972bc8ff7d0013bfa27b4 \
  --suffix-runner experimental/weight-space-20261104/continuation-review/source-audit/fixtures/suffix/run_registered_study.py.txt \
  --suffix-sha256 67da16654388d322f3735a67f3fbf1d42635ffccf6ac62668ff46b80b47f1942 \
  --report /tmp/choice-repair-choice-binding.json
```

Für den 6/6-Registrierungsnachweis denselben Aufruf mit
`probe_prospective_registration.py` und einer eigenen Reportdatei verwenden.
Die statische Gegenprüfung ist separat reproduzierbar:

```sh
python -B experimental/weight-space-20261104/continuation-review/independent-tests/audit_choice_sources.py --report /tmp/independent-choice-source-comparison.json
```

Die gespeicherten Ergebnisdateien sind `original-choice-binding.json`,
`capacity-repair-choice-binding.json`, `choice-repair-choice-binding.json`,
`capacity-repair-registration.json` und `choice-repair-registration.json`.
