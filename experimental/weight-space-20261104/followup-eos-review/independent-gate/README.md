# Unabhängige Prüfung: Nachtrainingsevidenz vor Intent-Freeze

**Stand:** 4. November 2026. **Status:** `RED_REPRODUCED`, keine Reparatur oder
Modellübernahme behauptet. Zwei fehlende Schutzbedingungen sind auf exakt
gebundenem Quelltext reproduziert; vier Kontrollen bestehen. Kein Modellprozess,
Training, echter Testdatensatz, produktiver Runtime-Eingriff oder fremder
Branch-Write wurde ausgeführt.

## Geprüfte Quellen und Arbeitsteilung

| Quelle | Geprüfter Commit | Einordnung |
|---|---|---|
| [Gewichts-PR #492](https://github.com/Ullmann27/device-ai/pull/492) | `30cd3a89810ed14fa84eca95236d3177e016118d` | Abgeschlossene G1-Forschung und unveränderter Ausgangsdriver |
| [Historischer Helfer #497](https://github.com/Ullmann27/device-ai/pull/497) | `06f071e5850dd405f374bbb58d826f64740d83af` | Frühere P0-Reparatur; ersetzt die stärkere G1-Fassung nicht |
| [Support-PR #496](https://github.com/Ullmann27/device-ai/pull/496) | `3968d785df607f8d461162c50bb3d710a925503e` | Alte Unterbrechungsbefunde nur für ihre jeweils belegten Quellen |
| [Neuer Forschungs-PR #508](https://github.com/Ullmann27/device-ai/pull/508) | `def5acf9cbba82ee51a53cc06fecc52e9cd0324d` | Separater aktiver Forschungszweig, auf #492 aufgebaut |

Der konkret verknüpfte [Koordinationskommentar vom 4. November](https://github.com/Ullmann27/device-ai/pull/492#issuecomment-6638799892)
belegt die Zuständigkeit des #508-Teams für neue Dateien unter
`experimental/lm1-training-recovery-20261104/`. Dieses Review dupliziert weder
dessen LR-Versuche noch dessen Trainingsharness. Der gelesene #508-Stand enthält
18 neue Dateien: Protokolle, Registrierung, Diagnosen, Taskdaten und
Build-/Mask-Prüfbelege. Er enthält keine veröffentlichten Trainings- oder
Testresultate. Das ist eine Aussage über den veröffentlichten Snapshot, keine
Aussage über einen möglicherweise inzwischen laufenden externen Prozess.

Unser Beitrag ist eine unabhängige, modellfreie Prüfung der Freigabe zwischen
abgeschlossenem Intent-Training, erforderlicher Nach-Kapazitätsmessung und Test.
Die zwei weiteren Helfer untersuchen Ausgabe-/EOS-Diagnostik und tatsächliche
Trainingszielzählung. Spätere #508-Commits benötigen einen erneuten, gezielten
Quellabgleich; der hier abgelegte Probe-Loader lehnt unbekannte Bytes vor deren
Kompilierung ab.

## Präziser Befund

Im geprüften [run_registered_study.py](https://github.com/Ullmann27/device-ai/blob/def5acf9cbba82ee51a53cc06fecc52e9cd0324d/experimental/lm1-training-recovery-20261104/run_registered_study.py)
ruft `intent()` nach jedem Trainingsarm `benchmark(...-capacity-after, ...)`
auf (Zeilen 154–160). Die getrennt aufrufbaren Phasen `freeze_evidence()` und
`test()` verlangen jedoch keinen dieser beiden Nach-Kapazitätsbelege
(Zeilen 205–231 und 246–278). Vollständige Trainingsquittungen und ausreichende
Dev-Metriken genügen deshalb für einen Freeze und den ersten Testprozess.

Ein praktischer Unterbrechungspfad ist: Der zweite Trainingsprozess hat sein
Ergebnis und seine Quittung vollständig geschrieben; der übergeordnete Ablauf
endet vor dessen nachfolgendem Benchmark. Ein späterer `freeze`-/`test`-Aufruf
kann den Test dann ohne vollständige Kapazitätsevidenz öffnen. Ebenso wird ein
abgeschnittener vorhandener Nach-Kapazitätsbeleg nicht gelesen und nicht
abgewiesen. `intent-summary.json` enthält im geprüften Code außerdem kein
Kapazitätsergebnis neben der Lernmetrik.

**Das ist kein erneuter BASELINE_GATE-Trainingsbypass.** Der Kotlin-Intent-Harness
führt sein initiales Gate tatsächlich in einer eigenen JVM mit `-Xmx1g` und
120 Sekunden durch und blockiert Updates bei Fehlschlag. Auch die ursprüngliche
G1-Orchestrierung bleibt in #508 unverändert.

**Ein gültig belegtes nachträgliches Capacity-FAIL darf weiter zur begrenzten
Intent-Messung führen.** Das legt [INTENT_PROTOCOL.md, Abschnitte 5, 7 und 8](https://github.com/Ullmann27/device-ai/blob/def5acf9cbba82ee51a53cc06fecc52e9cd0324d/experimental/lm1-training-recovery-20261104/INTENT_PROTOCOL.md)
ausdrücklich fest. Es muss aber mitberichtet werden und blockiert allgemeine
Eignung beziehungsweise Promotion. Ein unbekanntes, fehlendes oder ungültiges
Ergebnis ist kein solches belegtes FAIL. Dieses Review verlangt deshalb gültige
PASS-**oder**-FAIL-Evidenz vor dem Freeze, nicht ausschließlich PASS.

## Tatsächlich ausgeführte Reproduktion

```sh
python experimental/weight-space-20261104/followup-eos-review/independent-gate/probe_pr508_post_capacity.py
```

Erwarteter Exitcode auf dem abgelegten Original: **1**, Status
`RED_REPRODUCED`. Der [Rohbericht](probe-result.json) stammt aus der ausgeführten
Prüfung; er ist kein Modellleistungsbericht.

| Fall | Erwartung | Tatsächlich auf #508 | Prüfergebnis |
|---|---|---|---|
| Beide Nach-Kapazitätsbelege fehlen | Vor Freeze/Test abweisen | Freeze erstellt; erster Testprozess erreicht die abgefangene Grenze | **FAIL** |
| Wide-Nach-Kapazitätsquittung abgeschnitten | Vor Freeze/Test abweisen | Freeze erstellt; erster Testprozess erreicht die abgefangene Grenze | **FAIL** |
| Vollständige synthetische Metadaten, PASS | Test nach Freeze autorisieren | Erste Testprozessgrenze erreicht | PASS |
| Explizites synthetisches Capacity-FAIL | Begrenzten Tasktest weiterhin zulassen | Erste Testprozessgrenze erreicht | PASS |
| Trainingsquittung mit falschem Loghash | Vor Freeze/Test abweisen | `INVALID_TRAINING_RECEIPT`, kein Freeze/Testversuch | PASS |
| Unbekannte Runnerbytes | Vor Kompilierung abweisen | `UNREVIEWED_SOURCE_REJECTED_BEFORE_COMPILE` | PASS |

Die Probe führt die echten Methoden `Study.__init__`, `verify`, `selection`,
`freeze_evidence`, `freeze` und `test` aus. `ROOT` und `EXPERIMENT` zeigen dabei
auf einen neuen temporären synthetischen Dateibaum. Nur `subprocess.Popen`
wird abgefangen; es werden **null Prozesse** gestartet. Checkpoints, JAR,
Datensatz, Scores und Quittungen sind bewusst synthetisch. Die positiven
Metadatenkontrollen sind kein Nachweis, dass diese unechten Checkpoints ein
Modellgate bestehen würden. Alle vorhandenen synthetischen Quittungsbytes
bleiben unverändert. Temporäre Fixtures werden nach der Prüfung entfernt.

| Identität | SHA-256 |
|---|---|
| Exakter fremder Runner | `409f87f5a174804d7290c29972d6b4e2bbb19ad293ac1da027122c3f28d14d4f` |
| Unabhängige Probe | `2bae5a579fa2bd078f3c03780bed8057cc0da3c1d328077321adc16af09ed4dd` |
| Ausgeführter Rohbericht | `8a0695a590b28e047aa6bd2ef1870ab9a44dad62f99eed812c6b53c3098b04e4` |

Die Originalbytes liegen unverändert als
[`fixtures/pr508-def5acf9-run_registered_study.py.txt`](fixtures/pr508-def5acf9-run_registered_study.py.txt)
bei. Es handelt sich um eine nachvollziehbare Quellenkopie des Eigentümerrepos,
nicht um einen neu aktivierten Trainingsrunner. Die Probe prüft ihren Hash vor
`compile`/`exec`; die normale CLI-Einstiegsfunktion wird nicht aufgerufen.

## Kleinste erforderliche Korrektur im zuständigen #508-Zweig

1. Einen ausschließlich lesenden Prüfer der beiden vorhandenen
   `intent-<variant>-capacity-after.tsv`/`.receipt.json` ergänzen. Fehlende,
   abgeschnittene oder widersprüchliche Dateien weisen die Phase ab; sie dürfen
   nicht durch einen implizit gestarteten Prozess ersetzt werden.
2. Für jeden ausgewählten Checkpoint Quittungs- und Loghash, Registrierung,
   ausführbare Dateien, genauen Benchmarkbefehl mit `-Xmx1g`, 120-Sekunden-Grenze,
   Timeout-/Exitstatus und Checkpoint-/Tokenizeridentität kontrollieren. Gültige
   PASS-/FAIL-Auswertung aus den Rohmessungen unterscheiden. Ein vorzeitig
   beendeter oder unvollständiger Lauf ist kein bestätigtes bestandenes Gate.
3. Beide geprüften Resultate samt Belegidentitäten in `freeze_evidence()`
   aufnehmen. `test()` rekonstruiert damit auch diese Abhängigkeiten, bevor ein
   Testprozess gestartet wird. Änderungen seit dem Freeze weisen den Test ab.
4. Beide Kapazitätsresultate dauerhaft in `intent-summary.json` und im
   Benutzerbericht gemeinsam mit den Taskmetriken ausgeben. Ein belegtes FAIL
   darf den begrenzten Taskwert nicht verschwinden lassen; der Taskwert darf
   das FAIL nicht überschreiben oder allgemeine Eignung vortäuschen.
5. Die korrigierten Quellen und ihre Registrierung im zuständigen Branch vor
   weiterer Ausführung neu binden. Bereits ausgewertete Testdaten dürfen nach
   einer Trainingsänderung nicht wieder als unabhängiger blinder Test gelten.

Dieser Review wendet diesen Patch nicht an: Der aktive fremde Runner liegt
außerhalb unseres erlaubten Änderungsbereichs. Die zwei roten Fälle müssen auf
den konkret korrigierten, erneut geprüften Bytes grün werden; die ausdrücklich
zulässige FAIL-Kontrolle und die gültigen Abläufe müssen erhalten bleiben.

## Abnahme der neuen Forschung

**GO für die aktuelle modellfreie Diagnose:** Quellvergleich, synthetische
Kontrollflusstests, Ziel-/EOS-Zählung aus unveränderten Trainingsdaten und
Auswertung bereits veröffentlichter Rohlogs. Kein alter Holdout bestimmt neue
Parameter. Das Original und alle bisherigen Ergebnisse bleiben erhalten.

**NO-GO für eine Übernahme eines neuen Lernerfolgs allein aus diesem #508-Stand:**
Es fehlen veröffentlichte Modellresultate; die oben reproduzierte
Freeze-Abhängigkeit ist noch offen. Eine spätere Abnahme verlangt zusätzlich:

- Unveränderte Ausgangscheckpoint-/Tokenizeridentitäten und vollständige
  Vorher-/Nachherbelege; wirkliche Parameteränderung allein genügt nicht.
- Gültige initiale Kapazitäts- und Paritätsbelege vor jedem Update, klare
  Abbruchgrenzen und gesonderte nachträgliche Kapazitätsresultate.
- Vorab gebundene Train-/Dev-/Testaufteilung und feste Lernregel; Auswahl und
  beide vollständigen Armbelege vor dem einmaligen Test eingefroren.
- Unabhängige Nachzählung der tatsächlichen freien Ausgaben und aller Fälle,
  einschließlich vorzeitigem EOS, ungültigen Antworten, Klassenfehlern und
  Paarvergleichen. Teacher-forced NLL ersetzt keine freie Ausgabe.
- Strikte Trennung zwischen eng begrenztem Digit+EOS-Aufgabenerfolg, dem
  ursprünglichen vollständigen 32-Token-Gate und allgemeiner Modellqualität.
- Prüfung der behaupteten Commitidentität gegen die veröffentlichte Quelle:
  Der aktuelle `--source-commit`-Parser prüft selbst nur das 40-Hex-Format;
  der vom Aufrufer eingesetzte Commitstring ist keine eigenständige Attestierung.

Der alte G1-Driver bleibt auf SHA-256
`8f2f49bda56ce1f63e86fecf262229acff3d6f5d1ada1d97270b6c1731cda9be`.
Seine bestehenden Baseline-/Freeze-/Resume-Sicherungen wurden hier statisch
nachgelesen; die 75er-Suite wurde nicht erneut ausgeführt. Der alte #496-Befund
darf nicht pauschal als neuer P0 interpretiert werden: Ein historischer
`ladder()`-Frühereturn bei vorhandener Datei ist weiterhin sichtbar, aber
`continuation_preflight()` liest und prüft die Datei vor Training und Abschluss.
Die neuen registrierten Profile prüfen bereits beim Ladder-Resume vor.

**Rückfall:** Keine neue Modellversion übernehmen; Original und bestandene
Initialmodelle behalten. Die negative Probe bleibt als dokumentierter
Forschungsbefund erhalten. Keine Promotion, kein Produktivtraining und keine
Änderung der App-Runtime.
