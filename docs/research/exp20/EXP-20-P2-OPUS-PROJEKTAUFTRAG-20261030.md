# CONTINUUM JARVIS ULTIMATE – EXP-20 P2 · Projektbezogener bio-neuronaler Entwicklungsauftrag an Claude Opus 5.5

**Modus:** Höchstes Reasoning · autonome, falsifizierbare Forschung · Kotlin/Android · SHADOW-Integration · verifizierte Messung.
**Projekt:** Device AI Continuum / JARVIS Ultimate.
**Repository:** `Ullmann27/device-ai` · **Paket:** `at.heinz.deviceai.continuumlocal` · **Ziel:** Samsung Galaxy Z Fold7 / ARM64.

## 0. Auftrag / Dan-Books-Forschungsrahmen

Übernimm die operative Forschungsleitung für **genau eine** nächste Fähigkeit: Kann ein dynamisches, bio-inspiriertes Kontextsignal die bestehende **Compute-Allocation-/Routing-Entscheidung** bei zeitlichen Kontextwechseln nachweisbar verbessern? Entwickle sparsam, reproduzierbar und mit gleicher Information für jeden Vergleichsarm. Es geht um algorithmisch bessere Arbeit auf begrenzter Hardware, nicht um die Anzahl neuronaler Module.

**Hauptziel:** EXP-20 P2, kontrollierter Dreiwege-Vergleich **unveränderter Router vs. EMA vs. CTRNN** auf zuvor unabhängigen zeitlichen Dialogaufgaben. Eine Sparse-/Event-Driven-Variante ist nur ein begrenzter zweiter Teil **nach** dem Qualitätsvergleich. EXP-21/Fisher-EWC und EXP-22/STDP bleiben separate Folgearbeiten, wenn P2 fertig und belastbar ist.

**Keine theoretische Überlegenheit behaupten. Keine parallel produktiven Gehirne. Keine ungeprüfte Owner-/Tool-Freigabe.**

## 1. Exakter GitHub-Ausgangspunkt – LIVE verifizieren

Am **30.10.2026** war der letzte geprüfte Sprachintegrationszweig **PR #253** `research/jarvis-voice1-stale-recognizer-session-20261030`, vollständiger HEAD `ed408a7d921e47b5730b06f1f514268e249bd5f7`, auf **PR #252** `opus/jarvis-voice1-fix-20261030`, HEAD `2747f1a0600cba386defeb1a4641338fe8519d18`. Weitere Linien: **PR #251** `397b25ecd23a004ab203e0e476d9df3425a67f33`, **PR #249** `d6a666322db225586b03ddc51f5285c10f39dfe3` (von #248), **PR #245** `29c1e1e1bef289cddf1e0c7854f2511923b93c0f`. Dies sind Ausgangshinweise; vor Arbeit **neue volle HEADs, Bases, Merge-Bases und Divergenzen prüfen**. Keine parallelen Linien blind zusammenführen. Isolierten, editierbaren Branch und Draft-PR wählen, `main` nie ungeprüft verändern.

Verbindlich die **tatsächlich vorliegenden** Dateien lesen:

- `AGENTS.md`, `.github/copilot-instructions.md`, `docs/CONTINUUM-EXPERIMENTVERTRAG.md`, `docs/CONTINUUM-EXPERIMENTE.json`.
- `docs/research/jarvis/JARVIS-OWNER-RESILIENCE-AND-NEURODYNAMICS-20261030.md`.
- `docs/research/exp20/EXP-20-P1-PREREGISTRATION-20261030.md` und `EXP-20-P1-RESULT-20261030.md`.
- `app/src/test/java/at/heinz/deviceai/research/exp20/BoundedCtrnn.kt`, `BoundedCtrnnTest.kt`, `Exp20CostBench.kt`.
- `app/src/main/java/at/heinz/deviceai/research/argon4/ComputeAllocationPolicy.kt`, `app/src/main/java/at/heinz/deviceai/cognitive/ChatReasoningRouter.kt`, `AiContextCompiler.kt` und die zugehörigen Originaltests.
- Für die Telefon-Abnahme die bestehenden Android-Test-/Builder-Skripte, insbesondere `scripts/continuum-fusion-full-regression.sh` und `scripts/continuum-mobile-builder.mjs`.

**Tatsächliche Architektur:** `BoundedCtrnn` existiert zurzeit **nur im Android-JVM-Test-Source-Set**, **ohne Produktivaufrufer**. `ChatReasoningRouter.advise` berechnet heute eine `argonShadowChoice` per `ComputeAllocationPolicy.choose(Signals)` im SHADOW-Modus, ohne dadurch Modellaufrufe, Ausgaben, Tools oder Deadlines zu verändern. Daran die P2-Forschung orientieren. Keine erfundene Schnittstelle wie `ChatReasoningRouter.routeWithCtrnn` verwenden; tatsächliche Signaturen lesen.

**Registerstatus:** EXP-20 steht `PROPOSED_SHADOW`, obwohl der isolierte P1-Laborpilot nachgewiesen ist. Das ist **kein** Nachweis aktiver Android-Integration. EXP-21/EWC und EXP-22/STDP ebenfalls nicht produktiv.

## 2. Feststehende P1-Ausgangsmessung – kein Fold7-Beweis

EXP-20 P1: `BoundedCtrnn`, 16 Neuronen, dichte Kopplung, exponentieller Euler mit im Schritt eingefrorenem nichtlinearem Antrieb. Für `ρ = max_i Σ_j |W_ij| < 1` ist der **diskrete Schritt** in der Unendlichnorm unter identischer Eingabe kontraktiv mit `q = max_i[a_i+(1-a_i)ρ] < 1`, `a_i=exp(-Δt/τ_i)`. P1 zeigte 0 Verletzungen bei 10.000 numerischen Testfällen und keine beobachteten Step-Allokationen im gezielten Hostbenchmark.

**Nur Host (JVM), alte Quellversion:** CTRNN p50 **567 ns**, p95 **909 ns**, EMA/leaky integrator p50 **163 ns**, p95 **280 ns**; Zustands-/Strukturbilanz CTRNN **2.560 B**, EMA **256 B** für den P1-Aufbau. Der CTRNN-Schritt war ca. **3,5×** so teuer. Diese Werte sind weder Fixwerte künftiger P2-Implementierungen noch Fold7-Latenz oder gemessene Energie. P1 prüfte **keinen** Routing-Nutzen. Das dichte CTRNN feuert nicht automatisch sparse und arbeitet nicht nur bei Ereignissen.

`EXP-20-P1-RESULT-20261030.md` enthält den bestehenden Messvertrag und Rohbelegpfad; unverändert archivieren.

## 3. P2 vor Code und Optimierung VORREGISTRIEREN

Erstelle zuerst eine eigenständige P2-Präregistrierung mit: überprüfbarer Hypothese, genau einer primären Kontext-Routing-Entscheidung, unveränderter Baseline, gleichen zugänglichen Eingaben für alle Arme, festen Entwicklungs-/Validierungs-/versiegelten Abschlusssätzen, Primärmetrik, Mindestqualität und p50/p95/p99/RAM-Budget, festgelegten Parameterrastern, Abbruchkriterien und Auswahlregel. Der **unabhängige Holdout** muss von einem getrennten Akteur erzeugt oder vorher extern verschlossen und sein Inhalt/Hash fixiert werden. Ist dies nicht möglich, nur Entwicklungs-/Validierungsergebnisse berichten; **keinen unabhängigen Erfolg behaupten**.

Eine Stammkonversation mit vielen Varianten ist **eine** unabhängige Einheit, nicht hunderte Stichproben. Keine Vorab-Auswahl mit den Abschlusstest-Labels. Eine unabhängige Prüfinstanz muss die Labels und den versiegelten Datensatz vor Freigabe verwalten. Für Paarvergleiche ggf. McNemar auf wirklich unabhängigen Dialogeinheiten, Unsicherheitsintervalle berichten, keine p-Wert-Magie aus stark korrelierten Ereignissen.

**Informationsparität:** Zeitstempel/Δt, Gesprächs-ID, zeitliche Evidenz, Status und relevante Tool-/Präsenzsignale sind entweder für **alle drei** Verfahren verfügbar oder für keines. Keine heimliche Hint-Spalte und kein Wissen aus späteren Ereignissen. Besitzerfakten und EWM2-Zeitlinie bleiben Ground Truth, nicht neuronale Gewichte.

## 4. Genau eine messbare Schnittstelle in unserer Architektur

Nutze die bereits vorhandene `ComputeAllocationPolicy.Signals` mit `EvidenceDecision`, `coverage`, `claimFlags`, `unresolvedReference`, `freshnessNeeded`, `toolIntent`, `personal`, `modelReady`, `internetAllowed`, `lowBattery`, `throttled` und `remainingModelCalls`. Sie ist der **erste experimentelle Entscheidungspunkt**. Das bisherige `ComputeAllocationPolicy.choose(Signals)` ist nicht durch P2 zu überschreiben; es ist die baseline für einen kontrafaktischen Vergleich.

Definiere vor dem Test eine **kleine und nachprüfbare** Abbildung eines zeitlichen Kontextsignals auf **einen** vorher festgelegten, ohnehin vorhandenen Routing-Aspekt (z. B. vorgeschlagene Rückfrage bei unklarer/veralteter Referenz). Die Testwahrheit muss diese Aufgabe explizit abbilden. **Keine erfundenen Sensordaten oder verifizierten Quellen** und keine Änderungen der harten Feasibility-/Owner-/Tool-Bedingungen. Bei fehlender Evidenz/unklarem Bezug nur `CLARIFY`/`ABSTAIN` als sichere Optionen. Prüfe, ob die gewählte Routingaufgabe mit den vorhandenen Signaltypen überhaupt entscheidbar ist; falls nicht, zuerst nur isoliertes Forschungs-Scoring und einen dokumentierten Schnittstellenbedarf liefern, keine produktive Router-Signatur mutieren.

**Vergleich A:** unveränderte Routerentscheidung ohne zusätzlichen Zeit-Zustand.  
**Vergleich B:** einfacher EMA-/Leaky-Integratoren-Zustand mit denselben Merkmalen und Zeitstempeln.  
**Vergleich C:** vorhandenes `BoundedCtrnn` oder gezielt optimierte, kontrollierte Testvariante, identische Merkmale und Zeitinformation.

Für jedes Ereignis aufzeichnen: goldene Routingklasse, A/B/C-Vorschlag, Fehlerart, Ablehnungs-/Rückfragefall, Δt, Kontextzustandsversion, Kosten inklusive Input-Encoding und Gate-Kosten. Modellbewertung bleibt **SHADOW** und darf weder sichtbare Antworten noch lokale Modellaufrufe, Kalender/ToolGate, EWM2 oder TTS verändern.

## 5. P2-Prüfszenarien – unabhängige, zeitkritische Dialoge

Verwende sechs Szenarien als **Aufgabengruppen**, nicht als einzige sechs Testfälle:

1. Orts-/Gerätewechsel Haus → Auto/Unterwegs: veraltete Annahme erkennen, unabgeleitete Besitzerfreigaben beibehalten.
2. Explizite Korrektur einer Besitzerangabe: alte Aussage nicht mehr als aktuell verwenden; unabhängige Fakten unangetastet.
3. Pausen von Sekunden bis Stunden: kurzlebige Hinweise verfallen; dauerhafter Auftrag nur über EWM2/Bestätigung, nicht aus 16 Zahlen rekonstruiert.
4. Verschachtelte Entitäten und Reparaturaufträge: kein Cross-Turn- oder Cross-Session-Leak.
5. Identische Eingangssignale mit Δt=5 s vs. Δt=4 h: erwartete Routingreaktion **vorab** durch die Aufgabensemantik definieren.
6. Unklare Anwesenheit, zweite Personen, gealterte Sensorwerte und Standortdrift: keine Werkzeug-/Fahrzeug-/Herd-Aktion ohne gesonderte Besitzerfreigabe.

Nicht nur Treffer zählen: **false context switches, missed switches, stale-fact leakage, unnecessary clarifications** getrennt ausweisen. Insbesondere sichere Abstention nicht als Fehler labeln, wenn kein Beleg existiert. Auch die vollständigen Fehlerkosten der falschen Antwort erfassen.

## 6. Kernmathematik: Analytik, Sparse-Gating, Fehlernachweise

Das reale P1-Modell lautet:  
`τ_i · dh_i/dt = −h_i + Σ_j W_ij tanh(h_j) + x_i + b_i`.  
Seine vorhandene Implementation verwendet `h_i ← a_i h_i + (1−a_i)drive_i(h_t)` mit `a_i=exp(−Δt/τ_i)`. Dies ist **Exponential Euler**, *exakt für den linearen Leak-Term bei im Schritt festgehaltenem Drive*, **nicht** die analytische Lösung des gesamten rekurrent gekoppelten Netzes.

**Wichtige Korrektur des gelieferten Hypothesentextes:**  
`h_i(t+Δt)=h_i(t)exp(−Δt/τ_i)` ist nur dann exakter homogener Zerfall, wenn **auch die gesamte additive Antriebssumme** verschwindet: `Σ_j W_ij tanh(h_j)+x_i+b_i=0` über das Intervall. `x_i=0` oder „kleine Kopplungsänderung“ allein reichen nicht. Für einen tatsächlich konstanten und nachgewiesenen Antrieb `d_i` gilt dagegen `h_i(t+Δt)=a_i h_i(t)+(1−a_i)d_i`. Wenn sich die rekurrente Kopplung mit dem Zustand ändert, muss ein Fehlerterm konservativ abgeschätzt werden; bei nicht nachweisbarer Bedingung den **dichten Referenzintegrator** verwenden.

**Sparse-Kandidat:** Entscheide anhand eingefrorener, vorab dokumentierter Schwellen `θ_j`, ob eine Änderung der präsynaptischen Aktivierung ausgelassen werden darf; das ist nicht dieselbe Sache wie sparse Neuronen oder sichere Energieersparnis. Für `tanh` gilt Lipschitz-Konstante 1, sodass pro Zielneuron der unmittelbar ausgelassene Synapsen-Drive durch `δ_i ≤ Σ_(j ausgelassen) |W_ij| · |Δh_j|` begrenzt ist. Bei `|Δh_j|≤θ_j` folgt `δ_i≤Σ_(j ausgelassen)|W_ij|θ_j`. Diese Schranke muss auf die tatsächlichen Caches, Toleranzen, laufenden Δt und Reaktivierungsregeln übertragen werden; andernfalls ist „Sparse“ nicht sicher.

Für den diskreten P1-Schritt unter `ρ<1`: `q=max_i[a_i+(1−a_i)ρ]<1`. Eine konservative, begründete Schrittfehlergrenze ist `E_(k+1) ≤ q_k E_k + max_i((1−a_i)δ_(i,k))`, sofern Eingaben, Zustandscaches und Hypothesen die entsprechende Vergleichsannahme erfüllen. Über mehrere Schritte rekursiv akkumulieren und gegen das dichte Referenzmodell messen (`||h_sparse−h_dense||_∞`). **Keine allgemeingültige Bound aus dieser Formel behaupten, solange die Cache-/Gating-Annahmen nicht gezeigt wurden.**

Vergleiche eine sparsame Ereignisfortschreibung nur mit tatsächlich eingefrorener Kopplung oder bewiesenen Fehlergrenzen. Beachte: Wichtige späte Korrekturen oder Gerätewechsel müssen das Gate unabhängig von ihrer numerischen Amplitude aufwecken. Vorab definierte Fail-Open-zum-dichten-Modell-Regel für Ausfall des Sparse-Fallbacks; **Fail-Closed für Eingaben und Besitzerrechte**.

**Stabilität:** `τ_i>0` und endlich, alle Gewichte/Eingaben/Zustände begrenzt; ungültige Input-Werte verändern keine bestätigten Zustände. Bei explizitem Euler allein ist `Δt < 2 min τ` nur für entkoppelte reine Leaks hinreichend, nicht generell für gekoppelte CTRNN. Für Exponential-Euler gelten die spezifischen vorhandenen P1-Kontraktionsannahmen, nicht ein pauschales A-Stabilitätsversprechen. Eine als „bitgenau“ bezeichnete Reproduzierbarkeit ist nur innerhalb derselben kontrollierten JVM/Compiler/Hardwarekonfiguration sinnvoll; bei JDK-vs-ARM64 zusätzlich numerische Toleranzen separat festlegen.

**Vollkosten:** Initialisierung, Encode, Zeitvalidierung, Gate/Schwellensuche, Index-/Cacheverwaltung, Exponentialfunktionen, Speicher, Accuracy-Verifikation und Rückfallläufe mitmessen. Im Idle keine permanente Rechenschleife behaupten, wenn tatsächlich ein regelmäßiger Tick stattfindet. P1-Zahlen der JVM nicht als Fold7-Energiegewinn behandeln.

## 7. Messung, Selektion und Experimentabbruch

Vorab fixierte, für alle A/B/C identische Ressourcenbudgets. Berechne Accuracy je unabhängiger Aufgabe und die vier Fehlertypen, inklusive Zeit bis zur Antwort und SAFE-ABSTENTION. Messe p50/p95/p99 pro entscheidendem Ereignis sowie CPU-Zeit, Gesamtzustand+Gewichte+Caches, Allokationen und Peak-RSS. Benchmark deterministisch wärmen und zu Quell-SHA, Seed, Geräteprofil, JVM-/Compiler-Version binden. Keine unbelegten „0 Memory-Leaks“ aus einer ThreadMXBean-Schleife ableiten.

**Eisernes Auswahlgesetz:** Wenn C bei gleicher/geringerer Qualität höhere Vollkosten als B hat, **EMA**. Wenn C keinen vorab definierten, unabhängigen Qualitätsgewinn bei vertretbaren Kosten zeigt, **EMA**, sofern EMA seinerseits gegen A sinnvoll ist. Wenn B und C gegen A keinen Gewinn zeigen, **unveränderte Baseline**. Negative Ergebnisse sind gewollt und zu sichern.

Die primäre P2-Auswertung bleibt **Host-Labor** bis echte Fold7-Messungen von gleicher Quellversion und gleichen Eingaben vorliegen. Fällt der Holdout-/Hardwarezugriff aus, melde `UNKNOWN/NOT_RUN` statt einen Gewinner zu behaupten. Keine teure Nebensuche ohne klaren Beitrag zu diesem Ziel.

## 8. Keine verfrühte Router-Aktivierung; begrenzter SHADOW-Anschluss

Ein Adapter zu `ChatReasoningRouter` wird erst erwogen, wenn P2 im unabhängig verwalteten Holdout überzeugende Evidenz liefert. Er ist dann **abschaltbar und Shadow-only**: Vergleichslog mit versionsgebundener Provenienz, keine Kontrolle über `advise`-Ergebnis, LLM-Aufrufe, `ARGON`, `ToolGate`, EWM2, Eigentümerfakten, Nutzerantwort, Sprachdialog oder Produktivgewichte. Vorhandene `argonShadowChoice`-Beobachtung nicht verdrängen oder konkurrierende Schatten-Router erzeugen. Kein zusätzlicher privater PII-Log.

## 9. Folgearbeiten EXP-21 und EXP-22 – ausdrücklich getrennt

**Erst nach Abschluss von P2** eigene Forschungsrunden:
- **EXP-21** diagonal-Fisher-EWC: vier gleiche Daten-/Ressourcenarme (naives Weiterlernen, Replay, EWC, Replay+EWC), Alt-/Neuaufgaben getrennt, Gewichtsdrift, Fisher-Fehler, Replay-Kosten, Checkpoints. EWC vermindert Vergessen möglicherweise, garantiert es nicht. Keine produktiven Gemma-/NTW1-Gewichtsänderungen.
- **EXP-22** STDP/Metaplastizität: zeitlich invertierte Paare, Störsignale, doppelte/fehlende/nonfinite Zeitstempel, ≥10.000 Schritte Drift, bounded weights/eligibility traces, Vergleich mit einfachem zeitlichen Korrelationszähler; zeitliche Koinzidenz ist keine bewiesene Kausalität. Keine produktive Geräteaktionsfreigabe.

## 10. Android-/Toolchain-Gate und Sicherheit

Prüfe `testDebugUnitTest`, `lintDebug` und `assembleDebug` nur mit tatsächlich vorhandener SDK-/Toolchain und vorhandenen Task-Allowlisten; fehlende Toolchain = `NOT_RUN` mit konkretem Exit/Blocker, niemals APK-Erfolg. Auf Fold7 erst auf **derselben echten Quell-SHA** mit kontrollierten Audio-/Sensor-/Thermal-Bedingungen messen; Android `getThermalHeadroom` zeigt bei höheren Werten stärkere thermische Annäherung an Drosselung und kann NaN sein. ASR über `EXTRA_PREFER_OFFLINE` ist keine Offlinegarantie.

Dauerinvarianten von `CognitiveGenome`:
- `allowSelfReplication = false`
- `allowHiddenPersistence = false`
- `allowShutdownEvasion = false`

Kein autonomes Merge nach `main`, kein Force-Push anderer Arbeit, keine unautorisierte Installation oder Signierung, keine Stop-/Owner-/Sandbox-/ToolGate-Umgehung. Simulation ist kein Nachweis produktiver Sicherheit.

## 11. Abschlussvertrag – keine Architekturankündigung statt Code

Priorität 1: **P2-Präregistrierung + unabhängiger Holdout + A/B/C mit echten ausgeführten Tests**.  
Priorität 2: Nur wenn realer Nutzen/Fehlerrahmen es trägt, **genau ein** zusätzlicher Sparse-/Inter-Event-Kandidat samt Worst-Case-/Vollkostenvergleich.  
Priorität 3: Ein Shadow-Adapter nur bei bewiesenem Nutzen; EWC/STDP bleiben separate PRs.

Sichere getesteten Code, Seed/Datenzugänge, SHA-256 der versiegelten Daten, vollständige HEADs, Befehle, Exitcodes, PASS/FAIL/SKIP, Latenz-/RAM-Rohdaten, nachvollziehbare Baseline und Rückfallweg in einem **isolierten Draft-PR**. Dokumentiere Fehlversuche und unbekannte Ergebnisse.

**Berichte exakt:** Was war neu? Welche bestehenden APIs wurden unverändert genutzt? Welche Hypothese war falsch? Welche drei A/B/C-Fehlerraten und p50/p95/p99/RAM wurden auf welchem Host/Gerät gemessen? Ist ein Vorteil reproduziert und unabhängig geprüft? Warum gewinnt A, B oder C? Welche Tests und Builds sind tatsächlich ausgeführt? Welche Blockade bleibt? Vollständige Quell-SHAs und präzise Revert-Commits?

**BEGINNE JETZT:** Prüfe live PR #253/#252/#251 und die P1-Laborquellen; registriere P2 vor den Experimenten. Implementiere den kleinsten echten, falsifizierbaren Drei-Wege-Kontextvergleich an der vorhandenen `ComputeAllocationPolicy`. **Zuerst Qualität unabhängig beweisen; erst danach Sparse-Optimierung. Keine Schein-Gewinne.**