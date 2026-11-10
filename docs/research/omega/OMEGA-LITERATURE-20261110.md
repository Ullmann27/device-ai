# OMEGA: Architekturvergleich und begrenzte Forschungsentscheidung

Stand: 10.11.2026. Quellen live am 10.11.2026 geprüft. Lokaler Quellstand für
diesen Vergleich: `515afc28af11b9befeb5a61f9c44411d423635a9`.
Autorrolle: unabhängiger Literatur-/Architekturvergleich; keine Produktänderung,
kein Modelllauf, keine Gewichtsänderung. Fold7: **NOT_RUN**.

## Entscheidung

**B, die bestehende hybride Architektur, gezielt vervollständigen.** Der kleinste
aussichtsreiche Schritt ist ein konsistenter, begrenzter Arbeitszustand über
Kontextauflösung, Ziel/Erhaltungsbedingungen, Werkzeugentscheidung und Ergebnis.
Das ist eine zu prüfende technische Hypothese, kein bereits bewiesener Gewinn an
allgemeiner Intelligenz. Zusätzliche lokale Modellschleifen sind dafür zunächst
nicht erforderlich.

Die reale Baseline ist bereits teilweise hybrid. Sie besitzt
`LocalConversationContext`, `ConversationSituationModel`, `ToolGate`,
`ToolWorkSceneLease` und `CockpitAnimationTriggers`. Ein neuer Planer, eine zweite
Gedächtnisdatenbank oder ein weiterer Ereignisbus wären deshalb keine angemessene
Antwort auf fehlende Verbindungen. Die beiden Kontextklassen kennzeichnen ihre
Information ausdrücklich als Gesprächsmetadaten ohne Fakten- oder Werkzeugrecht.
Diese Trennung muss erhalten bleiben.

Die Literatur stützt die Plausibilität besserer Organisation: Gedächtnistests
müssen zeitliche Änderungen und Enthaltung prüfen [S3]; neuere Evaluationen
unterscheiden erinnerte Fakten von korrekt daraus abgeleiteten Werkzeugparametern
[S4]. Sie beweist nicht, dass unser konkreter Patch Gemma auf dem Fold7 verbessert.
Diese Entscheidung benötigt den Quellvergleich und die separat protokollierte
Evaluation des Integrators.

## Genau drei Architekturvarianten

Die Tabelle enthält technische Erwartungen, keine drei gemessenen App-Arme.
Vergleichsbasis sind dasselbe Modell, dieselben Werkzeuge und dieselben
Berechtigungen. Ein schwächerer Prompt als künstliche A-Baseline wäre unfair.

| Kriterium | A: optimierte klassische LLM-Pipeline | B: hybride Architektur | C: adaptive hybride Architektur |
| --- | --- | --- | --- |
| Antwortqualität | Gute Prompt-/Retrieval-Hygiene kann helfen; Ziel, Referenz und Regeln müssen bei jedem Turn sprachlich rekonstruiert werden. | Explizite Referenzen, Revisionen, offene Fragen und Erhaltungsbedingungen können vermeidbare Zustandsfehler ausschließen; freie Sprache bleibt Modellaufgabe. | Zusätzliche Verifikation kann verbleibende Fehler beheben, sofern ein geeigneter Prüfer existiert; mehr Versuche können Fehler auch festigen [S1,S2,S12]. |
| Rechenbedarf | Üblicherweise ein Generierungslauf; lange Kontextwiederholung belastet Prefill. | Im kleinsten Entwurf derselbe Modelllauf plus begrenzte Kotlin-Prüfung; keine zusätzliche Modellresidenz. Tatsächliche CPU-/Speicherkosten messen. | Höherer und variabler Aufwand; Modellaufrufe, Tokens, Laufzeit und Thermik müssen gemeinsam begrenzt sein. |
| Komplexität | Geringste Zahl zusätzlicher Module; schwieriger nachzuweisen, weshalb ein Modell eine Referenz auswählt. | Mittlere Komplexität; feste Zustandsübergänge und klare Eigentümerschaft sind einzeln prüfbar. | Höchste Komplexität: Abbruch, Ressourcenreservierung, veraltete Resultate, Verifier und Korrekturpfade. |
| Fehleranfälligkeit | Auslassungen, veralteter Fokus und sprachlich behauptete Erfolge bleiben Risiken. | Falsche Extraktion ist weiter möglich; sichere Enthaltung und Quellenbindung erforderlich. Regeln dürfen keine neuen Rechte schaffen. | Zusätzlich schlechte Schwierigkeitsklassifikation, korrelierte Fehler, falscher Prüfer und verspätete Ergebnisse. |
| Wartbarkeit | Einfacher Einstieg, aber implizite Regeln wachsen im Prompt. | Gut bei Erweiterung vorhandener Typen und einem autoritativen Zustand; schlecht bei konkurrierenden Zuständen. | Nur wartbar mit derselben Gate-/Zustandskette und unveränderlicher Versuchsevidenz. |
| Fold7-Realisierbarkeit | Vorhandener Runtime-Pfad. Modell-/Geräteleistung des aktuellen Stands nicht hier gemessen. | Reine begrenzte Kotlin-Logik passt zur vorhandenen Plattform; Gerätebeleg bleibt erforderlich. | Als enges Opt-in-Experiment realistisch; dauerndes Mehrfachdenken ist ohne thermische Messung nicht begründbar [S11]. |
| Entscheidung | Unveränderte Referenz und später faire Prompt-Ablation behalten. | **Priorisierter Prototyp.** | Forschungszweig erhalten; keine Aktivierung aufgrund synthetischer Call-Ersparnis. |

Ein größeres Modell kann weiterhin bei Wahrnehmung, Sprache oder bislang
ungelösten Aufgaben helfen. B ersetzt dessen Fähigkeiten nicht. Ein fehlender
Videozugriff, eine unklare Referenz oder eine fehlende Freigabe werden jedoch auch
durch mehr Parameter nicht zu beobachteter Evidenz oder erteilter Berechtigung.

## Verfahren: Nutzen, Evidenz und Integrationsgrenze

| Verfahren | Beleglage | Entscheidung für Continuum |
| --- | --- | --- |
| Adaptive Test-Time-Compute | Studien zeigen auf ihren Aufgaben prompt-/modellabhängige Vorteile; keine universell beste Strategie [S1,S12]. | C später mit festem Modell, geprüfter Fehlerkorrektur und vergleichbarem Gesamtbudget messen. Nicht jede Nachfrage startet weitere Inferenz. |
| Strukturierte Episoden und Arbeitsgedächtnis | LongMemEval untersucht Extraktion, Sitzungs-/Zeitbezug, Aktualisierung und Enthaltung [S3]. Mem2ActBench untersucht Anwendung bei Werkzeugparametern [S4]. | **Jetzt priorisieren:** vorhandenen Kontext und vorhandene bestätigte Erinnerungen mit Herkunft/Revision verwenden. Keine zweite Datenbank. |
| Verifizierte prozedurale Fähigkeiten | Voyager zeigt ausführbare wiederverwendbare Fertigkeiten und Umgebungsfeedback in Minecraft mit GPT-4; das ist kein Android-Sicherheitsnachweis [S5]. | Vorhandene `VerifiedSkillReview`-/Experience-Kandidaten weiter prüfen. Ein wiederverwendbarer Ablauf ist keine wiederverwendbare Besitzerfreigabe. |
| Neurosymbolisches Schlussfolgern | PAL delegiert die Ausführung erzeugter Programme an einen Interpreter [S6]. Das belegt einen spezifischen Hybridansatz, nicht allgemeine Logikfähigkeit. | Deterministische Prüfungen für IDs, Revisionen, Negation, Budgets und Resultatnachweise. Freien Modellcode nicht deshalb ausführen. |
| Ereignisgesteuerte Verarbeitung | Hier eine Engineering-Entscheidung, kein neurowissenschaftlicher Wirksamkeitsbeleg. Vorhandene Cockpit-Trigger nehmen echte Laufzeitereignisse entgegen. | Zustand bei Besitzer-Turn, Gate-Entscheid, Toolstart/-ende, Abbruch und Audioereignis ändern. Kein neuer Polling-/Selbstreflexionszyklus. |
| Sparse und quantisierte Inferenz | Offizielle LiteRT-Dokumentation beschreibt Quantisierung und deren Genauigkeits-/Backendabhängigkeit [S10]. Daraus folgt keine Sparse-Beschleunigung dieses Modells. | Modellbytes unverändert lassen. Neue Quantisierung nur als eigener reproduzierbarer Modellvergleich; Sparse-Verfahren benötigen unterstützte Gewichte und tatsächliche Kernel. |
| Wiederverwendung bestätigter Zwischenergebnisse | Ein deterministischer Cache kann Arbeit sparen; der Nutzen hängt vom Trefferanteil und Prüfaufwand ab. Das ist eine bedingte Engineering-Aussage. | Vorhandenen Broker nutzen; Schlüssel mindestens an relevante Quelle/Modell/Policy-/Gedächtnisrevision binden. Widerruf oder Quellwechsel invalidiert. Keine ungeprüfte Wiederverwendung früherer Werkzeugrechte. |
| Hierarchische Planung | Aus zusammensetzbaren Fertigkeiten folgt eine mögliche Planstruktur [S5], kein Beleg für beliebig tiefe Planung. | Ein Ziel, wenige überprüfbare Schritte, offene Fragen und Erhaltungsbedingungen. Bestehenden Auftrag/Backlog erweitern; keinen zweiten Orchestrator. |
| Kleine spezialisierte Modelle | Ein Spezialmodell ist erst nach Aufgaben-/Gerätevergleich besser als die vorhandene Regel oder das Hauptmodell. Die aktuelle Runtime-Dokumentation enthält MTP für GPU [S9]. | Kein zusätzlich dauerhaft geladenes Modell. Erst klare Restfehler und Einsparung inklusive Laden/RAM nachweisen. MTP ist ein separater Backend-Test, keine neue Gedächtnisarchitektur. |
| Recurrent-/State-Space-Modelle | Mamba und Recurrent-Depth sind reale Forschungsarchitekturen mit eigenen Modellen [S7,S8]. | **Für diesen Prototyp zurückstellen:** andere Gewichte, Operatoren, Exportpfade und mobile Benchmarks nötig. Mehrere Gemma-Aufrufe sind keine Rekurrenz im latenten Zustand. |
| Selbstkorrektur/Gegenbeweise | Intrinsische Selbstkorrektur ohne externes Feedback kann scheitern oder verschlechtern [S2]. | Nur begrenzte Korrektur nach konkretem Fehlerbeleg. Ein fehlgeschlagener Test darf einen neuen Kandidaten begründen, aber keinen Erfolg. Maximalbudget und harte Abbruchbedingungen bleiben maßgeblich. |
| Lokales Lernen aus Erfahrungen | Voyager demonstriert Lernen über gespeicherte Fertigkeiten ohne Gewichts-Finetuning in seiner Umgebung [S5]. | Belegte Episoden und geprüfte Kandidaten über vorhandene Strukturen speichern. Keine automatische Modellgewichtsänderung; negative Belege und Widerrufe erhalten. |

## Konkrete Konsequenz für den Animation-Auftrag

Empfohlener gemeinsamer Vertrag: vorhandene Sitzungs-/Auftragsidentität,
Referenzkandidaten samt Entwicklungsstand, bestätigte Erhaltungsbedingungen,
offene Rückfrage, Planrevision und verifizierte Ergebnisreferenzen. Diese Daten
sind Eingaben für Entscheidungen, keine Berechtigungsnachweise.

Die Aussage „die andere war besser“ kann bei zwei klar dokumentierten Kandidaten
einen Vergleichszielpunkt liefern. Bei mehreren plausiblen Kandidaten muss das
System die Alternativen benennen und gezielt fragen. Ein bloßes „ja“ darf nur die
zuvor eindeutig gestellte Frage beantworten. Es darf weder ein anderes Werkzeug
freigeben noch eine veränderte Operation auf einem neuen Ziel erlauben. Die
Freigabe bleibt an den vorhandenen konkreten Gate-Auftrag gebunden.

Die Hologramme verwenden weiterhin `CockpitAnimationTriggers` und
`ToolWorkSceneLease`. Ein Plan erzeugt noch keine Rechercheanimation. Erst der
wirkliche zugelassene Toolstart rechtfertigt die entsprechende Szene. Ein
Toolresultat aus einer überholten Revision darf weder den neuen Auftrag erledigen
noch dessen Fortschritt bestätigen. Audio-/Visem-Synchronisation bleibt an die
bestehenden tatsächlichen Audioereignisse gebunden und ist hier **NOT_RUN**.

## Faire spätere Prüfung

Für die minimale B-Änderung zuerst unveränderte Baseline und Kandidat mit denselben
Eingaben, Quellen und Gate-Entscheidungen vergleichen. Kontext korrekt und
Aktion erlaubt sind zwei getrennte Zielgrößen. Eine vermiedene Aktion allein ist
kein Qualitätsgewinn, wenn alle berechtigten Aktionen ebenfalls blockiert werden.
Eine zusätzliche positive Kontrolle muss erlaubte, eindeutig bestimmte Aufträge
weiter ausführen können.

Für Modellqualität anschließend dieselben Modellbytes, Decodierparameter,
Promptobergrenzen und Offline-/Online-Einstellungen verwenden. Entwicklungssätze
und unabhängige Evaluationsfälle getrennt halten; nach Einsicht in einen Holdout
ist dieser ein Regressionstest, kein unverbrauchter Holdout. Unterbrechungen,
Themenwechsel, veraltete Resultate, widerrufene Wünsche und widersprüchliche
Erinnerungen gemeinsam mit erlaubten Fortsetzungen messen.

CPU-Zeit, Walltime, Heap/RSS, Prompt-/Outputtokens und Modellaufrufe getrennt
berichten. Hostzeit ist keine Fold7-Latenz. Ein CPU-Threadlimit allein ist kein
Energiebeleg. Energie und thermischer Verlauf benötigen einen echten Geräteversuch
mit dokumentiertem Ausgangszustand, Laufreihenfolge und Wiederholungen [S11].

## Vorhandene technische Anker

Alle Pfade beziehen sich auf den oben genannten unveränderten lokalen SHA.

| Anker | Gelesener Befund |
| --- | --- |
| `app/build.gradle.kts:307` | LiteRT-LM Android ist auf `0.18.0` festgelegt. |
| `cognitive/LocalConversationContext.kt:10` | Ein bestehender Kontextbesitzer; separates Forschungsdigest, Sitzungsinvalidierung und Owner-Evidenzansicht. |
| `cognitive/ConversationSituationModel.kt:12` | Begrenzte Themenstruktur mit Ziel/Projekt/Aufgabe, Fragen, Korrekturen und Lebenszyklus; ausdrücklich ohne Toolautorität. |
| `localmodel/LiteRtLmLocalModelRuntime.kt:89` | Bestehender Runtime-Adapter und einzelner Hintergrundexecutor. |
| `localmodel/LocalModelComputeBudget.kt:30` | Vorhandene Threadauswahl am Ladezeitpunkt. Die Kommentare über Wärmeursachen sind kein hier erhobener Messbeleg. |
| `localmodel/LocalModelThermalGuard.kt:22` | Guard für Start/Abbruch bei gemeldetem thermischem Zustand; keine automatische Wiederholung. |
| `cockpit/CockpitAnimationTriggers.kt:46` | `toolStarted` erzeugt passende Action nur aus aufrufendem Laufzeitpfad. |
| `ui/ToolWorkSceneLease.kt:7` | Szene ist an Session, Scope, Generation und Call-ID gebunden; Präsentationszustand ohne Ausführungsrecht. |
| `AgentActivity.kt:9703` | Bestehende Gate-Erstellung und echte Toolstart-Verbindung zur Darstellung. |

Die Klassenpfade in der Tabelle ohne Präfix liegen unter
`app/src/main/java/at/heinz/deviceai/`.

## Primärquellen und Grenzen

Keine Quelle wurde hier auf dem Fold7 reproduziert. Die Paper-Ergebnisse gelten
für ihre jeweiligen Aufgaben, Modelle und Messbedingungen. Aktuelle Webseiten
können APIs neuerer Versionen beschreiben: insbesondere MTP/ThinkingConfig nicht
ohne Abgleich mit dem tatsächlich eingebundenen `0.18.0`-Artefakt aktivieren.

| ID | Primärquelle, geprüfte Fassung | Verwendete Aussage und Grenze |
| --- | --- | --- |
| S1 | Snell et al., [Scaling LLM Test-Time Compute Optimally](https://arxiv.org/abs/2408.03314), 2024, v1 | Promptabhängige Verteilung von Prüfen/Revisionen kann auf den untersuchten Aufgaben effizienter sein. Keine übertragbare Beschleunigungszahl für Continuum. |
| S2 | Huang et al., [Large Language Models Cannot Self-Correct Reasoning Yet](https://arxiv.org/abs/2310.01798), ICLR 2024, v2 | Negative Ergebnisse für intrinsische Selbstkorrektur ohne externes Feedback. Keine Unmöglichkeitsbehauptung über alle Modelle. |
| S3 | Wu et al., [LongMemEval](https://arxiv.org/abs/2410.10813), ICLR 2025, v2 | Evaluation von Zeitbezug, Aktualisierungen, Sitzungsbezug und Enthaltung; Index/Retrieval/Reading getrennt betrachten. Kein Continuum-Ergebnis. |
| S4 | Shen et al., [Mem2ActBench](https://aclanthology.org/2026.acl-long.370/), ACL 2026 | Benchmark für Gedächtnisanwendung bei Werkzeugwahl/-parametern über unterbrochene Verläufe; automatisiert erzeugte Aufgaben sind kein Beweis für reale Besitzerfreigaben. |
| S5 | Wang et al., [Voyager](https://arxiv.org/abs/2305.16291), 2023, v2 | Wiederverwendbare Codefertigkeiten und Feedback in Minecraft mit GPT-4, ohne notwendiges Finetuning. Keine direkte Android- oder lokale Modellvalidierung. |
| S6 | Gao et al., [PAL](https://proceedings.mlr.press/v202/gao23f.html), ICML 2023 | Sprachmodell schlägt Programme vor, ein Interpreter führt Berechnung aus. Kein Freibrief zur Ausführung ungesicherten Modellcodes. |
| S7 | Gu und Dao, [Mamba](https://arxiv.org/abs/2312.00752), 2024, v2 | Selektive State-Space-Architektur und ihre Benchmarks. Kein Nachweis für Konvertierung des vorhandenen Gemma-Modells. |
| S8 | Geiping et al., [Scaling up Test-Time Compute with Latent Reasoning](https://arxiv.org/abs/2502.05171), 2025, v2 | Wiederholter trainierter Block in eigener Modellarchitektur. Anwendungsschleifen um ein anderes Modell sind technisch etwas anderes. |
| S9 | Google, [LiteRT-LM Android](https://developers.google.com/edge/litert-lm/android), Seite aktualisiert 04.09.2026 | Android-Kotlin-API, Backendoptionen, experimentelle MTP-Steuerung und Thinking-Budget. Verfügbarkeit im Projektartefakt und Modell muss gesondert geprüft werden. |
| S10 | Google, [LiteRT Model optimization](https://developers.google.com/edge/litert/quantization/model_optimization), live geprüft | Quantisierung kann Speicher/Arbeit reduzieren; Genauigkeit und Beschleunigung sind modell-/hardwareabhängig. Kein Sparse-Speedup oder Gerätemesswert abgeleitet. |
| S11 | Android Developers, [Thermal API](https://developer.android.com/games/optimize/adpf/thermal), live geprüft | Thermischer Zustand begrenzt nachhaltige Leistung; Arbeitslast soll entsprechend angepasst werden. Kein Grad-Celsius-Wert aus einem Statuslevel ableiten. |
| S12 | Agarwal et al., [The Art of Scaling Test-Time Compute](https://arxiv.org/abs/2512.02008), 2025, v1 Preprint | Vergleich mehrerer Strategien: keine universell beste Methode über Modelle/Aufgaben. Die Studie untersucht keine Continuum-Fold7-Konfiguration. |

## Übergabe und Abschlussgrenze

Ein Literaturdurchlauf, drei Varianten, eine priorisierte Entscheidung; kein
weiteres Konzept oder Prototyp in diesem Teilauftrag. Betroffen: EXP-11, EXP-14
und EXP-16; kompatibel mit dem bestehenden Experimentvertrag, da keine
Berechtigungen, Persistenzformate oder Modellgewichte geändert wurden.

Integrator: OMEGA-Hauptagent. Ziel: dessen separater OMEGA-Branch mit späterem
Review gegen `cinema/fluid-light-stage-20261110`. Cinema bleibt Eigentümer seiner
Darstellung; diese Empfehlung bearbeitet keine Cinema-Datei. Integration nur nach
der separat vorregistrierten Baseline-/Kandidat-/Negativkontrollauswertung.
Rücknahme dieses Teilbeitrags: ausschließlich dieses Dokument entfernen.
