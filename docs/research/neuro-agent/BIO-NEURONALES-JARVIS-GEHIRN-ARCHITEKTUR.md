# CONTINUUM JARVIS · Architektur des bio-neuronalen Gedächtnisses
**Entwurfsstand:** 30. Oktober 2026 · Forschungs-Branch PR #268 · keine produktive Aktivierung oder Fold7-Abnahme.

## 1. Die eine autoritative Gehirnarchitektur

Hauptchat / Mikrofon (Android Audio, freigegebene ASR) → vorhandenes ContinuumCognitiveRuntime / LocalConversationContext → zeitgültige und autorisierte EWM2/SWB1/CRE1-Evidenz → vorhandener AiContextCompiler → vorhandenes lokales Gemma/LiteRT-LM → evidenzgebundene Antwort → freigegebene TTS.

Parallel, nur als Shadow: bereits ausgewählte, gültige Belege → Merkmalsextraktion *ohne Besitzergeheimnisse* → TemporalNeuralShadow → scalar/vector-Diagnose → Vergleichsbericht. Die neuronale Schicht darf weder Belegbestand noch Widerruf noch Antwortautorität schreiben. Die Arbeit an Semantik (M5) bleibt wichtiger als neue Modellgröße.

Autonomer Entwickler: CCC1 CodingTask → aktueller TaskFingerprint + Quell-Commit → optionaler lokaler oder NVIDIA-Proposal-Teacher → unvertrauenswürdiger Patchvorschlag → **bestehender** Critic/OS-isolierter CCC1-Executor → RED/GREEN/FRESH-GREEN, Receipt und Owner-Gate → Review-Branch. Kein neuer Worker/Journal/Signer; kein autonomer Wechsel zu main, kein Code-Selbstreplikator. Echte Produktverbesserung wird nur bei verifizierter, zuvor unbekannter Reparatur anerkannt.

## 2. Bereits vorhandene Bauteile und wissenschaftliche Grenzen

- PR #242: Argon-F1-Kontext-/Beleg-/Routing-Shadows im echten Android-Aufrufpfad; 1268 Android-JUnit auf Host, keine nachgewiesene Fold7-NPU/GPU-Inferenz dieses Branches.
- PR #256: robuste M5-Semantik nicht erreicht; CRE1 bindet Rollen und neue Ausdrücke nicht zuverlässig. EWM-v2-Ersetzungsfix ist eigenständiger Kandidat, kein abgeschlossener Holdout-Transfer.
- PR #71 / #73: Synapse Zero / V2, 4096-Parameter trainierbarer Rollenencoder, HRR/HDC-Proben und OOD-Fehlschlag auf neuen Formulierungen. Alter Test verbraucht, neuer eingefrorener Split nötig.
- PR #163: COIN-WM neuronaler Shadow plus EWC-Forschung; kein gesicherter Generalisierungsvorteil und kein sicherer node:vm-Code-Sandbox.
- PR #251: Forschungsprogramm EXP-18 HDC/VSA, EXP-20 CTRNN, EXP-21 Fisher/EWC, EXP-22 STDP, EXP-23 Thermals. Forschungsziele, nicht als produktiv nachgewiesen.
- PR #265: M8 Coding-Agent und Update-Button-Integration in separatem Branch; simulierter Modelltest / fehlende Fold7-Abnahme. Kein blindes Merge in PR #268.
- PR #268: Cloud-GPU Admission, NVIDIA 30B Synthetic Coding Lab, 550B Master Review, Research Cycle; ist Research-Shadow, keine GPU-Bereitstellung und keine Echtzeit-App-Anbindung.

## 3. Mathematische Kurzzeitdynamik

Der neue isolierte Kotlin-Baustein liegt unter app/src/main/java/at/heinz/deviceai/research/neuro/TemporalNeuralShadow.kt. Er ist **nicht** mit produktiver EWM2-Antwortautorität verbunden.

Kontinuierliche Zielgleichung: dx_i/dt = (-x_i + tanh(sum_j W_ij x_j + u_i))/tau_i.
Bei einem eingefrorenen *alten* rekurrenten Antrieb d_i = tanh(sum_j W_ij x_j(t_k)+u_i) erfolgt das Zeitschritt-Update exakt für diesen ZOH-Antrieb:
alpha_i = exp(-Delta t / tau_i),
x_i(t_k+Delta t) = alpha_i*x_i(t_k) + (1-alpha_i)*d_i.

Das ist **nicht** die exakte nichtlineare ODE-Lösung, wenn sich x(t) im Antrieb während des Intervalls ändert. Für sigmoid=tanh ist die Lipschitzkonstante 1. Die im Kotlin-Shadow erzwungene Schranke max_i sum_j |W_ij| < 0,95 ist eine hinreichende Kontraktivität des diskreten Schrittes bei positivem Delta t (Infinity-Norm); die Schrittbegrenzung ist max_i [alpha_i+(1-alpha_i)*sum_j |W_ij|] < 1. Bei Delta t=0 wird kein Fortschritt behauptet. expm1 vermeidet subtraktive Auslöschung für sehr kleine Intervalle. Die Inputfeatures sind endliche reelle Werte zwischen -1 und 1; Dimension 1 bis 32. Nach Zeitlücken größer maxGapMs und bei Thermal-Denial/Belegwiderruf werden Zustände gelöscht. Reset ist kein Gewichtslernen.

Behauptete Vorteile müssen auf einem **neuen**, unabhängigen Fold7-Holdout gemessen werden: korrekte Referenzauflösung, zeitliche Konflikte, Abstentionsrate, p50/p95, CPU-Zeit, elektrische Energie, Speicherbedarf. Selbst ein stabiler dynamischer Zustand beweist noch keine verbesserte Intelligenz.

## 4. Gedächtnis-Pipeline / Versionierung

Ein EWM-Fakt braucht: owner scope, subject, relation, object, Quellen-ID, createdAt, validFrom, optional validUntil/revokedAt, eigene Versions-/Belegbindung, Berechtigungszustand. Vorhandene EWM2-APIs entscheiden Gültigkeit; neuronale Aktivität höchstens als Shadow-Prognose. Neue Aussage ersetzt alte nur bei belegter Korrektur, widersprechende Aussagen ohne Auflösung führen zur Rückfrage. Kein Retention-Training auf echten Nutzerchats als Standard.

Die EWC-Hypothese verwendet L = L_task + lambda/2 * sum_i F_i*(theta_i-theta_old_i)^2, F_i ≥0 aus einer separaten alten Validationsmenge. Neue Lerngewichte dürfen nur im Forschungszweig ablationiert werden. Persistente Modellgewichte, EWM2 und Genome bleiben unverändert bis Owner-/Holdout-Abnahme.

## 5. Sparse-Gates, Hardware und Stimme

HDC/Spiking/CTRNN sind optional. Ein eventbasiertes Shadow-Feature wird nur bei tatsächlich neuem, gültigem Beleg oder geklärter Follow-up-Frage berechnet; kein Dauer-Takt, kein Hintergrund-Training bei Thermal-Alarm. Fold7 12 GB physischer RAM, RAM Plus keine zusätzliche GPU. GPU/NPU nur über tatsächlich kompatible, getestete LiteRT/Qualcomm-Runtimes. NVIDIA Ultra über HTTPS ist entfernter 550B-Teacher, nicht Teil des lokalen 12-GB-RAM; keine Cloud-Behauptung ohne Response-Receipt.

Jarvis-Sprache erfordert mikrofonbewusste Android-Freigabe, echte Offline/Cloud-ASR-Bestimmung, Zustands-/Unterbrechungsverwaltung, Verbindungsabbau und TTS. Verfügbarer Quellcode zu Audio-Dialog-/Wake-Word-Pfaden ist vor Integration zu prüfen. Keine Dauer-Mikrofonaufnahme oder Accessibility/Foreground-Umgehung.

## 6. Dauerhafter Forschungsworker

scripts/continuum-neuro-agent.mjs setzt **keine endlose, finanzielle Vollmacht** um. Ein langlebiger externer Node-22-Host kann eine Folge von NVIDIA-Aufrufen fahren: jeweils eine resevierte Anfrage, ein SHA-gebundener Bericht, fachlich wechselnder Schwerpunkt, ein explizites Besitzer-Consent-Flag; Standard 3/UTC-Tag, 24 insgesamt, mindestens 15 Minuten Intervall. Fehler und Quotenende brechen die Aufrufkette kontrolliert ab. Ein Lock verhindert parallele Prozessstarts. Anonymisierte technische Quellen werden an NVIDIA übertragen; Anbieter kann Inputs/Outputs gemäß dessen Nutzungsbedingungen protokollieren. Kein Token wird in Chat, Git, Branch oder Bericht ausgegeben.

Lokale Quelländerungen bleiben Aufgabe des bestehenden geprüften CCC1-Workers. Auch bei Git-Opt-in werden **nur Modell-unabhängige Belege** als lokale Commits auf dem Research-Branch gesichert. Kein automatisches Push, Merge, neuer Signierschlüssel, APK-Update oder Lernen aus ungeprüfter Fremdausgabe.

## 7. Abnahmetore und nächste konkrete Meilensteine

**N1 – Numerischer Kern:** vorhandenen TemporalNeuralShadow nativ mit Kotlin/JVM und unabhängigem, unverändertem Test samt Kontraktivität, Reset, Zeitüberlauf, Nicht-NaN und Permutationsfällen prüfen. Nur shadow.
**N2 – M5/OOD:** neuen versiegelten Rollen-/Negations-/Referenten-Holdout vor der Kandidatenauswahl fixieren. Baseline EWM2/CRE1, +HDC, +Synapse, +CTRNN messen. Test nicht mehrfach zum Tuning öffnen. Erfolg nur bei signifikanter Qualitätsverbesserung ohne Verschlechterung autoritativer Fakten.
**N3 – EWM-M6:** mindestens 200 unabhängige Versionierungs-/Ersetzungspaare mit Gegenfakt und unterbrochenem Widerruf. EWM2 ist autoritativ.
**N4 – M8 realer Repair:** drei vorher unbekannte, commit-gebundene Fehler, echter genehmigter NVIDIA-Teacher, OS-isolierter CCC1-Executor, RED/GREEN/FRESH GREEN und Reviewer-Belege. Simulation zählt nicht.
**N5 – Fold7 JARVIS-Stimme:** ASR→Gemma→TTS mit p95-Latenz, Akku, Thermals, Datenschutz, Unterbrechung und Originalsignatur nachgewiesen.
**N6 – echte Verbesserung:** mindestens drei unabhängige, vom Besitzer angenommene und regressionsgeprüfte Reparaturen; keine unautorisierte Rechteausweitung.

## 8. Reproduzierbare Befehle (nur nach Installation auf externem Host)

node --test scripts/continuum-neuro-agent.test.mjs scripts/continuum-nvidia-ultra-review.test.mjs
node scripts/continuum-neuro-agent.mjs --check
node scripts/continuum-neuro-agent.mjs --once
node scripts/continuum-neuro-agent.mjs --loop

Der korrekte Endpunkt lautet https://integrate.api.nvidia.com/v1/chat/completions (nicht https://nvidia.com); über NIM für Ultra werden max_tokens=16384 und chat_template_kwargs.enable_thinking=true gesendet. Browser-Anmeldung allein ist kein aktiver API-Zugang. Zwei zuvor im Chat gepostete Keys müssen widerrufen und ein neuer Key ausschließlich in Secret-/Umgebungsvariablen des ausführenden Hosts hinterlegt werden.

**Status:** Architekturdatei + Researchworker vorbereitet; echtes NVIDIA-550B-Gutachten, kontinuierlicher Host-Betrieb, automatische Code-Verbesserung und Fold7-Erfolg weiterhin nicht nachgewiesen.
