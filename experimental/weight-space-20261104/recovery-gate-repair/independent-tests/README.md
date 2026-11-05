# Unabhängige Prüfung der Post-Capacity-Abhängigkeit

## Ergebnis und Geltungsbereich

Der konkrete Reparaturstand ist **unabhängig GREEN**: 52 Fälle für jeden der drei
Runner und neun zusätzliche Fälle am tatsächlichen Dev-Auswahl-Einstieg ergeben
165 bestandene Prüfungen. Dieselben Orakel reproduzieren an den unveränderten
Originalquellen 132 Fehler bei 33 bestandenen Kontrollen. Die beiden Mengen sind
getrennte Ausführungen, keine zusammengezählte Modellbewertung.

Geprüft wird ein **Reparaturartefakt** für die drei Runner aus PR #508,
Quellcommit `be4e49600fed1db4c697230bb8e42c150dbfbdaf`. Die aktive Quelle dieses
fremden PR wurde hier nicht geändert. Der vollständige Patch liegt einen Ordner
höher; seine SHA-256 ist
`c69cb427e4ba0624720380edef9f7f0ce3e418591c10bd0a35f56590ad67e38f`.

Die finale Controllerprobe ist `probe_post_capacity_gate.py`, SHA-256
`4dddb6459c578c1feefbaee0bbff72eaad7a469eb799a01e078b8b4d33d51aec`.
Die Original- und Reparaturquellen werden vor dem Kompilieren an ihre SHA-256
gebunden. `independent-source-review.json` bindet die Prüfung zusätzlich an die
tatsächlichen Git-Objekte des genannten Quellcommits und vergleicht unveränderte
Study-Methoden auf AST-Ebene.

## Tatsächlich ausgeführte Ergebnisse

| Pfad | Originalquelle | Reparaturfixture | Berichte |
|---|---:|---:|---|
| v1, 128 Schritte | 10/52 PASS, 42/52 RED | 52/52 GREEN | `red-v1.json`, `green-v1.json` |
| long, 512 Schritte | 10/52 PASS, 42/52 RED | 52/52 GREEN | `red-long.json`, `green-long.json` |
| suffix, 512 Schritte | 10/52 PASS, 42/52 RED | 52/52 GREEN | `red-suffix.json`, `green-suffix.json` |
| Unveränderte Dev-Auswahlhilfe mit den jeweiligen Controllern | 3/9 PASS, 6/9 RED | 9/9 GREEN | `selector-original.json`, `selector-repaired.json` |

Ein RED-Bericht hat absichtlich Exitcode 1: Er dokumentiert einen reproduzierten
Kontrollflussfehler der Originalquelle. Ein GREEN-Bericht hat Exitcode 0. Die
Vorversion mit 50 Fällen pro Runner bleibt samt damaliger Probe und Berichten in
`historical-50-case-run/` erhalten und ist ausdrücklich als überholt markiert.
Zwei konkrete Ergänzungen erklären den finalen Umfang von 52: ein zulässiges
Latenz-FAIL und widersprüchliche aufsummierte Messzeiten. Ein noch früherer
Arbeitslauf mit 49 Fällen wurde vor dieser Archivierung um den eigenständigen
S1-Nachweisfall ergänzt; er ist keine finale Abnahmezahl.

## Was die Prüfung wirklich ausführt

Die echte `Study`-Konstruktion und die echten Methoden `verify`, `selection`,
`freeze_evidence`, `freeze`, `run` und `test` laufen aus den hashgebundenen
Original- beziehungsweise Reparaturquellen. Keine dieser Methoden wird ersetzt.
Nur `subprocess.Popen` wird abgefangen. Pfadkonstanten werden auf ein temporäres,
vollständig synthetisches Dateisystem im zugewiesenen Testordner umgebogen.

Bei Grenztests wirft die abgefangene `Popen` sofort eine Ausnahme. Ein im Bericht
gezählter `processBoundaryCalls=1` bedeutet daher, dass die echte Testmethode die
Startgrenze autorisierte; tatsächlich wurde **kein Prozess gestartet**. Die
Summary-Tests liefern ausschließlich an dieser Grenze künstliche Testausgaben
und einen künstlichen Exitcode zurück. So laufen auch die echten Receipt- und
Summary-Schreibpfade, ohne einen JVM-Prozess, ein Modell oder echte Testbeispiele
auszuführen. Der synthetische Score 40/48 ist ausschließlich eine Testfixture und
kein Forschungsergebnis.

Die getrennte Auswahlprobe führt die echte, unveränderte
`choose_development_study.main()` aus. Sie lädt die Controller mit deren echter
`controller()`-Funktion und prüft echte Registrierungen, Selektionen und
`freeze_evidence()`-Ergebnisse. Ihre 512 Schrittzeilen sind synthetische
Vollständigkeitsdaten, keine absolvierten Trainingsschritte. Der Auswahlhelfer
startet überhaupt keinen Prozess.

## Unabhängige Daten- und Orakelgrundlage

Die Capacity-Fixtures werden direkt aus dem Rohformat von
`experimental/weight-space-20261104/LmCapacityBenchmark.kt` erzeugt, dessen
SHA-256 vor dem Lauf überprüft wird:
`d88541b436089b6df04f50035cffa79378b939dabbf03ef77a9ca1acd52bbd9c`.
Keine Fixture wird durch die neu implementierte Validatorfunktion erzeugt.

Jeder vollständige Beleg enthält eine Protokollzeile, eine Modellzeile, acht
Call-Messungen, elf Speichermessungen und ein Ergebnis. Die acht Calls umfassen
einen Warmup und drei gemessene Wiederholungen mit jeweils einem beziehungsweise
32 angeforderten Tokens. Die ursprünglichen Grenzen bleiben 1.500 ms für den
Ein-Token-Call, 5.000 ms für 32 Tokens, 786.432 KiB beobachtetes VmHWM, ein
1-GiB-JVM-Heap und 120 Sekunden Prozessbudget.

Checkpointdateien, JARs, Tokenizer und Datensätze sind ausdrücklich künstliche
Identitätssentinels. Die Checkpointbytes sind keine ausführbaren LM1-Modelle.
Die in den Rohlog-Fixtures enthaltenen Parameterzahlen 132.160 und 6.562.304
testen die bestehende Protokollidentität; sie belegen weder neue trainierte
Parameter noch deren numerische Qualität oder realen Speicherverbrauch.

## Abgedeckte Entscheidungspunkte

Die Positivkontrollen erlauben vollständiges PASS, vollständiges EOS-FAIL,
vollständiges RSS-FAIL sowie vollständiges Latenz-FAIL. Ein gültiges FAIL ist eine
abgeschlossene negative Kapazitätsmessung. Gemäß Intent-Protokoll darf diese mit
einem begrenzten Intent-Ergebnis erscheinen, muss aber sichtbar bleiben.
`modelPromotion` und `generalIntelligenceClaim` bleiben in der echten Summary
`false`. Ein Prozess-Timeout, unvollständige Messungen oder fehlerhafte Identitäten
sind dagegen kein zulässiges negatives Messergebnis.

Die Negativfälle prüfen fehlende S1- und W1-Belege, fehlende oder kaputte Dateien,
falsche Quellen-/Registrierungs-/JAR-/Log-/Checkpoint-/Tokenizeridentität,
abweichende Prozessbefehle und Budgets, Timeout, ungültige Laufzeiten,
Exitcode-Widersprüche, fehlende/duplizierte Trials oder Ergebnisfelder,
unzulässige Protokolländerungen, nicht endliche Messwerte, erfundene PASS-Summaries,
falsche Parameter-/Dateigrößen und nicht zu den Rohmessungen passende Zusammenfassungen.
Die Summe der acht Callzeiten darf einschließlich Ladezeit nicht die erfasste
Prozesszeit überschreiten.

Vor dem Freeze müssen ungültige Nachweise ohne Freeze-Datei und ohne
Prozessstartversuch stoppen. Nach dem Freeze werden entfernte sowie semantisch
noch gültige, aber veränderte Receipts zurückgewiesen. Eine Mutation nach dem
ersten simulierten Testarm stoppt vor Arm 2. Eine Mutation nach dem letzten Arm
verhindert die Summary. Die bestehende Freeze-Datei bleibt in diesen Fällen
unverändert.

Die Wiederaufnahmeprüfung ergänzt nach einer abgewiesenen, fehlenden
Post-Capacity-Datei deren exakte Originalbytes. Anschließend darf der unveränderte
Trainingsstand seinen Freeze erzeugen und die erste Teststartgrenze erreichen.
Es wird weder neu trainiert noch ein existierender Trainingsreceipt umgeschrieben.
Eine frühere Dev-Auswahl bei gleichen Exact- und NLL-Werten bleibt zulässig.

Die separate Auswahlhilfe behält bei Gleichstand v2 und wählt bei höherem
Dev-Exact v3. Vollständiges EOS-FAIL bleibt im gewählten Freeze-Payload sichtbar.
Fehlende Rohtrials oder falsche Prozessbudgets blockieren die Auswahl auch dann,
wenn der betroffene Arm die Dev-Schwelle ohnehin verfehlt. Ungültige Belege werden
damit nicht als gewöhnliches `NO_DEVELOPMENT_SIGNAL` umgedeutet.

## Grenzen und unveränderte Forschung

Diese Ergebnisse sind Kontrollfluss- und Belegintegritätsnachweise. Sie sind
keine neue Modell-, Sprachqualitäts-, Kapazitäts- oder Android-Geräteabnahme und
keine Behauptung, jeden denkbaren gleichzeitigen Fremdeingriff mathematisch
auszuschließen. Vorhandene historische Trainingsläufe, Checkpoints, Registrierungen
und Forschungsresultate werden weder als Fixtures verwendet noch umgeschrieben.
Es gibt keine Modell-Promotion, keinen produktiven Trainingslauf und keine echte
Holdoutauswertung. Die älteren 75 G1-Fälle wurden nicht erneut ausgeführt.

Für eine spätere Anwendung auf einen echten Runner ist eine neue, prospektive
Quell- und Registrierungsbindung erforderlich. Alte Receipts werden durch diesen
Patch oder durch diese synthetischen Tests nicht nachträglich gültig gemacht.

## Reproduzieren

Alle Befehle werden aus dem Repository-Verzeichnis ausgeführt. Für jede Variante
`v1`, `long` oder `suffix` erzeugt der erste Befehl den erwarteten RED-Nachweis:

```bash
python -B experimental/weight-space-20261104/recovery-gate-repair/independent-tests/probe_post_capacity_gate.py \
  --variant v1 \
  --report experimental/weight-space-20261104/recovery-gate-repair/independent-tests/repeated-red-v1.json
```

Die reparierte v1-Quelle wird mit ihrem exakten finalen Hash geprüft:

```bash
python -B experimental/weight-space-20261104/recovery-gate-repair/independent-tests/probe_post_capacity_gate.py \
  --variant v1 \
  --runner experimental/weight-space-20261104/recovery-gate-repair/fixtures/v1/run_registered_study.py.txt \
  --expected-sha256 b1bed4338bb187ae6d835a46368cc05773ae96143559af368361103b64a7b327 \
  --report experimental/weight-space-20261104/recovery-gate-repair/independent-tests/repeated-green-v1.json
```

Die zwei weiteren finalen Fixture-Hashes sind für long
`638d02eaded31c3d7be7d978d6bcf08319dda9db35e948f1e0142008c2d5d387` und für suffix
`907d1c9635dc0e375c8d0ef68afa6f85f6c27655758a1895281f74ae0cfc0067`.
Für den echten Auswahl-Einstieg wird `--phase original` beziehungsweise
`--phase repaired` verwendet:

```bash
python -B experimental/weight-space-20261104/recovery-gate-repair/independent-tests/probe_development_selector.py \
  --phase repaired \
  --report experimental/weight-space-20261104/recovery-gate-repair/independent-tests/repeated-selector-repaired.json
```
