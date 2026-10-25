# JARVIS-MO – Internationaler Forschungsabgleich und sichere Anschlussplanung
Stand: 25.10.2026. Forschungs-Checkpoint von PR #133, Ausgangs-SHA `c721f785a5afc65cc05ec3415d9d0f7844d5ad88`.
**Status:** quellengeprüfte externe Forschung + konkrete Anschlussplanung; **keine** aus fremden Projekten übernommene Implementierung, kein fertiger Android-Gerätenachweis.
Projekt ausschließlich: privates `Ullmann27/device-ai`. Unveränderliche CCC1-/Besitzer-/ToolGate-Grenzen und AGENTS.md beachten.

## Bestätigte Ausgangslage im bestehenden Continuum
- `AgentActivity.runLocal` verwendet bereits `cognitive/BoundedAgent.kt`, lokale Modell-Runtime, ToolGate und Experience Store. **Hier** muss die Agentenkoordination wachsen; keinen zweiten JARVIS-Controller errichten.
- `cognitive/LocalConversationContext.kt` (PR #133) erhält derzeit 4 Sitzungspaare mit begrenzter Textlänge, ausdrücklich als unbestätigte Daten. Bei Sitzungswechsel wird gelöscht.
- `localmodel/LocalConversationBoundary.kt` (PR #133) trennt frischen Prompt und erlaubte Tool-Fortsetzung; alte native Modellunterhaltungen dürfen nicht ungewollt fortbestehen.
- `cognitive/HybridMemoryRetriever.kt`, `MemoryEcology.kt`, `learning/ContinuumMemoryDialogue.kt`, `ChatReasoningRouter.kt`, `LocalModelHost.kt`, `LiteRtLmLocalModelRuntime.kt` und SWB1/CDX1/LM1 existieren mit unterschiedlichen Reifegraden. LM1 ist nicht die aktive frei sprechende Hauptdialog-Runtime; LM2 kein nachgewiesener laufender Dialogkern.
- Astras `VH-SR` in PR #133 hat synthetische Gegenbeispielreplays mit Ergebnissen; diese bewerten **keine** echte Qwen-Inferenz, weil die Modellaufrufe dort 0 sind. Keine Generalisierungsbehauptung.
- `AGENTS.md` und PRs #135–#137 behandeln Handoff bzw. Besitzerprüfung. Die P-256-Signaturstufe besitzt **keine** persistente Nonce-Verbrauchsprüfung und darf keine CCC1-Rechte schaffen.

## Internationale Quellen und ausschließlich nützliche Muster

1. **Stanford, USA — OpenJarvis (2026)**  
   Quellen: https://github.com/open-jarvis/OpenJarvis ; https://open-jarvis.github.io/OpenJarvis/architecture/overview/  
   Übernahme **als Entwurfsmuster**, nicht als zweites Framework: Registry verfügbarer lokaler Modelle/Engines, separater Agent-, Tool-/Memory- und Lernpfad; ereignisgebundene Traces; End-to-End-Evaluation von Antwortqualität, Zeit, RAM, Energie. OpenJarvis ist primär Desktop-/Server-Stack, kein bereits geprüfter Fold7-Drop-in. Kernquelle Apache-2.0 (Einzeldependencies separat prüfen).
2. **Microsoft, USA — HuggingGPT/JARVIS (2023)**  
   Quelle: https://github.com/microsoft/JARVIS  
   Vierstufiger Koordinationsablauf: Intention/Taskplanung -> passende vorhandene Modell-/Tool-Komponente auswählen -> ausführen/beobachten -> Antwort konsolidieren. Bei uns direkt als Zustandsübergänge des bestehenden `BoundedAgentOrchestrator`, nicht als neues autonomes Modellnetz. Cloudbasierte HuggingFace-Modelle nicht blind einbauen.
3. **Alibaba Tongyi, China — Mobile-Agent / GUI-Owl (2024–2026)**  
   Quelle: https://github.com/X-PLUG/MobileAgent  
   Interessant: verknüpfte Beobachtungs-/Aktionsschleife für Android, Verifikation nach einer Aktion und Auswahl zwischen GUI-Aktion und direktem Werkzeug. Lizenz des Haupt-Repositories bei Sichtung MIT; Gewichte und APIs separat prüfen. Erst READ_ONLY-/Mock-UI-Pfad; keine automatische ADB-/Accessibility-/Shizuku-Ausführung über Sprachmodelltext.
4. **ByteDance, China — UI-TARS (2025–2026)**  
   Quelle: https://github.com/bytedance/UI-TARS  
   Explizite typisierte mobile Aktionen (`open_app`, `press_back`, `long_press`, `scroll`), Grounding mit Bildschirmbezug. Gute Forschung für späteren Mobile-Intent-/Action-Adapter; kein ungeprüftes 7B-Visionsmodell auf Fold7 erzwingen. Hauptcode Apache-2.0, Modellgewichte separat prüfen.
5. **Sakana AI, Japan — Darwin Gödel Machine (2025)**  
   Quellen: https://sakana.ai/dgm/ ; https://arxiv.org/abs/2505.22954  
   Archiv diverser begrenzter Verbesserungskandidaten; empirische Auswahl durch unabhängige Benchmarks; negative Änderungen verwerfen. Für Continuum: **nur** offline Experimentarchiv über die bestehende CCC1-/VH-SR-Pipeline, unveränderliche Permissions. Benchmarkgewinn der Autoren lässt sich nicht auf Continuum übertragen.
6. **Sakana AI, Japan — ShinkaEvolve (2025–2026)**  
   Quelle: https://github.com/SakanaAI/ShinkaEvolve  
   Übernahme als Experimentdesign: mehrere alternative Strategien, holdoutgetrennte Evaluierung, Diversity/Archive, reproduzierbare Budgets und Rollback. Python-/GPU-/Clusterstack nicht unbesehen als Android-Pflicht integrieren. Hauptcode Apache-2.0; Einzeldependencies prüfen.
7. **Sakana AI, Japan — AI Scientist v2 (2025)**  
   Quelle: https://github.com/SakanaAI/AI-Scientist-v2  
   Forschungsfragen -> Hypothesen -> begrenzte Experimente -> Nachweise -> Review. Für EIG-SC/Nutzwert von Experimentauswahl relevant, aber eigener Code besitzt **spezielle Responsible-AI-artige Lizenz- und Offenlegungspflichten**, CUDA/GPU-Stack und selbstgenerierte Ausführungsskripte: **vorerst nur Forschungsidee, nicht Quellkopie**.
8. **Mem0, USA/international — Langzeitgedächtnis (2025–2026)**  
   Quellen: https://github.com/mem0ai/mem0 ; https://arxiv.org/abs/2504.19413  
   Nützlich: kombinierte Keyword-/semantische-/zeitbezogene Rangfolge, Entity-Linking, Speicherabfrage nur aus passendem Zeitraum, Konflikt-/Provenienzbehandlung. **Nicht** die Nutzer- und Sicherheits-Gedächtnisse durch unkontrollierte LLM-Extraktion ersetzen. Hauptrepo Apache-2.0; beworbene Plattformbenchmarks beinhalten proprietäre Komponenten und sind nicht Open-Source-Benchmarkergebnisse.
9. **Google Research, USA — AndroidWorld**  
   Quelle: https://github.com/google-research/android_world  
   Reproduzierbare Android-Emulatoraufgaben und Erfolgsbedingungen statt nur `kein Crash`. Relevantes externes Evaluationsmuster: minimale fünf sichere eigene Workflows, später Benchmarkintegration falls Umgebung erlaubt. Kein Fold7-Ersatz.
10. **Home Assistant / OpenVoiceOS, Europa/international**  
    Quellen: https://contributors.home-assistant.io/architecture/platform/voice/ ; https://www.home-assistant.io/voice_control/voice_remote_local_assistant ; https://github.com/OpenVoiceOS/OpenVoiceOS  
    Für spätere Spracheingabe: Wake-word -> STT -> Intent-/Dialogkern -> TTS als separat austauschbare Schichten. Priorität **nach** getesteter textbasierter Intelligenz; keine Hintergrundaufnahme ohne Zustimmung.
11. **ggml-org llama.cpp / MLC-LLM**  
    Quellen: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md ; https://github.com/mlc-ai/mlc-llm  
    Realistische mobile Engine-Referenzen. Bevor neue Runtime importiert wird: vorhandene LiteRT-/Qwen-Anbindung mit tatsächlichem Fold7 RAM, TTFT, Tokens/s, thermischer Last und Crashstabilität vergleichen.

## Priorität: kleinster echter Intelligenzsprung statt Framework-Sammlung

### Meilenstein I: vorhandenes Gehirn messbar aktivieren
- `BoundedAgentOrchestrator` als **einziger** zentraler Controller.
- Reales Ablaufprotokoll: Anfrage-ID, Sitzungs-ID, Modellauswahl (auch 'ein einziges Modell'), Retrieval-Provenienz, geplanter Tool-Typ, ToolGate-Entscheid, ausgeführter READ_ONLY-Aufruf, Verifikationsstatus, Antwortquelle und Latenz. Strikte Redaction; keine Secrets oder private Inhalte in Git-Traces.
- Vorhandenen `HybridMemoryRetriever` mit Quellenvertrauen, Zeitfilter und `UNVERIFIED`-Abgrenzung gezielt vergleichen (A/B ablation ohne neue Datensilos).
- Keine nativen Modellgespräche über Sessionwechsel; neue Tool-Fortsetzung bleibt auf denselben ausdrücklich autorisierten Dialog begrenzt.
- Erst **fünf sichere End-to-End-Szenarien**: Mehrturnkontext; bestätigte Erinnerung; Zerlegung einer komplexeren Anfrage; read-only Tool plus überprüfte Observation; Korrektur eines vorgegebenen Fehlers. Negativfälle: fremder Sessiontext, widerrufene Erinnerung, manipulierte Toolausgabe, fehlende Berechtigung und Modellausfall.

### Meilenstein II: trainierbare Qualitätsverbesserung
- `LM1`, `SWB1`, `CDX1` gegenüber bewusst simpler Retrieval-/N-Gramm-Baseline und aktivem lokalen Dialogmodell bewerten.
- `LM2` getrennt und optional: gefrorener Train/Valid/Test-Split, Token- und CPU-/RAM-Budget, Perplexität/BPB plus Instruktionsaufgaben und Transfer-Holdouts; darf Dialogqualität nicht verschlechtern.
- Instrumentierte Traces dienen nur nach Datenschutz-/Provenienz-/Besitzerfreigabe als Trainingsmaterial. Keine ungeprüfte automatische Promotion neuer Gewichte.

### Meilenstein III: sichere Verbesserungsschleife
- `repair-loop.mjs` + bestehender `VH-SR` bleibt einzige Patch- und Gegenbeispielpipeline; opt-in Experimente mit gleichwertigen Workloads.
- Forschungsidee nach Sakana: Archiv getesteter Verbesserungskandidaten mit Commit, Hypothese, Quellen, Konfig, Budget, unabhängigen Resultaten und Rollback. Keine Selbstvergabe von Berechtigungen.
- App-, Termux-, OSV- und Besitzergrenzen erhalten; Android/CCC1 owner gate separat absichern. Forschungs-Signaturresultat `SIGNATURE_VALID_NOT_AUTHORIZED` ist ausdrücklich keine Grant-Berechtigung.

## Wissenschaftliche Abnahme
- Baseline alter Branch/HEAD gegen Neuerung auf **denselben** unveränderten Aufgaben und getrennten Holdouts, mit Tests, echten Modell-/Geräteausführungen und Fehlerzahlen. Synthetische Replays nicht als Modellintelligenz darstellen.
- Metriken: Task Success, Recall Precision, Factual Correctness, invalid-tool rate, prompt-injection resistance, latency p50/p95, peak RSS, tokens/s, battery and thermal indicators where available; memory consent/revocation correctness.
- No regressions on owner gates, leakage, multi-profile isolation, CCC1/source hashes, Android lifecycle or existing security unit tests.
- Messbasis: JDK17/SDK36-Android-Tests auf geprüftem Entwicklungscommit; Geräte-E2E nur mit tatsächlicher Fold7-Ausführung als PASS markieren. Kein neues `main`-Merge / keine APK-Signierung.
- Jede Änderung sofort in offiziellem **privaten** GitHub-Branch committen und pushen; Draft PR, `AGENTS.md` und Handoff beachten. Keine aus fremden Quellen blind kopierten Dateien und keine unvereinbaren Lizenzübernahmen.

## Konkrete Folgeaufgabe an Astra
1. PR-133-HEAD frisch prüfen und diese Forschungsmatrix mit dem bereits neu implementierten `LocalConversationContext`/native boundary abgleichen.
2. Einen **vollständigen fünfstufigen, readonly-first JARVIS-Intelligence-Vertical-Slice** im bestehenden Agent-Kern real anschließen, nicht noch einen parallelen Agenten erzeugen.
3. Mit fünf positiven und mindestens fünf negativen End-to-End-Szenarien, Quellenidentität und Tests nachweisen; Android-Device-Resultate und Labortests strikt trennen.
4. Nur falls Baseline reale Schwächen zeigt, small scoped Modellregistry, Memory-Ranker-Ablation und Trace-Format ergänzen; jede Erweiterung mit A/B-Metrik begründen.
5. Danach LM2 und experimentelles Strategiearchiv als getrennte, budgetierte Forschungszweige; keine Beförderung zu produktiver Owner-Rechtearchitektur.
6. Nach jedem brauchbaren Schritt commit/push/handoff; kontrolliere private Repo-Sichtbarkeit und überschreibe keinen aktiven Branch.
