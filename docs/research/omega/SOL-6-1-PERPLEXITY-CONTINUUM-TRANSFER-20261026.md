# TECHNISCHE VOLLUEBERGABE AN SOL 6.1 (DENKMODUS / PERPLEXITY)
Stand 26.10.2026; neu eingestiegener unabhängiger Reviewer mit GitHub-Lesezugriff.
**NUR** Device AI Continuum, Repository: https://github.com/Ullmann27/device-ai
Das Lern-/Spielprojekt Lumo gehört NICHT in diese Untersuchung.

## 0. MANDAT
Du erhältst hier den gesamten notwendigen Stand, weil du die vorangegangene Unterhaltung NICHT kennst. Bitte lies zuerst die tatsächlichen GitHub-Quellen und prüfe alle Behauptungen unabhängig. Ziel: lokal autonome Android-KI (Continuum) mit nachvollziehbarem Gedächtnis, lokalem Sprach-/Reasoning-Kern, selbst überprüfender Codereparatur und wiederholbarem lokalem Worker. Keine obligatorischen kostenpflichtigen externen KI-APIs. Kein fremder Zugriff auf private Erinnerungen, keine automatischen Berechtigungen. Ein erfolgreicher Forschungsversuch ist NICHT gleichbedeutend mit produktiver KI oder Besitzerfreigabe.

WICHTIG: Der User möchte, dass du herausfordernde Architektur- und Forschungsfragen mit einem konkreten, testbaren Umsetzungsvorschlag beantwortest. Bitte nicht die übrige Projektgeschichte voraussetzen und keine Angaben unüberprüft als Beweis behandeln.

## 1. LIVE GITHUB SNAPSHOT (vom Assistenten erneut geprüft, 26.10.2026)
- main: 190f5eabaa60fc59292b2b80d3086aafd7743a80; nicht von uns verändert.
- PR #151 (Sol, Brain): https://github.com/Ullmann27/device-ai/pull/151 ; HEAD 9d66813419f08468ba99bd7e45ae38ff6c0e6b99
- #152 (CDX1-Kollaps/Numerik), #153 (Grounding/False Hits), #154 (DeepSeek optional/protokolliert, NICHT live), #155 (Coding-Budget, Diagnose-Replay), #156 (SDK-/Repair-Handoff): alle Draft.
- PR #157: https://github.com/Ullmann27/device-ai/pull/157 ; unabhängige Dialogue-Routing-Review, Syntaxfixes, Grounding/Handoff.
- PR #158: https://github.com/Ullmann27/device-ai/pull/158 ; unabhängige Baby-Core-Provenienzkorrektur, **von main abgezweigt** und NICHT blind in neuere Schema-Version kopieren.
- PR #159: https://github.com/Ullmann27/device-ai/pull/159 ; Astra, Brain-/Coding-Integration, HEAD 301fe804700189ee99533662ce57be96c29ae450, 33 geänderte Dateien, 4 Commits, Draft. Unabhängig echte Gradle-/APK-Abnahme nicht belegt.
- PR #160: https://github.com/Ullmann27/device-ai/pull/160 ; Infrastrukturabbrüche dürfen Reparaturstrategien nicht negativ trainieren; HEAD bc424dfe208e2b06ae3c12a4adf7abb02ec79b05.
- PR #161: https://github.com/Ullmann27/device-ai/pull/161 ; Android↔Worker Typed Coding Source Identity, HEAD 4b569515bb3f43abccf008e1889eac68d570a105.
- PR #162: https://github.com/Ullmann27/device-ai/pull/162 ; bestätigte Erinnerung bewahren bei Kontextüberlänge/Fallback, HEAD cafc337a14eca01dc49e38eea3dd299034460552.
- PR #163: https://github.com/Ullmann27/device-ai/pull/163 ; COIN-WM v1/v2/v3-Audit, NTW1 Shadow Model, atomarer Statement-Übersetzer, CCC1-Independent-Verifier, HEAD 4e6ffd7a643b722cdce74f62ae8c947b61e94df4, 14 Dateien, 36 Commits, Draft. Base ist PR162-Branch, **kein separater Merge in main**.
- PR #160→161→162→163 sind gestapelte Review-Zweige; Base-Beziehungen bei jeder Änderung erneut prüfen. Keine Duplicate-Cherry-Picks und kein erzwungenes Merge.

## 2. WIE WIR HIERHER KAMEN
A. Vorhandene Architektur: Android-App mit Chat/AgentActivity, lokale Memory-Pipeline mit Provenienz/Vertrauen/Replay, local-first Sprachmodell-Route, experimentelle neuronale Komponenten (SWB1 Subword/Intent; CDX1 Byte-/Kontext-Decoder; CDXA1 als Forschungsentwurf), CCC1-Coding-Agent, feste Testprofile, repair-loop, repair-ledger, Docker-basiertes isolated-executor, lokal zyklischer Worker, Owner-ToolGate. Architekturstatus nicht mit erfolgreich durchgängig laufender APK verwechseln.

B. Sol #151 diagnostizierte Langtext-Kürzung und zu frühes Grounded-Recall-Supprimieren des lokalen Sprachmodells; PR157/162 liefern sichere Fallback-/Recall-Gates. PR153 Grounding reduzierte unerlaubte False Hits im Test stark, erhöhte aber False Negatives. Ein bereits verbrauchter 60-Fall-Holdout war nicht freigabereif; neuen Holdout nicht heimlich daraus bauen. Die minimale CDX1-Entwicklung (#152) löste keine neuen Faktenfälle (0/4), obwohl bekannte Trainingstexte besser gingen.

C. Argon/externes Modell lieferte COIN-WM:
 v1: fest codierte Bug-Reparaturregeln und unfairer Retrieval-Gegner; V8-Audit enthüllte 1/5 Transfer und Continual-Learning-Vergessen.
 v2: alpha-normalisierte Token-LCS-Operatoren und symmetrisches Replay, aber new Function/Regex-Gas umgehbar, Mutationsprüfung lückenhaft, vermeintlich verifizierte Fixes ungeprüft, gemeinsamer W_enc eingefroren. Kein Produktivimport.
 v3: gemeinsamer trainierbarer 16→12-Encoder, per-Operator-Policy/Transition, EWC, vier Vergleichsarme und Compound-Aufgaben. Native Node 22.16.0 zeigte, dass node:vm mit hineingereichtem Host-Math/Object via Math.constructor.constructor den Host erreichen kann. v3 wurde **nicht byte-identisch**, sondern in einer sorgfältig nachkonstruierten lokalen Node22-Referenz geprüft: 0/5 ZERO, 3/5 RETRIEVAL, 2/5 OPERATOR_ONLY, 3/5 NTW1; 1.210 trainierende Schritte, ~4,864 L2-Encoderdrift. Behauptete 5/5 NN war nicht bewiesen. Neuronale Strategie für B1/B2 war zwar richtig, doch flache Token-Hunks erzeugten kaputte JS-Syntax bzw. unconditional return 0. FIX-GENERATION, nicht allein RANKING, ist der Flaschenhals.
 Ebenso ist spawnSync mit env={} und --disallow-code-generation-from-strings **keine OS-Sandbox**: unabhängiger Node22-Gegencheck konnte weiterhin node:fs und node:net benutzen.

D. PR163 nahm darum NUR sichere Forschungsbausteine auf:
 - .github/continuum/coin-wm-v3-shadow-model.mjs : begrenztes mathematisches neuronales Shadow-Modell, keine Ausführung/Tools/Promotion, Status RESEARCH_SHADOW.
 - .github/continuum/structured-statement-translator.mjs : kleiner, bewusst UNVOLLSTÄNDIGER single-function/statement-Grammatik-Übersetzer mit genau einem atomaren Rewrite oder vollständigem Guard, unique anchor, reject-on-ambiguous/unsupported; KEIN voller AST-Parser.
 - .github/continuum/coin-wm-v3-structured-shadow.mjs : verbindet neural ranking → branded operator → Source-Patch-Kandidat, gibt nur CANDIDATE_UNVERIFIED mit sha256(source), sha256(candidate), sourceCommit, taskSha256 aus. Keine Ausführung.
 - .github/continuum/ccc1-independent-verifier.mjs : erstmals Forschungs-Patch an echte bestehende CCC1-Auftragsformen und die bereits vorhandenen Docker-/Worktree-/Fixed-Test-APIs gebunden; require existing owner grant, exakte Source-Digests, RED-baseline/GRUEN-candidate/GRUEN-in-zweitem-Worktree, Tests unverändert, immutabler Kandidaten-Snapshot. NIEMALS Lernerfolgs-/Ledger-Update oder Merge durch diesen Verifier; selbst PASS = DUAL_WORKTREE_CHECKS_PASS_REVIEW_REQUIRED.
 - die gleichnamigen .test.mjs-Dateien und zwei Evidenzberichte.
 - Nicht die Argon-APIs blind übernehmen! Kanonisches Coding Task hat sourceCommit (40 lowercase hex), allowedPaths, chain und taskFingerprint; nicht allowedFiles, frei gewählte testCommand oder kurzer Commit. Echte Ledger-Funktion ist recordSession (NICHT appendVerifiedEntry). NTW1-Modul bietet trainResearchSample (NICHT produktive trainStep). Fremder sandboxRunner-Callback ist inakzeptabel. isIsolatedExecutor prüft ein echtes gebrandetes Capability-Objekt.
 - Vor Existenz des echten Docker-basierten Executors ist ein RED→GREEN→FRESH-GREEN nicht getestet.

E. Unterschied zwischen Prüfungen und realen Beweisen:
 - Exakte auf GitHub gespeicherte Test-/Codekörper wurden in frischer V8-Testumgebung mit **offengelegten test-only Shims** geprüft: 5/5 Übersetzer, 4/4 neuronale Shadow-Schnittstelle, 6/6 CCC1-Preflight einschließlich Verbinden realer Original-Module und Coding Task Schema. Diese Werte gelten NICHT als echte Node CLI-, Docker-, Android- oder unabhängige Reparaturabnahme.
 - Test-only Kryptografie-Shim ist KEIN echtes SHA-256, obwohl Produktionsmodul Node crypto verwendet. Bitte ermittel echte native Ergebnisse.
 - Node22 auf dem früheren Prüfhost verfügbar, Docker dort NICHT installiert. Darum weder echte drei Sandbox-Läufe, nor native komplette node --test auf aktuellem Commit, noch Fold7-/APK-Abnahme beweisen.
 - GitHub Actions Runnerproblem in unabhängigen alten Prüfungen: frühe Jobs nach ~2–3 s fehlgeschlagen, runner_id 0, steps=[]; das ist KEIN Nachweis eines Kotlin-Kompilierfehlers; Ursache unklar, nicht blind erneut ausführen.
 - Das Entwicklungszielgerät ist Galaxy Z Fold7, installierte Haupt-App soll ausschließlich mit gleichem Package und gleichem Besitzer-Signaturzertifikat aktualisiert werden. Kein neues zweites Gerät-Package, keine Desktop-APK, kein APK-Releaseschritt in dieser Forschungsmission.

## 3. TECHNISCHE STARTDATEIEN (bitte tatsächlich lesen)
1. docs/evidence/coin-wm-v3-native-node-20261026/CCC1-INDEPENDENT-VERIFIER-AUDIT.md
2. docs/evidence/coin-wm-v3-native-node-20261026/STRUCTURED-TRANSLATOR-AUDIT.md
3. docs/research/omega/GPT6-COIN-WM-INDEPENDENT-AUDIT-20261026.md
4. .github/continuum/ccc1-independent-verifier.mjs
5. .github/continuum/ccc1-independent-verifier.test.mjs
6. .github/continuum/coin-wm-v3-shadow-model.mjs
7. .github/continuum/coin-wm-v3-structured-shadow.mjs
8. .github/continuum/structured-statement-translator.mjs
9. .github/continuum/coding-task.mjs
10. .github/continuum/coding-orchestrator.mjs
11. .github/continuum/repair-loop.mjs
12. .github/continuum/isolated-executor.mjs
13. .github/continuum/ccc1-path-policy.mjs
14. .github/continuum/repair-ledger.mjs
15. .github/continuum/local-teamleader-loop.mjs

Die Dokumente sind Hinweise, Code und echte Laufdaten sind der Prüfmaßstab. Gib Dateien + präzise Zeilen/Funktionen an, wenn du einen Defekt behauptest.

## 4. UNSERE HAUPTFRAGE AN SOL 6.1
**Welche KLEINSTE tatsächlich ausführbare, sicher isolierte und wissenschaftlich widerlegbare End-to-End-Anordnung beweist, dass die von NTW1-v3 LOKAL GELERNTE Strategieauswahl auf vorher versiegelten, zusammengesetzten unbekannten Coding-Fehlern bei GLEICHEN gelernten Operatoren bessere Pass@1-Ergebnisse liefert als eine nicht-neuronale Baseline, UND dass jeder positive/negative Erfahrungseintrag durch unabhängige RED→GREEN→GREEN-Belege sowie Eigentümerfreigabe abgesichert ist?**

Bitte unterscheide:
 (i) "richtiger Operator" vs. "korrekt angewendeter Patch" vs. "echte unabhängige Reparatur";
 (ii) Modell lernt Parameter vs. Modell verbessert generalisierende Fähigkeiten;
 (iii) ein grüner eigener Test vs. kausaler/belegter Fix auf unabhängigem Test;
 (iv) Laufzeit-Isolation vs. verifizierte Erlaubnis; (v) Review-Status vs. Gewichts-/Memory-Promotion.
Erfinde keine Benchmark-Erfolge oder APK-Verfügbarkeit.

## 5. ZUSAETZLICHE SCHARFE FRAGEN (bitte technisch beantworten)
Q1: Auditiere den **aktuellen** CCC1 Independent Verifier: korrekte Verwendung von runChain(), createWorktree(), removeWorktree(), testing JUnit reports, trackedTestHash coverage, git diff, symlinks, private grants, Gradle/Node profile limitations. Gibt es TOCTOU, fehlende Testmanifest-Bindung, veränderbare Runner-/Test-Profile, False RED, Tests mit exec=0, Test-Mutation über indirekte Fixtures oder Cache-Effekte? Kann die Pipeline einen unberechtigten Erfolg berichten? Wie genau reproduzieren?
Q2: Ist ein vollständiger JS-AST-Patch-Generator nötig, oder gibt es eine sicher begrenzte Transfergrammatik, die die echten B1/B2-Kompositionen zuverlässig abdeckt, ohne syntaktisch korrekte, aber falsche Patches zu belohnen? Schlage minimalen deterministischen Compiler-/AST-Pfad mit vorhandenen, überprüfbaren Abhängigkeiten vor. Keine angeblich vollständige AST-Engine aus Semikolon-Splitting.
Q3: Wie kann ein Quell-/Test-/Patch-Beleg als *unverfälschbarer Owner-Grant-gebundener Receipt* im bestehenden Coding-Journal und repair-ledger gespeichert werden, ohne vom Modell selbst signierbare positive Labels zu erlauben? Wie behandeln wir absichtlich gefälschte JUnit XML und falsche "tests.executed" im nicht vertrauenswürdigen Container?
Q4: Wie klassifizieren wir EACCES/ENOSPC/ENOMEM, Exit 126/127/137/143, Timeout, SIGKILL, Runner fehlt und unbekannte Check-Abbrüche ohne false-negative Reward? Wann darf ein legitimer negativer Code-Test als neg. Lernerlebnis gelten? Warum darf ein fehlerhafter Frischlauf bei grünem Erstlauf nicht automatisch als negativer Beweis für die Strategie gelten (Flaky vs. ungültiger Patch)?
Q5: Wie entwerfen wir versiegelte, ausgewogen vorregistrierte Hidden-Holdouts und einen echten vierarmigen Vergleich: ZERO_MEMORY, fairer RETRIEVAL, HEURISTIC_OPERATOR_NO_NN mit identischen Patches, NTW1; zusätzlich zufällige/untrainierte Netzwerk-Ablation, adversariale Kommentare/Variablennamen und syntaktisch korrekte Negativpatches? Empfiehl Metriken Pass@1, Patch-Validität, Neuheit, Retention/BWT, Konfidenzintervalle und Mindest-Laufbudgets für lokale Hardware.
Q6: EWC ist im v3-Code vorhanden, aber verhindert es unabhängig nachweisbar Vergessen, wenn Operatorfamilien sukzessive wachsen? Wo ist Fisher/Anchor mathematisch irreführend, wo droht Label Leakage (Index-Gleichheit statt tatsächlicher Test-Güte), und wie bauen wir stabile Replay/EWC-Ablationen ohne versteckte train/holdout Überschneidungen?
Q7: Was ist der konkrete Weg vom sicheren, owner-gebundenen, abgeschlossenen Reparatur-Receipt zur Android/Termux-App? Wie überprüfst du gleichen Package-Namen/Signatur, Erhaltung von on-device Memories und Modellgewichten, Fold7-Bedienung und reale Offline-Zyklen ohne Github Actions? Kein Release ohne Eigentümerfreigabe.
Q8: Prüfe Repo- und Branch-Integrationsrisiken #151–163 inkl. #158 Baby-Core-Provenienzkonflikt mit neuem Schema, duplicate cherry-picks in #154/#155 und die reale Fähigkeit des #159 Hauptintegrationsbranches. Schlage eine konfliktarme Reihenfolge mit Rückrollplan vor, nicht pauschales "alles in main".

## 6. ERWARTETE LIEFERUNG
A. Starte mit einem knappen, belegten **Ist-Befund**: welcher Code und welcher Git-HEAD ist real, welche Tests wurden wirklich nativ ausgeführt, was fehlt. Keine geschätzten Prozentwerte für eine Super-KI.
B. Liste die **fünf folgenreichsten Reprofehler** im aktiven Stack als FAIL/PASS/NOT_RUN, mit Datei/Funktion, minimalem Repro-Test, betroffener Sicherheitsinvariante.
C. Priorisiere EIN experimentelles, fertiges Vertical Slice, das Du innerhalb einer isolierten lokalen Node/Docker-Umgebung real ausführen kannst; schreibe vollständige Unit-/Integration-/Negativtests und einen reproduzierbaren lokalen Befehl.
D. Gib konkreten Patch/Diff oder vollständigen produktionsnahen Source, wenn du keinen GitHub-Schreibzugriff hast; wenn Schreibzugriff vorhanden ist, arbeite **nur auf einem EIGENEN Research-Branch mit Draft-PR und früh gepushtem Work-in-Progress-Commit**, nicht direkt auf Astras, GPT6s oder main-Branch.
E. Dokumentiere deterministische Seeds, exacte SHAs, nativen Testausgaben, Budget/Hardware/Run-Abbrüche, Negativ- und EWC-Ablationen. Kein Selbstlob, keine "5/5" ohne echte unabhängige Testausführung, keine privaten Daten ins Repo.
F. Schreibe ausdrücklich, welche bestehenden Sicherheitsgates (review, ToolGate, memory provenance, signatures, receipt gate) unverändert bleiben.

## 7. KEINE KOMPROMISSE
Nie ungeprüft nach main/production pushen. Keine Modellgewichts-Promotion, owner-memory-Mutation, Termux-ToolGrant-Umgehung, ungeprüfte Docker-/VM-Ausführung, GitHub Actions als notwendige CI oder kostenpflichtige externe LLM-API. Kein generierter Code außerhalb OS-Sandbox ausführen. Docker nicht vorhanden = NOT_RUN / FAIL-CLOSED, nicht automatisch spawnSync. Schon existierende Reparaturmechanismen dürfen nicht überschrieben werden. Behalte alles reproduzierbar auf GitHub, sodass der andere Agent weiterarbeiten kann.

**Abschluss an uns:** Was ist der EINDEUTIG größte kausale Qualitätshebel von hier aus? Was genau würdest du am realen Code zuerst ändern – und wie sähe ein einziger unabhängiger Beweis des Erfolgs aus?
