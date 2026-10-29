# Continuum system integration — working handoff

## Owner scope and fixed design

Integration branch `integration/continuum-brain-system-20261029`, starting at PR241 code `4c697c8f04fbb5b0477ae5e48b3d052cce383d34` on PR238 `a3fdc17958d4e552bd3b74cf611c9fd40480f269`. This is a review candidate, not the installed Fold7 app. EXP-01/02/03/05/07/11/12; existing learning core, EWM2, ToolGate, CCC1 ledger/backlog and worker remain authoritative. No APK, model downloads, private data access, signer, production merge or new worker/memory database.

Verified source snapshots on entry: PR227 `05db7e38d70d886b305a547b5b5b628272c5ddfa`; PR237 `f54327b906a67d4f40862b5dd25a6b72e6015bc7`; PR238 `a3fdc17958d4e552bd3b74cf611c9fd40480f269`; alternative PR239 `30fbef96182ed83ee55a1e6ec732d1c533df4a51`; ARGON PR240 `67888e3323f5f30964202e901034cdf80dddb456`. Recheck before publication. PR239 overlaps and removes stronger PR238 receipt/ledger protections: compare selectively, never merge wholesale. PR236 remains a separate release blocker.

## Implementation and verification plan

1. Preserve PR241 raw evidence and final handoff in its original branch; bring its documentation checkpoint into this branch without changing tested code. Its 371 Node PASS apply to that code only.
2. Reproduce and port only the post-base PR227 unresolved-pronoun fix in `intelligence/ewm/Ewm1Workspace.kt` plus its exact regression; retain later birthday/relationship corrections.
3. Move the three ARGON F1 Kotlin implementations into the main source set, preserving one canonical implementation and published tests. Connect bounded, session-only dialogue diagnostics through `LocalConversationContext`; connect same-turn, exact-authorized evidence/relation and routing diagnostics through `ContinuumCognitiveRuntime`. Never use rewrites, hypotheses, inferred relations or calibration as answer/prompt/permission authority. Reset/eviction, revocation, identity, observer failure, bounds and unchanged-answer tests required.
4. Add the missing date/time-zone anchor to the existing context compiler using its injected clock; test midnight/time-zone behavior and context budget. No timeout increase or extra model call.
5. Preserve F2 editable offline research scripts/results; execute their existing bounded Python tests. Do not activate GPU/cache/storage/performance proposals without Fold7 evidence.
6. Bring F3 pure functions into the existing CCC1 pipeline as a read-only shadow comparison, with no second journal or queue. Reproduce and fix stale-patch credit/capability gaps first. CCC1 `recordSession` and independent verification remain the only repair-learning authority; test malicious/stale reports and observer errors.
7. Run focused real Kotlin/Node tests, then the existing JDK17/Gradle8.11.1/SDK36 standalone-QA full unit/lint/parity gate and CCC1 Node gates on a clean exact commit. Capture raw XML/logs, exitcodes, commands, versions, source/dataset hashes. No device claims from host tests.
8. Commit/push each coherent tested block to this official branch, keep a Draft PR, append final source identities/evidence/results here. Leave unsafe, incompatible or unverified features explicitly SHADOW/OFF with their exact blocker.

Initial checkpoint status was NOT_YET_TESTED; final verified results follow below. The owner has explicitly requested autonomous implementation and parallel specialist work; no additional design approval is needed for these reversible isolated changes. Agents have disjoint file ownership. Original branches and the old dirty local checkout are retained untouched.

## Checkpoint before final verification

PR242 is open. PR241 documentation/evidence is now published at `39772c9b544a9625c7c8ffc0e6aaaedb36cc5192`; integration retains its byte-identical documentation. F2's ten existing Python tests executed PASS; editable offline scripts/reports are preserved, no runtime/backend activation. A complete real standalone-QA Gradle unit run reproduced the missing-date assertion (original XML/log retained). The `--tests` invocation was refused by the existing QA task allowlist before execution; the full task runs without altering that safety gate.

Code inspection corrected the initial PR227 hypothesis: current PR238 already has a stronger unresolved-pronoun abstention, so no older runtime hunk is applied. Additional regression preserves that behavior. ARGON F1 now has one canonical main-source implementation; existing conversation state owns a bounded ephemeral diagnostic kernel, same-turn SWB1 evidence feeds fresh bounded unconfirmed relation diagnostics, and the existing reasoning router computes a non-operative proposal. Product context/answers/tool authority and extra-call budgets remain unchanged. This checkpoint is **WIP/UNVERIFIED** until the final combined Kotlin/Node gates complete.


## Abschluss: tatsächliche Integration und Grenzen

Der letzte Budgetauftrag beendet die laufenden Blöcke; keine neue Forschung, APK oder zusätzliche Integration. Geprüfter Code: **`e06e17a4e8ba12a1dce3d5308e5eb455cda30b6d`**, [Draft PR242](https://github.com/Ullmann27/device-ai/pull/242), Branch `integration/continuum-brain-system-20261029`. Spätere Abschlusscommits enthalten ausschließlich Belege/Übergabe; endgültige SHA steht im PR und in `git rev-parse HEAD`. `main` blieb `190f5eabaa60fc59292b2b80d3086aafd7743a80`.

### Vergleich und Übernahmeentscheidungen

Die GitHub-Abfrage erfasste die 20 neuesten offenen PRs #221–#242 und deren tatsächliche Heads; zusätzlich wurden 292 Remote-Branchköpfe gesichert. Das ist **keine vollständige Codeprüfung aller 292 historischen Zweige**. Die Quellenprüfung konzentrierte sich auf die vom Besitzer genannten Schnittstellen. Rohmetadaten: `docs/evidence/system-integration-20261029/verified-pr-heads.json`, `remote-heads.tsv`.

| Quelle | Entscheidung und tatsächlicher Pfad |
|---|---|
| EWM2 #227/#228/#229/#230/#237 | Bereits im Ausgangsstand. Aktuelle Abstinenz bei unaufgelösten Pronomen, Geburtstags-Attributschutz, Widerrufs-/Modusprüfung und Besitzerbestätigung erhalten; zusätzlicher Produktregressionstest. Kein älteres CLARIFY-Hunk über die neuere ABSTAIN-Lösung kopiert. |
| DAN #238 `a3fdc179…` | Geerbter Hauptdialog → ToolGate → erfolgreiches, argument-/inhaltgebundenes Webdossier → widerrufbare Besitzer-Prüfinbox bleibt bestehen und wird von der vollen Android-Regression erfasst. |
| DAN #239 `30fbef96…` | Alternative überlappt #238 und würde neuere Receipt-/Ledger-Härtungen entfernen. Kein blinder Merge; stärkere vorhandene Implementierung behalten. |
| SOL #241 `39772c9b…` | Vollständig geerbt einschließlich Ledger-, Lease-, FIFO-, Wiederanlauf- und Teamleader-Reparaturen; Codecheckpoint `4c697c8f…`, Rohbelege und Handoff unverändert erhalten. Kontextqualifiziertes Reparaturranking bleibt standardmäßig AUS. |
| ARGON #240 `67888e33…` F1 | Drei kanonische Klassen in `app/src/main/java/at/heinz/deviceai/research/argon4/`, Originaltests im Test-Quellset. Keine zweite Implementierung. Laufende Verbindungen unten; alle Forschungsentscheidungen SHADOW. |
| ARGON F2 | Vier editierbare Python-Dateien, veröffentlichte Mess-/Simulationsberichte und Referenzpatches übernommen; keine `.pyc`-Dateien. Keine GPU-, Cache-, RAM-/500-GB- oder Modellaktivierung. Hardwareprüfung fehlt. |
| ARGON F3 | Bestehender Teamleader beobachtet sein eigenes CCC1-Ergebnis; keine neue Schleife, Warteschlange, Journaldatei oder positive Lerninstanz. Zwei reproduzierte Kernfehler repariert. |
| #221/#232/#236 | Riskante native Callback-Umschreibung, unbewiesene Backend-/Updateintegration nicht übernommen. #236 braucht unabhängige Sicherheits-, Lebenszyklus-, Signatur- und echte Zweitupdate-Abnahme vor späterem Release. |

### Echte Aufrufverbindungen

1. Hauptdialog → vorhandenes `LocalConversationContext.inspectRequest/record/clear` → `Argon4ConversationShadow` → kanonischer `DialogStateKernel`. Pro Sitzung max. 12 Entitäten; Neustart des Shadow-Kerns spätestens nach 64 Nutzerzügen; Modellantwort max. 1000 Zeichen. Keine umgeschriebene Frage verlässt den Observer, `render()` bleibt unverändert. Der vorhandene Statuskopierbereich zeigt ausschließlich enum-/zahlbasierte Shadow-Werte.
2. `ContinuumCognitiveRuntime.reply()` → bereits exakt identitätsgeprüfte SWB1-Turn-Belege → `Argon4EvidenceShadow` → frischer `JustifiedRelationStore`. Max. drei aktuelle Belege à 4096 Zeichen, feste Regeln, Tiefe 2/32 Ableitungen; nur bestätigte Nutzererinnerungen werden geparst. Kein zusätzlicher Abruf, kein retained Graph, keine gespeicherten Ableitungen. Widerruf wirkt bei der nächsten aktuellen Belegauswahl; Ableitungen bleiben unbestätigte Zählwerte im schon vorhandenen Antwort-Trace.
3. Bestehender `ChatReasoningRouter.advise()` → `ComputeAllocationPolicy` als nicht-operative Shadow-Prognose. Tatsächliche Modellbereitschaft stammt vom vorhandenen Runtime-Status. Abdeckung/Aussagenprüfung/aktuelle Einwilligung sind dort nicht vollständig verfügbar; deshalb konservative Null-/Offline-Signale, keine behauptete Messqualität. Vorhandene Rangfolge, ToolGate, Modellaufrufe, Fristen und Antwort bleiben unverändert. Keine Kalibrierung aus eigenen Antworten.
4. `AiContextCompiler.compile()` liefert erstmals Gerätedatum und Zeitzone aus einem gemeinsamen injizierbaren Uhr-Snapshot; derselbe Zeitpunkt bestimmt Retrieval-Alter. Der Datumsblock wird vollständig aufgenommen oder der bestehende Fallback benutzt. Der normale Modellprompt-Fallback verwendet denselben Helfer. Datum ist keine Bestätigung aktueller externer Fakten.
5. `localTeamleaderDriver` → tatsächlicher bestehender Reparaturbericht → `runDevLoopShadow/summarizeDevLoopShadow` → zusätzliche skalare Felder im **vorhandenen** `report.json`/Reviewbericht. Observer erhält isolierte Kopien; Ausnahme, manipulierte Ausgabe oder Wunsch nach Promotion ergibt ERROR. `SUMMARY_MATCH` bedeutet übereinstimmende Berichtsfelder, **keinen** neuen unabhängigen Beweis. `recordSession` bleibt die einzige Reparatur-Lerninstanz. Thompson-Auswahl ist nur eine ausdrücklich bezeichnete Proxy-Gegenüberstellung bereits versuchter Kandidaten, keine aktive Policy. Abschaltbar mit `devLoopObserver:null`.

### Behobene Fehler und messbarer Unterschied

- Vorher fehlte der Datumsblock im echten Modellkontext: eingefrorene neue Assertion scheiterte in der vollständigen Android-JUnit-Ausführung. Nachher gemeinsamer Uhr-Snapshot, Zeitzonen-/Mitternachtsprüfung und explizite Unsicherheitskennzeichnung.
- ARGON-F3-Prototyp vergab nach einem früher grünen Kandidaten auch nach Patchwechsel/Abbruch positiven Kredit; jetzt nur für aktuelle passende Verifikation und Kritik im erlaubten Abschlusszustand. Neue PATCH-Operation verwirft frühere Test-/Kritikdaten.
- Fehlende Operationsrechte ließen sich durch Weglassen des optionalen Metadatenfelds umgehen; PATCH/Test/Review prüfen jetzt jeweils die nötige Capability. Reproduziert durch zwei rote Kerneltests, anschließend 13 grün.
- F1 war nur im Test-Quellset erreichbar; jetzt reale, begrenzte Haupt-App-Aufrufe, durch OFF/SHADOW-Gleichheit, Gesprächstrennung, Widerruf, Observer-Fehler und unveränderte Modellaufrufzahl abgesichert. Das ist ein Integrationsnachweis, **kein gemessener Intelligenz- oder Latenzgewinn**.

### Nicht nachgewiesen / nicht verändert

Keine neue Fold7-Ausführung, native Gemma-Inferenz, langfristige Stabilität, Sprach-/Sensorprüfung, aktuelle installierte Quellidentität, Signatur oder APK in dieser Runde. Letzter Besitzerbericht bleibt Continuum Lokal 0.3.27/code28 auf `b2b2277…`; dieser Draft ist damit nicht automatisch installiert. Paket `at.heinz.deviceai.continuumlocal`, Generation16, Besitzerfakten, Rechte, Originalschlüssel, produktive SWB/CDX/Gemma-Gewichte und bestehende Installationen bleiben unverändert.

F1-Kalibrierung und seine Zeit-/Energiekosten sind Forschungsannahmen; Smartphone-Overhead ungemessen. Der Prototyp behält korrigierte Referenzausschlüsse intern bis zum begrenzten Shadow-Neustart; daraus wird keine produktive Entscheidung abgeleitet. F2 bleibt offline. F3-Hashketten sind keine Rechte oder authentifizierten Prozessnachweise. Kein neues generatives Coding-Modell, kontinuierlicher Dienst oder neuronaler Fähigkeitstransfer wird behauptet. PR241 zeigt nur den dort sauber eingegrenzten deterministischen Strategietransfer.

### Genau nächster ausführbarer Geräteschritt

Auf einem sauberen, bereits eingerichteten Fold7-Checkout ausschließlich dieselbe unsignierte QA ausführen; kein Build-/Installationsbefehl:

```sh
cd "$HOME/device-ai-fold7-candidate" && test -z "$(git status --porcelain)" && git fetch origin integration/continuum-brain-system-20261029 && git switch -c review/continuum-system-e06e17a4 e06e17a4e8ba12a1dce3d5308e5eb455cda30b6d && bash scripts/continuum-fusion-full-regression.sh e06e17a4e8ba12a1dce3d5308e5eb455cda30b6d
```

Das bestehende Skript verwendet in Termux den dokumentierten nativen AAPT2-Pfad und Mobile-AIDL-Flag. Existiert der Reviewbranch schon oder ist der Arbeitsbaum verändert, bricht der Befehl ab und erhält die vorhandene Arbeit. Danach erst eine getrennte Besitzerabnahme zu Signierung/Update und realem Chat/Neustart planen.

### Tatsächlich ausgeführte Abschlussprüfungen

| Prüfung auf Code `e06e17a4…` | Ergebnis | Beleg |
|---|---|---|
| Vollständige Android-Projekt-JUnit-Tests | **PASS 1268**, 240 Suiten, 0 Fehler/Fehlschläge/übersprungen | [Zusammenfassung](../../evidence/system-integration-20261029/final-qa/summary.json), [Original-XML](../../evidence/system-integration-20261029/final-qa/junit-xml) |
| Kotlin/Java-Kompilierung + vorhandenes NewApi-Lint-Gate | **PASS**, Gradle Exit 0, BUILD SUCCESSFUL, 36 Tasks, 4m38s | [Gradle-Log](../../evidence/system-integration-20261029/final-qa/gradle-full-suite.log) |
| Kanonischer Node-Vertragssatz | **PASS 226**, 0 FAIL/SKIP, Exit 0 | [Node-Log](../../evidence/system-integration-20261029/final-qa/node-contracts.log) |
| Erweiterte CCC1-/Worker-/F3-Gates | **PASS 390**, 0 FAIL/SKIP, Exit 0 | [Worker-Log](../../evidence/system-integration-20261029/final-worker-regression.log) |
| CRE1 / SWB1 / SWB2 Kotlin↔Node-Parität | **PASS**, unveränderte deterministische Testgewichte | [Paritätsbeleg](../../evidence/system-integration-20261029/final-qa/parity-verify.log) |
| F2 Python-Formeln/Simulation | **PASS 10**, Exit 0, bestehender Interpreter | [Python-Log](../../evidence/system-integration-20261029/f2-python.log) |
| Ursprünglicher Datumsfehler | **FAIL erhalten**: 1239 PASS / 1 FAIL, Exit 1; neue Assertion unverändert im grünen Gesamtlauf | [Original-XML](../../evidence/system-integration-20261029/android-baseline-red-xml) |
| F3-Gegenprüfungen vor Korrektur | **FAIL erhalten**: 2 Kerneltests; 3 Verbindungsprüfungen; danach 100 gezielte PASS | [F3-Rohbelege](../../evidence/system-integration-20261029/f3) |
| `--tests` unter Standalone-QA | **BLOCKED vor Ausführung**, Exit 1; vorhandener Task-Schutz nicht abgeschwächt | [Originalausgabe](../../evidence/system-integration-20261029/date-red.log) |
| Fold7 / Instrumentation / native Gemma / APK / Signatur / F2-Hardware | **NOT_RUN**; kein Fernzugang, keine Signatur-/Gerätefreigabe ausgeführt | [Gesamtstatus](../../evidence/system-integration-20261029/qa-summary.json) |

Die Node-Gates überlappen: nicht als 616 verschiedene Tests addieren. Geräteerfolg oder Qualitätsgewinn folgt aus keinem dieser Host-Gates. Alle JUnit-Suiten wurden neu ausgeführt (`--rerun-tasks`), der Source-Drift-Schutz des kanonischen Skripts blieb aktiv. Native Toolchain/ARM64 ist erst durch denselben Fold7-Lauf prüfbar. Lint prüft hier ausdrücklich das bestehende `NewApi`-Gate, nicht sämtliche denkbaren Android-Lint-Regeln.

Werkzeuge: Linux x86_64, JDK 17.0.21, Gradle 8.11.1, Android SDK36 (vorhandenes AAPT2 aus Build Tools35), Node24.19.0, Git2.52.0; keine neue Installation. Exakte Befehle/Versionen und Exitcodes liegen unter [commands.txt](../../evidence/system-integration-20261029/commands.txt), [tool-versions.json](../../evidence/system-integration-20261029/tool-versions.json) und `final-qa/command.txt`. Gesamtmanifest umfasst nur die tatsächlich übernommenen Textbelege. Elf deterministisch regenerierbare Paritätsbinärdateien wurden bewusst nicht eingecheckt; Original-Laufmanifest und ihre Hash-/Auslassungsliste bleiben erhalten. Keine privaten Modelle oder Sicherungen in den Belegen.

Quell-Checkpoints (alle normal gepusht): [F2 1c8c8d9e](https://github.com/Ullmann27/device-ai/commit/1c8c8d9e8ba238d28c85b2aa393a621906824369), [F1-Verbindung 49be08c2](https://github.com/Ullmann27/device-ai/commit/49be08c2394ccdfd812ec51bae1254c05f0b5dfc), [F3-Reparatur dc16f5e8](https://github.com/Ullmann27/device-ai/commit/dc16f5e8c2ab50765237815bc49928ff844af5ba), [Datum 780c5d24](https://github.com/Ullmann27/device-ai/commit/780c5d2450478537e744c4cd9abe54cea1868881), [geprüfter Code e06e17a4](https://github.com/Ullmann27/device-ai/commit/e06e17a4e8ba12a1dce3d5308e5eb455cda30b6d). Der bekannte gemeinsame Doc-Checkpoint von PR241 wurde zusätzlich ohne Quellenänderung in die Abstammung aufgenommen; kein blindes Forschungsbranch-Merge.

Rückfall: Forschungsmodus per Konstruktor `Argon4ShadowMode.OFF`, Worker `devLoopObserver:null`; bestehende Produktwege bleiben funktionsfähig. Revert einzelner Integrationscommits auf einem Reviewzweig ist möglich; kein Reset fremder Historie. Alle ursprünglichen Branches, Forschungsstände, Grafiken und editierbaren Quellen bleiben erhalten. Keine Folgeaktion nach diesem Abschluss automatisch gestartet.
