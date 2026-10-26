# CONTINUUM – MASTER-MISSION AN SOL 6.1 (MAXIMALER DENKMODUS)
**Datum:** 26. Oktober 2026
**Repository:** https://github.com/Ullmann27/device-ai
**Ausschließlich Device AI Continuum; NICHT Lumo.**
**Missionsname:** OPEN FABRIC / JARVIS-MO – Virtual Model Fabric + Local Intelligence Integration

## Deine Rolle und unser Ziel
Sol 6.1, du erhältst hier einen eigenständigen, vollständigen technischen Forschungs- und Entwicklungsauftrag. Du musst unsere bisherige Unterhaltung NICHT kennen. Du kannst GitHub lesen und sollst den echten neuesten Quellstand selbst bestätigen. Wir bauen eine auf Android handlungsfähige autonome lokale KI, die Gedächtnis, Reasoning, echte lokale Modellinferenz, Coding-Agent, Test-/Reparaturpipeline und zyklischen Worker wirklich verbindet. Große Modelle sollen bei Bedarf über eine logisch adressierbare, austauschbare Internet-/Rechen-Infrastruktur zugänglich sein, ohne 500GB Gewichte auf dem Handy oder einen eigenen Großrechner vorauszusetzen. **Kein kostenpflichtiger API-Zwang. Kein automatisches Cloud-Buchen. Kein Fake-Erfolg.**

Meine endgültige Architekturentscheidung: **Android-first Local Core + Virtual Model Fabric + unabhängige CCC1-Prüfung + lokal wiederverwendbare überprüfte Reparaturrezepte.** Du bist der Hauptentwickler für die neue Modell-/Ressourcenvermittlungsarchitektur. Astra übernimmt später bevorzugt einen getrennten Android-/Fold7-Integrations-Audit. Die Leitung und der andere Entwickler-Agent müssen deine Änderungen durchgehend auf GitHub lesen und fortsetzen können.

### ABSOLUTES OPEN-CODE-PROTOKOLL – NICHTS VERSPERREN
1. Arbeite ausschließlich auf einem **eigenen neuen GitHub-Forschungsbranch**, erstelle sofort einen **Draft-PR** und veröffentliche mindestens einen sauberen, realen WIP-Commit mit einem Hinweis, welche Dateien du bearbeitest.
2. **Jeder substantielle Zwischenstand wird umgehend gepusht:** echter Code, vollständige Tests, genaue Commit-SHA, reale Testbelege, offene Probleme und Wiederaufnahme-Befehl. Kein ausschließlich im flüchtigen oder privaten Workspace verbleibender Code. Bevor dein Zeitkontingent endet, pushen und genaue Übergabe aktualisieren.
3. Keine unzugänglichen Geheimbranches, keine Force-Pushes, kein Überschreiben fremder Arbeitszweige, keine Repo-Rechteänderungen, kein Archivieren/Schließen anderer PRs oder versteckte Datei-Freigabe. Keine Daten oder Schlüssel ins Repo.
4. Hauptbranch \`main\` unberührt; keine automatische Merge-/APK-/Gewichts-/Erinnerungspromotion. Veränderungen am produktiven Android-Pfad benötigen separate Besitzerfreigabe.
5. **Die Leitung muss parallel jederzeit mit dir arbeiten können.** Gib nach jedem Patch Source-Pfade, Commit, PR-Link, präzise Tests und nächste offene Aufgabe an. Berühre nicht die von GPT-6 und Astra parallel übernommenen Dateien ohne abgestimmte Review-Grenze.
6. Ein PR-Head allein kann durch einen anderen Agenten nicht sehen, was du noch nicht gepusht hast. Vermeide genau diese bisherige Fehlerklasse.

## Zuerst LIVE GITHUB lesen, nicht annehmen
Die zum Auftrag bestätigten Stände (vor Beginn unbedingt neu abgleichen):
- \`main\`: \`190f5eabaa60fc59292b2b80d3086aafd7743a80\`
- #159 Astra Integration; #160 Infrastruktur-Abbruch-Lerngate; #161 Android/Termux Typed Commit Binding; #162 Grounded-Recall-Fallback.
- #163 COIN-WM/NTW1-v3, atomarer Statement-Übersetzer und read-only Dual-Worktree-CCC1-Verifikation.
- #164 JUnit-Executed-Test-Identitäten und Testparität gegen Scheinreparatur.
- #165 Sols native Numerikschutz-/Binding-Patches auf reale Forschungssourcen angewendet.
- [#166](https://github.com/Ullmann27/device-ai/pull/166) \`736aa0d67f8045d055e15098a5c414525a6660fd\`: A/B/C-Engpassdiagnostik \`REFERENCE_PATCH\`, \`GIVEN_OPERATOR\`, \`NEURAL_TOP1\`; der neuronale Top-1-Operator wird nur EINMAL versucht, ohne Rang-2-Rückfall. Alle Ausführungen noch NOT_RUN.
- [#167](https://github.com/Ullmann27/device-ai/pull/167) \`9a4f0632af2ef6d5ecc017eee009fccfe6969172\`: paralleler, auf #166 basierender Draft-PR mit read-only Scorecard, lokalem privatem Evidence-Export und Tests.
- [#168](https://github.com/Ullmann27/device-ai/pull/168) (neuer paralleler Draft-PR auf #166): Virtual-Model-Fabric-Planer, Model-Shard-Metadaten, Datenschutz-/Budget-/Revisionsgates und Tests.
- [#154](https://github.com/Ullmann27/device-ai/pull/154) \`2fb604609d8f7c86ec49fe5ebd4d9158b80beb5a\` ist ein SEPARATER Sol-Branch mit optionaler DeepSeek-HTTPS-Bridge samt Owner-Consent, Zero-Cost-Assertions und pinned revision. **Nicht blind in #168 kopieren.**

WICHTIG: #167 und #168 sind **Geschwister-PRs** mit Basis #166 und enthalten einander NICHT. Vor Integration Änderungen vergleichen und einen neuen gemeinsamen Review-Branch verwenden. Keine Doppel-Cherry-Picks.

**Lies diese vollständigen aktuellen Dokumente und den echten Code:**
- https://github.com/Ullmann27/device-ai/blob/research/continuum-virtual-model-fabric-20261026/docs/research/omega/CONTINUUM-VIRTUAL-MODEL-FABRIC-HANDOFF-20261026.md
- https://github.com/Ullmann27/device-ai/blob/research/continuum-bottleneck-diagnosis-a-b-c-20261026/docs/research/omega/CONTINUUM-BOTTLENECK-ABC-DIAGNOSIS-20261026.md
- https://github.com/Ullmann27/device-ai/blob/review/continuum-ccc1-test-parity-20261026/docs/evidence/ccc1-test-case-parity-20261026.md
- Auf PR #154: \`docs/CONTINUUM-DEEPSEEK-BRIDGE-20261026.md\`
- \`AGENTS.md\`, \`docs/CONTINUUM-EXPERIMENTVERTRAG.md\`, \`docs/CONTINUUM-EXPERIMENTE.json\` und Issue #41.
- \`app/src/main/java/at/heinz/deviceai/cognitive/ComputeFabric.kt\`
- \`.github/continuum/coding-provider.mjs\`, \`coding-task.mjs\`, \`repair-loop.mjs\`, \`isolated-executor.mjs\`, \`ccc1-independent-verifier.mjs\`.
- \`scripts/continuum-deepseek-tier-plan.mjs\`, \`continuum-deepseek-hub-preflight.mjs\`.
- PR #154s \`scripts/continuum-deepseek-bridge.mjs\`, ohne sie vor Prüfung auf den aktuellen Zweig zu übertragen.

## Was GPT-6 seit der letzten Unterbrechung SELBST erledigt hat
A. PR #160: negative Strategie-Bestrafung nach Infrastrukturfehler ohne echte ausgeführte Tests unterbunden.
B. PR #161: freigegebenen Git-Quellcommit durch Android↔Typed Coding Rückkanal gebunden.
C. PR #162: bestätigte Gedächtnisantwort bleibt bei überlangem Modellkontext erhalten, statt UI-Fehler.
D. PR #163: NTW1-v3-Shadow mit atomarem begrenztem Patch-Übersetzer, Quelle/Task SHA-bound unverified proposal und anfängliche echte CCC1-Sandbox-Dual-Worktree-Verifikationsbrücke; **kein geprüfter echter Docker-RED→GREEN→GREEN**.
E. PR #164: gehashte JUnit-Fallidentitäten, executed-count/parity und Fail-Closed gegen vertauschte Tests; kein automatisches positives Training.
F. PR #165: deinen zuvor gesicherten Sol-Numerik- und Property-Binding-Patch exakt und konfliktfrei auf reale Forschungsdateien übertragen. 22/22 exakte Source-Test-Callbacks in V8 bestanden; die von dir getrennt dokumentierten nativen 17/17 stammen aus deiner Umgebung. Full native Branch JUnit-/Docker-Abnahme ausstehend.
G. PR #166: wirklichen \`REFERENCE_PATCH\` / \`GIVEN_OPERATOR\` / \`NEURAL_TOP1\` Diagnoseadapter mit dokumentiertem Top-1-Fehlerfall und separater Pagination-Aufgabe geschaffen. 5/5 V8-Originalquellen-Testcallbacks bestanden. Kein echter Reparaturbeweis, keine Gewichts-Promotion.
H. PR #167 entstand parallel: Scorecard/Private Evidence Export, native Teiltests berichtet, als Geschwisterbranch separat erhalten.
I. **NEU PR #168:** \`.github/continuum/continuum-virtual-model-fabric.mjs\` sowie Test. Baut auf realem Modell \`deepseek-ai/DeepSeek-V4.1-Flash-1017\` und vorhandener \`inspectHubManifest\` 48-Shard-Validierung auf. Deterministische, reine Planfunktion mit gepinntem Modell und Fallback-Revision, Auth-/Owner-Consent- und Kosten-/Byte-/Deadline-Vorprüfung, Schutz vor privaten Kontexten im Fernweg, Offline-/Local-First-Priorisierung und immutable Reports. **KEINE echte Autorisierung:** jede Node-Verfügbarkeit/Enroll-Angabe ist bis zur bestehenden signierten \`ComputeNodeRegistry\` untrusted; vorgeschlagene Route \`executable:false\`. Keine Gewichte geladen, kein GPU-Provisioning, kein Cloud-Zahlungsvorgang, kein Webaufruf. Native Node22.16.0 im temporären lokalen Modulspiegel mit kompatibler handkopierter Tier-Plan-Teilimplementierung: **9/9 PASS**; exakt vom GitHub neu abgerufene Modellplan-/Fabrik-/Testfunktionen in V8: **9/9 PASS**. **Noch NICHT native Full-ESM-Tests auf dem offiziellen PR HEAD**, kein echter DeepSeek-/Docker-/Fold7-Test.

## ARCHITEKTURFALLEN, DIE DU AUSDRÜCKLICH NICHT DUPLIZIEREN DARFST
1. Android \`ComputeFabric.kt\` hat BEREITS \`NodeCapabilityManifest\`, \`ComputeNodeRegistry\`, \`ComputeScheduler\`, \`ComputeTask\`, \`ComputeResultVerifier\`, kryptografisch kontrollierte Capability- und Resultumschläge. Das ist unser vorhandener Compute-/Permission-Ansatz. **Keinen zweiten privilegierten Fabric-Scheduler oder Consent-Service erstellen.** Baue nur eine schmale, reversible, versionierte read-only Verbindungs-/Planungsbrücke.
2. Android FIRST: Termux/Android-Builder muss selbst sinnvoll weiterlaufen; ein fremder Host/GPU ist optional für große Modelle, nicht notwendige Voraussetzung für Coding und lokal gespeicherte Erinnerungen.
3. #154s \`scripts/continuum-deepseek-tier-plan.mjs\` besitzt \`PINNED_REVISION\`; die aktuelle #168-Ancestry hat einen anderen gleichnamigen Skriptstand mit \`MODEL_ID\` und \`ESTIMATED_WEIGHT_BYTES\`, aber ohne \`PINNED_REVISION\`. Saubere selektive Integration erforderlich. Echte Modellrevision aus #154: \`1bae0e1088993b9b82de08f59de5be5a34e42170\`, zunächst nochmals am offiziellen Modell-Manifest verifizieren.
4. Eine authentifizierte HTTPS-Aussage des Dienstbetreibers über eine Modellrevision belegt NICHT die wirklich geladenen Gewichtsshards. 48 Shards und ~510 Milliarden deklarierte Bytes belegen Speicherbedarf, nicht aktive Inferenz. Sparse/MoE/Engram, CPU-/SSD-Offload und verteiltes Serving müssen empirisch geprüft werden.
5. Kein \`new Function\`, \`node:vm\` oder bloßer Node-Child-Process als angeblich sichere Sandbox für Modell-generierten Code. Coding-Kandidaten nur über vorhandenes owner-authorisiertes OS-isoliertes CCC1-System. Keine direkten Tool-Aufrufe aus Modellantworten.
6. Owner-private Memory nicht ungefragt extern übertragen; kein impliziter Internet-Fallback; keine teuren GPU-Instanzen ohne gesonderte Zustimmung.
7. Ein positiver Forschungstest in V8 ist **kein** echter autonomer Patch und kein Grund, NTW1-Gewichte oder \`repair-ledger.mjs\` selbsttätig zu verändern.

## DEIN GROSSER KONKRETER ENTWICKLUNGSAUFTRAG

### P0 – Sicherung, ABI-/Branch-Audit, native Tests
Rufe aktuelle Heads von #154, #163–168 ab. Prüfe, ob dein altes 17/17 Numerik-WIP jetzt real und unverändert in #165 enthalten ist. Teste auf einem vollständigen Node22 Checkout zuerst:
\`node --test .github/continuum/continuum-virtual-model-fabric.test.mjs\`
\`node --test .github/continuum/ccc1-bottleneck-diagnosis.test.mjs\`
\`node --test .github/continuum/ccc1-bottleneck-scorecard.test.mjs\` (bei separatem #167 Checkout)
\`node --test scripts/continuum-deepseek-tier-plan.test.mjs\`
\`node --test .github/continuum/coin-wm-v3-numeric-safety.test.mjs\`
Weitere native Tests auf genau gepinnten Commitständen. Dokumentiere Runner, Abort, SHA, Codepfade, Red/Green. Keine Testsummen über unterschiedliche Branchstände addieren.

### P1 – Bestehende Gehirn-, Modell- und Compute-Schnittstellen VERBINDEN
Skizziere und implementiere einen schmalen Adapter zwischen \`ContinuumMemoryDialogue\`/Android ToolGate, \`ComputeFabric.kt\`, Local Inference/Model Registry und dem aktuellen Node Virtual-Model-Planer. Jede Route braucht Modell-ID + immutable SHA-Revision + geprüften Capability/Owner-Grant + Datenklassifikation + Ablaufzeit + Input-/Output-/Geldbudget und Offline-Fallback.
Planung darf niemals Autorisierung ersetzen. Verwende bestehendes Besitzer-Consent- und ToolGate-System. Zeige einen lokal ausführbaren Coding-Fall mit gedächtnisbelegtem Kontext und Planungslogik; kein unnötig duplizierter Agent oder neues zweites Gedächtnis.
Liefer native Kotlin/Node-Konformitätsprüfungen mit gemeinsamen versionierten JSON-Testvektoren, z.B. fehlende signierte Capability, widerrufener Node, Modellrevision falsch, Owner offline, privates Gedächtnis, Abbruch.

### P2 – VIRTUELLE MODELLADRESSE und Ressourcenzugriff
Gestalte eine stabile logische \`model://...\` Identität oder vorhandene kompatible Routing-ID als **Datenkonzept**, nicht als vorgetäuschte neue Netzwerkfunktion. Hinter der Identität können stehen:
(a) kleines bereits verfügbares lokales Modell;
(b) ausdrücklich genehmigter, bereits laufender, no-charge DeepSeek-Dienst;
(c) temporärer legaler Besitzer-Worker/GPU;
(d) ein kooperativer Pool opt-in signierter Nodes;
(e) persistente Gewichts-Shards/Cache + bedarfsgesteuerte CPU/GPU, wo Laufzeit es technisch unterstützt.
Trenne STORE (persistente Bytes/Manifest/Provenienz), COMPUTE (physische kompatible Geräte), SESSION/LEASE, MODEL-SERVING-RUNTIME, REQUEST/RESULT, CANONICAL CODING VERIFICATION.
Dateishards sind nicht automatisch einzeln ausführbare MoE-Experten. Keine Kompatibilitäts-, Speicher- oder Geschwindigkeitsbehauptung ohne echte Messung.
Baue zuerst **einen** minimalen, wissenschaftlich testbaren Virtual-Fabric-Vertical-Slice; kein monatelanges verteiltes Dateisystem.

### P3 – Optionale DeepSeek-Bridge aus #154 VORSICHTIG ANSCHLIESSEN
Prüfe echten Stand und Branch-Unterschiede, Modellrevision, DeepSeek-Request-Encoding, offiziellen Operator-/Recipe-Vertrag, API-Schema, HTTPS/TLS, retention/training policies und Response-Signatur/Quellidentität. #154 verlangt einen speziellen \`/v1/continuum/capabilities\`-Betreiberadapter; ein beliebiger OpenAI-kompatibler Endpoint liefert ihn nicht automatisch.
Liefere ausschließlich einen **owner-consented und abgeschalteten-by-default** Remote-Provider, der die vorhandene Coding-Request- und CCC1-PRÜFUNG nutzt. Ohne echten Betreiber bleiben \`NOT_CONFIGURED\` / \`UNAVAILABLE\`; nie Fake-Inference. Keine neue externe API Pflicht, keine normalen Nutzerdaten oder Memory-Leaks.
Wenn kein bereits berechtigter No-Charge-Dienst vorliegt, sichere nur einen dry-run Vertrag und die echten Negativtests; KEINE GPU kostenpflichtig aktivieren.

### P4 – JARVIS-MO-FORSCHUNG: große Fähigkeiten mit wenig großen Modellaufrufen
Untersuche die Hypothesen:
H1: Ein unabhängig bewiesener Patch liefert ein generalisierbares, lokal abrufbares **Reparaturrezept** (Preconditions, operator, sichere Applicability Checks, Gegenbeispiele, Provenienz). Es hilft später über Domänennamen hinweg.
H2: Local-First + selective remote escalation (nur bei gemessenem Bedarf/Unsicherheit) erreicht bei gleicher Patchkorrektheit weniger übertragene Bytes / geringere Kosten / weniger Teacher-Aufrufe.
H3: Kontextreduktion durch lokalen Repo-Index, echtes failing test diagnostics, spezifischen owner-allowlisted Quellbereich minimiert Byte- und Modellkosten ohne Patch-Überanpassung.
H4: Cache-/Object-Storage-Versionierung, Paketreplikation, CPU/SSD Offload, sparsames MoE-Expert-Caching und kooperativer Compute-Pool sind getrennte Experimente – jeweils reale Host-Anforderungen, Revision, Latenz und Durchsatz messen.
Falls \`OneToken\` relevant ist, recherchiere die tatsächliche Publikation/Implementierung und teste deren realen Geltungsbereich statt den Namen als Beweis zu verwenden. Spekulative neue Kombinationen nur als klar falsifizierbare Hypothesen, keine Behauptung unbekannter Physik.

### P5 – UNABHÄNGIGER KODIER-ZYKLUS
Benutze das echte #166 A/B/C-Diagnoseinstrument und #167 Scorecard. Korrekte Referenz zuerst RED→GREEN→frischer GREEN mit owner-authorisierter OS-Isolation; danach Zwangsoperator; danach gefrorenes NTW1 Top1.
Bewerte Pass@1 NICHT als „erster anwendbarer Operator“, sondern echten erstplatzierten Operator; verifiziere executed-test identities / owner tests unchanged / ausgedehnte unabhängige Oracle-Tests. Prüfe falsche syntaktische Patches, Scheinreparatur, Mock-Hardware, Infrastrukturabbruch und frischen Worktree.
Kein Training bei abgebrochenem/unverifiziertem Test; kein positiver Lern-Receipt aus selbst geschriebenem Text.

### P6 – Lokales kontinuierliches Lernen & Worker
Schließe nach validierter Evidenz ein **isoliertes** Schattenexperiment an: confirmed repair -> provenanced recipe -> local retrieval / NTW1 training -> frozen hidden-task eval -> owner review. EWC/Replay separat gegen gleichen Operatorbestand messen. Echt autonomer local-first Workshop nur mit canonical Backlog/Lease/ToolGate, zyklischer Recovery, RAM/Thermal/Battery/Doze-Budget und Owner Stop. PC/GPU lediglich optionale Ressource, Android bleibt selbst Codier-Plattform.
Schütze private Erinnerungen, Signier-Zertifikat, APK package identity. Kein Produktionsmodell-Update ohne separate Abnahme.

## Eine erste, wirklich messbare Abnahme statt Versprechen
Führe ein öffentliches, ungefährliches Coding-Beispiel mit einem kleinen installierten echten Modell aus und protokolliere:
1. exakten Git-Commit + Owner-Grant + erlaubte Datei + echte fehlschlagende Tests;
2. tatsächliche lokale Modellrevision und Routing-Entscheidung;
3. optionale große Teacher-Eskalation nur wenn Betreiber NO_CHARGE/Consent real vorhanden; sonst lokal/fail closed;
4. Generated candidate -> real isolated CCC1 RED/GREEN/fresh GREEN + unabhängiger Oracle;
5. persistente knappe, owner-kontrollierte Beleg- und Rezeptablage; unabhängiger Transfer auf NEUER Fehlerklasse;
6. Vergleich local vs remote vs hybrid unter fixierten Budgets und sealed holdouts.

Messwerte: unabhängige Pass@1-Quote, echte korrigierte Aufgaben, bytes-on-phone/remote/transfer, Kontextbytes, lokale RAM/CPU, Zeit, vollständige Kosten, Owner-Grants, Abbruchquote, Netzwerk-Ausfälle, Gewinn in erfolgreichen Aufgaben je Remote-Modellaufruf. Ohne echte Hardware den Schritt als NOT_RUN markieren.

## Vier Abschlusskontrollen & Liefervertrag
(1) Syntax/APIs/kompilierbarer Code. (2) Nebenläufigkeit/Crash-Recovery/Lease/Doze. (3) Sicherheit/Provenienz/Consent/Null-Spend/keine Speicher- oder Testmanipulation. (4) End-to-End-Vogelperspektive Android->Memory->Local Model->Planner->Coding->CCC1->Review->nur danach Forschunglernen.
Liefer A) reale Branch-/PR-/Commit-Liste, B) tatsächliche native Node/Kotlin/Docker/Fold7 Resultate mit NOT_RUN, C) 5 schwere reproduzierte Defekte und volle Patches, D) eine nicht geschönte Benchmark-/Ablationstabelle, E) nächster exakter Schritt und Rückrollplan, F) vollständiges GitHub-Handoff vor Sitzungsende.
**Keine ungesicherten Erfindungen. Keine leeren Implementierungsversprechen. Keine blockierten Quellzweige.**

## Sofortiger Beginn
Starte mit eigenem Branch/Draft-PR, Wiederholungsprüfung der 9 Virtual-Fabric-Tests aus #168 auf dem exakten GitHub-HEAD und Audit von #154s separat gepinnter DeepSeek-Bridge gegen unsere vorhandene Kotlin ComputeFabric. Push den ersten kleinen, echten, testbaren Anschluss. Die Leitung muss den Commit noch in derselben Sitzung lesen und unabhängig kontrollieren können.
