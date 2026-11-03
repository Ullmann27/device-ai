# Lokaler Continuum-Coding-Dienst: Bedienung

Diese Anleitung beschreibt die implementierten CLI-Verträge. Die gemeinsame
Abnahme des endgültigen Quellstands steht noch aus; maßgeblich ist `HANDOFF.md`.
Nach Einrichtung und Start braucht der Dienst keine Codex-, Opus- oder
Cloud-Modellaufrufe. Die bisherigen Ausführungsnachweise stammen von Linux x64.
Eine laufende Cloud-Entwicklungsumgebung ist kein zugesagter Dauerhost. Für den
Dauerbetrieb müssen diese Dateien und Prozesse auf einem verfügbaren eigenen
Rechner liegen. ARM64/Fold7 ist eine getrennte, offene Abnahme.

## Voraussetzungen

- Sauberer Checkout des genau abgenommenen Quellcommits; während des Betriebs
  weder diesen Checkout noch das beauftragte Projekt ändern.
- Node, Git und Java; geprüft wurden Node24.19 und der vorhandene JVM-Buildpfad.
  Für die Profilbrücke werden außerdem die zum Projekt gehörenden Kotlin-
  Compiler-/Runtime-Abhängigkeiten benötigt. Auf einem anderen Rechner muss
  `CONTINUUM_FABRIC_JVM_DEPS` auf deren tatsächlich vorhandenes Verzeichnis zeigen;
  der Standardpfad `/workspace/continuum-build-env/native-graph-deps` gehört nur
  zu dieser Entwicklungsumgebung.
- Bereits zugelassene Qwen2.5-Coder-1.5B-Q4_K_M-GGUF und llama.cpp-Binärdatei.
  Modell-SHA: `cc324af070c2ecbfd324a30884d2f951a7ff756aba85cb811a6ec436933bb046`.
  Runtime-SHA: `c2c13a2a27c5a85137b6d083074fa83e0d990db129e715f8452352e3730cbf5a`.
  Keine7B-Freigabe, kein stiller Download oder Modellwechsel.
- Vorhandene, genehmigte Runtime-Konfiguration samt Modellmanifest und eine
  externe Isolationskonfiguration für den vorhandenen `ownerCodingExecutor`.
  Das tatsächliche Docker-Image und die feste Toolchain müssen lokal verfügbar
  sein. Es gibt keinen ungeschützten Shell-Ersatz.
- Der tatsächliche Kotlin-Profil-Store und seine zur Quelle passenden Klassen.
  Die Dateien sind Eigentümerdaten, keine Ausgabe eines frei formulierten Bots.

Auf dem hier geprüften Host liegen die genehmigten Modellangaben in
`/workspace/synapse-local-model/runtime-env.json`. Diese Ortsangabe ist kein
automatisch auf anderen Rechnern vorhandener Installationspfad. Modell und
Runtime bleiben separat von privaten Freigaben, Zustandsdateien und Quellcode.

## Profilzugriff vorbereiten

Nach dem bestehenden Gradle-Kompilierungspfad erstellt das folgende Skript den
realen Registry-Zugriff und führt seine Software-Vertragsprüfungen aus. `out` muss
ein frisches absolutes Verzeichnis außerhalb des Quellcheckouts sein.

```bash
SYNAPSE_BASE_CLASSES=/absoluter/checkout/app/build/tmp/kotlin-classes/debug \
  CONTINUUM_FABRIC_JVM_DEPS=/absoluter/pfad/zu/jvm-abhaengigkeiten \
  bash tools/genesis-workspaces/run-host.sh /privat/profil-bruecke
```

Für den Dienst wird `runtime-classpath.txt` verwendet. Die umfassende
Compiler-Classpath-Datei ist dafür nicht nötig. Ein vorhandener Profil-Store wird
weiterverwendet. Nur für eine ausdrücklich neue Installation können die
20 Startprofile mit dem bestehenden Store angelegt werden:

```bash
java -Xmx128m -cp "$(cat /privat/profil-bruecke/runtime-classpath.txt)" \
  at.heinz.deviceai.chief.HandoffHostChecks seed /privat/bot-profile
```

Dies registriert Identitäten und startet keine Modellberechnung. Namen und Rollen
verleihen keine Ausführungsrechte.

## Einrichten, starten, beobachten

Ersetze die absoluten Pfade durch vorhandene eigene Dateien. Der private
Dienstzustand muss außerhalb von Quellcode, Zielprojekt und Profil-Store liegen.
Er wird exklusiv mit Eigentümerzugriff angelegt. Die Einrichtung überschreibt
keine bestehende Konfiguration.

```bash
node scripts/continuum-coding-service.mjs init \
  --config /privat/continuum-dienst/config.json \
  --project /absoluter/pfad/zum/zielprojekt \
  --profiles /privat/bot-profile \
  --classpath "$(cat /privat/profil-bruecke/runtime-classpath.txt)" \
  --runtime-env /privat/runtime-env.json \
  --isolation-config /privat/isolation.json \
  --max-queue 8 --max-records 1000 --max-cycles 1000

node scripts/continuum-coding-service.mjs start --config /privat/continuum-dienst/config.json
node scripts/continuum-coding-service.mjs status --config /privat/continuum-dienst/config.json
node scripts/continuum-coding-service.mjs agents --config /privat/continuum-dienst/config.json --limit 20
node scripts/continuum-coding-service.mjs tasks --config /privat/continuum-dienst/config.json --limit 20
```

`start` startet einen eigenen Prozess. `run` ist der entsprechende Vordergrundlauf.
Ein Dienst gehört zunächst zu einem Zielprojekt. Er verwendet den vorhandenen
Chief-Loop mit einem aktiven Coding-Arbeiter. Das Modell wird bedarfsgerecht je
Auftrag geladen;20 Profile bedeuten weder20 geladene Modelle noch20 laufende
Computer. Wartende Aufträge und gespeicherte Historie haben getrennte Grenzen.
Erreichen von Speicher- oder Zyklusgrenzen ist ein sichtbarer Stopp/Blocker.

## Eigenen Auftrag prüfen und einreichen

Die Datei `/privat/auftrag.json` folgt dem vorhandenen Vertrag
`continuum-coding-task`, Version1. Beispielstruktur mit bewusst einzusetzendem
echten Commit, passendem Dateipfad und konkreten Abnahmeanforderungen:

```json
{
  "schema": "continuum-coding-task",
  "version": 1,
  "id": "mein-fehler-001",
  "sourceCommit": "HIER_DEN_TATSAECHLICHEN_40STELLIGEN_GIT_COMMIT_EINSETZEN",
  "kind": "BUGFIX",
  "goal": "Beschreibe hier den konkreten beobachteten Fehler.",
  "allowedPaths": ["lib/subject.mjs"],
  "acceptance": ["Beschreibe hier das erwartete Verhalten; vorhandene Tests bleiben unveraendert."],
  "chain": "node",
  "limits": {"maxModelCalls": 1, "maxContextChars": 12000}
}
```

Die ursprünglichen Tests müssen im Zielprojekt tatsächlich vorhanden sein und
zur zugelassenen festen Testkette passen. Höchstens drei zulässige Quelldateien
sind änderbar; Tests, Steuerung, Geheimnisse, Merge und Veröffentlichung bleiben
außerhalb des Auftrags. Die bestehende Forge-Profilgrenze ist ein Modellaufruf.

```bash
node scripts/continuum-coding-service.mjs prepare \
  --config /privat/continuum-dienst/config.json --task /privat/auftrag.json
```

Prüfe die angezeigte Aufgabe, Quelle, Grenzen und `reviewSha256`. Erst danach
übernimm diesen konkreten Hash als eigene Freigabe:

```bash
node scripts/continuum-coding-service.mjs submit \
  --config /privat/continuum-dienst/config.json --task /privat/auftrag.json \
  --approve HIER_DEN_GEPRUEFTEN_reviewSha256_EINSETZEN
```

Eine automatisch aus Modelltext übernommene Bestätigung ist keine Besitzerfreigabe.
Wiederholtes Einreichen derselben ID und desselben Inhalts erzeugt keinen zweiten
Lauf. FORGE erzeugt einen Kandidaten, LINT prüft über den vorhandenen unabhängigen
Ausführungspfad. ECHO erstellt einen deterministischen beleggebundenen Bericht;
ein weiterer Sprachmodellaufruf oder eine Modellmehrheit ersetzt keine Tests.

```bash
node scripts/continuum-coding-service.mjs evidence \
  --config /privat/continuum-dienst/config.json --task-id mein-fehler-001 --evidence-id result
node scripts/continuum-coding-service.mjs evidence \
  --config /privat/continuum-dienst/config.json --task-id mein-fehler-001 --evidence-id report
node scripts/continuum-coding-service.mjs cancel \
  --config /privat/continuum-dienst/config.json --task-id mein-fehler-001
node scripts/continuum-coding-service.mjs stop --config /privat/continuum-dienst/config.json
node scripts/continuum-coding-service.mjs status --config /privat/continuum-dienst/config.json
```

Eine Stoppanforderung und ein bestätigter Prozessstillstand sind unterscheidbar.
Nach einem Neustart bleiben Profile, Freigaben, Aufgaben und Belege erhalten.
Unklar unterbrochene Ausführungen bleiben `UNKNOWN`; sie werden nicht automatisch
wiederholt. Bereits geprüfte Reparaturen bleiben Vorschläge zur Besitzerprüfung.

## Lesender MCP-Zugang

Der separate Beobachter verwendet dieselben validierten Daten. Er hat genau vier
Werkzeuge: `get_status`, `list_agents`, `list_tasks`, `get_evidence`.

```bash
install -d -m 700 /privat/mcp-lesezugang
node scripts/continuum-coding-mcp.mjs init-token --token-file /privat/mcp-lesezugang/token
node scripts/continuum-coding-mcp.mjs serve \
  --config /privat/continuum-dienst/config.json \
  --token-file /privat/mcp-lesezugang/token --port 8766
```

Die Token-Datei liegt außerhalb von Dienstzustand, Projekt, Quellcode und
Profil-Store. Sie wird nicht ausgegeben oder in einen Auftrag kopiert. Der Server
bindet an127.0.0.1; ein lokaler MCP-Client muss Bearer-Authentifizierung und die
MCP-Sitzungsbedingungen einhalten. Beenden dieses Beobachters stoppt keinen
Coding-Auftrag.

**ChatGPT ist dadurch noch nicht verbunden.** Die aktuellen offiziellen
Anforderungen an Erreichbarkeit und OAuth sowie die notwendigen Besitzeraktionen
sind separat im MCP-Kompatibilitätsbericht dokumentiert. In dieser Sitzung gibt
es kein Werkzeug zur Registrierung einer ChatGPT-Verbindung. Es wurde kein
öffentlicher Endpoint oder Tunnel aktiviert.
