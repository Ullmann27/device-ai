# Forschungsrunde 2: nutzbare lokale Erfahrung und Kosten des vorhandenen Recall

**4. November 2026 · RESEARCH_SHADOW · Produktbaseline `e101e393dd6c80803ac9d9ac91fccd9812c4a18d`.**

## Ergebnis

Ein echter neuer Modell-Reparaturauftrag lässt sich in dieser Umgebung derzeit nicht belegen. Die unveränderte Produktionsfunktion `queryPinnedLocalCoding` liefert bei der vorhandenen Konfiguration tatsächlich **`BLOCKED_LOCAL_MODEL_UNAVAILABLE`**, bevor sie eine Aufgabe oder einen Modellprozess ausführt. Fünf Pflichtangaben zum Modellruntime fehlen. Es wurde kein Besitzerauftrag gestartet; die historische S01-Sperre wurde weder aufgehoben noch wiederholt. [E1]

Die verbleibende Zeit wurde für einen **neuen** Versuch genutzt: Auswahlverhalten und Laufzeit der echten Funktion `recallTechnicalExperiences` bei **0, 32 und 128 synthetischen Erfahrungen**, ergänzt durch eine Einzelkontrolle mit einem positiven Treffer. Die alten 51 Tests und neun Probes wurden nicht erneut als Fortschritt gezählt.

Der neue konkrete Befund lautet: **In diesem Datensatz passen zwei vollständige Erfahrungen in das 4.096-Zeichen-Budget. Mit dem vorhandenen API-Parameter `limit=2` statt `limit=3` lieferte Recall bytegleichen Text, war bei 128 Erfahrungen aber im Median in 15,34 statt 65,96 ms fertig.** Das Verhältnis der Mediane beträgt **4,30**. Keine Gateprüfung und keine Implementierung wurde geändert. Es handelt sich um eine Messung der Auswahl- und Prüfkosten, nicht um schnellere Modellinferenz oder bessere Reparaturen. [E2]

Das ist **kein Vorschlag für eine pauschale Produktumstellung auf zwei Einträge**: Bei kürzeren Erfahrungen könnten drei Einträge in denselben Kontext passen. Der Versuch identifiziert eine konkrete Stelle für weitere Forschung an der bereits vorhandenen Pipeline.

## 1. Verfügbarkeit eines echten lokalen Modellpfads

Die kurze Inventur prüfte ausschließlich vorhandene lokale Voraussetzungen:

| Prüfung | Beobachtung |
|---|---|
| Ausführbare Programme im `PATH` | Node, Python, Java und `bwrap` vorhanden; `llama-cli`, `llama-completion`, `llama-server`, Ollama, LiteRT-LM und `adb` nicht gefunden. |
| Aktuelle Umgebung | Keine Variablennamen für CONTINUUM, LLAMA, GEMMA, OLLAMA oder Modell-/Workerhost vorhanden. Es wurden keine Variablenwerte veröffentlicht. |
| Benannte Dateien unter `/workspace/scratch` | Keine `.gguf`, `.litertlm`, `.safetensors` oder der gezielt gesuchten llama-Binaries gefunden; `rg` endet mit dem dokumentierten Kein-Treffer-Code 1. |
| Sichtbare Prozessnamen beim Messzeitpunkt | `codex`, `python`, `ps`; kein erkennbarer Modell- oder Workerprozess. Das ist eine Momentaufnahme. |
| Produktions-Preflight | `BLOCKED_LOCAL_MODEL_UNAVAILABLE`; Aufrufdauer innerhalb der Funktion etwa 0,62 ms im ersten Lauf. Keine Modellinvokation. |
| Aktueller legitimierter Besitzer-/Workerauftrag | In dieser Forschungsumgebung nicht bereitgestellt oder ausgeführt. Historische Testzustände und deren Schlüssel werden nicht als aktuelle Autorisierung verwendet. |

Die fünf fehlenden Pflichtangaben lauten:

```text
CONTINUUM_LOCAL_BRAIN_MODEL_PATH
CONTINUUM_LOCAL_BRAIN_RUNTIME_PATH
CONTINUUM_LOCAL_BRAIN_MODEL_MANIFEST
CONTINUUM_LOCAL_BRAIN_RUNTIME_SHA256
CONTINUUM_LOCAL_BRAIN_RUNTIME_KIND
```

`runtime_preflight.mjs` ruft die Produktionsfunktion nur dann auf, wenn mindestens eine dieser Voraussetzungen fehlt. Am gepinnten Quellstand wird dieser Fall vor Bindungsprüfung und Runtimeausführung abgewiesen. Bei vollständig vorhandener Konfiguration führt das Hilfsskript ausdrücklich **keinen** Modelllauf aus, sondern meldet den Bedarf einer gesonderten zugelassenen Ausführung. Es ist kein Worker und erteilt keine Besitzerfreigabe. [S1, E1]

Die Inventur ist kein vollständiger Nachweis, dass auf jedem möglichen Dateipfad der Maschine kein Modell existiert. Der kleine eigenständige LM1-Kern aus dem anderen Forschungsbereich ersetzt außerdem keinen zugelassenen Coding-Runtime-Nachweis. Netzwerkverbindungen, Ports, privilegierte Zugriffe und Downloads wurden nicht versucht.

Der von Root übermittelte Status von PR #510 bei `2a364005b52e3240eb799f86aea31be90b390b10` lautet `LIVE_ACTIVATION_BLOCKED`. Dieses Commitobjekt war im übergebenen lokalen Repository nicht vorhanden; der Bericht kennzeichnet den PR-Status deshalb als Handoff, nicht als eigene erneute Verifikation. Der unabhängige neue Befund dieser Runde ist der tatsächlich ausgeführte Preflight auf der Produktbaseline. Weder #510 noch #506 wurden in die Baseline übernommen. [E1]

## 2. Was genau gemessen wurde

### Unveränderter Code und neue Daten

Der Runner liest `.github/continuum` und `scripts` unmittelbar aus dem gepinnten Git-Objekt mittels `git archive`. Er verwendet deshalb weder den inzwischen um Forschungsdateien ergänzten Index noch einen möglicherweise abweichenden Arbeitsbaum. **681 Dateien** wurden in das eigene Experimentverzeichnis rekonstruiert und vor/nach der Ausführung gehasht; alle blieben unverändert. Es wurde kein Checkout, Index oder Ref des Quellrepositorys geschrieben. [E3]

Der Versuch importiert aus dieser Kopie insbesondere `ExperienceLog`, `DurableTaskJournal`, die Envelope-/Task-Prüfungen und `recallTechnicalExperiences`. Die produktiven Implementierungen und ihre Grenzen bleiben unverändert.

Die synthetischen historischen Erfahrungen verteilen sich gleichmäßig über vier Gruppen:

| Gruppe | Quellkennung | Erlaubte Datei |
|---|---|---|
| 0 | A = Produktbaseline | `src/math.mjs` |
| 1 | A = Produktbaseline | `src/text.mjs` |
| 2 | B = synthetische 40-stellige SHA-Kennung | `src/math.mjs` |
| 3 | B = synthetische 40-stellige SHA-Kennung | `src/text.mjs` |

Für jede Abfrage existieren ein eigenes synthetisches kanonisches Grant und ein authentifiziertes OWNER-GOAL im ausschließlich lokalen Fixture-Verzeichnis. Der Abruf bekommt `project:null`. Er prüft hier **exakte Quellkennung**, nicht tatsächliche Git-Abstammung. B und die negative Abfragekennung C behaupten keine vorhandenen Git-Commits.

**Alle historischen technischen Nachweise sind CONTRACT_TEST-Fixtures.** Sie werden von der Versuchsroutine mit wegwerfbaren, öffentlich herleitbaren Testschlüsseln signiert, entsprechend dem Grundprinzip der vorhandenen Vertragstests. Die Felder `LOCAL_MODEL_HOST`, `verifiedModelExecution: true` und synthetische RED/GREEN-Daten dienen ausschließlich der kontrollierten Eingabe in den Lesepfad. Sie stammen nicht von einem Modell und werden nicht als reale Beobachtungen ausgegeben. Die Aufnahme über den live gebrandeten Producer `recordTechnicalExperience` wurde **nicht** durchgeführt. Kein produktiver Zustand, Besitzerauftrag oder bestehender Schlüssel wird verändert.

### Bedingungen und Messverfahren

Der erste Lauf untersuchte elf Bedingungen mit je 20 Messungen. Er zeigte, dass bei großem Bestand zwei Einträge ausgegeben werden, obwohl `limit=3` erlaubt ist. Deshalb folgte ein eng begrenzter zweiter Lauf mit zwei zusätzlichen Bedingungen: identische Ein-Datei-Abfrage, jetzt `limit=2`, bei 32 und 128 Erfahrungen.

Der abschließende Lauf enthält **13 Bedingungen × 20 = 260 gemessene Aufrufe**, zusätzlich **39 Aufwärmaufrufe** und eine einzelne positive Kontrolle. Die Reihenfolge der Bedingungen rotiert je Messrunde. Pro Aufruf werden reale Wandzeit, Prozess-CPU-Zeit, zurückgegebene IDs, Textlänge und Text-SHA-256 protokolliert. Einträge müssen aus der vorab bekannten zulässigen Menge stammen und deren neueste passende Folge bilden. Größen- und Anzahlgrenzen werden bei jedem Aufruf geprüft. Alle Prüfungen bestanden. [E2]

Die Angaben sind wiederholte Latenzmessungen innerhalb eines Hostprozesses. Die Aufrufe sind keine 260 unabhängigen Softwarefehler und keine 260 bewiesenen Fähigkeiten. Betriebssystem-Caches, JIT, Garbage Collection und andere Hostlast wurden nicht isoliert. p95 ist der empirische 95-Prozent-Quantilwert der jeweils 20 Aufrufe, kein Konfidenzintervall und keine Gerätegarantie.

## 3. Abschließende Messwerte

Abschließender Lauf: **2026-11-04 ab 19:16:15 UTC**, Node **v24.22.1**, Linux x86_64. Die gesamte Vorbereitung und Messung dauerte **12,360 s**, davon der Node-Versuch **11,715 s**. Der vorhergehende Erkundungslauf bleibt vollständig erhalten. [E2, E3]

| Bestand | Abfrage | Vorab passende Erfahrungen | Ausgabe | Median ms | p95 ms |
|---:|---|---:|---:|---:|---:|
| 0 | Leeres Ledger | 0 | 0 | 0,50 | 0,60 |
| 32 | Eine passende Datei, Limit 3 | 8 | 2 | 18,01 | 19,41 |
| 32 | Dieselbe Datei, Limit 2 | 8 | 2 | **6,57** | 9,17 |
| 32 | Zwei passende Dateien, Limit 3 | 16 | 2 | 29,79 | 34,66 |
| 32 | Unbekannte Datei | 0 | 0 | 6,18 | 6,53 |
| 32 | Unbekannte Quellkennung | 0 | 0 | 15,63 | 18,55 |
| 32 | Passende Datei, nur 128 Zeichen | 8 | 0 | 16,71 | 20,40 |
| 128 | Eine passende Datei, Limit 3 | 32 | 2 | 65,96 | 76,55 |
| 128 | Dieselbe Datei, Limit 2 | 32 | 2 | **15,34** | 16,16 |
| 128 | Zwei passende Dateien, Limit 3 | 64 | 2 | 110,62 | 124,87 |
| 128 | Unbekannte Datei | 0 | 0 | 21,77 | 25,13 |
| 128 | Unbekannte Quellkennung | 0 | 0 | 61,16 | 73,57 |
| 128 | Passende Datei, nur 128 Zeichen | 32 | 0 | 68,90 | 73,33 |

Standardmäßig beträgt das Textbudget 4.096 Zeichen. Die Zeilen mit 128 Zeichen verändern nur diesen erlaubten Parameter; ein unvollständiger Nachweis wird nicht ausgegeben. Die positive Einzelkontrolle liefert genau einen Eintrag. [E2]

### Vergleich des vorhandenen Anzahlparameters

| Bestand | Limit 3: Median | Limit 2: Median | Verhältnis der Mediane | Identität der Ausgabe |
|---:|---:|---:|---:|---|
| 32 | 18,010 ms | 6,574 ms | 2,74 | Alle 20 zugeordneten Text-Hashes und ID-Folgen gleich |
| 128 | 65,959 ms | 15,344 ms | 4,30 | Alle 20 zugeordneten Text-Hashes und ID-Folgen gleich |

Die Ausgabe misst 3.372 Zeichen bei Bestand 32 und 3.374 Zeichen bei Bestand 128. In diesen Fixtures passt ein dritter vollständiger Eintrag nicht mehr hinein. Die vergrößerte erlaubte Anzahl liefert daher keine zusätzliche Information, verursacht aber weitere Arbeit. Der Versuch mit zwei Einträgen benutzt nur einen bereits vorhandenen Parameter; es wurde kein Short-Circuit in die Produktionsimplementierung eingebaut. [E2]

## 4. Interpretation und konkrete Grenzen

**Auswahlgrenzen funktionieren in den neuen Daten.** Unbekannte Dateien und fremde Quellkennungen liefern keinen Treffer. Die positiven Kontrollen zeigen gleichzeitig, dass diese Nullergebnisse nicht auf einen generell abgeschalteten Abruf zurückgehen. Die ausgegebenen Erfahrungen bleiben im Typ `TECHNICAL_EXPERIENCE_REVIEW_ONLY`; sie verleihen keine Erlaubnis und werden nicht als Besitzerfakten verwendet.

**Das Ausgabeformat begrenzt den praktisch nutzbaren Umfang.** Die deklarierte Obergrenze von drei Einträgen bedeutet nicht, dass drei vollständige Provenienzobjekte in 4.096 Zeichen passen. Ein kleineres Zeichenbudget kann trotz vorhandener geeigneter Daten zu vollständig leerem Kontext führen. Die Beibehaltung vollständiger Nachweise ist eine beobachtete Schutzfunktion; die Frage ist, wie viel Arbeit ein solcher erfolgloser Abruf verursacht.

**Der Code erklärt die unterschiedliche Arbeit plausibel.** `recallTechnicalExperiences` liest und prüft zunächst das Ledger, betrachtet anschließend die Einträge rückwärts und prüft bei passenden Dateien die kanonischen Nachweise. Die exakte Quellgleichheit ohne Projekt wird erst nach diesen Nachweisprüfungen ausgewertet. Das Textbudget wird nach dem Aufbau eines Ergebniseintrags geprüft; bei Überschreitung läuft die Schleife mit `continue` weiter. Abgebrochen wird, wenn die Zahl ausgegebener Einträge `limit` erreicht. Deshalb kann der Fall „zwei passen, drei sind erlaubt“ viele weitere Nachweise prüfen. Dies ist eine aus dem Quellcode abgeleitete Erklärung, keine separat instrumentierte Zählung jeder internen Dateioperation. [S2]

**Kein allgemeiner Faktor 4,30.** Das gemessene Verhältnis gilt nur für den bezeichneten Datensatz, den gepinnten Code, dieses Zeichenbudget und diesen Hostlauf. Bei drei kürzeren Erfahrungen wäre `limit=2` ein anderer Informationsumfang. Auch die Änderung der Anfrage von einer auf zwei Dateien erhöht hier die Anzahl technisch zu prüfender Kandidaten; daraus folgt keine Qualitätsaussage über die zusätzliche Erfahrung.

**Die größte offene Frage bleibt unverändert:** Ob echte lokale Modellreparaturen mit solchen Erfahrungen häufiger gelingen. Keine dieser Messungen führt Gemma aus, verändert Gewichte oder entscheidet selbständig über einen produktiven Patch. Ein Modell- oder Fold7-Geschwindigkeitsfaktor lässt sich daraus nicht ableiten.

## 5. Nächster belastbarer Schritt

Sobald ein vorhandener, zugelassener lokaler Coding-Runtime mit gepinnten Modell-/Runtime-Dateien, Manifest und einem neuen gültigen Besitzerauftrag erreichbar ist, folgt genau ein begrenzter Fall über die bestehende Pipeline:

1. Frischer isolierter Quellstand und tatsächlich fehlschlagender Ausgangstest.
2. Modellaufruf über den regulären Producer mit beobachteter Runtimeidentität.
3. Patchprüfung und unabhängige RED/GREEN-Nachprüfung im bestehenden Reparaturweg.
4. Aufnahme über `recordTechnicalExperience`, ohne manuell erzeugte Modellbeobachtung.
5. Neuer Prozess und neuer zulässiger Auftrag; technischer Recall der aufgenommenen Erfahrung.

Die historische S01-Ausführung wird dafür nicht wiederverwendet. Ein neuer Auftrag benötigt seine eigenen gültigen Bindungen und Budgets. Eine künstlich erstellte Fixture wie in diesem Versuch ist kein Ersatz für diesen Nachweis.

Für die begleitende Effizienzforschung ist nun ein konkreter Vergleich definiert: echte aufgenommene Nachweise unterschiedlicher Länge, unveränderte 4.096-Zeichen-Grenze, vorhandenes `limit` 2 und 3; neben der Latenz immer die tatsächlichen IDs und Text-Hashes vergleichen. Nur wenn beide Bedingungen dieselbe Information liefern, darf die Laufzeitdifferenz als vermiedene Arbeit eingeordnet werden. Wenn drei kurze Nachweise passen, muss deren möglicher Nutzen separat gemessen werden. Dafür ist zunächst keine neue Architektur erforderlich.

## 6. Reproduktion und Dateiauswahl

Voraussetzungen: vorhandenes Python, Node, Git und `rg`; eine lokale Git-Objektdatenbank mit der Produktbaseline. Es gibt keinen Download. Die Git-Arbeitskopie darf bereits Forschungsdateien im Index haben, weil ausschließlich das gepinnte Objekt gelesen wird.

```bash
python /absoluter/pfad/autonomy/run_experiment.py \
  --repo /absoluter/pfad/zur/lokalen-git-objektdatenbank
```

Der Runner erzeugt pro Aufruf ein neues Verzeichnis unter seinem eigenen `runs/`. Die ursprüngliche Messung verwendete:

```bash
python research/round2/autonomy/run_experiment.py \
  --repo /workspace/scratch/9d2785b5ab4e/continuum-research
```

Die jeweils tatsächlich ausgeführten Programmversionen sind als `scripts-snapshot` im Laufverzeichnis erhalten und gegen die Hashes der jeweiligen `measurements.json` prüfbar. Der aktuelle Runner kennzeichnet einen nicht ausgeführten echten Modellfall allgemein mit `NOT_RUN` und gibt den tatsächlichen Preflightstatus getrennt an; die beiden protokollierten Messläufe verwendeten noch das gleichbedeutende, auf den damaligen Block zugeschnittene Feld `NOT_RUN_BLOCKED_LOCAL_MODEL_UNAVAILABLE`.

`latest-run.txt` zeigt auf den abschließenden Lauf. Die `DELIVERABLES.json` enthält ausschließlich Berichte, Forschungsprogramme, Hash-/Provenienzmanifeste und JSON-/Textlogs. Die rekonstruierten Quellkopien unter `pinned-source` sind lokale Zwischenprodukte und nicht Teil dieser Veröffentlichungsauswahl. Temporäre Fixture-Zustände werden nach dem Node-Versuch entfernt.

### Primäre Implementierungsquellen

- **[S1]** [Gepinnter Produktionspfad und Preflight](https://github.com/Ullmann27/device-ai/blob/e101e393dd6c80803ac9d9ac91fccd9812c4a18d/scripts/continuum-local-model-observation.mjs), insbesondere `queryPinnedLocalCoding`.
- **[S2]** [Gepinnter technischer Recall](https://github.com/Ullmann27/device-ai/blob/e101e393dd6c80803ac9d9ac91fccd9812c4a18d/.github/continuum/ccc1-technical-recall.mjs).
- **[S3]** [Experience-Ledger](https://github.com/Ullmann27/device-ai/blob/e101e393dd6c80803ac9d9ac91fccd9812c4a18d/.github/continuum/ccc1-experience.mjs) und [kanonisches Journal](https://github.com/Ullmann27/device-ai/blob/e101e393dd6c80803ac9d9ac91fccd9812c4a18d/.github/continuum/ccc1-journal.mjs).

### Rohbelege

- **[E1]** `runs/20261104T191459157686Z/availability.json` und `preflight.stdout.json`; ebenso im Abschlusslauf erneut erfasst. Enthält Namensinventur, tatsächlichen Preflightblock und Grenzen der Prüfung.
- **[E2]** `runs/20261104T191615417409Z/recall.stdout.json`: 13 Bedingungen, alle 260 Rohmessungen, 39 Warmups, positive Kontrolle, Auswahlprüfungen und Paarvergleich.
- **[E3]** `runs/20261104T191615417409Z/measurements.json` und `source-manifest.json`: Befehle, reale Laufzeiten, Scriptversionen und Hashvergleich der 681 rekonstruierten Dateien.
- **[E4]** `runs/20261104T191459157686Z/recall.stdout.json`: vorangegangener elfteiliger Erkundungslauf, vollständig erhalten; seine Laufzeiten wurden nicht mit dem Abschlusslauf vermischt.

Es wurden keine neuen globalen Literaturbehauptungen aufgestellt. Die in Runde 1 belegte Unterscheidung zwischen episodischem Kontext und Gewichtslernen bleibt maßgeblich; diese Runde ergänzt sie durch neue Messungen am tatsächlichen Abrufcode.
