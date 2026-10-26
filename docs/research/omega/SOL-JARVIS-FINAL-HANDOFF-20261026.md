# Sol 6.1 — JARVIS-MO Abschluss und unmittelbare Fortsetzung

> **Besitzer-Folgeauftrag bei 10%: Nachtrag maßgeblich:**
> [SOL-JARVIS-URGENT-FIXES-20261026.md](SOL-JARVIS-URGENT-FIXES-20261026.md).
> Der Android-Backlog-Startfehler und das stille Android-Reportlimit werden dort
> mit neuen RED/GREEN-Belegen geschlossen. Die folgenden Abschlussmessungen
> bleiben an ihren ursprünglichen Code-Commit gebunden und werden nicht umetikettiert.

Stand: 26.10.2026. **Auf ausdrücklichen Besitzerauftrag bei gemeldeten etwa 20%
Restkontingent kontrolliert beendet. Keine neuen Experimente oder Agentenaufträge
nach diesem Stopp. Gesamtmission NICHT abgeschlossen; Forschungsstand zur Prüfung.**

Repository: ausschließlich `Ullmann27/device-ai`.
Branch: `sol/continuum-jarvis-open-fabric-20261026`.
Draft-PR: https://github.com/Ullmann27/device-ai/pull/169

**Letzter geprüfter Code-Commit:**
`d1a5a215f42d6e7dbf08bc92eb8837653c1d119c`.
Der nachfolgende Abschlusscommit ergänzt ausschließlich Dokumentation und
Prüfartefakte; dessen genaue SHA steht am PR-Head und in `git log -1`.
Alle finalen nativen Messungen sind ausdrücklich an obigen Code-Commit gebunden.
Keine Freigabe für Main, APK, Installation, produktive Gewichte oder Besitzerwissen.

## 1. Ergebnis und echte Grenzen

Neu verbunden und geprüft sind:

1. Die vorhandene signierte Kotlin-Registry → begrenzter Ressourcen-Snapshot →
   vorhandener Node-Modellplaner, einschließlich echter Ed25519-Prüfung im
   synthetischen JVM-Test und unverändertem JSON-Transport zum Node-Verbraucher.
2. Die optionale #154-DeepSeek-Bridge → bestehender CodingProvider → feste CLI →
   authentifizierter HTTPS-Vertrag. Lokale TLS-Gegenstellen liefern Testantworten;
   ein realer DeepSeek-Betreiber ist **NOT_CONFIGURED**.
3. Vorhandenes NTW1-Ranking / Statement-Übersetzung / A-B-C-Diagnose → typisierter
   Besitzerauftrag → echter Docker-Executor → RED/GREEN/frischer GREEN-Worktree
   auf zwei öffentlichen Entwicklungsfällen. Das Modell ist untrainiert; keine
   neue Sprachmodell-Inferenz oder Generalisierung nachgewiesen.

Außerdem wurden Registry-Rechte, JUnit-Vollständigkeit, Lerngate und Git-/Abbruch-
Nachweise repariert. Der Code ist im eigenen Draft-PR veröffentlicht und lesbar.
Es gibt weiterhin **keinen** vollständig bewiesenen Android-Chat→KI-generierter
Patch→unabhängige Prüfung→Lernen→nächster autonomer Auftrag auf dem Fold7.

## 2. GitHub-Abhängigkeiten und Commit-Historie

Ausgangspunkt #168: `78ceeeb863a3806b0bc088a4a2c2544d96be24cd`.
Main bei Beginn und Abschluss unverändert:
`190f5eabaa60fc59292b2b80d3086aafd7743a80`.

```text
#151 → #159 → #160 → #161 → #162 → #163 → #164 → #165 → #166
                                                           ├─ #167 Scorecard ─┐
                                                           └─ #168 Fabric ────┴─ #169
#154 auf älterem #151-Stand ── nur geprüfte Transport-/Provider-Deltas ──────────┘
#158 auf Main: KEINE komplette Baby-Core-Datei übernommen
```

Die Heads #151–#168 wurden erneut über GitHub gelesen. Exakte vollständige
Head-/Base-SHAs, Status und Git-Abstammung:
`docs/evidence/sol-jarvis-20261026/initial-github-heads.json` und
`dependency-ancestry.json`.
#151 und #159–#168 sind Vorfahren dieses Code-Commits. #152–#158 sind nicht
vollständig Vorfahren; das schließt selektive ältere Übernahmen nicht aus.
Nicht blind fehlende Gesamtbranches cherry-picken.

#167 wurde nach Prüfung seines sechs Dateien umfassenden Deltas gegen #166
als Geschwisterzweig in den eigenen Branch integriert. #154 wurde **nicht** als
ganzer Branch übernommen: Die Provider-Differenz beginnt nach seinem duplizierten
Budgetcommit `31331293`; aktuelle Budgetreservierung und Quellbindungen blieben
erhalten. Der bestehende Tier-Planer erhielt nur den benötigten gepinnten
Revisions-Export; seine API wurde nicht durch die ältere Datei ersetzt.

Eigene Checkpoints, alle im PR erreichbar:

| Vollständige SHA | Inhalt |
|---|---|
| `4e4abc61ffb9d9a7e9d6db351c306d1105baf2fe` | Frühe Sicherung, eigener Branch und Draft-PR |
| `f4ea4c131c1732560e8624c3005c5733d4f9bf5d` | Geprüfte Geschwisterintegration #167 |
| `8bcecfe1a053dadfa724c1a1fe9bdc6ab5edd5ca` | Erste 58 nativen Prüfungen und Arbeitsplan |
| `475458b3c7e0832b67b57d5d2f623444aa7613fe` | Signierte Registry: unveränderliche Mengen, Gültigkeit, Node-Bindung |
| `90e75d3a41cb5ee292f1479c60b7767789d1451f` | Echter Kotlin→Node-Ressourcenvertrag |
| `04e984562d5755922b5b16fd72c61ab0f3f06be1` | Vollständige Testzählung, Evidenz-/Lerngate, nativer Beweis-Runner |
| `db475acea863905cdf8db5ffd36bf4397f06a538` | Selektive DeepSeek-Integration und erste reale A/B/C-Artefakte |
| `d1a5a215f42d6e7dbf08bc92eb8837653c1d119c` | HEAD-/Signal-/Lernschutz und veröffentlichte Prüferberichte |

Die frühere NTW1-Arbeit war bereits gesichert: Original-WIP
`2abfee480d826e58ce6a80b2ce685a438d8b0329`, tatsächlich integrierter #165-Stand
`da81db718612aa6f12ba27bd6db043e57c739e5f`. Keine Rekonstruktion aus Erinnerung.

## 3. Verbindungskarte mit ehrlichen Statusangaben

| Übergang | Tatsächlicher Stand |
|---|---|
| Android-Chat → Intent / bestätigtes Gedächtnis | Bestehende echte Quellverbindungen in `AgentActivity.runContinuumDialogue`, `CognitiveDecisionBridge`, `ContinuumCognitiveRuntime`; aktuelle Geräteausführung NOT_RUN |
| Gedächtnis → lokales LiteRT-Modell → Antwort / ToolGate | Bestehender `runLocal` / `LocalModelReasoningAdapter` / Runtime-Pfad. Fehlendes Modell wird abgewiesen. Keine installierten Besitzergewichte hier beobachtet; keine aktuelle Inferenz |
| Chat-Modell / DevelopmentProposal → typisierter Coding-Auftrag | **NICHT VERBUNDEN**. Der vorhandene Coding-Dialog verlangt manuelle Commit-/Pfad-/Akzeptanzangaben und Besitzerprüfung |
| Registry → Snapshot → vorhandener Modellplaner | **IMPLEMENTIERT UND NATIV GEPRÜFT** als API. Snapshot enthält keine privaten Kontexte oder Besitzerkennung. Kein produktiver Activity-/Service-Aufrufer bzw. Enrollment nachgewiesen |
| Ressourcenangebot → tatsächliche Modellgewichte / Ausführung | **NICHT BEWIESEN**. JSON ist nicht authentifiziert; Modellrevision, Verfügbarkeit und Lokalität bleiben Deklarationen. Plan ist nicht ausführungsberechtigt |
| CodingProvider → optionaler DeepSeek-Transport | **IMPLEMENTIERT, NATIV PROTOKOLLGEPRÜFT MIT TESTGEGENSTELLE**. Explizite Providerkonfiguration und exakte Einzelzustimmung bleiben notwendig; real NOT_CONFIGURED |
| NTW1 → atomarer Übersetzer → gebundener CCC1-Kandidat | **IMPLEMENTIERT UND NATIV GEPRÜFT**, Forschungsmodus; C versucht exakt Rang 1 |
| Besitzerauftrag → Docker → RED/GREEN/frischer GREEN | **ECHT AUSGEFÜHRT** auf zwei synthetischen öffentlichen Entwicklungsfällen, Ergebnisse weiter reviewpflichtig |
| CCC1-Ergebnis → produktive Gewichte / bestätigtes Besitzerwissen | Für diesen breiten Forschungs-/Modellpatch-Pfad **GESPERRT**. Legacy-Lernen begrenzter Strategien existiert weiterhin; keine globale Abschaltung behaupten |
| Kanonischer Backlog → Lease / Journal / Recovery / lokaler Loop | Bestehender Node-Pfad, gezielte echte Lebenszyklus-/Recovery-Tests bestanden. Kein neuer zweiter Worker. Gesamtes Mehrfachzyklus-Experiment E NOT_RUN |
| Typisiertes Android/Termux RUN → OS-Isolation auf dem Telefon | **BLOCKIERT**: derzeit nur Docker-Executor mit `/var/run/docker.sock`; kein bewiesener normaler Termux-Confinement-Backend |
| Haupt-App-Update / Fold7 / Signatur / Datenmigration | **NOT_RUN / Voraussetzungen fehlen**; keine APK erzeugt, signiert oder installiert |

Vollständige Call-Site-Karte und Workerbefund:
[Android-/Worker-Audit](SOL-JARVIS-ANDROID-WORKER-AUDIT-20261026.md).

## 4. Reparierte Defekte und erhaltene Grenzen

- **Signierte Mengen veränderbar:** Nach `register` ließen sich erlaubte
  Task-Typen/Backends über geteilte MutableSets erweitern. Vor Signaturprüfung
  wird jetzt ein unveränderlicher Snapshot erstellt. Native Reproduktion rot,
  anschließend grün.
- **Zeit-/Zielbindung:** Noch nicht gültige Manifeste und Clock-Rollback werden
  berücksichtigt; eine ausdrückliche Node-Zuweisung wird nicht ersetzt.
- **JUnit-Diagnoselimit verfälschte Abdeckung:** 100 fehlgeschlagene Testfälle
  wurden nach 60 Diagnosen nur als 60 gezählt. Die Aufzählung läuft nun vollständig
  weiter, während Diagnoseausgaben begrenzt bleiben.
- **Unvollständige Evidenz:** Überlange XML-/übergroße oder verlinkte/ausgelassene
  Host-Verzeichnisberichte markieren Evidenz unvollständig. Kein grüner Nachweis
  und kein negatives Strategie-Lernsignal daraus.
- **HEAD-Drift:** Nach Patch und Test wird die echte Worktree-HEAD wieder mit dem
  freigegebenen Commit verglichen; auch Originalquelle wird vor Abschluss geprüft.
  Regression verwendet echte temporäre Git-Commits.
- **Abbruch als RED/Lernsignal:** Fehlender/unerwarteter Exitcode, Signal und
  Prozessfehler können keinen reproduzierten RED-Nachweis geben. Signal-/Fehler-
  Metadaten bleiben im Ledger erhalten und sperren negative Lernaufnahme.
- Drei überholte Quelltext-/Fixture-Erwartungen aus dem initialen roten Gesamtlauf
  wurden an aktuelle Routing-/Commit-/Evidenzanforderungen angepasst. Keine
  Produktionssicherheitsregel wurde dafür gelockert.
- `DeviceToolExecutorRunner` wurde unverändert in eine eigene Android-Adapterdatei
  verschoben; Paket und API bleiben gleich. So sind die echten plattformneutralen
  Verträge ohne erfundene Device-Executor-Testklasse kompilierbar.

Report-Hashes sind keine Signatur. Auch nach echten zwei Worktrees bleiben
`verifiedSuccess:false`, `semanticRepairVerified:false`,
`testEvidenceAuthenticated:false`, `learningEligible:false`. Kandidaten und Tests
teilen eine Laufzeit; ein unabhängig authentifizierter semantischer Oracle-Pfad
ist nicht allgemein nachgewiesen. Keine automatische produktive Promotion.

## 5. Abschließende echte Tests

Geprüfter Code: `d1a5a215f42d6e7dbf08bc92eb8837653c1d119c`.
Host: Linux x86_64, Node **v24.19.0**, Java **21.0.12.1**, Kotlin-CLI **2.2.0**,
JVM-Ziel 17. Projekt-Gradle konfiguriert AGP 8.10.0 / Kotlin 2.4.0; **nicht**
als ausgeführter Gradle-Build ausgeben.

| Prüfung | Ergebnis | Beleg |
|---|---|---|
| Gesamte `.github/continuum`- und `scripts`-Node-Tests, echte vollständige ESM-Abhängigkeiten | **1351 PASS / 0 FAIL / 7 SKIP**, 1358 gesamt, Exit 0, 112,319 s | `final-full-node.json` / `.tap` |
| Syntax aller 400 getrackten `.mjs`-Module | **400 PASS / 0 Fehler** | `final-syntax.json` |
| Echter ausgewählter Kotlin-Quellgraph und JUnit | **15/15 PASS** | `jvm-final/junit.txt`, Compiler-/Compile-Ausgaben |
| Echter JVM-Export → unveränderte Bytes → Node | **9/9 PASS / 0 SKIP** | `jvm-final/node-contract.txt`, Snapshot |
| Abschlussregressionen für Verifier / Evidence / Ledger | **31/31 PASS** | `ccc1-final-safety-green.tap` |
| DeepSeek-/Provider-/CLI-/TLS-/Fabric-Kompatibilität | **40/40 PASS**; Antworten synthetisch | `deepseek-integration-green.tap` |
| Unabhängiger Android-/Worker-Prüfer | **41 + 6 gezielte PASS**; enthält statische Android-Wiring-Tests | `android-worker-targeted.tap`, `android-worker-journal-targeted.tap` |
| Echter Docker-A/B/C-Wiederholungslauf nach letzter Codereparatur | **5 vollständige RED/GREEN/frisch-GREEN-Arme**, ein Rang-1-Abbruch | `abc-final/`, `final-selected-verification.json` |

Alle relativen Belege liegen unter `docs/evidence/sol-jarvis-20261026/`.
Keine hier neu ausgeführte V8-Simulation wurde als nativer Erfolg gezählt.
Tests nutzen teilweise offen deklarierte Transport-/Daten-/Prozessfixtures;
ein nativer Testlauf macht eine solche Fixture nicht zur realen Modellinferenz.

Die sieben übersprungenen Node-Fälle: sechs ältere Prüfungen verlangen `kotlinc`
auf PATH (hier nur embeddable JARs); der siebte ist der optionale JVM→Node-Fall,
der im separaten gemeinsamen Runner tatsächlich mit 9/9 ausgeführt wurde.
Nicht pauschal als sieben bestandene Tests zählen.

Historischer Ausgangslauf desselben vollständigen Checkouts:
**1306 PASS / 5 FAIL / 6 SKIP**; Rohfehler bleiben erhalten. Spätere Änderungen
heben diese Historie nicht auf. RED-Artefakte für die gefundenen Defekte sind
ebenfalls veröffentlicht. Zwei anfängliche Docker-Image-Setupversuche brachen
vor Tests ab; `executor-setup-attempts.json` bewahrt sie als Infrastrukturereignisse.

**NOT_RUN / UNAVAILABLE:** Android Gradle/Lint/APK; Fold7; Besitzer-Signierung;
Installation/Update/Datenmigration; echtes Gemma/Qwen/DeepSeek-Serving; versiegelte
Transfer-Holdouts; Replay-/EWC-/GEM-Ablationen; Remote-Eskalationsvergleich;
vollständiger autonomer Mehrfachzyklus mit allen geforderten Fehlerinjektionen.

## 6. Was die Intelligenzmessung wirklich sagt

| Öffentlicher Entwicklungsfall | A Referenz | B vorgegebener Operator | C tatsächlicher untrainierter Rang 1 |
|---|---|---|---|
| Sortierung ohne Mutation | RED 4 Tests / 3 Fehler → GREEN 4 → frisch GREEN 4 | identisch bestanden | bestanden |
| Einbasierte Pagination | RED 4 / 4 Fehler → GREEN 4 → frisch GREEN 4 | identisch bestanden | `NOT_APPLICABLE`, kein Versuch mit Rang 2 |

Jede Suite enthält zwei Regressionstests und zwei separat formulierte
Verhaltensprüfungen. Alle fünf ausführbaren Arme verwendeten dieselben Schutz-
und Budgetregeln. Testidentitäten und Quellen-/Kandidaten-/Snapshot-Hashes sind
erhalten; die exakten kleinen Quellrepos liegen als Git-Bundles bei.

**Gemessener Hebel:** Die vorhandene Übersetzung kann beide kontrollierten
Reparaturen wirklich durch CCC1 bringen. Bei identischem Operatorbestand bleibt
die untrainierte Auswahl auf 1/2; bessere Strategieauswahl ist nun als Engpass
messbar. Nullmerkmale, Seed 20261026 und null Updates sind offengelegt.
Das ist **kein** Nachweis neuronaler Verbesserung, zuvor unbekannter
Generalisierung oder weniger Großmodellaufrufe. Es wurde kein solcher Score erfunden.

## 7. Drei Prüfagenten und vier Kontrollgänge

Alle drei Agentenberichte lagen vor Abschluss vor und sind veröffentlicht:

- Android/Worker: Call-Sites, 47 gezielte Prüfungen, echter offener Start-/Leasefehler.
- CCC1-Sicherheit: read-only Review; zwei Befunde mit Abschlussregression repariert,
  Android-Reportlimit offen. Die zunächst angefragte adversariale Ausführung wurde
  automatisch abgewiesen; die sichere Leseprüfung wurde abgeschlossen. Keine
  nicht ausgeführte Angriffssimulation als Erfolg behauptet.
- Virtual Fabric: fünf physikalisch begründete Wege und Primärquellen;
  [Forschungsbericht](SOL-JARVIS-VIRTUAL-FABRIC-RESEARCH-20261026.md).

| Kontrollgang | Tatsächliche Methode / Grenze |
|---|---|
| 1 Syntax und APIs | 400 Node-Module, vollständiger nativer Node-Lauf, realer ausgewählter Kotlin-Graph; komplette Android-SDK-Kompilierung fehlt |
| 2 Logik / Nebenläufigkeit | Registry-Regressionen, bestehende Lease-/SIGKILL-/Recovery-Tests und unabhängiger Worker-Quellreview; Android-Buchungsfehler bleibt offen |
| 3 Sicherheit / Provenienz | Native Evidenz-/Git-/Abbruch-Negativtests, echter Docker-Pfad, unabhängiger CCC1-Lesereview; keine allgemeine Report-/Oracle-Authentifizierung |
| 4 Gesamtsystem | Unabhängige Android-Call-Site-Verfolgung plus echte Node/Docker-Entwicklungsfälle; fehlende Chat→Coding-, Registry→UI- und mobile Isolation ausdrücklich markiert |

## 8. Ressourcen, Virtual Fabric und Android

Keine GPU gebucht, kein externer Modellaufruf, kein kostenpflichtiger Dienst
aktiviert. Tatsächlich eingerichtete Hardware ist nur dieser CPU-Host.
Docker verwendet vorhandenes `scripts/continuum-isolation.Dockerfile`, Image-ID
`sha256:695fb07716de18118c92d0e613fe8b7413f86c27dc2b5fd5faa4e8fca51d29f8`:
Netz aus, Nonroot, read-only, keine Capabilities, no-new-privileges, je Profil
1 CPU / 512 MiB / 128 PIDs / 30 s. Das sind gesetzte Grenzen, keine gemessenen
Peak-RSS-/Energiezahlen. Keine Hosting-Rechnung erzeugt; Gesamtnetzvolumen und
Cloudkosten wurden nicht unabhängig gemessen.

Bekannte Toolchain-Downloads: 249.432.552 Bytes offizielle JVM-Abhängigkeiten
plus 22.439.458 Bytes geprüftes llama.cpp-Releasearchiv, zusammen 271.872.010
Bytes Nutzdateien, **ohne** Git-/Registry-/Metadaten-/Protokolloverhead. Der vor
dem Besitzerstopp begonnene Runtime-Download wurde nur per veröffentlichtem
SHA-256 und MIT-Lizenz geprüft; kein Modell geladen, Runtime nicht ausgeführt,
keine Modellgewichte heruntergeladen. Wiederaufnahmeinformationen stehen in
`unfinished-local-inference-preflight.json`; Toolbinärdateien gehören nicht ins Repo.

Die unabhängige Metadatenrecherche bestätigt 48 DeepSeek-Shards mit zusammen
510.296.708.312 Bytes bei Revision `1bae0e1088993b9b82de08f59de5be5a34e42170`;
keine Bytes der Gewichte oder geladene GPU damit attestiert. Physische Speicherung,
Compute und Anbieterzustimmung bleiben nötig. SGLang-V4.1-Preview/Engram-Offload
ist eine reale zu prüfende Option; nicht mit V4-Kompatibilität gleichsetzen.
OneToken bleibt mehrdeutig/unverifiziert. A–E-Quellen, Speicher-/Transfer-/Kosten-
gleichungen und konkrete Grenzen stehen im separaten Forschungsbericht.

Android: Default-`applicationId` ist `at.heinz.deviceai`; vorhandene Flags erzeugen
u.a. `.mentorpaired`/`.preview`. Das beweist nicht die Paket-ID der installierten
Besitzer-App. SDK36, gepinntes Gradle, AAPT2, apksigner und Besitzerzertifikat fehlen
hier. Keine APK und kein Kompatibilitätsversprechen. Datenformate, Besitzererinnerungen
und produktive Modellgewichte wurden nicht migriert oder geändert. Gleiches Paket,
echtes Zertifikat, höherer Versionscode und reale Migrations-/Geräteprüfung bleiben
vor jedem Update zwingend. Android-Doze erlaubt keine unbegrenzte Hintergrundgarantie.

## 9. Offene Arbeiten in verbindlicher Reihenfolge

1. **P1: Android `startDevelopmentJob` fail-open bei fehlender Buchung.**
   `AgentActivity.kt` führt `coordinator.run(task)` auch aus, wenn
   `devBacklog.startJob(task)` fehlschlägt. Vor Modellarbeit abbrechen, busy/controller
   sauber zurücksetzen, mit schreibgeschütztem/gesperrtem/korruptem Backlog und
   Modellaufrufzähler beweisen: null Aufrufe. Nicht mit einer neuen Backlogverwaltung lösen.
2. **P1: Isolierter Android-Reportcap 400.** Vor der Begrenzung abbrechen oder eine
   unvollständige Evidenz bis Verifier/Ledger durchreichen. >400 synthetische
   Reportdateien gegen den echten festen Runner testen; Fake-Gradle klar markieren.
3. **P1: Semantischer Oracle / Reportauthentizität.** Keine positive produktive
   Lernaufnahme aus Roh-JUnit oder Modellselbstbehauptungen. Geschützte unabhängige
   Verhaltensorakel und Bindung an tatsächliche Runner-/Testmanifestidentität
   entwickeln, bevor breite autonome Reparaturerfolge trainierbar werden.
4. Vorhandenen Chat-/Development-Vorschlag kontrolliert als **ungeprüften Entwurf**
   in den bestehenden typisierten Coding-Dialog übergeben. Echten Quellcommit,
   Pfade und Besitzerreview verlangen. Keine Shell aus Modelltext übernehmen.
5. Neue Registry-Snapshot-API an einen tatsächlich bestehenden autorisierten
   Enrollment-/IPC-Aufrufer anschließen; serialisiertes JSON allein ist keine
   Registry-Autorisierung. Modellrevision, Speicher, Verfügbarkeit, Lokalität und
   Zustimmung getrennt halten. Logische `model://`-Adresse ist noch keine vollständige
   versionierte produktive Vermittlung.
6. Echte lokale Modellinstallation/Hash/Runtime prüfen und vorhandenen Provider
   mit realer Inferenz messen. Optionaler Remote-Betreiber bleibt NOT_CONFIGURED,
   bis ein explizit genehmigter Endpunkt den bestehenden Vertrag erfüllt.
7. Mobiles Confinement für normalen Android/Termux-Pfad separat nachweisen;
   Dockerpflicht nicht durch unisolierte Hostausführung ersetzen. Optionaler
   Dauerrechner bleibt Ergänzung, kein nachträglich eingeführtes Produktzwangsziel.
8. Erst danach versiegelte Reparaturrezept-/Ranking-/Retention-/EWC-Vergleiche und
   den vollständigen genehmigten Mehrfachzyklus ausführen. Produktive Gewichts-
   übernahme bleibt getrennt freigabepflichtig.

## 10. Genaue Wiederaufnahme für GPT-6 und Astra

```bash
gh repo clone Ullmann27/device-ai
cd device-ai
git switch sol/continuum-jarvis-open-fabric-20261026
git status --short
git log -8 --oneline
```

Zuerst diese Übergabe, `AGENTS.md`, Experimentvertrag/-register und Issue #41
lesen; dann **aktuelle** PR-Heads/Kommentare neu prüfen. Weiterentwicklung auf
eigenem sichtbaren Folgebranch/Draft-PR oder nach klarer Koordination hier;
keine fremden aktiven Branches überschreiben. Neuere Köpfe sind keine Erlaubnis
zum blinden Komplettmerge. Kleinster nächster Patch ist Punkt 1 oben.

Reproduktion ohne Modelldownload oder Dienstbuchung:

```bash
node --test --test-concurrency=2 .github/continuum/*.test.mjs scripts/*.test.mjs
CONTINUUM_FABRIC_JVM_DEPS=/pfad/zu/geprueften/jars \
  bash scripts/continuum-fabric-jvm-check.sh /tmp/continuum-fabric-resume
docker build -f scripts/continuum-isolation.Dockerfile -t continuum-isolation .
docker image inspect continuum-isolation --format '{{.Id}}'
```

Für den bereits genehmigten synthetischen Entwicklungslauf eine private externe
JSON-Konfiguration mit gepinnter Image-ID, konkretem Node-Toolchain-Verzeichnis
(`bin/node` muss existieren), timeoutMs 30000, memoryMb 512, pids 128, cpus 1
verwenden. Kein Besitzer-/Quellverzeichnis als Toolchain mounten. Danach:

```bash
node scripts/continuum-bottleneck-local-proof.mjs \
  --owner-approved-development-fixtures /absolut/isolation.json /neues/externes/evidenzverzeichnis
```

Die genaue ausgeführte Befehlsliste und Quell-SHA stehen in den finalen JSON-
Manifesten. JVM-Abhängigkeiten und SHA-256-Inventar: `fabric-jvm-dependencies.json`.
Keine privaten Schlüssel/Tokens aus dem Chat anfordern oder ins Repository legen.
Der signierte Besitzer-APK-Test bleibt eine getrennte spätere Geräteaktion.

Rollback: Einzelne eigene Integrationscommits auf einem neuen Reviewbranch mit
`git revert` zurücknehmen und passende Tests wiederholen; keinen gemeinsam
verwendeten Verlauf resetten/force-pushen. Kein Main-Merge, kein Branchlöschen.

Alle wertvollen Quellen, Tests, originalen RED/GREEN-Ausgaben, synthetischen
Quellbundles, Prüferbefunde und Fortsetzungsbefehle sind im Draft-PR gesichert.
Eine unvorhersehbare Workspacebeendigung erfordert keine Erinnerung an diesen Chat.
