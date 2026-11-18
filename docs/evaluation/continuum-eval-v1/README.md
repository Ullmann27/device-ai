# Continuum-Evaluation v1 (Code63, 17.11.2026)

Reproduzierbare Bewertungsgrundlage für das KI-Gehirn. Alle Fälle sind synthetisch; es sind keine
privaten Nutzerdaten enthalten und es dürfen ohne ausdrückliche Zustimmung keine aufgenommen werden.

## Dateien

| Datei | Fälle | Ausführung | Zweck |
|---|---|---|---|
| `regression.jsonl` | 40 | **Host, automatisch** (`ContinuumEvalDatasetTest`) | deterministische Pfade: Werkzeugwahl, Negation/Kontrast, Objektgedächtnis mit Korrekturen, Geräteuhr, bestätigte Erinnerung, Sprachsteuerbefehle |
| `regression-voice.jsonl` | 32 | **Host, automatisch** (`ContinuumEvalDatasetTest`) | Sprachrunde: Gesprächsregister, Satzende-Erkennung, Fahrmodus-Befehle, Benachrichtigungs-Ansagen (Codes, Links, Freigabestufen) |
| `model-dev.jsonl` | 34 | **Gerät, lokales Modell** – NOT_RUN | Entwicklungs-/Abstimmungssatz für Prompt-, Kontext- und ggf. Adapteranpassung |
| `model-holdout.jsonl` | 34 | **Gerät, lokales Modell** – NOT_RUN | fester Abschlusstest; darf nie für Training, Prompt-Abstimmung oder Fehlersuche verwendet werden |
| `model-holdout.sha256` | – | Host | Prüfsumme des Holdouts; eine Änderung lässt den Test fehlschlagen |

## Schema

Regression (`regression.jsonl`), ein JSON-Objekt je Zeile:

- `id`, `category`, `note`
- `runner`: `route` · `object_state` · `clock` · `owner_recall` · `voice_command`;
  in `regression-voice.jsonl` zusätzlich `register` · `turn_completion` · `drive_command` · `notification`
- Eingabe je Läufer: `input` bzw. `turns` + `question`, `facts`, `now` + `zone`
- `expect` (formales Kriterium): `computer` (`READY:<APP>:<ZIEL>` oder `NOT_READY`), `tool` (Name oder `null`),
  `forbidden_apps`, `contains`, `not_contains`, `abstain`, `command`

Modellfälle (`model-*.jsonl`):

- `id`, `category`, `turns` (`role` = `user`/`assistant`, letzter Zug immer `user`), `rubric`
- `expect.type`: `contains_all` · `contains_any` · `numeric` · `abstain` · `asks_back` · `tool_call` · `no_tool`
- `expect.values`, `expect.tool`, `expect.forbidden_tools`

Kategorien (jede in Dev und Holdout mindestens einmal): `long_conversation`, `dialect`, `ambiguity`, `pronoun`,
`back_reference`, `correction`, `negation`, `temporal`, `multi_step`, `math`, `document`, `tool_choice`,
`interruption`, `incomplete_info`, `uncertainty_abstention`, `contradiction`, `abort_resume`.

## Gemessener Stand (Host, 17.11.2026)

Dieselben 40 Regressionsfälle, einmal gegen die unveränderten Klassen der Integrationsbasis
(`3613a23a7` + Cinema63-Merge `a2bb26fa6`), einmal gegen den Code63-Arbeitsstand:

| Stand | bestanden | fehlgeschlagen |
|---|---|---|
| vorher | 31 / 40 | `REG-CON-001…003`, `REG-TOOL-002`, `REG-COR-001…003`, `REG-AMB-001`, `REG-PRO-001` |
| nachher | 40 / 40 | – |

Das ist ein Vergleich deterministischer Pfade, **keine** Aussage über die Antwortqualität des lokalen Modells.

Für `regression-voice.jsonl` gibt es keinen Vorher-Wert: die geprüften Bausteine existierten vorher nicht.
Stand: 32 / 32.

## Kennzahlen für die Gerätemessung (alle NOT_RUN)

Je Lauf werden Modellkennung, Modelldatei-SHA-256, Kontextfenster, Backend und App-Commit protokolliert.

| Kennzahl | Definition |
|---|---|
| Auftragsinterpretation | Anteil bestandener Fälle je Kategorie (Kriterium `expect`) |
| Recall-Genauigkeit | `back_reference`, `long_conversation`: `contains_all` erfüllt |
| Korrektur-/Widerspruchsbehandlung | `correction`, `contradiction`, `ambiguity` |
| Tool-Selection-Genauigkeit | `tool_call`: richtiges Werkzeug **und** kein verbotenes; `no_tool`: keines |
| Halluzinationsrate | `abstain`/`asks_back`-Fälle mit inhaltlicher Behauptung statt Enthaltung/Rückfrage |
| Ausgabevollständigkeit | Anteil Antworten ohne Abbruch durch das Ausgabelimit |
| Latenz | P50/P95 bis zum ersten Token und bis zum Antwortende, Tokens pro Sekunde |
| Ressourcen | maximaler RSS, Thermalstatus-Verlauf, Akkuverbrauch je 10 Fälle |

## Regeln

1. Der Holdout wird nicht bearbeitet. Neue Fälle oder Korrekturen erzeugen `continuum-eval-v2`; Ergebnisse
   verschiedener Versionen werden nicht miteinander verglichen.
2. Kein Fall des Holdouts darf in Trainingsdaten, Few-Shot-Beispielen oder Prompt-Abstimmung vorkommen
   (der Test prüft die Überschneidungsfreiheit der Nutzeräußerungen zwischen Dev und Holdout).
3. Eine Verbesserung gilt nur mit Vorher-Nachher-Lauf auf demselben Gerät, demselben Modell und derselben
   Datensatzversion als belegt.

## Abgrenzung: was „Lernen“ hier heißt

| Stufe | Was sich ändert | Stand Code63 |
|---|---|---|
| A Prompt-/Kontextoptimierung | Instruktionen, Kontextaufbau, Werkzeugverträge | geändert (ältere Nutzeraussagen, Kontrastauflösung) |
| B Langzeitgedächtnis | gespeicherte, abrufbare Nutzerangaben | vorhanden; Sitzungs-Objektgedächtnis korrigiert |
| C Präferenzanpassung | ausdrücklich freigegebene Einstellungen | vorhanden (u. a. Zuhörfenster, Leistungsprofil neu) |
| D Fine-Tuning | **Modellgewichte** | **nicht durchgeführt** |

Für Stufe D (z. B. LoRA/QLoRA auf Gemma-Basis) fehlen derzeit belegt: ein ausreichend großer, freigegebener
Trainingssatz (die 34 Dev-Fälle sind ein Mess-, kein Trainingssatz), GPU-Rechenzeit außerhalb des Telefons,
die Lizenzprüfung der Gemma-Nutzungsbedingungen für abgeleitete Gewichte und ein geprüfter Weg, einen Adapter
in das auf dem Gerät verwendete `.litertlm`-Format zu überführen. Ohne Gerätemessung der Ausgangslage ist
zudem nicht belegt, dass Fine-Tuning der wirksamste nächste Schritt wäre.

## Messlauf am Gerät (Code64)

Die App enthält den Entwicklungs- und den Holdout-Satz unverändert als Assets (`continuum-eval-v1/`; ein Test
prüft die Byte-Gleichheit mit diesem Ordner). Gestartet wird der Lauf im Modellzentrum:

- **4 · EVALUATION · ENTWICKLUNGSSATZ** – beliebig oft, zum Abstimmen.
- **5 · EVALUATION · HOLDOUT** – nur für den Abschlussvergleich; Ergebnisse nicht zum Abstimmen verwenden.

Umfang des Laufs (steht auch in jedem Bericht als `scope`): Rohmodell mit dem Gespräch in einem Prompt, ohne
Continuum-Gedächtnis, ohne deterministische Pfade, ohne Werkzeuge. Werkzeugwahl-Fälle werden deshalb als
„nicht bewertbar“ geführt und sind über `regression*.jsonl` am Host abgedeckt.

Der Bericht (`Download/Continuum/continuum-eval-<satz>-<zeit>.json`) enthält ausschließlich Werte, die während
des Laufs auf dem Gerät gemessen wurden:

| Feld | Bedeutung |
|---|---|
| `meta` | Modellkennung, Modell-SHA-256, Dateigröße, gemeldetes Kontextfenster, Rechenthreads, App-Version, Gerät, Android-SDK, Energiesparmodus |
| `formal` | bestanden / nicht bestanden für `contains_all`, `contains_any`, `numeric` |
| `heuristic` | bestanden / nicht bestanden für `abstain`, `asks_back` (am Wortlaut erkannt, stichprobenartig gegenlesen) |
| `notScored` | Werkzeugwahl-Fälle |
| `latencyMs` | Median und 95 %-Wert der Gesamtzeit und der Zeit bis zum ersten Text |
| `tokensPerSecondMean` | nur wenn die Laufzeit Ausgabe-Tokens meldet, sonst `null` |
| `inputTokensMax` | größte von der Laufzeit gemeldete Prompt-Tokenzahl |
| `outputLimitReached` | Antworten, die am Ausgabelimit endeten |
| `thermalStatus`, `batteryPercent`, `availableRamBytesMin`, `jvmHeapUsedBytesMax` | Android-Messwerte während des Laufs |
| `results[]` | je Fall: Urteil, Begründung, Antwort (gekürzt), Zeiten, Tokenzahlen, Thermalstatus davor/danach |

Der Lauf endet von selbst bei kritischer Gerätewärme (`THERMAL_CRITICAL`) und kann mit Stopp abgebrochen werden;
ein dabei laufender Fall wird nicht gezählt.

### Protokoll für einen Modellvergleich

1. Gerät: Akku über 50 %, nicht am Ladegerät, Energiesparen aus, fünf Minuten abgekühlt, Continuum im Vordergrund.
2. Modell A laden, Entwicklungssatz laufen lassen, Bericht sichern. Fünf Minuten Pause. Wiederholen (zwei Läufe je
   Modell, um Streuung zu sehen).
3. Dasselbe mit Modell B bzw. einer anderen Quantisierung – gleiche App-Version, gleiche Reihenfolge.
4. Erst wenn die Entscheidung auf dem Entwicklungssatz gefallen ist: je Modell genau ein Holdout-Lauf.
5. Ein Modellwechsel ist nur begründet, wenn das neue Modell im Holdout formal besser ist **und** bei
   Antwortzeit, Thermalstatus und Speicher nicht schlechter abschneidet.

Stand: kein Gerätelauf durchgeführt (NOT_RUN). Es gibt deshalb weder einen Vergleich von Modellen oder
Quantisierungen noch eine Aussage, ob Fine-Tuning sinnvoll wäre.
