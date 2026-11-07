# Unabhängige Prüfung der historischen EOS-Rohlogs

Experiment-ID: `continuum-historical-eos-audit-v1`

Geprüfter Repository-Stand: `30cd3a89810ed14fa84eca95236d3177e016118d`

Modus: ausschließlich lesende Auswertung vorhandener Git-Blobs; keine Gewichte geladen, keine Inferenz, kein Training, keine Holdout-Auswertung.

## Ergebnis

Der neue Prüfer `eos_diagnostics.py` bestätigt die Konsistenz von **16 archivierten Rohlogs**. Er verknüpft pro Fortsetzung die protokollierte Trainingsquelle mit der jeweiligen ursprünglichen Aufgabenprobe sowie den protokollierten Trainingscheckpoint mit den anschließenden Aufgabenproben und Kapazitätsmessungen. SHA-256, Tokenizer und Parameterzahl müssen zusammenpassen. Die JSON-Ausgabe enthält die vollständigen Prüfsummen der gelesenen Rohlogs und auf den Commit festgelegte Quellen-URLs.

| Profil / Fortsetzung | Parameter | EOS sofort, vorher | EOS sofort, danach | Aufgaben korrekt vorher / danach | Kapazität: EOS/0-Aufrufe |
|---|---:|---:|---:|---:|---:|
| core-6562304 / klein | 132.160 | 4/6 | 6/6 | 0/6 → 0/6 | 8/8 |
| core-6562304 / erweitert | 6.562.304 | 4/6 | 6/6 | 0/6 → 0/6 | 8/8 |
| core-9712128 / klein | 132.160 | 4/6 | 6/6 | 0/6 → 0/6 | 8/8 |
| core-9712128 / erweitert | 9.712.128 | 4/6 | 6/6 | 0/6 → 0/6 | 8/8 |

Die vollständige maschinelle Auswertung steht in [eos-audit-20261104.json](eos-audit-20261104.json). Die zugrunde liegenden Logs liegen in den festgelegten Verzeichnissen [core-6562304](https://github.com/Ullmann27/device-ai/tree/30cd3a89810ed14fa84eca95236d3177e016118d/experimental/weight-space-20261104/core-6-10m-evidence/run/core-6562304/logs) und [core-9712128](https://github.com/Ullmann27/device-ai/tree/30cd3a89810ed14fa84eca95236d3177e016118d/experimental/weight-space-20261104/core-6-10m-evidence/run/core-9712128/logs).

Vier Präzisierungen sind für die Interpretation entscheidend:

1. **Sofortiges EOS bestand bereits vor der Fortsetzung.** Neu betroffen sind nur die Aufgabenindizes 2 und 3: ausschließlich „Hallo“ beziehungsweise „fertig“ auszugeben. Beide Antworten waren vorher bereits falsch. Der Befund beschreibt eine zusätzliche Entleerung der Ausgabe; er belegt keinen Rückgang von vorher erfolgreichen Aufgaben.
2. **Acht Kapazitätsaufrufe entsprechen einem Protokollprompt.** Das Protokoll enthält ein Aufwärmpaar und drei gemessene Paare mit jeweils einem beziehungsweise 32 angeforderten Tokens. Alle vier Fortsetzungen verwenden denselben protokollierten Prompt-Hash. Die 32 Aufrufe über vier Läufe sind keine 32 unterschiedlichen Aufgaben. Alle haben null neue Tokens, `stop=EOS`, den SHA-256 der leeren Ausgabe und keine beobachtete erste Tokenzeit.
3. **Die kleinen Kontrollen zeigen denselben Effekt.** Beide kleinen Läufe protokollieren sogar denselben fortgesetzten Checkpoint `7e6d136d6b31b8f7e9839b344be39a38d0f00146286b315a04ecfb4c4997b91e`. Die vier Laufaufzeichnungen repräsentieren somit drei unterschiedliche fortgesetzte Checkpoint-Identitäten und keine vier unabhängigen Modellreplikationen.
4. **Die protokollierten Trainingsbedingungen sind vergleichbar.** Alle vier Läufe haben 16 vollständige Schritte, 3.255 kumulierte Tokens, dieselben Dataset-, Schedule-, Token-Schedule- und Tokenizer-Hashes sowie dieselben kumulierten Tokenzahlen je Schritt. Die NLL-Werte sind endlich. Das beweist die Übereinstimmung der protokollierten Angaben; die Trainingsdaten und Gewichtsdateien werden damit nicht erneut gehasht.

## Mechanismus und Aussagegrenze

In der festgelegten [LmRuntime.kt](https://github.com/Ullmann27/device-ai/blob/30cd3a89810ed14fa84eca95236d3177e016118d/app/src/main/java/at/heinz/deviceai/learning/lm/LmRuntime.kt) beendet die EOS-Auswahl die Generierung vor dem Anhängen eines Tokens und vor dem Erhöhen des Ausgabezählers. `EOS` zusammen mit null neuen Tokens passt deshalb zu einer EOS-Auswahl beim ersten Generierungsschritt. Die Aufgabenproben verwenden im [historischen Experimentharness](https://github.com/Ullmann27/device-ai/blob/30cd3a89810ed14fa84eca95236d3177e016118d/experimental/weight-space-20261104/LmWeightExperiment.kt) deterministische Generierung. Die Rohlogs enthalten jedoch **keine EOS-Wahrscheinlichkeiten, Logits, Margins oder Zwischencheckpoints**. Daraus lässt sich keine genaue Optimierer-, Gradienten-, Daten- oder EOS-Trainingsursache bestimmen.

Eine weitere Messgrenze folgt aus [LmModel.kt / LmEval.segmentBits](https://github.com/Ullmann27/device-ai/blob/30cd3a89810ed14fa84eca95236d3177e016118d/app/src/main/java/at/heinz/deviceai/learning/lm/LmModel.kt): Das abschließende EOS-Ziel wird für die BPB-Auswertung auf `-1` gesetzt und damit ausgelassen. BPB bewertet dadurch die Textvorhersage; es ist kein direkter Test des korrekten Beendigungszeitpunkts. Ein BPB-Wert allein kann sofortiges EOS nicht ausschließen.

**Abgegrenzte Folgerung:** Der zusätzliche Abbruch wird nach der kurzen Fortsetzung in beiden kleinen Kontrollen und beiden Erweiterungen beobachtet. Eine ausschließlich durch Breitenvergrößerung verursachte Störung ist damit nicht belegt, weil die kleine Kontrolle dasselbe Verhalten zeigt. Ebenso wenig belegen die Logs eine statistisch verallgemeinerbare Aufgabenleistung. Der Prüfer bestätigt nur ihre innere Konsistenz, nicht ihre Authentizität oder die Bytes fehlender Checkpoints.

## Reproduzieren

Aus dem Repository-Stamm; benötigt werden Python 3, Git und der angegebene historische Commit. Die Befehle starten keine JVM und keinen Trainingslauf.

```bash
python3 experimental/weight-space-20261104/eos_diagnostics.py --repo . > /tmp/continuum-eos-audit.json
EOS_AUDIT_REPO="$PWD" PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s experimental/weight-space-20261104/tests -p test_eos_diagnostics.py -v
```

Die Prüfung liest immer Git-Blobs des expliziten vollständigen Commit-SHA; ein fortgeschrittener Branch oder lokale Dateiänderungen verändern die Eingabebasis nicht. Die CLI schreibt ausschließlich JSON auf stdout und meldet widersprüchliche oder unvollständige Belege mit Exit-Code 1. `log_consistency=PASS` bedeutet ausschließlich konsistente Logangaben, keinen bestandenen Modellqualitäts- oder Kapazitätsgate. Das historische Kapazitätsergebnis bleibt in allen vier Fällen `false`.

**Validierung:** Neun Tests einschließlich der tatsächlichen historischen Regression bestanden. Die Negativkontrollen prüfen fehlende und doppelte Trials, falsche Nullausgaben oder TTFT-Angaben, unberechtigte Kapazitäts-Pass-Claims, inkonsistente Zählungen, Quellen-/Checkpoint-/Tokenizerwechsel, falsche Probe-Scores, unvollständige Trainingsschritte, nicht endliche NLL-Werte und doppelte TSV-Feldnamen. Ein leerer TIME-Abbruch wird ausdrücklich nicht als EOS gezählt. Ohne `EOS_AUDIT_REPO` wird nur der historische Git-Regressionstest übersprungen; die synthetischen Kontrollen bleiben ausführbar. Der gespeicherte vollständige Testlauf steht in [eos-diagnostics-tests.txt](eos-diagnostics-tests.txt).

## Übergabe an die Trainingsdiagnose

[PR #508](https://github.com/Ullmann27/device-ai/pull/508) besitzt bereits den neuen Kotlin-Diagnoseharness. Der nächste zweckmäßige, dort zu protokollierende Vergleich ist eine identische kleine Kontrollfortsetzung mit Beobachtungen vor dem Training und nach 1, 4 und 16 Schritten: EOS-Wahrscheinlichkeit und Abstand zum stärksten Nicht-EOS-Token am tatsächlichen Antwortbeginn, gemeinsam mit ungefilterter Ausgabe und Aufgabenrichtigkeit auf frischen Entwicklungsaufgaben. Das trennt einen bereits geerbten Abbruch von einer erst während der Fortsetzung verschobenen Entscheidung. Diese Empfehlung rechtfertigt weder EOS-Unterdrückung als Qualitätsnachweis noch Tuning am versiegelten Testsplit. Hier wurde dieser Versuch nicht ausgeführt.

Kompatibilität und Rücknahme: ausschließlich neue Python-, Test- und Evidenzdateien; keine produktiven Laufzeit- oder Modelländerungen. Rücknahme durch Entfernen dieser neuen Dateien. Keine Promotion oder Auswahländerung.
