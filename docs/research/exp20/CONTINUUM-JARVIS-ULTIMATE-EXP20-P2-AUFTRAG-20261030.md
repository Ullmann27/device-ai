# CONTINUUM JARVIS ULTIMATE — EXP-20 P2 / EXP-21 / EXP-22
## Verbindlicher Forschungs- und Implementierungsauftrag für Claude Opus 5.5 oder den nächsten Continuum-Entwicklungsagenten

**Stand der vorliegenden Live-Prüfung:** 30.10.2026. **Repository:** Ullmann27/device-ai (privat). **Ziel:** Samsung Galaxy Z Fold7, ARM64, Android-Paket at.heinz.deviceai.continuumlocal. **Modus:** autonom forschen, isoliert implementieren, real testen, früh committen/pushen, keine unbewiesenen Erfolgsbehauptungen. **Priorität:** zuerst Integration in das bereits bestehende Gehirn, nicht noch ein paralleles Gehirn bauen.

### 0. Verifizierte Ausgangslage und Git-Abstammung (vor Start zwingend erneut live prüfen)

- PR #251, Charter/Experimentregister, offen/Draft: research/continuum-owner-resilient-jarvis-charter-20261030, HEAD 397b25ecd23a004ab203e0e476d9df3425a67f33.
- PR #252, VOICE-1 + EXP-20 P1, offen/Draft, basiert auf #251: opus/jarvis-voice1-fix-20261030, HEAD 2747f1a0600cba386defeb1a4641338fe8519d18.
- PR #253, VOICE-1-Sitzungsschutz, offen/Draft, basiert auf #252: research/jarvis-voice1-stale-recognizer-session-20261030, HEAD ed408a7d921e47b5730b06f1f514268e249bd5f7. **Dies ist der verifizierte direkte P2-Ausgangspunkt.** Vor Codebeginn unveränderlichen Basissha prüfen.
- PR #245, M9–M16, offen/Draft, SEPARATER Forschungsstrang: opus/continuum-m9-m16-20261030, HEAD 29c1e1e1bef289cddf1e0c7854f2511923b93c0f. Der Stand enthält inzwischen M13–M16 und einen dokumentierten Aufrufketten-Test; die frühere Aussage „M13–M16 noch offen“ ist überholt. Keine ungeprüften Cherry-Picks in #253.
- PR #249, Self-Play/M13-Modulprüfung/Thermal-Gates, offen/Draft, SEPARATER Forschungsstrang: research/continuum-self-evolution-shadow-20261030, HEAD d6a666322db225586b03ddc51f5285c10f39dfe3. Die vom #252-Agenten geprüfte Thermal-Korrektur ersetzt keine Hardwaremessung.
- PR #236 bleibt separater, noch nicht real auf dem Fold7 doppelt verifizierter signierter Updater; keine Kopplung dieses Experiments an Installation/Signierung. PR #237 ist ein eigener geprüfter Dialog-Integrationszweig, keine Erlaubnis zu einem Merge.

Vor jeglicher Mutation: Repository, default branch, PR-Metadaten, vollständige HEAD- und Base-SHAs, Merge-Bases, Branch-Vergleiche, offene Konflikte, AGENTS.md, .github/copilot-instructions.md, docs/CONTINUUM-EXPERIMENTVERTRAG.md, docs/CONTINUUM-EXPERIMENTE.json, Issue #41 sowie alle berührten Quellstellen lesen. Einen eigenen Branch IM GLEICHEN Repository von der tatsächlich geprüften #253-Spitze anlegen und sofort einen Draft-PR auf die #253-Basis öffnen. Niemals auf main schreiben. Weder #245 noch #249 unbemerkt integrieren: erst Divergenz, Abhängigkeiten und konfliktfreie Minimalübernahme beweisen. Für jeden geprüften Meilenstein pushen und vollständige SHA rücklesen.

### 1. Oberstes Systemziel: messbar besseres, voll lokales Continuum

Continuum besitzt bereits Gemma, ContinuumLearningCore, EWM2 mit zeitlicher Faktengültigkeit, NTW1-v3, AiContextCompiler, ChatReasoningRouter / ComputeAllocationPolicy, ARGON, CCC1, BoundedAgent, ToolGate und den bestehenden Repair-/Worker-Pfad. Diese Komponenten und ihre Autoritäten bleiben erhalten.

Entwickle ein kleines, abschaltbares, nicht-autoritatives zeitliches Vorschlagssignal, das dem bestehenden Routing bei zeitabhängigem Kontext tatsächlich hilft, ohne die Belegauswahl, bestätigte Fakten, OwnerGate, ToolGate oder Berechtigungen zu überschreiben. Untersuche bio-neuronal inspirierte Mechanismen nur als falsifizierbare Forschungsinstrumente; keine Begriffe wie „neuronaler“, „liquid“ oder „biologisch“ gelten als Gütenachweis. Keine externe bezahlte KI-API, kein Zwang zu PC/Cloud/Actions; Fold7 ist das Primärgerät, ein lokaler PC nur optionaler Prüfstand. Privatsphäre/Offline-Eigenschaft für ASR, Gemma-Inferenz, TTS, Werkzeuge, Synchronisation und Telemetrie getrennt nachweisen.

**Jetzt keine zweite Architektur entwickeln.** Erst reale Call-Graph-Analyse und kleinster experimenteller Anschluss an die tatsächlich vorhandene Entscheidungsschnittstelle. Der bisherige P1-Vorschlag benennt ComputeAllocationPolicy; vor P2 prüfen, ob genau dort dieselbe operative Entscheidung wie beim ChatReasoningRouter getroffen wird, und das tatsächliche Interface verwenden. Unveränderte Eingabesignale, feste Zeitstempel und identische Entscheidungsbudgets für alle drei Arme.

### 2. Bereits gemessen: EXP-20 P1 ist KEIN Routing-Erfolg

Verifizierte Datei: docs/research/exp20/EXP-20-P1-RESULT-20261030.md auf #252. Bisheriger Code NUR im Test-Quellsatz:
- app/src/test/java/at/heinz/deviceai/research/exp20/BoundedCtrnn.kt
- app/src/test/java/at/heinz/deviceai/research/exp20/BoundedCtrnnTest.kt
- app/src/test/java/at/heinz/deviceai/research/exp20/Exp20CostBench.kt

P1 Host-JDK21, n=16, einmaliger dokumentierter Lauf: CTRNN p50=567 ns, p95=909 ns; EMA p50=163 ns, p95=280 ns. CTRNN damit ungefähr 3,5-mal so langsam im p50. Berichtete Nutzdaten-Speicherbilanz CTRNN=2560 Byte, EMA=256 Byte (nicht mit vollständiger JVM-Heap-Belegung inklusive Objektköpfen gleichsetzen). Gemessene Allokation im markierten Messloop: je 0 Byte. 10.000 numerische Versuche unter spezifizierter Zeilensummenbedingung bestanden; kein Routing-Mehrwert, keine reale Fold7-Latenz, keine Energie- oder Temperaturmessung. Die alten Messungen sind nur Referenz, niemals als neue P2-Messungen ausgeben.

Das P1-Modell implementiert exponentielles Euler mit eingefrorenem Drive je Schritt: h_i' = a_i h_i + (1-a_i)[b_i + clip(x_i) + Sum_j W_ij tanh(h_j)], a_i = exp(-dt/tau_i). Es ist für den linearen Leckanteil eines solchen Teilschritts exakt, **nicht** die exakte Lösung des gekoppelten nichtlinearen Gesamtsystems. Die P1-Implementierung ist dicht (dense), nicht eventgesteuert und nicht sparse. EWC (EXP-21) und STDP (EXP-22) sind noch keine geprüften Produktivimplementierungen. Die gesamte Folgearbeit beginnt daher ausdrücklich bei P2.

### 3. Experimentvertrag P2 VOR Entwicklung einfrieren

Vor dem ersten Optimierungspatch eine P2-Vorregistrierung im Repo committen, SHA-256 von Konfiguration, Split-Zuordnung, Eingabe-Encoder, Goldantworten und versiegeltem Holdout protokollieren. Exakte Hypothese, Falsifikationskriterium, Primärmetrik, Mindestwirkung, statistisches Verfahren, Ressourcenobergrenze, gültige Zeitsemantik, Abbruchkriterien, eine einzige geplante Kodeingriffsvariable und Entscheidungsregel schriftlich fixieren. Keine Daten, P2-Hyperparameter oder Schwellen am versiegelten Holdout lernen.

**Drei kontrollierte Arme:**
- A — echte vorhandene Router-/Allocation-Policy ohne neues zeitliches Signal (Baseline).
- B — EMA / unabhängige Leckintegratoren, im selben Eingabe-Encoder und auf demselben Entscheidungsweg.
- C — bounded Multi-Timescale CTRNN (unveränderte P1-Struktur als Vergleich, nur begründet optimierte Erweiterung).

**Isolationspflicht:** Die drei Arme sehen exakt dieselben öffentlich zugänglichen, gleich vorverarbeiteten Kontextsignale und Zeiten. Keiner darf zusätzlich EWM2-Fakten/Labels, Goldantworten, externe Zeitquellen oder versteckte Modell-Ausgaben erhalten. Zeitstempel müssen normiert aus ein und derselben Quelle stammen. Die vorhandenen Evidenz-, Owner- und Rechteschranken entscheiden weiterhin autoritativ.

**Sechs Pflichtszenarien, jeweils mit Positiv-/Negativkontrollen, unbekannten Namen und veränderten Zeitlagen:**
1. Ortswechsel/Invalidierung (Haus -> Auto, veraltete Situationsannahme vs. weiter gültige Fakten).
2. Explizite Nutzerkorrektur (alte Aussage ersetzen, unabhängige Fakten unverändert).
3. Asynchrone Unterbrechung mit wechselndem dt (kurzfristiger Kontext klingt ab, langfristiges Vorhaben bleibt referenzierbar).
4. Verschachtelte Entitäten/Termine/Reparaturaufträge (keine falsche Querbindung).
5. Gleiche Signalfolge mit 5 Sekunden gegenüber 4 Stunden (allein dt ändert die angemessene Gewichtung).
6. Präsenz- und Gerätedrift (keine selbst erteilte Werkzeugfreigabe oder Geräteaktion).

Bewerte zusätzlich bewusst zweideutige Situationen, wo eine begründete Rückfrage richtig ist. Primärmetrik: korrekte Routing-/Kontextentscheidung je voneinander unabhängiger Dialog-Stammaufgabe. Fehlertaxonomie: FALSE_CONTEXT_SWITCH, MISSED_CONTEXT_SWITCH, STALE_FACT_LEAKAGE, WRONG_ENTITY_BINDING, UNNECESSARY_CLARIFICATION und UNAUTHORIZED_ACTION_SUGGESTION. Bei letzterem ist die zulässige Anzahl null; eine Verletzung beendet den Integrationsversuch.

Daten strikt in TRAIN/DEVELOPMENT (für Konstruktion), VALIDATION (für Hyperparameter), SEALED_HOLDOUT (fremder unabhängiger Autor, für endgültige Entscheidung) trennen. Abwandlungen eines Dialogs zählen als EINE Cluster-Stichprobe. Versionierte Datensatzbelege ohne echte private Gesprächsinhalte; kein Holdout-Leak über Prompt-/Quell-/Testlogs. Der Holdout darf nur einmal für die fixierte Kandidatenauswahl geöffnet werden. Bei unzureichender statistischer Aussagekraft Ergebnis INCONCLUSIVE statt SUCCESS. Für gepaarte binäre Ergebnisse McNemar auf unabhängigen Aufgaben nutzen; für abhängige Varianten clusterweise Bootstrap/Resampling und Holm-Korrektur vorab festlegen. Effektgröße, Konfidenzintervall und absolute Fallzahlen berichten, nicht nur p-Werte. Kein nachträgliches Umetikettieren von Nichtsignifikanz als Erfolg.

### 4. Eventgetriebene analytische CTRNN-Dynamik — mathematisch korrekt

Prüfe getrennt (keine Vermischung mit Qualitätsvergleich) eine ereignisgesteuerte Rechenvariante gegenüber derselben unveränderten dichten CTRNN bei identischen Gewichten, Zustandsdimensionen und Eingaben.

Modell: tau_i * dy_i/dt = -y_i + Sum_j W_ij sigma(y_j + b_j) + I_i(t), tau_i>0, sigma=tanh. Grenzen für tau, Gewichtsnormen, Inputs, Zeitschritte und Zustände validieren. Drive- und Gewichtseinfrierung müssen explizit benannt sein.

**Wichtige mathematische Korrektur:** Wenn I_i=0, bedeutet das NICHT automatisch dy_i/dt = -y_i/tau_i, denn die rekurrente Kopplung Sum_j W_ij sigma(...) kann ungleich null sein. Der reine Zerfall y_i(t+dt)=y_i(t)*exp(-dt/tau_i) ist ausschließlich bei nachweisbar verschwindendem Gesamtdrive exakt. Bei konstant, eingefrorenem Drive d_i ist der exponentielle Schritt y_i'=a_i y_i+(1-a_i)d_i exakt für diesen eingefrorenen Teilabschnitt, aber nur eine Approximation für die nichtlineare gekoppelte Dynamik. Keine falsche analytische „Exaktheit“ behaupten.

Forschung A: Ereignisraten und Lückenverteilung aus reproduzierbaren synthetischen Trace-Sätzen. O(1) Arbeit pro tatsächlich aktualisiertem Neuron ist kein O(1) Aufwand für das gesamte Netz. Die Kosten für die Eventdetektion, Zeitsynchronisation, exp-Berechnung, Dirty-Index-Verwaltung, Bounds-Checks und Wiederherstellung des korrekten Zustands zu einem Abfragezeitpunkt mitzählen.

Forschung B: Sparse-Gate nur aktivieren, wenn vernachlässigte Komponenten einen überprüfbaren Fehlerkorridor einhalten. Miss ||y_sparse - y_dense||_infinity für denselben zeitdiskretisierten Modellvertrag und separat den Fehler beider Varianten gegen eine hochgenaue Referenzintegration der Original-ODE. Fehler in Routing-Entscheidungen separat messen (numerisch nah heißt nicht gleiche Routingentscheidung). Vergleiche mindestens dense, sparse, rein exponentieller Leck-/konstant-Drive-Kandidat, EMA und gegebenenfalls expliziten deterministischen Ereigniszähler.

Forschung C: Kontraktivität nur unter tatsächlich ausreichender geprüfter Bedingung: z.B. rho=max_i Sum_j |W_ij| < 1 bei 1-Lipschitz-tanh und gleichen positiven tau_i, zusammen mit dem in P1 nachgewiesenen Exponential-Euler-Kontraktionsfaktor q=max_i[a_i+(1-a_i)*rho]<1 pro positivem Schritt. Ein Spektralnorm- oder Eigenwert-Kriterium nicht als austauschbaren Ersatz ausgeben, wenn es für die implementierte Norm unzureichend ist. Eine reine Euler-Schrittweitenregel dt < 2 min(tau) reicht mit Rekurrenzkopplung nicht als pauschaler Stabilitätsbeweis; Numerik des tatsächlichen Verfahrens nachweisen. Degenerate Fälle (dt=0, negative dt, NaN/Infinity, tau=0, overflow, Uhrsprung, große dt, Wiederholung, Signalkollision) explizit testen.

Speichermodell exakt trennen: primitive Array-Nutzdaten, JVM-Objekt-/Array-Overheads (gegebenenfalls VM-abhängig), temporäre Allokationen, tatsächlicher Prozess-RSS/PSS und realer Android-App-RAM. Endlichkeit, Determinismus, Gewichtssättigung und bei Eignung Reduktions-/Fehlergrenzen nachweisen. Reproduzierbarkeit bitgenau nur für fixierte ausführbare Umgebung/JVM/Architektur beanspruchen; plattformübergreifende numerische Toleranzen vorab definieren.

### 5. Leistungsentscheidung: Qualität zuerst, Vollkosten ehrlich

Ein neuer zeitlicher Kernel darf nur eingesetzt werden, wenn er **gegen A UND B** eine vorab festgelegte, robuste Verbesserung erzielt und reale Ressourcenobergrenzen einhält. Keine Wahl anhand einer einzigen synthetischen Erfolgsrate.

Messplan: identisches CPU-/JIT-/GC-Warm-up und Eingabetrace, ausreichend Wiederholungen, vorab fixierte Korrektheitsprüfungen und getrennte Hostläufe für p50/p95/p99, Wallclock, Allokationen/Schritt, Zustands-/Konfigurationsspeicher, App-RAM; Bench-Reihenfolge randomisiert und Geräte-/Prozessbedingungen mitprotokolliert. Bei unveränderter Qualität gewinnt die billigere Lösung, insbesondere B vor C, und falls A gleich gut und günstiger ist, A. Bei zusätzlichem Qualitätsgewinn von C aber unvertretbaren Kosten kein Produktiv-Einsatz. Wenn alle Effekte unterbestimmt sind: INCONCLUSIVE/RESEARCH_ONLY.

**Physische Fold7-Validierung strikt separat:** exakte Commit-SHA, Modell-/APK-Hash und Signatur, reale ARM64/Android-Version, ASR/TTS-Provider, Akkuzustand, ThermalHeadroom/Status, Throttling, Messzeitfenster, Power-Profil und Startbedingungen dokumentieren. Ohne Zugriff auf echte Hardware: DEVICE_NOT_RUN, nicht „optimiert auf Fold7“. Host x86 ist kein Android-Energiebeweis; externe Energie-/Temperaturprognosen niemals als Messung ausgeben.

### 6. EXP-20 P2: echter, aber vollständig wirkungsloser SHADOW-Anschluss

Falls und nur falls der versiegelte Test C rechtfertigt, einen kleinen separat deaktivierbaren Shadow-Adapter in den realen bestehenden Router-/ComputeAllocationPolicy-Entscheidungsweg einfügen; technisch muss ausgeschlossen sein, dass er Nutzerausgabe, Belegauswahl, EWM2-Gültigkeit, Owner-/ToolGate-Entscheidungen, Device-Aktionen, Gemma- oder NTW1-Gewichte beeinflusst. Explizites Flag standardmäßig AUS, Ablationstest mit Byte-gleich bleibenden Produktivantworten, kontrafaktisches Logging ohne personenbezogene Rohtexte, Kill-Switch, null persistente Schattenfakten. **Nicht** aus Forschungs-Shadowsignalen bestätigte Fakten lernen.

Die JARVIS-VOICE-1-Fixes aus #252/#253 sind Regression-Gates: verspätete, fremde oder abgelaufene ASR-Sitzungen müssen IGNORE bleiben; keine Texte in neue Sitzungen verschieben; keine parallelen Recognizer oder Echo durch TTS; owner consent und bestehende Sprach-/App-Gates unverändert.

Bei negativem oder ungeklärtem P2-Resultat KEIN SHADOW-HOOK, sondern eine nachvollziehbare Verwerfungsentscheidung samt Rohdaten und Code im Forschungszweig.

### 7. EXP-21: EWC nur auf abgetrennten Lerngewichten

Erst NACH Abschluss P2 + Sparse-Entscheidung, unabhängig vom produktiven Dialog: diagonal approximierte Fisher-EWC erforschen. Verlust L_new + (lambda/2) Sum_k F_kk (theta_k - theta*_k)^2. Gegen exakt dieselben Tasks/Seeds/Budgets getrennt vier Arme testen: naives sequenzielles Lernen, Replay, Fisher-EWC und Replay+EWC. Alte/neue Aufgaben, Retention, Forward-Plasticity, Fisher-Schätzqualität, Norm der Parameterdrift, RAM-/CPU-Zusatzkosten und Trainingseinbrüche vollständig protokollieren. Kein Gemma-/NTW1-/CRE1-/SWB1-Produktivgewicht ändern. EWC nur bei statistisch robustem Zusatznutzen gegenüber Replay weiterverfolgen; sonst REJECTED.

### 8. EXP-22: STDP und Metaplastizität als separates Labor

Separat von CTRNN und EWC die Zeitfolge delta_t=t_post-t_pre, feste Eligibility-Traces, begrenzte Gewichte [w_min,w_max], Vorzeichen/Zeitskalen und harte Sättigung untersuchen. Vergleich mit deterministischem Zähl-/Korrelationstracker bei identischen Speicher-/Rechenbudgets.

Adversarial-Tests: identische Spike-Bursts, Zufallsrauschen, invertierte Reihenfolge, fehlende/doppelte/ungültige Timestamps, Uhrenwechsel, über 10.000 Schritte Drift sowie explizite Kontrollen für Koinzidenz ohne Kausalität. Keine automatische Produktionsfreigabe und kein erklärungsbedürftiges Gewicht ohne Beleg. Bei fehlender Überlegenheit: verwerfen.

### 9. Verbindliche Arbeitsfolge und harte Schutzgrenzen

Reihenfolge: (1) Live-Git- und Call-Graph-Prüfung, (2) Vorregistrierung MIT COMMIT vor erstem Lauf, (3) A/B/C-P2-Test mit sauberem Encoder und unabhängigen Splits, (4) gesondertes dense/sparse/analytisches Experiment samt mathematischen Fehlergrenzen, (5) Shadow-Hook nur bei bewiesenem Vorteil, (6) isolierte EWC- und STDP-Piloten, (7) alle erreichbaren Regressionen und Fold7-Protokoll, (8) Rohbelege/Abschluss/Handoff/rollback per Draft-PR.

Sicherheits-Invarianten unverändert:
- allowSelfReplication = false
- allowHiddenPersistence = false
- allowShutdownEvasion = false

Keine eigenmächtige Weitergabe von Besitzer-/Gedächtnisdaten; kein Umgehen von Besitzerwiderruf, Android-Permissions, ToolGate, CognitiveGenome oder CCC1. Keine zweite produktive Memory-DB, kein paralleler Worker, keine unbeaufsichtigte Modellmutation, kein Main-Merge, kein Force-Push, kein Geräte-Installations-/Signierzugriff. Kein Rebranding synthetischer Ergebnisse als reale autonome Verbesserung. Zeitüberschreitung, fehlende SDKs/Tools, OOM und fehlende Hardware als INFRASTRUCTURE_ABORT / NOT_RUN / UNKNOWN behandeln — nie als Lernsignal. Keine branchübergreifenden unbelegten Testübertragungen.

### 10. Ausführung & Nachweis (nicht nur Papier)

Nutze vorhandene lokale Projektskripte. Tatsächlich ausführbare Kotlin/JUnit/Node-Tests ausführen und Kommando, OS/JDK/Node, Git-SHA, Exit-Code, stdout/stderr, PASS/FAIL/SKIP und Dateiprüfsummen aufzeichnen. Android-Gradle-Aufgaben testDebugUnitTest, lintDebug, assembleDebug nur bei funktionierender Toolchain real ausführen. Niemals Allowlisten, Drift-Checks oder gegebene Freigabeschranken umgehen; keine unverifizierten APKs signieren oder installieren.

Nach jedem validierten Meilenstein committen und pushen, Voll-SHA per GitHub-Readback prüfen, Draft-PR aktuell halten, Handoff im Repository ablegen, damit andere Agenten denselben Stand ohne den aktuellen Chat übernehmen. Alle neuen Tests müssen eine rote Gegenprobe auf der unmodifizierten Basis umfassen, sofern der Test eine Fehlerbehebung behauptet. Kein Abschluss durch bloße Codegenerierung; auch negative Ergebnisse sind wertvoll und müssen gesichert werden.

**Abschlussbericht mit 12 zwingenden Antworten:** (1) welche Dateien/Caller tatsächlich geändert wurden; (2) Baseline-SHA und A/B/C-Identität; (3) eingefrorene Hypothesen und Split-/Holdout-Prüfsummen; (4) echte Befehle und Exit-Codes; (5) absolute richtige/falsche Entscheidungen samt Fehlertypen; (6) statistische Effektgrößen und Unsicherheit; (7) p50/p95/p99 + komplette Speicher-/Allokationskosten; (8) dense-sparse-Abweichung, ODE-Referenzfehler und Routing-Abweichung; (9) Trennung Host versus physisches Fold7; (10) Gewinner A/B/C gemäß Vorabregel; (11) ehrliche Grenzen/Blocker/EWC-STDP-Status; (12) vollständige SHAs, Draft-PR, Rückbau-/Revert-Anweisung und konkrete zusätzliche Fähigkeit für Continuum.

**STARTBEFEHL:** Beginne sofort mit Live-Readback von #251/#252/#253 und den abweichenden #245/#249-Strängen. Konzentriere die Entwicklung auf EXP-20 P2, nicht auf weitere unbewiesene Neuronenteile. Implementiere, miss, widerlege oder bestätige. Wenn CTRNN nicht gewinnt, nimm EMA oder den unveränderten Router. Kein Erfolg ohne reproduzierbare Rohbelege.
