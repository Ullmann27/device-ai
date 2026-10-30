# M8 gVisor-Sandbox, MCP-Adapter, Betriebsprofile – Übergabe an GPT-6 (Opus 5.5, 30.10.2026)

**Branch:** `claude/m8-gvisor-executor-kxrxy2`, Basis `chatgpt/continuum-m8-gvisor-mcp-offline-20261030` (`228afa4a`). Draft-PR, kein Merge.

## Fertig und getestet (Host, Node 22; als uid 65534 wegen `ISOLATION_UNPRIVILEGED_UID_REQUIRED`)
1. **gVisor im vorhandenen Executor** (`.github/continuum/isolated-executor.mjs`)
   - Konfiguration `{"runtime":"runsc"}` ergänzt `--runtime=runsc`; die bestehende Härtung bleibt unverändert.
   - **Vorher:** `docker info` muss `runsc` melden, sonst `ISOLATION_GVISOR_UNAVAILABLE`. Dabei startet kein Container, und es gibt keinen Host-Fallback.
   - **Nachher:** `docker inspect` muss die Runtime `runsc` bestätigen, sonst `ISOLATION_RUNTIME_MISMATCH`.
   - **Beleg:** Er trägt `runtime`; `isGvisorEvidence()` prüft das.
   - **Tests:** `isolated-executor-gvisor.test.mjs` 4/4 mit gefälschter Docker-CLI.
2. **Fehlerbehebung im Benchmark:** `--validate` unter `node --test` erbte `NODE_TEST_CONTEXT`, sodass die Ausgangsbasis scheinbar grün war; der 30-Aufgaben-Test dieses Branches war deshalb rot. Jetzt ist er 30/30 gültig und `continuum-agent-benchmark-extra.test.mjs` 2/2.
3. **MCP-Adapter** (`.github/continuum/mcp-adapter.mjs`)
   - Lokaler stdio-Transport, Allowlist des Besitzers.
   - Entscheidungstabelle **identisch zu `ContinuumDualControlPolicy.kt`** auf `feature/continuum-dual-control-profiles-20261030`.
   - Prüfung des Arbeitsbereichs, Budgets für Aufrufe, Zeit und Ergebnisgröße, Quittungen.
   - Der Server erbt keine Umgebungsvariablen außer `PATH`.
   - **Tests:** `mcp-adapter.test.mjs` 3/3, darunter 8 Negativfälle.

## Offen, mit Grund
- **Echter gVisor-Lauf:** Hier gibt es keinen Docker-Daemon und kein `runsc`. Auf einem Linux-Host:
  ```
  scripts/continuum-gvisor-selftest.sh sha256:<lokales-image> /pfad/zu/node-root
  ```
  Prüft: gVisor-Banner, kein Netz, keine Host-Geheimnisse, Root-Dateisystem nur lesbar, und eine allgemeine Reparatur `VERIFIED` mit **Referenzlösung** (keine Modellleistung).
- **Fold7:** Android bietet weder Docker noch `runsc`. gVisor sichert den PC- und CI-Pfad, **nicht** das Telefon. Fürs Telefon bleibt CI als Sandbox oder ein Android-`isolatedProcess`.
- **Echte Modellinferenz:** weiter nicht gemessen (`--brain chat`).
- **Playwright-MCP:** nicht installiert und nicht gestartet, weil hier kein npm- und Browser-Download möglich ist.
  - Einsatz: `createMcpSession({command:'npx',args:['@playwright/mcp@<gepinnte-version>','--headless','--isolated'],allowlist:{browser_navigate:{risk:'READ_ONLY',externalSideEffect:true},browser_snapshot:{risk:'READ_ONLY'}},profile:'PROTECTED',grant,…})`.
  - Lizenz: Apache-2.0 (vor Nutzung prüfen). Dabei gibt es Netzwerkzugriff, deshalb nur nach Besitzerfreigabe.
- **Profile in der App:** Die Kotlin-Policy ist nicht produktiv eingebunden; der MCP-Adapter ist noch nicht an den Android-Teamleiter angeschlossen.

## Nächste Schritte für GPT-6 (in dieser Reihenfolge)
1. Den Selbsttest auf einem Linux-Host mit gVisor ausführen und die Rohausgabe committen.
2. `--brain chat --executor <runsc-config>` auf den 30 Aufgaben gegen den Einmal-Provider, vorab registriert.
3. `ContinuumDualControlPolicy.kt` und `mcp-adapter.mjs` gegen eine gemeinsame Fallliste prüfen, sodass die Entscheidungen identisch sind. Danach den Adapter über den Teamleiter (`autonomous-teamleader.mjs`) als Werkzeugquelle anbinden.
4. Playwright-MCP gepinnt in einer Testumgebung mit `PROTECTED` erproben.

## M8.1 (Commit nach `0dd5afd`): MCP gehärtet, erster echter Werkzeugdurchlauf
- **Gehärtet:**
  - Es startet nur ein vom Besitzer freigegebenes Serverprogramm: absoluter Pfad mit aufgelöstem realpath, Argumente exakt wie freigegeben (`approvedServers`), sonst `MCP_SERVER_NOT_APPROVED`.
  - Leere Umgebung (nur `PATH=/usr/bin:/bin`); `cwd` ist der Arbeitsbereich.
  - Eigene Prozessgruppe, die bei Fehler, Timeout und Sitzungsende ganz beendet wird (`maxSessionMs`).
  - Bereichsprüfung auch für verschachtelte Argumente und nach Auflösen von Symlinks.
  - **Keine OS-Isolation:** Der Server läuft unter demselben Benutzer; es gibt keine Prozess- oder RAM-Limits auf OS-Ebene.
- **Echt:** `.github/continuum/mcp-git-readonly-server.mjs` (`git_head`, `git_status`, ohne Argumente und ohne Shell). Kette: Auftrag → `decideToolCall` (Tabelle von `ContinuumDualControlPolicy.kt`) → git → tatsächlicher HEAD → Quittung mit `resultSha256`.
- **Negativ:** nicht freigegebene Argumente, nicht freigegebenes Programm, angebotenes aber nicht erlaubtes Werkzeug, widerrufene Freigabe, verschachtelter Symlink-Ausbruch.
- **Tests:** `mcp-adapter.test.mjs` 4/4; `coding-*`, `repair-loop`, `benchmark-extra` 96/96; gVisor und Executor (uid 65534) 9/9.

## Nächster Zyklus
- **A, echte Inferenz:** `scripts/continuum-local-llama-termux.sh` (vorhanden) starten, dann:
  ```
  CONTINUUM_LOCAL_BRAIN_API_KEY=… node scripts/continuum-agent-benchmark.mjs --brain chat --only gen-sum-off-by-one
  ```
  Auf einem Docker-Host zusätzlich `--executor <cfg>`. `brainKind` und das Server-Log gehören in den Beleg.
- **B, Worker:** `createMcpSession` über `autonomous-teamleader.mjs` als Werkzeugquelle anbinden. Zyklen, Abbruch (`close()`), Limits und Wiederaufnahme testen.
- **C, gVisor:** `scripts/continuum-gvisor-selftest.sh` auf einem echten `runsc`-Host ausführen und die Rohausgabe committen.
- **D, APK:**
  - Die Updates aus PR #260 auf einen Release-Branch bringen.
  - Der Besitzer signiert in Termux mit der Original-P12; das Passwort gibt nur er ein.
  - Danach mit einem zweiten Release den In-App-Updater auf dem Fold7 prüfen.
