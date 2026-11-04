# Lokale Eigenständigkeit: bestehendes CCC1-Erfahrungsgedächtnis

**Status: RESEARCH_SHADOW · CPU-Host-Vertragsprüfung · 4. November 2026.**

## Ergebnis

Die bestehende CCC1-Architektur enthält bereits einen lokal nutzbaren Weg von abgeschlossenen Aufgaben zu dauerhaft gespeicherten Erfahrungen und einem begrenzten, quellengebundenen Abruf. Eine zusätzliche Gedächtnisdatenbank oder ein neuer Orchestrator ist dafür nicht erforderlich. Der Abruf ist im vorhandenen Coding-Orchestrator an den Modellkontext angeschlossen. Diese Integration wurde am Quellcode nachvollzogen; ihre Wirkung auf echte Modellreparaturen wurde hier **nicht** ausgeführt.

Die unveränderten gezielten Node-Tests bestanden: **7 von 7** Prüfungen zur Dauerhaftigkeit und **44 von 44** Prüfungen zum technischen Abruf. Ein zusätzlicher unabhängiger Versuch mit neun Prüffällen bestätigte die Wiederherstellung in einem tatsächlich neuen Node-Prozess und die Trennung zwischen einem lesbaren Erfolgseintrag und einem zum Abruf zugelassenen technischen Nachweis. Alle neun Erwartungen wurden beobachtet. Die drei Läufe dauerten zusammen **6,896 Sekunden** auf diesem Linux-Host; das ist eine einmalige Laufzeitmessung, kein Leistungsbenchmark für Android oder Gemma. [M1–M4]

**Die wichtigste Einschränkung ist positiv nachgewiesen:** Die Hash-Kette des allgemeinen Experience-Ledgers allein authentifiziert keine technische Wahrheit. Ein bearbeiteter Patch-Hash mit neu berechneter Hash-Kette wurde vom Ledger gelesen. Er verschaffte dem allgemeinen Eintrag ohne technischen Nachweis jedoch keine technische Abrufberechtigung. Ein manipulierter VERIFIER-Eintrag im kanonischen Aufgabenjournal scheiterte trotz neu berechneter Hash-Kette an dessen unverändertem Laufzeit-MAC. Diese Gegenprobe berechnete absichtlich keinen neuen MAC. Sie beweist keinen Schutz gegenüber einem Angreifer derselben UID, der auch den Laufzeitschlüssel und den privaten Zustand lesen oder verändern kann. [M4]

**Kein Modell wurde inferiert, trainiert oder verbessert.** Kein APK wurde gebaut, signiert oder installiert. Fold7, Termux, Energieverbrauch, Android-Hintergrundbetrieb und eine höhere Reparaturquote sind **NOT_RUN**. Der Versuch liefert Belege für lokale Persistenz- und Zulassungsmechanismen, nicht für selbständiges Lernen im Sinne verbesserter Modellgewichte oder gemessener Problemlöseleistung.

## 1. Untersuchungsgrundlage und Grenzen

Der eingefrorene Stand lautet:

```text
Repository: Ullmann27/device-ai
Baseline:   e101e393dd6c80803ac9d9ac91fccd9812c4a18d
Quellpfad:  /workspace/scratch/9d2785b5ab4e/continuum-research
Ausgaben:   /workspace/scratch/9d2785b5ab4e/research/autonomy
```

Es wurden ausschließlich Dateien im eigenen Forschungsverzeichnis geschrieben. Die elf erfassten Quell- und Testdateien hatten vor und nach dem Lauf identische SHA-256-Hashes; HEAD blieb unverändert, und `git diff --name-only HEAD` war vor und nach dem Lauf leer. Bereits vorhandene, nicht versionierte Forschungsdateien anderer Arbeiten sind in der Messdatei protokolliert, wurden aber weder geändert noch als Laufzeitcode verwendet. [M1]

Die vorhandenen Repository-Regeln wurden berücksichtigt: ein bestehendes Backlog/CCC1-System, isolierte Forschung, keine automatische Hochstufung zu Eigentümerwissen, kein Main-Merge, keine Installation und getrennte Host-/Geräteaussagen. Der heutige Forschungsauftrag erlaubt die begrenzte Untersuchung; daraus folgt keine Produktfreigabe. Der Teilversuch betrifft vor allem EXP-04/05/06/07/11/12. Die IDs sind Zuordnungen zum vorhandenen Register, keine Änderung ihrer dortigen Akzeptanzstufen.

Der neu geschriebene Versuch verwendet echte unveränderte CCC1-Implementierungen mit **synthetischen Auftrags-, Receipt- und Erfolgseinträgen**. Seine authentifizierten Journale werden ausschließlich mit einem wegwerfbaren Fixture-Schlüssel erzeugt. Ein Feld wie `execution: EXECUTED` innerhalb dieser synthetischen Testdaten ist kein Beleg für ausgeführte Modellinferenz oder eine echte Reparatur. Der Versuch ruft keine KI-API auf und verwendet keine kostenpflichtigen Runner. Eine Betriebssystem-Netzwerksperre oder ein umfassender Isolationstest wurde damit nicht nachgewiesen.

## 2. Was im vorhandenen Code bereits existiert

| Bestandteil | Tatsächliche Funktion am eingefrorenen Stand | Grenze |
|---|---|---|
| `ccc1-experience.mjs`: `experienceOf` | Leitet aus einem abgeschlossenen Aufgabenjournal eine Zusammenfassung ab: Quell-SHA, Hypothesen, Receipt-Hashes, Ergebnis, Runden und protokollierte Kosten. Offene Aufgaben ergeben `null`. | Prüft selbst keine Modellinferenz. Der Aufrufer muss das kanonische Journal zuvor prüfen. |
| `ExperienceLog` | Append-only JSONL mit Hash-Verkettung, eindeutiger Task-ID, Größenbegrenzung, Dateisynchronisation und exklusiver Schreib-Lease. | Die Hash-Kette erkennt Änderungen ohne passendes Neuhashen; sie authentifiziert keinen Schreiber. |
| `ccc1-journal.mjs`: `DurableTaskJournal` | Prüft Nachrichtenformat, Reihenfolge, Bindungen und Hash-Verkettung; OWNER, TEAMLEADER und VERIFIER tragen einen Laufzeit-HMAC. | Der Laufzeitschlüssel und der private Zustand bleiben Vertrauensgrenzen. |
| `ccc1-technical-recall.mjs`: `recordTechnicalExperience` | Verlangt zugelassene Laufzeit- und Verifier-Beobachtungen; bindet den technischen Nachweis an Aufgabe, Quelle, Receipt und Reflexion und signiert den persistierten JSON-Zustand. | Ein kopiertes `verifiedModelExecution: true` erfüllt diese Zulassung nicht. |
| `recallTechnicalExperiences` | Liest Ledger, kanonische Grants und authentifizierte Aufgabenjournale bei jedem Abruf neu. Beachtet Widerruf, Alter, Repository, Quellbezug und überlappende erlaubte Dateien. | Historische Evidenz bleibt prüfpflichtiger Kontext. Git-Abstammung beweist keine semantische Gültigkeit auf geändertem Code. |
| `coding-orchestrator.mjs` und `coding-provider.mjs` | Technischer Recall ist standardmäßig aktiviert, wird an den Provider übergeben und erscheint strukturiert im Prompt. Der Orchestrator versucht technische Erfahrungen vor der allgemeinen Zusammenfassung zu schreiben. | Der Endstatus führt weiterhin `approvedForMerge: false`, `deviceVerified: false`, `neuralTraining: false`. |

Diese Angaben stammen aus [S1–S7]. Der Abruf ist auf höchstens **drei Einträge und 4.096 Zeichen** begrenzt; Nachweise dürfen höchstens **30 Tage** alt sein. Das Ledger ist auf **4.096 Datensätze, 16 MiB Gesamtgröße und 256 KiB pro Datensatz** begrenzt. Ein kleiner Kontext oder fehlende Provenienz kann folglich einen formal erfolgreichen historischen Eintrag vom Abruf ausschließen. Das ist ein definierter Bestandteil des Mechanismus. [S1, S3]

Ein zusätzlicher Quellbefund betrifft die Reihenfolge: Die allgemeine Experience-Speicherung ist nach Task-ID einmalig. Ein zweiter, inhaltlich abweichender Eintrag derselben Aufgabe wird mit `false` abgewiesen, ohne den ersten zu ersetzen. Im unabhängigen Versuch blieben die gespeicherten Bytes unverändert. Der Coding-Orchestrator benennt den entsprechenden Fall `EXISTING_SUMMARY_NOT_UPGRADED`. Erfolgreiche alte Zusammenfassungen lassen sich daher nicht einfach durch spätere Erfolgsetiketten zu technischen Nachweisen aufwerten. [S1, S5, M4]

## 3. Methode und Reproduktion

Der vollständige Versuch ist ausführbar mit:

```bash
cd /workspace/scratch/9d2785b5ab4e
python research/autonomy/run_experiment.py --source /workspace/scratch/9d2785b5ab4e/continuum-research
```

**Bei einer abgelegten oder veröffentlichten Kopie ist `--source /absoluter/pfad/zum/eingefrorenen/checkout` ausdrücklich anzugeben.** Der im Skript vorhandene Default `HERE.parents[1] / "continuum-research"` passt ausschließlich zum ursprünglichen Scratch-Layout und ist beispielsweise nach Ablage unter `device-ai/research/autonomy` nicht der Repositorypfad. Das Quellargument muss auf einen separaten Checkout der Baseline zeigen, nicht auf den späteren Forschungs-PR mit anderem HEAD. Unabhängig vom Pfad verlangt das Skript exakt die oben genannte Baseline und verweigert einen anderen HEAD oder versionierte Quelländerungen. Der dokumentierte Ursprungslauf verwendete das damals gültige Default-Layout; der explizite Befehl oben adressiert denselben Quellpfad und denselben Code.

Das Skript benötigt Python und Node, installiert keine Abhängigkeiten und legt Ergebnisse relativ zu seinem eigenen Verzeichnis unter `results` sowie temporäre Daten unter `tmp` ab. Deshalb gehört auch seine veröffentlichte Kopie in ein eigens beschreibbares Experimentverzeichnis. Die synthetischen Dateien des unabhängigen Versuchs werden anschließend entfernt. Eine erneute Ausführung überschreibt nur die eigenen Ergebnisdateien. Die neun unabhängigen Probes lassen sich separat durch den unten dokumentierten dritten Hauptbefehl ausführen; sein drittes Argument muss auf das eigene temporäre Experimentverzeichnis zeigen.

Die tatsächlich gestarteten Hauptbefehle sind:

```text
node --test --test-concurrency=1 --test-reporter=junit .github/continuum/ccc1-experience-durability.test.mjs
node --test --test-concurrency=1 --test-reporter=junit .github/continuum/ccc1-technical-recall.test.mjs
node /workspace/scratch/9d2785b5ab4e/research/autonomy/probe.mjs /workspace/scratch/9d2785b5ab4e/continuum-research /workspace/scratch/9d2785b5ab4e/research/autonomy/tmp
```

Die ersten beiden Befehle liefen im eingefrorenen Quellverzeichnis mit umgeleitetem `TMPDIR`, `TMP` und `TEMP`. `run_experiment.py` protokolliert Start-/Endzeit, reale Dauer, Exitcode, Timeout, einzelne JUnit-Fälle und Hashes. Der Zusatzversuch lädt die Produktionsmodule direkt per Dateipfad; er ersetzt weder deren Implementierung noch deren Prüffunktionen. Ein Teil der **vorhandenen** Recall-Tests arbeitet dagegen ausdrücklich mit synthetisch signierten Nachweisen oder Testadaptern; deren erfolgreiche Ausführung wird ebenfalls nur als Vertragsprüfung gewertet. [M1–M4]

## 4. Messwerte

Messzeit: **2026-11-04 18:48:58 bis 18:49:05 UTC**. Laufzeitumgebung: Node **v24.22.1**, Python **3.12.14**, Linux x86_64. Die gemessene Summe der drei Läufe enthält Prozessstarts; der zusätzliche Zeitstempelrahmen umfasst auch Vorbereitung und Metadaten. Es gab keine Wiederholungsserie und keine Schätzung statistischer Unsicherheit. [M1]

| Lauf | Ergebnis | Reale Dauer | Aussage |
|---|---:|---:|---|
| Vorhandene Experience-Dauerhaftigkeitstests | 7 bestanden, 0 fehlgeschlagen, 0 übersprungen | 2,334 s | Unvollständige Schreibvorgänge, Sequenzfehler, Grenzen, Symlinks, Schreib-Lease und doppelte Aufgaben werden in den vorhandenen Fällen geprüft. |
| Vorhandene Technical-Recall-Tests | 44 bestanden, 0 fehlgeschlagen, 0 übersprungen | 4,277 s | Vertragsfälle für Neustart, Bindung, MAC, Widerruf, Umfang, Kapazität und echte konkurrierende bzw. beendete Kindprozesse bestehen. |
| Unabhängiger Zusatzversuch | 9 erwartete Verhaltensweisen beobachtet | 0,282 s | Direkte Gegenprobe der Ledger-/Journal-Grenze; keine Reparaturausführung. |
| Drei Läufe insgesamt | Exitcode jeweils 0 | **6,896 s** | Einmalige Host-Messung. |

Die neun unabhängigen Prüffälle im Einzelnen:

| Prüffall | Beobachtung |
|---|---|
| Offene Aufgabe | `experienceOf` liefert `null`. |
| Abgeschlossene synthetische Aufgabe | Ein Datensatz wird dauerhaft geschrieben; identisches erneutes Anhängen ergibt `false`. |
| Echter Prozessneustart | Ein neuer Node-Prozess liest dieselben fünf Journalereignisse und dieselbe Zusammenfassung; Ledger-Hash bleibt gleich. Technischer Recall enthält **0** Einträge bei Status `OK`. |
| Abweichende Zusammenfassung derselben Task-ID | `append` liefert `false`; ursprüngliche Bytes bleiben unverändert. Das ist Idempotenz, keine Meldung eines semantischen Konflikts. |
| Ledger-Änderung ohne Neuhashen | Lesen scheitert mit `experience log damaged sequence or record at line 1`. |
| Ledger-Änderung mit neu berechneter Hash-Kette | Gefälschter Patch-Hash wird lesbar; der allgemeine Erfolgseintrag bleibt für technischen Recall unzulässig, **0** Einträge. |
| Selbst behauptete Modellbestätigung | Speicherung als technischer Nachweis scheitert mit `TECHNICAL_RUNTIME_OBSERVATION_REQUIRED`; Ledger bleibt unverändert. |
| Änderung eines VERIFIER-Receipts plus neue Hash-Kette | Kanonisches Journal scheitert mit `entry 3 claims sender VERIFIER without a valid runtime MAC`. |
| Vollständiges letztes JSON ohne Abschluss-Newline | Recall gibt `BLOCKED_LEDGER_DAMAGED` und **0** Einträge aus; keine stillschweigende Reparatur. |

Die positive Abrufkontrolle stammt aus dem vorhandenen Test `CONTRACT_TEST signed fixture survives a genuine fresh Node process restart`: Dort überlebt ein vollständig gebundener, mit Fixture-Schlüssel signierter synthetischer technischer Nachweis einen wirklichen Prozessneustart. Im Zusatzversuch wurde bewusst nur die allgemeine Zusammenfassung verwendet. Seine Null-Ergebnisse bedeuten deshalb nicht, dass der gesamte Abruf abgeschaltet wäre. [M3, M4]

## 5. Was diese Ergebnisse für Eigenständigkeit bedeuten

**Belegt:** Die untersuchten Mechanismen benötigen für ihre Ausführung keinen externen KI-Dienst. Der Host kann Aufgabenverläufe und überprüfbare technische Erfahrungen lokal speichern, nach Neustart auslesen und ungeeignete Nachweise aus dem vorgesehenen technischen Abruf ausschließen. Persistenz, Wiederholungsschutz und Prüfung sind echte vorhandene Implementierungen. Die generischen Erfahrungseinträge allein besitzen bewusst weniger Autorität als technische Nachweise. [M1–M4]

**Aus Quellcode abgeleitet, nicht end-to-end gemessen:** Der bereits vorhandene Orchestrator kann solche zugelassenen Erfahrungen als begrenzten Kontext an einen späteren Coding-Auftrag übergeben. Damit existiert der technische Ansatzpunkt für erfahrungsabhängiges Verhalten ohne Gewichtsänderung. Ob ein lokal ausgeführtes Gemma-Modell diesen Kontext richtig verwendet und damit mehr unbekannte Fehler repariert, ist noch offen. [S3, S5, S6]

**Offene Leistungsfrage:** Die Tests prüfen Bedingungen, unter denen Wissen angeboten werden darf. Sie messen weder die Qualität einer Reflexion noch die semantische Ähnlichkeit neuer Aufgaben, die Reparaturquote oder das Risiko falscher Analogien. Die Auswahl prüft unter anderem Quellbezug und Dateiüberschneidung; diese Bedingungen ersetzen keinen neuen Test des konkreten Patches. Alte Erfolge müssen bei einer neuen Aufgabe weiter als Hinweise behandelt werden.

**Praktische Begrenzung:** Leere Abrufe können mehrere legitime Ursachen haben: fehlender technischer Nachweis, nicht passender Scope, abgelaufenes Alter, Widerruf, zu kleines Kontextbudget oder fehlende Zulassung der anfragenden Aufgabe. Ein bloßer Zähler `technicalRecallCount = 0` erklärt diese Ursachen nicht vollständig. Für die nächste Forschung sollten die vorhandenen Status- und Tracefelder zusammen ausgewertet werden, ohne private Eigentümerdaten oder eine zweite Gedächtnisstruktur einzuführen. Dies ist eine Forschungsfolgerung, keine hier vorgenommene Produktänderung. [S3, S5, S6]

## 6. Einordnung durch primäre Forschung

**Reflexion** beschreibt, wie sprachlich formuliertes Feedback in einem episodischen Gedächtnis spätere Versuche beeinflussen kann, ohne Modellgewichte zu aktualisieren. Das stützt die Forschungsfrage, ob bereits geprüfte technische Erfahrungen als Kontext helfen. Die Arbeit beweist weder die Wirksamkeit von Gemma in Continuum noch die Sicherheit einer bestimmten Persistenzimplementierung. Die hier gemessene MAC-/Journal-Grenze ist eine lokale Implementierungseigenschaft und wurde unabhängig von den Paper-Ergebnissen geprüft. [L1]

**SWE-agent** untersucht die Rolle der Schnittstelle, über die ein Sprachmodell Repositorys untersucht, Code bearbeitet und Tests ausführt. Daraus lässt sich für dieses Projekt die Hypothese ableiten, dass ein verlässlicher Test- und Rückmeldeweg mindestens ebenso gezielt untersucht werden sollte wie ein neues Planungsmodell. Die Arbeit ersetzt keinen lokalen Vergleich mit gleichen Aufgaben, Modellständen und Budgets. Ihre publizierten Benchmarkwerte werden deshalb hier nicht auf Continuum übertragen. [L2]

Die beiden Primärquellen wurden vor der Eingrenzung dieses Versuchs recherchiert. Für die Messung wurde keine neue globale Recherche und kein fremder Agentenrahmen eingeführt.

## 7. Eine priorisierte Anschlussforschung

**Priorität 1: Den vorhandenen technischen Recall mit einer echten lokalen Modellreparatur schließen und anschließend seinen Nutzen gegen denselben Modelllauf ohne Recall messen.**

Zuerst sollte genau ein kleiner, vorhandener Reparaturfall in einer isolierten Kopie durch den bereits existierenden Coding-Orchestrator laufen: gepinntes lokales Modell und gepinnte Laufzeit, echte fehlgeschlagene Ausgangstests, Modellvorschlag, unabhängige erfolgreiche Nachprüfung, technischer Nachweis und danach Abruf durch einen neuen, zulässigen Auftrag in einem neuen Prozess. Die Aufnahme muss über `recordTechnicalExperience` erfolgen; handgeschriebene Erfolgsflags oder Testadapter sind für diesen Nachweis unzulässig. Als Erfolg zählt erst die vollständige Verbindung von tatsächlicher Modellbeobachtung, Receipt, Grant, Quellbindung und späterem Abruf. Fehlt eine dieser Voraussetzungen, lautet das Resultat weiterhin **NOT_RUN** beziehungsweise der konkrete Blockstatus.

Erst danach folgt ein vorher festgelegter kleiner Paarvergleich auf zurückgehaltenen Fehlern: gleicher Modell-/Laufzeitstand, gleiche Ausgangsstände, gleiche erlaubte Dateien und identische Obergrenzen für Modellaufrufe, Kontext und Zeit. Die beiden Bedingungen unterscheiden sich allein durch `technicalRecallEnabled`. Beide behalten dieselben unabhängigen Tests und Eigentümergrenzen. Das Experience-Ledger für die Recall-Bedingung darf nur Nachweise anderer, bereits geprüfter Aufgaben enthalten; die Lösung des aktuellen Holdouts darf darin nicht vorkommen.

Erfasst werden müssen mindestens: unabhängiger Reparaturerfolg, neue Regressionen, Modellaufrufe, Eingabekontext, Laufzeit, korrektes Abbrechen, aufgenommene bzw. abgewiesene Erfahrung und deren Begründung. Eine höhere bloße Zahl an `COMPLETED`-Einträgen zählt nicht als Verbesserung. Ebenso darf ein Vorteil nur aus zusätzlichen Modellaufrufen oder größeren Kontextbudgets nicht dem Gedächtnis zugerechnet werden. Der gepaarte Rohdatensatz gehört in den Forschungsbericht; bei wenigen Fällen bleibt die Aussage eine Pilotmessung.

Diese Anschlussforschung nutzt die vorhandene Pipeline. Ein automatisches Training, neuer Worker, Merge, APK-Update oder Eingriff in die persönliche Erinnerung ist dafür nicht vorgesehen. Der anschließende Fold7-Nachweis muss getrennt erfolgen, einschließlich Laufzeit- und Gerätebedingungen; die vorliegende Host-Messung liefert dafür keine Werte.

## 8. Dateien und Nachweise

### Messungen und ausführbare Dateien

- **[M1]** `results/measurements.json`: vollständige Befehle, Zeitstempel, Umgebungsdaten, Einzelfälle, elf Quellhashes vor/nach dem Lauf, Script-Hashes und Integritätsvergleich.
- **[M2]** `results/experience-durability.stdout`: originale JUnit-Ausgabe der sieben vorhandenen Tests; `.stderr` enthält den unveränderten Fehlerkanal.
- **[M3]** `results/technical-recall.stdout`: originale JUnit-Ausgabe der 44 vorhandenen Tests; zugehöriger Fehlerkanal als `.stderr`.
- **[M4]** `results/independent-probe.stdout`: rohe JSON-Ergebnisse der neun unabhängigen Prüffälle, einschließlich Fehlertexte, Prozessidentität und Fixture-Hashes.
- `run_experiment.py`: Messung, Quellschutzprüfung und Ergebnisexport.
- `probe.mjs`: unabhängiger Manipulations- und Neustartversuch gegen die unveränderten Quellmodule.

### Quellcode, exakt auf Baseline gebunden

- **[S1]** [ccc1-experience.mjs](https://github.com/Ullmann27/device-ai/blob/e101e393dd6c80803ac9d9ac91fccd9812c4a18d/.github/continuum/ccc1-experience.mjs)
- **[S2]** [ccc1-journal.mjs](https://github.com/Ullmann27/device-ai/blob/e101e393dd6c80803ac9d9ac91fccd9812c4a18d/.github/continuum/ccc1-journal.mjs)
- **[S3]** [ccc1-technical-recall.mjs](https://github.com/Ullmann27/device-ai/blob/e101e393dd6c80803ac9d9ac91fccd9812c4a18d/.github/continuum/ccc1-technical-recall.mjs)
- **[S4]** [ccc1-worker.mjs](https://github.com/Ullmann27/device-ai/blob/e101e393dd6c80803ac9d9ac91fccd9812c4a18d/.github/continuum/ccc1-worker.mjs)
- **[S5]** [coding-orchestrator.mjs](https://github.com/Ullmann27/device-ai/blob/e101e393dd6c80803ac9d9ac91fccd9812c4a18d/.github/continuum/coding-orchestrator.mjs)
- **[S6]** [coding-provider.mjs](https://github.com/Ullmann27/device-ai/blob/e101e393dd6c80803ac9d9ac91fccd9812c4a18d/.github/continuum/coding-provider.mjs)
- **[S7]** [ccc1-technical-recall.test.mjs](https://github.com/Ullmann27/device-ai/blob/e101e393dd6c80803ac9d9ac91fccd9812c4a18d/.github/continuum/ccc1-technical-recall.test.mjs)

### Primäre Literatur

- **[L1]** Shinn et al.: *Reflexion: Language Agents with Verbal Reinforcement Learning*. arXiv:2303.11366. [Originalarbeit](https://arxiv.org/abs/2303.11366). Verwendete Aussage: sprachliches Feedback und episodisches Gedächtnis als Anpassung über Versuche hinweg ohne Gewichtsupdate.
- **[L2]** Yang et al.: *SWE-agent: Agent-Computer Interfaces Enable Automated Software Engineering*. arXiv:2405.15793. [Originalarbeit](https://arxiv.org/abs/2405.15793), [Autorenrepository](https://github.com/SWE-agent/SWE-agent). Verwendete Aussage: die Interaktionsschnittstelle für Navigation, Bearbeitung und Tests als relevante Komponente agentischer Softwarearbeit.
