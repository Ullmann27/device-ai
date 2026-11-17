# CONTINUUM Ω – Argon-4-Hochmodus: technisches Review und nächste Rückfragen
Stand 03.11.2026 (lokale Zeit). Repository: Ullmann27/device-ai, nur Device AI Continuum.

## Quelle und Herkunft
Argon-Ergebnis: [CONTINUUM OMEGA – ARGON 4 Forschungs- und Architektur-Dossier.pdf](https://drive.google.com/file/d/13Z_JOOMB7ky6U9t9RMmqegioJWX1ShRs/view?usp=drivesdk), 19 PDF-Seiten, in Google Drive gelesen. Argon erklärt ausdrücklich, dass er GitHub nicht live inspiziert hat. Alle Source-Claims gegen aktuelle Repo-Dateien prüfen; PDF-Ausschnitte sind **keine kompilierten Patches**, Leistungszahlen sind ohne Gerätemessung Hypothesen.

## Tatsächlich bestätigte Basis
- Fold7 owner-installed `0.3.38` / Code39, Original-App und originalsignierter Update-Publisher; native Gemma-Direktgenerierung `21.389 ms`, `gemma4-e4b-it`, Modellkatalog `0b2a8980...`.
- Offline 3-Turn-Ringtest: erster Turn rot4/blau7 → richtig blau7; zweiter Turn „rot nun9“ → alte blau7 vergessen; dritter Turn ursprünglicher Zustand → alte Daten fehlen. Die eine vollständige Ringaufgabe erzeugte am Gerät letztlich richtig blau7 ursprünglich/rot9 jetzt, nach einer eigenen widersprüchlichen Zwischenformulierung; daher kein Beweis stabiler First-Pass-Logik.
- Ursache des Mehrturn-Fehlers **nicht eindeutig bewiesen**: `LocalConversationContext` hält 4 Kurzturns mit Original-Owner-Anfrage bis 1000 Zeichen; `AgentActivity` rendert History; `LocalPromptFrame` konnte in der installierten Code39-Version optionalen Kontext bei Budgetüberschreitung verwerfen; `LiteRtLmLocalModelRuntime.stream` nutzt Fresh-Prompt. Modell-Aufmerksamkeit ist auch bei vollständig übertragener Historie eine eigene Fehlerklasse.
- PR #365 enthält bereits `requireHistory` und Speicherverb-Routing; Branch ist Draft und auf Fold7 noch nicht installiert.
- PR #366 (abhängig von PR #365) ergänzt nur private-textfreie `LocalContextIntegrityProbe` mit 5 neuen JUnit-Regressionsfällen und UI-Status bei Folgefragen. Isolierte Kotlin-Logik auf JDK21 kompiliert und 12/12 Assertions bestanden; vollständiger Android-/JUnit-/Gerätetest NOT_RUN.

## Entscheidungen aus dem 19-Seiten-Dossier
| Vorschlag | Entscheidung | Voraussetzung / Risiko |
|---|---|---|
| Bitemporale Zustandsprojektion | SCHATTENEXPERIMENT | Erst prüfen, ob Historie im Prompt ist. Fiktive/hypothetische Aussagen niemals dauerhaft als bestätigte Besitzerfakten speichern. Provenienz + aktuelle/historische Gültigkeit trennen; vergangene Version nicht überschreiben. |
| Prompt-/Sessiondiagnose | JETZT IN PR366 BEGINNEN | Nur Status, Turnanzahl und Bytezahl; **keine** SHA-Kurzformen von Zahlen/Farben; Dictionary-Rekonstruktion möglich. Diagnose beweist Transport, nicht Verstehen. |
| BEDL/delta-basiertes Schlussfolgern | FORSCHUNG/SHADOW | Monotone und nichtmonotone Updates, Widersprüche, Fehlerfälle sowie CPU/RAM/Energie gegen Baseline messen; keine „0 LLM Joule“ ohne echte Baseline. |
| CEGIS CCC1 RED→GREEN | KERNZIEL, NACH PR366/QA | Bereits vorhandene CCC1-Worker-/ToolGate-/UNKNOWN-Logik verwenden; Original-PDF-JS ist nicht unabhängig syntaktisch/prozesssicher geprüft und darf nicht als generischer Shell-Runner übernommen werden. |
| Eigene lokale Stimme + TTS/ASR, Lip-Sync | HOHER NUTZERWERT, GETRENNTER VERSUCH | TextToSpeech-Fallback erhalten, Piper/ONNX/Whisper/MediaPipe Lizenzen und Binary-Mobil-Laufzeiten selbst nachprüfen, kein fremder Voice-Clone als exportierbar voraussetzen; opt-in Kamera, keine Identifikation ohne explizite Erlaubnis. |
| Universal-Missionsvertrag Android↔PC↔Web | ARCHITEKTURPLAN, NICHT DOPPELT IMPLEMENTIEREN | Vorhandene ToolGate-/M8-Auftrags-IDs, Risiko-Policy, Quittungen und langlebige UNKNOWN-Sperre zuerst inventarisieren und adapterweise erweitern. |
| Neuromorphe/Neuralink/Loihi-Phrasen | NUR FORSCHUNGSINSPIRATION | Keine physische Loihi/BCI-Funktion auf Fold7, keine gemessene LLM-Beschleunigung. Sparse-Event-Reuse nur bei real nachweisbaren Triggern/Leistungsgewinnen. |

### Drei technische Risiken, die Argon in seiner nächsten Fassung beheben muss
1. `USER_CONFIRMED` unmittelbar für jeden Nutzertext ist falsch: eine hypothetische Kistenaufgabe ist kein Eigentümerfakt. `Speichere das nicht` verlangt nicht nur ein Flag, sondern ausdrücklich keinerlei dauerhafte Event-/Cache-/Log-Persistenz.
2. Hashes von `rot/blau/4/7/9`, insbesondere stark verkürzt, gelten **nicht** als echte Anonymisierung; statt tokenbasierten Canaries nur Zahl vorheriger Owner-Turns, Bytebudget und sichere boolesche vollständige Prompt-Aufnahme.
3. `CCC1`-Patchvorschläge dürfen keine beliebigen `testCommand`-Shell-Strings, unprüfbare Modell-Fixes, automatische Git-Merges oder Folgeaktionen nach persistiertem UNKNOWN erhalten. Erst Testdatei-Hardlinks/Symlinks/Worktree-Rechte/Race-/TOCTOU-Abwehr und unabhängige Testquittungen prüfen.

## Präzise Rückfragen an Argon 4 (Hochmodus, kurze Chatantwort oder lokale PDF)
**Q1 – Kontext-Falsifikation:** Unser PR366 liefert nun `OWNER_EVIDENCE_INCOMPLETE / NO_PRIOR_OWNER_TURN / RENDERED_HISTORY_UNAVAILABLE / PROMPT_UNAVAILABLE / REQUIRED_HISTORY_NOT_IN_PROMPT / HISTORY_IN_PROMPT` ohne Nutzerdaten. Entwirf ein minimalinvasives Experiment, das nach `HISTORY_IN_PROMPT` echten Modell-Fehlschluss **von** Native-Inferenz-Übertragungsfehler oder Session-/Kontextbudget trennt. Gib exakte Integration in bestehende Activity/LiteRT-LM-Adapter, ohne Klartext-/Tokenhash-Logs, ohne native Fake-Quittungen.

**Q2 – Gedächtnis ohne falsche Fakten:** Entwirf eine zustandsbasierte Ergänzung der bestehenden `MemoryEcology` und `LocalConversationContext`, in der hypothetische Ringzahlen nur flüchtige `TASK_SCOPED_HYPOTHESIS` sind, während ausdrücklich vom Besitzer bestätigte Fakten separat und nur erlaubterweise persistent werden. Demonstriere 4-Turn-Zeitwechsel inkl. `morgen`, `nicht speichern`, österreichischen Kurzformen, Aussagen über Dritte, Konflikt und Sessionwechsel. Liefere RED-Tests, Shadow-/Rollback-Plan und Grenzfälle statt eines zweiten Speichers.

**Q3 – CCC1-Hardening:** Vergleiche die PDF-`Ccc1CegisWorker`-Skizze mit bestehendem `.github/continuum/coding-orchestrator.mjs` / `coding-agent-loop.mjs` / `ToolGate`-Journal. Ohne direkten GitHub-Zugriff: beschreibe ein nachweisbar sicheres Integrationsdiff statt neuen parallelen Workern, inkl. Testmanipulation, symlink/path traversal, persistiertem UNKNOWN, Testkommando-Allowlist, echten Holdout-Gates und autorisierten Änderungen.

**Q4 – Offline-Stimme als schneller praktischer Fortschritt:** Welche konkrete aktuelle, tatsächlich lizenzkompatible Android-arm64-Pipeline liefert offline deutsche/österreichische ASR und eigene TTS-Stimme? Trenne Trainingscode, trainierte Gewichte, Sprecher-Datensatzrechte, Export, JNI/ONNX-Grenze, Satzstreaming, TTS-Abbruch, Kamera/Avatar-Opt-in. Liefere reproduzierbare Versionen/Dateigrößen/Lizenzen und Fold7-Messplan; keine unbestätigten 90-ms- oder 0-ms-Behauptungen.

**Q5 – Rechenökonomie/BEDL:** Lege eine Baseline, missible Ressourcenmetrik, Energie-/Stale-/Privatsphäre-Negativkontrollen und drei exakt definierte Falsifikationsversuche vor, bevor BEDL zu `ComputeFabric` und `ExperienceCompiler` verbunden wird. Erkläre, was algorithmisch schon bekannt ist und wo nur eine projektspezifische neue Kombination vorliegt.

## Arbeitsvertrag
Argon soll höchstens eine kurze Chat-Kompaktantwort ausgeben und bei Umfang eine von ihm **lokal** erzeugte, lesbare PDF bereitstellen. Er darf zuerst Quellen, Lizenzen, Unsicherheiten und Gegenproben prüfen; erst danach Produktionscode empfehlen. Keine Kostenpflicht, keine erfundenen APIs oder Benchmarks, keine zweite KI-Architektur. Die Antwort soll `SOURCE_ONLY / HOST_VERIFIED / DEVICE_VERIFIED / MISSION_VERIFIED` trennen.

## Freigabe / Offline
Bestehende Benutzer-App bleibt Code39. Für Code40 gilt nur bestehender Originalsignierer, exakt geprüfter Source-SHA, versionCode>=40 und separate Besitzerfreigabe; niemals automatische Zweit-App oder Datenlöschung. Bis zum nächsten angekündigten Download-/Internet-Test: Fold7-Internet AUS und Continuum-Webfreigabe „Nicht zulassen“.

## Externe Primärquellen-Stichprobe (03.11.2026)
Diese Überprüfung ändert keine Produktfreigaben:
- **Existenz bestätigt:** `LOCOMO-CONV` (Chang/Chen, Findings EMNLP Oktober 2026), URL: https://aclanthology.org/2026.findings-emnlp.1289/; Referenz passt zur wissenschaftlichen Dialog-Retrieval-Forschung, beweist keine Continuum-Verbesserung.
- **Existenz bestätigt:** LongMemEval, ICLR 2025 / öffentliches Benchmarkprojekt: https://github.com/xiaowu0162/LongMemEval ; methodische Benchmark-Idee übernehmen, keine ungeprüften Leistungswerte.
- **Existenz bestätigt:** xAI Grok 4.6, offizielle Mitteilung 12.08.2026 mit Fokus langlaufender Agenten: https://x.ai/news/grok-4-6 ; Herstellerbehauptungen sind keine Hardware-Fähigkeiten des Fold7.
- **Lizenzrisiko bestätigt:** OHF `piper1-gpl` nutzt GPL-3.0-or-later, Quellnachweis https://github.com/OHF-Voice/piper1-gpl/blob/main/COPYING ; die einzelnen Sprachmodelle haben laut https://github.com/OHF-Voice/piper1-gpl/blob/main/docs/VOICES.md getrennte MODEL_CARD-Lizenzen. ONNX-Datei direkt zu verwenden heißt **nicht automatisch**, dass Trainingsdaten-/Gewichte-Lizenzprobleme gelöst sind. Die PDF-Annahmen `65MB` und `p50 < 90ms` sind **keine** am Fold7 nachgewiesenen Messwerte.
- **Android-Richtung bestätigt:** `whisper.cpp` enthält ein Android-Java-Beispiel, https://github.com/ggml-org/whisper.cpp/blob/master/examples/whisper.android.java/README.md ; Integration und ASR-Latenz in Continuum noch nicht ausgeführt.
