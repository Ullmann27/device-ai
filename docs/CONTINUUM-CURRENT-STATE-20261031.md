> **P0 RELEASE-FREEZE 31.10.2026:** Die **neueste gemeinsame Source-of-Truth für eine nächste Besitzer-APK** ist **`release/continuum-local-complete-v31-20261031`**, siehe [Release-V31-Matrix](release/CONTINUUM-COMBINED-OWNER-RELEASE-V31.md). Der ältere Render-Release `0.3.29/versionCode30` ist **veröffentlicht**, aber enthält nicht sämtliche späteren Forschungs-/JARVIS-/CDX1-Änderungen. Ziel: `0.3.30/versionCode31`, `at.heinz.deviceai.continuumlocal`, **Originalsignatur**, kompletter QA/Gradle-/Fold7-Test. **Nicht fertig, nicht signiert, nicht online!** **KEINE WEITERE FUNKTIONSFORSCHUNG VOR DEM KOMBINIEREN, TESTEN UND PUBLIKATIONSGATE.** Auch Node-Forschungsskripte erzeugen keine Android-Rechenkapazität. Frühere Statusangaben in dieser Datei sind historisch und nicht der Release-Cutoff.

# DEVICE AI CONTINUUM — verbindlicher Projektstand und nächster Funktionsnachweis

**Stand:** 31. Oktober 2026 · **Repository:** `Ullmann27/device-ai`.
**Gültigkeit:** Architektur- und Quellenorientierung für Sol, Astra, Opus und alle weiteren Agenten. Bei jedem Einstieg aktuelle Branch-HEADs, PRs und reale Geräteversion erneut prüfen.

## 0. Das Produktziel — **echter** JARVIS, keine Demo

Die installierbare **Continuum Lokal** App (`at.heinz.deviceai.continuumlocal`) soll ein tatsächlich benutzbarer, lokaler Android-Assistent mit natürlicher Sprache, dauerhaftem und zeitlich richtigem Gedächtnis, transparenter Wissensgrundlage, Werkzeugzugriff, eigenständiger Fehlersuche, einem **real arbeitenden Coding-Agenten** und kontrollierten Softwareupdates werden. Ein späteres gemeinsames Geräte-/Cloud-Compute-Netz ist optional, nicht Voraussetzung für den lokalen Basischat.

Die Begriffe „JARVIS“, „DAN“, „Mythos 5.1“, „Astra 6.1“ sind **Vision, Rollenmodell und Forschungsmaßstab**, keine Belege für verfügbare fremde Modellgewichte oder eine bereits realisierte Superintelligenz. Der realistische Buchmodus darf den Code- oder Testnachweis nie ersetzen. Die vom Besitzer übermittelten JARVIS-/MCP-Videos sind **Funktionsreferenzen, keine verifizierte Aussage über Leistungen fremder Demos**; detaillierter Videobefund in [PR #263](https://github.com/Ullmann27/device-ai/pull/263) auf eigenem Zweig. Keine automatische Übernahme von Marketingbehauptungen.

## 1. Architektur, die nicht vergessen oder überschrieben werden darf

**Laufendes Kernprinzip:** Android-UI → Besitzereingabe → `AiContextCompiler` (mit Privacy-/Zeitvalidierung) → lokales `LocalModelRuntime`/Chatrouter → `BoundedAgentOrchestrator` → `ToolGate` → geprüftes Tool-Ergebnis → Benutzer und versionsgebundene Erfahrung.

**Biologisch-neuronale Forschungsstränge behalten:** `ContinuumBrain` und `ContinuumCognitiveRuntime`, CRE1, SWB/EWM-v2, `MemoryEcology`, `MemoryGraph`, `SelfModel`, zeitabhängige Zustände, Liquid/Continuous-Time-, Sparse-Gate- und Spiking-/Adaptive-Verfahren, Decoderforschung. Einige Komponenten sind experimentell/SHADOW oder noch nicht end-to-end gemessen. Niemals einen simulierten „Denkzustand“ mit realer neuronaler Modellleistung verwechseln. Neuronale Thesen: auf wohldefinierten Aufgaben gegen denselben Modell-Baseline testen; Ressourcenverbrauch und Fehler vollständig protokollieren.

**Coding-Agent M8:** vorhandene `.github/continuum/coding-agent-loop.mjs`, `coding-orchestrator.mjs`, `autonomous-teamleader.mjs`, `coding-task.mjs`, isolierter Executor, MCP-Preflight und `AgentActivity.runLocal` **weiterverwenden**. Typischer Fortschrittsnachweis: bisher unbekannter Fehler → reales lokal laufendes Modell → begrenzter Patch → unabhängige RED→GREEN-Tests → Besitzerentscheidung. Simulierte Skriptgehirne, Reviewer-Text oder grüne Mock-Tests reichen nicht.

**Funktional geforderte JARVIS-Pfade:** Sprachdialog und Voice-Lifecycle, reale Gerätesteuerung unter Android-Rechten, verständliches Kontrollzentrum, geprüfte Suche/Research, private Kurz- und Langzeiterinnerung, Skill-/Routine-Vorschläge (keine automatische Rechteausweitung), Tool-/MCP-Adapter nur autorisiert, periodische Benachrichtigung/Briefings, in-place Besitzer-Update-Knopf. Kein stets aktiver Hintergrundagent auf Android behaupten, solange Android-Lifecycle/Hostausführung nicht real nachgewiesen sind.

## 2. Wichtige überprüfte Branches/PRs (alle nicht als Installationsnachweis interpretieren)

| Stufe | Quelle | Bedeutung / Grenzen |
| --- | --- | --- |
| M7/M8 Arbeitsbasis | [PR #265](https://github.com/Ullmann27/device-ai/pull/265) | Agent-Loop, Teamleader, MCP-Preflight, EWM-v2, Update-Button auf Basis #260; Bericht über Kotlin/Node-Teiltests, aber keine vollständige Fold7-Abnahme |
| Neuro-JARVIS Integration | [PR #284](https://github.com/Ullmann27/device-ai/pull/284) | kontrollierte Integration vorhandener Erfahrung/Gedächtnis/Compute-Grundlagen, kein bewiesener Zuwachs der Modellintelligenz |
| M8 lokaler Modelladapter | [PR #285](https://github.com/Ullmann27/device-ai/pull/285) | echter App-Pfad bleibt erhalten; Filter- und Tool-Evidenz-Härtung im Quellcode, volle Tests ausstehend |
| Originalsigniertes Release | [PR #286](https://github.com/Ullmann27/device-ai/pull/286) | Release- und QA-Vertrag, **keine** signierte APK veröffentlicht; Original-P12/Host- und Versionsprüfung erforderlich |
| Frontier Compute/Reuse | [PR #287](https://github.com/Ullmann27/device-ai/pull/287) | 3.000 **logische** Experten, pure Task-Deduplikation, geteilte Zwischenstufen, synthetische A/B-Negativkontrollen; keine zusätzlichen FLOPS oder live verbundene Agenten |
| JARVIS Video/MCP | [PR #263](https://github.com/Ullmann27/device-ai/pull/263) | Besitzerreferenz-Video und reale MCP-Integrationsoptionen, noch nicht produktiv integriert |

**Stapelbasis für diese Übergabe:** `#265 → #284 → #285 → #287`. `#286` ist ein **paralleler**, ungemergter Signatur-/Release-Zweig; `#263` ebenfalls unabhängig. Git-Commits, Änderungen und tatsächliche Mergekompatibilität vor Integration separat überprüfen. Das ältere `main` und die historische Root-README sind **nicht** der jetzige produktive App-Stand.

## 3. Compute-Forschung: echter Fortschritt, aber exakt begrenzter Befund

Neuer [Übergabebeleg](handoff/CONTINUUM_FRONTIER_COMPUTE_20261031.md) und [vollständiges Forschungsprotokoll](../tools/continuum-virtual-fabric/FRONTIER_RESEARCH.md).

- 50/100/1.000/2.000/3.000 **logische** Agentenrollen mit Ressourcenreservierung; Null GPU ohne tatsächlich autorisierte GPU.
- 3.000 rein deterministische Beispieljobs mit 30 verschiedenen Inputs: 30 statt 3.000 teure Kernberechnungen; 2.970 vermeidbar.
- 3.000 **verschiedene** Endjobs, die 30 gemeinsame teure Zwischenstufen nutzen: 3.000 eigenständige Ergebnisse aus 30 Zwischenberechnungen, jeweils gegen vollständige Referenz verglichen.
- Negativkontrolle 3.000 verschiedene Zwischenstufen → 3.000 echte Berechnungen, **kein** Vorteil.
- **43/43 isolierte V8-JS-Prüfungen mit Node-Shims**, **keine** originalen Node-/Android-Gerätetests auf aktuellem Forschungs-HEAD; keine gemessene LLM-Beschleunigung, keine Fern-GPU oder 24/7-Nutzung.

**Intelligente technische Richtung:** verlustfreie wiederverwendbare Zwischenergebnisse (z.B. Quellbaum-/AST-/Embedding-/Tool-Ergebnisse) vor einer **wirklich** ausgeführten, individuellen KI-Entscheidung. Nie ungetestete generierte Reparaturen, geheime Erinnerungen oder Text-Modellevaluierungen als „verifiziert“ cachen. Konkrete nächste Forschungsarbeit: Persistenz/Cache-Ablauf, Digest+Quelle, Session-Privacy und read-only Anbindung an vorhandene `ComputeFabric` und M8-Arbeitsweg. Erst auf echten Arbeitspaketen messen.

## 4. Release-Wahrheit / Update der installierten App

Vom bisherigen Geräteverlauf berichteter letzter **installierter** Stand: `Continuum Lokal 0.3.28 / versionCode 29`, Paket `at.heinz.deviceai.continuumlocal`, Update-Button **„Updates prüfen und laden“**. Dies ist eine bisherige Geräteangabe, **kein** jetzt erneut vom Fold7 ausgelesener PackageManager-Befund.

**NEUE APK MUSS** exakt dieselbe Paketkennung und das bestehende Signierzertifikat haben, installierten `versionCode` tatsächlich übersteigen (**mindestens 30, sofern das Fold7 aktuell 29 meldet**), passende Release-Metadaten am in der App gepinnten Updatehost besitzen und zuerst alle Integrations-/Gerätetests bestehen. Die #286-Dokumentation verwendet teilweise **v29/versionCode 29**: das wäre **kein** gültiger Ersatz für eine bereits installierte `versionCode 29`. In jeder zukünftigen Release-PR zuerst aktuellen Gerätecode, Release-Version, Signatur-SHA-256 und Quellstand **explizit** neu abgleichen.

**Weder APK noch Update nach diesem Forschungszweig als „fertig“ deklarieren.** Die private Original-P12 bleibt auf dem Fold7, ihr Passwort wird niemals in GitHub, Code, Dokumenten oder normalen Logs gespeichert. Der bestehende Render-Owner-Release-Gate-Service veröffentlicht nur einen Prüfstatus, **keine** installierbare APK. Es ist ein echter originalsignierter Publisher mit korrekter Freigabe erforderlich. Weder alte `mentorpaired`-Signatur noch QA/debug APK als Update benutzen.

## 5. Die nächsten messbaren Ziele — Priorität vor neuen Versprechen

1. Exakter Forschungs-HEAD: sämtliche **Original-Node-Tests** und relevante Kotlin-/Android-Regressionspakete ausführen, nicht nur Mock-/V8-Prüfungen.
2. Gedächtnis/Disk- und Shared-Compute-Cache als **opt-in Forschungsadapter** umsetzen; persistente Cache-Metadaten, byte-genaue Quelle, Invalidation nach Memory-/Modell-/Tool-/Policy-Revision, Privacy und negative Tests.
3. An `AiContextCompiler`/bestehenden `ComputeFabric`- und M8-Workflow **read-only** anschließen; tatsächliche doppelte Repo-Analysen, Cache-Hit, CPU-Millisekunden, I/O, RAM und kWh/Temperatur messen. Ohne gemessene Verbesserung nicht produktiv aktivieren.
4. Neuronale Modelle (CRE1, zeitabhängige / liquide / spiking-inspirierte Varianten) gegen dieselben reproduzierbaren Baselines vergleichen, ohne Gewichte/Persistenz zu gefährden.
5. Echte, vom Modell allein vorgeschlagene Reparatur mit unabhängiger RED→GREEN-Regression nachweisen.
6. Gesamten kombinierten Stand grün bauen, originalsignierte **höhere** Continuum-Lokal-APK vorbereiten, Download/Updateknopf, Persistenz, Sprache und reale Fold7-Funktion prüfen; nur dann Release.
7. Danach erlaubte echte zusätzliche CPU/GPU-Knoten über `ComputeNodeRegistry` anbinden. Virtuelle Container erzeugen keine neue Hardware.

**Definition Erfolg:** Installierte, bedienbare Haupt-App, echtes lokales Modell und glaubhaft verifizierte Werkzeugresultate – nicht Screenshots, Buchplots, Marketingvergleiche oder Simulation allein.

## 6. Verpflichtung an alle Assistenten

Vor Codearbeit **diese Datei**, Root-`AGENTS.md`, `docs/CONTINUUM-EXPERIMENTVERTRAG.md`, aktuelles `docs/CONTINUUM-EXPERIMENTE.json`, PR-Abstammung sowie den nachweisbaren Geräte-/Teststand lesen. Auf dem offiziellen Repo arbeiten; isolierter PR, vollständige Ergebniskette, keine Main-Automerges, kein ungefragter kostenpflichtiger Compute, keine Geheimnisse. Forschungsrichtungen dürfen nicht gelöscht oder unbesehen in den Haupt-Chatpfad eingehängt werden.


## 7. Verbindliches Update des tatsächlichen Coding- und Speichercodes (31.10.2026)

**Neu im Forschungs-HEAD:** `app/src/main/java/at/heinz/deviceai/cognitive/ContinuumComputeReuseResearch.kt` und `app/src/test/java/at/heinz/deviceai/cognitive/ContinuumComputeReuseResearchTest.kt`.

Der neue **forschungsinterne, bisher nicht in AgentActivity gestartete** Adapter ruft den **bestehenden** `AiContextCompiler` wirklich auf, erzeugt nur einen SHA-256-Fingerabdruck des kontextgeprüften Modellinputs und führt begrenzte Kopien bereits unabhängig belegter **rein deterministischer** Zwischenresultate im flüchtigen Speicher. Key enthält Owner-/Kontext-/Modell-/Policy-Revision und Gedächtnis-Epoch. Die Funktionen liefern `hits`, `misses`, `verifiedWrites`, `rejects` und `entries` als erste messbare Zähler. Standardverifikation lehnt alle neuen Einträge ab. Neun neue JUnit-Testmethoden prüfen Isolation, Zählung, TTL, LRU und Fehlerfälle. **Keine JVM-/Gradle-Ausführung dieser neuen Tests bisher belegt**; diese Zähler sind ab sofort instrumentierbar, jedoch keine gemessene Live-App-Leistung.

**Separater Einstieg für Entwickler, die auf dem historischen `main` landen:** [Docs-only Draft #288](https://github.com/Ullmann27/device-ai/pull/288). Dieser PR hat bisher nichts in `main` gemergt.

**Vergleich letzter APK → kommender Kandidat:** [Release-Kompatibilitätsmatrix](release/CONTINUUM-APK-COMPARISON-20261031.md). Vorhandene Render QA ist Quell-/Testabnahme eines älteren Commit; Render Release-Gate ist nicht der für In-App-Updates autorisierte APK-Publisher. Bis Originalsignatur und VersionCode > bestätigter Installation reproduzierbar nachgewiesen sind: **kein neuer installierbarer Download**.

**Zusatzabnahme:** Ein neunter Kotlin/JUnit-Test ruft den **realen bestehenden** `AiContextCompiler` über `compileObserved` mit einer verifizierten und einer sensiblen Erinnerung auf. Er prüft, dass sensible Daten weder in den Compiler-Kontext noch in einen Cache gelangen. Die Abnahme ist **als Quellcode hinzugefügt**, noch nicht durch Gradle bestanden.
